package ai.agent.common;

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
import java.util.Optional;

/**
 * JSON file written atomically (temp file + move), so it is never left truncated. An unreadable
 * file is kept: it is renamed to {@code <name>.invalid-<epoch>} on the next write.
 */
@Slf4j
public class AtomicJsonFile {

    private final JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private final Path file;
    private final Clock clock;
    private boolean invalidFilePending;

    public AtomicJsonFile(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    public boolean exists() {
        return Files.exists(file);
    }

    /** Empty when the file is missing or unreadable (logged; kept as backup on the next write). */
    public synchronized <T> Optional<T> read(TypeReference<T> type) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readValue(file.toFile(), type));
        } catch (RuntimeException e) {
            log.error("{} is unreadable; starting empty, the file will be kept as a backup on the next write", file, e);
            invalidFilePending = true;
            return Optional.empty();
        }
    }

    public synchronized void write(Object value) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            if (invalidFilePending) {
                Path backup = file.resolveSibling(file.getFileName() + ".invalid-" + clock.instant().getEpochSecond());
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                log.warn("Unreadable {} kept as {}", file, backup);
                invalidFilePending = false;
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), value);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                log.warn("Atomic move not supported for {}; replacing without it", file);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }
}
