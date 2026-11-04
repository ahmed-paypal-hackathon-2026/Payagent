package com.payagent.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthAndSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContractTypeScriptParityTest contractParity = new ContractTypeScriptParityTest();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contractParityAndSchemaMatch() throws Exception {
        contractParity.enumLiteralsMatchTypeScriptMirror();
        contractParity.recordFieldsMatchTypeScriptMirror();
        new ContractFixtureDeserializationTest().fixturesDeserializeToCanonicalTypes();
    }

    @Test
    void buyerAndSellerAuthenticateIndependently() throws Exception {
        registerAndLogin("buyer-wpa1@example.com");
        registerAndLogin("seller-wpa1@example.com");
    }

    private void registerAndLogin(String email) throws Exception {
        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn().getResponse().getContentAsString();

        JsonNode registerNode = objectMapper.readTree(registerResponse);
        assertThat(registerNode.get("token").asText()).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email));
    }
}
