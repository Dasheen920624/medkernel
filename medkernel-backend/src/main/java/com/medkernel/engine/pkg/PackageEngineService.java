package com.medkernel.engine.pkg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
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
 * <p>提供资产打包、差异比对、发布灰度校验、多物理通道投影以及快速一键回滚的完整应用层实现。
 */
@Service
public class PackageEngineService {

    private static final Logger log = LoggerFactory.getLogger(PackageEngineService.class);
    private static final ObjectMapper DIFF_EXPORT_MAPPER = new ObjectMapper();
    private static final ObjectMapper OFFLINE_EXPORT_MAPPER = new ObjectMapper();
    private static final String OFFLINE_PACKAGE_FORMAT = "MEDKERNEL_PACKAGE_OFFLINE_V1";

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository itemRepository;
    private final ReleasePlanRepository planRepository;
    private final SyncTargetRepository targetRepository;
    private final SyncLogRepository logRepository;

    private final RuleDefinitionRepository ruleRepository;
    private final PathwayTemplateRepository pathwayRepository;
    private final EvaluationIndicatorRepository evaluationRepository;
    private final RuleVersionRepository ruleVersionRepository;

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
        validateAssetStatus(tenantId, request.assetType(), request.assetId());

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

        appendDiffExportLine(ndjson, new PackageDiffSummaryExportLine(
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
            appendDiffExportLine(ndjson, new PackageDiffDepartmentExportLine(
                "PACKAGE_DIFF_AFFECTED_DEPARTMENT",
                diff.packageId(),
                departmentId,
                traceId
            ));
        }
        for (PackageDiffChange change : diff.changes()) {
            appendDiffExportLine(ndjson, new PackageDiffChangeExportLine(
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

    private void addAffectedDepartment(List<String> affectedDepartments, String departmentId) {
        if (departmentId != null && !affectedDepartments.contains(departmentId)) {
            affectedDepartments.add(departmentId);
        }
    }

    private void appendDiffExportLine(StringBuilder builder, Object line) {
        try {
            builder.append(DIFF_EXPORT_MAPPER.writeValueAsString(line)).append('\n');
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "配置包差异影响证据导出失败");
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
        PackageItemAssetType assetType,
        String assetId,
        String baseVersion,
        String targetVersion,
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
        importOfflineAssetSnapshots(assetSnapshotsNode, itemsNode, tenantId, actor, traceId, now);
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
            itemsNode, tenantId, sourceTenantId, savedPackage.packageId(), sourcePackageId, actor, traceId, now);
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

    private void importOfflineAssetSnapshots(
            JsonNode assetSnapshotsNode,
            JsonNode itemsNode,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        Map<String, JsonNode> snapshotsByKey = new HashMap<>();
        for (JsonNode snapshotNode : assetSnapshotsNode) {
            if (snapshotNode == null || !snapshotNode.isObject()) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产内容快照必须是对象");
            }
            PackageItemAssetType assetType = parseAssetType(requireText(snapshotNode, "assetType", "离线包资产快照缺少 assetType"));
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
            PackageItemAssetType assetType = parseAssetType(requireText(itemNode, "assetType", "离线包资产条目缺少 assetType"));
            String assetId = requireText(itemNode, "assetId", "离线包资产条目缺少 assetId");
            String assetVersion = requireText(itemNode, "assetVersion", "离线包资产条目缺少 assetVersion");
            String key = offlineAssetKey(assetType, assetId, assetVersion);
            JsonNode snapshot = snapshotsByKey.get(key);
            if (snapshot == null) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包缺少资产内容快照: " + key);
            }
            importOfflineAssetSnapshot(assetType, assetId, assetVersion, snapshot, tenantId, actor, traceId, now);
        }
    }

    private void importOfflineAssetSnapshot(
            PackageItemAssetType assetType,
            String assetId,
            String assetVersion,
            JsonNode snapshot,
            String tenantId,
            String actor,
            String traceId,
            Instant now) {
        JsonNode content = requireObject(snapshot, "content", "离线包资产快照缺少 content 内容");
        switch (assetType) {
            case RULE -> importOfflineRuleSnapshot(assetId, assetVersion, content, tenantId, actor, traceId, now);
            case EVALUATION -> importOfflineEvaluationSnapshot(assetId, assetVersion, content, tenantId, actor, traceId, now);
            default -> throw new ApiException(
                ErrorCode.ENG_PACKAGE_002,
                "离线包暂不支持完整资产内容迁移: " + assetType);
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
            Instant now) {
        List<PackageItem> items = new ArrayList<>();
        Set<String> uniqueAssets = new HashSet<>();
        for (JsonNode itemNode : itemsNode) {
            if (itemNode == null || !itemNode.isObject()) {
                throw new ApiException(ErrorCode.ENG_PACKAGE_002, "离线包资产条目必须是对象");
            }
            requireSameText(itemNode, "tenantId", sourceTenantId, "离线包资产条目租户与源租户不一致");
            requireSameText(itemNode, "packageId", sourcePackageId, "离线包资产条目 packageId 与包元信息不一致");
            PackageItemAssetType assetType = parseAssetType(requireText(itemNode, "assetType", "离线包资产条目缺少 assetType"));
            String assetId = requireText(itemNode, "assetId", "离线包资产条目缺少 assetId");
            String assetVersion = requireText(itemNode, "assetVersion", "离线包资产条目缺少 assetVersion");
            String assetKey = assetType.name() + ":" + assetId;
            if (!uniqueAssets.add(assetKey)) {
                throw new ApiException(ErrorCode.CONFLICT, "离线包内存在重复资产条目: " + assetKey);
            }
            validateAssetStatus(tenantId, assetType, assetId);

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

    private PackageItemAssetType parseAssetType(String assetType) {
        try {
            return PackageItemAssetType.valueOf(assetType);
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

    private String offlineAssetKey(PackageItemAssetType assetType, String assetId, String assetVersion) {
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
        PackageItemAssetType assetType,
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
        PackageItemAssetType assetType,
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

    /**
     * 触发包同步与发布执行（支持灰度、全量、回滚等多通道投影）。
     */
    public PackageSyncResponse syncPackage(String packageId, PackageSyncRequest request) {
        String tenantId = currentTenantId();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();

        KnowledgePackage pack = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "知识包不存在: " + packageId));

        // 灰度与全量发布策略的基本参数及签名规则校验
        if (request.strategy() == ReleaseStrategy.GRAYSCALE 
            && (request.scopeType() == ReleaseScopeType.ALL || request.scopeValue() == null || request.scopeValue().isBlank())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_003, "灰度发布时必须指定有效的作用域范围和具体过滤值");
        }

        // 创建发布计划（独立小事务中写库）
        ReleasePlan plan = new ReleasePlan(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            packageId,
            request.targetOrgUnitId(),
            request.strategy(),
            request.scopeType(),
            request.scopeValue(),
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
                // 事务外部安全执行：物理投影同步（包含长 IO）
                evidence = syncPort.sync(tenantId, savedPlan, target);
            } catch (Exception e) {
                if (e instanceof PackageSyncNotConnectedException) {
                    log.warn("物理同步未接入真实同步适配器, targetId: {}, reason: {}", targetId, e.getMessage());
                } else {
                    log.error("物理同步失败, targetId: {}", targetId, e);
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

            if (request.strategy() == ReleaseStrategy.FULL && finalAllSuccess) {
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
        if (request.strategy() == ReleaseStrategy.FULL && allSuccess) {
            auditPublisher.publish(AuditAction.PUBLISH, "knowledge_package", packageId, 
                "知识包发布并同步全量成功: " + pack.name() + " (" + pack.packageVersion() + ")");
        } else {
            auditPublisher.publish(AuditAction.PUBLISH, "knowledge_package", packageId, 
                "知识包发布计划执行完成, 策略为: " + request.strategy() + ", 状态为: " + finalStatus);
        }

        return new PackageSyncResponse(savedPlan.planId(), packageId, finalStatus, logs);
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
            throw new ApiException(ErrorCode.ENG_PACKAGE_005, "回滚反向投影未全部成功，包状态未变更");
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
                        log.warn("回滚反向投影未接入真实同步适配器, targetId: {}, reason: {}", targetId, e.getMessage());
                    } else {
                        log.error("回滚反向投影失败, targetId: {}", targetId, e);
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

    private void validateAssetStatus(String tenantId, PackageItemAssetType type, String assetId) {
        switch (type) {
            case RULE -> {
                RuleDefinition rule = ruleRepository.findByRuleIdAndTenantId(assetId, tenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "入包规则不存在: " + assetId));
                // 审核通过的规则方可入包
                String status = rule.status() == null ? "" : rule.status().name();
                if (!"PUBLISHED".equalsIgnoreCase(status) && !"ACTIVE".equalsIgnoreCase(status)) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只允许 PUBLISHED 或 ACTIVE 状态的规则入包, 当前: " + status);
                }
            }
            case PATHWAY -> {
                PathwayTemplate template = pathwayRepository.findByTemplateIdAndTenantId(assetId, tenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_002, "入包路径不存在: " + assetId));
                String status = template.status() == null ? "" : template.status().name();
                if (!"PUBLISHED".equalsIgnoreCase(status) && !"ACTIVE".equalsIgnoreCase(status)) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只允许 PUBLISHED 或 ACTIVE 状态的路径入包, 当前: " + status);
                }
            }
            case EVALUATION -> {
                EvaluationIndicator indicator = evaluationRepository.findByIndicatorIdAndTenantId(assetId, tenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_002, "入包评估指标不存在: " + assetId));
                String status = indicator.status() == null ? "" : indicator.status().name();
                if (!"PUBLISHED".equalsIgnoreCase(status) && !"ACTIVE".equalsIgnoreCase(status)) {
                    throw new ApiException(ErrorCode.ENG_PACKAGE_002, "只允许 PUBLISHED 或 ACTIVE 状态的评估指标入包, 当前: " + status);
                }
            }
            default -> {
                // TERMINOLOGY、KNOWLEDGE、FOLLOWUP 等宽限处理
            }
        }
    }

    private String getAssetDepartment(String tenantId, PackageItemAssetType type, String assetId) {
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
