package ai.agent.runs;

import ai.agent.assistants.AssistantToolPolicy;
import ai.agent.audit.AuditContext;
import ai.agent.engine.AgentRunner;
import ai.agent.engine.RunConfig;
import ai.agent.engine.RunContext;
import ai.agent.engine.RunOutcome;
import ai.agent.errors.ApiException;
import ai.agent.llm.CallOptions;
import ai.agent.memory.ConversationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes one step of a run on a virtual thread: takes the lock to move queued -> in_progress,
 * releases it while the model is called, and takes it again to store the outcome. Any exception
 * ends the run as failed, so a run never stays in_progress.
 */
@Slf4j
@Component
public class RunWorker {

    static final String CANCELLED_RESULT = "Tool call cancelled.";

    private final RunStore runs;
    private final ThreadLocks locks;
    private final ConversationStore conversations;
    private final AgentRunner runner;
    private final ExecutorService executor;
    private final Clock clock;
    private final Duration expiresAfter;
    private final AssistantToolPolicy toolPolicy;

    public RunWorker(RunStore runs, ThreadLocks locks, ConversationStore conversations, AgentRunner runner,
                     @Qualifier("runExecutor") ExecutorService executor, Clock clock,
                     @Value("${agent.runs.expires-after:600s}") Duration expiresAfter, AssistantToolPolicy toolPolicy) {
        this.runs = runs;
        this.locks = locks;
        this.conversations = conversations;
        this.runner = runner;
        this.executor = executor;
        this.clock = clock;
        this.expiresAfter = expiresAfter;
        this.toolPolicy = toolPolicy;
    }

    public void schedule(String runId) {
        String caller = AuditContext.caller();
        executor.execute(() -> AuditContext.runAs(caller, () -> execute(runId)));
    }

    void execute(String runId) {
        Run queued = runs.find(runId).orElse(null);
        if (queued == null) {
            return;
        }
        String threadId = queued.threadId();
        Run run = locks.withLock(threadId, () -> {
            Run current = runs.find(runId).orElseThrow();
            if (current.status() != RunStatus.QUEUED) {
                return null;
            }
            Run started = current.withStatus(RunStatus.IN_PROGRESS)
                    .withStartedAt(current.startedAt() != null ? current.startedAt() : now());
            runs.save(started);
            return started;
        });
        if (run == null) {
            return;
        }

        long start = System.nanoTime();
        AtomicBoolean cancel = runs.cancelFlag(runId);
        RunOutcome outcome;
        AssistantToolPolicy.ExecutionTools tools = toolPolicy.resolve(run.tools());
        try {
            outcome = runner.run(threadId,
                    new RunConfig(run.instructions(), run.model(), tools.clientTools(),
                            new CallOptions(run.temperature(), run.topP(), null, false), tools.systemTools()),
                    new RunContext(run.id(), run.assistantId(), cancel::get,
                            conversations.toolRoundsSinceLastUserMessage(threadId)));
        } catch (ApiException e) {
            outcome = new RunOutcome.FailedOutcome(e.getCode(), e.getMessage(), null);
        } catch (RuntimeException e) {
            log.error("Run {} failed unexpectedly", runId, e);
            outcome = new RunOutcome.FailedOutcome("server_error", "Internal server error.", null);
        }
        RunOutcome result = outcome;
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        try {
            locks.withLock(threadId, () -> apply(runId, threadId, result, cancel.get(), latencyMs));
        } catch (RuntimeException e) {
            log.error("Run {} could not store its outcome", runId, e);
            locks.withLock(threadId, () -> runs.find(runId)
                    .filter(r -> r.status().isActive())
                    .ifPresent(r -> runs.save(r.withStatus(RunStatus.FAILED).withFailedAt(now())
                            .withLastError(new Run.LastError("server_error", "Internal server error.")))));
        }
    }

    private void apply(String runId, String threadId, RunOutcome outcome, boolean cancelRequested, long latencyMs) {
        Run current = runs.find(runId).orElseThrow();
        if (current.status() != RunStatus.IN_PROGRESS && current.status() != RunStatus.CANCELLING) {
            return;
        }
        Run run = current.withUsage(current.usage().plus(outcome.usage()));
        long now = now();

        Run next;
        if (cancelRequested || current.status() == RunStatus.CANCELLING || outcome instanceof RunOutcome.CancelledOutcome) {
            conversations.closePendingToolCalls(threadId, CANCELLED_RESULT);
            next = run.withStatus(RunStatus.CANCELLED).withCancelledAt(now).withRequiredToolCalls(List.of());
        } else {
            next = switch (outcome) {
                case RunOutcome.MessageOutcome m -> run.withStatus(RunStatus.COMPLETED).withCompletedAt(now);
                case RunOutcome.ToolCallsOutcome t -> run.withStatus(RunStatus.REQUIRES_ACTION)
                        .withRequiredToolCalls(conversations.pendingToolCalls(threadId))
                        .withExpiresAt(now + expiresAfter.toSeconds());
                case RunOutcome.IncompleteOutcome i -> run.withStatus(RunStatus.INCOMPLETE)
                        .withIncompleteReason(i.reason()).withCompletedAt(now);
                case RunOutcome.FailedOutcome f -> run.withStatus(RunStatus.FAILED).withFailedAt(now)
                        .withLastError(new Run.LastError(f.code(), f.message()));
                case RunOutcome.CancelledOutcome c -> run.withStatus(RunStatus.CANCELLED).withCancelledAt(now);
            };
        }
        runs.save(next);
        log.info("Run {} on {} with {}: {} in {} ms, {} tokens", runId, threadId, run.model(), next.status().json(),
                latencyMs, next.usage().totalTokens());
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }
}
