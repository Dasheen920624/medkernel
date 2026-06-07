package com.medkernel.engine.rule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleasePlan;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.engine.security.AuthenticatedRoleGuard;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.engine.terminology.TerminologyCoverageGate;
import com.medkernel.engine.terminology.TerminologyCoverageIssue;
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
 *   <li>真实执行：按触发点匹配统一版本已激活规则集合，返回命中明细 + 最高严重度；</li>
 *   <li>诊断：基于 {@code execution_id} 装配 {@link DiagnoseResponse}。</li>
 * </ul>
 * 所有写操作触发审计事件 {@link AuditRecorder} 与状态迁移记录 {@link StateTransitionRecorder}。
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
    private final RuleOverrideLogRepository overrides;
    private final RuleShadowFeedbackRepository shadowFeedback;
    private final RuleDslEvaluator evaluator;
    private final RuleApplicabilityService applicabilityService;
    private final AuditRecorder auditRecorder;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final ObjectMapper json;
    private final RuleImpactIndex impactIndex;
    private final TerminologyCoverageGate terminologyCoverageGate;
    private final RuleVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;
    private final RuleGovernanceService governanceService;
    private final InheritanceResolver inheritanceResolver;
    private final ContextSnapshotService contextSnapshots;
    private final RuleConflictDetector conflictDetector = new RuleConflictDetector();

    private enum RuleRuntimeMode {
        ACTIVE,
        SHADOW,
        INACTIVE
    }

    private record RuleRuntimeCandidate(
        RuleDefinition rule,
        RuleVersion version,
        RuleRuntimeMode mode
    ) {}

    /**
     * 注入规则引擎所需仓库、DSL 执行器、审计发布器、状态记录器与 JSON 处理器。
     */
    @Autowired
    public RuleEngineService(RuleDefinitionRepository definitions,
                             RuleVersionRepository versions,
                             RuleTestCaseRepository testCases,
                             RuleExecutionLogRepository executions,
                             RuleOverrideLogRepository overrides,
                             RuleDslEvaluator evaluator,
                             RuleApplicabilityService applicabilityService,
                             AuditRecorder auditRecorder,
                             StateTransitionRecorder transitions,
                             DiagnoseResponseAssembler diagnoseAssembler,
                             ObjectMapper json,
                             ObjectProvider<RuleImpactIndex> impactIndexProvider,
                             ObjectProvider<TerminologyCoverageGate> terminologyCoverageGateProvider,
                             RuleVersionedAssetAdapter versionedAssets,
                             AssetVersionRepository assetVersions,
                             ReleasePort releasePort,
                             RuleGovernanceService governanceService,
                             RuleShadowFeedbackRepository shadowFeedback,
                             InheritanceResolver inheritanceResolver,
                             ContextSnapshotService contextSnapshots) {
        this(definitions, versions, testCases, executions, overrides,
            evaluator, applicabilityService,
            auditRecorder, transitions,
            diagnoseAssembler, json, impactIndexProvider.getIfAvailable(RuleImpactIndex::empty),
            terminologyCoverageGateProvider.getIfAvailable(TerminologyCoverageGate::noop),
            versionedAssets, assetVersions, releasePort, governanceService, shadowFeedback,
            inheritanceResolver, contextSnapshots);
    }

    RuleEngineService(RuleDefinitionRepository definitions,
                      RuleVersionRepository versions,
                      RuleTestCaseRepository testCases,
                      RuleExecutionLogRepository executions,
                      RuleOverrideLogRepository overrides,
                      RuleDslEvaluator evaluator,
                      RuleApplicabilityService applicabilityService,
                      AuditRecorder auditRecorder,
                      StateTransitionRecorder transitions,
                      DiagnoseResponseAssembler diagnoseAssembler,
                      ObjectMapper json,
                      RuleImpactIndex impactIndex,
                      TerminologyCoverageGate terminologyCoverageGate,
                      RuleVersionedAssetAdapter versionedAssets,
                      AssetVersionRepository assetVersions,
                      ReleasePort releasePort,
                      RuleGovernanceService governanceService,
                      RuleShadowFeedbackRepository shadowFeedback,
                      InheritanceResolver inheritanceResolver,
                      ContextSnapshotService contextSnapshots) {
        this.definitions = definitions;
        this.versions = versions;
        this.testCases = testCases;
        this.executions = executions;
        this.overrides = overrides;
        this.shadowFeedback = Objects.requireNonNull(
            shadowFeedback, "规则影子运行反馈仓库不能为空");
        this.evaluator = evaluator;
        this.applicabilityService = Objects.requireNonNull(
            applicabilityService, "规则适用域服务不能为空");
        this.auditRecorder = auditRecorder;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.json = json;
        this.impactIndex = impactIndex == null ? RuleImpactIndex.empty() : impactIndex;
        this.terminologyCoverageGate = terminologyCoverageGate == null
            ? TerminologyCoverageGate.noop()
            : terminologyCoverageGate;
        this.versionedAssets = Objects.requireNonNull(versionedAssets, "规则统一版本适配器不能为空");
        this.assetVersions = Objects.requireNonNull(assetVersions, "统一资产版本仓库不能为空");
        this.releasePort = Objects.requireNonNull(releasePort, "统一发布端口不能为空");
        this.governanceService = Objects.requireNonNull(governanceService, "规则治理服务不能为空");
        this.inheritanceResolver = Objects.requireNonNull(inheritanceResolver, "继承解析器不能为空");
        this.contextSnapshots = Objects.requireNonNull(contextSnapshots, "标准上下文快照服务不能为空");
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
            request.priority() == null ? 100 : request.priority(),
            trimToNull(request.suppressedBy()),
            request.dedupeWindowSeconds() == null ? 0 : request.dedupeWindowSeconds(),
            RuleDefinitionStatus.DRAFT, versionId, request.packageVersion(), request.applicableOrgUnitId(),
            now, actor, now, actor, traceId);
        RuleVersion version = new RuleVersion(
            null, versionId, tenantId, ruleId, 1, request.sourceRef(), request.changeSummary(),
            writeJson(request.dsl()), writeJson(request.explanation()),
            RuleVersionStatus.DRAFT, null, null, null, now, actor, now, actor, traceId);

        definitions.save(definition);
        versions.save(version);
        governanceService.initialize(
            tenantId, versionId, definition.riskLevel(), actor, traceId);
        applicabilityService.saveMirror(version, request.dsl(), now, actor, traceId);
        versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.RULE,
            definition.ruleCode(),
            String.valueOf(version.versionNo()),
            releaseOrgScope(definition),
            releaseApplicableScope(definition),
            ruleAssetContent(definition, version),
            null,
            version.sourceRef(),
            actor,
            traceId,
            safetyPolicy(definition),
            null
        ));
        transitions.record(RULE_ENTITY, ruleId, null, RuleDefinitionStatus.DRAFT.name(), "CREATE_RULE", null);
        auditRecorder.record(AuditAction.CREATE, RULE_ENTITY, ruleId, "创建规则 " + request.ruleCode());
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
        ensureGovernanceDraft(tenantId, version.versionId());
        validateDsl(request.dsl());
        ensureRuleCodeAvailable(tenantId, request.ruleCode(), ruleId);

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        RuleDefinition updatedRule = new RuleDefinition(
            rule.id(), rule.ruleId(), rule.tenantId(), request.ruleCode(), request.name(),
            request.ruleType(), request.authoringMode() == null ? RuleAuthoringMode.DSL : request.authoringMode(),
            request.riskLevel() == null ? RuleRiskLevel.MEDIUM : request.riskLevel(),
            request.priority() == null ? 100 : request.priority(),
            trimToNull(request.suppressedBy()),
            request.dedupeWindowSeconds() == null ? 0 : request.dedupeWindowSeconds(),
            RuleDefinitionStatus.DRAFT, rule.activeVersionId(), request.packageVersion(),
            request.applicableOrgUnitId(), rule.createdAt(), rule.createdBy(), now, actor,
            RequestContext.currentTraceId());
        RuleVersion updatedVersion = new RuleVersion(
            version.id(), version.versionId(), version.tenantId(), version.ruleId(), version.versionNo(),
            request.sourceRef(), request.changeSummary(), writeJson(request.dsl()), writeJson(request.explanation()),
            RuleVersionStatus.DRAFT, version.publishedAt(), version.publishedBy(), version.rollbackVersionId(),
            version.createdAt(), version.createdBy(), now, actor, RequestContext.currentTraceId());

        AssetVersion assetVersion = requireRuleAssetVersion(rule, version);
        definitions.save(updatedRule);
        versions.save(updatedVersion);
        applicabilityService.saveMirror(
            updatedVersion, request.dsl(), now, actor, RequestContext.currentTraceId());
        AssetVersion updatedAssetVersion = versionedAssets.updateDraft(new AssetVersionDraftUpdateCommand(
            tenantId,
            assetVersion.versionId(),
            updatedRule.ruleCode(),
            releaseOrgScope(updatedRule),
            releaseApplicableScope(updatedRule),
            ruleAssetContent(updatedRule, updatedVersion),
            null,
            updatedVersion.sourceRef(),
            safetyPolicy(updatedRule),
            assetVersion.overridePolicy(),
            actor
        ));
        transitions.record(RULE_ENTITY, ruleId, rule.status().name(), RuleDefinitionStatus.DRAFT.name(),
            "UPDATE_RULE", null);
        auditRecorder.record(AuditAction.UPDATE, RULE_ENTITY, ruleId, "更新规则 " + request.ruleCode());
        return new RuleDetailResponse(
            updatedRule, updatedVersion,
            testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId),
            updatedAssetVersion.status(),
            governanceSnapshot(updatedRule, updatedVersion, List.of(), null, null, List.of()));
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
        AssetVersion assetVersion = requireRuleAssetVersion(rule, version);
        return new RuleDetailResponse(
            rule,
            version,
            testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId),
            assetVersion.status(),
            governanceSnapshot(rule, version, List.of(), null, null, List.of()));
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
        ensureGovernanceDraft(tenantId, version.versionId());
        ContextSnapshotResponse snapshot = contextSnapshots.findById(request.contextSnapshotId());
        if (snapshot.status() != ContextSnapshotStatus.ACTIVE || snapshot.resources() == null) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "规则测试用例只能固化 ACTIVE 标准上下文快照");
        }
        String caseId = "rtc-" + UUID.randomUUID();
        RuleTestCase saved = testCases.save(new RuleTestCase(
            null, caseId, tenantId, ruleId, version.versionId(), request.caseType(),
            snapshot.snapshotId(), writeObject(snapshot.resources()), request.expectedHit(), request.expectedSeverity(),
            request.expectedActionCode(), null, RuleTestCaseStatus.NOT_RUN, null, null,
            now, actor, now, actor, traceId));
        auditRecorder.record(AuditAction.UPDATE, RULE_ENTITY, ruleId, "新增规则测试用例 " + saved.caseId());
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
     * 记录同行评审或临床委员会会签。
     */
    @Transactional
    public RuleGovernanceResponse signoffGovernance(String ruleId, RuleSignoffRequest request) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        String actor = RequestContext.currentUserId().orElse("system");
        RoleCode role = authenticatedSignoffRole();
        RuleGovernance updated = governanceService.recordSignoff(
            tenantId,
            version.versionId(),
            request.stage(),
            request.decision(),
            request.reason(),
            actor,
            role,
            RequestContext.currentTraceId()
        );
        List<String> releaseEvidence = new ArrayList<>();
        String impactDigest = null;
        String impactStatus = null;
        if (updated.state() == RuleGovernanceState.DRAFT
                && request.decision() == RuleSignoffDecision.REJECTED) {
            RuleImpactResponse impact = impactFor(rule, version);
            impactDigest = impact.impactDigest();
            impactStatus = impact.analysisStatus();
            appendEvidence(
                releaseEvidence,
                releasePort.rejectReview(
                    governanceReleaseCommand(
                        rule,
                        version,
                        impact,
                        request.reason(),
                        actor
                    )
                )
            );
        }
        auditRecorder.record(
            AuditAction.UPDATE,
            RULE_ENTITY,
            ruleId,
            "规则治理签署 " + request.stage() + " / " + request.decision()
        );
        return governanceSnapshot(
            rule,
            version,
            List.of(),
            impactDigest,
            impactStatus,
            releaseEvidence
        );
    }

    /**
     * 按八阶段闭集推进规则治理状态，发布端口只执行当前阶段对应的一步。
     */
    @Transactional
    public RuleGovernanceResponse transitionGovernance(
            String ruleId,
            RuleGovernanceTransitionRequest request) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        RuleGovernance current = governanceService.requireGovernance(tenantId, version.versionId());
        RuleGovernanceState target = request.targetState();
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        RuleImpactResponse impact = impactFor(rule, version);
        List<RuleTestCaseResult> testResults = List.of();
        List<String> releaseEvidence = new ArrayList<>();

        if (current.state() == RuleGovernanceState.DRAFT
                && target == RuleGovernanceState.PEER_REVIEW) {
            ensureDraft(rule);
            validateGovernanceImpact(rule, request, impact);
            List<RuleTestCase> cases =
                testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId);
            ensureCoverage(cases);
            ensureTerminologyCoverage(version);
            ensureSuppressionContract(rule, version, false);
            ensureNoStaticConflicts(rule, version);
            testResults = cases.stream().map(testCase -> runTestCase(version, testCase)).toList();
            if (testResults.stream().anyMatch(result -> result.status() != RuleTestCaseStatus.PASS)) {
                throw new ApiException(ErrorCode.ENG_RULE_004, "规则测试用例未全部通过");
            }
        } else if (target == RuleGovernanceState.SHADOW
                || target == RuleGovernanceState.CANARY
                || target == RuleGovernanceState.FULL) {
            validateGovernanceImpact(rule, request, impact);
        }
        ensureGovernanceReleaseCoordinator(current, target);

        RuleGovernance updated = governanceService.transition(
            tenantId,
            version.versionId(),
            target,
            request.reason(),
            actor,
            traceId
        );
        if (target == RuleGovernanceState.PEER_REVIEW) {
            appendEvidence(
                releaseEvidence,
                releasePort.submitForReview(
                    governanceReleaseCommand(rule, version, impact, request.reason(), actor)
                )
            );
        } else if (target == RuleGovernanceState.SHADOW) {
            appendEvidence(
                releaseEvidence,
                releasePort.approveForSilentObservation(
                    governanceReleaseCommand(rule, version, impact, request.reason(), actor)
                )
            );
            Instant now = Instant.now();
            versions.save(copyVersion(
                version, RuleVersionStatus.PUBLISHED, now, actor, now, actor, traceId));
            definitions.save(copyRule(
                rule, RuleDefinitionStatus.PUBLISHED, version.versionId(), now, actor, traceId));
        } else if (target == RuleGovernanceState.CANARY) {
            appendEvidence(
                releaseEvidence,
                releasePort.releaseGray(
                    governanceReleaseCommand(rule, version, impact, request.reason(), actor)
                )
            );
        } else if (target == RuleGovernanceState.FULL) {
            ensureSuppressionContract(rule, version, true);
            appendEvidence(
                releaseEvidence,
                releasePort.releaseFull(
                    governanceReleaseCommand(rule, version, impact, request.reason(), actor)
                )
            );
        } else if (target == RuleGovernanceState.RETIRED) {
            retireRule(rule, version, actor, traceId);
        }

        transitions.record(
            RULE_ENTITY,
            ruleId,
            current.state().name(),
            updated.state().name(),
            "TRANSITION_RULE_GOVERNANCE",
            null
        );
        auditRecorder.record(
            target == RuleGovernanceState.RETIRED ? AuditAction.UPDATE : AuditAction.PUBLISH,
            RULE_ENTITY,
            ruleId,
            "规则治理推进至 " + target
        );
        return governanceSnapshot(
            rule,
            version,
            testResults,
            impact.impactDigest(),
            impact.analysisStatus(),
            releaseEvidence
        );
    }

    private void validateGovernanceImpact(
            RuleDefinition rule,
            RuleGovernanceTransitionRequest request,
            RuleImpactResponse impact) {
        if (request == null
                || request.impactDigest() == null
                || !request.impactDigest().equals(impact.impactDigest())
                || request.reason() == null
                || request.reason().isBlank()) {
            String prefix = requiresImpact(rule) ? "高危规则" : "规则";
            throw new ApiException(
                ErrorCode.ENG_RULE_004,
                prefix + "治理推进必须提交当前影响摘要和确认说明"
            );
        }
    }

    private VersionReleaseCommand governanceReleaseCommand(
            RuleDefinition rule,
            RuleVersion version,
            RuleImpactResponse impact,
            String reason,
            String actor) {
        AssetVersion assetVersion = requireRuleAssetVersion(rule, version);
        return new VersionReleaseCommand(
            rule.tenantId(),
            VersionedAssetType.RULE,
            rule.ruleCode(),
            assetVersion.versionId(),
            releaseOrgScope(rule),
            releaseApplicableScope(rule),
            null,
            null,
            impact.impactDigest(),
            reason.trim(),
            authenticatedRoleCodes(),
            actor,
            RequestContext.currentTraceId()
        );
    }

    private RuleGovernanceResponse governanceSnapshot(
            RuleDefinition rule,
            RuleVersion version,
            List<RuleTestCaseResult> testResults,
            String impactDigest,
            String impactStatus,
            List<String> releaseEvidence) {
        RuleGovernance governance =
            governanceService.requireGovernance(rule.tenantId(), version.versionId());
        List<RuleSignoff> signoffs =
            governanceService.signoffs(rule.tenantId(), version.versionId());
        int committeeApprovalCount = (int) signoffs.stream()
            .filter(signoff -> signoff.reviewRound() == governance.reviewRound())
            .filter(signoff -> signoff.stage() == RuleSignoffStage.COMMITTEE)
            .filter(signoff -> signoff.decision() == RuleSignoffDecision.APPROVED)
            .map(RuleSignoff::signerId)
            .distinct()
            .count();
        return new RuleGovernanceResponse(
            rule.ruleId(),
            version.versionId(),
            governance.state(),
            governance.requiredSignoffs(),
            governance.reviewRound(),
            committeeApprovalCount,
            governance.authorId(),
            governance.lastReason(),
            signoffs,
            testResults,
            impactDigest,
            impactStatus,
            releaseEvidence,
            RequestContext.currentTraceId()
        );
    }

    private void retireRule(RuleDefinition rule, RuleVersion version, String actor, String traceId) {
        Instant now = Instant.now();
        definitions.save(copyRule(
            rule, RuleDefinitionStatus.ARCHIVED, version.versionId(), now, actor, traceId));
        versions.save(copyVersion(
            version,
            RuleVersionStatus.ARCHIVED,
            version.publishedAt(),
            version.publishedBy(),
            now,
            actor,
            traceId
        ));
        AssetVersion assetVersion = requireRuleAssetVersion(rule, version);
        assetVersions.save(assetVersion.withStatusAndWindow(
            AssetVersionStatus.ARCHIVED,
            "version:" + assetVersion.versionId(),
            assetVersion.effectiveFrom(),
            now,
            now,
            actor
        ));
    }

    private static RoleCode authenticatedSignoffRole() {
        return List.of(
                RoleCode.MEDICAL_AFFAIRS,
                RoleCode.QA_MANAGER,
                RoleCode.INSURANCE_MANAGER,
                RoleCode.DEPT_HEAD,
                RoleCode.SPECIALIST
            ).stream()
            .filter(AuthenticatedRoleGuard::has)
            .findFirst()
            .orElseThrow(() -> new ApiException(
                ErrorCode.FORBIDDEN,
                "当前登录角色无权执行临床规则签署"
            ));
    }

    private static void ensureGovernanceReleaseCoordinator(
            RuleGovernance current,
            RuleGovernanceState target) {
        if (current.state() == RuleGovernanceState.DRAFT
                && target == RuleGovernanceState.PEER_REVIEW) {
            requireAnyRole(
                "提交同行评审仅规则治理创作角色可执行",
                RoleCode.MEDICAL_AFFAIRS,
                RoleCode.DEPT_HEAD,
                RoleCode.INSURANCE_MANAGER,
                RoleCode.SPECIALIST
            );
            return;
        }
        if (target == RuleGovernanceState.FULL) {
            requireAnyRole("规则全量激活仅医院管理员可执行", RoleCode.HOSPITAL_ADMIN);
            return;
        }
        if (target == RuleGovernanceState.SHADOW
                || target == RuleGovernanceState.CANARY
                || target == RuleGovernanceState.MONITOR
                || target == RuleGovernanceState.RETIRED) {
            requireAnyRole(
                "规则影子、灰度、监测和退役仅医务处或医院管理员可执行",
                RoleCode.MEDICAL_AFFAIRS,
                RoleCode.HOSPITAL_ADMIN
            );
        }
    }

    private static void requireAnyRole(String message, RoleCode... allowedRoles) {
        boolean allowed = java.util.Arrays.stream(allowedRoles).anyMatch(AuthenticatedRoleGuard::has);
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN, message);
        }
    }

    private static List<String> authenticatedRoleCodes() {
        return java.util.Arrays.stream(RoleCode.values())
            .filter(AuthenticatedRoleGuard::has)
            .map(RoleCode::code)
            .toList();
    }

    private AssetVersion requireRuleAssetVersion(RuleDefinition rule, RuleVersion version) {
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                rule.tenantId(), VersionedAssetType.RULE, rule.ruleCode(), String.valueOf(version.versionNo()))
            .orElseThrow(() -> new ApiException(
                ErrorCode.CONFLICT,
                "规则缺少统一资产版本，禁止发布: " + rule.ruleCode() + "@" + version.versionNo()
            ));
    }

    private static void appendEvidence(List<String> evidence, VersionReleasePlan plan) {
        if (plan != null && plan.evidenceSummary() != null && !plan.evidenceSummary().isBlank()) {
            evidence.add(plan.evidenceSummary());
        }
    }

    private static AssetVersionSafetyPolicy safetyPolicy(RuleDefinition rule) {
        return rule.riskLevel() == RuleRiskLevel.CRITICAL
            ? AssetVersionSafetyPolicy.SAFETY_REDLINE
            : AssetVersionSafetyPolicy.NORMAL;
    }

    private static String releaseOrgScope(RuleDefinition rule) {
        return notBlank(rule.applicableOrgUnitId(), "tenant:" + rule.tenantId());
    }

    private static String releaseApplicableScope(RuleDefinition rule) {
        return notBlank(rule.packageVersion(), "ALL");
    }

    private static String notBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String ruleAssetContent(RuleDefinition rule, RuleVersion version) {
        return writeObject(new RuleAssetContent(
            rule.ruleCode(),
            rule.name(),
            rule.ruleType(),
            rule.authoringMode(),
            rule.riskLevel(),
            rule.packageVersion(),
            rule.applicableOrgUnitId(),
            version.versionNo(),
            version.sourceRef(),
            version.changeSummary(),
            readJson(version.dslJson()),
            readJsonOrObject(version.explanationJson())
        ));
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
     * 按触发点和上下文执行统一版本已激活规则集合。
     *
     * <p>候选范围：请求未指定 {@code ruleIds} 时取本地和平台统一版本已激活规则，否则取指定规则；
     * 仅 DSL 的 {@code trigger} 与请求 {@code triggerPoint} 匹配的版本参与评估。
     */
    @Transactional
    public RuleEvaluateResponse evaluate(RuleEvaluateRequest request) {
        ContextSnapshotResponse snapshot = contextSnapshots.findById(request.contextSnapshotId());
        if (snapshot.status() != ContextSnapshotStatus.ACTIVE || snapshot.resources() == null) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "规则执行只能使用 ACTIVE 标准上下文快照");
        }
        return evaluateContext(
            request.triggerPoint(),
            json.valueToTree(snapshot.resources()),
            request.eventId(),
            request.ruleIds()
        );
    }

    /**
     * 执行服务内已经完成真实性校验的上下文，供临床事件主链路复用。
     */
    @Transactional
    public RuleEvaluateResponse evaluateContext(String triggerPoint, JsonNode context,
                                                String eventId, List<String> ruleIds) {
        requireCanonicalTrigger(triggerPoint);
        String tenantId = requireCurrentTenant();
        List<String> selectedRuleIds = ruleIds == null ? List.of() : ruleIds;
        List<RuleDefinition> candidates = selectedRuleIds.isEmpty()
            ? effectiveActiveRules(tenantId)
            : selectedRuleIds.stream().map(ruleId -> findEffectiveRule(ruleId, tenantId)).toList();

        List<RuleRuntimeCandidate> executable = candidates.stream()
            .map(rule -> runtimeCandidate(rule, findVersion(rule.activeVersionId(), rule.tenantId())))
            .filter(candidate -> candidate.mode() != RuleRuntimeMode.INACTIVE)
            .filter(candidate -> triggerMatches(candidate.version(), triggerPoint))
            .toList();
        executable = executable.stream()
            .sorted(Comparator
                .<RuleRuntimeCandidate>comparingInt(candidate -> candidate.rule().priority())
                .reversed()
                .thenComparing(candidate -> candidate.rule().ruleCode()))
            .toList();

        Set<String> matchedRuleCodes = new HashSet<>();
        List<RuleEvaluationItem> items = new ArrayList<>();
        for (RuleRuntimeCandidate entry : executable) {
            RuleDefinition rule = entry.rule();
            RuleVersion version = entry.version();
            RuleApplicabilityDecision applicability =
                evaluateApplicability(version, context);
            RuleEvaluationItem item;
            if (!applicability.applicable()) {
                item = recordNotApplicable(
                    rule, version, tenantId, context, triggerPoint, eventId, applicability);
            } else if (isSuppressed(rule, matchedRuleCodes)) {
                item = recordSuppressed(
                    rule, version, tenantId, context, triggerPoint, eventId);
            } else {
                item = evaluateApplicableAndLog(
                    rule, version, tenantId, context, triggerPoint, eventId,
                    entry.mode() == RuleRuntimeMode.SHADOW);
            }
            items.add(item);
            if (item.hit() && entry.mode() == RuleRuntimeMode.ACTIVE) {
                matchedRuleCodes.add(rule.ruleCode());
            }
        }
        RuleRiskLevel highest = items.stream()
            .filter(item -> item.status() == RuleExecutionStatus.SUCCESS)
            .filter(RuleEvaluationItem::hit)
            .map(RuleEvaluationItem::severity)
            .reduce(null, RuleRiskLevel::max);
        List<com.medkernel.engine.cdshook.CdsHookCard> cards = items.stream()
            .filter(item -> item.status() == RuleExecutionStatus.SUCCESS)
            .filter(RuleEvaluationItem::hit)
            .flatMap(item -> java.util.stream.IntStream.range(0, item.actions().size())
                .mapToObj(index -> item.actions().get(index).toCdsHookCard(
                    item.executionId() + "-action-" + (index + 1))))
            .toList();
        return new RuleEvaluateResponse(
            "eval-" + UUID.randomUUID(),
            items,
            highest,
            cards,
            RequestContext.currentTraceId());
    }

    private RuleRuntimeCandidate runtimeCandidate(RuleDefinition rule, RuleVersion version) {
        RuleRuntimeMode mode = assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                rule.tenantId(),
                VersionedAssetType.RULE,
                rule.ruleCode(),
                String.valueOf(version.versionNo()))
            .map(assetVersion -> {
                if (assetVersion.status() == AssetVersionStatus.ACTIVE) {
                    return RuleRuntimeMode.ACTIVE;
                }
                if (assetVersion.status() == AssetVersionStatus.PUBLISHED
                        && governanceService.requireGovernance(
                            rule.tenantId(), version.versionId()).state() == RuleGovernanceState.SHADOW) {
                    return RuleRuntimeMode.SHADOW;
                }
                return RuleRuntimeMode.INACTIVE;
            })
            .orElse(RuleRuntimeMode.INACTIVE);
        return new RuleRuntimeCandidate(rule, version, mode);
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

    /**
     * 分页读取当前租户最近的规则执行目录，供人工选择真实执行记录进行解释回放。
     */
    @Transactional(readOnly = true)
    public PageResponse<RuleExecutionSummaryResponse> listExecutions(PageRequest pageRequest) {
        String tenantId = requireCurrentTenant();
        PageRequest page = pageRequest == null ? PageRequest.defaults() : pageRequest;
        long total = executions.countByTenantId(tenantId);
        List<RuleExecutionSummaryResponse> rows = executions
            .pageByTenantId(tenantId, page.offset(), page.safeSize())
            .stream()
            .map(RuleExecutionSummaryResponse::from)
            .toList();
        return PageResponse.of(rows, page, total);
    }

    /**
     * 统计某规则影子运行命中、未命中与误报复核结果。
     */
    @Transactional(readOnly = true)
    public RuleShadowStatsResponse shadowStats(String ruleId) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        long total = executions.countShadowByRule(tenantId, rule.ruleId());
        long hitCount = executions.countShadowByRuleAndHit(tenantId, rule.ruleId(), true);
        long missCount = executions.countShadowByRuleAndHit(tenantId, rule.ruleId(), false);
        long falsePositiveCount = shadowFeedback.countByTenantIdAndRuleIdAndDecision(
            tenantId, rule.ruleId(), RuleShadowFeedbackDecision.FALSE_POSITIVE);
        double hitRate = total == 0 ? 0.0 : (double) hitCount / total;
        double falsePositiveRate = hitCount == 0 ? 0.0 : (double) falsePositiveCount / hitCount;
        return new RuleShadowStatsResponse(
            rule.ruleId(),
            total,
            hitCount,
            missCount,
            falsePositiveCount,
            hitRate,
            falsePositiveRate,
            RequestContext.currentTraceId()
        );
    }

    private List<RuleTestCaseResult> runTestCases(RuleVersion version, String tenantId) {
        return testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId).stream()
            .map(testCase -> runTestCase(version, testCase))
            .toList();
    }

    private RuleTestCaseResult runTestCase(RuleVersion version, RuleTestCase testCase) {
        try {
            JsonNode dsl = readJson(version.dslJson());
            JsonNode input = readJson(testCase.inputPayload());
            RuleApplicabilityDecision applicability = evaluateApplicability(version, input);
            RuleDslEvaluation evaluation = applicability.applicable()
                ? evaluator.evaluate(dsl, input)
                : notApplicableEvaluation(applicability);
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
        RuleApplicabilityDecision applicability = evaluateApplicability(version, context);
        if (!applicability.applicable()) {
            return recordNotApplicable(
                rule, version, executionTenantId, context, triggerPoint, eventId, applicability);
        }
        return evaluateApplicableAndLog(
            rule, version, executionTenantId, context, triggerPoint, eventId, false);
    }

    private RuleEvaluationItem evaluateApplicableAndLog(
            RuleDefinition rule,
            RuleVersion version,
            String executionTenantId,
            JsonNode context,
            String triggerPoint,
            String eventId,
            boolean shadowMode) {
        RuleDslEvaluation evaluation = evaluator.evaluate(readJson(version.dslJson()), context);
        String executionId = "rex-" + UUID.randomUUID();
        Instant now = Instant.now();
        String patientId = patientId(context);
        String semanticKey = evaluation.hit() ? semanticKey(rule, evaluation.actions()) : null;
        Optional<RuleExecutionLog> duplicate = shadowMode
            ? Optional.empty()
            : recentDuplicate(rule, executionTenantId, patientId, semanticKey, now);
        RuleExecutionStatus status = shadowMode
            ? RuleExecutionStatus.SHADOW_RECORDED
            : duplicate.isPresent()
                ? RuleExecutionStatus.DEDUPLICATED
                : evaluation.hit() ? RuleExecutionStatus.SUCCESS : RuleExecutionStatus.MISS;
        String deduplicatedFromExecutionId = duplicate.map(RuleExecutionLog::executionId).orElse(null);
        JsonNode explanation = shadowMode
            ? shadowExplanation(evaluation.explanation())
            : evaluation.explanation();
        RuleExecutionLog log = executions.save(new RuleExecutionLog(
            null, executionId, executionTenantId, rule.ruleId(), version.versionId(),
            triggerPoint, eventId, RequestContext.currentUserId().orElse(null),
            patientId, encounterId(context), semanticKey,
            digest(context), evaluation.hit(), evaluation.severity(), writeObject(evaluation.actions()),
            writeJson(explanation), status, null, null,
            deduplicatedFromExecutionId, now, now, RequestContext.currentTraceId()));
        transitions.record(
            EXECUTION_ENTITY, log.executionId(), null, status.name(),
            shadowMode ? "RECORD_SHADOW_RULE" : "EXECUTE_RULE", null);
        auditRecorder.record(AuditAction.EXECUTE, EXECUTION_ENTITY, log.executionId(), "执行规则 " + rule.ruleId());
        return new RuleEvaluationItem(
            log.executionId(), rule.ruleId(), version.versionId(), evaluation.hit(),
            evaluation.severity(),
            status == RuleExecutionStatus.SUCCESS ? evaluation.actions() : List.of(),
            explanation, status, null, deduplicatedFromExecutionId);
    }

    private JsonNode shadowExplanation(JsonNode original) {
        var copy = original == null || original.isMissingNode()
            ? json.createObjectNode()
            : original.deepCopy();
        if (copy instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            object.put("shadowMode", true);
            object.put("shadowReason", "影子运行只记录潜在命中，不产生临床动作");
            return object;
        }
        return json.createObjectNode()
            .put("shadowMode", true)
            .put("shadowReason", "影子运行只记录潜在命中，不产生临床动作")
            .set("original", copy);
    }

    private RuleEvaluationItem recordNotApplicable(
            RuleDefinition rule,
            RuleVersion version,
            String executionTenantId,
            JsonNode context,
            String triggerPoint,
            String eventId,
            RuleApplicabilityDecision applicability) {
        String executionId = "rex-" + UUID.randomUUID();
        Instant now = Instant.now();
        JsonNode explanation = applicabilityExplanation(applicability);
        RuleExecutionLog log = executions.save(new RuleExecutionLog(
            null, executionId, executionTenantId, rule.ruleId(), version.versionId(),
            triggerPoint, eventId, RequestContext.currentUserId().orElse(null),
            patientId(context), encounterId(context), null, digest(context), false, null, "[]",
            writeJson(explanation), RuleExecutionStatus.NOT_APPLICABLE, null, null,
            null, now, now, RequestContext.currentTraceId()));
        transitions.record(
            EXECUTION_ENTITY, log.executionId(), null, RuleExecutionStatus.NOT_APPLICABLE.name(),
            "SKIP_RULE_" + applicability.reasonCode(), null);
        auditRecorder.record(
            AuditAction.EXECUTE, EXECUTION_ENTITY, log.executionId(),
            "跳过不适用规则 " + rule.ruleId());
        return new RuleEvaluationItem(
            log.executionId(), rule.ruleId(), version.versionId(), false, null,
            List.of(), explanation, RuleExecutionStatus.NOT_APPLICABLE, null, null);
    }

    private RuleApplicabilityDecision evaluateApplicability(RuleVersion version, JsonNode context) {
        JsonNode dsl = readJson(version.dslJson());
        return applicabilityService.evaluate(
            dsl,
            context,
            RequestContext.currentOrgScope(),
            version.versionId());
    }

    private RuleDslEvaluation notApplicableEvaluation(RuleApplicabilityDecision applicability) {
        return new RuleDslEvaluation(
            false, null, List.of(), applicabilityExplanation(applicability));
    }

    private JsonNode applicabilityExplanation(RuleApplicabilityDecision applicability) {
        var explanation = json.createObjectNode();
        explanation.put("title", "规则不适用于当前上下文");
        explanation.put("reasonCode", applicability.reasonCode());
        explanation.put("reason", applicability.reason());
        explanation.set("applicability", applicability.details());
        return explanation;
    }

    private RuleEvaluationItem recordSuppressed(
            RuleDefinition rule,
            RuleVersion version,
            String executionTenantId,
            JsonNode context,
            String triggerPoint,
            String eventId) {
        String executionId = "rex-" + UUID.randomUUID();
        Instant now = Instant.now();
        JsonNode explanation = json.createObjectNode()
            .put("title", "规则已被高阶规则抑制")
            .put("reason", "本次执行已命中抑制规则 " + rule.suppressedBy())
            .put("suppressedBy", rule.suppressedBy());
        RuleExecutionLog log = executions.save(new RuleExecutionLog(
            null, executionId, executionTenantId, rule.ruleId(), version.versionId(),
            triggerPoint, eventId, RequestContext.currentUserId().orElse(null),
            patientId(context), encounterId(context), null, digest(context), false, null, "[]",
            writeJson(explanation), RuleExecutionStatus.SUPPRESSED, null, null,
            null, now, now, RequestContext.currentTraceId()));
        transitions.record(
            EXECUTION_ENTITY, log.executionId(), null, RuleExecutionStatus.SUPPRESSED.name(),
            "SUPPRESS_RULE_BY_" + rule.suppressedBy(), null);
        auditRecorder.record(
            AuditAction.EXECUTE, EXECUTION_ENTITY, log.executionId(),
            "抑制规则 " + rule.ruleId() + " by " + rule.suppressedBy());
        return new RuleEvaluationItem(
            log.executionId(), rule.ruleId(), version.versionId(), false, null,
            List.of(), explanation, RuleExecutionStatus.SUPPRESSED, rule.suppressedBy(), null);
    }

    private Optional<RuleExecutionLog> recentDuplicate(
            RuleDefinition rule,
            String tenantId,
            String patientId,
            String semanticKey,
            Instant now) {
        if (rule.dedupeWindowSeconds() <= 0 || patientId == null || semanticKey == null) {
            return Optional.empty();
        }
        return executions.findRecentSuccessful(
            tenantId, patientId, semanticKey, now.minusSeconds(rule.dedupeWindowSeconds()));
    }

    private static boolean isSuppressed(RuleDefinition rule, Set<String> matchedRuleCodes) {
        return rule.suppressedBy() != null
            && !rule.suppressedBy().isBlank()
            && matchedRuleCodes.contains(rule.suppressedBy());
    }

    private static String patientId(JsonNode context) {
        String mpi = context.path("patient").path("mpi").asText(null);
        if (mpi != null && !mpi.isBlank()) {
            return mpi.trim();
        }
        String root = context.path("patientId").asText(null);
        return root == null || root.isBlank() ? null : root.trim();
    }

    private static String encounterId(JsonNode context) {
        JsonNode encounters = context.path("encounters");
        if (encounters.isArray() && !encounters.isEmpty()) {
            String canonical = encounters.get(0).path("encounterId").asText(null);
            if (canonical != null && !canonical.isBlank()) {
                return canonical.trim();
            }
        }
        String root = context.path("encounterId").asText(null);
        return root == null || root.isBlank() ? null : root.trim();
    }

    private static String semanticKey(RuleDefinition rule, List<RuleActionResult> actions) {
        List<String> actionCodes = actions.stream()
            .map(action -> action.actionCode().name())
            .distinct()
            .sorted()
            .toList();
        return actionCodes.isEmpty() ? null : rule.ruleCode() + ":" + String.join(",", actionCodes);
    }

    /**
     * 捕获阻断或强提醒动作的人工越权理由，并绑定真实执行事实。
     */
    @Transactional
    public RuleOverrideResponse captureOverride(String executionId, RuleOverrideRequest request) {
        String tenantId = requireCurrentTenant();
        RuleExecutionLog execution = executions.findByExecutionIdAndTenantId(executionId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "规则执行记录不存在: " + executionId));
        if (execution.status() != RuleExecutionStatus.SUCCESS || !Boolean.TRUE.equals(execution.hit())) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "仅真实命中的规则动作允许人工越权");
        }
        if (request == null || !supportsOverride(request.actionCode())) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "仅 BLOCK 或 STRONG_REMINDER 动作允许人工越权");
        }
        String reason = trimToNull(request.reason());
        if (reason == null) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "人工越权必须填写理由");
        }
        boolean actionExists = false;
        for (JsonNode action : readJsonOrArray(execution.actionsJson())) {
            if (request.actionCode().name().equals(action.path("actionCode").asText())) {
                actionExists = true;
                break;
            }
        }
        if (!actionExists) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "执行记录中不存在待越权动作: " + request.actionCode());
        }
        if (overrides.findByTenantIdAndExecutionIdAndActionCode(
                tenantId, executionId, request.actionCode()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "该规则动作已记录人工越权");
        }

        Instant now = Instant.now();
        String overrideId = "rov-" + UUID.randomUUID();
        String actor = RequestContext.currentUserId().orElse("system");
        RuleOverrideLog saved = overrides.save(new RuleOverrideLog(
            null, overrideId, tenantId, execution.executionId(), execution.ruleId(), execution.versionId(),
            execution.patientId(), execution.encounterId(), request.actionCode(), reason,
            actor, now, now, RequestContext.currentTraceId()));
        auditRecorder.record(
            AuditAction.FEEDBACK, "rule_override_log", saved.overrideId(),
            "记录规则越权 " + executionId + "/" + request.actionCode().name());
        return new RuleOverrideResponse(
            saved.overrideId(), saved.executionId(), saved.ruleId(), saved.actionCode(),
            saved.overrideReason(), saved.overriddenBy(), saved.overriddenAt(), saved.traceId());
    }

    /**
     * 捕获影子命中的人工复核结论，作为误报统计的唯一事实来源。
     */
    @Transactional
    public RuleShadowFeedbackResponse captureShadowFeedback(
            String executionId,
            RuleShadowFeedbackRequest request) {
        String tenantId = requireCurrentTenant();
        RuleExecutionLog execution = executions.findByExecutionIdAndTenantId(executionId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "规则执行记录不存在: " + executionId));
        if (execution.status() != RuleExecutionStatus.SHADOW_RECORDED
                || !Boolean.TRUE.equals(execution.hit())) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "仅影子运行命中记录允许复核反馈");
        }
        if (request == null || request.decision() == null) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "影子复核结论不能为空");
        }
        String reason = trimToNull(request.reason());
        if (request.decision() == RuleShadowFeedbackDecision.FALSE_POSITIVE && reason == null) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "影子误报必须填写复核说明");
        }
        if (shadowFeedback.findByTenantIdAndExecutionId(tenantId, executionId).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "该影子执行已记录复核结论");
        }
        Instant now = Instant.now();
        String feedbackId = "rsf-" + UUID.randomUUID();
        String actor = RequestContext.currentUserId().orElse("system");
        RuleShadowFeedback saved = shadowFeedback.save(new RuleShadowFeedback(
            null,
            feedbackId,
            tenantId,
            execution.executionId(),
            execution.ruleId(),
            execution.versionId(),
            execution.patientId(),
            execution.encounterId(),
            request.decision(),
            reason,
            actor,
            now,
            now,
            RequestContext.currentTraceId()
        ));
        auditRecorder.record(
            AuditAction.FEEDBACK,
            "rule_shadow_feedback",
            saved.feedbackId(),
            "记录规则影子反馈 " + executionId + "/" + saved.decision().name()
        );
        return new RuleShadowFeedbackResponse(
            saved.feedbackId(),
            saved.executionId(),
            saved.ruleId(),
            saved.decision(),
            saved.reason(),
            saved.assessedBy(),
            saved.assessedAt(),
            saved.traceId()
        );
    }

    private static boolean supportsOverride(RuleActionCode actionCode) {
        return actionCode == RuleActionCode.BLOCK || actionCode == RuleActionCode.STRONG_REMINDER;
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
            .anyMatch(action -> testCase.expectedActionCode().equals(action.actionCode().name()));
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
            indexSnapshot.affectedPathways(), indexSnapshot.inPathPatients(), indexSnapshot.integrationAdapters());
        return new RuleImpactResponse(
            rule.ruleId(), version.versionId(), rule.riskLevel(), status, digest,
            affectedRules, indexSnapshot.affectedPathways(), indexSnapshot.inPathPatients(),
            indexSnapshot.integrationAdapters(), unavailable,
            RequestContext.currentTraceId());
    }

    private void ensureTerminologyCoverage(RuleVersion version) {
        List<TerminologyCoverageIssue> issues =
            terminologyCoverageGate.checkConditionCoverage(readJson(version.dslJson()).path("when"));
        if (!issues.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_RULE_004,
                "规则发布存在未覆盖编码对照，禁止上线：" + TerminologyCoverageGate.describeIssues(issues));
        }
    }

    private void ensureNoStaticConflicts(RuleDefinition candidate, RuleVersion candidateVersion) {
        LinkedHashMap<String, RuleDefinition> publishedByCode = new LinkedHashMap<>();
        definitions.findPublishedByTenantId(candidate.tenantId())
            .forEach(rule -> publishedByCode.put(rule.ruleCode(), rule));
        if (!PlatformTenant.isPlatformTenant(candidate.tenantId())) {
            definitions.findPublishedByTenantId(PlatformTenant.ID)
                .forEach(rule -> publishedByCode.putIfAbsent(rule.ruleCode(), rule));
        }
        List<RuleConflictTarget> targets = publishedByCode.values().stream()
            .filter(rule -> !rule.ruleCode().equals(candidate.ruleCode()))
            .filter(rule -> !hasExplicitSuppression(candidate, rule))
            .map(rule -> new RuleConflictTarget(
                rule.ruleCode(),
                readJson(findVersion(rule.activeVersionId(), rule.tenantId()).dslJson())))
            .toList();
        conflictDetector.detect(readJson(candidateVersion.dslJson()), targets)
            .ifPresent(conflict -> {
                throw new ApiException(
                    ErrorCode.ENG_RULE_004,
                    "规则与已发布规则 " + conflict.ruleCode() + " 在事实 " + conflict.fact()
                        + " 上存在" + conflict.reason()
                );
            });
    }

    private void ensureSuppressionContract(
            RuleDefinition candidate,
            RuleVersion candidateVersion,
            boolean requireActiveSource) {
        String sourceCode = trimToNull(candidate.suppressedBy());
        if (sourceCode == null) {
            return;
        }
        if (sourceCode.equals(candidate.ruleCode())) {
            throw new ApiException(ErrorCode.ENG_RULE_004, "规则不能抑制自身");
        }
        RuleDefinition source = findPublishedSuppressionSource(candidate.tenantId(), sourceCode)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_RULE_004,
                "抑制来源规则不存在或尚未发布: " + sourceCode));
        if (source.priority() <= candidate.priority()) {
            throw new ApiException(
                ErrorCode.ENG_RULE_004,
                "抑制来源规则 " + sourceCode + " 的优先级必须高于当前规则");
        }
        RuleVersion sourceVersion = findVersion(source.activeVersionId(), source.tenantId());
        String trigger = readJson(candidateVersion.dslJson()).path("trigger").asText(null);
        if (!triggerMatches(sourceVersion, trigger)) {
            throw new ApiException(
                ErrorCode.ENG_RULE_004,
                "抑制来源规则 " + sourceCode + " 必须与当前规则使用相同触发点");
        }
        if (requireActiveSource && !hasActiveUnifiedVersion(source)) {
            throw new ApiException(
                ErrorCode.ENG_RULE_004,
                "抑制来源规则 " + sourceCode + " 尚未全量激活");
        }
    }

    private Optional<RuleDefinition> findPublishedSuppressionSource(String tenantId, String ruleCode) {
        Optional<RuleDefinition> local = definitions.findByTenantIdAndRuleCode(tenantId, ruleCode)
            .filter(rule -> rule.status() == RuleDefinitionStatus.PUBLISHED);
        if (local.isPresent() || PlatformTenant.isPlatformTenant(tenantId)) {
            return local;
        }
        return definitions.findByTenantIdAndRuleCode(PlatformTenant.ID, ruleCode)
            .filter(rule -> rule.status() == RuleDefinitionStatus.PUBLISHED);
    }

    private static boolean hasExplicitSuppression(RuleDefinition left, RuleDefinition right) {
        return right.ruleCode().equals(left.suppressedBy())
            || left.ruleCode().equals(right.suppressedBy());
    }

    private String impactDigest(RuleDefinition rule, RuleVersion version, String status, List<String> unavailable,
                                List<RuleImpactObject> affectedRules,
                                List<RuleImpactObject> affectedPathways,
                                List<RuleImpactObject> inPathPatients,
                                List<RuleImpactObject> integrationAdapters) {
        return digestText(String.join("|",
            rule.tenantId(), rule.ruleId(), version.versionId(), rule.riskLevel().name(), status,
            impactObjectSignature(affectedRules),
            impactObjectSignature(affectedPathways),
            impactObjectSignature(inPathPatients),
            impactObjectSignature(integrationAdapters),
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

    private void ensureGovernanceDraft(String tenantId, String versionId) {
        RuleGovernance governance = governanceService.requireGovernance(tenantId, versionId);
        if (governance.state() != RuleGovernanceState.DRAFT) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "只有治理草稿阶段可以修改规则内容");
        }
    }

    private boolean triggerMatches(RuleVersion version, String triggerPoint) {
        String trigger = readJson(version.dslJson()).path("trigger").asText(null);
        return triggerPoint == null || triggerPoint.equals(trigger);
    }

    private void validateDsl(JsonNode dsl) {
        evaluator.evaluate(dsl, json.createObjectNode());
        applicabilityService.validateDsl(dsl);
        String trigger = dsl.path("trigger").asText(null);
        if (trigger == null || trigger.isBlank()) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则 DSL 缺少 trigger");
        }
        requireCanonicalTrigger(trigger);
        if (!dsl.has("then") || !dsl.has("explain")) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则 DSL 缺少 then 或 explain");
        }
    }

    private static void requireCanonicalTrigger(String triggerPoint) {
        boolean canonical = triggerPoint != null
            && EnumSet.allOf(ClinicalEventTriggerPoint.class).stream()
                .anyMatch(candidate -> candidate.wireValue().equals(triggerPoint));
        if (!canonical) {
            throw new ApiException(
                ErrorCode.ENG_RULE_001,
                "规则触发点必须使用客户面编码: patient-view/order-sign/medication-prescribe/"
                    + "result-review/discharge-sign/followup-alert"
            );
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
        Optional<RuleDefinition> direct = definitions.findByRuleIdAndTenantId(ruleId, tenantId)
            .or(() -> findPlatformRuleForTenant(ruleId, tenantId));
        if (direct.isPresent()) {
            Optional<RuleDefinition> resolved = resolveEffectiveRuleForCurrentOrg(direct.get(), tenantId);
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }
        return direct
            .or(() -> findPlatformRuleForTenant(ruleId, tenantId))
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "规则不存在: " + ruleId));
    }

    private Optional<RuleDefinition> resolveEffectiveRuleForCurrentOrg(RuleDefinition candidate, String tenantId) {
        String targetOrgUnitId = targetOrgUnitId();
        if (targetOrgUnitId == null) {
            return Optional.empty();
        }
        ResolvedAssetVersion resolved = inheritanceResolver.resolve(new InheritanceResolveQuery(
            tenantId,
            VersionedAssetType.RULE,
            candidate.ruleCode(),
            releaseApplicableScope(candidate),
            targetOrgUnitId
        ));
        if (resolved.disabled()) {
            throw new ApiException(ErrorCode.ENG_RULE_002, "规则已在当前组织停用");
        }
        if (resolved.version() == null) {
            throw new ApiException(ErrorCode.ENG_RULE_002, "当前组织未解析到有效规则版本");
        }
        AssetVersion assetVersion = resolved.version();
        int versionNo = Integer.parseInt(assetVersion.versionNo());
        return definitions.findByTenantIdAndRuleCode(assetVersion.tenantId(), candidate.ruleCode())
            .flatMap(rule -> versions.findByRuleIdAndTenantIdAndVersionNo(
                    rule.ruleId(), assetVersion.tenantId(), versionNo)
                .map(version -> copyRule(rule, rule.status(), version.versionId(),
                    rule.updatedAt(), rule.updatedBy(), rule.traceId())));
    }

    private Optional<RuleDefinition> findPlatformRuleForTenant(String ruleId, String tenantId) {
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            return Optional.empty();
        }
        return definitions.findByRuleIdAndTenantId(ruleId, PlatformTenant.ID)
            .map(platformRule -> definitions.findByTenantIdAndRuleCode(tenantId, platformRule.ruleCode())
                .filter(this::hasActiveUnifiedVersion)
                .orElse(platformRule));
    }

    private List<RuleDefinition> effectiveActiveRules(String tenantId) {
        LinkedHashMap<String, RuleDefinition> byCode = new LinkedHashMap<>();
        definitions.findPublishedByTenantId(tenantId).stream()
            .filter(this::hasActiveUnifiedVersion)
            .forEach(rule -> byCode.put(rule.ruleCode(), rule));
        if (!PlatformTenant.isPlatformTenant(tenantId)) {
            for (RuleDefinition rule : definitions.findPublishedByTenantId(PlatformTenant.ID)) {
                if (!byCode.containsKey(rule.ruleCode()) && hasActiveUnifiedVersion(rule)) {
                    byCode.put(rule.ruleCode(), rule);
                }
            }
        }
        return List.copyOf(byCode.values());
    }

    private boolean hasActiveUnifiedVersion(RuleDefinition rule) {
        RuleVersion version = findVersion(rule.activeVersionId(), rule.tenantId());
        return runtimeCandidate(rule, version).mode() != RuleRuntimeMode.INACTIVE;
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

    private static String targetOrgUnitId() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null) {
            return null;
        }
        if (scope.specialtyId() != null && !scope.specialtyId().isBlank()) {
            return scope.specialtyId();
        }
        if (scope.departmentId() != null && !scope.departmentId().isBlank()) {
            return scope.departmentId();
        }
        if (scope.siteId() != null && !scope.siteId().isBlank()) {
            return scope.siteId();
        }
        if (scope.campusId() != null && !scope.campusId().isBlank()) {
            return scope.campusId();
        }
        if (scope.hospitalId() != null && !scope.hospitalId().isBlank()) {
            return scope.hospitalId();
        }
        if (scope.groupId() != null && !scope.groupId().isBlank()) {
            return scope.groupId();
        }
        return null;
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
            source.priority(), source.suppressedBy(), source.dedupeWindowSeconds(),
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
            source.caseType(), source.contextSnapshotId(), source.inputPayload(),
            source.expectedHit(), source.expectedSeverity(),
            source.expectedActionCode(), actualHit, status, message, now,
            source.createdAt(), source.createdBy(), now, RequestContext.currentUserId().orElse("system"),
            RequestContext.currentTraceId());
    }

    private record RuleAssetContent(
        String ruleCode,
        String name,
        RuleType ruleType,
        RuleAuthoringMode authoringMode,
        RuleRiskLevel riskLevel,
        String packageVersion,
        String applicableOrgUnitId,
        Integer versionNo,
        String sourceRef,
        String changeSummary,
        JsonNode dsl,
        JsonNode explanation
    ) {}
}
