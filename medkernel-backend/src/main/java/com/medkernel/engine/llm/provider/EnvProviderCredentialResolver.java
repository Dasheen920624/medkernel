package com.medkernel.engine.llm.provider;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * 默认凭据解析器：从进程环境变量解析（LLM-08）。
 *
 * <p>{@code credential_ref} 即环境变量名（如 {@code MODEL_API_KEY}），密钥由部署环境/密钥库注入进程，
 * 数据库只存引用名，满足「凭证走密钥管理不落明文」。缺失返回空 → provider 不可用 → 诚实降级 B0。
 */
@Component
public class EnvProviderCredentialResolver implements ProviderCredentialResolver {

    @Override
    public Optional<String> resolveSecret(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Optional.empty();
        }
        String value = System.getenv(credentialRef.trim());
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
