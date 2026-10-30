package ai.agent.services;

import ai.agent.assistants.Assistant;
import ai.agent.assistants.AssistantRepository;
import ai.agent.assistants.AssistantToolPolicy;
import ai.agent.dto.ChatReply;
import ai.agent.dto.ToolResultRequest;
import ai.agent.engine.AgentRunner;
import ai.agent.engine.LlmFailures;
import ai.agent.engine.RunConfig;
import ai.agent.engine.RunContext;
import ai.agent.engine.RunOutcome;
import ai.agent.engine.StreamListener;
import ai.agent.errors.ConflictException;
import ai.agent.errors.NotFoundException;
import ai.agent.llm.CallOptions;
import ai.agent.llm.CancelSignal;
import ai.agent.errors.ValidationException;
import ai.agent.memory.ConversationStore;
import ai.agent.memory.StoredMessage;
import ai.agent.memory.ToolCall;
import ai.agent.runs.RunGuard;
import ai.agent.runs.ThreadLocks;
import ai.agent.tools.ToolService;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sync chat contract: /chat runs the agent; a client tool call is delivered one at a time and
 * /tools/result records its output and resumes the conversation once every call has a result.
 */
@Service
public class ChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConversationStore conversations;
    private final AgentRunner runner;
    private final ToolService tools;
    private final ChatSettings settings;
    private final ThreadLocks locks;
    private final RunGuard guard;
    private final AssistantRepository assistants;
    private final AssistantToolPolicy toolPolicy;

    public ChatService(ConversationStore conversations, AgentRunner runner, ToolService tools, ChatSettings settings,
                       ThreadLocks locks, RunGuard guard, AssistantRepository assistants, AssistantToolPolicy toolPolicy) {
        this.conversations = conversations;
        this.runner = runner;
        this.tools = tools;
        this.settings = settings;
        this.locks = locks;
        this.guard = guard;
        this.assistants = assistants;
        this.toolPolicy = toolPolicy;
    }

    /**
     * Holds the thread lock for the whole call and refuses while a /v1 run is active on the thread.
     * With an assistant id, this message runs with that assistant; the thread itself belongs to none.
     */
    public ChatReply chat(String conversationId, String prompt, String assistantId) {
        return chat(conversationId, prompt, assistantId, null);
    }

    /** With a {@code stream}, the model's text goes to it as it is generated (null: no streaming). */
    public ChatReply chat(String conversationId, String prompt, String assistantId, Stream stream) {
        conversations.thread(conversationId);
        return locks.withLock(conversationId, () -> {
            guard.noActiveRunForMessage(conversationId);
            return chatLocked(conversationId, prompt, assistantId, stream);
        });
    }

    public ChatReply toolResult(ToolResultRequest request) {
        return toolResult(request, null);
    }

    public ChatReply toolResult(ToolResultRequest request, Stream stream) {
        conversations.thread(request.conversationId());
        return locks.withLock(request.conversationId(), () -> {
            guard.noActiveRunForMessage(request.conversationId());
            return toolResultLocked(request, stream);
        });
    }

    private ChatReply chatLocked(String conversationId, String prompt, String assistantId, Stream stream) {
        String id = assistantId == null || assistantId.isBlank() ? null : assistantId;
        RunConfig config = configFor(id, "No assistant found with id '" + id + "'.");
        conversations.closePendingToolCalls(conversationId, AgentRunner.NOT_EXECUTED);
        conversations.append(conversationId, StoredMessage.user(conversationId, prompt));
        return execute(conversationId, id, config, 0, stream);
    }

    /**
     * The assistant's configuration, read on every call so edits apply to the next message; with no
     * assistant, the global prompt, model and tool catalog. Resolved before anything is written, so
     * an unknown assistant leaves the history as it was.
     */
    private RunConfig configFor(String assistantId, String notFoundMessage) {
        if (assistantId == null) {
            return new RunConfig(settings.systemPrompt(), settings.model(), tools.dynamicSpecifications());
        }
        Assistant assistant = assistants.find(assistantId).orElseThrow(() -> new NotFoundException(notFoundMessage));
        AssistantToolPolicy.ExecutionTools executionTools = toolPolicy.resolve(assistant.tools());
        return new RunConfig(assistant.instructions(), assistant.model(), executionTools.clientTools(),
                new CallOptions(assistant.temperature(), assistant.topP(), null, false), executionTools.systemTools());
    }

    private ChatReply toolResultLocked(ToolResultRequest request, Stream stream) {
        String conversationId = request.conversationId();
        // the turn that asked for the tool keeps its assistant, as a run does until its tool outputs arrive
        String assistantId = conversations.toolCallAssistant(conversationId);
        RunConfig config = configFor(assistantId,
                "The assistant '" + assistantId + "' that requested this tool call no longer exists.");
        List<ToolCall> pending = conversations.pendingToolCalls(conversationId);
        if (request.toolOutput() == null) {
            throw new ValidationException("tool_output is required (use \"\" for an empty result)");
        }
        if (request.toolName() == null) {
            throw new ValidationException("tool_name is required");
        }
        if (pending.isEmpty()) {
            throw new ConflictException("No pending tool call in conversation '" + conversationId + "'.");
        }
        ToolCall call = request.toolCallId() == null ? pending.getFirst() : pending.stream()
                .filter(c -> c.id().equals(request.toolCallId()))
                .findFirst()
                .orElseThrow(() -> new ConflictException("Tool call '" + request.toolCallId() + "' is not pending."));
        if (!call.name().equals(request.toolName())) {
            throw new ValidationException("tool_name '" + request.toolName() + "' does not match the pending tool call '"
                    + call.name() + "' (" + call.id() + ").");
        }

        conversations.appendToolResult(conversationId, call, request.toolOutput());

        List<ToolCall> remaining = conversations.pendingToolCalls(conversationId);
        if (!remaining.isEmpty()) {
            return toolCallReply(remaining.getFirst());
        }
        return execute(conversationId, assistantId, config, conversations.toolRoundsSinceLastUserMessage(conversationId), stream);
    }

    /**
     * Runs the agent; with a {@code stream}, model text goes to it as it is generated. TTFT is the
     * time from here to the first piece of text (the whole latency without streaming).
     */
    private ChatReply execute(String conversationId, String assistantId, RunConfig config, int initialRounds, Stream stream) {
        long start = System.nanoTime();
        AtomicLong firstPiece = new AtomicLong();
        StreamListener listener = stream == null ? null : piece -> {
            firstPiece.compareAndSet(0, System.nanoTime());
            stream.listener().onDelta(piece);
        };
        RunOutcome outcome = runner.run(conversationId, config, new RunContext(null, assistantId, () -> false, initialRounds,
                listener, stream == null ? null : stream.cancel()));
        long end = System.nanoTime();
        long latencyMs = (end - start) / 1_000_000;
        long ttftMs = ((firstPiece.get() == 0 ? end : firstPiece.get()) - start) / 1_000_000;

        return switch (outcome) {
            case RunOutcome.MessageOutcome message ->
                    ChatReply.MessageReply.of(message.text(), metadata(latencyMs, ttftMs, message.usage(), message.model()));
            case RunOutcome.ToolCallsOutcome calls -> toolCallReply(calls.pending().getFirst());
            case RunOutcome.IncompleteOutcome incomplete ->
                    ChatReply.MessageReply.of(incomplete.text(), metadata(latencyMs, ttftMs, incomplete.usage(), incomplete.model()));
            case RunOutcome.FailedOutcome failed -> throw LlmFailures.toApiException(failed);
            case RunOutcome.CancelledOutcome cancelled -> throw new IllegalStateException("Sync chat cannot be cancelled");
        };
    }

    private static ChatReply.Metadata metadata(long latencyMs, long ttftMs, TokenUsage usage, String model) {
        int total = usage == null || usage.totalTokenCount() == null ? 0 : usage.totalTokenCount();
        return new ChatReply.Metadata(latencyMs, ttftMs, total, model);
    }

    /** Where the text of a streamed answer goes, and the signal that its reader is gone. */
    public record Stream(StreamListener listener, CancelSignal cancel) {}

    private static ChatReply.ToolCallReply toolCallReply(ToolCall call) {
        return ChatReply.ToolCallReply.of(call.name(), toolInput(call.argumentsJson()), call.id());
    }

    private static Object toolInput(String argumentsJson) {
        try {
            return MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        } catch (RuntimeException e) {
            return argumentsJson;
        }
    }
}
