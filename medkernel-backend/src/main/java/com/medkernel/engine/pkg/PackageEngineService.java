package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideRegisterCommand;
import com.medkernel.engine.versioning.InheritanceOverrideService;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.RolloutPolicy;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleaseScopeType;
import com.medkernel.engine.versioning.VersionRollbackCommand;
import com.medkernel.engine.versioning.VersionedAssetType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.authoring.ConditionFragment;
import com.medkernel.engine.authoring.ConditionFragmentRepository;
import com.medkernel.engine.authoring.ConditionFragmentStatus;
import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.followup.FollowupTemplate;
import com.medkernel.engine.followup.FollowupTemplateRepository;
import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.pathway.PathwayEdge;
import com.medkernel.engine.pathway.PathwayEdgeRepository;
import com.medkernel.engine.pathway.PathwayMilestone;
import com.medkernel.engine.pathway.PathwayMilestoneRepository;
import com.medkernel.engine.pathway.PathwayNode;
import com.medkernel.engine.pathway.PathwayNodeRepository;
import com.medkernel.engine.pathway.SpecialtyMetricBinding;
import com.medkernel.engine.pathway.SpecialtyMetricBindingRepository;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleApplicabilityService;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.security.AuthenticatedRoleGuard;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.terminology.TermMapping;
import com.medkernel.engine.terminology.TermMappingRepository;
import com.medkernel.engine.terminology.TermMappingSnapshot;
import com.medkernel.engine.terminology.TermMappingSnapshotCodec;
import com.medkernel.engine.terminology.TermMappingSnapshotEntity;
import com.medkernel.engine.terminology.TermMappingSnapshotRepository;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 知识包发布同步核心应用服务。
 *
 * <p>提供资产打包、差异比对、发布灰度校验、多通道同步发布以及快速一键回滚的完整应用层实现。
 */
@Service
public class PackageEngineService {

    private static final Logger log = LoggerFactory.getLogger(PackageEngineService.class);
    private static final ObjectMapper PACKAGE_JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper OFFLINE_EXPORT_MAPPER = new ObjectMapper();
    private static final String OFFLINE_PACKAGE_FORMAT = "MEDKERNEL_PACKAGE_OFFLINE_V2";
    private static final int DEFAULT_GRAY_SCOPE_PERCENTAGE = 10;
    private static final Set<VersionedAssetType> DECLARATIVE_PACKAGE_ASSET_TYPES = Set.of(
        VersionedAssetType.FIELD_CATALOG,
        VersionedAssetType.VALUE_SET,
        VersionedAssetType.FORMULA,
        VersionedAssetType.ORDER_SET,
        VersionedAssetType.ACTION_CARD,
        VersionedAssetType.SUBPATHWAY
    );

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository itemRepository;
    private final ReleasePlanRepository planRepository;
    private final IntegrationAdapterRepository adapterRepository;
    private final SyncLogRepository logRepository;

    private final RuleDefinitionRepository ruleRepository;
    private final RuleApplicabilityService ruleApplicabilityService;
    private final ConditionFragmentRepository conditionFragmentRepository;
    private final PathwayTemplateRepository pathwayRepository;
    private final EvaluationIndicatorRepository evaluationRepository;
    private final FollowupTemplateRepository followupTemplateRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final KnowledgeIdentityRepository knowledgeIdentityRepository;
    private final PathwayMilestoneRepository pathwayMilestoneRepository;
    private final PathwayNodeRepository pathwayNodeRepository;
    private final PathwayEdgeRepository pathwayEdgeRepository;
    private final SpecialtyMetricBindingRepository pathwayMetricBindingRepository;
    private final KnowledgeAssetVersionRepository knowledgeVersionRepository;
    private final TermMappingSnapshotRepository terminologySnapshotRepository;
    private final TermMappingRepository terminologyMappingRepository;
    private final PilotPackageTemplateRepository pilotTemplateRepository;
    private final PilotPackageTemplateItemRepository pilotTemplateItemRepository;
    private final TenantPackageReferenceRepository packageReferenceRepository;
    private final InheritanceOverrideService inheritanceOverrideService;
    private final PackageEntitlementService entitlementService;

    private final PackageSyncPort syncPort;
    private final EffectiveKnowledgePackageResolver effectivePackageResolver;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate transactionTemplate;
    private final PackageVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;

    public PackageEngineService(
            KnowledgePackageRepository packageRepository,
            PackageItemRepository itemRepository,
            ReleasePlanRepository planRepository,
            IntegrationAdapterRepository adapterRepository,
            SyncLogRepository logRepository,
            RuleDefinitionRepository ruleRepository,
            RuleVersionRepository ruleVersionRepository,
            ConditionFragmentRepository conditionFragmentRepository,
            RuleApplicabilityService ruleApplicabilityService,
            PathwayTemplateRepository pathwayRepository,
            PathwayMilestoneRepository pathwayMilestoneRepository,
            PathwayNodeRepository pathwayNodeRepository,
            PathwayEdgeRepository pathwayEdgeRepository,
            SpecialtyMetricBindingRepository pathwayMetricBindingRepository,
            EvaluationIndicatorRepository evaluationRepository,
            FollowupTemplateRepository followupTemplateRepository,
            KnowledgeIdentityRepository knowledgeIdentityRepository,
            KnowledgeAssetVersionRepository knowledgeVersionRepository,
            TermMappingSnapshotRepository terminologySnapshotRepository,
            TermMappingRepository terminologyMappingRepository,
            PilotPackageTemplateRepository pilotTemplateRepository,
            PilotPackageTemplateItemRepository pilotTemplateItemRepository,
            TenantPackageReferenceRepository packageReferenceRepository,
            InheritanceOverrideService inheritanceOverrideService,
            PackageEntitlementService entitlementService,
            PackageSyncPort syncPort,
            EffectiveKnowledgePackageResolver effectivePackageResolver,
            AuditRecorder auditRecorder,
            TransactionTemplate transactionTemplate,
            PackageVersionedAssetAdapter versionedAssets,
            AssetVersionRepository assetVersions,
            ReleasePort releasePort) {
        this.packageRepository = packageRepository;
        this.itemRepository = itemRepository;
        this.planRepository = planRepository;
        this.adapterRepository = adapterRepository;
        this.logRepository = logRepository;
        this.ruleRepository = ruleRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.ruleApplicabilityService = ruleApplicabilityService;
        this.conditionFragmentRepository = conditionFragmentRepository;
        this.pathwayRepository = pathwayRepository;
        this.pathwayNodeRepository = pathwayNodeRepository;
        this.pathwayEdgeRepository = pathwayEdgeRepository;
        this.pathwayMetricBindingRepository = pathwayMetricBindingRepository;
        this.evaluationRepository = evaluationRepository;
        this.followupTemplateRepository = followupTemplateRepository;
        this.knowledgeIdentityRepository = knowledgeIdentityRepository;
        this.pathwayMilestoneRepository = pathwayMilestoneRepository;
        this.knowledgeVersionRepository = knowledgeVersionRepository;
        this.terminologySnapshotRepository = terminologySnapshotRepository;
        this.terminologyMappingRepository = terminologyMappingRepository;
        this.pilotTemplateRepository = pilotTemplateRepository;
        this.pilotTemplateItemRepository = pilotTemplateItemRepository;
        this.packageReferenceRepository = packageReferenceRepository;
        this.inheritanceOverrideService = inheritanceOverrideService;
        this.entitlementService = entitlementService;
        this.syncPort = syncPort;
        this.effectivePackageResolver = effectivePackageResolver;
        this.auditRecorder = auditRecorder;
        this.transactionTemplate = transactionTemplate;
        this.versionedAssets = versionedAssets;
        this.assetVersions = assetVersions;
        this.releasePort = releasePort;
    }

    /**
     * 创建知识包草稿。
     */
    @Transactional
    public PackageResponse createPackage(PackageCreateRequest request) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        PackageAccessPolicy accessPolicy = request.accessPolicy();
        if (accessPolicy == PackageAccessPolicy.ENTITLED && !PlatformTenant.ID.equals(tenantId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "只有平台主租户可创建受授权控制的知识包");
        }

        // 唯一性检验（同一个编码和版本不能重复）
        Optional<KnowledgePackage> existing = packageRepository
            .findByTenantIdAndPackageCodeAndPackageVersion(tenantId, request.packageCode(), request.packageVersion());
        if (existing.isPresent()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_004, "知识包版本在该租户内已存在: " + request.packageVersion());
        }

        KnowledgePackage pack = new KnowledgePackage(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            request.packageCode(),
            request.packageVersion(),
            request.name(),
            request.description(),
            accessPolicy,
            KnowledgePackageStatus.DRAFT,
            Instant.now(),
            actor,
            Instant.now(),
            actor,
            traceId
        );

        KnowledgePackage saved = packageRepository.save(pack);
        registerPackageDraft(saved, List.of(), actor, traceId);
        auditRecorder.record(AuditAction.CREATE, "knowledge_package", saved.packageId(),
            "创建知识包草稿: " + saved.name() + " (" + saved.packageVersion() + ")");
        return PackageResponse.from(saved);
    }

    /**
     * 获取当前租户下的知识包列表。
     */
    @Transactional(readOnly = true)
    public PageResponse<PackageSummaryResponse> listPackages(PageRequest page) {
        return listPackages(page, new PackageListFilter(null, null, null));
    }

    /**
     * 按关键词、状态和包内资产类型查询当前租户知识包。
     */
    @Transactional(readOnly = true)
    public PageResponse<PackageSummaryResponse> listPackages(PageRequest page, PackageListFilter filter) {
        String tenantId = currentTenantId();
        int offset = page.offset();
        int limit = page.safeSize();
        String keyword = filter.keyword() == null || filter.keyword().isBlank()
            ? null
            : "%" + filter.keyword().trim().toLowerCase() + "%";
        String status = filter.status() == null ? null : filter.status().name();
        String assetType = filter.assetType() == null ? null : filter.assetType().name();
        List<KnowledgePackage> packages = packageRepository.pageByFilter(
            tenantId, keyword, status, assetType, offset, limit);
        long total = packageRepository.countByFilter(tenantId, keyword, status, assetType);
        if (packages.isEmpty()) {
            return PageResponse.of(List.of(), page, total);
        }
        Set<String> packageIds = packages.stream()
            .map(KnowledgePackage::packageId)
            .collect(Collectors.toUnmodifiableSet());
        Map<String, List<PackageItem>> itemsByPackage = itemRepository
            .findByTenantIdAndPackageIdIn(tenantId, packageIds)
            .stream()
            .collect(Collectors.groupingBy(PackageItem::packageId));
        Set<String> packageCodes = packages.stream()
            .map(KnowledgePackage::packageCode)
            .collect(Collectors.toUnmodifiableSet());
        Map<String, AssetVersion> versionsByIdentity = assetVersions
            .findByTenantIdAndAssetTypeAndAssetIdentityIn(
                tenantId, VersionedAssetType.PACKAGE, packageCodes)
            .stream()
            .collect(Collectors.toMap(
                version -> packageVersionKey(version.assetIdentity(), version.versionNo()),
                Function.identity(),
                (left, right) -> left.updatedAt().isAfter(right.updatedAt()) ? left : right
            ));
        List<PackageSummaryResponse> summaries = packages.stream()
            .map(pack -> PackageSummaryResponse.from(
                pack,
                itemsByPackage.getOrDefault(pack.packageId(), List.of()),
                versionsByIdentity.get(packageVersionKey(pack.packageCode(), pack.packageVersion()))
            ))
            .toList();
        return PageResponse.of(summaries, page, total);
    }

    private String packageVersionKey(String packageCode, String packageVersion) {
        return packageCode + "\n" + packageVersion;
    }

    /**
     * 获取包详细信息以及包含的子资产列表。
     */
    @Transactional(readOnly = true)
    public PackageDetailResponse packageDetail(String packageId) {
        String tenantId = currentTenantId();
        KnowledgePackage pack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));

        List<PackageItem> items = itemRepository.findByTenantIdAndPackageId(tenantId, packageId);
        return PackageDetailResponse.from(pack, items);
    }

    /**
     * 向知识包草稿中添加一个子资产条目。
     */
    @Transactional
    public PackageItemResponse addPackageItem(String packageId, PackageItemRequest request) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();

        KnowledgePackage pack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));

        if (pack.status() != KnowledgePackageStatus.DRAFT) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只能向处于 DRAFT 草稿状态的知识包中添加资产");
        }

        // 验证所绑定的资产是否存在及其生命周期状态（未审核的资产如 DRAFT 不可入包）
        validateAssetStatus(tenantId, request.assetType(), request.assetId(), request.assetVersion());

        // 避免重复添加同个资产
        Optional<PackageItem> existing = itemRepository
            .findByTenantIdAndPackageIdAndAssetTypeAndAssetId(tenantId, packageId, request.assetType(), request.assetId());
        if (existing.isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "资产细项已在当前包中声明，无需重复添加");
        }

        PackageItem item = new PackageItem(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            packageId,
            request.assetType(),
            request.assetId(),
            request.assetVersion(),
            Instant.now(),
            actor,
            Instant.now(),
            actor,
            traceId
        );

        List<PackageItem> currentItems = itemRepository.findByTenantIdAndPackageId(tenantId, packageId);
        PackageItem saved = itemRepository.save(item);
        List<PackageItem> updatedItems = new ArrayList<>(currentItems);
        updatedItems.add(saved);
        updatePackageDraft(pack, updatedItems, actor);
        auditRecorder.record(AuditAction.UPDATE, "knowledge_package", packageId,
            "向知识包添加资产条目 (" + request.assetType() + "): " + request.assetId());
        return PackageItemResponse.from(saved);
    }

    /**
     * 查询当前租户可用的试点首发模板；租户模板优先，平台模板兜底。
     */
    @Transactional(readOnly = true)
    public List<PilotPackageTemplateResponse> listPilotTemplates() {
        String tenantId = currentTenantId();
        return activeTemplatesForTenant(tenantId).stream()
            .map(template -> visiblePilotTemplate(tenantId, template))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * 应用试点首发模板的推荐平台包引用，不为租户复制平台包或资产条目。
     */
    @Transactional
    public PilotPackageTemplateApplyResponse applyPilotTemplateReferences(
            String templateCode,
            PilotPackageTemplateApplyRequest request) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        PilotPackageTemplateApplyRequest applyRequest = requireApplyRequest(request);
        String targetOrgUnitId = requireTargetOrgUnitId(applyRequest.targetOrgUnitId());
        PilotPackageTemplate template = resolvePilotTemplate(tenantId, templateCode);
        List<PilotPackageTemplateItem> templateItems = pilotTemplateItemRepository
            .findByTenantIdAndTemplateIdOrderBySortOrderAsc(template.tenantId(), template.templateId());
        if (templateItems.isEmpty()) {
            throw new ApiException(
                ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                "首发模板未配置任何平台包引用: " + template.templateCode()
            );
        }

        List<TenantPackageReferenceResponse> references = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        Instant now = Instant.now();
        for (PilotPackageTemplateItem item : templateItems) {
            if (item.assetType() != VersionedAssetType.PACKAGE) {
                if (item.required()) {
                    blockers.add(item.assetType() + ":" + item.assetId() + " 不是平台包引用");
                }
                continue;
            }
            try {
                KnowledgePackage platformPackage = requirePlatformPackage(
                    tenantId, item.assetId(), item.assetVersion());
                TenantPackageReference reference = referenceFor(
                    tenantId, template.templateCode(), targetOrgUnitId, platformPackage, actor, traceId, now);
                references.add(TenantPackageReferenceResponse.from(reference));
            } catch (ApiException ex) {
                if (item.required()) {
                    blockers.add(item.assetId() + "@" + item.assetVersion() + "：" + ex.getMessage());
                }
            }
        }
        if (!blockers.isEmpty()) {
            throw new ApiException(
                ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                "首发模板依赖平台包缺失或未发布: " + String.join("；", blockers)
            );
        }
        if (references.isEmpty()) {
            throw new ApiException(
                ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                "首发模板未命中可引用平台包: " + template.templateCode()
            );
        }
        List<PilotPackageInitialOverrideResponse> initialOverrides =
            registerInitialOverrides(tenantId, actor, traceId, applyRequest.initialOverrides());
        return new PilotPackageTemplateApplyResponse(template.templateCode(), references, initialOverrides);
    }

    /**
     * 复算配置资产准备状态，供实施向导读取。
     */
    @Transactional(readOnly = true)
    public PackageAssetReadinessResponse getAssetReadiness() {
        String tenantId = currentTenantId();
        int templateCount = (int) activeTemplatesForTenant(tenantId).stream()
            .map(template -> visiblePilotTemplate(tenantId, template))
            .flatMap(Optional::stream)
            .count();
        long draftCount = packageRepository.countByFilter(
            tenantId, null, KnowledgePackageStatus.DRAFT.name(), null);
        long publishedCount = packageRepository.countByFilter(
            tenantId, null, KnowledgePackageStatus.PUBLISHED.name(), null);
        long activeCount = packageRepository.countByFilter(
            tenantId, null, KnowledgePackageStatus.ACTIVE.name(), null);
        long releasedCount = publishedCount + activeCount;
        List<TenantPackageReference> activeReferenceCandidates = packageReferenceRepository
            .findByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, TenantPackageReferenceStatus.ACTIVE);
        Set<String> referencedPackageIds = activeReferenceCandidates.stream()
            .map(TenantPackageReference::platformPackageId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<KnowledgePackage> referencedPackages = referencedPackageIds.isEmpty()
            ? List.of()
            : packageRepository.findByTenantIdAndPackageIdIn(PlatformTenant.ID, referencedPackageIds).stream()
                .filter(this::releasedPackage)
                .toList();
        Set<String> usableReferencedPackageIds =
            entitlementService.usablePackageIds(tenantId, referencedPackages);
        List<TenantPackageReference> activeReferences = activeReferenceCandidates.stream()
            .filter(reference -> usableReferencedPackageIds.contains(reference.platformPackageId()))
            .toList();
        long activeReferenceCount = activeReferences.size();
        String readyPackageId = packageRepository
            .findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, KnowledgePackageStatus.ACTIVE)
            .or(() -> packageRepository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(
                tenantId, KnowledgePackageStatus.PUBLISHED))
            .map(KnowledgePackage::packageId)
            .or(() -> activeReferences.stream()
                .findFirst()
                .map(TenantPackageReference::platformPackageId))
            .orElse(null);
        boolean grayscaleReady = planRepository.countByTenantIdAndStrategyAndStatus(
            tenantId, ReleaseStrategy.GRAYSCALE, ReleasePlanStatus.SUCCESS) > 0;

        List<String> blockers = new ArrayList<>();
        if (templateCount == 0) {
            blockers.add("未配置可用的试点首发模板");
        }
        if (releasedCount == 0 && activeReferenceCount == 0) {
            blockers.add("尚未引用平台配置资产包");
        }
        if (!grayscaleReady) {
            blockers.add("灰度发布尚未成功");
        }
        return new PackageAssetReadinessResponse(
            tenantId,
            blockers.isEmpty(),
            templateCount,
            draftCount,
            releasedCount,
            activeCount,
            activeReferenceCount,
            grayscaleReady,
            readyPackageId,
            blockers,
            Instant.now()
        );
    }

    /**
     * 校验包是否满足发布前基础门禁。
     */
    @Transactional(readOnly = true)
    public PackageValidateResponse validatePackage(String packageId) {
        String tenantId = currentTenantId();
        KnowledgePackage pack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));
        List<PackageItem> items = itemRepository.findByTenantIdAndPackageId(tenantId, packageId);
        List<PackageValidateIssue> issues = new ArrayList<>();
        if (items.isEmpty()) {
            issues.add(new PackageValidateIssue(
                "items",
                "BLOCKING",
                "配置包至少包含一个已审核资产后才能发布"
            ));
        }
        issues.addAll(validatePackageItemDependencies(tenantId, pack, items));
        boolean valid = issues.stream().noneMatch(issue -> "BLOCKING".equals(issue.severity()));
        return new PackageValidateResponse(
            packageId,
            pack.status(),
            items.size(),
            packageContentSha256(pack, items),
            valid,
            issues
        );
    }

    private List<PackageValidateIssue> validatePackageItemDependencies(
            String tenantId,
            KnowledgePackage pack,
            List<PackageItem> items) {
        List<PackageValidateIssue> issues = new ArrayList<>();
        for (PackageItem item : items) {
            try {
                if (embeddedTerminologyItem(pack, item)) {
                    validateEmbeddedTerminologySnapshots(tenantId, item);
                    continue;
                }
                validateAssetStatus(tenantId, item.assetType(), item.assetId(), item.assetVersion());
            } catch (ApiException ex) {
                issues.add(new PackageValidateIssue(
                    itemField(item),
                    "BLOCKING",
                    ex.getMessage()
                ));
            }
        }
        return issues;
    }

    private boolean embeddedTerminologyItem(KnowledgePackage pack, PackageItem item) {
        return item.assetType() == VersionedAssetType.TERMINOLOGY
            && item.packageId().equals(pack.packageId())
            && item.assetVersion().equals(pack.packageVersion())
            && item.assetId().startsWith(pack.packageCode() + "|");
    }

    private void validateEmbeddedTerminologySnapshots(String tenantId, PackageItem item) {
        if (terminologySnapshotRepository
                .findByTenantIdAndPackageItemId(tenantId, item.itemId())
                .isEmpty()) {
            throw new ApiException(
                ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                "术语知识包没有固化任何映射快照: " + item.assetId()
            );
        }
    }

    private String itemField(PackageItem item) {
        return "items[" + item.assetType() + ":" + item.assetId() + "]";
    }

    private String packageContentSha256(KnowledgePackage pack, List<PackageItem> items) {
        List<PackageContentDigestItem> digestItems = items.stream()
            .map(item -> new PackageContentDigestItem(
                item.assetType(),
                item.assetId(),
                item.assetVersion()
            ))
            .sorted(Comparator
                .comparing((PackageContentDigestItem item) -> item.assetType().name())
                .thenComparing(PackageContentDigestItem::assetId)
                .thenComparing(PackageContentDigestItem::assetVersion))
            .toList();
        return sha256Json(new PackageContentDigest(
            pack.packageCode(),
            pack.packageVersion(),
            digestItems
        ));
    }

    private void registerPackageDraft(
            KnowledgePackage pack,
            List<PackageItem> items,
            String actor,
            String traceId) {
        versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            pack.tenantId(),
            VersionedAssetType.PACKAGE,
            packageAssetIdentity(pack),
            pack.packageVersion(),
            packageOrganizationScope(pack),
            "ALL",
            null,
            packageContentSha256(pack, items),
            "knowledge-package:" + pack.packageId(),
            actor,
            traceId
        ));
    }

    private void updatePackageDraft(KnowledgePackage pack, List<PackageItem> items, String actor) {
        AssetVersion assetVersion = assetVersions
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                pack.tenantId(),
                VersionedAssetType.PACKAGE,
                packageAssetIdentity(pack),
                pack.packageVersion()
            )
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "配置包缺少统一资产版本，禁止修改: "
                    + pack.packageCode() + "@" + pack.packageVersion()
            ));
        versionedAssets.updateDraft(new AssetVersionDraftUpdateCommand(
            pack.tenantId(),
            assetVersion.versionId(),
            packageAssetIdentity(pack),
            packageOrganizationScope(pack),
            "ALL",
            null,
            packageContentSha256(pack, items),
            "knowledge-package:" + pack.packageId(),
            assetVersion.safetyPolicy(),
            assetVersion.overridePolicy(),
            actor
        ));
    }

    /**
     * 计算两个包版本之间的资产差异与变动影响分析。
     */
    @Transactional(readOnly = true)
    public PackageDiffResponse calculateDiff(String packageId, String basePackageId) {
        String tenantId = currentTenantId();

        // 校验包存在
        KnowledgePackage targetPack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "目标知识包不存在: " + packageId));

        String baseVersion = "NONE";
        List<PackageItem> baseItems = new ArrayList<>();
        if (basePackageId != null && !basePackageId.isBlank()) {
            KnowledgePackage basePack = packageRepository.findByPackageIdAndTenantId(basePackageId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "基准知识包不存在: " + basePackageId));
            baseVersion = basePack.packageVersion();
            baseItems = itemRepository.findByTenantIdAndPackageId(tenantId, basePackageId);
        }

        List<PackageItem> targetItems = itemRepository.findByTenantIdAndPackageId(tenantId, packageId);

        int added = 0;
        int updated = 0;
        int removed = 0;
        List<String> affectedDepts = new ArrayList<>();
        List<PackageDiffChange> changes = new ArrayList<>();

        for (PackageItem target : targetItems) {
            Optional<PackageItem> matchedBase = baseItems.stream()
                .filter(b -> b.assetType() == target.assetType() && b.assetId().equals(target.assetId()))
                .findFirst();

            if (matchedBase.isEmpty()) {
                added++;
                changes.add(new PackageDiffChange(
                    PackageDiffChangeType.ADDED,
                    target.assetType(),
                    target.assetId(),
                    null,
                    target.assetVersion()
                ));
            } else if (!matchedBase.get().assetVersion().equals(target.assetVersion())) {
                updated++;
                changes.add(new PackageDiffChange(
                    PackageDiffChangeType.UPDATED,
                    target.assetType(),
                    target.assetId(),
                    matchedBase.get().assetVersion(),
                    target.assetVersion()
                ));
            }
            addAffectedDepartment(affectedDepts, getAssetDepartment(tenantId, target.assetType(), target.assetId()));
        }

        for (PackageItem base : baseItems) {
            boolean existsInTarget = targetItems.stream()
                .anyMatch(t -> t.assetType() == base.assetType() && t.assetId().equals(base.assetId()));
            if (!existsInTarget) {
                removed++;
                changes.add(new PackageDiffChange(
                    PackageDiffChangeType.REMOVED,
                    base.assetType(),
                    base.assetId(),
                    base.assetVersion(),
                    null
                ));
                addAffectedDepartment(affectedDepts, getAssetDepartment(tenantId, base.assetType(), base.assetId()));
            }
        }

        return new PackageDiffResponse(
            packageId,
            baseVersion,
            targetPack.packageVersion(),
            added,
            updated,
            removed,
            affectedDepts,
            changes
        );
    }

    /**
     * 导出配置包差异与影响范围证据。
     */
    public String exportDiffEvidence(String packageId, String basePackageId) {
        String traceId = RequestContext.currentTraceId();
        PackageDiffResponse diff = calculateDiff(packageId, basePackageId);
        StringBuilder ndjson = new StringBuilder();

        appendEvidenceExportLine(ndjson, new PackageDiffSummaryExportLine(
            "PACKAGE_DIFF_SUMMARY",
            diff.packageId(),
            diff.baseVersion(),
            diff.targetVersion(),
            diff.addedCount(),
            diff.updatedCount(),
            diff.removedCount(),
            traceId
        ));
        for (String departmentId : diff.affectedDepartments()) {
            appendEvidenceExportLine(ndjson, new PackageDiffDepartmentExportLine(
                "PACKAGE_DIFF_AFFECTED_DEPARTMENT",
                diff.packageId(),
                departmentId,
                traceId
            ));
        }
        for (PackageDiffChange change : diff.changes()) {
            appendEvidenceExportLine(ndjson, new PackageDiffChangeExportLine(
                "PACKAGE_DIFF_CHANGE",
                diff.packageId(),
                change.changeType(),
                change.assetType(),
                change.assetId(),
                change.baseVersion(),
                change.targetVersion(),
                traceId
            ));
        }

        auditRecorder.record(AuditAction.EXPORT, "knowledge_package", packageId,
            "导出配置包差异影响证据，基准版本: " + diff.baseVersion()
                + "，目标版本: " + diff.targetVersion()
                + "，变更资产数: " + diff.changes().size());
        return ndjson.toString();
    }

    /**
     * 导出配置包同步证据与异常适配器清单。
     */
    public String exportSyncEvidence(String packageId) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        KnowledgePackage pack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));
        List<ReleasePlan> plans = planRepository.findByTenantIdAndPackageIdOrderByCreatedAtDesc(tenantId, packageId);
        Map<String, List<SyncLog>> logsByPlanId = new HashMap<>();
        List<SyncLog> allLogs = new ArrayList<>();
        for (ReleasePlan plan : plans) {
            List<SyncLog> logs = logRepository.findByTenantIdAndPlanId(tenantId, plan.planId());
            logsByPlanId.put(plan.planId(), logs);
            allLogs.addAll(logs);
        }

        long successAdapterCount = allLogs.stream()
            .filter(log -> log.status() == SyncLogStatus.SUCCESS)
            .count();
        long failedAdapterCount = allLogs.stream()
            .filter(log -> log.status() == SyncLogStatus.FAILED)
            .count();
        long notSyncedAdapterCount = allLogs.stream()
            .filter(log -> log.status() == SyncLogStatus.NOT_SYNCED)
            .count();

        StringBuilder ndjson = new StringBuilder();
        appendEvidenceExportLine(ndjson, new PackageSyncEvidenceSummaryExportLine(
            "PACKAGE_SYNC_EVIDENCE_SUMMARY",
            packageId,
            pack.packageCode(),
            pack.packageVersion(),
            plans.size(),
            allLogs.size(),
            successAdapterCount,
            failedAdapterCount,
            notSyncedAdapterCount,
            traceId
        ));
        for (ReleasePlan plan : plans) {
            appendEvidenceExportLine(ndjson, new PackageSyncPlanExportLine(
                "PACKAGE_SYNC_PLAN",
                packageId,
                plan.planId(),
                plan.targetOrgUnitId(),
                plan.strategy(),
                plan.scopeType(),
                plan.scopeValue(),
                plan.status(),
                plan.createdAt() == null ? null : plan.createdAt().toString(),
                plan.traceId(),
                traceId
            ));
            for (SyncLog log : logsByPlanId.getOrDefault(plan.planId(), List.of())) {
                appendEvidenceExportLine(ndjson, new PackageReleaseAdapterExportLine(
                    "PACKAGE_RELEASE_ADAPTER",
                    packageId,
                    log.planId(),
                    log.logId(),
                    log.adapterId(),
                    resolveReleaseAdapterName(tenantId, log.adapterId()),
                    log.status(),
                    log.errorCode(),
                    log.errorMessage(),
                    log.retryCount(),
                    log.syncEvidence(),
                    log.traceId(),
                    traceId
                ));
            }
        }

        auditRecorder.record(AuditAction.EXPORT, "knowledge_package", packageId,
            "导出配置包同步证据，发布计划数: " + plans.size()
                + "，同步日志数: " + allLogs.size()
                + "，失败适配器数: " + failedAdapterCount
                + "，未连通适配器数: " + notSyncedAdapterCount);
        return ndjson.toString();
    }

    private String resolveReleaseAdapterName(String tenantId, String adapterId) {
        return adapterRepository.findByAdapterIdAndTenantId(adapterId, tenantId)
            .map(IntegrationAdapter::name)
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .orElse(adapterId);
    }

    private void addAffectedDepartment(List<String> affectedDepartments, String departmentId) {
        if (departmentId != null && !affectedDepartments.contains(departmentId)) {
            affectedDepartments.add(departmentId);
        }
    }

    private void appendEvidenceExportLine(StringBuilder builder, Object line) {
        try {
            builder.append(PACKAGE_JSON_MAPPER.writeValueAsString(line)).append('\n');
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "配置包证据导出失败");
        }
    }

    private record PackageDiffSummaryExportLine(
        String event,
        String packageId,
        String baseVersion,
        String targetVersion,
        int addedCount,
        int updatedCount,
        int removedCount,
        String traceId
    ) {}

    private record PackageDiffDepartmentExportLine(
        String event,
        String packageId,
        String departmentId,
        String traceId
    ) {}

    private record PackageDiffChangeExportLine(
        String event,
        String packageId,
        PackageDiffChangeType changeType,
        VersionedAssetType assetType,
        String assetId,
        String baseVersion,
        String targetVersion,
        String traceId
    ) {}

    private record PackageSyncEvidenceSummaryExportLine(
        String event,
        String packageId,
        String packageCode,
        String packageVersion,
        int planCount,
        int logCount,
        long successAdapterCount,
        long failedAdapterCount,
        long notSyncedAdapterCount,
        String traceId
    ) {}

    private record PackageSyncPlanExportLine(
        String event,
        String packageId,
        String planId,
        String targetOrgUnitId,
        ReleaseStrategy strategy,
        ReleaseScopeType scopeType,
        String scopeValue,
        ReleasePlanStatus status,
        String createdAt,
        String planTraceId,
        String traceId
    ) {}

    private record PackageReleaseAdapterExportLine(
        String event,
        String packageId,
        String planId,
        String logId,
        String adapterId,
        String adapterName,
        SyncLogStatus status,
        String errorCode,
        String errorMessage,
        int retryCount,
        String syncEvidence,
        String logTraceId,
        String traceId
    ) {}

    /**
     * 导出可离线传递的配置包 JSON。
     *
     * <p>导出文件包含逻辑业务标识和当前支持资产的内容快照，不包含数据库自增主键；
     * 完整性摘要基于 payload 的真实字节生成，供后续离线导入验签使用。
     */
    public String exportOfflinePackage(String packageId, String targetOrgUnitId) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String normalizedTargetOrgUnitId = requireOfflineTargetOrgUnitId(targetOrgUnitId);
        KnowledgePackage pack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));
        EffectivePackageSnapshot effectiveSnapshot = buildEffectiveSnapshot(tenantId, pack, normalizedTargetOrgUnitId);

        List<PackageOfflineItem> items = buildOfflineItems(packageId, effectiveSnapshot.items());
        List<PackageOfflineAssetSnapshot> assetSnapshots = buildOfflineAssetSnapshots(effectiveSnapshot.items());
        PackageOfflinePayload payload = new PackageOfflinePayload(
            PackageOfflinePackageInfo.from(pack),
            effectiveSnapshot,
            items,
            assetSnapshots
        );
        String payloadSha256 = sha256Json(payload);
        PackageOfflineExport export = new PackageOfflineExport(
            OFFLINE_PACKAGE_FORMAT,
            new PackageOfflineManifest(
                packageId,
                tenantId,
                pack.packageCode(),
                pack.packageVersion(),
                pack.status(),
                normalizedTargetOrgUnitId,
                effectiveSnapshot.contentSha256(),
                items.size(),
                assetSnapshots.size(),
                effectiveSnapshot.excludedItems().size(),
                effectiveSnapshot.warnings().size(),
                "SHA-256",
                payloadSha256,
                Instant.now().toString(),
                traceId
            ),
            payload
        );

        auditRecorder.record(AuditAction.EXPORT, "knowledge_package", packageId,
            "导出配置包离线安装包，版本: " + pack.packageVersion()
                + "，目标组织: " + normalizedTargetOrgUnitId
                + "，有效快照: " + effectiveSnapshot.contentSha256()
                + "，资产条目数: " + items.size()
                + "，payloadSha256: " + payloadSha256);
        return writeOfflineJson(export);
    }

    /**
     * 导入离线配置包，完成完整性验签后以本地草案落库。
     *
     * <p>离线包携带逻辑业务标识和资产内容快照；导入端必须生成新的本地包 ID 与条目 ID，
     * 且不能绕过本院发布流程直接激活。
     */
    @Transactional
    public PackageOfflineImportResponse importOfflinePackage(PackageOfflineImportRequest request) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();

        JsonNode root = parseOfflinePackage(request);
        ensureOfflineFormat(root);
        JsonNode manifest = requireObject(root, "manifest", "离线包缺少 manifest 清单");
        JsonNode payload = requireObject(root, "payload", "离线包缺少 payload 内容");
        JsonNode packageInfo = requireObject(payload, "packageInfo", "离线包缺少 packageInfo 包元信息");
        JsonNode effectiveSnapshotNode = requireObject(payload, "effectiveSnapshot", "离线包缺少 effectiveSnapshot 有效快照");
        JsonNode itemsNode = requireArray(payload, "items", "离线包缺少 items 资产条目列表");
        JsonNode assetSnapshotsNode = requireArray(payload, "assetSnapshots", "离线包缺少 assetSnapshots 资产内容快照");
        EffectivePackageSnapshot effectiveSnapshot = readOfflineContent(effectiveSnapshotNode, EffectivePackageSnapshot.class);

        String packageCode = requireText(packageInfo, "packageCode", "离线包缺少 packageCode");
        String packageVersion = requireText(packageInfo, "packageVersion", "离线包缺少 packageVersion");
        String sourcePackageId = requireText(packageInfo, "packageId", "离线包缺少源 packageId");
        String sourceTenantId = requireText(packageInfo, "tenantId", "离线包缺少 tenantId");
        String packageName = requireText(packageInfo, "name", "离线包缺少包名称");
        String packageDescription = optionalText(packageInfo, "description");

        String hashAlgorithm = requireText(manifest, "hashAlgorithm", "离线包缺少摘要算法");
        if (!"SHA-256".equals(hashAlgorithm)) {
            throw new ApiException(ErrorCode.ENG_EVID_002, "离线包摘要算法不受支持: " + hashAlgorithm);
        }
        String declaredSha256 = requireText(manifest, "payloadSha256", "离线包缺少 payloadSha256");
        if (!declaredSha256.matches("[a-f0-9]{64}")) {
            throw new ApiException(ErrorCode.ENG_EVID_002, "离线包 payloadSha256 格式不合法");
        }
        String actualSha256 = sha256Json(payload);
        if (!declaredSha256.equals(actualSha256)) {
            throw new ApiException(ErrorCode.ENG_EVID_002, "离线包 payloadSha256 与实际内容不一致");
        }

        validateManifestPayloadMatch(manifest, packageInfo, sourcePackageId, sourceTenantId, packageCode, packageVersion);
        String targetOrgUnitId = requireText(manifest, "targetOrgUnitId", "离线包缺少 targetOrgUnitId");
        String declaredEffectiveSnapshotSha256 =
            requireText(manifest, "effectiveSnapshotSha256", "离线包缺少 effectiveSnapshotSha256");
        validateEffectiveSnapshot(
            effectiveSnapshot, sourcePackageId, packageCode, packageVersion, targetOrgUnitId,
            declaredEffectiveSnapshotSha256);
        validateOfflineItemsMatchEffectiveSnapshot(itemsNode, sourcePackageId, effectiveSnapshot);
        validateOfflineImportTenantLineage(tenantId, sourceTenantId);

        int itemCount = requireInt(manifest, "itemCount", "离线包 itemCount 不合法");
        if (itemCount != itemsNode.size() || itemCount != effectiveSnapshot.items().size()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包 itemCount 与有效快照资产条目数量不一致");
        }
        int assetSnapshotCount = requireInt(manifest, "assetSnapshotCount", "离线包 assetSnapshotCount 不合法");
        if (assetSnapshotCount != assetSnapshotsNode.size() || assetSnapshotCount != itemsNode.size()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产内容快照数量与资产条目数量不一致");
        }
        int excludedItemCount = requireInt(manifest, "excludedItemCount", "离线包 excludedItemCount 不合法");
        if (excludedItemCount != effectiveSnapshot.excludedItems().size()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包 excludedItemCount 与有效快照排除项数量不一致");
        }
        int warningCount = requireInt(manifest, "warningCount", "离线包 warningCount 不合法");
        if (warningCount != effectiveSnapshot.warnings().size()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包 warningCount 与有效快照警告数量不一致");
        }
        if (packageRepository
            .findByTenantIdAndPackageCodeAndPackageVersion(tenantId, packageCode, packageVersion)
            .isPresent()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_004, "知识包版本在该租户内已存在: " + packageVersion);
        }

        Instant now = Instant.now();
        importOfflineAssetSnapshots(
            assetSnapshotsNode, itemsNode, tenantId, sourceTenantId, actor, traceId, now);
        KnowledgePackage importedPackage = new KnowledgePackage(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            packageCode,
            packageVersion,
            packageName,
            packageDescription,
            KnowledgePackageStatus.DRAFT,
            now,
            actor,
            now,
            actor,
            traceId
        );
        KnowledgePackage savedPackage = packageRepository.save(importedPackage);
        List<PackageItem> importedItems = buildOfflineImportItems(
            itemsNode, tenantId, savedPackage.packageId(), sourcePackageId,
            actor, traceId, now);
        importedItems.forEach(itemRepository::save);
        registerPackageDraft(savedPackage, importedItems, actor, traceId);

        auditRecorder.record(AuditAction.IMPORT, "knowledge_package", savedPackage.packageId(),
            "导入配置包离线安装包为草案，版本: " + packageVersion
                + "，源租户: " + sourceTenantId
                + "，源包: " + sourcePackageId
                + "，资产条目数: " + importedItems.size()
                + "，payloadSha256: " + actualSha256);
        return new PackageOfflineImportResponse(
            savedPackage.packageId(),
            savedPackage.packageCode(),
            savedPackage.packageVersion(),
            savedPackage.status(),
            importedItems.size(),
            actualSha256
        );
    }

    private String requireOfflineTargetOrgUnitId(String targetOrgUnitId) {
        String normalized = normalizedText(targetOrgUnitId);
        if (normalized == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线导出目标组织 ID 不能为空");
        }
        return normalized;
    }

    private List<PackageOfflineItem> buildOfflineItems(
            String packageId,
            List<EffectivePackageItem> effectiveItems) {
        return effectiveItems.stream()
            .map(item -> PackageOfflineItem.from(packageId, item))
            .toList();
    }

    private List<PackageOfflineAssetSnapshot> buildOfflineAssetSnapshots(List<EffectivePackageItem> items) {
        return items.stream()
            .map(this::buildOfflineAssetSnapshot)
            .toList();
    }

    private PackageOfflineAssetSnapshot buildOfflineAssetSnapshot(EffectivePackageItem item) {
        String sourceTenantId = normalizedText(item.sourceTenantId());
        if (sourceTenantId == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "有效包条目缺少来源租户: " + item.assetType() + ":" + item.assetId());
        }
        String effectiveVersion = normalizedText(item.effectiveVersion());
        if (effectiveVersion == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "有效包条目缺少生效版本: " + item.assetType() + ":" + item.assetId());
        }
        JsonNode content = switch (item.assetType()) {
            case RULE -> buildRuleAssetContent(
                ruleRepository.findByRuleIdAndTenantId(item.assetId(), sourceTenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "离线导出规则不存在: " + item.assetId())),
                ruleVersionRepository.findByRuleIdAndTenantIdAndVersionNo(
                    item.assetId(), sourceTenantId, parseAssetVersionNo(effectiveVersion))
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "离线导出规则版本不存在: " + item.assetId() + "@" + effectiveVersion))
            );
            case PATHWAY -> {
                PathwayTemplate template = pathwayRepository
                    .findByTemplateIdAndTenantId(item.assetId(), sourceTenantId)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.ENG_PATHWAY_002,
                        "离线导出路径模板不存在: " + item.assetId()
                    ));
                if (!Integer.toString(template.templateVersion()).equals(effectiveVersion)) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "离线导出路径版本不存在: " + item.assetId() + "@" + effectiveVersion
                    );
                }
                yield buildPathwayAssetContent(
                    template,
                    pathwayMilestoneRepository.findByTemplateIdAndTenantIdOrderBySortOrderAsc(
                        item.assetId(), sourceTenantId),
                    pathwayNodeRepository.findByTemplateIdAndTenantIdOrderBySortOrderAsc(
                        item.assetId(), sourceTenantId),
                    pathwayEdgeRepository.findByTemplateIdAndTenantIdOrderByPriorityAsc(
                        item.assetId(), sourceTenantId),
                    pathwayMetricBindingRepository.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(
                        item.assetId(), sourceTenantId)
                );
            }
            case EVALUATION -> buildEvaluationAssetContent(
                evaluationRepository.findByIndicatorIdAndTenantId(item.assetId(), sourceTenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_002, "离线导出评估指标不存在: " + item.assetId()))
            );
            case KNOWLEDGE -> {
                KnowledgeIdentity identity = knowledgeIdentityRepository.findByTenantIdAndIdentityCode(sourceTenantId, item.assetId())
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_002, "离线导出知识身份不存在: " + item.assetId()));
                KnowledgeAssetVersion version = knowledgeVersionRepository
                    .findByTenantIdAndIdentityIdAndVersionNo(sourceTenantId, identity.id(), effectiveVersion)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "离线导出知识版本不存在: " + item.assetId() + "@" + effectiveVersion
                    ));
                yield buildKnowledgeAssetContent(identity, version);
            }
            case TERMINOLOGY -> {
                TerminologyAssetKey key = parseTerminologyAssetKey(item.assetId());
                KnowledgePackage terminologyPackage = packageRepository
                    .findByTenantIdAndPackageCodeAndPackageVersion(
                        sourceTenantId, key.packageCode(), effectiveVersion)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "离线导出术语知识包不存在: " + item.assetId() + "@" + effectiveVersion
                    ));
                PackageItem terminologyItem = itemRepository
                    .findByTenantIdAndPackageIdAndAssetTypeAndAssetId(
                        sourceTenantId,
                        terminologyPackage.packageId(),
                        VersionedAssetType.TERMINOLOGY,
                        item.assetId()
                    )
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "离线导出术语知识包缺少快照条目: " + item.assetId()
                    ));
                yield buildTerminologyAssetContent(terminologyPackage, terminologyItem);
            }
            case CONDITION_FRAGMENT -> buildConditionFragmentAssetContent(
                conditionFragmentRepository.findByFragmentIdAndTenantId(item.assetId(), sourceTenantId)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "离线导出条件片段不存在: " + item.assetId()
                    ))
            );
            case FOLLOWUP -> buildFollowupTemplateAssetContent(
                followupTemplateRepository.findByTemplateIdAndTenantId(item.assetId(), sourceTenantId)
                    .filter(template -> Integer.toString(template.versionNo()).equals(effectiveVersion))
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "离线导出随访模板版本不存在: " + item.assetId() + "@" + effectiveVersion
                    )),
                item
            );
            default -> {
                if (isDeclarativePackageAssetType(item.assetType())) {
                    yield buildDeclarativeAssetContent(item);
                }
                throw new ApiException(
                    ErrorCode.ENG_PACKAGE_002,
                    "配置包包含不允许迁移的资产类型: " + item.assetType());
            }
        };
        return new PackageOfflineAssetSnapshot(
            item.assetType(),
            item.assetId(),
            item.declaredVersion(),
            effectiveVersion,
            sourceTenantId,
            item.sourceVersionId(),
            item.contentHash(),
            sha256Json(content),
            content
        );
    }

    private JsonNode buildRuleAssetContent(RuleDefinition rule, RuleVersion version) {
        return OFFLINE_EXPORT_MAPPER.valueToTree(new PackageOfflineRuleContent(
            new PackageOfflineRuleDefinition(
                rule.ruleId(),
                rule.ruleCode(),
                rule.name(),
                enumName(rule.ruleType()),
                enumName(rule.authoringMode()),
                enumName(rule.riskLevel()),
                rule.priority(),
                rule.suppressedBy(),
                rule.dedupeWindowSeconds(),
                enumName(rule.status()),
                rule.activeVersionId(),
                rule.packageVersion(),
                rule.applicableOrgUnitId()
            ),
            new PackageOfflineRuleVersion(
                version.versionId(),
                version.versionNo(),
                version.sourceRef(),
                version.changeSummary(),
                version.dslJson(),
                version.explanationJson(),
                enumName(version.status()),
                instantText(version.publishedAt()),
                version.publishedBy(),
                version.rollbackVersionId()
            )
        ));
    }

    private JsonNode buildEvaluationAssetContent(EvaluationIndicator indicator) {
        return OFFLINE_EXPORT_MAPPER.valueToTree(new PackageOfflineEvaluationContent(
            new PackageOfflineEvaluationIndicator(
                indicator.indicatorId(),
                indicator.indicatorCode(),
                indicator.versionNo(),
                indicator.name(),
                enumName(indicator.subjectType()),
                indicator.denominatorDefinition(),
                indicator.numeratorDefinition(),
                indicator.exclusionDefinition(),
                indicator.scoringDefinition(),
                indicator.timeWindow(),
                indicator.organizationScope(),
                indicator.responsibleDepartmentId(),
                indicator.sourceRef(),
                indicator.packageVersion(),
                enumName(indicator.status()),
                instantText(indicator.publishedAt()),
                indicator.publishedBy(),
                instantText(indicator.activatedAt())
            )
        ));
    }

    private JsonNode buildConditionFragmentAssetContent(ConditionFragment fragment) {
        return OFFLINE_EXPORT_MAPPER.valueToTree(new PackageOfflineConditionFragmentContent(
            new PackageOfflineConditionFragment(
                fragment.fragmentId(),
                fragment.fragmentCode(),
                fragment.name(),
                fragment.category(),
                fragment.bodyJson(),
                fragment.versionNo(),
                enumName(fragment.status()),
                fragment.packageVersion(),
                instantText(fragment.createdAt()),
                fragment.createdBy(),
                instantText(fragment.updatedAt()),
                fragment.updatedBy(),
                fragment.traceId()
            )
        ));
    }

    private JsonNode buildFollowupTemplateAssetContent(
            FollowupTemplate template,
            EffectivePackageItem item) {
        return OFFLINE_EXPORT_MAPPER.valueToTree(new PackageOfflineFollowupContent(
            new PackageOfflineFollowupTemplate(
                template.templateId(),
                template.templateCode(),
                template.versionNo(),
                template.name(),
                template.description(),
                template.organizationScope(),
                template.applicableScope(),
                template.taskDefinitionJson(),
                template.questionnaireDefinitionJson(),
                template.abnormalActionJson(),
                template.sourceRef()
            ),
            item.contentHash(),
            AssetVersionStatus.PUBLISHED.name()
        ));
    }

    private JsonNode buildDeclarativeAssetContent(EffectivePackageItem item) {
        return OFFLINE_EXPORT_MAPPER.valueToTree(new PackageOfflineDeclarativeAssetContent(
            item.assetType(),
            item.assetId(),
            item.effectiveVersion(),
            item.effectiveVersion(),
            item.sourceTenantId(),
            item.sourceVersionId(),
            item.contentHash(),
            "DECLARATIVE_VERSIONED_ASSET"
        ));
    }

    private boolean isDeclarativePackageAssetType(VersionedAssetType assetType) {
        return DECLARATIVE_PACKAGE_ASSET_TYPES.contains(assetType);
    }

    private JsonNode buildPathwayAssetContent(
            PathwayTemplate template,
            List<PathwayMilestone> milestones,
            List<PathwayNode> nodes,
            List<PathwayEdge> edges,
            List<SpecialtyMetricBinding> metricBindings) {
        return OFFLINE_EXPORT_MAPPER.valueToTree(new PackageOfflinePathwayContent(
            new PackageOfflinePathwayTemplate(
                template.templateId(),
                template.packageId(),
                template.templateCode(),
                template.name(),
                template.diseaseCode(),
                template.templateVersion(),
                enumName(template.templateLevel()),
                enumName(template.status()),
                enumName(template.entryMode()),
                template.startNodeCode(),
                template.sourceRef(),
                template.description(),
                template.entryCriteriaJson(),
                template.exitCriteriaJson()
            ),
            milestones.stream().map(milestone -> new PackageOfflinePathwayMilestone(
                milestone.milestoneId(),
                milestone.phaseCode(),
                milestone.phaseName(),
                milestone.milestoneCode(),
                milestone.name(),
                milestone.dayOffset(),
                milestone.expectedOffsetMinutes(),
                milestone.achievementCriteriaJson(),
                milestone.sortOrder()
            )).toList(),
            nodes.stream().map(node -> new PackageOfflinePathwayNode(
                node.nodeId(),
                node.nodeCode(),
                node.name(),
                enumName(node.nodeType()),
                node.milestoneCode(),
                node.sortOrder(),
                node.responsibleRole(),
                node.dependencyJson(),
                node.timeWindowMinutes(),
                node.terminalFlag(),
                node.configJson()
            )).toList(),
            edges.stream().map(edge -> new PackageOfflinePathwayEdge(
                edge.edgeId(),
                edge.edgeCode(),
                edge.fromNodeCode(),
                edge.toNodeCode(),
                enumName(edge.edgeType()),
                edge.conditionJson(),
                edge.priority()
            )).toList(),
            metricBindings.stream().map(binding -> new PackageOfflinePathwayMetricBinding(
                binding.bindingId(),
                binding.packageId(),
                binding.nodeCode(),
                binding.metricCode(),
                binding.requiredFlag()
            )).toList()
        ));
    }

    private JsonNode buildKnowledgeAssetContent(KnowledgeIdentity identity, KnowledgeAssetVersion version) {
        return OFFLINE_EXPORT_MAPPER.valueToTree(new PackageOfflineKnowledgeContent(
            new PackageOfflineKnowledgeIdentity(
                identity.identityCode(),
                enumName(identity.domain()),
                identity.subject(),
                identity.specialtyId(),
                identity.description(),
                enumName(identity.status()),
                version.versionNo()
            ),
            new PackageOfflineKnowledgeVersion(
                version.versionNo(),
                version.versionLabel(),
                version.sourceDocumentId(),
                version.sourceVersionId(),
                version.contentHash(),
                version.anchors(),
                enumName(version.status()),
                enumName(version.riskLevel()),
                enumName(version.authorityLevel()),
                enumName(version.gradeQuality()),
                enumName(version.gradeStrength()),
                version.conflictArbitration(),
                instantText(version.effectiveFrom()),
                instantText(version.effectiveTo()),
                version.reviewedBy(),
                instantText(version.reviewedAt()),
                instantText(version.activatedAt()),
                instantText(version.supersededAt()),
                instantText(version.withdrawnAt()),
                version.withdrawnReason(),
                version.reviewCycleMonths(),
                instantText(version.nextReviewAt())
            )
        ));
    }

    private JsonNode buildTerminologyAssetContent(
            KnowledgePackage terminologyPackage,
            PackageItem terminologyItem) {
        List<TermMappingSnapshotEntity> packageItems = terminologySnapshotRepository
            .findByTenantIdAndPackageItemId(
                terminologyPackage.tenantId(), terminologyItem.itemId()).stream()
            .sorted(Comparator.comparing(
                TermMappingSnapshotEntity::mappingId,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        List<TermMappingSnapshot> snapshots = packageItems.stream()
            .map(TermMappingSnapshotEntity::mappingSnapshot)
            .map(TermMappingSnapshotCodec::read)
            .toList();
        List<PackageOfflineTermMapping> mappings = snapshots.stream()
            .map(snapshot -> new PackageOfflineTermMapping(
                snapshot.localTermId(),
                snapshot.standardTermId(),
                snapshot.sourceSystem(),
                snapshot.category(),
                snapshot.confidence(),
                snapshot.riskLevel(),
                snapshot.status(),
                snapshot.evidenceText(),
                snapshot.confirmedBy(),
                snapshot.confirmedAt()
            ))
            .toList();
        List<PackageOfflineTermMappingSnapshot> items = packageItems.stream()
            .map(item -> new PackageOfflineTermMappingSnapshot(item.mappingSnapshot()))
            .toList();
        TerminologyAssetKey key = parseTerminologyAssetKey(terminologyItem.assetId());
        AssetVersion packageVersion = assetVersions
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                terminologyPackage.tenantId(),
                VersionedAssetType.PACKAGE,
                terminologyPackage.packageCode(),
                terminologyPackage.packageVersion()
            )
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "离线导出术语知识包缺少统一 PACKAGE 版本: "
                    + terminologyPackage.packageCode() + "@" + terminologyPackage.packageVersion()
            ));
        return OFFLINE_EXPORT_MAPPER.valueToTree(new PackageOfflineTerminologyContent(
            new PackageOfflineTerminologyKnowledgePackage(
                terminologyPackage.packageId(),
                terminologyPackage.packageCode(),
                terminologyPackage.packageVersion(),
                terminologyPackage.name(),
                terminologyPackage.description(),
                terminologyPackage.status().name(),
                key.scopeLevel(),
                key.scopeCode(),
                packageVersion.contentHash()
            ),
            mappings,
            items
        ));
    }

    private void importOfflineAssetSnapshots(
            JsonNode assetSnapshotsNode,
            JsonNode itemsNode,
            String tenantId,
            String sourceTenantId,
            String actor,
            String traceId,
            Instant now) {
        Map<String, JsonNode> snapshotsByKey = new HashMap<>();
        Map<String, String> itemContentHashesByKey = offlineItemContentHashes(itemsNode);
        for (JsonNode snapshotNode : assetSnapshotsNode) {
            if (snapshotNode == null || !snapshotNode.isObject()) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产内容快照必须是对象");
            }
            VersionedAssetType assetType = parseAssetType(requireText(snapshotNode, "assetType", "离线包资产快照缺少 assetType"));
            String assetId = requireText(snapshotNode, "assetId", "离线包资产快照缺少 assetId");
            String effectiveVersion = requireText(snapshotNode, "effectiveVersion", "离线包资产快照缺少 effectiveVersion");
            String itemSourceTenantId = requireText(snapshotNode, "sourceTenantId", "离线包资产快照缺少 sourceTenantId");
            validateOfflineItemSourceLineage(tenantId, itemSourceTenantId);
            String contentHash = requireText(snapshotNode, "contentHash", "离线包资产快照缺少 contentHash");
            if (!contentHash.matches("[a-f0-9]{64}")) {
                throw new ApiException(ErrorCode.ENG_EVID_002, "离线包资产版本 contentHash 格式不合法");
            }
            String contentSha256 = requireText(snapshotNode, "contentSha256", "离线包资产快照缺少 contentSha256");
            if (!contentSha256.matches("[a-f0-9]{64}")) {
                throw new ApiException(ErrorCode.ENG_EVID_002, "离线包资产内容摘要格式不合法");
            }
            JsonNode content = requireObject(snapshotNode, "content", "离线包资产快照缺少 content 内容");
            String actualSha256 = sha256Json(content);
            if (!contentSha256.equals(actualSha256)) {
                throw new ApiException(ErrorCode.ENG_EVID_002, "离线包资产内容摘要与实际内容不一致");
            }
            String key = offlineAssetKey(assetType, itemSourceTenantId, assetId, effectiveVersion);
            String itemContentHash = itemContentHashesByKey.get(key);
            if (itemContentHash == null) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产内容快照不在有效资产条目内: " + key);
            }
            if (!itemContentHash.equals(contentHash)) {
                throw new ApiException(ErrorCode.ENG_EVID_002, "离线包资产内容快照 contentHash 与有效资产条目不一致");
            }
            if (snapshotsByKey.putIfAbsent(key, snapshotNode) != null) {
                throw new ApiException(ErrorCode.CONFLICT, "离线包内存在重复资产内容快照: " + key);
            }
        }

        for (JsonNode itemNode : itemsNode) {
            VersionedAssetType assetType = parseAssetType(requireText(itemNode, "assetType", "离线包资产条目缺少 assetType"));
            String assetId = requireText(itemNode, "assetId", "离线包资产条目缺少 assetId");
            String effectiveVersion = requireText(itemNode, "effectiveVersion", "离线包资产条目缺少 effectiveVersion");
            String itemSourceTenantId = requireText(itemNode, "sourceTenantId", "离线包资产条目缺少 sourceTenantId");
            validateOfflineItemSourceLineage(tenantId, itemSourceTenantId);
            String key = offlineAssetKey(assetType, itemSourceTenantId, assetId, effectiveVersion);
            JsonNode snapshot = snapshotsByKey.get(key);
            if (snapshot == null) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包缺少资产内容快照: " + key);
            }
            importOfflineAssetSnapshot(
                assetType,
                assetId,
                effectiveVersion,
                snapshot,
                tenantId,
                itemSourceTenantId,
                actor,
                traceId,
                now);
        }
    }

    private Map<String, String> offlineItemContentHashes(JsonNode itemsNode) {
        Map<String, String> contentHashesByKey = new HashMap<>();
        for (JsonNode itemNode : itemsNode) {
            if (itemNode == null || !itemNode.isObject()) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产条目必须是对象");
            }
            VersionedAssetType assetType = parseAssetType(requireText(itemNode, "assetType", "离线包资产条目缺少 assetType"));
            String assetId = requireText(itemNode, "assetId", "离线包资产条目缺少 assetId");
            String effectiveVersion = requireText(itemNode, "effectiveVersion", "离线包资产条目缺少 effectiveVersion");
            String itemSourceTenantId = requireText(itemNode, "sourceTenantId", "离线包资产条目缺少 sourceTenantId");
            String contentHash = requireText(itemNode, "contentHash", "离线包资产条目缺少 contentHash");
            if (!contentHash.matches("[a-f0-9]{64}")) {
                throw new ApiException(ErrorCode.ENG_EVID_002, "离线包资产条目 contentHash 格式不合法");
            }
            String key = offlineAssetKey(assetType, itemSourceTenantId, assetId, effectiveVersion);
            if (contentHashesByKey.putIfAbsent(key, contentHash) != null) {
                throw new ApiException(ErrorCode.CONFLICT, "离线包内存在重复资产条目: " + key);
            }
        }
        return contentHashesByKey;
    }

    private void importOfflineAssetSnapshot(
            VersionedAssetType assetType,
            String assetId,
            String assetVersion,
            JsonNode snapshot,
            String tenantId,
            String sourceTenantId,
            String actor,
            String traceId,
            Instant now) {
        JsonNode content = requireObject(snapshot, "content", "离线包资产快照缺少 content 内容");
        if (isPlatformSourceReferenceImport(tenantId, sourceTenantId)) {
            validateOfflineAssetSnapshotContent(assetType, assetId, assetVersion, content);
            return;
        }
        switch (assetType) {
            case RULE -> importOfflineRuleSnapshot(assetId, assetVersion, content, tenantId, actor, traceId, now);
            case PATHWAY -> importOfflinePathwaySnapshot(assetId, assetVersion, content, tenantId, actor, traceId, now);
            case EVALUATION -> importOfflineEvaluationSnapshot(assetId, assetVersion, content, tenantId, actor, traceId, now);
            case KNOWLEDGE -> importOfflineKnowledgeSnapshot(
                assetId, assetVersion, content, tenantId, actor, traceId, now);
            case TERMINOLOGY -> importOfflineTerminologySnapshot(
                assetId, assetVersion, content, tenantId, actor, traceId, now);
            case CONDITION_FRAGMENT -> importOfflineConditionFragmentSnapshot(
                assetId, assetVersion, content, tenantId, actor, traceId, now);
            case FOLLOWUP -> importOfflineFollowupTemplateSnapshot(
                assetId, assetVersion, content, tenantId, actor, traceId, now);
            default -> {
                if (isDeclarativePackageAssetType(assetType)) {
                    importOfflineDeclarativeAssetSnapshot(
                        assetType, assetId, assetVersion, content, tenantId, actor, traceId, now);
                    return;
                }
                throw new ApiException(
                    ErrorCode.ENG_PACKAGE_002,
                    "配置包包含不允许迁移的资产类型: " + assetType);
            }
        }
    }

    private void validateOfflineAssetSnapshotContent(
            VersionedAssetType assetType,
            String assetId,
            String assetVersion,
            JsonNode content) {
        switch (assetType) {
            case RULE -> {
                PackageOfflineRuleContent ruleContent = readOfflineContent(content, PackageOfflineRuleContent.class);
                if (!assetId.equals(ruleContent.rule().ruleId())) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包规则快照 ruleId 与资产条目不一致");
                }
                if (!Integer.valueOf(parseAssetVersionNo(assetVersion)).equals(ruleContent.version().versionNo())) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包规则快照 versionNo 与资产条目不一致");
                }
                ensurePackageAssetPublished("规则", ruleContent.rule().status());
                ruleApplicabilityService.validateDsl(readOfflineRuleDsl(ruleContent.version()));
            }
            case PATHWAY -> validateOfflinePathwayContent(assetId, assetVersion, content);
            case EVALUATION -> {
                PackageOfflineEvaluationContent evaluationContent =
                    readOfflineContent(content, PackageOfflineEvaluationContent.class);
                PackageOfflineEvaluationIndicator indicator = evaluationContent.indicator();
                if (!assetId.equals(indicator.indicatorId())) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包评估指标快照 indicatorId 与资产条目不一致");
                }
                if (!Integer.toString(indicator.versionNo()).equals(assetVersion)) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包评估指标快照 versionNo 与资产条目不一致");
                }
                ensurePackageAssetPublished("评估指标", indicator.status());
            }
            case KNOWLEDGE -> {
                PackageOfflineKnowledgeContent knowledgeContent =
                    readOfflineContent(content, PackageOfflineKnowledgeContent.class);
                if (!assetId.equals(knowledgeContent.identity().identityCode())) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包知识快照 identityCode 与资产条目不一致");
                }
                if (!assetVersion.equals(knowledgeContent.version().versionNo())) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包知识快照 versionNo 与资产条目不一致");
                }
                ensurePackageAssetPublished("知识版本", knowledgeContent.version().status());
                if (!"ACTIVE".equalsIgnoreCase(knowledgeContent.identity().status())) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                        "离线包知识身份必须为 ACTIVE 状态, 当前: " + knowledgeContent.identity().status());
                }
            }
            case TERMINOLOGY -> {
                PackageOfflineTerminologyContent terminologyContent =
                    readOfflineContent(content, PackageOfflineTerminologyContent.class);
                PackageOfflineTerminologyKnowledgePackage terminologyPackage =
                    terminologyContent.knowledgePackage();
                TerminologyAssetKey key = parseTerminologyAssetKey(assetId);
                if (!key.packageCode().equals(terminologyPackage.packageCode())
                        || !key.scopeLevel().equals(terminologyPackage.scopeLevel())
                        || !key.scopeCode().equals(terminologyPackage.scopeCode())) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包术语映射包快照业务键与资产条目不一致");
                }
                if (!assetVersion.equals(terminologyPackage.packageVersion())) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包术语映射包版本与资产条目不一致");
                }
                ensureTerminologyPackageReleased(terminologyPackage.status());
                validateOfflineTerminologyMappings(terminologyContent.mappings());
                validateOfflineTerminologySnapshots(terminologyContent);
            }
            case CONDITION_FRAGMENT -> validateOfflineConditionFragmentContent(assetId, assetVersion, content);
            case FOLLOWUP -> validateOfflineFollowupContent(assetId, assetVersion, content);
            default -> {
                if (isDeclarativePackageAssetType(assetType)) {
                    validateOfflineDeclarativeAssetContent(assetType, assetId, assetVersion, content);
                    return;
                }
                throw new ApiException(
                    ErrorCode.ENG_PACKAGE_002,
                    "配置包包含不允许迁移的资产类型: " + assetType);
            }
        }
    }

    private void ensurePackageAssetPublished(String assetName, String status) {
        if (!"PUBLISHED".equalsIgnoreCase(status) && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包" + assetName + "必须为 PUBLISHED 或 ACTIVE 状态, 当前: " + status);
        }
    }

    private void validateOfflineConditionFragmentContent(
            String assetId,
            String assetVersion,
            JsonNode content) {
        PackageOfflineConditionFragmentContent fragmentContent =
            readOfflineContent(content, PackageOfflineConditionFragmentContent.class);
        PackageOfflineConditionFragment fragment = fragmentContent.fragment();
        if (fragment == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包条件片段快照缺少 fragment");
        }
        if (!assetId.equals(fragment.fragmentId())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包条件片段 fragmentId 与资产条目不一致");
        }
        if (!Integer.toString(fragment.versionNo()).equals(assetVersion)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包条件片段 versionNo 与资产条目不一致");
        }
        if (normalizedText(fragment.fragmentCode()) == null
                || normalizedText(fragment.name()) == null
                || normalizedText(fragment.bodyJson()) == null
                || normalizedText(fragment.packageVersion()) == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包条件片段缺少编码、名称、正文或包版本");
        }
        ConditionFragmentStatus status = parseEnum(
            ConditionFragmentStatus.class,
            fragment.status(),
            "条件片段状态"
        );
        if (status != ConditionFragmentStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包条件片段必须为 ACTIVE 状态, 当前: " + fragment.status());
        }
    }

    private void validateOfflineFollowupContent(
            String assetId,
            String assetVersion,
            JsonNode content) {
        PackageOfflineFollowupContent followup =
            readOfflineContent(content, PackageOfflineFollowupContent.class);
        PackageOfflineFollowupTemplate template = followup.template();
        if (template == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包随访模板快照缺少 template");
        }
        if (!assetId.equals(template.templateId())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包随访模板 templateId 与资产条目不一致");
        }
        if (!Integer.toString(template.versionNo()).equals(assetVersion)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包随访模板 versionNo 与资产条目不一致");
        }
        if (normalizedText(template.templateCode()) == null
                || normalizedText(template.name()) == null
                || normalizedText(template.organizationScope()) == null
                || normalizedText(template.applicableScope()) == null
                || normalizedText(template.taskDefinitionJson()) == null
                || normalizedText(template.questionnaireDefinitionJson()) == null
                || normalizedText(template.abnormalActionJson()) == null
                || normalizedText(template.sourceRef()) == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包随访模板缺少必需配置");
        }
        if (normalizedText(followup.contentHash()) == null
                || !followup.contentHash().matches("[a-f0-9]{64}")) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包随访模板内容摘要不合法");
        }
        ensurePackageAssetPublished("随访模板", followup.status());
        requireJsonArray(template.taskDefinitionJson(), "离线包随访模板任务定义");
        requireJsonObject(template.questionnaireDefinitionJson(), "离线包随访问卷定义");
        requireJsonObject(template.abnormalActionJson(), "离线包随访异常处置定义");
    }

    private void validateOfflineDeclarativeAssetContent(
            VersionedAssetType assetType,
            String assetId,
            String assetVersion,
            JsonNode content) {
        PackageOfflineDeclarativeAssetContent declarative =
            readOfflineContent(content, PackageOfflineDeclarativeAssetContent.class);
        if (declarative.assetType() != assetType
                || !assetId.equals(declarative.assetId())
                || !assetVersion.equals(declarative.versionNo())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包声明型资产快照与资产条目不一致");
        }
        if (!"DECLARATIVE_VERSIONED_ASSET".equals(declarative.migrationContract())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包声明型资产迁移契约不受支持");
        }
        if (normalizedText(declarative.packageVersion()) == null
                || normalizedText(declarative.contentHash()) == null
                || !declarative.contentHash().matches("[a-f0-9]{64}")) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包声明型资产缺少包版本或内容摘要");
        }
    }

    private void validateOfflinePathwayContent(
            String assetId,
            String assetVersion,
            JsonNode content) {
        PackageOfflinePathwayContent pathway =
            readOfflineContent(content, PackageOfflinePathwayContent.class);
        PackageOfflinePathwayTemplate template = pathway.template();
        if (template == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径快照缺少 template");
        }
        if (!assetId.equals(template.templateId())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径快照 templateId 与资产条目不一致");
        }
        if (template.templateVersion() == null
                || !Integer.toString(template.templateVersion()).equals(assetVersion)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径快照 templateVersion 与资产条目不一致");
        }
        if (normalizedText(template.packageId()) == null
                || normalizedText(template.templateCode()) == null
                || normalizedText(template.startNodeCode()) == null) {
            throw new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "离线包路径模板缺少路径知识包、模板编码或起始节点");
        }
        ensurePackageAssetPublished("路径模板", template.status());
        parseEnum(com.medkernel.engine.pathway.PathwayTemplateLevel.class,
            template.templateLevel(), "路径模板层级");
        parseEnum(com.medkernel.engine.pathway.PathwayTemplateStatus.class,
            template.status(), "路径模板状态");
        Set<String> milestoneCodes = new HashSet<>();
        for (PackageOfflinePathwayMilestone milestone : pathway.milestones()) {
            if (normalizedText(milestone.milestoneId()) == null
                    || normalizedText(milestone.phaseCode()) == null
                    || normalizedText(milestone.phaseName()) == null
                    || normalizedText(milestone.milestoneCode()) == null
                    || normalizedText(milestone.name()) == null) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径里程碑缺少阶段、编码或名称");
            }
            if (!milestoneCodes.add(milestone.milestoneCode())) {
                throw new ApiException(ErrorCode.CONFLICT,
                    "离线包路径里程碑编码重复: " + milestone.milestoneCode());
            }
            if ((milestone.dayOffset() != null && milestone.dayOffset() < 0)
                    || (milestone.expectedOffsetMinutes() != null
                        && milestone.expectedOffsetMinutes() < 0)) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径里程碑天序或预期完成点不能为负数");
            }
        }
        if (pathway.nodes().isEmpty()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径快照至少需要一个节点");
        }
        Set<String> nodeCodes = new HashSet<>();
        boolean hasTerminalNode = false;
        for (PackageOfflinePathwayNode node : pathway.nodes()) {
            if (normalizedText(node.nodeId()) == null || normalizedText(node.nodeCode()) == null) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径节点缺少业务 ID 或节点编码");
            }
            if (!nodeCodes.add(node.nodeCode())) {
                throw new ApiException(ErrorCode.CONFLICT, "离线包路径节点编码重复: " + node.nodeCode());
            }
            parseEnum(com.medkernel.engine.pathway.PathwayNodeType.class, node.nodeType(), "路径节点类型");
            if (normalizedText(node.milestoneCode()) != null && !milestoneCodes.contains(node.milestoneCode())) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                    "离线包路径节点引用不存在的里程碑: " + node.nodeCode());
            }
            hasTerminalNode = hasTerminalNode || Boolean.TRUE.equals(node.terminalFlag());
        }
        if (!nodeCodes.contains(template.startNodeCode())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径起始节点不存在: " + template.startNodeCode());
        }
        if (!hasTerminalNode) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径缺少终止节点");
        }
        Set<String> timedNodeCodes = new HashSet<>();
        for (PackageOfflinePathwayNode node : pathway.nodes()) {
            if (node.timeWindowMinutes() != null && node.timeWindowMinutes() > 0) {
                timedNodeCodes.add(node.nodeCode());
            }
        }
        Set<String> edgeCodes = new HashSet<>();
        for (PackageOfflinePathwayEdge edge : pathway.edges()) {
            if (normalizedText(edge.edgeId()) == null || normalizedText(edge.edgeCode()) == null) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径边缺少业务 ID 或边编码");
            }
            if (!edgeCodes.add(edge.edgeCode())) {
                throw new ApiException(ErrorCode.CONFLICT, "离线包路径边编码重复: " + edge.edgeCode());
            }
            if (!nodeCodes.contains(edge.fromNodeCode()) || !nodeCodes.contains(edge.toNodeCode())) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包路径边引用不存在的节点: " + edge.edgeCode());
            }
            parseEnum(com.medkernel.engine.pathway.PathwayEdgeType.class, edge.edgeType(), "路径边类型");
        }
        validateOfflineRichPathwayNodeContracts(pathway);
        Set<String> clockMetricBoundNodes = new HashSet<>();
        for (PackageOfflinePathwayMetricBinding binding : pathway.metricBindings()) {
            if (!nodeCodes.contains(binding.nodeCode())) {
                throw new ApiException(
                    ErrorCode.ENG_PACKAGE_002,
                    "离线包路径指标绑定引用不存在的节点: " + binding.nodeCode()
                );
            }
            clockMetricBoundNodes.add(binding.nodeCode());
        }
        for (String nodeCode : timedNodeCodes) {
            if (!clockMetricBoundNodes.contains(nodeCode)) {
                throw new ApiException(
                    ErrorCode.ENG_PACKAGE_002,
                    "离线包路径节点 " + nodeCode + " 设置时窗后必须绑定时钟指标编码"
                );
            }
        }
    }

    private void validateOfflineRichPathwayNodeContracts(PackageOfflinePathwayContent pathway) {
        Map<String, List<PackageOfflinePathwayEdge>> outgoingByNode = new HashMap<>();
        for (PackageOfflinePathwayEdge edge : pathway.edges()) {
            outgoingByNode.computeIfAbsent(edge.fromNodeCode(), ignored -> new ArrayList<>()).add(edge);
        }
        for (PackageOfflinePathwayNode node : pathway.nodes()) {
            com.medkernel.engine.pathway.PathwayNodeType nodeType =
                parseEnum(com.medkernel.engine.pathway.PathwayNodeType.class, node.nodeType(), "路径节点类型");
            validateOfflineClockSla(node);
            List<PackageOfflinePathwayEdge> outgoing = outgoingByNode.getOrDefault(node.nodeCode(), List.of());
            switch (nodeType) {
                case DECISION -> validateOfflineDecisionNode(node, outgoing);
                case PARALLEL -> validateOfflineParallelNode(node, outgoing);
                case WAIT_TIMER -> validateOfflineWaitTimerNode(node, outgoing);
                case SUBPATHWAY -> requireOfflineNodeConfigText(
                    node, "subPathwayRef", "离线包路径子路径节点 " + node.nodeCode() + " 缺少 subPathwayRef");
                case MANUAL_GATE -> {
                    if (normalizedText(node.responsibleRole()) == null) {
                        throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                            "离线包路径人工闸门节点 " + node.nodeCode() + " 缺少责任角色");
                    }
                }
                case ORDER_SET -> requireOfflineNodeConfigText(
                    node, "orderSetRef", "离线包路径医嘱集节点 " + node.nodeCode() + " 缺少 orderSetRef");
                default -> {
                    // 普通活动节点只需要基础拓扑与枚举校验。
                }
            }
        }
    }

    private void validateOfflineClockSla(PackageOfflinePathwayNode node) {
        if (node.timeWindowMinutes() == null) {
            return;
        }
        if (node.timeWindowMinutes() < 0) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径时钟节点 " + node.nodeCode() + " 的 timeWindowMinutes 不能为负数");
        }
        if (node.timeWindowMinutes() == 0) {
            return;
        }

        JsonNode clockSla = offlineNodeConfigNode(node, "clockSla");
        if (clockSla == null || clockSla.isNull() || clockSla.isMissingNode()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径时钟节点 " + node.nodeCode() + " 缺少 clockSla");
        }
        if (!clockSla.isObject()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径时钟节点 " + node.nodeCode() + " 的 clockSla 必须是结构化对象");
        }

        String baselineEvent = requiredOfflineClockText(node, clockSla, "baselineEvent", "SLA 基准事件");
        if (!Set.of("NODE_START", "PATHWAY_ENTRY", "ADMISSION").contains(baselineEvent)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径时钟节点 " + node.nodeCode() + " 不支持 SLA 基准事件: " + baselineEvent);
        }

        int minMinutes = requiredOfflineClockNonNegativeInt(node, clockSla, "minMinutes");
        int targetMinutes = requiredOfflineClockNonNegativeInt(node, clockSla, "targetMinutes");
        int maxMinutes = requiredOfflineClockNonNegativeInt(node, clockSla, "maxMinutes");
        if (targetMinutes <= 0) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径时钟节点 " + node.nodeCode() + " 的 targetMinutes 必须大于 0");
        }
        if (minMinutes > targetMinutes || targetMinutes > maxMinutes) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径时钟节点 " + node.nodeCode() + " 的 SLA 时限必须满足 min <= target <= max");
        }
        validateOfflineClockEscalations(node, clockSla.path("escalations"));
    }

    private void validateOfflineClockEscalations(PackageOfflinePathwayNode node, JsonNode source) {
        if (source == null || !source.isArray() || source.size() == 0) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径时钟节点 " + node.nodeCode() + " 缺少超时升级策略");
        }
        Set<String> levels = new HashSet<>();
        Set<String> allowedLevels = Set.of("REMINDER", "REPORT", "QUALITY_RECORD");
        for (JsonNode item : source) {
            if (!item.isObject()) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                    "离线包路径时钟节点 " + node.nodeCode() + " 的超时升级策略必须是对象数组");
            }
            String level = requiredOfflineClockText(node, item, "level", "超时升级级别");
            if (!allowedLevels.contains(level)) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                    "离线包路径时钟节点 " + node.nodeCode() + " 不支持超时升级级别: " + level);
            }
            requiredOfflineClockNonNegativeInt(node, item, "afterMinutes");
            levels.add(level);
        }
        for (String required : allowedLevels) {
            if (!levels.contains(required)) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                    "离线包路径时钟节点 " + node.nodeCode() + " 缺少 " + required + " 超时升级级别");
            }
        }
    }

    private void validateOfflineDecisionNode(PackageOfflinePathwayNode node,
                                             List<PackageOfflinePathwayEdge> outgoing) {
        List<PackageOfflinePathwayEdge> guardedEdges = outgoing.stream()
            .filter(edge -> "CONDITION".equals(edge.edgeType()))
            .toList();
        if (outgoing.size() < 2 || guardedEdges.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径决策节点 " + node.nodeCode() + " 至少需要一个条件分支和一个兜底分支");
        }
        boolean hasDefaultFallback = outgoing.stream().anyMatch(edge -> "DEFAULT".equals(edge.edgeType()));
        if (!hasDefaultFallback) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径决策节点 " + node.nodeCode() + " 必须配置默认兜底分支");
        }
        boolean hasBlankGuard = guardedEdges.stream().anyMatch(edge -> normalizedText(edge.conditionJson()) == null);
        if (hasBlankGuard) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径决策节点 " + node.nodeCode() + " 的条件分支必须配置守卫条件");
        }
    }

    private void validateOfflineParallelNode(PackageOfflinePathwayNode node,
                                             List<PackageOfflinePathwayEdge> outgoing) {
        boolean hasFork = outgoing.size() >= 2;
        boolean hasJoin = outgoing.stream().anyMatch(edge -> "JOIN".equals(edge.edgeType()));
        if (!hasFork && !hasJoin) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径并行节点 " + node.nodeCode() + " 缺少并行分支或 JOIN 汇合边");
        }
    }

    private void validateOfflineWaitTimerNode(PackageOfflinePathwayNode node,
                                              List<PackageOfflinePathwayEdge> outgoing) {
        if (normalizedText(offlineNodeConfigText(node, "clock")) == null && node.timeWindowMinutes() == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径等待计时节点 " + node.nodeCode() + " 缺少 clock 或 timeWindowMinutes");
        }
        boolean hasTimerGuard = outgoing.stream().anyMatch(edge -> "CONDITION".equals(edge.edgeType()));
        if (!hasTimerGuard) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径等待计时节点 " + node.nodeCode() + " 缺少计时条件边");
        }
    }

    private String requireOfflineNodeConfigText(PackageOfflinePathwayNode node,
                                                String field,
                                                String message) {
        String value = offlineNodeConfigText(node, field);
        if (normalizedText(value) == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, message);
        }
        return value;
    }

    private String offlineNodeConfigText(PackageOfflinePathwayNode node, String field) {
        JsonNode value = offlineNodeConfigNode(node, field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private JsonNode offlineNodeConfigNode(PackageOfflinePathwayNode node, String field) {
        if (normalizedText(node.configJson()) == null) {
            return null;
        }
        try {
            return OFFLINE_EXPORT_MAPPER.readTree(node.configJson()).get(field);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径节点配置 JSON 解析失败: " + node.nodeCode(), exception);
        }
    }

    private String requiredOfflineClockText(
            PackageOfflinePathwayNode node,
            JsonNode source,
            String field,
            String label) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull() || normalizedText(value.asText()) == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径时钟节点 " + node.nodeCode() + " 缺少 " + label);
        }
        return value.asText().trim();
    }

    private int requiredOfflineClockNonNegativeInt(
            PackageOfflinePathwayNode node,
            JsonNode source,
            String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull() || !value.canConvertToInt() || value.asInt() < 0) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包路径时钟节点 " + node.nodeCode() + " 的 " + field + " 必须是非负整数");
        }
        return value.asInt();
    }

    private void validateOfflineTerminologyMappings(List<PackageOfflineTermMapping> mappings) {
        for (PackageOfflineTermMapping mapping : mappings) {
            try {
                TermMapping.validateImportedEnums(mapping.category(), mapping.riskLevel(), mapping.status());
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                    "离线包术语映射枚举不合法: " + ex.getMessage());
            }
        }
    }

    private void validateOfflineTerminologySnapshots(PackageOfflineTerminologyContent content) {
        if (content.items().size() != content.mappings().size()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包术语映射与不可变快照数量不一致");
        }
        for (int index = 0; index < content.mappings().size(); index++) {
            PackageOfflineTermMapping mapping = content.mappings().get(index);
            TermMappingSnapshot snapshot;
            try {
                snapshot = TermMappingSnapshotCodec.read(content.items().get(index).mappingSnapshot());
            } catch (ApiException exception) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包术语映射不可变快照结构不合法");
            }
            if (!Objects.equals(mapping.localTermId(), snapshot.localTermId())
                    || !Objects.equals(mapping.standardTermId(), snapshot.standardTermId())
                    || !Objects.equals(mapping.sourceSystem(), snapshot.sourceSystem())
                    || !Objects.equals(mapping.category(), snapshot.category())
                    || !Objects.equals(mapping.confidence(), snapshot.confidence())
                    || !Objects.equals(mapping.riskLevel(), snapshot.riskLevel())
                    || !Objects.equals(mapping.status(), snapshot.status())
                    || !Objects.equals(mapping.evidenceText(), snapshot.evidenceText())
                    || !Objects.equals(mapping.confirmedBy(), snapshot.confirmedBy())
                    || !Objects.equals(mapping.confirmedAt(), snapshot.confirmedAt())) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包术语映射与不可变快照业务键不一致");
            }
        }
    }

    private void ensureTerminologyPackageReleased(String status) {
        if (!"PUBLISHED".equalsIgnoreCase(status) && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包术语知识包必须为 PUBLISHED 或 ACTIVE 状态, 当前: " + status);
        }
    }

    private void importOfflineRuleSnapshot(
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        PackageOfflineRuleContent ruleContent = readOfflineContent(content, PackageOfflineRuleContent.class);
        if (!assetId.equals(ruleContent.rule().ruleId())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包规则快照 ruleId 与资产条目不一致");
        }
        if (!Integer.valueOf(parseAssetVersionNo(assetVersion)).equals(ruleContent.version().versionNo())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包规则快照 versionNo 与资产条目不一致");
        }

        Optional<RuleDefinition> existingRule = ruleRepository.findByRuleIdAndTenantId(assetId, tenantId);
        if (existingRule.isPresent()) {
            RuleVersion existingVersion = ruleVersionRepository
                .findByRuleIdAndTenantIdAndVersionNo(assetId, tenantId, parseAssetVersionNo(assetVersion))
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "本地规则存在但缺少离线包要求的版本: " + assetId + "@" + assetVersion));
            ensureLocalSnapshotMatches("规则", assetId, content, buildRuleAssetContent(existingRule.get(), existingVersion));
            return;
        }

        RuleVersion importedVersion = ruleVersionRepository.save(new RuleVersion(
            null,
            ruleContent.version().versionId(),
            tenantId,
            assetId,
            ruleContent.version().versionNo(),
            ruleContent.version().sourceRef(),
            ruleContent.version().changeSummary(),
            ruleContent.version().dslJson(),
            ruleContent.version().explanationJson(),
            parseEnum(com.medkernel.engine.rule.RuleVersionStatus.class, ruleContent.version().status(), "规则版本状态"),
            parseInstant(ruleContent.version().publishedAt()),
            ruleContent.version().publishedBy(),
            ruleContent.version().rollbackVersionId(),
            now,
            actor,
            now,
            actor,
            traceId
        ));
        ruleApplicabilityService.saveMirror(
            importedVersion,
            readOfflineRuleDsl(ruleContent.version()),
            now,
            actor,
            traceId);
        ruleRepository.save(new RuleDefinition(
            null,
            ruleContent.rule().ruleId(),
            tenantId,
            ruleContent.rule().ruleCode(),
            ruleContent.rule().name(),
            parseEnum(com.medkernel.engine.rule.RuleType.class, ruleContent.rule().ruleType(), "规则类型"),
            parseEnum(com.medkernel.engine.rule.RuleAuthoringMode.class, ruleContent.rule().authoringMode(), "规则编写模式"),
            parseEnum(com.medkernel.engine.rule.RuleRiskLevel.class, ruleContent.rule().riskLevel(), "规则风险级别"),
            ruleContent.rule().priority(),
            ruleContent.rule().suppressedBy(),
            ruleContent.rule().dedupeWindowSeconds(),
            parseEnum(com.medkernel.engine.rule.RuleDefinitionStatus.class, ruleContent.rule().status(), "规则状态"),
            ruleContent.rule().activeVersionId(),
            ruleContent.rule().packageVersion(),
            ruleContent.rule().applicableOrgUnitId(),
            now,
            actor,
            now,
            actor,
            traceId
        ));
    }

    private JsonNode readOfflineRuleDsl(PackageOfflineRuleVersion version) {
        try {
            return PACKAGE_JSON_MAPPER.readTree(version.dslJson());
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "离线包规则 DSL 不是合法 JSON: " + version.versionId());
        }
    }

    private void importOfflineEvaluationSnapshot(
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        PackageOfflineEvaluationContent evaluationContent = readOfflineContent(content, PackageOfflineEvaluationContent.class);
        PackageOfflineEvaluationIndicator indicator = evaluationContent.indicator();
        if (!assetId.equals(indicator.indicatorId())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包评估指标快照 indicatorId 与资产条目不一致");
        }
        if (!Integer.toString(indicator.versionNo()).equals(assetVersion)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包评估指标快照 versionNo 与资产条目不一致");
        }

        Optional<EvaluationIndicator> existingIndicator = evaluationRepository.findByIndicatorIdAndTenantId(assetId, tenantId);
        if (existingIndicator.isPresent()) {
            ensureLocalSnapshotMatches("评估指标", assetId, content, buildEvaluationAssetContent(existingIndicator.get()));
            return;
        }

        evaluationRepository.save(new EvaluationIndicator(
            null,
            indicator.indicatorId(),
            tenantId,
            indicator.indicatorCode(),
            indicator.versionNo(),
            indicator.name(),
            parseEnum(com.medkernel.engine.evaluation.EvaluationSubjectType.class, indicator.subjectType(), "评估主体类型"),
            indicator.denominatorDefinition(),
            indicator.numeratorDefinition(),
            indicator.exclusionDefinition(),
            indicator.scoringDefinition(),
            indicator.timeWindow(),
            indicator.organizationScope(),
            indicator.responsibleDepartmentId(),
            indicator.sourceRef(),
            indicator.packageVersion(),
            parseEnum(com.medkernel.engine.evaluation.EvaluationIndicatorStatus.class, indicator.status(), "评估指标状态"),
            parseInstant(indicator.publishedAt()),
            indicator.publishedBy(),
            parseInstant(indicator.activatedAt()),
            now,
            actor,
            now,
            actor,
            traceId
        ));
    }

    private void importOfflinePathwaySnapshot(
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        validateOfflinePathwayContent(assetId, assetVersion, content);
        PackageOfflinePathwayContent pathway =
            readOfflineContent(content, PackageOfflinePathwayContent.class);

        Optional<PathwayTemplate> existingTemplate =
            pathwayRepository.findByTemplateIdAndTenantId(assetId, tenantId);
        if (existingTemplate.isPresent()) {
            ensureLocalSnapshotMatches(
                "路径模板",
                assetId,
                content,
                buildPathwayAssetContent(
                    existingTemplate.get(),
                    pathwayMilestoneRepository.findByTemplateIdAndTenantIdOrderBySortOrderAsc(
                        assetId, tenantId),
                    pathwayNodeRepository.findByTemplateIdAndTenantIdOrderBySortOrderAsc(
                        assetId, tenantId),
                    pathwayEdgeRepository.findByTemplateIdAndTenantIdOrderByPriorityAsc(
                        assetId, tenantId),
                    pathwayMetricBindingRepository.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(
                        assetId, tenantId)
                )
            );
            return;
        }

        PackageOfflinePathwayTemplate template = pathway.template();
        pathwayRepository.save(new PathwayTemplate(
            null,
            template.templateId(),
            tenantId,
            template.packageId(),
            template.templateCode(),
            template.name(),
            template.diseaseCode(),
            template.templateVersion(),
            parseEnum(com.medkernel.engine.pathway.PathwayTemplateLevel.class,
                template.templateLevel(), "路径模板层级"),
            parseEnum(com.medkernel.engine.pathway.PathwayTemplateStatus.class,
                template.status(), "路径模板状态"),
            parseEnum(com.medkernel.engine.pathway.PathwayEntryMode.class,
                template.entryMode(), "路径入径模式"),
            template.startNodeCode(),
            template.sourceRef(),
            template.description(),
            template.entryCriteriaJson(),
            template.exitCriteriaJson(),
            now,
            actor,
            now,
            actor,
            traceId
        ));
        pathway.milestones().forEach(milestone -> pathwayMilestoneRepository.save(new PathwayMilestone(
            null,
            milestone.milestoneId(),
            tenantId,
            assetId,
            milestone.phaseCode(),
            milestone.phaseName(),
            milestone.milestoneCode(),
            milestone.name(),
            milestone.dayOffset(),
            milestone.expectedOffsetMinutes(),
            milestone.achievementCriteriaJson(),
            milestone.sortOrder(),
            now,
            actor,
            now,
            actor,
            traceId
        )));
        pathway.nodes().forEach(node -> pathwayNodeRepository.save(new PathwayNode(
            null,
            node.nodeId(),
            tenantId,
            assetId,
            node.nodeCode(),
            node.name(),
            parseEnum(com.medkernel.engine.pathway.PathwayNodeType.class,
                node.nodeType(), "路径节点类型"),
            node.milestoneCode(),
            node.sortOrder(),
            node.responsibleRole(),
            node.dependencyJson(),
            node.timeWindowMinutes(),
            node.terminalFlag(),
            node.configJson(),
            now,
            actor,
            now,
            actor,
            traceId
        )));
        pathway.edges().forEach(edge -> pathwayEdgeRepository.save(new PathwayEdge(
            null,
            edge.edgeId(),
            tenantId,
            assetId,
            edge.edgeCode(),
            edge.fromNodeCode(),
            edge.toNodeCode(),
            parseEnum(com.medkernel.engine.pathway.PathwayEdgeType.class,
                edge.edgeType(), "路径边类型"),
            edge.conditionJson(),
            edge.priority(),
            now,
            actor,
            now,
            actor,
            traceId
        )));
        pathway.metricBindings().forEach(binding ->
            pathwayMetricBindingRepository.save(new SpecialtyMetricBinding(
                null,
                binding.bindingId(),
                tenantId,
                binding.packageId(),
                assetId,
                binding.nodeCode(),
                binding.metricCode(),
                binding.requiredFlag(),
                now,
                actor,
                now,
                actor,
                traceId
            ))
        );
    }

    private void importOfflineKnowledgeSnapshot(
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        PackageOfflineKnowledgeContent knowledgeContent = readOfflineContent(content, PackageOfflineKnowledgeContent.class);
        validateOfflineAssetSnapshotContent(VersionedAssetType.KNOWLEDGE, assetId, assetVersion, content);

        Optional<KnowledgeIdentity> existingIdentity =
            knowledgeIdentityRepository.findByTenantIdAndIdentityCode(tenantId, assetId);
        if (existingIdentity.isPresent()) {
            KnowledgeAssetVersion existingVersion = knowledgeVersionRepository
                .findByTenantIdAndIdentityIdAndVersionNo(tenantId, existingIdentity.get().id(), assetVersion)
                .orElseThrow(() -> new ApiException(
                    ErrorCode.CONFLICT,
                    "本地知识身份存在但缺少离线包要求的版本: " + assetId + "@" + assetVersion
                ));
            ensureLocalSnapshotMatches("知识版本", assetId, content,
                buildKnowledgeAssetContent(existingIdentity.get(), existingVersion));
            ensureImportedKnowledgeAssetVersion(
                assetId, assetVersion, existingVersion, tenantId, actor, traceId, now);
            return;
        }

        PackageOfflineKnowledgeIdentity identity = knowledgeContent.identity();
        KnowledgeIdentity savedIdentity = knowledgeIdentityRepository.save(new KnowledgeIdentity(
            null,
            tenantId,
            identity.identityCode(),
            parseEnum(KnowledgeDomain.class, identity.domain(), "知识身份领域"),
            identity.subject(),
            identity.specialtyId(),
            identity.description(),
            parseEnum(KnowledgeIdentityStatus.class, identity.status(), "知识身份状态"),
            null,
            now,
            actor,
            now,
            actor
        ));
        PackageOfflineKnowledgeVersion version = knowledgeContent.version();
        KnowledgeVersionStatus versionStatus = parseEnum(KnowledgeVersionStatus.class, version.status(), "知识版本状态");
        KnowledgeRiskLevel riskLevel = parseEnum(KnowledgeRiskLevel.class, version.riskLevel(), "知识风险级别");
        String organizationScope = "tenant:" + tenantId;
        String applicableScope = KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE;
        String activeScopeKey = versionStatus == KnowledgeVersionStatus.ACTIVE
            ? KnowledgeAssetVersion.activeScopeKey(savedIdentity.id(), organizationScope, applicableScope)
            : "version-pending:" + savedIdentity.id() + ":" + version.versionNo();
        KnowledgeAssetVersion savedVersion = knowledgeVersionRepository.save(new KnowledgeAssetVersion(
            null,
            tenantId,
            savedIdentity.id(),
            version.versionNo(),
            version.versionLabel(),
            version.sourceDocumentId(),
            version.sourceVersionId(),
            version.contentHash(),
            version.anchors(),
            versionStatus,
            riskLevel,
            parseNullableEnum(SourceAuthorityLevel.class, version.authorityLevel(), "知识来源可信分级"),
            parseNullableEnum(GradeEvidenceQuality.class, version.gradeQuality(), "GRADE 证据质量"),
            parseNullableEnum(GradeRecommendationStrength.class, version.gradeStrength(), "GRADE 推荐强度"),
            version.conflictArbitration(),
            organizationScope,
            applicableScope,
            activeScopeKey,
            parseInstant(version.effectiveFrom()),
            parseInstant(version.effectiveTo()),
            version.reviewedBy(),
            parseInstant(version.reviewedAt()),
            parseInstant(version.activatedAt()),
            parseInstant(version.supersededAt()),
            parseInstant(version.withdrawnAt()),
            version.withdrawnReason(),
            now,
            actor,
            now,
            actor,
            version.reviewCycleMonths(),
            parseInstant(version.nextReviewAt())
        ));
        if (savedVersion.id() != null && savedVersion.status() == KnowledgeVersionStatus.ACTIVE) {
            knowledgeIdentityRepository.save(new KnowledgeIdentity(
                savedIdentity.id(),
                savedIdentity.tenantId(),
                savedIdentity.identityCode(),
                savedIdentity.domain(),
                savedIdentity.subject(),
                savedIdentity.specialtyId(),
                savedIdentity.description(),
                savedIdentity.status(),
                savedVersion.id(),
                savedIdentity.createdAt(),
                savedIdentity.createdBy(),
                now,
                actor
            ));
        }
        ensureImportedKnowledgeAssetVersion(
            assetId, assetVersion, savedVersion, tenantId, actor, traceId, now);
    }

    private void ensureImportedKnowledgeAssetVersion(
            String assetId,
            String assetVersion,
            KnowledgeAssetVersion knowledgeVersion,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        String organizationScope = knowledgeVersion.effectiveOrganizationScope();
        String applicableScope = knowledgeVersion.effectiveApplicableScope();
        Optional<AssetVersion> existing = assetVersions
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                tenantId, VersionedAssetType.KNOWLEDGE, assetId, assetVersion);
        if (existing.isPresent()) {
            AssetVersion unified = existing.get();
            if (unified.status() != AssetVersionStatus.PUBLISHED
                    || !knowledgeVersion.contentHash().equals(unified.contentHash())
                    || !organizationScope.equals(unified.organizationScope())
                    || !applicableScope.equals(unified.applicableScope())) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "本地统一知识版本与离线快照不一致: " + assetId + "@" + assetVersion);
            }
            return;
        }
        assetVersions.save(new AssetVersion(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            VersionedAssetType.KNOWLEDGE,
            assetId,
            assetVersion,
            organizationScope,
            applicableScope,
            knowledgeVersion.contentHash(),
            AssetVersionSafetyPolicy.NORMAL,
            knowledgeVersion.isHighRisk()
                ? AssetVersionOverridePolicy.REVIEW
                : AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            assetId + "|" + organizationScope + "|" + applicableScope,
            "knowledge-version:" + assetId + ":" + assetVersion,
            knowledgeVersion.effectiveFrom(),
            knowledgeVersion.effectiveTo(),
            now,
            actor,
            now,
            actor,
            traceId
        ));
    }

    private void importOfflineTerminologySnapshot(
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        PackageOfflineTerminologyContent terminologyContent =
            readOfflineContent(content, PackageOfflineTerminologyContent.class);
        validateOfflineAssetSnapshotContent(VersionedAssetType.TERMINOLOGY, assetId, assetVersion, content);
        PackageOfflineTerminologyKnowledgePackage importedPackage = terminologyContent.knowledgePackage();
        TerminologyAssetKey key = parseTerminologyAssetKey(assetId);

        Optional<KnowledgePackage> existingPackage = packageRepository
            .findByTenantIdAndPackageCodeAndPackageVersion(
                tenantId, key.packageCode(), assetVersion);
        if (existingPackage.isPresent()) {
            PackageItem existingItem = itemRepository
                .findByTenantIdAndPackageIdAndAssetTypeAndAssetId(
                    tenantId,
                    existingPackage.get().packageId(),
                    VersionedAssetType.TERMINOLOGY,
                    assetId
                )
                .orElseThrow(() -> new ApiException(
                    ErrorCode.CONFLICT,
                    "本地术语知识包缺少离线包要求的范围快照: " + assetId
                ));
            ensureLocalSnapshotMatches("术语映射包", assetId, content,
                buildTerminologyAssetContent(existingPackage.get(), existingItem));
            return;
        }

        KnowledgePackage savedPackage = packageRepository.save(new KnowledgePackage(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            importedPackage.packageCode(),
            importedPackage.packageVersion(),
            importedPackage.name(),
            importedPackage.description(),
            PackageAccessPolicy.OPEN,
            KnowledgePackageStatus.PUBLISHED,
            now,
            actor,
            now,
            actor,
            traceId
        ));
        PackageItem savedPackageItem = itemRepository.save(new PackageItem(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            savedPackage.packageId(),
            VersionedAssetType.TERMINOLOGY,
            assetId,
            assetVersion,
            now,
            actor,
            now,
            actor,
            traceId
        ));
        assetVersions.save(new AssetVersion(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            VersionedAssetType.PACKAGE,
            importedPackage.packageCode(),
            assetVersion,
            key.scopeLevel() + ":" + key.scopeCode(),
            "ALL",
            importedPackage.contentHash(),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            importedPackage.packageCode() + "|" + key.scopeLevel() + ":" + key.scopeCode() + "|ALL",
            "knowledge-package:" + savedPackage.packageId(),
            now,
            null,
            now,
            actor,
            now,
            actor,
            traceId
        ));

        List<TermMapping> savedMappings = new ArrayList<>();
        for (PackageOfflineTermMapping mapping : terminologyContent.mappings()) {
            TermMapping savedMapping = terminologyMappingRepository
                .findByTenantIdAndLocalTermIdAndStandardTermId(
                    tenantId,
                    mapping.localTermId(),
                    mapping.standardTermId()
                )
                .orElseGet(() -> terminologyMappingRepository.save(TermMapping.imported(
                    tenantId,
                    mapping.localTermId(),
                    mapping.standardTermId(),
                    mapping.sourceSystem(),
                    mapping.category(),
                    mapping.confidence(),
                    mapping.riskLevel(),
                    mapping.status(),
                    mapping.evidenceText(),
                    mapping.confirmedBy(),
                    parseInstant(mapping.confirmedAt()),
                    now,
                    actor
                )));
            savedMappings.add(savedMapping);
        }

        if (terminologyContent.items().size() != savedMappings.size()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "术语离线包映射与不可变快照数量不一致");
        }
        for (int i = 0; i < savedMappings.size(); i++) {
            TermMapping savedMapping = savedMappings.get(i);
            String mappingSnapshot = terminologyContent.items().get(i).mappingSnapshot();
            TermMappingSnapshot snapshot = TermMappingSnapshotCodec.read(mappingSnapshot)
                .withPersistenceIds(
                    savedMapping.id(),
                    savedMapping.localTermId(),
                    savedMapping.standardTermId()
                );
            String persistedSnapshot = TermMappingSnapshotCodec.write(snapshot);
            terminologySnapshotRepository.save(TermMappingSnapshotEntity.fromSnapshot(
                tenantId,
                savedPackageItem.itemId(),
                savedMapping.id(),
                snapshot,
                persistedSnapshot,
                now,
                actor
            ));
        }
    }

    private void importOfflineConditionFragmentSnapshot(
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        PackageOfflineConditionFragmentContent fragmentContent =
            readOfflineContent(content, PackageOfflineConditionFragmentContent.class);
        validateOfflineAssetSnapshotContent(VersionedAssetType.CONDITION_FRAGMENT, assetId, assetVersion, content);

        PackageOfflineConditionFragment fragment = fragmentContent.fragment();
        Optional<ConditionFragment> existing =
            conditionFragmentRepository.findByFragmentIdAndTenantId(assetId, tenantId);
        if (existing.isPresent()) {
            ensureLocalSnapshotMatches("条件片段", assetId, content, buildConditionFragmentAssetContent(existing.get()));
            return;
        }
        conditionFragmentRepository.findByTenantIdAndFragmentCodeAndVersionNo(
                tenantId,
                fragment.fragmentCode(),
                fragment.versionNo())
            .ifPresent(conflicting -> {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "条件片段编码版本已被其他业务 ID 占用: "
                        + fragment.fragmentCode() + "@" + fragment.versionNo()
                );
            });

        conditionFragmentRepository.save(new ConditionFragment(
            null,
            fragment.fragmentId(),
            tenantId,
            fragment.fragmentCode(),
            fragment.name(),
            fragment.category(),
            fragment.bodyJson(),
            fragment.versionNo(),
            parseEnum(ConditionFragmentStatus.class, fragment.status(), "条件片段状态"),
            fragment.packageVersion(),
            now,
            actor,
            now,
            actor,
            traceId
        ));
    }

    private void importOfflineDeclarativeAssetSnapshot(
            VersionedAssetType assetType,
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        PackageOfflineDeclarativeAssetContent declarative =
            readOfflineContent(content, PackageOfflineDeclarativeAssetContent.class);
        validateOfflineDeclarativeAssetContent(assetType, assetId, assetVersion, content);

        Optional<AssetVersion> existing = assetVersions
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                tenantId, assetType, assetId, assetVersion);
        if (existing.isPresent()) {
            if (!declarative.contentHash().equals(existing.get().contentHash())) {
                throw new ApiException(ErrorCode.CONFLICT, "本地声明型资产与离线包内容摘要不一致: " + assetId);
            }
            return;
        }

        AssetVersionStatus status = parseEnum(
            AssetVersionStatus.class,
            declarative.status(),
            "声明型资产状态"
        );
        ensurePackageAssetPublished("声明型资产", status.name());
        assetVersions.save(new AssetVersion(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            assetType,
            assetId,
            assetVersion,
            "tenant:" + tenantId,
            declarative.packageVersion(),
            declarative.contentHash(),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            "version:" + assetType + ":" + assetId + ":" + declarative.packageVersion(),
            declarative.sourceRef(),
            null,
            null,
            now,
            actor,
            now,
            actor,
            traceId
        ));
    }

    private void importOfflineFollowupTemplateSnapshot(
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        validateOfflineFollowupContent(assetId, assetVersion, content);
        PackageOfflineFollowupContent followup =
            readOfflineContent(content, PackageOfflineFollowupContent.class);
        PackageOfflineFollowupTemplate template = followup.template();
        Optional<FollowupTemplate> existing =
            followupTemplateRepository.findByTemplateIdAndTenantId(assetId, tenantId);
        if (existing.isPresent()) {
            AssetVersion existingVersion = assetVersions
                .findByVersionIdAndTenantId(existing.get().assetVersionId(), tenantId)
                .orElseThrow(() -> new ApiException(
                    ErrorCode.CONFLICT,
                    "本地随访模板缺少统一资产版本: " + assetId
                ));
            if (!followup.contentHash().equals(existingVersion.contentHash())) {
                throw new ApiException(ErrorCode.CONFLICT, "本地随访模板与离线包内容摘要不一致: " + assetId);
            }
            return;
        }
        followupTemplateRepository.findByTenantIdAndTemplateCodeAndVersionNo(
            tenantId, template.templateCode(), template.versionNo()
        ).ifPresent(conflict -> {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "随访模板编码版本已被其他业务 ID 占用: "
                    + template.templateCode() + "@" + template.versionNo()
            );
        });

        String versionId = "av-" + UUID.randomUUID();
        assetVersions.save(new AssetVersion(
            null,
            versionId,
            tenantId,
            VersionedAssetType.FOLLOWUP,
            assetId,
            assetVersion,
            template.organizationScope(),
            template.applicableScope(),
            followup.contentHash(),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            "version:FOLLOWUP:" + assetId + ":" + assetVersion,
            template.sourceRef(),
            now,
            null,
            now,
            actor,
            now,
            actor,
            traceId
        ));
        followupTemplateRepository.save(new FollowupTemplate(
            null,
            template.templateId(),
            tenantId,
            template.templateCode(),
            template.versionNo(),
            template.name(),
            template.description(),
            template.organizationScope(),
            template.applicableScope(),
            template.taskDefinitionJson(),
            template.questionnaireDefinitionJson(),
            template.abnormalActionJson(),
            template.sourceRef(),
            versionId,
            now,
            actor,
            now,
            actor,
            traceId
        ));
    }

    private void requireJsonArray(String payload, String label) {
        JsonNode node = readJsonNode(payload, label);
        if (!node.isArray() || node.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, label + "必须是非空 JSON 数组");
        }
    }

    private void requireJsonObject(String payload, String label) {
        JsonNode node = readJsonNode(payload, label);
        if (!node.isObject()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, label + "必须是 JSON 对象");
        }
    }

    private JsonNode readJsonNode(String payload, String label) {
        try {
            return OFFLINE_EXPORT_MAPPER.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, label + "不是合法 JSON", exception);
        }
    }

    private <T> T readOfflineContent(JsonNode content, Class<T> type) {
        try {
            return OFFLINE_EXPORT_MAPPER.treeToValue(content, type);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "离线包资产内容结构不合法");
        }
    }

    private void ensureLocalSnapshotMatches(String assetName, String assetId, JsonNode importedContent, JsonNode localContent) {
        if (!sha256Json(importedContent).equals(sha256Json(localContent))) {
            throw new ApiException(ErrorCode.CONFLICT, "本地" + assetName + "与离线包内容不一致: " + assetId);
        }
    }

    private String writeOfflineJson(Object export) {
        try {
            return OFFLINE_EXPORT_MAPPER.writeValueAsString(export) + "\n";
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "配置包离线安装包导出失败");
        }
    }

    private String sha256Json(Object payload) {
        try {
            byte[] bytes = OFFLINE_EXPORT_MAPPER.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "配置包离线安装包摘要生成失败");
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "运行环境不支持 SHA-256 摘要算法");
        }
    }

    private JsonNode parseOfflinePackage(PackageOfflineImportRequest request) {
        if (request == null || request.offlinePackageJson() == null || request.offlinePackageJson().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "离线包 JSON 不能为空");
        }
        try {
            JsonNode root = OFFLINE_EXPORT_MAPPER.readTree(request.offlinePackageJson());
            if (root == null || !root.isObject()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "离线包 JSON 根节点必须是对象");
            }
            return root;
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "离线包 JSON 解析失败");
        }
    }

    private void ensureOfflineFormat(JsonNode root) {
        String format = requireText(root, "format", "离线包缺少 format 字段");
        if (!OFFLINE_PACKAGE_FORMAT.equals(format)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "离线包格式不受支持: " + format);
        }
    }

    private void validateOfflineImportTenantLineage(String currentTenantId, String sourceTenantId) {
        if (currentTenantId.equals(sourceTenantId)) {
            return;
        }
        if (PlatformTenant.isPlatformTenant(sourceTenantId) && !PlatformTenant.isPlatformTenant(currentTenantId)) {
            return;
        }
        if (PlatformTenant.isPlatformTenant(currentTenantId)) {
            throw new ApiException(
                ErrorCode.TENANT_FORBIDDEN,
                "客户或集团离线包禁止导入平台主租户，平台主源只能由平台租户自身维护");
        }
        throw new ApiException(
            ErrorCode.TENANT_FORBIDDEN,
            "离线包仅允许本租户恢复，或从平台主租户下发到客户 / 集团租户");
    }

    private void validateOfflineItemSourceLineage(String currentTenantId, String itemSourceTenantId) {
        if (currentTenantId.equals(itemSourceTenantId)) {
            return;
        }
        if (PlatformTenant.isPlatformTenant(itemSourceTenantId) && !PlatformTenant.isPlatformTenant(currentTenantId)) {
            return;
        }
        throw new ApiException(
            ErrorCode.TENANT_FORBIDDEN,
            "离线包资产来源租户不允许导入当前租户: " + itemSourceTenantId);
    }

    private void validateEffectiveSnapshot(
            EffectivePackageSnapshot snapshot,
            String sourcePackageId,
            String packageCode,
            String packageVersion,
            String targetOrgUnitId,
            String declaredSha256) {
        if (snapshot == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包有效快照不能为空");
        }
        if (!declaredSha256.matches("[a-f0-9]{64}")) {
            throw new ApiException(ErrorCode.ENG_EVID_002, "离线包 effectiveSnapshotSha256 格式不合法");
        }
        if (!sourcePackageId.equals(snapshot.packageId())
                || !packageCode.equals(snapshot.packageCode())
                || !packageVersion.equals(snapshot.packageVersion())
                || !targetOrgUnitId.equals(snapshot.targetOrgUnitId())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包有效快照与包元信息不一致");
        }
        String actualSha256 = EffectivePackageSnapshot.from(new EffectiveKnowledgePackageResponse(
            snapshot.tenantId(),
            snapshot.targetOrgUnitId(),
            snapshot.packageId(),
            snapshot.packageCode(),
            snapshot.packageVersion(),
            snapshot.items(),
            snapshot.excludedItems(),
            snapshot.warnings()
        )).contentSha256();
        if (!declaredSha256.equals(snapshot.contentSha256()) || !actualSha256.equals(snapshot.contentSha256())) {
            throw new ApiException(ErrorCode.ENG_EVID_002, "离线包有效快照摘要与实际内容不一致");
        }
    }

    private void validateOfflineItemsMatchEffectiveSnapshot(
            JsonNode itemsNode,
            String sourcePackageId,
            EffectivePackageSnapshot snapshot) {
        Map<String, EffectivePackageItem> effectiveItemsByKey = new HashMap<>();
        for (EffectivePackageItem item : snapshot.items()) {
            String key = offlineAssetKey(
                item.assetType(), item.sourceTenantId(), item.assetId(), item.effectiveVersion());
            if (effectiveItemsByKey.putIfAbsent(key, item) != null) {
                throw new ApiException(ErrorCode.CONFLICT, "离线包有效快照内存在重复资产条目: " + key);
            }
        }
        for (JsonNode itemNode : itemsNode) {
            VersionedAssetType assetType = parseAssetType(requireText(itemNode, "assetType", "离线包资产条目缺少 assetType"));
            String assetId = requireText(itemNode, "assetId", "离线包资产条目缺少 assetId");
            String effectiveVersion = requireText(itemNode, "effectiveVersion", "离线包资产条目缺少 effectiveVersion");
            String itemSourceTenantId = requireText(itemNode, "sourceTenantId", "离线包资产条目缺少 sourceTenantId");
            requireSameText(itemNode, "packageId", sourcePackageId, "离线包资产条目 packageId 与包元信息不一致");
            String key = offlineAssetKey(assetType, itemSourceTenantId, assetId, effectiveVersion);
            EffectivePackageItem effectiveItem = effectiveItemsByKey.remove(key);
            if (effectiveItem == null) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产条目不在有效快照内: " + key);
            }
            requireSameText(itemNode, "declaredVersion", effectiveItem.declaredVersion(), "离线包资产条目声明版本与有效快照不一致");
            requireSameText(itemNode, "sourceVersionId", effectiveItem.sourceVersionId(), "离线包资产条目来源版本指针与有效快照不一致");
            requireSameText(itemNode, "contentHash", effectiveItem.contentHash(), "离线包资产条目内容哈希与有效快照不一致");
        }
        if (!effectiveItemsByKey.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包缺少有效快照资产条目: " + effectiveItemsByKey.keySet());
        }
    }

    private void validateManifestPayloadMatch(
            JsonNode manifest,
            JsonNode packageInfo,
            String sourcePackageId,
            String sourceTenantId,
            String packageCode,
            String packageVersion) {
        requireSameText(manifest, "packageId", sourcePackageId, "离线包 manifest.packageId 与 payload 不一致");
        requireSameText(manifest, "tenantId", sourceTenantId, "离线包 manifest.tenantId 与 payload 不一致");
        requireSameText(manifest, "packageCode", packageCode, "离线包 manifest.packageCode 与 payload 不一致");
        requireSameText(manifest, "packageVersion", packageVersion, "离线包 manifest.packageVersion 与 payload 不一致");

        String manifestStatus = optionalText(manifest, "status");
        String payloadStatus = optionalText(packageInfo, "status");
        if (manifestStatus != null && payloadStatus != null && !manifestStatus.equals(payloadStatus)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包 manifest.status 与 payload 不一致");
        }
    }

    private List<PackageItem> buildOfflineImportItems(
            JsonNode itemsNode,
            String tenantId,
            String importedPackageId,
            String sourcePackageId,
            String actor,
            String traceId,
            Instant now) {
        List<PackageItem> items = new ArrayList<>();
        Set<String> uniqueAssets = new HashSet<>();
        for (JsonNode itemNode : itemsNode) {
            if (itemNode == null || !itemNode.isObject()) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产条目必须是对象");
            }
            requireSameText(itemNode, "packageId", sourcePackageId, "离线包资产条目 packageId 与包元信息不一致");
            VersionedAssetType assetType = parseAssetType(requireText(itemNode, "assetType", "离线包资产条目缺少 assetType"));
            String assetId = requireText(itemNode, "assetId", "离线包资产条目缺少 assetId");
            String assetVersion = requireText(itemNode, "effectiveVersion", "离线包资产条目缺少 effectiveVersion");
            String itemSourceTenantId = requireText(itemNode, "sourceTenantId", "离线包资产条目缺少 sourceTenantId");
            validateOfflineItemSourceLineage(tenantId, itemSourceTenantId);
            String assetKey = offlineAssetKey(assetType, itemSourceTenantId, assetId, assetVersion);
            if (!uniqueAssets.add(assetKey)) {
                throw new ApiException(ErrorCode.CONFLICT, "离线包内存在重复资产条目: " + assetKey);
            }
            if (!isPlatformSourceReferenceImport(tenantId, itemSourceTenantId)) {
                validateAssetStatus(tenantId, assetType, assetId, assetVersion);
            }

            items.add(new PackageItem(
                null,
                UUID.randomUUID().toString(),
                tenantId,
                importedPackageId,
                assetType,
                assetId,
                assetVersion,
                now,
                actor,
                now,
                actor,
                traceId
            ));
        }
        return items;
    }

    private boolean isPlatformSourceReferenceImport(String tenantId, String sourceTenantId) {
        return PlatformTenant.isPlatformTenant(sourceTenantId) && !PlatformTenant.isPlatformTenant(tenantId);
    }

    private VersionedAssetType parseAssetType(String assetType) {
        try {
            return VersionedAssetType.valueOf(assetType);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产类型不受支持: " + assetType);
        }
    }

    private int parseAssetVersionNo(String assetVersion) {
        try {
            return Integer.parseInt(assetVersion);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产版本号必须是整数: " + assetVersion);
        }
    }

    private String offlineAssetKey(
            VersionedAssetType assetType,
            String sourceTenantId,
            String assetId,
            String assetVersion) {
        return assetType.name() + ":" + sourceTenantId + ":" + assetId + ":" + assetVersion;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "离线包时间格式不合法: " + value, e);
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "离线包缺少" + label);
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "离线包" + label + "不合法: " + value);
        }
    }

    private <E extends Enum<E>> E parseNullableEnum(Class<E> enumClass, String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "离线包" + label + "不合法: " + value);
        }
    }

    private JsonNode requireObject(JsonNode parent, String field, String message) {
        JsonNode value = parent.path(field);
        if (!value.isObject()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return value;
    }

    private JsonNode requireArray(JsonNode parent, String field, String message) {
        JsonNode value = parent.path(field);
        if (!value.isArray()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return value;
    }

    private String requireText(JsonNode parent, String field, String message) {
        JsonNode value = parent.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return value.asText().trim();
    }

    private String optionalText(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "离线包字段类型不合法: " + field);
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private int requireInt(JsonNode parent, String field, String message) {
        JsonNode value = parent.path(field);
        if (!value.canConvertToInt() || value.asInt() < 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return value.asInt();
    }

    private void requireSameText(JsonNode parent, String field, String expected, String message) {
        String actual = requireText(parent, field, message);
        if (!expected.equals(actual)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, message);
        }
    }

    private record PackageContentDigest(
        String packageCode,
        String packageVersion,
        List<PackageContentDigestItem> items
    ) {}

    private record PackageContentDigestItem(
        VersionedAssetType assetType,
        String assetId,
        String assetVersion
    ) {}

    private record PackageOfflineExport(
        String format,
        PackageOfflineManifest manifest,
        PackageOfflinePayload payload
    ) {}

    private record PackageOfflineManifest(
        String packageId,
        String tenantId,
        String packageCode,
        String packageVersion,
        KnowledgePackageStatus status,
        String targetOrgUnitId,
        String effectiveSnapshotSha256,
        int itemCount,
        int assetSnapshotCount,
        int excludedItemCount,
        int warningCount,
        String hashAlgorithm,
        String payloadSha256,
        String exportedAt,
        String traceId
    ) {}

    private record PackageOfflinePayload(
        PackageOfflinePackageInfo packageInfo,
        EffectivePackageSnapshot effectiveSnapshot,
        List<PackageOfflineItem> items,
        List<PackageOfflineAssetSnapshot> assetSnapshots
    ) {}

    private record PackageOfflineAssetSnapshot(
        VersionedAssetType assetType,
        String assetId,
        String declaredVersion,
        String effectiveVersion,
        String sourceTenantId,
        String sourceVersionId,
        String contentHash,
        String contentSha256,
        JsonNode content
    ) {}

    private record PackageOfflinePackageInfo(
        String packageId,
        String tenantId,
        String packageCode,
        String packageVersion,
        String name,
        String description,
        KnowledgePackageStatus status,
        String createdAt,
        String createdBy,
        String updatedAt,
        String updatedBy,
        String traceId
    ) {
        static PackageOfflinePackageInfo from(KnowledgePackage pack) {
            return new PackageOfflinePackageInfo(
                pack.packageId(),
                pack.tenantId(),
                pack.packageCode(),
                pack.packageVersion(),
                pack.name(),
                pack.description(),
                pack.status(),
                instantText(pack.createdAt()),
                pack.createdBy(),
                instantText(pack.updatedAt()),
                pack.updatedBy(),
                pack.traceId()
            );
        }
    }

    private record PackageOfflineItem(
        String packageId,
        VersionedAssetType assetType,
        String assetId,
        String declaredVersion,
        String effectiveVersion,
        String sourceTenantId,
        String sourceOrgPath,
        SourceTier sourceTier,
        boolean inherited,
        boolean overridden,
        boolean resolvedByUnifiedVersioning,
        String sourceVersionId,
        String contentHash
    ) {
        static PackageOfflineItem from(String packageId, EffectivePackageItem item) {
            return new PackageOfflineItem(
                packageId,
                item.assetType(),
                item.assetId(),
                item.declaredVersion(),
                item.effectiveVersion(),
                item.sourceTenantId(),
                item.sourceOrgPath(),
                item.sourceTier(),
                item.inherited(),
                item.overridden(),
                item.resolvedByUnifiedVersioning(),
                item.sourceVersionId(),
                item.contentHash()
            );
        }
    }

    private static String instantText(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private record PackageOfflineRuleContent(
        PackageOfflineRuleDefinition rule,
        PackageOfflineRuleVersion version
    ) {}

    private record PackageOfflineRuleDefinition(
        String ruleId,
        String ruleCode,
        String name,
        String ruleType,
        String authoringMode,
        String riskLevel,
        int priority,
        String suppressedBy,
        int dedupeWindowSeconds,
        String status,
        String activeVersionId,
        String packageVersion,
        String applicableOrgUnitId
    ) {}

    private record PackageOfflineRuleVersion(
        String versionId,
        Integer versionNo,
        String sourceRef,
        String changeSummary,
        String dslJson,
        String explanationJson,
        String status,
        String publishedAt,
        String publishedBy,
        String rollbackVersionId
    ) {}

    private record PackageOfflinePathwayContent(
        PackageOfflinePathwayTemplate template,
        List<PackageOfflinePathwayMilestone> milestones,
        List<PackageOfflinePathwayNode> nodes,
        List<PackageOfflinePathwayEdge> edges,
        List<PackageOfflinePathwayMetricBinding> metricBindings
    ) {
        PackageOfflinePathwayContent {
            milestones = milestones == null ? List.of() : List.copyOf(milestones);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            metricBindings = metricBindings == null ? List.of() : List.copyOf(metricBindings);
        }
    }

    private record PackageOfflinePathwayTemplate(
        String templateId,
        String packageId,
        String templateCode,
        String name,
        String diseaseCode,
        Integer templateVersion,
        String templateLevel,
        String status,
        String entryMode,
        String startNodeCode,
        String sourceRef,
        String description,
        String entryCriteriaJson,
        String exitCriteriaJson
    ) {}

    private record PackageOfflinePathwayMilestone(
        String milestoneId,
        String phaseCode,
        String phaseName,
        String milestoneCode,
        String name,
        Integer dayOffset,
        Integer expectedOffsetMinutes,
        String achievementCriteriaJson,
        Integer sortOrder
    ) {}

    private record PackageOfflinePathwayNode(
        String nodeId,
        String nodeCode,
        String name,
        String nodeType,
        String milestoneCode,
        Integer sortOrder,
        String responsibleRole,
        String dependencyJson,
        Integer timeWindowMinutes,
        Boolean terminalFlag,
        String configJson
    ) {}

    private record PackageOfflinePathwayEdge(
        String edgeId,
        String edgeCode,
        String fromNodeCode,
        String toNodeCode,
        String edgeType,
        String conditionJson,
        Integer priority
    ) {}

    private record PackageOfflinePathwayMetricBinding(
        String bindingId,
        String packageId,
        String nodeCode,
        String metricCode,
        Boolean requiredFlag
    ) {}

    private record PackageOfflineEvaluationContent(
        PackageOfflineEvaluationIndicator indicator
    ) {}

    private record PackageOfflineEvaluationIndicator(
        String indicatorId,
        String indicatorCode,
        int versionNo,
        String name,
        String subjectType,
        String denominatorDefinition,
        String numeratorDefinition,
        String exclusionDefinition,
        String scoringDefinition,
        String timeWindow,
        String organizationScope,
        String responsibleDepartmentId,
        String sourceRef,
        String packageVersion,
        String status,
        String publishedAt,
        String publishedBy,
        String activatedAt
    ) {}

    private record PackageOfflineConditionFragmentContent(
        PackageOfflineConditionFragment fragment
    ) {}

    private record PackageOfflineFollowupContent(
        PackageOfflineFollowupTemplate template,
        String contentHash,
        String status
    ) {}

    private record PackageOfflineFollowupTemplate(
        String templateId,
        String templateCode,
        int versionNo,
        String name,
        String description,
        String organizationScope,
        String applicableScope,
        String taskDefinitionJson,
        String questionnaireDefinitionJson,
        String abnormalActionJson,
        String sourceRef
    ) {}

    private record PackageOfflineConditionFragment(
        String fragmentId,
        String fragmentCode,
        String name,
        String category,
        String bodyJson,
        int versionNo,
        String status,
        String packageVersion,
        String createdAt,
        String createdBy,
        String updatedAt,
        String updatedBy,
        String traceId
    ) {}

    private record PackageOfflineDeclarativeAssetContent(
        VersionedAssetType assetType,
        String assetId,
        String versionNo,
        String packageVersion,
        String sourceTenantId,
        String sourceVersionId,
        String contentHash,
        String migrationContract,
        String status,
        String sourceRef
    ) {
        PackageOfflineDeclarativeAssetContent(
                VersionedAssetType assetType,
                String assetId,
                String versionNo,
                String packageVersion,
                String sourceTenantId,
                String sourceVersionId,
                String contentHash,
                String migrationContract) {
            this(
                assetType,
                assetId,
                versionNo,
                packageVersion,
                sourceTenantId,
                sourceVersionId,
                contentHash,
                migrationContract,
                AssetVersionStatus.PUBLISHED.name(),
                "declarative:" + assetType
            );
        }
    }

    private record PackageOfflineKnowledgeContent(
        PackageOfflineKnowledgeIdentity identity,
        PackageOfflineKnowledgeVersion version
    ) {}

    private record PackageOfflineKnowledgeIdentity(
        String identityCode,
        String domain,
        String subject,
        String specialtyId,
        String description,
        String status,
        String currentVersionNo
    ) {}

    private record PackageOfflineKnowledgeVersion(
        String versionNo,
        String versionLabel,
        Long sourceDocumentId,
        Long sourceVersionId,
        String contentHash,
        String anchors,
        String status,
        String riskLevel,
        String authorityLevel,
        String gradeQuality,
        String gradeStrength,
        String conflictArbitration,
        String effectiveFrom,
        String effectiveTo,
        String reviewedBy,
        String reviewedAt,
        String activatedAt,
        String supersededAt,
        String withdrawnAt,
        String withdrawnReason,
        Integer reviewCycleMonths,
        String nextReviewAt
    ) {}

    private record PackageOfflineTerminologyContent(
        PackageOfflineTerminologyKnowledgePackage knowledgePackage,
        List<PackageOfflineTermMapping> mappings,
        List<PackageOfflineTermMappingSnapshot> items
    ) {
        PackageOfflineTerminologyContent {
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    private record PackageOfflineTerminologyKnowledgePackage(
        String packageId,
        String packageCode,
        String packageVersion,
        String name,
        String description,
        String status,
        String scopeLevel,
        String scopeCode,
        String contentHash
    ) {}

    private record PackageOfflineTermMapping(
        Long localTermId,
        Long standardTermId,
        String sourceSystem,
        String category,
        Double confidence,
        String riskLevel,
        String status,
        String evidenceText,
        String confirmedBy,
        String confirmedAt
    ) {}

    private record PackageOfflineTermMappingSnapshot(
        String mappingSnapshot
    ) {}

    /**
     * 触发包同步与发布执行（支持灰度、全量、回滚等多通道同步发布）。
     */
    public PackageSyncResponse syncPackage(String packageId, PackageSyncRequest request) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        PackageSyncRequest releaseRequest = requireSyncRequest(request);
        ReleaseScope normalizedScope = normalizeReleaseScope(releaseRequest);

        KnowledgePackage pack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));
        validateReleaseAuthorization(pack, releaseRequest);

        assertPackageReadyForRelease(packageId);
        if (releaseRequest.adapterIds().isEmpty()) {
            return recordNotSyncedPlanForMissingChannel(
                tenantId, packageId, releaseRequest, normalizedScope, actor, traceId);
        }
        EffectivePackageSnapshot effectiveSnapshot = buildEffectiveSnapshot(
            tenantId, pack, releaseRequest.targetOrgUnitId());
        AssetVersion packageAsset = prepareUnifiedPackageRelease(pack);
        VersionReleaseCommand unifiedRelease = packageReleaseCommand(
            pack, packageAsset, effectiveSnapshot, releaseRequest, normalizedScope, actor, traceId);
        preparePackageReleaseStatus(packageAsset, unifiedRelease);

        // 创建发布计划（独立小事务中写库）
        ReleasePlan plan = new ReleasePlan(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            packageId,
            releaseRequest.targetOrgUnitId(),
            releaseRequest.strategy(),
            normalizedScope.scopeType(),
            normalizedScope.scopeValue(),
            ReleasePlanStatus.EXECUTING,
            Instant.now(),
            actor,
            Instant.now(),
            actor,
            traceId
        );
        ReleasePlan savedPlan = transactionTemplate.execute(status -> planRepository.save(plan));

        List<SyncLogResponse> logs = new ArrayList<>();
        boolean anySuccess = false;
        boolean allSuccess = true;
        boolean anyNotSynced = false;
        boolean anyFailed = false;

        for (String adapterId : request.adapterIds()) {
            IntegrationAdapter adapter = adapterRepository.findByAdapterIdAndTenantId(adapterId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "同步适配器不存在: " + adapterId));

            // 小事务1：插入同步初始 RUNNING 日志
            SyncLog savedLog = transactionTemplate.execute(status -> {
                SyncLog syncLog = new SyncLog(
                    null,
                    UUID.randomUUID().toString(),
                    tenantId,
                    savedPlan.planId(),
                    adapterId,
                    SyncLogStatus.RUNNING,
                    null, null, 0, null,
                    Instant.now(), actor, Instant.now(), actor, traceId
                );
                return logRepository.save(syncLog);
            });

            String evidence = null;
            Exception syncError = null;

            try {
                // 事务外部安全执行：同步发布（包含长 IO）
                evidence = syncPort.sync(tenantId, savedPlan, adapter, effectiveSnapshot);
                requireAdapterEvidence(evidence, "同步未返回同步证据: " + adapterId);
                evidence = syncEvidenceWithSnapshot(evidence, effectiveSnapshot);
            } catch (Exception e) {
                if (e instanceof PackageSyncNotConnectedException) {
                    log.warn("同步发布未接入真实同步适配器, adapterId: {}, reason: {}", adapterId, e.getMessage());
                } else {
                    log.error("同步发布失败, adapterId: {}", adapterId, e);
                }
                syncError = e;
            }

            final String finalEvidence = evidence;
            final Exception finalError = syncError;

            // 小事务2：更新同步成功、失败或未接入状态日志
            SyncLog updatedLog = transactionTemplate.execute(status -> {
                if (finalError == null) {
                    SyncLog successLog = new SyncLog(
                        savedLog.id(),
                        savedLog.logId(),
                        tenantId,
                        savedPlan.planId(),
                        adapterId,
                        SyncLogStatus.SUCCESS,
                        null, null, 0, finalEvidence,
                        savedLog.createdAt(), savedLog.createdBy(), Instant.now(), actor, traceId
                    );
                    return logRepository.save(successLog);
                } else if (finalError instanceof PackageSyncNotConnectedException) {
                    SyncLog notSyncedLog = new SyncLog(
                        savedLog.id(),
                        savedLog.logId(),
                        tenantId,
                        savedPlan.planId(),
                        adapterId,
                        SyncLogStatus.NOT_SYNCED,
                        PackageSyncNotConnectedException.CODE,
                        finalError.getMessage(),
                        0, null,
                        savedLog.createdAt(), savedLog.createdBy(), Instant.now(), actor, traceId
                    );
                    return logRepository.save(notSyncedLog);
                } else {
                    SyncLog failedLog = new SyncLog(
                        savedLog.id(),
                        savedLog.logId(),
                        tenantId,
                        savedPlan.planId(),
                        adapterId,
                        SyncLogStatus.FAILED,
                        "ENG-PACKAGE-005",
                        finalError.getMessage(),
                        0, null,
                        savedLog.createdAt(), savedLog.createdBy(), Instant.now(), actor, traceId
                    );
                    return logRepository.save(failedLog);
                }
            });

            if (syncError == null) {
                anySuccess = true;
            } else {
                allSuccess = false;
                if (syncError instanceof PackageSyncNotConnectedException) {
                    anyNotSynced = true;
                } else {
                    anyFailed = true;
                }
            }
            logs.add(SyncLogResponse.from(updatedLog));
        }

        final ReleasePlanStatus finalStatus = allSuccess ? ReleasePlanStatus.SUCCESS
            : (anyNotSynced && !anySuccess && !anyFailed ? ReleasePlanStatus.NOT_SYNCED : ReleasePlanStatus.FAILED);
        final boolean finalAllSuccess = allSuccess;

        // 小事务3：最终原子包状态激活与旧版本隔离切换
        transactionTemplate.executeWithoutResult(status -> {
            planRepository.save(savedPlan.withStatus(finalStatus));

            if (finalAllSuccess) {
                if (releaseRequest.strategy() == ReleaseStrategy.FULL) {
                    releasePort.publish(unifiedRelease);
                } else {
                    releasePort.releaseGray(unifiedRelease);
                }
            }
            if (releaseRequest.strategy() == ReleaseStrategy.FULL && finalAllSuccess) {
                // 原子切换：仅失效相同 packageCode 的 ACTIVE 知识包，不污染其他病种包
                List<KnowledgePackage> activePacks = packageRepository.findByTenantIdAndPackageCodeAndStatus(
                    tenantId, pack.packageCode(), KnowledgePackageStatus.ACTIVE);
                for (KnowledgePackage active : activePacks) {
                    packageRepository.save(active.withStatus(KnowledgePackageStatus.OFFLINE));
                }
                // 激活当前包
                packageRepository.save(pack.withStatus(KnowledgePackageStatus.ACTIVE));
            } else if (finalAllSuccess) {
                // 灰度发布全通道成功后才更新包状态，不覆盖现有 active。
                if (pack.status() == KnowledgePackageStatus.DRAFT) {
                    packageRepository.save(pack.withStatus(KnowledgePackageStatus.PUBLISHED));
                }
            }
        });

        // 事务外部：发布审计事实日志，确保子事务安全
        if (releaseRequest.strategy() == ReleaseStrategy.FULL && allSuccess) {
            auditRecorder.record(AuditAction.PUBLISH, "knowledge_package", packageId,
                "知识包发布并同步全量成功: " + pack.name() + " (" + pack.packageVersion() + ")");
        } else {
            auditRecorder.record(AuditAction.PUBLISH, "knowledge_package", packageId,
                "知识包发布计划执行完成, 策略为: " + releaseRequest.strategy()
                    + ", 作用域为: " + normalizedScope.scopeType()
                    + ", 状态为: " + finalStatus);
        }

        return new PackageSyncResponse(savedPlan.planId(), packageId, finalStatus, logs);
    }

    private PackageSyncRequest requireSyncRequest(PackageSyncRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "发布请求不能为空");
        }
        if (request.strategy() == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "发布策略不能为空");
        }
        if (request.targetOrgUnitId() == null || request.targetOrgUnitId().isBlank()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "目标组织 ID 不能为空");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "发布说明不能为空");
        }
        if (request.adapterIds() == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "发布适配器列表不能为空");
        }
        return request;
    }

    private PackageSyncResponse recordNotSyncedPlanForMissingChannel(
            String tenantId,
            String packageId,
            PackageSyncRequest releaseRequest,
            ReleaseScope normalizedScope,
            String actor,
            String traceId) {
        ReleasePlan plan = new ReleasePlan(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            packageId,
            releaseRequest.targetOrgUnitId(),
            releaseRequest.strategy(),
            normalizedScope.scopeType(),
            normalizedScope.scopeValue(),
            ReleasePlanStatus.EXECUTING,
            Instant.now(),
            actor,
            Instant.now(),
            actor,
            traceId
        );
        ReleasePlan savedPlan = transactionTemplate.execute(status -> planRepository.save(plan));
        transactionTemplate.executeWithoutResult(status -> planRepository.save(savedPlan.withStatus(ReleasePlanStatus.NOT_SYNCED)));
        auditRecorder.record(AuditAction.PUBLISH, "knowledge_package", packageId,
            "知识包发布计划未接入同步通道，状态为: " + ReleasePlanStatus.NOT_SYNCED);
        return new PackageSyncResponse(savedPlan.planId(), packageId, ReleasePlanStatus.NOT_SYNCED, List.of());
    }

    private AssetVersion prepareUnifiedPackageRelease(KnowledgePackage pack) {
        AssetVersion assetVersion = assetVersions
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                pack.tenantId(),
                VersionedAssetType.PACKAGE,
                packageAssetIdentity(pack),
                pack.packageVersion()
            )
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "配置包缺少统一资产版本，禁止发布: "
                    + pack.packageCode() + "@" + pack.packageVersion()
            ));
        return assetVersion;
    }

    private void preparePackageReleaseStatus(
            AssetVersion assetVersion,
            VersionReleaseCommand command) {
        if (assetVersion.status() == AssetVersionStatus.DRAFT) {
            releasePort.submitForReview(command);
            releasePort.approveReview(command);
        } else if (assetVersion.status() == AssetVersionStatus.IN_REVIEW) {
            releasePort.approveReview(command);
        } else if (assetVersion.status() != AssetVersionStatus.APPROVED
                && assetVersion.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "统一配置包版本状态不允许发布");
        }
    }

    private VersionReleaseCommand packageReleaseCommand(
            KnowledgePackage pack,
            AssetVersion assetVersion,
            EffectivePackageSnapshot snapshot,
            PackageSyncRequest request,
            ReleaseScope scope,
            String actor,
            String traceId) {
        return new VersionReleaseCommand(
            pack.tenantId(),
            VersionedAssetType.PACKAGE,
            packageAssetIdentity(pack),
            assetVersion.versionId(),
            assetVersion.organizationScope(),
            "ALL",
            request.strategy() == ReleaseStrategy.FULL
                ? VersionReleaseScopeType.ALL
                : VersionReleaseScopeType.valueOf(scope.scopeType().name()),
            request.strategy() == ReleaseStrategy.FULL ? null : scope.scopeValue(),
            request.strategy() == ReleaseStrategy.FULL
                ? RolloutPolicy.all()
                : RolloutPolicy.canaryBedPercent(DEFAULT_GRAY_SCOPE_PERCENTAGE),
            snapshot.contentSha256(),
            request.reason().trim(),
            actor,
            traceId,
            request.publishEvidence().electronicSignature(),
            request.publishEvidence().qualityGate()
        );
    }

    private AssetVersion requirePackageAsset(KnowledgePackage pack) {
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            pack.tenantId(),
            VersionedAssetType.PACKAGE,
            packageAssetIdentity(pack),
            pack.packageVersion()
        ).orElseThrow(() -> new ApiException(
            ErrorCode.ENG_PACKAGE_002,
            "配置包缺少统一资产版本，禁止回滚"
        ));
    }

    private String packageAssetIdentity(KnowledgePackage pack) {
        return pack.packageCode();
    }

    private String packageOrganizationScope(KnowledgePackage pack) {
        return "tenant:" + pack.tenantId();
    }

    private void validateReleaseAuthorization(
            KnowledgePackage pack,
            PackageSyncRequest request) {
        if (request.strategy() != ReleaseStrategy.FULL) {
            return;
        }
        boolean platformPackage = PlatformTenant.ID.equals(pack.tenantId());
        boolean allowed = platformPackage
            ? AuthenticatedRoleGuard.has(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR)
                || AuthenticatedRoleGuard.has(RoleCode.PLATFORM_GOVERNANCE_ADMIN)
            : AuthenticatedRoleGuard.has(RoleCode.KNOWLEDGE_GOVERNOR)
                || AuthenticatedRoleGuard.has(RoleCode.ORGANIZATION_ADMIN);
        if (!allowed) {
            String roleName = platformPackage ? "平台知识治理员" : "机构知识治理员或机构管理员";
            throw new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "配置包直接全量发布必须由" + roleName + "确认"
            );
        }
    }

    private ReleaseScope normalizeReleaseScope(PackageSyncRequest request) {
        if (request.strategy() != ReleaseStrategy.GRAYSCALE) {
            return new ReleaseScope(request.scopeType(), normalizedText(request.scopeValue()));
        }
        ReleaseScopeType requestedScopeType = request.scopeType();
        String requestedScopeValue = normalizedText(request.scopeValue());
        if (requestedScopeType == null || requestedScopeType == ReleaseScopeType.ALL) {
            return defaultGrayScope(request.targetOrgUnitId());
        }
        if (requestedScopeValue == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_003, "指定灰度发布作用域时必须填写作用域值");
        }
        return new ReleaseScope(requestedScopeType, requestedScopeValue);
    }

    private ReleaseScope defaultGrayScope(String targetOrgUnitId) {
        String scopeCode = normalizedText(targetOrgUnitId);
        return new ReleaseScope(ReleaseScopeType.FACILITY, scopeCode);
    }

    private String normalizedText(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record ReleaseScope(ReleaseScopeType scopeType, String scopeValue) {}

    private List<PilotPackageTemplate> activeTemplatesForTenant(String tenantId) {
        List<PilotPackageTemplate> templates = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        for (PilotPackageTemplate template : pilotTemplateRepository
                .findByTenantIdAndStatusOrderByTemplateCodeAsc(tenantId, PilotPackageTemplateStatus.ACTIVE)) {
            if (seenCodes.add(template.templateCode())) {
                templates.add(template);
            }
        }
        if (!PlatformTenant.ID.equals(tenantId)) {
            for (PilotPackageTemplate template : pilotTemplateRepository
                    .findByTenantIdAndStatusOrderByTemplateCodeAsc(
                        PlatformTenant.ID, PilotPackageTemplateStatus.ACTIVE)) {
                if (seenCodes.add(template.templateCode())) {
                    templates.add(template);
                }
            }
        }
        return templates;
    }

    private PilotPackageTemplate resolvePilotTemplate(String tenantId, String templateCode) {
        String normalizedTemplateCode = normalizedText(templateCode);
        if (normalizedTemplateCode == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "首发模板编码不能为空");
        }
        return pilotTemplateRepository
            .findByTenantIdAndTemplateCodeAndStatus(
                tenantId, normalizedTemplateCode, PilotPackageTemplateStatus.ACTIVE)
            .or(() -> PlatformTenant.ID.equals(tenantId)
                ? Optional.empty()
                : pilotTemplateRepository.findByTenantIdAndTemplateCodeAndStatus(
                    PlatformTenant.ID, normalizedTemplateCode, PilotPackageTemplateStatus.ACTIVE))
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PACKAGE_001,
                "首发模板不存在或已停用: " + normalizedTemplateCode
            ));
    }

    private PilotPackageTemplateApplyRequest requireApplyRequest(PilotPackageTemplateApplyRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "首发模板引用请求不能为空");
        }
        return request;
    }

    private String requireTargetOrgUnitId(String targetOrgUnitId) {
        String normalized = normalizedText(targetOrgUnitId);
        if (normalized == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "首发模板引用缺少目标组织");
        }
        return normalized;
    }

    private Optional<PilotPackageTemplateResponse> visiblePilotTemplate(
            String tenantId,
            PilotPackageTemplate template) {
        List<PilotPackageTemplateItem> visibleItems = new ArrayList<>();
        List<PilotPackageTemplateItem> templateItems = pilotTemplateItemRepository
            .findByTenantIdAndTemplateIdOrderBySortOrderAsc(template.tenantId(), template.templateId());
        for (PilotPackageTemplateItem item : templateItems) {
            if (item.assetType() != VersionedAssetType.PACKAGE) {
                visibleItems.add(item);
                continue;
            }
            try {
                requirePlatformPackage(tenantId, item.assetId(), item.assetVersion());
                visibleItems.add(item);
            } catch (ApiException ex) {
                if (item.required()) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(PilotPackageTemplateResponse.from(template, visibleItems));
    }

    private KnowledgePackage requirePlatformPackage(
            String tenantId,
            String packageCode,
            String packageVersion) {
        String normalizedCode = normalizedText(packageCode);
        String normalizedVersion = normalizedText(packageVersion);
        if (normalizedCode == null || normalizedVersion == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "平台包引用缺少包编码或版本");
        }
        KnowledgePackage platformPackage = packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                PlatformTenant.ID, normalizedCode, normalizedVersion)
            .orElseThrow(() -> new ApiException(
                ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                "平台知识包不存在: " + normalizedCode + "@" + normalizedVersion));
        if (!releasedPackage(platformPackage)) {
            throw new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "只允许引用 PUBLISHED 或 ACTIVE 状态的平台知识包, 当前: " + platformPackage.status()
            );
        }
        entitlementService.assertUsable(tenantId, platformPackage);
        return platformPackage;
    }

    private TenantPackageReference referenceFor(
            String tenantId,
            String templateCode,
            String targetOrgUnitId,
            KnowledgePackage platformPackage,
            String actor,
            String traceId,
            Instant now) {
        Optional<TenantPackageReference> existing =
            packageReferenceRepository.findByTenantIdAndPackageCodeAndPackageVersionAndTargetOrgUnitId(
                tenantId, platformPackage.packageCode(), platformPackage.packageVersion(), targetOrgUnitId);
        if (existing.isPresent()) {
            return existing.get();
        }
        TenantPackageReference reference = new TenantPackageReference(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            PlatformTenant.ID,
            platformPackage.packageId(),
            platformPackage.packageCode(),
            platformPackage.packageVersion(),
            targetOrgUnitId,
            templateCode,
            TenantPackageReferenceStatus.ACTIVE,
            now,
            actor,
            now,
            actor,
            traceId
        );
        TenantPackageReference saved = packageReferenceRepository.save(reference);
        auditRecorder.record(
            AuditAction.CREATE,
            "tenant_package_reference",
            saved.referenceId(),
            "应用首发模板引用平台知识包: " + platformPackage.packageCode() + "@" + platformPackage.packageVersion()
        );
        return saved;
    }

    private List<PilotPackageInitialOverrideResponse> registerInitialOverrides(
            String tenantId,
            String actor,
            String traceId,
            List<PilotPackageInitialOverrideRequest> requests) {
        List<PilotPackageInitialOverrideResponse> responses = new ArrayList<>();
        for (PilotPackageInitialOverrideRequest request : requests) {
            if (request == null) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "初始覆盖请求不能为空");
            }
            InheritanceOverride override = inheritanceOverrideService.registerOverride(
                new InheritanceOverrideRegisterCommand(
                    tenantId,
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
                    actor,
                    traceId,
                    request.propagation()
                ));
            responses.add(PilotPackageInitialOverrideResponse.from(override));
        }
        return responses;
    }

    private boolean releasedPackage(KnowledgePackage knowledgePackage) {
        return knowledgePackage != null
            && (knowledgePackage.status() == KnowledgePackageStatus.PUBLISHED
                || knowledgePackage.status() == KnowledgePackageStatus.ACTIVE);
    }

    private void assertPackageReadyForRelease(String packageId) {
        PackageValidateResponse validation = validatePackage(packageId);
        if (validation.valid()) {
            return;
        }
        List<String> blockingIssues = validation.issues().stream()
            .filter(issue -> "BLOCKING".equals(issue.severity()))
            .map(issue -> issue.field() + "：" + issue.message())
            .toList();
        throw new ApiException(
            ErrorCode.ENG_PACKAGE_002,
            "配置包发布前校验未通过: " + String.join("；", blockingIssues)
        );
    }

    /**
     * 按 API-10 发布入口执行灰度或全量发布。
     */
    public PackageSyncResponse releasePackage(String packageId, PackageSyncRequest request) {
        return syncPackage(packageId, request);
    }

    /**
     * 查询包关联发布计划的真实同步日志。
     */
    @Transactional(readOnly = true)
    public PageResponse<SyncLogResponse> listSyncLogs(String packageId, PageRequest page) {
        String tenantId = currentTenantId();
        packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));
        PageRequest safePage = page == null ? PageRequest.defaults() : page;
        long total = logRepository.countByTenantIdAndPackageId(tenantId, packageId);
        List<SyncLogResponse> logs = logRepository
            .pageByTenantIdAndPackageId(tenantId, packageId, safePage.offset(), safePage.safeSize()).stream()
            .map(SyncLogResponse::from)
            .toList();
        return PageResponse.of(logs, safePage, total);
    }

    /**
     * 一键快速回滚包版本到指定历史点。
     */
    public PackageResponse rollbackPackage(String packageId, PackageRollbackRequest request) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        PackageRollbackRequest rollbackRequest = requireRollbackRequest(request);
        String targetPackageId = requireRollbackText(rollbackRequest.targetPackageId(), "回滚目标包不能为空");
        String rollbackReason = validateRollbackRequestShape(rollbackRequest);

        KnowledgePackage currentActive = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "当前在用包不存在: " + packageId));

        KnowledgePackage targetRollback = packageRepository.findByPackageIdAndTenantId(targetPackageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "回滚目标包不存在: " + targetPackageId));

        validateRollbackAssets(currentActive, targetRollback, rollbackRequest);

        if (targetRollback.status() != KnowledgePackageStatus.OFFLINE) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "回滚目标包必须是曾经执行并已下线的历史版本（OFFLINE）");
        }

        RollbackSyncScope rollbackScope = resolveRollbackSyncScope(tenantId, currentActive);
        ReleasePlan rollbackPlan = new ReleasePlan(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            targetPackageId,
            rollbackScope.originalPlan().targetOrgUnitId(),
            ReleaseStrategy.FULL,
            ReleaseScopeType.ALL,
            null,
            ReleasePlanStatus.EXECUTING,
            Instant.now(),
            actor,
            Instant.now(),
            actor,
            traceId
        );
        ReleasePlan savedPlan = transactionTemplate.execute(status -> planRepository.save(rollbackPlan));

        EffectivePackageSnapshot rollbackSnapshot = buildEffectiveSnapshot(
            tenantId, targetRollback, rollbackScope.originalPlan().targetOrgUnitId());
        RollbackProjectionResult projectionResult = projectRollbackToOriginalAdapters(
            tenantId, savedPlan, rollbackScope.adapterIds(), rollbackSnapshot, actor, traceId);
        final KnowledgePackage[] savedTargetHolder = new KnowledgePackage[1];
        transactionTemplate.executeWithoutResult(status -> {
            planRepository.save(savedPlan.withStatus(projectionResult.finalStatus()));
            if (projectionResult.allSuccess()) {
                AssetVersion currentVersion = requirePackageAsset(currentActive);
                AssetVersion targetVersion = requirePackageAsset(targetRollback);
                releasePort.rollback(new VersionRollbackCommand(
                    tenantId,
                    VersionedAssetType.PACKAGE,
                    packageAssetIdentity(currentActive),
                    currentVersion.versionId(),
                    targetVersion.versionId(),
                    currentActive.packageVersion(),
                    targetRollback.packageVersion(),
                    rollbackReason,
                    rollbackRequest.confirmedHighRisk(),
                    actor,
                    traceId
                ));
                packageRepository.save(currentActive.withStatus(KnowledgePackageStatus.OFFLINE));
                savedTargetHolder[0] = packageRepository.save(targetRollback.withStatus(KnowledgePackageStatus.ACTIVE));
            }
        });

        if (!projectionResult.allSuccess()) {
            auditRecorder.record(AuditAction.ROLLBACK, "knowledge_package", targetPackageId,
                "一键回滚包版本从 " + currentActive.packageVersion()
                    + " 回退到 " + targetRollback.packageVersion()
                    + " 失败，发布计划状态: " + projectionResult.finalStatus()
                    + "，原因: " + rollbackReason
                    + "，操作人: " + actor);
            throw new ApiException(ErrorCode.ENG_PACKAGE_005, "回滚同步发布未全部成功，包状态未变更");
        }

        // 异步发布回滚审计事实存证
        auditRecorder.record(AuditAction.ROLLBACK, "knowledge_package", targetPackageId,
            "一键回滚包版本从 " + currentActive.packageVersion()
                + " 回退到 " + targetRollback.packageVersion()
                + "，原因: " + rollbackReason
                + "，操作人: " + actor);

        return PackageResponse.from(savedTargetHolder[0]);
    }

    /**
     * 获取当前租户下可用于配置包发布的启用适配器。
     *
     * @return 发布适配器列表
     */
    @Transactional(readOnly = true)
    public PageResponse<PackageReleaseAdapterResponse> listReleaseAdapters(PageRequest page) {
        String tenantId = currentTenantId();
        PageRequest safePage = page == null ? PageRequest.defaults() : page;
        long total = adapterRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
        List<PackageReleaseAdapterResponse> adapters = adapterRepository
            .pageByTenantIdAndStatus(tenantId, "ACTIVE", safePage.offset(), safePage.safeSize()).stream()
            .map(adapter -> PackageReleaseAdapterResponse.from(adapter, syncPort.supports(adapter)))
            .toList();
        return PageResponse.of(adapters, safePage, total);
    }

    private EffectivePackageSnapshot buildEffectiveSnapshot(
            String tenantId,
            KnowledgePackage pack,
            String targetOrgUnitId) {
        return EffectivePackageSnapshot.from(effectivePackageResolver.resolveOwnedLifecycleCandidate(
            tenantId, pack, targetOrgUnitId));
    }

    private String syncEvidenceWithSnapshot(String adapterEvidence, EffectivePackageSnapshot snapshot) {
        try {
            return PACKAGE_JSON_MAPPER.writeValueAsString(new PackageSyncSnapshotEvidence(
                snapshot.contentSha256(),
                snapshot.items().size(),
                snapshot.excludedItems().size(),
                snapshot.warnings(),
                adapterEvidence));
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "同步证据快照序列化失败", e);
        }
    }

    private void requireAdapterEvidence(String adapterEvidence, String message) {
        if (adapterEvidence == null || adapterEvidence.isBlank()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_005, message);
        }
    }

    private record PackageSyncSnapshotEvidence(
        String effectiveSnapshotSha256,
        int effectiveItemCount,
        int excludedItemCount,
        List<String> warnings,
        String adapterEvidence
    ) {}

    // ────────────────────────── 辅助支撑逻辑 ──────────────────────────

    private PackageRollbackRequest requireRollbackRequest(PackageRollbackRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "回滚请求不能为空");
        }
        return request;
    }

    private String validateRollbackRequestShape(PackageRollbackRequest request) {
        requireRollbackText(request.confirmedCurrentVersion(), "当前在用版本确认不能为空");
        requireRollbackText(request.confirmedTargetVersion(), "目标回滚版本确认不能为空");
        String reason = requireRollbackText(request.reason(), "回滚原因不能为空");
        if (!Boolean.TRUE.equals(request.confirmedHighRisk())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "回滚属于高危操作，必须完成二次确认");
        }
        return reason;
    }

    private void validateRollbackAssets(
            KnowledgePackage currentActive,
            KnowledgePackage targetRollback,
            PackageRollbackRequest request) {
        String confirmedCurrentVersion = requireRollbackText(
            request.confirmedCurrentVersion(),
            "当前在用版本确认不能为空");
        String confirmedTargetVersion = requireRollbackText(
            request.confirmedTargetVersion(),
            "目标回滚版本确认不能为空");

        if (currentActive.status() != KnowledgePackageStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "当前包必须处于 ACTIVE 状态才允许执行回滚");
        }
        if (!currentActive.packageCode().equals(targetRollback.packageCode())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "回滚目标包必须与当前在用包属于同一配置包编码");
        }
        if (!currentActive.packageVersion().equals(confirmedCurrentVersion)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "当前在用版本确认与实际版本不一致");
        }
        if (!targetRollback.packageVersion().equals(confirmedTargetVersion)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "目标回滚版本确认与实际版本不一致");
        }
    }

    private String requireRollbackText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, message);
        }
        return normalized;
    }

    private RollbackSyncScope resolveRollbackSyncScope(String tenantId, KnowledgePackage currentActive) {
        List<ReleasePlan> reusablePlans = planRepository
            .findByTenantIdAndPackageIdOrderByCreatedAtDesc(tenantId, currentActive.packageId()).stream()
            .filter(plan -> plan.status() == ReleasePlanStatus.SUCCESS || plan.status() == ReleasePlanStatus.ROLLBACKED)
            .toList();

        for (ReleasePlan plan : reusablePlans) {
            List<String> adapterIds = logRepository.findByTenantIdAndPlanId(tenantId, plan.planId()).stream()
                .filter(syncLog -> syncLog.status() == SyncLogStatus.SUCCESS)
                .map(SyncLog::adapterId)
                .distinct()
                .toList();
            if (!adapterIds.isEmpty()) {
                return new RollbackSyncScope(plan, adapterIds);
            }
        }

        throw new ApiException(ErrorCode.ENG_PACKAGE_002, "当前在用包缺少成功发布适配器记录，不能执行回滚");
    }

    private RollbackProjectionResult projectRollbackToOriginalAdapters(
            String tenantId,
            ReleasePlan savedPlan,
            List<String> adapterIds,
            EffectivePackageSnapshot rollbackSnapshot,
            String actor,
            String traceId) {
        boolean anySuccess = false;
        boolean allSuccess = true;
        boolean anyNotSynced = false;
        boolean anyFailed = false;

        for (String adapterId : adapterIds) {
            SyncLog savedLog = transactionTemplate.execute(status -> {
                SyncLog syncLog = new SyncLog(
                    null,
                    UUID.randomUUID().toString(),
                    tenantId,
                    savedPlan.planId(),
                    adapterId,
                    SyncLogStatus.RUNNING,
                    null, null, 0, null,
                    Instant.now(), actor, Instant.now(), actor, traceId
                );
                return logRepository.save(syncLog);
            });

            String evidence = null;
            Exception syncError = null;
            Optional<IntegrationAdapter> adapter = adapterRepository.findByAdapterIdAndTenantId(adapterId, tenantId);
            if (adapter.isEmpty()) {
                syncError = new ApiException(ErrorCode.ENG_PACKAGE_001, "回滚同步适配器不存在: " + adapterId);
            } else {
                try {
                    evidence = syncPort.sync(tenantId, savedPlan, adapter.get(), rollbackSnapshot);
                    if (evidence == null || evidence.isBlank()) {
                        syncError = new ApiException(ErrorCode.ENG_PACKAGE_005, "回滚同步未返回同步证据: " + adapterId);
                    } else {
                        evidence = syncEvidenceWithSnapshot(evidence, rollbackSnapshot);
                    }
                } catch (Exception e) {
                    if (e instanceof PackageSyncNotConnectedException) {
                        log.warn("回滚同步发布未接入真实同步适配器, adapterId: {}, reason: {}", adapterId, e.getMessage());
                    } else {
                        log.error("回滚同步发布失败, adapterId: {}", adapterId, e);
                    }
                    syncError = e;
                }
            }

            final String finalEvidence = evidence;
            final Exception finalError = syncError;
            transactionTemplate.execute(status -> {
                if (finalError == null) {
                    return logRepository.save(new SyncLog(
                        savedLog.id(),
                        savedLog.logId(),
                        tenantId,
                        savedPlan.planId(),
                        adapterId,
                        SyncLogStatus.SUCCESS,
                        null, null, 0, finalEvidence,
                        savedLog.createdAt(), savedLog.createdBy(), Instant.now(), actor, traceId
                    ));
                } else if (finalError instanceof PackageSyncNotConnectedException) {
                    return logRepository.save(new SyncLog(
                        savedLog.id(),
                        savedLog.logId(),
                        tenantId,
                        savedPlan.planId(),
                        adapterId,
                        SyncLogStatus.NOT_SYNCED,
                        PackageSyncNotConnectedException.CODE,
                        finalError.getMessage(),
                        0, null,
                        savedLog.createdAt(), savedLog.createdBy(), Instant.now(), actor, traceId
                    ));
                }
                return logRepository.save(new SyncLog(
                    savedLog.id(),
                    savedLog.logId(),
                    tenantId,
                    savedPlan.planId(),
                    adapterId,
                    SyncLogStatus.FAILED,
                    syncFailureCode(finalError),
                    finalError.getMessage(),
                    0, null,
                    savedLog.createdAt(), savedLog.createdBy(), Instant.now(), actor, traceId
                ));
            });

            if (syncError == null) {
                anySuccess = true;
            } else {
                allSuccess = false;
                if (syncError instanceof PackageSyncNotConnectedException) {
                    anyNotSynced = true;
                } else {
                    anyFailed = true;
                }
            }
        }

        ReleasePlanStatus finalStatus = allSuccess ? ReleasePlanStatus.ROLLBACKED
            : (anyNotSynced && !anySuccess && !anyFailed ? ReleasePlanStatus.NOT_SYNCED : ReleasePlanStatus.FAILED);
        return new RollbackProjectionResult(finalStatus, allSuccess);
    }

    private String syncFailureCode(Exception error) {
        if (error instanceof ApiException apiException) {
            return apiException.errorCode().code();
        }
        return ErrorCode.ENG_PACKAGE_005.code();
    }

    private record RollbackSyncScope(ReleasePlan originalPlan, List<String> adapterIds) {}

    private record RollbackProjectionResult(ReleasePlanStatus finalStatus, boolean allSuccess) {}

    private String currentTenantId() {
        return RequestContext.snapshot().orgScope().tenantId();
    }

    private String currentActor() {
        return RequestContext.snapshot().userId() == null ? "system" : RequestContext.snapshot().userId();
    }

    private void validateAssetStatus(String tenantId, VersionedAssetType type, String assetId, String assetVersion) {
        switch (type) {
            case RULE -> {
                RuleDefinition rule = ruleRepository.findByRuleIdAndTenantId(assetId, tenantId)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包规则不存在: " + assetId));
                // 审核通过的规则方可入包
                String status = rule.status() == null ? "" : rule.status().name();
                if (!"PUBLISHED".equalsIgnoreCase(status) && !"ACTIVE".equalsIgnoreCase(status)) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只允许 PUBLISHED 或 ACTIVE 状态的规则入包, 当前: " + status);
                }
            }
            case PATHWAY -> {
                PathwayTemplate template = pathwayRepository.findByTemplateIdAndTenantId(assetId, tenantId)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包路径不存在: " + assetId));
                String status = template.status() == null ? "" : template.status().name();
                if (!"PUBLISHED".equalsIgnoreCase(status) && !"ACTIVE".equalsIgnoreCase(status)) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只允许 PUBLISHED 或 ACTIVE 状态的路径入包, 当前: " + status);
                }
            }
            case EVALUATION -> {
                EvaluationIndicator indicator = evaluationRepository.findByIndicatorIdAndTenantId(assetId, tenantId)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包评估指标不存在: " + assetId));
                String status = indicator.status() == null ? "" : indicator.status().name();
                if (!"PUBLISHED".equalsIgnoreCase(status) && !"ACTIVE".equalsIgnoreCase(status)) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只允许 PUBLISHED 或 ACTIVE 状态的评估指标入包, 当前: " + status);
                }
            }
            case KNOWLEDGE -> {
                KnowledgeIdentity identity = knowledgeIdentityRepository.findByTenantIdAndIdentityCode(tenantId, assetId)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包知识身份不存在: " + assetId));
                if (!identity.isActive()) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只允许 ACTIVE 状态的知识身份入包, 当前: " + identity.status());
                }
                KnowledgeAssetVersion version = knowledgeVersionRepository
                    .findByTenantIdAndIdentityIdAndVersionNo(tenantId, identity.id(), assetVersion)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包知识版本不存在: " + assetId + "@" + assetVersion
                    ));
                if (!version.isAuthoritative()) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "只允许 ACTIVE 状态的知识版本入包, 当前: " + version.status()
                    );
                }
                AssetVersion unifiedVersion = assetVersions
                    .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                        tenantId, VersionedAssetType.KNOWLEDGE, assetId, assetVersion)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "统一知识版本不存在: " + assetId + "@" + assetVersion
                    ));
                if (unifiedVersion.status() != AssetVersionStatus.PUBLISHED) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "只允许统一底座 PUBLISHED 状态的知识版本入包, 当前: "
                            + unifiedVersion.status()
                    );
                }
            }
            case TERMINOLOGY -> {
                TerminologyAssetKey key = parseTerminologyAssetKey(assetId);
                KnowledgePackage terminologyPackage = packageRepository
                    .findByTenantIdAndPackageCodeAndPackageVersion(
                        tenantId, key.packageCode(), assetVersion)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包术语知识包不存在: " + assetId + "@" + assetVersion
                    ));
                if (terminologyPackage.status() != KnowledgePackageStatus.PUBLISHED
                        && terminologyPackage.status() != KnowledgePackageStatus.ACTIVE) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "只允许 PUBLISHED 或 ACTIVE 状态的术语知识包入包, 当前: "
                            + terminologyPackage.status()
                    );
                }
                itemRepository.findByTenantIdAndPackageIdAndAssetTypeAndAssetId(
                        tenantId,
                        terminologyPackage.packageId(),
                        VersionedAssetType.TERMINOLOGY,
                        terminologyAssetId(key))
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "术语知识包缺少范围快照条目: " + assetId
                    ));
            }
            case CONDITION_FRAGMENT -> {
                ConditionFragment fragment = conditionFragmentRepository.findByFragmentIdAndTenantId(assetId, tenantId)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包条件片段不存在: " + assetId
                    ));
                if (!Integer.toString(fragment.versionNo()).equals(assetVersion)) {
                    throw new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包条件片段版本不存在: " + assetId + "@" + assetVersion
                    );
                }
                if (fragment.status() != ConditionFragmentStatus.ACTIVE) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "只允许 ACTIVE 状态的条件片段入包, 当前: " + fragment.status()
                    );
                }
                AssetVersion unifiedVersion = assetVersions
                    .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                        tenantId, VersionedAssetType.CONDITION_FRAGMENT, assetId, assetVersion)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "统一条件片段版本不存在: " + assetId + "@" + assetVersion
                    ));
                if (unifiedVersion.status() != AssetVersionStatus.PUBLISHED) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "只允许统一底座 PUBLISHED 状态的条件片段入包, 当前: "
                            + unifiedVersion.status()
                    );
                }
            }
            case FOLLOWUP -> {
                FollowupTemplate template = followupTemplateRepository
                    .findByTemplateIdAndTenantId(assetId, tenantId)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包随访模板不存在: " + assetId
                    ));
                if (!Integer.toString(template.versionNo()).equals(assetVersion)) {
                    throw new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包随访模板版本不存在: " + assetId + "@" + assetVersion
                    );
                }
                AssetVersion unifiedVersion = assetVersions
                    .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                        tenantId, VersionedAssetType.FOLLOWUP, assetId, assetVersion)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "统一随访模板版本不存在: " + assetId + "@" + assetVersion
                    ));
                if (unifiedVersion.status() != AssetVersionStatus.PUBLISHED) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "只允许统一底座 PUBLISHED 状态的随访模板入包, 当前: "
                            + unifiedVersion.status()
                    );
                }
            }
            default -> {
                if (!isDeclarativePackageAssetType(type)) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "该资产类型尚未定义配置包迁移契约，不允许入包: " + type
                    );
                }
                AssetVersion version = assetVersions
                    .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                        tenantId, type, assetId, assetVersion)
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包声明型配置资产版本不存在: " + type + ":" + assetId + "@" + assetVersion
                    ));
                if (version.status() != AssetVersionStatus.PUBLISHED) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "只允许 PUBLISHED 状态的声明型配置资产入包, 当前: " + version.status()
                    );
                }
            }
        }
    }

    private TerminologyAssetKey parseTerminologyAssetKey(String assetId) {
        String[] parts = assetId == null ? new String[0] : assetId.split("\\|", -1);
        if (parts.length != 3
                || parts[0].isBlank()
                || parts[1].isBlank()
                || parts[2].isBlank()) {
            throw new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "术语映射包资产 ID 必须为 packageCode|scopeLevel|scopeCode: " + assetId
            );
        }
        return new TerminologyAssetKey(parts[0].trim(), parts[1].trim(), parts[2].trim());
    }

    private String terminologyAssetId(TerminologyAssetKey terminologyPackage) {
        return terminologyPackage.packageCode()
            + "|"
            + terminologyPackage.scopeLevel()
            + "|"
            + terminologyPackage.scopeCode();
    }

    private record TerminologyAssetKey(
        String packageCode,
        String scopeLevel,
        String scopeCode
    ) {}

    private String getAssetDepartment(String tenantId, VersionedAssetType type, String assetId) {
        switch (type) {
            case RULE -> {
                return ruleRepository.findByRuleIdAndTenantId(assetId, tenantId)
                    .map(RuleDefinition::applicableOrgUnitId)
                    .map(PackageEngineService::normalizeDepartmentId)
                    .orElse(null);
            }
            case PATHWAY -> {
                pathwayRepository.findByTemplateIdAndTenantId(assetId, tenantId);
                return null;
            }
            case EVALUATION -> {
                return evaluationRepository.findByIndicatorIdAndTenantId(assetId, tenantId)
                    .map(EvaluationIndicator::responsibleDepartmentId)
                    .map(PackageEngineService::normalizeDepartmentId)
                    .orElse(null);
            }
            default -> {
                return null;
            }
        }
    }

    private static String normalizeDepartmentId(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return null;
        }
        return departmentId.strip();
    }
}
