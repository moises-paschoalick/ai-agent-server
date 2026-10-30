package ai.agent.llm;

/**
 * Result of resolving a {@code model} value: which provider, which model name,
 * and the alias temperature when the value was an alias.
 */
public record ResolvedModel(String providerId, String modelName, Double aliasTemperature) {}
