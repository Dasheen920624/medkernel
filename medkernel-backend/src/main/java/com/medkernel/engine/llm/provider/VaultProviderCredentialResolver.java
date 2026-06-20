package com.medkernel.engine.llm.provider;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * 租户模型凭据解析器。
 *
 * <p>只读取当前租户的加密凭据库；未登记时返回未配置，加密库损坏时诚实失败，
 * 不得读取进程环境变量或其他凭据来源。
 */
@Component
public class VaultProviderCredentialResolver implements ProviderCredentialResolver {

    private final ModelProviderCredentialRepository repository;
    private final ProviderCredentialCodec codec;

    public VaultProviderCredentialResolver(
            ModelProviderCredentialRepository repository,
            ProviderCredentialCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Override
    public Optional<String> resolveSecret(String tenantId, String providerCode) {
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
        return Optional.empty();
    }
}
