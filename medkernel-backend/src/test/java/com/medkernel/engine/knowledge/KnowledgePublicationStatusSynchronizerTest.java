package com.medkernel.engine.knowledge;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.PlatformTenant;

class KnowledgePublicationStatusSynchronizerTest {

    private static final Instant NOW = Instant.parse("2026-07-05T08:00:00Z");
    private static final String CONTENT_HASH = "a".repeat(64);

    private final KnowledgeIdentityRepository identities = mock(KnowledgeIdentityRepository.class);
    private final KnowledgeAssetVersionRepository versions = mock(KnowledgeAssetVersionRepository.class);
    private final KnowledgeSupersessionRepository supersessions = mock(KnowledgeSupersessionRepository.class);
    private final KnowledgePublicationStatusSynchronizer synchronizer =
        new KnowledgePublicationStatusSynchronizer(identities, versions, supersessions);

    KnowledgePublicationStatusSynchronizerTest() {
        when(versions.save(any(KnowledgeAssetVersion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.save(any(KnowledgeIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supersessions.save(any(KnowledgeSupersession.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void marksMatchingDiagnosticItemKnowledgeVersionActiveWhenUnifiedKnowledgeVersionIsPublished() {
        KnowledgeIdentity identity = identity(null);
        KnowledgeAssetVersion candidate =
            version(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, null, null);
        when(identities.findByTenantIdAndIdentityCode(PlatformTenant.ID, "plat:diagnostic_item:lab-potassium"))
            .thenReturn(Optional.of(identity));
        when(versions.findByTenantIdAndIdentityIdAndContentHash(
            PlatformTenant.ID, 1L, CONTENT_HASH))
            .thenReturn(Optional.of(candidate));
        when(versions.findActiveByEffectiveScope(
            PlatformTenant.ID, 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE))
            .thenReturn(Optional.empty());

        synchronizer.afterPublished(
            assetVersion(VersionedAssetType.KNOWLEDGE),
            NOW,
            "engine-operator",
            "trace-knowledge-sync");

        verify(versions).save(org.mockito.ArgumentMatchers.argThat(value ->
            value.id().equals(10L)
                && value.status() == KnowledgeVersionStatus.ACTIVE
                && value.effectiveFrom().equals(NOW)
                && value.reviewedBy().equals("engine-operator")
                && value.activatedAt().equals(NOW)
                && value.nextReviewAt().equals(NOW.atZone(java.time.ZoneOffset.UTC).plusMonths(12).toInstant())));
        verify(identities).save(org.mockito.ArgumentMatchers.argThat(value ->
            value.id().equals(1L)
                && value.currentVersionId().equals(10L)
                && value.updatedAt().equals(NOW)
                && value.updatedBy().equals("engine-operator")));
        verify(supersessions).save(org.mockito.ArgumentMatchers.argThat(value ->
            value.identityId().equals(1L)
                && value.oldVersionId() == null
                && value.newVersionId().equals(10L)
                && value.transitionType() == SupersessionType.ACTIVATE
                && value.transitionedAt().equals(NOW)
                && value.transitionedBy().equals("engine-operator")));
    }

    @Test
    void supersedesCurrentActiveVersionInSameEffectiveScope() {
        KnowledgeIdentity identity = identity(5L);
        KnowledgeAssetVersion active = version(KnowledgeVersionStatus.ACTIVE, 5L, NOW.minusSeconds(3600));
        KnowledgeAssetVersion candidate =
            version(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, null, null);
        when(identities.findByTenantIdAndIdentityCode(PlatformTenant.ID, "plat:diagnostic_item:lab-potassium"))
            .thenReturn(Optional.of(identity));
        when(versions.findByTenantIdAndIdentityIdAndContentHash(
            PlatformTenant.ID, 1L, CONTENT_HASH))
            .thenReturn(Optional.of(candidate));
        when(versions.findActiveByEffectiveScope(
            PlatformTenant.ID, 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE))
            .thenReturn(Optional.of(active));

        synchronizer.afterPublished(
            assetVersion(VersionedAssetType.KNOWLEDGE),
            NOW,
            "engine-operator",
            "trace-knowledge-sync");

        verify(versions).save(org.mockito.ArgumentMatchers.argThat(value ->
            value.id().equals(5L)
                && value.status() == KnowledgeVersionStatus.SUPERSEDED
                && value.effectiveTo().equals(NOW)
                && value.supersededAt().equals(NOW)));
        verify(supersessions).save(org.mockito.ArgumentMatchers.argThat(value ->
            value.oldVersionId().equals(5L)
                && value.newVersionId().equals(10L)
                && value.transitionType() == SupersessionType.REPLACE));
    }

    @Test
    void ignoresNonKnowledgePublishedVersions() {
        synchronizer.afterPublished(
            assetVersion(VersionedAssetType.RULE),
            NOW,
            "engine-operator",
            "trace-rule");

        verifyNoInteractions(identities, versions, supersessions);
    }

    private AssetVersion assetVersion(VersionedAssetType assetType) {
        return new AssetVersion(
            1L,
            "av-lab-potassium",
            PlatformTenant.ID,
            assetType,
            "plat:diagnostic_item:lab-potassium",
            "V1",
            "tenant:t-1",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            CONTENT_HASH,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            "version:av-lab-potassium",
            "knowledge-version:plat:diagnostic_item:lab-potassium:V1",
            NOW,
            null,
            NOW.minusSeconds(3600),
            "engine-operator",
            NOW,
            "engine-operator",
            "trace-knowledge-sync"
        );
    }

    private KnowledgeIdentity identity(Long currentVersionId) {
        return new KnowledgeIdentity(
            1L,
            PlatformTenant.ID,
            "plat:diagnostic_item:lab-potassium",
            KnowledgeDomain.DIAGNOSTIC_ITEM,
            "血钾检验说明书",
            null,
            null,
            KnowledgeIdentityStatus.ACTIVE,
            currentVersionId,
            NOW.minusSeconds(3600),
            "engine-operator",
            NOW.minusSeconds(3600),
            "engine-operator"
        );
    }

    private KnowledgeAssetVersion version(
            KnowledgeVersionStatus status,
            Long id,
            Instant effectiveFrom) {
        Long versionId = id == null ? 10L : id;
        return new KnowledgeAssetVersion(
            versionId,
            PlatformTenant.ID,
            1L,
            "V1",
            "本地上线演练血钾检验说明书",
            1L,
            1L,
            CONTENT_HASH,
            "{\"source\":\"local-e2e\"}",
            status,
            KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.D_HOSPITAL,
            GradeEvidenceQuality.LOW,
            GradeRecommendationStrength.WEAK,
            null,
            "tenant:t-1",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            status == KnowledgeVersionStatus.ACTIVE
                ? KnowledgeAssetVersion.activeScopeKey(
                    1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)
                : "version:" + versionId,
            effectiveFrom,
            null,
            status == KnowledgeVersionStatus.ACTIVE ? "engine-operator" : null,
            status == KnowledgeVersionStatus.ACTIVE ? NOW.minusSeconds(3600) : null,
            status == KnowledgeVersionStatus.ACTIVE ? NOW.minusSeconds(3600) : null,
            null,
            null,
            null,
            NOW.minusSeconds(3600),
            "engine-operator",
            NOW.minusSeconds(3600),
            "engine-operator",
            12,
            null
        );
    }
}
