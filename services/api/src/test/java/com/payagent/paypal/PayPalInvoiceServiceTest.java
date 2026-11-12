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
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

class PayPalInvoiceServiceTest {

    private PayPalAuthClient authClient;
    private PayPalProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private PayPalInvoiceService invoiceService;

    private Logger invoiceServiceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        authClient = mock(PayPalAuthClient.class);
        when(authClient.getAccessToken()).thenReturn("MOCK_BEARER_TOKEN");

        properties = new PayPalProperties();
        properties.setBaseUrl("https://api-m.sandbox.paypal.com");

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        invoiceService = new PayPalInvoiceService(authClient, properties, restClientBuilder);

        invoiceServiceLogger = (Logger) LoggerFactory.getLogger(PayPalInvoiceService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        invoiceServiceLogger.addAppender(logAppender);
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

    private List<String> loggedMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private String createCommandJson() {
        return """
                {
                  "id": "INV2-SYNTH-0001",
                  "status": "DRAFT",
                  "detail": { "invoice_number": "0001", "currency_code": "USD" },
                  "links": [ { "rel": "self", "href": "https://api-m.sandbox.paypal.com/v2/invoicing/invoices/INV2-SYNTH-0001", "method": "GET" } ]
                }
                """;
    }

    @Test
    void createDraftInvoice_valid201_returnsIdAndLogsSanitizedFingerprint() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(createCommandJson(), MediaType.APPLICATION_JSON));

        PayPalInvoiceResponse response = invoiceService.createDraftInvoice(
                new CreateInvoiceCommand("deal-1", "seller@example.test", "buyer@example.test",
                        "Synthetic Invoice", List.of("Deliverable A"), 10000L, "USD", "deadbeef".repeat(8)),
                "test-req-001");

        assertThat(response.id()).isEqualTo("INV2-SYNTH-0001");
        List<String> logs = loggedMessages();
        assertThat(logs).anySatisfy(line -> {
            assertThat(line).contains("status=200");
            assertThat(line).contains("hasId=true");
            assertThat(line).contains("sha256=");
            assertThat(line).doesNotContain("seller@example.test");
            assertThat(line).doesNotContain("buyer@example.test");
        });
        mockServer.verify();
    }

    @Test
    void createDraftInvoice_empty201_throwsPayPalException() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> invoiceService.createDraftInvoice(
                new CreateInvoiceCommand("deal-1", "seller@example.test", "buyer@example.test",
                        "Synthetic Invoice", List.of("Deliverable A"), 10000L, "USD", "deadbeef".repeat(8)),
                "test-req-002"))
                .isInstanceOf(PayPalException.class)
                .satisfies(e -> assertThat(((PayPalException) e).getErrorCode()).isEqualTo("INVOICE_CREATION_FAILED"))
                .hasMessageContaining("Empty invoice response from PayPal");

        List<String> logs = loggedMessages();
        assertThat(logs).anySatisfy(line -> assertThat(line).contains("body=empty"));
        assertThat(logs).noneSatisfy(line -> assertThat(line).contains("seller@example.test"));
        mockServer.verify();
    }

    @Test
    void createDraftInvoice_201WithoutId_throwsPayPalException() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"DRAFT\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> invoiceService.createDraftInvoice(
                new CreateInvoiceCommand("deal-1", "seller@example.test", "buyer@example.test",
                        "Synthetic Invoice", List.of("Deliverable A"), 10000L, "USD", "deadbeef".repeat(8)),
                "test-req-003"))
                .isInstanceOf(PayPalException.class)
                .satisfies(e -> assertThat(((PayPalException) e).getErrorCode()).isEqualTo("INVOICE_CREATION_FAILED"))
                .hasMessageContaining("Empty invoice response from PayPal");

        assertThat(loggedMessages()).anySatisfy(line -> assertThat(line).contains("hasId=false"));
        mockServer.verify();
    }

    @Test
    void createDraftInvoice_403PayPalError_isSanitizedAndPreservesDebugId() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body("""
                                {"name":"PERMISSION_DENIED","debug_id":"debug-xyz123","message":"PII-free check email=seller@example.test"}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> invoiceService.createDraftInvoice(
                new CreateInvoiceCommand("deal-1", "seller@example.test", "buyer@example.test",
                        "Synthetic Invoice", List.of("Deliverable A"), 10000L, "USD", "deadbeef".repeat(8)),
                "test-req-004"))
                .isInstanceOf(PayPalException.class)
                .satisfies(e -> assertThat(((PayPalException) e).getErrorCode()).isEqualTo("INVOICE_CREATION_FAILED"))
                .hasMessageContaining("debug-xyz123");

        List<String> logs = loggedMessages();
        assertThat(logs).anySatisfy(line -> {
            assertThat(line).contains("errorName=PERMISSION_DENIED");
            assertThat(line).contains("debugId=debug-xyz123");
            assertThat(line).doesNotContain("seller@example.test");
        });
        mockServer.verify();
    }

    @Test
    void createDraftInvoice_400And500PayPalErrors_areSanitizedAndMapped() {
        org.springframework.http.HttpStatus[] statuses = {
                org.springframework.http.HttpStatus.BAD_REQUEST,
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
        };
        for (int i = 0; i < statuses.length; i++) {
            int idx = i;
            mockServer = MockRestServiceServer.bindTo(restClientBuilder = RestClient.builder()).build();
            invoiceService = new PayPalInvoiceService(authClient, properties, restClientBuilder);
            mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(MockRestResponseCreators.withStatus(statuses[idx])
                            .body("{\"name\":\"TEST_ERR_" + idx + "\",\"debug_id\":\"dbg-" + idx + "\",\"message\":\"should-not-leak\"}")
                            .contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> invoiceService.createDraftInvoice(
                    new CreateInvoiceCommand("deal-1", "seller@example.test", "buyer@example.test",
                            "Synthetic Invoice", List.of("Deliverable A"), 10000L, "USD", "deadbeef".repeat(8)),
                    "test-req-00" + idx))
                    .isInstanceOf(PayPalException.class)
                    .satisfies(e -> assertThat(((PayPalException) e).getErrorCode()).isEqualTo("INVOICE_CREATION_FAILED"))
                    .hasMessageContaining("dbg-" + idx);
        }
    }

    @Test
    void createDraftInvoice_malformed2xxJson_throwsPayPalExceptionWithoutSecondaryCrash() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{ this is not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> invoiceService.createDraftInvoice(
                new CreateInvoiceCommand("deal-1", "seller@example.test", "buyer@example.test",
                        "Synthetic Invoice", List.of("Deliverable A"), 10000L, "USD", "deadbeef".repeat(8)),
                "test-req-006"))
                .isInstanceOf(PayPalException.class)
                .satisfies(e -> assertThat(((PayPalException) e).getErrorCode()).isEqualTo("INVOICE_CREATION_FAILED"))
                .hasMessageContaining("Malformed invoice response from PayPal");

        assertThat(loggedMessages()).anySatisfy(line -> assertThat(line).contains("status=200"));
        mockServer.verify();
    }

    @Test
    void createDraftInvoice_nullBody201_throwsPayPalExceptionWithoutSecondaryCrash() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> invoiceService.createDraftInvoice(
                new CreateInvoiceCommand("deal-1", "seller@example.test", "buyer@example.test",
                        "Synthetic Invoice", List.of("Deliverable A"), 10000L, "USD", "deadbeef".repeat(8)),
                "test-req-007"))
                .isInstanceOf(PayPalException.class)
                .satisfies(e -> assertThat(((PayPalException) e).getErrorCode()).isEqualTo("INVOICE_CREATION_FAILED"));

        assertThat(loggedMessages()).noneSatisfy(line -> assertThat(line).contains("seller@example.test"));
        mockServer.verify();
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
