package ai.agent.llm;

import dev.langchain4j.model.chat.response.ChatResponse;

public record LlmResult(String providerId, String modelName, ChatResponse response) {}
