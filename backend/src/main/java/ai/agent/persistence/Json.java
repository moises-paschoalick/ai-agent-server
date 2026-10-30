package ai.agent.persistence;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The jsonb columns: domain records written as they are. Unknown fields are ignored on read and
 * missing ones come back null, so fields can be added without a data migration.
 */
final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {}

    static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    static <T> T read(String json, TypeReference<T> type) {
        return MAPPER.readValue(json, type);
    }
}
