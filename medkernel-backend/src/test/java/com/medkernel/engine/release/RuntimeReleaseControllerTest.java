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
    private final RuntimeReleaseController controller =
        new RuntimeReleaseController(baselines, runtimes, queries, candidates);

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
}
