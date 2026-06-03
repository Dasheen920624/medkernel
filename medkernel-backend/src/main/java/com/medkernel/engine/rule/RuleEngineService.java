package com.medkernel.engine.rule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.PayloadRef;
import com.medkernel.shared.observability.StateTransitionRecorder;

/**
 * 规则引擎应用服务（GA-ENG-API-05 受控规则资产 + 确定性执行 + 可解释日志）。
 *
 * <p>聚合规则定义、版本、测试用例、执行日志四张表，承担：
 * <ul>
 *   <li>创建规则与初始草稿版本（DSL 校验失败抛 {@code ENG-RULE-001}）；</li>
 *   <li>新增测试用例并校验状态（仅 {@code DRAFT} 可加）；</li>
 *   <li>试运行执行：复用 {@link RuleDslEvaluator}，同步写 {@code rule_execution_log} 与状态历史；</li>
 *   <li>发布门禁：要求阳性/阴性/边界/冲突四类用例齐备且全部 PASS，否则抛 {@code ENG-RULE-004}；</li>
 *   <li>真实执行：按触发点匹配已发布规则集合，返回命中明细 + 最高严重度；</li>
 *   <li>诊断：基于 {@code execution_id} 装配 {@link DiagnoseResponse}。</li>
 * </ul>
 * 所有写操作触发审计事件 {@link AuditEventPublisher} 与状态迁移记录 {@link StateTransitionRecorder}。
 */
@Service
public class RuleEngineService {

    private static final String RULE_ENTITY = "rule_definition";
    private static final String EXECUTION_ENTITY = "rule_execution";
    private static final EnumSet<RuleTestCaseType> REQUIRED_CASE_TYPES =
        EnumSet.of(RuleTestCaseType.POSITIVE, RuleTestCaseType.NEGATIVE,
            RuleTestCaseType.BOUNDARY, RuleTestCaseType.CONFLICT);

    private final RuleDefinitionRepository definitions;
    private final RuleVersionRepository versions;
    private final RuleTestCaseRepository testCases;
    private final RuleExecutionLogRepository executions;
    private final RuleDslEvaluator evaluator;
    private final AuditEventPublisher auditPublisher;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final ObjectMapper json;
    private final RuleImpactIndex impactIndex;

    /**
     * 注入规则引擎所需仓库、DSL 执行器、审计发布器、状态记录器与 JSON 处理器。
     */
    @Autowired
    public RuleEngineService(RuleDefinitionRepository definitions,
                             RuleVersionRepository versions,
                             RuleTestCaseRepository testCases,
                             RuleExecutionLogRepository executions,
                             RuleDslEvaluator evaluator,
                             AuditEventPublisher auditPublisher,
                             StateTransitionRecorder transitions,
                             DiagnoseResponseAssembler diagnoseAssembler,
                             ObjectMapper json,
                             ObjectProvider<RuleImpactIndex> impactIndexProvider) {
        this(definitions, versions, testCases, executions, evaluator, auditPublisher, transitions,
            diagnoseAssembler, json, impactIndexProvider.getIfAvailable(RuleImpactIndex::empty));
    }

    RuleEngineService(RuleDefinitionRepository definitions,
                      RuleVersionRepository versions,
                      RuleTestCaseRepository testCases,
                      RuleExecutionLogRepository executions,
                      RuleDslEvaluator evaluator,
                      AuditEventPublisher auditPublisher,
                      StateTransitionRecorder transitions,
                      DiagnoseResponseAssembler diagnoseAssembler,
                      ObjectMapper json) {
        this(definitions, versions, testCases, executions, evaluator, auditPublisher, transitions,
            diagnoseAssembler, json, RuleImpactIndex.empty());
    }

    RuleEngineService(RuleDefinitionRepository definitions,
                      RuleVersionRepository versions,
                      RuleTestCaseRepository testCases,
                      RuleExecutionLogRepository executions,
                      RuleDslEvaluator evaluator,
                      AuditEventPublisher auditPublisher,
                      StateTransitionRecorder transitions,
                      DiagnoseResponseAssembler diagnoseAssembler,
                      ObjectMapper json,
                      RuleImpactIndex impactIndex) {
        this.definitions = definitions;
        this.versions = versions;
        this.testCases = testCases;
        this.executions = executions;
        this.evaluator = evaluator;
        this.auditPublisher = auditPublisher;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.json = json;
        this.impactIndex = impactIndex == null ? RuleImpactIndex.empty() : impactIndex;
    }

    /**
     * 创建规则定义和初始草稿版本。
     *
     * <p>前置：当前请求必须携带租户上下文；DSL 必须包含 trigger/when/then/explain。
     * 失败：DSL 校验失败抛 {@link ApiException} 错误码 {@code ENG-RULE-001}。
     */
    @Transactional
    public RuleCreateResponse createRule(RuleCreateRequest request) {
        String tenantId = requireCurrentTenant();
        String traceId = RequestContext.currentTraceId();
        String actor = RequestContext.currentUserId().orElse("system");
        Instant now = Instant.now();
        validateDsl(request.dsl());
        ensureRuleCodeAvailable(tenantId, request.ruleCode(), null);

        String ruleId = "rule-" + UUID.randomUUID();
        String versionId = "rv-" + UUID.randomUUID();
        RuleDefinition definition = new RuleDefinition(
            null, ruleId, tenantId, request.ruleCode(), request.name(), request.ruleType(),
            request.authoringMode() == null ? RuleAuthoringMode.DSL : request.authoringMode(),
            request.riskLevel() == null ? RuleRiskLevel.MEDIUM : request.riskLevel(),
            RuleDefinitionStatus.DRAFT, versionId, request.packageVersion(), request.applicableOrgUnitId(),
            now, actor, now, actor, traceId);
        RuleVersion version = new RuleVersion(
            null, versionId, tenantId, ruleId, 1, request.sourceRef(), request.changeSummary(),
            writeJson(request.dsl()), writeJson(request.explanation()),
            RuleVersionStatus.DRAFT, null, null, null, now, actor, now, actor, traceId);

        definitions.save(definition);
        versions.save(version);
        transitions.record(RULE_ENTITY, ruleId, null, RuleDefinitionStatus.DRAFT.name(), "CREATE_RULE", null);
        auditPublisher.publish(AuditAction.CREATE, RULE_ENTITY, ruleId, "创建规则 " + request.ruleCode());
        return new RuleCreateResponse(ruleId, versionId, RuleDefinitionStatus.DRAFT, traceId);
    }

    /**
     * 更新草稿规则定义和当前草稿版本。
     *
     * <p>本卡只收口 API 合同与草稿修改，不伪造多版本能力；完整版本递增、灰度和回滚由 SYS-04 承接。
     */
    @Transactional
    public RuleDetailResponse updateRule(String ruleId, RuleUpdateRequest request) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        ensureDraft(rule);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        validateDsl(request.dsl());
        ensureRuleCodeAvailable(tenantId, request.ruleCode(), ruleId);

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        RuleDefinition updatedRule = new RuleDefinition(
            rule.id(), rule.ruleId(), rule.tenantId(), request.ruleCode(), request.name(),
            request.ruleType(), request.authoringMode() == null ? RuleAuthoringMode.DSL : request.authoringMode(),
            request.riskLevel() == null ? RuleRiskLevel.MEDIUM : request.riskLevel(),
            RuleDefinitionStatus.DRAFT, rule.activeVersionId(), request.packageVersion(),
            request.applicableOrgUnitId(), rule.createdAt(), rule.createdBy(), now, actor,
            RequestContext.currentTraceId());
        RuleVersion updatedVersion = new RuleVersion(
            version.id(), version.versionId(), version.tenantId(), version.ruleId(), version.versionNo(),
            request.sourceRef(), request.changeSummary(), writeJson(request.dsl()), writeJson(request.explanation()),
            RuleVersionStatus.DRAFT, version.publishedAt(), version.publishedBy(), version.rollbackVersionId(),
            version.createdAt(), version.createdBy(), now, actor, RequestContext.currentTraceId());

        definitions.save(updatedRule);
        versions.save(updatedVersion);
        transitions.record(RULE_ENTITY, ruleId, rule.status().name(), RuleDefinitionStatus.DRAFT.name(),
            "UPDATE_RULE", null);
        auditPublisher.publish(AuditAction.UPDATE, RULE_ENTITY, ruleId, "更新规则 " + request.ruleCode());
        return new RuleDetailResponse(
            updatedRule, updatedVersion,
            testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId));
    }

    /**
     * 装载规则当前版本与全部测试用例的聚合详情。
     *
     * <p>失败：规则不存在抛 {@code ENG-RULE-002}；版本不存在抛 {@code ENG-RULE-003}。
     */
    @Transactional(readOnly = true)
    public RuleDetailResponse detail(String ruleId) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        return new RuleDetailResponse(
            rule, version, testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId));
    }

    /**
     * 按可选状态、类型、风险级别过滤分页查询规则定义。
     *
     * <p>过滤条件为 {@code null} 时不进入 SQL；总数与行集复用 {@link RuleDefinitionRepository#countByFilter}
     * / {@link RuleDefinitionRepository#pageByFilter}。
     */
    @Transactional(readOnly = true)
    public PageResponse<RuleDefinition> list(RuleFilter filter, PageRequest page) {
        String tenantId = requireCurrentTenant();
        String status = filter == null || filter.status() == null ? null : filter.status().name();
        String type = filter == null || filter.ruleType() == null ? null : filter.ruleType().name();
        String risk = filter == null || filter.riskLevel() == null ? null : filter.riskLevel().name();
        List<RuleDefinition> effectiveRows = effectiveRulesByFilter(tenantId, status, type, risk);
        long total = effectiveRows.size();
        List<RuleDefinition> rows = slice(effectiveRows, page.offset(), page.safeSize());
        return PageResponse.of(rows, page, total);
    }

    /**
     * 为指定规则当前版本新增一条测试用例。
     *
     * <p>前置：规则必须处于 {@code DRAFT} 状态，否则抛 {@code ENG-RULE-006}；
     * 失败：规则不存在抛 {@code ENG-RULE-002}。
     */
    @Transactional
    public RuleTestCaseResponse addTestCase(String ruleId, RuleTestCaseRequest request) {
        String tenantId = requireCurrentTenant();
        String traceId = RequestContext.currentTraceId();
        String actor = RequestContext.currentUserId().orElse("system");
        Instant now = Instant.now();
        RuleDefinition rule = findRule(ruleId, tenantId);
        ensureDraft(rule);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        String caseId = "rtc-" + UUID.randomUUID();
        RuleTestCase saved = testCases.save(new RuleTestCase(
            null, caseId, tenantId, ruleId, version.versionId(), request.caseType(),
            writeJson(request.inputPayload()), request.expectedHit(), request.expectedSeverity(),
            request.expectedActionCode(), null, RuleTestCaseStatus.NOT_RUN, null, null,
            now, actor, now, actor, traceId));
        auditPublisher.publish(AuditAction.UPDATE, RULE_ENTITY, ruleId, "新增规则测试用例 " + saved.caseId());
        return new RuleTestCaseResponse(saved.caseId(), saved.caseType(), saved.lastStatus());
    }

    /**
     * 执行当前版本全部测试用例并回写结果，不推进发布状态。
     */
    @Transactional
    public RuleTestRunResponse runTests(String ruleId) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        List<RuleTestCaseResult> results = runTestCases(version, tenantId);
        boolean allPassed = !results.isEmpty()
            && results.stream().allMatch(result -> result.status() == RuleTestCaseStatus.PASS);
        return new RuleTestRunResponse(
            ruleId, version.versionId(), allPassed, results, RequestContext.currentTraceId());
    }

    /**
     * 用指定上下文试运行执行规则当前版本，并写入执行日志与状态迁移。
     *
     * <p>触发点取自 DSL 的 {@code trigger}（缺省 {@code SIMULATE}）；
     * 失败：规则/版本不存在抛 {@code ENG-RULE-002}/{@code ENG-RULE-003}。
     */
    @Transactional
    public RuleEvaluationItem simulate(String ruleId, RuleSimulateRequest request) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        String trigger = readJson(version.dslJson()).path("trigger").asText("SIMULATE");
        return evaluateAndLog(rule, version, tenantId, request.context(), trigger, null);
    }

    /**
     * 执行规则发布门禁并把状态从 {@code DRAFT} 推进到 {@code PUBLISHED}。
     *
     * <p>门禁：阳性/阴性/边界/冲突四类用例必须齐备且全部 PASS，否则抛 {@code ENG-RULE-004}；
     * 前置规则非草稿抛 {@code ENG-RULE-006}。
     */
    @Transactional
    public RulePublishResponse publish(String ruleId) {
        return publish(ruleId, new RulePublishRequest(null, null));
    }

    /**
     * 执行规则发布门禁并把状态从 {@code DRAFT} 推进到 {@code PUBLISHED}。
     *
     * <p>高危规则必须携带当前影响分析摘要；发布仍要求测试用例覆盖完整且全部通过。
     */
    @Transactional
    public RulePublishResponse publish(String ruleId, RulePublishRequest request) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        ensureDraft(rule);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        List<RuleTestCase> cases = testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId);
        ensureCoverage(cases);
        RuleImpactResponse impact = impactFor(rule, version);
        if (requiresImpact(rule) && (request == null || request.impactDigest() == null
                || !request.impactDigest().equals(impact.impactDigest()))) {
            throw new ApiException(ErrorCode.RULE_PUBLISH_GATE_DENIED, "高危规则发布前必须提交当前影响分析摘要");
        }

        List<RuleTestCaseResult> results = cases.stream()
            .map(testCase -> runTestCase(version, testCase))
            .toList();
        boolean passed = results.stream().allMatch(result -> result.status() == RuleTestCaseStatus.PASS);
        if (!passed) {
            throw new ApiException(ErrorCode.ENG_RULE_004, "规则测试用例未全部通过");
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        RuleVersion publishedVersion = copyVersion(
            version, RuleVersionStatus.PUBLISHED, now, actor, now, actor, RequestContext.currentTraceId());
        RuleDefinition publishedRule = copyRule(
            rule, RuleDefinitionStatus.PUBLISHED, version.versionId(), now, actor, RequestContext.currentTraceId());
        versions.save(publishedVersion);
        definitions.save(publishedRule);
        transitions.record(RULE_ENTITY, ruleId, rule.status().name(),
            RuleDefinitionStatus.PUBLISHED.name(), "PUBLISH_RULE", null);
        auditPublisher.publish(AuditAction.PUBLISH, RULE_ENTITY, ruleId, "发布规则版本 " + version.versionId());
        return new RulePublishResponse(
            ruleId, version.versionId(), RuleDefinitionStatus.PUBLISHED,
            RequestContext.currentTraceId(), results, impact.impactDigest(), impact.analysisStatus());
    }

    /**
     * 计算发布前影响分析，只返回当前关系库可真实证明的对象。
     */
    @Transactional(readOnly = true)
    public RuleImpactResponse impact(String ruleId) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        return impactFor(rule, version);
    }

    /**
     * 按触发点和上下文执行候选已发布规则集合。
     *
     * <p>候选范围：请求未指定 {@code ruleIds} 时取全租户已发布规则，否则取指定规则；
     * 仅 DSL 的 {@code trigger} 与请求 {@code triggerPoint} 匹配的版本参与评估。
     */
    @Transactional
    public RuleEvaluateResponse evaluate(RuleEvaluateRequest request) {
        String tenantId = requireCurrentTenant();
        List<RuleDefinition> candidates = request.ruleIds().isEmpty()
            ? effectivePublishedRules(tenantId)
            : request.ruleIds().stream().map(ruleId -> findEffectiveRule(ruleId, tenantId)).toList();

        List<RuleEvaluationItem> items = candidates.stream()
            .filter(rule -> rule.status() == RuleDefinitionStatus.PUBLISHED)
            .map(rule -> Map.entry(rule, findVersion(rule.activeVersionId(), rule.tenantId())))
            .filter(entry -> entry.getValue().status() == RuleVersionStatus.PUBLISHED)
            .filter(entry -> triggerMatches(entry.getValue(), request.triggerPoint()))
            .map(entry -> evaluateAndLog(entry.getKey(), entry.getValue(), tenantId,
                request.context(), request.triggerPoint(), request.eventId()))
            .toList();
        RuleRiskLevel highest = items.stream()
            .map(RuleEvaluationItem::severity)
            .reduce(null, RuleRiskLevel::max);
        return new RuleEvaluateResponse("eval-" + UUID.randomUUID(), items, highest, RequestContext.currentTraceId());
    }

    /**
     * 按执行 ID 装配可解释诊断响应。
     *
     * <p>失败：执行记录不存在抛 {@code ENG-RULE-002}；返回结构由
     * {@link DiagnoseResponseAssembler} 统一组装（实体快照 + 状态历史 + 输入摘要 PayloadRef）。
     */
    @Transactional(readOnly = true)
    public DiagnoseResponse diagnose(String executionId) {
        String tenantId = requireCurrentTenant();
        RuleExecutionLog execution = executions.findByExecutionIdAndTenantId(executionId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "规则执行记录不存在: " + executionId));
        PayloadRef payloadRef = new PayloadRef(
            PayloadRef.STORAGE_INLINE, execution.inputDigest(),
            "db://rule_execution_log/" + execution.executionId(), 0L, "application/json");
        return diagnoseAssembler.assemble(
            EXECUTION_ENTITY, execution.executionId(), tenantId, execution.status().name(),
            execution, List.of(), Map.of(), payloadRef, execution.traceId(),
            new DiagnoseResponse.ExecutionSummary(
                execution.ruleId(),
                execution.versionId(),
                null,
                execution.errorCode()
            ));
    }

    /**
     * 返回客户面解释响应，直接读取执行日志快照。
     */
    @Transactional(readOnly = true)
    public RuleExplanationResponse explain(String executionId) {
        String tenantId = requireCurrentTenant();
        RuleExecutionLog execution = executions.findByExecutionIdAndTenantId(executionId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "规则执行记录不存在: " + executionId));
        return new RuleExplanationResponse(
            execution.executionId(), execution.ruleId(), execution.versionId(), execution.triggerPoint(),
            execution.eventId(), execution.inputDigest(), Boolean.TRUE.equals(execution.hit()), execution.severity(),
            readJsonOrArray(execution.actionsJson()), readJsonOrObject(execution.explanationJson()),
            execution.status(), execution.traceId());
    }

    private List<RuleTestCaseResult> runTestCases(RuleVersion version, String tenantId) {
        return testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId).stream()
            .map(testCase -> runTestCase(version, testCase))
            .toList();
    }

    private RuleTestCaseResult runTestCase(RuleVersion version, RuleTestCase testCase) {
        try {
            RuleDslEvaluation evaluation = evaluator.evaluate(readJson(version.dslJson()), readJson(testCase.inputPayload()));
            boolean pass = matchesExpectation(testCase, evaluation);
            RuleTestCaseStatus status = pass ? RuleTestCaseStatus.PASS : RuleTestCaseStatus.FAIL;
            String message = pass ? "测试通过" : "实际结果与期望不一致";
            testCases.save(copyTestCaseResult(testCase, evaluation.hit(), status, message));
            return new RuleTestCaseResult(
                testCase.caseId(), testCase.caseType(), Boolean.TRUE.equals(testCase.expectedHit()),
                evaluation.hit(), testCase.expectedSeverity(), evaluation.severity(), status, message);
        } catch (ApiException exception) {
            testCases.save(copyTestCaseResult(testCase, false, RuleTestCaseStatus.ERROR, exception.getMessage()));
            return new RuleTestCaseResult(
                testCase.caseId(), testCase.caseType(), Boolean.TRUE.equals(testCase.expectedHit()),
                false, testCase.expectedSeverity(), null, RuleTestCaseStatus.ERROR, exception.getMessage());
        }
    }

    private RuleEvaluationItem evaluateAndLog(RuleDefinition rule, RuleVersion version, String executionTenantId,
                                              JsonNode context, String triggerPoint, String eventId) {
        RuleDslEvaluation evaluation = evaluator.evaluate(readJson(version.dslJson()), context);
        String executionId = "rex-" + UUID.randomUUID();
        RuleExecutionStatus status = evaluation.hit() ? RuleExecutionStatus.SUCCESS : RuleExecutionStatus.MISS;
        RuleExecutionLog log = executions.save(new RuleExecutionLog(
            null, executionId, executionTenantId, rule.ruleId(), version.versionId(),
            triggerPoint, eventId, RequestContext.currentUserId().orElse(null),
            digest(context), evaluation.hit(), evaluation.severity(), writeObject(evaluation.actions()),
            writeJson(evaluation.explanation()), status, null, null,
            Instant.now(), Instant.now(), RequestContext.currentTraceId()));
        transitions.record(EXECUTION_ENTITY, log.executionId(), null, status.name(), "EXECUTE_RULE", null);
        auditPublisher.publish(AuditAction.EXECUTE, EXECUTION_ENTITY, log.executionId(), "执行规则 " + rule.ruleId());
        return new RuleEvaluationItem(
            log.executionId(), rule.ruleId(), version.versionId(), evaluation.hit(),
            evaluation.severity(), evaluation.actions(), evaluation.explanation());
    }

    private boolean matchesExpectation(RuleTestCase testCase, RuleDslEvaluation evaluation) {
        boolean expectedHit = Boolean.TRUE.equals(testCase.expectedHit());
        if (expectedHit != evaluation.hit()) {
            return false;
        }
        if (!expectedHit) {
            return true;
        }
        if (testCase.expectedSeverity() != null && testCase.expectedSeverity() != evaluation.severity()) {
            return false;
        }
        if (testCase.expectedActionCode() == null || testCase.expectedActionCode().isBlank()) {
            return true;
        }
        return evaluation.actions().stream()
            .anyMatch(action -> testCase.expectedActionCode().equals(action.actionCode()));
    }

    private void ensureCoverage(List<RuleTestCase> cases) {
        EnumSet<RuleTestCaseType> covered = EnumSet.noneOf(RuleTestCaseType.class);
        cases.forEach(testCase -> covered.add(testCase.caseType()));
        if (!covered.containsAll(REQUIRED_CASE_TYPES)) {
            throw new ApiException(ErrorCode.ENG_RULE_004,
                "规则发布必须覆盖阳性、阴性、边界、冲突四类测试用例");
        }
    }

    private RuleImpactResponse impactFor(RuleDefinition rule, RuleVersion version) {
        RuleImpactIndexSnapshot indexSnapshot = impactIndex.analyze(rule.tenantId(), rule, version);
        List<String> unavailable = indexSnapshot.unavailableScopes();
        List<RuleImpactObject> affectedRules = List.of(new RuleImpactObject(
            "RULE_DEFINITION", rule.ruleId(), rule.name(), "当前规则版本将被发布或替换"));
        String status = unavailable.isEmpty() ? "COMPLETE" : "PARTIAL";
        String digest = impactDigest(
            rule, version, status, unavailable, affectedRules,
            indexSnapshot.affectedPathways(), indexSnapshot.inPathPatients(), indexSnapshot.syncTargets());
        return new RuleImpactResponse(
            rule.ruleId(), version.versionId(), rule.riskLevel(), status, digest,
            affectedRules, indexSnapshot.affectedPathways(), indexSnapshot.inPathPatients(),
            indexSnapshot.syncTargets(), unavailable,
            RequestContext.currentTraceId());
    }

    private String impactDigest(RuleDefinition rule, RuleVersion version, String status, List<String> unavailable,
                                List<RuleImpactObject> affectedRules,
                                List<RuleImpactObject> affectedPathways,
                                List<RuleImpactObject> inPathPatients,
                                List<RuleImpactObject> syncTargets) {
        return digestText(String.join("|",
            rule.tenantId(), rule.ruleId(), version.versionId(), rule.riskLevel().name(), status,
            impactObjectSignature(affectedRules),
            impactObjectSignature(affectedPathways),
            impactObjectSignature(inPathPatients),
            impactObjectSignature(syncTargets),
            String.join(";", unavailable)));
    }

    private String impactObjectSignature(List<RuleImpactObject> objects) {
        return objects.stream()
            .map(object -> String.join(":",
                object.objectType(), object.objectId(), object.displayName(), object.impactReason()))
            .sorted()
            .toList()
            .toString();
    }

    private boolean requiresImpact(RuleDefinition rule) {
        return rule.riskLevel() == RuleRiskLevel.HIGH || rule.riskLevel() == RuleRiskLevel.CRITICAL;
    }

    private void ensureDraft(RuleDefinition rule) {
        if (rule.status() != RuleDefinitionStatus.DRAFT) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "仅草稿规则允许当前操作: " + rule.ruleId());
        }
    }

    private boolean triggerMatches(RuleVersion version, String triggerPoint) {
        String trigger = readJson(version.dslJson()).path("trigger").asText(null);
        return triggerPoint == null || triggerPoint.equals(trigger);
    }

    private void validateDsl(JsonNode dsl) {
        evaluator.evaluate(dsl, json.createObjectNode());
        if (dsl.path("trigger").asText(null) == null || dsl.path("trigger").asText().isBlank()) {
            throw new ApiException(ErrorCode.RULE_DSL_INVALID, "规则 DSL 缺少 trigger");
        }
        if (!dsl.has("then") || !dsl.has("explain")) {
            throw new ApiException(ErrorCode.RULE_DSL_INVALID, "规则 DSL 缺少 then 或 explain");
        }
    }

    private void ensureRuleCodeAvailable(String tenantId, String ruleCode, String currentRuleId) {
        var existing = definitions.findByTenantIdAndRuleCode(tenantId, ruleCode);
        if (existing != null && existing.isPresent()
                && (currentRuleId == null || !currentRuleId.equals(existing.get().ruleId()))) {
            throw new ApiException(ErrorCode.CONFLICT, "同租户下规则编码已存在: " + ruleCode);
        }
    }

    private RuleDefinition findRule(String ruleId, String tenantId) {
        return definitions.findByRuleIdAndTenantId(ruleId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "规则不存在: " + ruleId));
    }

    private RuleDefinition findEffectiveRule(String ruleId, String tenantId) {
        return definitions.findByRuleIdAndTenantId(ruleId, tenantId)
            .or(() -> findPlatformRuleForTenant(ruleId, tenantId))
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "规则不存在: " + ruleId));
    }

    private Optional<RuleDefinition> findPlatformRuleForTenant(String ruleId, String tenantId) {
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            return Optional.empty();
        }
        return definitions.findByRuleIdAndTenantId(ruleId, PlatformTenant.ID)
            .map(platformRule -> definitions.findByTenantIdAndRuleCode(tenantId, platformRule.ruleCode())
                .orElse(platformRule));
    }

    private List<RuleDefinition> effectivePublishedRules(String tenantId) {
        LinkedHashMap<String, RuleDefinition> byCode = new LinkedHashMap<>();
        definitions.findPublishedByTenantId(tenantId).forEach(rule -> byCode.put(rule.ruleCode(), rule));
        if (!PlatformTenant.isPlatformTenant(tenantId)) {
            definitions.findPublishedByTenantId(PlatformTenant.ID)
                .forEach(rule -> byCode.putIfAbsent(rule.ruleCode(), rule));
        }
        return List.copyOf(byCode.values());
    }

    private List<RuleDefinition> effectiveRulesByFilter(String tenantId, String status, String ruleType, String riskLevel) {
        LinkedHashMap<String, RuleDefinition> byCode = new LinkedHashMap<>();
        definitions.listByFilter(tenantId, status, ruleType, riskLevel)
            .forEach(rule -> byCode.put(rule.ruleCode(), rule));
        if (!PlatformTenant.isPlatformTenant(tenantId)) {
            String platformStatus = status == null ? RuleDefinitionStatus.PUBLISHED.name() : status;
            if (RuleDefinitionStatus.PUBLISHED.name().equals(platformStatus)) {
                definitions.listByFilter(PlatformTenant.ID, platformStatus, ruleType, riskLevel)
                    .forEach(rule -> byCode.putIfAbsent(rule.ruleCode(), rule));
            }
        }
        return List.copyOf(byCode.values());
    }

    private List<RuleDefinition> slice(List<RuleDefinition> rows, int offset, int limit) {
        if (rows.isEmpty() || offset >= rows.size()) {
            return List.of();
        }
        int end = Math.min(rows.size(), offset + limit);
        return rows.subList(offset, end);
    }

    private RuleVersion findVersion(String versionId, String tenantId) {
        if (versionId == null || versionId.isBlank()) {
            throw new ApiException(ErrorCode.ENG_RULE_003, "规则未绑定当前版本");
        }
        return versions.findByVersionIdAndTenantId(versionId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_003, "规则版本不存在: " + versionId));
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private JsonNode readJson(String source) {
        try {
            return json.readTree(source);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则 JSON 解析失败", exception);
        }
    }

    private JsonNode readJsonOrArray(String source) {
        if (source == null || source.isBlank()) {
            return json.createArrayNode();
        }
        return readJson(source);
    }

    private JsonNode readJsonOrObject(String source) {
        if (source == null || source.isBlank()) {
            return json.createObjectNode();
        }
        return readJson(source);
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        return writeObject(node);
    }

    private String writeObject(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_RULE_005, "规则结果序列化失败", exception);
        }
    }

    private String digest(JsonNode context) {
        return digestText(writeJson(context));
    }

    private String digestText(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ErrorCode.ENG_RULE_005, "规则输入摘要计算失败", exception);
        }
    }

    private RuleDefinition copyRule(RuleDefinition source, RuleDefinitionStatus status,
                                    String activeVersionId, Instant updatedAt,
                                    String updatedBy, String traceId) {
        return new RuleDefinition(
            source.id(), source.ruleId(), source.tenantId(), source.ruleCode(),
            source.name(), source.ruleType(), source.authoringMode(), source.riskLevel(),
            status, activeVersionId, source.packageVersion(), source.applicableOrgUnitId(),
            source.createdAt(), source.createdBy(), updatedAt, updatedBy, traceId);
    }

    private RuleVersion copyVersion(RuleVersion source, RuleVersionStatus status,
                                    Instant publishedAt, String publishedBy,
                                    Instant updatedAt, String updatedBy, String traceId) {
        return new RuleVersion(
            source.id(), source.versionId(), source.tenantId(), source.ruleId(), source.versionNo(),
            source.sourceRef(), source.changeSummary(), source.dslJson(), source.explanationJson(),
            status, publishedAt, publishedBy, source.rollbackVersionId(),
            source.createdAt(), source.createdBy(), updatedAt, updatedBy, traceId);
    }

    private RuleTestCase copyTestCaseResult(RuleTestCase source, boolean actualHit,
                                            RuleTestCaseStatus status, String message) {
        Instant now = Instant.now();
        return new RuleTestCase(
            source.id(), source.caseId(), source.tenantId(), source.ruleId(), source.versionId(),
            source.caseType(), source.inputPayload(), source.expectedHit(), source.expectedSeverity(),
            source.expectedActionCode(), actualHit, status, message, now,
            source.createdAt(), source.createdBy(), now, RequestContext.currentUserId().orElse("system"),
            RequestContext.currentTraceId());
    }
}
