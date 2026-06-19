package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.pkg.EffectiveKnowledgePackageResolver;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResponse;
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SandboxRuntimeBindingServiceTest {

    private final SandboxRuntimeBindingRepository bindings = mock(SandboxRuntimeBindingRepository.class);
    private final KnowledgePackageRepository packages = mock(KnowledgePackageRepository.class);
    private final EffectiveKnowledgePackageResolver effectivePackages =
        mock(EffectiveKnowledgePackageResolver.class);
    private final SandboxRuntimeBaselineResolver baselines = mock(SandboxRuntimeBaselineResolver.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final SandboxRuntimeBindingService service = new SandboxRuntimeBindingService(
        bindings, packages, effectivePackages, baselines, audit);

    @BeforeEach
    void setUpContext() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-binding", new OrgScope(
                "tenant-1", null, "hospital-1", null, null, "dept-ed", null, null),
            "governor-1"));
        when(bindings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void activatesPackageFromAuthoritativeIdentityAndDeactivatesPreviousBinding() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        SandboxRuntimeBinding previous = new SandboxRuntimeBinding(
            1L, "binding-old", "tenant-1", "dept-ed", "tenant-1", "pkg-old",
            "PKG.OLD", "1.0.0", SandboxRuntimeBindingStatus.ACTIVE, "tenant-1|ACTIVE",
            now, "governor-old", now, "governor-old", now, "governor-old", "trace-old");
        when(bindings.findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
            "tenant-1", SandboxRuntimeBindingStatus.ACTIVE)).thenReturn(List.of(previous));
        KnowledgePackage selected = pack("tenant-1", "pkg-new", "PKG.NEW", "7.2.1");
        when(packages.findByPackageIdAndTenantId("pkg-new", "tenant-1"))
            .thenReturn(java.util.Optional.of(selected));
        when(effectivePackages.resolveExplicitPackage("tenant-1", selected, "dept-ed"))
            .thenReturn(effective("pkg-new", "PKG.NEW", "7.2.1"));

        SandboxRuntimeStatusResponse result = service.activate(
            new SandboxRuntimeBindingRequest("tenant-1", "pkg-new"));

        assertThat(result.ready()).isTrue();
        assertThat(result.packageVersion()).isEqualTo("7.2.1");
        assertThat(result.resolutionSource()).isEqualTo(SandboxResolutionSource.TENANT_PACKAGE);
        org.mockito.ArgumentCaptor<SandboxRuntimeBinding> captor =
            org.mockito.ArgumentCaptor.forClass(SandboxRuntimeBinding.class);
        verify(bindings, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).status()).isEqualTo(SandboxRuntimeBindingStatus.INACTIVE);
        assertThat(captor.getAllValues().get(0).activeScopeKey()).isNull();
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(SandboxRuntimeBindingStatus.ACTIVE);
        assertThat(captor.getAllValues().get(1).activeScopeKey()).isEqualTo("tenant-1|ACTIVE");
        verify(audit).record(
            AuditAction.PUBLISH, "sandbox_runtime_binding", captor.getAllValues().get(1).bindingId(),
            "激活沙盘运行绑定 PKG.NEW@7.2.1 target=dept-ed source=TENANT_PACKAGE");
    }

    @Test
    void acceptsEntitledPlatformPackageWithoutCopyingItIntoTenantOwnership() {
        when(bindings.findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
            "tenant-1", SandboxRuntimeBindingStatus.ACTIVE)).thenReturn(List.of());
        KnowledgePackage selected = pack(
            PlatformTenant.ID, "pkg-platform", "PKG.PLATFORM", "3.0.0");
        when(packages.findByPackageIdAndTenantId("pkg-platform", PlatformTenant.ID))
            .thenReturn(java.util.Optional.of(selected));
        when(effectivePackages.resolveExplicitPackage("tenant-1", selected, "dept-ed"))
            .thenReturn(effective("pkg-platform", "PKG.PLATFORM", "3.0.0"));

        SandboxRuntimeStatusResponse result = service.activate(
            new SandboxRuntimeBindingRequest(PlatformTenant.ID, "pkg-platform"));

        assertThat(result.packageOwnerTenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(result.resolutionSource()).isEqualTo(SandboxResolutionSource.PLATFORM_PACKAGE);
    }

    @Test
    void rejectsPackageOwnedByUnrelatedTenantBeforeResolution() {
        assertThatThrownBy(() -> service.activate(
            new SandboxRuntimeBindingRequest("tenant-other", "pkg-foreign")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("只能绑定演练机构自有包或平台主源包");

        verifyNoInteractions(packages, effectivePackages, audit);
    }

    @Test
    void reportsMissingBindingAsHonestNotReadyState() {
        when(baselines.resolveCurrent("tenant-1", "dept-ed"))
            .thenThrow(new IllegalStateException("SANDBOX_RUNTIME_BASELINE_MISSING"));

        SandboxRuntimeStatusResponse status = service.currentStatus();

        assertThat(status.ready()).isFalse();
        assertThat(status.reasonCode()).isEqualTo("SANDBOX_RUNTIME_BASELINE_MISSING");
        assertThat(status.externalSideEffects()).isFalse();
    }

    private static KnowledgePackage pack(
            String ownerTenantId,
            String packageId,
            String packageCode,
            String version) {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new KnowledgePackage(
            null, packageId, ownerTenantId, packageCode, version, "沙盘包", "测试",
            KnowledgePackageStatus.ACTIVE, now, "governor-1", now, "governor-1", "trace-1");
    }

    private static EffectiveKnowledgePackageResponse effective(
            String packageId,
            String packageCode,
            String version) {
        return new EffectiveKnowledgePackageResponse(
            "tenant-1", "dept-ed", packageId, packageCode, version,
            List.of(), List.of(), List.of());
    }
}
