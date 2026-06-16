package com.medkernel.engine.knowledge.production.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import com.medkernel.shared.hash.Sha256ContentHash;

class KnowledgeGenerationTriageServiceTest {

    private static final String TENANT = "t-1";
    private static final String JOB = "job-1";

    private KnowledgeAssetVersionRepository versions;
    private GenerationTriageRepository triages;
    private KnowledgeGenerationTriageService service;

    @BeforeEach
    void setUp() {
        versions = mock(KnowledgeAssetVersionRepository.class);
        triages = mock(GenerationTriageRepository.class);
        when(triages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new KnowledgeGenerationTriageService(versions, triages);
    }

    @Test
    void classifiesNewAssetWhenTargetIdentityIsAbsent() {
        GenerationTriageDecision decision = service.evaluate(
            envelope("{\"sections\":{}}", SourceAuthorityLevel.B_GUIDELINE),
            context(null));

        assertThat(decision.state()).isEqualTo(GenerationTriageState.NEW_ASSET);
        assertThat(decision.action()).isEqualTo(GenerationTriageAction.SUBMIT_REVIEW);
        verify(versions, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void classifiesDuplicateAndSkipsReviewWhenContentHashAlreadyExists() {
        KnowledgeAssetEnvelope candidate = envelope("{\"sections\":{\"a\":\"same\"}}",
            SourceAuthorityLevel.B_GUIDELINE);
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, SourceAuthorityLevel.B_GUIDELINE, candidate.contentHash())));

        GenerationTriageDecision decision = service.evaluate(candidate, context(10L));

        assertThat(decision.state()).isEqualTo(GenerationTriageState.DUPLICATE);
        assertThat(decision.action()).isEqualTo(GenerationTriageAction.SKIP_DUPLICATE);
        assertThat(decision.matchedVersionId()).isEqualTo(5L);
        verify(triages).save(argThat(row -> row.triageState() == GenerationTriageState.DUPLICATE
            && row.action() == GenerationTriageAction.SKIP_DUPLICATE));
    }

    @Test
    void classifiesMinorRevisionWhenAuthorityIsSameAndContentDiffers() {
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, SourceAuthorityLevel.B_GUIDELINE, "a".repeat(64))));

        GenerationTriageDecision decision = service.evaluate(
            envelope("{\"sections\":{\"a\":\"changed\"}}", SourceAuthorityLevel.B_GUIDELINE),
            context(10L));

        assertThat(decision.state()).isEqualTo(GenerationTriageState.MINOR_REVISION);
        assertThat(decision.action()).isEqualTo(GenerationTriageAction.MERGE_REVIEW);
    }

    @Test
    void classifiesMajorUpgradeWhenCandidateAuthorityIsHigher() {
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, SourceAuthorityLevel.D_HOSPITAL, "a".repeat(64))));

        GenerationTriageDecision decision = service.evaluate(
            envelope("{\"sections\":{\"a\":\"higher authority\"}}", SourceAuthorityLevel.A_REGULATION),
            context(10L));

        assertThat(decision.state()).isEqualTo(GenerationTriageState.MAJOR_UPGRADE);
        assertThat(decision.action()).isEqualTo(GenerationTriageAction.UPGRADE_REVIEW);
    }

    @Test
    void classifiesConflictWhenPayloadDeclaresConflict() {
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, SourceAuthorityLevel.B_GUIDELINE, "a".repeat(64))));

        GenerationTriageDecision decision = service.evaluate(
            envelope("{\"triage\":{\"state\":\"CONFLICT\"},\"sections\":{}}", SourceAuthorityLevel.B_GUIDELINE),
            context(10L));

        assertThat(decision.state()).isEqualTo(GenerationTriageState.CONFLICT);
        assertThat(decision.action()).isEqualTo(GenerationTriageAction.CONFLICT_REVIEW);
    }

    @Test
    void classifiesDowngradeWhenCandidateAuthorityIsLower() {
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, SourceAuthorityLevel.A_REGULATION, "a".repeat(64))));

        GenerationTriageDecision decision = service.evaluate(
            envelope("{\"sections\":{\"a\":\"lower authority\"}}", SourceAuthorityLevel.E_FEEDBACK),
            context(10L));

        assertThat(decision.state()).isEqualTo(GenerationTriageState.DOWNGRADE);
        assertThat(decision.action()).isEqualTo(GenerationTriageAction.DOWNGRADE_REVIEW);
    }

    @Test
    void classifiesDeprecationWhenPayloadDeclaresRetirement() {
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            active(5L, SourceAuthorityLevel.B_GUIDELINE, "a".repeat(64))));

        GenerationTriageDecision decision = service.evaluate(
            envelope("{\"triage\":{\"deprecated\":true},\"sections\":{}}", SourceAuthorityLevel.B_GUIDELINE),
            context(10L));

        assertThat(decision.state()).isEqualTo(GenerationTriageState.DEPRECATION);
        assertThat(decision.action()).isEqualTo(GenerationTriageAction.RETIREMENT_REVIEW);
    }

    @Test
    void classifiesUncertainWhenExistingIdentityHasNoActiveBaseline() {
        when(versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, 10L)).thenReturn(List.of(
            draft(5L, SourceAuthorityLevel.B_GUIDELINE, "a".repeat(64))));

        GenerationTriageDecision decision = service.evaluate(
            envelope("{\"sections\":{\"a\":\"no baseline\"}}", SourceAuthorityLevel.B_GUIDELINE),
            context(10L));

        assertThat(decision.state()).isEqualTo(GenerationTriageState.UNCERTAIN);
        assertThat(decision.action()).isEqualTo(GenerationTriageAction.MANUAL_REVIEW);
    }

    private GenerationTriageContext context(Long targetIdentityId) {
        return new GenerationTriageContext(TENANT, JOB, targetIdentityId, VersionedAssetType.RULE);
    }

    private KnowledgeAssetEnvelope envelope(String payload, SourceAuthorityLevel authorityLevel) {
        return new KnowledgeAssetEnvelope(VersionedAssetType.RULE, "identity:10", "主题", "draft",
            List.of(), authorityLevel, null, null, KnowledgeRiskLevel.MEDIUM, TENANT,
            Sha256ContentHash.sha256(payload, "payload"), payload, AssetVersionStatus.DRAFT);
    }

    private KnowledgeAssetVersion active(Long id, SourceAuthorityLevel authorityLevel, String contentHash) {
        return version(id, KnowledgeVersionStatus.ACTIVE, authorityLevel, contentHash);
    }

    private KnowledgeAssetVersion draft(Long id, SourceAuthorityLevel authorityLevel, String contentHash) {
        return version(id, KnowledgeVersionStatus.DRAFT, authorityLevel, contentHash);
    }

    private KnowledgeAssetVersion version(
            Long id, KnowledgeVersionStatus status, SourceAuthorityLevel authorityLevel, String contentHash) {
        Instant now = Instant.EPOCH;
        String scope = "tenant:" + TENANT;
        return new KnowledgeAssetVersion(id, TENANT, 10L, "v" + id, "版本 " + id, 7L, 9L,
            contentHash, "[]", status, KnowledgeRiskLevel.MEDIUM, authorityLevel,
            GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.WEAK, null,
            scope, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            status == KnowledgeVersionStatus.ACTIVE
                ? KnowledgeAssetVersion.activeScopeKey(10L, scope, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)
                : "version:" + id,
            now, null, null, null, status == KnowledgeVersionStatus.ACTIVE ? now : null,
            null, null, null, now, "u", now, "u", 12, now);
    }
}
