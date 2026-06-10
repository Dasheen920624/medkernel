package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 院内本地术语字典条目（HIS/LIS/PACS 等来源系统采集到的原始词条）。
 *
 * <p>租户隔离；业务键 (tenant_id, source_system, local_code, category) 唯一。
 * 与 {@link StandardTerm} 通过 {@link TermMapping} 建立映射关系，
 * 状态字段 {@link LocalTermStatus} 反映映射完成度（UNMAPPED / MAPPED / DISABLED）。
 */
@Table("local_term")
public record LocalTerm(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("source_system") String sourceSystem,
    @Column("local_code") String localCode,
    @Column("category") TermCategory category,
    @Column("local_name") String localName,
    @Column("normalized_name") String normalizedName,
    @Column("department_id") String departmentId,
    @Column("status") LocalTermStatus status,
    @Column("first_seen_at") Instant firstSeenAt,
    @Column("last_seen_at") Instant lastSeenAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
    LocalTerm mapped(String userId, Instant now) {
        return new LocalTerm(
            id, tenantId, sourceSystem, localCode, category, localName, normalizedName, departmentId,
            LocalTermStatus.MAPPED, firstSeenAt, lastSeenAt, createdAt, createdBy, now, userId
        );
    }
}
