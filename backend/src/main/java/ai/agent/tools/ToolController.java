package ai.agent.tools;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tools")
public class ToolController {

    private final ToolService service;
    private final ToolGenerator generator;

    public ToolController(ToolService service, ToolGenerator generator) {
        this.service = service;
        this.generator = generator;
    }

    /** Tool suggested by the model from a description; nothing is saved. */
    public record GeneratedTool(String name, String description, Map<String, Object> parameters) {}

    @GetMapping
    public List<ToolView> list() {
        return service.list();
    }

    @GetMapping("/{name}")
    public ToolView get(@PathVariable String name) {
        return service.get(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ToolView create(@RequestBody ToolDefinition.Request request) {
        return service.create(request);
    }

    @PostMapping("/generate")
    public GeneratedTool generate(@RequestBody ToolGenerator.Request request) {
        ToolDefinition tool = generator.generate(request);
        return new GeneratedTool(tool.name(), tool.description(), tool.parameters());
    }

    @PutMapping("/{name}")
    public ToolView update(@PathVariable String name, @RequestBody ToolDefinition.Request request) {
        return service.update(name, request);
    }

    @DeleteMapping("/{name}")
    public Map<String, String> delete(@PathVariable String name) {
        service.delete(name);
        return Map.of("message", "Tool deleted");
    }
}
