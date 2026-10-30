package ai.agent.assistants;

import ai.agent.common.AtomicJsonFile;
import tools.jackson.core.type.TypeReference;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assistants in memory, in creation order; persisted to {@code <agent.data-dir>/assistants.json}
 * when {@code agent.assistants.persist} is true (default). With false, no file is read or written.
 */
public class FileAssistantRepository implements AssistantRepository {

    public static final String FILE_NAME = "assistants.json";
    private static final TypeReference<List<Assistant>> LIST = new TypeReference<>() {};

    private final Map<String, Assistant> assistants = new LinkedHashMap<>();
    private final AtomicJsonFile file;

    public FileAssistantRepository(boolean persist, String dataDir, Clock clock) {
        this.file = persist ? new AtomicJsonFile(Path.of(dataDir).resolve(FILE_NAME), clock) : null;
        if (file != null) {
            file.read(LIST).ifPresent(list -> list.forEach(a -> assistants.put(a.id(), a)));
        }
    }

    @Override
    public synchronized List<Assistant> findAll() {
        return List.copyOf(assistants.values());
    }

    @Override
    public synchronized Optional<Assistant> find(String id) {
        return Optional.ofNullable(assistants.get(id));
    }

    @Override
    public synchronized void save(Assistant assistant) {
        assistants.put(assistant.id(), assistant);
        persist();
    }

    @Override
    public synchronized boolean delete(String id) {
        if (assistants.remove(id) == null) {
            return false;
        }
        persist();
        return true;
    }

    private void persist() {
        if (file != null) {
            file.write(new ArrayList<>(assistants.values()));
        }
    }
}
