package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.engine.versioning.AssetPublicationStatusSynchronizer;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 知识统一资产版本发布后的知识版本投影状态同步器。
 *
 * <p>平台标准版本和机构生效版本通过统一资产发布入口把知识版本纳入运行清单后，
 * 这里同步知识领域状态，确保运行侧只读取当前 ACTIVE 的权威版本。
 */
@Component
public class KnowledgePublicationStatusSynchronizer implements AssetPublicationStatusSynchronizer {

    private final KnowledgeIdentityRepository identities;
    private final KnowledgeAssetVersionRepository versions;
    private final KnowledgeSupersessionRepository supersessions;

    public KnowledgePublicationStatusSynchronizer(
            KnowledgeIdentityRepository identities,
            KnowledgeAssetVersionRepository versions,
            KnowledgeSupersessionRepository supersessions) {
        this.identities = identities;
        this.versions = versions;
        this.supersessions = supersessions;
    }

    @Override
    public void afterPublished(
            AssetVersion publishedVersion,
            Instant publishedAt,
            String actor,
            String traceId) {
        if (publishedVersion.assetType() != VersionedAssetType.KNOWLEDGE) {
            return;
        }
        if (publishedVersion.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.CONFLICT, "知识资产同步只接受已发布版本");
        }
        KnowledgeIdentity identity = identities
            .findByTenantIdAndIdentityCode(
                publishedVersion.tenantId(),
                publishedVersion.assetIdentity())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_KNOW_001,
                "知识资产版本缺少知识身份投影，禁止发布: "
                    + publishedVersion.assetIdentity() + "@" + publishedVersion.versionNo()));
        if (identity.status() != KnowledgeIdentityStatus.ACTIVE) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "知识身份当前状态不允许同步发布: "
                    + identity.identityCode() + "=" + identity.status());
        }
        KnowledgeAssetVersion target = versions
            .findByTenantIdAndIdentityIdAndContentHash(
                publishedVersion.tenantId(),
                identity.id(),
                publishedVersion.contentHash())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_KNOW_001,
                "知识资产版本缺少知识内容投影，禁止发布: "
                    + publishedVersion.assetIdentity() + "#" + publishedVersion.contentHash()));
        if (target.status() == KnowledgeVersionStatus.ACTIVE
                && target.id().equals(identity.currentVersionId())) {
            return;
        }
        if (target.status() == null || !target.status().isActivatable()) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "知识版本当前状态不允许同步发布: "
                    + identity.identityCode() + "@" + target.versionNo()
                    + "=" + target.status());
        }

        String organizationScope = target.effectiveOrganizationScope();
        String applicableScope = target.effectiveApplicableScope();
        Optional<KnowledgeAssetVersion> currentActive = versions.findActiveByEffectiveScope(
            publishedVersion.tenantId(),
            identity.id(),
            organizationScope,
            applicableScope);
        Long oldVersionId = currentActive.map(KnowledgeAssetVersion::id).orElse(null);
        if (currentActive.isPresent() && !currentActive.get().id().equals(target.id())) {
            versions.save(superseded(currentActive.get(), publishedAt, actor));
        }

        KnowledgeAssetVersion activated = versions.save(active(target, publishedAt, actor));
        identities.save(new KnowledgeIdentity(
            identity.id(),
            identity.tenantId(),
            identity.identityCode(),
            identity.domain(),
            identity.subject(),
            identity.specialtyId(),
            identity.description(),
            identity.status(),
            activated.id(),
            identity.createdAt(),
            identity.createdBy(),
            publishedAt,
            actor
        ));
        supersessions.save(new KnowledgeSupersession(
            null,
            publishedVersion.tenantId(),
            identity.id(),
            oldVersionId,
            activated.id(),
            oldVersionId == null ? SupersessionType.ACTIVATE : SupersessionType.REPLACE,
            "统一资产版本发布同步知识权威版本",
            publishedAt,
            actor,
            null,
            null,
            null
        ));
    }

    private KnowledgeAssetVersion superseded(
            KnowledgeAssetVersion source,
            Instant now,
            String actor) {
        return new KnowledgeAssetVersion(
            source.id(),
            source.tenantId(),
            source.identityId(),
            source.versionNo(),
            source.versionLabel(),
            source.sourceDocumentId(),
            source.sourceVersionId(),
            source.contentHash(),
            source.anchors(),
            KnowledgeVersionStatus.SUPERSEDED,
            source.riskLevel(),
            source.authorityLevel(),
            source.gradeQuality(),
            source.gradeStrength(),
            source.conflictArbitration(),
            source.effectiveOrganizationScope(),
            source.effectiveApplicableScope(),
            source.scopeKeyForStatus(KnowledgeVersionStatus.SUPERSEDED),
            source.effectiveFrom(),
            now,
            source.reviewedBy(),
            source.reviewedAt(),
            source.activatedAt(),
            now,
            source.withdrawnAt(),
            source.withdrawnReason(),
            source.createdAt(),
            source.createdBy(),
            now,
            actor,
            source.reviewCycleMonths(),
            source.nextReviewAt()
        );
    }

    private KnowledgeAssetVersion active(
            KnowledgeAssetVersion source,
            Instant now,
            String actor) {
        return new KnowledgeAssetVersion(
            source.id(),
            source.tenantId(),
            source.identityId(),
            source.versionNo(),
            source.versionLabel(),
            source.sourceDocumentId(),
            source.sourceVersionId(),
            source.contentHash(),
            source.anchors(),
            KnowledgeVersionStatus.ACTIVE,
            source.riskLevel(),
            source.authorityLevel(),
            source.gradeQuality(),
            source.gradeStrength(),
            source.conflictArbitration(),
            source.effectiveOrganizationScope(),
            source.effectiveApplicableScope(),
            source.activeScopeKeyForActiveStatus(),
            now,
            null,
            actor,
            now,
            now,
            null,
            null,
            null,
            source.createdAt(),
            source.createdBy(),
            now,
            actor,
            source.reviewCycleMonths(),
            nextReviewAt(now, source.reviewCycleMonths())
        );
    }

    private Instant nextReviewAt(Instant activatedAt, Integer reviewCycleMonths) {
        int months = reviewCycleMonths == null ? 12 : reviewCycleMonths;
        return activatedAt.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }
}
