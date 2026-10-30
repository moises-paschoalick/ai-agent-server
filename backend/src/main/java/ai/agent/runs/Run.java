package ai.agent.runs;

import ai.agent.assistants.AssistantTool;
import ai.agent.memory.ToolCall;
import lombok.With;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a run; every transition stores a new copy (under the thread lock), so
 * readers never see an intermediate state. Model, instructions, tools and sampling are frozen
 * at creation.
 */
@With
public record Run(
        String id,
        String threadId,
        String assistantId,
        long createdAt,
        RunStatus status,
        List<ToolCall> requiredToolCalls,
        LastError lastError,
        Long expiresAt,
        Long startedAt,
        Long cancelledAt,
        Long failedAt,
        Long completedAt,
        String incompleteReason,
        String model,
        String instructions,
        List<AssistantTool> tools,
        Map<String, String> metadata,
        RunUsage usage,
        Double temperature,
        Double topP,
        JsonNode toolChoice,
        Boolean parallelToolCalls) {

    public record LastError(String code, String message) {}

    public Run {
        requiredToolCalls = requiredToolCalls == null ? List.of() : List.copyOf(requiredToolCalls);
        tools = tools == null ? List.of() : List.copyOf(tools);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        usage = usage == null ? RunUsage.ZERO : usage;
    }
}
