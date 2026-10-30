package ai.agent.engine;

import ai.agent.memory.ToolCall;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

/**
 * How an execution ended. {@code usage} is the sum over every model call of the execution.
 */
public sealed interface RunOutcome {

    TokenUsage usage();

    /** The model answered with text; {@code model} is the name reported by the provider. */
    record MessageOutcome(String text, TokenUsage usage, int rounds, String model) implements RunOutcome {}

    /** The model asked for client (dynamic) tools; system tools of the same turn were already executed. */
    record ToolCallsOutcome(List<ToolCall> pending, TokenUsage usage, int rounds) implements RunOutcome {}

    /** Stopped before an answer, e.g. reason "max_tool_rounds"; {@code text} is the recorded notice. */
    record IncompleteOutcome(String reason, String text, TokenUsage usage, int rounds, String model) implements RunOutcome {}

    /** The model call failed; code is rate_limit_exceeded, timeout or server_error. */
    record FailedOutcome(String code, String message, TokenUsage usage) implements RunOutcome {}

    /** Cancellation was requested; a response that arrived afterwards was discarded. */
    record CancelledOutcome(TokenUsage usage) implements RunOutcome {}
}
