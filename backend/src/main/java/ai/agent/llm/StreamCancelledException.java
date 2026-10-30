package ai.agent.llm;

/** The stream was cancelled ({@link CancelSignal}) before the model finished; nothing of it is kept. */
public class StreamCancelledException extends RuntimeException {

    public StreamCancelledException() {
        super("The stream was cancelled.");
    }
}
