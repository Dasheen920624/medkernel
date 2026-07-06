package com.medkernel.engine.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItemRepository;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.versioning.InheritanceOverrideRepository;
import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideMode;
import com.medkernel.engine.versioning.InheritanceOverrideStatus;
import com.medkernel.engine.versioning.InheritancePropagation;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.PlatformTenant;

class RuntimeReleaseQueryServiceTest {

    private final PlatformBaselineReleaseRepository baselines =
        mock(PlatformBaselineReleaseRepository.class);
    private final PlatformBaselineItemRepository baselineItems =
        mock(PlatformBaselineItemRepository.class);
    private final ClinicalRuntimeReleaseRepository runtimes =
        mock(ClinicalRuntimeReleaseRepository.class);
    private final ClinicalRuntimeReleaseItemRepository runtimeItems =
        mock(ClinicalRuntimeReleaseItemRepository.class);
    private final OrgUnitRepository organizations = mock(OrgUnitRepository.class);
    private final InheritanceOverrideRepository overrides =
        mock(InheritanceOverrideRepository.class);
    private final RuntimeReleaseQueryService service =
        new RuntimeReleaseQueryService(
            baselines, baselineItems, runtimes, runtimeItems, organizations, overrides);

    @Test
    void returnsTheCurrentPlatformBaselineWithItsExactMaterializedItems() {
        PlatformBaselineRelease baseline = baseline();
        PlatformBaselineItem item = new PlatformBaselineItem(
            1L, "baseline-A8", PlatformTenant.ID,
            VersionedAssetType.RULE, "RULE.CKD", ReleaseEntryState.ACTIVE,
            "rule-v3", "V3", "3".repeat(64),
            Instant.EPOCH, "operator-platform", "trace-platform");
        when(baselines.findFirstByOrderByRevisionNoDesc())
            .thenReturn(Optional.of(baseline));
        when(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "baseline-A8")).thenReturn(List.of(item));

        PlatformBaselineDetailResponse result = service.currentPlatformBaseline().orElseThrow();

        assertThat(result.release()).isEqualTo(baseline);
        assertThat(result.items()).containsExactly(item);
    }

    @Test
    void reportsAnHonestEmptyStateWhenPlatformHasNoBaselineRevision() {
        when(baselines.findFirstByOrderByRevisionNoDesc()).thenReturn(Optional.empty());

        assertThat(service.currentPlatformBaseline()).isEmpty();
    }

    @Test
    void returnsTheCurrentHospitalRuntimeWithItsExactMaterializedItems() {
        ClinicalRuntimeRelease runtime = runtime();
        ClinicalRuntimeReleaseItem item = new ClinicalRuntimeReleaseItem(
            1L, "runtime-H9", PlatformTenant.ID, ReleaseSourceLayer.PLATFORM,
            VersionedAssetType.RULE, "RULE.CKD", ReleaseEntryState.ACTIVE,
            "rule-v3", "V3", "3".repeat(64),
            Instant.EPOCH, "operator-hospital", "trace-hospital");
        when(runtimes.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.of(runtime));
        when(runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "runtime-H9")).thenReturn(List.of(item));

        ClinicalRuntimeReleaseDetailResponse result =
            service.currentHospitalRuntime("tenant-A", "hospital-A").orElseThrow();

        assertThat(result.release()).isEqualTo(runtime);
        assertThat(result.items()).containsExactly(item);
    }

    @Test
    void reportsAnHonestEmptyStateWhenHospitalHasNoRuntimeRevision() {
        when(runtimes.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.empty());

        assertThat(service.currentHospitalRuntime("tenant-A", "hospital-A")).isEmpty();
    }

    @Test
    void returnsHospitalRuntimeHistoryInDescendingRevisionOrder() {
        ClinicalRuntimeRelease latest = runtime();
        ClinicalRuntimeRelease previous = new ClinicalRuntimeRelease(
            8L, "runtime-H8", "tenant-A", "hospital-A", 8L,
            "baseline-A7", "c".repeat(64), null,
            Instant.EPOCH, "operator-hospital",
            Instant.EPOCH, "operator-hospital", "trace-hospital");
        when(runtimes.pageByTenantIdAndHospitalId(
            "tenant-A", "hospital-A", 0, 20))
            .thenReturn(List.of(latest, previous));
        when(runtimes.countByTenantIdAndHospitalId("tenant-A", "hospital-A"))
            .thenReturn(2L);

        var result = service.hospitalRuntimeHistory(
            "tenant-A", "hospital-A", new PageRequest(1, 20, null));

        assertThat(result.items())
            .extracting(ClinicalRuntimeRelease::releaseId)
            .containsExactly("runtime-H9", "runtime-H8");
        assertThat(result.total()).isEqualTo(2L);
    }

    @Test
    void analyzesPlatformUpgradeAgainstCurrentHospitalRuntimeWithoutMutatingRuntime() {
        PlatformBaselineRelease target = baseline();
        ClinicalRuntimeRelease current = runtime();
        when(baselines.findByBaselineReleaseId("baseline-A8")).thenReturn(Optional.of(target));
        when(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "baseline-A8")).thenReturn(List.of(
                platformItem(VersionedAssetType.ACTION_CARD, "CARD.NEW", ReleaseEntryState.ACTIVE,
                    "card-new-v1", "V1", "1".repeat(64)),
                platformItem(VersionedAssetType.KNOWLEDGE, "KNOW.CKD", ReleaseEntryState.ACTIVE,
                    "know-v2", "V2", "2".repeat(64)),
                platformItem(VersionedAssetType.RULE, "RULE.CKD", ReleaseEntryState.ACTIVE,
                    "rule-v1", "V1", "3".repeat(64)),
                platformItem(VersionedAssetType.VALUE_SET, "VALUE.DISABLED",
                    ReleaseEntryState.DISABLED, null, null, null)
            ));
        when(runtimes.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.of(current));
        when(organizations.findByTenantIdAndId("tenant-A", "hospital-A"))
            .thenReturn(Optional.of(hospital()));
        when(runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "runtime-H9")).thenReturn(List.of(
                runtimeItem(VersionedAssetType.KNOWLEDGE, "KNOW.CKD", ReleaseEntryState.ACTIVE,
                    "know-v1", "V1", "4".repeat(64)),
                runtimeItem(VersionedAssetType.RULE, "RULE.CKD", ReleaseEntryState.ACTIVE,
                    "rule-v1", "V1", "3".repeat(64)),
                runtimeItem(VersionedAssetType.VALUE_SET, "VALUE.DISABLED",
                    ReleaseEntryState.ACTIVE, "value-v1", "V1", "5".repeat(64))
            ));

        PlatformUpgradeAnalysisResponse result = service.analyzePlatformUpgrade(
            "tenant-A",
            "hospital-A",
            "baseline-A8"
        );

        assertThat(result.analysisDigest()).matches("[0-9a-f]{64}");
        assertThat(result.runtimeMutation()).isFalse();
        assertThat(result.targetBaseline().baselineReleaseId()).isEqualTo("baseline-A8");
        assertThat(result.currentRuntime().releaseId()).isEqualTo("runtime-H9");
        assertThat(result.diffSummary().added()).isEqualTo(1);
        assertThat(result.diffSummary().modified()).isEqualTo(1);
        assertThat(result.diffSummary().disabled()).isEqualTo(1);
        assertThat(result.diffSummary().unchanged()).isEqualTo(1);
        assertThat(result.items())
            .extracting(
                PlatformUpgradeDiffItem::assetIdentity,
                PlatformUpgradeDiffItem::changeType,
                PlatformUpgradeDiffItem::currentVersionId,
                PlatformUpgradeDiffItem::targetVersionId)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                    "CARD.NEW", "ADDED", null, "card-new-v1"),
                org.assertj.core.groups.Tuple.tuple(
                    "KNOW.CKD", "MODIFIED", "know-v1", "know-v2"),
                org.assertj.core.groups.Tuple.tuple(
                    "RULE.CKD", "UNCHANGED", "rule-v1", "rule-v1"),
                org.assertj.core.groups.Tuple.tuple(
                    "VALUE.DISABLED", "DISABLED", "value-v1", null)
            );
    }

    @Test
    void platformUpgradeAnalysisDigestChangesWhenConflictDetailsChange() {
        PlatformBaselineRelease target = baseline();
        ClinicalRuntimeRelease current = runtime();
        when(baselines.findByBaselineReleaseId("baseline-A8")).thenReturn(Optional.of(target));
        when(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "baseline-A8")).thenReturn(List.of(
                platformItem(VersionedAssetType.RULE, "RULE.CKD", ReleaseEntryState.ACTIVE,
                    "rule-v2", "V2", "2".repeat(64))
            ));
        when(runtimes.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.of(current));
        when(organizations.findByTenantIdAndId("tenant-A", "hospital-A"))
            .thenReturn(Optional.of(hospital()));
        when(runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "runtime-H9")).thenReturn(List.of(
                runtimeItem(VersionedAssetType.RULE, "RULE.CKD", ReleaseEntryState.ACTIVE,
                    "rule-v1", "V1", "1".repeat(64))
            ));
        when(overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndLifecycleStatus(
                "tenant-A",
                VersionedAssetType.RULE,
                "RULE.CKD",
                InheritanceOverrideStatus.ACTIVE))
            .thenReturn(List.of(override("override-A", "local-rule-A")))
            .thenReturn(List.of(override("override-B", "local-rule-B")));

        PlatformUpgradeAnalysisResponse first = service.analyzePlatformUpgrade(
            "tenant-A",
            "hospital-A",
            "baseline-A8"
        );
        PlatformUpgradeAnalysisResponse second = service.analyzePlatformUpgrade(
            "tenant-A",
            "hospital-A",
            "baseline-A8"
        );

        assertThat(first.diffSummary().conflictCount()).isEqualTo(1);
        assertThat(second.diffSummary().conflictCount()).isEqualTo(1);
        assertThat(first.analysisDigest()).isNotEqualTo(second.analysisDigest());
    }

    private PlatformBaselineRelease baseline() {
        return new PlatformBaselineRelease(
            8L, "baseline-A8", 8L, "a".repeat(64),
            Instant.EPOCH, "operator-platform",
            Instant.EPOCH, "operator-platform", "trace-platform");
    }

    private ClinicalRuntimeRelease runtime() {
        return new ClinicalRuntimeRelease(
            9L, "runtime-H9", "tenant-A", "hospital-A", 9L,
            "baseline-A8", "b".repeat(64), null,
            Instant.EPOCH, "operator-hospital",
            Instant.EPOCH, "operator-hospital", "trace-hospital");
    }

    private PlatformBaselineItem platformItem(
            VersionedAssetType assetType,
            String assetIdentity,
            ReleaseEntryState entryState,
            String versionId,
            String versionNo,
            String contentHash) {
        return new PlatformBaselineItem(
            null,
            "baseline-A8",
            PlatformTenant.ID,
            assetType,
            assetIdentity,
            entryState,
            versionId,
            versionNo,
            contentHash,
            Instant.EPOCH,
            "operator-platform",
            "trace-platform"
        );
    }

    private ClinicalRuntimeReleaseItem runtimeItem(
            VersionedAssetType assetType,
            String assetIdentity,
            ReleaseEntryState entryState,
            String versionId,
            String versionNo,
            String contentHash) {
        return new ClinicalRuntimeReleaseItem(
            null,
            "runtime-H9",
            PlatformTenant.ID,
            ReleaseSourceLayer.PLATFORM,
            assetType,
            assetIdentity,
            entryState,
            versionId,
            versionNo,
            contentHash,
            Instant.EPOCH,
            "operator-hospital",
            "trace-hospital"
        );
    }

    private OrgUnit hospital() {
        return new OrgUnit(
            "hospital-A",
            null,
            "tenant-A",
            "/tenant-A/hospital-A",
            OrgLevel.FACILITY,
            "hospital-A",
            "中心医院",
            null,
            OrgFacilityType.HOSPITAL,
            null,
            OrgUnitStatus.ACTIVE,
            Instant.EPOCH,
            "operator",
            Instant.EPOCH,
            "operator"
        );
    }

    private InheritanceOverride override(String overrideId, String overrideVersionId) {
        return new InheritanceOverride(
            null,
            overrideId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.CKD",
            "rule-v1",
            overrideVersionId,
            InheritanceOverrideMode.REPLACE,
            InheritancePropagation.INHERITABLE,
            InheritanceOverrideStatus.ACTIVE,
            "/tenant-A/hospital-A",
            "adult|inpatient",
            "本院覆盖",
            "平台升级冲突测试",
            "肾病中心",
            Instant.EPOCH,
            "operator",
            Instant.EPOCH,
            "operator",
            "trace-test"
        );
    }
}
