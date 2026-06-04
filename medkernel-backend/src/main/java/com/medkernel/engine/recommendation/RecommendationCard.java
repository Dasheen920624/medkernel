package com.medkernel.engine.recommendation;

import java.time.Instant;

import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * GA-ENG-API-07 推荐卡片实体（CDSS 辅助建议与风险提醒的载体）。
 *
 * <p>状态机由 {@link RecommendationCardStatus} 定义；
 * 高风险/红线卡必须 {@code requiresPhysicianConfirmation=true}，
 * 强打断卡必须高风险，由 {@link RecommendationEngineService#trigger} 校验。
 * 业务键 card_id 在租户内唯一；关联触发 {@link RecommendationTrigger}，
 * 来源 {@link RecommendationSource} 与反馈 {@link RecommendationFeedback} 通过 card_id 反查。
 * AI 候选必须 {@code aiGenerated=true}，不能伪装为人工规则结论。
 */
@Table("recommendation_card")
public record RecommendationCard(
    @Id Long id,
    @Column("card_id") String cardId,
    @Column("tenant_id") String tenantId,
    @Column("trigger_id") String triggerId,
    @Column("card_code") String cardCode,
    @Column("card_type") RecommendationCardType cardType,
    String title,
    String summary,
    @Column("suggested_action") String suggestedAction,
    @Column("risk_level") RecommendationRiskLevel riskLevel,
    @Column("interrupt_level") RecommendationInterruptLevel interruptLevel,
    RecommendationCardStatus status,
    @Column("requires_physician_confirmation") boolean requiresPhysicianConfirmation,
    @Column("ai_generated") boolean aiGenerated,
    @Column("source_summary") String sourceSummary,
    @Column("explanation_json") String explanationJson,
    @Column("fatigue_key") String fatigueKey,
    @Column("expires_at") Instant expiresAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId,
    @Column("risk_matrix_id") String riskMatrixId,
    @Column("risk_matrix_version") String riskMatrixVersion,
    @Column("automation_level") CdssAutomationLevel automationLevel,
    @Column("review_requirement") CdssReviewRequirement reviewRequirement,
    @Column("silent_run_hours") int silentRunHours,
    @Column("release_gate") String releaseGate,
    @Column("auto_execution_allowed") boolean autoExecutionAllowed,
    @Column("samd_classification") String samdClassification,
    @Column("regulatory_evidence") String regulatoryEvidence,
    @Column("risk_matrix_explanation") String riskMatrixExplanation
) {
    public RecommendationCard {
        automationLevel = automationLevel == null ? CdssAutomationLevel.INFORM_ONLY : automationLevel;
        reviewRequirement = reviewRequirement == null ? CdssReviewRequirement.OPTIONAL_REVIEW : reviewRequirement;
        releaseGate = hasText(releaseGate) ? releaseGate : "STANDARD_CHANGE_REVIEW";
        samdClassification = hasText(samdClassification) ? samdClassification : "NMPA_RESERVED";
        regulatoryEvidence = hasText(regulatoryEvidence) ? regulatoryEvidence : "NOT_ASSESSED";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
