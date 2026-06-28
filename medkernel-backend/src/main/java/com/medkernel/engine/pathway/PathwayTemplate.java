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
    @Column("template_code") String templateCode,
    String name,
    @Column("disease_code") String diseaseCode,
    @Column("template_version") Integer templateVersion,
    @Column("template_level") PathwayTemplateLevel templateLevel,
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
    public PathwayTemplate withStatus(
            PathwayTemplateStatus nextStatus,
            Instant now,
            String actor,
            String nextTraceId) {
        return new PathwayTemplate(
            id,
            templateId,
            tenantId,
            templateCode,
            name,
            diseaseCode,
            templateVersion,
            templateLevel,
            nextStatus,
            entryMode,
            startNodeCode,
            sourceRef,
            description,
            entryCriteriaJson,
            exitCriteriaJson,
            createdAt,
            createdBy,
            now,
            actor,
            nextTraceId
        );
    }
}
