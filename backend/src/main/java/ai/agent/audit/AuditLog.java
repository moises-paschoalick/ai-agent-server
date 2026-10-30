package ai.agent.audit;

import java.time.Instant;
import java.util.List;

/**
 * The audit trail: in memory (last events) or in PostgreSQL ({@code agent.store.type}).
 * {@link #record} never throws: a lost event must not fail the client's call.
 */
public interface AuditLog {

    void record(AuditEvent event);

    List<AuditEvent> query(AuditQuery query);

    /** Removes events older than {@code cutoff}; returns how many. */
    int deleteOlderThan(Instant cutoff);

    /** Discards everything (for components built without an audit trail). */
    static AuditLog discarding() {
        return new AuditLog() {
            @Override
            public void record(AuditEvent event) {
            }

            @Override
            public List<AuditEvent> query(AuditQuery query) {
                return List.of();
            }

            @Override
            public int deleteOlderThan(Instant cutoff) {
                return 0;
            }
        };
    }
}
