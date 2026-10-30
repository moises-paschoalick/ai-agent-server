package ai.agent.engine;

import ai.agent.llm.CancelSignal;

import java.util.function.BooleanSupplier;

/**
 * Execution context: run id (null in the sync contract), assistant id (null for the global sync
 * settings), cancellation flag and the tool rounds already spent on the current user input.
 * With a {@code listener}, model calls stream and their text goes to it as it arrives;
 * {@code streamCancel} stops such a stream when its reader is gone.
 */
public record RunContext(String runId, String assistantId, BooleanSupplier cancelRequested, int initialRounds,
                         StreamListener listener, CancelSignal streamCancel) {

    public RunContext {
        streamCancel = streamCancel == null ? new CancelSignal() : streamCancel;
    }

    /** Without streaming. */
    public RunContext(String runId, String assistantId, BooleanSupplier cancelRequested, int initialRounds) {
        this(runId, assistantId, cancelRequested, initialRounds, null, null);
    }

    public static RunContext sync() {
        return new RunContext(null, null, () -> false, 0);
    }
}
