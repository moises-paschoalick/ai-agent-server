package ai.agent.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/** Once a day, deletes audit events older than {@code agent.audit.retention} (AGENT_AUDIT_RETENTION, 90 days). */
@Slf4j
@Component
public class AuditRetentionJob {

    private final AuditLog audit;
    private final Clock clock;
    private final Duration retention;

    public AuditRetentionJob(AuditLog audit, Clock clock, @Value("${agent.audit.retention:90d}") Duration retention) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalStateException("AGENT_AUDIT_RETENTION must be positive (got " + retention + ").");
        }
        this.audit = audit;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${agent.audit.retention-cron:0 30 3 * * *}")
    public int purge() {
        int deleted = audit.deleteOlderThan(clock.instant().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} audit event(s) older than {}", deleted, retention);
        }
        return deleted;
    }
}
