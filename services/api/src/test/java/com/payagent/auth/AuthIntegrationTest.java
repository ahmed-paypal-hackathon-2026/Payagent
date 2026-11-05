package com.payagent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for authentication endpoints using real H2 database.
 * <p>
 * These tests verify the full authentication flow including:
 * - User registration and persistence
 * - Duplicate email handling (database unique constraint)
 * - Password hashing with BCrypt
 * - Valid login with JWT generation
 * - Invalid password rejection
 * - Unknown user rejection (no user enumeration)
 * - Bean validation (email format, password length)
 * <p>
 * Uses {@code @SpringBootTest} with H2 in-memory database configured via
 * {@code application-test.yml}. Flyway is disabled; schema is created by
 * Hibernate {@code ddl-auto: create-drop}. These tests do NOT verify Flyway
 * migrations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void register_createsUserAndReturnsToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "integration@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("integration@example.com"));
    }

    @Test
    void register_duplicateEmail_returnsConflict() throws Exception {
        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "duplicate-integration@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated());

        // Second registration with same email - should hit unique constraint
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "duplicate-integration@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        // Register first
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "login-integration@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated());

        // Then login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "login-integration@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("login-integration@example.com"));
    }

    @Test
    void login_withInvalidPassword_returnsUnauthorized() throws Exception {
        // Register first
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "wrongpass-integration@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated());

        // Login with wrong password
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "wrongpass-integration@example.com",
                                    "password": "wrongpassword"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_nonexistentUser_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "nonexistent-integration@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_invalidEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "not-an-email",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "valid@example.com",
                                    "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpoint_missingJwt_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/deals")
                        .header("Idempotency-Key", "missing-jwt-" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_invalidJwt_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/deals")
                        .header("Idempotency-Key", "invalid-jwt-" + UUID.randomUUID())
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_expiredJwt_returnsUnauthorized() throws Exception {
        JwtTokenProvider expiredIssuer = new JwtTokenProvider(
                "test-jwt-secret-minimum-32-characters-long", -1);
        String token = expiredIssuer.createToken(UUID.randomUUID(), "expired@example.com");

        mockMvc.perform(post("/api/v1/deals")
                        .header("Idempotency-Key", "expired-jwt-" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}