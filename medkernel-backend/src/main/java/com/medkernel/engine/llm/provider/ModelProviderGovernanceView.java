package com.medkernel.engine.llm.provider;

import java.time.Instant;

/**
 * 模型服务治理脱敏快照。
 *
 * <p>仅返回凭据来源、尾四位和轮换审计，不包含凭据引用名、密文、完整指纹或密钥内容。
 */
public record ModelProviderGovernanceView(
    String providerCode,
    String providerType,
    String endpointUri,
    boolean credentialConfigured,
    String credentialSource,
    String credentialLast4,
    Long credentialVersion,
    Instant credentialUpdatedAt,
    String credentialUpdatedBy,
    String modelVersion,
    boolean enabled,
    String status,
    Long version,
    Instant updatedAt,
    String updatedBy
) {
    static ModelProviderGovernanceView from(ModelProviderConfig config) {
        return from(config, null);
    }

    static ModelProviderGovernanceView from(
            ModelProviderConfig config,
            ModelProviderCredential credential) {
        boolean vaultConfigured = credential != null;
        return new ModelProviderGovernanceView(
            config.providerCode(),
            config.providerType(),
            config.endpointUri(),
            vaultConfigured,
            vaultConfigured ? "VAULT" : "NONE",
            vaultConfigured ? credential.credentialLast4() : null,
            vaultConfigured ? credential.version() : null,
            vaultConfigured ? credential.updatedAt() : null,
            vaultConfigured ? credential.updatedBy() : null,
            config.modelVersion(),
            config.enabled(),
            config.status(),
            config.version(),
            config.updatedAt(),
            config.updatedBy());
    }
}
