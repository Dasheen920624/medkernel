package com.medkernel.engine.knowledge;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.mockito.ArgumentCaptor;
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
 * API-03 标准知识资产 API 合同测试。
 *
 * <p>这些测试只验证客户可调用的 REST 合同：标准上下文、路径、候选审核响应和历史重放标识。
 * 服务层细节由 KnowledgeIdentityServiceTest / KnowledgeVersionServiceTest 覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class KnowledgeAssetApiContractTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    KnowledgeIdentityService identityService;

    @MockBean
    KnowledgeVersionService versionService;

    @MockBean
    KnowledgeExportService exportService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createIdentityRejectsMissingStandardContext() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/identities")
                .with(medicalAffairsJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "identity_code": "DRUG.ROSUVA",
                      "domain": "DRUG",
                      "subject": "瑞舒伐他汀说明书"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"))
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createIdentityAcceptsSnakeCaseStandardContext() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/identities")
                .with(medicalAffairsJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-knowledge-001",
                      "trace_id": "trace-knowledge-001",
                      "tenant_id": "t-1",
                      "group_id": "g-1",
                      "hospital_id": "h-1",
                      "campus_id": "c-1",
                      "site_id": "s-1",
                      "department_id": "d-1",
                      "specialty_id": "sp-1",
                      "user_id": "u-99",
                      "role_codes": ["medical-affairs"],
                      "package_version": "pkg-2026.06",
                      "identityCode": "DRUG.ROSUVA",
                      "domain": "DRUG",
                      "subject": "瑞舒伐他汀说明书"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void sourceVersionUsesNestedSourceRoute() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/sources/1/versions")
                .with(medicalAffairsJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-source-version-001",
                      "trace_id": "trace-source-version-001",
                      "tenant_id": "t-1",
                      "user_id": "u-99",
                      "role_codes": ["medical-affairs"],
                      "package_version": "pkg-2026.06",
                      "version_no": "2026",
                      "content": "瑞舒伐他汀说明书来源原文",
                      "file_uri": "file://controlled/source.pdf"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void versionSubmitRouteExistsUnderIdentity() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/identities/1/versions/10/submit")
                .with(medicalAffairsJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(standardContextJson()))
            .andExpect(status().isOk());
    }

    @Test
    void createVersionAcceptsGradeFields() throws Exception {
        when(versionService.classifyCandidate(eq(1L), any()))
            .thenReturn(candidateResponse(CandidateClassificationType.SAME_IDENTITY_NEW_VERSION));

        mvc.perform(post("/api/v1/engine/knowledge/identities/1/versions")
                .with(medicalAffairsJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-version-create-001",
                      "trace_id": "trace-version-create-001",
                      "tenant_id": "t-1",
                      "user_id": "u-99",
                      "role_codes": ["medical-affairs"],
                      "package_version": "pkg-2026.06",
                      "version_no": "2026",
                      "version_label": "2026 版",
                      "source_document_id": 7,
                      "source_version_id": 8,
                      "content": "真实指南内容",
                      "anchors": "[]",
                      "risk_level": "LOW",
                      "grade_quality": "HIGH",
                      "grade_strength": "STRONG"
                    }
                    """))
            .andExpect(status().isOk());

        ArgumentCaptor<KnowledgeVersionCreateRequest> requestCaptor =
            ArgumentCaptor.forClass(KnowledgeVersionCreateRequest.class);
        verify(versionService).classifyCandidate(eq(1L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().gradeQuality()).isEqualTo(GradeEvidenceQuality.HIGH);
        assertThat(requestCaptor.getValue().gradeStrength()).isEqualTo(GradeRecommendationStrength.STRONG);
    }

    @Test
    void citationsReadbackRouteReturnsEnvelope() throws Exception {
        when(identityService.listCitations(1L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/engine/knowledge/identities/1/citations")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void replayRouteMarksHistoricalVersion() throws Exception {
        when(versionService.replayVersion(eq(1L), eq(10L), eq("pkg-2026.06"), eq("ctx-snap-001")))
            .thenReturn(new KnowledgeReplayResponse(
                1L, 10L, "v1", KnowledgeVersionStatus.SUPERSEDED, true,
                "pkg-2026.06", "ctx-snap-001", "sha256-old", "[]", null, null
            ));

        mvc.perform(get("/api/v1/engine/knowledge/identities/1/versions/10/replay")
                .queryParam("packageVersion", "pkg-2026.06")
                .queryParam("snapshotId", "ctx-snap-001")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.historicalVersion").value(true))
            .andExpect(jsonPath("$.data.packageVersion").value("pkg-2026.06"))
            .andExpect(jsonPath("$.data.snapshotId").value("ctx-snap-001"));
    }

    @Test
    void candidatesRouteReturnsClassificationWorkflowContract() throws Exception {
        when(versionService.listCandidates(1L))
            .thenReturn(candidateResponse(CandidateClassificationType.SAME_IDENTITY_NEW_VERSION));

        mvc.perform(get("/api/v1/engine/knowledge/identities/1/candidates")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.reasonCode").value("SAME_IDENTITY_NEW_VERSION"))
            .andExpect(jsonPath("$.data.candidates").isArray())
            .andExpect(jsonPath("$.data.classifications[0].reviewStatus").value("PENDING_REPLACEMENT_REVIEW"));
    }

    @Test
    void candidateReviewRouteReturnsReviewDecisionContract() throws Exception {
        when(versionService.reviewCandidate(eq(77L), any()))
            .thenReturn(candidateResponse("APPROVED", CandidateReviewStatus.APPROVED));

        mvc.perform(post("/api/v1/engine/knowledge/candidates/77/review")
                .with(medicalAffairsJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-candidate-review-001",
                      "trace_id": "trace-candidate-review-001",
                      "tenant_id": "t-1",
                      "user_id": "u-99",
                      "role_codes": ["medical-affairs"],
                      "package_version": "pkg-2026.06",
                      "decision": "APPROVE",
                      "reason": "同意"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reasonCode").value("APPROVED"))
            .andExpect(jsonPath("$.data.classifications[0].reviewStatus").value("APPROVED"));
    }

    @Test
    void candidateDiffRouteReturnsStoredClassificationView() throws Exception {
        when(versionService.diffCandidate(77L))
            .thenReturn(candidateResponse(CandidateClassificationType.CONFLICT));

        mvc.perform(get("/api/v1/engine/knowledge/candidates/77/diff")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reasonCode").value("CONFLICT"))
            .andExpect(jsonPath("$.data.classifications[0].diffSummary").value("当前 ACTIVE 与候选对照"));
    }

    @Test
    void exportSubmitRejectsMissingStandardContext() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/exports")
                .with(exportJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "IDENTITIES"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor medicalAffairsJwt() {
        return jwt().jwt(token -> token
                .subject("api03-medical-affairs")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("medical-affairs")))
            .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("api03-doctor")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("doctor")))
            .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor exportJwt() {
        return jwt().jwt(token -> token
                .subject("api03-audit")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("audit-compliance")))
            .authorities(new SimpleGrantedAuthority("ROLE_AUDIT_COMPLIANCE"));
    }

    private static String standardContextJson() {
        return """
            {
              "request_id": "req-version-submit-001",
              "trace_id": "trace-version-submit-001",
              "tenant_id": "t-1",
              "user_id": "u-99",
              "role_codes": ["medical-affairs"],
              "package_version": "pkg-2026.06"
            }
            """;
    }

    private static KnowledgeCandidateResponse candidateResponse(CandidateClassificationType type) {
        return candidateResponse(type.name(), CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
    }

    private static KnowledgeCandidateResponse candidateResponse(String reasonCode, CandidateReviewStatus status) {
        Instant now = Instant.now();
        CandidateClassificationType type = CandidateClassificationType.valueOf(
            reasonCode.equals("APPROVED") ? "SAME_IDENTITY_NEW_VERSION" : reasonCode);
        KnowledgeAssetVersion candidate = new KnowledgeAssetVersion(
            22L, "t-1", 1L, "2026", "2026 版",
            7L, 8L, "a".repeat(64), "[]",
            status == CandidateReviewStatus.APPROVED ? KnowledgeVersionStatus.ACTIVE : KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW,
            KnowledgeRiskLevel.LOW, SourceAuthorityLevel.B_GUIDELINE,
            GradeEvidenceQuality.HIGH, GradeRecommendationStrength.STRONG, null,
            "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            status == CandidateReviewStatus.APPROVED
                ? KnowledgeAssetVersion.activeScopeKey(1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)
                : "version:22",
            null, null, null, null, null, null, null, null,
            now, "u-99", now, "u-99"
        );
        CandidateClassification classification = new CandidateClassification(
            77L, "t-1", "tenant:t-1", 1L, 22L, 5L, type, status,
            candidate.contentHash(), "content_hash 与身份匹配", "当前 ACTIVE 与候选对照",
            now, "u-99", now, "u-99"
        );
        return new KnowledgeCandidateResponse(
            1L,
            List.of(candidate),
            List.of(classification),
            true,
            reasonCode,
            "候选审核工作流测试响应"
        );
    }

    @SuppressWarnings("unused")
    private static KnowledgeIdentity identity(Long id) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            id, "t-1", "DRUG.ROSUVA", KnowledgeDomain.DRUG, "瑞舒伐他汀说明书",
            "sp-1", "真实来源说明书", KnowledgeIdentityStatus.ACTIVE, null,
            now, "u-99", now, "u-99"
        );
    }
}
