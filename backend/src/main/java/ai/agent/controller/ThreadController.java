package ai.agent.controller;


import ai.agent.memory.ConversationStore;
import ai.agent.memory.StoredMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/threads")
@RequiredArgsConstructor
public class ThreadController {

    private final ConversationStore conversations;

    /** Visible message of the sync history. */
    public record HistoryItem(String role, String content) {}

    /** A thread belongs to no assistant: each /chat message says which one answers (as /v1 runs do). */
    @PostMapping
    public Map<String, String> createThread() {
        return Map.of("conversation_id", conversations.create());
    }

    @GetMapping
    public List<String> listThreads() {
        return conversations.ids();
    }

    /** Item of GET /threads/summaries (the list of GET /threads stays as plain ids). */
    public record ThreadSummaryView(@JsonProperty("conversation_id") String conversationId,
                                    @JsonProperty("created_at") long createdAt,
                                    @JsonProperty("assistant_id") String assistantId,
                                    String title,
                                    @JsonProperty("message_count") int messageCount,
                                    @JsonProperty("last_activity_at") long lastActivityAt) {}

    @GetMapping("/summaries")
    public List<ThreadSummaryView> summaries() {
        return conversations.summaries().stream()
                .map(s -> new ThreadSummaryView(s.id(), s.createdAt(), s.assistantId(), s.title(), s.messageCount(), s.lastActivityAt()))
                .toList();
    }

    @GetMapping("/{conversationId}/history")
    public List<HistoryItem> history(@PathVariable String conversationId) {
        return conversations.messages(conversationId).stream()
                .filter(StoredMessage::visible)
                .map(m -> new HistoryItem(m.role().name().toLowerCase(), m.content()))
                .toList();
    }

    @DeleteMapping("/{conversationId}")
    public Map<String, String> delete(@PathVariable String conversationId) {
        conversations.delete(conversationId);
        return Map.of("message", "Thread deleted");
    }
}
