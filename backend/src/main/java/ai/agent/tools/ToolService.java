package ai.agent.tools;

import ai.agent.errors.ConflictException;
import ai.agent.errors.NotFoundException;
import ai.agent.errors.ValidationException;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Catalog rules: unique names across dynamic and system tools, system tools are read-only.
 */
@Service
public class ToolService {

    private final ToolRepository repository;
    private final SystemToolExecutor systemTools;

    public ToolService(ToolRepository repository, SystemToolExecutor systemTools) {
        this.repository = repository;
        this.systemTools = systemTools;
    }

    public List<ToolView> list() {
        List<ToolView> views = new ArrayList<>();
        repository.findAll().forEach(t -> views.add(ToolView.of(t, ToolKind.DYNAMIC)));
        systemTools.definitions().forEach(t -> views.add(ToolView.of(t, ToolKind.SYSTEM)));
        return views;
    }

    public ToolView get(String name) {
        return systemTools.definitions().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .map(t -> ToolView.of(t, ToolKind.SYSTEM))
                .orElseGet(() -> repository.find(name)
                        .map(t -> ToolView.of(t, ToolKind.DYNAMIC))
                        .orElseThrow(() -> notFound(name)));
    }

    public ToolView create(ToolDefinition.Request request) {
        String name = request.name();
        SchemaValidator.validateName(name);
        if (systemTools.isSystem(name) || repository.find(name).isPresent()) {
            throw new ConflictException("A tool named '" + name + "' already exists.");
        }
        ToolDefinition tool = validated(request.toDefinition(name));
        repository.save(tool);
        return ToolView.of(tool, ToolKind.DYNAMIC);
    }

    public ToolView update(String name, ToolDefinition.Request request) {
        rejectSystem(name);
        repository.find(name).orElseThrow(() -> notFound(name));
        if (request.name() != null && !request.name().equals(name)) {
            throw new ValidationException("Tool name in the body ('" + request.name() + "') must match the URL ('" + name + "').");
        }
        ToolDefinition tool = validated(request.toDefinition(name));
        repository.save(tool);
        return ToolView.of(tool, ToolKind.DYNAMIC);
    }

    public void delete(String name) {
        rejectSystem(name);
        if (!repository.delete(name)) {
            throw notFound(name);
        }
    }

    /** Dynamic (client-executed) tools only, converted for LangChain4j. */
    public List<ToolSpecification> dynamicSpecifications() {
        return repository.findAll().stream().map(SchemaConverter::toSpecification).toList();
    }

    /** Every tool the model may call, converted for LangChain4j. */
    public List<ToolSpecification> specifications() {
        return Stream.concat(repository.findAll().stream(), systemTools.definitions().stream())
                .map(SchemaConverter::toSpecification)
                .toList();
    }

    private static ToolDefinition validated(ToolDefinition tool) {
        SchemaValidator.validateParameters(tool.parameters());
        return tool;
    }

    private void rejectSystem(String name) {
        if (systemTools.isSystem(name)) {
            throw new ConflictException("System tool '" + name + "' cannot be changed or removed.");
        }
    }

    private static NotFoundException notFound(String name) {
        return new NotFoundException("No tool found with name '" + name + "'.");
    }
}
