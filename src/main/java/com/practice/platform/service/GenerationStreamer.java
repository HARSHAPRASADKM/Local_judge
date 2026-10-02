package com.practice.platform.service;

import com.practice.platform.entity.Problem;
import com.practice.platform.entity.UserAccount;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs one problem generation off the request thread and forwards its stages to an
 * {@link SseEmitter}. The client keeps a single POST open and receives {@code stage}
 * events, then a final {@code done} (with the new problem id) or {@code error} event.
 */
@Component
public class GenerationStreamer {

    private final GenerationService generation;
    private final SseTaskRunner runner;

    public GenerationStreamer(GenerationService generation, SseTaskRunner runner) {
        this.generation = generation;
        this.runner = runner;
    }

    public SseEmitter stream(UserAccount user, String prompt, String difficulty, String topic) {
        return runner.run(emitter -> {
            Problem problem = generation.generate(user, prompt, difficulty, topic,
                    SseTaskRunner.stages(emitter));

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("id", problem.getId());
            done.put("title", problem.getTitle());
            SseTaskRunner.send(emitter, "done", done);
        });
    }
}
