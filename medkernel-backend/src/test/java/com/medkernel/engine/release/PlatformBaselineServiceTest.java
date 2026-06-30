package com.medkernel.engine.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.versioning.AssetDependencyRepository;
import com.medkernel.engine.versioning.AssetIdentity;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetIdentityStatus;
import com.medkernel.engine.versioning.AssetPublicationStatusSynchronizer;
import com.medkernel.engine.versioning.AssetTechnicalValidationService;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

class PlatformBaselineServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-23T10:00:00Z");
    private final PlatformBaselineReleaseRepository releases =
        mock(PlatformBaselineReleaseRepository.class);
    private final PlatformBaselineItemRepository items =
        mock(PlatformBaselineItemRepository.class);
    private final AssetIdentityRepository identities = mock(AssetIdentityRepository.class);
    private final AssetVersionRepository versions = mock(AssetVersionRepository.class);
    private final AssetTechnicalValidationService validation =
        mock(AssetTechnicalValidationService.class);
    private final AssetDependencyRepository dependencies = mock(AssetDependencyRepository.class);
    private final AssetPublicationStatusSynchronizer publicationSynchronizer =
        mock(AssetPublicationStatusSynchronizer.class);
    private PlatformBaselineService service;

    @BeforeEach
    void setUp() {
        service = new PlatformBaselineService(
            releases,
            items,
            identities,
            versions,
            validation,
            dependencies,
            List.of(publicationSynchronizer),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(releases.save(any(PlatformBaselineRelease.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any(PlatformBaselineItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.save(any(AssetVersion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publishesMixedAssetChangesIntoOneCompleteImmutableBaseline() {
        PlatformBaselineRelease previous = new PlatformBaselineRelease(
            1L, "baseline-A1", 1L, "a".repeat(64),
            NOW.minusSeconds(3600), "operator-old",
            NOW.minusSeconds(3600), "operator-old", "trace-old");
        when(releases.findFirstByOrderByRevisionNoDesc()).thenReturn(Optional.of(previous));
        when(items.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc("baseline-A1"))
            .thenReturn(List.of(
                item("baseline-A1", VersionedAssetType.RULE, "RULE.OLD",
                    ReleaseEntryState.ACTIVE, "rule-old-v1", "V1", "1".repeat(64)),
                item("baseline-A1", VersionedAssetType.KNOWLEDGE, "KNOW.CKD",
                    ReleaseEntryState.ACTIVE, "know-v1", "V1", "2".repeat(64))
            ));
        when(identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(PlatformTenant.ID))
            .thenReturn(List.of(
                identity(VersionedAssetType.RULE, "RULE.OLD"),
                identity(VersionedAssetType.KNOWLEDGE, "KNOW.CKD"),
                identity(VersionedAssetType.PATHWAY, "PATH.CKD")
            ));
        AssetVersion knowledgeV2 = version(
            "know-v2", VersionedAssetType.KNOWLEDGE, "KNOW.CKD", "V2", "3".repeat(64));
        AssetVersion pathwayV1 = version(
            "path-v1", VersionedAssetType.PATHWAY, "PATH.CKD", "V1", "4".repeat(64));
        when(versions.findByVersionIdAndTenantId("know-v2", PlatformTenant.ID))
            .thenReturn(Optional.of(knowledgeV2));
        when(versions.findByVersionIdAndTenantId("path-v1", PlatformTenant.ID))
            .thenReturn(Optional.of(pathwayV1));
        when(dependencies
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                any(), any(), any(), any()))
            .thenReturn(List.of());

        PlatformBaselineRelease published = service.publish(new PlatformBaselinePublishCommand(
            List.of("know-v2", "path-v1"),
            List.of(new ReleaseAssetRef(VersionedAssetType.RULE, "RULE.OLD")),
            "operator-A",
            "trace-A"
        ));

        assertThat(published.revisionNo()).isEqualTo(2L);
        assertThat(published.manifestSha256()).matches("[0-9a-f]{64}");
        ArgumentCaptor<PlatformBaselineItem> saved =
            ArgumentCaptor.forClass(PlatformBaselineItem.class);
        verify(items, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues())
            .extracting(
                PlatformBaselineItem::assetType,
                PlatformBaselineItem::assetIdentity,
                PlatformBaselineItem::entryState,
                PlatformBaselineItem::versionNo)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.KNOWLEDGE, "KNOW.CKD", ReleaseEntryState.ACTIVE, "V2"),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.PATHWAY, "PATH.CKD", ReleaseEntryState.ACTIVE, "V1"),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.RULE, "RULE.OLD", ReleaseEntryState.DISABLED, null)
            );
        assertThat(knowledgeV2.status()).isEqualTo(AssetVersionStatus.DRAFT);
        verify(versions).save(org.mockito.ArgumentMatchers.argThat(
            value -> value.versionId().equals("know-v2")
                && value.status() == AssetVersionStatus.PUBLISHED));
        verify(validation).validateForPublish(knowledgeV2, "operator-A", "trace-A");
        verify(validation).validateForPublish(pathwayV1, "operator-A", "trace-A");
        verify(publicationSynchronizer).afterPublished(
            org.mockito.ArgumentMatchers.argThat(value ->
                value.versionId().equals("path-v1")
                    && value.assetType() == VersionedAssetType.PATHWAY
                    && value.status() == AssetVersionStatus.PUBLISHED),
            org.mockito.ArgumentMatchers.eq(NOW),
            org.mockito.ArgumentMatchers.eq("operator-A"),
            org.mockito.ArgumentMatchers.eq("trace-A"));
    }

    @Test
    void activatesAlreadyPublishedKnowledgeVersionInNextBaselineWithoutRepublishing() {
        AssetVersion publishedKnowledge = version(
            "know-v1", VersionedAssetType.KNOWLEDGE, "KNOW.REPORT.LAB", "V1", "7".repeat(64))
            .withStatus(AssetVersionStatus.PUBLISHED, "version:know-v1", NOW.minusSeconds(600), "operator-old");
        when(releases.findFirstByOrderByRevisionNoDesc()).thenReturn(Optional.empty());
        when(identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(PlatformTenant.ID))
            .thenReturn(List.of(identity(VersionedAssetType.KNOWLEDGE, "KNOW.REPORT.LAB")));
        when(versions.findByVersionIdAndTenantId("know-v1", PlatformTenant.ID))
            .thenReturn(Optional.of(publishedKnowledge));
        when(dependencies
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                any(), any(), any(), any()))
            .thenReturn(List.of());

        PlatformBaselineRelease release = service.publish(new PlatformBaselinePublishCommand(
            List.of("know-v1"),
            List.of(),
            "operator-A",
            "trace-A"
        ));

        assertThat(release.revisionNo()).isEqualTo(1L);
        ArgumentCaptor<PlatformBaselineItem> saved =
            ArgumentCaptor.forClass(PlatformBaselineItem.class);
        verify(items).save(saved.capture());
        assertThat(saved.getValue()).satisfies(item -> {
            assertThat(item.assetType()).isEqualTo(VersionedAssetType.KNOWLEDGE);
            assertThat(item.assetIdentity()).isEqualTo("KNOW.REPORT.LAB");
            assertThat(item.entryState()).isEqualTo(ReleaseEntryState.ACTIVE);
            assertThat(item.versionId()).isEqualTo("know-v1");
            assertThat(item.versionNo()).isEqualTo("V1");
        });
        verify(validation, never()).validateForPublish(publishedKnowledge, "operator-A", "trace-A");
        verify(versions, never()).save(any(AssetVersion.class));
        verify(publicationSynchronizer, never()).afterPublished(
            any(), any(), any(), any());
    }

    @Test
    void rejectsWithdrawnVersionFromPlatformBaselineChange() {
        AssetVersion withdrawn = version(
            "rule-v2", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "V2", "5".repeat(64))
            .withStatus(AssetVersionStatus.WITHDRAWN, "version:rule-v2", NOW, "operator-A");
        when(versions.findByVersionIdAndTenantId("rule-v2", PlatformTenant.ID))
            .thenReturn(Optional.of(withdrawn));
        when(releases.findFirstByOrderByRevisionNoDesc()).thenReturn(Optional.empty());
        when(identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(PlatformTenant.ID))
            .thenReturn(List.of(identity(VersionedAssetType.RULE, "RULE.RENAL.DOSE")));

        assertThatThrownBy(() -> service.publish(new PlatformBaselinePublishCommand(
            List.of("rule-v2"),
            List.of(),
            "operator-A",
            "trace-A"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("草稿或已发布")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void rejectsTwoVersionsOfTheSameStableIdentityInOneBaselineChange() {
        AssetVersion v2 = version(
            "rule-v2", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "V2", "5".repeat(64));
        AssetVersion v3 = version(
            "rule-v3", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "V3", "6".repeat(64));
        when(versions.findByVersionIdAndTenantId("rule-v2", PlatformTenant.ID))
            .thenReturn(Optional.of(v2));
        when(versions.findByVersionIdAndTenantId("rule-v3", PlatformTenant.ID))
            .thenReturn(Optional.of(v3));
        when(releases.findFirstByOrderByRevisionNoDesc()).thenReturn(Optional.empty());
        when(identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(PlatformTenant.ID))
            .thenReturn(List.of(identity(VersionedAssetType.RULE, "RULE.RENAL.DOSE")));

        assertThatThrownBy(() -> service.publish(new PlatformBaselinePublishCommand(
            List.of("rule-v2", "rule-v3"),
            List.of(),
            "operator-A",
            "trace-A"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("同一稳定资产身份")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsPublishingAndDisablingTheSameIdentityInOneChange() {
        AssetVersion v2 = version(
            "rule-v2", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "V2", "5".repeat(64));
        when(versions.findByVersionIdAndTenantId("rule-v2", PlatformTenant.ID))
            .thenReturn(Optional.of(v2));
        when(releases.findFirstByOrderByRevisionNoDesc()).thenReturn(Optional.empty());
        when(identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(PlatformTenant.ID))
            .thenReturn(List.of(identity(VersionedAssetType.RULE, "RULE.RENAL.DOSE")));

        assertThatThrownBy(() -> service.publish(new PlatformBaselinePublishCommand(
            List.of("rule-v2"),
            List.of(new ReleaseAssetRef(
                VersionedAssetType.RULE, "RULE.RENAL.DOSE")),
            "operator-A",
            "trace-A"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("同时发布和停用")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsAPlatformVersionWhoseStableIdentityIsRetired() {
        AssetVersion v2 = version(
            "rule-v2", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "V2", "5".repeat(64));
        when(versions.findByVersionIdAndTenantId("rule-v2", PlatformTenant.ID))
            .thenReturn(Optional.of(v2));
        when(releases.findFirstByOrderByRevisionNoDesc()).thenReturn(Optional.empty());
        AssetIdentity retired = identity(VersionedAssetType.RULE, "RULE.RENAL.DOSE");
        when(identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(PlatformTenant.ID))
            .thenReturn(List.of(new AssetIdentity(
                retired.id(), retired.tenantId(), retired.assetType(),
                retired.assetIdentity(), AssetIdentityStatus.RETIRED,
                retired.latestVersionSequence(), retired.createdAt(), retired.createdBy(),
                retired.updatedAt(), retired.updatedBy(), retired.traceId())));

        assertThatThrownBy(() -> service.publish(new PlatformBaselinePublishCommand(
            List.of("rule-v2"),
            List.of(),
            "operator-A",
            "trace-A"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("退役")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void rejectsTheWholeBaselineWhenAnyAssetFailsTechnicalValidation() {
        AssetVersion formula = version(
            "formula-v1", VersionedAssetType.FORMULA, "FORMULA.EGFR", "V1", "5".repeat(64));
        when(versions.findByVersionIdAndTenantId("formula-v1", PlatformTenant.ID))
            .thenReturn(Optional.of(formula));
        when(releases.findFirstByOrderByRevisionNoDesc()).thenReturn(Optional.empty());
        when(identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(PlatformTenant.ID))
            .thenReturn(List.of(identity(VersionedAssetType.FORMULA, "FORMULA.EGFR")));
        doThrow(new ApiException(ErrorCode.CONFLICT, "配置资产正文与版本内容哈希不一致"))
            .when(validation)
            .validateForPublish(formula, "operator-A", "trace-A");

        assertThatThrownBy(() -> service.publish(new PlatformBaselinePublishCommand(
            List.of("formula-v1"),
            List.of(),
            "operator-A",
            "trace-A"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("哈希")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
        verify(versions, never()).save(any());
        verify(releases, never()).save(any());
        verify(items, never()).save(any());
    }

    private AssetIdentity identity(VersionedAssetType type, String code) {
        return new AssetIdentity(
            1L, PlatformTenant.ID, type, code,
            AssetIdentityStatus.ACTIVE, 2L,
            NOW.minusSeconds(7200), "operator-old",
            NOW.minusSeconds(60), "operator-old", "trace-old");
    }

    private AssetVersion version(
            String versionId,
            VersionedAssetType type,
            String identity,
            String versionNo,
            String hash) {
        return new AssetVersion(
            1L, versionId, PlatformTenant.ID, type, identity, versionNo,
            "/", "ALL", hash,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT, "version:" + versionId, null,
            null, null, NOW.minusSeconds(60), "operator-A",
            NOW.minusSeconds(60), "operator-A", "trace-A");
    }

    private PlatformBaselineItem item(
            String releaseId,
            VersionedAssetType type,
            String identity,
            ReleaseEntryState state,
            String versionId,
            String versionNo,
            String hash) {
        return new PlatformBaselineItem(
            null, releaseId, PlatformTenant.ID, type, identity, state,
            versionId, versionNo, hash,
            NOW.minusSeconds(3600), "operator-old", "trace-old");
    }
}
