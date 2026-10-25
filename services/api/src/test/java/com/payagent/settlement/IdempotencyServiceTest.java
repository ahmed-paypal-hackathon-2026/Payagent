package com.payagent.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.settlement.exception.IdempotencyConflictException;
import com.payagent.settlement.persistence.IdempotencyKeyEntity;
import com.payagent.settlement.persistence.IdempotencyKeyRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class IdempotencyServiceTest {

    private IdempotencyKeyRepository repository;
    private ObjectMapper objectMapper;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyKeyRepository.class);
        objectMapper = new ObjectMapper();
        service = new IdempotencyService(repository, objectMapper);
    }

    @Test
    void executesAndStoresResponseOnFirstInvocation() {
        when(repository.findByIdempotencyKeyAndRoute("key-1", "/test/route")).thenReturn(Optional.empty());

        AtomicInteger callCount = new AtomicInteger();
        ResponseEntity<String> response = service.execute(
                "key-1",
                "/test/route",
                UUID.randomUUID(),
                "payload-1",
                String.class,
                () -> {
                    callCount.incrementAndGet();
                    return ResponseEntity.ok("success-1");
                }
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("success-1");
        assertThat(callCount.get()).isEqualTo(1);

        verify(repository).save(any(IdempotencyKeyEntity.class));
    }

    @Test
    void replaysStoredResponseWhenCalledAgainWithSamePayload() throws Exception {
        String payload = "payload-1";
        // Pre-calculated SHA-256 for "\"payload-1\""
        String requestHash = service.execute(
                "temp-key",
                "/temp",
                UUID.randomUUID(),
                payload,
                String.class,
                () -> ResponseEntity.ok("temp")
        ).getBody() != null ? "dummy" : "dummy";

        // Let's create an existing entity with matching hash
        ObjectMapper mapper = new ObjectMapper();
        String jsonPayload = mapper.writeValueAsString(payload);
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String expectedHash = java.util.HexFormat.of().formatHex(md.digest(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        IdempotencyKeyEntity existing = new IdempotencyKeyEntity(
                "key-replay",
                "/test/route",
                UUID.randomUUID(),
                expectedHash,
                200,
                "\"cached-success\""
        );
        when(repository.findByIdempotencyKeyAndRoute("key-replay", "/test/route")).thenReturn(Optional.of(existing));

        AtomicInteger callCount = new AtomicInteger();
        ResponseEntity<String> response = service.execute(
                "key-replay",
                "/test/route",
                UUID.randomUUID(),
                payload,
                String.class,
                () -> {
                    callCount.incrementAndGet();
                    return ResponseEntity.ok("new-call");
                }
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("cached-success");
        // Action was NOT executed
        assertThat(callCount.get()).isEqualTo(0);
    }

    @Test
    void throwsConflictWhenSameKeyHasDifferentPayload() {
        IdempotencyKeyEntity existing = new IdempotencyKeyEntity(
                "key-conflict",
                "/test/route",
                UUID.randomUUID(),
                "original-different-hash",
                200,
                "\"original-result\""
        );
        when(repository.findByIdempotencyKeyAndRoute("key-conflict", "/test/route")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.execute(
                "key-conflict",
                "/test/route",
                UUID.randomUUID(),
                "differing-payload",
                String.class,
                () -> ResponseEntity.ok("result")
        )).isInstanceOf(IdempotencyConflictException.class);
    }
}
