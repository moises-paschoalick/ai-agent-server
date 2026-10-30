package ai.agent.assistants;

import ai.agent.common.Ids;
import ai.agent.common.ListQuery;
import ai.agent.common.MetadataRules;
import ai.agent.common.Page;
import ai.agent.errors.NotFoundException;
import ai.agent.errors.ValidationException;
import ai.agent.llm.ModelRouter;
import ai.agent.tools.SchemaValidator;
import ai.agent.tools.SystemToolExecutor;
import ai.agent.tools.ToolDefinition;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Assistant rules: accepts an OpenAI export as is (id, object, created_at and unknown fields are
 * ignored), validates model, tools (same rules as /tools), ranges and metadata. Field errors carry
 * the field in {@code param}.
 */
@Service
public class AssistantService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRINGS = new TypeReference<>() {};

    private final AssistantRepository repository;
    private final ModelRouter router;
    private final SystemToolExecutor systemTools;

    public AssistantService(AssistantRepository repository, ModelRouter router, SystemToolExecutor systemTools) {
        this.repository = repository;
        this.router = router;
        this.systemTools = systemTools;
    }

    public Assistant create(JsonNode body) {
        JsonNode json = body == null ? MAPPER.createObjectNode() : body;
        Assistant assistant = validated(new Assistant(
                Ids.newId("asst_"),
                Instant.now().getEpochSecond(),
                text(json, "name"),
                text(json, "description"),
                text(json, "model"),
                text(json, "instructions"),
                parseTools(json),
                metadata(json),
                number(json, "temperature"),
                number(json, "top_p"),
                nullIfMissing(json.get("response_format"))));
        repository.save(assistant);
        return assistant;
    }

    /** Changes only the fields present in the body. */
    public Assistant update(String id, JsonNode body) {
        Assistant current = get(id);
        JsonNode json = body == null ? MAPPER.createObjectNode() : body;
        Assistant updated = validated(new Assistant(
                current.id(),
                current.createdAt(),
                json.has("name") ? text(json, "name") : current.name(),
                json.has("description") ? text(json, "description") : current.description(),
                json.has("model") ? text(json, "model") : current.model(),
                json.has("instructions") ? text(json, "instructions") : current.instructions(),
                json.has("tools") ? parseTools(json) : current.tools(),
                json.has("metadata") ? metadata(json) : current.metadata(),
                json.has("temperature") ? number(json, "temperature") : current.temperature(),
                json.has("top_p") ? number(json, "top_p") : current.topP(),
                json.has("response_format") ? nullIfMissing(json.get("response_format")) : current.responseFormat()));
        repository.save(updated);
        return updated;
    }

    public Assistant get(String id) {
        return repository.find(id)
                .orElseThrow(() -> new NotFoundException("No assistant found with id '" + id + "'."));
    }

    public Page<Assistant> list(ListQuery query) {
        return Page.of(repository.findAll(), Assistant::id, query);
    }

    public void delete(String id) {
        if (!repository.delete(id)) {
            throw new NotFoundException("No assistant found with id '" + id + "'.");
        }
    }

    private Assistant validated(Assistant assistant) {
        if (assistant.model() == null || assistant.model().isBlank()) {
            throw new ValidationException("model is required", "model");
        }
        try {
            router.resolve(assistant.model());
        } catch (ValidationException e) {
            throw new ValidationException(e.getMessage(), "model");
        }
        range("temperature", assistant.temperature(), 2);
        range("top_p", assistant.topP(), 1);
        return assistant;
    }

    /**
     * Parses and validates the "tools" field of an assistant or run (same rules as /tools). A
     * function named like a system tool declares it: description and parameters are kept as sent
     * but not validated, since the server's definition is the one the model gets.
     */
    public List<AssistantTool> parseTools(JsonNode json) {
        JsonNode tools = json.get("tools");
        if (tools == null || tools.isNull()) {
            return List.of();
        }
        if (!tools.isArray()) {
            throw new ValidationException("tools must be a list", "tools");
        }
        List<AssistantTool> result = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            JsonNode tool = tools.get(i);
            String type = tool.has("type") ? tool.get("type").asString() : null;
            if ("retrieval".equals(type)) {
                continue;
            }
            if (!"function".equals(type)) {
                throw new ValidationException("Tool type '" + type + "' is not supported.", "tools[" + i + "].type");
            }
            result.add(function(tool.get("function"), "tools[" + i + "].function", systemTools::isSystem));
        }
        return result;
    }

    private static AssistantTool function(JsonNode function, String param, Predicate<String> isSystem) {
        if (function == null || !function.isObject()) {
            throw new ValidationException("function is required for tools of type 'function'", param);
        }
        String name = text(function, "name");
        boolean declaration = isSystem.test(name);
        boolean hasParameters = function.has("parameters") && !function.get("parameters").isNull();
        Map<String, Object> parameters = hasParameters
                ? MAPPER.convertValue(function.get("parameters"), OBJECT)
                : declaration ? null : ToolDefinition.emptyParameters();
        try {
            SchemaValidator.validateName(name);
            if (!declaration) {
                SchemaValidator.validateParameters(parameters);
            }
        } catch (ValidationException e) {
            throw new ValidationException(e.getMessage(), param);
        }
        JsonNode strict = function.get("strict");
        return AssistantTool.function(name, text(function, "description"), parameters,
                strict == null || strict.isNull() ? null : strict.asBoolean());
    }

    private static Map<String, String> metadata(JsonNode json) {
        JsonNode metadata = json.get("metadata");
        if (metadata == null || metadata.isNull()) {
            return Map.of();
        }
        if (!metadata.isObject()) {
            throw new ValidationException("metadata must be an object", "metadata");
        }
        return MetadataRules.validated(MAPPER.convertValue(metadata, STRINGS));
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new ValidationException(field + " must be a string", field);
        }
        return value.stringValue();
    }

    private static Double number(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new ValidationException(field + " must be a number", field);
        }
        return value.doubleValue();
    }

    private static void range(String field, Double value, double max) {
        if (value != null && (value < 0 || value > max)) {
            throw new ValidationException(field + " must be between 0 and " + (int) max, field);
        }
    }

    private static JsonNode nullIfMissing(JsonNode value) {
        return value == null || value.isNull() ? null : value;
    }
}
