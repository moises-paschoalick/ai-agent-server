package ai.agent.api.v1.dto;

import ai.agent.assistants.AssistantTool;
import ai.agent.memory.ToolCall;
import ai.agent.runs.Run;
import ai.agent.runs.RunStatus;
import ai.agent.runs.RunUsage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * OpenAI "thread.run" object. {@code usage} is null until the run is terminal; token limits, truncation and
 * response format have no effect in this version and are returned with fixed values.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RunView(
        String id,
        String object,
        long createdAt,
        String threadId,
        String assistantId,
        RunStatus status,
        RequiredAction requiredAction,
        Run.LastError lastError,
        Long expiresAt,
        Long startedAt,
        Long cancelledAt,
        Long failedAt,
        Long completedAt,
        IncompleteDetails incompleteDetails,
        String model,
        String instructions,
        List<AssistantTool> tools,
        Map<String, String> metadata,
        Usage usage,
        Double temperature,
        Double topP,
        Integer maxPromptTokens,
        Integer maxCompletionTokens,
        TruncationStrategy truncationStrategy,
        String responseFormat,
        JsonNode toolChoice,
        Boolean parallelToolCalls) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RequiredAction(String type, SubmitToolOutputs submitToolOutputs) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SubmitToolOutputs(List<ToolCallView> toolCalls) {}

    public record ToolCallView(String id, String type, Function function) {}

    /** {@code arguments} is always the JSON string sent by the model. */
    public record Function(String name, String arguments) {}

    public record IncompleteDetails(String reason) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TruncationStrategy(String type, Integer lastMessages) {
        static final TruncationStrategy AUTO = new TruncationStrategy("auto", null);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Usage(int promptTokens, int completionTokens, int totalTokens) {}

    public static RunView of(Run r) {
        RequiredAction action = r.status() == RunStatus.REQUIRES_ACTION
                ? new RequiredAction("submit_tool_outputs", new SubmitToolOutputs(r.requiredToolCalls().stream()
                        .map(RunView::toolCall).toList()))
                : null;
        RunUsage u = r.usage();
        return new RunView(r.id(), "thread.run", r.createdAt(), r.threadId(), r.assistantId(), r.status(), action,
                r.lastError(), r.expiresAt(), r.startedAt(), r.cancelledAt(), r.failedAt(), r.completedAt(),
                r.incompleteReason() == null ? null : new IncompleteDetails(r.incompleteReason()),
                r.model(), r.instructions(), r.tools(), r.metadata(),
                r.status().isTerminal() ? new Usage(u.promptTokens(), u.completionTokens(), u.totalTokens()) : null,
                r.temperature(), r.topP(), null, null, TruncationStrategy.AUTO, "auto", r.toolChoice(), r.parallelToolCalls());
    }

    private static ToolCallView toolCall(ToolCall call) {
        return new ToolCallView(call.id(), "function",
                new Function(call.name(), call.argumentsJson() == null ? "{}" : call.argumentsJson()));
    }
}
