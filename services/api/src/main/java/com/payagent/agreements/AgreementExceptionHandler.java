package com.payagent.agreements;

import com.payagent.settlement.exception.IdempotencyConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.payagent.agreements")
public class AgreementExceptionHandler {

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

    @ExceptionHandler(AgreementConflictException.class)
    public ResponseEntity<Map<String, Object>> handleAgreementConflict(AgreementConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "CONFLICT",
                "message", ex.getMessage(),
                "status", HttpStatus.CONFLICT.value(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(AgreementNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAgreementNotFound(AgreementNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "NOT_FOUND",
                "message", ex.getMessage(),
                "status", HttpStatus.NOT_FOUND.value(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(AgreementInvalidInputException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidInput(AgreementInvalidInputException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "BAD_REQUEST",
                "message", ex.getMessage(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "timestamp", Instant.now().toString()
        ));
    }
}
