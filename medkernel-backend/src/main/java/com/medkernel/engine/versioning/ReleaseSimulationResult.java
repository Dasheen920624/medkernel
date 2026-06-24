package com.medkernel.engine.versioning;

import java.time.Instant;
import java.util.List;

/**
 * 发布前影响评估结果。
 */
public record ReleaseSimulationResult(
    String simulationDigest,
    Instant generatedAt,
    String candidateVersionId,
    String currentVersionId,
    List<AffectedOrganization> affectedOrganizations,
    List<String> applicableDimensions,
    Diff diff,
    Replay replay,
    Check safety,
    Check dependencies,
    List<Conflict> conflicts,
    boolean releasable
) {
    public ReleaseSimulationResult {
        affectedOrganizations = affectedOrganizations == null ? List.of() : List.copyOf(affectedOrganizations);
        applicableDimensions = applicableDimensions == null ? List.of() : List.copyOf(applicableDimensions);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public record AffectedOrganization(String orgUnitId, String orgPath, String orgName) {
    }

    public record Diff(
        String changeType,
        String currentVersionNo,
        String candidateVersionNo,
        String currentContentHash,
        String candidateContentHash
    ) {
    }

    public record Replay(
        String status,
        long sampledCases,
        long changedCases,
        long triggerIncreases,
        long triggerDecreases,
        long severityIncreases,
        long severityDecreases,
        List<String> highRiskSnapshotIds,
        List<ImpactedAsset> impactedAssets,
        String reason
    ) {
        public Replay {
            highRiskSnapshotIds = highRiskSnapshotIds == null ? List.of() : List.copyOf(highRiskSnapshotIds);
            impactedAssets = impactedAssets == null ? List.of() : List.copyOf(impactedAssets);
        }

        public static Replay unsupported(String reason) {
            return new Replay("UNSUPPORTED", 0, 0, 0, 0, 0, 0, List.of(), List.of(), reason);
        }

        public static Replay noData(String reason) {
            return new Replay("NO_DATA", 0, 0, 0, 0, 0, 0, List.of(), List.of(), reason);
        }
    }

    public record ImpactedAsset(
        VersionedAssetType assetType,
        String assetIdentity,
        String versionId,
        String versionNo
    ) {
    }

    public record Check(boolean passed, List<String> issues) {
        public Check {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public record Conflict(
        String overrideId,
        String orgPath,
        String overrideMode,
        String resultingSource
    ) {
    }
}
