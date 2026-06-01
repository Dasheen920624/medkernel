package com.medkernel.engine.context;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.medkernel.shared.config.ClinicalEventWorkerSettings;

/**
 * 临床事件处理配置。
 */
@Component
@ConfigurationProperties(prefix = "medkernel.events")
public record ClinicalEventProperties(
    long maxPayloadSizeBytes,
    Duration syncTimeout,
    int workerBatchSize,
    int maxRetries,
    List<Long> backoffSeconds,
    long workerPollIntervalMs
) implements ClinicalEventWorkerSettings {

    public ClinicalEventProperties {
        if (maxPayloadSizeBytes <= 0) {
            maxPayloadSizeBytes = 1_048_576L;
        }
        if (syncTimeout == null) {
            syncTimeout = Duration.ofSeconds(3);
        }
        if (workerBatchSize <= 0) {
            workerBatchSize = 50;
        }
        if (maxRetries <= 0) {
            maxRetries = 5;
        }
        if (backoffSeconds == null || backoffSeconds.isEmpty()) {
            backoffSeconds = List.of(5L, 30L, 300L, 1800L);
        } else {
            backoffSeconds = List.copyOf(backoffSeconds);
        }
        if (workerPollIntervalMs <= 0) {
            workerPollIntervalMs = 200L;
        }
    }

    public ClinicalEventProperties(long maxPayloadSizeBytes,
                                   Duration syncTimeout,
                                   int workerBatchSize,
                                   int maxRetries,
                                   List<Long> backoffSeconds) {
        this(maxPayloadSizeBytes, syncTimeout, workerBatchSize, maxRetries, backoffSeconds, 200L);
    }

    public ClinicalEventProperties() {
        this(1_048_576L, Duration.ofSeconds(3), 50, 5, List.of(5L, 30L, 300L, 1800L), 200L);
    }
}
