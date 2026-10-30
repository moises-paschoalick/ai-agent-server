package ai.agent.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts a validated JSON Schema (as a Map) into LangChain4j schema elements without losing
 * enum, nested objects, arrays, nullable types or descriptions. Unknown constructs are sent raw.
 */
public final class SchemaConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SchemaConverter() {
    }

    public static ToolSpecification toSpecification(ToolDefinition tool) {
        return ToolSpecification.builder()
                .name(tool.name())
                .description(tool.description())
                .parameters(object(tool.parameters()))
                .build();
    }

    static JsonSchemaElement element(Map<?, ?> schema) {
        if (schema.containsKey("$ref") || schema.containsKey("oneOf") || schema.containsKey("allOf")) {
            return raw(schema);
        }
        String description = (String) schema.get("description");

        if (schema.get("anyOf") instanceof List<?> branches) {
            return JsonAnyOfSchema.builder()
                    .description(description)
                    .anyOf(branches.stream().map(b -> element((Map<?, ?>) b)).toList())
                    .build();
        }

        Object type = schema.get("type");
        if (type instanceof List<?> types) {
            List<?> nonNull = types.stream().filter(t -> !"null".equals(t)).toList();
            if (nonNull.size() == 1 && types.contains("null")) {
                JsonSchemaElement value = single((String) nonNull.getFirst(), schema, null);
                return JsonAnyOfSchema.builder().description(description).anyOf(value, new JsonNullSchema()).build();
            }
            return raw(schema);
        }
        if (type instanceof String name) {
            return single(name, schema, description);
        }
        if (schema.get("enum") instanceof List<?>) {
            return enumOrRaw(schema, description);
        }
        return raw(schema);
    }

    private static JsonSchemaElement single(String type, Map<?, ?> schema, String description) {
        if (schema.get("enum") instanceof List<?>) {
            return enumOrRaw(schema, description);
        }
        return switch (type) {
            case "string" -> JsonStringSchema.builder().description(description).build();
            case "integer" -> JsonIntegerSchema.builder().description(description).build();
            case "number" -> JsonNumberSchema.builder().description(description).build();
            case "boolean" -> JsonBooleanSchema.builder().description(description).build();
            case "null" -> new JsonNullSchema();
            case "array" -> JsonArraySchema.builder()
                    .description(description)
                    .items(schema.get("items") instanceof Map<?, ?> items ? element(items) : null)
                    .build();
            case "object" -> objectBuilder(schema).description(description).build();
            default -> raw(schema);
        };
    }

    private static JsonObjectSchema object(Map<?, ?> schema) {
        return objectBuilder(schema).description((String) schema.get("description")).build();
    }

    private static JsonObjectSchema.Builder objectBuilder(Map<?, ?> schema) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        if (schema.get("properties") instanceof Map<?, ?> properties) {
            properties.forEach((name, child) -> builder.addProperty((String) name, element((Map<?, ?>) child)));
        }
        List<String> required = new ArrayList<>();
        if (schema.get("required") instanceof List<?> names) {
            names.forEach(n -> required.add((String) n));
        }
        builder.required(required);
        if (schema.get("additionalProperties") instanceof Boolean additional) {
            builder.additionalProperties(additional);
        }
        return builder;
    }

    /** Enum of strings; a null entry is dropped here because nullability is expressed by anyOf. */
    private static JsonSchemaElement enumOrRaw(Map<?, ?> schema, String description) {
        List<?> values = ((List<?>) schema.get("enum")).stream().filter(Objects::nonNull).toList();
        if (values.isEmpty() || !values.stream().allMatch(v -> v instanceof String)) {
            return raw(schema);
        }
        return JsonEnumSchema.builder()
                .description(description)
                .enumValues(values.stream().map(String.class::cast).toList())
                .build();
    }

    private static JsonSchemaElement raw(Map<?, ?> schema) {
        return JsonRawSchema.from(MAPPER.writeValueAsString(schema));
    }
}
