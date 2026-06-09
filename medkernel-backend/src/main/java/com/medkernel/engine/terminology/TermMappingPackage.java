package com.medkernel.engine.terminology;

import java.time.Instant;

/**
 * 术语映射包领域视图（含 DRAFT / GRAY / PUBLISHED / SUPERSEDED / ROLLED_BACK / ARCHIVED 生命周期）。
 *
 * <p>数据来自统一知识包、统一包条目和统一资产版本，不再对应独立物理表。
 */
public record TermMappingPackage(
    Long id,
    String tenantId,
    String packageCode,
    String packageVersion,
    String displayName,
    String scopeLevel,
    String scopeCode,
    TermMappingPackageStatus status,
    Integer mappingCount,
    String contentHash,
    String publishedBy,
    Instant publishedAt,
    Long rollbackFromPackageId,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy
) {

    public static TermMappingPackage imported(
            String tenantId,
            String packageCode,
            String packageVersion,
            String displayName,
            String scopeLevel,
            String scopeCode,
            String status,
            Integer mappingCount,
            String contentHash,
            String publishedBy,
            Instant publishedAt,
            Long rollbackFromPackageId,
            Instant now,
            String actor) {
        return imported(
            null,
            tenantId,
            packageCode,
            packageVersion,
            displayName,
            scopeLevel,
            scopeCode,
            status,
            mappingCount,
            contentHash,
            publishedBy,
            publishedAt,
            rollbackFromPackageId,
            now,
            actor
        );
    }

    public static TermMappingPackage imported(
            Long id,
            String tenantId,
            String packageCode,
            String packageVersion,
            String displayName,
            String scopeLevel,
            String scopeCode,
            String status,
            Integer mappingCount,
            String contentHash,
            String publishedBy,
            Instant publishedAt,
            Long rollbackFromPackageId,
            Instant now,
            String actor) {
        return new TermMappingPackage(
            id,
            tenantId,
            packageCode,
            packageVersion,
            displayName,
            scopeLevel,
            scopeCode,
            parseStatus(status),
            mappingCount,
            contentHash,
            publishedBy,
            publishedAt,
            rollbackFromPackageId,
            now,
            actor,
            now,
            actor
        );
    }

    public boolean isReleasedForPackageAsset() {
        return status == TermMappingPackageStatus.PUBLISHED || status == TermMappingPackageStatus.GRAY;
    }

    public String statusName() {
        return status == null ? null : status.name();
    }

    private static TermMappingPackageStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("术语映射包状态不能为空");
        }
        return TermMappingPackageStatus.valueOf(status);
    }

    TermMappingPackage withStatus(TermMappingPackageStatus nextStatus, String userId, Instant now) {
        return new TermMappingPackage(
            id, tenantId, packageCode, packageVersion, displayName, scopeLevel, scopeCode,
            nextStatus, mappingCount, contentHash,
            nextStatus == TermMappingPackageStatus.PUBLISHED || nextStatus == TermMappingPackageStatus.GRAY ? userId : publishedBy,
            nextStatus == TermMappingPackageStatus.PUBLISHED || nextStatus == TermMappingPackageStatus.GRAY ? now : publishedAt,
            rollbackFromPackageId, createdAt, createdBy, now, userId
        );
    }

    TermMappingPackage rolledBack(String userId, Instant now) {
        return new TermMappingPackage(
            id, tenantId, packageCode, packageVersion, displayName, scopeLevel, scopeCode,
            TermMappingPackageStatus.ROLLED_BACK, mappingCount, contentHash,
            publishedBy, publishedAt, rollbackFromPackageId, createdAt, createdBy, now, userId
        );
    }

    TermMappingPackage restoredFromRollback(Long sourcePackageId, String userId, Instant now) {
        return new TermMappingPackage(
            id, tenantId, packageCode, packageVersion, displayName, scopeLevel, scopeCode,
            TermMappingPackageStatus.PUBLISHED, mappingCount, contentHash,
            userId, now, sourcePackageId, createdAt, createdBy, now, userId
        );
    }
}
