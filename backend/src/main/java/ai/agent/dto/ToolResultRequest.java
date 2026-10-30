package ai.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /tools/result; {@code toolCallId} is optional (oldest pending call is used).
 * {@code stream: true} answers with server-sent events.
 */
public record ToolResultRequest(
        @JsonProperty("conversation_id") String conversationId,
        @JsonProperty("tool_name") String toolName,
        @JsonProperty("tool_output") String toolOutput,
        @JsonProperty("tool_call_id") String toolCallId,
        Boolean stream) {

    public boolean streaming() {
        return Boolean.TRUE.equals(stream);
    }
}
