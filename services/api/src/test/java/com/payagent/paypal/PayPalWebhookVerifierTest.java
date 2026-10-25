package com.payagent.paypal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayPalWebhookVerifierTest {

    private PayPalAuthClient authClient;
    private PayPalProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private PayPalWebhookVerifier verifier;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        authClient = mock(PayPalAuthClient.class);
        when(authClient.getAccessToken()).thenReturn("MOCK_BEARER_TOKEN");

        properties = new PayPalProperties();
        properties.setBaseUrl("https://api-m.sandbox.paypal.com");
        properties.setWebhookId("WH-TEST-1234");

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        verifier = new PayPalWebhookVerifier(authClient, properties, restClientBuilder);
        objectMapper = new ObjectMapper();
    }

    @Test
    void returnsTrueWhenPayPalVerifiesSuccess() throws Exception {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v1/notifications/verify-webhook-signature"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andExpect(jsonPath("$.webhook_id").value("WH-TEST-1234"))
                .andExpect(jsonPath("$.transmission_id").value("tx-123"))
                .andRespond(withSuccess("""
                        {
                          "verification_status": "SUCCESS"
                        }
                        """, MediaType.APPLICATION_JSON));

        var eventNode = objectMapper.readTree("{\"id\":\"WH-EVENT-1\"}");

        boolean result = verifier.verifySignature(
                "SHA256withRSA",
                "https://api.sandbox.paypal.com/cert.pem",
                "tx-123",
                "signature-xyz",
                "2026-10-25T12:00:00Z",
                eventNode
        );

        assertThat(result).isTrue();
        mockServer.verify();
    }

    @Test
    void returnsFalseWhenPayPalReturnsFailure() throws Exception {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v1/notifications/verify-webhook-signature"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "verification_status": "FAILURE"
                        }
                        """, MediaType.APPLICATION_JSON));

        var eventNode = objectMapper.readTree("{\"id\":\"WH-EVENT-TAMPERED\"}");

        boolean result = verifier.verifySignature(
                "SHA256withRSA",
                "https://api.sandbox.paypal.com/cert.pem",
                "tx-tampered",
                "tampered-sig",
                "2026-10-25T12:00:00Z",
                eventNode
        );

        assertThat(result).isFalse();
        mockServer.verify();
    }

    @Test
    void returnsFalseWhenHeadersOrWebhookIdMissing() throws Exception {
        properties.setWebhookId(null);
        var eventNode = objectMapper.readTree("{}");

        boolean result = verifier.verifySignature(
                "SHA256withRSA", "cert", "tx", "sig", "time", eventNode
        );
        assertThat(result).isFalse();
    }
}
