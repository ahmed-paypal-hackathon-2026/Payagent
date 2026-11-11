package com.payagent.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end webhook reconciliation against a disposable PostgreSQL database.
 * Verifies Flyway-migrated schema, atomic event deduplication, transition
 * policy and rollback of the event record when the handler fails.
 */
@SpringBootTest
class SettlementWebhookPostgreSqlServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String serviceUrl = System.getenv("PAYAGENT_B17_SERVICE_URL");

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("PAYAGENT_B17_SERVICE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("PAYAGENT_B17_PG_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("PAYAGENT_B17_PG_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("payagent.evidence.signing-secret", () -> "pg-test-evidence-signing-secret-b17-32bytes");
        registry.add("payagent.jwt.secret", () -> "pg-test-jwt-secret-b17-testing-only-not-production");
    }

    @Autowired
    private SettlementReconciliationService reconciliationService;

    @Test
    void captureCompletedEventPersistsAuditedStateAndIsIdempotent() throws Exception {
        Assumptions.assumeTrue(serviceUrl != null && !serviceUrl.isBlank(), "Disposable PG test database not provided");

        UUID userId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String eventId = "B17-SVC-" + suffix;
        String authId = "AUTH-SVC-" + suffix;
        String captureId = "CAP-SVC-" + suffix;
        String invoiceNumber = "INV-SVC-" + suffix;

        try (Connection c = DriverManager.getConnection(serviceUrl,
                System.getenv("PAYAGENT_B17_PG_USER"), System.getenv("PAYAGENT_B17_PG_PASSWORD"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)")) {
                ps.setObject(1, userId);
                ps.setString(2, "buyer-" + suffix + "@example.com");
                ps.setString(3, "hash");
                ps.executeUpdate();
                ps.setObject(1, sellerId);
                ps.setString(2, "seller-" + suffix + "@example.com");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO deals (id, buyer_user_id, seller_user_id, status) VALUES (?, ?, ?, 'AGREED')")) {
                ps.setObject(1, dealId);
                ps.setObject(2, userId);
                ps.setObject(3, sellerId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO payments (id, deal_id, agreement_version, lifecycle_status, paypal_order_id, paypal_authorization_id, amount_minor, currency) VALUES (?, ?, 1, 'AUTHORIZED', ?, ?, 98000, 'USD')")) {
                ps.setObject(1, paymentId);
                ps.setObject(2, dealId);
                ps.setString(3, "ORD-SVC-" + suffix);
                ps.setString(4, authId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO invoices (id, deal_id, agreement_version, lifecycle_status, paypal_invoice_id, paypal_invoice_number, amount_minor, currency) VALUES (?, ?, 1, 'SENT', ?, ?, 98000, 'USD')")) {
                ps.setObject(1, invoiceId);
                ps.setObject(2, dealId);
                ps.setString(3, "INVOICEV2-" + suffix);
                ps.setString(4, invoiceNumber);
                ps.executeUpdate();
            }
        }

        var resource = objectMapper.readTree("""
                {
                  "id": "%s",
                  "supplementary_data": { "related_ids": { "authorization_id": "%s" } }
                }
                """.formatted(captureId, authId));
        var result = reconciliationService.processWebhookEvent(eventId, "PAYMENT.CAPTURE.COMPLETED", captureId, resource, "{}");

        assertThat(result).isEqualTo(SettlementReconciliationService.WebhookProcessingResult.PROCESSED);

        try (Connection c = DriverManager.getConnection(serviceUrl,
                System.getenv("PAYAGENT_B17_PG_USER"), System.getenv("PAYAGENT_B17_PG_PASSWORD"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT lifecycle_status, paypal_capture_id FROM payments WHERE id = ?")) {
                ps.setObject(1, paymentId);
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("COMPLETED");
                assertThat(rs.getString(2)).isEqualTo(captureId);
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT lifecycle_status FROM invoices WHERE id = ?")) {
                ps.setObject(1, invoiceId);
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("PAID");
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM paypal_events WHERE event_id = ?")) {
                ps.setString(1, eventId);
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            int auditCount = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM audit_events WHERE deal_id = ?")) {
                ps.setObject(1, dealId);
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                auditCount = rs.getInt(1);
            }
            assertThat(auditCount).isGreaterThanOrEqualTo(1);
        }

        // Duplicate delivery is ignored and does not create duplicate audit rows.
        int auditBefore = 0;
        try (Connection c = DriverManager.getConnection(serviceUrl,
                System.getenv("PAYAGENT_B17_PG_USER"), System.getenv("PAYAGENT_B17_PG_PASSWORD"));
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM audit_events WHERE deal_id = ?")) {
            ps.setObject(1, dealId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            auditBefore = rs.getInt(1);
        }

        var duplicate = reconciliationService.processWebhookEvent(eventId, "PAYMENT.CAPTURE.COMPLETED", captureId, resource, "{}");
        assertThat(duplicate).isEqualTo(SettlementReconciliationService.WebhookProcessingResult.DUPLICATE);

        try (Connection c = DriverManager.getConnection(serviceUrl,
                System.getenv("PAYAGENT_B17_PG_USER"), System.getenv("PAYAGENT_B17_PG_PASSWORD"));
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM audit_events WHERE deal_id = ?")) {
            ps.setObject(1, dealId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(auditBefore);
        }
    }

    @Test
    void failingHandlerRollsBackEventRecordAndFinancialMutation() throws Exception {
        Assumptions.assumeTrue(serviceUrl != null && !serviceUrl.isBlank(), "Disposable PG test database not provided");

        // Deterministic rollback trigger: the update to COMPLETED fails, so the
        // whole transaction (event record, payment, invoice, audit) must roll back.
        UUID paymentId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String eventId = "B17-ROLLBACK-" + suffix;
        String authId = "AUTH-ROLLBACK-" + suffix;

        try (Connection c = DriverManager.getConnection(serviceUrl,
                System.getenv("PAYAGENT_B17_PG_USER"), System.getenv("PAYAGENT_B17_PG_PASSWORD"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)")) {
                ps.setObject(1, userId);
                ps.setString(2, "rollback-buyer-" + suffix + "@example.com");
                ps.setString(3, "hash");
                ps.executeUpdate();
                ps.setObject(1, sellerId);
                ps.setString(2, "rollback-seller-" + suffix + "@example.com");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO deals (id, buyer_user_id, seller_user_id, status) VALUES (?, ?, ?, 'AGREED')")) {
                ps.setObject(1, dealId);
                ps.setObject(2, userId);
                ps.setObject(3, sellerId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO payments (id, deal_id, agreement_version, lifecycle_status, paypal_authorization_id, amount_minor, currency) VALUES (?, ?, 1, 'AUTHORIZED', ?, 98000, 'USD')")) {
                ps.setObject(1, paymentId);
                ps.setObject(2, dealId);
                ps.setString(3, authId);
                ps.executeUpdate();
            }
        }

        try (Connection c = DriverManager.getConnection(serviceUrl,
                System.getenv("PAYAGENT_B17_PG_USER"), System.getenv("PAYAGENT_B17_PG_PASSWORD"))) {
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE OR REPLACE FUNCTION b17_fail_payment() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                            + "IF NEW.id = '" + paymentId + "'::uuid THEN RAISE EXCEPTION 'b17 deterministic rollback trigger'; END IF; "
                            + "RETURN NEW; END $$")) {
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DROP TRIGGER IF EXISTS b17_fail_payment_trigger ON payments")) {
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TRIGGER b17_fail_payment_trigger BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION b17_fail_payment()")) {
                ps.execute();
            }
        }

        try {
            var resource = objectMapper.readTree("""
                    {
                      "id": "CAP-ROLLBACK",
                      "supplementary_data": { "related_ids": { "authorization_id": "%s" } }
                    }
                    """.formatted(authId));

            assertThatThrownBy(() -> reconciliationService.processWebhookEvent(eventId, "PAYMENT.CAPTURE.COMPLETED", "CAP-ROLLBACK", resource, "{}"))
                    .isInstanceOf(Exception.class);

            try (Connection c = DriverManager.getConnection(serviceUrl,
                    System.getenv("PAYAGENT_B17_PG_USER"), System.getenv("PAYAGENT_B17_PG_PASSWORD"))) {
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM paypal_events WHERE event_id = ?")) {
                    ps.setString(1, eventId);
                    ResultSet rs = ps.executeQuery();
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT lifecycle_status FROM payments WHERE id = ?")) {
                    ps.setObject(1, paymentId);
                    ResultSet rs = ps.executeQuery();
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("AUTHORIZED");
                }
            }
        } finally {
            try (Connection c = DriverManager.getConnection(serviceUrl,
                    System.getenv("PAYAGENT_B17_PG_USER"), System.getenv("PAYAGENT_B17_PG_PASSWORD"))) {
                try (PreparedStatement ps = c.prepareStatement("DROP TRIGGER IF EXISTS b17_fail_payment_trigger ON payments")) {
                    ps.execute();
                }
                try (PreparedStatement ps = c.prepareStatement("DROP FUNCTION IF EXISTS b17_fail_payment()")) {
                    ps.execute();
                }
            }
        }
    }
}
