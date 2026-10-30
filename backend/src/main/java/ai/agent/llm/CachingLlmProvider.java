package ai.agent.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one client per model name (and one streaming client), created on first use.
 */
public abstract class CachingLlmProvider implements LlmProvider {

    private final String id;
    private final String type;
    protected final ProviderSettings settings;
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingModels = new ConcurrentHashMap<>();

    protected CachingLlmProvider(String id, String type, ProviderSettings settings) {
        this.id = id;
        this.type = type;
        this.settings = settings;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public ChatModel chatModel(String modelName) {
        return models.computeIfAbsent(modelName, this::buildModel);
    }

    @Override
    public StreamingChatModel streamingChatModel(String modelName) {
        return streamingModels.computeIfAbsent(modelName, this::buildStreamingModel);
    }

    protected abstract ChatModel buildModel(String modelName);

    /** Streaming clients have no retries: a retry after the first piece would repeat text already sent. */
    protected abstract StreamingChatModel buildStreamingModel(String modelName);
}
