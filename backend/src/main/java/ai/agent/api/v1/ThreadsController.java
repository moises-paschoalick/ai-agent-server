package ai.agent.api.v1;

import ai.agent.api.v1.dto.DeletedResponse;
import ai.agent.api.v1.dto.ThreadView;
import ai.agent.common.MetadataRules;
import ai.agent.errors.ValidationException;
import ai.agent.memory.ConversationStore;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /v1 threads over the same conversations as the sync contract (Q-10). */
@RestController
@RequestMapping("/v1/threads")
public class ThreadsController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRINGS = new TypeReference<>() {};

    private final ConversationStore conversations;

    public ThreadsController(ConversationStore conversations) {
        this.conversations = conversations;
    }

    @PostMapping
    public ThreadView create(@RequestBody(required = false) JsonNode body) {
        Map<String, String> metadata = metadata(body);
        List<MessageInput> messages = new ArrayList<>();
        JsonNode initial = body == null ? null : body.get("messages");
        if (initial != null && !initial.isNull()) {
            if (!initial.isArray()) {
                throw new ValidationException("messages must be a list", "messages");
            }
            initial.forEach(m -> messages.add(MessageInput.parse(m)));
        }

        String id = conversations.create(metadata);
        messages.forEach(m -> conversations.append(id, m.toStored(id)));
        return ThreadView.of(conversations.thread(id));
    }

    @GetMapping("/{threadId}")
    public ThreadView get(@PathVariable String threadId) {
        return ThreadView.of(conversations.thread(threadId));
    }

    @PostMapping("/{threadId}")
    public ThreadView update(@PathVariable String threadId, @RequestBody(required = false) JsonNode body) {
        conversations.thread(threadId);
        if (body != null && body.has("metadata")) {
            return ThreadView.of(conversations.setMetadata(threadId, metadata(body)));
        }
        return ThreadView.of(conversations.thread(threadId));
    }

    @DeleteMapping("/{threadId}")
    public DeletedResponse delete(@PathVariable String threadId) {
        conversations.delete(threadId);
        return DeletedResponse.of(threadId, "thread");
    }

    private static Map<String, String> metadata(JsonNode body) {
        JsonNode metadata = body == null ? null : body.get("metadata");
        if (metadata == null || metadata.isNull()) {
            return Map.of();
        }
        if (!metadata.isObject()) {
            throw new ValidationException("metadata must be an object", "metadata");
        }
        return MetadataRules.validated(MAPPER.convertValue(metadata, STRINGS));
    }
}
