package com.practice.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().toLowerCase().contains("ollama")
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.CONFLICT;
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("already running")) {
            status = HttpStatus.TOO_MANY_REQUESTS;
        }
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
    }
}
