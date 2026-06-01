package com.medkernel.engine.projection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投影重建与一致性校验服务。
 */
@Service
public class ProjectionSyncService {

    private final ClinicalGraphProjectionSource source;
    private final ProjectionSnapshotRepository snapshots;
    private final ProjectionSyncRepository syncs;

    public ProjectionSyncService(
            ClinicalGraphProjectionSource source,
            ProjectionSnapshotRepository snapshots,
            ProjectionSyncRepository syncs) {
        this.source = source;
        this.snapshots = snapshots;
        this.syncs = syncs;
    }

    @Transactional
    public ProjectionRebuildResponse rebuildClinicalGraph(String tenantId, String requestedBy, String traceId) {
        Instant startedAt = Instant.now();
        ProjectionSync running = syncs.save(ProjectionSync.running(
            tenantId,
            ProjectionTargetType.CLINICAL_GRAPH,
            requestedBy,
            traceId,
            startedAt));
        try {
            List<ProjectionFact> sourceFacts = source.factsForTenant(tenantId);
            Instant syncedAt = Instant.now();
            snapshots.deleteByTenantIdAndTargetType(tenantId, ProjectionTargetType.CLINICAL_GRAPH);
            List<ProjectionSnapshot> rows = sourceFacts.stream()
                .map(fact -> ProjectionSnapshot.fromFact(tenantId, fact, syncedAt, traceId))
                .toList();
            snapshots.saveAll(rows);
            List<ProjectionSnapshot> projectionRows = snapshots.findByTenantIdAndTargetType(
                tenantId,
                ProjectionTargetType.CLINICAL_GRAPH);
            String sourceHash = aggregateHashFromFacts(sourceFacts);
            String projectionHash = aggregateHashFromSnapshots(projectionRows);
            ProjectionSyncStatus status = sourceHash.equals(projectionHash)
                ? ProjectionSyncStatus.SUCCESS
                : ProjectionSyncStatus.FAILED;
            ProjectionSync finished = syncs.save(running.finish(
                status,
                sourceFacts.size(),
                projectionRows.size(),
                sourceHash,
                projectionHash,
                status == ProjectionSyncStatus.SUCCESS ? "投影重建完成" : "投影重建后校验不一致",
                Instant.now()));
            return new ProjectionRebuildResponse(
                finished.syncId(),
                ProjectionTargetType.CLINICAL_GRAPH,
                finished.status(),
                finished.sourceCount(),
                finished.projectionCount(),
                finished.sourceHash(),
                finished.projectionHash(),
                finished.traceId());
        } catch (RuntimeException exception) {
            syncs.save(running.finish(
                ProjectionSyncStatus.FAILED,
                0,
                0,
                null,
                null,
                "投影重建失败: " + exception.getMessage(),
                Instant.now()));
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public ProjectionConsistencyReport checkClinicalGraphConsistency(String tenantId) {
        List<ProjectionFact> sourceFacts = source.factsForTenant(tenantId);
        List<ProjectionSnapshot> projectionRows = snapshots.findByTenantIdAndTargetType(
            tenantId,
            ProjectionTargetType.CLINICAL_GRAPH);
        Map<String, ProjectionFact> sourceByKey = sourceByKey(sourceFacts);
        Map<String, ProjectionSnapshot> projectionByKey = projectionByKey(projectionRows);

        List<ProjectionDiffItem> missing = new ArrayList<>();
        List<ProjectionDiffItem> extra = new ArrayList<>();
        List<ProjectionDiffItem> changed = new ArrayList<>();

        sourceByKey.forEach((key, fact) -> {
            ProjectionSnapshot snapshot = projectionByKey.get(key);
            if (snapshot == null) {
                missing.add(new ProjectionDiffItem(key, fact.contentHash(), null));
            } else if (!fact.contentHash().equals(snapshot.contentHash())) {
                changed.add(new ProjectionDiffItem(key, fact.contentHash(), snapshot.contentHash()));
            }
        });
        projectionByKey.forEach((key, snapshot) -> {
            if (!sourceByKey.containsKey(key)) {
                extra.add(new ProjectionDiffItem(key, null, snapshot.contentHash()));
            }
        });

        sortDiffs(missing);
        sortDiffs(extra);
        sortDiffs(changed);
        String sourceHash = aggregateHashFromFacts(sourceFacts);
        String projectionHash = aggregateHashFromSnapshots(projectionRows);
        boolean consistent = missing.isEmpty()
            && extra.isEmpty()
            && changed.isEmpty()
            && sourceHash.equals(projectionHash);
        return new ProjectionConsistencyReport(
            ProjectionTargetType.CLINICAL_GRAPH,
            tenantId,
            consistent,
            sourceFacts.size(),
            projectionRows.size(),
            sourceHash,
            projectionHash,
            missing,
            extra,
            changed);
    }

    private Map<String, ProjectionFact> sourceByKey(List<ProjectionFact> facts) {
        Map<String, ProjectionFact> result = new HashMap<>();
        for (ProjectionFact fact : facts) {
            result.put(fact.factKey(), fact);
        }
        return result;
    }

    private Map<String, ProjectionSnapshot> projectionByKey(List<ProjectionSnapshot> rows) {
        Map<String, ProjectionSnapshot> result = new HashMap<>();
        for (ProjectionSnapshot row : rows) {
            result.put(row.factKey(), row);
        }
        return result;
    }

    private void sortDiffs(List<ProjectionDiffItem> items) {
        items.sort(Comparator.comparing(ProjectionDiffItem::factKey));
    }

    private String aggregateHashFromFacts(List<ProjectionFact> facts) {
        return aggregateHash(facts.stream()
            .map(fact -> fact.factKey() + "=" + fact.contentHash())
            .sorted()
            .toList());
    }

    private String aggregateHashFromSnapshots(List<ProjectionSnapshot> rows) {
        return aggregateHash(rows.stream()
            .map(row -> row.factKey() + "=" + row.contentHash())
            .sorted()
            .toList());
    }

    private String aggregateHash(List<String> lines) {
        return sha256(String.join("\n", lines));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256 摘要算法", exception);
        }
    }
}
