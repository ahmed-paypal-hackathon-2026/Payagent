package com.payagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the CORS_ALLOWED_ORIGINS environment-provided value is
 * actually consumed by SecurityConfig (comma-separated list).
 */
@SpringBootTest(properties = "CORS_ALLOWED_ORIGINS=https://configured.example.com, https://staging.example.com")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsEnvOverrideIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflight_configuredOrigin_succeeds() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://configured.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://configured.example.com"));
    }

    @Test
    void preflight_secondConfiguredOrigin_succeeds() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://staging.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://staging.example.com"));
    }

    @Test
    void preflight_unconfiguredOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://unconfigured.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
