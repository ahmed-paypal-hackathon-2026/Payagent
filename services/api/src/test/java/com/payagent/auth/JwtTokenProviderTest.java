package com.payagent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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