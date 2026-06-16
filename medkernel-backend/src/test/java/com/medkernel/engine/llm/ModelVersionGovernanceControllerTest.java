package com.medkernel.engine.llm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

/**
 * LLM-04 版本治理控制器权限与响应测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ModelVersionGovernanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelVersionGovernanceService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String BODY = """
        {
          "capabilityCode": "rule.draft",
          "promptVersion": "prompt:v1",
          "promptContent": "受控提示词正文",
          "toolVersion": "tool:v1",
          "toolContract": "{\\"name\\":\\"submitProductionCandidate\\"}",
          "modelVersion": "model:v1",
          "modelDescriptor": "claude-opus-4"
        }
        """;

    @Test
    void integrationOperatorCanPublishVersionBundle() throws Exception {
        when(service.publish(any(ModelVersionBundleRequest.class))).thenReturn(response("ACTIVE"));

        mockMvc.perform(post("/api/v1/model-versions/bundles")
                .with(jwt().jwt(token -> token
                    .subject("ops")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("integration-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.promptVersion").value("prompt:v1"))
            .andExpect(jsonPath("$.data.toolVersion").value("tool:v1"));

        verify(service).publish(any(ModelVersionBundleRequest.class));
    }

    @Test
    void doctorCannotPublishVersionBundle() throws Exception {
        mockMvc.perform(post("/api/v1/model-versions/bundles")
                .with(jwt().jwt(token -> token
                    .subject("doctor")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void clinicalReaderCanQueryActiveAndExport() throws Exception {
        when(service.active("rule.draft")).thenReturn(response("ACTIVE"));
        when(service.export("rule.draft"))
            .thenReturn(new ModelVersionExportResponse("tenant-1", "rule.draft", List.of(response("ACTIVE"))));

        mockMvc.perform(get("/api/v1/model-versions/capabilities/rule.draft/active")
                .with(jwt().jwt(token -> token
                    .subject("reader")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modelHash").value("m-hash"));

        mockMvc.perform(get("/api/v1/model-versions/capabilities/rule.draft/export")
                .with(jwt().jwt(token -> token
                    .subject("reader")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bundles[0].promptHash").value("p-hash"));

        verify(service).active(eq("rule.draft"));
        verify(service).export(eq("rule.draft"));
    }

    private ModelVersionBundleResponse response(String status) {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        return new ModelVersionBundleResponse(
            1L, "tenant-1", "rule.draft", "prompt:v1", "p-hash",
            "tool:v1", "t-hash", "model:v1", "m-hash", status, now, null);
    }
}
