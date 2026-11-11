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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflight_deployedFrontendOrigin_succeeds() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://payagent-web-89qf.onrender.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://payagent-web-89qf.onrender.com"));
    }

    @Test
    void preflight_deployedFrontendOrigin_onPaymentRoute_succeeds() throws Exception {
        mockMvc.perform(options("/api/v1/deals/11111111-1111-1111-1111-111111111111/settlement")
                        .header("Origin", "https://payagent-web-89qf.onrender.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://payagent-web-89qf.onrender.com"));
    }

    @Test
    void preflight_allowedOrigin_succeeds() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void preflight_untrustedOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginPost_allowedHeaders_present() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type,Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("Authorization")));
    }
}
