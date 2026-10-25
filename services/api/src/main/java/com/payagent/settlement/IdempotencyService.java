package com.payagent.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.settlement.exception.IdempotencyConflictException;
import com.payagent.settlement.persistence.IdempotencyKeyEntity;
import com.payagent.settlement.persistence.IdempotencyKeyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> ResponseEntity<T> execute(String idempotencyKey, String route, UUID dealId, Object requestPayload,
                                         Class<T> responseClass, Supplier<ResponseEntity<T>> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        String requestHash = computeHash(requestPayload);
        Optional<IdempotencyKeyEntity> existingOpt = idempotencyKeyRepository.findByIdempotencyKeyAndRoute(idempotencyKey, route);

        if (existingOpt.isPresent()) {
            IdempotencyKeyEntity existing = existingOpt.get();
            if (!existing.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency conflict for key={} route={}: payload hash mismatch", idempotencyKey, route);
                throw new IdempotencyConflictException("Idempotency key '" + idempotencyKey + "' has already been used with differing request parameters.");
            }

            log.info("Returning cached idempotent response for key={} route={}", idempotencyKey, route);
            try {
                T cachedBody = objectMapper.readValue(existing.getResponseBody(), responseClass);
                return ResponseEntity.status(existing.getResponseStatus()).body(cachedBody);
            } catch (Exception e) {
                log.error("Failed to deserialize cached response for key {}: {}", idempotencyKey, e.getMessage());
                throw new IllegalStateException("Failed to replay cached idempotent response", e);
            }
        }

        ResponseEntity<T> response = action.get();

        try {
            String responseBodyJson = objectMapper.writeValueAsString(response.getBody());
            IdempotencyKeyEntity record = new IdempotencyKeyEntity(
                    idempotencyKey,
                    route,
                    dealId,
                    requestHash,
                    response.getStatusCode().value(),
                    responseBodyJson
            );
            idempotencyKeyRepository.save(record);
        } catch (Exception e) {
            log.warn("Failed to store idempotency record for key {}: {}", idempotencyKey, e.getMessage());
        }

        return response;
    }

    private String computeHash(Object payload) {
        try {
            String str = payload != null ? objectMapper.writeValueAsString(payload) : "";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to calculate request hash for idempotency", e);
        }
    }
}
