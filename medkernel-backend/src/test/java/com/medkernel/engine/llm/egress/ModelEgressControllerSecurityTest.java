package com.medkernel.engine.llm.egress;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private static final String CONFIRMATION_BODY = """
        {
          "capabilityCode": "knowledge.extract",
          "payloadHash": "hash-abc",
          "purpose": "生成机构知识草稿"
        }
        """;

    @Test
    void clinicalUserCannotManageEgressWhitelist() throws Exception {
        mockMvc.perform(put("/api/v1/model-egress/whitelist/knowledge.extract")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(WHITELIST_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanManageEgressWhitelist() throws Exception {
        mockMvc.perform(put("/api/v1/model-egress/whitelist/knowledge.extract")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(WHITELIST_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void clinicalUserCannotManageDataMinimizationPolicy() throws Exception {
        mockMvc.perform(put("/api/v1/data-minimization/policies/model-egress/knowledge.extract")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(WHITELIST_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanManageDataMinimizationPolicy() throws Exception {
        mockMvc.perform(put("/api/v1/data-minimization/policies/model-egress/knowledge.extract")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(WHITELIST_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void engineOperatorCanConfirmDesensitizedEgressPayload() throws Exception {
        mockMvc.perform(post("/api/v1/model-egress/confirmations")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CONFIRMATION_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void clinicalUserCannotConfirmDesensitizedEgressPayload() throws Exception {
        mockMvc.perform(post("/api/v1/data-minimization/policies/model-egress/confirmations")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CONFIRMATION_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditorCanReviewDesensitizedEgressConfirmations() throws Exception {
        mockMvc.perform(get("/api/v1/data-minimization/policies/model-egress/confirmations")
                .with(jwt().jwt(token -> token
                    .subject("audit-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("auditor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void clinicalUserCannotReviewDesensitizedEgressConfirmations() throws Exception {
        mockMvc.perform(get("/api/v1/data-minimization/policies/model-egress/confirmations")
                .with(jwt().jwt(token -> token
                    .subject("clinical-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
                .andExpect(status().isForbidden());
    }
}
