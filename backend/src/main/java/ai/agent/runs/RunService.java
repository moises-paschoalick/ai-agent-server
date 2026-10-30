package ai.agent.runs;

import ai.agent.assistants.Assistant;
import ai.agent.assistants.AssistantService;
import ai.agent.assistants.AssistantTool;
import ai.agent.common.Ids;
import ai.agent.common.ListQuery;
import ai.agent.common.Page;
import ai.agent.errors.NotFoundException;
import ai.agent.errors.ValidationException;
import ai.agent.llm.ModelRouter;
import ai.agent.memory.ConversationStore;
import ai.agent.memory.ToolCall;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.StringNode;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Run lifecycle: creation (with the configuration frozen from the assistant), queries,
 * submit_tool_outputs, cancel and expiration. Every transition happens under the thread lock.
 */
@Service
public class RunService {

    static final String EXPIRED_RESULT = "Tool call expired.";

    private final RunStore runs;
    private final ThreadLocks locks;
    private final RunGuard guard;
    private final RunWorker worker;
    private final ConversationStore conversations;
    private final AssistantService assistants;
    private final ModelRouter router;
    private final Clock clock;
    private final Duration expiresAfter;

    public record ToolOutput(String toolCallId, String output) {}

    public RunService(RunStore runs, ThreadLocks locks, RunGuard guard, RunWorker worker, ConversationStore conversations,
                      AssistantService assistants, ModelRouter router, Clock clock,
                      @Value("${agent.runs.expires-after:600s}") Duration expiresAfter) {
        this.runs = runs;
        this.locks = locks;
        this.guard = guard;
        this.worker = worker;
        this.conversations = conversations;
        this.assistants = assistants;
        this.router = router;
        this.clock = clock;
        this.expiresAfter = expiresAfter;
    }

    /** Validates the request and returns the run it would create (not saved). Thread must exist. */
    public Run prepare(String threadId, RunRequest request) {
        if (request.stream()) {
            throw new ValidationException("Streaming is not supported.", "stream");
        }
        if (request.assistantId() == null || request.assistantId().isBlank()) {
            throw new ValidationException("assistant_id is required", "assistant_id");
        }
        Assistant assistant = assistants.get(request.assistantId());

        String model = request.model() != null ? request.model() : assistant.model();
        try {
            router.resolve(model);
        } catch (ValidationException e) {
            throw new ValidationException(e.getMessage(), "model");
        }
        String base = request.instructions() != null ? request.instructions() : assistant.instructions();
        String instructions = request.additionalInstructions() == null || request.additionalInstructions().isBlank()
                ? base
                : (base == null || base.isBlank() ? request.additionalInstructions() : base + "\n\n" + request.additionalInstructions());
        List<AssistantTool> tools = request.tools() != null && !request.tools().isNull()
                ? assistants.parseTools(JsonNodeFactory.instance.objectNode().set("tools", request.tools()))
                : assistant.tools();
        Double temperature = request.temperature() != null ? request.temperature() : assistant.temperature();
        Double topP = request.topP() != null ? request.topP() : assistant.topP();
        range("temperature", temperature, 2);
        range("top_p", topP, 1);

        long now = now();
        return new Run(Ids.newId("run_"), threadId, assistant.id(), now, RunStatus.QUEUED, List.of(), null,
                now + expiresAfter.toSeconds(), null, null, null, null, null, model, instructions, tools,
                request.metadata(), null, temperature, topP,
                request.toolChoice() != null && !request.toolChoice().isNull() ? request.toolChoice() : StringNode.valueOf("auto"),
                request.parallelToolCalls() != null ? request.parallelToolCalls() : Boolean.TRUE);
    }

    public Run create(String threadId, RunRequest request) {
        conversations.thread(threadId);
        Run run = prepare(threadId, request);
        locks.withLock(threadId, () -> {
            guard.noActiveRunForNewRun(threadId);
            request.additionalMessages().forEach(m -> conversations.append(threadId, m));
            runs.save(run);
        });
        worker.schedule(run.id());
        return run;
    }

    public Run get(String threadId, String runId) {
        conversations.thread(threadId);
        return runs.find(runId)
                .filter(r -> r.threadId().equals(threadId))
                .orElseThrow(() -> new NotFoundException("No run found with id '" + runId + "'."));
    }

    public Page<Run> list(String threadId, ListQuery query) {
        conversations.thread(threadId);
        return Page.of(runs.forThread(threadId), Run::id, query);
    }

    public Run submitToolOutputs(String threadId, String runId, List<ToolOutput> outputs) {
        get(threadId, runId);
        Run updated = locks.withLock(threadId, () -> {
            Run run = get(threadId, runId);
            if (run.status() != RunStatus.REQUIRES_ACTION) {
                throw new ValidationException("Runs in status \"" + run.status().json() + "\" do not accept tool outputs.");
            }
            List<String> expected = run.requiredToolCalls().stream().map(ToolCall::id).toList();
            List<String> got = outputs.stream().map(ToolOutput::toolCallId).toList();
            if (got.size() != new HashSet<>(got).size() || !new HashSet<>(got).equals(new HashSet<>(expected))) {
                throw new ValidationException("Expected tool outputs for call ids " + expected + ", got " + got + ".", "tool_outputs");
            }
            for (ToolOutput output : outputs) {
                if (output.output() == null) {
                    throw new ValidationException("output is required for tool call '" + output.toolCallId() + "' (use \"\" for an empty result)",
                            "tool_outputs");
                }
            }
            Map<String, String> byId = outputs.stream().collect(Collectors.toMap(ToolOutput::toolCallId, ToolOutput::output));
            run.requiredToolCalls().forEach(call -> conversations.appendToolResult(threadId, call, byId.get(call.id())));
            Run queued = run.withStatus(RunStatus.QUEUED).withRequiredToolCalls(List.of());
            runs.save(queued);
            return queued;
        });
        worker.schedule(runId);
        return updated;
    }

    public Run cancel(String threadId, String runId) {
        get(threadId, runId);
        return locks.withLock(threadId, () -> {
            Run run = get(threadId, runId);
            Run next = switch (run.status()) {
                case QUEUED, REQUIRES_ACTION -> {
                    conversations.closePendingToolCalls(threadId, RunWorker.CANCELLED_RESULT);
                    yield run.withStatus(RunStatus.CANCELLED).withCancelledAt(now()).withRequiredToolCalls(List.of());
                }
                case IN_PROGRESS -> {
                    runs.cancelFlag(runId).set(true);
                    yield run.withStatus(RunStatus.CANCELLING);
                }
                case CANCELLING -> run;
                default -> throw new ValidationException("Cannot cancel run with status '" + run.status().json() + "'.");
            };
            runs.save(next);
            return next;
        });
    }

    /** Moves runs waiting in requires_action past their expires_at to expired. */
    public void expire() {
        long now = now();
        runs.withStatus(RunStatus.REQUIRES_ACTION).stream()
                .filter(r -> r.expiresAt() != null && now > r.expiresAt())
                .forEach(r -> locks.withLock(r.threadId(), () -> runs.find(r.id())
                        .filter(c -> c.status() == RunStatus.REQUIRES_ACTION && now > c.expiresAt())
                        .ifPresent(c -> {
                            conversations.closePendingToolCalls(c.threadId(), EXPIRED_RESULT);
                            runs.save(c.withStatus(RunStatus.EXPIRED).withRequiredToolCalls(List.of()));
                        })));
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }

    private static void range(String field, Double value, double max) {
        if (value != null && (value < 0 || value > max)) {
            throw new ValidationException(field + " must be between 0 and " + (int) max, field);
        }
    }
}
