package ai.agent.runs;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs indexed by thread in creation order, in memory or in PostgreSQL ({@code agent.store.type}),
 * plus the cancellation flag of each run, always local and read by the worker without the lock.
 */
public interface RunStore {

    /** Inserts or replaces the run (every transition stores a new snapshot). */
    void save(Run run);

    Optional<Run> find(String runId);

    /** Oldest first. */
    List<Run> forThread(String threadId);

    default Optional<Run> active(String threadId) {
        return forThread(threadId).stream().filter(r -> r.status().isActive()).findFirst();
    }

    List<Run> all();

    /** Runs in any of these statuses, in no particular order. */
    default List<Run> withStatus(RunStatus... statuses) {
        List<RunStatus> wanted = List.of(statuses);
        return all().stream().filter(r -> wanted.contains(r.status())).toList();
    }

    AtomicBoolean cancelFlag(String runId);
}
