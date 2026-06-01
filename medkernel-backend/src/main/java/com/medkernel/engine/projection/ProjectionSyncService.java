package com.medkernel.engine.projection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.clinical.model.ClinicalProjectionStatus;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;

/**
 * 投影重建与一致性校验服务。
 */
@Service
public class ProjectionSyncService {

    private final ClinicalGraphProjectionSource source;
    private final ProjectionSnapshotRepository snapshots;
    private final ProjectionSyncRepository syncs;
    private final ProjectionRuntimePolicy policy;
    private final ProjectionExecutionPort executor;
    private final AuditRecorder auditRecorder;

    public ProjectionSyncService(
            ClinicalGraphProjectionSource source,
            ProjectionSnapshotRepository snapshots,
            ProjectionSyncRepository syncs,
            ProjectionRuntimePolicy policy,
            ProjectionExecutionPort executor,
            AuditRecorder auditRecorder) {
        this.source = source;
        this.snapshots = snapshots;
        this.syncs = syncs;
        this.policy = policy;
        this.executor = executor;
        this.auditRecorder = auditRecorder;
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
            if (!policy.graphProjectionEnabled()) {
                ProjectionSync finished = syncs.save(running.finish(
                    ProjectionSyncStatus.NOT_SYNCED,
                    0,
                    0,
                    null,
                    null,
                    "graph-projection Feature Flag 关闭，未执行图投影重建",
                    Instant.now()));
                recordSyncAudit(finished, ProjectionSyncStatus.NOT_SYNCED);
                return responseFrom(
                    finished,
                    ProjectionSyncStatus.NOT_SYNCED,
                    "graph-projection Feature Flag 关闭，关系库权威主链路保持可用");
            }

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
            ProjectionExecutionResult difyResult = externalExecutionResult(
                tenantId,
                finished,
                sourceFacts.size(),
                sourceHash,
                traceId);
            recordSyncAudit(finished, difyResult.status());
            return responseFrom(finished, difyResult.status(), message(finished.message(), difyResult.message()));
        } catch (RuntimeException exception) {
            ProjectionSync failed = syncs.save(running.finish(
                ProjectionSyncStatus.FAILED,
                0,
                0,
                null,
                null,
                "投影重建失败: " + exception.getMessage(),
                Instant.now()));
            recordSyncAudit(failed, ProjectionSyncStatus.NOT_SYNCED);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public ProjectionConsistencyReport checkClinicalGraphConsistency(String tenantId) {
        if (!policy.graphProjectionEnabled()) {
            return new ProjectionConsistencyReport(
                ProjectionTargetType.CLINICAL_GRAPH,
                tenantId,
                ProjectionSyncStatus.NOT_SYNCED,
                "graph-projection Feature Flag 关闭，未执行投影一致性校验",
                false,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(),
                List.of());
        }
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
            consistent ? ProjectionSyncStatus.SUCCESS : ProjectionSyncStatus.FAILED,
            consistent ? "投影快照与关系库权威源一致" : "投影快照与关系库权威源不一致",
            consistent,
            sourceFacts.size(),
            projectionRows.size(),
            sourceHash,
            projectionHash,
            missing,
            extra,
            changed);
    }

    @Transactional(readOnly = true)
    public ProjectionRuntimeStatusResponse runtimeStatus(String tenantId) {
        boolean graphEnabled = policy.graphProjectionEnabled();
        boolean difyEnabled = policy.difyWorkflowEnabled();
        long snapshotCount = graphEnabled
            ? snapshots.countByTenantIdAndTargetType(tenantId, ProjectionTargetType.CLINICAL_GRAPH)
            : 0L;
        ClinicalProjectionStatus status = graphEnabled && snapshotCount > 0
            ? ClinicalProjectionStatus.UP
            : ClinicalProjectionStatus.NOT_SYNCED;
        return new ProjectionRuntimeStatusResponse(
            ProjectionTargetType.CLINICAL_GRAPH,
            tenantId,
            graphEnabled,
            difyEnabled,
            status,
            ProjectionSyncStatus.NOT_SYNCED,
            snapshotCount,
            graphEnabled
                ? "图投影开关已开启；状态由快照数量与同步结果决定"
                : "graph-projection Feature Flag 关闭，关系库权威主链路保持可用");
    }

    private ProjectionExecutionResult externalExecutionResult(String tenantId, ProjectionSync finished,
            int sourceCount, String sourceHash, String traceId) {
        if (!policy.difyWorkflowEnabled()) {
            return ProjectionExecutionResult.notSynced("dify-workflow Feature Flag 关闭，未执行外部工作流");
        }
        return executor.execute(new ProjectionExecutionCommand(
            tenantId,
            finished.syncId(),
            ProjectionTargetType.CLINICAL_GRAPH,
            sourceCount,
            sourceHash,
            traceId));
    }

    private ProjectionRebuildResponse responseFrom(ProjectionSync sync, ProjectionSyncStatus difyStatus,
            String message) {
        return new ProjectionRebuildResponse(
            sync.syncId(),
            ProjectionTargetType.CLINICAL_GRAPH,
            sync.status(),
            sync.sourceCount(),
            sync.projectionCount(),
            sync.sourceHash(),
            sync.projectionHash(),
            sync.traceId(),
            difyStatus,
            message);
    }

    private void recordSyncAudit(ProjectionSync sync, ProjectionSyncStatus difyStatus) {
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.EXECUTE,
            "mk_projection_sync",
            sync.syncId(),
            "投影同步 " + sync.status(),
            null,
            auditAfter(sync, difyStatus),
            null));
    }

    private Map<String, Object> auditAfter(ProjectionSync sync, ProjectionSyncStatus difyStatus) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("tenantId", sync.tenantId());
        after.put("targetType", sync.targetType());
        after.put("status", sync.status());
        after.put("sourceCount", sync.sourceCount());
        after.put("projectionCount", sync.projectionCount());
        after.put("sourceHash", sync.sourceHash());
        after.put("projectionHash", sync.projectionHash());
        after.put("difyExecutionStatus", difyStatus);
        after.put("traceId", sync.traceId());
        return after;
    }

    private String message(String syncMessage, String externalMessage) {
        if (externalMessage == null || externalMessage.isBlank()) {
            return syncMessage;
        }
        return syncMessage + "；" + externalMessage;
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
