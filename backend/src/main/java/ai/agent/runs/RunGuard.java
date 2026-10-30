package ai.agent.runs;

import ai.agent.errors.ActiveRunException;
import org.springframework.stereotype.Component;

/**
 * "At most one active run per thread" checks. Call them while holding the thread lock
 * ({@link ThreadLocks}), so the answer cannot change before the write that depends on it.
 */
@Component
public class RunGuard {

    private final RunStore runs;

    public RunGuard(RunStore runs) {
        this.runs = runs;
    }

    public void noActiveRunForNewRun(String threadId) {
        runs.active(threadId).ifPresent(r -> {
            throw new ActiveRunException("Thread " + threadId + " already has an active run " + r.id() + ".");
        });
    }

    public void noActiveRunForMessage(String threadId) {
        runs.active(threadId).ifPresent(r -> {
            throw new ActiveRunException("Can't add messages to " + threadId + " while a run " + r.id() + " is active.");
        });
    }
}
