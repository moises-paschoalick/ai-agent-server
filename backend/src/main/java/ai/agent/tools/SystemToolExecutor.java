package ai.agent.tools;

import ai.agent.tools.system.CalculateTool;
import ai.agent.tools.system.ConvertTimezoneTool;
import ai.agent.tools.system.CurrentDateTimeTool;
import ai.agent.tools.system.DateCalcTool;
import ai.agent.tools.system.SystemTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tools executed by the server itself (package {@code tools.system}). They are declared here, not
 * in the catalog file, and cannot be edited or removed through the API. Adding one is a new
 * {@link SystemTool} in the list below.
 */
@Component
public class SystemToolExecutor {

    public static final String CURRENT_DATE_TIME = CurrentDateTimeTool.NAME;

    private final Map<String, SystemTool> tools = new LinkedHashMap<>();

    @Autowired
    public SystemToolExecutor(Clock clock, @Value("${agent.timezone:America/Sao_Paulo}") String timezone) {
        this(defaults(clock, ZoneId.of(timezone)));
    }

    SystemToolExecutor(List<SystemTool> tools) {
        tools.stream().sorted(Comparator.comparing(SystemTool::name)).forEach(t -> this.tools.put(t.name(), t));
    }

    private static List<SystemTool> defaults(Clock clock, ZoneId zone) {
        return List.of(new CurrentDateTimeTool(clock, zone), new CalculateTool(), new DateCalcTool(clock, zone),
                new ConvertTimezoneTool(clock, zone));
    }

    /** Ordered by name. */
    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(SystemTool::definition).toList();
    }

    public boolean isSystem(String name) {
        return name != null && tools.containsKey(name);
    }

    public String execute(String name, String argumentsJson) {
        SystemTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown system tool '" + name + "'");
        }
        return tool.execute(argumentsJson);
    }
}
