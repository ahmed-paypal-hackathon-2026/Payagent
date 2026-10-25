package com.payagent.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PayPalWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookVerifier.class);

    private final PayPalAuthClient authClient;
    private final PayPalProperties properties;
    private final RestClient restClient;

    public PayPalWebhookVerifier(PayPalAuthClient authClient, PayPalProperties properties, RestClient.Builder restClientBuilder) {
        this.authClient = authClient;
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public boolean verifySignature(
            String authAlgo,
            String certUrl,
            String transmissionId,
            String transmissionSig,
            String transmissionTime,
            JsonNode webhookEvent) {

        String webhookId = properties.getWebhookId();
        if (webhookId == null || webhookId.isBlank()) {
            log.warn("PAYPAL_WEBHOOK_ID is not configured. Webhook signature verification cannot succeed.");
            return false;
        }

        if (authAlgo == null || certUrl == null || transmissionId == null
                || transmissionSig == null || transmissionTime == null || webhookEvent == null) {
            log.warn("Missing required PayPal webhook signature headers or body");
            return false;
        }

        Map<String, Object> verificationPayload = Map.of(
                "auth_algo", authAlgo,
                "cert_url", certUrl,
                "transmission_id", transmissionId,
                "transmission_sig", transmissionSig,
                "transmission_time", transmissionTime,
                "webhook_id", webhookId,
                "webhook_event", webhookEvent
        );

        try {
            String token = authClient.getAccessToken();
            JsonNode response = restClient.post()
                    .uri("/v1/notifications/verify-webhook-signature")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(verificationPayload)
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null && response.has("verification_status")) {
                String status = response.get("verification_status").asText();
                boolean verified = "SUCCESS".equalsIgnoreCase(status);
                if (!verified) {
                    log.warn("PayPal webhook signature verification failed with status: {}", status);
                }
                return verified;
            }

            return false;
        } catch (Exception e) {
            log.error("Error during PayPal webhook signature verification: {}", e.getMessage());
            return false;
        }
    }
}
