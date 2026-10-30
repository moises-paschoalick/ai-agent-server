package ai.agent.api.v1;

import ai.agent.common.MetadataRules;
import ai.agent.errors.ValidationException;
import ai.agent.memory.StoredMessage;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A /v1 message to create: role user|assistant, text content (string or text parts joined by
 * a line break), optional metadata. Images and attachments are not supported.
 */
record MessageInput(String role, String text, Map<String, String> metadata) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRINGS = new TypeReference<>() {};

    static MessageInput parse(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new ValidationException("A message object is required", "content");
        }
        JsonNode role = json.get("role");
        if (role == null || !role.isString() || !(role.stringValue().equals("user") || role.stringValue().equals("assistant"))) {
            throw new ValidationException("role must be 'user' or 'assistant'", "role");
        }
        JsonNode attachments = json.get("attachments");
        if (attachments != null && attachments.isArray() && !attachments.isEmpty()) {
            throw new ValidationException("Attachments are not supported.", "attachments");
        }
        JsonNode metadata = json.get("metadata");
        Map<String, String> meta = metadata == null || metadata.isNull()
                ? Map.of()
                : MetadataRules.validated(MAPPER.convertValue(metadata, STRINGS));
        return new MessageInput(role.stringValue(), text(json.get("content")), meta);
    }

    StoredMessage toStored(String threadId) {
        StoredMessage message = role.equals("user")
                ? StoredMessage.user(threadId, text)
                : StoredMessage.assistantText(threadId, text, null, null);
        return message.withMetadata(metadata);
    }

    private static String text(JsonNode content) {
        String text;
        if (content != null && content.isString()) {
            text = content.stringValue();
        } else if (content != null && content.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode part : content) {
                String type = part.has("type") ? part.get("type").asString() : null;
                if (!"text".equals(type)) {
                    throw new ValidationException("Content type '" + type + "' is not supported.", "content");
                }
                JsonNode value = part.get("text");
                if (value != null && value.isObject()) {
                    value = value.get("value");
                }
                if (value == null || !value.isString()) {
                    throw new ValidationException("Text parts need a 'text' string", "content");
                }
                parts.add(value.stringValue());
            }
            text = String.join("\n", parts);
        } else {
            text = null;
        }
        if (text == null || text.isEmpty()) {
            throw new ValidationException("content is required", "content");
        }
        return text;
    }
}
