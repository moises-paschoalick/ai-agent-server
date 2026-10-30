package ai.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of POST /chat and POST /tools/result: either a message or a client tool call.
 */
public sealed interface ChatReply {

    String type();

    record MessageReply(String type, String content, Metadata metadata) implements ChatReply {

        public static MessageReply of(String content, Metadata metadata) {
            return new MessageReply("message", content, metadata);
        }
    }

    /** {@code ttftMs}: time to the first piece of text (equal to {@code latencyMs} without streaming). */
    record Metadata(@JsonProperty("latency_ms") long latencyMs,
                    @JsonProperty("ttft_ms") long ttftMs,
                    @JsonProperty("total_tokens") int totalTokens,
                    String model) {}

    /** {@code toolInput} is the arguments as a JSON object, or the raw string when it is not valid JSON. */
    record ToolCallReply(String type, String tool,
                         @JsonProperty("tool_input") Object toolInput,
                         @JsonProperty("tool_call_id") String toolCallId) implements ChatReply {

        public static ToolCallReply of(String tool, Object toolInput, String toolCallId) {
            return new ToolCallReply("tool_call", tool, toolInput, toolCallId);
        }
    }
}
