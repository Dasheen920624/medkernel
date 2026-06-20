package com.medkernel.engine.llm.provider;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * 租户模型凭据解析器。
 *
 * <p>优先读取当前租户的加密凭据库；未登记时才兼容既有环境变量引用。加密库存在但损坏时必须诚实失败，
 * 不得静默回退到另一份凭据。
 */
@Component
public class VaultProviderCredentialResolver implements ProviderCredentialResolver {

    private final ModelProviderCredentialRepository repository;
    private final ProviderCredentialCodec codec;
    private final EnvProviderCredentialResolver environment;

    public VaultProviderCredentialResolver(
            ModelProviderCredentialRepository repository,
            ProviderCredentialCodec codec,
            EnvProviderCredentialResolver environment) {
        this.repository = repository;
        this.codec = codec;
        this.environment = environment;
    }

    @Override
    public Optional<String> resolveSecret(
            String tenantId,
            String providerCode,
            String credentialRef) {
        if (tenantId != null && !tenantId.isBlank()
                && providerCode != null && !providerCode.isBlank()) {
            Optional<ModelProviderCredential> stored = repository.findByTenantIdAndProviderCode(
                tenantId.trim(),
                providerCode.trim()
            );
            if (stored.isPresent()) {
                return Optional.of(codec.decode(stored.get().credentialCiphertext()));
            }
        }
        return environment.resolveSecret(credentialRef);
    }
}
