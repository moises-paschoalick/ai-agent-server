package ai.agent.persistence;

import ai.agent.memory.ConversationStore;
import ai.agent.runs.Run;
import ai.agent.runs.RunStatus;
import ai.agent.runs.RunStore;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.List;

/**
 * At startup, runs left active by the previous process have no worker any more: queued and
 * in_progress become failed, cancelling becomes cancelled. requires_action is kept, so
 * submit_tool_outputs still resumes it.
 */
@Slf4j
public class RunRecovery {

    static final Run.LastError RESTARTED =
            new Run.LastError("server_error", "The server restarted while the run was in progress.");
    static final String INTERRUPTED_RESULT = "Tool call interrupted by a server restart.";

    private final RunStore runs;
    private final ConversationStore conversations;
    private final Clock clock;

    public RunRecovery(RunStore runs, ConversationStore conversations, Clock clock) {
        this.runs = runs;
        this.conversations = conversations;
        this.clock = clock;
    }

    public int recover() {
        long now = clock.instant().getEpochSecond();
        List<Run> interrupted = runs.withStatus(RunStatus.QUEUED, RunStatus.IN_PROGRESS, RunStatus.CANCELLING);
        for (Run run : interrupted) {
            if (conversations.exists(run.threadId())) {
                conversations.closePendingToolCalls(run.threadId(), INTERRUPTED_RESULT);
            }
            runs.save(run.status() == RunStatus.CANCELLING
                    ? run.withStatus(RunStatus.CANCELLED).withCancelledAt(now).withRequiredToolCalls(List.of())
                    : run.withStatus(RunStatus.FAILED).withFailedAt(now).withLastError(RESTARTED).withRequiredToolCalls(List.of()));
        }
        if (!interrupted.isEmpty()) {
            log.warn("{} run(s) were interrupted by the restart and are now failed or cancelled", interrupted.size());
        }
        return interrupted.size();
    }
}
