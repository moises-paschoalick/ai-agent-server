package ai.agent.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

/**
 * One active provider. Vendor classes stay behind this interface (llm.provider package).
 */
public interface LlmProvider {

    String id();

    String type();

    ChatModel chatModel(String modelName);

    /** The same model answering in pieces; same parameters as {@link #chatModel}. */
    default StreamingChatModel streamingChatModel(String modelName) {
        throw new UnsupportedOperationException("Provider type '" + type() + "' does not stream.");
    }

    ChatRequestParameters parameters(CallOptions options);
}
