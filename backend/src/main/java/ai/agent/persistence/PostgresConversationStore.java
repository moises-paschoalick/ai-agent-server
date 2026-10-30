package ai.agent.persistence;

import ai.agent.common.Ids;
import ai.agent.memory.ConversationHistory;
import ai.agent.memory.ConversationStore;
import ai.agent.memory.StoredMessage;
import ai.agent.memory.ToolCall;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversations in the {@code threads} and {@code messages} tables; messages keep their order by
 * {@code seq}. Composite writes run in one transaction; the caller still holds the thread lock.
 */
public class PostgresConversationStore implements ConversationStore {

    private static final TypeReference<Map<String, String>> METADATA = new TypeReference<>() {};

    private final PostgresDatabase database;
    private final JdbcClient jdbc;
    private final Clock clock;

    public PostgresConversationStore(PostgresDatabase database, Clock clock) {
        this.database = database;
        this.jdbc = database.jdbc();
        this.clock = clock;
    }

    @Override
    public String create(Map<String, String> metadata) {
        String id = Ids.newId("thread_");
        long now = now();
        jdbc.sql("INSERT INTO threads (id, created_at, updated_at, metadata) VALUES (?, ?, ?, ?::jsonb)")
                .params(id, now, now, Json.write(metadata == null ? Map.of() : metadata))
                .update();
        return id;
    }

    @Override
    public ThreadInfo thread(String threadId) {
        return jdbc.sql("SELECT created_at, metadata FROM threads WHERE id = ?").param(threadId)
                .query((rs, n) -> new ThreadInfo(threadId, rs.getLong(1), Map.copyOf(Json.read(rs.getString(2), METADATA))))
                .optional()
                .orElseThrow(() -> ConversationHistory.notFound(threadId));
    }

    @Override
    public ThreadInfo setMetadata(String threadId, Map<String, String> metadata) {
        int updated = jdbc.sql("UPDATE threads SET metadata = ?::jsonb, updated_at = ? WHERE id = ?")
                .params(Json.write(metadata == null ? Map.of() : metadata), now(), threadId)
                .update();
        if (updated == 0) {
            throw ConversationHistory.notFound(threadId);
        }
        return thread(threadId);
    }

    @Override
    public boolean exists(String threadId) {
        return jdbc.sql("SELECT count(*) FROM threads WHERE id = ?").param(threadId).query(Long.class).single() > 0;
    }

    @Override
    public List<String> ids() {
        return jdbc.sql("SELECT id FROM threads ORDER BY created_at, id").query(String.class).list();
    }

    /** Two reads (threads, then every message in order), summarized by the same rules as in memory. */
    @Override
    public List<ThreadSummary> summaries() {
        Map<String, Long> created = new LinkedHashMap<>();
        jdbc.sql("SELECT id, created_at FROM threads").query(rs -> {
            created.put(rs.getString(1), rs.getLong(2));
        });
        Map<String, List<StoredMessage>> histories = new LinkedHashMap<>();
        jdbc.sql("SELECT thread_id, body FROM messages ORDER BY thread_id, seq").query(rs -> {
            histories.computeIfAbsent(rs.getString(1), t -> new ArrayList<>()).add(Json.read(rs.getString(2), StoredMessage.class));
        });
        List<ThreadSummary> result = new ArrayList<>();
        created.forEach((id, createdAt) -> result.add(ConversationHistory.summary(id, createdAt, histories.getOrDefault(id, List.of()))));
        result.sort(ConversationHistory.SUMMARY_ORDER);
        return result;
    }

    /** Messages and runs go with the thread (ON DELETE CASCADE). */
    @Override
    public void delete(String threadId) {
        if (jdbc.sql("DELETE FROM threads WHERE id = ?").param(threadId).update() == 0) {
            throw ConversationHistory.notFound(threadId);
        }
    }

    @Override
    public List<StoredMessage> messages(String threadId) {
        return database.inTransaction(() -> history(threadId));
    }

    @Override
    public void append(String threadId, StoredMessage message) {
        database.inTransaction(() -> {
            touch(threadId);
            insert(threadId, message);
        });
    }

    @Override
    public List<ToolCall> pendingToolCalls(String threadId) {
        return ConversationHistory.pending(messages(threadId));
    }

    @Override
    public void closePendingToolCalls(String threadId, String result) {
        database.inTransaction(() -> {
            List<StoredMessage> history = history(threadId);
            String turnId = ConversationHistory.lastToolCallTurn(history);
            List<ToolCall> pending = ConversationHistory.pending(history);
            if (!pending.isEmpty()) {
                touch(threadId);
                pending.forEach(call -> insert(threadId, StoredMessage.toolResult(threadId, call, result, turnId, null)));
            }
        });
    }

    @Override
    public String toolCallAssistant(String threadId) {
        return ConversationHistory.toolCallAssistant(messages(threadId));
    }

    @Override
    public void appendToolResult(String threadId, ToolCall call, String result) {
        database.inTransaction(() -> {
            List<StoredMessage> history = history(threadId);
            touch(threadId);
            insert(threadId, StoredMessage.toolResult(threadId, call, result, ConversationHistory.lastToolCallTurn(history), null));
        });
    }

    @Override
    public int toolRoundsSinceLastUserMessage(String threadId) {
        return ConversationHistory.toolRoundsSinceLastUserMessage(messages(threadId));
    }

    private List<StoredMessage> history(String threadId) {
        if (!exists(threadId)) {
            throw ConversationHistory.notFound(threadId);
        }
        return jdbc.sql("SELECT body FROM messages WHERE thread_id = ? ORDER BY seq").param(threadId)
                .query((rs, n) -> Json.read(rs.getString(1), StoredMessage.class)).list();
    }

    private void touch(String threadId) {
        if (jdbc.sql("UPDATE threads SET updated_at = ? WHERE id = ?").params(now(), threadId).update() == 0) {
            throw ConversationHistory.notFound(threadId);
        }
    }

    private void insert(String threadId, StoredMessage message) {
        jdbc.sql("""
                INSERT INTO messages (id, thread_id, role, run_id, assistant_id, created_at, body)
                VALUES (?, ?, ?, ?, ?, ?, ?::json)
                """)
                .params(message.id(), threadId, message.role().name(), message.runId(), message.assistantId(),
                        message.createdAt(), Json.write(message))
                .update();
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }
}
