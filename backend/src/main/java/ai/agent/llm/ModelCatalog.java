package ai.agent.llm;

import ai.agent.configuration.AgentLlmProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Models offered to clients (GET /models): the default model, then the aliases, then the
 * models declared in {@code agent.llm.providers.<id>.models} of every active provider, without duplicates.
 */
@Component
public class ModelCatalog {

    private final AgentLlmProperties properties;
    private final ProviderRegistry registry;

    public ModelCatalog(AgentLlmProperties properties, ProviderRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    public List<String> models() {
        Set<String> models = new LinkedHashSet<>();
        models.add(properties.defaultModel());
        models.addAll(new TreeMap<>(properties.aliases()).keySet());
        new TreeMap<>(properties.providers()).forEach((id, provider) -> {
            if (registry.isActive(id)) {
                provider.models().forEach(model -> models.add(id + ":" + model));
            }
        });
        return List.copyOf(models);
    }
}
