package ai.agent.audit;

import ai.agent.runs.Run;
import ai.agent.runs.RunStatus;
import ai.agent.runs.RunStore;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records run_status whenever a saved run is new or has a different status. */
public class AuditingRunStore implements RunStore {

    private final RunStore delegate;
    private final AuditLog audit;

    public AuditingRunStore(RunStore delegate, AuditLog audit) {
        this.delegate = delegate;
        this.audit = audit;
    }

    @Override
    public void save(Run run) {
        RunStatus before = delegate.find(run.id()).map(Run::status).orElse(null);
        delegate.save(run);
        if (before != run.status()) {
            AuditEvent event = AuditEvent.of(AuditEvent.RUN_STATUS).withScope(run.threadId(), run.id(), run.assistantId())
                    .withOutcome(run.status().json(), run.lastError() == null ? null : run.lastError().code());
            audit.record(run.status().isTerminal()
                    ? event.withCall(run.model(), null, run.usage().promptTokens(), run.usage().completionTokens(), run.usage().totalTokens())
                    : event.withCall(run.model(), null, null, null, null));
        }
    }

    @Override
    public Optional<Run> find(String runId) {
        return delegate.find(runId);
    }

    @Override
    public List<Run> forThread(String threadId) {
        return delegate.forThread(threadId);
    }

    @Override
    public List<Run> all() {
        return delegate.all();
    }

    @Override
    public List<Run> withStatus(RunStatus... statuses) {
        return delegate.withStatus(statuses);
    }

    @Override
    public AtomicBoolean cancelFlag(String runId) {
        return delegate.cancelFlag(runId);
    }
}
