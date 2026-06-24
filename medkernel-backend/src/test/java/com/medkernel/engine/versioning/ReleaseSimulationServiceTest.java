package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotRepository;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;

class ReleaseSimulationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private AssetVersionRepository assetVersions;
    private InheritanceOverrideRepository overrides;
    private AssetDependencyService dependencies;
    private OrgUnitRepository orgUnits;
    private OrgHierarchyRepository orgHierarchy;
    private ContextSnapshotRepository snapshots;

    @BeforeEach
    void setUp() {
        assetVersions = mock(AssetVersionRepository.class);
        overrides = mock(InheritanceOverrideRepository.class);
        dependencies = mock(AssetDependencyService.class);
        orgUnits = mock(OrgUnitRepository.class);
        orgHierarchy = mock(OrgHierarchyRepository.class);
        snapshots = mock(ContextSnapshotRepository.class);
    }

    @Test
    void blocksReleaseWhenLockedSafetyAndDependenciesFailAndReturnsSubtreeConflicts() {
        AssetVersion candidate = version(
            "av-v2", "2", "hash-v2", AssetVersionOverridePolicy.REVIEW, AssetVersionStatus.DRAFT);
        AssetVersion current = version(
            "av-v1", "1", "hash-v1", AssetVersionOverridePolicy.LOCKED, AssetVersionStatus.PUBLISHED);
        when(assetVersions.findByVersionIdAndTenantId("av-v2", "tenant-A"))
            .thenReturn(Optional.of(candidate));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            eq("tenant-A"),
            eq(VersionedAssetType.RULE),
            any(),
            eq(AssetVersionStatus.PUBLISHED)
        )).thenReturn(List.of(current));
        when(orgHierarchy.findDescendantsAndSelf("tenant-A", "hospital-A"))
            .thenReturn(List.of(
                org("hospital-A", "/TENANT-A/HOSP-A", "中心医院", OrgLevel.FACILITY),
                org("dept-A", "/TENANT-A/HOSP-A/DEPT-A", "心内科", OrgLevel.DEPARTMENT)
            ));
        when(overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndLifecycleStatus(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            InheritanceOverrideStatus.ACTIVE
        )).thenReturn(List.of(override("/TENANT-A/HOSP-A/DEPT-A")));
        doThrow(new ApiException(ErrorCode.CONFLICT, "引用完整性校验失败：术语版本不可解析"))
            .when(dependencies).assertDependenciesResolvable(candidate);
        when(snapshots.findRecentActiveByTenantId(eq("tenant-A"), any(), eq(100)))
            .thenReturn(List.of());

        ReleaseSimulationService service = service(List.of(), List.of());
        ReleaseSimulationResult result = service.simulate(command());

        assertThat(result.affectedOrganizations())
            .extracting(ReleaseSimulationResult.AffectedOrganization::orgUnitId)
            .containsExactly("hospital-A", "dept-A");
        assertThat(result.safety().passed()).isFalse();
        assertThat(result.safety().issues()).anyMatch(issue -> issue.contains("LOCKED"));
        assertThat(result.dependencies().passed()).isFalse();
        assertThat(result.dependencies().issues()).anyMatch(issue -> issue.contains("引用完整性"));
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.replay().status()).isEqualTo("NO_DATA");
        assertThat(result.releasable()).isFalse();
        assertThat(result.simulationDigest()).matches("[0-9a-f]{64}");
        verifyNoInteractions(orgUnits);
    }

    @Test
    void returnsReplayEvidenceAndAllowsConfirmationWhenAllSafetyChecksPass() {
        AssetVersion candidate = version(
            "av-v2", "2", "hash-v2", AssetVersionOverridePolicy.REVIEW, AssetVersionStatus.DRAFT);
        AssetVersion current = version(
            "av-v1", "1", "hash-v1", AssetVersionOverridePolicy.REVIEW, AssetVersionStatus.PUBLISHED);
        when(assetVersions.findByVersionIdAndTenantId("av-v2", "tenant-A"))
            .thenReturn(Optional.of(candidate));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            eq("tenant-A"),
            eq(VersionedAssetType.RULE),
            any(),
            eq(AssetVersionStatus.PUBLISHED)
        )).thenReturn(List.of(current));
        when(orgHierarchy.findDescendantsAndSelf("tenant-A", "hospital-A"))
            .thenReturn(List.of(org("hospital-A", "/TENANT-A/HOSP-A", "中心医院", OrgLevel.FACILITY)));
        when(overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndLifecycleStatus(
            any(), any(), any(), any()
        )).thenReturn(List.of());
        when(snapshots.findRecentActiveByTenantId(eq("tenant-A"), any(), eq(100)))
            .thenReturn(List.of(snapshot("ctx-1", "hospital-A")));
        ReleaseSimulationReplayEvaluator evaluator = mock(ReleaseSimulationReplayEvaluator.class);
        when(evaluator.supports(VersionedAssetType.RULE)).thenReturn(true);
        when(evaluator.replay(any(), eq(current), eq(candidate), any())).thenReturn(
            new ReleaseSimulationResult.Replay(
                "SUPPORTED", 40, 6, 4, 2, 1, 0, List.of("ctx-high-risk"), null));
        SafetyMonotonicityCheck safetyCheck = mock(SafetyMonotonicityCheck.class);
        when(safetyCheck.supports(VersionedAssetType.RULE)).thenReturn(true);
        when(safetyCheck.isAtLeastAsStrict(current, candidate)).thenReturn(true);

        ReleaseSimulationService service = service(
            List.of(safetyCheck),
            List.of(evaluator)
        );
        ReleaseSimulationResult result = service.simulate(command());

        assertThat(result.safety().passed()).isTrue();
        assertThat(result.dependencies().passed()).isTrue();
        assertThat(result.replay().sampledCases()).isEqualTo(40);
        assertThat(result.replay().changedCases()).isEqualTo(6);
        assertThat(result.diff().changeType()).isEqualTo("MODIFIED");
        assertThat(result.releasable()).isTrue();
    }

    @Test
    void rejectsCandidateFromAnotherTenantBeforeReadingAssets() {
        ReleaseSimulationCommand crossTenant = new ReleaseSimulationCommand(
            "tenant-A",
            "tenant-B",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v2",
            List.of("hospital-A"),
            "/TENANT-A/HOSP-A",
            "adult|inpatient",
            RolloutPolicy.canaryBedPercent(10),
            30,
            100
        );

        assertThatThrownBy(() -> service(List.of(), List.of()).simulate(crossTenant))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("其他租户");

        verifyNoInteractions(assetVersions);
    }

    private ReleaseSimulationService service(
            List<SafetyMonotonicityCheck> safetyChecks,
            List<ReleaseSimulationReplayEvaluator> replayEvaluators) {
        return new ReleaseSimulationService(
            assetVersions,
            overrides,
            dependencies,
            safetyChecks,
            orgUnits,
            orgHierarchy,
            snapshots,
            replayEvaluators,
            new ObjectMapper(),
            CLOCK
        );
    }

    private ReleaseSimulationCommand command() {
        return new ReleaseSimulationCommand(
            "tenant-A",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v2",
            List.of("hospital-A"),
            "/TENANT-A/HOSP-A",
            "adult|inpatient",
            new RolloutPolicy(
                RolloutStrategy.ORG_SUBTREE,
                List.of("hospital-A"),
                null,
                List.of(),
                null,
                null
            ),
            30,
            100
        );
    }

    private AssetVersion version(
            String versionId,
            String versionNo,
            String hash,
            AssetVersionOverridePolicy overridePolicy,
            AssetVersionStatus status) {
        return new AssetVersion(
            1L,
            versionId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionNo,
            "/TENANT-A/HOSP-A",
            "adult|inpatient",
            hash,
            AssetVersionSafetyPolicy.NORMAL,
            overridePolicy,
            status,
            "scope",
            "source",
            NOW.minusSeconds(86_400),
            null,
            NOW.minusSeconds(172_800),
            "author",
            NOW.minusSeconds(86_400),
            "author",
            "trace"
        );
    }

    private OrgUnit org(String id, String path, String name, OrgLevel level) {
        return new OrgUnit(
            id,
            null,
            "tenant-A",
            path,
            level,
            id,
            name,
            null,
            null,
            null,
            OrgUnitStatus.ACTIVE,
            NOW,
            "author",
            NOW,
            "author"
        );
    }

    private InheritanceOverride override(String orgPath) {
        return new InheritanceOverride(
            1L,
            "ovr-1",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v1",
            "av-local",
            InheritanceOverrideMode.REPLACE,
            InheritancePropagation.INHERITABLE,
            InheritanceOverrideStatus.ACTIVE,
            orgPath,
            "adult|inpatient",
            "本地阈值更严格",
            "本院制度",
            "心内科",
            NOW,
            "author",
            NOW,
            "author",
            "trace"
        );
    }

    private ContextSnapshot snapshot(String snapshotId, String orgUnitId) {
        return new ContextSnapshot(
            1L,
            snapshotId,
            "tenant-A",
            orgUnitId,
            "request-1",
            "/TENANT-A/HOSP-A",
            "runtime-release-test",
            "patient-1",
            "encounter-1",
            ContextSnapshotStatus.ACTIVE,
            "[]",
            "{}",
            "{}",
            QualityStatus.VALID,
            "trace",
            null,
            NOW.minusSeconds(3600),
            "author"
        );
    }
}
