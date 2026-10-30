package ai.agent.llm;

import java.util.concurrent.atomic.AtomicBoolean;

/** Set when whoever reads a stream is gone (client disconnected, timeout): the generation stops. */
public final class CancelSignal {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
