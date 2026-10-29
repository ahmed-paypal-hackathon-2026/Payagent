package com.payagent.releasegates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.paypal.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in verification of B's existing real Sandbox artifacts. No create/send/capture/void operations. */
@EnabledIfEnvironmentVariable(named = "PAYAGENT_LIVE_SANDBOX", matches = "true")
class PayPalSandboxReleaseGateIntegrationTest {
    private PayPalProperties properties;
    private PayPalAuthClient auth;

    @BeforeEach
    void configureSandboxOnly() {
        properties = new PayPalProperties();
        properties.setBaseUrl("https://api-m.sandbox.paypal.com");
        properties.setClientId(required("PAYPAL_CLIENT_ID"));
        properties.setClientSecret(required("PAYPAL_CLIENT_SECRET"));
        auth = new PayPalAuthClient(properties, RestClient.builder());
    }

    @Test
    void sandboxInvoiceMatchesExpectedAgreementAmount() {
        var invoice = new PayPalInvoiceService(auth, properties, RestClient.builder())
                .getInvoice(required("PAYAGENT_TEST_INVOICE_ID"));
        assertEquals(required("PAYAGENT_TEST_INVOICE_ID"), invoice.id());
        assertNotNull(invoice.amount());
        assertEquals(required("PAYAGENT_TEST_CURRENCY"), invoice.amount().currencyCode());
        assertEquals(0, expectedMajor().compareTo(new BigDecimal(invoice.amount().value())));
    }

    @Test
    void sandboxOrderMatchesExpectedAgreementAndAuthorizationIntent() {
        var order = new PayPalOrderService(auth, properties, RestClient.builder())
                .getOrder(required("PAYAGENT_TEST_ORDER_ID"));
        assertEquals(required("PAYAGENT_TEST_ORDER_ID"), order.id());
        assertEquals("AUTHORIZE", order.intent());
        assertNotNull(order.purchaseUnits());
        assertEquals(1, order.purchaseUnits().size(), "Payagent agreement uses one purchase unit");
        var amount = order.purchaseUnits().getFirst().amount();
        assertEquals(required("PAYAGENT_TEST_CURRENCY"), amount.currencyCode());
        assertEquals(0, expectedMajor().compareTo(new BigDecimal(amount.value())));
    }

    @Test
    void tst07_realWebhookVerifiesAndTamperedEventFails() throws Exception {
        properties.setWebhookId(required("PAYPAL_WEBHOOK_ID"));
        var proof = new ObjectMapper().readTree(Path.of(required("PAYAGENT_WEBHOOK_PROOF_FILE")).toFile());
        TreeMap<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        proof.get("headers").fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
        for (String name : new String[]{"PAYPAL-AUTH-ALGO", "PAYPAL-CERT-URL", "PAYPAL-TRANSMISSION-ID", "PAYPAL-TRANSMISSION-SIG", "PAYPAL-TRANSMISSION-TIME"}) {
            assertTrue(headers.containsKey(name), "Missing recorded webhook header " + name);
        }
        var verifier = new PayPalWebhookVerifier(auth, properties, RestClient.builder());
        var event = proof.get("event");
        assertNotNull(event);
        assertTrue(verifier.verifySignature(headers.get("PAYPAL-AUTH-ALGO"), headers.get("PAYPAL-CERT-URL"),
                headers.get("PAYPAL-TRANSMISSION-ID"), headers.get("PAYPAL-TRANSMISSION-SIG"),
                headers.get("PAYPAL-TRANSMISSION-TIME"), event), "Use an actual delivered Sandbox event, not a simulator event");
        var tampered = event.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) tampered).put("id", "WH-TAMPERED-RELEASE-PROBE");
        assertFalse(verifier.verifySignature(headers.get("PAYPAL-AUTH-ALGO"), headers.get("PAYPAL-CERT-URL"),
                headers.get("PAYPAL-TRANSMISSION-ID"), headers.get("PAYPAL-TRANSMISSION-SIG"),
                headers.get("PAYPAL-TRANSMISSION-TIME"), tampered));
    }

    private static BigDecimal expectedMajor() {
        return new BigDecimal(required("PAYAGENT_TEST_AMOUNT_MINOR")).movePointLeft(2);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        assertNotNull(value, "Set " + key + " locally before enabling Sandbox probes");
        assertFalse(value.isBlank(), "Set " + key + " locally before enabling Sandbox probes");
        return value;
    }
}
