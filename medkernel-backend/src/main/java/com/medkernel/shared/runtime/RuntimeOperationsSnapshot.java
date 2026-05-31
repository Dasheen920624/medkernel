package com.medkernel.shared.runtime;

import java.time.Instant;
import java.util.List;

/**
 * 运行底座快照。
 *
 * <p>仅暴露上线判断所需状态，不返回数据库密码、JWT 密钥、Dify 密钥等敏感值。
 */
public record RuntimeOperationsSnapshot(
    String serviceName,
    String environment,
    String deploymentMode,
    String databaseDialect,
    String migrationLocation,
    List<String> activeProfiles,
    String healthStatus,
    RuntimeJvmMetadata jvm,
    RuntimeOsMetadata os,
    List<RuntimeFeatureFlag> featureFlags,
    List<RuntimeDependencyStatus> dependencies,
    RuntimeBackupReadiness backup,
    RuntimeDomesticProfile domesticProfile,
    Instant generatedAt
) {

    public record RuntimeJvmMetadata(
        String javaVersion,
        String javaVendor,
        String vmName,
        boolean virtualThreadsEnabled,
        int availableProcessors
    ) {
    }

    public record RuntimeOsMetadata(
        String name,
        String version,
        String arch
    ) {
    }

    public record RuntimeFeatureFlag(
        String key,
        String displayName,
        boolean enabled,
        String risk,
        String owner,
        String description,
        String source,
        String warning
    ) {
    }

    public record RuntimeDependencyStatus(
        String key,
        String displayName,
        String status,
        String detail
    ) {
    }

    public record RuntimeBackupReadiness(
        boolean enabled,
        String rpo,
        String rto,
        String backupScript,
        String restoreScript,
        String checksumPolicy,
        String source,
        String warning
    ) {
    }

    public record RuntimeDomesticProfile(
        String targetOs,
        String targetJdk,
        List<String> databaseVendors,
        List<String> cryptoAlgorithms,
        String evidence
    ) {
    }
}
