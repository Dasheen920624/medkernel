package com.medkernel.engine.knowledge.production.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.production.KnowledgeProductionCandidate;
import com.medkernel.engine.knowledge.production.KnowledgeProductionCandidateRepository;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowRun;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowRunRepository;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowRunStatus;
import com.medkernel.engine.knowledge.production.triage.GenerationTriage;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageAction;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageRepository;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageState;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class PublicationQualityRecordServiceTest {

    private static final String TENANT = "t-1";
    private static final String JOB = "job-1";
    private static final String HASH = "a".repeat(64);
    private static final String CANDIDATE_REF = "kv:11:v1";

    private AikGateResultRepository gateResults;
    private KnowledgeProductionCandidateRepository candidates;
    private GenerationTriageRepository triages;
    private KnowledgeShadowRunRepository shadowRuns;
    private KnowledgeAssetVersionRepository versions;
    private PublicationQualityRecordService service;

    @BeforeEach
    void setUp() {
        gateResults = mock(AikGateResultRepository.class);
        candidates = mock(KnowledgeProductionCandidateRepository.class);
        triages = mock(GenerationTriageRepository.class);
        shadowRuns = mock(KnowledgeShadowRunRepository.class);
        versions = mock(KnowledgeAssetVersionRepository.class);
        CandidateGate firstGate = gate("SOURCE_PRESENT");
        CandidateGate secondGate = gate("CONTENT_FORMAT");
        service = new PublicationQualityRecordService(
            List.of(firstGate, secondGate),
            gateResults,
            candidates,
            triages,
            shadowRuns,
            versions,
            new ObjectMapper());
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant(TENANT), "u-1"));
        when(candidates.findByTenantIdAndCandidateRefIn(TENANT, List.of(CANDIDATE_REF)))
            .thenReturn(List.of(candidate(CANDIDATE_REF, HASH)));
        when(versions.findByTenantIdAndId(TENANT, 101L)).thenReturn(Optional.of(version(101L, 11L, "v1", HASH)));
        when(gateResults.findByTenantIdAndJobCodeOrderByIdAsc(TENANT, JOB))
            .thenReturn(List.of(
                gateResult(1L, "SOURCE_PRESENT", true, null),
                gateResult(2L, "CONTENT_FORMAT", true, null)));
        when(triages.findByTenantIdAndJobCodeOrderByIdAsc(TENANT, JOB))
            .thenReturn(List.of(triage(HASH, GenerationTriageAction.SUBMIT_REVIEW)));
        when(shadowRuns.findByTenantIdAndJobCodeOrderByIdAsc(TENANT, JOB))
            .thenReturn(List.of(shadow(HASH, KnowledgeShadowRunStatus.PASSED, true, false)));
        when(gateResults.save(any(AikGateResult.class))).thenAnswer(invocation -> {
            AikGateResult row = invocation.getArgument(0);
            return new AikGateResult(
                900L, row.tenantId(), row.jobCode(), row.contentHash(), row.gateCode(),
                row.passed(), row.reason(), row.createdAt(), row.createdBy());
        });
    }

    @Test
    void createsImmutableRecordOnlyAfterReadingCompletePassedServerResults() {
        PublicationQualityRecord record = service.create(
            JOB, new PublicationQualityRecordRequest(CANDIDATE_REF, 11L, 101L));

        assertThat(record.id()).isEqualTo(900L);
        assertThat(record.candidateRef()).isEqualTo(CANDIDATE_REF);
        assertThat(record.versionId()).isEqualTo(101L);
        ArgumentCaptor<AikGateResult> saved = ArgumentCaptor.forClass(AikGateResult.class);
        verify(gateResults).save(saved.capture());
        assertThat(saved.getValue().gateCode()).isEqualTo(PublicationQualityRecordService.RECORD_GATE_CODE);
        assertThat(saved.getValue().passed()).isTrue();
        assertThat(saved.getValue().reason())
            .contains("\"candidateRef\":\"kv:11:v1\"", "\"versionId\":101", "\"contentHash\":\"" + HASH + "\"");
    }

    @Test
    void rejectsWhenAnyRequiredGateIsMissing() {
        when(gateResults.findByTenantIdAndJobCodeOrderByIdAsc(TENANT, JOB))
            .thenReturn(List.of(gateResult(1L, "SOURCE_PRESENT", true, null)));

        assertThatThrownBy(() -> service.create(
            JOB, new PublicationQualityRecordRequest(CANDIDATE_REF, 11L, 101L)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("缺少发布校验项")
            .hasMessageContaining("CONTENT_FORMAT");
        verify(gateResults, never()).save(any());
    }

    @Test
    void rejectsWhenAnyServerGateFailed() {
        when(gateResults.findByTenantIdAndJobCodeOrderByIdAsc(TENANT, JOB))
            .thenReturn(List.of(
                gateResult(1L, "SOURCE_PRESENT", true, null),
                gateResult(2L, "CONTENT_FORMAT", false, "内容指纹不一致")));

        assertThatThrownBy(() -> service.create(
            JOB, new PublicationQualityRecordRequest(CANDIDATE_REF, 11L, 101L)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("发布校验未通过")
            .hasMessageContaining("CONTENT_FORMAT");
        verify(gateResults, never()).save(any());
    }

    @Test
    void rejectsForgedRecordId() {
        when(gateResults.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requirePublishEvidence(404L, 11L, version(101L, 11L, "v1", HASH)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("发布质量校验记录")
            .hasMessageContaining("不存在");
    }

    @Test
    void rejectsRecordCreatedForAnotherCandidate() {
        AikGateResult foreign = new AikGateResult(
            901L, TENANT, "job-2", "b".repeat(64),
            PublicationQualityRecordService.RECORD_GATE_CODE, true,
            """
                {"candidateRef":"kv:22:v2","identityId":22,"versionId":202,"contentHash":"%s"}
                """.formatted("b".repeat(64)).trim(),
            Instant.now(), "u-2");
        when(gateResults.findById(901L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.requirePublishEvidence(901L, 11L, version(101L, 11L, "v1", HASH)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不属于当前候选");
    }

    private CandidateGate gate(String code) {
        CandidateGate gate = mock(CandidateGate.class);
        when(gate.code()).thenReturn(code);
        return gate;
    }

    private KnowledgeProductionCandidate candidate(String ref, String hash) {
        return new KnowledgeProductionCandidate(
            1L, TENANT, JOB, "knowledge:test", hash, ref, KnowledgeRiskLevel.LOW,
            Instant.parse("2026-06-22T10:00:00Z"), "u-1");
    }

    private AikGateResult gateResult(Long id, String code, boolean passed, String reason) {
        return new AikGateResult(id, TENANT, JOB, HASH, code, passed, reason, Instant.now(), "u-1");
    }

    private GenerationTriage triage(String hash, GenerationTriageAction action) {
        return new GenerationTriage(
            1L, TENANT, JOB, hash, VersionedAssetType.KNOWLEDGE, null, null, null,
            GenerationTriageState.NEW_ASSET, action, "进入审核", Instant.now(), "u-1");
    }

    private KnowledgeShadowRun shadow(
            String hash,
            KnowledgeShadowRunStatus status,
            boolean readyForReview,
            boolean degradationDetected) {
        return new KnowledgeShadowRun(
            1L, TENANT, JOB, VersionedAssetType.KNOWLEDGE, null, hash,
            "knowledge.production.knowledge", status, 1, 1, 0, 0,
            degradationDetected, readyForReview, "通过", Instant.now(), "u-1");
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, String versionNo, String hash) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            id, TENANT, identityId, versionNo, versionNo, 1L, 1L, hash, "[]",
            KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE, null, null, null,
            "tenant:" + TENANT, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE, "version:" + id,
            null, null, null, null, null, null, null, null,
            now, "u-1", now, "u-1", 12, null);
    }
}
