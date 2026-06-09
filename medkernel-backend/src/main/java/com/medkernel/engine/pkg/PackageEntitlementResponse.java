package com.medkernel.engine.pkg;

import java.time.Instant;

/**
 * 平台包租户授权响应。
 */
public record PackageEntitlementResponse(
    String entitlementId,
    String tenantId,
    String platformPackageId,
    String packageIdentity,
    PackageEntitlementViewStatus status,
    Instant grantedAt,
    Instant expiresAt,
    String reason,
    Instant updatedAt,
    String updatedBy
) {
    public static PackageEntitlementResponse from(PackageEntitlement entity, Instant now) {
        PackageEntitlementViewStatus viewStatus = entity.status() == PackageEntitlementStatus.REVOKED
            ? PackageEntitlementViewStatus.REVOKED
            : !entity.expiresAt().isAfter(now)
                ? PackageEntitlementViewStatus.EXPIRED
                : PackageEntitlementViewStatus.ACTIVE;
        return new PackageEntitlementResponse(
            entity.entitlementId(),
            entity.tenantId(),
            entity.platformPackageId(),
            entity.packageIdentity(),
            viewStatus,
            entity.grantedAt(),
            entity.expiresAt(),
            entity.reason(),
            entity.updatedAt(),
            entity.updatedBy());
    }
}
