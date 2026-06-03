package com.medkernel.engine.integration.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 第三方对接总线出站异步投递请求；主流程只登记补偿，不等待外部系统完成。
 */
public record IntegrationOutboundRequestDto(
    @NotBlank @Size(max = 64) String messageId,
    @Size(max = 128) String traceId,
    @NotBlank @Size(max = 64) String adapterId,
    @NotBlank @Size(max = 128) String targetSystem,
    @NotBlank @Size(max = 32) String protocolType,
    @Size(max = 512) String payloadSummary,
    @NotNull JsonNode payload,
    @Min(1) @Max(10) Integer maxRetries
) {
    public IntegrationOutboundRequestDto {
        maxRetries = maxRetries == null ? 3 : maxRetries;
    }
}
