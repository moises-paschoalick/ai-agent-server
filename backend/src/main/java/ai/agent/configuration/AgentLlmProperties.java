package ai.agent.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LLM providers, default model and aliases ({@code agent.llm.*}).
 */
@ConfigurationProperties(prefix = "agent.llm")
public record AgentLlmProperties(
        @Name("default") String defaultModel,
        @DefaultValue("0.3") Double temperature,
        @DefaultValue("60s") Duration timeout,
        @DefaultValue("2") Integer maxRetries,
        Map<String, Provider> providers,
        Map<String, Alias> aliases) {

    public AgentLlmProperties {
        providers = providers == null ? Map.of() : Map.copyOf(providers);
        aliases = aliases == null ? Map.of() : Map.copyOf(aliases);
    }

    /**
     * One declared provider. Empty {@code apiKey} keeps it declared but inactive;
     * {@code models} only feeds the model list shown to clients (GET /models).
     */
    public record Provider(String type, String apiKey, String baseUrl,
                           Duration timeout, Integer maxRetries, Integer maxTokens, List<String> models) {

        public Provider {
            models = models == null ? List.of()
                    : models.stream().map(String::strip).filter(m -> !m.isEmpty()).toList();
        }

        public boolean active() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * Logical model name: {@code model} is "provider:model"; {@code temperature} is optional.
     */
    public record Alias(String model, Double temperature) {}
}
