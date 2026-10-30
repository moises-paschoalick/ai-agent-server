package ai.agent.controller;

import ai.agent.errors.ApiException;
import ai.agent.errors.LlmRateLimitException;
import ai.agent.errors.LlmTimeoutException;
import ai.agent.errors.LlmUpstreamException;
import ai.agent.errors.ValidationException;
import ai.agent.llm.CallOptions;
import ai.agent.llm.LlmGateway;
import ai.agent.llm.LlmResult;
import ai.agent.services.JokeDictionary;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * LLM health check and easter egg: asks the model for a joke and reports
 * which model answered and how long it took. Intentionally left out of the README.
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class PromptController {

    static final int MAX_TERM_LENGTH = 100;
    static final int MAX_MODEL_LENGTH = 200;

    private static final PromptTemplate JOKE_TEMPLATE =
            PromptTemplate.from("Conte uma piada {{adjective}} sobre {{content}}, em português do Brasil.");

    private final LlmGateway gateway;
    private final JokeDictionary dictionary;

    public PromptController(LlmGateway gateway, JokeDictionary dictionary) {
        this.gateway = gateway;
        this.dictionary = dictionary;
    }

    @GetMapping("/joke")
    public JokeResponse randomJoke(@RequestParam(required = false) String model) {
        return joke(model, null, null);
    }

    @PostMapping("/joke")
    public JokeResponse generateJoke(@RequestParam(required = false) String model,
                                     @RequestBody(required = false) JokeRequest request) {
        return request == null ? joke(model, null, null) : joke(model, request.adjective(), request.content());
    }

    private JokeResponse joke(String model, String adjective, String content) {
        if (model != null && model.length() > MAX_MODEL_LENGTH) {
            throw new ValidationException("model must have at most " + MAX_MODEL_LENGTH + " characters");
        }
        String chosenAdjective = termOrRandom("adjective", adjective, dictionary::randomAdjective);
        String chosenContent = termOrRandom("content", content, dictionary::randomContent);

        String prompt = JOKE_TEMPLATE.apply(Map.of(
                "adjective", chosenAdjective,
                "content", chosenContent)).text();

        long start = System.nanoTime();
        LlmResult result;
        try {
            result = gateway.chat(model, List.of(UserMessage.from(prompt)), CallOptions.defaults());
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw translate(e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        String answeredBy = result.response().modelName() != null ? result.response().modelName() : result.modelName();
        return new JokeResponse(chosenAdjective, chosenContent, result.response().aiMessage().text(),
                result.providerId(), answeredBy, latencyMs);
    }

    private static String termOrRandom(String field, String value, java.util.function.Supplier<String> random) {
        if (value == null || value.isBlank()) {
            return random.get();
        }
        if (value.length() > MAX_TERM_LENGTH) {
            throw new ValidationException(field + " must have at most " + MAX_TERM_LENGTH + " characters");
        }
        return value.strip();
    }

    private static ApiException translate(RuntimeException e) {
        log.warn("Language model call failed: {}", e.getClass().getSimpleName());
        if (e instanceof RateLimitException) {
            return new LlmRateLimitException("The language model rate limit was exceeded.");
        }
        if (e instanceof TimeoutException) {
            return new LlmTimeoutException("The language model call timed out.");
        }
        return new LlmUpstreamException("The language model call failed.");
    }

    public record JokeRequest(String adjective, String content) {}

    public record JokeResponse(String adjective,
                               String content,
                               String joke,
                               String provider,
                               String model,
                               @JsonProperty("latency_ms") long latencyMs) {}
}
