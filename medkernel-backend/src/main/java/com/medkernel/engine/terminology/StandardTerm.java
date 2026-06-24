package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 标准临床术语字典记录（ICD-10 / SNOMED CT / LOINC / RxNorm / ATC 等标准词条）。
 *
 * <p>平台标准词以平台主租户归属作为标准版本；客户租户可保留少量本地覆盖记录，
 * 院内术语与标准词的适配关系由 {@link LocalTerm} / {@link TermMapping} 表达。
 * 业务键 (tenant_id, standard_system, term_code, version_no) 唯一。
 * 状态字段 {@link StandardTermStatus} 决定是否可被映射引用（ACTIVE 可用 / DISABLED 禁用）。
 */
@Table("standard_term")
public record StandardTerm(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("standard_system") String standardSystem,
    @Column("term_code") String termCode,
    @Column("category") TermCategory category,
    @Column("display_name") String displayName,
    @Column("normalized_name") String normalizedName,
    @Column("version_no") String versionNo,
    @Column("status") StandardTermStatus status,
    @Column("source_version_id") Long sourceVersionId,
    @Column("evidence_text") String evidenceText,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
    public String statusName() {
        return status == null ? null : status.name();
    }
}
