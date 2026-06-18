package com.medkernel.engine.llm.provider;

import java.time.Instant;

/**
 * 模型 provider 治理脱敏快照。
 *
 * <p>仅返回凭据是否已配置，不包含凭据引用名或密钥内容，供受权运维人员读取当前配置与乐观锁版本。
 */
public record ModelProviderGovernanceView(
    String providerCode,
    String providerType,
    String endpointUri,
    boolean credentialConfigured,
    String modelVersion,
    boolean enabled,
    String status,
    Long version,
    Instant updatedAt,
    String updatedBy
) {
    static ModelProviderGovernanceView from(ModelProviderConfig config) {
        return new ModelProviderGovernanceView(
            config.providerCode(),
            config.providerType(),
            config.endpointUri(),
            config.credentialRef() != null && !config.credentialRef().isBlank(),
            config.modelVersion(),
            config.enabled(),
            config.status(),
            config.version(),
            config.updatedAt(),
            config.updatedBy());
    }
}
