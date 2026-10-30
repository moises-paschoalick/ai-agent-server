package ai.agent.memory;

import ai.agent.errors.NotFoundException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What every store derives from a thread's history, computed the same way whatever the storage.
 */
public final class ConversationHistory {

    public static final int TITLE_LENGTH = 80;

    /** Most recent activity first, then newest thread, then id. */
    public static final Comparator<ConversationStore.ThreadSummary> SUMMARY_ORDER =
            Comparator.comparingLong(ConversationStore.ThreadSummary::lastActivityAt)
                    .thenComparingLong(ConversationStore.ThreadSummary::createdAt).reversed()
                    .thenComparing(ConversationStore.ThreadSummary::id);

    private ConversationHistory() {}

    /**
     * The assistant is the last one that answered (a /v1 run or a /chat with assistant_id); threads
     * belong to no assistant. The title is the first user message.
     */
    public static ConversationStore.ThreadSummary summary(String id, long createdAt, List<StoredMessage> history) {
        String title = null;
        String lastAssistant = null;
        int count = 0;
        long lastActivity = createdAt;
        for (StoredMessage message : history) {
            if (message.visible()) {
                count++;
            }
            if (title == null && message.role() == StoredMessage.Role.USER && message.content() != null) {
                title = message.content().length() > TITLE_LENGTH ? message.content().substring(0, TITLE_LENGTH) : message.content();
            }
            if (message.role() == StoredMessage.Role.ASSISTANT) {
                lastAssistant = message.assistantId();
            }
            lastActivity = Math.max(lastActivity, message.createdAt());
        }
        return new ConversationStore.ThreadSummary(id, createdAt, lastAssistant, title, count, lastActivity);
    }

    public static List<ToolCall> pending(List<StoredMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            StoredMessage message = history.get(i);
            if (message.role() == StoredMessage.Role.ASSISTANT && !message.toolCalls().isEmpty()) {
                Set<String> answered = new HashSet<>();
                for (StoredMessage later : history.subList(i + 1, history.size())) {
                    if (later.role() == StoredMessage.Role.TOOL) {
                        answered.add(later.toolCallId());
                    }
                }
                return message.toolCalls().stream().filter(c -> !answered.contains(c.id())).toList();
            }
        }
        return List.of();
    }

    public static String lastToolCallTurn(List<StoredMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (!history.get(i).toolCalls().isEmpty()) {
                return history.get(i).turnId();
            }
        }
        return null;
    }

    public static String toolCallAssistant(List<StoredMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (!history.get(i).toolCalls().isEmpty()) {
                return history.get(i).assistantId();
            }
        }
        return null;
    }

    public static int toolRoundsSinceLastUserMessage(List<StoredMessage> history) {
        int rounds = 0;
        for (int i = history.size() - 1; i >= 0 && history.get(i).role() != StoredMessage.Role.USER; i--) {
            if (!history.get(i).toolCalls().isEmpty()) {
                rounds++;
            }
        }
        return rounds;
    }

    public static NotFoundException notFound(String threadId) {
        return new NotFoundException("No thread found with id '" + threadId + "'.");
    }
}
