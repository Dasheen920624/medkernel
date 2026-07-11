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

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
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

    @MockBean
    KnowledgeRetirementService retirementService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createIdentityRejectsMissingStandardContext() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/identities")
                .with(engineOperatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "identitySlug": "rosuvastatin-guide",
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
                .with(engineOperatorJwt())
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
                      "role_codes": ["engine-operator"],
                      "identitySlug": "rosuvastatin-guide",
                      "domain": "DRUG",
                      "subject": "瑞舒伐他汀说明书"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void sourceVersionUsesNestedSourceRoute() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/sources/1/versions")
                .with(engineOperatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-source-version-001",
                      "trace_id": "trace-source-version-001",
                      "tenant_id": "t-1",
                      "user_id": "u-99",
                      "role_codes": ["engine-operator"],
                      "versionNo": "2026",
                      "content": "瑞舒伐他汀说明书来源原文",
                      "fileUri": "file://controlled/source.pdf"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void versionSubmitRouteExistsUnderIdentity() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/identities/1/versions/10/submit")
                .with(engineOperatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(standardContextJson()))
            .andExpect(status().isOk());
    }

    @Test
    void createVersionAcceptsGradeFields() throws Exception {
        when(versionService.classifyCandidate(eq(1L), any()))
            .thenReturn(candidateResponse(CandidateClassificationType.SAME_IDENTITY_NEW_VERSION));

        mvc.perform(post("/api/v1/engine/knowledge/identities/1/versions")
                .with(engineOperatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-version-create-001",
                      "trace_id": "trace-version-create-001",
                      "tenant_id": "t-1",
                      "user_id": "u-99",
                      "role_codes": ["engine-operator"],
                      "versionNo": "2026",
                      "versionLabel": "2026 版",
                      "sourceDocumentId": 7,
                      "sourceVersionId": 8,
                      "content": "真实指南内容",
                      "anchors": "[]",
                      "riskLevel": "LOW",
                      "gradeQuality": "HIGH",
                      "gradeStrength": "STRONG",
                      "reviewCycleMonths": 12
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
    void citationCreateRouteAcceptsStructuredEvidenceLink() throws Exception {
        mvc.perform(post("/api/v1/engine/knowledge/citations")
                .with(engineOperatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assetVersionId": 10,
                      "sourceFragmentId": 20,
                      "relation": "DERIVED_FROM",
                      "weight": 90,
                      "startOffset": 0,
                      "endOffset": 12
                    }
                    """))
            .andExpect(status().isOk());

        ArgumentCaptor<CitationCreateRequest> requestCaptor =
            ArgumentCaptor.forClass(CitationCreateRequest.class);
        verify(identityService).createCitation(requestCaptor.capture());
        assertThat(requestCaptor.getValue().assetVersionId()).isEqualTo(10L);
        assertThat(requestCaptor.getValue().sourceFragmentId()).isEqualTo(20L);
        assertThat(requestCaptor.getValue().relation()).isEqualTo(CitationRelation.DERIVED_FROM);
    }

    @Test
    void sourceEvidenceRouteReturnsPrioritizedDisplayContract() throws Exception {
        when(identityService.listSourceEvidence(1L)).thenReturn(List.of(sourceEvidence()));

        mvc.perform(get("/api/v1/engine/knowledge/identities/1/source-evidence")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].authorityLabel").value("A 法规"))
            .andExpect(jsonPath("$.data[0].displayRole").value("PRIMARY"))
            .andExpect(jsonPath("$.data[0].recommendedByDefault").value(true))
            .andExpect(jsonPath("$.data[0].supplementary").value(false))
            .andExpect(jsonPath("$.data[0].displayLabel").value("A 法规 · 主证据"))
            .andExpect(jsonPath("$.data[0].rankingReason").value("按可信分级、来源发布时间和适用域精确度排序"));
    }

    @Test
    void sourceVersionFragmentsRouteReturnsParsedFragmentReadback() throws Exception {
        when(identityService.listSourceVersionFragments(8L)).thenReturn(List.of(sourceFragment()));

        mvc.perform(get("/api/v1/engine/knowledge/sources/versions/8/fragments")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(20))
            .andExpect(jsonPath("$.data[0].sourceVersionId").value(8))
            .andExpect(jsonPath("$.data[0].anchorPath").value("section-3.2.1"))
            .andExpect(jsonPath("$.data[0].textExcerpt").value("用于符合适应证的患者。"))
            .andExpect(jsonPath("$.data[0].contentHash").value("fragment-hash"));
    }

    @Test
    void provenanceRouteReturnsExactSourceChainInsteadOfAuditSnapshot() throws Exception {
        when(identityService.getProvenance(eq(1L), any())).thenReturn(provenance());

        mvc.perform(get("/api/v1/engine/knowledge/identities/1/provenance")
                .param("page", "1")
                .param("size", "20")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.identity.identityCode").value("plat:drug:rosuvastatin-guide"))
            .andExpect(jsonPath("$.data.currentVersionId").value(22))
            .andExpect(jsonPath("$.data.versions.items[0].id").value(22))
            .andExpect(jsonPath("$.data.versions.page").value(1))
            .andExpect(jsonPath("$.data.sourceEvidence[0].sourceVersionNo").value("2026.1"))
            .andExpect(jsonPath("$.data.sourceEvidence[0].sourceVersionHash").value("source-version-hash"))
            .andExpect(jsonPath("$.data.sourceEvidence[0].anchorPath").value("section-3.2.1"))
            .andExpect(jsonPath("$.data.sourceEvidence[0].anchorLabel").value("适应证"))
            .andExpect(jsonPath("$.data.sourceEvidence[0].fragmentHash").value("fragment-hash"))
            .andExpect(jsonPath("$.data.sourceEvidence[0].startOffset").value(0))
            .andExpect(jsonPath("$.data.sourceEvidence[0].endOffset").value(12))
            .andExpect(jsonPath("$.data.unresolvedCitationCount").value(1))
            .andExpect(jsonPath("$.data.partial").value(true));
    }

    @Test
    void reviewQueueRouteReturnsDueStatusAndReviewMetadata() throws Exception {
        KnowledgeIdentity identity = identity(1L);
        KnowledgeAssetVersion version = provenance().versions().items().getFirst();
        PageRequest pageRequest = new PageRequest(1, 20, "nextReviewAt,asc");
        when(versionService.listReviewQueue(eq(45), any())).thenReturn(PageResponse.of(List.of(
            new KnowledgeReviewQueueItem(identity, version, KnowledgeReviewStatus.OVERDUE, -3)), pageRequest, 1L));

        mvc.perform(get("/api/v1/engine/knowledge/review-queue")
                .queryParam("withinDays", "45")
                .queryParam("page", "1")
                .queryParam("size", "20")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].identity.identityCode")
                .value("plat:drug:rosuvastatin-guide"))
            .andExpect(jsonPath("$.data.items[0].version.reviewCycleMonths").value(12))
            .andExpect(jsonPath("$.data.items[0].status").value("OVERDUE"))
            .andExpect(jsonPath("$.data.items[0].daysUntilDue").value(-3))
            .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void deprecateRouteAcceptsSuccessorGracePeriodAndMigrationGuidance() throws Exception {
        Instant gracePeriodEnd = Instant.parse("2099-07-09T00:00:00Z");
        KnowledgeSupersession transition = new KnowledgeSupersession(
            9L, "t-1", 1L, 22L, 23L, SupersessionType.DEPRECATE,
            "进入迁移宽限期", Instant.parse("2026-06-09T00:00:00Z"), "u-99",
            2L, gracePeriodEnd, "迁移到新版指南并重新核对本地覆盖");
        when(retirementService.deprecate(eq(1L), any())).thenReturn(transition);

        mvc.perform(post("/api/v1/engine/knowledge/identities/1/deprecate")
                .with(engineOperatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "successorIdentityId": 2,
                      "gracePeriodEnd": "2099-07-09T00:00:00Z",
                      "migrationGuidance": "迁移到新版指南并重新核对本地覆盖"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.transitionType").value("DEPRECATE"))
            .andExpect(jsonPath("$.data.successorIdentityId").value(2))
            .andExpect(jsonPath("$.data.gracePeriodEnd").value("2099-07-09T00:00:00Z"))
            .andExpect(jsonPath("$.data.migrationGuidance")
                .value("迁移到新版指南并重新核对本地覆盖"));

        ArgumentCaptor<KnowledgeRetirementRequest> requestCaptor =
            ArgumentCaptor.forClass(KnowledgeRetirementRequest.class);
        verify(retirementService).deprecate(eq(1L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().successorIdentityId()).isEqualTo(2L);
        assertThat(requestCaptor.getValue().gracePeriodEnd()).isEqualTo(gracePeriodEnd);
    }

    @Test
    void replayRouteMarksHistoricalVersion() throws Exception {
        when(versionService.replayVersion(eq(1L), eq(10L), eq("ctx-snap-001")))
            .thenReturn(new KnowledgeReplayResponse(
                1L, 10L, "v1", KnowledgeVersionStatus.SUPERSEDED, true,
                "ctx-snap-001", "sha256-old", "[]", null, null
            ));

        mvc.perform(get("/api/v1/engine/knowledge/identities/1/versions/10/replay")
                .queryParam("snapshotId", "ctx-snap-001")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.historicalVersion").value(true))
            .andExpect(jsonPath("$.data.snapshotId").value("ctx-snap-001"));
    }

    @Test
    void candidatesRouteReturnsClassificationWorkflowContract() throws Exception {
        when(versionService.listCandidates(eq(1L), any()))
            .thenReturn(candidateResponse(CandidateClassificationType.SAME_IDENTITY_NEW_VERSION));

        mvc.perform(get("/api/v1/engine/knowledge/identities/1/candidates")
                .queryParam("page", "2")
                .queryParam("size", "10")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.reasonCode").value("SAME_IDENTITY_NEW_VERSION"))
            .andExpect(jsonPath("$.data.candidates.items[0].id").value(22))
            .andExpect(jsonPath("$.data.candidates.page").value(1))
            .andExpect(jsonPath("$.data.candidates.size").value(20))
            .andExpect(jsonPath("$.data.candidates.total").value(1))
            .andExpect(jsonPath("$.data.classifications[0].reviewStatus").value("PENDING_REPLACEMENT_REVIEW"));
        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(versionService).listCandidates(eq(1L), pageCaptor.capture());
        assertThat(pageCaptor.getValue().page()).isEqualTo(2);
        assertThat(pageCaptor.getValue().size()).isEqualTo(10);
    }

    @Test
    void candidateReviewRouteReturnsReviewDecisionContract() throws Exception {
        when(versionService.reviewCandidate(eq(77L), any()))
            .thenReturn(candidateResponse("APPROVED", CandidateReviewStatus.APPROVED));

        mvc.perform(post("/api/v1/engine/knowledge/candidates/77/review")
                .with(engineOperatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-candidate-review-001",
                      "trace_id": "trace-candidate-review-001",
                      "tenant_id": "t-1",
                      "user_id": "u-99",
                      "role_codes": ["engine-operator"],
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor engineOperatorJwt() {
        return jwt().jwt(token -> token
                .subject("api03-engine-operator")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("engine-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("api03-doctor")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("clinical-user")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor exportJwt() {
        return jwt().jwt(token -> token
                .subject("api03-audit")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("auditor")))
            .authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"));
    }

    private static String standardContextJson() {
        return """
            {
              "request_id": "req-version-submit-001",
              "trace_id": "trace-version-submit-001",
              "tenant_id": "t-1",
              "user_id": "u-99",
              "role_codes": ["engine-operator"]
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
        , 12, null);
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

    private static KnowledgeSourceEvidence sourceEvidence() {
        return new KnowledgeSourceEvidence(
            22L,
            1L,
            100L,
            7L,
            8L,
            "SRC.NHC.2026",
            "国家药品说明书",
            SourceType.POLICY,
            SourceAuthorityLevel.A_REGULATION,
            "A 法规",
            "国家卫健委发布文件编号 NHC-2026-01",
            "2026.1",
            "source-version-hash",
            "section-3.2.1",
            "适应证",
            "用于符合适应证的患者。",
            "fragment-hash",
            0,
            12,
            GradeEvidenceQuality.HIGH,
            GradeRecommendationStrength.STRONG,
            Instant.parse("2026-01-01T00:00:00Z"),
            CitationRelation.SUPPORTS,
            90,
            "tenant:t-1",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeSourceEvidenceRole.PRIMARY,
            true,
            false,
            "A 法规 · 主证据",
            "按可信分级、来源发布时间和适用域精确度排序",
            null
        );
    }

    private static SourceFragment sourceFragment() {
        return new SourceFragment(
            20L,
            "t-1",
            8L,
            "section-3.2.1",
            "适应证",
            "用于符合适应证的患者。",
            "fragment-hash",
            Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static KnowledgeProvenanceResponse provenance() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        KnowledgeIdentity identity = identity(1L);
        KnowledgeAssetVersion active = new KnowledgeAssetVersion(
            22L, "t-1", 1L, "v2026.1", "2026 版",
            7L, 8L, "asset-version-hash", null,
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.A_REGULATION, GradeEvidenceQuality.HIGH,
            GradeRecommendationStrength.STRONG, null,
            "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(
                1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            null, null, null, null, now, null, null, null,
            now, "u-99", now, "u-99"
        , 12, null);
        return new KnowledgeProvenanceResponse(
            identity,
            active.id(),
            PageResponse.of(List.of(active), PageRequest.defaults(), 1L),
            PageResponse.empty(PageRequest.defaults()),
            List.of(sourceEvidence()),
            1,
            true
        );
    }

    private static KnowledgeIdentity identity(Long id) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            id, "t-1", "plat:drug:rosuvastatin-guide", KnowledgeDomain.DRUG, "瑞舒伐他汀说明书",
            "sp-1", "真实来源说明书", KnowledgeIdentityStatus.ACTIVE, 22L,
            now, "u-99", now, "u-99"
        );
    }
}
