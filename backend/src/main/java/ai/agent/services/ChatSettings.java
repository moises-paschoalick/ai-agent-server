package ai.agent.services;

import ai.agent.errors.ValidationException;
import ai.agent.llm.ModelRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Global system prompt and model of the sync chat. Kept in memory: a PUT lasts until the next
 * restart, and the initial values come from agent.chat.system-prompt and agent.llm.default.
 */
@Component
public class ChatSettings {

    static final int MAX_SYSTEM_PROMPT_LENGTH = 20_000;

    private final ModelRouter router;
    private final String defaultModel;
    private volatile String systemPrompt;
    private volatile String model;

    public ChatSettings(ModelRouter router,
                        @Value("${agent.chat.system-prompt:}") String systemPrompt,
                        @Value("${agent.llm.default}") String defaultModel) {
        this.router = router;
        this.systemPrompt = systemPrompt;
        this.defaultModel = defaultModel;
        this.model = defaultModel;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    /** Empty means no system prompt. */
    public void setSystemPrompt(String value) {
        if (value == null) {
            throw new ValidationException("system_prompt is required (use \"\" for none)");
        }
        if (value.length() > MAX_SYSTEM_PROMPT_LENGTH) {
            throw new ValidationException("system_prompt must have at most " + MAX_SYSTEM_PROMPT_LENGTH + " characters");
        }
        this.systemPrompt = value;
    }

    public String model() {
        return model;
    }

    /** Empty goes back to agent.llm.default; anything else must resolve to an active provider. */
    public void setModel(String value) {
        if (value == null || value.isBlank()) {
            this.model = defaultModel;
            return;
        }
        router.resolve(value);
        this.model = value.strip();
    }
}
