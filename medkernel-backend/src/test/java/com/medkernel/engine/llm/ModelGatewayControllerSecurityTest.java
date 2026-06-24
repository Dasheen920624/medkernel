package com.medkernel.engine.llm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class ModelGatewayControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelGatewayService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String TASK_BODY = """
        {
          "capabilityCode": "knowledge.extract",
          "inputData": "提取高血压病历信息",
          "timeoutSeconds": 60
        }
        """;

    private static final String POLICY_BODY = """
        {
          "routeStrategy": "BASELINE",
          "desensitizeStrategy": "MASK_ALL",
          "expectedSchema": "{\\"type\\":\\"object\\",\\"required\\":[\\"status\\"]}"
        }
        """;

    private static final String CATALOG_BODY = """
        {
          "displayName": "病历摘要",
          "description": "生成待人工审核的结构化病历摘要。",
          "category": "语义抽取",
          "enabled": true,
          "sortOrder": 25
        }
        """;

    @Test
    void testSubmitTaskWithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/model-capabilities/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSubmitTaskWithWriteRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/model-capabilities/tasks")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void testSubmitTaskWithReadOnlyRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/model-capabilities/tasks")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("auditor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_AUDITOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void testSubmitTaskWithWriteRoleButMissingTenant_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/model-capabilities/tasks")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void doctorCannotManageTenantModelPolicy() throws Exception {
        mockMvc.perform(put("/api/v1/model-capabilities/policies/knowledge.extract")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(POLICY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanManageTenantModelPolicy() throws Exception {
        mockMvc.perform(put("/api/v1/model-capabilities/policies/knowledge.extract")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(POLICY_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void doctorCannotManageGlobalModelCapabilityCatalog() throws Exception {
        mockMvc.perform(put("/api/v1/model-capabilities/catalog/custom.summary")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CATALOG_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanManageGlobalModelCapabilityCatalog() throws Exception {
        mockMvc.perform(put("/api/v1/model-capabilities/catalog/custom.summary")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CATALOG_BODY))
                .andExpect(status().isOk());
    }
}
