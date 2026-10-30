package ai.agent.audit;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** The last {@link #CAPACITY} events in memory (lost on restart). */
public class InMemoryAuditLog implements AuditLog {

    public static final int CAPACITY = 1_000;

    private final Deque<AuditEvent> events = new ArrayDeque<>();
    private final AtomicLong ids = new AtomicLong();
    private final int capacity;

    public InMemoryAuditLog() {
        this(CAPACITY);
    }

    InMemoryAuditLog(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public synchronized void record(AuditEvent event) {
        if (events.size() == capacity) {
            events.removeFirst();
        }
        events.addLast(event.withId(ids.incrementAndGet()));
    }

    @Override
    public synchronized List<AuditEvent> query(AuditQuery query) {
        List<AuditEvent> result = new ArrayList<>();
        for (Iterator<AuditEvent> it = events.descendingIterator(); it.hasNext() && result.size() < query.limit(); ) {
            AuditEvent event = it.next();
            if (query.matches(event)) {
                result.add(event);
            }
        }
        return result;
    }

    @Override
    public synchronized int deleteOlderThan(Instant cutoff) {
        int before = events.size();
        events.removeIf(e -> e.at().isBefore(cutoff));
        return before - events.size();
    }
}
