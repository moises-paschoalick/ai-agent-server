package ai.agent.audit;

import ai.agent.tools.ToolDefinition;
import ai.agent.tools.ToolRepository;

import java.util.List;
import java.util.Optional;

/** Records tool_created, tool_updated and tool_deleted around any repository. */
public class AuditingToolRepository implements ToolRepository {

    private final ToolRepository delegate;
    private final AuditLog audit;

    public AuditingToolRepository(ToolRepository delegate, AuditLog audit) {
        this.delegate = delegate;
        this.audit = audit;
    }

    @Override
    public List<ToolDefinition> findAll() {
        return delegate.findAll();
    }

    @Override
    public Optional<ToolDefinition> find(String name) {
        return delegate.find(name);
    }

    @Override
    public void save(ToolDefinition tool) {
        boolean existed = delegate.find(tool.name()).isPresent();
        delegate.save(tool);
        audit.record(AuditEvent.of(existed ? "tool_updated" : "tool_created").withTool(tool.name(), null));
    }

    @Override
    public boolean delete(String name) {
        boolean deleted = delegate.delete(name);
        if (deleted) {
            audit.record(AuditEvent.of("tool_deleted").withTool(name, null));
        }
        return deleted;
    }
}
