package ai.agent.tools;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dynamic tools kept in memory and persisted to {@code <agent.data-dir>/tools.json}.
 * Writes go to a temp file and are moved over the catalog, so it is never left truncated.
 */
@Slf4j
public class FileToolRepository implements ToolRepository {

    public static final String FILE_NAME = "tools.json";
    private static final TypeReference<List<ToolDefinition>> LIST = new TypeReference<>() {};

    private final JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();
    private final Path file;
    private final Clock clock;
    private boolean invalidFilePending;

    public FileToolRepository(String dataDir, Clock clock) {
        this.file = Path.of(dataDir).resolve(FILE_NAME);
        this.clock = clock;
        load();
    }

    @Override
    public synchronized List<ToolDefinition> findAll() {
        return List.copyOf(tools.values());
    }

    @Override
    public synchronized Optional<ToolDefinition> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public synchronized void save(ToolDefinition tool) {
        tools.put(tool.name(), tool);
        persist();
    }

    @Override
    public synchronized boolean delete(String name) {
        if (tools.remove(name) == null) {
            return false;
        }
        persist();
        return true;
    }

    private void load() {
        if (!Files.exists(file)) {
            ToolRepository.seed().forEach(t -> tools.put(t.name(), t));
            persist();
            log.info("Tool catalog created at {} from the seed ({} tools)", file, tools.size());
            return;
        }
        try {
            mapper.readValue(file.toFile(), LIST).forEach(t -> tools.put(t.name(), t));
        } catch (RuntimeException e) {
            log.error("Tool catalog {} is unreadable; starting empty, the file will be kept as a backup on the next write", file, e);
            tools.clear();
            invalidFilePending = true;
        }
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            if (invalidFilePending) {
                Path backup = file.resolveSibling(FILE_NAME + ".invalid-" + clock.instant().getEpochSecond());
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                log.warn("Unreadable tool catalog kept as {}", backup);
                invalidFilePending = false;
            }
            Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
            mapper.writeValue(tmp.toFile(), new ArrayList<>(tools.values()));
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                log.warn("Atomic move not supported for {}; replacing without it", file);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write the tool catalog " + file, e);
        }
    }
}
