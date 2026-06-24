package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideMode;
import com.medkernel.engine.versioning.InheritanceOverrideRegisterCommand;
import com.medkernel.engine.versioning.InheritanceOverrideService;
import com.medkernel.engine.versioning.InheritancePropagation;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.ids.Ulid;

/**
 * 平台知识按需派生服务。
 *
 * <p>默认读取平台版本时不复制数据；只有客户明确发起定制时，才复制知识身份、
 * 当前平台版本及其完整证据链，并固化平台标准版本血缘。
 */
@Service
public class KnowledgeCustomizationService {

    private final KnowledgeCustomizationRepository customizations;
    private final KnowledgeIdentityRepository identities;
    private final KnowledgeAssetVersionRepository versions;
    private final SourceDocumentRepository sourceDocuments;
    private final SourceVersionRepository sourceVersions;
    private final SourceFragmentRepository sourceFragments;
    private final CitationRepository citations;
    private final OrgUnitRepository organizations;
    private final KnowledgeVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final KnowledgeVersionService versionService;
    private final InheritanceOverrideService overrideService;
    private final AuditRecorder auditRecorder;

    public KnowledgeCustomizationService(
            KnowledgeCustomizationRepository customizations,
            KnowledgeIdentityRepository identities,
            KnowledgeAssetVersionRepository versions,
            SourceDocumentRepository sourceDocuments,
            SourceVersionRepository sourceVersions,
            SourceFragmentRepository sourceFragments,
            CitationRepository citations,
            OrgUnitRepository organizations,
            KnowledgeVersionedAssetAdapter versionedAssets,
            AssetVersionRepository assetVersions,
            KnowledgeVersionService versionService,
            InheritanceOverrideService overrideService,
            AuditRecorder auditRecorder) {
        this.customizations = customizations;
        this.identities = identities;
        this.versions = versions;
        this.sourceDocuments = sourceDocuments;
        this.sourceVersions = sourceVersions;
        this.sourceFragments = sourceFragments;
        this.citations = citations;
        this.organizations = organizations;
        this.versionedAssets = versionedAssets;
        this.assetVersions = assetVersions;
        this.versionService = versionService;
        this.overrideService = overrideService;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 为当前客户租户和目标组织创建平台知识的本地派生草稿。
     */
    @Transactional
    public KnowledgeCustomizationResponse create(KnowledgeCustomizationCreateRequest request) {
        String tenantId = tenantId();
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "平台主租户维护权威源，不创建本地定制");
        }
        String applicableScope = required(request.applicableScope(), "适用范围");
        OrgUnit target = organizations.findByTenantIdAndId(
                tenantId, required(request.targetOrgUnitId(), "目标组织"))
            .filter(OrgUnit::isActive)
            .orElseThrow(() -> ApiException.notFound("目标组织 " + request.targetOrgUnitId()));

        var existing = customizations
            .findByTenantIdAndPlatformIdentityIdAndTargetOrgUnitIdAndApplicableScope(
                tenantId, request.platformIdentityId(), target.id(), applicableScope);
        if (existing.isPresent() && existing.get().status() != KnowledgeCustomizationStatus.RESTORED) {
            return response(existing.get(), target);
        }

        KnowledgeIdentity platformIdentity = identities.findByTenantIdAndId(
                PlatformTenant.ID, request.platformIdentityId())
            .orElseThrow(() -> ApiException.notFound(
                "平台知识身份 id=" + request.platformIdentityId()));
        if (!platformIdentity.isActive() || !platformIdentity.hasCurrentVersion()) {
            throw new ApiException(ErrorCode.CONFLICT, "平台知识当前没有可派生的权威版本");
        }
        KnowledgeAssetVersion platformVersion = versions.findByTenantIdAndId(
                PlatformTenant.ID, platformIdentity.currentVersionId())
            .filter(KnowledgeAssetVersion::isAuthoritative)
            .orElseThrow(() -> new ApiException(
                ErrorCode.CONFLICT, "平台知识当前权威版本不可用"));

        String actor = actor();
        Instant now = Instant.now();
        KnowledgeIdentity localIdentity = identities
            .findByTenantIdAndIdentityCode(tenantId, platformIdentity.identityCode())
            .orElseGet(() -> identities.save(new KnowledgeIdentity(
                null,
                tenantId,
                platformIdentity.identityCode(),
                platformIdentity.domain(),
                platformIdentity.subject(),
                platformIdentity.specialtyId(),
                platformIdentity.description(),
                KnowledgeIdentityStatus.ACTIVE,
                null,
                now,
                actor,
                now,
                actor)));

        ClonedEvidence evidence = cloneEvidence(platformVersion, tenantId, actor, now);
        AssetVersion localAssetDraft = versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.KNOWLEDGE,
            localIdentity.identityCode(),
            target.orgPath(),
            applicableScope,
            null,
            platformVersion.contentHash(),
            "platform-knowledge:" + platformVersion.id(),
            actor,
            traceId(),
            platformVersion.isHighRisk()
                ? AssetVersionSafetyPolicy.SAFETY_REDLINE
                : AssetVersionSafetyPolicy.NORMAL,
            platformVersion.isHighRisk()
                ? AssetVersionOverridePolicy.REVIEW
                : AssetVersionOverridePolicy.FREE));
        String localVersionNo = localAssetDraft.versionNo();
        KnowledgeAssetVersion localVersion = versions.save(new KnowledgeAssetVersion(
            null,
            tenantId,
            localIdentity.id(),
            localVersionNo,
            "基于平台 " + platformVersion.versionNo() + " 的本地草稿",
            evidence.sourceDocumentId(),
            evidence.sourceVersionId(),
            platformVersion.contentHash(),
            platformVersion.anchors(),
            KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW,
            platformVersion.riskLevel(),
            platformVersion.authorityLevel(),
            platformVersion.gradeQuality(),
            platformVersion.gradeStrength(),
            null,
            target.orgPath(),
            applicableScope,
            "version-pending:" + localIdentity.id() + ":" + localVersionNo,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            now,
            actor,
            now,
            actor,
            platformVersion.reviewCycleMonths(),
            null));
        cloneCitations(platformVersion, localVersion, tenantId, actor, now, evidence.fragmentIds());
        identities.save(new KnowledgeIdentity(
            localIdentity.id(),
            localIdentity.tenantId(),
            localIdentity.identityCode(),
            localIdentity.domain(),
            localIdentity.subject(),
            localIdentity.specialtyId(),
            localIdentity.description(),
            localIdentity.status(),
            localVersion.id(),
            localIdentity.createdAt(),
            localIdentity.createdBy(),
            now,
            actor));

        KnowledgeCustomization saved = customizations.save(new KnowledgeCustomization(
            existing.map(KnowledgeCustomization::customizationId)
                .orElseGet(() -> "kc-" + Ulid.newUlid()),
            tenantId,
            platformIdentity.id(),
            platformVersion.id(),
            platformVersion.versionNo(),
            localIdentity.id(),
            localVersion.id(),
            target.id(),
            target.orgPath(),
            applicableScope,
            KnowledgeSourceType.LOCAL_CUSTOMIZATION,
            KnowledgeCustomizationStatus.DRAFT,
            compact(request.reason()),
            null,
            existing.map(value -> value.version() + 1L).orElse(1L),
            existing.map(KnowledgeCustomization::createdAt).orElse(now),
            existing.map(KnowledgeCustomization::createdBy).orElse(actor),
            now,
            actor,
            traceId()));
        auditRecorder.record(
            AuditAction.CREATE,
            "mk_knowledge_customization",
            saved.customizationId(),
            "从平台知识创建本地定制，目标组织=" + target.name()
                + "，平台版本=" + platformVersion.versionNo());
        return response(saved, target);
    }

    /**
     * 分页查询当前租户的知识派生血缘。
     */
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeCustomizationResponse> list(PageRequest pageRequest) {
        PageRequest page = pageRequest == null ? PageRequest.defaults() : pageRequest;
        String tenantId = tenantId();
        List<KnowledgeCustomizationResponse> items = customizations
            .pageByTenantId(tenantId, page.offset(), page.safeSize())
            .stream()
            .map(item -> organizations.findByTenantIdAndId(tenantId, item.targetOrgUnitId())
                .map(org -> response(item, org))
                .orElseGet(() -> response(item, null)))
            .toList();
        return PageResponse.of(items, page, customizations.countByTenantId(tenantId));
    }

    /**
     * 发布本地派生版本，并原子登记目标组织的替换覆盖。
     */
    @Transactional
    public KnowledgeCustomizationResponse publish(
            String customizationId,
            String reason,
            Long qualityGateRecordId) {
        String tenantId = tenantId();
        KnowledgeCustomization item = requireCustomization(customizationId);
        if (item.status() == KnowledgeCustomizationStatus.ACTIVE) {
            return response(item, requireTarget(item));
        }
        if (item.status() == KnowledgeCustomizationStatus.RESTORED) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "该定制已恢复平台标准，请先基于最新平台版本重新创建草稿");
        }
        KnowledgeIdentity localIdentity = identities.findByTenantIdAndId(
                tenantId, item.localIdentityId())
            .orElseThrow(() -> ApiException.notFound("本地知识身份"));
        KnowledgeAssetVersion localVersion = versions.findByTenantIdAndId(
                tenantId, item.localVersionId())
            .orElseThrow(() -> ApiException.notFound("本地知识版本"));
        KnowledgeIdentity platformIdentity = identities.findByTenantIdAndId(
                PlatformTenant.ID, item.platformIdentityId())
            .orElseThrow(() -> ApiException.notFound("平台知识身份"));
        KnowledgeAssetVersion platformVersion = versions.findByTenantIdAndId(
                PlatformTenant.ID, item.platformVersionId())
            .orElseThrow(() -> ApiException.notFound("平台知识版本"));

        KnowledgeAssetVersion activated = versionService.activate(
            localIdentity.id(),
            localVersion.id(),
            required(reason, "发布原因"),
            qualityGateRecordId);
        var localAssetVersion = assetVersions
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                tenantId,
                VersionedAssetType.KNOWLEDGE,
                localIdentity.identityCode(),
                activated.versionNo())
            .orElseThrow(() -> ApiException.notFound("本地统一知识版本"));
        var platformAssetVersion = assetVersions
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                PlatformTenant.ID,
                VersionedAssetType.KNOWLEDGE,
                platformIdentity.identityCode(),
                platformVersion.versionNo())
            .orElseThrow(() -> ApiException.notFound("平台统一知识版本"));
        InheritanceOverride override = overrideService.registerOverride(
            new InheritanceOverrideRegisterCommand(
                tenantId,
                VersionedAssetType.KNOWLEDGE,
                localIdentity.identityCode(),
                platformAssetVersion.versionId(),
                localAssetVersion.versionId(),
                item.targetOrgUnitId(),
                item.applicableScope(),
                InheritanceOverrideMode.REPLACE,
                "从平台版本 " + platformVersion.versionNo()
                    + " 派生本地版本 " + activated.versionNo(),
                compact(reason),
                "目标组织 " + item.targetOrgPath() + " 及其继承范围",
                actor(),
                traceId(),
                InheritancePropagation.INHERITABLE));
        Instant now = Instant.now();
        KnowledgeCustomization saved = customizations.save(new KnowledgeCustomization(
            item.customizationId(),
            item.tenantId(),
            item.platformIdentityId(),
            item.platformVersionId(),
            item.platformVersionNo(),
            item.localIdentityId(),
            activated.id(),
            item.targetOrgUnitId(),
            item.targetOrgPath(),
            item.applicableScope(),
            item.sourceType(),
            KnowledgeCustomizationStatus.ACTIVE,
            item.reason(),
            override.overrideId(),
            item.version() + 1L,
            item.createdAt(),
            item.createdBy(),
            now,
            actor(),
            traceId()));
        auditRecorder.record(
            AuditAction.PUBLISH,
            "mk_knowledge_customization",
            saved.customizationId(),
            "发布机构知识定制并接管目标组织，覆盖=" + override.overrideId());
        return response(saved, requireTarget(saved));
    }

    /**
     * 停用本地覆盖并恢复平台标准。历史版本、证据和血缘全部保留。
     */
    @Transactional
    public KnowledgeCustomizationResponse restorePlatformStandard(
            String customizationId,
            String reason) {
        KnowledgeCustomization item = requireCustomization(customizationId);
        if (item.status() == KnowledgeCustomizationStatus.RESTORED) {
            return response(item, requireTarget(item));
        }
        String normalizedReason = required(reason, "恢复原因");
        if (item.status() == KnowledgeCustomizationStatus.ACTIVE) {
            KnowledgeAssetVersion localVersion = versions.findByTenantIdAndId(
                    tenantId(), item.localVersionId())
                .orElseThrow(() -> ApiException.notFound("本地知识版本"));
            if (localVersion.status() == KnowledgeVersionStatus.ACTIVE) {
                versionService.withdraw(
                    item.localIdentityId(),
                    item.localVersionId(),
                    "恢复平台标准：" + normalizedReason);
            }
            if (item.overrideId() != null) {
                overrideService.retireOverride(
                    tenantId(), item.overrideId(), actor(), traceId());
            }
        }
        Instant now = Instant.now();
        KnowledgeCustomization saved = customizations.save(new KnowledgeCustomization(
            item.customizationId(),
            item.tenantId(),
            item.platformIdentityId(),
            item.platformVersionId(),
            item.platformVersionNo(),
            item.localIdentityId(),
            item.localVersionId(),
            item.targetOrgUnitId(),
            item.targetOrgPath(),
            item.applicableScope(),
            item.sourceType(),
            KnowledgeCustomizationStatus.RESTORED,
            item.reason(),
            item.overrideId(),
            item.version() + 1L,
            item.createdAt(),
            item.createdBy(),
            now,
            actor(),
            traceId()));
        auditRecorder.record(
            AuditAction.EXECUTE,
            "mk_knowledge_customization",
            saved.customizationId(),
            "恢复平台标准，原因=" + normalizedReason);
        return response(saved, requireTarget(saved));
    }

    private ClonedEvidence cloneEvidence(
            KnowledgeAssetVersion platformVersion,
            String tenantId,
            String actor,
            Instant now) {
        SourceDocument source = sourceDocuments.findByTenantIdAndId(
                PlatformTenant.ID, platformVersion.sourceDocumentId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_KNOW_001, "平台知识来源文献不存在"));
        SourceVersion sourceVersion = sourceVersions.findByTenantIdAndId(
                PlatformTenant.ID, platformVersion.sourceVersionId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_KNOW_001, "平台知识来源版本不存在"));

        String suffix = Ulid.newUlid().substring(18).toLowerCase();
        SourceDocument localSource = sourceDocuments.save(new SourceDocument(
            null,
            tenantId,
            source.sourceCode() + "-local-" + suffix,
            source.sourceType(),
            source.authorityLevel(),
            source.authorityBasis(),
            source.title(),
            source.publisher(),
            source.license(),
            source.language(),
            now,
            actor,
            now,
            actor));
        SourceVersion localSourceVersion = sourceVersions.save(new SourceVersion(
            null,
            tenantId,
            localSource.id(),
            sourceVersion.versionNo(),
            sourceVersion.publishedAt(),
            sourceVersion.contentHash(),
            sourceVersion.fileUri(),
            sourceVersion.language(),
            now,
            actor));
        Map<Long, Long> fragmentIds = new HashMap<>();
        sourceFragments.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc(
                PlatformTenant.ID, sourceVersion.id())
            .forEach(fragment -> {
                SourceFragment local = sourceFragments.save(new SourceFragment(
                    null,
                    tenantId,
                    localSourceVersion.id(),
                    fragment.anchorPath(),
                    fragment.anchorLabel(),
                    fragment.textExcerpt(),
                    fragment.contentHash(),
                    now));
                fragmentIds.put(fragment.id(), local.id());
            });
        return new ClonedEvidence(localSource.id(), localSourceVersion.id(), fragmentIds);
    }

    private void cloneCitations(
            KnowledgeAssetVersion platformVersion,
            KnowledgeAssetVersion localVersion,
            String tenantId,
            String actor,
            Instant now,
            Map<Long, Long> fragmentIds) {
        citations.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc(
                PlatformTenant.ID, platformVersion.id())
            .forEach(citation -> {
                Long localFragmentId = fragmentIds.get(citation.sourceFragmentId());
                if (localFragmentId == null) {
                    throw new ApiException(
                        ErrorCode.ENG_KNOW_001,
                        "平台知识引用片段缺失，不能创建不完整的本地定制");
                }
                citations.save(new Citation(
                    null,
                    tenantId,
                    localVersion.id(),
                    localFragmentId,
                    citation.relation(),
                    citation.weight(),
                    citation.startOffset(),
                    citation.endOffset(),
                    now,
                    actor));
            });
    }

    private KnowledgeCustomizationResponse response(
            KnowledgeCustomization item,
            OrgUnit organization) {
        KnowledgeRiskLevel riskLevel = versions.findByTenantIdAndId(
                item.tenantId(), item.localVersionId())
            .map(KnowledgeAssetVersion::riskLevel)
            .orElseThrow(() -> ApiException.notFound("本地知识版本"));
        boolean updateAvailable = identities.findByTenantIdAndId(
                PlatformTenant.ID, item.platformIdentityId())
            .map(KnowledgeIdentity::currentVersionId)
            .filter(current -> !current.equals(item.platformVersionId()))
            .isPresent();
        return new KnowledgeCustomizationResponse(
            item.customizationId(),
            item.sourceType(),
            item.status(),
            item.platformIdentityId(),
            item.platformVersionId(),
            item.platformVersionNo(),
            item.localIdentityId(),
            item.localVersionId(),
            riskLevel,
            item.targetOrgUnitId(),
            organization == null ? "组织已停用" : organization.name(),
            item.targetOrgPath(),
            item.applicableScope(),
            item.reason(),
            item.overrideId(),
            updateAvailable,
            item.updatedAt());
    }

    private KnowledgeCustomization requireCustomization(String customizationId) {
        return customizations.findByTenantIdAndCustomizationId(
                tenantId(), required(customizationId, "定制 ID"))
            .orElseThrow(() -> ApiException.notFound("知识定制 " + customizationId));
    }

    private OrgUnit requireTarget(KnowledgeCustomization item) {
        return organizations.findByTenantIdAndId(
                item.tenantId(), item.targetOrgUnitId())
            .orElseThrow(() -> ApiException.notFound("目标组织 " + item.targetOrgUnitId()));
    }

    private String tenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String traceId() {
        return RequestContext.currentTraceId();
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private String compact(String value) {
        return required(value, "定制原因").replaceAll("\\s+", " ");
    }

    private record ClonedEvidence(
        Long sourceDocumentId,
        Long sourceVersionId,
        Map<Long, Long> fragmentIds
    ) {
    }
}
