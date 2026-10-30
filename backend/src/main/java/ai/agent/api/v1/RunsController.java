package ai.agent.api.v1;

import ai.agent.api.v1.dto.ListResponse;
import ai.agent.api.v1.dto.RunView;
import ai.agent.common.ListQuery;
import ai.agent.memory.ConversationStore;
import ai.agent.runs.RunRequest;
import ai.agent.runs.RunService;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v1/threads")
public class RunsController {

    private final RunService runs;
    private final ConversationStore conversations;

    public RunsController(RunService runs, ConversationStore conversations) {
        this.runs = runs;
        this.conversations = conversations;
    }

    @PostMapping("/{threadId}/runs")
    public RunView create(@PathVariable String threadId, @RequestBody(required = false) JsonNode body) {
        conversations.thread(threadId);
        return RunView.of(runs.create(threadId, RunRequests.parse(body, threadId)));
    }

    /** Creates a thread (messages, metadata) and a run in one call; nothing is created if the run is invalid. */
    @PostMapping("/runs")
    public RunView createThreadAndRun(@RequestBody(required = false) JsonNode body) {
        JsonNode thread = body == null ? null : body.get("thread");
        List<MessageInput> messages = new ArrayList<>();
        JsonNode initial = thread == null ? null : thread.get("messages");
        if (initial != null && initial.isArray()) {
            initial.forEach(m -> messages.add(MessageInput.parse(m)));
        }
        var metadata = RunRequests.metadata(thread == null ? null : thread.get("metadata"));
        RunRequest request = RunRequests.parse(body, null);
        runs.prepare(null, request);

        String threadId = conversations.create(metadata);
        messages.forEach(m -> conversations.append(threadId, m.toStored(threadId)));
        return RunView.of(runs.create(threadId, request));
    }

    @GetMapping("/{threadId}/runs")
    public ListResponse<RunView> list(@PathVariable String threadId,
                                      @RequestParam(required = false) Integer limit,
                                      @RequestParam(required = false) String order,
                                      @RequestParam(required = false) String after,
                                      @RequestParam(required = false) String before) {
        return ListResponse.of(runs.list(threadId, ListQuery.of(limit, order, after, before)).map(RunView::of));
    }

    @GetMapping("/{threadId}/runs/{runId}")
    public RunView get(@PathVariable String threadId, @PathVariable String runId) {
        return RunView.of(runs.get(threadId, runId));
    }

    @PostMapping("/{threadId}/runs/{runId}/submit_tool_outputs")
    public RunView submitToolOutputs(@PathVariable String threadId, @PathVariable String runId,
                                     @RequestBody(required = false) JsonNode body) {
        return RunView.of(runs.submitToolOutputs(threadId, runId, RunRequests.toolOutputs(body)));
    }

    @PostMapping("/{threadId}/runs/{runId}/cancel")
    public RunView cancel(@PathVariable String threadId, @PathVariable String runId) {
        return RunView.of(runs.cancel(threadId, runId));
    }
}
