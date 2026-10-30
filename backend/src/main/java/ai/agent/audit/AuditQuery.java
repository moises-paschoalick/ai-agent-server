package ai.agent.audit;

import java.time.Instant;

/** Filters of GET /audit/events; null means "any". Newest first, at most {@code limit}. */
public record AuditQuery(String threadId, String runId, String assistantId, String type, Instant since, int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 500;

    boolean matches(AuditEvent event) {
        return (threadId == null || threadId.equals(event.threadId()))
                && (runId == null || runId.equals(event.runId()))
                && (assistantId == null || assistantId.equals(event.assistantId()))
                && (type == null || type.equals(event.type()))
                && (since == null || !event.at().isBefore(since));
    }
}
