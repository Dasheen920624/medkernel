package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.knowledge.Citation;
import com.medkernel.engine.knowledge.CitationRelation;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeCandidateResponse;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityService;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeSourceCreateRequest;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 诊断知识服务：标准新增映射+审计、测试病例发布门禁（命中一致放行/不一致 ENG_DX_006）、置信策略回退/缺失 ENG_DX_005。 */
class DiagnosisKnowledgeServiceTest {

    private DiagnosisCriterionRepository criteria;
    private DiagnosisDifferentialRepository differentials;
    private DiagnosisCarePointerRepository carePointers;
    private DiagnosisTestCaseRepository testCases;
    private DiagnosisConfidencePolicyRepository policies;
    private AuditRecorder audit;
    private KnowledgeIdentityService knowledgeIdentities;
    private KnowledgeVersionService knowledgeVersions;
    private DiagnosisReferenceValidator references;
    private DiagnosisKnowledgeService service;

    private final DiagnosisConfidencePolicy defaultPolicy = new DiagnosisConfidencePolicy(
        1L, "t-1", "DEFAULT", 2, true, 1, null, "system", null, "system", null);

    @BeforeEach
    void setUp() {
        criteria = mock(DiagnosisCriterionRepository.class);
        differentials = mock(DiagnosisDifferentialRepository.class);
        carePointers = mock(DiagnosisCarePointerRepository.class);
        testCases = mock(DiagnosisTestCaseRepository.class);
        policies = mock(DiagnosisConfidencePolicyRepository.class);
        audit = mock(AuditRecorder.class);
        knowledgeIdentities = mock(KnowledgeIdentityService.class);
        knowledgeVersions = mock(KnowledgeVersionService.class);
        references = mock(DiagnosisReferenceValidator.class);
        // 命中核心用真实实现，门禁测试才有意义。
        DiagnosisMatcher matcher = new DiagnosisMatcher(new DiagnosisConfidenceEvaluator());
        service = new DiagnosisKnowledgeService(criteria, differentials, carePointers, testCases,
            policies, matcher, audit, knowledgeIdentities, knowledgeVersions, references);

        when(criteria.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(carePointers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(knowledgeVersions.getVersion(10L)).thenReturn(diagnosisVersion(
            10L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, 7L));
        when(knowledgeIdentities.get(7L)).thenReturn(diagnosisIdentity(7L));
        // 非主租户：策略回退到平台主源 t-1 DEFAULT。
        when(policies.findByTenantIdAndScopeKey("t-dept", "DEFAULT")).thenReturn(Optional.empty());
        when(policies.findByTenantIdAndScopeKey("t-1", "DEFAULT")).thenReturn(Optional.of(defaultPolicy));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-dx", OrgScope.tenant("t-dept"), "doctor-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void addCriterionPersistsWithContextAndAudits() {
        service.addCriterion(10L, new DiagnosisCriterionRequest(
            "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR, null, null, null));

        ArgumentCaptor<DiagnosisCriterion> cap = ArgumentCaptor.forClass(DiagnosisCriterion.class);
        verify(criteria).save(cap.capture());
        DiagnosisCriterion saved = cap.getValue();
        assertThat(saved.tenantId()).isEqualTo("t-dept");
        assertThat(saved.diagnosisVersionId()).isEqualTo(10L);
        assertThat(saved.findingTermCode()).isEqualTo("FEVER");
        assertThat(saved.direction()).isEqualTo(DiagnosisDirection.REQUIRED);
        assertThat(saved.weight()).isEqualTo(DiagnosisWeight.MAJOR);
        assertThat(saved.createdBy()).isEqualTo("doctor-1");
        assertThat(saved.traceId()).isEqualTo("trace-dx");
        verify(audit).record(eq(AuditAction.CREATE), eq("mk_diagnosis_criterion"), any(), any());
    }

    @Test
    void addCarePointerPersistsStructuredTargetAsSoftSuggestion() {
        DiagnosisCarePointer saved = service.addCarePointer(10L, new DiagnosisCarePointerRequest(
            DiagnosisCarePointerType.PATHWAY,
            DiagnosisCareTargetType.PATHWAY,
            "PATH.CKD",
            "医师确认诊断后评估是否入径"
        ));

        assertThat(saved.pointerType()).isEqualTo(DiagnosisCarePointerType.PATHWAY);
        assertThat(saved.targetType()).isEqualTo(DiagnosisCareTargetType.PATHWAY);
        assertThat(saved.targetRef()).isEqualTo("PATH.CKD");
        assertThat(saved.isSoft()).isTrue();
        verify(audit).record(eq(AuditAction.CREATE), eq("mk_diagnosis_care_pointer"), any(), any());
    }

    @Test
    void addCarePointerRejectsIncompatiblePointerAndTargetTypes() {
        assertThatThrownBy(() -> service.addCarePointer(10L, new DiagnosisCarePointerRequest(
            DiagnosisCarePointerType.TREATMENT,
            DiagnosisCareTargetType.PATHWAY,
            "PATH.CKD",
            "错误绑定"
        )))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(carePointers, never()).save(any());
    }

    @Test
    void addCriterionRejectsMutationOfActiveDiagnosisVersion() {
        when(knowledgeVersions.getVersion(10L))
            .thenReturn(diagnosisVersion(10L, KnowledgeVersionStatus.ACTIVE, 7L));

        assertThatThrownBy(() -> service.addCriterion(10L, new DiagnosisCriterionRequest(
            "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR, null, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.CONFLICT);

        verify(criteria, never()).save(any());
    }

    @Test
    void createAssetBuildsEvidenceCompleteDiagnosisDraftInOneTransaction() {
        DiagnosisAssetCreateRequest request = diagnosisAssetRequest();
        SourceDocument source = sourceDocument(30L);
        SourceVersion sourceVersion = sourceVersion(31L, source.id());
        KnowledgeIdentity identity = diagnosisIdentity(7L);
        KnowledgeAssetVersion version = diagnosisVersion(
            10L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, identity.id());
        SourceFragment fragment = sourceFragment(32L, sourceVersion.id());
        Citation citation = citation(33L, version.id(), fragment.id());
        when(knowledgeIdentities.registerSource(any(KnowledgeSourceCreateRequest.class))).thenReturn(source);
        when(knowledgeIdentities.registerSourceVersion(eq(source.id()), any())).thenReturn(sourceVersion);
        when(knowledgeIdentities.createIdentity(any())).thenReturn(identity);
        when(knowledgeVersions.classifyCandidate(eq(identity.id()), any())).thenReturn(
            new KnowledgeCandidateResponse(identity.id(), List.of(version), List.of(), true,
                "NEW_ASSET", "已创建"));
        when(knowledgeIdentities.createFragment(any())).thenReturn(fragment);
        when(knowledgeIdentities.createCitation(any())).thenReturn(citation);

        DiagnosisAssetDraftResponse response = service.createAsset(request);

        assertThat(response.identity().domain()).isEqualTo(KnowledgeDomain.DIAGNOSIS);
        assertThat(response.version().id()).isEqualTo(10L);
        assertThat(response.citation().assetVersionId()).isEqualTo(10L);
        verify(knowledgeIdentities).createCitation(any());
    }

    @Test
    void createAssetRejectsEvidenceExcerptOutsideSourceContent() {
        DiagnosisAssetCreateRequest valid = diagnosisAssetRequest();
        DiagnosisAssetCreateRequest request = new DiagnosisAssetCreateRequest(
            valid.requestId(), valid.traceId(), valid.tenantId(), valid.groupId(),
            valid.hospitalId(), valid.campusId(), valid.siteId(), valid.departmentId(),
            valid.specialtyId(), valid.userId(), valid.roleCodes(), valid.packageVersion(),
            valid.identity(), valid.source(), valid.version(),
            new DiagnosisAssetCreateRequest.EvidenceInput(
                "section-1", "诊断标准", "原文中不存在的证据片段"));

        assertThatThrownBy(() -> service.createAsset(request))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(knowledgeIdentities, never()).registerSource(any(KnowledgeSourceCreateRequest.class));
    }

    @Test
    void createVersionBuildsEvidenceCompleteDraftForExistingDiagnosisIdentity() {
        DiagnosisAssetCreateRequest assetRequest = diagnosisAssetRequest();
        DiagnosisVersionCreateRequest request = new DiagnosisVersionCreateRequest(
            assetRequest.requestId(), assetRequest.traceId(), assetRequest.tenantId(),
            assetRequest.groupId(), assetRequest.hospitalId(), assetRequest.campusId(),
            assetRequest.siteId(), assetRequest.departmentId(), assetRequest.specialtyId(),
            assetRequest.userId(), assetRequest.roleCodes(), assetRequest.packageVersion(),
            assetRequest.source(), assetRequest.version(), assetRequest.evidence());
        SourceDocument source = sourceDocument(30L);
        SourceVersion sourceVersion = sourceVersion(31L, source.id());
        KnowledgeIdentity identity = diagnosisIdentity(7L);
        KnowledgeAssetVersion version = diagnosisVersion(
            12L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, identity.id());
        SourceFragment fragment = sourceFragment(32L, sourceVersion.id());
        Citation citation = citation(33L, version.id(), fragment.id());
        when(knowledgeIdentities.registerSource(any(KnowledgeSourceCreateRequest.class))).thenReturn(source);
        when(knowledgeIdentities.registerSourceVersion(eq(source.id()), any())).thenReturn(sourceVersion);
        when(knowledgeVersions.classifyCandidate(eq(identity.id()), any())).thenReturn(
            new KnowledgeCandidateResponse(identity.id(), List.of(version), List.of(), true,
                "SAME_IDENTITY_NEW_VERSION", "已创建"));
        when(knowledgeIdentities.createFragment(any())).thenReturn(fragment);
        when(knowledgeIdentities.createCitation(any())).thenReturn(citation);

        DiagnosisAssetDraftResponse response = service.createVersion(identity.id(), request);

        assertThat(response.identity().id()).isEqualTo(identity.id());
        assertThat(response.version().id()).isEqualTo(12L);
        verify(knowledgeIdentities, never()).createIdentity(any());
        verify(knowledgeIdentities).createCitation(any());
    }

    @Test
    void createVersionRejectsNonDiagnosisIdentity() {
        DiagnosisAssetCreateRequest assetRequest = diagnosisAssetRequest();
        DiagnosisVersionCreateRequest request = new DiagnosisVersionCreateRequest(
            assetRequest.requestId(), assetRequest.traceId(), assetRequest.tenantId(),
            assetRequest.groupId(), assetRequest.hospitalId(), assetRequest.campusId(),
            assetRequest.siteId(), assetRequest.departmentId(), assetRequest.specialtyId(),
            assetRequest.userId(), assetRequest.roleCodes(), assetRequest.packageVersion(),
            assetRequest.source(), assetRequest.version(), assetRequest.evidence());
        KnowledgeIdentity guideline = new KnowledgeIdentity(
            8L, "t-dept", "GUIDE.CKD", KnowledgeDomain.GUIDELINE, "慢性肾脏病指南",
            "NEPH", null, KnowledgeIdentityStatus.ACTIVE, null,
            Instant.now(), "doctor-1", Instant.now(), "doctor-1");
        when(knowledgeIdentities.get(8L)).thenReturn(guideline);

        assertThatThrownBy(() -> service.createVersion(8L, request))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(knowledgeIdentities, never()).registerSource(any(KnowledgeSourceCreateRequest.class));
    }

    @Test
    void publishGatePassesWhenTestCaseMatchesRecomputedConfidence() {
        stubVersionCriteria();
        when(testCases.findByTenantIdAndDiagnosisVersionId("t-dept", 10L))
            .thenReturn(List.of(testCase("CASE-1", "FEVER,COUGH", DiagnosisConfidence.STRONG)));

        assertThatCode(() -> service.publishGate(10L)).doesNotThrowAnyException();
    }

    @Test
    void publishGateThrowsDx006WhenExpectedMismatchesRecomputed() {
        stubVersionCriteria();
        // 命中实得 STRONG，但期望写成 WEAK → 门禁必须阻断发布。
        when(testCases.findByTenantIdAndDiagnosisVersionId("t-dept", 10L))
            .thenReturn(List.of(testCase("CASE-1", "FEVER,COUGH", DiagnosisConfidence.WEAK)));

        assertThatThrownBy(() -> service.publishGate(10L))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_DX_006);
    }

    @Test
    void publishGateRejectsVersionWithoutRegressionCases() {
        stubVersionCriteria();
        when(testCases.findByTenantIdAndDiagnosisVersionId("t-dept", 10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.publishGate(10L))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.ENG_DX_006);
    }

    @Test
    void publishGateRejectsCaseExpectingAnotherDiagnosisIdentity() {
        stubVersionCriteria();
        DiagnosisTestCase wrongIdentityCase = new DiagnosisTestCase(
            null, "t-dept", 10L, "CASE-WRONG-DX", "FEVER,COUGH", 99L,
            DiagnosisConfidence.STRONG, Instant.now(), "doctor-1", Instant.now(),
            "doctor-1", "trace-dx");
        when(testCases.findByTenantIdAndDiagnosisVersionId("t-dept", 10L))
            .thenReturn(List.of(wrongIdentityCase));

        assertThatThrownBy(() -> service.publishGate(10L))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.ENG_DX_006);
    }

    @Test
    void publishGateThrowsDx005WhenNoConfidencePolicy() {
        when(policies.findByTenantIdAndScopeKey("t-1", "DEFAULT")).thenReturn(Optional.empty());
        stubVersionCriteria();
        when(testCases.findByTenantIdAndDiagnosisVersionId("t-dept", 10L))
            .thenReturn(List.of(testCase("CASE-1", "FEVER,COUGH", DiagnosisConfidence.STRONG)));

        assertThatThrownBy(() -> service.publishGate(10L))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_DX_005);
    }

    @Test
    void publishDiagnosisBlocksActivationWhenGateFails() {
        stubVersionCriteria();
        when(testCases.findByTenantIdAndDiagnosisVersionId("t-dept", 10L))
            .thenReturn(List.of(testCase("CASE-1", "FEVER,COUGH", DiagnosisConfidence.WEAK)));

        assertThatThrownBy(() -> service.publishDiagnosis(
            1L, 10L, "上线", com.medkernel.engine.versioning.VersionPublishEvidence.empty()))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_DX_006);
        // 门禁真正生效：分级不符时绝不触达版本激活。
        verify(knowledgeVersions, never()).activate(any(), any(), any(), any());
    }

    private void stubVersionCriteria() {
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-dept", 10L)).thenReturn(List.of(
            criterion("FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            criterion("COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR)));
    }

    private DiagnosisCriterion criterion(String code, DiagnosisDirection dir, DiagnosisWeight w) {
        Instant now = Instant.now();
        return new DiagnosisCriterion(null, "t-dept", 10L, code, dir, w, null, null, null,
            now, "doctor-1", now, "doctor-1", "trace-dx");
    }

    private DiagnosisTestCase testCase(String code, String findings, DiagnosisConfidence expected) {
        Instant now = Instant.now();
        return new DiagnosisTestCase(null, "t-dept", 10L, code, findings, 7L, expected,
            now, "doctor-1", now, "doctor-1", "trace-dx");
    }

    private DiagnosisAssetCreateRequest diagnosisAssetRequest() {
        return new DiagnosisAssetCreateRequest(
            "req-1", "trace-dx", "t-dept", null, null, null, null, null, null,
            "doctor-1", List.of("clinical-governor"), "pkg-2026.06",
            new DiagnosisAssetCreateRequest.IdentityInput(
                "chronic-kidney-disease", "慢性肾脏病", "NEPH", "结构化诊断知识"),
            new DiagnosisAssetCreateRequest.SourceInput(
                "SRC.CKD.2026", SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE,
                "国家指南", "慢性肾脏病诊疗指南", "发布机构", "受控授权", "zh-CN",
                "2026", Instant.parse("2026-01-01T00:00:00Z"),
                "repository://guideline/ckd-2026", "指南总则。真实诊断标准原文。诊疗建议。"),
            new DiagnosisAssetCreateRequest.VersionInput(
                "2026", "2026 版", KnowledgeRiskLevel.HIGH,
                GradeEvidenceQuality.HIGH, GradeRecommendationStrength.STRONG, 12),
            new DiagnosisAssetCreateRequest.EvidenceInput(
                "section-1", "诊断标准", "真实诊断标准原文")
        );
    }

    private KnowledgeIdentity diagnosisIdentity(Long id) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(id, "t-dept", "DX.CKD", KnowledgeDomain.DIAGNOSIS,
            "慢性肾脏病", "NEPH", "结构化诊断知识", KnowledgeIdentityStatus.ACTIVE, null,
            now, "doctor-1", now, "doctor-1");
    }

    private KnowledgeAssetVersion diagnosisVersion(
            Long id, KnowledgeVersionStatus status, Long identityId) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            id, "t-dept", identityId, "2026", "2026 版", 30L, 31L, "hash", "section-1",
            status, KnowledgeRiskLevel.HIGH, SourceAuthorityLevel.B_GUIDELINE,
            GradeEvidenceQuality.HIGH, GradeRecommendationStrength.STRONG, null,
            "tenant:t-dept", "DEFAULT", "version:" + id, null, null, null, null,
            null, null, null, null, now, "doctor-1", now, "doctor-1", 12, null);
    }

    private SourceDocument sourceDocument(Long id) {
        Instant now = Instant.now();
        return new SourceDocument(id, "t-dept", "SRC.CKD.2026", SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE, "国家指南", "慢性肾脏病诊疗指南",
            "发布机构", "受控授权", "zh-CN", now, "doctor-1", now, "doctor-1");
    }

    private SourceVersion sourceVersion(Long id, Long sourceDocumentId) {
        return new SourceVersion(id, "t-dept", sourceDocumentId, "2026",
            Instant.parse("2026-01-01T00:00:00Z"), "hash",
            "repository://guideline/ckd-2026", "zh-CN", Instant.now(), "doctor-1");
    }

    private SourceFragment sourceFragment(Long id, Long sourceVersionId) {
        return new SourceFragment(id, "t-dept", sourceVersionId, "section-1",
            "诊断标准", "真实诊断标准原文", "fragment-hash", Instant.now());
    }

    private Citation citation(Long id, Long versionId, Long fragmentId) {
        return new Citation(id, "t-dept", versionId, fragmentId,
            CitationRelation.DERIVED_FROM, 100, null, null, Instant.now(), "doctor-1");
    }
}
