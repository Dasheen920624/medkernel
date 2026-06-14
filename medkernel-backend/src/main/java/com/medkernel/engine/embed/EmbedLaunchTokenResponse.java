package com.medkernel.engine.embed;

import java.time.Instant;

/**
 * 嵌入启动令牌生成响应数据契约 (GA-ENG-API-11)。
 */
public record EmbedLaunchTokenResponse(
    String token,
    Instant expiredAt,
    String embedUrl,
    EmbedIntegrationMode integrationMode,
    String launchEndpoint,
    String hook,
    String hookInstance
) {
    public EmbedLaunchTokenResponse(
            String token,
            Instant expiredAt,
            String embedUrl,
            EmbedIntegrationMode integrationMode,
            String launchEndpoint,
            String hook) {
        this(token, expiredAt, embedUrl, integrationMode, launchEndpoint, hook, null);
    }

    public EmbedLaunchTokenResponse(String token, Instant expiredAt, String embedUrl) {
        this(token, expiredAt, embedUrl, EmbedIntegrationMode.IFRAME, "/api/v1/engine/embed/launch", null, null);
    }
}
