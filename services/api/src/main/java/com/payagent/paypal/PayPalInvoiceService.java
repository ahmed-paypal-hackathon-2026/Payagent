package com.payagent.paypal;

import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.paypal.model.CreateInvoiceCommand;
import com.payagent.paypal.model.PayPalInvoiceResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PayPalInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(PayPalInvoiceService.class);
    private static final String PAYPAL_REQUEST_ID_HEADER = "PayPal-Request-Id";

    private final PayPalAuthClient authClient;
    private final RestClient restClient;

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

            PayPalInvoiceResponse response = spec.body(payload)
                    .retrieve()
                    .body(PayPalInvoiceResponse.class);

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

    private String sanitizeEmail(String email, String defaultEmail) {
        if (email != null && email.contains("@") && email.indexOf('@') < email.lastIndexOf('.')) {
            return email.trim();
        }
        return defaultEmail;
    }
}
