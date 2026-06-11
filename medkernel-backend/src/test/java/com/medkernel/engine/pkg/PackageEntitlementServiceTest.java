package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

class PackageEntitlementServiceTest {

    private KnowledgePackageRepository packageRepository;
    private PackageEntitlementRepository entitlementRepository;
    private OrgUnitRepository orgUnitRepository;
    private AuditRecorder auditRecorder;
    private PackageEntitlementService service;

    @BeforeEach
    void setUp() {
        packageRepository = mock(KnowledgePackageRepository.class);
        entitlementRepository = mock(PackageEntitlementRepository.class);
        orgUnitRepository = mock(OrgUnitRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        OrgUnit tenantRoot = mock(OrgUnit.class);
        when(tenantRoot.status()).thenReturn(OrgUnitStatus.ACTIVE);
        when(orgUnitRepository.findByTenantIdAndParentIdIsNull("tenant-A"))
            .thenReturn(Optional.of(tenantRoot));
        service = new PackageEntitlementService(
            packageRepository, entitlementRepository, orgUnitRepository, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-entitlement", OrgScope.tenant(PlatformTenant.ID), "platform-governance-admin"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void grantsRestrictedPlatformPackageToTargetTenant() {
        KnowledgePackage pack = platformPackage(PackageAccessPolicy.ENTITLED);
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        when(packageRepository.findByPackageIdAndTenantId("pkg-commercial", PlatformTenant.ID))
            .thenReturn(Optional.of(pack));
        when(entitlementRepository.findByTenantIdAndPlatformPackageId("tenant-A", "pkg-commercial"))
            .thenReturn(Optional.empty());
        when(entitlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PackageEntitlementResponse response = service.grant(
            "pkg-commercial",
            new PackageEntitlementGrantRequest("tenant-A", expiresAt, "已完成商业许可签署"));

        assertThat(response.tenantId()).isEqualTo("tenant-A");
        assertThat(response.packageIdentity()).isEqualTo("PKG.COMMERCIAL@2026.06");
        assertThat(response.status()).isEqualTo(PackageEntitlementViewStatus.ACTIVE);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        verify(auditRecorder).record(
            eq(AuditAction.PERMISSION_CHANGE),
            eq("package_entitlement"),
            any(),
            org.mockito.ArgumentMatchers.contains("开通"));
    }

    @Test
    void renewsExistingEntitlementWithoutCreatingSecondIdentity() {
        KnowledgePackage pack = platformPackage(PackageAccessPolicy.ENTITLED);
        Instant now = Instant.now();
        PackageEntitlement existing = new PackageEntitlement(
            1L,
            "entitlement-1",
            "tenant-A",
            PlatformTenant.ID,
            "pkg-commercial",
            "PKG.COMMERCIAL@2026.06",
            PackageEntitlementStatus.REVOKED,
            now.minus(60, ChronoUnit.DAYS),
            now.minus(1, ChronoUnit.DAYS),
            "旧授权已撤销",
            now.minus(60, ChronoUnit.DAYS),
            "platform-governance-admin",
            now.minus(1, ChronoUnit.DAYS),
            "platform-governance-admin",
            "trace-old");
        when(packageRepository.findByPackageIdAndTenantId("pkg-commercial", PlatformTenant.ID))
            .thenReturn(Optional.of(pack));
        when(entitlementRepository.findByTenantIdAndPlatformPackageId("tenant-A", "pkg-commercial"))
            .thenReturn(Optional.of(existing));
        when(entitlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grant(
            "pkg-commercial",
            new PackageEntitlementGrantRequest(
                "tenant-A",
                now.plus(90, ChronoUnit.DAYS),
                "续签完成"));

        verify(entitlementRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.entitlementId().equals("entitlement-1")
                && saved.status() == PackageEntitlementStatus.GRANTED
                && saved.createdAt().equals(existing.createdAt())
                && saved.createdBy().equals(existing.createdBy())));
    }

    @Test
    void rejectsGrantForUnknownTenant() {
        KnowledgePackage pack = platformPackage(PackageAccessPolicy.ENTITLED);
        when(packageRepository.findByPackageIdAndTenantId("pkg-commercial", PlatformTenant.ID))
            .thenReturn(Optional.of(pack));

        assertThatThrownBy(() -> service.grant(
                "pkg-commercial",
                new PackageEntitlementGrantRequest(
                    "tenant-missing",
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    "错误租户授权")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);

        verify(entitlementRepository, never()).save(any());
    }

    @Test
    void revokesExistingEntitlementAndKeepsItsOriginalGrantTime() {
        KnowledgePackage pack = platformPackage(PackageAccessPolicy.ENTITLED);
        Instant grantedAt = Instant.now().minus(10, ChronoUnit.DAYS);
        PackageEntitlement existing = new PackageEntitlement(
            1L,
            "entitlement-1",
            "tenant-A",
            PlatformTenant.ID,
            "pkg-commercial",
            "PKG.COMMERCIAL@2026.06",
            PackageEntitlementStatus.GRANTED,
            grantedAt,
            Instant.now().plus(20, ChronoUnit.DAYS),
            "商业许可已审批",
            grantedAt,
            "platform-governance-admin",
            grantedAt,
            "platform-governance-admin",
            "trace-old");
        when(packageRepository.findByPackageIdAndTenantId("pkg-commercial", PlatformTenant.ID))
            .thenReturn(Optional.of(pack));
        when(entitlementRepository.findByTenantIdAndPlatformPackageId("tenant-A", "pkg-commercial"))
            .thenReturn(Optional.of(existing));
        when(entitlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PackageEntitlementResponse response = service.revoke(
            "pkg-commercial",
            "tenant-A",
            new PackageEntitlementRevokeRequest(
                null, null, null, null, null, null, null, null, null, null, null, null,
                "商业许可已终止"));

        assertThat(response.status()).isEqualTo(PackageEntitlementViewStatus.REVOKED);
        assertThat(response.grantedAt()).isEqualTo(grantedAt);
        assertThat(response.reason()).isEqualTo("商业许可已终止");
        verify(auditRecorder).record(
            eq(AuditAction.PERMISSION_CHANGE),
            eq("package_entitlement"),
            eq("entitlement-1"),
            org.mockito.ArgumentMatchers.contains("撤销"));
    }

    @Test
    void rejectsMissingAndExpiredEntitlementsBeforePackageUse() {
        KnowledgePackage pack = platformPackage(PackageAccessPolicy.ENTITLED);
        when(entitlementRepository.findByTenantIdAndPlatformPackageId("tenant-A", "pkg-commercial"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertUsable("tenant-A", pack))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);

        Instant now = Instant.now();
        when(entitlementRepository.findByTenantIdAndPlatformPackageId("tenant-A", "pkg-commercial"))
            .thenReturn(Optional.of(new PackageEntitlement(
                1L,
                "entitlement-expired",
                "tenant-A",
                PlatformTenant.ID,
                "pkg-commercial",
                "PKG.COMMERCIAL@2026.06",
                PackageEntitlementStatus.GRANTED,
                now.minus(30, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.MINUTES),
                "试用授权",
                now.minus(30, ChronoUnit.DAYS),
                "platform-governance-admin",
                now.minus(30, ChronoUnit.DAYS),
                "platform-governance-admin",
                "trace-expired")));

        assertThatThrownBy(() -> service.assertUsable("tenant-A", pack))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.PACKAGE_ENTITLEMENT_EXPIRED);
    }

    @Test
    void openPackageAndPlatformOwnerDoNotRequireTenantGrant() {
        service.assertUsable("tenant-A", platformPackage(PackageAccessPolicy.OPEN));
        service.assertUsable(PlatformTenant.ID, platformPackage(PackageAccessPolicy.ENTITLED));

        verify(entitlementRepository, never()).findByTenantIdAndPlatformPackageId(any(), any());
    }

    @Test
    void batchUsabilityIncludesOpenAndActiveEntitledPackagesOnly() {
        Instant now = Instant.now();
        KnowledgePackage openPackage = platformPackage(
            "pkg-open", "PKG.OPEN", PackageAccessPolicy.OPEN);
        KnowledgePackage activeRestrictedPackage = platformPackage(
            "pkg-active", "PKG.ACTIVE", PackageAccessPolicy.ENTITLED);
        KnowledgePackage expiredRestrictedPackage = platformPackage(
            "pkg-expired", "PKG.EXPIRED", PackageAccessPolicy.ENTITLED);
        when(entitlementRepository.findByTenantIdAndPlatformPackageIdIn(
                "tenant-A", Set.of("pkg-active", "pkg-expired")))
            .thenReturn(List.of(
                entitlement("pkg-active", PackageEntitlementStatus.GRANTED, now.plus(1, ChronoUnit.DAYS)),
                entitlement("pkg-expired", PackageEntitlementStatus.GRANTED, now.minus(1, ChronoUnit.MINUTES))
            ));

        Set<String> usablePackageIds = service.usablePackageIds(
            "tenant-A",
            List.of(openPackage, activeRestrictedPackage, expiredRestrictedPackage));

        assertThat(usablePackageIds).containsExactlyInAnyOrder("pkg-open", "pkg-active");
    }

    @Test
    void rejectsGrantFromNonPlatformTenant() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-tenant", OrgScope.tenant("tenant-A"), "tenant-admin"));

        assertThatThrownBy(() -> service.grant(
                "pkg-commercial",
                new PackageEntitlementGrantRequest(
                    "tenant-B",
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    "越权尝试")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);

        verify(packageRepository, never()).findByPackageIdAndTenantId(any(), any());
    }

    private KnowledgePackage platformPackage(PackageAccessPolicy accessPolicy) {
        return platformPackage("pkg-commercial", "PKG.COMMERCIAL", accessPolicy);
    }

    private KnowledgePackage platformPackage(
            String packageId,
            String packageCode,
            PackageAccessPolicy accessPolicy) {
        Instant now = Instant.parse("2026-06-09T01:00:00Z");
        return new KnowledgePackage(
            1L,
            packageId,
            PlatformTenant.ID,
            packageCode,
            "2026.06",
            "商业指南包",
            "受许可约束的平台知识包",
            accessPolicy,
            KnowledgePackageStatus.ACTIVE,
            now,
            "platform-governance-admin",
            now,
            "platform-governance-admin",
            "trace-package");
    }

    private PackageEntitlement entitlement(
            String platformPackageId,
            PackageEntitlementStatus status,
            Instant expiresAt) {
        Instant grantedAt = Instant.now().minus(1, ChronoUnit.DAYS);
        return new PackageEntitlement(
            1L,
            "entitlement-" + platformPackageId,
            "tenant-A",
            PlatformTenant.ID,
            platformPackageId,
            platformPackageId + "@2026.06",
            status,
            grantedAt,
            expiresAt,
            "授权依据",
            grantedAt,
            "platform-governance-admin",
            grantedAt,
            "platform-governance-admin",
            "trace-entitlement");
    }
}
