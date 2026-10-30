package ai.agent.assistants;

import ai.agent.tools.SchemaConverter;
import ai.agent.tools.ToolDefinition;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;
import java.util.Map;

/**
 * A tool of an assistant, as in the OpenAI format; only {@code type = "function"} is kept.
 */
public record AssistantTool(String type, Function function) {

    public record Function(String name, String description, Map<String, Object> parameters, Boolean strict) {}

    public static AssistantTool function(String name, String description, Map<String, Object> parameters, Boolean strict) {
        return new AssistantTool("function", new Function(name, description, parameters, strict));
    }

    /** What the model receives for these tools (used by /v1 runs and by sync threads of an assistant). */
    public static List<ToolSpecification> specifications(List<AssistantTool> tools) {
        return tools.stream()
                .map(t -> SchemaConverter.toSpecification(new ToolDefinition(t.function().name(), t.function().description(),
                        t.function().parameters(), Boolean.TRUE.equals(t.function().strict()))))
                .toList();
    }
}
