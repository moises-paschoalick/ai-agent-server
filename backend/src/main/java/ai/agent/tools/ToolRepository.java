package ai.agent.tools;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * Dynamic tools in creation order: in a JSON file or in PostgreSQL ({@code agent.store.type}).
 * An empty catalog starts with the seed (the classpath {@code tools.json}).
 */
public interface ToolRepository {

    List<ToolDefinition> findAll();

    Optional<ToolDefinition> find(String name);

    /** Inserts or replaces by name. */
    void save(ToolDefinition tool);

    /** False when there is no tool with this name. */
    boolean delete(String name);

    /** The fictitious tools a new catalog starts with. */
    static List<ToolDefinition> seed() {
        try (InputStream is = ToolRepository.class.getClassLoader().getResourceAsStream("tools.json")) {
            return is == null ? List.of() : JsonMapper.builder().build().readValue(is, new TypeReference<List<ToolDefinition>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the tool seed", e);
        }
    }
}
