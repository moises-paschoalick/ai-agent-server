package ai.agent.controller;

import ai.agent.dto.ChatReply;
import ai.agent.errors.ApiException;
import ai.agent.llm.CancelSignal;
import ai.agent.llm.StreamCancelledException;
import ai.agent.services.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/**
 * A /chat or /tools/result answer as server-sent events, written by the request thread itself
 * (which keeps the thread lock): {@code delta} for each piece of text, then exactly one
 * {@code message}, {@code tool_call} or {@code error}. Nothing is written before the first event,
 * so a failure before it (validation, 404, 409, a model error before any text) is still an
 * ordinary JSON error. When the client is gone, the generation is cancelled.
 */
@Slf4j
final class SseResponse {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpServletResponse response;
    private final CancelSignal cancel = new CancelSignal();
    /** Not the writer: PrintWriter hides the IOException that tells the client is gone. */
    private OutputStream out;

    SseResponse(HttpServletResponse response) {
        this.response = response;
    }

    void run(Function<ChatService.Stream, ChatReply> work) {
        try {
            ChatReply reply = work.apply(new ChatService.Stream(text -> send("delta", Map.of("content", text)), cancel));
            send(reply.type(), reply);
        } catch (StreamCancelledException e) {
            log.debug("Stream closed by the client");
        } catch (RuntimeException e) {
            if (out == null) {
                throw e;
            }
            fail(e);
        }
    }

    private void fail(RuntimeException e) {
        Map<String, String> error = e instanceof ApiException api
                ? Map.of("code", api.getCode(), "message", api.getMessage())
                : Map.of("code", "server_error", "message", "Internal server error.");
        if (!(e instanceof ApiException)) {
            log.error("Stream failed", e);
        }
        try {
            send("error", error);
        } catch (StreamCancelledException gone) {
            log.debug("Stream closed by the client before the error event");
        }
    }

    /** One event; the first one commits the response as text/event-stream. */
    private synchronized void send(String event, Object data) {
        if (cancel.isCancelled()) {
            throw new StreamCancelledException();
        }
        try {
            if (out == null) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/event-stream");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("X-Accel-Buffering", "no");
                out = response.getOutputStream();
            }
            out.write(("event: " + event + "\ndata: " + MAPPER.writeValueAsString(data) + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            response.flushBuffer();
        } catch (IOException e) {
            cancel.cancel();
            throw new StreamCancelledException();
        }
    }
}
