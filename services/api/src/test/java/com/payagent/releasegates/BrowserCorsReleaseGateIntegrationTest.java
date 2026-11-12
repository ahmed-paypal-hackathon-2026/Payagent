package com.payagent.releasegates;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The frontend defaults to localhost:3000 while its JSON API defaults to localhost:8080. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrowserCorsReleaseGateIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void browserLoginPreflightAllowsConfiguredDevelopmentOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}
