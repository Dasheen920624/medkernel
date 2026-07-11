package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.release.PlatformBaselineItem;
import com.medkernel.engine.release.PlatformBaselineItemRepository;
import com.medkernel.engine.release.PlatformBaselineRelease;
import com.medkernel.engine.release.PlatformBaselineReleaseRepository;
import com.medkernel.engine.release.ClinicalRuntimeReleaseItemOfflineSnapshot;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.AssetDependency;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetDependencyRepository;
import com.medkernel.engine.versioning.AssetIdentity;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetIdentityStatus;
import com.medkernel.engine.versioning.AssetPublicationStatusSynchronizer;
import com.medkernel.engine.versioning.AssetOwnershipScope;
import com.medkernel.engine.versioning.AssetScopeResolver;
import com.medkernel.engine.versioning.AssetTechnicalValidationService;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.PlatformTenant;

class ClinicalRuntimeReleaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-23T11:00:00Z");
    private final PlatformBaselineReleaseRepository baselines =
        mock(PlatformBaselineReleaseRepository.class);
    private final PlatformBaselineItemRepository baselineItems =
        mock(PlatformBaselineItemRepository.class);
    private final ClinicalRuntimeReleaseRepository releases =
        mock(ClinicalRuntimeReleaseRepository.class);
    private final ClinicalRuntimeReleaseItemRepository runtimeItems =
        mock(ClinicalRuntimeReleaseItemRepository.class);
    private final OrgUnitRepository organizations = mock(OrgUnitRepository.class);
    private final AssetIdentityRepository identities = mock(AssetIdentityRepository.class);
    private final AssetVersionRepository versions = mock(AssetVersionRepository.class);
    private final AssetTechnicalValidationService validation =
        mock(AssetTechnicalValidationService.class);
    private final AssetDependencyRepository dependencies = mock(AssetDependencyRepository.class);
    private final AssetScopeResolver assetScopes = mock(AssetScopeResolver.class);
    private final AssetPublicationStatusSynchronizer publicationSynchronizer =
        mock(AssetPublicationStatusSynchronizer.class);
    private ClinicalRuntimeReleaseService service;

    @BeforeEach
    void setUp() {
        service = new ClinicalRuntimeReleaseService(
            baselines,
            baselineItems,
            releases,
            runtimeItems,
            organizations,
            identities,
            versions,
            validation,
            dependencies,
            assetScopes,
            List.of(publicationSynchronizer),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(releases.save(any(ClinicalRuntimeRelease.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(runtimeItems.save(any(ClinicalRuntimeReleaseItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc("tenant-A"))
            .thenReturn(List.of());
        when(dependencies
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                any(), any(), any(), any()))
            .thenReturn(List.of());
    }

    @Test
    void activatesEveryRuntimeAssetTypeFromPlatformBaselineIntoHospitalManifest() {
        stubHospitalAndBaseline();
        List<PlatformBaselineItem> allRuntimeAssets = Arrays.stream(VersionedAssetType.values())
            .map(type -> baselineItem(
                type,
                "BASELINE." + type.name(),
                "av-" + type.name().toLowerCase(),
                "V1",
                Integer.toHexString(type.ordinal() + 1).repeat(64).substring(0, 64)))
            .toList();
        allRuntimeAssets.forEach(this::stubPublishedPlatformVersion);
        when(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "baseline-A8"))
            .thenReturn(allRuntimeAssets);
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.empty());

        service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            null,
            allRuntimeAssets.stream()
                .map(item -> ClinicalRuntimeAssetSelection.platform(
                    item.assetType(),
                    item.assetIdentity()))
                .toList(),
            "operator-A",
            "trace-all-assets"
        ));

        ArgumentCaptor<ClinicalRuntimeReleaseItem> saved =
            ArgumentCaptor.forClass(ClinicalRuntimeReleaseItem.class);
        verify(runtimeItems, org.mockito.Mockito.times(VersionedAssetType.values().length))
            .save(saved.capture());
        assertThat(saved.getAllValues())
            .filteredOn(item -> item.entryState() == ReleaseEntryState.ACTIVE)
            .extracting(ClinicalRuntimeReleaseItem::assetType)
            .containsExactly(Arrays.stream(VersionedAssetType.values())
                .sorted(Comparator.comparing(VersionedAssetType::name))
                .toArray(VersionedAssetType[]::new));
        assertThat(saved.getAllValues())
            .allSatisfy(item -> {
                assertThat(item.sourceLayer()).isEqualTo(ReleaseSourceLayer.PLATFORM);
                assertThat(item.versionId()).isNotBlank();
                assertThat(item.versionNo()).isEqualTo("V1");
                assertThat(item.contentHash()).matches("[0-9a-f]{64}");
            });
    }

    @Test
    void rejectsEmptyActiveAssetSelectionBeforeCreatingRuntimeRelease() {
        stubHospitalAndBaseline();

        assertThatThrownBy(() -> service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            null,
            List.of(),
            "operator-A",
            "trace-empty-assets"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("启用资产不能为空")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(releases, org.mockito.Mockito.never()).save(any(ClinicalRuntimeRelease.class));
        verify(runtimeItems, org.mockito.Mockito.never()).save(any(ClinicalRuntimeReleaseItem.class));
    }

    @Test
    void activatesAnyMixedAssetSetAndMaterializesTheCompleteManifest() {
        stubHospitalAndBaseline();
        AssetVersion localPathway = version(
            "path-local-v1",
            "tenant-A",
            VersionedAssetType.PATHWAY,
            "PATH.CKD.LOCAL",
            "V1",
            "4".repeat(64),
            "/tenant-A/group-A/hospital-A");
        when(versions.findByVersionIdAndTenantId("path-local-v1", "tenant-A"))
            .thenReturn(Optional.of(localPathway));
        when(assetScopes.resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A/hospital-A"))
            .thenReturn(new AssetOwnershipScope(
                ReleaseSourceLayer.HOSPITAL,
                "/tenant-A/group-A/hospital-A"));
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.PATHWAY, "PATH.CKD.LOCAL"))
            .thenReturn(Optional.of(new AssetIdentity(
                1L, "tenant-A", VersionedAssetType.PATHWAY, "PATH.CKD.LOCAL",
                AssetIdentityStatus.ACTIVE, 1L,
                NOW.minusSeconds(3600), "operator-old",
                NOW.minusSeconds(60), "operator-old", "trace-old")));
        when(dependencies
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                "tenant-A",
                VersionedAssetType.PATHWAY,
                "PATH.CKD.LOCAL",
                "path-local-v1"))
            .thenReturn(List.of(dependency(
                "tenant-A",
                VersionedAssetType.PATHWAY,
                "PATH.CKD.LOCAL",
                "path-local-v1",
                VersionedAssetType.RULE,
                "RULE.CKD")));
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.empty());

        ClinicalRuntimeRelease result = service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            null,
            List.of(
                ClinicalRuntimeAssetSelection.platform(
                    VersionedAssetType.KNOWLEDGE, "KNOW.CKD"),
                ClinicalRuntimeAssetSelection.local(
                    VersionedAssetType.PATHWAY,
                    "PATH.CKD.LOCAL",
                    "path-local-v1")
            ),
            "operator-A",
            "trace-A"
        ));

        assertThat(result.revisionNo()).isEqualTo(1L);
        assertThat(result.platformBaselineReleaseId()).isEqualTo("baseline-A8");
        assertThat(result.manifestSha256()).matches("[0-9a-f]{64}");
        ArgumentCaptor<ClinicalRuntimeReleaseItem> saved =
            ArgumentCaptor.forClass(ClinicalRuntimeReleaseItem.class);
        verify(runtimeItems, org.mockito.Mockito.times(4)).save(saved.capture());
        assertThat(saved.getAllValues())
            .extracting(
                ClinicalRuntimeReleaseItem::assetType,
                ClinicalRuntimeReleaseItem::assetIdentity,
                ClinicalRuntimeReleaseItem::entryState,
                ClinicalRuntimeReleaseItem::sourceLayer)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.FIELD_CATALOG, "FIELD.CANONICAL",
                    ReleaseEntryState.DISABLED, ReleaseSourceLayer.PLATFORM),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.KNOWLEDGE, "KNOW.CKD",
                    ReleaseEntryState.ACTIVE, ReleaseSourceLayer.PLATFORM),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.PATHWAY, "PATH.CKD.LOCAL",
                    ReleaseEntryState.ACTIVE, ReleaseSourceLayer.HOSPITAL),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.RULE, "RULE.CKD",
                    ReleaseEntryState.ACTIVE, ReleaseSourceLayer.PLATFORM)
            );
    }

    @Test
    void activatesMultipleAssetsOfTheSameTypeTogetherWithOtherAssetTypes() {
        stubHospitalAndBaseline();
        List<PlatformBaselineItem> baseline = List.of(
            baselineItem(VersionedAssetType.FIELD_CATALOG, "FIELD.CANONICAL",
                "field-v1", "V1", "1".repeat(64)),
            baselineItem(VersionedAssetType.KNOWLEDGE, "KNOW.CKD",
                "know-v2", "V2", "2".repeat(64)),
            baselineItem(VersionedAssetType.PATHWAY, "PATH.CKD",
                "path-v1", "V1", "3".repeat(64)),
            baselineItem(VersionedAssetType.RULE, "RULE.CKD.DOSE",
                "rule-dose-v3", "V3", "4".repeat(64)),
            baselineItem(VersionedAssetType.RULE, "RULE.CKD.ALERT",
                "rule-alert-v2", "V2", "5".repeat(64))
        );
        baseline.forEach(this::stubPublishedPlatformVersion);
        when(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "baseline-A8"))
            .thenReturn(baseline);

        service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            null,
            List.of(
                ClinicalRuntimeAssetSelection.platform(
                    VersionedAssetType.KNOWLEDGE, "KNOW.CKD"),
                ClinicalRuntimeAssetSelection.platform(
                    VersionedAssetType.PATHWAY, "PATH.CKD"),
                ClinicalRuntimeAssetSelection.platform(
                    VersionedAssetType.RULE, "RULE.CKD.DOSE"),
                ClinicalRuntimeAssetSelection.platform(
                    VersionedAssetType.RULE, "RULE.CKD.ALERT")
            ),
            "operator-A",
            "trace-A"
        ));

        ArgumentCaptor<ClinicalRuntimeReleaseItem> saved =
            ArgumentCaptor.forClass(ClinicalRuntimeReleaseItem.class);
        verify(runtimeItems, org.mockito.Mockito.times(5)).save(saved.capture());
        assertThat(saved.getAllValues())
            .filteredOn(item -> item.entryState() == ReleaseEntryState.ACTIVE)
            .extracting(
                ClinicalRuntimeReleaseItem::assetType,
                ClinicalRuntimeReleaseItem::assetIdentity)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.KNOWLEDGE, "KNOW.CKD"),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.PATHWAY, "PATH.CKD"),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.RULE, "RULE.CKD.ALERT"),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.RULE, "RULE.CKD.DOSE")
            );
    }

    @Test
    void validatesAndPublishesALocalDraftInsideTheSameAtomicRuntimeActivation() {
        stubHospitalAndBaseline();
        AssetVersion draft = version(
            "rule-local-v1",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.LOCAL",
            "V1",
            "5".repeat(64),
            "/tenant-A/group-A/hospital-A")
            .withStatus(
                AssetVersionStatus.DRAFT,
                "version:rule-local-v1",
                NOW.minusSeconds(60),
                "operator-A");
        when(versions.findByVersionIdAndTenantId("rule-local-v1", "tenant-A"))
            .thenReturn(Optional.of(draft));
        when(versions.save(any(AssetVersion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.RULE, "RULE.LOCAL"))
            .thenReturn(Optional.of(new AssetIdentity(
                1L, "tenant-A", VersionedAssetType.RULE, "RULE.LOCAL",
                AssetIdentityStatus.ACTIVE, 1L,
                NOW.minusSeconds(3600), "operator-old",
                NOW.minusSeconds(60), "operator-old", "trace-old")));
        when(assetScopes.resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A/hospital-A"))
            .thenReturn(new AssetOwnershipScope(
                ReleaseSourceLayer.HOSPITAL,
                "/tenant-A/group-A/hospital-A"));
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.empty());

        ClinicalRuntimeRelease result = service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            null,
            List.of(ClinicalRuntimeAssetSelection.local(
                VersionedAssetType.RULE,
                "RULE.LOCAL",
                "rule-local-v1")),
            "operator-A",
            "trace-A"
        ));

        assertThat(result.revisionNo()).isEqualTo(1L);
        verify(validation).validateForPublish(draft, "operator-A", "trace-A");
        verify(versions).save(org.mockito.ArgumentMatchers.argThat(
            value -> value.versionId().equals("rule-local-v1")
                && value.status() == AssetVersionStatus.PUBLISHED
                && value.effectiveFrom().equals(NOW)));
        verify(runtimeItems).save(org.mockito.ArgumentMatchers.argThat(
            value -> value.assetIdentity().equals("RULE.LOCAL")
                && value.entryState() == ReleaseEntryState.ACTIVE
                && value.sourceLayer() == ReleaseSourceLayer.HOSPITAL));
    }

    @Test
    void publishesLocalPathwayDraftAndNotifiesProjectionSynchronizers() {
        stubHospitalAndBaseline();
        AssetVersion draft = version(
            "pathway-local-v1",
            "tenant-A",
            VersionedAssetType.PATHWAY,
            "PATH.CLINICAL.CYCLE",
            "V1",
            "6".repeat(64),
            "/tenant-A/group-A/hospital-A")
            .withStatus(
                AssetVersionStatus.DRAFT,
                "version:pathway-local-v1",
                NOW.minusSeconds(60),
                "operator-A");
        when(versions.findByVersionIdAndTenantId("pathway-local-v1", "tenant-A"))
            .thenReturn(Optional.of(draft));
        when(versions.save(any(AssetVersion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.PATHWAY, "PATH.CLINICAL.CYCLE"))
            .thenReturn(Optional.of(new AssetIdentity(
                1L, "tenant-A", VersionedAssetType.PATHWAY, "PATH.CLINICAL.CYCLE",
                AssetIdentityStatus.ACTIVE, 1L,
                NOW.minusSeconds(3600), "operator-old",
                NOW.minusSeconds(60), "operator-old", "trace-old")));
        when(assetScopes.resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A/hospital-A"))
            .thenReturn(new AssetOwnershipScope(
                ReleaseSourceLayer.HOSPITAL,
                "/tenant-A/group-A/hospital-A"));
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.empty());

        service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            null,
            List.of(ClinicalRuntimeAssetSelection.local(
                VersionedAssetType.PATHWAY,
                "PATH.CLINICAL.CYCLE",
                "pathway-local-v1")),
            "operator-A",
            "trace-A"
        ));

        verify(validation).validateForPublish(draft, "operator-A", "trace-A");
        verify(publicationSynchronizer).afterPublished(
            org.mockito.ArgumentMatchers.argThat(value ->
                value.versionId().equals("pathway-local-v1")
                    && value.assetType() == VersionedAssetType.PATHWAY
                    && value.status() == AssetVersionStatus.PUBLISHED),
            org.mockito.ArgumentMatchers.eq(NOW),
            org.mockito.ArgumentMatchers.eq("operator-A"),
            org.mockito.ArgumentMatchers.eq("trace-A"));
    }

    @Test
    void rejectsLostUpdateWhenExpectedCurrentRevisionIsStale() {
        stubHospitalAndBaseline();
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-H9", 9L)));

        assertThatThrownBy(() -> service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            "runtime-H8",
            List.of(),
            "operator-A",
            "trace-A"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已变化")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void rejectsWithdrawnLocalVersionEvenWhenActiveInExpectedCurrentRelease() {
        stubHospitalAndBaseline();
        AssetVersion withdrawn = version(
            "risk-v2",
            "tenant-A",
            VersionedAssetType.CDSS_RISK,
            "CDSS.RISK.MATRIX",
            "V2",
            "7".repeat(64),
            "/tenant-A/group-A/hospital-A")
            .withStatus(
                AssetVersionStatus.WITHDRAWN,
                null,
                NOW.minusSeconds(30),
                "operator-old");
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-H9", 9L)));
        when(runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "runtime-H9"))
            .thenReturn(List.of(runtimeItem(
                "runtime-H9",
                ReleaseSourceLayer.HOSPITAL,
                VersionedAssetType.CDSS_RISK,
                "CDSS.RISK.MATRIX",
                ReleaseEntryState.ACTIVE,
                "risk-v2",
                "V2",
                "7".repeat(64))));
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.CDSS_RISK, "CDSS.RISK.MATRIX"))
            .thenReturn(Optional.of(new AssetIdentity(
                1L, "tenant-A", VersionedAssetType.CDSS_RISK, "CDSS.RISK.MATRIX",
                AssetIdentityStatus.ACTIVE, 2L,
                NOW.minusSeconds(3600), "operator-old",
                NOW.minusSeconds(60), "operator-old", "trace-old")));
        when(versions.findByVersionIdAndTenantId("risk-v2", "tenant-A"))
            .thenReturn(Optional.of(withdrawn));
        assertThatThrownBy(() -> service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            "runtime-H9",
            List.of(ClinicalRuntimeAssetSelection.local(
                VersionedAssetType.CDSS_RISK,
                "CDSS.RISK.MATRIX",
                "risk-v2")),
            "operator-A",
            "trace-reject-active-withdrawn"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("只能启用草稿或已发布的本地资产版本")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(releases, org.mockito.Mockito.never()).save(any(ClinicalRuntimeRelease.class));
        verify(versions, org.mockito.Mockito.never()).save(any(AssetVersion.class));
        verify(runtimeItems, org.mockito.Mockito.never())
            .save(any(ClinicalRuntimeReleaseItem.class));
    }

    @Test
    void rejectsWithdrawnLocalVersionThatIsNotActiveInExpectedCurrentRelease() {
        stubHospitalAndBaseline();
        AssetVersion withdrawn = version(
            "risk-v2",
            "tenant-A",
            VersionedAssetType.CDSS_RISK,
            "CDSS.RISK.MATRIX",
            "V2",
            "7".repeat(64),
            "/tenant-A/group-A/hospital-A")
            .withStatus(
                AssetVersionStatus.WITHDRAWN,
                null,
                NOW.minusSeconds(30),
                "operator-old");
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-H9", 9L)));
        when(runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "runtime-H9"))
            .thenReturn(List.of(runtimeItem(
                "runtime-H9",
                ReleaseSourceLayer.HOSPITAL,
                VersionedAssetType.CDSS_RISK,
                "CDSS.RISK.MATRIX",
                ReleaseEntryState.ACTIVE,
                "risk-v1",
                "V1",
                "6".repeat(64))));
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.CDSS_RISK, "CDSS.RISK.MATRIX"))
            .thenReturn(Optional.of(new AssetIdentity(
                1L, "tenant-A", VersionedAssetType.CDSS_RISK, "CDSS.RISK.MATRIX",
                AssetIdentityStatus.ACTIVE, 2L,
                NOW.minusSeconds(3600), "operator-old",
                NOW.minusSeconds(60), "operator-old", "trace-old")));
        when(versions.findByVersionIdAndTenantId("risk-v2", "tenant-A"))
            .thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            "runtime-H9",
            List.of(ClinicalRuntimeAssetSelection.local(
                VersionedAssetType.CDSS_RISK,
                "CDSS.RISK.MATRIX",
                "risk-v2")),
            "operator-A",
            "trace-reject-withdrawn"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("只能启用草稿或已发布的本地资产版本")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(releases, org.mockito.Mockito.never()).save(any(ClinicalRuntimeRelease.class));
        verify(versions, org.mockito.Mockito.never()).save(any(AssetVersion.class));
    }

    @Test
    void requiredDependencyPrefersHospitalThenGroupThenPlatform() {
        stubHospitalAndBaseline();
        AssetIdentity pathwayIdentity = new AssetIdentity(
            1L, "tenant-A", VersionedAssetType.PATHWAY, "PATH.CKD.LOCAL",
            AssetIdentityStatus.ACTIVE, 1L,
            NOW.minusSeconds(3600), "operator-old",
            NOW.minusSeconds(60), "operator-old", "trace-old");
        AssetIdentity ruleIdentity = new AssetIdentity(
            2L, "tenant-A", VersionedAssetType.RULE, "RULE.CKD",
            AssetIdentityStatus.ACTIVE, 5L,
            NOW.minusSeconds(3600), "operator-old",
            NOW.minusSeconds(60), "operator-old", "trace-old");
        when(identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc("tenant-A"))
            .thenReturn(List.of(pathwayIdentity, ruleIdentity));
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.PATHWAY, "PATH.CKD.LOCAL"))
            .thenReturn(Optional.of(pathwayIdentity));
        AssetVersion pathway = version(
            "path-local-v1", "tenant-A", VersionedAssetType.PATHWAY,
            "PATH.CKD.LOCAL", "V1", "4".repeat(64),
            "/tenant-A/group-A/hospital-A");
        AssetVersion groupRule = version(
            "rule-group-v5", "tenant-A", VersionedAssetType.RULE,
            "RULE.CKD", "V5", "5".repeat(64),
            "/tenant-A/group-A");
        AssetVersion hospitalRule = version(
            "rule-hospital-v4", "tenant-A", VersionedAssetType.RULE,
            "RULE.CKD", "V4", "6".repeat(64),
            "/tenant-A/group-A/hospital-A");
        when(versions.findByVersionIdAndTenantId("path-local-v1", "tenant-A"))
            .thenReturn(Optional.of(pathway));
        when(versions.findByVersionIdAndTenantId("rule-hospital-v4", "tenant-A"))
            .thenReturn(Optional.of(hospitalRule));
        when(versions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.PATHWAY, "PATH.CKD.LOCAL",
            AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(pathway));
        when(versions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.RULE, "RULE.CKD",
            AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(groupRule, hospitalRule));
        when(assetScopes.resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A/hospital-A"))
            .thenReturn(new AssetOwnershipScope(
                ReleaseSourceLayer.HOSPITAL,
                "/tenant-A/group-A/hospital-A"));
        when(assetScopes.resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A"))
            .thenReturn(new AssetOwnershipScope(
                ReleaseSourceLayer.GROUP,
                "/tenant-A/group-A"));
        when(dependencies
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                "tenant-A", VersionedAssetType.PATHWAY,
                "PATH.CKD.LOCAL", "path-local-v1"))
            .thenReturn(List.of(dependency(
                "tenant-A",
                VersionedAssetType.PATHWAY,
                "PATH.CKD.LOCAL",
                "path-local-v1",
                VersionedAssetType.RULE,
                "RULE.CKD")));
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.empty());

        service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            null,
            List.of(ClinicalRuntimeAssetSelection.local(
                VersionedAssetType.PATHWAY,
                "PATH.CKD.LOCAL",
                "path-local-v1")),
            "operator-A",
            "trace-A"
        ));

        ArgumentCaptor<ClinicalRuntimeReleaseItem> saved =
            ArgumentCaptor.forClass(ClinicalRuntimeReleaseItem.class);
        verify(runtimeItems, org.mockito.Mockito.times(4)).save(saved.capture());
        assertThat(saved.getAllValues())
            .filteredOn(item -> item.assetType() == VersionedAssetType.RULE
                && item.assetIdentity().equals("RULE.CKD"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.entryState()).isEqualTo(ReleaseEntryState.ACTIVE);
                assertThat(item.sourceLayer()).isEqualTo(ReleaseSourceLayer.HOSPITAL);
                assertThat(item.versionId()).isEqualTo("rule-hospital-v4");
            });
    }

    @Test
    void regularActivationCannotDowngradeThePlatformBaseline() {
        stubHospital();
        PlatformBaselineRelease older = new PlatformBaselineRelease(
            7L, "baseline-A7", 7L, "7".repeat(64),
            NOW.minusSeconds(7200), "platform-operator",
            NOW.minusSeconds(7200), "platform-operator", "trace-platform");
        PlatformBaselineRelease currentBaseline = new PlatformBaselineRelease(
            8L, "baseline-A8", 8L, "8".repeat(64),
            NOW.minusSeconds(3600), "platform-operator",
            NOW.minusSeconds(3600), "platform-operator", "trace-platform");
        when(baselines.findByBaselineReleaseId("baseline-A7"))
            .thenReturn(Optional.of(older));
        when(baselines.findByBaselineReleaseId("baseline-A8"))
            .thenReturn(Optional.of(currentBaseline));
        when(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "baseline-A7")).thenReturn(List.of());
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-H9", 9L)));

        assertThatThrownBy(() -> service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A7",
            "runtime-H9",
            List.of(),
            "operator-A",
            "trace-A"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("旧平台标准版本")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void rollbackCopiesExactHistoricalItemsIntoANewHigherRevision() {
        stubHospital();
        ClinicalRuntimeRelease target = release("runtime-H3", 3L);
        when(releases.findByTenantIdAndReleaseId("tenant-A", "runtime-H3"))
            .thenReturn(Optional.of(target));
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-H9", 9L)));
        when(runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc("runtime-H3"))
            .thenReturn(List.of(new ClinicalRuntimeReleaseItem(
                1L, "runtime-H3", PlatformTenant.ID, ReleaseSourceLayer.PLATFORM,
                VersionedAssetType.RULE, "RULE.CKD", ReleaseEntryState.ACTIVE,
                "rule-v1", "V1", "3".repeat(64),
                NOW.minusSeconds(3600), "operator-old", "trace-old")));
        when(versions.findByVersionIdAndTenantId("rule-v1", PlatformTenant.ID))
            .thenReturn(Optional.of(version(
                "rule-v1",
                PlatformTenant.ID,
                VersionedAssetType.RULE,
                "RULE.CKD",
                "V1",
                "3".repeat(64),
                "/t-1")));

        ClinicalRuntimeRelease rolledBack = service.rollback(
            "tenant-A",
            "hospital-A",
            "runtime-H3",
            "operator-A",
            "trace-rollback"
        );

        assertThat(rolledBack.revisionNo()).isEqualTo(10L);
        assertThat(rolledBack.rollbackFromReleaseId()).isEqualTo("runtime-H3");
        assertThat(rolledBack.platformBaselineReleaseId()).isEqualTo("baseline-A8");
        ArgumentCaptor<ClinicalRuntimeReleaseItem> copied =
            ArgumentCaptor.forClass(ClinicalRuntimeReleaseItem.class);
        verify(runtimeItems).save(copied.capture());
        assertThat(copied.getValue().releaseId()).isEqualTo(rolledBack.releaseId());
        assertThat(copied.getValue().versionId()).isEqualTo("rule-v1");
    }

    @Test
    void rollbackRejectsHistoricalReleaseContainingWithdrawnActiveAsset() {
        stubHospital();
        when(releases.findByTenantIdAndReleaseId("tenant-A", "runtime-H3"))
            .thenReturn(Optional.of(release("runtime-H3", 3L)));
        when(runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc("runtime-H3"))
            .thenReturn(List.of(runtimeItem(
                "runtime-H3",
                ReleaseSourceLayer.HOSPITAL,
                VersionedAssetType.CDSS_RISK,
                "CDSS.RISK.MATRIX",
                ReleaseEntryState.ACTIVE,
                "risk-v2",
                "V2",
                "7".repeat(64))));
        when(versions.findByVersionIdAndTenantId("risk-v2", "tenant-A"))
            .thenReturn(Optional.of(version(
                "risk-v2",
                "tenant-A",
                VersionedAssetType.CDSS_RISK,
                "CDSS.RISK.MATRIX",
                "V2",
                "7".repeat(64),
                "/tenant-A/group-A/hospital-A")
                .withStatus(
                    AssetVersionStatus.WITHDRAWN,
                    null,
                    NOW.minusSeconds(30),
                    "operator-old")));

        assertThatThrownBy(() -> service.rollback(
            "tenant-A",
            "hospital-A",
            "runtime-H3",
            "operator-A",
            "trace-reject-withdrawn-rollback"
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已撤回")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(releases, org.mockito.Mockito.never()).save(any(ClinicalRuntimeRelease.class));
        verify(runtimeItems, org.mockito.Mockito.never())
            .save(any(ClinicalRuntimeReleaseItem.class));
        verify(versions).lockByVersionIdAndTenantId("risk-v2", "tenant-A");
    }

    @Test
    void rejectsWithdrawnPlatformVersionFromNewRuntimeRelease() {
        stubHospitalAndBaseline();
        when(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "baseline-A8"))
            .thenReturn(List.of(baselineItem(
                VersionedAssetType.RULE,
                "RULE.CKD",
                "rule-v3",
                "V3",
                "3".repeat(64))));
        when(versions.findByVersionIdAndTenantId("rule-v3", PlatformTenant.ID))
            .thenReturn(Optional.of(version(
                "rule-v3",
                PlatformTenant.ID,
                VersionedAssetType.RULE,
                "RULE.CKD",
                "V3",
                "3".repeat(64),
                "/")
                .withStatus(
                    AssetVersionStatus.WITHDRAWN,
                    "version:rule-v3",
                    NOW.minusSeconds(30),
                    "platform-operator")));

        assertThatThrownBy(() -> service.activate(new ClinicalRuntimeReleaseCommand(
            "tenant-A",
            "hospital-A",
            "baseline-A8",
            null,
            List.of(ClinicalRuntimeAssetSelection.platform(
                VersionedAssetType.RULE,
                "RULE.CKD")),
            "operator-A",
            "trace-reject-withdrawn-platform"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已撤回")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(releases, org.mockito.Mockito.never()).save(any(ClinicalRuntimeRelease.class));
        verify(runtimeItems, org.mockito.Mockito.never())
            .save(any(ClinicalRuntimeReleaseItem.class));
        verify(versions).lockByVersionIdAndTenantId("rule-v3", PlatformTenant.ID);
    }

    @Test
    void restoreOfflineSnapshotCopiesValidatedItemsIntoANewHigherRevision() {
        stubHospital();
        when(releases.findByTenantIdAndReleaseId("tenant-A", "runtime-H3"))
            .thenReturn(Optional.of(release("runtime-H3", 3L)));
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-current", 9L)));
        when(versions.findByVersionIdAndTenantId("rule-v1", PlatformTenant.ID))
            .thenReturn(Optional.of(version(
                "rule-v1",
                PlatformTenant.ID,
                VersionedAssetType.RULE,
                "RULE.CKD",
                "V1",
                "3".repeat(64),
                "/t-1")));

        ClinicalRuntimeRelease restored = service.restoreOfflineSnapshot(
            new ClinicalRuntimeReleaseOfflineRestoreCommand(
                "tenant-A",
                "hospital-A",
                "runtime-current",
                "runtime-H3",
                "baseline-A8",
                "b".repeat(64),
                List.of(new ClinicalRuntimeReleaseItemOfflineSnapshot(
                    PlatformTenant.ID,
                    ReleaseSourceLayer.PLATFORM,
                    VersionedAssetType.RULE,
                    "RULE.CKD",
                    ReleaseEntryState.ACTIVE,
                    "rule-v1",
                    "V1",
                    "3".repeat(64)
                )),
                "operator-A",
                "trace-restore"
            ));

        assertThat(restored.releaseId()).startsWith("runtime-");
        assertThat(restored.releaseId()).isNotEqualTo("runtime-H3");
        assertThat(restored.revisionNo()).isEqualTo(10L);
        assertThat(restored.rollbackFromReleaseId()).isEqualTo("runtime-H3");
        assertThat(restored.manifestSha256()).isEqualTo("b".repeat(64));
        ArgumentCaptor<ClinicalRuntimeReleaseItem> copied =
            ArgumentCaptor.forClass(ClinicalRuntimeReleaseItem.class);
        verify(runtimeItems).save(copied.capture());
        assertThat(copied.getValue().releaseId()).isEqualTo(restored.releaseId());
        assertThat(copied.getValue().assetIdentity()).isEqualTo("RULE.CKD");
        assertThat(copied.getValue().traceId()).isEqualTo("trace-restore");
    }

    @Test
    void restoreOfflineSnapshotRejectsWithdrawnActiveAsset() {
        stubHospital();
        when(releases.findByTenantIdAndReleaseId("tenant-A", "runtime-H3"))
            .thenReturn(Optional.of(release("runtime-H3", 3L)));
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-current", 9L)));
        when(versions.findByVersionIdAndTenantId("risk-v2", "tenant-A"))
            .thenReturn(Optional.of(version(
                "risk-v2",
                "tenant-A",
                VersionedAssetType.CDSS_RISK,
                "CDSS.RISK.MATRIX",
                "V2",
                "7".repeat(64),
                "/tenant-A/group-A/hospital-A")
                .withStatus(
                    AssetVersionStatus.WITHDRAWN,
                    null,
                    NOW.minusSeconds(30),
                    "operator-old")));

        assertThatThrownBy(() -> service.restoreOfflineSnapshot(
            new ClinicalRuntimeReleaseOfflineRestoreCommand(
                "tenant-A",
                "hospital-A",
                "runtime-current",
                "runtime-H3",
                "baseline-A8",
                "b".repeat(64),
                List.of(new ClinicalRuntimeReleaseItemOfflineSnapshot(
                    "tenant-A",
                    ReleaseSourceLayer.HOSPITAL,
                    VersionedAssetType.CDSS_RISK,
                    "CDSS.RISK.MATRIX",
                    ReleaseEntryState.ACTIVE,
                    "risk-v2",
                    "V2",
                    "7".repeat(64)
                )),
                "operator-A",
                "trace-reject-withdrawn-restore"
            )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已撤回")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(releases, org.mockito.Mockito.never()).save(any(ClinicalRuntimeRelease.class));
        verify(runtimeItems, org.mockito.Mockito.never())
            .save(any(ClinicalRuntimeReleaseItem.class));
    }

    @Test
    void restoreOfflineSnapshotRejectsMissingSourceRuntimeLedger() {
        stubHospital();
        when(releases.findByTenantIdAndReleaseId("tenant-A", "runtime-H3"))
            .thenReturn(Optional.empty());
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-current", 9L)));

        assertThatThrownBy(() -> service.restoreOfflineSnapshot(restoreCommand(
            "runtime-current",
            "runtime-H3",
            "hospital-A",
            "baseline-A8",
            "b".repeat(64)
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("来源机构生效版本不存在")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void restoreOfflineSnapshotRejectsSourceRuntimeLedgerMismatch() {
        stubHospital();
        when(releases.findByTenantIdAndReleaseId("tenant-A", "runtime-H3"))
            .thenReturn(Optional.of(release("runtime-H3", 3L, "other-hospital", "b".repeat(64))));
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-current", 9L)));

        assertThatThrownBy(() -> service.restoreOfflineSnapshot(restoreCommand(
            "runtime-current",
            "runtime-H3",
            "hospital-A",
            "baseline-A8",
            "b".repeat(64)
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("来源机构生效版本不一致")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void restoreOfflineSnapshotRejectsStaleExpectedCurrentRelease() {
        stubHospital();
        when(releases.findByTenantIdAndReleaseId("tenant-A", "runtime-H3"))
            .thenReturn(Optional.of(release("runtime-H3", 3L)));
        when(releases.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A"))
            .thenReturn(Optional.of(release("runtime-current", 9L)));

        assertThatThrownBy(() -> service.restoreOfflineSnapshot(
            new ClinicalRuntimeReleaseOfflineRestoreCommand(
                "tenant-A",
                "hospital-A",
                "runtime-old",
                "runtime-H3",
                "baseline-A8",
                "b".repeat(64),
                List.of(new ClinicalRuntimeReleaseItemOfflineSnapshot(
                    PlatformTenant.ID,
                    ReleaseSourceLayer.PLATFORM,
                    VersionedAssetType.RULE,
                    "RULE.CKD",
                    ReleaseEntryState.ACTIVE,
                    "rule-v1",
                    "V1",
                    "3".repeat(64)
                )),
                "operator-A",
                "trace-restore"
            )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("当前机构生效版本已变化")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    private ClinicalRuntimeReleaseOfflineRestoreCommand restoreCommand(
            String expectedCurrentReleaseId,
            String sourceReleaseId,
            String hospitalId,
            String platformBaselineReleaseId,
            String manifestSha256) {
        return new ClinicalRuntimeReleaseOfflineRestoreCommand(
            "tenant-A",
            hospitalId,
            expectedCurrentReleaseId,
            sourceReleaseId,
            platformBaselineReleaseId,
            manifestSha256,
            List.of(new ClinicalRuntimeReleaseItemOfflineSnapshot(
                PlatformTenant.ID,
                ReleaseSourceLayer.PLATFORM,
                VersionedAssetType.RULE,
                "RULE.CKD",
                ReleaseEntryState.ACTIVE,
                "rule-v1",
                "V1",
                "3".repeat(64)
            )),
            "operator-A",
            "trace-restore"
        );
    }

    private void stubHospitalAndBaseline() {
        stubHospital();
        when(baselines.findByBaselineReleaseId("baseline-A8"))
            .thenReturn(Optional.of(new PlatformBaselineRelease(
                8L, "baseline-A8", 8L, "a".repeat(64),
                NOW.minusSeconds(3600), "platform-operator",
                NOW.minusSeconds(3600), "platform-operator", "trace-platform")));
        List<PlatformBaselineItem> baseline = List.of(
            baselineItem(VersionedAssetType.FIELD_CATALOG, "FIELD.CANONICAL",
                "field-v1", "V1", "1".repeat(64)),
            baselineItem(VersionedAssetType.KNOWLEDGE, "KNOW.CKD",
                "know-v2", "V2", "2".repeat(64)),
            baselineItem(VersionedAssetType.RULE, "RULE.CKD",
                "rule-v3", "V3", "3".repeat(64))
        );
        baseline.forEach(this::stubPublishedPlatformVersion);
        when(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "baseline-A8"))
            .thenReturn(baseline);
    }

    private void stubPublishedPlatformVersion(PlatformBaselineItem item) {
        when(versions.findByVersionIdAndTenantId(item.versionId(), PlatformTenant.ID))
            .thenReturn(Optional.of(version(
                item.versionId(),
                PlatformTenant.ID,
                item.assetType(),
                item.assetIdentity(),
                item.versionNo(),
                item.contentHash(),
                "/")));
    }

    private void stubHospital() {
        when(organizations.findByTenantIdAndId("tenant-A", "hospital-A"))
            .thenReturn(Optional.of(new OrgUnit(
                "hospital-A",
                "group-A",
                "tenant-A",
                "/tenant-A/group-A/hospital-A",
                OrgLevel.FACILITY,
                "H001",
                "测试医院",
                null,
                OrgFacilityType.HOSPITAL,
                null,
                OrgUnitStatus.ACTIVE,
                NOW.minusSeconds(3600),
                "operator-old",
                NOW.minusSeconds(3600),
                "operator-old"
            )));
    }

    private PlatformBaselineItem baselineItem(
            VersionedAssetType type,
            String identity,
            String versionId,
            String versionNo,
            String hash) {
        return new PlatformBaselineItem(
            null, "baseline-A8", PlatformTenant.ID, type, identity,
            ReleaseEntryState.ACTIVE, versionId, versionNo, hash,
            NOW.minusSeconds(3600), "platform-operator", "trace-platform");
    }

    private AssetVersion version(
            String versionId,
            String tenantId,
            VersionedAssetType type,
            String identity,
            String versionNo,
            String hash,
            String orgPath) {
        return new AssetVersion(
            1L, versionId, tenantId, type, identity, versionNo,
            orgPath, "ALL", hash,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED, "version:" + versionId, null,
            NOW.minusSeconds(60), null,
            NOW.minusSeconds(3600), "operator-old",
            NOW.minusSeconds(60), "operator-old", "trace-old");
    }

    private ClinicalRuntimeReleaseItem runtimeItem(
            String releaseId,
            ReleaseSourceLayer sourceLayer,
            VersionedAssetType assetType,
            String assetIdentity,
            ReleaseEntryState entryState,
            String versionId,
            String versionNo,
            String contentHash) {
        return new ClinicalRuntimeReleaseItem(
            1L,
            releaseId,
            "tenant-A",
            sourceLayer,
            assetType,
            assetIdentity,
            entryState,
            versionId,
            versionNo,
            contentHash,
            NOW.minusSeconds(3600),
            "operator-old",
            "trace-old"
        );
    }

    private AssetDependency dependency(
            String tenantId,
            VersionedAssetType ownerType,
            String ownerIdentity,
            String ownerVersionId,
            VersionedAssetType targetType,
            String targetIdentity) {
        return new AssetDependency(
            1L, "dep-1", tenantId,
            ownerType, ownerIdentity, ownerVersionId,
            targetType, targetIdentity,
            null, null, AssetDependencyKind.RULE,
            NOW.minusSeconds(60), "operator-old",
            NOW.minusSeconds(60), "operator-old", "trace-old");
    }

    private ClinicalRuntimeRelease release(String releaseId, long revision) {
        return release(releaseId, revision, "hospital-A", "b".repeat(64));
    }

    private ClinicalRuntimeRelease release(
            String releaseId,
            long revision,
            String hospitalId,
            String manifestSha256) {
        return new ClinicalRuntimeRelease(
            1L,
            releaseId,
            "tenant-A",
            hospitalId,
            revision,
            "baseline-A8",
            manifestSha256,
            null,
            NOW.minusSeconds(3600),
            "operator-old",
            NOW.minusSeconds(3600),
            "operator-old",
            "trace-old"
        );
    }
}
