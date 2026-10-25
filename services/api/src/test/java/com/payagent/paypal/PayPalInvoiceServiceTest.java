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

import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.paypal.model.CreateInvoiceCommand;
import com.payagent.paypal.model.PayPalInvoiceResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayPalInvoiceServiceTest {

    private PayPalAuthClient authClient;
    private PayPalProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private PayPalInvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        authClient = mock(PayPalAuthClient.class);
        when(authClient.getAccessToken()).thenReturn("MOCK_BEARER_TOKEN");

        properties = new PayPalProperties();
        properties.setBaseUrl("https://api-m.sandbox.paypal.com");

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        invoiceService = new PayPalInvoiceService(authClient, properties, restClientBuilder);
    }

    @Test
    void createDraftInvoiceEmbedsHashInNoteAndMatchesExactCentAmount() {
        String testHash = "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b";
        CreateInvoiceCommand command = new CreateInvoiceCommand(
                "deal-123",
                "seller@example.com",
                "buyer@example.com",
                "Web Development Contract",
                List.of("UI Design", "REST API"),
                98000L,
                "USD",
                testHash
        );

        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andExpect(header("PayPal-Request-Id", "req-id-123"))
                .andExpect(jsonPath("$.amount.value").value("980.00"))
                .andExpect(jsonPath("$.amount.currency_code").value("USD"))
                .andExpect(jsonPath("$.detail.note").value("termsHashSha256: " + testHash + " | Deliverables: UI Design; REST API"))
                .andExpect(jsonPath("$.items[0].unit_amount.value").value("980.00"))
                .andRespond(withSuccess("""
                        {
                          "id": "INV2-TEST-1234",
                          "status": "DRAFT",
                          "detail": {
                            "invoice_number": "0001",
                            "note": "termsHashSha256: 4a5e1e... | Deliverables: UI Design; REST API"
                          },
                          "links": [
                            {
                              "rel": "payer-view",
                              "href": "https://www.sandbox.paypal.com/invoice/p/#TEST1234",
                              "method": "GET"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        PayPalInvoiceResponse response = invoiceService.createDraftInvoice(command, "req-id-123");

        assertThat(response.id()).isEqualTo("INV2-TEST-1234");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.detail().invoiceNumber()).isEqualTo("0001");
        assertThat(response.getPayerViewUrl()).isEqualTo("https://www.sandbox.paypal.com/invoice/p/#TEST1234");

        mockServer.verify();
    }

    @Test
    void sendInvoicePostsToPayPalWithRequestId() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices/INV2-123/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andExpect(header("PayPal-Request-Id", "send-req-456"))
                .andExpect(jsonPath("$.send_to_recipient").value(true))
                .andRespond(withNoContent());

        invoiceService.sendInvoice("INV2-123", "send-req-456");
        mockServer.verify();
    }

    @Test
    void recordPaymentSendsPayloadWithConvertedAmount() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices/INV2-123/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andExpect(jsonPath("$.method").value("PAYPAL"))
                .andExpect(jsonPath("$.amount.value").value("980.00"))
                .andExpect(jsonPath("$.amount.currency_code").value("USD"))
                .andRespond(withNoContent());

        invoiceService.recordPayment("INV2-123", 98000L, "USD", "Deliverables accepted", "pay-req-789");
        mockServer.verify();
    }

    @Test
    void cancelInvoiceSendsCancelRequest() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices/INV2-123/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andExpect(jsonPath("$.subject").value("Invoice cancelled"))
                .andRespond(withNoContent());

        invoiceService.cancelInvoice("INV2-123", "Dispute opened", "cancel-req-111");
        mockServer.verify();
    }

    @Test
    void getInvoiceRetrievesInvoiceDetails() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices/INV2-123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer MOCK_BEARER_TOKEN"))
                .andRespond(withSuccess("""
                        {
                          "id": "INV2-123",
                          "status": "SENT",
                          "detail": {
                            "invoice_number": "0002"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PayPalInvoiceResponse invoice = invoiceService.getInvoice("INV2-123");
        assertThat(invoice.id()).isEqualTo("INV2-123");
        assertThat(invoice.status()).isEqualTo("SENT");
        mockServer.verify();
    }

    @Test
    void statusMappingMapsCorrectly() {
        assertThat(invoiceService.mapToLifecycleStatus("DRAFT")).isEqualTo(InvoiceLifecycleStatus.DRAFT);
        assertThat(invoiceService.mapToLifecycleStatus("SENT")).isEqualTo(InvoiceLifecycleStatus.SENT);
        assertThat(invoiceService.mapToLifecycleStatus("SCHEDULED")).isEqualTo(InvoiceLifecycleStatus.SENT);
        assertThat(invoiceService.mapToLifecycleStatus("PAID")).isEqualTo(InvoiceLifecycleStatus.PAID);
        assertThat(invoiceService.mapToLifecycleStatus("MARKED_AS_PAID")).isEqualTo(InvoiceLifecycleStatus.PAID);
        assertThat(invoiceService.mapToLifecycleStatus("CANCELLED")).isEqualTo(InvoiceLifecycleStatus.CANCELLED);
        assertThat(invoiceService.mapToLifecycleStatus("REFUNDED")).isEqualTo(InvoiceLifecycleStatus.CANCELLED);
        assertThat(invoiceService.mapToLifecycleStatus("RANDOM_NEW_STATUS")).isEqualTo(InvoiceLifecycleStatus.UNKNOWN);
        assertThat(invoiceService.mapToLifecycleStatus(null)).isEqualTo(InvoiceLifecycleStatus.UNKNOWN);
    }

    @Test
    void throwsPayPalExceptionOnUpstreamError() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices/INV2-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> invoiceService.getInvoice("INV2-123"))
                .isInstanceOf(PayPalException.class)
                .hasMessageContaining("Failed to get invoice");
    }
}
