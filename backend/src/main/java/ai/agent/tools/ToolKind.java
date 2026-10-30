package ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ToolKind {
    DYNAMIC, SYSTEM;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
