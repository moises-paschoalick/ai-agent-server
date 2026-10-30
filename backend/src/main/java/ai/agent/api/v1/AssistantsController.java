package ai.agent.api.v1;

import ai.agent.api.v1.dto.AssistantView;
import ai.agent.api.v1.dto.DeletedResponse;
import ai.agent.api.v1.dto.ListResponse;
import ai.agent.assistants.AssistantService;
import ai.agent.common.ListQuery;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/v1/assistants")
public class AssistantsController {

    private final AssistantService service;

    public AssistantsController(AssistantService service) {
        this.service = service;
    }

    @PostMapping
    public AssistantView create(@RequestBody(required = false) JsonNode body) {
        return AssistantView.of(service.create(body));
    }

    @GetMapping
    public ListResponse<AssistantView> list(@RequestParam(required = false) Integer limit,
                                            @RequestParam(required = false) String order,
                                            @RequestParam(required = false) String after,
                                            @RequestParam(required = false) String before) {
        return ListResponse.of(service.list(ListQuery.of(limit, order, after, before)).map(AssistantView::of));
    }

    @GetMapping("/{id}")
    public AssistantView get(@PathVariable String id) {
        return AssistantView.of(service.get(id));
    }

    @PostMapping("/{id}")
    public AssistantView update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return AssistantView.of(service.update(id, body));
    }

    @DeleteMapping("/{id}")
    public DeletedResponse delete(@PathVariable String id) {
        service.delete(id);
        return DeletedResponse.of(id, "assistant");
    }
}
