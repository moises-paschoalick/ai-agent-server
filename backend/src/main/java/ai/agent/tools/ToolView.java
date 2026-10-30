package ai.agent.tools;

import java.util.Map;

/**
 * API representation of a tool, with its kind.
 */
public record ToolView(String name, String description, Map<String, Object> parameters, boolean strict, ToolKind kind) {

    static ToolView of(ToolDefinition tool, ToolKind kind) {
        return new ToolView(tool.name(), tool.description(), tool.parameters(), tool.strict(), kind);
    }
}
