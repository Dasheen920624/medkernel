package com.medkernel.shared.config;

/**
 * 第三方适配器周期健康探测运行配置视图。
 */
@FunctionalInterface
public interface IntegrationHealthProbeSettings {

    long healthProbeIntervalMs();
}
