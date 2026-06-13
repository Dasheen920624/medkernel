package com.medkernel.engine.pkg;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.BatchResolvedAsset;
import com.medkernel.engine.versioning.InheritanceBatchResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.VersionedAssetIdentity;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

/**
 * 将平台知识包基线解析为指定机构实际生效的包快照。
 */
@Service
public class EffectiveKnowledgePackageResolver {

    private static final String GLOBAL_SCOPE = "ALL";

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository itemRepository;
    private final InheritanceResolver inheritanceResolver;
    private final PackageEntitlementService entitlementService;
    private final AssetVersionRepository assetVersionRepository;
    private final RuleDefinitionRepository ruleRepository;
    private final PathwayTemplateRepository pathwayRepository;
    private final EvaluationIndicatorRepository evaluationRepository;

    public EffectiveKnowledgePackageResolver(
            KnowledgePackageRepository packageRepository,
            PackageItemRepository itemRepository,
            InheritanceResolver inheritanceResolver,
            PackageEntitlementService entitlementService,
            AssetVersionRepository assetVersionRepository,
            RuleDefinitionRepository ruleRepository,
            PathwayTemplateRepository pathwayRepository,
            EvaluationIndicatorRepository evaluationRepository) {
        this.packageRepository = packageRepository;
        this.itemRepository = itemRepository;
        this.inheritanceResolver = inheritanceResolver;
        this.entitlementService = entitlementService;
        this.assetVersionRepository = assetVersionRepository;
        this.ruleRepository = ruleRepository;
        this.pathwayRepository = pathwayRepository;
        this.evaluationRepository = evaluationRepository;
    }

    public EffectiveKnowledgePackageResponse resolve(
            String tenantId,
            String packageCode,
            String packageVersion,
            String targetOrgUnitId) {
        return resolve(tenantId, packageCode, packageVersion, targetOrgUnitId, null, null);
    }

    public EffectiveKnowledgePackageResponse resolve(
            String tenantId,
            String packageCode,
            String packageVersion,
            String targetOrgUnitId,
            String applicableScope,
            Instant effectiveAt) {
        String effectiveTenantId = required(tenantId, "租户 ID");
        String effectivePackageCode = required(packageCode, "知识包编码");
        String effectivePackageVersion = required(packageVersion, "知识包版本");
        String effectiveTargetOrgUnitId = required(targetOrgUnitId, "目标组织 ID");

        KnowledgePackage pack = packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                effectiveTenantId, effectivePackageCode, effectivePackageVersion)
            .orElseGet(() -> packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                    PlatformTenant.ID, effectivePackageCode, effectivePackageVersion)
                .orElseThrow(() -> new ApiException(
                    ErrorCode.NOT_FOUND,
                    "知识包不存在: " + effectivePackageCode + "@" + effectivePackageVersion)));
        return resolveSelectedPackage(
            effectiveTenantId,
            effectiveTargetOrgUnitId,
            pack,
            applicableScope,
            effectiveAt,
            true);
    }

    /**
     * 解析当前租户自己拥有的包生命周期候选快照。
     *
     * <p>发布、离线导出和回滚需要在状态切换前读取候选包；该入口不回退平台基线，
     * 并通过包归属校验避免未发布平台包被下游租户消费。
     */
    public EffectiveKnowledgePackageResponse resolveOwnedLifecycleCandidate(
            String tenantId,
            KnowledgePackage pack,
            String targetOrgUnitId) {
        String effectiveTenantId = required(tenantId, "租户 ID");
        String effectiveTargetOrgUnitId = required(targetOrgUnitId, "目标组织 ID");
        if (pack == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "知识包不能为空");
        }
        if (!effectiveTenantId.equals(pack.tenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "只能解析当前租户自己拥有的生命周期候选包");
        }
        return resolveSelectedPackage(
            effectiveTenantId,
            effectiveTargetOrgUnitId,
            pack,
            null,
            null,
            false);
    }

    private EffectiveKnowledgePackageResponse resolveSelectedPackage(
            String effectiveTenantId,
            String effectiveTargetOrgUnitId,
            KnowledgePackage pack,
            String applicableScope,
            Instant effectiveAt,
            boolean requireReleasedPlatformBaseline) {
        boolean platformPackage = PlatformTenant.ID.equals(pack.tenantId());
        if (requireReleasedPlatformBaseline
                && platformPackage
                && pack.status() != KnowledgePackageStatus.PUBLISHED
                && pack.status() != KnowledgePackageStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "平台基线知识包尚未发布，不能解析有效包: " + pack.packageId());
        }
        if (platformPackage) {
            entitlementService.assertUsable(effectiveTenantId, pack);
        }

        List<PackageItem> declaredItems =
            itemRepository.findByTenantIdAndPackageId(pack.tenantId(), pack.packageId());
        Map<VersionedAssetIdentity, PackageItem> declaredByIdentity = new LinkedHashMap<>();
        List<String> declaredApplicableScopes = new ArrayList<>();
        List<PackageItem> embeddedItems = new ArrayList<>();
        for (PackageItem item : declaredItems) {
            if (embeddedTerminologyItem(pack, item)) {
                embeddedItems.add(item);
            } else {
                VersionedAssetIdentity identity = declaredIdentity(pack.tenantId(), item);
                declaredByIdentity.put(identity, item);
                String assetScope = declaredAssetApplicableScope(pack.tenantId(), identity, item.assetVersion());
                if (assetScope != null && !assetScope.isBlank()) {
                    declaredApplicableScopes.add(assetScope);
                }
            }
        }
        List<BatchResolvedAsset> batch = declaredByIdentity.isEmpty()
            ? List.of()
            : inheritanceResolver.resolveBatch(new InheritanceBatchResolveQuery(
                effectiveTenantId,
                List.copyOf(declaredByIdentity.keySet()),
                scopes(applicableScope, pack.packageVersion(), declaredApplicableScopes),
                effectiveTargetOrgUnitId,
                effectiveAt));
        Map<VersionedAssetIdentity, BatchResolvedAsset> resolvedByIdentity = new LinkedHashMap<>();
        for (BatchResolvedAsset item : batch) {
            resolvedByIdentity.put(item.identity(), item);
        }
        List<EffectivePackageItem> effectiveItems = new ArrayList<>();
        List<EffectivePackageExclusion> excludedItems = new ArrayList<>();
        AssetVersion packageAssetVersion = embeddedItems.isEmpty()
            ? null
            : assetVersionRepository
                .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                    pack.tenantId(),
                    com.medkernel.engine.versioning.VersionedAssetType.PACKAGE,
                    pack.packageCode(),
                    pack.packageVersion()
                )
                .orElseThrow(() -> new ApiException(
                    ErrorCode.NOT_FOUND,
                    "术语知识包缺少统一 PACKAGE 版本: "
                        + pack.packageCode() + "@" + pack.packageVersion()
                ));
        for (PackageItem embeddedItem : embeddedItems) {
            effectiveItems.add(new EffectivePackageItem(
                embeddedItem.assetType(),
                embeddedItem.assetId(),
                embeddedItem.assetVersion(),
                pack.packageVersion(),
                pack.tenantId(),
                packageAssetVersion.organizationScope(),
                platformPackage ? com.medkernel.engine.versioning.SourceTier.PLATFORM
                    : com.medkernel.engine.versioning.SourceTier.ORG,
                platformPackage,
                false,
                true,
                packageAssetVersion.versionId(),
                packageAssetVersion.contentHash()
            ));
        }

        for (Map.Entry<VersionedAssetIdentity, PackageItem> entry : declaredByIdentity.entrySet()) {
            VersionedAssetIdentity identity = entry.getKey();
            PackageItem declaredItem = entry.getValue();
            BatchResolvedAsset batchItem = resolvedByIdentity.get(identity);
            if (batchItem == null) {
                throw new ApiException(
                    ErrorCode.NOT_FOUND,
                    "有效包条目未接入统一版本资产: " + itemIdentity(declaredItem));
            }
            ResolvedAssetVersion resolved = batchItem.resolution();
            if (resolved.disabled()) {
                excludedItems.add(new EffectivePackageExclusion(
                    declaredItem.assetType(),
                    declaredItem.assetId(),
                    declaredItem.assetVersion(),
                    "机构有效版本已被覆盖停用",
                    resolved.sourceOrgPath()));
                continue;
            }
            AssetVersion version = resolved.version();
            if (version == null) {
                throw new ApiException(ErrorCode.CONFLICT, "统一继承解析返回空版本: " + itemIdentity(declaredItem));
            }
            effectiveItems.add(toEffectiveItem(declaredItem, resolved));
        }

        for (BatchResolvedAsset batchItem : batch) {
            if (!batchItem.added() || declaredByIdentity.containsKey(batchItem.identity())) {
                continue;
            }
            ResolvedAssetVersion resolved = batchItem.resolution();
            if (resolved.disabled()) {
                excludedItems.add(new EffectivePackageExclusion(
                    batchItem.identity().assetType(),
                    batchItem.identity().assetIdentity(),
                    null,
                    "机构独有资产已被下级覆盖停用",
                    resolved.sourceOrgPath()));
                continue;
            }
            AssetVersion version = resolved.version();
            if (version == null) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "ADD 独有资产解析返回空版本: "
                        + batchItem.identity().assetType() + ":" + batchItem.identity().assetIdentity());
            }
            effectiveItems.add(toEffectiveItem(batchItem.identity(), version.versionNo(), resolved));
        }

        return new EffectiveKnowledgePackageResponse(
            effectiveTenantId,
            effectiveTargetOrgUnitId,
            pack.packageId(),
            pack.packageCode(),
            pack.packageVersion(),
            effectiveItems,
            excludedItems,
            List.of());
    }

    private VersionedAssetIdentity declaredIdentity(String sourceTenantId, PackageItem item) {
        if (item.assetType() == VersionedAssetType.TERMINOLOGY) {
            return new VersionedAssetIdentity(VersionedAssetType.PACKAGE, terminologyPackageCode(item.assetId()));
        }
        return new VersionedAssetIdentity(item.assetType(), unifiedAssetIdentity(sourceTenantId, item));
    }

    private String unifiedAssetIdentity(String sourceTenantId, PackageItem item) {
        return switch (item.assetType()) {
            case RULE -> ruleRepository.findByRuleIdAndTenantId(item.assetId(), sourceTenantId)
                .map(RuleDefinition::ruleCode)
                .orElse(item.assetId());
            case PATHWAY -> pathwayRepository.findByTemplateIdAndTenantId(item.assetId(), sourceTenantId)
                .map(PathwayTemplate::templateCode)
                .orElse(item.assetId());
            case EVALUATION -> evaluationRepository.findByIndicatorIdAndTenantId(item.assetId(), sourceTenantId)
                .map(EvaluationIndicator::indicatorCode)
                .orElse(item.assetId());
            default -> item.assetId();
        };
    }

    private String terminologyPackageCode(String assetId) {
        String[] parts = assetId == null ? new String[0] : assetId.split("\\|", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "术语映射包资产 ID 必须为 packageCode|scopeLevel|scopeCode: " + assetId);
        }
        return parts[0].trim();
    }

    private String declaredAssetApplicableScope(
            String sourceTenantId,
            VersionedAssetIdentity identity,
            String declaredVersion) {
        return assetVersionRepository.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                sourceTenantId,
                identity.assetType(),
                identity.assetIdentity(),
                declaredVersion)
            .map(AssetVersion::applicableScope)
            .orElse(null);
    }

    private EffectivePackageItem toEffectiveItem(
            PackageItem declaredItem,
            ResolvedAssetVersion resolved) {
        AssetVersion version = resolved.version();
        return new EffectivePackageItem(
            declaredItem.assetType(),
            declaredItem.assetId(),
            declaredItem.assetVersion(),
            version.versionNo(),
            version.tenantId(),
            resolved.sourceOrgPath(),
            resolved.sourceTier(),
            resolved.inherited(),
            resolved.overridden(),
            true,
            version.versionId(),
            version.contentHash());
    }

    private EffectivePackageItem toEffectiveItem(
            VersionedAssetIdentity identity,
            String declaredVersion,
            ResolvedAssetVersion resolved) {
        AssetVersion version = resolved.version();
        return new EffectivePackageItem(
            identity.assetType(),
            identity.assetIdentity(),
            declaredVersion,
            version.versionNo(),
            version.tenantId(),
            resolved.sourceOrgPath(),
            resolved.sourceTier(),
            resolved.inherited(),
            resolved.overridden(),
            true,
            version.versionId(),
            version.contentHash());
    }

    private List<String> scopes(
            String applicableScope,
            String packageVersion,
            List<String> declaredApplicableScopes) {
        Set<String> values = new LinkedHashSet<>();
        if (applicableScope != null && !applicableScope.isBlank()) {
            values.add(applicableScope.trim());
        }
        for (String declaredApplicableScope : declaredApplicableScopes) {
            if (declaredApplicableScope != null && !declaredApplicableScope.isBlank()) {
                values.add(declaredApplicableScope.trim());
            }
        }
        values.add(required(packageVersion, "知识包版本"));
        values.add(GLOBAL_SCOPE);
        return List.copyOf(values);
    }

    private String itemIdentity(PackageItem item) {
        return item.assetType() + ":" + item.assetId() + "@" + item.assetVersion();
    }

    private boolean embeddedTerminologyItem(KnowledgePackage pack, PackageItem item) {
        return item.assetType() == com.medkernel.engine.versioning.VersionedAssetType.TERMINOLOGY
            && item.packageId().equals(pack.packageId())
            && item.assetVersion().equals(pack.packageVersion())
            && item.assetId().startsWith(pack.packageCode() + "|");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }
}
