package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 术语映射包版本（含 DRAFT / GRAY / PUBLISHED / SUPERSEDED / ROLLED_BACK / ARCHIVED 生命周期）。
 *
 * <p>把已确认 {@link TermMapping} 按租户 + package_code + scope 打包发布到指定组织作用域；
 * 业务键 (tenant_id, package_code, package_version, scope_level, scope_code) 唯一。
 * 全量发布会把同作用域旧 PUBLISHED 包置为 SUPERSEDED；回滚把当前置 ROLLED_BACK 并重新激活目标包。
 */
@Table("term_mapping_package")
public record TermMappingPackage(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("package_code") String packageCode,
    @Column("package_version") String packageVersion,
    @Column("display_name") String displayName,
    @Column("scope_level") String scopeLevel,
    @Column("scope_code") String scopeCode,
    @Column("status") TermMappingPackageStatus status,
    @Column("mapping_count") Integer mappingCount,
    @Column("content_hash") String contentHash,
    @Column("published_by") String publishedBy,
    @Column("published_at") Instant publishedAt,
    @Column("rollback_from_package_id") Long rollbackFromPackageId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
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
