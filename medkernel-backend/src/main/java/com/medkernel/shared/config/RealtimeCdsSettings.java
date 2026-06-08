package com.medkernel.shared.config;

import java.time.Duration;

/**
 * 实时 CDS 同步求值预算配置。
 */
public interface RealtimeCdsSettings {
    Duration defaultTimeout();

    Duration orderSignTimeout();
}
