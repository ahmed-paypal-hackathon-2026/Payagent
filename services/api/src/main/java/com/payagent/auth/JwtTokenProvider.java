package com.payagent.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final long validityInMs;
    private SecretKey secretKey;

    public JwtTokenProvider(
            @Value("${payagent.jwt.secret}") String secret,
            @Value("${payagent.jwt.validity-minutes:60}") long validityMinutes) {
        this.validityInMs = validityMinutes * 60 * 1000;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @PostConstruct
    protected void init() {
        if (secretKey == null) {
            throw new IllegalStateException("JWT secret key not configured");
        }
    }

    public String createToken(UUID userId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(validityInMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseToken(token).getPayload().getSubject());
    }

    public String getEmail(String token) {
        return parseToken(token).getPayload().get("email", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}