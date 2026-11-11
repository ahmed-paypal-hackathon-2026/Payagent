package com.payagent.paypal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayPalWebhookSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void webhookEndpointIsReachableWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/paypal/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"WH-SEC-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SIGNATURE"));
    }

    @Test
    void paypalNotificationsAliasEndpointIsReachableWithoutJwt() throws Exception {
        mockMvc.perform(post("/v1/notifications/verify-webhook-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"WH-SEC-ALIAS-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SIGNATURE"));
    }
}
