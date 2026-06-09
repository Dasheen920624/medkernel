package com.medkernel.engine.knowledge;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideRepository;
import com.medkernel.engine.versioning.InheritanceOverrideStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

/**
 * 知识身份弃用、宽限迁移与到期退役服务。
 */
@Service
public class KnowledgeRetirementService {

    private static final String SYSTEM_ACTOR = "system:knowledge-retirement";

    private final KnowledgeIdentityRepository identities;
    private final KnowledgeAssetVersionRepository versions;
    private final KnowledgeSupersessionRepository supersessions;
    private final InheritanceOverrideRepository overrides;
    private final AuditRecorder audit;
    private final Clock clock;

    @Autowired
    public KnowledgeRetirementService(
            KnowledgeIdentityRepository identities,
            KnowledgeAssetVersionRepository versions,
            KnowledgeSupersessionRepository supersessions,
            InheritanceOverrideRepository overrides,
            AuditRecorder audit) {
        this(identities, versions, supersessions, overrides, audit, Clock.systemUTC());
    }

    KnowledgeRetirementService(
            KnowledgeIdentityRepository identities,
            KnowledgeAssetVersionRepository versions,
            KnowledgeSupersessionRepository supersessions,
            InheritanceOverrideRepository overrides,
            Clock clock) {
        this(identities, versions, supersessions, overrides, null, clock);
    }

    KnowledgeRetirementService(
            KnowledgeIdentityRepository identities,
            KnowledgeAssetVersionRepository versions,
            KnowledgeSupersessionRepository supersessions,
            InheritanceOverrideRepository overrides,
            AuditRecorder audit,
            Clock clock) {
        this.identities = identities;
        this.versions = versions;
        this.supersessions = supersessions;
        this.overrides = overrides;
        this.audit = audit;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public KnowledgeSupersession deprecate(Long identityId, KnowledgeRetirementRequest request) {
        String tenantId = currentTenant();
        if (!PlatformTenant.isPlatformTenant(tenantId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "只有平台主租户可以安排平台知识退役");
        }
        Instant now = Instant.now(clock);
        if (request.successorIdentityId().equals(identityId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "后继知识身份不能指向自身");
        }
        if (!request.gracePeriodEnd().isAfter(now)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "宽限期结束时间必须晚于当前时间");
        }
        KnowledgeIdentity current = identities.findByTenantIdAndIdForUpdate(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        if (current.status() != KnowledgeIdentityStatus.ACTIVE || current.currentVersionId() == null) {
            throw new ApiException(ErrorCode.CONFLICT, "只有存在权威版本的有效知识身份可以安排弃用");
        }
        KnowledgeIdentity successor = identities.findByTenantIdAndId(tenantId, request.successorIdentityId())
            .orElseThrow(() -> ApiException.notFound("后继知识身份 id=" + request.successorIdentityId()));
        if (successor.status() != KnowledgeIdentityStatus.ACTIVE
                || successor.currentVersionId() == null
                || successor.domain() != current.domain()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "后继知识必须是同域且存在权威版本的有效身份");
        }
        String actor = currentActor();
        identities.save(new KnowledgeIdentity(
            current.id(), current.tenantId(), current.identityCode(), current.domain(), current.subject(),
            current.specialtyId(), current.description(), KnowledgeIdentityStatus.DEPRECATED, current.currentVersionId(),
            current.createdAt(), current.createdBy(), now, actor));
        KnowledgeSupersession transition = supersessions.save(new KnowledgeSupersession(
            null, tenantId, current.id(), current.currentVersionId(), successor.currentVersionId(),
            SupersessionType.DEPRECATE, "知识身份进入迁移宽限期", now, actor,
            successor.id(), request.gracePeriodEnd(), request.migrationGuidance().trim()));
        recordAudit(AuditAction.UPDATE, current.id(), "安排知识身份弃用并指定后继 " + successor.identityCode());
        return transition;
    }

    @Transactional
    public int finalizeDueRetirements() {
        Instant now = Instant.now(clock);
        int finalized = 0;
        for (KnowledgeSupersession due : safeList(supersessions.findDueDeprecations(now))) {
            KnowledgeIdentity identity = identities.findByTenantIdAndIdForUpdate(due.tenantId(), due.identityId())
                .orElseThrow(() -> ApiException.notFound("知识身份 id=" + due.identityId()));
            if (identity.status() == KnowledgeIdentityStatus.WITHDRAWN) {
                continue;
            }
            withdrawCurrentVersion(identity, due, now);
            suspendOverrides(identity, due, now);
            identities.save(new KnowledgeIdentity(
                identity.id(), identity.tenantId(), identity.identityCode(), identity.domain(), identity.subject(),
                identity.specialtyId(), identity.description(), KnowledgeIdentityStatus.WITHDRAWN, null,
                identity.createdAt(), identity.createdBy(), now, SYSTEM_ACTOR));
            supersessions.save(new KnowledgeSupersession(
                null, due.tenantId(), due.identityId(), due.oldVersionId(), due.newVersionId(),
                SupersessionType.RETIRE, "知识身份宽限期结束并退役", now, SYSTEM_ACTOR,
                due.successorIdentityId(), due.gracePeriodEnd(), due.migrationGuidance()));
            recordSystemAudit(
                identity.tenantId(),
                AuditAction.UPDATE,
                identity.id(),
                "知识身份宽限期结束并完成退役");
            finalized++;
        }
        return finalized;
    }

    private void withdrawCurrentVersion(KnowledgeIdentity identity, KnowledgeSupersession due, Instant now) {
        if (identity.currentVersionId() == null) {
            return;
        }
        versions.findByTenantIdAndId(identity.tenantId(), identity.currentVersionId())
            .filter(version -> version.status() == KnowledgeVersionStatus.ACTIVE)
            .ifPresent(version -> versions.save(new KnowledgeAssetVersion(
                version.id(), version.tenantId(), version.identityId(), version.versionNo(), version.versionLabel(),
                version.sourceDocumentId(), version.sourceVersionId(), version.contentHash(), version.anchors(),
                KnowledgeVersionStatus.WITHDRAWN, version.riskLevel(), version.authorityLevel(),
                version.gradeQuality(), version.gradeStrength(), version.conflictArbitration(),
                version.effectiveOrganizationScope(), version.effectiveApplicableScope(),
                version.scopeKeyForStatus(KnowledgeVersionStatus.WITHDRAWN),
                version.effectiveFrom(), now, version.reviewedBy(), version.reviewedAt(),
                version.activatedAt(), version.supersededAt(), now,
                "知识身份退役：" + due.migrationGuidance(),
                version.createdAt(), version.createdBy(), now, SYSTEM_ACTOR,
                version.reviewCycleMonths(), version.nextReviewAt())));
    }

    private void suspendOverrides(KnowledgeIdentity identity, KnowledgeSupersession due, Instant now) {
        List<InheritanceOverride> published = overrides.findByAssetTypeAndAssetIdentityAndLifecycleStatus(
            VersionedAssetType.KNOWLEDGE, identity.identityCode(), InheritanceOverrideStatus.PUBLISHED);
        for (InheritanceOverride item : safeList(published)) {
            overrides.save(new InheritanceOverride(
                item.id(), item.overrideId(), item.tenantId(), item.assetType(), item.assetIdentity(),
                item.inheritedVersionId(), item.overrideVersionId(), item.overrideMode(), item.propagation(),
                InheritanceOverrideStatus.DEPRECATED, item.orgPath(), item.applicableScope(), item.diffSummary(),
                appendMigrationGuidance(item.overrideReason(), due.migrationGuidance()),
                item.impactScope(), item.createdAt(), item.createdBy(), now, SYSTEM_ACTOR, item.traceId()));
        }
    }

    private String appendMigrationGuidance(String currentReason, String guidance) {
        String migration = "上游知识退役：" + guidance;
        if (currentReason == null || currentReason.isBlank()) {
            return migration;
        }
        return currentReason.trim() + "\n" + migration;
    }

    private String currentTenant() {
        if (RequestContext.currentOrgScope() == null || !RequestContext.currentOrgScope().hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return RequestContext.currentOrgScope().tenantId();
    }

    private String currentActor() {
        return RequestContext.currentUserId()
            .filter(actor -> !actor.isBlank())
            .orElse(SYSTEM_ACTOR);
    }

    private void recordAudit(AuditAction action, Long identityId, String summary) {
        if (audit != null) {
            audit.record(action, "knowledge_identity", String.valueOf(identityId), summary);
        }
    }

    private void recordSystemAudit(String tenantId, AuditAction action, Long identityId, String summary) {
        RequestContext.runWith(
            new RequestContext.Snapshot(null, OrgScope.tenant(tenantId), SYSTEM_ACTOR),
            () -> recordAudit(action, identityId, summary));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
