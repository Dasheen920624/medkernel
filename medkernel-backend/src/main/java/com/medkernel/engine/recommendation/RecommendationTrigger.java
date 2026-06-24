package com.medkernel.engine.recommendation;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * GA-ENG-API-07 推荐触发事实（一次 CDSS 运行入口）。
 *
 * <p>触发源可来自临床事件、标准上下文、规则执行、路径推进、知识查询或外部嵌入场景；
 * 状态由 {@link RecommendationTriggerStatus} 控制（RECEIVED→EVALUATED/NO_CARD/FAILED）。
 * 业务键 trigger_id 在租户内唯一，与 {@link RecommendationCard} 通过 trigger_id 关联。
 * 首版允许上游把候选卡随触发请求一并提交，综合生成逻辑由 {@code GA-ENG-CDSS-01} 承担。
 */
@Table("recommendation_trigger")
public record RecommendationTrigger(
    @Id Long id,
    @Column("trigger_id") String triggerId,
    @Column("tenant_id") String tenantId,
    @Column("trigger_code") String triggerCode,
    @Column("trigger_type") String triggerType,
    @Column("source_event_id") String sourceEventId,
    @Column("context_snapshot_id") String contextSnapshotId,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("patient_pathway_id") String patientPathwayId,
    @Column("scenario_code") String scenarioCode,
    @Column("runtime_release_id") String runtimeReleaseId,
    @Column("input_digest") String inputDigest,
    RecommendationTriggerStatus status,
    @Column("error_code") String errorCode,
    @Column("occurred_at") Instant occurredAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
