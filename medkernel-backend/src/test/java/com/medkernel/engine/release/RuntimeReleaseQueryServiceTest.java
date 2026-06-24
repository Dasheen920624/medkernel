package com.medkernel.engine.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
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
    private final RuntimeReleaseQueryService service =
        new RuntimeReleaseQueryService(
            baselines, baselineItems, runtimes, runtimeItems);

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

        PlatformBaselineDetailResponse result = service.currentPlatformBaseline();

        assertThat(result.release()).isEqualTo(baseline);
        assertThat(result.items()).containsExactly(item);
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
            service.currentHospitalRuntime("tenant-A", "hospital-A");

        assertThat(result.release()).isEqualTo(runtime);
        assertThat(result.items()).containsExactly(item);
    }

    @Test
    void reportsAnHonestNotFoundStateWhenHospitalHasNoRuntimeRevision() {
        when(runtimes.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            "tenant-A", "hospital-A")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentHospitalRuntime(
            "tenant-A", "hospital-A"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
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
}
