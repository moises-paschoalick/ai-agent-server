package ai.agent.audit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * One audit event. Never carries message text, tool arguments or results, instructions or keys:
 * only ids, the hash of the caller's API key, timings, token counts and outcomes.
 * {@code latencyMs} is the model call for {@code llm_call} and the execution of a system tool for
 * {@code tool_call}; {@code ttftMs}, of an {@code llm_call}, the time to its first streamed piece
 * (the latency when it did not stream). {@code id} is null until stored.
 */
public record AuditEvent(
        Long id,
        Instant at,
        String type,
        @JsonProperty("thread_id") String threadId,
        @JsonProperty("run_id") String runId,
        @JsonProperty("assistant_id") String assistantId,
        @JsonProperty("api_key_hash") String apiKeyHash,
        String model,
        @JsonProperty("latency_ms") Long latencyMs,
        @JsonProperty("ttft_ms") Long ttftMs,
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("total_tokens") Integer totalTokens,
        @JsonProperty("tool_name") String toolName,
        String outcome,
        @JsonProperty("error_code") String errorCode,
        Map<String, Object> detail) {

    public static final String LLM_CALL = "llm_call";
    public static final String TOOL_CALL = "tool_call";
    public static final String RUN_STATUS = "run_status";
    public static final String THREAD_DELETED = "thread_deleted";

    public AuditEvent {
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }

    /** An event of {@code type} now, by the current caller; the other fields come from the with* copies. */
    public static AuditEvent of(String type) {
        return new AuditEvent(null, Instant.now(), type, null, null, null, AuditContext.caller(),
                null, null, null, null, null, null, null, null, null, Map.of());
    }

    public AuditEvent withId(Long value) {
        return new AuditEvent(value, at, type, threadId, runId, assistantId, apiKeyHash, model, latencyMs, ttftMs,
                promptTokens, completionTokens, totalTokens, toolName, outcome, errorCode, detail);
    }

    public AuditEvent withAt(Instant value) {
        return new AuditEvent(id, value, type, threadId, runId, assistantId, apiKeyHash, model, latencyMs, ttftMs,
                promptTokens, completionTokens, totalTokens, toolName, outcome, errorCode, detail);
    }

    public AuditEvent withScope(String thread, String run, String assistant) {
        return new AuditEvent(id, at, type, thread, run, assistant, apiKeyHash, model, latencyMs, ttftMs,
                promptTokens, completionTokens, totalTokens, toolName, outcome, errorCode, detail);
    }

    public AuditEvent withCall(String modelName, Long latency, Integer prompt, Integer completion, Integer total) {
        return new AuditEvent(id, at, type, threadId, runId, assistantId, apiKeyHash, modelName, latency, ttftMs,
                prompt, completion, total, toolName, outcome, errorCode, detail);
    }

    public AuditEvent withTtft(Long value) {
        return new AuditEvent(id, at, type, threadId, runId, assistantId, apiKeyHash, model, latencyMs, value,
                promptTokens, completionTokens, totalTokens, toolName, outcome, errorCode, detail);
    }

    public AuditEvent withTool(String name, Long latency) {
        return new AuditEvent(id, at, type, threadId, runId, assistantId, apiKeyHash, model, latency, ttftMs,
                promptTokens, completionTokens, totalTokens, name, outcome, errorCode, detail);
    }

    public AuditEvent withOutcome(String value, String error) {
        return new AuditEvent(id, at, type, threadId, runId, assistantId, apiKeyHash, model, latencyMs, ttftMs,
                promptTokens, completionTokens, totalTokens, toolName, value, error, detail);
    }

    public AuditEvent withDetail(Map<String, Object> value) {
        return new AuditEvent(id, at, type, threadId, runId, assistantId, apiKeyHash, model, latencyMs, ttftMs,
                promptTokens, completionTokens, totalTokens, toolName, outcome, errorCode, value);
    }
}
