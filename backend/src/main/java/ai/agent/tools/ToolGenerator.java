package ai.agent.tools;

import ai.agent.engine.LlmFailures;
import ai.agent.errors.ApiException;
import ai.agent.errors.LlmUpstreamException;
import ai.agent.errors.ValidationException;
import ai.agent.llm.CallOptions;
import ai.agent.llm.LlmGateway;
import ai.agent.llm.ModelRouter;
import ai.agent.services.ChatSettings;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the model for a tool definition (name, description, JSON Schema) from a plain description.
 * Nothing is saved: the result is a suggestion the user reviews. A reply that fails the catalog
 * rules gets one more attempt, with the reason sent back to the model.
 */
@Component
public class ToolGenerator {

    static final int MAX_DESCRIPTION_LENGTH = 2_000;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*(.*?)```", Pattern.DOTALL);

    private final LlmGateway gateway;
    private final ChatSettings settings;
    private final ModelRouter router;
    private final SystemToolExecutor systemTools;

    public ToolGenerator(LlmGateway gateway, ChatSettings settings, ModelRouter router, SystemToolExecutor systemTools) {
        this.gateway = gateway;
        this.settings = settings;
        this.router = router;
        this.systemTools = systemTools;
    }

    public record Request(String description, String model) {}

    public ToolDefinition generate(Request request) {
        String description = request.description() == null ? "" : request.description().strip();
        if (description.isEmpty()) {
            throw new ValidationException("description is required", "description");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException("description must have at most " + MAX_DESCRIPTION_LENGTH + " characters", "description");
        }
        String model = request.model() == null || request.model().isBlank() ? settings.model() : request.model().strip();
        router.resolve(model);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt()));
        messages.add(UserMessage.from(description));

        String reply = call(model, messages);
        try {
            return parse(reply);
        } catch (InvalidTool first) {
            messages.add(AiMessage.from(reply));
            messages.add(UserMessage.from("Invalid: " + first.getMessage() + ". Answer again with only the JSON object."));
            try {
                return parse(call(model, messages));
            } catch (InvalidTool second) {
                throw new LlmUpstreamException("The model did not return a valid tool: " + second.getMessage());
            }
        }
    }

    private String call(String model, List<ChatMessage> messages) {
        try {
            String text = gateway.chat(model, messages, CallOptions.defaults()).response().aiMessage().text();
            return text == null ? "" : text;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw LlmFailures.toApiException(LlmFailures.classify(e, null));
        }
    }

    /** The tool in the reply, checked with the catalog rules; InvalidTool with the reason otherwise. */
    ToolDefinition parse(String reply) {
        Map<String, Object> object;
        try {
            object = MAPPER.readValue(extractJson(reply), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (RuntimeException e) {
            throw new InvalidTool("the reply is not a JSON object");
        }
        if (object == null) {
            throw new InvalidTool("the reply is not a JSON object");
        }
        String name = object.get("name") instanceof String s ? s : null;
        try {
            SchemaValidator.validateName(name);
        } catch (ValidationException e) {
            throw new InvalidTool(e.getMessage());
        }
        if (systemTools.isSystem(name)) {
            throw new InvalidTool("'" + name + "' is the name of a system tool, choose another name");
        }
        Object description = object.get("description");
        if (description != null && !(description instanceof String)) {
            throw new InvalidTool("description must be a string");
        }
        if (!(object.get("parameters") instanceof Map<?, ?> raw)) {
            throw new InvalidTool("parameters must be a JSON Schema object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) raw;
        try {
            SchemaValidator.validateParameters(parameters);
        } catch (ValidationException e) {
            throw new InvalidTool(e.getMessage());
        }
        return new ToolDefinition(name, description == null ? "" : (String) description, parameters, false);
    }

    /** The JSON inside a ```json fence, or from the first '{' to the last '}'. */
    static String extractJson(String reply) {
        String text = reply.strip();
        Matcher fence = FENCE.matcher(text);
        if (fence.find()) {
            text = fence.group(1).strip();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private String systemPrompt() {
        String reserved = String.join(", ", systemTools.definitions().stream().map(ToolDefinition::name).toList());
        return """
                You design function tools for an AI assistant. The user describes what a tool should do; \
                answer with only one JSON object, no text around it:
                {"name": "...", "description": "...", "parameters": {...}}
                Rules:
                - name: snake_case, verb + object (for example get_order_status), 1 to 64 letters, digits, '_' or '-'.
                - Reserved names, never use them: %s.
                - description: when the assistant should call the tool and what it returns, in the same language \
                as the user's request.
                - parameters: a JSON Schema with "type": "object" and "properties"; every property has "type" and \
                "description"; use "enum" for closed sets of values; "required" lists only what the tool cannot guess.
                - Do not invent parameters the request does not need.
                """.formatted(reserved);
    }

    static final class InvalidTool extends RuntimeException {
        InvalidTool(String message) {
            super(message);
        }
    }
}
