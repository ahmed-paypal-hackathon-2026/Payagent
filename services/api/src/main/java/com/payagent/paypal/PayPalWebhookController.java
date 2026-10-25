package com.payagent.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.settlement.SettlementReconciliationService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/paypal")
public class PayPalWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookController.class);

    private final PayPalWebhookVerifier webhookVerifier;
    private final SettlementReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    public PayPalWebhookController(
            PayPalWebhookVerifier webhookVerifier,
            SettlementReconciliationService reconciliationService,
            ObjectMapper objectMapper) {
        this.webhookVerifier = webhookVerifier;
        this.reconciliationService = reconciliationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/webhooks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime,
            @RequestBody String rawPayload) {

        log.info("Received PayPal webhook event with transmissionId={}", transmissionId);

        JsonNode eventNode;
        try {
            eventNode = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            log.warn("Malformed JSON in PayPal webhook payload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "MALFORMED_JSON", "message", "Invalid JSON payload"));
        }

        boolean verified = webhookVerifier.verifySignature(
                authAlgo,
                certUrl,
                transmissionId,
                transmissionSig,
                transmissionTime,
                eventNode
        );

        if (!verified) {
            log.warn("Rejected PayPal webhook with transmissionId={}: signature verification failed", transmissionId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_SIGNATURE", "message", "Webhook signature verification failed"));
        }

        String eventId = eventNode.has("id") ? eventNode.get("id").asText() : transmissionId;
        String eventType = eventNode.has("event_type") ? eventNode.get("event_type").asText() : "UNKNOWN";
        JsonNode resourceNode = eventNode.get("resource");
        String resourceId = resourceNode != null && resourceNode.has("id") ? resourceNode.get("id").asText() : null;

        reconciliationService.processWebhookEvent(eventId, eventType, resourceId, resourceNode, rawPayload);

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }
}
