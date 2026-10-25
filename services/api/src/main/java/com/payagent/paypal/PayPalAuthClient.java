package com.payagent.paypal;

import com.payagent.paypal.model.PayPalAuthResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PayPalAuthClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalAuthClient.class);
    private static final long REFRESH_BUFFER_SECONDS = 60;

    private final PayPalProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public PayPalAuthClient(PayPalProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, restClientBuilder, Clock.systemUTC());
    }

    public PayPalAuthClient(PayPalProperties properties, RestClient.Builder restClientBuilder, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        assertSandboxHost(properties.getBaseUrl());
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public String getAccessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) {
            return cachedToken;
        }

        lock.lock();
        try {
            // Re-check inside lock
            now = clock.instant();
            if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) {
                return cachedToken;
            }

            PayPalAuthResponse response = requestNewToken();
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new PayPalException(500, "AUTH_FAILED", "PayPal OAuth2 response contained an empty access token");
            }

            long expiresInSeconds = response.expiresIn() != null ? response.expiresIn() : 3600;
            this.cachedToken = response.accessToken();
            this.tokenExpiresAt = now.plusSeconds(expiresInSeconds);
            log.info("PayPal OAuth token refreshed successfully. Expires in {} seconds", expiresInSeconds);
            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    private PayPalAuthResponse requestNewToken() {
        String clientId = properties.getClientId();
        String clientSecret = properties.getClientSecret();
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new PayPalException(401, "MISSING_CREDENTIALS", "PayPal client-id or client-secret is not configured");
        }

        String rawAuth = clientId + ":" + clientSecret;
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(rawAuth.getBytes(StandardCharsets.UTF_8));

        try {
            return restClient.post()
                    .uri("/v1/oauth2/token")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(PayPalAuthResponse.class);
        } catch (Exception e) {
            log.error("Failed to authenticate with PayPal Sandbox at {}: {}", properties.getBaseUrl(), e.getMessage());
            throw new PayPalException(502, "OAUTH_REQUEST_FAILED", "Failed to retrieve PayPal OAuth2 token: " + e.getMessage(), e);
        }
    }

    private void assertSandboxHost(String baseUrl) {
        if (baseUrl == null || !baseUrl.toLowerCase().contains("sandbox.paypal.com")) {
            throw new IllegalStateException("PayPal base URL must target Sandbox (api-m.sandbox.paypal.com). Configured: " + baseUrl);
        }
    }

    void invalidateToken() {
        lock.lock();
        try {
            this.cachedToken = null;
            this.tokenExpiresAt = Instant.EPOCH;
        } finally {
            lock.unlock();
        }
    }
}
