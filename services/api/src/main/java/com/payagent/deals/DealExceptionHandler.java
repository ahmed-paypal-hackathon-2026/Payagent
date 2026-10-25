package com.payagent.deals;

import com.payagent.ai.AiNegotiationException;
import com.payagent.settlement.exception.IdempotencyConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.payagent.deals")
public class DealExceptionHandler {

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "IDEMPOTENCY_CONFLICT",
                "message", ex.getMessage(),
                "status", HttpStatus.CONFLICT.value(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "FORBIDDEN",
                "message", ex.getMessage(),
                "status", HttpStatus.FORBIDDEN.value(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(AiNegotiationException.class)
    public ResponseEntity<Map<String, Object>> handleAiNegotiation(AiNegotiationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "INVALID_TERMS",
                "message", ex.getMessage(),
                "status", HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "BAD_REQUEST",
                "message", ex.getMessage(),
                "status", HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "timestamp", Instant.now().toString()
        ));
    }
}
