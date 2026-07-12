package com.medkernel.engine.knowledge.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** 完整医疗资源包由服务端固定的兼容范围，不接受客户端自报。 */
@ConfigurationProperties(prefix = "medkernel.knowledge.package-export")
public record FullPackageExportProperties(
    String packageFormatVersion,
    String minimumEngineVersion,
    String maximumEngineVersion,
    String minimumDatabaseSchemaVersion,
    String maximumDatabaseSchemaVersion
) {

    @ConstructorBinding
    public FullPackageExportProperties {
        packageFormatVersion = defaultValue(packageFormatVersion, "1.0");
        minimumEngineVersion = defaultValue(minimumEngineVersion, "1.0.0");
        maximumEngineVersion = defaultValue(maximumEngineVersion, "1.x");
        minimumDatabaseSchemaVersion = defaultValue(minimumDatabaseSchemaVersion, "V1");
        maximumDatabaseSchemaVersion = defaultValue(maximumDatabaseSchemaVersion, "V1");
    }

    public FullPackageExportProperties() {
        this("1.0", "1.0.0", "1.x", "V1", "V1");
    }

    public FullPackageManifest.Compatibility compatibility() {
        return new FullPackageManifest.Compatibility(
            packageFormatVersion,
            minimumEngineVersion,
            maximumEngineVersion,
            minimumDatabaseSchemaVersion,
            maximumDatabaseSchemaVersion);
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
