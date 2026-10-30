package ai.agent.runs;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One lock per thread (conversation), shared by /v1 runs, /v1 messages and the sync chat:
 * every state transition and every write that depends on "is a run active?" happens under it.
 */
@Component
public class ThreadLocks {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(String threadId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(threadId, id -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void withLock(String threadId, Runnable action) {
        withLock(threadId, () -> {
            action.run();
            return null;
        });
    }
}
