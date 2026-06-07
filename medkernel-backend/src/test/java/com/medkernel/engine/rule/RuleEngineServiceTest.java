package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.terminology.MappingCoverageItem;
import com.medkernel.engine.terminology.TerminologyCoverageGate;
import com.medkernel.engine.terminology.TerminologyCoverageIssue;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.SourceTier;
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
    private ReleasePort releasePort;
    private RuleGovernanceService governanceService;
    private InheritanceResolver inheritanceResolver;
    private ContextSnapshotService contextSnapshots;
    private ObjectMapper json;
    private RuleEngineService service;

    @BeforeEach
    void setUp() {
        definitions = mock(RuleDefinitionRepository.class);
        versions = mock(RuleVersionRepository.class);
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
        releasePort = mock(ReleasePort.class);
        governanceService = mock(RuleGovernanceService.class);
        inheritanceResolver = mock(InheritanceResolver.class);
        contextSnapshots = mock(ContextSnapshotService.class);
        json = new ObjectMapper();
        json.findAndRegisterModules();
        applicabilityService = new RuleApplicabilityService(
            applicabilities, new RuleApplicabilityEvaluator(json), json);
        service = new RuleEngineService(
            definitions, versions, testCases, executions, overrides,
            new RuleDslEvaluator(json), applicabilityService,
            auditRecorder, transitions, diagnoseAssembler, json,
            RuleImpactIndex.empty(), TerminologyCoverageGate.noop(),
            versionedAssets, assetVersions, releasePort, governanceService, shadowFeedback,
            backtests, driftSnapshots, inheritanceResolver, contextSnapshots);

        when(definitions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versions.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", "1"))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.DRAFT)));
        when(governanceService.requireGovernance(any(), any()))
            .thenReturn(governance(RuleGovernanceState.DRAFT));
        when(governanceService.signoffs(any(), any())).thenReturn(List.of());

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rule", OrgScope.tenant("tenant-A"), "tester"));
        authenticate(RoleCode.SPECIALIST);
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
            RuleRiskLevel.HIGH, "rpv-1", "dept-1", "院内抗凝用药管理规范 2026",
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
                && command.versionNo().equals("1")
                && command.organizationScope().equals("dept-1")
                && command.applicableScope().equals("rpv-1")
        ));
        verify(governanceService).initialize(
            "tenant-A", response.versionId(), RuleRiskLevel.HIGH, "tester", "trace-rule");
        verify(auditRecorder).record(AuditAction.CREATE, "rule_definition", response.ruleId(), "创建规则 RULE.ANTICOAG");
        verify(transitions).record("rule_definition", response.ruleId(), null, "DRAFT", "CREATE_RULE", null);
    }

    @Test
    void draftTransitionSubmitsOnlyForPeerReview() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        RuleGovernance draft = governance(RuleGovernanceState.DRAFT);
        RuleGovernance peerReview = governance(RuleGovernanceState.PEER_REVIEW);
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
            .thenReturn(draft, peerReview);
        when(governanceService.transition(
            "tenant-A", "version-1", RuleGovernanceState.PEER_REVIEW,
            "提交同行评审", "tester", "trace-rule"))
            .thenReturn(peerReview);
        when(governanceService.signoffs("tenant-A", "version-1")).thenReturn(List.of());
        when(releasePort.submitForReview(any())).thenReturn(releasePlan(
            "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG",
            VersionReleaseStatus.PENDING_REVIEW, "PENDING_REVIEW 提交审核：规则影响摘要"));
        RuleImpactResponse impact = service.impact("rule-1");

        RuleGovernanceResponse response = service.transitionGovernance(
            "rule-1",
            new RuleGovernanceTransitionRequest(
                RuleGovernanceState.PEER_REVIEW,
                impact.impactDigest(),
                "提交同行评审"
            )
        );

        assertThat(response.state()).isEqualTo(RuleGovernanceState.PEER_REVIEW);
        assertThat(response.testResults()).hasSize(4);
        assertThat(response.releaseEvidence())
            .containsExactly("PENDING_REVIEW 提交审核：规则影响摘要");
        verify(releasePort).submitForReview(any());
        verify(releasePort, org.mockito.Mockito.never()).approveForSilentObservation(any());
        verify(releasePort, org.mockito.Mockito.never()).releaseGray(any());
    }

    @Test
    void signoffUsesAuthenticatedRoleInsteadOfRequestRoleCodes() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(committee);
        when(governanceService.signoffs("tenant-A", "version-1")).thenReturn(List.of());
        when(governanceService.recordSignoff(
            "tenant-A",
            "version-1",
            RuleSignoffStage.COMMITTEE,
            RuleSignoffDecision.APPROVED,
            "委员会同意进入影子验证",
            "tester",
            RoleCode.MEDICAL_AFFAIRS,
            "trace-rule"
        )).thenReturn(committee);
        authenticate(RoleCode.MEDICAL_AFFAIRS);

        service.signoffGovernance(
            "rule-1",
            new RuleSignoffRequest(
                null, null, null, null, null, null, null, null, null, null,
                List.of(RoleCode.HOSPITAL_ADMIN.code()), null,
                RuleSignoffStage.COMMITTEE,
                RuleSignoffDecision.APPROVED,
                "委员会同意进入影子验证"
            )
        );

        verify(governanceService).recordSignoff(
            "tenant-A",
            "version-1",
            RuleSignoffStage.COMMITTEE,
            RuleSignoffDecision.APPROVED,
            "委员会同意进入影子验证",
            "tester",
            RoleCode.MEDICAL_AFFAIRS,
            "trace-rule"
        );
    }

    @Test
    void rejectedSignoffReturnsUnifiedVersionToDraftForNextReviewRound() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        RuleGovernance draft = governance(RuleGovernanceState.DRAFT);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(governanceService.recordSignoff(
            "tenant-A",
            "version-1",
            RuleSignoffStage.COMMITTEE,
            RuleSignoffDecision.REJECTED,
            "证据不足，退回修订",
            "tester",
            RoleCode.MEDICAL_AFFAIRS,
            "trace-rule"
        )).thenReturn(draft);
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(draft);
        when(releasePort.rejectReview(any())).thenReturn(releasePlan(
            "av-rule-default",
            VersionedAssetType.RULE,
            "RULE.ANTICOAG",
            VersionReleaseStatus.REVIEW_REJECTED,
            "REVIEW_REJECTED 审核拒绝：证据不足，退回修订"
        ));
        authenticate(RoleCode.MEDICAL_AFFAIRS);

        RuleGovernanceResponse response = service.signoffGovernance(
            "rule-1",
            new RuleSignoffRequest(
                RuleSignoffStage.COMMITTEE,
                RuleSignoffDecision.REJECTED,
                "证据不足，退回修订"
            )
        );

        assertThat(response.state()).isEqualTo(RuleGovernanceState.DRAFT);
        assertThat(response.releaseEvidence())
            .containsExactly("REVIEW_REJECTED 审核拒绝：证据不足，退回修订");
        verify(releasePort).rejectReview(any());
    }

    @Test
    void committeeTransitionPublishesContentAndEntersShadowOnly() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE);
        RuleGovernance shadow = governance(RuleGovernanceState.SHADOW);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(committee, shadow);
        when(governanceService.transition(
            "tenant-A", "version-1", RuleGovernanceState.SHADOW,
            "会签完成，进入影子验证", "tester", "trace-rule"))
            .thenReturn(shadow);
        when(governanceService.signoffs("tenant-A", "version-1")).thenReturn(List.of());
        when(releasePort.approveForSilentObservation(any())).thenReturn(releasePlan(
            "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG",
            VersionReleaseStatus.SILENT_OBSERVATION,
            "SILENT_OBSERVATION 静默观察：会签完成"));
        authenticate(RoleCode.MEDICAL_AFFAIRS);
        RuleImpactResponse impact = service.impact("rule-1");

        RuleGovernanceResponse response = service.transitionGovernance(
            "rule-1",
            new RuleGovernanceTransitionRequest(
                RuleGovernanceState.SHADOW,
                impact.impactDigest(),
                "会签完成，进入影子验证"
            )
        );

        assertThat(response.state()).isEqualTo(RuleGovernanceState.SHADOW);
        verify(releasePort).approveForSilentObservation(any());
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
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", "1"))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.ACTIVE)));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(monitoring, retired);
        when(governanceService.transition(
            "tenant-A", "version-1", RuleGovernanceState.RETIRED,
            "新版规则完成替代", "tester", "trace-rule"))
            .thenReturn(retired);
        when(governanceService.signoffs("tenant-A", "version-1")).thenReturn(List.of());
        authenticate(RoleCode.MEDICAL_AFFAIRS);

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
            saved -> saved.status() == AssetVersionStatus.ARCHIVED
                && saved.effectiveTo() != null));
    }

    @Test
    void createRuleRejectsLegacyTriggerEnumName() {
        JsonNode legacyDsl = dsl().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacyDsl).put("trigger", "ORDER_SIGN");

        assertThatThrownBy(() -> service.createRule(new RuleCreateRequest(
            "RULE.LEGACY.TRIGGER", "旧触发点规则", RuleType.ORDER, RuleAuthoringMode.DSL,
            RuleRiskLevel.MEDIUM, "rpv-1", "dept-1", "规则触发点契约",
            "拒绝旧枚举名", legacyDsl, legacyDsl.path("explain"))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("触发点必须使用客户面编码")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_001);
    }

    @Test
    void specialistCannotMoveCommitteeRuleIntoShadow() {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(governance(RuleGovernanceState.COMMITTEE));
        authenticate(RoleCode.SPECIALIST);
        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.SHADOW,
                    impact.impactDigest(),
                    "尝试进入影子运行"
                )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("仅医务处或医院管理员")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void createRuleRejectsDslWithoutApplicability() {
        JsonNode invalidDsl = dsl().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidDsl).remove("applicability");

        assertThatThrownBy(() -> service.createRule(new RuleCreateRequest(
            "RULE.NO.APPLICABILITY", "缺少适用域", RuleType.ORDER, RuleAuthoringMode.DSL,
            RuleRiskLevel.MEDIUM, "rpv-1", "dept-1", "规则适用域契约",
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
            List.of(), "rpv-1", "RULE.ANTICOAG", "抗凝风险提示", RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, 100, null, 0, "dept-1",
            "院内抗凝用药管理规范 2026", "更新解释", updatedDsl, updatedDsl.path("explain")
        ));

        verify(versionedAssets).updateDraft(org.mockito.Mockito.argThat(
            (AssetVersionDraftUpdateCommand command) ->
                command.tenantId().equals("tenant-A")
                    && command.versionId().equals("av-rule-default")
                    && command.assetIdentity().equals("RULE.ANTICOAG")
                    && command.organizationScope().equals("dept-1")
                    && command.applicableScope().equals("rpv-1")
                    && command.content().contains("\"ruleCode\":\"RULE.ANTICOAG\"")
                    && command.content().contains("\"explain\":\"更新后的抗凝风险解释\"")
                    && command.safetyPolicy() == AssetVersionSafetyPolicy.NORMAL
                    && command.actor().equals("tester")
        ));
    }

    @Test
    void updateRuleRejectsContentChangeAfterPeerReviewStarts() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(governance(RuleGovernanceState.PEER_REVIEW));

        assertThatThrownBy(() -> service.updateRule("rule-1", new RuleUpdateRequest(
            null, null, null, null, null, null, null, null, null, null,
            List.of(), "rpv-1", "RULE.ANTICOAG", "抗凝风险提示", RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, 100, null, 0, "dept-1",
            "院内抗凝用药管理规范 2026", "审核中禁止修改", dsl(), dsl().path("explain")
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
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", "1"))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.ACTIVE)));

        RuleDetailResponse response = service.detail("rule-1");

        assertThat(response.deploymentStatus()).isEqualTo(AssetVersionStatus.ACTIVE);
    }

    @Test
    void addTestCasePersistsAgainstCurrentVersion() throws Exception {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A")).thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A")).thenReturn(Optional.of(version));
        when(contextSnapshots.findById("ctx-1")).thenReturn(new ContextSnapshotResponse(
            "ctx-1", ContextSnapshotStatus.ACTIVE, validResources(), "pkg-1",
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
            "ctx-old", ContextSnapshotStatus.SUPERSEDED, validResources(), "pkg-1",
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
                    RuleGovernanceState.PEER_REVIEW,
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
                    RuleGovernanceState.PEER_REVIEW,
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
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.transitionGovernance(
                "rule-1",
                new RuleGovernanceTransitionRequest(
                    RuleGovernanceState.PEER_REVIEW,
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
                    RuleGovernanceState.PEER_REVIEW,
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
                    RuleGovernanceState.PEER_REVIEW,
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
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", "1"))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-1", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.PUBLISHED)));
        when(releasePort.releaseFull(any())).thenReturn(releasePlan(
            "av-rule-1", VersionedAssetType.RULE, "RULE.ANTICOAG",
            VersionReleaseStatus.FULL, "FULL 全量激活：规则影响摘要"));
        RuleGovernance canary = governance(RuleGovernanceState.CANARY);
        RuleGovernance full = governance(RuleGovernanceState.FULL);
        when(governanceService.requireGovernance("tenant-A", "version-1"))
            .thenReturn(canary, full);
        when(governanceService.transition(
            "tenant-A", "version-1", RuleGovernanceState.FULL,
            "院级管理员确认全量激活", "tester", "trace-rule"))
            .thenReturn(full);
        when(governanceService.signoffs("tenant-A", "version-1")).thenReturn(List.of());
        authenticate(RoleCode.HOSPITAL_ADMIN);
        RuleImpactResponse impact = service.impact("rule-1");

        RuleGovernanceResponse response = service.transitionGovernance(
            "rule-1",
            new RuleGovernanceTransitionRequest(
                RuleGovernanceState.FULL,
                impact.impactDigest(),
                "院级管理员确认全量激活"
            )
        );

        assertThat(response.releaseEvidence()).contains("FULL 全量激活：规则影响摘要");
        verify(releasePort).releaseFull(any());
        verify(auditRecorder).record(
            AuditAction.PUBLISH, "rule_definition", "rule-1", "规则治理推进至 FULL");
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
    void highRiskPeerReviewWithoutImpactDigestIsDeniedBeforeTesting() {
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
                    RuleGovernanceState.PEER_REVIEW,
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
            definitions, versions, testCases, executions, overrides,
            new RuleDslEvaluator(json), applicabilityService,
            auditRecorder, transitions, diagnoseAssembler, json,
            RuleImpactIndex.empty(), coverageGate,
            versionedAssets, assetVersions, releasePort, governanceService, shadowFeedback,
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
                    RuleGovernanceState.PEER_REVIEW,
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
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.ACTIVE);
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
    }

    @Test
    void evaluateShadowRuleRecordsPotentialHitWithoutClinicalAction() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);
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
        stubRuleAssetStatus("tenant-A", "RULE.HIGH", "1", AssetVersionStatus.PUBLISHED);
        stubRuleAssetStatus("tenant-A", "RULE.LOW", "1", AssetVersionStatus.ACTIVE);
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
            "snapshot-1", ContextSnapshotStatus.ACTIVE, validResources(), "pkg-1",
            QualityStatus.VALID, List.of(), Map.of(), Instant.now(), "trace-snapshot"));
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of());
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of());

        RuleEvaluateResponse response = service.evaluate(new RuleEvaluateRequest(
            "order-sign", "snapshot-1", "evt-1", List.of()));

        assertThat(response.items()).isEmpty();
        verify(contextSnapshots).findById("snapshot-1");
    }

    @Test
    void evaluateNeverExecutesReviewedRuleBeforeUnifiedVersionIsActive() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG", "1"))
            .thenReturn(Optional.of(assetVersion(
                "av-rule-default", VersionedAssetType.RULE, "RULE.ANTICOAG", "1",
                AssetVersionStatus.PUBLISHED)));

        RuleEvaluateResponse response = service.evaluateContext(
            "order-sign", hitContext(), "evt-reviewed", List.of("rule-1"));

        assertThat(response.items()).isEmpty();
        verify(executions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void evaluateSpecifiedRuleUsesInheritanceResolvedVersionForCurrentDepartment() {
        InheritanceResolver resolver = mock(InheritanceResolver.class);
        RuleEngineService inheritedService = new RuleEngineService(
            definitions, versions, testCases, executions, overrides,
            new RuleDslEvaluator(json), applicabilityService,
            auditRecorder, transitions, diagnoseAssembler, json,
            RuleImpactIndex.empty(), TerminologyCoverageGate.noop(),
            versionedAssets, assetVersions, releasePort, governanceService, shadowFeedback,
            backtests, driftSnapshots, resolver, contextSnapshots);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rule", new OrgScope("tenant-A", null, "hosp-1", null, null, "dept-1", null), "tester"));
        RuleDefinition rule = existingRule(
            "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            "version-1", RuleDefinitionStatus.PUBLISHED);
        RuleVersion effectiveVersion = existingVersion(
            "version-2", "tenant-A", "rule-1", RuleVersionStatus.PUBLISHED, 2);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));
        when(resolver.resolve(any())).thenReturn(new ResolvedAssetVersion(
            assetVersion("av-rule-2", VersionedAssetType.RULE, "RULE.ANTICOAG", "2", AssetVersionStatus.ACTIVE),
            "dept-1", false, true, false, null, SourceTier.ORG));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.ANTICOAG"))
            .thenReturn(Optional.of(rule));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-1", "tenant-A", 2))
            .thenReturn(Optional.of(effectiveVersion));
        when(versions.findByVersionIdAndTenantId("version-2", "tenant-A"))
            .thenReturn(Optional.of(effectiveVersion));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "2", AssetVersionStatus.ACTIVE);

        RuleEvaluateResponse response = inheritedService.evaluateContext(
            "order-sign", hitContext(), "evt-effective", List.of("rule-1"));

        assertThat(response.items()).singleElement()
            .extracting(RuleEvaluationItem::versionId)
            .isEqualTo("version-2");
        verify(resolver).resolve(any());
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
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);
        stubRuleAssetStatus("t-1", "RULE.ANTICOAG", "1", AssetVersionStatus.ACTIVE);

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
        stubRuleAssetStatus("t-1", "RULE.PLATFORM.ANTICOAG", "1", AssetVersionStatus.ACTIVE);

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
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.ACTIVE);
        stubRuleAssetStatus("t-1", "RULE.DVT", "1", AssetVersionStatus.ACTIVE);

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
        when(versions.findByVersionIdAndTenantId("version-local", "tenant-A"))
            .thenReturn(Optional.of(localVersion));
        when(versions.findByVersionIdAndTenantId("version-platform", "t-1"))
            .thenReturn(Optional.of(platformVersion));
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.PUBLISHED);
        stubRuleAssetStatus("t-1", "RULE.ANTICOAG", "1", AssetVersionStatus.ACTIVE);

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
        stubRuleAssetStatus("tenant-A", "RULE.HIGH", "1", AssetVersionStatus.ACTIVE);
        stubRuleAssetStatus("tenant-A", "RULE.LOW", "1", AssetVersionStatus.ACTIVE);

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
        stubRuleAssetStatus("tenant-A", "RULE.HIGH", "1", AssetVersionStatus.ACTIVE);
        stubRuleAssetStatus("tenant-A", "RULE.LOW", "1", AssetVersionStatus.ACTIVE);
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
        stubRuleAssetStatus("tenant-A", "RULE.HIGH", "1", AssetVersionStatus.ACTIVE);
        stubRuleAssetStatus("tenant-A", "RULE.LOW", "1", AssetVersionStatus.ACTIVE);

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
        stubRuleAssetStatus("tenant-A", "RULE.ANTICOAG", "1", AssetVersionStatus.ACTIVE);
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
            1L, "rex-1", "tenant-A", "rule-1", "version-1", "order-sign",
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
            1L, "rex-1", "tenant-A", "rule-1", "version-1", "order-sign",
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
            1L, "rex-1", "tenant-A", "rule-1", "version-1", "order-sign",
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
            1L, "rex-1", "tenant-A", "rule-1", "version-1", "order-sign",
            "evt-1", "tester", "MPI-1", "ENC-1", "RULE.ANTICOAG:BLOCK",
            "sha256:abc", true, RuleRiskLevel.CRITICAL,
            "[{\"actionCode\":\"BLOCK\"}]", "{\"title\":\"禁忌阻断\"}",
            RuleExecutionStatus.SUCCESS, null, null, null, executedAt, executedAt, "trace-rule");
        when(executions.findByExecutionIdAndTenantId("rex-1", "tenant-A"))
            .thenReturn(Optional.of(execution));
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
            "rpv-1", "dept-1", now, "tester", now, "tester", "trace-rule");
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
            status, versionId, "rpv-1", "dept-1",
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
            1L, executionId, "tenant-A", ruleId, versionId, "order-sign",
            "evt-1", "tester", patientId, null, semanticKey, "sha256:abc", true,
            RuleRiskLevel.HIGH, "[{\"actionCode\":\"STRONG_REMINDER\"}]",
            "{\"title\":\"抗凝风险提示\"}", status, null, null,
            deduplicatedFromExecutionId, executedAt, executedAt, "trace-rule");
    }

    private RuleGovernance governance(RuleGovernanceState state) {
        return governance("version-1", state);
    }

    private RuleGovernance governance(String versionId, RuleGovernanceState state) {
        Instant now = Instant.parse("2026-06-07T13:30:00Z");
        return new RuleGovernance(
            1L,
            "rg-1",
            "tenant-A",
            versionId,
            state,
            2,
            1,
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
        Instant now = Instant.now();
        return new RuleTestCase(
            null, "case-" + type, "tenant-A", "rule-1", "version-1", type,
            "ctx-" + type, input.toString(), expectedHit, expectedHit ? RuleRiskLevel.HIGH : null,
            expectedHit ? "STRONG_REMINDER" : null, null, null, null, null,
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
              "trigger": "order-sign",
              "applicability": {
                "population": {},
                "orgScope": {},
                "settings": ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
                "effective": {"rolloutPercent": 100}
              },
              "when": {
                "all": [
                  {"fact": "patient.age", "operator": "gte", "value": 18},
                  {"fact": "order.drugClass", "operator": "equals", "value": "ANTICOAGULANT"}
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

    private JsonNode hitContext() {
        return read("""
            {
              "patient": {"age": 72},
              "encounters": [{"encounterId": "ENC-1", "encounterType": "INPATIENT"}],
              "order": {"drugClass": "ANTICOAGULANT"}
            }
            """);
    }

    private JsonNode hitContextWithPatient() {
        return read("""
            {
              "patient": {"mpi": "MPI-1", "age": 72},
              "encounters": [{"encounterId": "ENC-1", "encounterType": "INPATIENT"}],
              "order": {"drugClass": "ANTICOAGULANT"}
            }
            """);
    }

    private JsonNode boundaryContext() {
        return read("""
            {
              "patient": {"age": 18},
              "encounters": [{"encounterId": "ENC-1", "encounterType": "INPATIENT"}],
              "order": {"drugClass": "ANTICOAGULANT"}
            }
            """);
    }

    private JsonNode missContext() {
        return read("""
            {
              "patient": {"age": 12},
              "encounters": [{"encounterId": "ENC-1", "encounterType": "INPATIENT"}],
              "order": {"drugClass": "ANTICOAGULANT"}
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
        return new AssetVersion(
            1L, versionId, tenantId, type, identity, versionNo,
            "dept-1", "rpv-1",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, status,
            "version:" + versionId, "统一发布测试", null, null,
            now, "tester", now, "tester", "trace-rule");
    }

    private void stubRuleAssetStatus(String tenantId, String identity, String versionNo,
                                     AssetVersionStatus status) {
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            tenantId, VersionedAssetType.RULE, identity, versionNo))
            .thenReturn(Optional.of(assetVersion(
                tenantId, "av-" + identity + "-" + versionNo,
                VersionedAssetType.RULE, identity, versionNo, status)));
    }

    private VersionReleasePlan releasePlan(String versionId, VersionedAssetType type,
                                           String identity, VersionReleaseStatus status,
                                           String evidence) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new VersionReleasePlan(
            1L, "vrl-" + status, "tenant-A", type, identity, versionId, null,
            "dept-1", "rpv-1", VersionReleaseScopeType.HOSPITAL, null,
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
