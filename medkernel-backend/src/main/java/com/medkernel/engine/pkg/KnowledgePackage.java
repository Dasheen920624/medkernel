package com.medkernel.engine.pkg;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 知识包主数据实体。
 *
 * <p>保存已审核的字典、规则和路径等资产包的版本、状态与发布信息。
 */
@Table("knowledge_package")
public record KnowledgePackage(
    @Id Long id,
    @Column("package_id") String packageId,
    @Column("tenant_id") String tenantId,
    @Column("package_code") String packageCode,
    @Column("package_version") String packageVersion,
    String name,
    String description,
    @Column("access_policy") PackageAccessPolicy accessPolicy,
    KnowledgePackageStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public KnowledgePackage(
            Long id,
            String packageId,
            String tenantId,
            String packageCode,
            String packageVersion,
            String name,
            String description,
            KnowledgePackageStatus status,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(
            id, packageId, tenantId, packageCode, packageVersion, name, description,
            PackageAccessPolicy.OPEN, status, createdAt, createdBy, updatedAt, updatedBy, traceId);
    }

    public KnowledgePackage withStatus(KnowledgePackageStatus newStatus) {
        return new KnowledgePackage(
            id, packageId, tenantId, packageCode, packageVersion,
            name, description, accessPolicy, newStatus,
            createdAt, createdBy, Instant.now(), updatedBy, traceId
        );
    }
}
