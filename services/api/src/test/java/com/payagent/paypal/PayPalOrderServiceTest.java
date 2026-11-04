package com.payagent.paypal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.paypal.model.AuthorizeOrderResult;
import com.payagent.paypal.model.CreateOrderCommand;
import com.payagent.paypal.model.PayPalCaptureResponse;
import com.payagent.paypal.model.PayPalOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayPalOrderServiceTest {

    private PayPalAuthClient authClient;
    private PayPalProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private PayPalOrderService orderService;

    @BeforeEach
    void setUp() {
        authClient = mock(PayPalAuthClient.class);
        when(authClient.getAccessToken()).thenReturn("MOCK_BEARER_TOKEN");

        properties = new PayPalProperties();
        properties.setBaseUrl("https://api-m.sandbox.paypal.com");

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        orderService = new PayPalOrderService(authClient, properties, restClientBuilder);
    }

    @Test
    void createOrderSendsAuthorizeIntentWithLinkedInvoiceId() {
        CreateOrderCommand cmd = new CreateOrderCommand(
                "deal-uuid-1",
                "INV2-ABCD-1234",
                "Negotiated Settlement",
                98000L,
                "USD"
        );

        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andExpect(header("PayPal-Request-Id", "order-req-1"))
                .andExpect(jsonPath("$.intent").value("AUTHORIZE"))
                .andExpect(jsonPath("$.purchase_units[0].invoice_id").value("INV2-ABCD-1234"))
                .andExpect(jsonPath("$.purchase_units[0].amount.value").value("980.00"))
                .andExpect(jsonPath("$.purchase_units[0].amount.currency_code").value("USD"))
                .andRespond(withSuccess("""
                        {
                          "id": "ORD-5O190127TN364715T",
                          "status": "CREATED",
                          "intent": "AUTHORIZE",
                          "links": [
                            {
                              "rel": "approve",
                              "href": "https://www.sandbox.paypal.com/checkoutnow?token=ORD-5O190127TN364715T",
                              "method": "GET"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        PayPalOrderResponse response = orderService.createOrder(cmd, "order-req-1");

        assertThat(response.id()).isEqualTo("ORD-5O190127TN364715T");
        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.getApproveUrl()).contains("https://www.sandbox.paypal.com/checkoutnow");

        mockServer.verify();
    }

    @Test
    void authorizeOrderExtractsAuthorizationIdAndExpiry() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORD-123/authorize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andExpect(header("PayPal-Request-Id", "auth-req-2"))
                .andRespond(withSuccess("""
                        {
                          "id": "ORD-123",
                          "status": "COMPLETED",
                          "purchase_units": [
                            {
                              "payments": {
                                "authorizations": [
                                  {
                                    "id": "AUTH-789-XYZ",
                                    "status": "CREATED",
                                    "expiration_time": "2026-11-23T12:00:00Z"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AuthorizeOrderResult result = orderService.authorizeOrder("ORD-123", "auth-req-2");

        assertThat(result.orderId()).isEqualTo("ORD-123");
        assertThat(result.authorizationId()).isEqualTo("AUTH-789-XYZ");
        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.expirationTime()).isEqualTo("2026-11-23T12:00:00Z");

        mockServer.verify();
    }

    @Test
    void captureAuthorizationPostsFinalCaptureWithExactCents() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/payments/authorizations/AUTH-789-XYZ/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andExpect(header("PayPal-Request-Id", "cap-req-3"))
                .andExpect(jsonPath("$.final_capture").value(true))
                .andExpect(jsonPath("$.amount.value").value("980.00"))
                .andExpect(jsonPath("$.amount.currency_code").value("USD"))
                .andRespond(withSuccess("""
                        {
                          "id": "CAP-999-ABC",
                          "status": "COMPLETED",
                          "amount": {
                            "currency_code": "USD",
                            "value": "980.00"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PayPalCaptureResponse capture = orderService.captureAuthorization("AUTH-789-XYZ", 98000L, "USD", "cap-req-3");

        assertThat(capture.id()).isEqualTo("CAP-999-ABC");
        assertThat(capture.status()).isEqualTo("COMPLETED");

        mockServer.verify();
    }

    @Test
    void voidAuthorizationCallsVoidEndpoint() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/payments/authorizations/AUTH-789-XYZ/void"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andExpect(header("PayPal-Request-Id", "void-req-4"))
                .andRespond(withNoContent());

        orderService.voidAuthorization("AUTH-789-XYZ", "void-req-4");
        mockServer.verify();
    }

    @Test
    void statusMappingsReturnExpectedContracts() {
        assertThat(orderService.mapOrderStatus("CREATED")).isEqualTo(PaymentLifecycleStatus.ORDER_CREATED);
        assertThat(orderService.mapOrderStatus("APPROVED")).isEqualTo(PaymentLifecycleStatus.ORDER_CREATED);
        assertThat(orderService.mapOrderStatus("COMPLETED")).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(orderService.mapOrderStatus("VOIDED")).isEqualTo(PaymentLifecycleStatus.VOIDED);

        assertThat(orderService.mapAuthorizationStatus("CREATED")).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(orderService.mapAuthorizationStatus("PENDING")).isEqualTo(PaymentLifecycleStatus.AUTHORIZED);
        assertThat(orderService.mapAuthorizationStatus("CAPTURED")).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        assertThat(orderService.mapAuthorizationStatus("VOIDED")).isEqualTo(PaymentLifecycleStatus.VOIDED);
        assertThat(orderService.mapAuthorizationStatus("DENIED")).isEqualTo(PaymentLifecycleStatus.DECLINED);

        assertThat(orderService.mapCaptureStatus("COMPLETED")).isEqualTo(PaymentLifecycleStatus.COMPLETED);
        assertThat(orderService.mapCaptureStatus("PENDING")).isEqualTo(PaymentLifecycleStatus.CAPTURE_PENDING);
        assertThat(orderService.mapCaptureStatus("DECLINED")).isEqualTo(PaymentLifecycleStatus.DECLINED);
        assertThat(orderService.mapCaptureStatus(null)).isEqualTo(PaymentLifecycleStatus.UNKNOWN);
    }

    @Test
    void throwsPayPalExceptionOnFailure() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORD-FAIL/authorize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> orderService.authorizeOrder("ORD-FAIL", "req"))
                .isInstanceOf(PayPalException.class)
                .hasMessageContaining("Failed to authorize order");
    }
}
