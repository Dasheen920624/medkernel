package com.medkernel.engine.knowledge.acquisition;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * AIK-STD-14 公域资料获取运行账本。记录真实 URL、抓取时点、原文指纹、资料 URI 和解析 job。
 */
@Table("mk_knowledge_acquisition_run")
public record KnowledgeAcquisitionRun(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("run_code") String runCode,
    @Column("source_id") Long sourceId,
    @Column("source_code") String sourceCode,
    @Column("url") String url,
    @Column("domain") String domain,
    @Column("trigger_type") AcquisitionTriggerType triggerType,
    @Column("status") KnowledgeAcquisitionRunStatus status,
    @Column("fetched_at") Instant fetchedAt,
    @Column("source_hash") String sourceHash,
    @Column("byte_size") Long byteSize,
    @Column("content_type") String contentType,
    @Column("license") String license,
    @Column("license_policy") AcquisitionLicensePolicy licensePolicy,
    @Column("robots_policy") AcquisitionRobotsPolicy robotsPolicy,
    @Column("material_file_uri") String materialFileUri,
    @Column("source_document_id") Long sourceDocumentId,
    @Column("source_version_id") Long sourceVersionId,
    @Column("parse_job_code") String parseJobCode,
    @Column("failure_reason") String failureReason,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
