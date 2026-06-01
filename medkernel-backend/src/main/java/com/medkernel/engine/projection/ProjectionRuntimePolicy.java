package com.medkernel.engine.projection;

import org.springframework.stereotype.Component;

import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.runtime.RuntimeProperties;

/**
 * 投影运行策略读取器，统一从配置中心运行开关判断图投影与 Dify 执行器状态。
 */
@Component
public class ProjectionRuntimePolicy {

    static final String GRAPH_PROJECTION = "graph-projection";
    static final String DIFY_WORKFLOW = "dify-workflow";

    private final RuntimeProperties properties;
    private final SystemConfigService configService;

    public ProjectionRuntimePolicy(RuntimeProperties properties, SystemConfigService configService) {
        this.properties = properties;
        this.configService = configService;
    }

    public boolean graphProjectionEnabled() {
        return configService.runtimeFeatureFlagEnabled(properties, GRAPH_PROJECTION);
    }

    public boolean difyWorkflowEnabled() {
        return configService.runtimeFeatureFlagEnabled(properties, DIFY_WORKFLOW);
    }
}
