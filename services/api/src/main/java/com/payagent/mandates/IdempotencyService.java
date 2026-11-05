package com.payagent.mandates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Persistent idempotency for mutating mandate endpoints.
 *
 * <p>Behavior per the WP-A2 contract:
 * <ul>
 *   <li>Same {@code Idempotency-Key} + same logical request → replay the cached response.</li>
 *   <li>Same {@code Idempotency-Key} + different request → HTTP 409 Conflict.</li>
 *   <li>Concurrent requests with the same key+route cannot both insert (DB unique constraint);
 *     one wins, the other falls back to the cached response.</li>
 * </ul>
 */
@Service("mandateIdempotencyService")
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate executorTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public IdempotencyService(IdempotencyRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setReadOnly(true);
        this.executorTemplate = new TransactionTemplate(transactionManager);
        this.executorTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executorTemplate.setReadOnly(false);
    }

    /**
     * Execute a request under idempotency protection.
     *
     * @param idempotencyKey the client-supplied idempotency key
     * @param route the request route (e.g. POST /api/v1/deals/{dealId}/mandates)
     * @param dealId the deal the request targets
     * @param requestBody the raw request body bytes (used for the request fingerprint)
     * @param executor the business logic to run if no cached response exists
     * @return the cached or freshly computed response
     */
    @Transactional
    public IdempotentResponse execute(String idempotencyKey, String route, UUID dealId,
                                      byte[] requestBody, IdempotencyExecutor executor) {
        String requestHash = sha256(requestBody);

        IdempotencyKey existing = repository.findByIdempotencyKeyAndRoute(idempotencyKey, route)
                .orElse(null);

        // Fall through intentionally: existing may be null.


        if (existing != null) {
            if (requestHash.equals(existing.getRequestHash())) {
                // Same key + same request → replay cached response.
                return new IdempotentResponse(
                        existing.getResponseStatus(),
                        existing.getResponseBody(),
                        true);
            }
            // Same key + different request → conflict.
            return new IdempotentResponse(409, null, false);
        }

        // No cached entry → execute the business logic in a new transaction to avoid
        // marking the outer transaction rollback-only on business errors.
        IdempotentResponse result = executorTemplate.execute(status -> executor.execute());

        // Persist the idempotency record. If a concurrent request already inserted
        // the same key+route, the unique constraint will throw; fall back to replay.
        try {
            IdempotencyKey record = new IdempotencyKey(
                    idempotencyKey, dealId, route, requestHash,
                    result.status(), result.body());
            repository.save(record);
        } catch (Exception e) {
            // Concurrent insert: reload the winner's record in a new transaction to avoid
            // using a session that has been marked rollback-only.
            IdempotencyKey winner = transactionTemplate.execute(status -> 
                repository.findByIdempotencyKeyAndRoute(idempotencyKey, route).orElse(null)
            );
            if (winner != null && requestHash.equals(winner.getRequestHash())) {
                return new IdempotentResponse(
                        winner.getResponseStatus(), winner.getResponseBody(), true);
            }
            if (winner != null) {
                return new IdempotentResponse(409, null, false);
            }
            throw new RuntimeException("Idempotency record could not be persisted", e);
        }

        return result;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new String(java.util.HexFormat.of().formatHex(digest.digest(data)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Business logic to execute when no cached idempotency response exists.
     */
    @FunctionalInterface
    public interface IdempotencyExecutor {
        IdempotentResponse execute();
    }

    /**
     * Result of an idempotent execution: status code, optional JSON body, and whether it was replayed.
     */
    public record IdempotentResponse(int status, String body, boolean replayed) {}
}
