package ai.agent.memory;

import ai.agent.common.Ids;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversations kept in memory (lost on restart). Each conversation serializes its own reads and
 * writes, so its history is never left half-written.
 */
public class InMemoryConversationStore implements ConversationStore {

    /** The message list doubles as the lock of the conversation. */
    private static final class Conversation {
        final long createdAt = Instant.now().getEpochSecond();
        volatile Map<String, String> metadata;
        final List<StoredMessage> messages = new ArrayList<>();

        Conversation(Map<String, String> metadata) {
            this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    @Override
    public String create(Map<String, String> metadata) {
        String id = Ids.newId("thread_");
        conversations.put(id, new Conversation(metadata));
        return id;
    }

    @Override
    public ThreadInfo thread(String threadId) {
        Conversation conversation = conversation(threadId);
        return new ThreadInfo(threadId, conversation.createdAt, conversation.metadata);
    }

    @Override
    public ThreadInfo setMetadata(String threadId, Map<String, String> metadata) {
        conversation(threadId).metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        return thread(threadId);
    }

    @Override
    public boolean exists(String threadId) {
        return conversations.containsKey(threadId);
    }

    @Override
    public List<String> ids() {
        return new ArrayList<>(conversations.keySet());
    }

    @Override
    public List<ThreadSummary> summaries() {
        List<ThreadSummary> result = new ArrayList<>();
        conversations.forEach((id, conversation) -> {
            synchronized (conversation.messages) {
                result.add(ConversationHistory.summary(id, conversation.createdAt, conversation.messages));
            }
        });
        result.sort(ConversationHistory.SUMMARY_ORDER);
        return result;
    }

    @Override
    public void delete(String threadId) {
        if (conversations.remove(threadId) == null) {
            throw ConversationHistory.notFound(threadId);
        }
    }

    @Override
    public List<StoredMessage> messages(String threadId) {
        List<StoredMessage> history = require(threadId);
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    @Override
    public void append(String threadId, StoredMessage message) {
        List<StoredMessage> history = require(threadId);
        synchronized (history) {
            history.add(message);
        }
    }

    @Override
    public List<ToolCall> pendingToolCalls(String threadId) {
        List<StoredMessage> history = require(threadId);
        synchronized (history) {
            return ConversationHistory.pending(history);
        }
    }

    @Override
    public void closePendingToolCalls(String threadId, String result) {
        List<StoredMessage> history = require(threadId);
        synchronized (history) {
            String turnId = ConversationHistory.lastToolCallTurn(history);
            for (ToolCall call : ConversationHistory.pending(history)) {
                history.add(StoredMessage.toolResult(threadId, call, result, turnId, null));
            }
        }
    }

    @Override
    public String toolCallAssistant(String threadId) {
        List<StoredMessage> history = require(threadId);
        synchronized (history) {
            return ConversationHistory.toolCallAssistant(history);
        }
    }

    @Override
    public void appendToolResult(String threadId, ToolCall call, String result) {
        List<StoredMessage> history = require(threadId);
        synchronized (history) {
            history.add(StoredMessage.toolResult(threadId, call, result, ConversationHistory.lastToolCallTurn(history), null));
        }
    }

    @Override
    public int toolRoundsSinceLastUserMessage(String threadId) {
        List<StoredMessage> history = require(threadId);
        synchronized (history) {
            return ConversationHistory.toolRoundsSinceLastUserMessage(history);
        }
    }

    private List<StoredMessage> require(String threadId) {
        return conversation(threadId).messages;
    }

    private Conversation conversation(String threadId) {
        Conversation conversation = conversations.get(threadId);
        if (conversation == null) {
            throw ConversationHistory.notFound(threadId);
        }
        return conversation;
    }
}
