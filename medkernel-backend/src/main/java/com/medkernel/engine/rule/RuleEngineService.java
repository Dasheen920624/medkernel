package com.medkernel.engine.rule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.medkernel.engine.versioning.VersionReleaseScopeType;
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
    private final RuleDslEvaluator evaluator;
    private final AuditRecorder auditRecorder;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final ObjectMapper json;
    private final RuleImpactIndex impactIndex;
    private final TerminologyCoverageGate terminologyCoverageGate;
    private final RuleVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;
    private final InheritanceResolver inheritanceResolver;
    private final ContextSnapshotService contextSnapshots;

    /**
     * 注入规则引擎所需仓库、DSL 执行器、审计发布器、状态记录器与 JSON 处理器。
     */
    @Autowired
    public RuleEngineService(RuleDefinitionRepository definitions,
                             RuleVersionRepository versions,
                             RuleTestCaseRepository testCases,
                             RuleExecutionLogRepository executions,
                             RuleDslEvaluator evaluator,
                             AuditRecorder auditRecorder,
                             StateTransitionRecorder transitions,
                             DiagnoseResponseAssembler diagnoseAssembler,
                             ObjectMapper json,
                             ObjectProvider<RuleImpactIndex> impactIndexProvider,
                             ObjectProvider<TerminologyCoverageGate> terminologyCoverageGateProvider,
                             RuleVersionedAssetAdapter versionedAssets,
                             AssetVersionRepository assetVersions,
                             ReleasePort releasePort,
                             InheritanceResolver inheritanceResolver,
                             ContextSnapshotService contextSnapshots) {
        this(definitions, versions, testCases, executions, evaluator, auditRecorder, transitions,
            diagnoseAssembler, json, impactIndexProvider.getIfAvailable(RuleImpactIndex::empty),
            terminologyCoverageGateProvider.getIfAvailable(TerminologyCoverageGate::noop),
            versionedAssets, assetVersions, releasePort, inheritanceResolver, contextSnapshots);
    }

    RuleEngineService(RuleDefinitionRepository definitions,
                      RuleVersionRepository versions,
                      RuleTestCaseRepository testCases,
                      RuleExecutionLogRepository executions,
                      RuleDslEvaluator evaluator,
                      AuditRecorder auditRecorder,
                      StateTransitionRecorder transitions,
                      DiagnoseResponseAssembler diagnoseAssembler,
                      ObjectMapper json,
                      RuleImpactIndex impactIndex,
                      TerminologyCoverageGate terminologyCoverageGate,
                      RuleVersionedAssetAdapter versionedAssets,
                      AssetVersionRepository assetVersions,
                      ReleasePort releasePort,
                      InheritanceResolver inheritanceResolver,
                      ContextSnapshotService contextSnapshots) {
        this.definitions = definitions;
        this.versions = versions;
        this.testCases = testCases;
        this.executions = executions;
        this.evaluator = evaluator;
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
            RuleDefinitionStatus.DRAFT, versionId, request.packageVersion(), request.applicableOrgUnitId(),
            now, actor, now, actor, traceId);
        RuleVersion version = new RuleVersion(
            null, versionId, tenantId, ruleId, 1, request.sourceRef(), request.changeSummary(),
            writeJson(request.dsl()), writeJson(request.explanation()),
            RuleVersionStatus.DRAFT, null, null, null, now, actor, now, actor, traceId);

        definitions.save(definition);
        versions.save(version);
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

        AssetVersion assetVersion = requireRuleAssetVersion(rule, version);
        definitions.save(updatedRule);
        versions.save(updatedVersion);
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
            updatedAssetVersion.status());
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
            assetVersion.status());
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
            throw new ApiException(ErrorCode.ENG_RULE_004, "高危规则发布前必须提交当前影响分析摘要");
        }
        ensureTerminologyCoverage(version);

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
        List<String> releaseEvidence = coordinateCanaryRelease(rule, version, impact, request, actor);
        transitions.record(RULE_ENTITY, ruleId, rule.status().name(),
            RuleDefinitionStatus.PUBLISHED.name(), "PUBLISH_RULE", null);
        auditRecorder.record(AuditAction.PUBLISH, RULE_ENTITY, ruleId, "发布规则版本 " + version.versionId());
        return new RulePublishResponse(
            ruleId, version.versionId(), RuleDefinitionStatus.PUBLISHED,
            RequestContext.currentTraceId(), results, impact.impactDigest(), impact.analysisStatus(), releaseEvidence);
    }

    private List<String> coordinateCanaryRelease(
            RuleDefinition rule,
            RuleVersion version,
            RuleImpactResponse impact,
            RulePublishRequest request,
            String actor) {
        AssetVersion assetVersion = requireRuleAssetVersion(rule, version);
        VersionReleaseCommand command = new VersionReleaseCommand(
            rule.tenantId(),
            VersionedAssetType.RULE,
            rule.ruleCode(),
            assetVersion.versionId(),
            releaseOrgScope(rule),
            releaseApplicableScope(rule),
            VersionReleaseScopeType.HOSPITAL,
            null,
            impact.impactDigest(),
            releaseReason(request, "规则发布门禁通过"),
            request == null ? List.of() : request.roleCodes(),
            actor,
            RequestContext.currentTraceId()
        );
        return advanceCanaryRelease(assetVersion, command);
    }

    /**
     * 将已通过灰度发布门禁的规则全量激活到统一版本生效域。
     */
    @Transactional
    public RulePublishResponse fullRollout(String ruleId, RulePublishRequest request) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        if (rule.status() != RuleDefinitionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "只有已发布规则可以全量激活");
        }
        if (!AuthenticatedRoleGuard.has(RoleCode.HOSPITAL_ADMIN)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "规则全量激活仅医院管理员可执行");
        }
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        RuleImpactResponse impact = impactFor(rule, version);
        if (request == null
                || request.impactDigest() == null
                || !request.impactDigest().equals(impact.impactDigest())
                || request.reason() == null
                || request.reason().isBlank()) {
            throw new ApiException(ErrorCode.ENG_RULE_004, "规则全量激活必须提交当前影响摘要和确认说明");
        }
        String actor = RequestContext.currentUserId().orElse("system");
        AssetVersion assetVersion = requireRuleAssetVersion(rule, version);
        VersionReleasePlan plan = releasePort.releaseFull(new VersionReleaseCommand(
            rule.tenantId(),
            VersionedAssetType.RULE,
            rule.ruleCode(),
            assetVersion.versionId(),
            releaseOrgScope(rule),
            releaseApplicableScope(rule),
            VersionReleaseScopeType.ALL,
            null,
            impact.impactDigest(),
            request.reason().trim(),
            request.roleCodes(),
            actor,
            RequestContext.currentTraceId()
        ));
        List<String> evidence = new ArrayList<>();
        appendEvidence(evidence, plan);
        transitions.record(
            RULE_ENTITY,
            ruleId,
            RuleDefinitionStatus.PUBLISHED.name(),
            RuleDefinitionStatus.PUBLISHED.name(),
            "FULL_ROLLOUT_RULE",
            null
        );
        auditRecorder.record(
            AuditAction.PUBLISH,
            RULE_ENTITY,
            ruleId,
            "全量激活规则版本 " + version.versionId()
        );
        return new RulePublishResponse(
            ruleId,
            version.versionId(),
            RuleDefinitionStatus.PUBLISHED,
            RequestContext.currentTraceId(),
            List.of(),
            impact.impactDigest(),
            impact.analysisStatus(),
            evidence
        );
    }

    private AssetVersion requireRuleAssetVersion(RuleDefinition rule, RuleVersion version) {
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                rule.tenantId(), VersionedAssetType.RULE, rule.ruleCode(), String.valueOf(version.versionNo()))
            .orElseThrow(() -> new ApiException(
                ErrorCode.CONFLICT,
                "规则缺少统一资产版本，禁止发布: " + rule.ruleCode() + "@" + version.versionNo()
            ));
    }

    private List<String> advanceCanaryRelease(AssetVersion assetVersion, VersionReleaseCommand command) {
        List<String> evidence = new ArrayList<>();
        AssetVersionStatus status = assetVersion.status();
        if (status == AssetVersionStatus.DRAFT) {
            appendEvidence(evidence, releasePort.submitForReview(command));
            appendEvidence(evidence, releasePort.approveForSilentObservation(command));
            appendEvidence(evidence, releasePort.releaseGray(command));
            return evidence;
        }
        if (status == AssetVersionStatus.PENDING_REVIEW) {
            appendEvidence(evidence, releasePort.approveForSilentObservation(command));
            appendEvidence(evidence, releasePort.releaseGray(command));
            return evidence;
        }
        if (status == AssetVersionStatus.PUBLISHED || status == AssetVersionStatus.ACTIVE) {
            appendEvidence(evidence, releasePort.releaseGray(command));
        }
        return evidence;
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

    private static String releaseReason(RulePublishRequest request, String fallback) {
        return request == null ? fallback : notBlank(request.reason(), fallback);
    }

    private static String notBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
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
        String tenantId = requireCurrentTenant();
        List<String> selectedRuleIds = ruleIds == null ? List.of() : ruleIds;
        List<RuleDefinition> candidates = selectedRuleIds.isEmpty()
            ? effectiveActiveRules(tenantId)
            : selectedRuleIds.stream().map(ruleId -> findEffectiveRule(ruleId, tenantId)).toList();

        List<RuleEvaluationItem> items = candidates.stream()
            .map(rule -> Map.entry(rule, findVersion(rule.activeVersionId(), rule.tenantId())))
            .filter(this::isActiveUnifiedVersion)
            .filter(entry -> triggerMatches(entry.getValue(), triggerPoint))
            .map(entry -> evaluateAndLog(entry.getKey(), entry.getValue(), tenantId,
                context, triggerPoint, eventId))
            .toList();
        RuleRiskLevel highest = items.stream()
            .map(RuleEvaluationItem::severity)
            .reduce(null, RuleRiskLevel::max);
        return new RuleEvaluateResponse("eval-" + UUID.randomUUID(), items, highest, RequestContext.currentTraceId());
    }

    private boolean isActiveUnifiedVersion(Map.Entry<RuleDefinition, RuleVersion> candidate) {
        RuleDefinition rule = candidate.getKey();
        RuleVersion version = candidate.getValue();
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                rule.tenantId(),
                VersionedAssetType.RULE,
                rule.ruleCode(),
                String.valueOf(version.versionNo()))
            .filter(assetVersion -> assetVersion.status() == AssetVersionStatus.ACTIVE)
            .isPresent();
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
        auditRecorder.record(AuditAction.EXECUTE, EXECUTION_ENTITY, log.executionId(), "执行规则 " + rule.ruleId());
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

    private boolean triggerMatches(RuleVersion version, String triggerPoint) {
        String trigger = readJson(version.dslJson()).path("trigger").asText(null);
        return triggerPoint == null || triggerPoint.equals(trigger);
    }

    private void validateDsl(JsonNode dsl) {
        evaluator.evaluate(dsl, json.createObjectNode());
        if (dsl.path("trigger").asText(null) == null || dsl.path("trigger").asText().isBlank()) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则 DSL 缺少 trigger");
        }
        if (!dsl.has("then") || !dsl.has("explain")) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则 DSL 缺少 then 或 explain");
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
        return isActiveUnifiedVersion(Map.entry(rule, version));
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
