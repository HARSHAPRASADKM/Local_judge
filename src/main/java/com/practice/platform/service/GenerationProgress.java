package com.practice.platform.service;

/**
 * Receives coarse stage updates while a problem is being generated, so a caller
 * can stream real progress to the browser instead of showing a spinner.
 */
@FunctionalInterface
public interface GenerationProgress {

    /**
     * @param key   stable identifier of the stage (e.g. {@code prompt}, {@code selfcheck})
     * @param label human readable description shown in the UI
     */
    void stage(String key, String label);

    /** No-op sink for callers that do not care about progress. */
    GenerationProgress NONE = (key, label) -> {
    };
}
