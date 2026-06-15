package com.medkernel.engine.knowledge.parsing;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 文档解析 job（AIK-STD-02）。跟踪「源文件 → 解析 → 物化进受控来源」的生命周期。
 * 成功后 {@code resultSourceVersionId} 指向物化的 source_version；失败 {@code errorMessage} 诚实记原因，
 * 绝不在失败时产半真片段（FR-5）。
 */
@Table("mk_doc_parse_job")
public record DocParseJob(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("job_code") String jobCode,
    @Column("source_document_id") Long sourceDocumentId,
    @Column("source_file_name") String sourceFileName,
    @Column("document_format") DocumentFormat documentFormat,
    @Column("source_hash") String sourceHash,
    @Column("status") ParseJobStatus status,
    @Column("result_source_version_id") Long resultSourceVersionId,
    @Column("parsed_section_count") Integer parsedSectionCount,
    @Column("parsed_fragment_count") Integer parsedFragmentCount,
    @Column("error_message") String errorMessage,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
