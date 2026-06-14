package com.medkernel.engine.llm.egress;

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
class ModelEgressControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelEgressGovernanceService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String WHITELIST_BODY = """
        {
          "allowedFields": ["clinicalText"],
          "sensitivityLevel": "HIGH"
        }
        """;

    @Test
    void clinicalUserCannotManageEgressWhitelist() throws Exception {
        mockMvc.perform(put("/api/v1/model-egress/whitelist/knowledge.extract")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(WHITELIST_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void integrationOperatorCanManageEgressWhitelist() throws Exception {
        mockMvc.perform(put("/api/v1/model-egress/whitelist/knowledge.extract")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(WHITELIST_BODY))
                .andExpect(status().isOk());
    }
}
