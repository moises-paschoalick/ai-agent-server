package ai.agent.runs;

import dev.langchain4j.model.output.TokenUsage;

/** Token usage summed over every model call of a run. */
public record RunUsage(int promptTokens, int completionTokens, int totalTokens) {

    public static final RunUsage ZERO = new RunUsage(0, 0, 0);

    public RunUsage plus(TokenUsage usage) {
        if (usage == null) {
            return this;
        }
        return new RunUsage(
                promptTokens + orZero(usage.inputTokenCount()),
                completionTokens + orZero(usage.outputTokenCount()),
                totalTokens + orZero(usage.totalTokenCount()));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
