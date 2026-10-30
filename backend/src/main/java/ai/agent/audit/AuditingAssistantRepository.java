package ai.agent.audit;

import ai.agent.assistants.Assistant;
import ai.agent.assistants.AssistantRepository;

import java.util.List;
import java.util.Optional;

/** Records assistant_created, assistant_updated and assistant_deleted around any repository. */
public class AuditingAssistantRepository implements AssistantRepository {

    private final AssistantRepository delegate;
    private final AuditLog audit;

    public AuditingAssistantRepository(AssistantRepository delegate, AuditLog audit) {
        this.delegate = delegate;
        this.audit = audit;
    }

    @Override
    public List<Assistant> findAll() {
        return delegate.findAll();
    }

    @Override
    public Optional<Assistant> find(String id) {
        return delegate.find(id);
    }

    @Override
    public void save(Assistant assistant) {
        boolean existed = delegate.find(assistant.id()).isPresent();
        delegate.save(assistant);
        audit.record(AuditEvent.of(existed ? "assistant_updated" : "assistant_created").withScope(null, null, assistant.id()));
    }

    @Override
    public boolean delete(String id) {
        boolean deleted = delegate.delete(id);
        if (deleted) {
            audit.record(AuditEvent.of("assistant_deleted").withScope(null, null, id));
        }
        return deleted;
    }
}
