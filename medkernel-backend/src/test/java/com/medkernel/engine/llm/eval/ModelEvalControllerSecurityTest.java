package com.medkernel.engine.llm.eval;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
 * 模型评测控制器权限安全测试（LLM-07 T18，{@code llm.eval.manage}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ModelEvalControllerSecurityTest {

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

    private static final String BODY = """
        {
          "providerCode": "ollama-local",
          "modelVersion": "qwen2.5:7b",
          "capabilityCode": "knowledge.extract"
        }
        """;

    @Test
    void clinicalUserCannotRunEvaluation() throws Exception {
        mockMvc.perform(post("/api/v1/model-evaluations")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void qualityGovernorCanRunEvaluation() throws Exception {
        mockMvc.perform(post("/api/v1/model-evaluations")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void clinicalUserCannotCreateRegressionCase() throws Exception {
        mockMvc.perform(post("/api/v1/model-evaluations/regression-cases")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(caseBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    void qualityGovernorCanCreateRegressionCase() throws Exception {
        mockMvc.perform(post("/api/v1/model-evaluations/regression-cases")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR")))
                .contentType(MediaType.APPLICATION_JSON).content(caseBody()))
            .andExpect(status().isOk());
    }

    @Test
    void qualityGovernorCanBulkImportRegressionCases() throws Exception {
        mockMvc.perform(post("/api/v1/model-evaluations/regression-cases:bulk-import")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"cases":[%s]}
                    """.formatted(caseBody())))
            .andExpect(status().isOk());
    }

    @Test
    void qualityGovernorCanDisableRegressionCase() throws Exception {
        mockMvc.perform(post("/api/v1/model-evaluations/regression-cases/9:disable")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR"))))
            .andExpect(status().isOk());
    }

    @Test
    void qualityGovernorCanSignOffPendingEvaluation() throws Exception {
        mockMvc.perform(post("/api/v1/model-evaluations/9/sign-off")
                .with(jwt().jwt(token -> token
                    .subject("quality-reviewer").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("quality-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QUALITY_GOVERNOR"))))
            .andExpect(status().isOk());
    }

    @Test
    void platformGovernanceAdminCannotReplaceExpertSignOff() throws Exception {
        mockMvc.perform(post("/api/v1/model-evaluations/9/sign-off")
                .with(jwt().jwt(token -> token
                    .subject("platform-admin").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("platform-governance-admin")))
                    .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_GOVERNANCE_ADMIN"))))
            .andExpect(status().isForbidden());
    }

    private static String caseBody() {
        return """
            {
              "capabilityCode": "rule.draft",
              "caseInput": "请依据真实来源判断候选知识是否必须阻断。",
              "expectedPhrase": "必须阻断",
              "redLineType": "DOSE_LIMIT",
              "citationRequired": true,
              "caseVersion": "2026.1",
              "sourceReference": "source-version:77#dose-limit",
              "enabled": true
            }
            """;
    }
}
