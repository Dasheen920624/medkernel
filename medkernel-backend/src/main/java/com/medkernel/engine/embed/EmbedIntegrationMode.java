package com.medkernel.engine.embed;

/**
 * 嵌入集成方式。
 */
public enum EmbedIntegrationMode {
    IFRAME,
    SDK,
    API;

    public static EmbedIntegrationMode defaultIfNull(EmbedIntegrationMode mode) {
        return mode == null ? IFRAME : mode;
    }
}
