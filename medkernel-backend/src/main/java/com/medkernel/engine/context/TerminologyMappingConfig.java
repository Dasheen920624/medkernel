package com.medkernel.engine.context;

import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 字典映射端口装配。
 *
 * <p>当 terminology 模块尚未提供 {@link TerminologyMappingPort} 实现时，
 * 注入一个 noop 默认 bean：所有锚点返回 {@code UNKNOWN}，
 * 不阻断 snapshot 创建，便于 API-01 独立验收。
 */
@Configuration
public class TerminologyMappingConfig {

    @Bean
    @ConditionalOnMissingBean(TerminologyMappingPort.class)
    public TerminologyMappingPort noopTerminologyMappingPort() {
        return (tenantId, anchors) -> anchors.stream()
            .collect(Collectors.toMap(
                ClinicalCodeMappingAnchor::key,
                anchor -> "UNKNOWN",
                (existing, duplicate) -> existing));
    }
}
