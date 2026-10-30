package ai.agent.llm.provider;

import ai.agent.llm.CachingLlmProvider;
import ai.agent.llm.CallOptions;
import ai.agent.llm.LlmProvider;
import ai.agent.llm.LlmProviderFactory;
import ai.agent.llm.ProviderSettings;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.springframework.stereotype.Component;

@Component
public class AnthropicProviderFactory implements LlmProviderFactory {

    public static final String TYPE = "anthropic";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public LlmProvider create(String id, ProviderSettings settings) {
        return new CachingLlmProvider(id, TYPE, settings) {

            @Override
            protected ChatModel buildModel(String modelName) {
                AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                        .apiKey(settings.apiKey())
                        .modelName(modelName)
                        .timeout(settings.timeout())
                        .maxRetries(settings.maxRetries())
                        .maxTokens(settings.maxTokens());
                if (settings.baseUrl() != null) {
                    builder.baseUrl(settings.baseUrl());
                }
                return builder.build();
            }

            @Override
            protected StreamingChatModel buildStreamingModel(String modelName) {
                AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder builder = AnthropicStreamingChatModel.builder()
                        .apiKey(settings.apiKey())
                        .modelName(modelName)
                        .timeout(settings.timeout())
                        .maxTokens(settings.maxTokens());
                if (settings.baseUrl() != null) {
                    builder.baseUrl(settings.baseUrl());
                }
                return builder.build();
            }

            @Override
            public ChatRequestParameters parameters(CallOptions options) {
                return AnthropicChatRequestParameters.builder()
                        .temperature(options.temperature())
                        .topP(options.topP())
                        .maxOutputTokens(options.maxTokens())
                        .disableParallelToolUse(options.oneToolAtATime() ? Boolean.TRUE : null)
                        .build();
            }
        };
    }
}
