package com.payagent.paypal;

import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.paypal.model.CreateInvoiceCommand;
import com.payagent.paypal.model.PayPalInvoiceResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class PayPalInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(PayPalInvoiceService.class);
    private static final String PAYPAL_REQUEST_ID_HEADER = "PayPal-Request-Id";

    private final PayPalAuthClient authClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PayPalInvoiceService(PayPalAuthClient authClient, PayPalProperties properties, RestClient.Builder restClientBuilder) {
        this.authClient = authClient;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public PayPalInvoiceResponse createDraftInvoice(CreateInvoiceCommand command, String requestId) {
        String token = authClient.getAccessToken();
        String decimalAmount = PayPalMoneyConverter.toDecimalString(command.amountMinorUnits(), command.currency());
        String deliverablesSummary = command.deliverables() != null && !command.deliverables().isEmpty()
                ? String.join("; ", command.deliverables())
                : "Standard deliverables";

        // PayPal Invoicing v2 detail.note holds immutable agreement hash and deliverables summary
        // (note: PayPal ignores notes passed in the /send endpoint, so it is embedded here in detail.note)
        String note = "termsHashSha256: " + command.termsHashSha256() + " | Deliverables: " + deliverablesSummary;

        Map<String, Object> payload = Map.of(
                "detail", Map.of(
                        "currency_code", command.currency(),
                        "note", note,
                        "term", "DUE_ON_RECEIPT"
                ),
                "invoicer", Map.of(
                        "email_address", sanitizeEmail(command.sellerEmail(), "seller@payagent.internal")
                ),
                "primary_recipients", List.of(
                        Map.of("billing_info", Map.of(
                                "email_address", sanitizeEmail(command.buyerEmail(), "buyer@payagent.internal")
                        ))
                ),
                "items", List.of(
                        Map.of(
                                "name", command.title() != null && !command.title().isBlank() ? command.title() : "Deal Settlement",
                                "description", deliverablesSummary,
                                "quantity", "1",
                                "unit_amount", Map.of(
                                        "currency_code", command.currency(),
                                        "value", decimalAmount
                                )
                        )
                ),
                "amount", Map.of(
                        "currency_code", command.currency(),
                        "value", decimalAmount,
                        "breakdown", Map.of(
                                "item_total", Map.of(
                                        "currency_code", command.currency(),
                                        "value", decimalAmount
                                )
                        )
                )
        );

        try {
            var spec = restClient.post()
                    .uri("/v2/invoicing/invoices")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON);

            if (requestId != null && !requestId.isBlank()) {
                spec.header(PAYPAL_REQUEST_ID_HEADER, requestId);
            }

            ResponseEntity<String> entity;
            try {
                entity = spec.body(payload)
                        .retrieve()
                        .toEntity(String.class);
            } catch (RestClientResponseException e) {
                logPayPalResponseDiagnostics("create-invoice", e.getRawStatusCode(), e.getResponseBodyAsByteArray(), requestId);
                throw buildPayPalException(e);
            }

            String body = entity.getBody();
            logPayPalResponseDiagnostics("create-invoice", entity.getStatusCode().value(),
                    body == null ? null : body.getBytes(StandardCharsets.UTF_8), requestId);

            PayPalInvoiceResponse response = parseInvoiceBody(body, entity.getStatusCode().value());

            if (response == null || response.id() == null) {
                throw new PayPalException(502, "INVOICE_CREATION_FAILED", "Empty invoice response from PayPal");
            }

            log.info("Created draft PayPal invoice: {}", response.id());
            return response;
        } catch (PayPalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create PayPal draft invoice: {}", e.getMessage());
            throw new PayPalException(502, "INVOICE_CREATION_FAILED", "PayPal invoice creation failed: " + e.getMessage(), e);
        }
    }

    public void sendInvoice(String invoiceId, String requestId) {
        String token = authClient.getAccessToken();
        Map<String, Object> body = Map.of("send_to_recipient", true);

        try {
            var spec = restClient.post()
                    .uri("/v2/invoicing/invoices/{id}/send", invoiceId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON);

            if (requestId != null && !requestId.isBlank()) {
                spec.header(PAYPAL_REQUEST_ID_HEADER, requestId);
            }

            spec.body(body).retrieve().toBodilessEntity();
            log.info("Sent PayPal invoice: {}", invoiceId);
        } catch (Exception e) {
            log.error("Failed to send PayPal invoice {}: {}", invoiceId, e.getMessage());
            throw new PayPalException(502, "INVOICE_SEND_FAILED", "Failed to send invoice " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    public void recordPayment(String invoiceId, long amountMinorUnits, String currency, String note, String requestId) {
        String token = authClient.getAccessToken();
        String decimalAmount = PayPalMoneyConverter.toDecimalString(amountMinorUnits, currency);
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        Map<String, Object> body = Map.of(
                "method", "PAYPAL",
                "payment_date", today,
                "amount", Map.of(
                        "currency_code", currency,
                        "value", decimalAmount
                ),
                "note", note != null ? note : "Payment recorded by PayAgent"
        );

        try {
            var spec = restClient.post()
                    .uri("/v2/invoicing/invoices/{id}/payments", invoiceId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON);

            if (requestId != null && !requestId.isBlank()) {
                spec.header(PAYPAL_REQUEST_ID_HEADER, requestId);
            }

            spec.body(body).retrieve().toBodilessEntity();
            log.info("Recorded payment of {} {} against invoice {}", decimalAmount, currency, invoiceId);
        } catch (Exception e) {
            log.error("Failed to record payment on invoice {}: {}", invoiceId, e.getMessage());
            throw new PayPalException(502, "INVOICE_RECORD_PAYMENT_FAILED", "Failed to record payment on invoice " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    public void cancelInvoice(String invoiceId, String reason, String requestId) {
        String token = authClient.getAccessToken();
        Map<String, Object> body = Map.of(
                "subject", "Invoice cancelled",
                "note", reason != null ? reason : "Deal cancelled or disputed",
                "send_to_invoicer", true,
                "send_to_recipient", true
        );

        try {
            var spec = restClient.post()
                    .uri("/v2/invoicing/invoices/{id}/cancel", invoiceId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON);

            if (requestId != null && !requestId.isBlank()) {
                spec.header(PAYPAL_REQUEST_ID_HEADER, requestId);
            }

            spec.body(body).retrieve().toBodilessEntity();
            log.info("Cancelled PayPal invoice: {}", invoiceId);
        } catch (Exception e) {
            log.error("Failed to cancel invoice {}: {}", invoiceId, e.getMessage());
            throw new PayPalException(502, "INVOICE_CANCEL_FAILED", "Failed to cancel invoice " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    public PayPalInvoiceResponse getInvoice(String invoiceId) {
        String token = authClient.getAccessToken();

        try {
            return restClient.get()
                    .uri("/v2/invoicing/invoices/{id}", invoiceId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(PayPalInvoiceResponse.class);
        } catch (Exception e) {
            log.error("Failed to get invoice {}: {}", invoiceId, e.getMessage());
            throw new PayPalException(502, "INVOICE_GET_FAILED", "Failed to get invoice " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    public InvoiceLifecycleStatus mapToLifecycleStatus(String paypalStatus) {
        if (paypalStatus == null || paypalStatus.isBlank()) {
            return InvoiceLifecycleStatus.UNKNOWN;
        }
        return switch (paypalStatus.toUpperCase()) {
            case "DRAFT" -> InvoiceLifecycleStatus.DRAFT;
            case "SENT", "SCHEDULED" -> InvoiceLifecycleStatus.SENT;
            case "PAID", "MARKED_AS_PAID" -> InvoiceLifecycleStatus.PAID;
            case "CANCELLED", "REFUNDED", "PARTIALLY_REFUNDED" -> InvoiceLifecycleStatus.CANCELLED;
            default -> InvoiceLifecycleStatus.UNKNOWN;
        };
    }

    /** Captures a sanitized one-line fingerprint of a PayPal response for diagnosis. Never logs body content, headers, or PII. */
    private void logPayPalResponseDiagnostics(String operation, int status, byte[] bodyBytes, String requestId) {
        String id = safeIdRef(requestId);
        try {
            if (bodyBytes == null || bodyBytes.length == 0) {
                log.warn("paypal {} id={} status={} body=empty", operation, id, status);
                return;
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String sha256 = sha256Hex(bodyBytes);
            Set<String> keys = new LinkedHashSet<>();
            JsonNode node = null;
            try {
                node = objectMapper.readTree(bodyBytes);
                if (node != null && node.isObject()) {
                    node.fieldNames().forEachRemaining(keys::add);
                }
            } catch (Exception ignored) {
                // unparseable JSON — not fatal, diagnostics proceed with size/sha256 only
            }
            boolean hasId = node != null && node.hasNonNull("id");
            boolean hasDebugId = node != null && node.path("debug_id").isTextual();
            if (status >= 400) {
                String errorName = node != null && node.path("name").isTextual() ? node.path("name").asText() : null;
                String debugId = hasDebugId ? node.path("debug_id").asText() : null;
                List<String> issueCodes = new ArrayList<>();
                JsonNode details = node != null ? node.path("details") : null;
                if (details != null && details.isArray()) {
                    details.forEach(detail -> {
                        JsonNode issue = detail.path("issue");
                        if (issue.isTextual()) {
                            String it = issue.asText();
                            if (it.length() <= 64 && it.matches("[A-Z0-9_]+")) {
                                issueCodes.add(it);
                            }
                        }
                    });
                }
                log.warn("paypal {} id={} status={} bodySize={} sha256={} keys={} hasId={} errorName={} debugId={} issues={}",
                        operation, id, status, bodyBytes.length, sha256, keys, hasId, errorName, debugId, issueCodes);
            } else {
                log.info("paypal {} id={} status={} bodySize={} sha256={} keys={} hasId={}",
                        operation, id, status, bodyBytes.length, sha256, keys, hasId);
            }
        } catch (Exception ex) {
            // Logging must never throw — otherwise the diagnostic path would hide the PayPal failure.
            log.warn("paypal {} id={} status={} bodyDigest={} (diag-failed)", operation, id, status,
                    bodyBytes == null ? "none" : sha256Hex(bodyBytes));
        }
    }

    private String safeIdRef(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "unknown";
        }
        String trimmed = requestId.trim();
        return trimmed.length() <= 128 ? trimmed : trimmed.substring(0, 128);
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "digest-unavailable";
        }
    }

    private PayPalInvoiceResponse parseInvoiceBody(String body, int status) {
        if (body == null || body.isBlank()) {
            log.warn("paypal create-invoice status={} body empty — treating as INVOICE_CREATION_FAILED", status);
            return null;
        }
        try {
            return objectMapper.readValue(body, PayPalInvoiceResponse.class);
        } catch (Exception e) {
            log.warn("paypal create-invoice status={} bodySize={} sha256={} (malformed JSON)", status,
                    body.getBytes(StandardCharsets.UTF_8).length, sha256Hex(body.getBytes(StandardCharsets.UTF_8)));
            throw new PayPalException(502, "INVOICE_CREATION_FAILED", "Malformed invoice response from PayPal", e);
        }
    }

    private PayPalException buildPayPalException(RestClientResponseException e) {
        int status = e.getRawStatusCode();
        String debugId = null;
        String errorName = null;
        byte[] bodyBytes = e.getResponseBodyAsByteArray();
        try {
            if (bodyBytes != null) {
                JsonNode node = objectMapper.readTree(bodyBytes);
                if (node.path("name").isTextual()) {
                    errorName = node.path("name").asText();
                }
                if (node.path("debug_id").isTextual()) {
                    debugId = node.path("debug_id").asText();
                }
            }
        } catch (Exception parseFailure) {
            log.warn("paypal create-invoice error envelope unparseable; status={}", status);
        }
        StringBuilder message = new StringBuilder("PayPal invoice creation failed");
        message.append(" (status=").append(status).append(')');
        if (errorName != null) {
            message.append(" name=").append(errorName);
        }
        if (debugId != null) {
            message.append(" debugId=").append(debugId);
        }
        return new PayPalException(502, "INVOICE_CREATION_FAILED", message.toString(), e);
    }

    private String sanitizeEmail(String email, String defaultEmail) {
        if (email != null && email.contains("@") && email.indexOf('@') < email.lastIndexOf('.')) {
            return email.trim();
        }
        return defaultEmail;
    }
}
