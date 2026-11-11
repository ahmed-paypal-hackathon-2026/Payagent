package com.payagent.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Raw-JDBC PostgreSQL semantics for the atomic event insert used by the
 * webhook reconciliation pipeline. Confirms a unique-event conflict is
 * resolved by the database, not by a check-then-write race.
 */
class PayPalEventsPostgreSqlConcurrencyTest {

    private String url;
    private String user;
    private String password;
    private final String eventId = "B17-PG-TEST-" + System.nanoTime();

    @BeforeEach
    void setUp() {
        url = System.getenv("PAYAGENT_B17_SERVICE_URL");
        user = System.getenv("PAYAGENT_B17_PG_USER");
        password = System.getenv("PAYAGENT_B17_PG_PASSWORD");
        assumeTrue(url != null && user != null && password != null, "Disposable PG test database not provided");

        Flyway.configure().dataSource(url, user, password).load().migrate();
        try (Connection c = DriverManager.getConnection(url, user, password); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM paypal_events WHERE event_id = ?")) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clean test event rows", e);
        }
    }

    @Test
    void concurrentInsertsResultInExactlyOneClaim() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        int[] inserted = new int[2];
        Thread[] threads = new Thread[2];

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                try (Connection c = DriverManager.getConnection(url, user, password)) {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO paypal_events (event_id, event_type, resource_id, payload_json, received_at) "
                                    + "VALUES (?, ?, ?, CAST(? AS jsonb), NOW()) ON CONFLICT (event_id) DO NOTHING")) {
                        ps.setString(1, eventId);
                        ps.setString(2, "PAYMENT.CAPTURE.COMPLETED");
                        ps.setString(3, "CAP-CONCURRENT");
                        ps.setString(4, "{\"id\":\"CAP-CONCURRENT\"}");
                        inserted[idx] = ps.executeUpdate();
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        threads[0].start();
        threads[1].start();
        assertTrue(ready.await(5, TimeUnit.SECONDS), "threads failed to reach insert point");
        start.countDown();
        threads[0].join(15_000);
        threads[1].join(15_000);

        int claims = inserted[0] + inserted[1];
        assertEquals(1, claims, "exactly one concurrent insert may claim the event");

        try (Connection c = DriverManager.getConnection(url, user, password); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM paypal_events WHERE event_id = ?")) {
            ps.setString(1, eventId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void openTransactionRollbackRemovesEventClaim() throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO paypal_events (event_id, event_type, resource_id, payload_json, received_at) "
                            + "VALUES (?, ?, ?, CAST(? AS jsonb), NOW()) ON CONFLICT (event_id) DO NOTHING")) {
                ps.setString(1, eventId);
                ps.setString(2, "PAYMENT.CAPTURE.COMPLETED");
                ps.setString(3, "CAP-ROLLBACK");
                ps.setString(4, "{}");
                ps.executeUpdate();
            }
            c.rollback();
        }

        try (Connection c = DriverManager.getConnection(url, user, password); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM paypal_events WHERE event_id = ?")) {
            ps.setString(1, eventId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }
}
