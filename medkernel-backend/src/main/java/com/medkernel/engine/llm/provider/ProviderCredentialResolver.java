package com.medkernel.engine.llm.provider;

import java.util.Optional;

/**
 * provider 凭据解析器（LLM-08）。
 *
 * <p>优先按租户与 Provider 编码读取加密凭据库，未登记时再解析
 * {@code mk_llm_provider.credential_ref} 环境变量引用；密钥绝不进日志、审计或出域证据。
 */
public interface ProviderCredentialResolver {

    Optional<String> resolveSecret(String tenantId, String providerCode, String credentialRef);
}
