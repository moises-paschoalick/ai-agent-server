package ai.agent.runs;

import ai.agent.memory.StoredMessage;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * A run creation request, already parsed from the /v1 JSON. {@code tools} is the raw "tools"
 * field (null = use the assistant's tools); {@code additionalMessages} are stored before the run.
 */
public record RunRequest(
        String assistantId,
        String model,
        String instructions,
        String additionalInstructions,
        JsonNode tools,
        JsonNode toolChoice,
        Boolean parallelToolCalls,
        Double temperature,
        Double topP,
        Map<String, String> metadata,
        List<StoredMessage> additionalMessages,
        boolean stream) {

    public RunRequest {
        metadata = metadata == null ? Map.of() : metadata;
        additionalMessages = additionalMessages == null ? List.of() : List.copyOf(additionalMessages);
    }
}
