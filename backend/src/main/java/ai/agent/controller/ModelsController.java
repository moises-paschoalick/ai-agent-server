package ai.agent.controller;

import ai.agent.llm.ModelCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Models a client can pick for /chat (the front's model selector). */
@RestController
@RequiredArgsConstructor
public class ModelsController {

    private final ModelCatalog catalog;

    @GetMapping("/models")
    public List<String> models() {
        return catalog.models();
    }
}
