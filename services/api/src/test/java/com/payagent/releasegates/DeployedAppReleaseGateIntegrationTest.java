package com.payagent.releasegates;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in contract probes for the completed disposable Sandbox staging deal. Never enabled by default. */
@EnabledIfEnvironmentVariable(named = "PAYAGENT_LIVE_APP", matches = "true")
class DeployedAppReleaseGateIntegrationTest {
    private URI base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void requireExplicitSandboxTestEnvironment() {
        assertEquals("true", required("PAYAGENT_TESTS_CONFIRM_SANDBOX"), "Use only disposable Sandbox test deals");
        base = URI.create(required("PAYAGENT_TEST_API_URL"));
        assertNotNull(base.getHost());
        assertNull(base.getUserInfo());
        assertTrue("https".equals(base.getScheme()) ||
                ("http".equals(base.getScheme()) && Set.of("localhost", "127.0.0.1").contains(base.getHost())));
    }

    @Test
    void tst10_outsiderCannotReadDeal() throws Exception {
        assertEquals(403, get(route(""), required("PAYAGENT_TEST_OUTSIDER_TOKEN")).statusCode());
    }

    @Test
    void tst10_outsiderCannotReadMandate() throws Exception {
        assertEquals(403, get(route("/mandates"), required("PAYAGENT_TEST_OUTSIDER_TOKEN")).statusCode());
    }

    @Test
    void tst10_12_outsiderCannotUseValidEvidenceLink() throws Exception {
        assertEquals(403, get(URI.create(required("PAYAGENT_TEST_EVIDENCE_URL")), required("PAYAGENT_TEST_OUTSIDER_TOKEN")).statusCode());
    }

    @Test
    void tst13_expiredEvidenceLinkRejectsAuthorizedParticipant() throws Exception {
        // Supply a genuine previously-issued URL after its 15-minute TTL; no sleeping or fabricated signature.
        assertEquals(403, get(URI.create(required("PAYAGENT_TEST_EXPIRED_EVIDENCE_URL")), required("PAYAGENT_TEST_BUYER_TOKEN")).statusCode());
    }

    @Test
    void tst12_authorizedDownloadMatchesRecordedSha256() throws Exception {
        var response = get(URI.create(required("PAYAGENT_TEST_EVIDENCE_URL")), required("PAYAGENT_TEST_BUYER_TOKEN"));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().length > 0 && response.body().length <= 10 * 1024 * 1024);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.body()));
        assertEquals(required("PAYAGENT_TEST_EVIDENCE_SHA256"), digest);
    }

    @Test
    void tst11_openingDisputePreventsCaptureAndKeepsAuthorization() throws Exception {
        String token = required("PAYAGENT_TEST_BUYER_TOKEN");
        var beforeResponse = get(route("/settlement"), token);
        assertEquals(200, beforeResponse.statusCode());
        var before = mapper.readTree(beforeResponse.body());
        assertEquals("AUTHORIZED", before.path("paymentStatus").asText());
        assertTrue(before.path("paypalCaptureId").isNull());
        var dispute = post(route("/disputes"), token, "{\"reason\":\"Disposable release verification dispute\",\"resolutionRequest\":\"Review delivery\"}");
        assertTrue(dispute.statusCode() == 200 || dispute.statusCode() == 201, "Dispute creation must succeed");
        assertEquals("DISPUTED", mapper.readTree(dispute.body()).path("status").asText());
        assertEquals(409, post(route("/accept-deliverables"), token,
                "{\"evidenceVersion\":" + Integer.parseInt(required("PAYAGENT_TEST_EVIDENCE_VERSION")) + "}").statusCode());
        var afterResponse = get(route("/settlement"), token);
        assertEquals(200, afterResponse.statusCode());
        var after = mapper.readTree(afterResponse.body());
        assertEquals("AUTHORIZED", after.path("paymentStatus").asText());
        assertTrue(after.path("paypalCaptureId").isNull());
    }

    private URI route(String suffix) {
        UUID id = UUID.fromString(required("PAYAGENT_TEST_DEAL_ID"));
        return base.resolve("/api/v1/deals/" + id + suffix);
    }

    private void requireSameOrigin(URI uri) {
        assertEquals(base.getScheme(), uri.getScheme());
        assertEquals(base.getHost(), uri.getHost());
        assertEquals(base.getPort(), uri.getPort());
        assertNull(uri.getUserInfo(), "No credential-bearing URLs");
    }

    private HttpResponse<byte[]> get(URI uri, String token) throws Exception {
        requireSameOrigin(uri);
        return http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> post(URI uri, String token, String body) throws Exception {
        requireSameOrigin(uri);
        return http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).header("Idempotency-Key", "release-" + UUID.randomUUID())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String required(String key) {
        String value = System.getenv(key);
        assertNotNull(value, "Set " + key + " for the disposable Sandbox test environment");
        assertFalse(value.isBlank(), "Set " + key + " locally");
        return value;
    }
}
