package com.medkernel.shared.config;

import java.time.Duration;

/**
 * 配置中心读取临床事件 worker 启动默认值的最小契约。
 */
public interface ClinicalEventWorkerSettings {
    default Duration syncTimeout() {
        return Duration.ofSeconds(3);
    }

    long workerPollIntervalMs();
}
