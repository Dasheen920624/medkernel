package com.medkernel.shared.security;

/**
 * 当前平台凭证的首登安全状态提供者，供控制器入口守卫判定是否必须先改密。
 */
public interface CredentialBootstrapStatusProvider {

    boolean mustChangePassword(String tenantId, String userId);
}
