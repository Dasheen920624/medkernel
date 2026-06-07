package com.medkernel.engine.security.auth;

/**
 * 平台凭证创建结果，临时密码仅在系统生成时一次性返回。
 */
public record CredentialCreationResult(String userId, String username, String tempPassword) {
}
