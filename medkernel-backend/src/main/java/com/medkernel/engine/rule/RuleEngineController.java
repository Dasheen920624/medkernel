package com.medkernel.engine.rule;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 规则引擎 REST 入口（GA-ENG-API-05 {@code /api/v1/engine/rule/**}）。
 *
 * <p>承担规则定义、版本、测试用例、试运行、影响分析、治理、真实执行与解释的 HTTP 合同；
 * 权限由 {@code @PreAuthorize} 强校验 {@code rule.read}/{@code rule.write}/{@code rule.publish}，
 * 租户隔离由类级 {@link DataScope}{@code (requireTenant=true)} 兜底。
 */
@RestController
@RequestMapping("/api/v1/engine/rule")
@DataScope(requireTenant = true)
public class RuleEngineController {

    private final RuleEngineService service;

    /**
     * 注入规则引擎应用服务，控制器仅负责 HTTP 合同和权限入口编排。
     */
    public RuleEngineController(RuleEngineService service) {
        this.service = service;
    }

    /**
     * 创建规则定义并生成初始草稿版本。
     *
     * <p>权限：{@code rule.write}；DSL 必须可解析且包含 when/then/explain，触发点由
     * 资产版本触发绑定维护，否则抛
     * {@link com.medkernel.shared.api.error.ApiException} 错误码 {@code ENG-RULE-001}。
     */
    @PostMapping("/rules")
    @PreAuthorize("@perm.has('rule.write')")
    public ResponseEntity<ApiResult<RuleCreateResponse>> create(@RequestBody @Valid RuleCreateRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.createRule(request)));
    }

    /**
     * 按状态、类型、风险级别分页查询规则定义。
     *
     * <p>权限：{@code rule.read}；过滤参数全部可选，{@code null} 表示不过滤。
     */
    @GetMapping("/rules")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<PageResponse<RuleDefinition>> list(
            @RequestParam(required = false) RuleDefinitionStatus status,
            @RequestParam(required = false) RuleType ruleType,
            @RequestParam(required = false) RuleRiskLevel riskLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.list(
            new RuleFilter(status, ruleType, riskLevel, keyword),
            new PageRequest(page, size, sort)));
    }

    /**
     * 查看指定规则的定义、当前版本及测试用例覆盖情况。
     *
     * <p>权限：{@code rule.read}；规则不存在抛错误码 {@code ENG-RULE-002}。
     */
    @GetMapping("/rules/{ruleId}")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<RuleDetailResponse> detail(@PathVariable String ruleId) {
        return ApiResult.ok(service.detail(ruleId));
    }

    /**
     * 更新草稿规则定义和当前草稿版本。
     */
    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("@perm.has('rule.write')")
    public ApiResult<RuleDetailResponse> update(@PathVariable String ruleId,
                                                @RequestBody @Valid RuleUpdateRequest request) {
        validateContext(request);
        return ApiResult.ok(service.updateRule(ruleId, request));
    }

    /**
     * 将已全量运行的规则复制为同一稳定编码的下一版草稿。
     */
    @PostMapping("/rules/{ruleId}/versions")
    @PreAuthorize("@perm.has('rule.write')")
    public ResponseEntity<ApiResult<RuleVersionCreateResponse>> createNextVersion(
            @PathVariable String ruleId,
            @RequestBody @Valid RuleOperationRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.createNextVersion(ruleId)));
    }

    /**
     * 新增规则测试用例（仅草稿状态可加）。
     *
     * <p>权限：{@code rule.write}；规则状态不为 {@code DRAFT} 时抛错误码 {@code ENG-RULE-006}。
     */
    @PostMapping("/rules/{ruleId}/test-cases")
    @PreAuthorize("@perm.has('rule.write')")
    public ResponseEntity<ApiResult<RuleTestCaseResponse>> addTestCase(
            @PathVariable String ruleId,
            @RequestBody @Valid RuleTestCaseRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.addTestCase(ruleId, request)));
    }

    /**
     * 执行当前版本全部测试用例，不推进发布状态。
     */
    @PostMapping("/rules/{ruleId}/test")
    @PreAuthorize("@perm.has('rule.write')")
    public ApiResult<RuleTestRunResponse> test(@PathVariable String ruleId,
                                               @RequestBody @Valid RuleOperationRequest request) {
        validateContext(request);
        return ApiResult.ok(service.runTests(ruleId));
    }

    /**
     * 用指定上下文试运行执行该规则的当前版本。
     *
     * <p>权限：{@code rule.write}；试运行同样写 {@code rule_execution_log}，便于回放与诊断。
     */
    @PostMapping("/rules/{ruleId}/simulate")
    @PreAuthorize("@perm.has('rule.write')")
    public ApiResult<RuleEvaluationItem> simulate(
            @PathVariable String ruleId,
            @RequestBody @Valid RuleSimulateRequest request) {
        validateContext(request);
        return ApiResult.ok(service.simulate(ruleId, request));
    }

    /**
     * 返回发布前影响分析。当前仅返回关系库可真实证明的对象，未知跨域范围显式标记。
     */
    @GetMapping("/rules/{ruleId}/impact")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<RuleImpactResponse> impact(@PathVariable String ruleId) {
        return ApiResult.ok(service.impact(ruleId));
    }

    /**
     * 按七阶段闭集推进规则治理状态。
     */
    @PostMapping("/rules/{ruleId}/governance/transitions")
    @PreAuthorize("@perm.hasAny('rule.publish','rule.write')")
    public ApiResult<RuleGovernanceResponse> transitionGovernance(
            @PathVariable String ruleId,
            @RequestBody @Valid RuleGovernanceTransitionRequest request) {
        validateContext(request);
        return ApiResult.ok(service.transitionGovernance(ruleId, request));
    }

    /**
     * 按触发点和上下文执行所有匹配且统一版本已发布的规则。
     *
     * <p>权限：{@code rule.read}；返回命中明细、最高严重度与 traceId，供临床嵌入提示消费。
     */
    @PostMapping("/rules/evaluate")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<RuleEvaluateResponse> evaluate(@RequestBody @Valid RuleEvaluateRequest request) {
        validateContext(request);
        return ApiResult.ok(service.evaluate(request));
    }

    /**
     * 分页读取当前租户的规则执行目录，供解释回放选择。
     */
    @GetMapping("/rules/executions")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<PageResponse<RuleExecutionSummaryResponse>> listExecutions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResult.ok(service.listExecutions(new PageRequest(page, size, "executedAt,desc")));
    }

    /**
     * 查看单条规则的影子运行命中、未命中与误报统计。
     */
    @GetMapping("/rules/{ruleId}/shadow-stats")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<RuleShadowStatsResponse> shadowStats(@PathVariable String ruleId) {
        return ApiResult.ok(service.shadowStats(ruleId));
    }

    /**
     * 基于当前规则版本的金标准样本运行历史回测。
     */
    @PostMapping("/rules/{ruleId}/backtest")
    @PreAuthorize("@perm.has('rule.write')")
    public ApiResult<RuleBacktestResponse> runBacktest(
            @PathVariable String ruleId,
            @RequestBody(required = false) @Valid RuleBacktestRequest request) {
        return ApiResult.ok(service.runBacktest(ruleId, request));
    }

    /**
     * 查看当前规则最近一次历史回测结果。
     */
    @GetMapping("/rules/{ruleId}/backtest/latest")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<RuleBacktestResponse> latestBacktest(@PathVariable String ruleId) {
        return ApiResult.ok(service.latestBacktest(ruleId));
    }

    /**
     * 基于生产执行窗口记录上线后漂移快照。
     */
    @PostMapping("/rules/{ruleId}/drift")
    @PreAuthorize("@perm.has('rule.write')")
    public ApiResult<RuleDriftSnapshotResponse> captureDriftSnapshot(
            @PathVariable String ruleId,
            @RequestBody @Valid RuleDriftSnapshotRequest request) {
        return ApiResult.ok(service.captureDriftSnapshot(ruleId, request));
    }

    /**
     * 查看当前规则最近一次漂移监测结果。
     */
    @GetMapping("/rules/{ruleId}/drift/latest")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<RuleDriftSnapshotResponse> latestDriftSnapshot(@PathVariable String ruleId) {
        return ApiResult.ok(service.latestDriftSnapshot(ruleId));
    }

    /**
     * 查看一次规则执行的客户面解释响应（命中链、动作、解释快照等）。
     *
     * <p>权限：{@code rule.read}；执行记录不存在抛错误码 {@code ENG-RULE-002}。
     */
    @GetMapping("/rules/executions/{executionId}/explain")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<RuleExplanationResponse> explain(@PathVariable String executionId) {
        return ApiResult.ok(service.explain(executionId));
    }

    /**
     * 为真实命中的阻断或强提醒动作记录人工越权理由。
     */
    @PostMapping("/rules/executions/{executionId}/override")
    @PreAuthorize("@perm.has('rule.override')")
    public ApiResult<RuleOverrideResponse> captureOverride(
            @PathVariable String executionId,
            @RequestBody @Valid RuleOverrideRequest request) {
        return ApiResult.ok(service.captureOverride(executionId, request));
    }

    /**
     * 为影子运行命中记录人工复核结论，作为误报统计来源。
     */
    @PostMapping("/rules/executions/{executionId}/shadow-feedback")
    @PreAuthorize("@perm.has('rule.write')")
    public ApiResult<RuleShadowFeedbackResponse> captureShadowFeedback(
            @PathVariable String executionId,
            @RequestBody @Valid RuleShadowFeedbackRequest request) {
        return ApiResult.ok(service.captureShadowFeedback(executionId, request));
    }

    private void validateContext(RuleContextRequest request) {
        request.apiContext().validateTenant(RequestContext.currentOrgScope().tenantId());
    }
}
