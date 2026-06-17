package com.medkernel.engine.knowledge.acquisition;

import java.net.URI;
import java.time.Instant;

/**
 * 已抓取公域资料原文字节。调用方负责先完成白名单和部署形态门禁。
 */
public record FetchedWebContent(
    URI effectiveUri,
    String contentType,
    byte[] bytes,
    Instant fetchedAt
) {
    public FetchedWebContent {
        bytes = bytes == null ? new byte[0] : bytes.clone();
        fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
