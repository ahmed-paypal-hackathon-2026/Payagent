package com.payagent.releasegates;

import com.payagent.auth.*;
import com.payagent.web.HealthController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationReleaseGateTest {
    @Test
    void tst14_expiredJwtCannotPopulateSecurityContext() throws Exception {
        String secret = "release-test-only-jwt-secret-at-least-32-characters";
        JwtTokenProvider expiredIssuer = new JwtTokenProvider(secret, -1);
        JwtTokenProvider verifier = new JwtTokenProvider(secret, 60);
        String token = expiredIssuer.createToken(UUID.randomUUID(), "expired@example.com");
        assertFalse(verifier.validateToken(token));
        UserDetailsServiceImpl users = mock(UserDetailsServiceImpl.class);
        JwtAuthFilter filter = new JwtAuthFilter(verifier, users, mock(UserRepository.class), new org.springframework.mock.env.MockEnvironment());
        SecurityContextHolder.clearContext();
        try {
            var mvc = MockMvcBuilders.standaloneSetup(new HealthController()).addFilters(filter).build();
            mvc.perform(get("/api/v1/health").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verifyNoInteractions(users);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
