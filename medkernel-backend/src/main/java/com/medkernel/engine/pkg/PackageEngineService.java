package com.medkernel.engine.pkg;

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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
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
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.terminology.TermMapping;
import com.medkernel.engine.terminology.TermMappingPackage;
import com.medkernel.engine.terminology.TermMappingPackageItem;
import com.medkernel.engine.terminology.TermMappingPackageItemRepository;
import com.medkernel.engine.terminology.TermMappingPackageRepository;
import com.medkernel.engine.terminology.TermMappingRepository;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
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
    private static final String OFFLINE_PACKAGE_FORMAT = "MEDKERNEL_PACKAGE_OFFLINE_V1";
    private static final String DEFAULT_GRAY_SCOPE_STRATEGY = "BED_PERCENT";
    private static final int DEFAULT_GRAY_SCOPE_PERCENTAGE = 10;

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository itemRepository;
    private final ReleasePlanRepository planRepository;
    private final SyncTargetRepository targetRepository;
    private final SyncLogRepository logRepository;

    private final RuleDefinitionRepository ruleRepository;
    private final PathwayTemplateRepository pathwayRepository;
    private final EvaluationIndicatorRepository evaluationRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final KnowledgeIdentityRepository knowledgeIdentityRepository;
    private final KnowledgeAssetVersionRepository knowledgeVersionRepository;
    private final TermMappingPackageRepository terminologyPackageRepository;
    private final TermMappingPackageItemRepository terminologyPackageItemRepository;
    private final TermMappingRepository terminologyMappingRepository;
    private final PilotPackageTemplateRepository pilotTemplateRepository;
    private final PilotPackageTemplateItemRepository pilotTemplateItemRepository;

    private final PackageSyncPort syncPort;
    private final AuditEventPublisher auditPublisher;
    private final TransactionTemplate transactionTemplate;

    public PackageEngineService(
            KnowledgePackageRepository packageRepository,
            PackageItemRepository itemRepository,
            ReleasePlanRepository planRepository,
            SyncTargetRepository targetRepository,
            SyncLogRepository logRepository,
            RuleDefinitionRepository ruleRepository,
            RuleVersionRepository ruleVersionRepository,
            PathwayTemplateRepository pathwayRepository,
            EvaluationIndicatorRepository evaluationRepository,
            KnowledgeIdentityRepository knowledgeIdentityRepository,
            KnowledgeAssetVersionRepository knowledgeVersionRepository,
            TermMappingPackageRepository terminologyPackageRepository,
            TermMappingPackageItemRepository terminologyPackageItemRepository,
            TermMappingRepository terminologyMappingRepository,
            PilotPackageTemplateRepository pilotTemplateRepository,
            PilotPackageTemplateItemRepository pilotTemplateItemRepository,
            PackageSyncPort syncPort,
            AuditEventPublisher auditPublisher,
            TransactionTemplate transactionTemplate) {
        this.packageRepository = packageRepository;
        this.itemRepository = itemRepository;
        this.planRepository = planRepository;
        this.targetRepository = targetRepository;
        this.logRepository = logRepository;
        this.ruleRepository = ruleRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.pathwayRepository = pathwayRepository;
        this.evaluationRepository = evaluationRepository;
        this.knowledgeIdentityRepository = knowledgeIdentityRepository;
        this.knowledgeVersionRepository = knowledgeVersionRepository;
        this.terminologyPackageRepository = terminologyPackageRepository;
        this.terminologyPackageItemRepository = terminologyPackageItemRepository;
        this.terminologyMappingRepository = terminologyMappingRepository;
        this.pilotTemplateRepository = pilotTemplateRepository;
        this.pilotTemplateItemRepository = pilotTemplateItemRepository;
        this.syncPort = syncPort;
        this.auditPublisher = auditPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 创建知识包草稿。
     */
    @Transactional
    public PackageResponse createPackage(PackageCreateRequest request) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();

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
            KnowledgePackageStatus.DRAFT,
            Instant.now(),
            actor,
            Instant.now(),
            actor,
            traceId
        );

        KnowledgePackage saved = packageRepository.save(pack);
        auditPublisher.publish(AuditAction.CREATE, "knowledge_package", saved.packageId(), 
            "创建知识包草稿: " + saved.name() + " (" + saved.packageVersion() + ")");
        return PackageResponse.from(saved);
    }

    /**
     * 获取当前租户下的知识包列表。
     */
    @Transactional(readOnly = true)
    public PageResponse<KnowledgePackage> listPackages(PageRequest page) {
        String tenantId = currentTenantId();
        int offset = page.offset();
        int limit = page.safeSize();

        List<KnowledgePackage> items = packageRepository.pageByTenantId(tenantId, offset, limit);
        long total = packageRepository.countByTenantId(tenantId);
        return PageResponse.of(items, page, total);
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

        PackageItem saved = itemRepository.save(item);
        auditPublisher.publish(AuditAction.UPDATE, "knowledge_package", packageId,
            "向知识包添加资产条目 (" + request.assetType() + "): " + request.assetId());
        return PackageItemResponse.from(saved);
    }

    /**
     * 查询当前租户可用的试点首发模板；租户模板优先，平台模板兜底。
     */
    @Transactional(readOnly = true)
    public List<PilotPackageTemplateResponse> listPilotTemplates() {
        return activeTemplatesForTenant(currentTenantId()).stream()
            .map(template -> PilotPackageTemplateResponse.from(
                template,
                pilotTemplateItemRepository.findByTenantIdAndTemplateIdOrderBySortOrderAsc(
                    template.tenantId(), template.templateId())))
            .toList();
    }

    /**
     * 将试点首发模板实例化为配置包草稿，并按模板资产项自动组包。
     */
    @Transactional
    public PilotPackageInstantiationResponse instantiatePilotTemplate(
            String templateCode,
            PilotPackageTemplateInstantiateRequest request) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        PilotPackageTemplateInstantiateRequest instantiateRequest = requireInstantiateRequest(request);
        PilotPackageTemplate template = resolvePilotTemplate(tenantId, templateCode);
        List<PilotPackageTemplateItem> templateItems = pilotTemplateItemRepository
            .findByTenantIdAndTemplateIdOrderBySortOrderAsc(template.tenantId(), template.templateId());
        if (templateItems.isEmpty()) {
            throw new ApiException(
                ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                "首发模板未配置任何资产项: " + template.templateCode()
            );
        }

        String packageCode = firstNonBlank(instantiateRequest.packageCode(), template.packageCodePrefix());
        String packageVersion = firstNonBlank(instantiateRequest.packageVersion(), template.defaultPackageVersion());
        String packageName = firstNonBlank(instantiateRequest.name(), template.name());
        String packageDescription = firstNonBlank(instantiateRequest.description(), template.description());
        requirePackageIdentity(packageCode, packageVersion, packageName);

        if (packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                tenantId, packageCode, packageVersion).isPresent()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_004, "知识包版本在该租户内已存在: " + packageVersion);
        }

        List<PilotPackageTemplateItem> validItems = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        for (PilotPackageTemplateItem item : templateItems) {
            try {
                validateAssetStatus(tenantId, item.assetType(), item.assetId(), item.assetVersion());
                validItems.add(item);
            } catch (ApiException ex) {
                if (item.required()) {
                    blockers.add(item.assetType() + ":" + item.assetId() + "@" + item.assetVersion()
                        + "：" + ex.getMessage());
                }
            }
        }
        if (!blockers.isEmpty()) {
            throw new ApiException(
                ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                "首发模板依赖资产缺失或未发布: " + String.join("；", blockers)
            );
        }
        if (validItems.isEmpty()) {
            throw new ApiException(
                ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                "首发模板未命中可入包资产: " + template.templateCode()
            );
        }

        Instant now = Instant.now();
        KnowledgePackage pack = new KnowledgePackage(
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
        KnowledgePackage savedPackage = packageRepository.save(pack);
        List<PackageItemResponse> savedItems = validItems.stream()
            .map(item -> itemRepository.save(new PackageItem(
                null,
                UUID.randomUUID().toString(),
                tenantId,
                savedPackage.packageId(),
                item.assetType(),
                item.assetId(),
                item.assetVersion(),
                now,
                actor,
                now,
                actor,
                traceId
            )))
            .map(PackageItemResponse::from)
            .toList();

        auditPublisher.publish(AuditAction.CREATE, "knowledge_package", savedPackage.packageId(),
            "由首发模板实例化配置包草稿: " + template.templateCode()
                + "，资产条目数: " + savedItems.size());
        return new PilotPackageInstantiationResponse(
            template.templateCode(),
            PackageResponse.from(savedPackage),
            savedItems
        );
    }

    /**
     * 复算配置资产准备状态，供实施向导读取。
     */
    @Transactional(readOnly = true)
    public PackageAssetReadinessResponse getAssetReadiness() {
        String tenantId = currentTenantId();
        int templateCount = activeTemplatesForTenant(tenantId).size();
        List<KnowledgePackage> packages = packageRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
        long draftCount = packages.stream()
            .filter(pack -> pack.status() == KnowledgePackageStatus.DRAFT)
            .count();
        long releasedCount = packages.stream()
            .filter(this::releasedPackage)
            .count();
        long activeCount = packages.stream()
            .filter(pack -> pack.status() == KnowledgePackageStatus.ACTIVE)
            .count();
        String readyPackageId = packages.stream()
            .filter(pack -> pack.status() == KnowledgePackageStatus.ACTIVE)
            .findFirst()
            .or(() -> packages.stream().filter(pack -> pack.status() == KnowledgePackageStatus.PUBLISHED).findFirst())
            .map(KnowledgePackage::packageId)
            .orElse(null);
        boolean grayscaleReady = planRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .anyMatch(plan -> plan.strategy() == ReleaseStrategy.GRAYSCALE
                && plan.status() == ReleasePlanStatus.SUCCESS);

        List<String> blockers = new ArrayList<>();
        if (templateCount == 0) {
            blockers.add("未配置可用的试点首发模板");
        }
        if (releasedCount == 0) {
            blockers.add("配置包尚未灰度发布或启用");
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
        issues.addAll(validatePackageItemDependencies(tenantId, items));
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

    private List<PackageValidateIssue> validatePackageItemDependencies(String tenantId, List<PackageItem> items) {
        List<PackageValidateIssue> issues = new ArrayList<>();
        for (PackageItem item : items) {
            try {
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

        auditPublisher.publish(AuditAction.EXPORT, "knowledge_package", packageId,
            "导出配置包差异影响证据，基准版本: " + diff.baseVersion()
                + "，目标版本: " + diff.targetVersion()
                + "，变更资产数: " + diff.changes().size());
        return ndjson.toString();
    }

    /**
     * 导出配置包同步证据与失败站点清单。
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

        long successTargetCount = allLogs.stream()
            .filter(log -> log.status() == SyncLogStatus.SUCCESS)
            .count();
        long failedTargetCount = allLogs.stream()
            .filter(log -> log.status() == SyncLogStatus.FAILED)
            .count();
        long notSyncedTargetCount = allLogs.stream()
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
            successTargetCount,
            failedTargetCount,
            notSyncedTargetCount,
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
                appendEvidenceExportLine(ndjson, new PackageSyncTargetExportLine(
                    "PACKAGE_SYNC_TARGET",
                    packageId,
                    log.planId(),
                    log.logId(),
                    log.targetId(),
                    resolveSyncTargetName(tenantId, log.targetId()),
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

        auditPublisher.publish(AuditAction.EXPORT, "knowledge_package", packageId,
            "导出配置包同步证据，发布计划数: " + plans.size()
                + "，同步日志数: " + allLogs.size()
                + "，失败站点数: " + failedTargetCount
                + "，未接入站点数: " + notSyncedTargetCount);
        return ndjson.toString();
    }

    private String resolveSyncTargetName(String tenantId, String targetId) {
        return targetRepository.findByTargetIdAndTenantId(targetId, tenantId)
            .map(SyncTarget::targetName)
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .orElse(targetId);
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
        long successTargetCount,
        long failedTargetCount,
        long notSyncedTargetCount,
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

    private record PackageSyncTargetExportLine(
        String event,
        String packageId,
        String planId,
        String logId,
        String targetId,
        String targetName,
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
    public String exportOfflinePackage(String packageId) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        KnowledgePackage pack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));
        List<PackageItem> items = itemRepository.findByTenantIdAndPackageId(tenantId, packageId);

        List<PackageOfflineAssetSnapshot> assetSnapshots = buildOfflineAssetSnapshots(tenantId, items);
        PackageOfflinePayload payload = new PackageOfflinePayload(
            PackageOfflinePackageInfo.from(pack),
            items.stream().map(PackageOfflineItem::from).toList(),
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
                items.size(),
                assetSnapshots.size(),
                "SHA-256",
                payloadSha256,
                Instant.now().toString(),
                traceId
            ),
            payload
        );

        auditPublisher.publish(AuditAction.EXPORT, "knowledge_package", packageId,
            "导出配置包离线安装包，版本: " + pack.packageVersion()
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
        JsonNode itemsNode = requireArray(payload, "items", "离线包缺少 items 资产条目列表");
        JsonNode assetSnapshotsNode = requireArray(payload, "assetSnapshots", "离线包缺少 assetSnapshots 资产内容快照");

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
        validateOfflineImportTenantLineage(tenantId, sourceTenantId);

        int itemCount = requireInt(manifest, "itemCount", "离线包 itemCount 不合法");
        if (itemCount != itemsNode.size()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包 itemCount 与资产条目数量不一致");
        }
        int assetSnapshotCount = requireInt(manifest, "assetSnapshotCount", "离线包 assetSnapshotCount 不合法");
        if (assetSnapshotCount != assetSnapshotsNode.size() || assetSnapshotCount != itemsNode.size()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产内容快照数量与资产条目数量不一致");
        }
        if (packageRepository
            .findByTenantIdAndPackageCodeAndPackageVersion(tenantId, packageCode, packageVersion)
            .isPresent()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_004, "知识包版本在该租户内已存在: " + packageVersion);
        }

        Instant now = Instant.now();
        boolean platformSourceReference = isPlatformSourceReferenceImport(tenantId, sourceTenantId);
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
            itemsNode, tenantId, sourceTenantId, savedPackage.packageId(), sourcePackageId,
            actor, traceId, now, platformSourceReference);
        importedItems.forEach(itemRepository::save);

        auditPublisher.publish(AuditAction.IMPORT, "knowledge_package", savedPackage.packageId(),
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

    private List<PackageOfflineAssetSnapshot> buildOfflineAssetSnapshots(String tenantId, List<PackageItem> items) {
        return items.stream()
            .map(item -> buildOfflineAssetSnapshot(tenantId, item))
            .toList();
    }

    private PackageOfflineAssetSnapshot buildOfflineAssetSnapshot(String tenantId, PackageItem item) {
        JsonNode content = switch (item.assetType()) {
            case RULE -> buildRuleAssetContent(
                ruleRepository.findByRuleIdAndTenantId(item.assetId(), tenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "离线导出规则不存在: " + item.assetId())),
                ruleVersionRepository.findByRuleIdAndTenantIdAndVersionNo(
                    item.assetId(), tenantId, parseAssetVersionNo(item.assetVersion()))
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "离线导出规则版本不存在: " + item.assetId() + "@" + item.assetVersion()))
            );
            case EVALUATION -> buildEvaluationAssetContent(
                evaluationRepository.findByIndicatorIdAndTenantId(item.assetId(), tenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_002, "离线导出评估指标不存在: " + item.assetId()))
            );
            case KNOWLEDGE -> {
                KnowledgeIdentity identity = knowledgeIdentityRepository.findByTenantIdAndIdentityCode(tenantId, item.assetId())
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_002, "离线导出知识身份不存在: " + item.assetId()));
                KnowledgeAssetVersion version = knowledgeVersionRepository
                    .findByTenantIdAndIdentityIdAndVersionNo(tenantId, identity.id(), item.assetVersion())
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "离线导出知识版本不存在: " + item.assetId() + "@" + item.assetVersion()
                    ));
                yield buildKnowledgeAssetContent(identity, version);
            }
            case TERMINOLOGY -> {
                TerminologyAssetKey key = parseTerminologyAssetKey(item.assetId());
                TermMappingPackage terminologyPackage = terminologyPackageRepository
                    .findByTenantIdAndPackageCodeAndPackageVersionAndScopeLevelAndScopeCode(
                        tenantId, key.packageCode(), item.assetVersion(), key.scopeLevel(), key.scopeCode())
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "离线导出术语映射包不存在: " + item.assetId() + "@" + item.assetVersion()
                    ));
                yield buildTerminologyAssetContent(terminologyPackage);
            }
            default -> throw new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "离线包暂不支持完整资产内容迁移: " + item.assetType());
        };
        return new PackageOfflineAssetSnapshot(
            item.assetType(),
            item.assetId(),
            item.assetVersion(),
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
                version.withdrawnReason()
            )
        ));
    }

    private JsonNode buildTerminologyAssetContent(TermMappingPackage terminologyPackage) {
        if (terminologyPackage.id() == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线导出术语映射包缺少本地主键，不能回查条目: " + terminologyPackage.packageCode());
        }
        List<TermMappingPackageItem> packageItems = terminologyPackageItemRepository
            .findByTenantIdAndPackageId(terminologyPackage.tenantId(), terminologyPackage.id()).stream()
            .sorted(Comparator.comparing(
                TermMappingPackageItem::mappingId,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        List<PackageOfflineTermMapping> mappings = packageItems.stream()
            .map(item -> terminologyMappingRepository.findByTenantIdAndId(terminologyPackage.tenantId(), item.mappingId())
                .orElseThrow(() -> new ApiException(
                    ErrorCode.ENG_PACKAGE_002,
                    "离线导出术语映射包条目缺少正式映射: " + terminologyPackage.packageCode() + "#" + item.mappingId()
                )))
            .map(mapping -> new PackageOfflineTermMapping(
                mapping.localTermId(),
                mapping.standardTermId(),
                mapping.sourceSystem(),
                mapping.categoryName(),
                mapping.confidence(),
                mapping.riskLevelName(),
                mapping.statusName(),
                mapping.evidenceText(),
                mapping.confirmedBy(),
                instantText(mapping.confirmedAt())
            ))
            .toList();
        List<PackageOfflineTermMappingPackageItem> items = packageItems.stream()
            .map(item -> new PackageOfflineTermMappingPackageItem(item.mappingSnapshot()))
            .toList();
        return OFFLINE_EXPORT_MAPPER.valueToTree(new PackageOfflineTerminologyContent(
            new PackageOfflineTerminologyPackage(
                terminologyPackage.packageCode(),
                terminologyPackage.packageVersion(),
                terminologyPackage.displayName(),
                terminologyPackage.scopeLevel(),
                terminologyPackage.scopeCode(),
                terminologyPackage.statusName(),
                terminologyPackage.mappingCount(),
                terminologyPackage.contentHash(),
                terminologyPackage.grayScopeJson(),
                terminologyPackage.publishedBy(),
                instantText(terminologyPackage.publishedAt()),
                terminologyPackage.rollbackFromPackageId()
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
        for (JsonNode snapshotNode : assetSnapshotsNode) {
            if (snapshotNode == null || !snapshotNode.isObject()) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产内容快照必须是对象");
            }
            VersionedAssetType assetType = parseAssetType(requireText(snapshotNode, "assetType", "离线包资产快照缺少 assetType"));
            String assetId = requireText(snapshotNode, "assetId", "离线包资产快照缺少 assetId");
            String assetVersion = requireText(snapshotNode, "assetVersion", "离线包资产快照缺少 assetVersion");
            String contentSha256 = requireText(snapshotNode, "contentSha256", "离线包资产快照缺少 contentSha256");
            if (!contentSha256.matches("[a-f0-9]{64}")) {
                throw new ApiException(ErrorCode.ENG_EVID_002, "离线包资产内容摘要格式不合法");
            }
            JsonNode content = requireObject(snapshotNode, "content", "离线包资产快照缺少 content 内容");
            String actualSha256 = sha256Json(content);
            if (!contentSha256.equals(actualSha256)) {
                throw new ApiException(ErrorCode.ENG_EVID_002, "离线包资产内容摘要与实际内容不一致");
            }
            String key = offlineAssetKey(assetType, assetId, assetVersion);
            if (snapshotsByKey.putIfAbsent(key, snapshotNode) != null) {
                throw new ApiException(ErrorCode.CONFLICT, "离线包内存在重复资产内容快照: " + key);
            }
        }

        for (JsonNode itemNode : itemsNode) {
            VersionedAssetType assetType = parseAssetType(requireText(itemNode, "assetType", "离线包资产条目缺少 assetType"));
            String assetId = requireText(itemNode, "assetId", "离线包资产条目缺少 assetId");
            String assetVersion = requireText(itemNode, "assetVersion", "离线包资产条目缺少 assetVersion");
            String key = offlineAssetKey(assetType, assetId, assetVersion);
            JsonNode snapshot = snapshotsByKey.get(key);
            if (snapshot == null) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包缺少资产内容快照: " + key);
            }
            importOfflineAssetSnapshot(
                assetType,
                assetId,
                assetVersion,
                snapshot,
                tenantId,
                sourceTenantId,
                actor,
                traceId,
                now);
        }
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
            case EVALUATION -> importOfflineEvaluationSnapshot(assetId, assetVersion, content, tenantId, actor, traceId, now);
            case KNOWLEDGE -> importOfflineKnowledgeSnapshot(assetId, assetVersion, content, tenantId, actor, now);
            case TERMINOLOGY -> importOfflineTerminologySnapshot(assetId, assetVersion, content, tenantId, actor, now);
            default -> throw new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "离线包暂不支持完整资产内容迁移: " + assetType);
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
            }
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
                PackageOfflineTerminologyPackage terminologyPackage = terminologyContent.terminologyPackage();
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
                if (terminologyPackage.mappingCount() != null
                        && terminologyPackage.mappingCount() != terminologyContent.mappings().size()) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包术语映射包 mappingCount 与映射快照数量不一致");
                }
                validateOfflineTerminologyMappings(terminologyContent.mappings());
            }
            default -> throw new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "离线包暂不支持完整资产内容迁移: " + assetType);
        }
    }

    private void ensurePackageAssetPublished(String assetName, String status) {
        if (!"PUBLISHED".equalsIgnoreCase(status) && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包" + assetName + "必须为 PUBLISHED 或 ACTIVE 状态, 当前: " + status);
        }
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

    private void ensureTerminologyPackageReleased(String status) {
        if (!"PUBLISHED".equalsIgnoreCase(status) && !"GRAY".equalsIgnoreCase(status)) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002,
                "离线包术语映射包必须为 PUBLISHED 或 GRAY 状态, 当前: " + status);
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

        ruleVersionRepository.save(new RuleVersion(
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
        ruleRepository.save(new RuleDefinition(
            null,
            ruleContent.rule().ruleId(),
            tenantId,
            ruleContent.rule().ruleCode(),
            ruleContent.rule().name(),
            parseEnum(com.medkernel.engine.rule.RuleType.class, ruleContent.rule().ruleType(), "规则类型"),
            parseEnum(com.medkernel.engine.rule.RuleAuthoringMode.class, ruleContent.rule().authoringMode(), "规则编写模式"),
            parseEnum(com.medkernel.engine.rule.RuleRiskLevel.class, ruleContent.rule().riskLevel(), "规则风险级别"),
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

    private void importOfflineKnowledgeSnapshot(
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
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
            actor
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
    }

    private void importOfflineTerminologySnapshot(
            String assetId,
            String assetVersion,
            JsonNode content,
            String tenantId,
            String actor,
            Instant now) {
        PackageOfflineTerminologyContent terminologyContent =
            readOfflineContent(content, PackageOfflineTerminologyContent.class);
        validateOfflineAssetSnapshotContent(VersionedAssetType.TERMINOLOGY, assetId, assetVersion, content);
        PackageOfflineTerminologyPackage importedPackage = terminologyContent.terminologyPackage();
        TerminologyAssetKey key = parseTerminologyAssetKey(assetId);

        Optional<TermMappingPackage> existingPackage = terminologyPackageRepository
            .findByTenantIdAndPackageCodeAndPackageVersionAndScopeLevelAndScopeCode(
                tenantId, key.packageCode(), assetVersion, key.scopeLevel(), key.scopeCode());
        if (existingPackage.isPresent()) {
            ensureLocalSnapshotMatches("术语映射包", assetId, content,
                buildTerminologyAssetContent(existingPackage.get()));
            return;
        }

        TermMappingPackage savedPackage = terminologyPackageRepository.save(TermMappingPackage.imported(
            tenantId,
            importedPackage.packageCode(),
            importedPackage.packageVersion(),
            importedPackage.displayName(),
            importedPackage.scopeLevel(),
            importedPackage.scopeCode(),
            importedPackage.status(),
            importedPackage.mappingCount(),
            importedPackage.contentHash(),
            importedPackage.grayScopeJson(),
            importedPackage.publishedBy(),
            parseInstant(importedPackage.publishedAt()),
            importedPackage.rollbackFromPackageId(),
            now,
            actor
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

        for (int i = 0; i < savedMappings.size(); i++) {
            TermMapping savedMapping = savedMappings.get(i);
            String mappingSnapshot = i < terminologyContent.items().size()
                ? terminologyContent.items().get(i).mappingSnapshot()
                : termMappingSnapshot(savedMapping);
            terminologyPackageItemRepository.save(new TermMappingPackageItem(
                null,
                tenantId,
                savedPackage.id(),
                savedMapping.id(),
                mappingSnapshot,
                now,
                actor
            ));
        }
    }

    private String termMappingSnapshot(TermMapping mapping) {
        return "{\"localTermId\":" + mapping.localTermId()
            + ",\"standardTermId\":" + mapping.standardTermId()
            + ",\"status\":\"" + mapping.statusName() + "\"}";
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
            String sourceTenantId,
            String importedPackageId,
            String sourcePackageId,
            String actor,
            String traceId,
            Instant now,
            boolean platformSourceReference) {
        List<PackageItem> items = new ArrayList<>();
        Set<String> uniqueAssets = new HashSet<>();
        for (JsonNode itemNode : itemsNode) {
            if (itemNode == null || !itemNode.isObject()) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产条目必须是对象");
            }
            requireSameText(itemNode, "tenantId", sourceTenantId, "离线包资产条目租户与源租户不一致");
            requireSameText(itemNode, "packageId", sourcePackageId, "离线包资产条目 packageId 与包元信息不一致");
            VersionedAssetType assetType = parseAssetType(requireText(itemNode, "assetType", "离线包资产条目缺少 assetType"));
            String assetId = requireText(itemNode, "assetId", "离线包资产条目缺少 assetId");
            String assetVersion = requireText(itemNode, "assetVersion", "离线包资产条目缺少 assetVersion");
            String assetKey = assetType.name() + ":" + assetId;
            if (!uniqueAssets.add(assetKey)) {
                throw new ApiException(ErrorCode.CONFLICT, "离线包内存在重复资产条目: " + assetKey);
            }
            if (!platformSourceReference) {
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

    private String offlineAssetKey(VersionedAssetType assetType, String assetId, String assetVersion) {
        return assetType.name() + ":" + assetId + ":" + assetVersion;
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
        int itemCount,
        int assetSnapshotCount,
        String hashAlgorithm,
        String payloadSha256,
        String exportedAt,
        String traceId
    ) {}

    private record PackageOfflinePayload(
        PackageOfflinePackageInfo packageInfo,
        List<PackageOfflineItem> items,
        List<PackageOfflineAssetSnapshot> assetSnapshots
    ) {}

    private record PackageOfflineAssetSnapshot(
        VersionedAssetType assetType,
        String assetId,
        String assetVersion,
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
        String itemId,
        String tenantId,
        String packageId,
        VersionedAssetType assetType,
        String assetId,
        String assetVersion,
        String createdAt,
        String createdBy,
        String updatedAt,
        String updatedBy,
        String traceId
    ) {
        static PackageOfflineItem from(PackageItem item) {
            return new PackageOfflineItem(
                item.itemId(),
                item.tenantId(),
                item.packageId(),
                item.assetType(),
                item.assetId(),
                item.assetVersion(),
                instantText(item.createdAt()),
                item.createdBy(),
                instantText(item.updatedAt()),
                item.updatedBy(),
                item.traceId()
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
        String withdrawnReason
    ) {}

    private record PackageOfflineTerminologyContent(
        PackageOfflineTerminologyPackage terminologyPackage,
        List<PackageOfflineTermMapping> mappings,
        List<PackageOfflineTermMappingPackageItem> items
    ) {
        PackageOfflineTerminologyContent {
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    private record PackageOfflineTerminologyPackage(
        String packageCode,
        String packageVersion,
        String displayName,
        String scopeLevel,
        String scopeCode,
        String status,
        Integer mappingCount,
        String contentHash,
        String grayScopeJson,
        String publishedBy,
        String publishedAt,
        Long rollbackFromPackageId
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

    private record PackageOfflineTermMappingPackageItem(
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
        validateReleaseAuthorization(releaseRequest);
        ReleaseScope normalizedScope = normalizeReleaseScope(releaseRequest);

        KnowledgePackage pack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));

        assertPackageReadyForRelease(packageId);

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

        for (String targetId : request.targetIds()) {
            SyncTarget target = targetRepository.findByTargetIdAndTenantId(targetId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "同步通道目标不存在: " + targetId));

            // 小事务1：插入同步初始 RUNNING 日志
            SyncLog savedLog = transactionTemplate.execute(status -> {
                SyncLog syncLog = new SyncLog(
                    null,
                    UUID.randomUUID().toString(),
                    tenantId,
                    savedPlan.planId(),
                    targetId,
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
                evidence = syncPort.sync(tenantId, savedPlan, target);
            } catch (Exception e) {
                if (e instanceof PackageSyncNotConnectedException) {
                    log.warn("同步发布未接入真实同步适配器, targetId: {}, reason: {}", targetId, e.getMessage());
                } else {
                    log.error("同步发布失败, targetId: {}", targetId, e);
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
                        targetId,
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
                        targetId,
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
                        targetId,
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

            if (releaseRequest.strategy() == ReleaseStrategy.FULL && finalAllSuccess) {
                // 原子切换：仅失效相同 packageCode 的 ACTIVE 知识包，不污染其他病种包
                List<KnowledgePackage> activePacks = packageRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                    .filter(p -> p.status() == KnowledgePackageStatus.ACTIVE && p.packageCode().equals(pack.packageCode()))
                    .toList();
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
            auditPublisher.publish(AuditAction.PUBLISH, "knowledge_package", packageId, 
                "知识包发布并同步全量成功: " + pack.name() + " (" + pack.packageVersion() + ")");
        } else {
            auditPublisher.publish(AuditAction.PUBLISH, "knowledge_package", packageId, 
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
        return request;
    }

    private void validateReleaseAuthorization(PackageSyncRequest request) {
        if (request.strategy() == ReleaseStrategy.FULL && !hasHospitalAdminRole(request.roleCodes())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "配置包直接全量发布必须由院级管理员确认");
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
        ObjectNode scope = PACKAGE_JSON_MAPPER.createObjectNode();
        scope.put("strategy", DEFAULT_GRAY_SCOPE_STRATEGY);
        scope.put("percentage", DEFAULT_GRAY_SCOPE_PERCENTAGE);
        scope.put("scopeCode", scopeCode);
        return new ReleaseScope(ReleaseScopeType.HOSPITAL, scope.toString());
    }

    private boolean hasHospitalAdminRole(List<String> roleCodes) {
        return roleCodes == null ? false : roleCodes.stream()
            .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace('.', '_'))
            .anyMatch(role -> role.equals("HOSPITAL_ADMIN")
                || role.equals("ROLE_HOSPITAL_ADMIN")
                || role.equals("TENANT_ADMIN")
                || role.equals("ROLE_TENANT_ADMIN"));
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

    private PilotPackageTemplateInstantiateRequest requireInstantiateRequest(
            PilotPackageTemplateInstantiateRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "首发模板实例化请求不能为空");
        }
        return request;
    }

    private String firstNonBlank(String candidate, String fallback) {
        String normalizedCandidate = normalizedText(candidate);
        return normalizedCandidate == null ? normalizedText(fallback) : normalizedCandidate;
    }

    private void requirePackageIdentity(String packageCode, String packageVersion, String name) {
        if (packageCode == null || packageCode.isBlank()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "实例化配置包缺少包编码");
        }
        if (packageVersion == null || packageVersion.isBlank()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "实例化配置包缺少包版本");
        }
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "实例化配置包缺少包名称");
        }
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
    public List<SyncLogResponse> listSyncLogs(String packageId) {
        String tenantId = currentTenantId();
        packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));
        return planRepository.findByTenantIdAndPackageIdOrderByCreatedAtDesc(tenantId, packageId).stream()
            .flatMap(plan -> logRepository.findByTenantIdAndPlanId(tenantId, plan.planId()).stream())
            .map(SyncLogResponse::from)
            .toList();
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

        RollbackProjectionResult projectionResult = projectRollbackToOriginalTargets(
            tenantId, savedPlan, rollbackScope.targetIds(), actor, traceId);

        final KnowledgePackage[] savedTargetHolder = new KnowledgePackage[1];
        transactionTemplate.executeWithoutResult(status -> {
            planRepository.save(savedPlan.withStatus(projectionResult.finalStatus()));
            if (projectionResult.allSuccess()) {
                packageRepository.save(currentActive.withStatus(KnowledgePackageStatus.OFFLINE));
                savedTargetHolder[0] = packageRepository.save(targetRollback.withStatus(KnowledgePackageStatus.ACTIVE));
            }
        });

        if (!projectionResult.allSuccess()) {
            auditPublisher.publish(AuditAction.ROLLBACK, "knowledge_package", targetPackageId,
                "一键回滚包版本从 " + currentActive.packageVersion()
                    + " 回退到 " + targetRollback.packageVersion()
                    + " 失败，发布计划状态: " + projectionResult.finalStatus()
                    + "，原因: " + rollbackReason
                    + "，操作人: " + actor);
            throw new ApiException(ErrorCode.ENG_PACKAGE_005, "回滚同步发布未全部成功，包状态未变更");
        }

        // 异步发布回滚审计事实存证
        auditPublisher.publish(AuditAction.ROLLBACK, "knowledge_package", targetPackageId,
            "一键回滚包版本从 " + currentActive.packageVersion()
                + " 回退到 " + targetRollback.packageVersion()
                + "，原因: " + rollbackReason
                + "，操作人: " + actor);

        return PackageResponse.from(savedTargetHolder[0]);
    }

    /**
     * 获取当前租户下状态为 ACTIVE 的所有同步目标列表。
     *
     * @return 状态为 ACTIVE 的同步目标实体列表
     */
    @Transactional(readOnly = true)
    public List<SyncTarget> listSyncTargets() {
        String tenantId = currentTenantId();
        return targetRepository.findByTenantIdAndStatus(tenantId, SyncTargetStatus.ACTIVE);
    }

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
            List<String> targetIds = logRepository.findByTenantIdAndPlanId(tenantId, plan.planId()).stream()
                .filter(syncLog -> syncLog.status() == SyncLogStatus.SUCCESS)
                .map(SyncLog::targetId)
                .distinct()
                .toList();
            if (!targetIds.isEmpty()) {
                return new RollbackSyncScope(plan, targetIds);
            }
        }

        throw new ApiException(ErrorCode.ENG_PACKAGE_002, "当前在用包缺少成功同步目标记录，不能执行回滚");
    }

    private RollbackProjectionResult projectRollbackToOriginalTargets(
            String tenantId,
            ReleasePlan savedPlan,
            List<String> targetIds,
            String actor,
            String traceId) {
        boolean anySuccess = false;
        boolean allSuccess = true;
        boolean anyNotSynced = false;
        boolean anyFailed = false;

        for (String targetId : targetIds) {
            SyncLog savedLog = transactionTemplate.execute(status -> {
                SyncLog syncLog = new SyncLog(
                    null,
                    UUID.randomUUID().toString(),
                    tenantId,
                    savedPlan.planId(),
                    targetId,
                    SyncLogStatus.RUNNING,
                    null, null, 0, null,
                    Instant.now(), actor, Instant.now(), actor, traceId
                );
                return logRepository.save(syncLog);
            });

            String evidence = null;
            Exception syncError = null;
            Optional<SyncTarget> target = targetRepository.findByTargetIdAndTenantId(targetId, tenantId);
            if (target.isEmpty()) {
                syncError = new ApiException(ErrorCode.ENG_PACKAGE_001, "回滚同步通道目标不存在: " + targetId);
            } else {
                try {
                    evidence = syncPort.sync(tenantId, savedPlan, target.get());
                    if (evidence == null || evidence.isBlank()) {
                        syncError = new ApiException(ErrorCode.ENG_PACKAGE_005, "回滚同步未返回同步证据: " + targetId);
                    }
                } catch (Exception e) {
                    if (e instanceof PackageSyncNotConnectedException) {
                        log.warn("回滚同步发布未接入真实同步适配器, targetId: {}, reason: {}", targetId, e.getMessage());
                    } else {
                        log.error("回滚同步发布失败, targetId: {}", targetId, e);
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
                        targetId,
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
                        targetId,
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
                    targetId,
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

    private record RollbackSyncScope(ReleasePlan originalPlan, List<String> targetIds) {}

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
                if (identity.currentVersionId() != null
                        && version.id() != null
                        && !identity.currentVersionId().equals(version.id())) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "知识资产版本不是当前权威版本: " + assetId + "@" + assetVersion
                    );
                }
            }
            case TERMINOLOGY -> {
                TerminologyAssetKey key = parseTerminologyAssetKey(assetId);
                TermMappingPackage terminologyPackage = terminologyPackageRepository
                    .findByTenantIdAndPackageCodeAndPackageVersionAndScopeLevelAndScopeCode(
                        tenantId,
                        key.packageCode(),
                        assetVersion,
                        key.scopeLevel(),
                        key.scopeCode()
                    )
                    .orElseThrow(() -> new ApiException(
                        ErrorCode.PACKAGE_DEPENDENCY_MISSING,
                        "入包术语映射包不存在: " + assetId + "@" + assetVersion
                    ));
                if (!terminologyPackage.isReleasedForPackageAsset()) {
                    throw new ApiException(
                        ErrorCode.ENG_PACKAGE_002,
                        "只允许 PUBLISHED 或 GRAY 状态的术语映射包入包, 当前: " + terminologyPackage.statusName()
                    );
                }
            }
            case FOLLOWUP -> {
                throw new ApiException(
                    ErrorCode.ENG_PACKAGE_002,
                    "随访计划属于患者运行数据，不允许作为配置包资产入包；请在 D3 FOLLOW-01 建立随访模板资产后再接入包发布"
                );
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

    private String terminologyAssetId(TermMappingPackage terminologyPackage) {
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
