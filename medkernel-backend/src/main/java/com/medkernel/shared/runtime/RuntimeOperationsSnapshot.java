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
    RuntimeDomesticCompatibility domesticCompatibility,
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
        RuntimeBackupDrillEvidence drillEvidence,
        String source,
        String warning
    ) {
    }

    public record RuntimeBackupDrillEvidence(
        String status,
        Instant completedAt,
        Integer migrationCount,
        String evidenceReference,
        String checksumEvidence,
        Boolean drillDatabaseIsIsolated,
        String rpo,
        String rto,
        String detail
    ) {

        public static RuntimeBackupDrillEvidence notAvailable() {
            return new RuntimeBackupDrillEvidence(
                "NOT_AVAILABLE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "尚未提供隔离恢复演练证据"
            );
        }

        public static RuntimeBackupDrillEvidence invalid() {
            return new RuntimeBackupDrillEvidence(
                "INVALID",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "隔离恢复演练证据格式无效"
            );
        }
    }

    public record RuntimeDomesticProfile(
        String targetOs,
        String targetJdk,
        List<String> databaseVendors,
        List<String> cryptoAlgorithms,
        String evidence
    ) {
    }

    public record RuntimeDomesticCompatibility(
        String overallStatus,
        String summary,
        List<RuntimeDomesticCheckItem> items,
        Instant checkedAt
    ) {
    }

    public record RuntimeDomesticCheckItem(
        String key,
        String category,
        String displayName,
        String status,
        String actualValue,
        String expectedValue,
        String reason,
        String recommendation,
        String evidence
    ) {
    }
}
