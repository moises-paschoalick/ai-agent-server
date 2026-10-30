package ai.agent.engine;

import ai.agent.errors.ApiException;
import ai.agent.errors.LlmRateLimitException;
import ai.agent.errors.LlmTimeoutException;
import ai.agent.errors.LlmUpstreamException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

/**
 * Typed failure codes for model calls. Messages are fixed: the provider's own message
 * may carry details (or a key) and is never passed on.
 */
@Slf4j
public final class LlmFailures {

    public static final String RATE_LIMIT = "rate_limit_exceeded";
    public static final String TIMEOUT = "timeout";
    public static final String SERVER_ERROR = "server_error";

    private LlmFailures() {
    }

    public static RunOutcome.FailedOutcome classify(RuntimeException e, TokenUsage usage) {
        log.warn("Language model call failed: {}", e.getClass().getSimpleName());
        if (e instanceof RateLimitException) {
            return new RunOutcome.FailedOutcome(RATE_LIMIT, "The language model rate limit was exceeded.", usage);
        }
        if (e instanceof TimeoutException) {
            return new RunOutcome.FailedOutcome(TIMEOUT, "The language model call timed out.", usage);
        }
        return new RunOutcome.FailedOutcome(SERVER_ERROR, "The language model call failed.", usage);
    }

    /** HTTP error for a failed outcome in the sync contract. */
    public static ApiException toApiException(RunOutcome.FailedOutcome failed) {
        return switch (failed.code()) {
            case RATE_LIMIT -> new LlmRateLimitException(failed.message());
            case TIMEOUT -> new LlmTimeoutException(failed.message());
            default -> new LlmUpstreamException(failed.message());
        };
    }
}
