package com.medkernel.engine.pathway;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 路径模板主数据。
 *
 * <p>保存专病路径模板的编码、病种、版本、层级、状态、起始节点、来源和准入/退出条件摘要。
 */
@Table("pathway_template")
public record PathwayTemplate(
    @Id Long id,
    @Column("template_id") String templateId,
    @Column("tenant_id") String tenantId,
    @Column("package_id") String packageId,
    @Column("template_code") String templateCode,
    String name,
    @Column("disease_code") String diseaseCode,
    @Column("template_version") Integer templateVersion,
    @Column("template_level") PathwayTemplateLevel templateLevel,
    @Column("parent_template_id") String parentTemplateId,
    PathwayTemplateStatus status,
    @Column("entry_mode") PathwayEntryMode entryMode,
    @Column("start_node_code") String startNodeCode,
    @Column("source_ref") String sourceRef,
    String description,
    @Column("entry_criteria_json") String entryCriteriaJson,
    @Column("exit_criteria_json") String exitCriteriaJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public PathwayTemplate(
            Long id,
            String templateId,
            String tenantId,
            String packageId,
            String templateCode,
            String name,
            String diseaseCode,
            Integer templateVersion,
            PathwayTemplateLevel templateLevel,
            PathwayTemplateStatus status,
            PathwayEntryMode entryMode,
            String startNodeCode,
            String sourceRef,
            String description,
            String entryCriteriaJson,
            String exitCriteriaJson,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, templateId, tenantId, packageId, templateCode, name, diseaseCode, templateVersion,
            templateLevel, null, status, entryMode, startNodeCode, sourceRef, description,
            entryCriteriaJson, exitCriteriaJson, createdAt, createdBy, updatedAt, updatedBy, traceId);
    }
}
