package ai.agent.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * The PostgreSQL connection pool, migrated by Flyway on creation. Fails fast, with the masked
 * URL, when the database does not answer.
 */
@Slf4j
public class PostgresDatabase implements AutoCloseable {

    static final long CONNECT_TIMEOUT_MS = 10_000;

    private final HikariDataSource dataSource;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public PostgresDatabase(DatabaseUrl url) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("agent-server");
        config.setJdbcUrl(url.jdbcUrl());
        if (url.username() != null) {
            config.setUsername(url.username());
        }
        if (url.password() != null) {
            config.setPassword(url.password());
        }
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(CONNECT_TIMEOUT_MS);
        config.setInitializationFailTimeout(CONNECT_TIMEOUT_MS);
        try {
            this.dataSource = new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not connect to the database at DATABASE_URL=" + url.masked()
                    + " (" + rootMessage(e, url) + ").");
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        this.jdbc = JdbcClient.create(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        logVectorExtension();
        log.info("Storage: PostgreSQL at {}", url.masked());
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    public void inTransaction(Runnable work) {
        transactions.executeWithoutResult(status -> work.run());
    }

    @Override
    public void close() {
        dataSource.close();
    }

    /** pgvector (RAG, a later phase) is only checked here; no migration creates it yet. */
    private void logVectorExtension() {
        boolean available = jdbc.sql("SELECT count(*) FROM pg_available_extensions WHERE name = 'vector'")
                .query(Long.class).single() > 0;
        if (available) {
            log.info("PostgreSQL extension 'vector' is available (pgvector)");
        } else {
            log.info("PostgreSQL extension 'vector' is not available; a pgvector image is needed for RAG");
        }
    }

    /** The driver's reason, without anything that could carry the password. */
    private static String rootMessage(Throwable e, DatabaseUrl url) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return url.password() == null || url.password().isEmpty() ? message : message.replace(url.password(), "***");
    }
}
