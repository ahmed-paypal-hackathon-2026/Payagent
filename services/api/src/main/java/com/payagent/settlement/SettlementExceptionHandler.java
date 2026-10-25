package com.payagent.settlement;

import com.payagent.paypal.PayPalException;
import com.payagent.settlement.exception.IdempotencyConflictException;
import com.payagent.settlement.exception.SettlementConflictException;
import com.payagent.settlement.exception.SettlementForbiddenException;
import com.payagent.settlement.exception.SettlementNotFoundException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = {"com.payagent.settlement", "com.payagent.paypal"})
public class SettlementExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SettlementExceptionHandler.class);

    @ExceptionHandler(SettlementNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(SettlementNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(SettlementForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(SettlementForbiddenException ex) {
        log.warn("Forbidden access: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(SettlementConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(SettlementConflictException ex) {
        log.warn("Settlement conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        log.warn("Idempotency conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(PayPalException.class)
    public ResponseEntity<Map<String, Object>> handlePayPalException(PayPalException ex) {
        log.error("PayPal upstream failure [{}]: {}", ex.getErrorCode(), ex.getMessage());
        HttpStatus status = ex.getStatusCode() >= 400 && ex.getStatusCode() < 500
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.BAD_GATEWAY;
        return buildResponse(status, ex.getErrorCode(), ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", error,
                "message", message,
                "status", status.value(),
                "timestamp", Instant.now().toString()
        ));
    }
}
