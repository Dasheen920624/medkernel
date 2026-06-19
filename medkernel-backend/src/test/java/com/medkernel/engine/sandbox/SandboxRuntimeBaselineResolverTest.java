package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.pkg.EffectiveKnowledgePackageResolver;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResponse;
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.PlatformTenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SandboxRuntimeBaselineResolverTest {

    private final SandboxRuntimeBindingRepository bindings = mock(SandboxRuntimeBindingRepository.class);
    private final KnowledgePackageRepository packages = mock(KnowledgePackageRepository.class);
    private final EffectiveKnowledgePackageResolver effectivePackages = mock(EffectiveKnowledgePackageResolver.class);
    private final SandboxRuntimeBaselineResolver resolver =
        new SandboxRuntimeBaselineResolver(bindings, packages, effectivePackages);

    @Test
    void resolvesCurrentFromTheOnlyExplicitActiveBindingAndFreezesEffectivePackage() {
        SandboxRuntimeBinding binding = binding("tenant-A", "tenant-A", "pkg-local", "1.2.0");
        KnowledgePackage pack = pack("tenant-A", "pkg-local", "1.2.0", KnowledgePackageStatus.ACTIVE);
        when(bindings.findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
            "tenant-A", SandboxRuntimeBindingStatus.ACTIVE)).thenReturn(List.of(binding));
        when(packages.findByPackageIdAndTenantId("pkg-local", "tenant-A")).thenReturn(Optional.of(pack));
        when(effectivePackages.resolve("tenant-A", "PKG.SANDBOX", "1.2.0", "hospital-A"))
            .thenReturn(effective("tenant-A", "pkg-local", "1.2.0"));

        SandboxRuntimeBaseline baseline = resolver.resolveCurrent("tenant-A", "hospital-A");

        assertThat(baseline.mode()).isEqualTo(SandboxRunMode.CURRENT);
        assertThat(baseline.bindingId()).isEqualTo("binding-1");
        assertThat(baseline.packageId()).isEqualTo("pkg-local");
        assertThat(baseline.packageVersion()).isEqualTo("1.2.0");
        assertThat(baseline.resolutionSource()).isEqualTo(SandboxResolutionSource.TENANT_PACKAGE);
        assertThat(baseline.effectivePackage()).isNotNull();
        verify(effectivePackages).resolve("tenant-A", "PKG.SANDBOX", "1.2.0", "hospital-A");
    }

    @Test
    void supportsExplicitPlatformPackageBindingWithoutCopyingItToTenant() {
        SandboxRuntimeBinding binding = binding("tenant-A", PlatformTenant.ID, "pkg-platform", "3.0.0");
        KnowledgePackage pack = pack(PlatformTenant.ID, "pkg-platform", "3.0.0", KnowledgePackageStatus.PUBLISHED);
        when(bindings.findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
            "tenant-A", SandboxRuntimeBindingStatus.ACTIVE)).thenReturn(List.of(binding));
        when(packages.findByPackageIdAndTenantId("pkg-platform", PlatformTenant.ID)).thenReturn(Optional.of(pack));
        when(effectivePackages.resolve("tenant-A", "PKG.SANDBOX", "3.0.0", "hospital-A"))
            .thenReturn(effective("tenant-A", "pkg-platform", "3.0.0"));

        SandboxRuntimeBaseline baseline = resolver.resolveCurrent("tenant-A", "hospital-A");

        assertThat(baseline.resolutionSource()).isEqualTo(SandboxResolutionSource.PLATFORM_PACKAGE);
        assertThat(baseline.packageOwnerTenantId()).isEqualTo(PlatformTenant.ID);
    }

    @Test
    void rejectsMissingOrAmbiguousBindingInsteadOfSelectingLatestPackage() {
        when(bindings.findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
            "tenant-A", SandboxRuntimeBindingStatus.ACTIVE)).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolveCurrent("tenant-A", "hospital-A"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("SANDBOX_RUNTIME_BASELINE_MISSING");
        verifyNoInteractions(packages, effectivePackages);

        when(bindings.findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
            "tenant-A", SandboxRuntimeBindingStatus.ACTIVE)).thenReturn(List.of(
                binding("tenant-A", "tenant-A", "pkg-a", "1"),
                binding("tenant-A", "tenant-A", "pkg-b", "2")));

        assertThatThrownBy(() -> resolver.resolveCurrent("tenant-A", "hospital-A"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("SANDBOX_RUNTIME_BASELINE_AMBIGUOUS");
        verify(packages, never()).findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(
            "tenant-A", KnowledgePackageStatus.ACTIVE);
    }

    @Test
    void rejectsBindingToMissingDraftOrMismatchedPackage() {
        SandboxRuntimeBinding binding = binding("tenant-A", "tenant-A", "pkg-local", "1.2.0");
        when(bindings.findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
            "tenant-A", SandboxRuntimeBindingStatus.ACTIVE)).thenReturn(List.of(binding));
        when(packages.findByPackageIdAndTenantId("pkg-local", "tenant-A")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveCurrent("tenant-A", "hospital-A"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("SANDBOX_RUNTIME_PACKAGE_MISSING");

        KnowledgePackage draft = pack("tenant-A", "pkg-local", "1.2.0", KnowledgePackageStatus.DRAFT);
        when(packages.findByPackageIdAndTenantId("pkg-local", "tenant-A")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> resolver.resolveCurrent("tenant-A", "hospital-A"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("SANDBOX_RUNTIME_PACKAGE_NOT_RELEASED");
        verifyNoInteractions(effectivePackages);
    }

    private static SandboxRuntimeBinding binding(
            String tenantId, String ownerTenantId, String packageId, String version) {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new SandboxRuntimeBinding(
            1L, "binding-1", tenantId, "hospital-A", ownerTenantId, packageId,
            "PKG.SANDBOX", version, SandboxRuntimeBindingStatus.ACTIVE, tenantId + "|ACTIVE",
            now, "governor-1", now, "governor-1", now, "governor-1", "trace-1");
    }

    private static KnowledgePackage pack(
            String ownerTenantId, String packageId, String version, KnowledgePackageStatus status) {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new KnowledgePackage(
            1L, packageId, ownerTenantId, "PKG.SANDBOX", version, "沙盘包", "沙盘运行基线",
            status, now, "governor-1", now, "governor-1", "trace-1");
    }

    private static EffectiveKnowledgePackageResponse effective(
            String tenantId, String packageId, String version) {
        return new EffectiveKnowledgePackageResponse(
            tenantId, "hospital-A", packageId, "PKG.SANDBOX", version,
            List.of(), List.of(), List.of());
    }
}
