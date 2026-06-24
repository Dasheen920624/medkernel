package com.medkernel.engine.llm.provider;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 模型服务接入配置实体（LLM-08）。
 *
 * <p>登记机构可用的真实模型服务：类型（B1 本地 OLLAMA / B2 外部 OPENAI_COMPATIBLE·CLAUDE / DIFY）、
 * 调用地址、服务的模型版本、启停与健康状态。凭据由机构加密凭据库独立维护。
 */
@Table("mk_llm_provider")
public record ModelProviderConfig(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("provider_code") String providerCode,
    @Column("provider_type") String providerType, // OLLAMA, OPENAI_COMPATIBLE, CLAUDE, DIFY
    @Column("endpoint_uri") String endpointUri,
    @Column("model_version") String modelVersion,
    @Column("enabled_flag") String enabledFlag,
    @Column("status") String status, // NOT_CONNECTED, HEALTHY, UNHEALTHY
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Version @Column("lock_version") Long version
) {
    public boolean enabled() {
        return "Y".equalsIgnoreCase(enabledFlag);
    }
}
