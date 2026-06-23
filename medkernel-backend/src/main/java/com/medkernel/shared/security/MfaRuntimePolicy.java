package com.medkernel.shared.security;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.medkernel.shared.config.SystemConfigItem;
import com.medkernel.shared.config.SystemConfigRepository;

/**
 * MFA 运行策略。
 *
 * <p>关系库配置优先，缺失或读取失败时回退启动配置；启动配置默认关闭。
 */
@Service
public class MfaRuntimePolicy {

    public static final String ENABLED_KEY = "medkernel.auth.mfa.enabled";
    private static final String SYSTEM_TENANT = "SYSTEM";

    private final SystemConfigRepository repository;
    private final AuthMfaProperties properties;

    public MfaRuntimePolicy(SystemConfigRepository repository, AuthMfaProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public boolean enabled() {
        try {
            SystemConfigItem item = repository.findActive(SYSTEM_TENANT, ENABLED_KEY).orElse(null);
            if (item == null || item.value() == null || item.value().isBlank()) {
                return properties.enabled();
            }
            return Boolean.parseBoolean(item.value().trim());
        } catch (DataAccessException ignored) {
            return properties.enabled();
        }
    }
}
