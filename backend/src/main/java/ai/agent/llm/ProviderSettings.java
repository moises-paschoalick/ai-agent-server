package ai.agent.llm;

import java.time.Duration;

/**
 * Connection settings of one provider, with the global defaults already applied.
 */
public record ProviderSettings(String apiKey, String baseUrl, Duration timeout, Integer maxRetries, Integer maxTokens) {}
