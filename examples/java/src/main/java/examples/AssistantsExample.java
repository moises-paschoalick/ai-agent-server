package examples;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.beta.assistants.Assistant;
import com.openai.models.beta.assistants.AssistantCreateParams;
import com.openai.models.beta.assistants.FunctionTool;
import com.openai.models.beta.threads.Thread;
import com.openai.models.beta.threads.ThreadCreateParams;
import com.openai.models.beta.threads.messages.Message;
import com.openai.models.beta.threads.messages.MessageListParams;
import com.openai.models.beta.threads.runs.RequiredActionFunctionToolCall;
import com.openai.models.beta.threads.runs.Run;
import com.openai.models.beta.threads.runs.RunCreateParams;
import com.openai.models.beta.threads.runs.RunRetrieveParams;
import com.openai.models.beta.threads.runs.RunStatus;
import com.openai.models.beta.threads.runs.RunSubmitToolOutputsParams;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The OpenAI Java SDK talking to Agent Server: the only difference from calling OpenAI is the base URL
 * and the key. Creates an assistant with a client tool, asks a question, runs the tool here when
 * the run asks for it (requires_action), sends the result back and prints the answer.
 *
 * <pre>
 * AGENT_BASE_URL  http://localhost:8080/v1 (default)
 * AGENT_API_KEY   one of AGENT_API_KEYS
 * AGENT_MODEL     openai:gpt-5.4-mini (default), as provider:model
 * </pre>
 */
public class AssistantsExample {

    /** The "backend" of the client tool: in a real client, a database or another API. */
    private static final Map<String, String> ORDERS = Map.of(
            "A-1001", "shipped yesterday, arrives tomorrow",
            "A-1002", "waiting for payment");

    public static void main(String[] args) throws InterruptedException {
        String baseUrl = env("AGENT_BASE_URL", "http://localhost:8080/v1");
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)                      // the only change to use Agent Server instead of OpenAI
                .apiKey(env("AGENT_API_KEY", null))
                .build();

        Assistant assistant = client.beta().assistants().create(AssistantCreateParams.builder()
                .model(env("AGENT_MODEL", "openai:gpt-5.4-mini"))
                .name("Orders (Java example)")
                .instructions("You help customers with their orders. Use get_order_status to look an order up. Answer briefly.")
                .addTool(orderStatusTool())
                .build());
        System.out.println("Assistant " + assistant.id() + " at " + baseUrl);

        try {
            Thread thread = client.beta().threads().create(ThreadCreateParams.builder()
                    .addMessage(ThreadCreateParams.Message.builder()
                            .role(ThreadCreateParams.Message.Role.USER)
                            .content("What is the status of order A-1001?")
                            .build())
                    .build());

            Run run = client.beta().threads().runs().create(RunCreateParams.builder()
                    .threadId(thread.id())
                    .assistantId(assistant.id())
                    .build());

            run = waitForRun(client, run);
            while (run.status().equals(RunStatus.REQUIRES_ACTION)) {
                run = waitForRun(client, submitToolOutputs(client, run));
            }

            if (!run.status().equals(RunStatus.COMPLETED)) {
                System.out.println("Run ended " + run.status() + ": " + run.lastError().map(e -> e.message()).orElse("no details"));
                return;
            }
            System.out.println("Answer: " + lastAnswer(client, thread.id()));
            run.usage().ifPresent(u -> System.out.println("Tokens: " + u.totalTokens()));
        } finally {
            client.beta().assistants().delete(assistant.id());
        }
    }

    /** get_order_status(order_id): a function tool, in the OpenAI format. */
    private static FunctionTool orderStatusTool() {
        return FunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("get_order_status")
                        .description("Current status of an order, by its number.")
                        .parameters(FunctionParameters.builder()
                                .putAdditionalProperty("type", JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "order_id", Map.of("type", "string", "description", "Order number, like A-1001"))))
                                .putAdditionalProperty("required", JsonValue.from(List.of("order_id")))
                                .build())
                        .build())
                .build();
    }

    /** Polls the run every 500 ms while it is queued or in progress (at most 2 minutes). */
    private static Run waitForRun(OpenAIClient client, Run run) throws InterruptedException {
        for (int i = 0; i < 240 && (run.status().equals(RunStatus.QUEUED) || run.status().equals(RunStatus.IN_PROGRESS)
                || run.status().equals(RunStatus.CANCELLING)); i++) {
            java.lang.Thread.sleep(500);
            run = client.beta().threads().runs().retrieve(RunRetrieveParams.builder()
                    .threadId(run.threadId())
                    .runId(run.id())
                    .build());
        }
        return run;
    }

    /** Runs every tool call the model asked for, here on the client, and sends the results back. */
    private static Run submitToolOutputs(OpenAIClient client, Run run) {
        List<RequiredActionFunctionToolCall> calls = run.requiredAction().orElseThrow().submitToolOutputs().toolCalls();
        RunSubmitToolOutputsParams.Builder outputs = RunSubmitToolOutputsParams.builder()
                .threadId(run.threadId())
                .runId(run.id());
        for (RequiredActionFunctionToolCall call : calls) {
            String output = call.function().name().equals("get_order_status")
                    ? orderStatus(call.function().arguments())
                    : "Unknown tool '" + call.function().name() + "'.";
            System.out.println("Tool " + call.function().name() + " " + call.function().arguments() + " -> " + output);
            outputs.addToolOutput(RunSubmitToolOutputsParams.ToolOutput.builder()
                    .toolCallId(call.id())
                    .output(output)
                    .build());
        }
        return client.beta().threads().runs().submitToolOutputs(outputs.build());
    }

    /** The arguments are a JSON string, as the model wrote them: {"order_id":"A-1001"}. */
    private static String orderStatus(String argumentsJson) {
        Matcher id = Pattern.compile("\"order_id\"\\s*:\\s*\"([^\"]+)\"").matcher(argumentsJson);
        if (!id.find()) {
            return "Missing order_id.";
        }
        return ORDERS.getOrDefault(id.group(1), "Order " + id.group(1) + " not found.");
    }

    /** The newest message of the thread is the assistant's answer. */
    private static String lastAnswer(OpenAIClient client, String threadId) {
        Message message = client.beta().threads().messages().list(MessageListParams.builder()
                .threadId(threadId)
                .limit(1)
                .build()).data().getFirst();
        return message.content().getFirst().text().map(t -> t.text().value()).orElse("(no text)");
    }

    /** .env in the working directory (the project root under Gradle/IntelliJ); KEY=VALUE is valid properties syntax. */
    private static final Properties DOT_ENV = load(Path.of(".env"));

    /** application.properties from the classpath, with the non-secret defaults. */
    private static final Properties APP_PROPERTIES = loadResource("application.properties");

    /** Looks a setting up in the environment, then .env, then application.properties, then the fallback. */
    private static String env(String name, String fallback) {
        for (String value : new String[]{System.getenv(name), DOT_ENV.getProperty(name), APP_PROPERTIES.getProperty(name)}) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        if (fallback == null) {
            throw new IllegalStateException(name + " is required (environment, .env or application.properties).");
        }
        return fallback;
    }

    private static Properties load(Path file) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file.toAbsolutePath(), e);
            }
        }
        return properties;
    }

    private static Properties loadResource(String name) {
        Properties properties = new Properties();
        try (InputStream in = AssistantsExample.class.getClassLoader().getResourceAsStream(name)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + name, e);
        }
        return properties;
    }
}
