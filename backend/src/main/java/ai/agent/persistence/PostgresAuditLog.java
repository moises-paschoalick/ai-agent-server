package ai.agent.persistence;

import ai.agent.audit.AuditEvent;
import ai.agent.audit.AuditLog;
import ai.agent.audit.AuditQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Audit events in {@code audit_events}, written off the request path: {@link #record} only queues,
 * and a virtual thread writes batches of up to {@link #BATCH} events. A full queue or a failed
 * write drops the events with a warning; the client's call is never affected.
 */
@Slf4j
public class PostgresAuditLog implements AuditLog, AutoCloseable {

    static final int QUEUE_CAPACITY = 10_000;
    static final int BATCH = 100;
    private static final Duration WAIT = Duration.ofMillis(200);
    private static final TypeReference<Map<String, Object>> DETAIL = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final BlockingQueue<AuditEvent> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    /** Queued or being written: flush waits for zero. */
    private final AtomicInteger pending = new AtomicInteger();
    private final Thread writer;
    private volatile boolean running = true;

    public PostgresAuditLog(PostgresDatabase database) {
        this.jdbc = database.jdbc();
        this.writer = Thread.ofVirtual().name("audit-writer").start(this::drain);
    }

    @Override
    public void record(AuditEvent event) {
        pending.incrementAndGet();
        if (!queue.offer(event)) {
            pending.decrementAndGet();
            log.warn("Audit queue is full; dropped a {} event", event.type());
        }
    }

    /** Waits (up to 2 s) for queued events, so a query right after a call sees them. */
    @Override
    public List<AuditEvent> query(AuditQuery query) {
        flush(Duration.ofSeconds(2));
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_events WHERE true");
        Map<String, Object> params = new LinkedHashMap<>();
        filter(sql, params, "thread_id", query.threadId());
        filter(sql, params, "run_id", query.runId());
        filter(sql, params, "assistant_id", query.assistantId());
        filter(sql, params, "type", query.type());
        if (query.since() != null) {
            sql.append(" AND at >= :since");
            params.put("since", Timestamp.from(query.since()));
        }
        sql.append(" ORDER BY at DESC, id DESC LIMIT :limit");
        params.put("limit", query.limit());
        return jdbc.sql(sql.toString()).params(params).query((rs, n) -> read(rs)).list();
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        flush(Duration.ofSeconds(2));
        return jdbc.sql("DELETE FROM audit_events WHERE at < ?").param(Timestamp.from(cutoff)).update();
    }

    /** True when every queued event was written (or dropped) before {@code timeout}. */
    public boolean flush(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (pending.get() > 0) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        flush(Duration.ofSeconds(2));
        running = false;
        writer.interrupt();
    }

    private void drain() {
        List<AuditEvent> batch = new ArrayList<>(BATCH);
        while (running) {
            try {
                AuditEvent first = queue.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, BATCH - 1);
                write(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                pending.addAndGet(-batch.size());
                batch.clear();
            }
        }
    }

    private void write(List<AuditEvent> batch) {
        try {
            for (AuditEvent e : batch) {
                jdbc.sql("""
                        INSERT INTO audit_events (at, type, thread_id, run_id, assistant_id, api_key_hash, model, latency_ms,
                            ttft_ms, prompt_tokens, completion_tokens, total_tokens, tool_name, outcome, error_code, detail)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """)
                        .params(Timestamp.from(e.at()), e.type(), e.threadId(), e.runId(), e.assistantId(), e.apiKeyHash(),
                                e.model(), e.latencyMs(), e.ttftMs(), e.promptTokens(), e.completionTokens(), e.totalTokens(),
                                e.toolName(), e.outcome(), e.errorCode(), e.detail().isEmpty() ? null : Json.write(e.detail()))
                        .update();
            }
        } catch (RuntimeException ex) {
            log.warn("Could not write {} audit event(s): {}", batch.size(), ex.getMessage());
        }
    }

    private static void filter(StringBuilder sql, Map<String, Object> params, String column, String value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = :").append(column);
            params.put(column, value);
        }
    }

    private static AuditEvent read(ResultSet rs) throws SQLException {
        String detail = rs.getString("detail");
        return new AuditEvent(rs.getLong("id"), rs.getTimestamp("at").toInstant(), rs.getString("type"),
                rs.getString("thread_id"), rs.getString("run_id"), rs.getString("assistant_id"), rs.getString("api_key_hash"),
                rs.getString("model"), (Long) rs.getObject("latency_ms"), (Long) rs.getObject("ttft_ms"),
                (Integer) rs.getObject("prompt_tokens"),
                (Integer) rs.getObject("completion_tokens"), (Integer) rs.getObject("total_tokens"),
                rs.getString("tool_name"), rs.getString("outcome"), rs.getString("error_code"),
                detail == null ? Map.of() : Json.read(detail, DETAIL));
    }
}
