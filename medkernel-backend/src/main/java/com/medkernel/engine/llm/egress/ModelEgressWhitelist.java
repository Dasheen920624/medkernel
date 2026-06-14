package com.medkernel.engine.llm.egress;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 模型外调出域字段白名单实体（LLM-03 FR-1）。
 *
 * <p>声明指定租户、指定能力码在 B2 外调时允许出域的字段清单（JSON 字符串数组）与出域敏感级别。
 * 敏感级 {@code HIGH} 的出域须经审批方可放行（{@link ModelEgressApproval}）。
 */
@Table("mk_llm_egress_whitelist")
public record ModelEgressWhitelist(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("allowed_fields") String allowedFields,
    @Column("sensitivity_level") String sensitivityLevel, // LOW, MEDIUM, HIGH
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
