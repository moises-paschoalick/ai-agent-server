package ai.agent.persistence;

import ai.agent.tools.ToolDefinition;
import ai.agent.tools.ToolRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Dynamic tools in the {@code tools} table, in creation order (a replaced tool keeps its place).
 */
public class PostgresToolRepository implements ToolRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public PostgresToolRepository(PostgresDatabase database, Clock clock) {
        this.jdbc = database.jdbc();
        this.clock = clock;
    }

    @Override
    public List<ToolDefinition> findAll() {
        return jdbc.sql("SELECT body FROM tools ORDER BY seq")
                .query((rs, n) -> Json.read(rs.getString(1), ToolDefinition.class)).list();
    }

    @Override
    public Optional<ToolDefinition> find(String name) {
        return jdbc.sql("SELECT body FROM tools WHERE name = ?").param(name)
                .query((rs, n) -> Json.read(rs.getString(1), ToolDefinition.class)).optional();
    }

    @Override
    public void save(ToolDefinition tool) {
        long now = clock.instant().getEpochSecond();
        jdbc.sql("""
                INSERT INTO tools (name, created_at, updated_at, body) VALUES (?, ?, ?, ?::json)
                ON CONFLICT (name) DO UPDATE SET body = EXCLUDED.body, updated_at = EXCLUDED.updated_at
                """)
                .params(tool.name(), now, now, Json.write(tool))
                .update();
    }

    @Override
    public boolean delete(String name) {
        return jdbc.sql("DELETE FROM tools WHERE name = ?").param(name).update() > 0;
    }
}
