package ai.agent.llm;

import ai.agent.configuration.AgentLlmProperties;
import ai.agent.errors.ValidationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the providers declared in {@code agent.llm.providers} and validates the
 * configuration at startup: unknown type, bad default or alias stop the application.
 */
@Component
public class ProviderRegistry {

    private final Map<String, String> declaredTypes = new HashMap<>();
    private final Map<String, LlmProvider> active = new HashMap<>();
    private final ModelRef defaultModel;
    private final Map<String, AgentLlmProperties.Alias> aliases;

    /** "provider:model" split at the first ':'. */
    public record ModelRef(String providerId, String modelName) {}

    public ProviderRegistry(AgentLlmProperties properties, List<LlmProviderFactory> factories) {
        Map<String, LlmProviderFactory> byType = factories.stream()
                .collect(Collectors.toMap(LlmProviderFactory::type, Function.identity()));

        properties.providers().forEach((id, provider) -> {
            LlmProviderFactory factory = byType.get(provider.type());
            if (factory == null) {
                throw new IllegalStateException("Provider '" + id + "' has unknown type '" + provider.type()
                        + "'; expected one of " + byType.keySet());
            }
            declaredTypes.put(id, provider.type());
            if (provider.active()) {
                active.put(id, factory.create(id, settings(provider, properties)));
            }
        });

        this.defaultModel = parseDefault(properties.defaultModel());
        this.aliases = properties.aliases();
        aliases.forEach((name, alias) -> {
            ModelRef target = split(alias.model());
            if (target == null || !declaredTypes.containsKey(target.providerId())) {
                throw new IllegalStateException("Alias '" + name + "' must point to 'provider:model' with a declared provider, got '"
                        + alias.model() + "'");
            }
        });
    }

    public boolean isDeclared(String providerId) {
        return declaredTypes.containsKey(providerId);
    }

    /** Declared with an api-key. */
    public boolean isActive(String providerId) {
        return active.containsKey(providerId);
    }

    /** Active provider, or 400 when it is declared without a key. */
    public LlmProvider provider(String providerId) {
        LlmProvider provider = active.get(providerId);
        if (provider == null) {
            throw new ValidationException("Provider '" + providerId + "' is not configured.");
        }
        return provider;
    }

    public ModelRef defaultModel() {
        return defaultModel;
    }

    public Map<String, AgentLlmProperties.Alias> aliases() {
        return aliases;
    }

    /** Splits "provider:model" at the first ':' when the prefix is a declared provider; null otherwise. */
    public ModelRef split(String value) {
        if (value == null) {
            return null;
        }
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            return null;
        }
        String prefix = value.substring(0, colon);
        return isDeclared(prefix) ? new ModelRef(prefix, value.substring(colon + 1)) : null;
    }

    private ModelRef parseDefault(String value) {
        ModelRef ref = split(value);
        if (ref == null) {
            throw new IllegalStateException("agent.llm.default must be 'provider:model' with a declared provider, got '" + value + "'");
        }
        if (!active.containsKey(ref.providerId())) {
            throw new IllegalStateException("Default provider '" + ref.providerId() + "' has no api-key configured");
        }
        return ref;
    }

    private static ProviderSettings settings(AgentLlmProperties.Provider provider, AgentLlmProperties properties) {
        return new ProviderSettings(
                provider.apiKey(),
                provider.baseUrl(),
                provider.timeout() != null ? provider.timeout() : properties.timeout(),
                provider.maxRetries() != null ? provider.maxRetries() : properties.maxRetries(),
                provider.maxTokens());
    }
}
