package ai.agent.engine;

import ai.agent.memory.StoredMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * StoredMessage -> LangChain4j messages, rebuilt on every model call.
 */
public final class MessageMapper {

    private MessageMapper() {
    }

    public static List<ChatMessage> toChatMessages(String instructions, List<StoredMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        if (instructions != null && !instructions.isBlank()) {
            messages.add(SystemMessage.from(instructions));
        }
        history.forEach(m -> messages.add(toChatMessage(m)));
        return messages;
    }

    static ChatMessage toChatMessage(StoredMessage message) {
        return switch (message.role()) {
            case USER -> UserMessage.from(message.content());
            case ASSISTANT -> message.toolCalls().isEmpty()
                    ? AiMessage.from(message.content())
                    : AiMessage.from(blankToNull(message.content()), message.toolCalls().stream()
                            .map(c -> ToolExecutionRequest.builder().id(c.id()).name(c.name()).arguments(c.argumentsJson()).build())
                            .toList());
            case TOOL -> ToolExecutionResultMessage.from(message.toolCallId(), message.toolName(), message.content());
        };
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
