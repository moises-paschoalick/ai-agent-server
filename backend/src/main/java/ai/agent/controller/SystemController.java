package ai.agent.controller;

import ai.agent.services.ChatSettings;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Global system prompt of /chat (in memory until restart). */
@RestController
@RequestMapping("/system-prompt")
@RequiredArgsConstructor
public class SystemController {

    private final ChatSettings settings;

    public record SystemPrompt(@JsonProperty("system_prompt") String systemPrompt) {}

    @GetMapping
    public SystemPrompt get() {
        return new SystemPrompt(settings.systemPrompt());
    }

    @PutMapping
    public SystemPrompt put(@RequestBody SystemPrompt body) {
        settings.setSystemPrompt(body.systemPrompt());
        return get();
    }
}
