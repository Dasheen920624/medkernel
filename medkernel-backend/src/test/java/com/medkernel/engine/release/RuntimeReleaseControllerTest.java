package com.medkernel.engine.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;

import com.medkernel.engine.context.ClinicalRuntimeAssetSelection;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseCommand;
import com.medkernel.engine.context.ClinicalRuntimeReleaseService;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

class RuntimeReleaseControllerTest {

    private final PlatformBaselineService baselines = mock(PlatformBaselineService.class);
    private final ClinicalRuntimeReleaseService runtimes =
        mock(ClinicalRuntimeReleaseService.class);
    private final RuntimeReleaseQueryService queries =
        mock(RuntimeReleaseQueryService.class);
    private final ReleaseCandidateQueryService candidates =
        mock(ReleaseCandidateQueryService.class);
    private final RuntimeReleaseOfflineDeliveryService offlineDelivery =
        mock(RuntimeReleaseOfflineDeliveryService.class);
    private final RuntimeReleaseController controller =
        new RuntimeReleaseController(baselines, runtimes, queries, candidates, offlineDelivery);

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void platformPublishUsesAuthenticatedPlatformContextInsteadOfCallerOwnedFields() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-platform", OrgScope.tenant(PlatformTenant.ID), "operator-platform"));
        when(baselines.publish(any())).thenReturn(new PlatformBaselineRelease(
            1L, "baseline-A1", 1L, "a".repeat(64),
            Instant.EPOCH, "operator-platform",
            Instant.EPOCH, "operator-platform", "trace-platform"));

        controller.publishPlatformBaseline(new PlatformBaselinePublishRequest(
            List.of("rule-v1"),
            List.of(new ReleaseAssetRef(VersionedAssetType.PATHWAY, "PATH.OLD"))
        ));

        ArgumentCaptor<PlatformBaselinePublishCommand> command =
            ArgumentCaptor.forClass(PlatformBaselinePublishCommand.class);
        verify(baselines).publish(command.capture());
        assertThat(command.getValue().actor()).isEqualTo("operator-platform");
        assertThat(command.getValue().traceId()).isEqualTo("trace-platform");
    }

    @Test
    void hospitalActivationMaterializesAnyMixedAssetSelectionFromAuthenticatedTenant() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        when(runtimes.activate(any())).thenReturn(runtimeRelease());

        controller.activateHospitalRuntime("hospital-A", new ClinicalRuntimeActivateRequest(
            "baseline-A8",
            "runtime-H8",
            null,
            List.of(
                ClinicalRuntimeAssetSelection.platform(
                    VersionedAssetType.KNOWLEDGE, "KNOW.CKD"),
                ClinicalRuntimeAssetSelection.local(
                    VersionedAssetType.RULE, "RULE.CKD.LOCAL", "rule-v2")
            )
        ));

        ArgumentCaptor<ClinicalRuntimeReleaseCommand> command =
            ArgumentCaptor.forClass(ClinicalRuntimeReleaseCommand.class);
        verify(runtimes).activate(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(command.getValue().hospitalId()).isEqualTo("hospital-A");
        assertThat(command.getValue().actor()).isEqualTo("operator-hospital");
        assertThat(command.getValue().activeAssets()).hasSize(2);
    }

    @Test
    void hospitalRollbackRequiresAndForwardsTheConfirmedCurrentRelease() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        when(runtimes.rollback(
            "tenant-A",
            "hospital-A",
            "runtime-target-A",
            "runtime-current-A",
            "operator-hospital",
            "trace-hospital"
        )).thenReturn(runtimeRelease());

        controller.rollbackHospitalRuntime(
            "hospital-A",
            new ClinicalRuntimeRollbackRequest("runtime-target-A", "runtime-current-A")
        );

        verify(runtimes).rollback(
            "tenant-A",
            "hospital-A",
            "runtime-target-A",
            "runtime-current-A",
            "operator-hospital",
            "trace-hospital"
        );
    }

    @Test
    void hospitalActivationRequiresConfirmedPlatformUpgradeAnalysisWhenBaselineChanges() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        when(queries.analyzePlatformUpgrade("tenant-A", "hospital-A", "baseline-A9"))
            .thenReturn(upgradeAnalysis("digest-A9", "baseline-A8"));

        assertThatThrownBy(() -> controller.activateHospitalRuntime(
            "hospital-A",
            new ClinicalRuntimeActivateRequest("baseline-A9", "runtime-H8", null, List.of())
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(exception).hasMessageContaining("平台升级前必须先完成差异与冲突分析");
        });

        controller.activateHospitalRuntime(
            "hospital-A",
            new ClinicalRuntimeActivateRequest(
                "baseline-A9",
                "runtime-H8",
                "digest-A9",
                List.of(ClinicalRuntimeAssetSelection.platform(
                    VersionedAssetType.KNOWLEDGE, "KNOW.CKD"))
            )
        );

        verify(runtimes).activate(any());
    }

    @Test
    void hospitalActivationRejectsConfirmedPlatformUpgradeWhenConflictsRemain() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        when(queries.analyzePlatformUpgrade("tenant-A", "hospital-A", "baseline-A9"))
            .thenReturn(upgradeAnalysis("digest-A9", "baseline-A8", 1));

        assertThatThrownBy(() -> controller.activateHospitalRuntime(
            "hospital-A",
            new ClinicalRuntimeActivateRequest("baseline-A9", "runtime-H8", "digest-A9", List.of())
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(exception).hasMessageContaining("平台升级分析仍存在机构覆盖冲突");
        });
    }

    @Test
    void tenantContextCannotPublishThePlatformAuthorityBaseline() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));

        assertThatThrownBy(() -> controller.publishPlatformBaseline(
            new PlatformBaselinePublishRequest(List.of("rule-v1"), List.of())))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                assertThat(exception).hasMessageContaining("只有平台权威范围可以发布平台标准版本");
            });
    }

    @Test
    void currentHospitalRuntimeUsesAuthenticatedTenantAndExactHospital() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        ClinicalRuntimeReleaseDetailResponse detail =
            new ClinicalRuntimeReleaseDetailResponse(runtimeRelease(), List.of());
        when(queries.currentHospitalRuntime("tenant-A", "hospital-A"))
            .thenReturn(Optional.of(detail));

        assertThat(controller.currentHospitalRuntime("hospital-A").data())
            .isEqualTo(detail);
    }

    @Test
    void currentPlatformBaselineReturnsSuccessfulEmptyStateBeforeFirstBaseline() {
        when(queries.currentPlatformBaseline()).thenReturn(Optional.empty());

        assertThat(controller.currentPlatformBaseline().data()).isNull();
    }

    @Test
    void currentHospitalRuntimeReturnsSuccessfulEmptyStateBeforeFirstHospitalRevision() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        when(queries.currentHospitalRuntime("tenant-A", "hospital-A")).thenReturn(Optional.empty());

        assertThat(controller.currentHospitalRuntime("hospital-A").data()).isNull();
    }

    @Test
    void hospitalCandidateQueryUsesAuthenticatedTenantInsteadOfCallerOwnedTenant() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        PageResponse<ReleaseCandidateAsset> page = new PageResponse<>(
            List.of(), 1, 20, 0, false, false);
        when(candidates.hospitalCandidates(
            "tenant-A",
            "hospital-A",
            VersionedAssetType.RULE,
            "肾病",
            new PageRequest(1, 20, null)))
            .thenReturn(page);

        assertThat(controller.hospitalReleaseCandidates(
            "hospital-A", VersionedAssetType.RULE, "肾病", 1, 20, null).data())
            .isEqualTo(page);
    }

    @Test
    void hospitalHistoryUsesAuthenticatedTenantAndServerPagination() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        PageResponse<ClinicalRuntimeRelease> page = new PageResponse<>(
            List.of(runtimeRelease()), 1, 20, 1, false, false);
        when(queries.hospitalRuntimeHistory(
            "tenant-A", "hospital-A", new PageRequest(1, 20, "revisionNo,desc")))
            .thenReturn(page);

        assertThat(controller.hospitalRuntimeHistory(
            "hospital-A", 1, 20, "revisionNo,desc").data())
            .isEqualTo(page);
    }

    @Test
    void releaseReadEndpointsUseTheUnifiedAssetReadPermission() throws Exception {
        assertThat(permissionOf("currentPlatformBaseline"))
            .isEqualTo("@perm.has('asset.read')");
        assertThat(permissionOf("currentHospitalRuntime", String.class))
            .isEqualTo("@perm.has('asset.read')");
        assertThat(permissionOf(
            "hospitalReleaseCandidates",
            String.class,
            VersionedAssetType.class,
            String.class,
            Integer.class,
            Integer.class,
            String.class
        )).isEqualTo("@perm.has('asset.read')");
        assertThat(permissionOf(
            "hospitalRuntimeHistory",
            String.class,
            Integer.class,
            Integer.class,
            String.class
        )).isEqualTo("@perm.has('asset.read')");
        assertThat(permissionOf(
            "validateHospitalRuntimeOfflineImport",
            String.class,
            RuntimeReleaseOfflineImportPreviewRequest.class
        )).isEqualTo("@perm.has('asset.read')");
    }

    @Test
    void offlineDeliveryEndpointsUseAuthenticatedTenantAndDoNotAcceptHospitalMismatch() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        RuntimeReleaseOfflineDeliveryResponse delivery = new RuntimeReleaseOfflineDeliveryResponse(
            RuntimeReleaseOfflineDeliveryService.DELIVERY_KIND,
            "ev-runtime",
            "/api/v1/compliance/evidence/snapshots/ev-runtime/file",
            "sm3:" + "1".repeat(64),
            "SM3_WITH_SM2",
            false,
            null,
            List.of()
        );
        when(offlineDelivery.exportCurrentRuntimeRelease(
            "tenant-A", "hospital-A", "operator-hospital", "trace-hospital"))
            .thenReturn(delivery);
        RuntimeReleaseOfflineImportPreviewRequest request =
            new RuntimeReleaseOfflineImportPreviewRequest("ev-runtime", "runtime-H9", "hospital-A");
        RuntimeReleaseOfflineImportPreviewResponse preview =
            new RuntimeReleaseOfflineImportPreviewResponse(
                "VALIDATED",
                false,
                true,
                true,
                "runtime-H9",
                "hospital-A",
                "b".repeat(64),
                "sm3:" + "1".repeat(64),
                13,
                RuntimeReleaseOfflineDeliveryService.WARNING
            );
        when(offlineDelivery.validateImportPreview("tenant-A", request)).thenReturn(preview);

        assertThat(controller.exportHospitalRuntimeOfflineDelivery("hospital-A").data())
            .isEqualTo(delivery);
        assertThat(controller.validateHospitalRuntimeOfflineImport("hospital-A", request).data())
            .isEqualTo(preview);
        assertThatThrownBy(() -> controller.validateHospitalRuntimeOfflineImport(
            "hospital-B",
            request
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void releaseOfflineDeliveryExportRequiresTenantOverridePermission() throws Exception {
        assertThat(permissionOf("exportHospitalRuntimeOfflineDelivery", String.class))
            .isEqualTo("@perm.has('tenant.override')");
    }

    @Test
    void offlineDeliveryRestoreUsesAuthenticatedTenantAndRejectsHospitalMismatch() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-hospital", OrgScope.tenant("tenant-A"), "operator-hospital"));
        RuntimeReleaseOfflineRestoreRequest request = new RuntimeReleaseOfflineRestoreRequest(
            "ev-runtime",
            "runtime-H9",
            "hospital-A",
            "runtime-current",
            "sm3:" + "1".repeat(64)
        );
        RuntimeReleaseOfflineRestoreResponse restored =
            new RuntimeReleaseOfflineRestoreResponse(
                "RESTORED",
                true,
                "ev-runtime",
                "runtime-H9",
                "hospital-A",
                "sm3:" + "1".repeat(64),
                "b".repeat(64),
                13,
                runtimeRelease()
            );
        when(offlineDelivery.restoreImport(
            "tenant-A", request, "operator-hospital", "trace-hospital"))
            .thenReturn(restored);

        assertThat(controller.restoreHospitalRuntimeOfflineDelivery("hospital-A", request).data())
            .isEqualTo(restored);
        assertThatThrownBy(() -> controller.restoreHospitalRuntimeOfflineDelivery(
            "hospital-B",
            request
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void releaseOfflineDeliveryRestoreRequiresTenantOverridePermission() throws Exception {
        assertThat(permissionOf(
            "restoreHospitalRuntimeOfflineDelivery",
            String.class,
            RuntimeReleaseOfflineRestoreRequest.class
        )).isEqualTo("@perm.has('tenant.override')");
    }

    private String permissionOf(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = RuntimeReleaseController.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(PreAuthorize.class).value();
    }

    private ClinicalRuntimeRelease runtimeRelease() {
        return new ClinicalRuntimeRelease(
            1L,
            "runtime-H9",
            "tenant-A",
            "hospital-A",
            9L,
            "baseline-A8",
            "b".repeat(64),
            null,
            Instant.EPOCH,
            "operator-hospital",
            Instant.EPOCH,
            "operator-hospital",
            "trace-hospital"
        );
    }

    private PlatformUpgradeAnalysisResponse upgradeAnalysis(
            String digest,
            String currentBaselineReleaseId) {
        return upgradeAnalysis(digest, currentBaselineReleaseId, 0);
    }

    private PlatformUpgradeAnalysisResponse upgradeAnalysis(
            String digest,
            String currentBaselineReleaseId,
            int conflictCount) {
        return new PlatformUpgradeAnalysisResponse(
            digest,
            Instant.EPOCH,
            false,
            new PlatformUpgradeBaselineSnapshot("baseline-A9", 9L, "a".repeat(64)),
            new PlatformUpgradeRuntimeSnapshot(
                "runtime-H8",
                8L,
                currentBaselineReleaseId,
                "b".repeat(64)
            ),
            new PlatformUpgradeDiffSummary(1, 1, 0, 11, conflictCount),
            List.of()
        );
    }
}
