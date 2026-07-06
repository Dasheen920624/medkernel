package com.medkernel.engine.safety;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ClinicalRedlineControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ClinicalRedlineService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void unauthenticatedReadReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/engine/safety/redlines"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void doctorCanReachReadEndpointButDataScopeRejectsMissingTenant() throws Exception {
        mockMvc.perform(get("/api/v1/engine/safety/redlines")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void doctorWithTenantCanReadConfiguredRedlineCatalog() throws Exception {
        when(service.activeCatalog(ClinicalRedlineCategory.DRUG_INTERACTION))
            .thenReturn(new ClinicalRedlineCatalogResponse(
                ClinicalRedlineContentStatus.CONFIGURED,
                ClinicalRedlineCategory.requiredSafetyCategories(),
                List.of(new ClinicalRedlineResponse(
                    "redline-ddi-warfarin-nsaid",
                    ClinicalRedlineCategory.DRUG_INTERACTION,
                    "RDL-DDI-001",
                    "2026.1",
                    ClinicalRedlineStatus.ACTIVE,
                    "华法林合并非甾体抗炎药出血风险",
                    "合用可能显著增加出血风险",
                    "{\"field\":\"medications[].code\",\"operator\":\"in\"}",
                    com.medkernel.engine.recommendation.RecommendationRiskLevel.CRITICAL,
                    "risk-matrix-critical-ddi",
                    "4",
                    com.medkernel.engine.cdss.risk.CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
                    168,
                    "OPT04_REDLINE_SILENT_TRIAL",
                    "药品说明书与临床指南证据",
                    "source-version:42#section-1",
                    42L,
                    false)),
                "trace-redline"));

        mockMvc.perform(get("/api/v1/engine/safety/redlines")
                .param("category", "DRUG_INTERACTION")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentStatus").value("CONFIGURED"))
            .andExpect(jsonPath("$.data.redlines[0].redlineId").value("redline-ddi-warfarin-nsaid"))
            .andExpect(jsonPath("$.data.redlines[0].lowerTenantOverrideAllowed").value(false));
    }

    @Test
    void unauthenticatedDryRunReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/engine/safety/redlines:dry-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDryRunJson()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void engineOperatorWithTenantCanCreateSafetyRedlineDraft() throws Exception {
        when(service.createDraft(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ClinicalRedlineResponse(
                "redline-local-rehearsal-baseline",
                ClinicalRedlineCategory.DOSE_LIMIT,
                "RDL-LOCAL-REHEARSAL",
                "2026.1",
                ClinicalRedlineStatus.DRAFT,
                "本地上线演练安全红线",
                "用于验证安全红线草稿创建后仍需静默试运行和上线门禁。",
                "{\"all\":[{\"field\":\"medications[].dose\",\"operator\":\"gt\",\"value\":1}]}",
                com.medkernel.engine.recommendation.RecommendationRiskLevel.CRITICAL,
                "risk-matrix-local-rehearsal",
                "4",
                com.medkernel.engine.cdss.risk.CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
                168,
                "OPT04_REDLINE_SILENT_TRIAL",
                "本地上线演练安全证据",
                "source-version:local-e2e#safety-redline",
                null,
                false));

        mockMvc.perform(post("/api/v1/engine/safety/redlines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDraftJson())
                .with(jwt().jwt(token -> token
                    .subject("engine-operator")
                    .claim("tenant_id", "t-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.redlineId").value("redline-local-rehearsal-baseline"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.lowerTenantOverrideAllowed").value(false));
    }

    @Test
    void doctorWithTenantCannotPromoteRedline() throws Exception {
        mockMvc.perform(post("/api/v1/engine/safety/redlines:promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "redlineId": "redline-ddi-warfarin-nsaid",
                      "trialId": "trial-pass",
                      "expectedRedlineVersion": "2026.2",
                      "promotionReason": "静默试运行达标"
                    }
                    """)
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void medicalAffairsWithTenantCanSubmitDryRunEvidence() throws Exception {
        when(service.dryRun(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ClinicalRedlineTrialResponse(
                "trial-pass",
                "redline-ddi-warfarin-nsaid",
                "RDL-DDI-001",
                "2026.2",
                ClinicalRedlineTrialStatus.PASSED,
                Instant.parse("2026-05-26T00:00:00Z"),
                Instant.parse("2026-06-03T00:00:00Z"),
                168,
                192,
                1200,
                18,
                1,
                0,
                true,
                "evidence://silent-trials/redline-ddi-warfarin-nsaid/2026.2",
                "trace-redline"));

        mockMvc.perform(post("/api/v1/engine/safety/redlines:dry-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDryRunJson())
                .with(jwt().jwt(token -> token
                    .subject("medical-admin-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("medical_affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PASSED"))
            .andExpect(jsonPath("$.data.requiredSilentHours").value(168))
            .andExpect(jsonPath("$.data.actualSilentHours").value(192));
    }

    private String validDryRunJson() {
        return """
            {
              "redlineId": "redline-ddi-warfarin-nsaid",
              "observedFrom": "2026-05-26T00:00:00Z",
              "observedTo": "2026-06-03T00:00:00Z",
              "evaluatedCaseCount": 1200,
              "matchedCaseCount": 18,
              "falsePositiveCaseCount": 1,
              "safetyIncidentCount": 0,
              "evidenceReference": "evidence://silent-trials/redline-ddi-warfarin-nsaid/2026.2",
              "operatorNote": "试运行窗口来自真实临床事件回放统计"
            }
            """;
    }

    private String validDraftJson() {
        return """
            {
              "redlineId": "redline-local-rehearsal-baseline",
              "category": "DOSE_LIMIT",
              "triggerPoint": "medication-prescribe",
              "scopeType": "TENANT",
              "scopeRef": "t-1",
              "redlineKey": "RDL-LOCAL-REHEARSAL",
              "redlineVersion": "2026.1",
              "hazardSeverity": "CRITICAL",
              "riskMatrixId": "risk-matrix-local-rehearsal",
              "riskMatrixVersion": "4",
              "reviewRequirement": "PHYSICIAN_CONFIRMATION",
              "silentRunHours": 168,
              "releaseGate": "OPT04_REDLINE_SILENT_TRIAL",
              "title": "本地上线演练安全红线",
              "clinicalHazard": "用于验证安全红线草稿创建后仍需静默试运行和上线门禁。",
              "conditionDsl": "{\\"all\\":[{\\"field\\":\\"medications[].dose\\",\\"operator\\":\\"gt\\",\\"value\\":1}]}",
              "evidenceSource": "本地上线演练安全证据",
              "evidenceReference": "source-version:local-e2e#safety-redline",
              "lowerTenantOverrideAllowed": false
            }
            """;
    }
}
