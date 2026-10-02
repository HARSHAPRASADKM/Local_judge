package com.practice.platform.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Shared plumbing for endpoints that stream Server-Sent Events: it owns a small worker
 * pool, forwards {@code stage}/{@code delta} events and always closes the stream with a
 * {@code done} or {@code error} event. The client keeps a single POST open.
 */
@Component
public class SseTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(SseTaskRunner.class);

    /** Long enough to cover the Ollama read timeout plus any judging work. */
    public static final long TIMEOUT_MS = 600_000L;

    /** Tokens are batched to this many characters before a delta is flushed. */
    private static final int DELTA_BATCH_CHARS = 8;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "sse-worker");
        thread.setDaemon(true);
        return thread;
    });

    /** Work to run on a worker thread. May throw; the runner turns failures into an error event. */
    @FunctionalInterface
    public interface Task {
        void execute(SseEmitter emitter) throws Exception;
    }

    public SseEmitter run(Task task) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> log.debug("SSE client disconnected: {}", error.getMessage()));

        executor.submit(() -> {
            try {
                task.execute(emitter);
                emitter.complete();
            } catch (Exception e) {
                send(emitter, "error", Map.of("message", message(e)));
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // already completed or timed out
                }
            }
        });
        return emitter;
    }

    private static String message(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "Request failed. Is Ollama running?" : message;
    }

    public static void stage(SseEmitter emitter, String key, String label) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("label", label);
        send(emitter, "stage", data);
    }

    /** A {@link GenerationProgress} that turns stages into SSE events. */
    public static GenerationProgress stages(SseEmitter emitter) {
        return (key, label) -> stage(emitter, key, label);
    }

    /**
     * A token consumer that streams incremental text as {@code delta} events, batched so a
     * fast model does not flood the connection. Callers still send the complete text with
     * {@code done}, so a partially flushed tail is harmless.
     */
    public static Consumer<String> deltas(SseEmitter emitter) {
        StringBuilder buffer = new StringBuilder();
        return token -> {
            if (token == null || token.isEmpty()) {
                return;
            }
            synchronized (buffer) {
                buffer.append(token);
                if (buffer.length() >= DELTA_BATCH_CHARS) {
                    send(emitter, "delta", Map.of("text", buffer.toString()));
                    buffer.setLength(0);
                }
            }
        };
    }

    public static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            // Browser stopped listening (cancelled or refreshed); work still finishes.
            log.debug("Could not send '{}' event: {}", name, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
