package com.medkernel.engine.knowledge.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * 医院完整医疗资源包上传、隔离和预检的外置安全边界。
 *
 * @param quarantineRoot 未信任包的受管隔离根目录
 * @param maxPackageBytes 单个真实上传文件最大字节数
 * @param maxEntries 容器最大条目数
 * @param maxEntryBytes 单个展开条目最大字节数
 * @param maxExpandedBytes 全部展开条目最大字节数
 * @param maxCompressionRatio 最大解压比；首发规范包仍只接受无压缩条目
 * @param supportedPackageFormatVersion 当前支持的容器格式版本
 * @param currentEngineVersion 当前院内引擎版本
 * @param currentDatabaseSchemaVersion 当前院内数据库模式版本
 */
@ConfigurationProperties(prefix = "medkernel.knowledge.package-import")
public record FullPackageImportProperties(
    String quarantineRoot,
    long maxPackageBytes,
    int maxEntries,
    long maxEntryBytes,
    long maxExpandedBytes,
    int maxCompressionRatio,
    String supportedPackageFormatVersion,
    String currentEngineVersion,
    String currentDatabaseSchemaVersion
) {

    private static final long MEBIBYTE = 1024L * 1024L;

    @ConstructorBinding
    public FullPackageImportProperties {
        quarantineRoot = defaultText(quarantineRoot, "./data/knowledge-package-quarantine");
        maxPackageBytes = positiveOrDefault(maxPackageBytes, 512L * MEBIBYTE);
        maxEntries = positiveOrDefault(maxEntries, 10_000);
        maxEntryBytes = positiveOrDefault(maxEntryBytes, 128L * MEBIBYTE);
        maxExpandedBytes = positiveOrDefault(maxExpandedBytes, 512L * MEBIBYTE);
        maxCompressionRatio = positiveOrDefault(maxCompressionRatio, 10);
        supportedPackageFormatVersion = defaultText(supportedPackageFormatVersion, "1.0");
        currentEngineVersion = defaultText(currentEngineVersion, "1.0.0");
        currentDatabaseSchemaVersion = defaultText(currentDatabaseSchemaVersion, "V1");
        if (maxEntryBytes > maxExpandedBytes) {
            throw new IllegalArgumentException("医疗资源包单条目上限不能大于总展开上限");
        }
    }

    public FullPackageImportProperties() {
        this(null, 0, 0, 0, 0, 0, null, null, null);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long positiveOrDefault(long value, long fallback) {
        return value <= 0 ? fallback : value;
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }
}
