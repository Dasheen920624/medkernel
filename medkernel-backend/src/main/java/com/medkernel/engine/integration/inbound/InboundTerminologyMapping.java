package com.medkernel.engine.integration.inbound;

/**
 * 指定发布列车中解析出的不可变术语映射。
 */
public record InboundTerminologyMapping(
    Long mappingId,
    Long standardTermId,
    String standardCode,
    String versionNo
) {
}
