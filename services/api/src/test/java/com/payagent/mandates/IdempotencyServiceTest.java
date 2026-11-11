package com.payagent.mandates;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IdempotencyServiceTest {

    @Autowired private IdempotencyService idempotencyService;
    @Autowired private IdempotencyRepository idempotencyRepository;

    // -------------------------------------------------------- replay / conflict

    @Test
    void sameKeySameRequest_replaysCachedResponse() {
        String key = "replay-key-1";
        String route = "POST /test/mandates";
        byte[] body = "{\"test\":\"body\"}".getBytes();

        IdempotencyService.IdempotentResponse first = idempotencyService.execute(
                key, route, null, body,
                () -> new IdempotencyService.IdempotentResponse(201, "{\"created\":true}", false));

        assertEquals(201, first.status());
        assertEquals("{\"created\":true}", first.body());
        assertFalse(first.replayed());

        // Same key + same body → replay cached response
        IdempotencyService.IdempotentResponse replay = idempotencyService.execute(
                key, route, null, body,
                () -> new IdempotencyService.IdempotentResponse(201, "{\"created\":true}", false));

        assertEquals(201, replay.status());
        assertEquals("{\"created\":true}", replay.body());
        assertTrue(replay.replayed());
    }

    @Test
    void sameKeyDifferentRequest_returns409() {
        String key = "conflict-key-1";
        String route = "POST /test/mandates";
        byte[] body1 = "{\"test\":\"body1\"}".getBytes();
        byte[] body2 = "{\"test\":\"body2\"}".getBytes();

        IdempotencyService.IdempotentResponse first = idempotencyService.execute(
                key, route, null, body1,
                () -> new IdempotencyService.IdempotentResponse(201, "{\"created\":true}", false));

        assertEquals(201, first.status());
        assertFalse(first.replayed());

        // Same key + different body → 409 Conflict
        IdempotencyService.IdempotentResponse conflict = idempotencyService.execute(
                key, route, null, body2,
                () -> new IdempotencyService.IdempotentResponse(201, "{\"created\":true}", false));

        assertEquals(409, conflict.status());
        assertFalse(conflict.replayed());
    }

    @Test
    void differentKeysAreIndependent() {
        String route = "POST /test/mandates";
        byte[] body = "{\"test\":\"body\"}".getBytes();

        IdempotencyService.IdempotentResponse r1 = idempotencyService.execute(
                "key-independent-1", route, null, body,
                () -> new IdempotencyService.IdempotentResponse(201, "{\"r1\":true}", false));
        IdempotencyService.IdempotentResponse r2 = idempotencyService.execute(
                "key-independent-2", route, null, body,
                () -> new IdempotencyService.IdempotentResponse(201, "{\"r2\":true}", false));

        assertEquals(201, r1.status());
        assertEquals(201, r2.status());
        assertFalse(r1.replayed());
        assertFalse(r2.replayed());
        assertNotEqual(r1.body(), r2.body());
    }

    // -------------------------------------------------------- concurrency

    @Test
    void concurrentSameKeyOnlyOneRecordPersisted() throws Exception {
        String key = "concurrent-key-1";
        String route = "POST /test/mandates";
        byte[] body = "{\"test\":\"body\"}".getBytes();

        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    IdempotencyService.IdempotentResponse result = idempotencyService.execute(
                            key, route, null, body,
                            () -> new IdempotencyService.IdempotentResponse(201, "{\"created\":true}", false));
                    switch (result.status()) {
                        case 201 -> successCount.incrementAndGet();
                        case 409 -> conflictCount.incrementAndGet();
                        default -> otherCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        int totalHandled = successCount.get() + conflictCount.get() + otherCount.get();
        assertEquals(threadCount, totalHandled,
                "All threads should complete: success=" + successCount + ", conflict=" + conflictCount
                        + ", other=" + otherCount);

        // Exactly one idempotency record should exist for this route in the database.
        // Prior tests may have created unrelated records, so count only this route.
        long recordCount = idempotencyRepository.findAll().stream()
                .filter(r -> r.getRoute().equals(route))
                .count();
        assertEquals(1, recordCount,
                "Exactly one idempotency record should exist for route " + route + ", found " + recordCount);
    }

    private static void assertNotEqual(Object a, Object b) {
        if (a.equals(b)) {
            throw new AssertionError("Expected different values but both are <" + a + ">");
        }
    }
}
