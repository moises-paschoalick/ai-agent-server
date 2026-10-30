package ai.agent.llm;

/**
 * Creates providers of one adapter type ("openai-compatible", "anthropic", "gemini").
 */
public interface LlmProviderFactory {

    String type();

    LlmProvider create(String id, ProviderSettings settings);
}
