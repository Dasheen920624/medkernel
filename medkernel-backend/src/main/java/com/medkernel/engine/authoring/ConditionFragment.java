package com.medkernel.engine.authoring;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 条件片段实体。
 *
 * <p>保存可在规则 {@code when} 与路径守卫中复用的命名条件组；同一租户下
 * {@code fragment_code + version_no} 唯一，运行时必须同时匹配知识包版本。
 */
@Table("mk_engine_condition_fragment")
public record ConditionFragment(
    @Id Long id,
    @Column("fragment_id") String fragmentId,
    @Column("tenant_id") String tenantId,
    @Column("fragment_code") String fragmentCode,
    String name,
    String category,
    @Column("body_json") String bodyJson,
    @Column("version_no") Integer versionNo,
    ConditionFragmentStatus status,
    @Column("package_version") String packageVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
