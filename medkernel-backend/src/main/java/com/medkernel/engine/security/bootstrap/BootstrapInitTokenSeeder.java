package com.medkernel.engine.security.bootstrap;

import java.time.Duration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 首发部署启动种子：仅登记显式提供的一次性 init token，不创建账号、不输出明文 token。
 */
@Component
public class BootstrapInitTokenSeeder implements ApplicationRunner {

    private static final String ACTOR = "bootstrap-seeder";
    private static final String TRACE_ID = "startup";
    private static final long DEFAULT_TTL_MINUTES = 60;

    private final Environment environment;
    private final BootstrapInitTokenService tokenService;

    public BootstrapInitTokenSeeder(Environment environment, BootstrapInitTokenService tokenService) {
        this.environment = environment;
        this.tokenService = tokenService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String rawToken = firstNonBlank(
            environment.getProperty("MEDKERNEL_BOOTSTRAP_INIT_TOKEN"),
            environment.getProperty("medkernel.bootstrap.init-token"));
        if (rawToken == null) {
            return;
        }
        tokenService.registerDeploymentToken(rawToken, ttl(), ACTOR, TRACE_ID);
    }

    private Duration ttl() {
        String value = firstNonBlank(
            environment.getProperty("MEDKERNEL_BOOTSTRAP_INIT_TOKEN_TTL_MINUTES"),
            environment.getProperty("medkernel.bootstrap.init-token-ttl-minutes"));
        if (value == null) {
            return Duration.ofMinutes(DEFAULT_TTL_MINUTES);
        }
        long minutes = Long.parseLong(value);
        if (minutes <= 0) {
            throw new IllegalArgumentException("初始化 token 有效期必须大于 0 分钟");
        }
        return Duration.ofMinutes(minutes);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
