package ai.agent.persistence;

import ai.agent.runs.Run;
import ai.agent.runs.RunStatus;
import ai.agent.runs.RunStore;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs in the {@code runs} table, one row per run replaced on every transition. Cancellation flags
 * stay in this process: the worker reads them without the database (one instance only).
 */
public class PostgresRunStore implements RunStore {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public PostgresRunStore(PostgresDatabase database, Clock clock) {
        this.jdbc = database.jdbc();
        this.clock = clock;
    }

    @Override
    public void save(Run run) {
        jdbc.sql("""
                INSERT INTO runs (id, thread_id, status, created_at, updated_at, body) VALUES (?, ?, ?, ?, ?, ?::json)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at, body = EXCLUDED.body
                """)
                .params(run.id(), run.threadId(), run.status().name(), run.createdAt(), clock.instant().getEpochSecond(),
                        Json.write(run))
                .update();
    }

    @Override
    public Optional<Run> find(String runId) {
        return jdbc.sql("SELECT body FROM runs WHERE id = ?").param(runId)
                .query((rs, n) -> Json.read(rs.getString(1), Run.class)).optional();
    }

    @Override
    public List<Run> forThread(String threadId) {
        return jdbc.sql("SELECT body FROM runs WHERE thread_id = ? ORDER BY seq").param(threadId)
                .query((rs, n) -> Json.read(rs.getString(1), Run.class)).list();
    }

    @Override
    public List<Run> all() {
        return jdbc.sql("SELECT body FROM runs ORDER BY seq")
                .query((rs, n) -> Json.read(rs.getString(1), Run.class)).list();
    }

    @Override
    public List<Run> withStatus(RunStatus... statuses) {
        List<String> names = Arrays.stream(statuses).map(Enum::name).toList();
        return jdbc.sql("SELECT body FROM runs WHERE status IN (:statuses) ORDER BY seq").param("statuses", names)
                .query((rs, n) -> Json.read(rs.getString(1), Run.class)).list();
    }

    @Override
    public AtomicBoolean cancelFlag(String runId) {
        return cancelFlags.computeIfAbsent(runId, id -> new AtomicBoolean());
    }
}
