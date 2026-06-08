package com.medkernel.engine.authoring;

import org.springframework.stereotype.Component;

import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.runtime.RuntimeProperties;

/**
 * 基于运行配置中心的创作增强能力开关。
 */
@Component
public class RuntimeAuthoringFeatureGate implements AuthoringFeatureGate {

    private final RuntimeProperties runtimeProperties;
    private final SystemConfigService systemConfigService;

    public RuntimeAuthoringFeatureGate(RuntimeProperties runtimeProperties, SystemConfigService systemConfigService) {
        this.runtimeProperties = runtimeProperties;
        this.systemConfigService = systemConfigService;
    }

    @Override
    public boolean enabled(AuthoringFeatureFlag flag) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return systemConfigService.runtimeFeatureFlagEnabledForTenant(runtimeProperties, flag.key(), tenantId);
    }
}
