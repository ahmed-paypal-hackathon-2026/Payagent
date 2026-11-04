package com.payagent.paypal;

import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.paypal.model.AuthorizeOrderResult;
import com.payagent.paypal.model.CreateOrderCommand;
import com.payagent.paypal.model.PayPalCaptureResponse;
import com.payagent.paypal.model.PayPalOrderResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PayPalOrderService {

    private static final Logger log = LoggerFactory.getLogger(PayPalOrderService.class);
    private static final String PAYPAL_REQUEST_ID_HEADER = "PayPal-Request-Id";

    private final PayPalAuthClient authClient;
    private final RestClient restClient;

    public PayPalOrderService(PayPalAuthClient authClient, PayPalProperties properties, RestClient.Builder restClientBuilder) {
        this.authClient = authClient;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public PayPalOrderResponse createOrder(CreateOrderCommand command, String requestId) {
        String token = authClient.getAccessToken();
        String decimalAmount = PayPalMoneyConverter.toDecimalString(command.amountMinorUnits(), command.currency());

        Map<String, Object> purchaseUnit = Map.of(
                "reference_id", command.dealId() != null ? command.dealId() : "deal-ref",
                "invoice_id", command.invoiceId(),
                "description", command.title() != null ? command.title() : "Deal Settlement Authorization",
                "amount", Map.of(
                        "currency_code", command.currency(),
                        "value", decimalAmount
                )
        );

        Map<String, Object> payload = Map.of(
                "intent", "AUTHORIZE",
                "purchase_units", List.of(purchaseUnit)
        );

        try {
            var spec = restClient.post()
                    .uri("/v2/checkout/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON);

            if (requestId != null && !requestId.isBlank()) {
                spec.header(PAYPAL_REQUEST_ID_HEADER, requestId);
            }

            PayPalOrderResponse response = spec.body(payload)
                    .retrieve()
                    .body(PayPalOrderResponse.class);

            if (response == null || response.id() == null) {
                throw new PayPalException(502, "ORDER_CREATION_FAILED", "Empty order response from PayPal");
            }

            log.info("Created PayPal order {} with intent AUTHORIZE linked to invoice {}", response.id(), command.invoiceId());
            return response;
        } catch (PayPalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create PayPal order: {}", e.getMessage());
            throw new PayPalException(502, "ORDER_CREATION_FAILED", "PayPal order creation failed: " + e.getMessage(), e);
        }
    }

    public AuthorizeOrderResult authorizeOrder(String orderId, String requestId) {
        String token = authClient.getAccessToken();

        try {
            var spec = restClient.post()
                    .uri("/v2/checkout/orders/{id}/authorize", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON);

            if (requestId != null && !requestId.isBlank()) {
                spec.header(PAYPAL_REQUEST_ID_HEADER, requestId);
            }

            PayPalOrderResponse response = spec.body(Map.of())
                    .retrieve()
                    .body(PayPalOrderResponse.class);

            if (response == null) {
                throw new PayPalException(502, "ORDER_AUTHORIZE_FAILED", "Empty response when authorizing order " + orderId);
            }

            var auth = response.getFirstAuthorization();
            String authId = auth != null ? auth.id() : null;
            String authStatus = auth != null ? auth.status() : null;
            String expirationTime = auth != null ? auth.expirationTime() : null;

            log.info("Authorized PayPal order {}: authId={}, status={}", orderId, authId, authStatus);
            return new AuthorizeOrderResult(orderId, authId, authStatus, expirationTime);
        } catch (PayPalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to authorize PayPal order {}: {}", orderId, e.getMessage());
            throw new PayPalException(502, "ORDER_AUTHORIZE_FAILED", "Failed to authorize order " + orderId + ": " + e.getMessage(), e);
        }
    }

    public PayPalCaptureResponse captureAuthorization(String authorizationId, long amountMinorUnits, String currency, String requestId) {
        String token = authClient.getAccessToken();
        String decimalAmount = PayPalMoneyConverter.toDecimalString(amountMinorUnits, currency);

        Map<String, Object> payload = Map.of(
                "final_capture", true,
                "amount", Map.of(
                        "currency_code", currency,
                        "value", decimalAmount
                ),
                "note_to_payer", "Deliverables accepted"
        );

        try {
            var spec = restClient.post()
                    .uri("/v2/payments/authorizations/{id}/capture", authorizationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON);

            if (requestId != null && !requestId.isBlank()) {
                spec.header(PAYPAL_REQUEST_ID_HEADER, requestId);
            }

            PayPalCaptureResponse response = spec.body(payload)
                    .retrieve()
                    .body(PayPalCaptureResponse.class);

            if (response == null || response.id() == null) {
                throw new PayPalException(502, "CAPTURE_FAILED", "Empty capture response for authorization " + authorizationId);
            }

            log.info("Captured authorization {}: captureId={}, status={}", authorizationId, response.id(), response.status());
            return response;
        } catch (PayPalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to capture authorization {}: {}", authorizationId, e.getMessage());
            throw new PayPalException(502, "CAPTURE_FAILED", "Failed to capture authorization " + authorizationId + ": " + e.getMessage(), e);
        }
    }

    public void voidAuthorization(String authorizationId, String requestId) {
        String token = authClient.getAccessToken();

        try {
            var spec = restClient.post()
                    .uri("/v2/payments/authorizations/{id}/void", authorizationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON);

            if (requestId != null && !requestId.isBlank()) {
                spec.header(PAYPAL_REQUEST_ID_HEADER, requestId);
            }

            spec.body(Map.of()).retrieve().toBodilessEntity();
            log.info("Voided PayPal authorization {}", authorizationId);
        } catch (Exception e) {
            log.error("Failed to void authorization {}: {}", authorizationId, e.getMessage());
            throw new PayPalException(502, "VOID_FAILED", "Failed to void authorization " + authorizationId + ": " + e.getMessage(), e);
        }
    }

    public PayPalOrderResponse getOrder(String orderId) {
        String token = authClient.getAccessToken();

        try {
            return restClient.get()
                    .uri("/v2/checkout/orders/{id}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(PayPalOrderResponse.class);
        } catch (Exception e) {
            log.error("Failed to get order {}: {}", orderId, e.getMessage());
            throw new PayPalException(502, "ORDER_GET_FAILED", "Failed to get order " + orderId + ": " + e.getMessage(), e);
        }
    }

    public PaymentLifecycleStatus mapOrderStatus(String orderStatus) {
        if (orderStatus == null || orderStatus.isBlank()) {
            return PaymentLifecycleStatus.UNKNOWN;
        }
        return switch (orderStatus.toUpperCase()) {
            case "CREATED", "SAVED", "APPROVED" -> PaymentLifecycleStatus.ORDER_CREATED;
            case "COMPLETED" -> PaymentLifecycleStatus.AUTHORIZED;
            case "VOIDED" -> PaymentLifecycleStatus.VOIDED;
            default -> PaymentLifecycleStatus.UNKNOWN;
        };
    }

    public PaymentLifecycleStatus mapAuthorizationStatus(String authStatus) {
        if (authStatus == null || authStatus.isBlank()) {
            return PaymentLifecycleStatus.UNKNOWN;
        }
        return switch (authStatus.toUpperCase()) {
            case "CREATED", "PENDING" -> PaymentLifecycleStatus.AUTHORIZED;
            case "CAPTURED" -> PaymentLifecycleStatus.COMPLETED;
            case "VOIDED" -> PaymentLifecycleStatus.VOIDED;
            case "DENIED" -> PaymentLifecycleStatus.DECLINED;
            default -> PaymentLifecycleStatus.UNKNOWN;
        };
    }

    public PaymentLifecycleStatus mapCaptureStatus(String captureStatus) {
        if (captureStatus == null || captureStatus.isBlank()) {
            return PaymentLifecycleStatus.UNKNOWN;
        }
        return switch (captureStatus.toUpperCase()) {
            case "COMPLETED" -> PaymentLifecycleStatus.COMPLETED;
            case "PENDING" -> PaymentLifecycleStatus.CAPTURE_PENDING;
            case "DECLINED", "FAILED" -> PaymentLifecycleStatus.DECLINED;
            default -> PaymentLifecycleStatus.UNKNOWN;
        };
    }
}
