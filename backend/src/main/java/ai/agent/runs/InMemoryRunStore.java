package ai.agent.runs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs in memory (lost on restart).
 */
public class InMemoryRunStore implements RunStore {

    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private final Map<String, List<String>> byThread = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    @Override
    public void save(Run run) {
        if (runs.put(run.id(), run) == null) {
            List<String> ids = byThread.computeIfAbsent(run.threadId(), t -> new ArrayList<>());
            synchronized (ids) {
                ids.add(run.id());
            }
            cancelFlags.put(run.id(), new AtomicBoolean());
        }
    }

    @Override
    public Optional<Run> find(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public List<Run> forThread(String threadId) {
        List<String> ids = byThread.getOrDefault(threadId, List.of());
        synchronized (ids) {
            return ids.stream().map(runs::get).toList();
        }
    }

    @Override
    public List<Run> all() {
        return List.copyOf(runs.values());
    }

    @Override
    public AtomicBoolean cancelFlag(String runId) {
        return cancelFlags.computeIfAbsent(runId, id -> new AtomicBoolean());
    }
}
