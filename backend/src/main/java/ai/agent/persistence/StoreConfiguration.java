package ai.agent.persistence;

import ai.agent.assistants.AssistantRepository;
import ai.agent.audit.AuditLog;
import ai.agent.audit.AuditingAssistantRepository;
import ai.agent.audit.AuditingConversationStore;
import ai.agent.audit.AuditingRunStore;
import ai.agent.audit.AuditingToolRepository;
import ai.agent.audit.InMemoryAuditLog;
import ai.agent.assistants.FileAssistantRepository;
import ai.agent.memory.ConversationStore;
import ai.agent.memory.InMemoryConversationStore;
import ai.agent.runs.InMemoryRunStore;
import ai.agent.runs.RunStore;
import ai.agent.tools.FileToolRepository;
import ai.agent.tools.ToolRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.util.Locale;

/**
 * Where assistants, tools, conversations and runs are kept ({@code agent.store.type}):
 * {@code memory} (default) or {@code postgres}. Any other value stops the startup.
 */
@Configuration
public class StoreConfiguration {

    static final String TYPE = "agent.store.type";
    static final String DATABASE_URL = "agent.store.database-url";

    /** Runs before any bean is created, so a bad value fails with its own message, not a missing bean. */
    @Bean
    static BeanFactoryPostProcessor storeTypeCheck() {
        return new StoreTypeCheck();
    }

    /** Conversations and runs in memory; assistants and tools in the JSON files of {@code agent.data-dir}. */
    @Configuration
    @ConditionalOnProperty(name = TYPE, havingValue = "memory", matchIfMissing = true)
    static class Memory {

        @Bean
        AuditLog auditLog() {
            return new InMemoryAuditLog();
        }

        @Bean
        ConversationStore conversationStore(AuditLog audit) {
            return new AuditingConversationStore(new InMemoryConversationStore(), audit);
        }

        @Bean
        RunStore runStore(AuditLog audit) {
            return new AuditingRunStore(new InMemoryRunStore(), audit);
        }

        @Bean
        AssistantRepository assistantRepository(@Value("${agent.assistants.persist:true}") boolean persist,
                                                @Value("${agent.data-dir:./data}") String dataDir, Clock clock,
                                                AuditLog audit) {
            return new AuditingAssistantRepository(new FileAssistantRepository(persist, dataDir, clock), audit);
        }

        @Bean
        ToolRepository toolRepository(@Value("${agent.data-dir:./data}") String dataDir, Clock clock, AuditLog audit) {
            return new AuditingToolRepository(new FileToolRepository(dataDir, clock), audit);
        }
    }

    /** Everything in PostgreSQL ({@code DATABASE_URL}), migrated by Flyway at startup. */
    @Configuration
    @ConditionalOnProperty(name = TYPE, havingValue = "postgres")
    static class Postgres {

        @Bean(destroyMethod = "close")
        PostgresDatabase postgresDatabase(@Value("${" + DATABASE_URL + ":}") String databaseUrl) {
            return new PostgresDatabase(DatabaseUrl.parse(databaseUrl));
        }

        @Bean(destroyMethod = "close")
        PostgresAuditLog auditLog(PostgresDatabase database) {
            return new PostgresAuditLog(database);
        }

        @Bean
        ConversationStore conversationStore(PostgresDatabase database, Clock clock, AuditLog audit) {
            return new AuditingConversationStore(new PostgresConversationStore(database, clock), audit);
        }

        @Bean
        RunStore runStore(PostgresDatabase database, Clock clock, AuditLog audit) {
            return new AuditingRunStore(new PostgresRunStore(database, clock), audit);
        }

        /** Before the web server takes requests: import the files, then close what the last process left running. */
        @Bean
        SmartInitializingSingleton postgresStartup(PostgresDatabase database, RunStore runs, ConversationStore conversations,
                                                   @Value("${agent.data-dir:./data}") String dataDir, Clock clock) {
            return () -> {
                new FileImporter(database, dataDir, clock).importIfEmpty();
                new RunRecovery(runs, conversations, clock).recover();
            };
        }

        @Bean
        AssistantRepository assistantRepository(PostgresDatabase database, Clock clock, AuditLog audit) {
            return new AuditingAssistantRepository(new PostgresAssistantRepository(database, clock), audit);
        }

        @Bean
        ToolRepository toolRepository(PostgresDatabase database, Clock clock, AuditLog audit) {
            return new AuditingToolRepository(new PostgresToolRepository(database, clock), audit);
        }
    }

    static final class StoreTypeCheck implements BeanFactoryPostProcessor, EnvironmentAware {

        private Environment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            String type = environment.getProperty(TYPE, "memory").strip().toLowerCase(Locale.ROOT);
            switch (type) {
                case "memory" -> { }
                case "postgres" -> DatabaseUrl.parse(environment.getProperty(DATABASE_URL));
                default -> throw new IllegalStateException(
                        "AGENT_STORE must be 'memory' or 'postgres' (got '" + environment.getProperty(TYPE) + "').");
            }
        }
    }
}
