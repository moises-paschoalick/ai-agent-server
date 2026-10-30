package ai.agent.llm;

import ai.agent.configuration.AgentLlmProperties;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@code model} field: empty -> default; alias -> its target;
 * "provider:model" when the prefix is a declared provider; anything else -> default provider.
 */
@Component
public class ModelRouter {

    private final ProviderRegistry registry;

    public ModelRouter(ProviderRegistry registry) {
        this.registry = registry;
    }

    public ResolvedModel resolve(String model) {
        if (model == null || model.isBlank()) {
            return checked(registry.defaultModel(), null);
        }
        String value = model.strip();

        AgentLlmProperties.Alias alias = registry.aliases().get(value);
        if (alias != null) {
            return checked(registry.split(alias.model()), alias.temperature());
        }

        ProviderRegistry.ModelRef ref = registry.split(value);
        if (ref != null) {
            return checked(ref, null);
        }
        return checked(new ProviderRegistry.ModelRef(registry.defaultModel().providerId(), value), null);
    }

    private ResolvedModel checked(ProviderRegistry.ModelRef ref, Double aliasTemperature) {
        registry.provider(ref.providerId());
        return new ResolvedModel(ref.providerId(), ref.modelName(), aliasTemperature);
    }
}
