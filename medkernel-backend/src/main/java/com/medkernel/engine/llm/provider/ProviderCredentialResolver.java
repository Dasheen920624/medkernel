package com.medkernel.engine.llm.provider;

import java.util.Optional;

/**
 * 模型服务密钥解析器（LLM-08）。
 *
 * <p>只按机构与模型服务编码读取加密凭据库；密钥绝不进日志、审计或外调证据。
 */
public interface ProviderCredentialResolver {

    Optional<String> resolveSecret(String tenantId, String providerCode);
}
