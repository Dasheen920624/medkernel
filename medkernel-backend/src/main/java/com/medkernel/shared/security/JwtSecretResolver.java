package com.medkernel.shared.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * JWT 密钥解析器：生产必须显式提供部署密钥，dev/test 才允许使用本地默认密钥。
 */
@Component
public class JwtSecretResolver {

    private static final String DEV_SECRET_KEY = "medkernel.jwt.dev-secret";
    private static final String DEV_SECRET_DEFAULT = "medkernel-dev-secret-please-change-at-least-32-bytes";
    private static final int MIN_SECRET_LENGTH = 32;

    private final AuthJwtProperties properties;
    private final Environment environment;

    public JwtSecretResolver(AuthJwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public String resolve() {
        String configured = normalize(properties.secret());
        if (configured != null) {
            return requireStrong(configured, "MEDKERNEL_AUTH_JWT_SECRET");
        }
        if (devOrTestProfile()) {
            return requireStrong(environment.getProperty(DEV_SECRET_KEY, DEV_SECRET_DEFAULT), DEV_SECRET_KEY);
        }
        throw new IllegalStateException("生产环境必须显式配置 MEDKERNEL_AUTH_JWT_SECRET，禁止使用 dev 默认 JWT 密钥");
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
                .filter(s -> !s.isBlank())
                .forEach(profiles::add);
        }
        return profiles.contains("dev") || profiles.contains("test");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
