package com.payagent.bootstrap;

import com.payagent.web.HealthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone controller test for the public health endpoint.
 * <p>
 * Uses {@link MockMvcBuilders#standaloneSetup(Object...)} to test the controller
 * in isolation without loading any Spring application context.
 * This test verifies the controller mapping and response shape but does NOT verify
 * that the real {@link com.payagent.config.SecurityConfig} permits this endpoint.
 * Security filter chain behavior is tested separately in integration tests.
 */
class HealthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("payagent-api"));
    }
}