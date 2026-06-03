package com.medkernel.engine.embed;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 嵌入启动令牌实体。
 *
 * <p>表示为集成系统进入 MedKernel 嵌入组件时生成的一次性、短失效启动令牌。
 */
@Table("embed_launch_token")
public record EmbedLaunchToken(
    @Id Long id,
    String token,
    @Column("tenant_id") String tenantId,
    @Column("user_id") String userId,
    @Column("role_code") String roleCode,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("trigger_point") String triggerPoint,
    String status,
    @Column("expired_at") Instant expiredAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId,
    @Column("integration_mode") String integrationMode,
    String hook,
    @Column("hook_instance") String hookInstance,
    @Column("consumed_at") Instant consumedAt
) {
    public EmbedLaunchToken(
            Long id,
            String token,
            String tenantId,
            String userId,
            String roleCode,
            String patientId,
            String encounterId,
            String triggerPoint,
            String status,
            Instant expiredAt,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(
            id, token, tenantId, userId, roleCode, patientId, encounterId, triggerPoint, status,
            expiredAt, createdAt, createdBy, updatedAt, updatedBy, traceId,
            EmbedIntegrationMode.IFRAME.name(), null, null, null);
    }
}
