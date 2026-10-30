package ai.agent.llm.provider;

import ai.agent.llm.CachingLlmProvider;
import ai.agent.llm.CallOptions;
import ai.agent.llm.LlmProvider;
import ai.agent.llm.LlmProviderFactory;
import ai.agent.llm.ProviderSettings;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;

/**
 * Any API that speaks the OpenAI chat format: OpenAI, OpenRouter, Grok, Groq, Ollama, vLLM...
 */
@Component
public class OpenAiCompatibleProviderFactory implements LlmProviderFactory {

    public static final String TYPE = "openai-compatible";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public LlmProvider create(String id, ProviderSettings settings) {
        return new CachingLlmProvider(id, TYPE, settings) {

            @Override
            protected ChatModel buildModel(String modelName) {
                return OpenAiChatModel.builder()
                        .baseUrl(settings.baseUrl())
                        .apiKey(settings.apiKey())
                        .modelName(modelName)
                        .timeout(settings.timeout())
                        .maxRetries(settings.maxRetries())
                        .build();
            }

            @Override
            protected StreamingChatModel buildStreamingModel(String modelName) {
                return OpenAiStreamingChatModel.builder()
                        .baseUrl(settings.baseUrl())
                        .apiKey(settings.apiKey())
                        .modelName(modelName)
                        .timeout(settings.timeout())
                        .build();
            }

            @Override
            public ChatRequestParameters parameters(CallOptions options) {
                return OpenAiChatRequestParameters.builder()
                        .temperature(options.temperature())
                        .topP(options.topP())
                        .maxOutputTokens(options.maxTokens())
                        .parallelToolCalls(options.oneToolAtATime() ? Boolean.FALSE : null)
                        .build();
            }
        };
    }
}
