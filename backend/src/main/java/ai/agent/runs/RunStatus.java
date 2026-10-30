package ai.agent.runs;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RunStatus {
    QUEUED, IN_PROGRESS, REQUIRES_ACTION, CANCELLING,
    COMPLETED, FAILED, CANCELLED, INCOMPLETE, EXPIRED;

    /** queued, in_progress, requires_action or cancelling: at most one per thread. */
    public boolean isActive() {
        return this == QUEUED || this == IN_PROGRESS || this == REQUIRES_ACTION || this == CANCELLING;
    }

    public boolean isTerminal() {
        return !isActive();
    }

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
