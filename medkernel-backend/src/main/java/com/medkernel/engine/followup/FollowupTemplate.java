package com.medkernel.engine.followup;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 随访方案不可变版本。
 *
 * <p>方案只保存任务、问卷和异常处置定义，不保存患者、就诊、作答等运行数据。
 */
@Table("mk_followup_template")
public record FollowupTemplate(
    @Id Long id,
    @Column("template_id") String templateId,
    @Column("tenant_id") String tenantId,
    @Column("template_code") String templateCode,
    @Column("version_no") Integer versionNo,
    String name,
    String description,
    @Column("organization_scope") String organizationScope,
    @Column("applicable_scope") String applicableScope,
    @Column("task_definition_json") String taskDefinitionJson,
    @Column("questionnaire_definition_json") String questionnaireDefinitionJson,
    @Column("abnormal_action_json") String abnormalActionJson,
    @Column("source_ref") String sourceRef,
    @Column("asset_version_id") String assetVersionId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
