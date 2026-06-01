package com.medkernel.shared.config;

/**
 * 配置中心读取临床事件 worker 启动默认值的最小契约。
 */
public interface ClinicalEventWorkerSettings {
    long workerPollIntervalMs();
}
