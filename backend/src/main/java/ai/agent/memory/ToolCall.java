package ai.agent.memory;

/**
 * A tool call requested by the model; {@code argumentsJson} is kept exactly as the model sent it.
 */
public record ToolCall(String id, String name, String argumentsJson) {}
