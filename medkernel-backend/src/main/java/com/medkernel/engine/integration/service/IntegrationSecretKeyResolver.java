package com.medkernel.engine.integration.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 集成凭证主密钥解析器。
 *
 * <p>生产环境必须通过 {@code MEDKERNEL_INTEGRATION_SECRET_KEY} 提供独立主密钥；
 * 本地开发和测试环境使用固定测试密钥，禁止与 JWT 签名密钥复用。
 */
@Component
public class IntegrationSecretKeyResolver {

    private static final String ENV_KEY = "MEDKERNEL_INTEGRATION_SECRET_KEY";
    private static final String CONFIG_KEY = "medkernel.integration.secret-key";
    private static final String DEV_SECRET = "medkernel-integration-dev-secret-at-least-32-bytes";
    private static final int MIN_SECRET_LENGTH = 32;

    private final Environment environment;

    public IntegrationSecretKeyResolver(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateAtStartup() {
        resolve();
    }

    public String resolve() {
        String configured = normalize(environment.getProperty(ENV_KEY));
        if (configured == null) {
            configured = normalize(environment.getProperty(CONFIG_KEY));
        }
        if (configured != null) {
            return requireStrong(configured, ENV_KEY);
        }
        if (devOrTestProfile()) {
            return DEV_SECRET;
        }
        throw new IllegalStateException("生产环境必须显式配置 " + ENV_KEY + "，禁止明文存储集成凭证");
    }

    private String requireStrong(String secret, String source) {
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(source + " 长度至少 32 位");
        }
        return secret;
    }

    private boolean devOrTestProfile() {
        Set<String> profiles = new LinkedHashSet<>(Arrays.asList(environment.getActiveProfiles()));
        String configured = environment.getProperty("spring.profiles.active");
        if (configured != null) {
            Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(profiles::add);
        }
        return profiles.contains("dev") || profiles.contains("test");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
