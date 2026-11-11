package com.payagent.evidence.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Profiles;

@Service
public class EvidenceSignatureService {

    public static final long DEFAULT_TTL_SECONDS = 900; // 15 minutes
    private static final int MIN_SECRET_BYTES = 32;

    private static final Set<String> REJECTED_PLACEHOLDERS = Set.of(
            "local-evidence-signing-secret-default-32-bytes",
            "local-evidence-signing-secret",
            "change-me-evidence-hmac-secret",
            "change-me",
            "evidence-signing-secret",
            "default",
            "test",
            "secret"
    );

    private final byte[] secretBytes;

    public EvidenceSignatureService(
            @Value("${payagent.evidence.signing-secret:}") String secret,
            Environment environment) {
        boolean localProfile = environment.acceptsProfiles(Profiles.of("local", "dev", "test"));

        if (secret == null || secret.isBlank()) {
            if (localProfile) {
                throw new IllegalStateException(
                        "PAYAGENT_EVIDENCE_SIGNING_SECRET must be set for local development too. "
                                + "Use a random value of at least 32 bytes; do not reuse the documented placeholder.");
            }
            throw new IllegalStateException(
                    "PAYAGENT_EVIDENCE_SIGNING_SECRET is required in production and must not be blank.");
        }

        String normalized = secret.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (REJECTED_PLACEHOLDERS.contains(lower)
                || lower.startsWith("change-me")
                || normalized.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "PAYAGENT_EVIDENCE_SIGNING_SECRET is missing, too short, or an insecure placeholder. "
                            + "Provide a random secret of at least 32 bytes.");
        }

        this.secretBytes = normalized.getBytes(StandardCharsets.UTF_8);
    }

    public String generateDownloadUrl(UUID dealId, UUID evidenceId) {
        long expires = Instant.now().getEpochSecond() + DEFAULT_TTL_SECONDS;
        String signature = computeSignature(evidenceId, dealId, expires);
        return "/api/v1/deals/" + dealId + "/evidence/" + evidenceId + "/download?expires=" + expires + "&sig=" + signature;
    }

    public boolean verifySignature(UUID dealId, UUID evidenceId, long expires, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }

        long now = Instant.now().getEpochSecond();
        if (now > expires) {
            return false;
        }

        String expected = computeSignature(evidenceId, dealId, expires);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = signature.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private String computeSignature(UUID evidenceId, UUID dealId, long expires) {
        try {
            String data = evidenceId + ":" + dealId + ":" + expires;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }
}
