package com.medkernel.engine.knowledge.diagnosis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 诊断知识维护 API 安全/契约：认证、knowledge.read/write/publish 权限矩阵、@DataScope 租户门、发布门禁 ENG-DX-006 → 409。
 *
 * <p>角色权限取自 DefaultPermissionPolicy：MEDICAL_AFFAIRS 有 read/write/publish；DOCTOR 有 read 无 publish；GUEST 无知识权限。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DiagnosisKnowledgeApiContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DiagnosisKnowledgeService service;

    private static final String CRITERIA = "/api/v1/engine/knowledge/diagnosis/versions/10/criteria";
    private static final String ASSETS = "/api/v1/engine/knowledge/diagnosis/assets";
    private static final String NEW_VERSION =
        "/api/v1/engine/knowledge/diagnosis/identities/1/versions";
    private static final String PUBLISH = "/api/v1/engine/knowledge/diagnosis/identities/1/versions/10/publish";
    private static final String CRITERION_JSON =
        "{\"findingTermCode\":\"FEVER\",\"direction\":\"REQUIRED\",\"weight\":\"MAJOR\"}";

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    private static RequestPostProcessor tenantJwt(String roleAuthority) {
        return jwt().jwt(token -> token.subject("u-1").claim("tenant_id", "t-1"))
            .authorities(new SimpleGrantedAuthority(roleAuthority));
    }

    // —— 认证 ——

    @Test
    void unauthenticatedReadIsUnauthorized() throws Exception {
        mockMvc.perform(get(CRITERIA)).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedWriteIsUnauthorized() throws Exception {
        mockMvc.perform(post(CRITERIA).contentType(MediaType.APPLICATION_JSON).content(CRITERION_JSON))
            .andExpect(status().isUnauthorized());
    }

    // —— 权限矩阵（@PreAuthorize 先于 @DataScope）——

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestForbiddenFromReadAndWrite() throws Exception {
        mockMvc.perform(get(CRITERIA)).andExpect(status().isForbidden());
        mockMvc.perform(post(CRITERIA).contentType(MediaType.APPLICATION_JSON).content(CRITERION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorForbiddenFromPublish() throws Exception {
        mockMvc.perform(post(PUBLISH)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCanReachReadButDataScopeRejectsMissingTenant() throws Exception {
        mockMvc.perform(get(CRITERIA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_MEDICAL_AFFAIRS")
    void medicalAffairsCanReachWriteButDataScopeRejectsMissingTenant() throws Exception {
        mockMvc.perform(post(CRITERIA).contentType(MediaType.APPLICATION_JSON).content(CRITERION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    // —— 业务路径（带租户）——

    @Test
    void medicalAffairsWithTenantCanCreateCriterion() throws Exception {
        when(service.addCriterion(eq(10L), any())).thenReturn(new DiagnosisCriterion(
            100L, "t-1", 10L, "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR,
            null, null, null, Instant.now(), "u", Instant.now(), "u", "tr"));

        mockMvc.perform(post(CRITERIA).with(tenantJwt("ROLE_MEDICAL_AFFAIRS"))
                .contentType(MediaType.APPLICATION_JSON).content(CRITERION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.findingTermCode").value("FEVER"));
    }

    @Test
    void medicalAffairsWithTenantCanCreateEvidenceCompleteAsset() throws Exception {
        mockMvc.perform(post(ASSETS).with(tenantJwt("ROLE_MEDICAL_AFFAIRS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-dx-1",
                      "trace_id": "trace-dx-1",
                      "tenant_id": "t-1",
                      "user_id": "u-1",
                      "role_codes": ["clinical-governor"],
                      "package_version": "pkg-2026.06",
                      "identity": {
                        "identitySlug": "chronic-kidney-disease",
                        "subject": "慢性肾脏病"
                      },
                      "source": {
                        "sourceCode": "SRC.CKD.2026",
                        "sourceType": "GUIDELINE",
                        "authorityLevel": "B_GUIDELINE",
                        "authorityBasis": "国家指南",
                        "title": "慢性肾脏病诊疗指南",
                        "versionNo": "2026",
                        "fileUri": "repository://guideline/ckd-2026",
                        "content": "真实指南原文"
                      },
                      "version": {
                        "versionNo": "2026",
                        "riskLevel": "HIGH",
                        "gradeQuality": "HIGH",
                        "reviewCycleMonths": 12
                      },
                      "evidence": {
                        "anchorPath": "section-1",
                        "anchorLabel": "诊断标准",
                        "textExcerpt": "真实诊断标准原文"
                      }
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void medicalAffairsWithTenantCanCreateEvidenceCompleteVersion() throws Exception {
        mockMvc.perform(post(NEW_VERSION).with(tenantJwt("ROLE_MEDICAL_AFFAIRS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-dx-v2",
                      "trace_id": "trace-dx-v2",
                      "tenant_id": "t-1",
                      "user_id": "u-1",
                      "role_codes": ["clinical-governor"],
                      "package_version": "pkg-2026.06",
                      "source": {
                        "sourceCode": "SRC.CKD.2027",
                        "sourceType": "GUIDELINE",
                        "authorityLevel": "B_GUIDELINE",
                        "authorityBasis": "国家指南",
                        "title": "慢性肾脏病诊疗指南",
                        "versionNo": "2027",
                        "fileUri": "repository://guideline/ckd-2027",
                        "content": "真实诊断标准原文"
                      },
                      "version": {
                        "versionNo": "2027",
                        "riskLevel": "HIGH",
                        "gradeQuality": "HIGH",
                        "reviewCycleMonths": 12
                      },
                      "evidence": {
                        "anchorPath": "section-1",
                        "anchorLabel": "诊断标准",
                        "textExcerpt": "真实诊断标准原文"
                      }
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void publishSurfacesGateFailureAsConflict() throws Exception {
        when(service.publishDiagnosis(eq(1L), eq(10L), any(), any()))
            .thenThrow(new ApiException(ErrorCode.ENG_DX_006, "测试病例 CASE-1 期望 WEAK 实得 STRONG"));

        mockMvc.perform(post(PUBLISH).with(tenantJwt("ROLE_MEDICAL_AFFAIRS")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ENG-DX-006"));
    }
}
