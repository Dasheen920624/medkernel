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
import java.util.Locale;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.context.ContextFieldPathPolicy;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.event.EngineDomainEventPort;
import com.medkernel.engine.event.OverrideCapturedEvent;
import com.medkernel.engine.event.RuleFiredEvent;
import com.medkernel.engine.versioning.AssetReferenceConsistency;
import com.medkernel.engine.versioning.AssetVersionNumbers;
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
import com.medkernel.engine.versioning.AssetTriggerBindingService;
import com.medkernel.engine.versioning.AssetTriggerPurpose;
import com.medkernel.engine.versioning.InheritanceResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.RolloutPolicy;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleasePlan;
import com.medkernel.engine.versioning.VersionPublishEvidence;
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
 * <p>聚合规则定义、版本、验证用例、执行日志四张表，承担：
 * <ul>
 *   <li>创建规则与初始草稿版本（DSL 校验失败抛 {@code ENG-RULE-001}）；</li>
 *   <li>新增验证用例并校验状态（仅 {@code DRAFT} 可加）；</li>
 *   <li>试运行执行：复用 {@link RuleDslEvaluator}，同步写 {@code rule_execution_log} 与状态历史；</li>
 *   <li>发布门禁：要求阳性/阴性/边界/冲突四类用例齐备且全部 PASS，否则抛 {@code ENG-RULE-004}；</li>
 *   <li>真实执行：按触发点匹配统一版本已发布规则集合，返回命中明细 + 最高严重度；</li>
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
    private final RuleParameterBindingRepository parameterBindings;
    private final RuleTestCaseRepository testCases;
    private final RuleExecutionLogRepository executions;
    private final RuleOverrideLogRepository overrides;
    private final RuleShadowFeedbackRepository shadowFeedback;
    private final RuleBacktestRunRepository backtests;
    private final RuleDriftSnapshotRepository driftSnapshots;
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
    private final AssetTriggerBindingService triggerBindings;
    private final ReleasePort releasePort;
    private final RuleGovernanceService governanceService;
    private final InheritanceResolver inheritanceResolver;
    private final RuleEffectiveVersionResolver effectiveVersions;
    private final ContextSnapshotService contextSnapshots;
    private final EngineDomainEventPort domainEvents;
    private final RuleConflictDetector conflictDetector = new RuleConflictDetector();

    private enum RuleRuntimeMode {
        ACTIVE,
        SHADOW,
        CANARY,
        INACTIVE
    }

    private record RuleRuntimeCandidate(
        RuleDefinition rule,
        RuleVersion version,
        RuleRuntimeMode mode
    ) {}

    private record RuleParameterSpec(
        String key,
        String valueType,
        boolean required
    ) {}

    /**
     * 注入规则引擎所需仓库、DSL 执行器、审计发布器、状态记录器与 JSON 处理器。
     */
    @Autowired
    public RuleEngineService(RuleDefinitionRepository definitions,
                             RuleVersionRepository versions,
                             RuleParameterBindingRepository parameterBindings,
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
                             AssetTriggerBindingService triggerBindings,
                             ReleasePort releasePort,
                             RuleGovernanceService governanceService,
                             RuleShadowFeedbackRepository shadowFeedback,
                             RuleBacktestRunRepository backtests,
                             RuleDriftSnapshotRepository driftSnapshots,
                             InheritanceResolver inheritanceResolver,
                             ContextSnapshotService contextSnapshots,
                             ObjectProvider<EngineDomainEventPort> domainEventProvider) {
        this(definitions, versions, parameterBindings, testCases, executions, overrides,
            evaluator, applicabilityService,
            auditRecorder, transitions,
            diagnoseAssembler, json, impactIndexProvider.getIfAvailable(RuleImpactIndex::empty),
            terminologyCoverageGateProvider.getIfAvailable(TerminologyCoverageGate::noop),
            versionedAssets, assetVersions, triggerBindings, releasePort, governanceService, shadowFeedback,
            backtests, driftSnapshots, inheritanceResolver, contextSnapshots,
            domainEventProvider.getIfAvailable(EngineDomainEventPort::noop));
    }

    RuleEngineService(RuleDefinitionRepository definitions,
                      RuleVersionRepository versions,
                      RuleParameterBindingRepository parameterBindings,
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
                      AssetTriggerBindingService triggerBindings,
                      ReleasePort releasePort,
                      RuleGovernanceService governanceService,
                      RuleShadowFeedbackRepository shadowFeedback,
                      RuleBacktestRunRepository backtests,
                      RuleDriftSnapshotRepository driftSnapshots,
                      InheritanceResolver inheritanceResolver,
                      ContextSnapshotService contextSnapshots) {
        this(definitions, versions, parameterBindings, testCases, executions, overrides, evaluator, applicabilityService,
            auditRecorder, transitions, diagnoseAssembler, json, impactIndex, terminologyCoverageGate,
            versionedAssets, assetVersions, triggerBindings, releasePort, governanceService, shadowFeedback, backtests,
            driftSnapshots, inheritanceResolver, contextSnapshots, EngineDomainEventPort.noop());
    }

    RuleEngineService(RuleDefinitionRepository definitions,
                      RuleVersionRepository versions,
                      RuleParameterBindingRepository parameterBindings,
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
                      AssetTriggerBindingService triggerBindings,
                      ReleasePort releasePort,
                      RuleGovernanceService governanceService,
                      RuleShadowFeedbackRepository shadowFeedback,
                      RuleBacktestRunRepository backtests,
                      RuleDriftSnapshotRepository driftSnapshots,
                      InheritanceResolver inheritanceResolver,
                      ContextSnapshotService contextSnapshots,
                      EngineDomainEventPort domainEvents) {
        this.definitions = definitions;
        this.versions = versions;
        this.parameterBindings = Objects.requireNonNull(
            parameterBindings, "规则参数绑定仓库不能为空");
        this.testCases = testCases;
        this.executions = executions;
        this.overrides = overrides;
        this.shadowFeedback = Objects.requireNonNull(
            shadowFeedback, "规则影子运行反馈仓库不能为空");
        this.backtests = Objects.requireNonNull(backtests, "规则回测仓库不能为空");
        this.driftSnapshots = Objects.requireNonNull(driftSnapshots, "规则漂移监测仓库不能为空");
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
        this.triggerBindings = Objects.requireNonNull(triggerBindings, "资产触发绑定服务不能为空");
        this.releasePort = Objects.requireNonNull(releasePort, "统一发布端口不能为空");
        this.governanceService = Objects.requireNonNull(governanceService, "规则治理服务不能为空");
        this.inheritanceResolver = Objects.requireNonNull(inheritanceResolver, "继承解析器不能为空");
        this.effectiveVersions =
            new RuleEffectiveVersionResolver(definitions, versions, assetVersions, inheritanceResolver);
        this.contextSnapshots = Objects.requireNonNull(contextSnapshots, "标准上下文快照服务不能为空");
        this.domainEvents = domainEvents == null ? EngineDomainEventPort.noop() : domainEvents;
    }

    /**
     * 创建规则定义和初始草稿版本。
     *
     * <p>前置：当前请求必须携带租户上下文；DSL 必须包含 when/then/explain，
     * 触发点由精确资产版本的多触发绑定独立维护。
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
        List<RuleParameterBinding> bindings = parameterBindingRecords(
            request.dsl(), request.parameterBindings(), tenantId, versionId, now, actor, traceId);
        RuleDefinition definition = new RuleDefinition(
            null, ruleId, tenantId, request.ruleCode(), request.name(), request.ruleType(),
            request.authoringMode() == null ? RuleAuthoringMode.DSL : request.authoringMode(),
            request.riskLevel() == null ? RuleRiskLevel.MEDIUM : request.riskLevel(),
            request.priority() == null ? 100 : request.priority(),
            trimToNull(request.suppressedBy()),
            request.dedupeWindowSeconds() == null ? 0 : request.dedupeWindowSeconds(),
            RuleDefinitionStatus.DRAFT, versionId, request.applicableOrgUnitId(),
            now, actor, now, actor, traceId);
        RuleVersion version = new RuleVersion(
            null, versionId, tenantId, ruleId, 1, request.sourceRef(), request.changeSummary(),
            writeJson(request.dsl()), writeJson(request.explanation()),
            RuleVersionStatus.DRAFT, null, null, null, now, actor, now, actor, traceId);

        definitions.save(definition);
        versions.save(version);
        bindings.forEach(parameterBindings::save);
        governanceService.initialize(
            tenantId, versionId, definition.riskLevel(), actor, traceId);
        applicabilityService.saveMirror(version, request.dsl(), now, actor, traceId);
        AssetVersion registeredAssetVersion = versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.RULE,
            definition.ruleCode(),
            releaseOrgScope(definition),
            releaseApplicableScope(definition),
            ruleAssetContent(definition, version),
            null,
            version.sourceRef(),
            actor,
            traceId,
            safetyPolicy(definition),
            null,
            AssetReferenceConsistency.dependencyDeclarations(request.dsl())
        ));
        triggerBindings.replaceBindings(
            registeredAssetVersion, request.triggers(), actor, traceId);
        transitions.record(RULE_ENTITY, ruleId, null, RuleDefinitionStatus.DRAFT.name(), "CREATE_RULE", null);
        auditRecorder.record(AuditAction.CREATE, RULE_ENTITY, ruleId, "创建规则 " + request.ruleCode());
        if (!bindings.isEmpty()) {
            auditRecorder.record(
                AuditAction.CREATE,
                "mk_engine_rule_parameter_binding",
                versionId,
                "保存规则参数绑定 " + bindings.size() + " 项"
            );
        }
        return new RuleCreateResponse(ruleId, versionId, RuleDefinitionStatus.DRAFT, traceId);
    }

    /**
     * 将已全量运行的当前规则复制为同一稳定编码的下一版草稿。
     *
     * <p>旧发布版本继续由统一版本解析器提供运行服务；新版本只切换编辑态指针，
     * 并复制 DSL、解释、适用域、参数绑定和发布门禁用例。测试执行结果不会复制，
     * 新版本必须重新完成安全复核。
     */
    @Transactional
    public RuleVersionCreateResponse createNextVersion(String ruleId) {
        String tenantId = requireCurrentTenant();
        String traceId = RequestContext.currentTraceId();
        String actor = RequestContext.currentUserId().orElse("system");
        Instant now = Instant.now();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion current = findVersion(rule.activeVersionId(), tenantId);
        RuleGovernance governance =
            governanceService.requireGovernance(tenantId, current.versionId());
        if (rule.status() != RuleDefinitionStatus.PUBLISHED
                || current.status() != RuleVersionStatus.PUBLISHED
                || (governance.state() != RuleGovernanceState.FULL
                    && governance.state() != RuleGovernanceState.MONITOR)) {
            throw new ApiException(
                ErrorCode.ENG_RULE_006,
                "只有已全量运行或监测中的规则可以复制为下一版本"
            );
        }
        int nextVersionNo = versions
            .findByRuleIdAndTenantIdOrderByVersionNoDesc(ruleId, tenantId)
            .stream()
            .map(RuleVersion::versionNo)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(current.versionNo()) + 1;
        String nextVersionId = "rv-" + UUID.randomUUID();
        RuleVersion next = new RuleVersion(
            null,
            nextVersionId,
            tenantId,
            rule.ruleId(),
            nextVersionNo,
            current.sourceRef(),
            "基于 v" + current.versionNo() + " 创建 v" + nextVersionNo + " 草稿",
            current.dslJson(),
            current.explanationJson(),
            RuleVersionStatus.DRAFT,
            null,
            null,
            null,
            now,
            actor,
            now,
            actor,
            traceId
        );
        RuleDefinition updatedRule = copyRule(
            rule,
            RuleDefinitionStatus.PUBLISHED,
            nextVersionId,
            now,
            actor,
            traceId
        );

        versions.save(next);
        definitions.save(updatedRule);
        applicabilityService.saveMirror(
            next, readJson(next.dslJson()), now, actor, traceId);
        parameterBindings
            .findByRuleVersionIdAndTenantIdOrderByParamKeyAsc(current.versionId(), tenantId)
            .stream()
            .map(binding -> new RuleParameterBinding(
                null,
                nextVersionId,
                tenantId,
                binding.paramKey(),
                binding.paramValueJson(),
                now,
                actor,
                traceId
            ))
            .forEach(parameterBindings::save);
        testCases
            .findByVersionIdAndTenantIdOrderByCreatedAtAsc(current.versionId(), tenantId)
            .stream()
            .map(source -> copyTestCaseToVersion(
                source, nextVersionId, now, actor, traceId))
            .forEach(testCases::save);
        governanceService.initialize(
            tenantId, nextVersionId, updatedRule.riskLevel(), actor, traceId);
        AssetVersion sourceAssetVersion = requireRuleAssetVersion(rule, current);
        AssetVersion nextAssetVersion = versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.RULE,
            updatedRule.ruleCode(),
            releaseOrgScope(updatedRule),
            releaseApplicableScope(updatedRule),
            ruleAssetContent(updatedRule, next),
            null,
            next.sourceRef(),
            actor,
            traceId,
            safetyPolicy(updatedRule),
            null,
            AssetReferenceConsistency.dependencyDeclarations(readJson(next.dslJson()))
        ));
        triggerBindings.copyBindings(
            sourceAssetVersion, nextAssetVersion, actor, traceId);
        transitions.record(
            RULE_ENTITY,
            ruleId,
            current.versionId(),
            nextVersionId,
            "CREATE_NEXT_RULE_VERSION",
            null
        );
        auditRecorder.record(
            AuditAction.CREATE,
            RULE_ENTITY,
            ruleId,
            "复制规则为下一版本 v" + nextVersionNo
        );
        return new RuleVersionCreateResponse(
            ruleId,
            nextVersionId,
            nextVersionNo,
            RuleVersionStatus.DRAFT,
            traceId
        );
    }

    /**
     * 更新草稿规则定义和当前草稿版本。
     *
     * <p>已发布版本保持不可变并继续运行；只有当前指向的草稿版本可修改。
     */
    @Transactional
    public RuleDetailResponse updateRule(String ruleId, RuleUpdateRequest request) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        ensureEditableDraft(rule, version);
        ensureGovernanceDraft(tenantId, version.versionId());
        validateDsl(request.dsl());
        ensurePublishedIdentityMetadataUnchanged(rule, request);
        ensureRuleCodeAvailable(tenantId, request.ruleCode(), ruleId);

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        RuleDefinitionStatus authoringStatus =
            rule.status() == RuleDefinitionStatus.PUBLISHED
                ? RuleDefinitionStatus.PUBLISHED
                : RuleDefinitionStatus.DRAFT;
        RuleDefinition updatedRule = new RuleDefinition(
            rule.id(), rule.ruleId(), rule.tenantId(), request.ruleCode(), request.name(),
            request.ruleType(), request.authoringMode() == null ? RuleAuthoringMode.DSL : request.authoringMode(),
            request.riskLevel() == null ? RuleRiskLevel.MEDIUM : request.riskLevel(),
            request.priority() == null ? 100 : request.priority(),
            trimToNull(request.suppressedBy()),
            request.dedupeWindowSeconds() == null ? 0 : request.dedupeWindowSeconds(),
            authoringStatus, rule.activeVersionId(), request.applicableOrgUnitId(),
            rule.createdAt(), rule.createdBy(), now, actor,
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
            actor,
            RequestContext.currentTraceId(),
            AssetReferenceConsistency.dependencyDeclarations(request.dsl())
        ));
        triggerBindings.replaceBindings(
            updatedAssetVersion,
            request.triggers(),
            actor,
            RequestContext.currentTraceId()
        );
        transitions.record(RULE_ENTITY, ruleId, rule.status().name(), authoringStatus.name(),
            "UPDATE_RULE", null);
        auditRecorder.record(AuditAction.UPDATE, RULE_ENTITY, ruleId, "更新规则 " + request.ruleCode());
        return new RuleDetailResponse(
            updatedRule, updatedVersion,
            versionHistory(updatedRule, updatedVersion),
            testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId),
            triggerBindings.listBindings(updatedAssetVersion),
            updatedAssetVersion.status(),
            governanceSnapshot(updatedRule, updatedVersion, List.of(), null, null, List.of()));
    }

    /**
     * 装载规则当前版本与全部验证用例的聚合详情。
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
            versionHistory(rule, version),
            testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId),
            triggerBindings.listBindings(assetVersion),
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
        String keyword = filter == null ? null : keywordLike(filter.keyword());
        if (requiresEffectiveRuleMerge(tenantId, status)) {
            String platformStatus = RuleDefinitionStatus.PUBLISHED.name();
            long total = definitions.countEffectiveByFilter(
                tenantId, PlatformTenant.ID, status, platformStatus, type, risk, keyword);
            if (total == 0) {
                return PageResponse.empty(page);
            }
            List<RuleDefinition> rows = definitions.pageEffectiveByFilter(
                tenantId, PlatformTenant.ID, status, platformStatus, type, risk, keyword,
                page.offset(), page.safeSize());
            return PageResponse.of(rows, page, total);
        }
        long total = definitions.countByFilter(tenantId, status, type, risk, keyword);
        if (total == 0) {
            return PageResponse.empty(page);
        }
        List<RuleDefinition> rows = definitions.pageByFilter(
            tenantId, status, type, risk, keyword, page.offset(), page.safeSize());
        return PageResponse.of(rows, page, total);
    }

    /**
     * 为指定规则当前版本新增一条验证用例。
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
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        ensureEditableDraft(rule, version);
        ensureGovernanceDraft(tenantId, version.versionId());
        ContextSnapshotResponse snapshot = contextSnapshots.findById(request.contextSnapshotId());
        if (snapshot.status() != ContextSnapshotStatus.ACTIVE || snapshot.resources() == null) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "规则验证用例只能基于已生效标准上下文生成");
        }
        String caseId = "rtc-" + UUID.randomUUID();
        RuleTestCase saved = testCases.save(new RuleTestCase(
            null, caseId, tenantId, ruleId, version.versionId(), request.caseType(),
            snapshot.snapshotId(), writeObject(snapshot.resources()), request.expectedHit(), request.expectedSeverity(),
            request.expectedActionCode(), null, RuleTestCaseStatus.NOT_RUN, null, null,
            now, actor, now, actor, traceId));
        auditRecorder.record(AuditAction.UPDATE, RULE_ENTITY, ruleId, "新增规则验证用例 " + saved.caseId());
        return new RuleTestCaseResponse(saved.caseId(), saved.caseType(), saved.lastStatus());
    }

    /**
     * 执行当前版本全部验证用例并回写结果，不推进发布状态。
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
     * <p>试运行必须显式选择当前资产版本已经绑定的标准临床触发点；
     * 失败：规则/版本不存在抛 {@code ENG-RULE-002}/{@code ENG-RULE-003}。
     */
    @Transactional
    public RuleEvaluationItem simulate(String ruleId, RuleSimulateRequest request) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        requireCanonicalTrigger(request.triggerPoint());
        if (!triggerBindings.matches(
                requireRuleAssetVersion(rule, version),
                AssetTriggerPurpose.RULE_EXECUTION,
                request.triggerPoint())) {
            throw new ApiException(
                ErrorCode.ENG_RULE_006,
                "试运行触发点未绑定当前规则版本: " + request.triggerPoint());
        }
        return evaluateAndLog(
            rule, version, tenantId, request.context(), request.triggerPoint(), null);
    }

    /**
     * 按七阶段闭集推进规则治理状态，发布端口只执行当前阶段对应的一步。
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
                && target == RuleGovernanceState.REVIEWED) {
            ensureEditableDraft(rule, version);
            ensureRuleStableAssetReferences(rule, version);
            validateGovernanceImpact(rule, request, impact);
            List<RuleTestCase> cases =
                testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId);
            ensureCoverage(cases);
            ensureTerminologyCoverage(version);
            ensureSuppressionContract(rule, version, false);
            ensureNoStaticConflicts(rule, version);
            testResults = cases.stream().map(testCase -> runTestCase(version, testCase)).toList();
            if (testResults.stream().anyMatch(result -> result.status() != RuleTestCaseStatus.PASS)) {
                throw new ApiException(ErrorCode.ENG_RULE_004, "规则验证用例未全部通过");
            }
        } else if (target == RuleGovernanceState.SHADOW
                || target == RuleGovernanceState.CANARY
                || target == RuleGovernanceState.FULL) {
            validateGovernanceImpact(rule, request, impact);
        }
        ensureGovernanceReleaseCoordinator(rule, current, target);

        RuleGovernance updated = governanceService.transition(
            tenantId,
            version.versionId(),
            target,
            request.reason(),
            actor,
            traceId
        );
        if (target == RuleGovernanceState.REVIEWED) {
            appendEvidence(
                releaseEvidence,
                releasePort.submitForReview(
                    governanceReleaseCommand(rule, version, impact, request, actor)
                )
            );
        } else if (target == RuleGovernanceState.SHADOW) {
            appendEvidence(
                releaseEvidence,
                releasePort.approveReview(
                    governanceReleaseCommand(rule, version, impact, request, actor)
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
                    governanceReleaseCommand(rule, version, impact, request, actor)
                )
            );
        } else if (target == RuleGovernanceState.FULL) {
            ensureSuppressionContract(rule, version, true);
            appendEvidence(
                releaseEvidence,
                releasePort.publish(
                    governanceReleaseCommand(rule, version, impact, request, actor)
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
            RuleGovernanceTransitionRequest request,
            String actor) {
        return governanceReleaseCommand(
            rule,
            version,
            impact,
            request.reason(),
            request.publishEvidence(),
            request.targetState() == RuleGovernanceState.CANARY
                ? RolloutPolicy.canaryBedPercent(10)
                : RolloutPolicy.all(),
            actor
        );
    }

    private VersionReleaseCommand governanceReleaseCommand(
            RuleDefinition rule,
            RuleVersion version,
            RuleImpactResponse impact,
            String reason,
            String actor) {
        return governanceReleaseCommand(
            rule,
            version,
            impact,
            reason,
            VersionPublishEvidence.empty(),
            RolloutPolicy.all(),
            actor
        );
    }

    private VersionReleaseCommand governanceReleaseCommand(
            RuleDefinition rule,
            RuleVersion version,
            RuleImpactResponse impact,
            String reason,
            VersionPublishEvidence publishEvidence,
            RolloutPolicy rolloutPolicy,
            String actor) {
        AssetVersion assetVersion = requireRuleAssetVersion(rule, version);
        return new VersionReleaseCommand(
            rule.tenantId(),
            VersionedAssetType.RULE,
            rule.ruleCode(),
            assetVersion.versionId(),
            assetVersion.organizationScope(),
            assetVersion.applicableScope(),
            null,
            null,
            rolloutPolicy,
            impact.impactDigest(),
            reason.trim(),
            actor,
            RequestContext.currentTraceId(),
            publishEvidence.qualityGate()
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
        return new RuleGovernanceResponse(
            rule.ruleId(),
            version.versionId(),
            governance.state(),
            governance.authorId(),
            governance.lastReason(),
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
            AssetVersionStatus.WITHDRAWN,
            "version:" + assetVersion.versionId(),
            assetVersion.effectiveFrom(),
            now,
            now,
            actor
        ));
    }

    private static void ensureGovernanceReleaseCoordinator(
            RuleDefinition rule,
            RuleGovernance current,
            RuleGovernanceState target) {
        if (PlatformTenant.isPlatformTenant(rule.tenantId())) {
            boolean submitForReview = current.state() == RuleGovernanceState.DRAFT
                && target == RuleGovernanceState.REVIEWED;
            boolean coordinateRelease = target == RuleGovernanceState.SHADOW
                || target == RuleGovernanceState.CANARY
                || target == RuleGovernanceState.FULL
                || target == RuleGovernanceState.MONITOR
                || target == RuleGovernanceState.RETIRED;
            if (submitForReview || coordinateRelease) {
                requireAnyRole(
                    "平台规则治理推进需要医疗引擎运营职责",
                    RoleCode.ENGINE_OPERATOR);
            }
            return;
        }
        if (current.state() == RuleGovernanceState.DRAFT
                && target == RuleGovernanceState.REVIEWED) {
            requireAnyRole(
                "确认规则进入发布验证需要医疗引擎运营职责",
                RoleCode.ENGINE_OPERATOR
            );
            return;
        }
        if (target == RuleGovernanceState.FULL) {
            requireAnyRole(
                "规则全量激活需要医疗引擎运营职责",
                RoleCode.ENGINE_OPERATOR);
            return;
        }
        if (target == RuleGovernanceState.SHADOW
                || target == RuleGovernanceState.CANARY
                || target == RuleGovernanceState.MONITOR
                || target == RuleGovernanceState.RETIRED) {
            requireAnyRole(
                "规则影子、灰度、监测和退役需要医疗引擎运营职责",
                RoleCode.ENGINE_OPERATOR
            );
        }
    }

    private static void requireAnyRole(String message, RoleCode... allowedRoles) {
        boolean allowed = java.util.Arrays.stream(allowedRoles).anyMatch(AuthenticatedRoleGuard::has);
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN, message);
        }
    }

    private AssetVersion requireRuleAssetVersion(RuleDefinition rule, RuleVersion version) {
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                rule.tenantId(), VersionedAssetType.RULE, rule.ruleCode(), assetVersionNo(version))
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
        return null;
    }

    private static String releaseApplicableScope(RuleDefinition rule) {
        return "ALL";
    }

    private static String notBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String keywordLike(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
    }

    private String ruleAssetContent(RuleDefinition rule, RuleVersion version) {
        return writeObject(new RuleAssetContent(
            rule.ruleCode(),
            rule.name(),
            rule.ruleType(),
            rule.authoringMode(),
            rule.riskLevel(),
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
     * 按触发点和上下文执行统一版本已发布规则集合。
     *
     * <p>候选范围：请求未指定 {@code ruleIds} 时取本地和平台统一版本已发布规则，否则取指定规则；
     * 仅资产版本显式绑定请求 {@code triggerPoint} 的版本参与评估。
     */
    @Transactional
    public RuleEvaluateResponse evaluate(RuleEvaluateRequest request) {
        ContextSnapshotResponse snapshot = contextSnapshots.findById(request.contextSnapshotId());
        if (snapshot.status() != ContextSnapshotStatus.ACTIVE || snapshot.resources() == null) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "规则执行只能使用已生效标准上下文");
        }
        return evaluateContext(
            request.triggerPoint(),
            json.valueToTree(snapshot.resources()),
            request.eventId(),
            request.ruleIds(),
            snapshot.runtimeReleaseId()
        );
    }

    /**
     * 执行服务内已经完成真实性校验的上下文，供临床事件主链路复用。
     */
    @Transactional
    public RuleEvaluateResponse evaluateContext(String triggerPoint, JsonNode context,
                                                String eventId, List<String> ruleIds) {
        return evaluateContext(triggerPoint, context, eventId, ruleIds, null);
    }

    /**
     * 执行服务内已经完成真实性校验的上下文，可携带机构生效版本 ID 留证。
     */
    @Transactional
    public RuleEvaluateResponse evaluateContext(String triggerPoint, JsonNode context,
                                                String eventId, List<String> ruleIds,
                                                String runtimeReleaseId) {
        requireCanonicalTrigger(triggerPoint);
        String tenantId = requireCurrentTenant();
        List<String> selectedRuleIds = ruleIds == null ? List.of() : ruleIds;
        List<RuleDefinition> candidates = selectedRuleIds.isEmpty()
            ? effectiveActiveRules(tenantId)
            : selectedRuleIds.stream().map(ruleId -> findEffectiveRule(ruleId, tenantId)).toList();

        List<RuleRuntimeCandidate> executable = candidates.stream()
            .flatMap(rule -> runtimeCandidates(tenantId, rule, context).stream())
            .filter(candidate -> candidate.mode() != RuleRuntimeMode.INACTIVE)
            .filter(candidate -> candidate.mode() != RuleRuntimeMode.CANARY
                || isCanaryEligible(candidate.rule(), context))
            .filter(candidate -> triggerBindings.matches(
                requireRuleAssetVersion(candidate.rule(), candidate.version()),
                AssetTriggerPurpose.RULE_EXECUTION,
                triggerPoint
            ))
            .toList();
        executable = executable.stream()
            .sorted(Comparator
                .<RuleRuntimeCandidate>comparingInt(candidate -> candidate.rule().priority())
                .reversed()
            .thenComparing(candidate -> candidate.rule().ruleCode()))
            .toList();
        return evaluateCandidates(tenantId, triggerPoint, context, eventId, executable, runtimeReleaseId);
    }

    /**
     * 只执行机构生效版本锁定的确切规则版本，不再读取可变的当前激活版本。
     */
    @Transactional
    public RuleEvaluateResponse evaluatePinnedContext(
            String triggerPoint,
            JsonNode context,
            String eventId,
            List<RuntimeRuleReference> selectedRules,
            String runtimeReleaseId) {
        requireCanonicalTrigger(triggerPoint);
        String tenantId = requireCurrentTenant();
        if (runtimeReleaseId == null || runtimeReleaseId.isBlank()) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "机构生效版本 ID 不能为空");
        }
        List<RuleRuntimeCandidate> executable = (selectedRules == null
                ? List.<RuntimeRuleReference>of()
                : List.copyOf(selectedRules))
            .stream()
            .map(reference -> pinnedCandidate(tenantId, reference))
            .sorted(Comparator
                .<RuleRuntimeCandidate>comparingInt(candidate -> candidate.rule().priority())
                .reversed()
                .thenComparing(candidate -> candidate.rule().ruleCode()))
            .toList();
        return evaluateCandidates(
            tenantId, triggerPoint, context, eventId, executable, runtimeReleaseId.trim());
    }

    private RuleRuntimeCandidate pinnedCandidate(
            String executionTenantId,
            RuntimeRuleReference reference) {
        if (reference == null
                || reference.tenantId() == null || reference.tenantId().isBlank()
                || reference.ruleId() == null || reference.ruleId().isBlank()
                || reference.versionId() == null || reference.versionId().isBlank()) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "机构生效版本中的规则引用不完整");
        }
        if (!executionTenantId.equals(reference.tenantId())
                && !PlatformTenant.isPlatformTenant(reference.tenantId())) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "机构生效版本引用了其他租户的规则");
        }
        RuleDefinition rule = definitions
            .findByRuleIdAndTenantId(reference.ruleId(), reference.tenantId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_RULE_006, "机构生效版本中的规则不存在：" + reference.ruleId()));
        RuleVersion version = versions
            .findByVersionIdAndTenantId(reference.versionId(), reference.tenantId())
            .filter(candidate -> rule.ruleId().equals(candidate.ruleId()))
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_RULE_006,
                "机构生效版本中的规则版本不存在：" + reference.versionId()));
        if (rule.status() != RuleDefinitionStatus.PUBLISHED
                || version.status() != RuleVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "机构生效版本只能执行已发布规则版本");
        }
        return new RuleRuntimeCandidate(rule, version, RuleRuntimeMode.ACTIVE);
    }

    private RuleEvaluateResponse evaluateCandidates(
            String tenantId,
            String triggerPoint,
            JsonNode context,
            String eventId,
            List<RuleRuntimeCandidate> executable,
            String runtimeReleaseId) {
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
                    rule, version, tenantId, context, triggerPoint, eventId,
                    runtimeReleaseId, applicability);
            } else if (isSuppressed(rule, matchedRuleCodes)) {
                item = recordSuppressed(
                    rule, version, tenantId, context, triggerPoint, eventId,
                    runtimeReleaseId);
            } else {
                item = evaluateApplicableAndLog(
                    rule, version, tenantId, context, triggerPoint, eventId,
                    entry.mode() == RuleRuntimeMode.SHADOW, runtimeReleaseId);
            }
            items.add(item);
            if (item.hit()
                    && (entry.mode() == RuleRuntimeMode.ACTIVE
                        || entry.mode() == RuleRuntimeMode.CANARY)) {
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

    private List<RuleRuntimeCandidate> runtimeCandidates(
            String tenantId,
            RuleDefinition rule,
            JsonNode context) {
        String applicableScope = releaseApplicableScope(rule);
        Optional<RuleRuntimeCandidate> active = effectiveVersions.resolve(
                tenantId, rule, applicableScope)
            .map(resolved -> runtimeCandidate(resolved.rule(), resolved.version()));
        Optional<RuleRuntimeCandidate> staged =
            governedPrePublicationCandidate(rule, applicableScope);
        if (staged.isEmpty()) {
            return active.stream().toList();
        }
        RuleRuntimeCandidate candidate = staged.get();
        boolean sameVersion = active
            .map(current -> current.version().versionId().equals(candidate.version().versionId()))
            .orElse(false);
        if (sameVersion) {
            return List.of(candidate);
        }
        if (candidate.mode() == RuleRuntimeMode.SHADOW) {
            List<RuleRuntimeCandidate> result = new ArrayList<>();
            active.ifPresent(result::add);
            result.add(candidate);
            return List.copyOf(result);
        }
        if (candidate.mode() == RuleRuntimeMode.CANARY) {
            return isCanaryEligible(candidate.rule(), context)
                ? List.of(candidate)
                : active.stream().toList();
        }
        return active.stream().toList();
    }

    private RuleRuntimeCandidate runtimeCandidate(RuleDefinition rule, RuleVersion version) {
        RuleRuntimeMode mode = assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                rule.tenantId(),
                VersionedAssetType.RULE,
                rule.ruleCode(),
                assetVersionNo(version))
            .map(assetVersion -> {
                if (assetVersion.status() == AssetVersionStatus.PUBLISHED) {
                    return RuleRuntimeMode.ACTIVE;
                }
                if (assetVersion.status() == AssetVersionStatus.DRAFT
                        && governanceService.requireGovernance(
                            rule.tenantId(), version.versionId()).state() == RuleGovernanceState.SHADOW) {
                    return RuleRuntimeMode.SHADOW;
                }
                return RuleRuntimeMode.INACTIVE;
            })
            .orElse(RuleRuntimeMode.INACTIVE);
        return new RuleRuntimeCandidate(rule, version, mode);
    }

    private Optional<RuleRuntimeCandidate> governedPrePublicationCandidate(
            RuleDefinition rule,
            String applicableScope) {
        if (rule == null || rule.activeVersionId() == null || rule.activeVersionId().isBlank()) {
            return Optional.empty();
        }
        return versions.findByVersionIdAndTenantId(rule.activeVersionId(), rule.tenantId())
            .flatMap(version -> assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                    rule.tenantId(),
                    VersionedAssetType.RULE,
                    rule.ruleCode(),
                    assetVersionNo(version))
                .filter(assetVersion -> applicableScope.equals(assetVersion.applicableScope()))
                .filter(assetVersion -> assetVersion.status() == AssetVersionStatus.DRAFT)
                .map(assetVersion -> governanceService.requireGovernance(
                    rule.tenantId(), version.versionId()).state())
                .map(state -> switch (state) {
                    case SHADOW -> new RuleRuntimeCandidate(rule, version, RuleRuntimeMode.SHADOW);
                    case CANARY -> new RuleRuntimeCandidate(rule, version, RuleRuntimeMode.CANARY);
                    default -> new RuleRuntimeCandidate(rule, version, RuleRuntimeMode.INACTIVE);
                }));
    }

    private static boolean isCanaryEligible(RuleDefinition rule, JsonNode context) {
        String mpi = patientId(context);
        if (mpi == null) {
            return false;
        }
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256")
                .digest((mpi + "|" + rule.ruleCode()).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行时不支持 SHA-256", exception);
        }
        int prefix = (hash[0] & 0xff) << 24
            | (hash[1] & 0xff) << 16
            | (hash[2] & 0xff) << 8
            | (hash[3] & 0xff);
        return Integer.remainderUnsigned(prefix, 100) < 10;
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

    /**
     * 基于当前规则版本的真实脱敏金标准样本执行历史回测，产出临床有效性指标。
     */
    @Transactional
    public RuleBacktestResponse runBacktest(String ruleId, RuleBacktestRequest request) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        List<RuleTestCase> cases =
            testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(version.versionId(), tenantId);
        ensureBacktestGoldStandard(cases);

        int truePositive = 0;
        int falsePositive = 0;
        int trueNegative = 0;
        int falseNegative = 0;
        List<String> falsePositiveExamples = new ArrayList<>();
        List<String> falseNegativeExamples = new ArrayList<>();
        for (RuleTestCase testCase : cases) {
            BacktestCaseOutcome outcome = evaluateBacktestCase(version, testCase);
            if (outcome.expectedHit() && outcome.actualHit()) {
                truePositive++;
            } else if (!outcome.expectedHit() && outcome.actualHit()) {
                falsePositive++;
                falsePositiveExamples.add(outcome.caseId());
            } else if (!outcome.expectedHit()) {
                trueNegative++;
            } else {
                falseNegative++;
                falseNegativeExamples.add(outcome.caseId());
            }
        }

        int sampleCount = cases.size();
        RuleBacktestRun saved = backtests.save(new RuleBacktestRun(
            null,
            "rbt-" + UUID.randomUUID(),
            tenantId,
            rule.ruleId(),
            version.versionId(),
            request == null ? null : trimToNull(request.cohortRef()),
            sampleCount,
            truePositive,
            falsePositive,
            trueNegative,
            falseNegative,
            ratio(truePositive, truePositive + falseNegative),
            ratio(trueNegative, trueNegative + falsePositive),
            ratio(truePositive + trueNegative, sampleCount),
            ratio(truePositive + falsePositive, sampleCount),
            writeObject(falsePositiveExamples),
            writeObject(falseNegativeExamples),
            Instant.now(),
            RequestContext.currentUserId().orElse("system"),
            RequestContext.currentTraceId()
        ));
        auditRecorder.record(
            AuditAction.EXECUTE,
            "rule_backtest_run",
            saved.backtestId(),
            "执行规则历史回测 " + rule.ruleId() + "/" + sampleCount
        );
        return toBacktestResponse(saved);
    }

    /**
     * 基于真实生产执行日志窗口记录规则上线后命中率漂移快照。
     */
    @Transactional
    public RuleDriftSnapshotResponse captureDriftSnapshot(
            String ruleId,
            RuleDriftSnapshotRequest request) {
        if (request == null || request.windowStart() == null || request.windowEnd() == null
                || !request.windowStart().isBefore(request.windowEnd())) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "漂移监测窗口必须包含合法开始与结束时间");
        }
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        RuleVersion version = findVersion(rule.activeVersionId(), tenantId);
        ensureDriftGovernanceReady(tenantId, version.versionId());
        RuleBacktestRun baseline = resolveDriftBaseline(tenantId, rule.ruleId(), request.baselineBacktestId());
        long sampleCount = executions.countProductionByRuleBetween(
            tenantId, rule.ruleId(), request.windowStart(), request.windowEnd());
        if (sampleCount <= 0) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "漂移监测窗口内没有真实生产执行样本");
        }
        long hitCount = executions.countProductionHitsByRuleBetween(
            tenantId, rule.ruleId(), request.windowStart(), request.windowEnd());
        double currentFireRate = ratio(hitCount, sampleCount);
        double driftDelta = roundRate(currentFireRate - baseline.fireRate());
        double threshold = thresholdOrDefault(request.threshold());
        RuleDriftStatus status = Math.abs(driftDelta) > threshold
            ? RuleDriftStatus.WARNING
            : RuleDriftStatus.STABLE;
        RuleDriftSnapshot saved = driftSnapshots.save(new RuleDriftSnapshot(
            null,
            "rds-" + UUID.randomUUID(),
            tenantId,
            rule.ruleId(),
            version.versionId(),
            baseline.backtestId(),
            request.windowStart(),
            request.windowEnd(),
            sampleCount,
            hitCount,
            baseline.fireRate(),
            currentFireRate,
            driftDelta,
            threshold,
            status,
            Instant.now(),
            RequestContext.currentUserId().orElse("system"),
            RequestContext.currentTraceId()
        ));
        auditRecorder.record(
            AuditAction.EXECUTE,
            "rule_drift_snapshot",
            saved.driftId(),
            "记录规则漂移监测 " + rule.ruleId() + "/" + status.name()
        );
        return toDriftResponse(saved);
    }

    /**
     * 查看最新历史回测结果；无数据时返回空，由前端呈现待回测状态。
     */
    @Transactional(readOnly = true)
    public RuleBacktestResponse latestBacktest(String ruleId) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        return backtests.findLatestByTenantIdAndRuleId(tenantId, rule.ruleId())
            .map(this::toBacktestResponse)
            .orElse(null);
    }

    /**
     * 查看最新漂移快照；无数据时返回空，由前端呈现待监测状态。
     */
    @Transactional(readOnly = true)
    public RuleDriftSnapshotResponse latestDriftSnapshot(String ruleId) {
        String tenantId = requireCurrentTenant();
        RuleDefinition rule = findRule(ruleId, tenantId);
        return driftSnapshots.findLatestByTenantIdAndRuleId(tenantId, rule.ruleId())
            .map(this::toDriftResponse)
            .orElse(null);
    }

    private record BacktestCaseOutcome(String caseId, boolean expectedHit, boolean actualHit) {}

    private BacktestCaseOutcome evaluateBacktestCase(RuleVersion version, RuleTestCase testCase) {
        try {
            JsonNode input = readJson(testCase.inputPayload());
            RuleApplicabilityDecision applicability = evaluateApplicability(version, input);
            RuleDslEvaluation evaluation = applicability.applicable()
                ? evaluator.evaluate(readJson(version.dslJson()), input)
                : notApplicableEvaluation(applicability);
            return new BacktestCaseOutcome(
                testCase.caseId(),
                Boolean.TRUE.equals(testCase.expectedHit()),
                evaluation.hit()
            );
        } catch (ApiException exception) {
            throw new ApiException(
                ErrorCode.ENG_RULE_006,
                "规则回测样本无法求值: " + testCase.caseId(),
                exception
            );
        }
    }

    private void ensureBacktestGoldStandard(List<RuleTestCase> cases) {
        if (cases == null || cases.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "规则回测必须先配置真实脱敏金标准样本");
        }
        boolean hasPositive = cases.stream().anyMatch(testCase -> Boolean.TRUE.equals(testCase.expectedHit()));
        boolean hasNegative = cases.stream().anyMatch(testCase -> !Boolean.TRUE.equals(testCase.expectedHit()));
        if (!hasPositive || !hasNegative) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "规则回测金标准必须同时包含阳性与阴性样本");
        }
    }

    private void ensureDriftGovernanceReady(String tenantId, String versionId) {
        RuleGovernance governance = governanceService.requireGovernance(tenantId, versionId);
        if (governance.state() != RuleGovernanceState.FULL
                && governance.state() != RuleGovernanceState.MONITOR) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "只有全量或监测阶段规则允许记录漂移快照");
        }
    }

    private RuleBacktestRun resolveDriftBaseline(
            String tenantId,
            String ruleId,
            String baselineBacktestId) {
        String explicitId = trimToNull(baselineBacktestId);
        Optional<RuleBacktestRun> baseline = explicitId == null
            ? backtests.findLatestByTenantIdAndRuleId(tenantId, ruleId)
            : backtests.findByTenantIdAndBacktestId(tenantId, explicitId);
        RuleBacktestRun resolved = baseline
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_006, "漂移监测必须先完成规则历史回测"));
        if (!ruleId.equals(resolved.ruleId())) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "漂移基线不属于当前规则");
        }
        return resolved;
    }

    private RuleBacktestResponse toBacktestResponse(RuleBacktestRun run) {
        return new RuleBacktestResponse(
            run.backtestId(),
            run.ruleId(),
            run.versionId(),
            run.cohortRef(),
            run.sampleCount(),
            run.truePositiveCount(),
            run.falsePositiveCount(),
            run.trueNegativeCount(),
            run.falseNegativeCount(),
            run.sensitivity(),
            run.specificity(),
            run.accuracy(),
            run.fireRate(),
            readStringList(run.falsePositiveExamplesJson()),
            readStringList(run.falseNegativeExamplesJson()),
            run.createdAt(),
            run.traceId()
        );
    }

    private RuleDriftSnapshotResponse toDriftResponse(RuleDriftSnapshot snapshot) {
        return new RuleDriftSnapshotResponse(
            snapshot.driftId(),
            snapshot.ruleId(),
            snapshot.versionId(),
            snapshot.baselineBacktestId(),
            snapshot.windowStart(),
            snapshot.windowEnd(),
            snapshot.sampleCount(),
            snapshot.hitCount(),
            snapshot.baselineFireRate(),
            snapshot.currentFireRate(),
            snapshot.driftDelta(),
            snapshot.threshold(),
            snapshot.status(),
            snapshot.createdAt(),
            snapshot.traceId()
        );
    }

    private List<String> readStringList(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        try {
            return json.readerForListOf(String.class).readValue(source);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_RULE_005, "规则指标样例解析失败", exception);
        }
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return roundRate((double) numerator / denominator);
    }

    private static double thresholdOrDefault(Double threshold) {
        if (threshold == null) {
            return 0.10;
        }
        return roundRate(threshold);
    }

    private static double roundRate(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
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
                ? evaluateTestCaseDsl(version.tenantId(), dsl, input, testCase)
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

    private RuleDslEvaluation evaluateTestCaseDsl(
            String tenantId,
            JsonNode dsl,
            JsonNode input,
            RuleTestCase testCase) {
        String runtimeReleaseId = testCaseRuntimeReleaseId(testCase);
        return runtimeReleaseId == null
            ? evaluator.evaluate(dsl, input)
            : evaluator.evaluate(dsl, input, tenantId, runtimeReleaseId);
    }

    private String testCaseRuntimeReleaseId(RuleTestCase testCase) {
        String snapshotId = trimToNull(testCase.contextSnapshotId());
        if (snapshotId == null) {
            return null;
        }
        ContextSnapshotResponse snapshot = contextSnapshots.findById(snapshotId);
        if (snapshot.status() != ContextSnapshotStatus.ACTIVE
                || snapshot.runtimeReleaseId() == null
                || snapshot.runtimeReleaseId().isBlank()) {
            throw new ApiException(
                ErrorCode.ENG_RULE_006,
                "规则验证用例缺少可用机构生效版本: " + testCase.caseId());
        }
        return snapshot.runtimeReleaseId().trim();
    }

    private RuleEvaluationItem evaluateAndLog(RuleDefinition rule, RuleVersion version, String executionTenantId,
                                              JsonNode context, String triggerPoint, String eventId) {
        RuleApplicabilityDecision applicability = evaluateApplicability(version, context);
        if (!applicability.applicable()) {
            return recordNotApplicable(
                rule, version, executionTenantId, context, triggerPoint, eventId, null, applicability);
        }
        return evaluateApplicableAndLog(
            rule, version, executionTenantId, context, triggerPoint, eventId, false, null);
    }

    private RuleEvaluationItem evaluateApplicableAndLog(
            RuleDefinition rule,
            RuleVersion version,
            String executionTenantId,
            JsonNode context,
            String triggerPoint,
            String eventId,
            boolean shadowMode,
            String runtimeReleaseId) {
        RuleDslEvaluation evaluation = evaluator.evaluate(
            readJson(version.dslJson()),
            context,
            executionTenantId,
            runtimeReleaseId
        );
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
            runtimeReleaseId, triggerPoint, eventId, RequestContext.currentUserId().orElse(null),
            patientId, encounterId(context), semanticKey,
            digest(context), evaluation.hit(), evaluation.severity(), writeObject(evaluation.actions()),
            writeJson(explanation), status, null, null,
            deduplicatedFromExecutionId, now, now, RequestContext.currentTraceId()));
        transitions.record(
            EXECUTION_ENTITY, log.executionId(), null, status.name(),
            shadowMode ? "RECORD_SHADOW_RULE" : "EXECUTE_RULE", null);
        auditRecorder.record(AuditAction.EXECUTE, EXECUTION_ENTITY, log.executionId(), "执行规则 " + rule.ruleId());
        if (status == RuleExecutionStatus.SUCCESS) {
            domainEvents.ruleFired(new RuleFiredEvent(
                executionTenantId,
                log.traceId(),
                runtimeReleaseId,
                rule.ruleId(),
                rule.ruleCode(),
                version.versionId(),
                log.executionId(),
                triggerPoint,
                eventId,
                patientId,
                encounterId(context),
                evaluation.severity() == null ? null : evaluation.severity().name(),
                actionCodes(evaluation.actions()),
                now));
        }
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
            String runtimeReleaseId,
            RuleApplicabilityDecision applicability) {
        String executionId = "rex-" + UUID.randomUUID();
        Instant now = Instant.now();
        JsonNode explanation = applicabilityExplanation(applicability);
        RuleExecutionLog log = executions.save(new RuleExecutionLog(
            null, executionId, executionTenantId, rule.ruleId(), version.versionId(),
            runtimeReleaseId, triggerPoint, eventId, RequestContext.currentUserId().orElse(null),
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
            String eventId,
            String runtimeReleaseId) {
        String executionId = "rex-" + UUID.randomUUID();
        Instant now = Instant.now();
        JsonNode explanation = json.createObjectNode()
            .put("title", "规则已被高阶规则抑制")
            .put("reason", "本次执行已命中抑制规则 " + rule.suppressedBy())
            .put("suppressedBy", rule.suppressedBy());
        RuleExecutionLog log = executions.save(new RuleExecutionLog(
            null, executionId, executionTenantId, rule.ruleId(), version.versionId(),
            runtimeReleaseId, triggerPoint, eventId, RequestContext.currentUserId().orElse(null),
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
        RuleDefinition rule = findRule(execution.ruleId(), tenantId);

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
        domainEvents.overrideCaptured(new OverrideCapturedEvent(
            tenantId,
            saved.traceId(),
            execution.runtimeReleaseId(),
            saved.overrideId(),
            saved.executionId(),
            saved.ruleId(),
            rule.ruleCode(),
            saved.versionId(),
            saved.patientId(),
            saved.encounterId(),
            saved.actionCode().name(),
            saved.overrideReason(),
            saved.overriddenBy(),
            saved.overriddenAt()));
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

    private static List<String> actionCodes(List<RuleActionResult> actions) {
        return actions == null ? List.of() : actions.stream()
            .map(RuleActionResult::actionCode)
            .filter(Objects::nonNull)
            .map(Enum::name)
            .distinct()
            .toList();
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
                "规则发布必须覆盖阳性、阴性、边界、冲突四类验证用例");
        }
    }

    private RuleImpactResponse impactFor(RuleDefinition rule, RuleVersion version) {
        RuleImpactIndexSnapshot indexSnapshot = impactIndex.analyze(rule.tenantId(), rule, version);
        List<String> unavailable = indexSnapshot.unavailableScopes();
        List<RuleImpactObject> affectedRules = List.of(new RuleImpactObject(
            "RULE_DEFINITION", rule.ruleId(), rule.name(), "当前规则版本将被发布或替换"));
        List<String> referencedAssets =
            AssetReferenceConsistency.referenceSummaries(readJson(version.dslJson()));
        String status = unavailable.isEmpty() ? "COMPLETE" : "PARTIAL";
        String digest = impactDigest(
            rule, version, status, unavailable, affectedRules,
            indexSnapshot.affectedPathways(), indexSnapshot.inPathPatients(),
            indexSnapshot.integrationAdapters(), referencedAssets);
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

    private void ensureRuleStableAssetReferences(RuleDefinition rule, RuleVersion version) {
        AssetReferenceConsistency.requireStableAssetReferences(
            readJson(version.dslJson()),
            ErrorCode.ENG_RULE_004,
            "规则 " + rule.ruleCode());
    }

    private void ensureNoStaticConflicts(RuleDefinition candidate, RuleVersion candidateVersion) {
        AssetVersion candidateAssetVersion = requireRuleAssetVersion(candidate, candidateVersion);
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
            .flatMap(rule -> {
                RuleVersion targetVersion =
                    findVersion(rule.activeVersionId(), rule.tenantId());
                AssetVersion targetAssetVersion =
                    requireRuleAssetVersion(rule, targetVersion);
                if (!triggerBindings.overlaps(
                        candidateAssetVersion,
                        targetAssetVersion,
                        AssetTriggerPurpose.RULE_EXECUTION)) {
                    return java.util.stream.Stream.empty();
                }
                return java.util.stream.Stream.of(new RuleConflictTarget(
                    rule.ruleCode(),
                    readJson(targetVersion.dslJson())
                ));
            })
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
            boolean requirePublishedSource) {
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
        if (!triggerBindings.covers(
                requireRuleAssetVersion(source, sourceVersion),
                requireRuleAssetVersion(candidate, candidateVersion),
                AssetTriggerPurpose.RULE_EXECUTION)) {
            throw new ApiException(
                ErrorCode.ENG_RULE_004,
                "抑制来源规则 " + sourceCode + " 必须覆盖当前规则的全部触发点");
        }
        if (requirePublishedSource && !hasPublishedUnifiedVersion(source)) {
            throw new ApiException(
                ErrorCode.ENG_RULE_004,
                "抑制来源规则 " + sourceCode + " 尚未发布");
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
                                List<RuleImpactObject> integrationAdapters,
                                List<String> referencedAssets) {
        return digestText(String.join("|",
            rule.tenantId(), rule.ruleId(), version.versionId(), rule.riskLevel().name(), status,
            impactObjectSignature(affectedRules),
            impactObjectSignature(affectedPathways),
            impactObjectSignature(inPathPatients),
            impactObjectSignature(integrationAdapters),
            referencedAssets.toString(),
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

    private void ensureEditableDraft(RuleDefinition rule, RuleVersion version) {
        boolean initialDraft = rule.status() == RuleDefinitionStatus.DRAFT;
        boolean nextVersionDraft = rule.status() == RuleDefinitionStatus.PUBLISHED;
        if ((!initialDraft && !nextVersionDraft)
                || version == null
                || version.status() != RuleVersionStatus.DRAFT
                || !version.versionId().equals(rule.activeVersionId())) {
            throw new ApiException(
                ErrorCode.ENG_RULE_006,
                "仅当前草稿版本允许修改: " + rule.ruleId()
            );
        }
    }

    private void ensurePublishedIdentityMetadataUnchanged(
            RuleDefinition rule,
            RuleUpdateRequest request) {
        if (rule.status() != RuleDefinitionStatus.PUBLISHED) {
            return;
        }
        RuleAuthoringMode requestedMode =
            request.authoringMode() == null ? RuleAuthoringMode.DSL : request.authoringMode();
        RuleRiskLevel requestedRisk =
            request.riskLevel() == null ? RuleRiskLevel.MEDIUM : request.riskLevel();
        int requestedPriority = request.priority() == null ? 100 : request.priority();
        int requestedDedupe = request.dedupeWindowSeconds() == null
            ? 0
            : request.dedupeWindowSeconds();
        boolean unchanged =
            Objects.equals(rule.ruleCode(), request.ruleCode())
                && Objects.equals(rule.name(), request.name())
                && rule.ruleType() == request.ruleType()
                && rule.authoringMode() == requestedMode
                && rule.riskLevel() == requestedRisk
                && rule.priority() == requestedPriority
                && Objects.equals(trimToNull(rule.suppressedBy()), trimToNull(request.suppressedBy()))
                && rule.dedupeWindowSeconds() == requestedDedupe
                && Objects.equals(rule.applicableOrgUnitId(), request.applicableOrgUnitId());
        if (!unchanged) {
            throw new ApiException(
                ErrorCode.ENG_RULE_006,
                "规则稳定编码、风险和适用域元数据已发布，下一版本只能修改 DSL、解释和来源说明"
            );
        }
    }

    private void ensureGovernanceDraft(String tenantId, String versionId) {
        RuleGovernance governance = governanceService.requireGovernance(tenantId, versionId);
        if (governance.state() != RuleGovernanceState.DRAFT) {
            throw new ApiException(ErrorCode.ENG_RULE_006, "只有治理草稿阶段可以修改规则内容");
        }
    }

    private List<RuleVersion> versionHistory(RuleDefinition rule, RuleVersion current) {
        List<RuleVersion> history =
            versions.findByRuleIdAndTenantIdOrderByVersionNoDesc(rule.ruleId(), rule.tenantId());
        return history == null || history.isEmpty() ? List.of(current) : List.copyOf(history);
    }

    private void validateDsl(JsonNode dsl) {
        AssetReferenceConsistency.requireStableAssetReferences(
            dsl,
            ErrorCode.ENG_RULE_001,
            "规则 DSL"
        );
        rejectUnknownContextFields(dsl);
        evaluator.evaluate(dslForStaticValidation(dsl), json.createObjectNode());
        applicabilityService.validateDsl(dsl);
        if (dsl.has("trigger")) {
            throw new ApiException(
                ErrorCode.ENG_RULE_001,
                "规则 DSL 不得包含 trigger，触发点由资产版本多触发绑定维护");
        }
        if (!dsl.has("then") || !dsl.has("explain")) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则 DSL 缺少 then 或 explain");
        }
    }

    private JsonNode dslForStaticValidation(JsonNode dsl) {
        if (dsl == null || !dsl.isObject()) {
            return dsl;
        }
        JsonNode then = dsl.path("then");
        if (!then.isArray()) {
            return dsl;
        }
        boolean hasActionCardReference = false;
        for (JsonNode action : then) {
            if (action.isObject() && action.has("actionCardRef")) {
                hasActionCardReference = true;
                break;
            }
        }
        if (!hasActionCardReference) {
            return dsl;
        }

        ObjectNode normalizedDsl = dsl.deepCopy();
        ArrayNode normalizedThen = json.createArrayNode();
        for (JsonNode action : then) {
            if (!action.isObject() || !action.has("actionCardRef")) {
                normalizedThen.add(action.deepCopy());
                continue;
            }
            normalizedThen.add(actionCardReferenceForValidation(action));
        }
        normalizedDsl.set("then", normalizedThen);
        return normalizedDsl;
    }

    private ObjectNode actionCardReferenceForValidation(JsonNode action) {
        JsonNode rawRef = action.get("actionCardRef");
        if (rawRef == null || !rawRef.isTextual() || trimToNull(rawRef.asText()) == null) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "临床提示卡引用 actionCardRef 必须是非空文本");
        }
        ObjectNode validationAction = json.createObjectNode();
        validationAction.put("actionCardRef", trimToNull(rawRef.asText()));
        validationAction.put("actionCode", RuleActionCode.REMIND.name());
        validationAction.put("atSeverity", RuleRiskLevel.LOW.name());
        validationAction.put("indicator", "info");
        validationAction.put("summary", "临床提示卡引用待机构生效版本物化");
        validationAction.put("detail", "规则草稿仅校验提示卡引用格式；执行时由机构生效版本物化为真实临床提示卡");
        ObjectNode source = json.createObjectNode();
        source.put("label", "临床提示卡引用");
        validationAction.set("source", source);
        validationAction.set("suggestions", json.createArrayNode());
        validationAction.set("overrideReasons", json.createArrayNode());
        validationAction.put("requiresPhysicianConfirmation", false);
        return validationAction;
    }

    private void rejectUnknownContextFields(JsonNode dsl) {
        List<String> unknown = ContextFieldPathPolicy.unknownFields(
            ContextFieldPathPolicy.ruleDslFields(dsl));
        if (!unknown.isEmpty()) {
            throw new ApiException(
                ErrorCode.ENG_RULE_001,
                "字段目录不存在：" + String.join(", ", unknown)
            );
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

    private List<RuleParameterBinding> parameterBindingRecords(
        JsonNode dsl,
        JsonNode bindingValues,
        String tenantId,
        String versionId,
        Instant now,
        String actor,
        String traceId
    ) {
        List<RuleParameterSpec> specs = readParameterSpecs(dsl);
        JsonNode values = normalizeParameterBindingValues(bindingValues);
        if (specs.isEmpty()) {
            if (values.size() > 0) {
                throw new ApiException(
                    ErrorCode.ENG_RULE_001,
                    "规则 DSL 未声明 meta.parameters，不能保存参数绑定"
                );
            }
            return List.of();
        }

        LinkedHashMap<String, RuleParameterSpec> byKey = new LinkedHashMap<>();
        for (RuleParameterSpec spec : specs) {
            byKey.put(spec.key(), spec);
        }
        values.fieldNames().forEachRemaining(key -> {
            if (!byKey.containsKey(key)) {
                throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数未在 schema 中声明: " + key);
            }
        });

        List<RuleParameterBinding> records = new ArrayList<>();
        for (RuleParameterSpec spec : specs) {
            JsonNode value = values.path(spec.key());
            if (isMissingParameterValue(value)) {
                if (spec.required()) {
                    throw new ApiException(ErrorCode.ENG_RULE_001, "缺少必填规则参数: " + spec.key());
                }
                continue;
            }
            validateParameterValue(spec, value);
            records.add(new RuleParameterBinding(
                null,
                versionId,
                tenantId,
                spec.key(),
                writeJson(value),
                now,
                actor,
                traceId
            ));
        }
        return records;
    }

    private List<RuleParameterSpec> readParameterSpecs(JsonNode dsl) {
        JsonNode parameters = dsl.path("meta").path("parameters");
        if (parameters.isMissingNode() || parameters.isNull()) {
            return List.of();
        }
        if (!parameters.isArray()) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则 DSL meta.parameters 必须是数组");
        }

        LinkedHashMap<String, RuleParameterSpec> specs = new LinkedHashMap<>();
        for (JsonNode parameter : parameters) {
            if (!parameter.isObject()) {
                throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数 schema 项必须是对象");
            }
            String key = parameter.path("key").asText("").trim();
            String valueType = parameter.path("valueType").asText("").trim();
            if (key.isBlank()) {
                throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数 schema 缺少 key");
            }
            if (specs.containsKey(key)) {
                throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数 schema key 重复: " + key);
            }
            if (!isAllowedParameterValueType(valueType)) {
                throw new ApiException(ErrorCode.ENG_RULE_001, "不支持的规则参数类型: " + valueType);
            }
            specs.put(key, new RuleParameterSpec(key, valueType, parameter.path("required").asBoolean(false)));
        }
        return List.copyOf(specs.values());
    }

    private JsonNode normalizeParameterBindingValues(JsonNode bindingValues) {
        if (bindingValues == null || bindingValues.isMissingNode() || bindingValues.isNull()) {
            return json.createObjectNode();
        }
        if (!bindingValues.isObject()) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数绑定必须是 JSON 对象");
        }
        return bindingValues;
    }

    private static boolean isAllowedParameterValueType(String valueType) {
        return Set.of("CODE", "TEXT", "DECIMAL", "INTEGER", "BOOLEAN", "VALUE_SET", "ORG_SCOPE")
            .contains(valueType);
    }

    private static boolean isMissingParameterValue(JsonNode value) {
        return value == null
            || value.isMissingNode()
            || value.isNull()
            || (value.isTextual() && value.asText().trim().isBlank());
    }

    private static void validateParameterValue(RuleParameterSpec spec, JsonNode value) {
        switch (spec.valueType()) {
            case "CODE", "TEXT" -> requireTextParameter(spec, value);
            case "DECIMAL" -> requireNumberParameter(spec, value);
            case "INTEGER" -> requireIntegerParameter(spec, value);
            case "BOOLEAN" -> requireBooleanParameter(spec, value);
            case "VALUE_SET", "ORG_SCOPE" -> requireStructuredOrTextParameter(spec, value);
            default -> throw new ApiException(ErrorCode.ENG_RULE_001, "不支持的规则参数类型: " + spec.valueType());
        }
    }

    private static void requireTextParameter(RuleParameterSpec spec, JsonNode value) {
        if (!value.isTextual() || value.asText().trim().isBlank()) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数必须是非空文本: " + spec.key());
        }
    }

    private static void requireNumberParameter(RuleParameterSpec spec, JsonNode value) {
        if (!value.isNumber()) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数必须是数字: " + spec.key());
        }
    }

    private static void requireIntegerParameter(RuleParameterSpec spec, JsonNode value) {
        if (!value.isIntegralNumber()) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数必须是整数: " + spec.key());
        }
    }

    private static void requireBooleanParameter(RuleParameterSpec spec, JsonNode value) {
        if (!value.isBoolean()) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数必须是布尔值: " + spec.key());
        }
    }

    private static void requireStructuredOrTextParameter(RuleParameterSpec spec, JsonNode value) {
        if (value.isTextual()) {
            requireTextParameter(spec, value);
            return;
        }
        if ((value.isObject() || value.isArray()) && value.size() > 0) {
            return;
        }
        throw new ApiException(ErrorCode.ENG_RULE_001, "规则参数必须是非空文本、对象或数组: " + spec.key());
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
        return direct
            .or(() -> findPlatformRuleForTenant(ruleId, tenantId))
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_RULE_002, "规则不存在: " + ruleId));
    }

    private Optional<RuleDefinition> resolveEffectiveRuleForCurrentOrg(RuleDefinition candidate, String tenantId) {
        String targetOrgUnitId = targetOrgUnitId();
        if (targetOrgUnitId == null) {
            return Optional.empty();
        }
        return resolveEffectiveRuleForOrg(candidate, tenantId, targetOrgUnitId, true);
    }

    private Optional<RuleDefinition> resolveEffectiveRuleForOrg(
            RuleDefinition candidate,
            String tenantId,
            String targetOrgUnitId,
            boolean failOnDisabled) {
        ResolvedAssetVersion resolved = inheritanceResolver.resolve(new InheritanceResolveQuery(
            tenantId,
            VersionedAssetType.RULE,
            candidate.ruleCode(),
            releaseApplicableScope(candidate),
            targetOrgUnitId
        ));
        if (resolved.disabled()) {
            if (!failOnDisabled) {
                return Optional.empty();
            }
            throw new ApiException(ErrorCode.ENG_RULE_002, "规则已在当前组织停用");
        }
        if (resolved.version() == null) {
            if (!failOnDisabled) {
                return Optional.empty();
            }
            throw new ApiException(ErrorCode.ENG_RULE_002, "当前组织未解析到有效规则版本");
        }
        AssetVersion assetVersion = resolved.version();
        int versionNo = AssetVersionNumbers.intSequence(
            assetVersion.versionNo(), "规则统一版本号");
        return definitions.findByTenantIdAndRuleCode(assetVersion.tenantId(), candidate.ruleCode())
            .flatMap(rule -> versions.findByRuleIdAndTenantIdAndVersionNo(
                    rule.ruleId(), assetVersion.tenantId(), versionNo)
                .map(version -> copyRule(rule, rule.status(), version.versionId(),
                    rule.updatedAt(), rule.updatedBy(), rule.traceId())));
    }

    private static String assetVersionNo(RuleVersion version) {
        return AssetVersionNumbers.canonical(version.versionNo());
    }

    private Optional<RuleDefinition> findPlatformRuleForTenant(String ruleId, String tenantId) {
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            return Optional.empty();
        }
        return definitions.findByRuleIdAndTenantId(ruleId, PlatformTenant.ID)
            .map(platformRule -> definitions.findByTenantIdAndRuleCode(tenantId, platformRule.ruleCode())
                .filter(this::hasPublishedUnifiedVersion)
                .orElse(platformRule));
    }

    private List<RuleDefinition> effectiveActiveRules(String tenantId) {
        LinkedHashMap<String, RuleDefinition> byCode = new LinkedHashMap<>();
        definitions.findPublishedByTenantId(tenantId)
            .forEach(rule -> byCode.put(rule.ruleCode(), rule));
        if (!PlatformTenant.isPlatformTenant(tenantId)) {
            for (RuleDefinition rule : definitions.findPublishedByTenantId(PlatformTenant.ID)) {
                byCode.putIfAbsent(rule.ruleCode(), rule);
            }
        }
        return List.copyOf(byCode.values());
    }

    private boolean requiresEffectiveRuleMerge(String tenantId, String status) {
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            return false;
        }
        return status == null || RuleDefinitionStatus.PUBLISHED.name().equals(status);
    }

    private boolean hasPublishedUnifiedVersion(RuleDefinition rule) {
        return effectiveVersions.resolve(
                rule.tenantId(), rule, releaseApplicableScope(rule))
            .filter(resolved -> rule.tenantId().equals(resolved.assetVersion().tenantId()))
            .isPresent();
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
        return scope.nearestOrgUnitId();
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
            status, activeVersionId, source.applicableOrgUnitId(),
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

    private RuleTestCase copyTestCaseToVersion(
            RuleTestCase source,
            String targetVersionId,
            Instant now,
            String actor,
            String traceId) {
        return new RuleTestCase(
            null,
            "rtc-" + UUID.randomUUID(),
            source.tenantId(),
            source.ruleId(),
            targetVersionId,
            source.caseType(),
            source.contextSnapshotId(),
            source.inputPayload(),
            source.expectedHit(),
            source.expectedSeverity(),
            source.expectedActionCode(),
            null,
            RuleTestCaseStatus.NOT_RUN,
            null,
            null,
            now,
            actor,
            now,
            actor,
            traceId
        );
    }

    private record RuleAssetContent(
        String ruleCode,
        String name,
        RuleType ruleType,
        RuleAuthoringMode authoringMode,
        RuleRiskLevel riskLevel,
        String applicableOrgUnitId,
        Integer versionNo,
        String sourceRef,
        String changeSummary,
        JsonNode dsl,
        JsonNode explanation
    ) {}
}
