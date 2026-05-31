package com.medkernel.shared.config;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.medkernel.shared.runtime.RuntimeProperties;

/**
 * 启动期把 YAML 默认配置导入关系库配置中心。
 */
@Component
public class SystemConfigSeeder implements ApplicationRunner {

    private final RuntimeProperties runtimeProperties;
    private final SystemConfigService service;

    public SystemConfigSeeder(RuntimeProperties runtimeProperties, SystemConfigService service) {
        this.runtimeProperties = runtimeProperties;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant seededAt = Instant.now();
        runtimeProperties.getFeatureFlags().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> {
                RuntimeProperties.FeatureFlag flag = entry.getValue();
                service.seed(new SystemConfigSeed(
                    SystemConfigService.SYSTEM_TENANT,
                    SystemConfigService.RUNTIME_FLAG_PREFIX + entry.getKey() + SystemConfigService.RUNTIME_FLAG_SUFFIX,
                    Boolean.toString(flag.isEnabled()),
                    "BOOLEAN",
                    flag.getDisplayName(),
                    flag.getRisk(),
                    flag.getOwner(),
                    flag.getDescription(),
                    "YML_SEED",
                    "HIGH".equalsIgnoreCase(flag.getRisk()),
                    seededAt), "system");
            });
    }
}
