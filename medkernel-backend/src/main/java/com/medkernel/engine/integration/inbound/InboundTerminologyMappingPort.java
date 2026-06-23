package com.medkernel.engine.integration.inbound;

/**
 * 集成接入域按医院运行修订锁定的精确术语版本解析映射的窄端口。
 */
public interface InboundTerminologyMappingPort {

    InboundTerminologyMapping resolve(
        String tenantId,
        String runtimeReleaseId,
        String sourceSystem,
        String localCode,
        String targetDictionaryKey,
        String category
    );
}
