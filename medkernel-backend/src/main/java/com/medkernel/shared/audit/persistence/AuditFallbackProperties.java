package com.medkernel.shared.audit.persistence;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计降级文件启动兜底配置；运行时路径以配置中心为准。
 */
@ConfigurationProperties(prefix = "medkernel.audit.fallback")
public record AuditFallbackProperties(
    String path
) {

    public String pathOrDefault() {
        if (path != null && !path.isBlank()) {
            return path.trim();
        }
        return defaultPath();
    }

    public static String defaultPath() {
        return Path.of(System.getProperty("user.dir"), "var", "audit-fallback", "audit-fallback.jsonl")
            .toAbsolutePath()
            .normalize()
            .toString();
    }
}
