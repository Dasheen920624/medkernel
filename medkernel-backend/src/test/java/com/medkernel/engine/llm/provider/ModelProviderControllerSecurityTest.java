package com.medkernel.engine.llm.provider;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ModelProviderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelProviderGovernanceService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String BODY = """
        {
          "providerType": "OLLAMA",
          "endpointUri": "http://127.0.0.1:11434",
          "modelVersion": "qwen2.5:7b",
          "enabled": true
        }
        """;

    @Test
    void clinicalUserCannotConfigureProvider() throws Exception {
        mockMvc.perform(put("/api/v1/model-providers/ollama-local")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void integrationOperatorCanConfigureProvider() throws Exception {
        mockMvc.perform(put("/api/v1/model-providers/ollama-local")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }
}
