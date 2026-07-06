package com.medkernel.engine.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceRepository;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotRepository;
import com.medkernel.engine.evaluation.runtime.RuntimeReleaseEvaluationSelector;
import com.medkernel.engine.org.OrgAssignmentValidator;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.security.AuthenticatedPermissionGuard;
import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.RolloutPolicy;
import com.medkernel.engine.versioning.VersionPublishEvidence;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleaseScopeType;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评估质控应用服务（GA-ENG-API-08 指标配置 + 运行事实 + 问题整改闭环）。
 *
 * <p>聚合评估指标、运行、结果、质量问题、整改任务、复核记录与幂等键七类数据，承担：
 * <ul>
 *   <li>指标草稿创建、提交审核、发布、激活与旧版下线；</li>
 *   <li>接收人工抽检、上游结果或批量导入的评估运行事实；</li>
 *   <li>记录评估结果、质量问题和 P0/P1 等高风险问题的整改任务；</li>
 *   <li>处理整改提交、复核关闭、退回和豁免，并支持 {@code Idempotency-Key} 幂等重放；</li>
 *   <li>按运行 ID 装配可解释诊断响应。</li>
 * </ul>
 * 所有读写均按当前租户隔离，写动作发布审计事件并记录状态迁移。
 */
@Service
public class EvaluationEngineService {

    private static final String INDICATOR_ENTITY = "evaluation_indicator";
    private static final String RUN_ENTITY = "evaluation_run";
    private static final String FINDING_ENTITY = "quality_finding";
    private static final String TASK_ENTITY = "rectification_task";

    private final EvaluationIndicatorRepository indicators;
    private final EvaluationRunRepository runs;
    private final EvaluationResultRepository results;
    private final QualityFindingRepository findings;
    private final RectificationTaskRepository tasks;
    private final RectificationReviewRepository reviews;
    private final EvaluationIdempotencyKeyRepository idempotencyKeys;
    private final AuditRecorder auditRecorder;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final CanonicalResourceRepository canonicalResources;
    private final ContextSnapshotRepository snapshots;
    private final RuleDslEvaluator ruleEvaluator;
    private final ObjectMapper json;
    private final EvaluationVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;
    private final OrgAssignmentValidator assignments;
    private final RuntimeReleaseEvaluationSelector runtimeEvaluations;

    /**
     * 注入评估质控闭环所需仓库、审计发布器、状态记录器与诊断装配器。
     */
    public EvaluationEngineService(
            EvaluationIndicatorRepository indicators,
            EvaluationRunRepository runs,
            EvaluationResultRepository results,
            QualityFindingRepository findings,
            RectificationTaskRepository tasks,
            RectificationReviewRepository reviews,
            EvaluationIdempotencyKeyRepository idempotencyKeys,
            AuditRecorder auditRecorder,
            StateTransitionRecorder transitions,
            DiagnoseResponseAssembler diagnoseAssembler,
            CanonicalResourceRepository canonicalResources,
            ContextSnapshotRepository snapshots,
            RuleDslEvaluator ruleEvaluator,
            ObjectMapper json,
            EvaluationVersionedAssetAdapter versionedAssets,
            AssetVersionRepository assetVersions,
            ReleasePort releasePort,
            OrgAssignmentValidator assignments,
            RuntimeReleaseEvaluationSelector runtimeEvaluations) {
        this.indicators = indicators;
        this.runs = runs;
        this.results = results;
        this.findings = findings;
        this.tasks = tasks;
        this.reviews = reviews;
        this.idempotencyKeys = idempotencyKeys;
        this.auditRecorder = auditRecorder;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.canonicalResources = canonicalResources;
        this.snapshots = snapshots;
        this.ruleEvaluator = ruleEvaluator;
        this.json = json;
        this.versionedAssets = versionedAssets;
        this.assetVersions = assetVersions;
        this.releasePort = releasePort;
        this.assignments = assignments;
        this.runtimeEvaluations = runtimeEvaluations;
    }

    /**
     * 创建评估指标草稿版本。
     *
     * <p>前置：请求必须包含指标编码、名称、对象类型、分母、分子、时间窗、组织范围、
     * 责任科室和来源引用；失败抛出 {@code ENG-EVAL-001}。
     */
    @Transactional
    public EvaluationIndicator createIndicator(EvaluationIndicatorCreateRequest request) {
        validateIndicator(request);
        assignments.requireActiveDepartment(request.responsibleDepartmentId());
        String tenantId = tenantId();
        int versionNo = indicators.findTopByTenantIdAndIndicatorCodeOrderByVersionNoDesc(
                tenantId, request.indicatorCode()
            )
            .map(EvaluationIndicator::versionNo)
            .orElse(0) + 1;
        Instant now = Instant.now();
        String indicatorId = "ei-" + UUID.randomUUID();
        EvaluationIndicator indicator;
        try {
            indicator = indicators.save(new EvaluationIndicator(
                null, indicatorId, tenantId, request.indicatorCode(), versionNo, request.name(),
                request.subjectType(), request.denominatorDefinition(), request.numeratorDefinition(),
                request.exclusionDefinition(), request.scoringDefinition(), request.timeWindow(),
                request.organizationScope(), request.responsibleDepartmentId(), request.sourceRef(),
                EvaluationIndicatorStatus.DRAFT, null, null, null,
                now, actor(), now, actor(), traceId()));
            versionedAssets.registerDraft(new AssetVersionRegisterCommand(
                indicator.tenantId(),
                VersionedAssetType.EVALUATION,
                indicator.indicatorCode(),
                versionOrganizationScope(indicator),
                evaluationApplicableScope(indicator),
                indicatorContent(indicator),
                null,
                indicator.sourceRef(),
                actor(),
                traceId()
            ));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "评估指标版本并发创建冲突，请刷新后重试: "
                    + request.indicatorCode() + "@" + versionNo,
                exception
            );
        }
        transitions.record(INDICATOR_ENTITY, indicatorId, null, EvaluationIndicatorStatus.DRAFT.name(),
            "创建评估指标草稿", null);
        auditRecorder.record(AuditAction.CREATE, INDICATOR_ENTITY, indicatorId,
            "创建评估指标 " + request.indicatorCode());
        return indicator;
    }

    /**
     * 按可选状态、对象类型和指标编码过滤分页查询指标版本。
     *
     * <p>过滤条件为 {@code null} 时不进入 SQL；分页总数与行集分别由仓库 count/page 查询提供。
     */
    @Transactional(readOnly = true)
    public PageResponse<EvaluationIndicator> listIndicators(
            EvaluationIndicatorFilter filter, PageRequest pageRequest) {
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        EvaluationIndicatorFilter f = filter == null
            ? new EvaluationIndicatorFilter(null, null, null)
            : filter;
        String status = f.status() == null ? null : f.status().name();
        String subjectType = f.subjectType() == null ? null : f.subjectType().name();
        long total = indicators.countByFilter(tenantId(), status, subjectType, f.indicatorCode());
        List<EvaluationIndicator> rows = indicators.pageByFilter(
            tenantId(), status, subjectType, f.indicatorCode(), req.offset(), req.safeSize());
        return PageResponse.of(rows, req, total);
    }

    /**
     * 查看指定评估指标版本。
     *
     * <p>失败：指标不存在抛出 {@code ENG-EVAL-002}。
     */
    @Transactional(readOnly = true)
    public EvaluationIndicator indicatorDetail(String indicatorId) {
        return findIndicator(indicatorId);
    }

    /**
     * 将指标从 {@code DRAFT} 推进到 {@code PENDING_REVIEW}。
     *
     * <p>状态不匹配时抛出 {@code ENG-EVAL-003}；成功后记录状态迁移和审核审计事件。
     */
    @Transactional
    public EvaluationIndicator submitIndicator(String indicatorId) {
        EvaluationIndicator indicator = findIndicator(indicatorId);
        requireStatus(indicator, EvaluationIndicatorStatus.DRAFT);
        releasePort.submitForReview(releaseCommand(
            indicator,
            requireAssetVersion(indicator),
            "提交评估指标审核"
        ));
        EvaluationIndicator saved = saveIndicatorStatus(
            indicator, EvaluationIndicatorStatus.PENDING_REVIEW, null, null);
        transitions.record(INDICATOR_ENTITY, indicatorId, indicator.status().name(), saved.status().name(),
            "提交评估指标审核", null);
        auditRecorder.record(AuditAction.REVIEW, INDICATOR_ENTITY, indicatorId,
            "提交评估指标审核 " + indicator.indicatorCode());
        return saved;
    }

    /**
     * 将待审核指标发布为 {@code PUBLISHED}。
     *
     * <p>仅 {@code PENDING_REVIEW} 可发布；发布时写入发布时间和发布人。
     */
    @Transactional
    public EvaluationIndicator publishIndicator(
            String indicatorId,
            EvaluationIndicatorReleaseRequest request) {
        EvaluationIndicator indicator = findIndicator(indicatorId);
        requireStatus(indicator, EvaluationIndicatorStatus.PENDING_REVIEW);
        releasePort.approveReview(releaseCommand(
            indicator,
            requireAssetVersion(indicator),
            request
        ));
        Instant now = Instant.now();
        EvaluationIndicator saved = saveIndicatorStatus(
            indicator, EvaluationIndicatorStatus.PUBLISHED, now, null);
        transitions.record(INDICATOR_ENTITY, indicatorId, indicator.status().name(), saved.status().name(),
            "发布评估指标", null);
        auditRecorder.record(AuditAction.PUBLISH, INDICATOR_ENTITY, indicatorId,
            "发布评估指标 " + indicator.indicatorCode());
        return saved;
    }

    /**
     * 将已发布指标进入默认 10% 床位灰度，指标口径仍保持不可变。
     */
    @Transactional
    public EvaluationIndicator grayIndicator(
            String indicatorId,
            EvaluationIndicatorReleaseRequest request) {
        EvaluationIndicator indicator = findIndicator(indicatorId);
        requireStatus(indicator, EvaluationIndicatorStatus.PUBLISHED);
        releasePort.releaseGray(releaseCommand(
            indicator,
            requireAssetVersion(indicator),
            request,
            RolloutPolicy.canaryBedPercent(10)
        ));
        EvaluationIndicator gray = saveIndicatorStatus(
            indicator, EvaluationIndicatorStatus.GRAY, indicator.publishedAt(), null);
        transitions.record(INDICATOR_ENTITY, indicatorId, indicator.status().name(), gray.status().name(),
            "评估指标灰度发布", null);
        auditRecorder.record(AuditAction.PUBLISH, INDICATOR_ENTITY, indicatorId,
            "灰度发布评估指标 " + indicator.indicatorCode());
        return gray;
    }

    /**
     * 激活已发布指标，并将同租户同编码旧 {@code ACTIVE} 版本下线。
     *
     * <p>用于保证新评估运行只绑定当前生效指标版本，历史结果仍保留原版本快照。
     */
    @Transactional
    public EvaluationIndicator activateIndicator(
            String indicatorId,
            EvaluationIndicatorReleaseRequest request) {
        EvaluationIndicator indicator = findIndicator(indicatorId);
        if (indicator.status() != EvaluationIndicatorStatus.PUBLISHED
                && indicator.status() != EvaluationIndicatorStatus.GRAY) {
            throw new ApiException(ErrorCode.ENG_EVAL_003);
        }
        if (!AuthenticatedPermissionGuard.has(PermissionCode.EVALUATION_PUBLISH)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "评估指标全量激活需要 evaluation.publish 权限");
        }
        releasePort.publish(releaseCommand(
            indicator,
            requireAssetVersion(indicator),
            request
        ));
        Instant now = Instant.now();
        for (EvaluationIndicator old : indicators.findByTenantIdAndIndicatorCodeAndStatus(
                tenantId(), indicator.indicatorCode(), EvaluationIndicatorStatus.ACTIVE)) {
            EvaluationIndicator offline = saveIndicatorStatus(old, EvaluationIndicatorStatus.OFFLINE, null, null);
            transitions.record(INDICATOR_ENTITY, old.indicatorId(), old.status().name(), offline.status().name(),
                "新版指标激活后下线旧版", null);
        }
        EvaluationIndicator active = saveIndicatorStatus(
            indicator, EvaluationIndicatorStatus.ACTIVE, indicator.publishedAt(), now);
        transitions.record(INDICATOR_ENTITY, indicatorId, indicator.status().name(), active.status().name(),
            "激活评估指标", null);
        auditRecorder.record(AuditAction.UPDATE, INDICATOR_ENTITY, indicatorId,
            "激活评估指标 " + indicator.indicatorCode());
        return active;
    }

    /**
     * 针对指定上下文快照执行病例质控扫描，依据 ACTIVE 指标的分子、分母及排除定义执行评估逻辑，
     * 自动生成运行事实、达标/缺陷结果、质量问题和必要的科室整改任务，并调用 run 方法持久化。
     */
    @Transactional
    public EvaluationRunResponse evaluateSnapshot(EvaluationEvaluateSnapshotRequest request) {
        if (request == null || !hasText(request.contextSnapshotId()) || !hasText(request.scenarioCode())) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "上下文快照ID与就诊场景不能为空");
        }
        
        String tenantId = tenantId();
        String actor = actor();
        String traceId = traceId();
        Instant now = Instant.now();

        // 1. 获取并校验 ContextSnapshot 实体
        ContextSnapshot snapshot = snapshots.findBySnapshotIdAndTenantId(request.contextSnapshotId(), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_001, "就诊上下文快照不存在"));

        // 2. 抓取并组装 canonical 临床资源为 ObjectNode contextJson
        List<CanonicalResource> resourceList = canonicalResources.findBySnapshotIdOrderBySeqNoAsc(request.contextSnapshotId());
        ObjectNode contextJson = json.createObjectNode();
        
        ArrayNode allergyIntolerances = json.createArrayNode();
        ArrayNode encounters = json.createArrayNode();
        ArrayNode conditions = json.createArrayNode();
        ArrayNode nursingAssessments = json.createArrayNode();
        ArrayNode observations = json.createArrayNode();
        ArrayNode diagnosticReports = json.createArrayNode();
        ArrayNode medications = json.createArrayNode();
        ArrayNode procedures = json.createArrayNode();
        ArrayNode documents = json.createArrayNode();
        ArrayNode carePlans = json.createArrayNode();
        ArrayNode followUps = json.createArrayNode();
        ArrayNode claims = json.createArrayNode();

        for (CanonicalResource res : resourceList) {
            JsonNode dataNode = readResourcePayload(res);
            switch (res.resourceType()) {
                case PATIENT -> contextJson.set("patient", dataNode);
                case ALLERGY_INTOLERANCE -> allergyIntolerances.add(dataNode);
                case ENCOUNTER -> encounters.add(dataNode);
                case CONDITION -> conditions.add(dataNode);
                case NURSING_ASSESSMENT -> nursingAssessments.add(dataNode);
                case OBSERVATION -> observations.add(dataNode);
                case DIAGNOSTIC_REPORT -> diagnosticReports.add(dataNode);
                case MEDICATION -> medications.add(dataNode);
                case PROCEDURE -> procedures.add(dataNode);
                case DOCUMENT -> documents.add(dataNode);
                case CARE_PLAN -> carePlans.add(dataNode);
                case FOLLOW_UP -> followUps.add(dataNode);
                case CLAIM -> claims.add(dataNode);
            }
        }
        contextJson.set("allergyIntolerances", allergyIntolerances);
        contextJson.set("encounters", encounters);
        contextJson.set("conditions", conditions);
        contextJson.set("nursingAssessments", nursingAssessments);
        contextJson.set("observations", observations);
        contextJson.set("diagnosticReports", diagnosticReports);
        contextJson.set("medications", medications);
        contextJson.set("procedures", procedures);
        contextJson.set("documents", documents);
        contextJson.set("carePlans", carePlans);
        contextJson.set("followUps", followUps);
        contextJson.set("claims", claims);

        // 3. 从当前机构生效版本加载本次可执行的指标库
        List<EvaluationIndicator> activeIndicators = new ArrayList<>(
            runtimeEvaluations.select(tenantId, snapshot.runtimeReleaseId()));
        activeIndicators.sort(Comparator
            .comparing(EvaluationIndicator::indicatorCode)
            .thenComparingInt(EvaluationIndicator::versionNo)
            .thenComparing(EvaluationIndicator::indicatorId));
        if (activeIndicators.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_EVAL_004, "当前机构生效版本未包含生效质控评估指标，无法执行扫描");
        }

        String inputDigest = automaticEvaluationInputDigest(request, snapshot, resourceList, activeIndicators);
        String runCode = automaticEvaluationRunCode(inputDigest);
        Optional<EvaluationRun> existingRun = runs.findByRunCodeAndTenantId(runCode, tenantId);
        if (existingRun.isPresent()) {
            return replayRunResponse(existingRun.get());
        }
        List<EvaluationResultRequest> resultRequests = new ArrayList<>();

        for (EvaluationIndicator indicator : activeIndicators) {
            List<RuleDslEvaluation> ruleEvidence = new ArrayList<>();
            // A. 入组评估
            if (indicator.denominatorDefinition() == null || indicator.denominatorDefinition().isBlank()) {
                continue;
            }

            boolean inDenominator = false;
            RuleDslEvaluation denominatorEvaluation = evaluateIndicatorRule(
                indicator, indicator.denominatorDefinition(), contextJson, "分母入组规则校验");
            ruleEvidence.add(denominatorEvaluation);
            inDenominator = denominatorEvaluation.hit();

            if (!inDenominator) {
                continue;
            }

            // B. 排除条件评估
            boolean excluded = false;
            if (indicator.exclusionDefinition() != null && !indicator.exclusionDefinition().isBlank()) {
                RuleDslEvaluation exclusionEvaluation = evaluateIndicatorRule(
                    indicator, indicator.exclusionDefinition(), contextJson, "排除规则校验");
                ruleEvidence.add(exclusionEvaluation);
                excluded = exclusionEvaluation.hit();
            }

            // C. 分子审计条件评估
            boolean hitNumerator = false;
            if (!excluded && indicator.numeratorDefinition() != null && !indicator.numeratorDefinition().isBlank()) {
                RuleDslEvaluation numeratorEvaluation = evaluateIndicatorRule(
                    indicator, indicator.numeratorDefinition(), contextJson, "分子达标规则校验");
                ruleEvidence.add(numeratorEvaluation);
                hitNumerator = numeratorEvaluation.hit();
            }

            // D. 组装评估结论、生成缺陷与整改
            BigDecimal score;
            EvaluationResultLevel level;
            boolean hitFlag;
            String evidenceSummary;
            List<QualityFindingRequest> resultFindings = new ArrayList<>();

            if (excluded) {
                score = BigDecimal.valueOf(100);
                level = EvaluationResultLevel.PASS;
                hitFlag = true;
                evidenceSummary = evidenceSummary(
                    "病例已入组，但已由排除条件自动排除，审计判定达标。", ruleEvidence);
            } else if (hitNumerator) {
                score = BigDecimal.valueOf(100);
                level = EvaluationResultLevel.PASS;
                hitFlag = true;
                evidenceSummary = evidenceSummary(
                    "病例入组质量达标，已符合质量控制分子规则定义。", ruleEvidence);
            } else {
                score = BigDecimal.valueOf(0);
                hitFlag = false;
                
                QualityFindingSeverity severity = QualityFindingSeverity.P1;
                String scoreDef = indicator.scoringDefinition() == null ? "" : indicator.scoringDefinition();
                if (scoreDef.contains("P0") || scoreDef.contains("CRITICAL") || scoreDef.contains("极危")) {
                    severity = QualityFindingSeverity.P0;
                    level = EvaluationResultLevel.CRITICAL;
                } else if (scoreDef.contains("P2") || scoreDef.contains("中危")) {
                    severity = QualityFindingSeverity.P2;
                    level = EvaluationResultLevel.NON_COMPLIANT;
                } else if (scoreDef.contains("P3") || scoreDef.contains("低危")) {
                    severity = QualityFindingSeverity.P3;
                    level = EvaluationResultLevel.NON_COMPLIANT;
                } else {
                    level = EvaluationResultLevel.NON_COMPLIANT;
                }

                evidenceSummary = evidenceSummary(
                    "病例质量缺陷：未满足质量分子控制标准，自动生成整改派单。", ruleEvidence);

                String findingCode = indicator.indicatorCode() + "_FND";
                String findingTitle = "指标不达标：" + indicator.name();
                String findingDesc = "系统病例扫描不达标：未满足质量审计的分子指标达标标准。";
                
                resultFindings.add(new QualityFindingRequest(
                    findingCode,
                    findingTitle,
                    findingDesc,
                    severity,
                    evidenceSummary("系统自动评估扫描质量证据支撑。", ruleEvidence),
                    indicator.responsibleDepartmentId(),
                    now.plusSeconds(86400 * 7),
                    null
                ));
            }

            resultRequests.add(new EvaluationResultRequest(
                indicator.indicatorId(),
                indicator.subjectType() == null ? EvaluationSubjectType.PATIENT : indicator.subjectType(),
                indicator.subjectType() == EvaluationSubjectType.MEDICAL_RECORD ? snapshot.encounterId() : snapshot.patientId(),
                score,
                level,
                hitFlag,
                evidenceSummary,
                indicator.sourceRef(),
                indicator.responsibleDepartmentId(),
                resultFindings
            ));
        }

        if (resultRequests.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_EVAL_004, "当前就诊上下文快照未匹配进入任何指标的分母入组规则，无须生成结果");
        }

        EvaluationRunRequest runRequest = new EvaluationRunRequest(
            runCode,
            EvaluationRunType.UPSTREAM_RESULT,
            null,
            request.contextSnapshotId(),
            snapshot.patientId(),
            snapshot.encounterId(),
            request.scenarioCode(),
            null,
            inputDigest,
            now,
            resultRequests
        );

        return this.run(runRequest);
    }

    /**
     * 接收一次评估运行事实，持久化运行、结果、问题与必要整改任务。
     *
     * <p>前置：运行必须具备可追溯上下文或人工抽检来源；每条结果必须绑定当前租户的 {@code ACTIVE} 指标；
     * P0/P1 问题必须带责任科室和整改期限，否则抛出 {@code ENG-EVAL-006}。
     */
    @Transactional
    public EvaluationRunResponse run(EvaluationRunRequest request) {
        validateRun(request);
        String tenantId = tenantId();
        String runtimeReleaseId = resolveRuntimeReleaseId(request, tenantId);
        Map<String, EvaluationIndicator> activeIndicators =
            resolveActiveIndicatorsForRun(request, tenantId, runtimeReleaseId);

        Instant now = Instant.now();
        String actor = actor();
        String traceId = traceId();
        String runId = "er-" + UUID.randomUUID();
        EvaluationRun savedRun = runs.save(new EvaluationRun(
            null, runId, tenantId, request.runCode(), request.runType(), request.sourceEventId(),
            request.contextSnapshotId(), request.patientId(), request.encounterId(), request.scenarioCode(),
            runtimeReleaseId, request.inputDigest(), EvaluationRunStatus.RECORDED, null,
            request.occurredAt() == null ? now : request.occurredAt(),
            now, actor, now, actor, traceId));

        int findingCount = 0;
        int taskCount = 0;
        for (EvaluationResultRequest resultRequest : request.results()) {
            EvaluationIndicator indicator = activeIndicators.get(resultRequest.indicatorId());
            String resultId = "ers-" + UUID.randomUUID();
            results.save(new EvaluationResult(
                null, resultId, tenantId, runId, indicator.indicatorId(), indicator.indicatorCode(),
                indicator.versionNo(), resultRequest.subjectType(), resultRequest.subjectRefId(),
                resultRequest.scoreValue(), resultRequest.resultLevel(), resultRequest.hitFlag(),
                resultRequest.evidenceSummary(), resultRequest.sourceRef(), resultRequest.responsibleDepartmentId(),
                now, actor, now, actor, traceId));
            for (QualityFindingRequest findingRequest : safeFindings(resultRequest.findings())) {
                boolean assigned = shouldAssign(findingRequest);
                String findingId = "qf-" + UUID.randomUUID();
                QualityFinding finding = findings.save(new QualityFinding(
                    null, findingId, tenantId, runId, resultId, indicator.indicatorId(),
                    findingRequest.findingCode(), findingRequest.title(), findingRequest.description(),
                    findingRequest.severity(), assigned ? QualityFindingStatus.ASSIGNED : QualityFindingStatus.NEW,
                    findingRequest.evidenceSummary(), findingRequest.responsibleDepartmentId(),
                    findingRequest.dueAt(), now, actor, now, actor, traceId));
                findingCount++;
                if (assigned) {
                    String taskId = "rct-" + UUID.randomUUID();
                    tasks.save(new RectificationTask(
                        null, taskId, tenantId, findingId, findingRequest.responsibleDepartmentId(),
                        findingRequest.assigneeUserId(), RectificationTaskStatus.ASSIGNED, findingRequest.dueAt(),
                        null, null, null, null, null, now, actor, now, actor, traceId));
                    taskCount++;
                    transitions.record(TASK_ENTITY, taskId, null, RectificationTaskStatus.ASSIGNED.name(),
                        "创建质量问题整改任务", null);
                }
                transitions.record(FINDING_ENTITY, finding.findingId(), null, finding.status().name(),
                    "记录质量问题", null);
            }
        }
        transitions.record(RUN_ENTITY, runId, null, savedRun.status().name(), "接收评估运行", null);
        auditRecorder.record(AuditAction.EXECUTE, RUN_ENTITY, runId, "接收评估运行 " + request.runCode());
        return new EvaluationRunResponse(
            runId, savedRun.status(), request.results().size(), findingCount, taskCount, traceId);
    }

    /**
     * 按指标编码、结果等级和责任科室分页查询评估结果。
     */
    @Transactional(readOnly = true)
    public PageResponse<EvaluationResult> listResults(EvaluationResultFilter filter, PageRequest pageRequest) {
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        EvaluationResultFilter f = filter == null ? new EvaluationResultFilter(null, null, null) : filter;
        String level = f.resultLevel() == null ? null : f.resultLevel().name();
        long total = results.countByFilter(tenantId(), f.indicatorCode(), level, f.responsibleDepartmentId());
        List<EvaluationResult> rows = results.pageByFilter(
            tenantId(), f.indicatorCode(), level, f.responsibleDepartmentId(), req.offset(), req.safeSize());
        return PageResponse.of(rows, req, total);
    }

    /**
     * 按严重度、状态和责任科室分页查询质量问题。
     */
    @Transactional(readOnly = true)
    public PageResponse<QualityFinding> listFindings(QualityFindingFilter filter, PageRequest pageRequest) {
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        QualityFindingFilter f = filter == null ? new QualityFindingFilter(null, null, null) : filter;
        String severity = f.severity() == null ? null : f.severity().name();
        String status = f.status() == null ? null : f.status().name();
        long total = findings.countByFilter(tenantId(), severity, status, f.responsibleDepartmentId());
        List<QualityFinding> rows = findings.pageByFilter(
            tenantId(), severity, status, f.responsibleDepartmentId(), req.offset(), req.safeSize());
        return PageResponse.of(rows, req, total);
    }

    /**
     * 查看质量问题、当前整改任务和全部复核历史。
     *
     * <p>失败：问题不存在抛出 {@code ENG-EVAL-005}。
     */
    @Transactional(readOnly = true)
    public QualityFindingDetailResponse findingDetail(String findingId) {
        QualityFinding finding = findFinding(findingId);
        return new QualityFindingDetailResponse(
            finding,
            tasks.findByFindingIdAndTenantId(findingId, tenantId()).orElse(null),
            reviews.findByFindingIdAndTenantIdOrderByReviewedAtAsc(findingId, tenantId()));
    }

    /**
     * 将未派发质量问题派发为整改任务。
     *
     * <p>重复派发相同责任科室、责任人和截止时间时返回已有任务；重复派发但内容不同抛出 {@code ENG-EVAL-008}。
     */
    @Transactional
    public RectificationResponse dispatchRectification(
            RectificationDispatchRequest request, String idempotencyKey) {
        if (request == null || !hasText(request.findingId()) || !hasText(request.responsibleDepartmentId())
                || request.dueAt() == null) {
            throw new ApiException(ErrorCode.ENG_EVAL_001);
        }
        if (hasText(idempotencyKey) && idempotencyKey.length() > 128) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "幂等键长度超过 128");
        }
        assignments.requireActiveDepartment(request.responsibleDepartmentId());
        assignments.requireActiveUserIfPresent(request.assigneeUserId());

        QualityFinding finding = findFinding(request.findingId());
        Optional<RectificationTask> existing =
            tasks.findByFindingIdAndTenantId(request.findingId(), tenantId());
        if (existing.isPresent()) {
            RectificationTask task = existing.get();
            if (!sameDispatch(task, request)) {
                throw new ApiException(ErrorCode.ENG_EVAL_008);
            }
            return new RectificationResponse(task.taskId(), finding.status(), task.status(), traceId());
        }
        if (finding.status() != QualityFindingStatus.NEW) {
            throw new ApiException(ErrorCode.ENG_EVAL_007);
        }

        Instant now = Instant.now();
        String actor = actor();
        String taskId = shortDigestId("rct-", tenantId(), request.findingId());
        QualityFinding assignedFinding = findings.save(new QualityFinding(
            finding.id(), finding.findingId(), finding.tenantId(), finding.runId(), finding.resultId(),
            finding.indicatorId(), finding.findingCode(), finding.title(), finding.description(),
            finding.severity(), QualityFindingStatus.ASSIGNED, finding.evidenceSummary(),
            request.responsibleDepartmentId(), request.dueAt(), finding.createdAt(), finding.createdBy(),
            now, actor, finding.traceId()));
        RectificationTask task = tasks.save(new RectificationTask(
            null, taskId, tenantId(), request.findingId(), request.responsibleDepartmentId(),
            blankToNull(request.assigneeUserId()), RectificationTaskStatus.ASSIGNED, request.dueAt(),
            null, null, null, null, null, now, actor, now, actor, traceId()));
        transitions.record(FINDING_ENTITY, request.findingId(), finding.status().name(),
            assignedFinding.status().name(), "派发质量问题整改", null);
        transitions.record(TASK_ENTITY, task.taskId(), null, task.status().name(), "派发质量问题整改任务", null);
        auditRecorder.record(AuditAction.CREATE, TASK_ENTITY, task.taskId(),
            "派发质量问题整改 " + request.findingId());
        return new RectificationResponse(task.taskId(), assignedFinding.status(), task.status(), traceId());
    }

    /**
     * 提交整改说明和证据引用，不启用幂等键。
     */
    @Transactional
    public RectificationResponse submitRectification(String findingId, RectificationSubmitRequest request) {
        return submitRectification(findingId, request, null);
    }

    /**
     * 提交整改说明和证据引用，并按可选幂等键重放首次成功结果。
     *
     * <p>仅 {@code ASSIGNED}/{@code RETURNED} 整改任务可提交；同键异文抛出 {@code ENG-EVAL-008}。
     */
    @Transactional
    public RectificationResponse submitRectification(
            String findingId, RectificationSubmitRequest request, String idempotencyKey) {
        if (request == null || !hasText(request.rectificationSummary()) || !hasText(request.evidenceRef())) {
            throw new ApiException(ErrorCode.ENG_EVAL_001);
        }
        String requestDigest = digestValues(request.rectificationSummary(), request.evidenceRef());
        Optional<EvaluationIdempotencyKey> replay = findIdempotencyReplay(
            EvaluationIdempotencyOperation.RECTIFICATION_SUBMIT, findingId, requestDigest, idempotencyKey);
        if (replay.isPresent()) {
            EvaluationIdempotencyKey key = replay.get();
            return new RectificationResponse(
                key.taskId(), key.findingStatus(), key.taskStatus(), key.traceId());
        }
        QualityFinding finding = findFinding(findingId);
        RectificationTask task = findTask(findingId);
        if (task.status() != RectificationTaskStatus.ASSIGNED
                && task.status() != RectificationTaskStatus.RETURNED) {
            throw new ApiException(ErrorCode.ENG_EVAL_007);
        }
        Instant now = Instant.now();
        String actor = actor();
        RectificationTask submitted = tasks.save(new RectificationTask(
            task.id(), task.taskId(), task.tenantId(), task.findingId(), task.responsibleDepartmentId(),
            task.assigneeUserId(), RectificationTaskStatus.SUBMITTED, task.dueAt(),
            request.rectificationSummary(), request.evidenceRef(), now, actor, null,
            task.createdAt(), task.createdBy(), now, actor, task.traceId()));
        QualityFinding remediating = saveFindingStatus(finding, QualityFindingStatus.REMEDIATING, now, actor);
        transitions.record(TASK_ENTITY, task.taskId(), task.status().name(), submitted.status().name(),
            "提交质量问题整改", null);
        transitions.record(FINDING_ENTITY, findingId, finding.status().name(), remediating.status().name(),
            "责任科室提交整改", null);
        auditRecorder.record(AuditAction.UPDATE, FINDING_ENTITY, findingId, "提交质量问题整改 " + task.taskId());
        String traceId = traceId();
        saveIdempotencyKey(
            idempotencyKey, EvaluationIdempotencyOperation.RECTIFICATION_SUBMIT, findingId,
            task.taskId(), null, requestDigest, remediating.status(), submitted.status(), traceId, now, actor);
        return new RectificationResponse(task.taskId(), remediating.status(), submitted.status(), traceId);
    }

    /**
     * 按整改任务 ID 提交整改说明和证据引用。
     */
    @Transactional
    public RectificationResponse submitRectificationTask(
            String taskId, RectificationSubmitRequest request, String idempotencyKey) {
        RectificationTask task = findTaskByTaskId(taskId);
        return submitRectification(task.findingId(), request, idempotencyKey);
    }

    /**
     * 提交整改复核结论，不启用幂等键。
     */
    @Transactional
    public RectificationReviewResponse reviewRectification(String findingId, RectificationReviewRequest request) {
        return reviewRectification(findingId, request, null);
    }

    /**
     * 提交整改复核结论，并按可选幂等键重放首次成功结果。
     *
     * <p>仅已提交整改且问题处于 {@code REMEDIATING} 时可复核；{@code P0} 问题不得通过普通复核豁免。
     */
    @Transactional
    public RectificationReviewResponse reviewRectification(
            String findingId, RectificationReviewRequest request, String idempotencyKey) {
        if (request == null || request.decision() == null) {
            throw new ApiException(ErrorCode.ENG_EVAL_001);
        }
        String requestDigest = digestValues(
            request.decision().name(), request.comment(), request.evidenceRef());
        Optional<EvaluationIdempotencyKey> replay = findIdempotencyReplay(
            EvaluationIdempotencyOperation.RECTIFICATION_REVIEW, findingId, requestDigest, idempotencyKey);
        if (replay.isPresent()) {
            EvaluationIdempotencyKey key = replay.get();
            return new RectificationReviewResponse(
                key.reviewId(), key.findingStatus(), key.taskStatus(), key.traceId());
        }
        QualityFinding finding = findFinding(findingId);
        RectificationTask task = findTask(findingId);
        if (task.status() != RectificationTaskStatus.SUBMITTED
                || finding.status() != QualityFindingStatus.REMEDIATING) {
            throw new ApiException(ErrorCode.ENG_EVAL_007);
        }
        if (request.decision() == RectificationReviewDecision.WAIVED
                && finding.severity() == QualityFindingSeverity.P0) {
            throw new ApiException(ErrorCode.ENG_EVAL_007, "P0 质量问题不得通过普通复核豁免");
        }
        if ((request.decision() == RectificationReviewDecision.APPROVED
                && !hasText(request.comment()) && !hasText(request.evidenceRef()))
                || (request.decision() == RectificationReviewDecision.RETURNED && !hasText(request.comment()))
                || (request.decision() == RectificationReviewDecision.WAIVED && !hasText(request.comment()))) {
            throw new ApiException(ErrorCode.ENG_EVAL_007);
        }
        Instant now = Instant.now();
        String actor = actor();
        String reviewId = "rr-" + UUID.randomUUID();
        reviews.save(new RectificationReview(
            null, reviewId, tenantId(), findingId, task.taskId(), request.decision(), request.comment(),
            request.evidenceRef(), actor, now, now, actor, now, actor, traceId()));
        QualityFindingStatus findingStatus = switch (request.decision()) {
            case APPROVED -> QualityFindingStatus.CLOSED;
            case RETURNED -> QualityFindingStatus.REMEDIATING;
            case WAIVED -> QualityFindingStatus.WAIVED;
        };
        RectificationTaskStatus taskStatus = switch (request.decision()) {
            case APPROVED -> RectificationTaskStatus.CLOSED;
            case RETURNED -> RectificationTaskStatus.RETURNED;
            case WAIVED -> RectificationTaskStatus.WAIVED;
        };
        QualityFinding reviewedFinding = saveFindingStatus(finding, findingStatus, now, actor);
        RectificationTask reviewedTask = tasks.save(new RectificationTask(
            task.id(), task.taskId(), task.tenantId(), task.findingId(), task.responsibleDepartmentId(),
            task.assigneeUserId(), taskStatus, task.dueAt(), task.rectificationSummary(), task.evidenceRef(),
            task.submittedAt(), task.submittedBy(),
            taskStatus == RectificationTaskStatus.CLOSED || taskStatus == RectificationTaskStatus.WAIVED
                ? now : task.closedAt(),
            task.createdAt(), task.createdBy(), now, actor, task.traceId()));
        transitions.record(FINDING_ENTITY, findingId, finding.status().name(), reviewedFinding.status().name(),
            "复核质量问题整改 " + request.decision(), null);
        transitions.record(TASK_ENTITY, task.taskId(), task.status().name(), reviewedTask.status().name(),
            "复核整改任务 " + request.decision(), null);
        auditRecorder.record(AuditAction.REVIEW, FINDING_ENTITY, findingId,
            "复核质量问题整改 " + request.decision());
        String traceId = traceId();
        saveIdempotencyKey(
            idempotencyKey, EvaluationIdempotencyOperation.RECTIFICATION_REVIEW, findingId,
            task.taskId(), reviewId, requestDigest, reviewedFinding.status(), reviewedTask.status(),
            traceId, now, actor);
        return new RectificationReviewResponse(reviewId, reviewedFinding.status(), reviewedTask.status(), traceId);
    }

    /**
     * 按整改任务 ID 提交整改复核结论。
     */
    @Transactional
    public RectificationReviewResponse reviewRectificationTask(
            String taskId, RectificationReviewRequest request, String idempotencyKey) {
        RectificationTask task = findTaskByTaskId(taskId);
        return reviewRectification(task.findingId(), request, idempotencyKey);
    }

    /**
     * 按整改任务 ID 提交专用豁免动作，要求带决定依据。
     */
    @Transactional
    public RectificationReviewResponse waiveRectificationTask(
            String taskId, RectificationWaiveRequest request, String idempotencyKey) {
        if (request == null || !hasText(request.reason()) || !hasText(request.decisionRef())) {
            throw new ApiException(ErrorCode.ENG_EVAL_001);
        }
        String evidence = hasText(request.evidenceRef())
            ? "决定依据: " + request.decisionRef() + "；证据引用: " + request.evidenceRef()
            : "决定依据: " + request.decisionRef();
        return reviewRectificationTask(
            taskId,
            new RectificationReviewRequest(RectificationReviewDecision.WAIVED, request.reason(), evidence),
            idempotencyKey);
    }

    /**
     * 生成整改闭环报告。
     */
    @Transactional(readOnly = true)
    public RectificationReportResponse rectificationReport(RectificationReportFilter filter) {
        return rectificationReport(filter, Instant.now());
    }

    /**
     * 生成整改闭环报告，可注入时钟用于可重复测试。
     */
    @Transactional(readOnly = true)
    public RectificationReportResponse rectificationReport(RectificationReportFilter filter, Instant now) {
        RectificationReportFilter f = filter == null ? new RectificationReportFilter(null) : filter;
        String departmentId = blankToNull(f.responsibleDepartmentId());
        String tenant = tenantId();
        long total = tasks.countByTenantIdAndDepartmentFilter(tenant, departmentId);
        long open = tasks.countOpenByTenantIdAndDepartmentFilter(tenant, departmentId);
        long closed = tasks.countClosedByTenantIdAndDepartmentFilter(tenant, departmentId);
        long waived = tasks.countWaivedByTenantIdAndDepartmentFilter(tenant, departmentId);
        long overdue = tasks.countOverdueOpenByTenantIdAndDepartmentFilter(tenant, departmentId, now);
        long p0Open = tasks.countOpenP0ByTenantIdAndDepartmentFilter(tenant, departmentId);
        BigDecimal closureRate = total == 0
            ? BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY)
            : BigDecimal.valueOf(closed).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        RectificationReportStatus status = total == 0
            ? RectificationReportStatus.NO_TASKS
            : RectificationReportStatus.AVAILABLE;
        return new RectificationReportResponse(
            status, total, open, closed, waived, overdue, p0Open,
            closureRate, TASK_ENTITY, traceId());
    }

    /**
     * 按运行 ID 装配可解释诊断响应。
     *
     * <p>诊断响应包含运行状态快照、关联结果 ID、问题 ID、整改任务 ID 与 traceId；运行不存在抛出 {@code ENG-EVAL-001}。
     */
    @Transactional(readOnly = true)
    public DiagnoseResponse diagnose(String runId) {
        EvaluationRun run = runs.findByRunIdAndTenantId(runId, tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_001, "评估运行不存在"));
        List<EvaluationResult> runResults = results.findByRunIdAndTenantIdOrderByCreatedAtAsc(runId, tenantId());
        List<QualityFinding> runFindings = new ArrayList<>();
        List<RectificationTask> runTasks = new ArrayList<>();
        for (EvaluationResult result : runResults) {
            List<QualityFinding> resultFindings =
                findings.findByResultIdAndTenantIdOrderByCreatedAtAsc(result.resultId(), tenantId());
            runFindings.addAll(resultFindings);
            for (QualityFinding finding : resultFindings) {
                tasks.findByFindingIdAndTenantId(finding.findingId(), tenantId()).ifPresent(runTasks::add);
            }
        }
        Map<String, List<String>> related = Map.of(
            "results", runResults.stream().map(EvaluationResult::resultId).toList(),
            "findings", runFindings.stream().map(QualityFinding::findingId).toList(),
            "tasks", runTasks.stream().map(RectificationTask::taskId).toList());
        return diagnoseAssembler.assemble(
            RUN_ENTITY, runId, tenantId(), run.status().name(), run, List.of(), related, null,
            run.traceId() == null ? traceId() : run.traceId());
    }

    private void validateIndicator(EvaluationIndicatorCreateRequest request) {
        if (request == null || !hasText(request.indicatorCode())
                || !hasText(request.name()) || request.subjectType() == null
                || !hasText(request.denominatorDefinition()) || !hasText(request.numeratorDefinition())
                || !hasText(request.timeWindow()) || !hasText(request.organizationScope())
                || !hasText(request.responsibleDepartmentId()) || !hasText(request.sourceRef())) {
            throw new ApiException(ErrorCode.ENG_EVAL_001);
        }
        validateRuleDefinition("分母", request.denominatorDefinition());
        validateRuleDefinition("分子", request.numeratorDefinition());
        if (hasText(request.exclusionDefinition())) {
            validateRuleDefinition("排除", request.exclusionDefinition());
        }
    }

    private void validateRun(EvaluationRunRequest request) {
        if (request == null || !hasText(request.runCode()) || request.runType() == null
                || !hasText(request.scenarioCode()) || !hasText(request.inputDigest())
                || request.results() == null || request.results().isEmpty()) {
            throw new ApiException(ErrorCode.ENG_EVAL_001);
        }
        boolean hasContextReference = hasText(request.sourceEventId()) || hasText(request.contextSnapshotId());
        boolean hasManualSource = request.runType() == EvaluationRunType.MANUAL_SAMPLE
            && request.results().stream().allMatch(result -> result != null && hasText(result.sourceRef()));
        if (!hasContextReference && !hasManualSource) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "评估运行缺少可追溯的上下文或人工抽检来源");
        }
    }

    private Map<String, EvaluationIndicator> resolveActiveIndicatorsForRun(
            EvaluationRunRequest request, String tenantId, String runtimeReleaseId) {
        Map<String, EvaluationIndicator> activeIndicators = hasText(runtimeReleaseId)
            ? activeRuntimeIndicatorsById(tenantId, runtimeReleaseId)
            : new LinkedHashMap<>();
        for (EvaluationResultRequest resultRequest : request.results()) {
            EvaluationIndicator indicator = hasText(runtimeReleaseId)
                ? activeIndicators.get(resultRequest.indicatorId())
                : indicators.findByIndicatorIdAndTenantId(resultRequest.indicatorId(), tenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_004));
            if (indicator == null || indicator.status() != EvaluationIndicatorStatus.ACTIVE) {
                throw new ApiException(ErrorCode.ENG_EVAL_004);
            }
            activeIndicators.put(resultRequest.indicatorId(), indicator);
            validateResult(resultRequest);
        }
        return activeIndicators;
    }

    private Map<String, EvaluationIndicator> activeRuntimeIndicatorsById(
            String tenantId, String runtimeReleaseId) {
        Map<String, EvaluationIndicator> runtimeIndicators = new LinkedHashMap<>();
        for (EvaluationIndicator indicator : runtimeEvaluations.select(tenantId, runtimeReleaseId)) {
            if (indicator.status() != EvaluationIndicatorStatus.ACTIVE) {
                throw new ApiException(ErrorCode.ENG_EVAL_004);
            }
            runtimeIndicators.put(indicator.indicatorId(), indicator);
        }
        return runtimeIndicators;
    }

    private String resolveRuntimeReleaseId(EvaluationRunRequest request, String tenantId) {
        if (!hasText(request.contextSnapshotId())) {
            return blankToNull(request.runtimeReleaseId());
        }
        ContextSnapshot snapshot = snapshots
            .findBySnapshotIdAndTenantId(request.contextSnapshotId(), tenantId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_EVAL_001, "评估运行引用的上下文快照不存在"));
        if (!hasText(snapshot.runtimeReleaseId())) {
            throw new ApiException(
                ErrorCode.ENG_EVAL_001, "评估运行引用的上下文快照缺少机构生效版本");
        }
        if (hasText(request.runtimeReleaseId())
                && !snapshot.runtimeReleaseId().equals(request.runtimeReleaseId().trim())) {
            throw new ApiException(
                ErrorCode.ENG_EVAL_001, "评估机构生效版本与上下文快照锁定的机构生效版本不一致");
        }
        return snapshot.runtimeReleaseId();
    }

    private void validateResult(EvaluationResultRequest result) {
        if (result == null || !hasText(result.indicatorId()) || result.subjectType() == null
                || !hasText(result.subjectRefId()) || result.resultLevel() == null
                || !hasText(result.evidenceSummary())) {
            throw new ApiException(ErrorCode.ENG_EVAL_001);
        }
        for (QualityFindingRequest finding : safeFindings(result.findings())) {
            if (finding == null || !hasText(finding.findingCode()) || !hasText(finding.title())
                    || !hasText(finding.description()) || finding.severity() == null
                    || !hasText(finding.evidenceSummary())) {
                throw new ApiException(ErrorCode.ENG_EVAL_001);
            }
            if (isHighRisk(finding.severity())
                    && (!hasText(finding.responsibleDepartmentId()) || finding.dueAt() == null)) {
                throw new ApiException(ErrorCode.ENG_EVAL_006);
            }
            if (!isHighRisk(finding.severity())
                    && hasPartialAssignment(finding)
                    && (!hasText(finding.responsibleDepartmentId()) || finding.dueAt() == null)) {
                throw new ApiException(ErrorCode.ENG_EVAL_001);
            }
        }
    }

    private JsonNode readResourcePayload(CanonicalResource resource) {
        try {
            return json.readTree(resource.resourcePayloadJson());
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.ENG_EVAL_001,
                "临床资源载荷解析失败：" + resource.resourceId());
        }
    }

    private RuleDslEvaluation evaluateIndicatorRule(
            EvaluationIndicator indicator, String definition, ObjectNode contextJson, String explain) {
        try {
            return ruleEvaluator.evaluateConditionTree(
                json.readTree(definition),
                contextJson,
                json.getNodeFactory().textNode(explain));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.ENG_EVAL_001,
                "评估指标规则解析或执行失败：" + indicator.indicatorCode());
        }
    }

    private void validateRuleDefinition(String label, String definition) {
        try {
            ruleEvaluator.evaluateConditionTree(
                json.readTree(definition),
                json.createObjectNode(),
                json.getNodeFactory().textNode(label + "规则定义校验"));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.ENG_EVAL_001,
                label + "规则定义必须是可执行的规则 DSL 条件树");
        }
    }

    private String automaticEvaluationInputDigest(
            EvaluationEvaluateSnapshotRequest request,
            ContextSnapshot snapshot,
            List<CanonicalResource> resourceList,
            List<EvaluationIndicator> activeIndicators) {
        List<String> values = new ArrayList<>();
        values.add(request.contextSnapshotId());
        values.add(request.scenarioCode());
        values.add(snapshot.runtimeReleaseId());
        values.add(snapshot.patientId());
        values.add(snapshot.encounterId());
        for (CanonicalResource resource : resourceList) {
            values.add(resource.resourceId());
            values.add(resource.resourceType().name());
            values.add(resource.resourcePayloadJson());
        }
        for (EvaluationIndicator indicator : activeIndicators) {
            values.add(indicator.indicatorId());
            values.add(indicator.indicatorCode());
            values.add(Integer.toString(indicator.versionNo()));
            values.add(indicator.denominatorDefinition());
            values.add(indicator.numeratorDefinition());
            values.add(indicator.exclusionDefinition());
            values.add(indicator.scoringDefinition());
        }
        return digestValues(values.toArray(String[]::new));
    }

    private EvaluationRunResponse replayRunResponse(EvaluationRun run) {
        List<EvaluationResult> runResults =
            results.findByRunIdAndTenantIdOrderByCreatedAtAsc(run.runId(), run.tenantId());
        int findingCount = 0;
        int taskCount = 0;
        for (EvaluationResult result : runResults) {
            List<QualityFinding> resultFindings =
                findings.findByResultIdAndTenantIdOrderByCreatedAtAsc(result.resultId(), run.tenantId());
            findingCount += resultFindings.size();
            for (QualityFinding finding : resultFindings) {
                if (tasks.findByFindingIdAndTenantId(finding.findingId(), run.tenantId()).isPresent()) {
                    taskCount++;
                }
            }
        }
        return new EvaluationRunResponse(
            run.runId(), run.status(), runResults.size(), findingCount, taskCount,
            run.traceId() == null ? traceId() : run.traceId());
    }

    private String automaticEvaluationRunCode(String inputDigest) {
        String digestValue = inputDigest == null ? "" : inputDigest.replace("sha256:", "");
        return "ER_AUTO_" + digestValue.substring(0, Math.min(16, digestValue.length()));
    }

    private String evidenceSummary(String conclusion, List<RuleDslEvaluation> evaluations) {
        StringBuilder summary = new StringBuilder(conclusion);
        for (RuleDslEvaluation evaluation : evaluations) {
            if (evaluation == null || evaluation.explanation() == null || evaluation.explanation().isNull()) {
                continue;
            }
            summary.append(" 规则证据：").append(compactJson(evaluation.explanation())).append('。');
        }
        return limitEvidenceSummary(summary.toString());
    }

    private String compactJson(JsonNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (Exception exception) {
            return node.toString();
        }
    }

    private String limitEvidenceSummary(String value) {
        if (value.length() <= 2000) {
            return value;
        }
        return value.substring(0, 1997) + "...";
    }

    private boolean shouldAssign(QualityFindingRequest request) {
        return isHighRisk(request.severity())
            || (hasText(request.responsibleDepartmentId()) && request.dueAt() != null);
    }

    private boolean hasPartialAssignment(QualityFindingRequest request) {
        return hasText(request.responsibleDepartmentId())
            || request.dueAt() != null
            || hasText(request.assigneeUserId());
    }

    private boolean isHighRisk(QualityFindingSeverity severity) {
        return severity == QualityFindingSeverity.P0 || severity == QualityFindingSeverity.P1;
    }

    private List<QualityFindingRequest> safeFindings(List<QualityFindingRequest> value) {
        return value == null ? List.of() : value;
    }

    private EvaluationIndicator findIndicator(String indicatorId) {
        return indicators.findByIndicatorIdAndTenantId(indicatorId, tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_002));
    }

    private QualityFinding findFinding(String findingId) {
        return findings.findByFindingIdAndTenantId(findingId, tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_005));
    }

    private RectificationTask findTask(String findingId) {
        return tasks.findByFindingIdAndTenantId(findingId, tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_005));
    }

    private RectificationTask findTaskByTaskId(String taskId) {
        if (!hasText(taskId)) {
            throw new ApiException(ErrorCode.ENG_EVAL_001);
        }
        return tasks.findByTaskIdAndTenantId(taskId, tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_005));
    }

    private Optional<EvaluationIdempotencyKey> findIdempotencyReplay(
            EvaluationIdempotencyOperation operation, String findingId,
            String requestDigest, String idempotencyKey) {
        if (!hasText(idempotencyKey)) {
            return Optional.empty();
        }
        if (idempotencyKey.length() > 128) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "幂等键长度超过 128");
        }
        Optional<EvaluationIdempotencyKey> existing =
            idempotencyKeys.findByTenantIdAndOperationTypeAndIdempotencyKey(
                tenantId(), operation, idempotencyKey);
        if (existing.isPresent()
                && (!existing.get().findingId().equals(findingId)
                || !existing.get().requestDigest().equals(requestDigest))) {
            throw new ApiException(ErrorCode.ENG_EVAL_008);
        }
        return existing;
    }

    private void saveIdempotencyKey(
            String idempotencyKey, EvaluationIdempotencyOperation operation, String findingId,
            String taskId, String reviewId, String requestDigest,
            QualityFindingStatus findingStatus, RectificationTaskStatus taskStatus,
            String traceId, Instant now, String actor) {
        if (!hasText(idempotencyKey)) {
            return;
        }
        idempotencyKeys.save(new EvaluationIdempotencyKey(
            null, tenantId(), idempotencyKey, operation, findingId, taskId, reviewId,
            requestDigest, findingStatus, taskStatus, now, actor, traceId));
    }

    private String digestValues(String... values) {
        StringBuilder content = new StringBuilder();
        for (String value : values) {
            String normalized = value == null ? "" : value;
            content.append(normalized.length()).append(':').append(normalized);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", exception);
        }
    }

    private String shortDigestId(String prefix, String... values) {
        return prefix + digestValues(values).substring("sha256:".length(), "sha256:".length() + 16);
    }

    private boolean sameDispatch(RectificationTask task, RectificationDispatchRequest request) {
        return task.responsibleDepartmentId().equals(request.responsibleDepartmentId())
            && java.util.Objects.equals(task.assigneeUserId(), blankToNull(request.assigneeUserId()))
            && task.dueAt().equals(request.dueAt());
    }

    private String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private void requireStatus(EvaluationIndicator indicator, EvaluationIndicatorStatus required) {
        if (indicator.status() != required) {
            throw new ApiException(ErrorCode.ENG_EVAL_003);
        }
    }

    private AssetVersion requireAssetVersion(EvaluationIndicator indicator) {
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            indicator.tenantId(),
            VersionedAssetType.EVALUATION,
            indicator.indicatorCode(),
            assetVersionNo(indicator)
        ).orElseThrow(() -> new ApiException(
            ErrorCode.ENG_EVAL_003,
            "评估指标缺少统一资产版本，禁止推进发布状态"
        ));
    }

    private String assetVersionNo(EvaluationIndicator indicator) {
        return AssetVersionNumbers.canonical(indicator.versionNo());
    }

    private VersionReleaseCommand releaseCommand(
            EvaluationIndicator indicator,
            AssetVersion assetVersion,
            String reason) {
        return releaseCommand(indicator, assetVersion, reason, null);
    }

    private VersionReleaseCommand releaseCommand(
            EvaluationIndicator indicator,
            AssetVersion assetVersion,
            EvaluationIndicatorReleaseRequest request) {
        return releaseCommand(indicator, assetVersion, request, RolloutPolicy.all());
    }

    private VersionReleaseCommand releaseCommand(
            EvaluationIndicator indicator,
            AssetVersion assetVersion,
            EvaluationIndicatorReleaseRequest request,
            RolloutPolicy rolloutPolicy) {
        return releaseCommand(
            indicator,
            assetVersion,
            requireReleaseReason(request),
            request.publishEvidence(),
            rolloutPolicy
        );
    }

    private VersionReleaseCommand releaseCommand(
            EvaluationIndicator indicator,
            AssetVersion assetVersion,
            String reason,
            VersionPublishEvidence publishEvidence) {
        return releaseCommand(indicator, assetVersion, reason, publishEvidence, RolloutPolicy.all());
    }

    private VersionReleaseCommand releaseCommand(
            EvaluationIndicator indicator,
            AssetVersion assetVersion,
            String reason,
            VersionPublishEvidence publishEvidence,
            RolloutPolicy rolloutPolicy) {
        return new VersionReleaseCommand(
            indicator.tenantId(),
            VersionedAssetType.EVALUATION,
            indicator.indicatorCode(),
            assetVersion.versionId(),
            assetVersion.organizationScope(),
            assetVersion.applicableScope(),
            null,
            null,
            rolloutPolicy,
            assetVersion.contentHash(),
            reason,
            actor(),
            traceId(),
            publishEvidence == null ? null : publishEvidence.qualityGate()
        );
    }

    private String evaluationApplicableScope(EvaluationIndicator indicator) {
        return indicator.subjectType().name() + ":" + indicator.timeWindow();
    }

    private String versionOrganizationScope(EvaluationIndicator indicator) {
        return null;
    }

    private String indicatorContent(EvaluationIndicator indicator) {
        ObjectNode content = json.createObjectNode();
        content.put("indicatorCode", indicator.indicatorCode());
        content.put("versionNo", indicator.versionNo());
        content.put("name", indicator.name());
        content.put("subjectType", indicator.subjectType().name());
        content.put("denominatorDefinition", indicator.denominatorDefinition());
        content.put("numeratorDefinition", indicator.numeratorDefinition());
        content.put("exclusionDefinition", indicator.exclusionDefinition());
        content.put("scoringDefinition", indicator.scoringDefinition());
        content.put("timeWindow", indicator.timeWindow());
        content.put("organizationScope", indicator.organizationScope());
        content.put("responsibleDepartmentId", indicator.responsibleDepartmentId());
        return compactJson(content);
    }

    private String requireReleaseReason(EvaluationIndicatorReleaseRequest request) {
        if (request == null || !hasText(request.reason())) {
            throw new ApiException(ErrorCode.ENG_EVAL_003, "评估指标发布必须填写审核或灰度说明");
        }
        return request.reason().trim();
    }

    private EvaluationIndicator saveIndicatorStatus(
            EvaluationIndicator indicator, EvaluationIndicatorStatus status, Instant publishedAt, Instant activatedAt) {
        Instant now = Instant.now();
        return indicators.save(new EvaluationIndicator(
            indicator.id(), indicator.indicatorId(), indicator.tenantId(), indicator.indicatorCode(),
            indicator.versionNo(), indicator.name(), indicator.subjectType(), indicator.denominatorDefinition(),
            indicator.numeratorDefinition(), indicator.exclusionDefinition(), indicator.scoringDefinition(),
            indicator.timeWindow(), indicator.organizationScope(), indicator.responsibleDepartmentId(),
            indicator.sourceRef(), status,
            publishedAt == null ? indicator.publishedAt() : publishedAt,
            publishedAt == null ? indicator.publishedBy() : actor(),
            activatedAt == null ? indicator.activatedAt() : activatedAt,
            indicator.createdAt(), indicator.createdBy(), now, actor(), indicator.traceId()));
    }

    private QualityFinding saveFindingStatus(
            QualityFinding finding, QualityFindingStatus status, Instant now, String actor) {
        return findings.save(new QualityFinding(
            finding.id(), finding.findingId(), finding.tenantId(), finding.runId(), finding.resultId(),
            finding.indicatorId(), finding.findingCode(), finding.title(), finding.description(),
            finding.severity(), status, finding.evidenceSummary(), finding.responsibleDepartmentId(),
            finding.dueAt(), finding.createdAt(), finding.createdBy(), now, actor, finding.traceId()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }
}
