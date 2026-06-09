package com.medkernel.engine.integration.runtime;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResponse;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResolver;
import com.medkernel.engine.pkg.EffectivePackageSnapshot;
import com.medkernel.engine.pkg.PackageEngineService;
import com.medkernel.engine.pkg.PackageSyncRequest;
import com.medkernel.engine.pkg.PackageSyncResponse;
import com.medkernel.engine.pkg.SyncLogResponse;
import com.medkernel.engine.pkg.SyncLogStatus;
import com.medkernel.engine.versioning.ApplicableScopeMatcher;
import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideRegisterCommand;
import com.medkernel.engine.versioning.InheritanceOverrideService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 第三方知识运行时稳定门面，只编排现有领域服务，不复制领域逻辑。
 */
@Service
public class ThirdPartyKnowledgeRuntimeService {

    public static final String CONTRACT_VERSION = "v1";

    private final EffectiveKnowledgePackageResolver packageResolver;
    private final ContextSnapshotService contexts;
    private final InheritanceOverrideService overrides;
    private final PackageEngineService packages;
    private final AuditRecorder audits;

    public ThirdPartyKnowledgeRuntimeService(
            EffectiveKnowledgePackageResolver packageResolver,
            ContextSnapshotService contexts,
            InheritanceOverrideService overrides,
            PackageEngineService packages,
            AuditRecorder audits) {
        this.packageResolver = packageResolver;
        this.contexts = contexts;
        this.overrides = overrides;
        this.packages = packages;
        this.audits = audits;
    }

    public ThirdPartyEffectivePackageResponse resolveEffectivePackage(
            ThirdPartyEffectivePackageQuery query) {
        if (query == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "有效包解析参数不能为空");
        }
        Instant effectiveAt = query.effectiveAt() == null ? Instant.now() : query.effectiveAt();
        String applicableScope = ApplicableScopeMatcher.canonicalQuery(
            query.specialtyId(),
            query.scenarioCode(),
            query.careSetting(),
            query.cohort(),
            query.role());
        EffectiveKnowledgePackageResponse resolved = packageResolver.resolve(
            tenantId(),
            required(query.packageCode(), "知识包编码"),
            required(query.packageVersion(), "知识包版本"),
            required(query.targetOrgUnitId(), "目标组织 ID"),
            applicableScope,
            effectiveAt);
        return new ThirdPartyEffectivePackageResponse(
            CONTRACT_VERSION,
            effectiveAt,
            applicableScope,
            EffectivePackageSnapshot.from(resolved));
    }

    public ContextSnapshotResponse writeContext(
            ContextSnapshotRequest request,
            String idempotencyKey) {
        return contexts.create(request, idempotencyKey);
    }

    public InheritanceOverride createOverride(ThirdPartyOverrideRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "覆盖参数不能为空");
        }
        InheritanceOverride saved = overrides.registerOverride(new InheritanceOverrideRegisterCommand(
            tenantId(),
            request.assetType(),
            request.assetIdentity(),
            request.inheritedVersionId(),
            request.overrideVersionId(),
            request.targetOrgUnitId(),
            request.applicableScope(),
            request.overrideMode(),
            request.diffSummary(),
            request.overrideReason(),
            request.impactScope(),
            actor(),
            RequestContext.currentTraceId(),
            request.propagation()));
        String targetId = saved == null ? request.assetIdentity() : saved.overrideId();
        audits.record(
            AuditAction.CREATE,
            "mk_version_inheritance_override",
            targetId,
            "第三方契约登记组织覆盖: " + request.overrideMode());
        return saved;
    }

    public InheritanceOverride retireOverride(String overrideId) {
        InheritanceOverride retired = overrides.retireOverride(
            tenantId(),
            required(overrideId, "覆盖 ID"),
            actor(),
            RequestContext.currentTraceId());
        audits.record(
            AuditAction.DELETE,
            "mk_version_inheritance_override",
            overrideId,
            "第三方契约退役组织覆盖");
        return retired;
    }

    public PackageSyncResponse distributePackage(String packageId, PackageSyncRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "知识包分发请求不能为空");
        }
        request.apiContext().validateTenant(tenantId());
        return packages.syncPackage(required(packageId, "知识包 ID"), request);
    }

    public ThirdPartyPackageReconciliationResponse reconcilePackage(String packageId) {
        String normalizedPackageId = required(packageId, "知识包 ID");
        List<SyncLogResponse> logs = packages.listSyncLogs(normalizedPackageId);
        return new ThirdPartyPackageReconciliationResponse(
            CONTRACT_VERSION,
            normalizedPackageId,
            reconciliationStatus(logs),
            logs);
    }

    private ThirdPartyReconciliationStatus reconciliationStatus(List<SyncLogResponse> logs) {
        if (logs == null || logs.isEmpty()) {
            return ThirdPartyReconciliationStatus.NOT_DISTRIBUTED;
        }
        if (logs.stream().anyMatch(log -> log.status() == SyncLogStatus.FAILED)) {
            return ThirdPartyReconciliationStatus.FAILED;
        }
        if (logs.stream().anyMatch(log ->
                log.status() == SyncLogStatus.RUNNING || log.status() == SyncLogStatus.RETRYING)) {
            return ThirdPartyReconciliationStatus.IN_PROGRESS;
        }
        if (logs.stream().anyMatch(log -> log.status() == SyncLogStatus.NOT_SYNCED)) {
            return ThirdPartyReconciliationStatus.NOT_SYNCED;
        }
        return ThirdPartyReconciliationStatus.SUCCESS;
    }

    private String tenantId() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }
}
