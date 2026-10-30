package ai.agent.tools;

import ai.agent.errors.ValidationException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks tool names and the parts of a JSON Schema the converter relies on.
 * Unknown keywords are accepted and preserved.
 */
public final class SchemaValidator {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Set<String> TYPES = Set.of("string", "integer", "number", "boolean", "array", "object", "null");

    private SchemaValidator() {
    }

    public static void validateName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new ValidationException("Tool name must have 1 to 64 letters, digits, '_' or '-', got '" + name + "'");
        }
    }

    public static void validateParameters(Map<String, Object> parameters) {
        if (!"object".equals(parameters.get("type"))) {
            throw invalid("parameters", "root type must be \"object\"");
        }
        validate(parameters, "parameters");
    }

    private static void validate(Map<?, ?> schema, String path) {
        Object type = schema.get("type");
        if (type != null) {
            validateType(type, path);
        }

        Object properties = schema.get("properties");
        if (properties != null) {
            if (!(properties instanceof Map<?, ?> props)) {
                throw invalid(path + ".properties", "must be an object");
            }
            props.forEach((name, child) -> validate(asSchema(child, path + ".properties." + name), path + ".properties." + name));
        }

        Object required = schema.get("required");
        if (required != null) {
            if (!(required instanceof List<?> names)) {
                throw invalid(path + ".required", "must be a list");
            }
            Map<?, ?> props = properties instanceof Map<?, ?> m ? m : Map.of();
            for (Object name : names) {
                if (!props.containsKey(name)) {
                    throw invalid(path + ".required", "references missing property '" + name + "'");
                }
            }
        }

        Object enumValues = schema.get("enum");
        if (enumValues != null && (!(enumValues instanceof List<?> values) || values.isEmpty())) {
            throw invalid(path + ".enum", "must be a non-empty list");
        }

        Object items = schema.get("items");
        if (items != null) {
            validate(asSchema(items, path + ".items"), path + ".items");
        }

        for (String keyword : List.of("anyOf", "oneOf", "allOf")) {
            Object branches = schema.get(keyword);
            if (branches != null) {
                if (!(branches instanceof List<?> list) || list.isEmpty()) {
                    throw invalid(path + "." + keyword, "must be a non-empty list");
                }
                for (int i = 0; i < list.size(); i++) {
                    String branchPath = path + "." + keyword + "[" + i + "]";
                    validate(asSchema(list.get(i), branchPath), branchPath);
                }
            }
        }
    }

    private static void validateType(Object type, String path) {
        if (type instanceof String name) {
            if (!TYPES.contains(name)) {
                throw invalid(path + ".type", "unknown type '" + name + "'");
            }
            return;
        }
        if (type instanceof List<?> names && !names.isEmpty()) {
            for (Object name : names) {
                if (!(name instanceof String n) || !TYPES.contains(n)) {
                    throw invalid(path + ".type", "unknown type '" + name + "'");
                }
            }
            return;
        }
        throw invalid(path + ".type", "must be a type name or a non-empty list of type names");
    }

    private static Map<?, ?> asSchema(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(path, "must be a schema object");
        }
        return map;
    }

    private static ValidationException invalid(String path, String problem) {
        return new ValidationException("Invalid tool schema at " + path + ": " + problem);
    }
}
