package com.medkernel.engine.recommendation;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * GA-ENG-API-07 医师反馈事实实体（只记录医师处理动作，不写病历或医嘱）。
 *
 * <p>反馈类型 {@link RecommendationFeedbackType} 决定推荐卡 {@link RecommendationCardStatus} 推进；
 * 记录操作者、操作角色、原因代码、原因说明、幂等键和 traceId；采纳/不采纳必须有结构化原因。
 */
@Table("recommendation_feedback")
public record RecommendationFeedback(
    @Id Long id,
    @Column("feedback_id") String feedbackId,
    @Column("tenant_id") String tenantId,
    @Column("card_id") String cardId,
    @Column("idempotency_key") String idempotencyKey,
    @Column("feedback_type") RecommendationFeedbackType feedbackType,
    @Column("reason_code") String reasonCode,
    @Column("reason_text") String reasonText,
    @Column("operator_id") String operatorId,
    @Column("operator_role") String operatorRole,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public RecommendationFeedback(
            Long id,
            String feedbackId,
            String tenantId,
            String cardId,
            RecommendationFeedbackType feedbackType,
            String reasonCode,
            String reasonText,
            String operatorId,
            String operatorRole,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, feedbackId, tenantId, cardId, null, feedbackType, reasonCode, reasonText,
            operatorId, operatorRole, createdAt, createdBy, updatedAt, updatedBy, traceId);
    }
}
