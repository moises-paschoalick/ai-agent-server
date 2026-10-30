package ai.agent.audit;

import ai.agent.memory.ConversationStore;
import ai.agent.memory.StoredMessage;
import ai.agent.memory.ToolCall;

import java.util.List;
import java.util.Map;

/** Records thread_deleted around any store; everything else passes through. */
public class AuditingConversationStore implements ConversationStore {

    private final ConversationStore delegate;
    private final AuditLog audit;

    public AuditingConversationStore(ConversationStore delegate, AuditLog audit) {
        this.delegate = delegate;
        this.audit = audit;
    }

    @Override
    public void delete(String threadId) {
        delegate.delete(threadId);
        audit.record(AuditEvent.of(AuditEvent.THREAD_DELETED).withScope(threadId, null, null));
    }

    @Override
    public String create(Map<String, String> metadata) {
        return delegate.create(metadata);
    }

    @Override
    public ThreadInfo thread(String threadId) {
        return delegate.thread(threadId);
    }

    @Override
    public ThreadInfo setMetadata(String threadId, Map<String, String> metadata) {
        return delegate.setMetadata(threadId, metadata);
    }

    @Override
    public boolean exists(String threadId) {
        return delegate.exists(threadId);
    }

    @Override
    public List<String> ids() {
        return delegate.ids();
    }

    @Override
    public List<ThreadSummary> summaries() {
        return delegate.summaries();
    }

    @Override
    public List<StoredMessage> messages(String threadId) {
        return delegate.messages(threadId);
    }

    @Override
    public void append(String threadId, StoredMessage message) {
        delegate.append(threadId, message);
    }

    @Override
    public List<ToolCall> pendingToolCalls(String threadId) {
        return delegate.pendingToolCalls(threadId);
    }

    @Override
    public void closePendingToolCalls(String threadId, String result) {
        delegate.closePendingToolCalls(threadId, result);
    }

    @Override
    public String toolCallAssistant(String threadId) {
        return delegate.toolCallAssistant(threadId);
    }

    @Override
    public void appendToolResult(String threadId, ToolCall call, String result) {
        delegate.appendToolResult(threadId, call, result);
    }

    @Override
    public int toolRoundsSinceLastUserMessage(String threadId) {
        return delegate.toolRoundsSinceLastUserMessage(threadId);
    }
}
