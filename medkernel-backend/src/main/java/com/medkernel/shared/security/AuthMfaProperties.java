package com.medkernel.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MFA 启动配置。
 *
 * <p>默认关闭；需要双因素登录时由环境变量或配置中心显式开启。
 */
@ConfigurationProperties(prefix = "medkernel.auth.mfa")
public record AuthMfaProperties(boolean enabled) {
}
