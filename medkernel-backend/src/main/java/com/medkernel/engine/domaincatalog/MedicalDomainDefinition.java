package com.medkernel.engine.domaincatalog;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 平台统一医疗领域目录项。
 *
 * <p>领域编码是稳定分类标识，不具有版本号，也不参与临床执行路由。
 */
@Table("medical_domain")
public record MedicalDomainDefinition(
    @Id Long id,
    @Column("domain_code") String domainCode,
    String name,
    String description,
    @Column("parent_domain_code") String parentDomainCode,
    MedicalDomainStatus status,
    @Column("sort_order") Integer sortOrder,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
