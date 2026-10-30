package ai.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /chat. With {@code assistant_id}, this message runs with that assistant (as a /v1
 * run does); without it, with the global prompt, model and tools. {@code stream: true} answers
 * with server-sent events.
 */
public record ChatPromptRequest(@JsonProperty("conversation_id") String conversationId, String prompt,
                                @JsonProperty("assistant_id") String assistantId, Boolean stream) {

    public boolean streaming() {
        return Boolean.TRUE.equals(stream);
    }
}
