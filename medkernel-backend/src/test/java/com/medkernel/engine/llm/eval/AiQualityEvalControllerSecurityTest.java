package com.medkernel.engine.llm.eval;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * AI 质量评测中心控制器权限测试（OPT-06，{@code llm.eval.manage}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AiQualityEvalControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelEvalService service;

    @MockBean
    private MedicalRegressionCaseManagementService caseManagementService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void clinicalUserCannotRunAiQualityEvaluation() throws Exception {
        mockMvc.perform(post("/api/v1/ai-eval/runs")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(runBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanRunAiQualityEvaluation() throws Exception {
        mockMvc.perform(post("/api/v1/ai-eval/runs")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(runBody()))
            .andExpect(status().isOk());
    }

    @Test
    void engineOperatorCanReadAiQualityTrend() throws Exception {
        mockMvc.perform(get("/api/v1/ai-eval/trends")
                .queryParam("capabilityCode", "recommendation.draft")
                .queryParam("modelVersion", "B0-Deterministic-Baseline")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk());
    }

    private static String runBody() {
        return """
            {
              "capabilityCode": "recommendation.draft",
              "providerCode": "b0-fixture",
              "modelVersion": "B0-Deterministic-Baseline",
              "promptVersion": "prompt:v1",
              "toolVersion": "tool:v1",
              "caseOutputs": []
            }
            """;
    }
}
