package com.medkernel.engine.knowledge.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

class KnowledgeDiffDetectionServiceTest {

    private static final String TENANT = "t-1";
    private static final Instant NOW = Instant.parse("2026-06-16T00:00:00Z");

    private KnowledgeAssetVersionRepository versions;
    private KnowledgeDiffRepository diffs;
    private ExpiryTaskRepository expiryTasks;
    private KnowledgeDiffDetectionService service;

    @BeforeEach
    void setUp() {
        versions = mock(KnowledgeAssetVersionRepository.class);
        diffs = mock(KnowledgeDiffRepository.class);
        expiryTasks = mock(ExpiryTaskRepository.class);
        when(diffs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(expiryTasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new KnowledgeDiffDetectionService(
            versions, diffs, expiryTasks, Clock.fixed(NOW, ZoneOffset.UTC));
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant(TENANT), "reviewer-1"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void returnsHonestEmptyWhenCandidateHashMatchesActiveAuthority() {
        KnowledgeAssetEnvelope candidate = envelope("同一内容", SourceAuthorityLevel.B_GUIDELINE);
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, candidate.contentHash(), NOW.plusSeconds(86400L * 30))));

        KnowledgeDiffDetection detection = service.detect(candidate, context(10L));

        assertThat(detection.updated()).isFalse();
        assertThat(detection.diffType()).isNull();
        assertThat(detection.currentVersionId()).isEqualTo(5L);
        assertThat(detection.basis()).contains("content_hash 与现行权威版本一致");
        verify(diffs, never()).save(any());
        verify(expiryTasks, never()).save(any());
    }

    @Test
    void createsExpiryTaskWithoutFakeDiffWhenSameContentIsReviewOverdue() {
        KnowledgeAssetEnvelope candidate = envelope("同一内容但现行版本超期", SourceAuthorityLevel.B_GUIDELINE);
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, candidate.contentHash(), NOW.minusSeconds(60))));

        KnowledgeDiffDetection detection = service.detect(candidate, context(10L));

        assertThat(detection.updated()).isFalse();
        assertThat(detection.diffType()).isNull();
        assertThat(detection.currentVersionId()).isEqualTo(5L);
        assertThat(detection.expiryTaskStatus()).isEqualTo(ExpiryTaskStatus.OPEN);
        assertThat(detection.basis()).contains("content_hash 与现行权威版本一致");
        verify(diffs, never()).save(any());
        verify(expiryTasks).save(argThat(task ->
            task.taskType() == ExpiryTaskType.REVIEW_OVERDUE
                && task.status() == ExpiryTaskStatus.OPEN
                && task.identityId().equals(10L)
                && task.versionId().equals(5L)
                && task.reason().contains("超过 next_review_at")));
    }

    @Test
    void recordsRevisionDiffWithoutTouchingAuthoritativeVersion() {
        KnowledgeAssetEnvelope candidate = envelope("新版内容", SourceAuthorityLevel.B_GUIDELINE);
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, "a".repeat(64), NOW.plusSeconds(86400L * 30))));

        KnowledgeDiffDetection detection = service.detect(candidate, context(10L));

        assertThat(detection.updated()).isTrue();
        assertThat(detection.diffType()).isEqualTo(KnowledgeDiffType.REVISED);
        assertThat(detection.currentVersionId()).isEqualTo(5L);
        verify(diffs).save(argThat(row ->
            row.diffType() == KnowledgeDiffType.REVISED
                && row.targetIdentityId().equals(10L)
                && row.currentVersionId().equals(5L)
                && row.candidateContentHash().equals(candidate.contentHash())
                && row.currentContentHash().equals("a".repeat(64))));
        verify(expiryTasks, never()).save(any());
        verify(versions, never()).save(any());
    }

    @Test
    void recordsDeprecationDiffAndCreatesExpiryReviewTask() {
        KnowledgeAssetEnvelope candidate = envelope(
            "{\"triage\":{\"retirement\":true},\"sections\":{\"evidence\":\"source recall\"}}",
            SourceAuthorityLevel.A_REGULATION);
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, "a".repeat(64), NOW.plusSeconds(86400L * 30))));

        KnowledgeDiffDetection detection = service.detect(candidate, context(10L));

        assertThat(detection.updated()).isTrue();
        assertThat(detection.diffType()).isEqualTo(KnowledgeDiffType.DEPRECATED);
        assertThat(detection.expiryTaskStatus()).isEqualTo(ExpiryTaskStatus.OPEN);
        verify(diffs).save(argThat(row -> row.diffType() == KnowledgeDiffType.DEPRECATED
            && row.basis().contains("声明废止")));
        verify(expiryTasks).save(argThat(task ->
            task.taskType() == ExpiryTaskType.SOURCE_DEPRECATED
                && task.status() == ExpiryTaskStatus.OPEN
                && task.identityId().equals(10L)
                && task.versionId().equals(5L)
                && task.reason().contains("SRC-1:v2:section-a")));
        verify(versions, never()).save(any());
    }

    @Test
    void recordsNewDiffWhenNoCurrentIdentityIsBound() {
        KnowledgeAssetEnvelope candidate = envelope("全新主题", SourceAuthorityLevel.B_GUIDELINE);

        KnowledgeDiffDetection detection = service.detect(candidate, context(null));

        assertThat(detection.updated()).isTrue();
        assertThat(detection.diffType()).isEqualTo(KnowledgeDiffType.NEW);
        assertThat(detection.currentVersionId()).isNull();
        verify(versions, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc(any(), any());
        verify(diffs).save(argThat(row -> row.diffType() == KnowledgeDiffType.NEW
            && row.assetIdentity().equals(candidate.assetIdentity())
            && row.candidateContentHash().equals(candidate.contentHash())));
    }

    private KnowledgeDiffContext context(Long targetIdentityId) {
        return new KnowledgeDiffContext("run-1", targetIdentityId);
    }

    private KnowledgeAssetEnvelope envelope(String payload, SourceAuthorityLevel authorityLevel) {
        return new KnowledgeAssetEnvelope(VersionedAssetType.KNOWLEDGE, "drug:aspirin", "阿司匹林",
            "v2", List.of(new AssetSourceRef("SRC-1:v2:section-a", authorityLevel)), authorityLevel,
            GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.STRONG, KnowledgeRiskLevel.MEDIUM,
            TENANT, Sha256ContentHash.sha256(payload, "payload"), payload, AssetVersionStatus.DRAFT);
    }

    private KnowledgeAssetVersion active(Long id, String contentHash, Instant nextReviewAt) {
        return new KnowledgeAssetVersion(
            id, TENANT, 10L, "v1", "当前版", 7L, 9L, contentHash, "[]",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.MEDIUM, SourceAuthorityLevel.B_GUIDELINE,
            GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.STRONG, null,
            "tenant:" + TENANT, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(10L, "tenant:" + TENANT, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            NOW.minusSeconds(86400L), null, "reviewer", NOW.minusSeconds(86400L),
            NOW.minusSeconds(86400L), null, null, null,
            NOW.minusSeconds(86400L), "u", NOW.minusSeconds(86400L), "u", 12, nextReviewAt);
    }
}
