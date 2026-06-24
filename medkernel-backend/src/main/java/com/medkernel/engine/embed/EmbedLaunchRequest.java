package com.medkernel.engine.embed;

import jakarta.validation.constraints.NotBlank;

/**
 * 嵌入启动凭证兑换请求数据契约。
 */
public record EmbedLaunchRequest(
    @NotBlank String token,
    EmbedIntegrationMode integrationMode,
    String hook,
    String hookInstance
) {
    public EmbedLaunchRequest {
        integrationMode = EmbedIntegrationMode.defaultIfNull(integrationMode);
    }
}
