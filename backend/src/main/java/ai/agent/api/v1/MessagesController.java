package ai.agent.api.v1;

import ai.agent.api.v1.dto.ListResponse;
import ai.agent.api.v1.dto.MessageView;
import ai.agent.common.ListQuery;
import ai.agent.common.Page;
import ai.agent.errors.NotFoundException;
import ai.agent.memory.ConversationStore;
import ai.agent.memory.StoredMessage;
import ai.agent.runs.RunGuard;
import ai.agent.runs.ThreadLocks;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** /v1 messages: only visible messages (user and assistant text) are shown. */
@RestController
@RequestMapping("/v1/threads/{threadId}/messages")
public class MessagesController {

    private final ConversationStore conversations;
    private final ThreadLocks locks;
    private final RunGuard guard;

    public MessagesController(ConversationStore conversations, ThreadLocks locks, RunGuard guard) {
        this.conversations = conversations;
        this.locks = locks;
        this.guard = guard;
    }

    @PostMapping
    public MessageView create(@PathVariable String threadId, @RequestBody(required = false) JsonNode body) {
        conversations.thread(threadId);
        StoredMessage message = MessageInput.parse(body).toStored(threadId);
        locks.withLock(threadId, () -> {
            guard.noActiveRunForMessage(threadId);
            conversations.append(threadId, message);
        });
        return MessageView.of(message);
    }

    @GetMapping
    public ListResponse<MessageView> list(@PathVariable String threadId,
                                          @RequestParam(required = false) Integer limit,
                                          @RequestParam(required = false) String order,
                                          @RequestParam(required = false) String after,
                                          @RequestParam(required = false) String before,
                                          @RequestParam(name = "run_id", required = false) String runId) {
        ListQuery query = ListQuery.of(limit, order, after, before);
        List<StoredMessage> visible = conversations.messages(threadId).stream()
                .filter(StoredMessage::visible)
                .filter(m -> runId == null || runId.equals(m.runId()))
                .toList();
        return ListResponse.of(Page.of(visible, StoredMessage::id, query).map(MessageView::of));
    }

    @GetMapping("/{messageId}")
    public MessageView get(@PathVariable String threadId, @PathVariable String messageId) {
        return conversations.messages(threadId).stream()
                .filter(StoredMessage::visible)
                .filter(m -> m.id().equals(messageId))
                .findFirst()
                .map(MessageView::of)
                .orElseThrow(() -> new NotFoundException("No message found with id '" + messageId + "'."));
    }
}
