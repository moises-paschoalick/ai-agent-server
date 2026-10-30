package ai.agent.assistants;

import java.util.List;
import java.util.Optional;

/**
 * Assistants in creation order: in a JSON file or in PostgreSQL ({@code agent.store.type}).
 */
public interface AssistantRepository {

    /** Oldest first. */
    List<Assistant> findAll();

    Optional<Assistant> find(String id);

    /** Inserts or replaces by id. */
    void save(Assistant assistant);

    /** False when there is no assistant with this id. */
    boolean delete(String id);
}
