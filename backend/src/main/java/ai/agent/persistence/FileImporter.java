package ai.agent.persistence;

import ai.agent.assistants.Assistant;
import ai.agent.assistants.FileAssistantRepository;
import ai.agent.tools.FileToolRepository;
import ai.agent.tools.ToolDefinition;
import ai.agent.tools.ToolRepository;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * First start on an empty database: brings over {@code assistants.json} and {@code tools.json} from
 * {@code agent.data-dir} with the same ids, so the assistant_id of existing flows keeps working.
 * Without tools.json the catalog starts with the seed. A table that already has rows is left alone.
 */
@Slf4j
public class FileImporter {

    private final PostgresDatabase database;
    private final Path dataDir;
    private final Clock clock;

    public FileImporter(PostgresDatabase database, String dataDir, Clock clock) {
        this.database = database;
        this.dataDir = Path.of(dataDir);
        this.clock = clock;
    }

    public void importIfEmpty() {
        database.inTransaction(() -> {
            if (isEmpty("assistants")) {
                importAssistants();
            }
            if (isEmpty("tools")) {
                importTools();
            }
        });
    }

    private void importAssistants() {
        Path file = dataDir.resolve(FileAssistantRepository.FILE_NAME);
        if (!Files.exists(file)) {
            return;
        }
        List<Assistant> assistants = new FileAssistantRepository(true, dataDir.toString(), clock).findAll();
        PostgresAssistantRepository target = new PostgresAssistantRepository(database, clock);
        assistants.forEach(target::save);
        log.info("Imported {} assistant(s) from {}", assistants.size(), file);
    }

    private void importTools() {
        Path file = dataDir.resolve(FileToolRepository.FILE_NAME);
        List<ToolDefinition> tools = Files.exists(file)
                ? new FileToolRepository(dataDir.toString(), clock).findAll()
                : ToolRepository.seed();
        PostgresToolRepository target = new PostgresToolRepository(database, clock);
        tools.forEach(target::save);
        log.info("Imported {} tool(s) from {}", tools.size(), Files.exists(file) ? file : "the seed");
    }

    private boolean isEmpty(String table) {
        return database.jdbc().sql("SELECT NOT EXISTS (SELECT 1 FROM " + table + ")").query(Boolean.class).single();
    }
}
