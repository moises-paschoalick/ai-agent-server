package ai.agent.services;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adjectives and topics for the /ai/joke health check. Loaded once at startup;
 * a missing or too small dictionary fails the application start.
 */
@Component
public class JokeDictionary {

    static final String RESOURCE = "jokes/dictionary.json";
    static final int MIN_ENTRIES = 20;

    record Terms(List<String> adjectives, List<String> contents) {}

    private final List<String> adjectives;
    private final List<String> contents;

    public JokeDictionary() {
        this(load());
    }

    JokeDictionary(Terms terms) {
        this.adjectives = validate("adjectives", terms.adjectives());
        this.contents = validate("contents", terms.contents());
    }

    public String randomAdjective() {
        return pick(adjectives);
    }

    public String randomContent() {
        return pick(contents);
    }

    public List<String> adjectives() {
        return adjectives;
    }

    public List<String> contents() {
        return contents;
    }

    private static String pick(List<String> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private static List<String> validate(String name, List<String> values) {
        if (values == null || values.size() < MIN_ENTRIES) {
            throw new IllegalStateException("Joke dictionary needs at least " + MIN_ENTRIES + " " + name);
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalStateException("Joke dictionary has duplicate " + name);
        }
        return List.copyOf(values);
    }

    private static Terms load() {
        try (InputStream is = JokeDictionary.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException(RESOURCE + " not found in classpath");
            }
            return new ObjectMapper().readValue(is, Terms.class);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
    }
}
