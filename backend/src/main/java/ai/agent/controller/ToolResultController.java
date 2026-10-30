package ai.agent.controller;

import ai.agent.dto.ChatReply;
import ai.agent.dto.ToolResultRequest;
import ai.agent.errors.ValidationException;
import ai.agent.services.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ToolResultController {

    private final ChatService chatService;

    /** JSON reply; with {@code stream: true}, server-sent events written here (then null is returned). */
    @PostMapping("/tools/result")
    public ChatReply toolResult(@RequestBody ToolResultRequest request, HttpServletResponse response) {
        if (request.conversationId() == null) {
            throw new ValidationException("conversation_id is required");
        }
        if (!request.streaming()) {
            return chatService.toolResult(request);
        }
        new SseResponse(response).run(stream -> chatService.toolResult(request, stream));
        return null;
    }
}
