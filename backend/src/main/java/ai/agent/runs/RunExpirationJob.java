package ai.agent.runs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically expires runs left in requires_action past their expires_at. */
@Component
public class RunExpirationJob {

    private final RunService runs;

    public RunExpirationJob(RunService runs) {
        this.runs = runs;
    }

    @Scheduled(fixedDelayString = "${agent.runs.expiration-check:30s}")
    public void expireStale() {
        runs.expire();
    }
}
