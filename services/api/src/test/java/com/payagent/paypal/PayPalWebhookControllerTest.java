package com.payagent.paypal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.settlement.SettlementReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PayPalWebhookControllerTest {

    private PayPalWebhookVerifier webhookVerifier;
    private SettlementReconciliationService reconciliationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        webhookVerifier = mock(PayPalWebhookVerifier.class);
        reconciliationService = mock(SettlementReconciliationService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        PayPalWebhookController controller = new PayPalWebhookController(
                webhookVerifier,
                reconciliationService,
                objectMapper
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void rejectsTamperedOrInvalidSignatureWith400() throws Exception {
        when(webhookVerifier.verifySignature(any(), any(), any(), any(), any(), any()))
                .thenReturn(false);

        mockMvc.perform(post("/api/v1/paypal/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
                        .header("PAYPAL-CERT-URL", "https://api.sandbox.paypal.com/cert.pem")
                        .header("PAYPAL-TRANSMISSION-ID", "tx-tampered")
                        .header("PAYPAL-TRANSMISSION-SIG", "invalid-sig")
                        .header("PAYPAL-TRANSMISSION-TIME", "2026-10-25T12:00:00Z")
                        .content("{\"id\":\"WH-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SIGNATURE"));
    }

    @Test
    void acceptsValidSignatureWith200AndDispatchesEvent() throws Exception {
        when(webhookVerifier.verifySignature(any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
        when(reconciliationService.processWebhookEvent(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(true);

        mockMvc.perform(post("/api/v1/paypal/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
                        .header("PAYPAL-CERT-URL", "https://api.sandbox.paypal.com/cert.pem")
                        .header("PAYPAL-TRANSMISSION-ID", "tx-valid-1")
                        .header("PAYPAL-TRANSMISSION-SIG", "valid-signature")
                        .header("PAYPAL-TRANSMISSION-TIME", "2026-10-25T12:00:00Z")
                        .content("{\"id\":\"WH-EVENT-99\",\"event_type\":\"INVOICING.INVOICE.PAID\",\"resource\":{\"id\":\"INV2-123\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(reconciliationService).processWebhookEvent(
                org.mockito.ArgumentMatchers.eq("WH-EVENT-99"),
                org.mockito.ArgumentMatchers.eq("INVOICING.INVOICE.PAID"),
                org.mockito.ArgumentMatchers.eq("INV2-123"),
                any(),
                anyString()
        );
    }
}
