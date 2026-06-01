package com.medkernel.shared.config;

/**
 * 高危配置变更的二次校验端口。
 */
public interface HighRiskChangeGuard {
    void assertHighRiskAllowed(String resourceType, String resourceId);
}
