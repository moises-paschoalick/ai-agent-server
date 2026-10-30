package ai.agent.engine;

import ai.agent.llm.CallOptions;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * What one execution uses: instructions (system message, optional), model value
 * (null = default), the client tools, call options such as temperature and top_p, and the system
 * tools the runner may add (all of them unless said otherwise).
 */
public record RunConfig(String instructions, String model, List<ToolSpecification> tools, CallOptions options,
                        SystemToolSelection systemTools) {

    public RunConfig {
        tools = tools == null ? List.of() : List.copyOf(tools);
        options = options == null ? CallOptions.defaults() : options;
        systemTools = systemTools == null ? SystemToolSelection.all() : systemTools;
    }

    public RunConfig(String instructions, String model, List<ToolSpecification> tools, CallOptions options) {
        this(instructions, model, tools, options, SystemToolSelection.all());
    }

    public RunConfig(String instructions, String model, List<ToolSpecification> tools) {
        this(instructions, model, tools, CallOptions.defaults());
    }
}
