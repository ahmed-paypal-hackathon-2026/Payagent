package com.payagent.evidence.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EvidenceSignatureService {

    public static final long DEFAULT_TTL_SECONDS = 900; // 15 minutes

    private final byte[] secretBytes;

    public EvidenceSignatureService(
            @Value("${payagent.evidence.signing-secret:local-evidence-signing-secret-default-32-bytes}") String secret) {
        String effectiveSecret = (secret == null || secret.isBlank())
                ? "local-evidence-signing-secret-default-32-bytes"
                : secret;
        this.secretBytes = effectiveSecret.getBytes(StandardCharsets.UTF_8);
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
