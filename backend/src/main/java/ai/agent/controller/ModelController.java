package ai.agent.controller;

import ai.agent.services.ChatSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Global model of /chat (in memory until restart); same formats as any model value. */
@RestController
@RequestMapping("/model")
@RequiredArgsConstructor
public class ModelController {

    private final ChatSettings settings;

    public record ModelValue(String model) {}

    @GetMapping
    public ModelValue get() {
        return new ModelValue(settings.model());
    }

    @PutMapping
    public ModelValue put(@RequestBody ModelValue body) {
        settings.setModel(body.model());
        return get();
    }
}
