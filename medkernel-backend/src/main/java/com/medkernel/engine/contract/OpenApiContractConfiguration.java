package com.medkernel.engine.contract;

import java.util.List;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SYS-02 OpenAPI 服务契约配置。
 *
 * <p>OpenAPI 暴露路径来自 {@link ServiceContractCatalog}，确保新增控制器必须先进入
 * 服务契约目录，再被统一文档化。
 */
@Configuration
public class OpenApiContractConfiguration {

    @Bean
    public GroupedOpenApi medkernelServiceContractsOpenApi() {
        List<String> paths = ServiceContractCatalog.openApiPaths();
        return GroupedOpenApi.builder()
            .group("medkernel-service-contracts")
            .pathsToMatch(paths.toArray(String[]::new))
            .build();
    }
}
