package com.payagent.paypal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayPalAuthClientTest {

    private PayPalProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private MutableClock testClock;

    @BeforeEach
    void setUp() {
        properties = new PayPalProperties();
        properties.setBaseUrl("https://api-m.sandbox.paypal.com");
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        testClock = new MutableClock(Instant.parse("2026-10-25T12:00:00Z"));
    }

    @Test
    void rejectsNonSandboxBaseUrl() {
        properties.setBaseUrl("https://api-m.paypal.com");

        assertThatThrownBy(() -> new PayPalAuthClient(properties, restClientBuilder, testClock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must target Sandbox");
    }

    @Test
    void fetchesAndCachesAccessToken() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andRespond(withSuccess("""
                        {
                          "access_token": "ACCESS_TOKEN_1",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));

        PayPalAuthClient client = new PayPalAuthClient(properties, restClientBuilder, testClock);

        String token1 = client.getAccessToken();
        assertThat(token1).isEqualTo("ACCESS_TOKEN_1");

        // Second call should return cached token without triggering another HTTP call
        String token2 = client.getAccessToken();
        assertThat(token2).isEqualTo("ACCESS_TOKEN_1");

        mockServer.verify();
    }

    @Test
    void refreshesTokenWhenExpiredOrNearExpiry() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "access_token": "TOKEN_INITIAL",
                          "token_type": "Bearer",
                          "expires_in": 300
                        }
                        """, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "access_token": "TOKEN_REFRESHED",
                          "token_type": "Bearer",
                          "expires_in": 300
                        }
                        """, MediaType.APPLICATION_JSON));

        PayPalAuthClient client = new PayPalAuthClient(properties, restClientBuilder, testClock);

        assertThat(client.getAccessToken()).isEqualTo("TOKEN_INITIAL");

        // Advance clock past expiration buffer (300s - 60s buffer = 240s)
        testClock.advanceSeconds(250);

        assertThat(client.getAccessToken()).isEqualTo("TOKEN_REFRESHED");
        mockServer.verify();
    }

    @Test
    void throwsPayPalExceptionOnHttpError() {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        PayPalAuthClient client = new PayPalAuthClient(properties, restClientBuilder, testClock);

        assertThatThrownBy(client::getAccessToken)
                .isInstanceOf(PayPalException.class)
                .hasMessageContaining("Failed to retrieve PayPal OAuth2 token");
    }

    @Test
    void throwsWhenCredentialsMissing() {
        properties.setClientId("");
        properties.setClientSecret("");

        PayPalAuthClient client = new PayPalAuthClient(properties, restClientBuilder, testClock);

        assertThatThrownBy(client::getAccessToken)
                .isInstanceOf(PayPalException.class)
                .hasMessageContaining("client-id or client-secret is not configured");
    }

    @Test
    void threadSafeTokenRetrieval() throws Exception {
        mockServer.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "access_token": "CONCURRENT_TOKEN",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));

        PayPalAuthClient client = new PayPalAuthClient(properties, restClientBuilder, testClock);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(client::getAccessToken);
        }

        List<Future<String>> futures = executor.invokeAll(tasks);
        for (Future<String> future : futures) {
            assertThat(future.get()).isEqualTo("CONCURRENT_TOKEN");
        }
        executor.shutdown();

        mockServer.verify();
    }

    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advanceSeconds(long seconds) {
            current.updateAndGet(i -> i.plusSeconds(seconds));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
