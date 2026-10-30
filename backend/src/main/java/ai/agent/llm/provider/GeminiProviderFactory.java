package ai.agent.llm.provider;

import ai.agent.llm.CachingLlmProvider;
import ai.agent.llm.CallOptions;
import ai.agent.llm.LlmProvider;
import ai.agent.llm.LlmProviderFactory;
import ai.agent.llm.ProviderSettings;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import org.springframework.stereotype.Component;

/**
 * Gemini has no parallel tool calls switch; the engine handles several calls per turn.
 */
@Component
public class GeminiProviderFactory implements LlmProviderFactory {

    public static final String TYPE = "gemini";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public LlmProvider create(String id, ProviderSettings settings) {
        return new CachingLlmProvider(id, TYPE, settings) {

            @Override
            protected ChatModel buildModel(String modelName) {
                GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
                        .apiKey(settings.apiKey())
                        .modelName(modelName)
                        .timeout(settings.timeout())
                        .maxRetries(settings.maxRetries())
                        .maxOutputTokens(settings.maxTokens());
                if (settings.baseUrl() != null) {
                    builder.baseUrl(settings.baseUrl());
                }
                return builder.build();
            }

            @Override
            protected StreamingChatModel buildStreamingModel(String modelName) {
                GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder = GoogleAiGeminiStreamingChatModel.builder()
                        .apiKey(settings.apiKey())
                        .modelName(modelName)
                        .timeout(settings.timeout())
                        .maxOutputTokens(settings.maxTokens());
                if (settings.baseUrl() != null) {
                    builder.baseUrl(settings.baseUrl());
                }
                return builder.build();
            }

            @Override
            public ChatRequestParameters parameters(CallOptions options) {
                return ChatRequestParameters.builder()
                        .temperature(options.temperature())
                        .topP(options.topP())
                        .maxOutputTokens(options.maxTokens())
                        .build();
            }
        };
    }
}
