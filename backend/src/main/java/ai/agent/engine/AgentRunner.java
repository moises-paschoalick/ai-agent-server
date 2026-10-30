package ai.agent.engine;

import ai.agent.audit.AuditEvent;
import ai.agent.audit.AuditLog;
import ai.agent.common.Ids;
import ai.agent.errors.ApiException;
import ai.agent.llm.CallOptions;
import ai.agent.llm.LlmGateway;
import ai.agent.llm.LlmResult;
import ai.agent.llm.StreamCancelledException;
import ai.agent.memory.ConversationStore;
import ai.agent.memory.StoredMessage;
import ai.agent.memory.ToolCall;
import ai.agent.tools.SchemaConverter;
import ai.agent.tools.SystemToolExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * The agent loop: sends the history and tools to the model, runs system tools on the server
 * and repeats, and stops on text, on client (dynamic) tool calls, on the round limit or on a
 * typed failure. Everything that happens is recorded in the conversation. With a stream listener
 * in the context, model calls stream and their text reaches it as it is generated; a cancelled
 * stream throws {@link StreamCancelledException} and leaves no answer in the conversation.
 */
@Component
public class AgentRunner {

    public static final String NOT_EXECUTED = "Tool call not executed.";

    private final ConversationStore conversations;
    private final LlmGateway gateway;
    private final SystemToolExecutor systemTools;
    private final int maxToolRounds;
    private final AuditLog audit;

    @Autowired
    public AgentRunner(ConversationStore conversations, LlmGateway gateway, SystemToolExecutor systemTools,
                       @Value("${agent.engine.max-tool-rounds:10}") int maxToolRounds, AuditLog audit) {
        this.conversations = conversations;
        this.gateway = gateway;
        this.systemTools = systemTools;
        this.maxToolRounds = maxToolRounds;
        this.audit = audit;
    }

    /** Without an audit trail (tests). */
    public AgentRunner(ConversationStore conversations, LlmGateway gateway, SystemToolExecutor systemTools, int maxToolRounds) {
        this(conversations, gateway, systemTools, maxToolRounds, AuditLog.discarding());
    }

    public RunOutcome run(String threadId, RunConfig config, RunContext ctx) {
        conversations.messages(threadId);

        List<ToolSpecification> tools = new ArrayList<>(config.tools());
        systemTools.definitions().stream()
                .filter(t -> config.systemTools().allows(t.name()))
                .forEach(t -> tools.add(SchemaConverter.toSpecification(t)));
        Set<String> dynamicNames = config.tools().stream().map(ToolSpecification::name).collect(Collectors.toSet());

        int rounds = ctx.initialRounds();
        TokenUsage usage = null;
        String model = null;
        while (true) {
            if (ctx.cancelRequested().getAsBoolean()) {
                return new RunOutcome.CancelledOutcome(usage);
            }

            ChatResponse response;
            long start = System.nanoTime();
            AtomicLong firstPiece = new AtomicLong();
            try {
                List<ChatMessage> messages = MessageMapper.toChatMessages(config.instructions(), conversations.messages(threadId));
                LlmResult result = ctx.listener() == null
                        ? gateway.chat(config.model(), messages, tools, config.options())
                        : gateway.stream(config.model(), messages, tools, config.options(), piece -> {
                            firstPiece.compareAndSet(0, System.nanoTime());
                            ctx.listener().onDelta(piece);
                        }, ctx.streamCancel());
                response = result.response();
                model = response.modelName() != null ? response.modelName() : result.modelName();
                auditCall(threadId, ctx, result.providerId() + ":" + model, start, firstPiece.get(), response.tokenUsage(),
                        response.aiMessage().hasToolExecutionRequests() ? "tool_call" : "message", null);
            } catch (ApiException e) {
                auditCall(threadId, ctx, config.model(), start, firstPiece.get(), null, "error", e.getCode());
                throw e;
            } catch (StreamCancelledException e) {
                auditCall(threadId, ctx, config.model(), start, firstPiece.get(), null, "cancelled", null);
                throw e;
            } catch (RuntimeException e) {
                RunOutcome failure = LlmFailures.classify(e, usage);
                auditCall(threadId, ctx, config.model(), start, firstPiece.get(), null, "error",
                        failure instanceof RunOutcome.FailedOutcome f ? f.code() : "server_error");
                return failure;
            }
            usage = TokenUsage.sum(usage, response.tokenUsage());

            if (ctx.cancelRequested().getAsBoolean()) {
                return new RunOutcome.CancelledOutcome(usage);
            }

            AiMessage ai = response.aiMessage();
            if (!ai.hasToolExecutionRequests()) {
                String text = ai.text() == null ? "" : ai.text();
                conversations.append(threadId, StoredMessage.assistantText(threadId, text, ctx.runId(), ctx.assistantId()));
                return new RunOutcome.MessageOutcome(text, usage, rounds, model);
            }

            String turnId = Ids.newId("turn_");
            List<ToolCall> calls = ai.toolExecutionRequests().stream().map(AgentRunner::toToolCall).toList();
            conversations.append(threadId, StoredMessage.assistantToolCalls(
                    threadId, ai.text(), calls, turnId, ctx.runId(), ctx.assistantId()));

            List<ToolCall> pending = new ArrayList<>();
            for (ToolCall call : calls) {
                if (systemTools.isSystem(call.name()) && config.systemTools().allows(call.name())) {
                    long toolStart = System.nanoTime();
                    String result = executeSystemTool(call);
                    auditTool(threadId, ctx, call.name(), "system", millisSince(toolStart));
                    record(threadId, call, result, turnId, ctx);
                } else if (dynamicNames.contains(call.name())) {
                    auditTool(threadId, ctx, call.name(), "client", null);
                    pending.add(call);
                } else {
                    auditTool(threadId, ctx, call.name(), "unknown", null);
                    record(threadId, call, "Unknown tool '" + call.name() + "'.", turnId, ctx);
                }
            }
            if (!pending.isEmpty()) {
                return new RunOutcome.ToolCallsOutcome(pending, usage, rounds);
            }

            rounds++;
            if (rounds > maxToolRounds) {
                String notice = "Stopped: the tool round limit (" + maxToolRounds + ") was reached.";
                conversations.append(threadId, StoredMessage.assistantText(threadId, notice, ctx.runId(), ctx.assistantId()));
                return new RunOutcome.IncompleteOutcome("max_tool_rounds", notice, usage, rounds, model);
            }
        }
    }

    /** {@code firstPiece}: nanoTime of the first streamed piece, 0 when none (TTFT is then the latency). */
    private void auditCall(String threadId, RunContext ctx, String model, long start, long firstPiece, TokenUsage usage,
                           String outcome, String errorCode) {
        long latencyMs = millisSince(start);
        audit.record(AuditEvent.of(AuditEvent.LLM_CALL).withScope(threadId, ctx.runId(), ctx.assistantId())
                .withCall(model, latencyMs,
                        usage == null ? null : usage.inputTokenCount(),
                        usage == null ? null : usage.outputTokenCount(),
                        usage == null ? null : usage.totalTokenCount())
                .withTtft(firstPiece == 0 ? latencyMs : (firstPiece - start) / 1_000_000)
                .withOutcome(outcome, errorCode));
    }

    /** {@code kind}: system (run here, with its duration), client (returned to the caller) or unknown. */
    private void auditTool(String threadId, RunContext ctx, String name, String kind, Long latencyMs) {
        audit.record(AuditEvent.of(AuditEvent.TOOL_CALL).withScope(threadId, ctx.runId(), ctx.assistantId())
                .withTool(name, latencyMs).withOutcome(kind, null));
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String executeSystemTool(ToolCall call) {
        try {
            return systemTools.execute(call.name(), call.argumentsJson());
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    private void record(String threadId, ToolCall call, String result, String turnId, RunContext ctx) {
        conversations.append(threadId, StoredMessage.toolResult(threadId, call, result, turnId, ctx.runId()));
    }

    private static ToolCall toToolCall(ToolExecutionRequest request) {
        String id = request.id() == null || request.id().isBlank() ? Ids.newId("call_") : request.id();
        return new ToolCall(id, request.name(), request.arguments());
    }
}
