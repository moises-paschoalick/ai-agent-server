package ai.agent.llm;

import ai.agent.configuration.AgentLlmProperties;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Single entry point for model calls: resolves the provider from {@code model}
 * and sends the request with that provider's parameters.
 */
@Component
public class LlmGateway {

    private final ModelRouter router;
    private final ProviderRegistry registry;
    private final Double defaultTemperature;

    public LlmGateway(ModelRouter router, ProviderRegistry registry, AgentLlmProperties properties) {
        this.router = router;
        this.registry = registry;
        this.defaultTemperature = properties.temperature();
    }

    public LlmResult chat(String model, List<ChatMessage> messages, CallOptions options) {
        return chat(model, messages, List.of(), options);
    }

    /** Sends {@code tools} too; asks the provider for one tool call at a time when there are tools. */
    public LlmResult chat(String model, List<ChatMessage> messages, List<ToolSpecification> tools, CallOptions options) {
        Prepared prepared = prepare(model, messages, tools, options);
        return new LlmResult(prepared.resolved().providerId(), prepared.resolved().modelName(),
                prepared.provider().chatModel(prepared.resolved().modelName()).chat(prepared.request()));
    }

    /**
     * Same call as {@link #chat}, answered in pieces: {@code onDelta} gets each piece of text as it
     * arrives, on the provider's thread; tool calls only come complete, in the result. Blocks until
     * the model finishes and throws what {@code chat} would throw; {@link StreamCancelledException}
     * when {@code cancel} is set or {@code onDelta} fails (the reader is gone), after stopping the
     * generation.
     */
    public LlmResult stream(String model, List<ChatMessage> messages, List<ToolSpecification> tools, CallOptions options,
                            Consumer<String> onDelta, CancelSignal cancel) {
        Prepared prepared = prepare(model, messages, tools, options);
        StreamingChatModel streaming = prepared.provider().streamingChatModel(prepared.resolved().modelName());
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streaming.chat(prepared.request(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(PartialResponse partial, PartialResponseContext context) {
                if (done.isDone()) {
                    return;
                }
                try {
                    if (cancel.isCancelled()) {
                        throw new StreamCancelledException();
                    }
                    if (partial.text() != null && !partial.text().isEmpty()) {
                        onDelta.accept(partial.text());
                    }
                } catch (RuntimeException e) {
                    context.streamingHandle().cancel();
                    cancel.cancel();
                    done.completeExceptionally(new StreamCancelledException());
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                done.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });
        ChatResponse response;
        try {
            response = done.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
        if (cancel.isCancelled()) {
            throw new StreamCancelledException();
        }
        return new LlmResult(prepared.resolved().providerId(), prepared.resolved().modelName(), response);
    }

    private record Prepared(ResolvedModel resolved, LlmProvider provider, ChatRequest request) {}

    private Prepared prepare(String model, List<ChatMessage> messages, List<ToolSpecification> tools, CallOptions options) {
        ResolvedModel resolved = router.resolve(model);
        LlmProvider provider = registry.provider(resolved.providerId());

        CallOptions effective = new CallOptions(
                firstNonNull(options.temperature(), resolved.aliasTemperature(), defaultTemperature),
                options.topP(), options.maxTokens(), options.oneToolAtATime() || !tools.isEmpty());

        ChatRequest.Builder request = ChatRequest.builder()
                .messages(messages)
                .parameters(provider.parameters(effective));
        if (!tools.isEmpty()) {
            request.toolSpecifications(tools);
        }
        return new Prepared(resolved, provider, request.build());
    }

    private static Double firstNonNull(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
