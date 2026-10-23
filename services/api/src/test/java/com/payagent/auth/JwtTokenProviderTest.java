package com.payagent.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        // Test secret: 32+ characters, HS256 compatible
        String secret = "test-jwt-secret-minimum-32-characters-long-for-testing";
        long validityMinutes = 60;
        jwtTokenProvider = new JwtTokenProvider(secret, validityMinutes);
    }

    @Test
    void createAndParseToken_roundTrip() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";

        String token = jwtTokenProvider.createToken(userId, email);

        assertNotNull(token);
        assertFalse(token.isBlank());

        UUID parsedUserId = jwtTokenProvider.getUserId(token);
        String parsedEmail = jwtTokenProvider.getEmail(token);

        assertEquals(userId, parsedUserId);
        assertEquals(email, parsedEmail);
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.string"));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";

        String token = jwtTokenProvider.createToken(userId, email);
        // Tamper with the token
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(jwtTokenProvider.validateToken(tampered));
    }
}