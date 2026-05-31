package com.medkernel.engine.pkg;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
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

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository itemRepository;
    private final ReleasePlanRepository planRepository;
    private final SyncTargetRepository targetRepository;
    private final SyncLogRepository logRepository;

    private final RuleDefinitionRepository ruleRepository;
    private final PathwayTemplateRepository pathwayRepository;
    private final EvaluationIndicatorRepository evaluationRepository;

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

        for (PackageItem target : targetItems) {
            Optional<PackageItem> matchedBase = baseItems.stream()
                .filter(b -> b.assetType() == target.assetType() && b.assetId().equals(target.assetId()))
                .findFirst();

            if (matchedBase.isEmpty()) {
                added++;
            } else if (!matchedBase.get().assetVersion().equals(target.assetVersion())) {
                updated++;
            }
            String deptId = getAssetDepartment(tenantId, target.assetType(), target.assetId());
            if (deptId != null && !affectedDepts.contains(deptId)) {
                affectedDepts.add(deptId);
            }
        }

        for (PackageItem base : baseItems) {
            boolean existsInTarget = targetItems.stream()
                .anyMatch(t -> t.assetType() == base.assetType() && t.assetId().equals(base.assetId()));
            if (!existsInTarget) {
                removed++;
            }
        }

        return new PackageDiffResponse(
            packageId,
            baseVersion,
            targetPack.packageVersion(),
            added,
            updated,
            removed,
            affectedDepts
        );
    }

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
                log.error("物理同步失败, targetId: {}", targetId, e);
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
    @Transactional
    public PackageResponse rollbackPackage(String packageId, PackageRollbackRequest request) {
        String tenantId = currentTenantId();
        String actor = currentActor();
        PackageRollbackRequest rollbackRequest = requireRollbackRequest(request);
        String targetPackageId = requireRollbackText(rollbackRequest.targetPackageId(), "回滚目标包不能为空");
        String rollbackReason = validateRollbackRequestShape(rollbackRequest);

        KnowledgePackage currentActive = packageRepository.findByPackageIdAndTenantId(packageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "当前在用包不存在: " + packageId));

        KnowledgePackage targetRollback = packageRepository.findByPackageIdAndTenantId(targetPackageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PACKAGE_001, "回滚目标包不存在: " + targetPackageId));

        validateRollbackAssets(currentActive, targetRollback, rollbackRequest);

        if (targetRollback.status() != KnowledgePackageStatus.PUBLISHED 
            && targetRollback.status() != KnowledgePackageStatus.OFFLINE) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "回滚目标包必须是曾经成功发布的包（PUBLISHED 或 OFFLINE）");
        }

        // 执行状态原子转换：隔离当前包，激活历史包
        packageRepository.save(currentActive.withStatus(KnowledgePackageStatus.OFFLINE));
        KnowledgePackage savedTarget = packageRepository.save(targetRollback.withStatus(KnowledgePackageStatus.ACTIVE));

        // 异步发布回滚审计事实存证
        auditPublisher.publish(AuditAction.ROLLBACK, "knowledge_package", targetPackageId,
            "一键回滚包版本从 " + currentActive.packageVersion()
                + " 回退到 " + targetRollback.packageVersion()
                + "，原因: " + rollbackReason
                + "，操作人: " + actor);

        return PackageResponse.from(savedTarget);
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
