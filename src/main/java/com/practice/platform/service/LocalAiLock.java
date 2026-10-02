package com.practice.platform.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
public class LocalAiLock {
    private final ReentrantLock lock = new ReentrantLock();

    public void runExclusive(Runnable task) {
        if (!lock.tryLock()) {
            throw new IllegalStateException("Another generate or hint job is already running. Wait until it finishes.");
        }
        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }

    public <T> T callExclusive(java.util.concurrent.Callable<T> task) {
        if (!lock.tryLock()) {
            throw new IllegalStateException("Another generate or hint job is already running. Wait until it finishes.");
        }
        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }
}
