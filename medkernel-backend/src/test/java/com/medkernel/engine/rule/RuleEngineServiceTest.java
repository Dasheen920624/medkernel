package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextFieldCatalogAssets;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.event.EngineDomainEventPort;
import com.medkernel.engine.event.OverrideCapturedEvent;
import com.medkernel.engine.event.RuleFiredEvent;
import com.medkernel.engine.terminology.MappingCoverageItem;
import com.medkernel.engine.terminology.TerminologyCoverageGate;
import com.medkernel.engine.terminology.TerminologyCoverageIssue;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetDependencyDeclaration;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.AssetTriggerBindingInput;
import com.medkernel.engine.versioning.AssetTriggerBindingService;
import com.medkernel.engine.versioning.AssetTriggerPurpose;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionPublishEvidence;
import com.medkernel.engine.versioning.VersionPublishQualityGate;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleasePlan;
import com.medkernel.engine.versioning.VersionReleaseScopeType;
import com.medkernel.engine.versioning.VersionReleaseStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.medkernel.engine.context.ContextSnapshotServiceFixtures.validResources;

class RuleEngineServiceTest {

    private RuleDefinitionRepository definitions;
    private RuleVersionRepository versions;
    private RuleParameterBindingRepository parameterBindings;
    private RuleTestCaseRepository testCases;
    private RuleExecutionLogRepository executions;
    private RuleOverrideLogRepository overrides;
    private RuleShadowFeedbackRepository shadowFeedback;
    private RuleBacktestRunRepository backtests;
    private RuleDriftSnapshotRepository driftSnapshots;
    private RuleApplicabilityRepository applicabilities;
    private RuleApplicabilityService applicabilityService;
    private AuditRecorder auditRecorder;
    private StateTransitionRecorder transitions;
    private DiagnoseResponseAssembler diagnoseAssembler;
    private RuleVersionedAssetAdapter versionedAssets;
    private AssetVersionRepository assetVersions;
    private AssetTriggerBindingService triggerBindings;
    private ReleasePort releasePort;
    private RuleGovernanceService governanceService;
    private InheritanceResolver inheritanceResolver;
    private ContextSnapshotService contextSnapshots;
    private EngineDomainEventPort domainEvents;
    private ObjectMapper json;
    private RuleEngineService service;

    @BeforeEach
    void setUp() {
        definitions = mock(RuleDefinitionRepository.class);
        versions = mock(RuleVersionRepository.class);
        parameterBindings = mock(RuleParameterBindingRepository.class);
        testCases = mock(RuleTestCaseRepository.class);
        executions = mock(RuleExecutionLogRepository.class);
        overrides = mock(RuleOverrideLogRepository.class);
        shadowFeedback = mock(RuleShadowFeedbackRepository.class);
        backtests = mock(RuleBacktestRunRepository.class);
        driftSnapshots = mock(RuleDriftSnapshotRepository.class);
        applicabilities = mock(RuleApplicabilityRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        transitions = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        versionedAssets = mock(RuleVersionedAssetAdapter.class);
        assetVersions = mock(AssetVersionRepository.class);
        triggerBindings = mock(AssetTriggerBindingService.class);
        releasePort = mock(ReleasePort.class);
        governanceService = mock(RuleGovernanceService.class);
        inheritanceResolver = mock(InheritanceResolver.class);
        contextSnapshots = mock(ContextSnapshotService.class);
        domainEvents = mock(EngineDomainEventPort.class);
        json = new ObjectMapper();
        json.findAndRegisterModules();
        applicabilityService = new RuleApplicabilityService(
            applicabilities, new RuleApplicabilityEvaluator(json), json);
        service = new RuleEngineService(
            definitions, versions, parameterBindings, testCases, executions, overrides,
            new RuleDslEvaluator(json), applicabilityService,
            auditRecorder, transitions, diagnoseAssembler, json,
            RuleImpactIndex.empty(), TerminologyCoverageGate.noop(),
            versionedAssets, assetVersions, triggerBindings, releasePort, governanceService, shadowFeedback,
            backtests, driftSnapshots, inheritanceResolver, contextSnapshots, domainEvents);

        when(definitions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(parameterBindings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testCases.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(executions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(overrides.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shadowFeedback.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(backtests.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(driftSnapshots.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicabilities.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versionedAssets.registerDraft(any())).thenReturn(assetVersion(
            "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1", AssetVersionStatus.DRAFT));
        when(versionedAssets.updateDraft(any())).thenReturn(assetVersion(
            "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1", AssetVersionStatus.DRAFT));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", canonicalAssetVersionNo("1")))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.DRAFT)));
        when(governanceService.requireGovernance(any(), any()))
            .thenReturn(governance(RuleGovernanceState.DRAFT));
        when(triggerBindings.matches(
            any(), eq(AssetTriggerPurpose.RULE_EXECUTION), any()
        )).thenReturn(true);
        when(triggerBindings.covers(
            any(), any(), eq(AssetTriggerPurpose.RULE_EXECUTION)
        )).thenReturn(true);
        when(triggerBindings.overlaps(
            any(), any(), eq(AssetTriggerPurpose.RULE_EXECUTION)
        )).thenReturn(true);

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rule", OrgScope.tenant("tenant-A"), "tester"));
        authenticate(RoleCode.ENGINE_OPERATOR);
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRulePersistsDefinitionAndDraftVersion() throws Exception {
        RuleCreateResponse response = service.createRule(new RuleCreateRequest(
            "RULE.ANTICOAG", "抗凝风险提示", RuleType.ORDER, RuleAuthoringMode.DSL,
            RuleRiskLevel.HIGH, ruleTriggers("order-sign", "result-review"),
            "dept-1", "院内抗凝用药管理规范 2026",
            "初始版本", dsl(), dsl().path("explain")));

        assertThat(response.ruleId()).startsWith("rule-");
        assertThat(response.versionId()).startsWith("rv-");
        assertThat(response.status()).isEqualTo(RuleDefinitionStatus.DRAFT);
        assertThat(response.traceId()).isEqualTo("trace-rule");

        ArgumentCaptor<RuleDefinition> ruleCap = ArgumentCaptor.forClass(RuleDefinition.class);
        ArgumentCaptor<RuleVersion> versionCap = ArgumentCaptor.forClass(RuleVersion.class);
        verify(definitions).save(ruleCap.capture());
        verify(versions).save(versionCap.capture());
        assertThat(ruleCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(ruleCap.getValue().activeVersionId()).isEqualTo(response.versionId());
        assertThat(versionCap.getValue().versionNo()).isEqualTo(1);
        assertThat(versionCap.getValue().sourceRef()).isEqualTo("院内抗凝用药管理规范 2026");
        ArgumentCaptor<RuleApplicability> applicabilityCap =
            ArgumentCaptor.forClass(RuleApplicability.class);
        verify(applicabilities).save(applicabilityCap.capture());
        assertThat(applicabilityCap.getValue().ruleVersionId()).isEqualTo(response.versionId());
        assertThat(applicabilityCap.getValue().settingsJson())
            .isEqualTo("[\"INPATIENT\",\"OUTPATIENT\",\"ED\",\"FOLLOWUP\"]");
        assertThat(applicabilityCap.getValue().rolloutPercent()).isEqualTo(100);
        verify(versionedAssets).registerDraft(org.mockito.Mockito.argThat(command ->
            command.assetType() == VersionedAssetType.RULE
                && command.tenantId().equals("tenant-A")
                && command.assetIdentity().equals("RULE.ANTICOAG")
                && command.organizationScope() == null
                && command.applicableScope().equals("ALL")
        ));
        verify(triggerBindings).replaceBindings(
            org.mockito.Mockito.argThat(version ->
                version.versionId().equals("av-rule-default")
                    && version.assetIdentity().equals("RULE.ANTICOAG")),
            eq(ruleTriggers("order-sign", "result-review")),
            eq("tester"),
            eq("trace-rule")
        );
        verify(governanceService).initialize(
            "tenant-A", response.versionId(), RuleRiskLevel.HIGH, "tester", "trace-rule");
        verify(auditRecorder).record(AuditAction.CREATE, "rule_definition", response.ruleId(), "创建规则 RULE.ANTICOAG");
        verify(transitions).record("rule_definition", response.ruleId(), null, "DRAFT", "CREATE_RULE", null);
    }

    @Test
    void createRuleRegistersStableRuntimeAssetDependenciesFromDsl() {
        JsonNode referencedDsl = dslWithValueSetReference("VS.ANTICOAGULANT");

        service.createRule(new RuleCreateRequest(
            "RULE.ANTICOAG", "抗凝风险提示", RuleType.ORDER, RuleAuthoringMode.DSL,
            RuleRiskLevel.HIGH, ruleTriggers("order-sign"), "dept-1",
            "院内抗凝用药管理规范 2026",
            "初始版本", referencedDsl, referencedDsl.path("explain")));

        verify(versionedAssets).registerDraft(org.mockito.Mockito.argThat(command ->
            hasDependency(command.dependencies(), VersionedAssetType.FIELD_CATALOG,
                ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY, AssetDependencyKind.FIELD)
                && hasDependency(command.dependencies(), VersionedAssetType.VALUE_SET,
                    "VS.ANTICOAGULANT", AssetDependencyKind.RUNTIME_ASSET)
        ));
    }

    @Test
    void createRuleAcceptsActionCardReferenceAndRegistersRuntimeDependency() {
        JsonNode referencedDsl = dslWithActionCardReference("ACTION.CKD.DOSE_REVIEW");

        service.createRule(new RuleCreateRequest(
            "RULE.CKD.DOSE_REVIEW", "CKD 用药复核", RuleType.ORDER, RuleAuthoringMode.DSL,
            RuleRiskLevel.HIGH, ruleTriggers("order-sign"), "dept-1",
            "CKD 用药安全规范 2026",
            "初始版本", referencedDsl, referencedDsl.path("explain")));

        verify(versionedAssets).registerDraft(org.mockito.Mockito.argThat(command ->
            hasDependency(command.dependencies(), VersionedAssetType.FIELD_CATALOG,
                ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY, AssetDependencyKind.FIELD)
                && hasDependency(command.dependencies(), VersionedAssetType.ACTION_CARD,
                    "ACTION.CKD.DOSE_REVIEW", AssetDependencyKind.RUNTIME_ASSET)
        ));
    }

    @Test
    void createRuleRejectsFieldsOutsideContextCatalog() {
        JsonNode invalidDsl = read("""
            {
              "when": {"all": [{"fact": "order.drugClass", "operator": "equals", "value": "ANTICOAGULANT"}]},
              "then": [{"actionCode": "REMIND", "summary": "x", "detail": "x", "source": {"label": "x"}, "suggestions": [], "overrideReasons": []}],
              "explain": {"title": "旧字段"}
            }
            """);

        assertThatThrownBy(() -> service.createRule(new RuleCreateRequest(
            "RULE.LEGACY.FIELD", "旧字段规则", RuleType.ORDER, RuleAuthoringMode.DSL,
            RuleRiskLevel.HIGH, ruleTriggers("order-sign"), "dept-1",
            "院内抗凝用药管理规范 2026",
            "初始版本", invalidDsl, invalidDsl.path("explain"))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("字段目录不存在")
            .hasMessageContaining("order.drugClass");

        verify(versionedAssets, never()).registerDraft(org.mockito.Mockito.argThat(command ->
            "RULE.LEGACY.FIELD".equals(command.assetIdentity())));
    }

    @Test
    void createNextVersionCopiesPublishedRuleAsDraftWithoutStoppingCurrentRuntimeVersion() {
        RuleDefinition published = existingRule(
            "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            "version-1", RuleDefinitionStatus.PUBLISHED);
        RuleVersion versionOne = existingVersion(
            "version-1", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 1);
        RuleTestCase sourceCase = testCase(RuleTestCaseType.POSITIVE, true, hitContext());
        RuleParameterBinding sourceBinding = new RuleParameterBinding(
            1L, "version-1", "tenant-A", "threshold", "18",
            Instant.now(), "tester", "trace-rule");
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(published));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(versionOne));
        when(versions.findByRuleIdAndTenantIdOrderByVersionNoDesc("rule-1", "tenant-A"))
            .thenReturn(List.of(versionOne));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(governance(RuleGovernanceState.MONITOR));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(sourceCase));
        when(parameterBindings.findByRuleVersionIdAndTenantIdOrderByParamKeyAsc(
            "version-1", "tenant-A"))
            .thenReturn(List.of(sourceBinding));
        when(versionedAssets.registerDraft(any())).thenReturn(assetVersion(
            "av-rule-v2", VersionedAssetType.RULE, "RULE.ANTICOAG", "2",
            AssetVersionStatus.DRAFT));

        RuleVersionCreateResponse response = service.createNextVersion("rule-1");

        assertThat(response.ruleId()).isEqualTo("rule-1");
        assertThat(response.versionNo()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(RuleVersionStatus.DRAFT);
        ArgumentCaptor<RuleDefinition> ruleCap = ArgumentCaptor.forClass(RuleDefinition.class);
        ArgumentCaptor<RuleVersion> versionCap = ArgumentCaptor.forClass(RuleVersion.class);
        verify(definitions).save(ruleCap.capture());
        verify(versions).save(versionCap.capture());
        assertThat(ruleCap.getValue().status()).isEqualTo(RuleDefinitionStatus.PUBLISHED);
        assertThat(ruleCap.getValue().activeVersionId()).isEqualTo(response.versionId());
        assertThat(versionCap.getValue().versionNo()).isEqualTo(2);
        assertThat(versionCap.getValue().status()).isEqualTo(RuleVersionStatus.DRAFT);
        assertThat(versionCap.getValue().dslJson()).isEqualTo(versionOne.dslJson());

        ArgumentCaptor<RuleTestCase> testCaseCap = ArgumentCaptor.forClass(RuleTestCase.class);
        verify(testCases).save(testCaseCap.capture());
        assertThat(testCaseCap.getValue().caseId()).isNotEqualTo(sourceCase.caseId());
        assertThat(testCaseCap.getValue().versionId()).isEqualTo(response.versionId());
        assertThat(testCaseCap.getValue().lastStatus()).isEqualTo(RuleTestCaseStatus.NOT_RUN);
        assertThat(testCaseCap.getValue().lastRunAt()).isNull();

        ArgumentCaptor<RuleParameterBinding> bindingCap =
            ArgumentCaptor.forClass(RuleParameterBinding.class);
        verify(parameterBindings).save(bindingCap.capture());
        assertThat(bindingCap.getValue().ruleVersionId()).isEqualTo(response.versionId());
        assertThat(bindingCap.getValue().paramKey()).isEqualTo("threshold");
        verify(governanceService).initialize(
            "tenant-A", response.versionId(), RuleRiskLevel.HIGH, "tester", "trace-rule");
        verify(versionedAssets).registerDraft(org.mockito.Mockito.argThat(command ->
            command.assetIdentity().equals("RULE.ANTICOAG")
        ));
    }

    @Test
    void createRulePersistsParameterBindingsDeclaredByDslMeta() {
        JsonNode dsl = parameterizedCriticalValueDsl();

        RuleCreateResponse response = service.createRule(new RuleCreateRequest(
            "RULE.LAB.CRITICAL.K", "血钾危急值回报", RuleType.LAB, RuleAuthoringMode.VISUAL,
            RuleRiskLevel.CRITICAL, ruleTriggers("result-review"), "dept-icu",
            "检验危急值管理制度 2026",
            "按参数生成危急值规则", dsl, dsl.path("explain"), read("""
                {
                  "observationCode": "K",
                  "criticalThreshold": 6.5,
                  "returnMinutes": 15
                }
                """)));

        ArgumentCaptor<RuleParameterBinding> bindingCap =
            ArgumentCaptor.forClass(RuleParameterBinding.class);
        verify(parameterBindings, times(3)).save(bindingCap.capture());
        assertThat(bindingCap.getAllValues())
            .extracting(RuleParameterBinding::paramKey)
            .containsExactlyInAnyOrder("observationCode", "criticalThreshold", "returnMinutes");
        assertThat(bindingCap.getAllValues())
            .allSatisfy(binding -> {
                assertThat(binding.tenantId()).isEqualTo("tenant-A");
                assertThat(binding.ruleVersionId()).isEqualTo(response.versionId());
                assertThat(binding.createdBy()).isEqualTo("tester");
                assertThat(binding.traceId()).isEqualTo("trace-rule");
            });
        assertThat(bindingCap.getAllValues())
            .filteredOn(binding -> binding.paramKey().equals("criticalThreshold"))
            .singleElement()
            .extracting(RuleParameterBinding::paramValueJson)
            .isEqualTo("6.5");
        verify(auditRecorder).record(
            AuditAction.CREATE, "mk_engine_rule_parameter_binding", response.versionId(), "保存规则参数绑定 3 项");
    }

    @Test
    void createRuleRejectsMissingRequiredParameterBinding() {
        JsonNode dsl = parameterizedCriticalValueDsl();

        assertThatThrownBy(() -> service.createRule(new RuleCreateRequest(
            "RULE.LAB.CRITICAL.K", "血钾危急值回报", RuleType.LAB, RuleAuthoringMode.VISUAL,
            RuleRiskLevel.CRITICAL, ruleTriggers("result-review"), "dept-icu",
            "检验危急值管理制度 2026",
            "缺少回报时限", dsl, dsl.path("explain"), read("""
                {
                  "observationCode": "K",
                  "criticalThreshold": 6.5
                }
                """))))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.ENG_RULE_001);
        verify(parameterBindings, never()).save(any());
    }

    @Test
    void draftTransitionRunsTechnicalGatesAndRecordsResponsibleConfirmation() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        RuleGovernance draft = governance(RuleGovernanceState.DRAFT);
        RuleGovernance reviewed = governance(RuleGovernanceState.REVIEWED);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(draft, reviewed);
        when(governanceService.transition(
            "tenant-A", "version-1", RuleGovernanceState.REVIEWED,
            "负责人确认技术验证结果", "tester", "trace-rule"))
            .thenReturn(reviewed);
        when(releasePort.submitForReview(any())).thenReturn(releasePlan(
            "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG",
            VersionReleaseStatus.IN_REVIEW, "IN_REVIEW 提交评审：规则影响摘要"));
        RuleImpactResponse impact = service.impact("rule-1");

        RuleGovernanceResponse response = service.transitionGovernance(
            "rule-1",
            new RuleGovernanceTransitionRequest(
                RuleGovernanceState.REVIEWED,
                impact.impactDigest(),
                "负责人确认技术验证结果"
            )
        );

        assertThat(response.state()).isEqualTo(RuleGovernanceState.REVIEWED);
        assertThat(response.testResults()).hasSize(4);
        assertThat(response.releaseEvidence())
            .containsExactly("IN_REVIEW 提交评审：规则影响摘要");
        verify(releasePort).submitForReview(any());
        verify(releasePort, org.mockito.Mockito.never()).approveReview(any());
        verify(releasePort, org.mockito.Mockito.never()).releaseGray(any());
    }

    @Test
    void platformRuleDraftTransitionUsesEngineOperatorResponsibility() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-platform-rule", OrgScope.tenant(PlatformTenant.ID), "platform-publisher"));
        authenticate(RoleCode.ENGINE_OPERATOR);
        RuleDefinition rule = existingRule(
            "rule-platform", PlatformTenant.ID, "RULE.PLATFORM.BASELINE",
            "平台标准版本规则", "version-platform", RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(
            "version-platform", PlatformTenant.ID, "rule-platform", RuleVersionStatus.DRAFT);
        RuleGovernance draft = governance(
            PlatformTenant.ID, "version-platform", RuleGovernanceState.DRAFT);
        RuleGovernance reviewed = governance(
            PlatformTenant.ID, "version-platform", RuleGovernanceState.REVIEWED);
        when(definitions.findByRuleIdAndTenantId("rule-platform", PlatformTenant.ID))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-platform", PlatformTenant.ID))
            .thenReturn(Optional.of(version));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(
                "version-platform", PlatformTenant.ID))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        when(governanceService.requireGovernance(PlatformTenant.ID, "version-platform"))
            .thenReturn(draft, reviewed);
        when(governanceService.transition(
            PlatformTenant.ID, "version-platform", RuleGovernanceState.REVIEWED,
            "负责人确认平台规则技术验证结果", "platform-publisher", "trace-platform-rule"))
            .thenReturn(reviewed);
        stubRuleAssetStatus(
            PlatformTenant.ID, "RULE.PLATFORM.BASELINE", "1", AssetVersionStatus.DRAFT);
        when(releasePort.submitForReview(any())).thenReturn(releasePlan(
            "av-platform-rule", VersionedAssetType.RULE, "RULE.PLATFORM.BASELINE",
            VersionReleaseStatus.IN_REVIEW, "IN_REVIEW 提交评审：平台规则影响摘要"));
        RuleImpactResponse impact = service.impact("rule-platform");

        RuleGovernanceResponse response = service.transitionGovernance(
            "rule-platform",
            new RuleGovernanceTransitionRequest(
                RuleGovernanceState.REVIEWED,
                impact.impactDigest(),
                "负责人确认平台规则技术验证结果"
            )
        );

        assertThat(response.state()).isEqualTo(RuleGovernanceState.REVIEWED);
        verify(releasePort).submitForReview(any());
    }

    @Test
    void draftTransitionRejectsManualRuntimeVersionInValueSetReference() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersionWithDsl(
            RuleVersionStatus.DRAFT,
            dslWithManualRuntimeVersion("VS.ANTICOAGULANT", "rpv-2"));
        RuleGovernance draft = governance(RuleGovernanceState.DRAFT);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(draft);
        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.REVIEWED,
                    impact.impactDigest(),
                    "手工运行定位应被拒绝"
                )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不得手工携带运行定位字段")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);

        verify(releasePort, never()).submitForReview(any());
    }

    @Test
    void reviewedTransitionPublishesContentAndEntersShadowOnly() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        RuleGovernance reviewed = governance(RuleGovernanceState.REVIEWED);
        RuleGovernance shadow = governance(RuleGovernanceState.SHADOW);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(reviewed, shadow);
        when(governanceService.transition(
            "tenant-A", "version-1", RuleGovernanceState.SHADOW,
            "负责人确认进入影子验证", "tester", "trace-rule"))
            .thenReturn(shadow);
        when(releasePort.approveReview(any())).thenReturn(releasePlan(
            "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG",
            VersionReleaseStatus.APPROVED,
            "APPROVED 评审通过：负责人确认完成"));
        authenticate(RoleCode.ENGINE_OPERATOR);
        RuleImpactResponse impact = service.impact("rule-1");

        RuleGovernanceResponse response = service.transitionGovernance(
            "rule-1",
            new RuleGovernanceTransitionRequest(
                RuleGovernanceState.SHADOW,
                impact.impactDigest(),
                "负责人确认进入影子验证"
            )
        );

        assertThat(response.state()).isEqualTo(RuleGovernanceState.SHADOW);
        verify(releasePort).approveReview(any());
        verify(releasePort, org.mockito.Mockito.never()).releaseGray(any());
        verify(definitions).save(org.mockito.Mockito.argThat(
            saved -> saved.status() == RuleDefinitionStatus.PUBLISHED));
        verify(versions).save(org.mockito.Mockito.argThat(
            saved -> saved.status() == RuleVersionStatus.PUBLISHED));
    }

    @Test
    void retirementArchivesRuleVersionAndUnifiedAssetWithoutDeletingEvidence() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.PUBLISHED);
        RuleVersion version = existingVersion(RuleVersionStatus.PUBLISHED);
        RuleGovernance monitoring = governance(RuleGovernanceState.MONITOR);
        RuleGovernance retired = governance(RuleGovernanceState.RETIRED);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", canonicalAssetVersionNo("1")))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.PUBLISHED)));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(monitoring, retired);
        when(governanceService.transition(
            "tenant-A", "version-1", RuleGovernanceState.RETIRED,
            "新版规则完成替代", "tester", "trace-rule"))
            .thenReturn(retired);
        authenticate(RoleCode.ENGINE_OPERATOR);

        RuleGovernanceResponse response = service.transitionGovernance(
            "rule-1",
            new RuleGovernanceTransitionRequest(
                RuleGovernanceState.RETIRED,
                null,
                "新版规则完成替代"
            )
        );

        assertThat(response.state()).isEqualTo(RuleGovernanceState.RETIRED);
        verify(definitions).save(org.mockito.Mockito.argThat(
            saved -> saved.status() == RuleDefinitionStatus.ARCHIVED));
        verify(versions).save(org.mockito.Mockito.argThat(
            saved -> saved.status() == RuleVersionStatus.ARCHIVED));
        verify(assetVersions).save(org.mockito.Mockito.argThat(
            saved -> saved.status() == AssetVersionStatus.WITHDRAWN
                && saved.effectiveTo() != null));
    }

    @Test
    void createRuleRejectsLegacyTriggerInsideDslBecauseBindingsOwnTheRuntimeTrigger() {
        JsonNode legacyDsl = dsl().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacyDsl).put("trigger", "order-sign");

        assertThatThrownBy(() -> service.createRule(new RuleCreateRequest(
            "RULE.LEGACY.TRIGGER", "旧触发点规则", RuleType.ORDER, RuleAuthoringMode.DSL,
            RuleRiskLevel.MEDIUM, ruleTriggers("order-sign"), "dept-1", "规则触发点契约",
            "拒绝旧枚举名", legacyDsl, legacyDsl.path("explain"))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("DSL 不得包含 trigger")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_001);
    }

    @Test
    void clinicalUserCannotMoveReviewedRuleIntoShadow() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(governance(RuleGovernanceState.REVIEWED));
        authenticate(RoleCode.CLINICAL_USER);
        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.SHADOW,
                    impact.impactDigest(),
                    "尝试进入影子运行"
                )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("需要医疗引擎运营职责")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void createRuleRejectsDslWithoutApplicability() {
        JsonNode invalidDsl = dsl().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidDsl).remove("applicability");

        assertThatThrownBy(() -> service.createRule(new RuleCreateRequest(
            "RULE.NO.APPLICABILITY", "缺少适用域", RuleType.ORDER, RuleAuthoringMode.DSL,
            RuleRiskLevel.MEDIUM, ruleTriggers("order-sign"), "dept-1", "规则适用域契约",
            "拒绝不完整 DSL", invalidDsl, invalidDsl.path("explain"))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("applicability")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_001);
    }

    @Test
    void updateRuleUpdatesTheRegisteredUnifiedVersionContent() throws Exception {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.ANTICOAG"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        JsonNode updatedDsl = dsl().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) updatedDsl)
            .put("explain", "更新后的抗凝风险解释");

        service.updateRule("rule-1", new RuleUpdateRequest(
            null, null, null, null, null, null, null, null, null, null,
            List.of(), ruleTriggers("order-sign"), "RULE.ANTICOAG", "抗凝风险提示", RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, 100, null, 0, "dept-1",
            "院内抗凝用药管理规范 2026", "更新解释", updatedDsl, updatedDsl.path("explain")
        ));

        verify(versionedAssets).updateDraft(org.mockito.Mockito.argThat(
            (AssetVersionDraftUpdateCommand command) ->
                command.tenantId().equals("tenant-A")
                    && command.versionId().equals("av-rule-default")
                    && command.assetIdentity().equals("RULE.ANTICOAG")
                    && command.organizationScope() == null
                    && command.applicableScope().equals("ALL")
                    && command.content().contains("\"ruleCode\":\"RULE.ANTICOAG\"")
                    && command.content().contains("\"explain\":\"更新后的抗凝风险解释\"")
                    && command.safetyPolicy() == AssetVersionSafetyPolicy.NORMAL
                    && command.actor().equals("tester")
        ));
    }

    @Test
    void updateNextVersionKeepsPublishedRuleIdentityAvailableForCurrentRuntimeVersion() {
        RuleDefinition rule = existingRule(
            "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            "version-2", RuleDefinitionStatus.PUBLISHED);
        RuleVersion version = existingVersion(
            "version-2", "tenant-A", "rule-1", RuleVersionStatus.DRAFT, 2);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.ANTICOAG"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-2", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", canonicalAssetVersionNo("2")))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-v2", VersionedAssetType.RULE, "RULE.ANTICOAG", "2",
                AssetVersionStatus.DRAFT)));
        when(versionedAssets.updateDraft(any())).thenReturn(assetVersion(
            "av-rule-v2", VersionedAssetType.RULE, "RULE.ANTICOAG", "2",
            AssetVersionStatus.DRAFT));
        JsonNode updatedDsl = dsl().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) updatedDsl.path("explain"))
            .put("summary", "V2 更新后的解释");

        service.updateRule("rule-1", new RuleUpdateRequest(
            null, null, null, null, null, null, null, null, null, null,
            List.of(), ruleTriggers("order-sign"), "RULE.ANTICOAG", "抗凝风险提示", RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, 100, null, 0, "dept-1",
            "院内抗凝用药管理规范 2026", "V2 更新解释", updatedDsl, updatedDsl.path("explain")
        ));

        verify(definitions).save(org.mockito.Mockito.argThat(saved ->
            saved.status() == RuleDefinitionStatus.PUBLISHED
                && saved.activeVersionId().equals("version-2")
        ));
        verify(transitions).record(
            "rule_definition", "rule-1", "PUBLISHED", "PUBLISHED", "UPDATE_RULE", null);
    }

    @Test
    void updateRuleRejectsContentChangeAfterTechnicalValidationStarts() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(governance(RuleGovernanceState.REVIEWED));

        assertThatThrownBy(() -> service.updateRule("rule-1", new RuleUpdateRequest(
            null, null, null, null, null, null, null, null, null, null,
            List.of(), ruleTriggers("order-sign"), "RULE.ANTICOAG", "抗凝风险提示", RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, 100, null, 0, "dept-1",
            "院内抗凝用药管理规范 2026", "技术验证后禁止修改", dsl(), dsl().path("explain")
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("只有治理草稿阶段可以修改")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_006);

        verify(definitions, org.mockito.Mockito.never()).save(any());
        verify(versions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void detailProjectsTheUnifiedDeploymentStatus() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", canonicalAssetVersionNo("1")))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.PUBLISHED)));

        RuleDetailResponse response = service.detail("rule-1");

        assertThat(response.deploymentStatus()).isEqualTo(AssetVersionStatus.PUBLISHED);
    }

    @Test
    void detailReturnsAllVersionsNewestFirstForTraceability() {
        RuleDefinition rule = existingRule(
            "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            "version-2", RuleDefinitionStatus.PUBLISHED);
        RuleVersion versionTwo = existingVersion(
            "version-2", "tenant-A", "rule-1", RuleVersionStatus.DRAFT, 2);
        RuleVersion versionOne = existingVersion(
            "version-1", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 1);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-2", "tenant-A"))
            .thenReturn(Optional.of(versionTwo));
        when(versions.findByRuleIdAndTenantIdOrderByVersionNoDesc("rule-1", "tenant-A"))
            .thenReturn(List.of(versionTwo, versionOne));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", canonicalAssetVersionNo("2")))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-v2", VersionedAssetType.RULE, "RULE.ANTICOAG", "2",
                AssetVersionStatus.DRAFT)));

        RuleDetailResponse response = service.detail("rule-1");

        assertThat(response.versions()).extracting(RuleVersion::versionNo)
            .containsExactly(2, 1);
    }

    @Test
    void addTestCasePersistsAgainstCurrentVersion() throws Exception {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A")).thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A")).thenReturn(Optional.of(version));
        when(contextSnapshots.findById("ctx-1")).thenReturn(new ContextSnapshotResponse(
            "ctx-1", ContextSnapshotStatus.ACTIVE, validResources(), "runtime-release-test",
            QualityStatus.VALID, List.of(), Map.of(), Instant.now(), "trace-ctx"));

        RuleTestCaseResponse response = service.addTestCase("rule-1", new RuleTestCaseRequest(
            RuleTestCaseType.POSITIVE, "ctx-1", true, RuleRiskLevel.HIGH, "STRONG_REMINDER"));

        assertThat(response.caseId()).startsWith("rtc-");
        assertThat(response.caseType()).isEqualTo(RuleTestCaseType.POSITIVE);
        ArgumentCaptor<RuleTestCase> caseCap = ArgumentCaptor.forClass(RuleTestCase.class);
        verify(testCases).save(caseCap.capture());
        assertThat(caseCap.getValue().versionId()).isEqualTo("version-1");
        assertThat(caseCap.getValue().contextSnapshotId()).isEqualTo("ctx-1");
        assertThat(caseCap.getValue().inputPayload()).contains("\"patient\"");
    }

    @Test
    void addTestCaseRejectsNonActiveContextSnapshot() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(contextSnapshots.findById("ctx-old")).thenReturn(new ContextSnapshotResponse(
            "ctx-old", ContextSnapshotStatus.SUPERSEDED, validResources(), "runtime-release-test",
            QualityStatus.VALID, List.of(), Map.of(), Instant.now(), "trace-ctx"));

        assertThatThrownBy(() -> service.addTestCase("rule-1", new RuleTestCaseRequest(
            RuleTestCaseType.POSITIVE, "ctx-old", true, RuleRiskLevel.HIGH, "STRONG_REMINDER")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_006);
    }

    @Test
    void peerReviewFailsWhenRequiredCaseTypeMissing() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(testCase(RuleTestCaseType.POSITIVE, true, hitContext())));

        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.REVIEWED,
                    impact.impactDigest(),
                    "提交同行评审"
                )))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);
    }

    @Test
    void peerReviewFailsWhenAnyTestCaseExpectationDiffersAndStoresResult() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, missContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, hitContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.REVIEWED,
                    impact.impactDigest(),
                    "提交同行评审"
                )))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);

        ArgumentCaptor<RuleTestCase> caseCap = ArgumentCaptor.forClass(RuleTestCase.class);
        verify(testCases, org.mockito.Mockito.atLeastOnce()).save(caseCap.capture());
        assertThat(caseCap.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.caseType()).isEqualTo(RuleTestCaseType.POSITIVE);
            assertThat(saved.lastStatus()).isEqualTo(RuleTestCaseStatus.FAIL);
        });
    }

    @Test
    void peerReviewRejectsStaticConflictWithPublishedRuleBeforeStateChange() {
        RuleDefinition draft = existingRule(RuleDefinitionStatus.DRAFT);
        RuleDefinition published = governedRule(
            "rule-existing", "RULE.EXISTING", "version-existing", 100, null, 0);
        RuleVersion candidateVersion = ruleVersionWithAction(
            "version-1", "rule-1", RuleVersionStatus.DRAFT, "BLOCK");
        RuleVersion existingVersion = ruleVersionWithAction(
            "version-existing", "rule-existing", RuleVersionStatus.PUBLISHED, "REMIND");
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(draft));
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(published));
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of());
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(candidateVersion));
        when(versions.findByVersionIdAndTenantId("version-existing", "tenant-A"))
            .thenReturn(Optional.of(existingVersion));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext(), "BLOCK"),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext(), "BLOCK"),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        stubRuleAssetStatus(
            "tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.DRAFT);
        stubRuleAssetStatus(
            "tenant-A", "RULE.EXISTING", "1", AssetVersionStatus.PUBLISHED);
        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.REVIEWED,
                    impact.impactDigest(),
                    "已核查影响摘要"
                )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("RULE.EXISTING")
            .hasMessageContaining("patient.age")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);

        verify(definitions, org.mockito.Mockito.never()).save(any());
        verify(versions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void peerReviewRejectsSuppressionSourceThatIsNotHigherPriority() {
        RuleDefinition draft = governedRule(
            "rule-1", "RULE.ANTICOAG", "version-1", 200, "RULE.LOW", 0,
            RuleDefinitionStatus.DRAFT);
        RuleDefinition lowerPriority = governedRule(
            "rule-low", "RULE.LOW", "version-low", 100, null, 0);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(draft));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.LOW"))
            .thenReturn(Optional.of(lowerPriority));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-low", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(
                "version-low", "tenant-A", "rule-low", RuleVersionStatus.PUBLISHED)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.REVIEWED,
                    impact.impactDigest(),
                    "已核查影响摘要"
                )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("优先级必须高于")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);
    }

    @Test
    void peerReviewRejectsMissingSuppressionSource() {
        RuleDefinition draft = governedRule(
            "rule-1", "RULE.ANTICOAG", "version-1", 100, "RULE.MISSING", 0,
            RuleDefinitionStatus.DRAFT);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(draft));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.MISSING"))
            .thenReturn(Optional.empty());
        when(definitions.findByTenantIdAndRuleCode("t-1", "RULE.MISSING"))
            .thenReturn(Optional.empty());
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.REVIEWED,
                    impact.impactDigest(),
                    "已核查影响摘要"
                )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("抑制来源规则不存在或尚未发布")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);
    }

    @Test
    void fullTransitionActivatesTheUnifiedRuleVersionForRuntimeResolution() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", canonicalAssetVersionNo("1")))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-1", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.PUBLISHED)));
        when(releasePort.publish(any())).thenReturn(releasePlan(
            "av-rule-1", VersionedAssetType.RULE, "RULE.ANTICOAG",
            VersionReleaseStatus.PUBLISHED, "FULL 全量激活：规则影响摘要"));
        RuleGovernance canary = governance(RuleGovernanceState.CANARY);
        RuleGovernance full = governance(RuleGovernanceState.FULL);
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(canary, full);
        when(governanceService.transition(
            "tenant-A", "version-1", RuleGovernanceState.FULL,
            "院级管理员确认全量激活", "tester", "trace-rule"))
            .thenReturn(full);
        authenticate(RoleCode.ENGINE_OPERATOR);
        RuleImpactResponse impact = service.impact("rule-1");
        VersionPublishQualityGate qualityGate = new VersionPublishQualityGate(
            true, true, true, true, true, "规则发布质量校验全部通过"
        );

        RuleGovernanceResponse response = service.transitionGovernance(
            "rule-1",
            new RuleGovernanceTransitionRequest(
                RuleGovernanceState.FULL,
                impact.impactDigest(),
                "院级管理员确认全量激活",
                new VersionPublishEvidence(qualityGate)
            )
        );

        assertThat(response.releaseEvidence()).contains("FULL 全量激活：规则影响摘要");
        ArgumentCaptor<VersionReleaseCommand> releaseCommand =
            ArgumentCaptor.forClass(VersionReleaseCommand.class);
        verify(releasePort).publish(releaseCommand.capture());
        assertThat(releaseCommand.getValue().qualityGate()).isEqualTo(qualityGate);
        verify(auditRecorder).record(
            AuditAction.PUBLISH, "rule_definition", "rule-1", "规则治理推进至 FULL");
    }

    @Test
    void platformRuleFullTransitionRequiresEngineOperatorResponsibility() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-platform-rule", OrgScope.tenant(PlatformTenant.ID), "platform-publisher"));
        RuleDefinition rule = existingRule(
            "rule-platform", PlatformTenant.ID, "RULE.PLATFORM.BASELINE",
            "平台标准版本规则", "version-platform", RuleDefinitionStatus.PUBLISHED);
        RuleVersion version = existingVersion(
            "version-platform", PlatformTenant.ID, "rule-platform", RuleVersionStatus.PUBLISHED);
        RuleGovernance canary = governance(
            PlatformTenant.ID, "version-platform", RuleGovernanceState.CANARY);
        RuleGovernance full = governance(
            PlatformTenant.ID, "version-platform", RuleGovernanceState.FULL);
        when(definitions.findByRuleIdAndTenantId("rule-platform", PlatformTenant.ID))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-platform", PlatformTenant.ID))
            .thenReturn(Optional.of(version));
        when(governanceService.requireGovernance(PlatformTenant.ID, "version-platform"))
            .thenReturn(canary, canary, full);
        when(governanceService.transition(
            PlatformTenant.ID, "version-platform", RuleGovernanceState.FULL,
            "平台管理员确认全量激活", "platform-publisher", "trace-platform-rule"))
            .thenReturn(full);
        stubRuleAssetStatus(
            PlatformTenant.ID, "RULE.PLATFORM.BASELINE", "1", AssetVersionStatus.PUBLISHED);
        when(releasePort.publish(any())).thenReturn(releasePlan(
            "av-platform-rule", VersionedAssetType.RULE, "RULE.PLATFORM.BASELINE",
            VersionReleaseStatus.PUBLISHED, "FULL 全量激活：平台规则影响摘要"));
        RuleImpactResponse impact = service.impact("rule-platform");
        RuleGovernanceTransitionRequest request = new RuleGovernanceTransitionRequest(
            RuleGovernanceState.FULL,
            impact.impactDigest(),
            "平台管理员确认全量激活",
            new VersionPublishEvidence(
                new VersionPublishQualityGate(
                    true, true, true, true, true, "平台规则发布质量校验全部通过"
                )
            )
        );

        authenticate(RoleCode.PLATFORM_ADMIN);
        assertThatThrownBy(() -> service.transitionGovernance("rule-platform", request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("医疗引擎运营职责")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);
        verify(releasePort, never()).publish(any());

        authenticate(RoleCode.ENGINE_OPERATOR);
        RuleGovernanceResponse response = service.transitionGovernance("rule-platform", request);

        assertThat(response.state()).isEqualTo(RuleGovernanceState.FULL);
        verify(releasePort).publish(any());
    }

    @Test
    void impactReturnsPartialKnownObjectsAndDigest() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));

        RuleImpactResponse response = service.impact("rule-1");

        assertThat(response.analysisStatus()).isEqualTo("PARTIAL");
        assertThat(response.impactDigest()).startsWith("sha256:");
        assertThat(response.affectedRules()).singleElement().satisfies(rule -> {
            assertThat(rule.objectType()).isEqualTo("RULE_DEFINITION");
            assertThat(rule.objectId()).isEqualTo("rule-1");
        });
        assertThat(response.affectedPathways()).isEmpty();
        assertThat(response.inPathPatients()).isEmpty();
        assertThat(response.integrationAdapters()).isEmpty();
        assertThat(response.unavailableScopes()).hasSize(3);
    }

    @Test
    void impactDigestChangesWhenReferencedValueSetChanges() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersionWithDsl(
                RuleVersionStatus.DRAFT,
                dslWithValueSetReference("VS.ANTICOAGULANT"))))
            .thenReturn(Optional.of(existingVersionWithDsl(
                RuleVersionStatus.DRAFT,
                dslWithValueSetReference("VS.ANTICOAGULANT.V2"))));

        RuleImpactResponse first = service.impact("rule-1");
        RuleImpactResponse second = service.impact("rule-1");

        assertThat(second.impactDigest()).isNotEqualTo(first.impactDigest());
    }

    @Test
    void highRiskTechnicalValidationWithoutImpactDigestIsDeniedBeforeTesting() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.REVIEWED,
                    null,
                    "提交同行评审"
                )))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);
    }

    @Test
    void peerReviewFailsWhenTerminologyCoverageHasUnmappedCode() {
        TerminologyCoverageGate coverageGate = mock(TerminologyCoverageGate.class);
        RuleEngineService gatedService = new RuleEngineService(
            definitions, versions, parameterBindings, testCases, executions, overrides,
            new RuleDslEvaluator(json), applicabilityService,
            auditRecorder, transitions, diagnoseAssembler, json,
            RuleImpactIndex.empty(), coverageGate,
            versionedAssets, assetVersions, triggerBindings, releasePort, governanceService, shadowFeedback,
            backtests, driftSnapshots, inheritanceResolver, contextSnapshots);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        when(coverageGate.checkConditionCoverage(any())).thenReturn(List.of(
            new TerminologyCoverageIssue(
                "conditions[].code", "ICD-10", "E11", MappingCoverageItem.UNMAPPED, 0)));
        RuleImpactResponse impact = gatedService.impact("rule-1");

        assertThatThrownBy(() -> gatedService.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.REVIEWED,
                    impact.impactDigest(),
                    "已核查影响摘要"
                )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("编码对照")
            .hasMessageContaining("E11")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);

        verify(definitions, org.mockito.Mockito.never()).save(any());
        verify(versions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void runTestsReturnsAllResultsWithoutPublishing() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext())
            ));

        RuleTestRunResponse response = service.runTests("rule-1");

        assertThat(response.allPassed()).isTrue();
        assertThat(response.results()).hasSize(2);
        verify(definitions, org.mockito.Mockito.never()).save(any());
        verify(versions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void evaluatePublishedRulePersistsExecutionLogAndReturnsExplanation() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);
        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-1", List.of("rule-1"));

        assertThat(response.items()).hasSize(1);
        assertThat(response.highestSeverity()).isEqualTo(RuleRiskLevel.HIGH);
        assertThat(response.cards()).singleElement().satisfies(card -> {
            assertThat(card.summary()).isEqualTo("抗凝用药需确认出血风险");
            assertThat(card.indicator()).isEqualTo("critical");
            assertThat(card.source().label()).isEqualTo("规则测试来源");
            assertThat(card.requiresPhysicianConfirmation()).isTrue();
        });
        RuleEvaluationItem item = response.items().getFirst();
        assertThat(item.hit()).isTrue();
        assertThat(item.explanation().get("title").asText()).isEqualTo("抗凝风险提示");
        assertThat(item.explanation().path("conditionEvidence")).hasSize(2);
        assertThat(item.explanation().path("conditionEvidence").get(0).path("fact").asText())
            .isEqualTo("patient.age");
        assertThat(item.explanation().path("conditionEvidence").get(0).path("actual").asInt())
            .isEqualTo(72);

        ArgumentCaptor<RuleExecutionLog> executionCap = ArgumentCaptor.forClass(RuleExecutionLog.class);
        verify(executions).save(executionCap.capture());
        assertThat(executionCap.getValue().inputDigest()).startsWith("sha256:");
        assertThat(executionCap.getValue().actionsJson()).contains("STRONG_REMINDER");
        assertThat(executionCap.getValue().explanationJson())
            .contains("\"conditionEvidence\"")
            .contains("\"sourcePath\":\"$.patient.age\"");
        verify(auditRecorder).record(AuditAction.EXECUTE, "rule_execution", item.executionId(), "执行规则 rule-1");
        ArgumentCaptor<RuleFiredEvent> eventCap = ArgumentCaptor.forClass(RuleFiredEvent.class);
        verify(domainEvents).ruleFired(eventCap.capture());
        assertThat(eventCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(eventCap.getValue().traceId()).isEqualTo("trace-rule");
        assertThat(eventCap.getValue().runtimeReleaseId()).isNull();
        assertThat(eventCap.getValue().ruleId()).isEqualTo("rule-1");
        assertThat(eventCap.getValue().ruleCode()).isEqualTo("RULE.ANTICOAG");
        assertThat(eventCap.getValue().executionId()).isEqualTo(item.executionId());
        assertThat(eventCap.getValue().patientId()).isNull();
        assertThat(eventCap.getValue().actions()).containsExactly("STRONG_REMINDER");
    }

    @Test
    void evaluatePinnedContextExecutesTheReleaseVersionInsteadOfTheCurrentEditingPointer() {
        RuleDefinition rule = existingRule(
            "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            "version-current", RuleDefinitionStatus.PUBLISHED);
        RuleVersion pinned = existingVersion(
            "version-pinned", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 1);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-pinned", "tenant-A"))
            .thenReturn(Optional.of(pinned));

        RuleEvaluateResponse response = service.evaluatePinnedContext(
            "order-sign",
            hitContext(),
            "evt-pinned",
            List.of(new RuntimeRuleReference("tenant-A", "rule-1", "version-pinned")),
            "release-4");

        assertThat(response.items())
            .singleElement()
            .extracting(RuleEvaluationItem::versionId)
            .isEqualTo("version-pinned");
        verify(versions, never()).findByVersionIdAndTenantId("version-current", "tenant-A");
    }

    @Test
    void evaluateShadowRuleRecordsPotentialHitWithoutClinicalAction() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.DRAFT);
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(governance(RuleGovernanceState.SHADOW));

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-shadow", List.of("rule-1"));

        assertThat(response.highestSeverity()).isNull();
        assertThat(response.cards()).isEmpty();
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.hit()).isTrue();
            assertThat(item.status()).isEqualTo(RuleExecutionStatus.SHADOW_RECORDED);
            assertThat(item.actions()).isEmpty();
            assertThat(item.severity()).isEqualTo(RuleRiskLevel.HIGH);
            assertThat(item.explanation().path("shadowMode").asBoolean()).isTrue();
        });
        ArgumentCaptor<RuleExecutionLog> executionCap = ArgumentCaptor.forClass(RuleExecutionLog.class);
        verify(executions).save(executionCap.capture());
        assertThat(executionCap.getValue().status()).isEqualTo(RuleExecutionStatus.SHADOW_RECORDED);
        assertThat(executionCap.getValue().hit()).isTrue();
        assertThat(executionCap.getValue().actionsJson()).contains("STRONG_REMINDER");
        assertThat(executionCap.getValue().explanationJson()).contains("\"shadowMode\":true");
        verify(executions, org.mockito.Mockito.never()).findRecentSuccessful(any(), any(), any(), any());
    }

    @Test
    void evaluateCanaryRuleOnlyActivatesStableTenPercentCohort() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.DRAFT);
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(governance(RuleGovernanceState.CANARY));

        RuleEvaluateResponse included = service.evaluateContext(
            "order-sign", hitContextWithPatient("MPI-CANARY-0"), "evt-canary-in", List.of("rule-1"));
        RuleEvaluateResponse excluded = service.evaluateContext(
            "order-sign", hitContextWithPatient("MPI-1"), "evt-canary-out", List.of("rule-1"));

        assertThat(included.items()).singleElement().satisfies(item -> {
            assertThat(item.hit()).isTrue();
            assertThat(item.status()).isEqualTo(RuleExecutionStatus.SUCCESS);
            assertThat(item.actions()).hasSize(1);
        });
        assertThat(included.cards()).hasSize(1);
        assertThat(excluded.items()).isEmpty();
        assertThat(excluded.cards()).isEmpty();
    }

    @Test
    void evaluateShadowNextVersionKeepsPublishedVersionActiveAndRunsDraftVersionWithoutActions() {
        RuleDefinition rule = existingRule(
            "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            "version-2", RuleDefinitionStatus.PUBLISHED);
        RuleVersion versionOne = existingVersion(
            "version-1", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 1);
        RuleVersion versionTwo = existingVersion(
            "version-2", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 2);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-2", "tenant-A"))
            .thenReturn(Optional.of(versionTwo));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-1", "tenant-A", 1))
            .thenReturn(Optional.of(versionOne));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", canonicalAssetVersionNo("2")))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-v2", VersionedAssetType.RULE, "RULE.ANTICOAG", "2",
                AssetVersionStatus.DRAFT)));
        when(governanceService.requireGovernance("tenant-A", "version-2"))
            .thenReturn(governance("version-2", RuleGovernanceState.SHADOW));

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContextWithPatient(), "evt-shadow-v2", List.of("rule-1"), "rpv-1");

        assertThat(response.items()).extracting(RuleEvaluationItem::versionId)
            .containsExactly("version-1", "version-2");
        assertThat(response.items()).extracting(RuleEvaluationItem::status)
            .containsExactly(RuleExecutionStatus.SUCCESS, RuleExecutionStatus.SHADOW_RECORDED);
        assertThat(response.cards()).hasSize(1);
        verify(domainEvents, times(1)).ruleFired(any());
    }

    @Test
    void evaluateCanaryNextVersionRoutesEligiblePatientsToV2AndOthersToV1() {
        RuleDefinition rule = existingRule(
            "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            "version-2", RuleDefinitionStatus.PUBLISHED);
        RuleVersion versionOne = existingVersion(
            "version-1", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 1);
        RuleVersion versionTwo = existingVersion(
            "version-2", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 2);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-2", "tenant-A"))
            .thenReturn(Optional.of(versionTwo));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-1", "tenant-A", 1))
            .thenReturn(Optional.of(versionOne));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", canonicalAssetVersionNo("2")))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-v2", VersionedAssetType.RULE, "RULE.ANTICOAG", "2",
                AssetVersionStatus.DRAFT)));
        when(governanceService.requireGovernance("tenant-A", "version-2"))
            .thenReturn(governance("version-2", RuleGovernanceState.CANARY));

        RuleEvaluateResponse included = service.evaluateContext(
            "order-sign", hitContextWithPatient("MPI-CANARY-0"),
            "evt-canary-v2-in", List.of("rule-1"), "rpv-1");
        RuleEvaluateResponse excluded = service.evaluateContext(
            "order-sign", hitContextWithPatient("MPI-1"),
            "evt-canary-v2-out", List.of("rule-1"), "rpv-1");

        assertThat(included.items()).singleElement()
            .extracting(RuleEvaluationItem::versionId)
            .isEqualTo("version-2");
        assertThat(excluded.items()).singleElement()
            .extracting(RuleEvaluationItem::versionId)
            .isEqualTo("version-1");
        assertThat(included.cards()).hasSize(1);
        assertThat(excluded.cards()).hasSize(1);
    }

    @Test
    void evaluateShadowRuleDoesNotSuppressActiveLowerPriorityRule() {
        RuleDefinition shadowHigh = governedRule(
            "rule-shadow", "RULE.HIGH", "version-shadow", 900, null, 0);
        RuleDefinition activeLow = governedRule(
            "rule-low", "RULE.LOW", "version-low", 100, "RULE.HIGH", 0);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(activeLow, shadowHigh));
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of());
        when(versions.findByVersionIdAndTenantId("version-shadow", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(
                "version-shadow", "tenant-A", "rule-shadow", RuleVersionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-low", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(
                "version-low", "tenant-A", "rule-low", RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.HIGH", "1", AssetVersionStatus.DRAFT);
        stubRuleAssetStatus("tenant-A", "RULE.LOW", "1", AssetVersionStatus.PUBLISHED);
        when(governanceService.requireGovernance("tenant-A", "version-shadow"))
            .thenReturn(governance("version-shadow", RuleGovernanceState.SHADOW));

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContextWithPatient(), "evt-shadow-suppression", List.of());

        assertThat(response.items()).extracting(RuleEvaluationItem::ruleId)
            .containsExactly("rule-shadow", "rule-low");
        assertThat(response.items()).extracting(RuleEvaluationItem::status)
            .containsExactly(RuleExecutionStatus.SHADOW_RECORDED, RuleExecutionStatus.SUCCESS);
        assertThat(response.cards()).hasSize(1);
        assertThat(response.highestSeverity()).isEqualTo(RuleRiskLevel.HIGH);
    }

    @Test
    void evaluateLoadsActiveContextSnapshotInsteadOfAcceptingCallerPayload() {
        when(contextSnapshots.findById("snapshot-1")).thenReturn(new ContextSnapshotResponse(
            "snapshot-1", ContextSnapshotStatus.ACTIVE, validResources(), "runtime-release-test",
            QualityStatus.VALID, List.of(), Map.of(), Instant.now(), "trace-snapshot"));
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of());
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of());

        RuleEvaluateResponse response = service.evaluate(new RuleEvaluateRequest(
            "order-sign", "snapshot-1", "evt-1", List.of()));

        assertThat(response.items()).isEmpty();
        verify(contextSnapshots).findById("snapshot-1");
    }

    @Test
    void evaluateExplicitRuleDoesNotRejectDifferentRuntimeReleaseId() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-runtime-release", List.of("rule-1"), "runtime-release-test");

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.ruleId()).isEqualTo("rule-1");
            assertThat(item.hit()).isTrue();
        });
        verify(executions).save(any());
    }

    @Test
    void evaluateNeverExecutesReviewedRuleBeforeUnifiedVersionIsActive() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", canonicalAssetVersionNo("1")))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.DRAFT)));

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-reviewed", List.of("rule-1"));

        assertThat(response.items()).isEmpty();
        verify(executions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void evaluateAllRulesUsesInheritanceResolvedVersionForCurrentDepartment() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rule", new OrgScope("tenant-A", null, "hosp-1", null, null, "dept-1", null, null), "tester"));
        RuleDefinition rule = existingRule(
            "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            "version-1", RuleDefinitionStatus.PUBLISHED);
        RuleVersion originalVersion = existingVersion(
            "version-1", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 1);
        RuleVersion effectiveVersion = existingVersion(
            "version-2", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 2);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(rule));
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of());
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(originalVersion));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);
        when(inheritanceResolver.resolve(any())).thenReturn(new ResolvedAssetVersion(
            assetVersion("av-rule-2", VersionedAssetType.RULE, "RULE.ANTICOAG", "2", AssetVersionStatus.PUBLISHED),
            "dept-1", false, true, false, null, SourceTier.ORG));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.ANTICOAG"))
            .thenReturn(Optional.of(rule));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-1", "tenant-A", 2))
            .thenReturn(Optional.of(effectiveVersion));
        when(versions.findByVersionIdAndTenantId("version-2", "tenant-A"))
            .thenReturn(Optional.of(effectiveVersion));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "2", AssetVersionStatus.PUBLISHED);

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-effective-all", List.of());

        assertThat(response.items()).singleElement()
            .extracting(RuleEvaluationItem::versionId)
            .isEqualTo("version-2");
        verify(inheritanceResolver).resolve(any());
    }

    @Test
    void evaluateSpecifiedRuleUsesInheritanceResolvedVersionForCurrentDepartment() {
        InheritanceResolver resolver = mock(InheritanceResolver.class);
        RuleEngineService inheritedService = new RuleEngineService(
            definitions, versions, parameterBindings, testCases, executions, overrides,
            new RuleDslEvaluator(json), applicabilityService,
            auditRecorder, transitions, diagnoseAssembler, json,
            RuleImpactIndex.empty(), TerminologyCoverageGate.noop(),
            versionedAssets, assetVersions, triggerBindings, releasePort, governanceService, shadowFeedback,
            backtests, driftSnapshots, resolver, contextSnapshots);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rule", new OrgScope("tenant-A", null, "hosp-1", null, null, "dept-1", null, null), "tester"));
        RuleDefinition rule = existingRule(
            "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            "version-1", RuleDefinitionStatus.PUBLISHED);
        RuleVersion effectiveVersion = existingVersion(
            "version-2", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 2);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(resolver.resolve(any())).thenReturn(new ResolvedAssetVersion(
            assetVersion("av-rule-2", VersionedAssetType.RULE, "RULE.ANTICOAG", "2", AssetVersionStatus.PUBLISHED),
            "dept-1", false, true, false, null, SourceTier.ORG));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.ANTICOAG"))
            .thenReturn(Optional.of(rule));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-1", "tenant-A", 2))
            .thenReturn(Optional.of(effectiveVersion));
        when(versions.findByVersionIdAndTenantId("version-2", "tenant-A"))
            .thenReturn(Optional.of(effectiveVersion));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "2", AssetVersionStatus.PUBLISHED);

        RuleEvaluateResponse response = inheritedService.evaluateContext(
            "order-sign", hitContext(), "evt-effective", List.of("rule-1"));

        assertThat(response.items()).singleElement()
            .extracting(RuleEvaluationItem::versionId)
            .isEqualTo("version-2");
        verify(resolver).resolve(any());
    }

    @Test
    void listUsesEffectiveRepositoryPagingForCustomerTenantWithoutLoadingSnapshots() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("tenant-A"), "rule-admin"));
        RuleDefinition localOverride = existingRule(
            "rule-local", "tenant-A", "RULE.ANTICOAG", "院内抗凝风险提示",
            "version-local", RuleDefinitionStatus.PUBLISHED);
        RuleDefinition platformOnly = existingRule(
            "rule-platform-dvt", "t-1", "RULE.DVT", "平台 DVT 风险提示",
            "version-platform-dvt", RuleDefinitionStatus.PUBLISHED);
        when(definitions.countEffectiveByFilter(
            "tenant-A", PlatformTenant.ID, null, RuleDefinitionStatus.PUBLISHED.name(), null, null, "%抗凝%"))
            .thenReturn(2L);
        when(definitions.pageEffectiveByFilter(
            "tenant-A", PlatformTenant.ID, null, RuleDefinitionStatus.PUBLISHED.name(), null, null, "%抗凝%", 0, 20))
            .thenReturn(List.of(localOverride, platformOnly));

        PageResponse<RuleDefinition> response = service.list(
            new RuleFilter(null, null, null, "抗凝"),
            PageRequest.defaults());

        assertThat(response.total()).isEqualTo(2L);
        assertThat(response.items()).extracting(RuleDefinition::ruleId)
            .containsExactly("rule-local", "rule-platform-dvt");
        verify(definitions, never()).listByFilter(any(), any(), any(), any(), any());
    }

    @Test
    void evaluateSpecifiedPlatformRuleKeepsPlatformActiveUntilLocalOverrideIsActivated() {
        RuleDefinition platformActive = existingRule(
            "rule-platform", "t-1", "RULE.ANTICOAG", "平台抗凝风险提示",
            "version-platform", RuleDefinitionStatus.PUBLISHED);
        RuleDefinition localReviewed = existingRule(
            "rule-local", "tenant-A", "RULE.ANTICOAG", "院内抗凝风险提示",
            "version-local", RuleDefinitionStatus.PUBLISHED);
        RuleVersion platformVersion = existingVersion(
            "version-platform", "t-1", "rule-platform", RuleVersionStatus.PUBLISHED);
        RuleVersion localVersion = existingVersion(
            "version-local", "tenant-A", "rule-local", RuleVersionStatus.PUBLISHED);
        when(definitions.findByRuleIdAndTenantId("rule-platform", "tenant-A"))
            .thenReturn(Optional.empty());
        when(definitions.findByRuleIdAndTenantId("rule-platform", "t-1"))
            .thenReturn(Optional.of(platformActive));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.ANTICOAG"))
            .thenReturn(Optional.of(localReviewed));
        when(versions.findByVersionIdAndTenantId("version-local", "tenant-A"))
            .thenReturn(Optional.of(localVersion));
        when(versions.findByVersionIdAndTenantId("version-platform", "t-1"))
            .thenReturn(Optional.of(platformVersion));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.DRAFT);
        stubRuleAssetStatus("t-1", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-explicit-platform", List.of("rule-platform"));

        assertThat(response.items()).extracting(RuleEvaluationItem::ruleId)
            .containsExactly("rule-platform");
    }

    @Test
    void evaluateWithoutExplicitRuleIdsFallsBackToPlatformPublishedRules() {
        RuleDefinition platformRule = existingRule(
            "rule-platform", "t-1", "RULE.PLATFORM.ANTICOAG", "平台抗凝风险提示",
            "version-platform", RuleDefinitionStatus.PUBLISHED);
        RuleVersion platformVersion = existingVersion(
            "version-platform", "t-1", "rule-platform", RuleVersionStatus.PUBLISHED);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of());
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of(platformRule));
        when(versions.findByVersionIdAndTenantId("version-platform", "t-1")).thenReturn(Optional.of(platformVersion));
        stubRuleAssetStatus("t-1", "RULE.PLATFORM.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-platform", List.of());

        assertThat(response.items()).extracting(RuleEvaluationItem::ruleId)
            .containsExactly("rule-platform");
        ArgumentCaptor<RuleExecutionLog> executionCap = ArgumentCaptor.forClass(RuleExecutionLog.class);
        verify(executions).save(executionCap.capture());
        assertThat(executionCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(executionCap.getValue().ruleId()).isEqualTo("rule-platform");
    }

    @Test
    void evaluateMergesLocalOverridesWithNonOverriddenPlatformRules() {
        RuleDefinition localOverride = existingRule(
            "rule-local", "tenant-A", "RULE.ANTICOAG", "院内抗凝风险提示",
            "version-local", RuleDefinitionStatus.PUBLISHED);
        RuleDefinition platformShadowed = existingRule(
            "rule-platform-shadowed", "t-1", "RULE.ANTICOAG", "平台抗凝风险提示",
            "version-platform-shadowed", RuleDefinitionStatus.PUBLISHED);
        RuleDefinition platformOnly = existingRule(
            "rule-platform-dvt", "t-1", "RULE.DVT", "平台 DVT 风险提示",
            "version-platform-dvt", RuleDefinitionStatus.PUBLISHED);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(localOverride));
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of(platformShadowed, platformOnly));
        when(versions.findByVersionIdAndTenantId("version-local", "tenant-A"))
            .thenReturn(Optional.of(existingVersion("version-local", "tenant-A", "rule-local", RuleVersionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-platform-dvt", "t-1"))
            .thenReturn(Optional.of(existingVersion("version-platform-dvt", "t-1", "rule-platform-dvt", RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);
        stubRuleAssetStatus("t-1", "RULE.DVT", "1", AssetVersionStatus.PUBLISHED);

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-merge", List.of());

        assertThat(response.items()).extracting(RuleEvaluationItem::ruleId)
            .containsExactly("rule-local", "rule-platform-dvt");
        verify(versions, org.mockito.Mockito.never())
            .findByVersionIdAndTenantId("version-platform-shadowed", "t-1");
    }

    @Test
    void evaluateKeepsPlatformActiveRuleUntilLocalReviewedOverrideIsActivated() {
        RuleDefinition localReviewed = existingRule(
            "rule-local", "tenant-A", "RULE.ANTICOAG", "院内抗凝风险提示",
            "version-local", RuleDefinitionStatus.PUBLISHED);
        RuleDefinition platformActive = existingRule(
            "rule-platform", "t-1", "RULE.ANTICOAG", "平台抗凝风险提示",
            "version-platform", RuleDefinitionStatus.PUBLISHED);
        RuleVersion localVersion = existingVersion(
            "version-local", "tenant-A", "rule-local", RuleVersionStatus.PUBLISHED);
        RuleVersion platformVersion = existingVersion(
            "version-platform", "t-1", "rule-platform", RuleVersionStatus.PUBLISHED);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(localReviewed));
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of(platformActive));
        when(definitions.findByTenantIdAndRuleCode("t-1", "RULE.ANTICOAG"))
            .thenReturn(Optional.of(platformActive));
        when(versions.findByVersionIdAndTenantId("version-local", "tenant-A"))
            .thenReturn(Optional.of(localVersion));
        when(versions.findByVersionIdAndTenantId("version-platform", "t-1"))
            .thenReturn(Optional.of(platformVersion));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.DRAFT);
        stubRuleAssetStatus("t-1", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-platform-until-local-active", List.of());

        assertThat(response.items()).extracting(RuleEvaluationItem::ruleId)
            .containsExactly("rule-platform");
    }

    @Test
    void evaluateRunsHigherPriorityFirstAndRecordsExplicitSuppression() {
        RuleDefinition high = governedRule(
            "rule-high", "RULE.HIGH", "version-high", 900, null, 0);
        RuleDefinition low = governedRule(
            "rule-low", "RULE.LOW", "version-low", 100, "RULE.HIGH", 0);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(low, high));
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of());
        when(versions.findByVersionIdAndTenantId("version-high", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(
                "version-high", "tenant-A", "rule-high", RuleVersionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-low", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(
                "version-low", "tenant-A", "rule-low", RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.HIGH", "1", AssetVersionStatus.PUBLISHED);
        stubRuleAssetStatus("tenant-A", "RULE.LOW", "1", AssetVersionStatus.PUBLISHED);

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContextWithPatient(), "evt-priority", List.of());

        assertThat(response.items()).extracting(RuleEvaluationItem::ruleId)
            .containsExactly("rule-high", "rule-low");
        assertThat(response.items()).extracting(RuleEvaluationItem::status)
            .containsExactly(RuleExecutionStatus.SUCCESS, RuleExecutionStatus.SUPPRESSED);
        assertThat(response.items().get(1).suppressedBy()).isEqualTo("RULE.HIGH");
        assertThat(response.items().get(1).actions()).isEmpty();
        assertThat(response.cards()).hasSize(1);
        assertThat(response.highestSeverity()).isEqualTo(RuleRiskLevel.HIGH);

        ArgumentCaptor<RuleExecutionLog> logs = ArgumentCaptor.forClass(RuleExecutionLog.class);
        verify(executions, org.mockito.Mockito.times(2)).save(logs.capture());
        assertThat(logs.getAllValues()).extracting(RuleExecutionLog::status)
            .containsExactly(RuleExecutionStatus.SUCCESS, RuleExecutionStatus.SUPPRESSED);
    }

    @Test
    void evaluateKeepsLowerRuleSuppressedWhenHigherRuleIsDeduplicated() {
        RuleDefinition high = governedRule(
            "rule-high", "RULE.HIGH", "version-high", 900, null, 120);
        RuleDefinition low = governedRule(
            "rule-low", "RULE.LOW", "version-low", 100, "RULE.HIGH", 0);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(low, high));
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of());
        when(versions.findByVersionIdAndTenantId("version-high", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(
                "version-high", "tenant-A", "rule-high", RuleVersionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-low", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(
                "version-low", "tenant-A", "rule-low", RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.HIGH", "1", AssetVersionStatus.PUBLISHED);
        stubRuleAssetStatus("tenant-A", "RULE.LOW", "1", AssetVersionStatus.PUBLISHED);
        when(executions.findRecentSuccessful(
            eq("tenant-A"), eq("MPI-1"), eq("RULE.HIGH:STRONG_REMINDER"), any(Instant.class)))
            .thenReturn(Optional.of(executionLog(
                "rex-first", "rule-high", "version-high", RuleExecutionStatus.SUCCESS,
                "MPI-1", "RULE.HIGH:STRONG_REMINDER", null,
                Instant.now().minus(30, ChronoUnit.SECONDS))));

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContextWithPatient(), "evt-repeat-priority", List.of());

        assertThat(response.items()).extracting(RuleEvaluationItem::status)
            .containsExactly(RuleExecutionStatus.DEDUPLICATED, RuleExecutionStatus.SUPPRESSED);
        assertThat(response.items().get(1).suppressedBy()).isEqualTo("RULE.HIGH");
        assertThat(response.cards()).isEmpty();
    }

    @Test
    void evaluateDoesNotLetInapplicableHighPriorityRuleSuppressApplicableLowerRule() {
        RuleDefinition high = governedRule(
            "rule-high", "RULE.HIGH", "version-high", 900, null, 0);
        RuleDefinition low = governedRule(
            "rule-low", "RULE.LOW", "version-low", 100, "RULE.HIGH", 0);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(low, high));
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of());
        when(versions.findByVersionIdAndTenantId("version-high", "tenant-A"))
            .thenReturn(Optional.of(existingVersionWithSettings(
                "version-high", "rule-high", RuleVersionStatus.PUBLISHED, "OUTPATIENT")));
        when(versions.findByVersionIdAndTenantId("version-low", "tenant-A"))
            .thenReturn(Optional.of(existingVersionWithSettings(
                "version-low", "rule-low", RuleVersionStatus.PUBLISHED, "INPATIENT")));
        stubRuleAssetStatus("tenant-A", "RULE.HIGH", "1", AssetVersionStatus.PUBLISHED);
        stubRuleAssetStatus("tenant-A", "RULE.LOW", "1", AssetVersionStatus.PUBLISHED);

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContextWithPatient(), "evt-applicability", List.of());

        assertThat(response.items()).extracting(RuleEvaluationItem::status)
            .containsExactly(RuleExecutionStatus.NOT_APPLICABLE, RuleExecutionStatus.SUCCESS);
        assertThat(response.items().get(0).hit()).isFalse();
        assertThat(response.items().get(0).actions()).isEmpty();
        assertThat(response.items().get(1).hit()).isTrue();
        assertThat(response.cards()).hasSize(1);
    }

    @Test
    void evaluateDeduplicatesSamePatientAndSemanticActionWithinConfiguredWindow() {
        RuleDefinition rule = governedRule(
            "rule-1", "RULE.ANTICOAG", "version-1", 100, null, 120);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);
        RuleExecutionLog first = executionLog(
            "rex-first", "rule-1", "version-1", RuleExecutionStatus.SUCCESS,
            "MPI-1", "RULE.ANTICOAG:STRONG_REMINDER", null,
            Instant.now().minus(30, ChronoUnit.SECONDS));
        when(executions.findRecentSuccessful(
            eq("tenant-A"), eq("MPI-1"), eq("RULE.ANTICOAG:STRONG_REMINDER"), any(Instant.class)))
            .thenReturn(Optional.of(first));

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContextWithPatient(), "evt-repeat", List.of("rule-1"));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.hit()).isTrue();
            assertThat(item.status()).isEqualTo(RuleExecutionStatus.DEDUPLICATED);
            assertThat(item.deduplicatedFromExecutionId()).isEqualTo("rex-first");
            assertThat(item.actions()).isEmpty();
        });
        assertThat(response.cards()).isEmpty();
        assertThat(response.highestSeverity()).isNull();

        ArgumentCaptor<RuleExecutionLog> log = ArgumentCaptor.forClass(RuleExecutionLog.class);
        verify(executions).save(log.capture());
        assertThat(log.getValue().status()).isEqualTo(RuleExecutionStatus.DEDUPLICATED);
        assertThat(log.getValue().deduplicatedFromExecutionId()).isEqualTo("rex-first");
        assertThat(log.getValue().patientId()).isEqualTo("MPI-1");
        assertThat(log.getValue().encounterId()).isEqualTo("ENC-1");
    }

    @Test
    void diagnoseAssemblesFromExecutionLog() {
        RuleExecutionLog execution = new RuleExecutionLog(
            1L, "rex-1", "tenant-A", "rule-1", "version-1", null, "order-sign",
            "evt-1", "tester", "MPI-1", "ENC-1", "RULE.ANTICOAG:STRONG_REMINDER",
            "sha256:abc", true, RuleRiskLevel.HIGH,
            "[]", "{\"title\":\"抗凝风险提示\"}", RuleExecutionStatus.SUCCESS,
            null, null, null, Instant.now(), Instant.now(), "trace-rule");
        DiagnoseResponse expected = new DiagnoseResponse(
            "rule_execution", "rex-1", "tenant-A", "SUCCESS",
            execution, List.of(), List.of(), Map.of(), null, "trace-rule", null);
        when(executions.findByExecutionIdAndTenantId("rex-1", "tenant-A")).thenReturn(Optional.of(execution));
        when(diagnoseAssembler.assemble(eq("rule_execution"), eq("rex-1"), eq("tenant-A"),
            eq("SUCCESS"), eq(execution), eq(List.of()), eq(Map.of()), any(), eq("trace-rule"),
            any(DiagnoseResponse.ExecutionSummary.class)))
            .thenReturn(expected);

        DiagnoseResponse actual = service.diagnose("rex-1");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<DiagnoseResponse.ExecutionSummary> summaryCaptor =
            ArgumentCaptor.forClass(DiagnoseResponse.ExecutionSummary.class);
        verify(diagnoseAssembler).assemble(eq("rule_execution"), eq("rex-1"), eq("tenant-A"),
            eq("SUCCESS"), eq(execution), eq(List.of()), eq(Map.of()), any(), eq("trace-rule"),
            summaryCaptor.capture());
        assertThat(summaryCaptor.getValue().matchedRuleId()).isEqualTo("rule-1");
        assertThat(summaryCaptor.getValue().matchedVersionId()).isEqualTo("version-1");
        assertThat(summaryCaptor.getValue().degradationReason()).isNull();
    }

    @Test
    void explainReturnsHitChainFromExecutionLog() {
        RuleExecutionLog execution = new RuleExecutionLog(
            1L, "rex-1", "tenant-A", "rule-1", "version-1", null, "order-sign",
            "evt-1", "tester", "MPI-1", "ENC-1", "RULE.ANTICOAG:STRONG_REMINDER",
            "sha256:abc", true, RuleRiskLevel.HIGH,
            "[{\"actionCode\":\"STRONG_REMINDER\"}]", "{\"title\":\"抗凝风险提示\"}",
            RuleExecutionStatus.SUCCESS, null, null, null, Instant.now(), Instant.now(), "trace-rule");
        when(executions.findByExecutionIdAndTenantId("rex-1", "tenant-A")).thenReturn(Optional.of(execution));

        RuleExplanationResponse response = service.explain("rex-1");

        assertThat(response.executionId()).isEqualTo("rex-1");
        assertThat(response.ruleId()).isEqualTo("rule-1");
        assertThat(response.actions().get(0).path("actionCode").asText()).isEqualTo("STRONG_REMINDER");
        assertThat(response.explanation().path("title").asText()).isEqualTo("抗凝风险提示");
        assertThat(response.inputDigest()).isEqualTo("sha256:abc");
    }

    @Test
    void listExecutionsReturnsCurrentTenantPageInReverseChronologicalOrder() {
        Instant executedAt = Instant.parse("2026-06-07T08:00:00Z");
        RuleExecutionLog execution = new RuleExecutionLog(
            1L, "rex-1", "tenant-A", "rule-1", "version-1", null, "order-sign",
            "evt-1", "tester", "MPI-1", "ENC-1", "RULE.ANTICOAG:STRONG_REMINDER",
            "sha256:abc", true, RuleRiskLevel.HIGH,
            "[]", "{\"title\":\"抗凝风险提示\"}", RuleExecutionStatus.SUCCESS,
            null, null, null, executedAt, executedAt, "trace-rule");
        when(executions.countByTenantId("tenant-A")).thenReturn(1L);
        when(executions.pageByTenantId("tenant-A", 0, 20)).thenReturn(List.of(execution));

        PageResponse<RuleExecutionSummaryResponse> response =
            service.listExecutions(new PageRequest(1, 20, null));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.executionId()).isEqualTo("rex-1");
            assertThat(item.ruleId()).isEqualTo("rule-1");
            assertThat(item.triggerPoint()).isEqualTo("order-sign");
            assertThat(item.status()).isEqualTo(RuleExecutionStatus.SUCCESS);
            assertThat(item.executedAt()).isEqualTo(executedAt);
        });
    }

    @Test
    void captureOverrideRequiresRealBlockingActionAndPersistsReason() {
        Instant executedAt = Instant.parse("2026-06-07T08:00:00Z");
        RuleExecutionLog execution = new RuleExecutionLog(
            1L, "rex-1", "tenant-A", "rule-1", "version-1", "runtime-H7", "order-sign",
            "evt-1", "tester", "MPI-1", "ENC-1", "RULE.ANTICOAG:BLOCK",
            "sha256:abc", true, RuleRiskLevel.CRITICAL,
            "[{\"actionCode\":\"BLOCK\"}]", "{\"title\":\"禁忌阻断\"}",
            RuleExecutionStatus.SUCCESS, null, null, null, executedAt, executedAt, "trace-rule");
        when(executions.findByExecutionIdAndTenantId("rex-1", "tenant-A"))
            .thenReturn(Optional.of(execution));
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(overrides.findByTenantIdAndExecutionIdAndActionCode(
            "tenant-A", "rex-1", RuleActionCode.BLOCK))
            .thenReturn(Optional.empty());

        RuleOverrideResponse response = service.captureOverride(
            "rex-1", new RuleOverrideRequest(RuleActionCode.BLOCK, "已完成临床复核，继续处置"));

        assertThat(response.executionId()).isEqualTo("rex-1");
        assertThat(response.actionCode()).isEqualTo(RuleActionCode.BLOCK);
        ArgumentCaptor<RuleOverrideLog> saved = ArgumentCaptor.forClass(RuleOverrideLog.class);
        verify(overrides).save(saved.capture());
        assertThat(saved.getValue().patientId()).isEqualTo("MPI-1");
        assertThat(saved.getValue().encounterId()).isEqualTo("ENC-1");
        assertThat(saved.getValue().overrideReason()).isEqualTo("已完成临床复核，继续处置");
        verify(auditRecorder).record(
            AuditAction.FEEDBACK, "rule_override_log", saved.getValue().overrideId(),
            "记录规则越权 rex-1/BLOCK");
        ArgumentCaptor<OverrideCapturedEvent> eventCap = ArgumentCaptor.forClass(OverrideCapturedEvent.class);
        verify(domainEvents).overrideCaptured(eventCap.capture());
        assertThat(eventCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(eventCap.getValue().traceId()).isEqualTo("trace-rule");
        assertThat(eventCap.getValue().runtimeReleaseId()).isEqualTo("runtime-H7");
        assertThat(eventCap.getValue().ruleCode()).isEqualTo("RULE.ANTICOAG");
        assertThat(eventCap.getValue().overrideId()).isEqualTo(saved.getValue().overrideId());
        assertThat(eventCap.getValue().actionCode()).isEqualTo("BLOCK");
        assertThat(eventCap.getValue().overriddenBy()).isEqualTo("tester");
    }

    @Test
    void captureOverrideCannotCrossTenantBoundary() {
        when(executions.findByExecutionIdAndTenantId("rex-other", "tenant-A"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.captureOverride(
                "rex-other",
                new RuleOverrideRequest(RuleActionCode.BLOCK, "跨租户越权尝试")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("规则执行记录不存在: rex-other")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_002);

        verify(overrides, never()).save(any());
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void captureShadowFeedbackPersistsFalsePositiveAssessment() {
        RuleExecutionLog execution = executionLog(
            "rex-shadow", "rule-1", "version-1", RuleExecutionStatus.SHADOW_RECORDED,
            "MPI-1", "RULE.ANTICOAG:STRONG_REMINDER", null, Instant.now());
        when(executions.findByExecutionIdAndTenantId("rex-shadow", "tenant-A"))
            .thenReturn(Optional.of(execution));
        when(shadowFeedback.findByTenantIdAndExecutionId("tenant-A", "rex-shadow"))
            .thenReturn(Optional.empty());

        RuleShadowFeedbackResponse response = service.captureShadowFeedback(
            "rex-shadow",
            new RuleShadowFeedbackRequest(
                RuleShadowFeedbackDecision.FALSE_POSITIVE,
                "影子提示与当前临床处置不匹配"
            ));

        assertThat(response.executionId()).isEqualTo("rex-shadow");
        assertThat(response.decision()).isEqualTo(RuleShadowFeedbackDecision.FALSE_POSITIVE);
        ArgumentCaptor<RuleShadowFeedback> saved = ArgumentCaptor.forClass(RuleShadowFeedback.class);
        verify(shadowFeedback).save(saved.capture());
        assertThat(saved.getValue().ruleId()).isEqualTo("rule-1");
        assertThat(saved.getValue().patientId()).isEqualTo("MPI-1");
        assertThat(saved.getValue().reason()).isEqualTo("影子提示与当前临床处置不匹配");
        verify(auditRecorder).record(
            AuditAction.FEEDBACK, "rule_shadow_feedback", saved.getValue().feedbackId(),
            "记录规则影子反馈 rex-shadow/FALSE_POSITIVE");
    }

    @Test
    void shadowStatsCountsHitMissAndFalsePositiveFeedback() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(executions.countShadowByRule("tenant-A", "rule-1")).thenReturn(5L);
        when(executions.countShadowByRuleAndHit("tenant-A", "rule-1", true)).thenReturn(3L);
        when(executions.countShadowByRuleAndHit("tenant-A", "rule-1", false)).thenReturn(2L);
        when(shadowFeedback.countByTenantIdAndRuleIdAndDecision(
            "tenant-A", "rule-1", RuleShadowFeedbackDecision.FALSE_POSITIVE)).thenReturn(1L);

        RuleShadowStatsResponse response = service.shadowStats("rule-1");

        assertThat(response.totalExecutions()).isEqualTo(5);
        assertThat(response.hitCount()).isEqualTo(3);
        assertThat(response.missCount()).isEqualTo(2);
        assertThat(response.falsePositiveCount()).isEqualTo(1);
        assertThat(response.hitRate()).isEqualTo(0.6);
        assertThat(response.falsePositiveRate()).isEqualTo(1.0 / 3.0);
    }

    @Test
    void backtestRuleCalculatesSensitivitySpecificityAndPersistsRun() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, missContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.CONFLICT, false, hitContext())
            ));

        RuleBacktestResponse response = service.runBacktest(
            "rule-1", new RuleBacktestRequest("ckd-2026-q1"));

        assertThat(response.sampleCount()).isEqualTo(4);
        assertThat(response.truePositiveCount()).isEqualTo(1);
        assertThat(response.falseNegativeCount()).isEqualTo(1);
        assertThat(response.trueNegativeCount()).isEqualTo(1);
        assertThat(response.falsePositiveCount()).isEqualTo(1);
        assertThat(response.sensitivity()).isEqualTo(0.5);
        assertThat(response.specificity()).isEqualTo(0.5);
        assertThat(response.accuracy()).isEqualTo(0.5);
        assertThat(response.fireRate()).isEqualTo(0.5);
        assertThat(response.falsePositiveCaseIds()).containsExactly("case-CONFLICT");
        assertThat(response.falseNegativeCaseIds()).containsExactly("case-BOUNDARY");
        ArgumentCaptor<RuleBacktestRun> saved = ArgumentCaptor.forClass(RuleBacktestRun.class);
        verify(backtests).save(saved.capture());
        assertThat(saved.getValue().cohortRef()).isEqualTo("ckd-2026-q1");
        assertThat(saved.getValue().versionId()).isEqualTo("version-1");
        verify(auditRecorder).record(
            AuditAction.EXECUTE, "rule_backtest_run", saved.getValue().backtestId(),
            "执行规则历史回测 rule-1/4");
    }

    @Test
    void monitorDriftComparesCurrentWindowWithLatestBacktest() {
        Instant windowStart = Instant.parse("2026-06-01T00:00:00Z");
        Instant windowEnd = Instant.parse("2026-06-07T00:00:00Z");
        RuleBacktestRun baseline = backtestRun("rbt-1", 0.50);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(governance(RuleGovernanceState.MONITOR));
        when(backtests.findLatestByTenantIdAndRuleId("tenant-A", "rule-1"))
            .thenReturn(Optional.of(baseline));
        when(executions.countProductionByRuleBetween("tenant-A", "rule-1", windowStart, windowEnd))
            .thenReturn(10L);
        when(executions.countProductionHitsByRuleBetween("tenant-A", "rule-1", windowStart, windowEnd))
            .thenReturn(8L);

        RuleDriftSnapshotResponse response = service.captureDriftSnapshot(
            "rule-1",
            new RuleDriftSnapshotRequest(windowStart, windowEnd, null, 0.10));

        assertThat(response.baselineBacktestId()).isEqualTo("rbt-1");
        assertThat(response.sampleCount()).isEqualTo(10);
        assertThat(response.hitCount()).isEqualTo(8);
        assertThat(response.baselineFireRate()).isEqualTo(0.50);
        assertThat(response.currentFireRate()).isEqualTo(0.80);
        assertThat(response.driftDelta()).isEqualTo(0.30);
        assertThat(response.status()).isEqualTo(RuleDriftStatus.WARNING);
        ArgumentCaptor<RuleDriftSnapshot> saved = ArgumentCaptor.forClass(RuleDriftSnapshot.class);
        verify(driftSnapshots).save(saved.capture());
        assertThat(saved.getValue().threshold()).isEqualTo(0.10);
        assertThat(saved.getValue().status()).isEqualTo(RuleDriftStatus.WARNING);
    }

    private RuleDefinition existingRule(RuleDefinitionStatus status) {
        return existingRule("rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示", "version-1", status);
    }

    private RuleDefinition existingRule(String ruleId, String tenantId, String ruleCode, String name,
                                        String versionId, RuleDefinitionStatus status) {
        Instant now = Instant.now();
        return new RuleDefinition(
            1L, ruleId, tenantId, ruleCode, name, RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, 100, null, 0, status, versionId,
            "dept-1", now, "tester", now, "tester", "trace-rule");
    }

    private RuleDefinition governedRule(String ruleId, String ruleCode, String versionId,
                                        int priority, String suppressedBy, int dedupeWindowSeconds) {
        return governedRule(
            ruleId, ruleCode, versionId, priority, suppressedBy, dedupeWindowSeconds,
            RuleDefinitionStatus.PUBLISHED);
    }

    private RuleDefinition governedRule(String ruleId, String ruleCode, String versionId,
                                        int priority, String suppressedBy, int dedupeWindowSeconds,
                                        RuleDefinitionStatus status) {
        Instant now = Instant.now();
        return new RuleDefinition(
            1L, ruleId, "tenant-A", ruleCode, ruleCode, RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, priority, suppressedBy, dedupeWindowSeconds,
            status, versionId, "dept-1",
            now, "tester", now, "tester", "trace-rule");
    }

    private RuleExecutionLog executionLog(
            String executionId,
            String ruleId,
            String versionId,
            RuleExecutionStatus status,
            String patientId,
            String semanticKey,
            String deduplicatedFromExecutionId,
            Instant executedAt) {
        return new RuleExecutionLog(
            1L, executionId, "tenant-A", ruleId, versionId, null, "order-sign",
            "evt-1", "tester", patientId, null, semanticKey, "sha256:abc", true,
            RuleRiskLevel.HIGH, "[{\"actionCode\":\"STRONG_REMINDER\"}]",
            "{\"title\":\"抗凝风险提示\"}", status, null, null,
            deduplicatedFromExecutionId, executedAt, executedAt, "trace-rule");
    }

    private RuleGovernance governance(RuleGovernanceState state) {
        return governance("version-1", state);
    }

    private RuleGovernance governance(String versionId, RuleGovernanceState state) {
        return governance("tenant-A", versionId, state);
    }

    private RuleGovernance governance(
            String tenantId,
            String versionId,
            RuleGovernanceState state) {
        Instant now = Instant.parse("2026-06-07T13:30:00Z");
        return new RuleGovernance(
            1L,
            "rg-1",
            tenantId,
            versionId,
            state,
            "author-1",
            "规则治理测试",
            now,
            "author-1",
            now,
            "author-1",
            "trace-rule",
            0L
        );
    }

    private RuleVersion existingVersion(RuleVersionStatus status) {
        return existingVersion("version-1", "tenant-A", "rule-1", status);
    }

    private RuleVersion existingVersion(String versionId, String tenantId, String ruleId, RuleVersionStatus status) {
        return existingVersion(versionId, tenantId, ruleId, status, 1);
    }

    private RuleVersion existingVersion(String versionId, String tenantId, String ruleId,
                                        RuleVersionStatus status, Integer versionNo) {
        Instant now = Instant.now();
        return new RuleVersion(
            1L, versionId, tenantId, ruleId, versionNo, "院内抗凝用药管理规范 2026",
            "初始版本", dsl().toString(), dsl().path("explain").toString(), status,
            null, null, null, now, "tester", now, "tester", "trace-rule");
    }

    private RuleVersion existingVersionWithDsl(RuleVersionStatus status, JsonNode dsl) {
        Instant now = Instant.now();
        return new RuleVersion(
            1L, "version-1", "tenant-A", "rule-1", 1,
            "院内抗凝用药管理规范 2026", "初始版本",
            dsl.toString(), dsl.path("explain").toString(), status,
            null, null, null, now, "tester", now, "tester", "trace-rule");
    }

    private RuleVersion ruleVersionWithAction(
            String versionId, String ruleId, RuleVersionStatus status, String actionCode) {
        Instant now = Instant.now();
        JsonNode actionDsl = dsl().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) actionDsl.path("then").get(0))
            .put("actionCode", actionCode);
        return new RuleVersion(
            1L, versionId, "tenant-A", ruleId, 1, "院内规则治理规范 2026",
            "静态冲突测试", actionDsl.toString(), actionDsl.path("explain").toString(), status,
            null, null, null, now, "tester", now, "tester", "trace-rule");
    }

    private RuleVersion existingVersionWithSettings(
            String versionId,
            String ruleId,
            RuleVersionStatus status,
            String... settings) {
        Instant now = Instant.now();
        JsonNode scopedDsl = dsl().deepCopy();
        var settingArray = json.createArrayNode();
        for (String setting : settings) {
            settingArray.add(setting);
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) scopedDsl.path("applicability"))
            .set("settings", settingArray);
        return new RuleVersion(
            1L, versionId, "tenant-A", ruleId, 1, "院内规则适用域规范 2026",
            "适用域测试", scopedDsl.toString(), scopedDsl.path("explain").toString(), status,
            null, null, null, now, "tester", now, "tester", "trace-rule");
    }

    private RuleTestCase testCase(RuleTestCaseType type, boolean expectedHit, JsonNode input) {
        return testCase(type, expectedHit, input, expectedHit ? "STRONG_REMINDER" : null);
    }

    private RuleTestCase testCase(
            RuleTestCaseType type,
            boolean expectedHit,
            JsonNode input,
            String expectedActionCode) {
        Instant now = Instant.now();
        return new RuleTestCase(
            null, "case-" + type, "tenant-A", "rule-1", "version-1", type,
            "ctx-" + type, input.toString(), expectedHit, expectedHit ? RuleRiskLevel.HIGH : null,
            expectedActionCode, null, null, null, null,
            now, "tester", now, "tester", "trace-rule");
    }

    private RuleBacktestRun backtestRun(String backtestId, double fireRate) {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new RuleBacktestRun(
            1L, backtestId, "tenant-A", "rule-1", "version-1", "ckd-2026-q1",
            20, 8, 2, 9, 1, 0.8889, 0.8182, 0.85, fireRate,
            "[]", "[]", now, "tester", "trace-rule");
    }

    private JsonNode dsl() {
        return read("""
            {
              "applicability": {
                "population": {},
                "orgScope": {},
                "settings": ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
                "effective": {"rolloutPercent": 100}
              },
              "when": {
                "all": [
                  {"fact": "patient.age", "operator": "gte", "value": 18},
                  {"fact": "medications[].code", "operator": "equals", "value": "ANTICOAGULANT"}
                ]
              },
              "then": [
                {"actionCode": "STRONG_REMINDER", "atSeverity": "HIGH", "indicator": "critical", "summary": "抗凝用药需确认出血风险", "detail": "抗凝用药需确认出血风险", "source": {"label": "规则测试来源"}, "suggestions": [], "overrideReasons": [], "requiresPhysicianConfirmation": true}
              ],
              "explain": {
                "title": "抗凝风险提示",
                "reason": "患者年龄和医嘱类别满足规则条件",
                "sourceRef": "院内抗凝用药管理规范 2026"
              }
            }
            """);
    }

    private JsonNode parameterizedCriticalValueDsl() {
        return read("""
            {
              "meta": {
                "parameters": [
                  {"key": "observationCode", "label": "检验项", "valueType": "CODE", "required": true},
                  {"key": "criticalThreshold", "label": "危急阈值", "valueType": "DECIMAL", "required": true},
                  {"key": "returnMinutes", "label": "回报时限分钟", "valueType": "INTEGER", "required": true}
                ]
              },
              "applicability": {
                "population": {},
                "orgScope": {},
                "settings": ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
                "effective": {"rolloutPercent": 100}
              },
              "when": {
                "all": [
                  {
                    "expr": {
                      "field": "observations[].valueNumeric",
                      "select": "latest",
                      "where": {
                        "all": [
                          {
                            "expr": {"field": "observations[].code"},
                            "operator": "equals",
                            "value": {"const": "K"}
                          }
                        ]
                      }
                    },
                    "operator": "gte",
                    "value": 6.5
                  }
                ]
              },
              "then": [
                {"actionCode": "STRONG_REMINDER", "atSeverity": "CRITICAL", "indicator": "critical", "summary": "血钾危急值回报", "detail": "15 分钟内完成危急值回报、确认与记录", "source": {"label": "检验危急值管理制度 2026"}, "suggestions": [], "overrideReasons": [], "requiresPhysicianConfirmation": true}
              ],
              "explain": {
                "title": "血钾危急值回报",
                "summary": "参数化危急值规则"
              }
            }
            """);
    }

    private List<AssetTriggerBindingInput> ruleTriggers(String... triggerPoints) {
        return java.util.Arrays.stream(triggerPoints)
            .map(triggerPoint -> new AssetTriggerBindingInput(
                triggerPoint,
                AssetTriggerPurpose.RULE_EXECUTION,
                List.of()
            ))
            .toList();
    }

    private JsonNode dslWithValueSetReference(String valueSetCode) {
        JsonNode source = dsl().deepCopy();
        com.fasterxml.jackson.databind.node.ObjectNode leaf =
            (com.fasterxml.jackson.databind.node.ObjectNode) source.path("when").path("all").get(1);
        com.fasterxml.jackson.databind.node.ObjectNode value = json.createObjectNode();
        value.put("valueSet", valueSetCode);
        value.put("expandedCount", 1);
        value.putArray("members").add("ANTICOAGULANT");
        leaf.set("value", value);
        return source;
    }

    private JsonNode dslWithActionCardReference(String actionCardCode) {
        JsonNode source = dsl().deepCopy();
        com.fasterxml.jackson.databind.node.ArrayNode then =
            (com.fasterxml.jackson.databind.node.ArrayNode) source.path("then");
        then.removeAll();
        com.fasterxml.jackson.databind.node.ObjectNode action = json.createObjectNode();
        action.put("actionCardRef", actionCardCode);
        then.add(action);
        return source;
    }

    private JsonNode dslWithManualRuntimeVersion(String valueSetCode, String runtimeVersion) {
        JsonNode source = dslWithValueSetReference(valueSetCode);
        ((com.fasterxml.jackson.databind.node.ObjectNode)
            source.path("when").path("all").get(1).path("value"))
            .put("packageVersion", runtimeVersion);
        return source;
    }

    private boolean hasDependency(
            List<AssetDependencyDeclaration> dependencies,
            VersionedAssetType assetType,
            String assetIdentity,
            AssetDependencyKind kind) {
        return dependencies.stream().anyMatch(dependency ->
            dependency.dependsOnAssetType() == assetType
                && dependency.dependsOnIdentity().equals(assetIdentity)
                && dependency.kind() == kind);
    }

    private JsonNode hitContext() {
        return read("""
            {
              "patient": {"age": 72},
              "encounters": [{"encounterId": "ENC-1", "encounterType": "INPATIENT"}],
              "medications": [{"code": "ANTICOAGULANT", "prescriptionStatus": "ACTIVE"}]
            }
            """);
    }

    private JsonNode hitContextWithPatient() {
        return hitContextWithPatient("MPI-1");
    }

    private JsonNode hitContextWithPatient(String patientId) {
        return read("""
            {
              "patient": {"mpi": "%s", "age": 72},
              "encounters": [{"encounterId": "ENC-1", "encounterType": "INPATIENT"}],
              "medications": [{"code": "ANTICOAGULANT", "prescriptionStatus": "ACTIVE"}]
            }
            """.formatted(patientId));
    }

    private JsonNode boundaryContext() {
        return read("""
            {
              "patient": {"age": 18},
              "encounters": [{"encounterId": "ENC-1", "encounterType": "INPATIENT"}],
              "medications": [{"code": "ANTICOAGULANT", "prescriptionStatus": "ACTIVE"}]
            }
            """);
    }

    private JsonNode missContext() {
        return read("""
            {
              "patient": {"age": 12},
              "encounters": [{"encounterId": "ENC-1", "encounterType": "INPATIENT"}],
              "medications": [{"code": "ANTICOAGULANT", "prescriptionStatus": "ACTIVE"}]
            }
            """);
    }

    private AssetVersion assetVersion(String versionId, VersionedAssetType type,
                                      String identity, String versionNo, AssetVersionStatus status) {
        return assetVersion("tenant-A", versionId, type, identity, versionNo, status);
    }

    private AssetVersion assetVersion(String tenantId, String versionId, VersionedAssetType type,
                                      String identity, String versionNo, AssetVersionStatus status) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        String canonicalVersionNo = canonicalAssetVersionNo(versionNo);
        return new AssetVersion(
            1L, versionId, tenantId, type, identity, canonicalVersionNo,
            "dept-1", "ALL",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, status,
            "version:" + versionId, "统一发布测试", null, null,
            now, "tester", now, "tester", "trace-rule");
    }

    private void stubRuleAssetStatus(String tenantId, String identity, String versionNo,
                                     AssetVersionStatus status) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        String canonicalVersionNo = canonicalAssetVersionNo(versionNo);
        String organizationScope = PlatformTenant.isPlatformTenant(tenantId)
            ? PlatformAuthority.PLATFORM_ORG_PATH
            : "tenant:" + tenantId;
        AssetVersion version = new AssetVersion(
            1L, "av-" + identity + "-" + canonicalVersionNo, tenantId,
            VersionedAssetType.RULE, identity, canonicalVersionNo,
            organizationScope, "ALL",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, status,
            "version:av-" + identity + "-" + canonicalVersionNo, "统一发布测试", null, null,
            now, "tester", now, "tester", "trace-rule");
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            tenantId, VersionedAssetType.RULE, identity, canonicalVersionNo))
            .thenReturn(Optional.of(version));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            tenantId, VersionedAssetType.RULE, identity, AssetVersionStatus.PUBLISHED))
            .thenReturn(status == AssetVersionStatus.PUBLISHED ? List.of(version) : List.of());
    }

    private String canonicalAssetVersionNo(String versionNo) {
        return AssetVersionNumbers.canonical(
            AssetVersionNumbers.intSequence(versionNo, "测试统一资产版本号"));
    }

    private VersionReleasePlan releasePlan(String versionId, VersionedAssetType type,
                                           String identity, VersionReleaseStatus status,
                                           String evidence) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new VersionReleasePlan(
            1L, "vrl-" + status, "tenant-A", type, identity, versionId, null,
            "dept-1", "rpv-1", VersionReleaseScopeType.FACILITY, null,
            status, "sha256:impact", "已审核", evidence,
            now, "tester", now, "tester", "trace-rule");
    }

    private void authenticate(RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "tester",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
            )
        );
    }

    private JsonNode read(String source) {
        try {
            return json.readTree(source);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
