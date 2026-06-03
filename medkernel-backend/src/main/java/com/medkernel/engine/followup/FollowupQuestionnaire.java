package com.medkernel.engine.followup;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 随访问卷实体。
 */
@Table("followup_questionnaire")
public record FollowupQuestionnaire(
    @Id Long id,
    @Column("questionnaire_id") String questionnaireId,
    @Column("tenant_id") String tenantId,
    @Column("plan_id") String planId,
    @Column("task_id") String taskId,
    @Column("questionnaire_template_id") String questionnaireTemplateId,
    @Column("form_data") String formData,
    @Column("answer_data") String answerData,
    BigDecimal score,
    String status,
    @Column("idempotency_key") String idempotencyKey,
    @Column("submitted_at") Instant submittedAt,
    @Column("executor_id") String executorId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public FollowupQuestionnaire(
            Long id,
            String questionnaireId,
            String tenantId,
            String taskId,
            String formData,
            BigDecimal score,
            String status,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, questionnaireId, tenantId, null, taskId, null, formData, null, score, status, null, null, null,
            createdAt, createdBy, updatedAt, updatedBy, traceId);
    }
}
