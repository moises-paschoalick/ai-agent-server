package ai.agent.controller;

import ai.agent.dto.ChatPromptRequest;
import ai.agent.dto.ChatReply;
import ai.agent.errors.ValidationException;
import ai.agent.services.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** JSON reply; with {@code stream: true}, server-sent events written here (then null is returned). */
    @PostMapping
    public ChatReply chat(@RequestBody ChatPromptRequest request, HttpServletResponse response) {

        if (request.conversationId() == null || request.prompt() == null) {
            throw new ValidationException("conversation_id and prompt are required");
        }

        if (!request.streaming()) {
            return chatService.chat(request.conversationId(), request.prompt(), request.assistantId());
        }
        new SseResponse(response).run(stream ->
                chatService.chat(request.conversationId(), request.prompt(), request.assistantId(), stream));
        return null;
    }

}
