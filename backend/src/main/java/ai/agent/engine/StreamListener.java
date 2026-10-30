package ai.agent.engine;

/** Receives the text of the model as it is generated, piece by piece, in order. */
@FunctionalInterface
public interface StreamListener {

    void onDelta(String text);
}
