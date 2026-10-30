package ai.agent.llm;

/**
 * Provider-neutral call options; each provider translates them into its own request parameters.
 * Null values fall back to the provider/model defaults.
 */
public record CallOptions(Double temperature, Double topP, Integer maxTokens, boolean oneToolAtATime) {

    public static CallOptions defaults() {
        return new CallOptions(null, null, null, false);
    }

    public CallOptions withTemperature(Double value) {
        return new CallOptions(value, topP, maxTokens, oneToolAtATime);
    }
}
