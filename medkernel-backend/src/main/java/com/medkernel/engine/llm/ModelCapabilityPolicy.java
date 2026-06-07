package com.medkernel.engine.llm;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
  * 场景路由与脱敏策略配置实体。
  *
  * <p>定义指定租户针对指定模型能力的外部路由去向、数据脱敏级别以及输出结果 Schema 规则限制。
  */
@Table("model_capability_policy")
public record ModelCapabilityPolicy(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("route_strategy") String routeStrategy, // DISABLED, BASELINE, LOCAL_MODEL, EXTERNAL_MODEL
    @Column("desensitize_strategy") String desensitizeStrategy, // DEFAULT, MASK_ALL, NONE
    @Column("expected_schema") String expectedSchema,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
