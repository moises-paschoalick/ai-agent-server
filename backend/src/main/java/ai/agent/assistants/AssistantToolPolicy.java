package ai.agent.assistants;

import ai.agent.engine.SystemToolSelection;
import ai.agent.tools.SystemToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Tools of an execution with an assistant (a /v1 run or a /chat with assistant_id). A function
 * tool named like a system tool is a declaration of that system tool: it never becomes a client
 * tool. With agent.tools.system-mode=all every system tool is offered; with "declared", only the
 * declared ones.
 */
@Component
public class AssistantToolPolicy {

    public enum Mode { ALL, DECLARED }

    /** Client tools for the model, and the system tools the execution may use. */
    public record ExecutionTools(List<ToolSpecification> clientTools, SystemToolSelection systemTools) {}

    private final SystemToolExecutor systemTools;
    private final Mode mode;

    public AssistantToolPolicy(SystemToolExecutor systemTools, @Value("${agent.tools.system-mode:all}") String mode) {
        this.systemTools = systemTools;
        this.mode = parse(mode);
    }

    static Mode parse(String value) {
        try {
            return Mode.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalStateException("agent.tools.system-mode (AGENT_SYSTEM_TOOLS_MODE) must be 'all' or 'declared', got '"
                    + value + "'");
        }
    }

    public Mode mode() {
        return mode;
    }

    public ExecutionTools resolve(List<AssistantTool> tools) {
        List<AssistantTool> client = tools.stream().filter(t -> !systemTools.isSystem(t.function().name())).toList();
        List<String> declared = tools.stream().map(t -> t.function().name()).filter(systemTools::isSystem).toList();
        return new ExecutionTools(AssistantTool.specifications(client),
                mode == Mode.ALL ? SystemToolSelection.all() : SystemToolSelection.only(declared));
    }
}
