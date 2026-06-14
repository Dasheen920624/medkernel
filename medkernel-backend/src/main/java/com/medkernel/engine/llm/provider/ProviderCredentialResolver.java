package com.medkernel.engine.llm.provider;

import java.util.Optional;

/**
 * provider 凭据解析器（LLM-08）。
 *
 * <p>{@code mk_llm_provider.credential_ref} 仅存密钥引用（环境变量名/密钥库条目名），
 * 由本解析器在调用时解析为真实密钥；密钥绝不落库、绝不进日志/审计/出域证据。
 */
public interface ProviderCredentialResolver {

    Optional<String> resolveSecret(String credentialRef);
}
