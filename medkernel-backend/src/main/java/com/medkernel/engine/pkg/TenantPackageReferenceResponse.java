package com.medkernel.engine.pkg;

/**
 * 租户平台包引用响应。
 */
public record TenantPackageReferenceResponse(
    String referenceId,
    String tenantId,
    String platformTenantId,
    String platformPackageId,
    String packageCode,
    String packageVersion,
    String targetOrgUnitId,
    String sourceTemplateCode,
    TenantPackageReferenceStatus status
) {
    public static TenantPackageReferenceResponse from(TenantPackageReference reference) {
        return new TenantPackageReferenceResponse(
            reference.referenceId(),
            reference.tenantId(),
            reference.platformTenantId(),
            reference.platformPackageId(),
            reference.packageCode(),
            reference.packageVersion(),
            reference.targetOrgUnitId(),
            reference.sourceTemplateCode(),
            reference.status()
        );
    }
}
