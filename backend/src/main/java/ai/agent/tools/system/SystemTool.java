package ai.agent.tools.system;

import ai.agent.tools.ToolDefinition;

/**
 * A tool run by the server itself: deterministic, fast, without side effects or access to other
 * systems. A failure is reported to the model as the result text, so {@link #execute} may throw.
 */
public interface SystemTool {

    ToolDefinition definition();

    default String name() {
        return definition().name();
    }

    /** Result sent back to the model; {@code argumentsJson} is the JSON object the model produced. */
    String execute(String argumentsJson);
}
