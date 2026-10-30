package ai.agent.persistence;

import ai.agent.assistants.Assistant;
import ai.agent.assistants.AssistantRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Assistants in the {@code assistants} table, in creation order (a replaced assistant keeps its place).
 */
public class PostgresAssistantRepository implements AssistantRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public PostgresAssistantRepository(PostgresDatabase database, Clock clock) {
        this.jdbc = database.jdbc();
        this.clock = clock;
    }

    @Override
    public List<Assistant> findAll() {
        return jdbc.sql("SELECT body FROM assistants ORDER BY seq")
                .query((rs, n) -> Json.read(rs.getString(1), Assistant.class)).list();
    }

    @Override
    public Optional<Assistant> find(String id) {
        return jdbc.sql("SELECT body FROM assistants WHERE id = ?").param(id)
                .query((rs, n) -> Json.read(rs.getString(1), Assistant.class)).optional();
    }

    @Override
    public void save(Assistant assistant) {
        jdbc.sql("""
                INSERT INTO assistants (id, created_at, updated_at, body) VALUES (?, ?, ?, ?::json)
                ON CONFLICT (id) DO UPDATE SET body = EXCLUDED.body, updated_at = EXCLUDED.updated_at
                """)
                .params(assistant.id(), assistant.createdAt(), clock.instant().getEpochSecond(), Json.write(assistant))
                .update();
    }

    @Override
    public boolean delete(String id) {
        return jdbc.sql("DELETE FROM assistants WHERE id = ?").param(id).update() > 0;
    }
}
