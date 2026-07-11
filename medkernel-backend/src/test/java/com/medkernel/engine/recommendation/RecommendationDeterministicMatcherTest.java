package com.medkernel.engine.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeEffectiveVersionResolver;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.pathway.PathwayEntryMode;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleApplicabilityEvaluator;
import com.medkernel.engine.rule.RuleApplicabilityRepository;
import com.medkernel.engine.rule.RuleApplicabilityService;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleDslAssetMaterializer;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.engine.rule.RuntimeReleaseRuleSelector;
import com.medkernel.engine.rule.RuntimeRuleReference;
import com.medkernel.engine.rule.RuntimeRuleSelection;
import com.medkernel.engine.safety.ClinicalRedlineMatcher;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.ResolvedDeclarativeAsset;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RecommendationDeterministicMatcherTest {

    private final ContextSnapshotService snapshots = mock(ContextSnapshotService.class);
    private final RuleDefinitionRepository ruleDefinitions = mock(RuleDefinitionRepository.class);
    private final RuleVersionRepository ruleVersions = mock(RuleVersionRepository.class);
    private final RuntimeReleaseRuleSelector runtimeRuleSelector = mock(RuntimeReleaseRuleSelector.class);
    private final RuleApplicabilityRepository ruleApplicabilities =
        mock(RuleApplicabilityRepository.class);
    private final PatientPathwayRepository patientPathways = mock(PatientPathwayRepository.class);
    private final PathwayTemplateRepository pathwayTemplates = mock(PathwayTemplateRepository.class);
    private final KnowledgeEffectiveVersionResolver effectiveKnowledgeVersions =
        mock(KnowledgeEffectiveVersionResolver.class);
    private final ClinicalRedlineMatcher redlineMatcher = mock(ClinicalRedlineMatcher.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final RecommendationDeterministicMatcher matcher = new RecommendationDeterministicMatcher(
        snapshots,
        ruleDefinitions,
        ruleVersions,
        runtimeRuleSelector,
        new RuleDslEvaluator(json),
        new RuleApplicabilityService(
            ruleApplicabilities, new RuleApplicabilityEvaluator(json), json),
        patientPathways,
        pathwayTemplates,
        effectiveKnowledgeVersions,
        redlineMatcher,
        json
    );

    @BeforeEach
    void setUp() {
        when(redlineMatcher.match(any(), any(), any())).thenReturn(List.of());
        when(runtimeRuleSelector.select(anyString(), anyString(), anyString()))
            .thenReturn(new RuntimeRuleSelection("runtime-release-test", "baseline-test", List.of()));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void matchesPublishedRuleAgainstContextAndReturnsTraceableCandidate() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(ruleDefinition()));
        stubRuleResolution(ruleDefinition(), ruleVersion(), null);
        stubKnowledgeResolution(knowledgeIdentity(), knowledgeVersion());
        when(patientPathways.findByPatientPathwayIdAndTenantId("pathway-1", "tenant-A"))
            .thenReturn(Optional.of(patientPathway()));
        when(pathwayTemplates.findByTemplateIdAndTenantId("template-1", "tenant-A"))
            .thenReturn(Optional.of(pathwayTemplate()));

        List<RecommendationCardRequest> matches = matcher.match(triggerRequest());

        assertThat(matches).hasSize(1);
        RecommendationCardRequest card = matches.get(0);
        assertThat(card.cardCode()).isEqualTo("RULE.RISK_GENDER.v1");
        assertThat(card.cardType()).isEqualTo(RecommendationCardType.RISK);
        assertThat(card.riskLevel()).isEqualTo(RecommendationRiskLevel.MEDIUM);
        assertThat(card.interruptLevel()).isEqualTo(RecommendationInterruptLevel.INFO);
        assertThat(card.aiGenerated()).isFalse();
        assertThat(card.sourceSummary()).contains("RISK_GENDER").contains("v1");
        assertThat(card.explanationJson())
            .contains("conditionEvidence")
            .contains("patient.gender")
            .contains("\"knowledgeIdentityCode\":\"RISK_GENDER\"")
            .contains("\"knowledgeVersionId\":100");
        assertThat(card.sources())
            .extracting(RecommendationSourceRequest::sourceType)
            .containsExactly(
                RecommendationSourceType.RULE,
                RecommendationSourceType.KNOWLEDGE,
                RecommendationSourceType.CONTEXT,
                RecommendationSourceType.PATHWAY
            );
        assertThat(card.sources())
            .extracting(RecommendationSourceRequest::sourceRefId)
            .containsExactly("rule-risk", "RISK_GENDER", "snapshot-1", "pathway-1");
        RecommendationSourceRequest knowledge = card.sources().get(1);
        assertThat(knowledge.sourceVersion()).isEqualTo("2026.1");
        assertThat(knowledge.citationLocator()).isEqualTo("knowledge_version:tenant-A:100");
        assertThat(knowledge.sourceHash()).isEqualTo("sha256:knowledge-risk-gender");
    }

    @Test
    void matchesRuntimeBoundRuleWithoutLegacyDslTriggerField() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(ruleDefinition()));
        stubRuleResolution(ruleDefinition(), ruleVersionWithoutLegacyTrigger(), null);

        List<RecommendationCardRequest> matches =
            matcher.match(triggerRequestWithoutPathway("medication-prescribe"));

        assertThat(matches).singleElement().satisfies(card -> {
            assertThat(card.cardCode()).isEqualTo("RULE.RISK_GENDER.v1");
            assertThat(card.explanationJson()).contains("patient.gender");
        });
    }

    @Test
    void reviewedRuleDoesNotProduceRecommendationBeforeUnifiedActivation() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());

        List<RecommendationCardRequest> matches = matcher.match(triggerRequestWithoutPathway());

        assertThat(matches).isEmpty();
    }

    @Test
    void doesNotProduceRecommendationWhenRuleIsOutsideClinicalSetting() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(ruleDefinition()));
        stubRuleResolution(ruleDefinition(), ruleVersionForSetting("OUTPATIENT"), null);

        assertThat(matcher.match(triggerRequestWithoutPathway())).isEmpty();
    }

    @Test
    void fallsBackToPlatformRuleAndKnowledgeWhenTenantHasNoOverride() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        RecommendationTriggerRequest request = triggerRequestWithoutPathway();
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of());
        when(ruleDefinitions.findPublishedByTenantId("t-1")).thenReturn(List.of(platformRuleDefinition()));
        stubRuleResolution(platformRuleDefinition(), platformRuleVersion(), null);
        stubKnowledgeResolution(platformKnowledgeIdentity(), platformKnowledgeVersion());

        List<RecommendationCardRequest> matches = matcher.match(request);

        assertThat(matches).hasSize(1);
        RecommendationCardRequest card = matches.get(0);
        assertThat(card.cardCode()).isEqualTo("RULE.RISK_GENDER.v1");
        assertThat(card.explanationJson())
            .contains("\"knowledgeSourceTenantId\":\"t-1\"")
            .contains("\"knowledgeVersionId\":200");
        assertThat(card.sources())
            .extracting(RecommendationSourceRequest::sourceType)
            .containsExactly(
                RecommendationSourceType.RULE,
                RecommendationSourceType.KNOWLEDGE,
                RecommendationSourceType.CONTEXT
            );
    }

    @Test
    void keepsPlatformActiveRuleUntilLocalReviewedOverrideIsActivated() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(ruleDefinition()));
        when(ruleDefinitions.findPublishedByTenantId("t-1")).thenReturn(List.of(platformRuleDefinition()));
        stubRuleResolution(platformRuleDefinition(), platformRuleVersion(), null);
        stubKnowledgeResolution(platformKnowledgeIdentity(), platformKnowledgeVersion());

        List<RecommendationCardRequest> matches = matcher.match(triggerRequestWithoutPathway());

        assertThat(matches).singleElement().satisfies(card ->
            assertThat(card.sources().get(0).sourceRefId()).isEqualTo("rule-platform-risk"));
    }

    @Test
    void recordsRuntimeReleaseAssetSourceWhenBuildingRecommendation() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss",
            new OrgScope("tenant-A", "group-A", "hospital-A", "campus-A", "site-A", "dept-A", null, "specialty-A"),
            "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        stubRuleResolution(ruleDefinition(), ruleVersionNo("rv-risk-v2", 2), tenantOverrideRuleAsset());
        stubKnowledgeResolution(knowledgeIdentity(), knowledgeVersion());

        List<RecommendationCardRequest> matches = matcher.match(triggerRequestWithoutPathway());

        assertThat(matches).hasSize(1);
        RecommendationCardRequest card = matches.get(0);
        assertThat(card.cardCode()).isEqualTo("RULE.RISK_GENDER.v2");
        assertThat(card.sourceSummary())
            .contains("RISK_GENDER")
            .contains("运行版本=runtime-release-test")
            .contains("asset_version=av-RISK_GENDER-2")
            .contains("来源层=HOSPITAL")
            .contains("sha256:tenant-rule-v2");
        RecommendationSourceRequest ruleSource = card.sources().get(0);
        assertThat(ruleSource.sourceHash()).isEqualTo("sha256:tenant-rule-v2");
        assertThat(ruleSource.summary())
            .contains("运行版本=runtime-release-test")
            .contains("asset_version=av-RISK_GENDER-2")
            .contains("来源层=HOSPITAL");
        assertThat(card.explanationJson())
            .contains("\"runtimeReleaseId\":\"runtime-release-test\"")
            .contains("\"assetVersionId\":\"av-RISK_GENDER-2\"")
            .contains("\"sourceLayer\":\"HOSPITAL\"")
            .contains("\"contentHash\":\"sha256:tenant-rule-v2\"");
    }

    @Test
    void appendsClinicalRedlineMatchesFromRuntimeMatcher() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of());
        when(ruleDefinitions.findPublishedByTenantId("t-1")).thenReturn(List.of());
        when(redlineMatcher.match(any(), any(), any())).thenReturn(List.of(redlineCard()));

        List<RecommendationCardRequest> matches = matcher.match(triggerRequest());

        assertThat(matches).hasSize(1);
        RecommendationCardRequest card = matches.get(0);
        assertThat(card.cardCode()).isEqualTo("REDLINE.RDL-DDI-001.v2026.2");
        assertThat(card.riskLevel()).isEqualTo(RecommendationRiskLevel.CRITICAL);
        assertThat(card.interruptLevel()).isEqualTo(RecommendationInterruptLevel.STRONG_INTERRUPTIVE);
        assertThat(card.sources())
            .extracting(RecommendationSourceRequest::sourceType)
            .containsExactly(RecommendationSourceType.REDLINE, RecommendationSourceType.KNOWLEDGE);
    }

    @Test
    void materializesActionCardFromSnapshotRuntimeReleaseWhenBuildingRecommendation() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(ruleDefinition()));
        stubRuleResolution(ruleDefinition(), actionCardRuleVersion(), null);
        RecommendationDeterministicMatcher materializingMatcher = matcherWith(materializingRuleEvaluator());

        List<RecommendationCardRequest> matches = materializingMatcher.match(triggerRequestWithoutPathway());

        assertThat(matches).singleElement().satisfies(card -> {
            assertThat(card.riskLevel()).isEqualTo(RecommendationRiskLevel.HIGH);
            assertThat(card.summary()).isEqualTo("请结合标本状态复核。");
            assertThat(card.requiresPhysicianConfirmation()).isTrue();
            assertThat(card.sources())
                .extracting(RecommendationSourceRequest::sourceType)
                .containsExactly(RecommendationSourceType.RULE, RecommendationSourceType.CONTEXT);
        });
    }

    private RecommendationTriggerRequest triggerRequest() {
        return new RecommendationTriggerRequest(
            "TRG.ORDER", "order-sign", "event-1", "snapshot-1",
            "patient-1", "enc-1", "pathway-1", "WARD_ORDER",
            "sha256:trigger", Instant.now(), List.of());
    }

    private RecommendationTriggerRequest triggerRequestWithoutPathway() {
        return triggerRequestWithoutPathway("order-sign");
    }

    private RecommendationTriggerRequest triggerRequestWithoutPathway(String triggerType) {
        return new RecommendationTriggerRequest(
            "TRG.ORDER", triggerType, "event-1", "snapshot-1",
            "patient-1", "enc-1", null, "WARD_ORDER",
            "sha256:trigger", Instant.now(), List.of());
    }

    private ContextSnapshotResponse snapshot() {
        CanonicalPatient patient = new CanonicalPatient(
            "mpi-1", "测试患者", null, "FEMALE", List.of(),
            "HIS", "patient-1", "v1", Instant.now(), Instant.now(), QualityStatus.VALID);
        CanonicalEncounter encounter = new CanonicalEncounter(
            "enc-1", "INPATIENT", Instant.now(), null, "dept-1", "doctor-1", "bed-1",
            "HIS", "enc-1", "v1", Instant.now(), Instant.now(), QualityStatus.VALID);
        return new ContextSnapshotResponse(
            "snapshot-1", ContextSnapshotStatus.ACTIVE,
            new ContextSnapshotResources(patient, List.of(), List.of(encounter), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                ContextSnapshotResources.emptyExtensions()),
            "runtime-release-test",
            QualityStatus.VALID, List.of(), java.util.Map.of(), Instant.now(), "trace-cdss");
    }

    private void stubRuleResolution(
            RuleDefinition rule,
            RuleVersion version,
            AssetVersion assetVersionOverride) {
        AssetVersion assetVersion = assetVersionOverride == null
            ? ruleAsset(rule.tenantId(), version.versionNo())
            : assetVersionOverride;
        when(runtimeRuleSelector.select("tenant-A", "runtime-release-test", "order-sign"))
            .thenReturn(new RuntimeRuleSelection(
                "runtime-release-test",
                "baseline-test",
                List.of(new RuntimeRuleReference(
                    rule.tenantId(),
                    rule.ruleId(),
                    version.versionId(),
                    assetVersion.versionId(),
                    assetVersion.versionNo(),
                    assetVersion.contentHash(),
                    "HOSPITAL"))));
        when(runtimeRuleSelector.select("tenant-A", "runtime-release-test", "medication-prescribe"))
            .thenReturn(new RuntimeRuleSelection(
                "runtime-release-test",
                "baseline-test",
                List.of(new RuntimeRuleReference(
                    rule.tenantId(),
                    rule.ruleId(),
                    version.versionId(),
                    assetVersion.versionId(),
                    assetVersion.versionNo(),
                    assetVersion.contentHash(),
                    "HOSPITAL"))));
        when(ruleDefinitions.findByRuleIdAndTenantId(rule.ruleId(), rule.tenantId()))
            .thenReturn(Optional.of(rule));
        when(ruleVersions.findByVersionIdAndTenantId(version.versionId(), version.tenantId()))
            .thenReturn(Optional.of(version));
    }

    private void stubKnowledgeResolution(
            KnowledgeIdentity identity,
            KnowledgeAssetVersion version) {
        AssetVersion assetVersion = knowledgeAsset(identity, version);
        when(effectiveKnowledgeVersions.resolve(
            "tenant-A",
            identity.identityCode(),
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE))
            .thenReturn(Optional.of(
                new KnowledgeEffectiveVersionResolver.ResolvedKnowledgeVersion(
                    identity, version, assetVersion, null)));
    }

    private AssetVersion ruleAsset(String tenantId, int versionNo) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new AssetVersion(
            1L, "av-RISK_GENDER-" + versionNo, tenantId, VersionedAssetType.RULE,
            "RISK_GENDER", String.valueOf(versionNo), "tenant:" + tenantId, "rule-1",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED, "version:RISK_GENDER-" + versionNo, "测试规则",
            null, null, now, "tester", now, "tester", "trace-cdss");
    }

    private AssetVersion knowledgeAsset(
            KnowledgeIdentity identity,
            KnowledgeAssetVersion version) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new AssetVersion(
            3L, "av-knowledge-" + version.id(), identity.tenantId(),
            VersionedAssetType.KNOWLEDGE, identity.identityCode(), version.versionNo(),
            version.organizationScope(), version.applicableScope(), version.contentHash(),
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            identity.identityCode() + "|" + version.organizationScope() + "|" + version.applicableScope(),
            "knowledge-version:" + version.id(),
            null, null, now, "tester", now, "tester", "trace-cdss");
    }

    private AssetVersion tenantOverrideRuleAsset() {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new AssetVersion(
            2L,
            "av-RISK_GENDER-2",
            "tenant-A",
            VersionedAssetType.RULE,
            "RISK_GENDER",
            "2",
            "dept-A",
            "rule-1",
            "sha256:tenant-rule-v2",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            "version:RISK_GENDER-2",
            "测试规则覆盖版本",
            null,
            null,
            now,
            "tester",
            now,
            "tester",
            "trace-cdss"
        );
    }

    private RecommendationCardRequest redlineCard() {
        return new RecommendationCardRequest(
            "REDLINE.RDL-DDI-001.v2026.2",
            RecommendationCardType.MEDICATION,
            "华法林与 NSAID 联用红线",
            "患者当前上下文命中临床安全红线",
            "立即复核并按院内安全流程处理，不得自动执行医嘱",
            RecommendationRiskLevel.CRITICAL,
            RecommendationInterruptLevel.STRONG_INTERRUPTIVE,
            true,
            false,
            "临床安全红线 RDL-DDI-001 v2026.2 命中",
            "{\"matchType\":\"CLINICAL_REDLINE\"}",
            "REDLINE:RDL-DDI-001",
            null,
            CdssAutomationLevel.INTERRUPTIVE,
            List.of(
                new RecommendationSourceRequest(
                    RecommendationSourceType.REDLINE,
                    "redline-ddi-warfarin-nsaid",
                    "2026.2",
                    "华法林与 NSAID 联用红线",
                    "clinical_redline:redline-ddi-warfarin-nsaid",
                    null,
                    "临床安全红线命中"),
                new RecommendationSourceRequest(
                    RecommendationSourceType.KNOWLEDGE,
                    "knowledge-version:42",
                    "42",
                    "红线来源知识版本",
                    "knowledge_version:42",
                    null,
                    "召回链来源")));
    }

    private RuleDefinition ruleDefinition() {
        Instant now = Instant.now();
        return new RuleDefinition(
            1L, "rule-risk", "tenant-A", "RISK_GENDER", "性别风险评估",
            RuleType.DIAGNOSIS, RuleAuthoringMode.DSL, RuleRiskLevel.MEDIUM,
            100, null, 0, RuleDefinitionStatus.PUBLISHED, "rv-risk-v1", "dept-1",
            now, "tester", now, "tester", "trace-cdss");
    }

    private RuleVersion ruleVersion() {
        return ruleVersionForSetting("INPATIENT");
    }

    private RuleVersion ruleVersionWithoutLegacyTrigger() {
        Instant now = Instant.now();
        String dsl = """
            {
              "applicability": {
                "population": {},
                "orgScope": {},
                "settings": ["INPATIENT"],
                "effective": {"rolloutPercent": 100}
              },
              "when": {
                "fact": "patient.gender",
                "operator": "equals",
                "value": "FEMALE"
              },
              "then": [
                {"actionCode": "REMIND", "atSeverity": "MEDIUM", "indicator": "warning", "summary": "请结合上下文复核性别相关风险", "detail": "请结合上下文复核性别相关风险", "source": {"label": "规则测试来源"}, "suggestions": [], "overrideReasons": []}
              ],
              "explain": {
                "summary": "规则命中性别相关风险"
              }
            }
            """;
        return new RuleVersion(
            13L, "rv-risk-v1", "tenant-A", "rule-risk", 1,
            "knowledge:RISK_GENDER", "发布性别风险评估", dsl, "{\"summary\":\"规则解释\"}",
            RuleVersionStatus.PUBLISHED, now, "reviewer", null,
            now, "tester", now, "tester", "trace-cdss");
    }

    private RuleVersion ruleVersionNo(String versionId, int versionNo) {
        Instant now = Instant.now();
        return new RuleVersion(
            11L, versionId, "tenant-A", "rule-risk", versionNo,
            "knowledge:RISK_GENDER", "发布性别风险评估", ruleVersion().dslJson(), "{\"summary\":\"规则解释\"}",
            RuleVersionStatus.PUBLISHED, now, "reviewer", null,
            now, "tester", now, "tester", "trace-cdss");
    }

    private RuleVersion ruleVersionForSetting(String setting) {
        Instant now = Instant.now();
        String dsl = """
            {
              "trigger": "order-sign",
              "applicability": {
                "population": {},
                "orgScope": {},
                "settings": ["%s"],
                "effective": {"rolloutPercent": 100}
              },
              "when": {
                "fact": "patient.gender",
                "operator": "equals",
                "value": "FEMALE"
              },
              "then": [
                {"actionCode": "REMIND", "atSeverity": "MEDIUM", "indicator": "warning", "summary": "请结合上下文复核性别相关风险", "detail": "请结合上下文复核性别相关风险", "source": {"label": "规则测试来源"}, "suggestions": [], "overrideReasons": []}
              ],
              "explain": {
                "summary": "规则命中性别相关风险"
              }
            }
            """.formatted(setting);
        return new RuleVersion(
            10L, "rv-risk-v1", "tenant-A", "rule-risk", 1,
            "knowledge:RISK_GENDER", "发布性别风险评估", dsl, "{\"summary\":\"规则解释\"}",
            RuleVersionStatus.PUBLISHED, now, "reviewer", null,
            now, "tester", now, "tester", "trace-cdss");
    }

    private RuleVersion actionCardRuleVersion() {
        Instant now = Instant.now();
        String dsl = """
            {
              "trigger": "order-sign",
              "applicability": {
                "population": {},
                "orgScope": {},
                "settings": ["INPATIENT"],
                "effective": {"rolloutPercent": 100}
              },
              "when": {
                "fact": "patient.gender",
                "operator": "equals",
                "value": "FEMALE"
              },
              "then": [{"actionCardRef": "CARD.K.RECHECK"}],
              "explain": {
                "summary": "规则命中临床提示卡引用"
              }
            }
            """;
        return new RuleVersion(
            12L, "rv-risk-action-card", "tenant-A", "rule-risk", 1,
            "manual:action-card", "发布临床提示卡引用规则", dsl, "{\"summary\":\"规则解释\"}",
            RuleVersionStatus.PUBLISHED, now, "reviewer", null,
            now, "tester", now, "tester", "trace-cdss");
    }

    private RuleDefinition platformRuleDefinition() {
        Instant now = Instant.now();
        return new RuleDefinition(
            2L, "rule-platform-risk", "t-1", "RISK_GENDER", "性别风险评估",
            RuleType.DIAGNOSIS, RuleAuthoringMode.DSL, RuleRiskLevel.MEDIUM,
            100, null, 0, RuleDefinitionStatus.PUBLISHED, "rv-platform-risk-v1", "dept-1",
            now, "tester", now, "tester", "trace-cdss");
    }

    private RuleVersion platformRuleVersion() {
        Instant now = Instant.now();
        return new RuleVersion(
            20L, "rv-platform-risk-v1", "t-1", "rule-platform-risk", 1,
            "knowledge:RISK_GENDER", "发布平台性别风险评估", ruleVersion().dslJson(), "{\"summary\":\"规则解释\"}",
            RuleVersionStatus.PUBLISHED, now, "reviewer", null,
            now, "tester", now, "tester", "trace-cdss");
    }

    private KnowledgeIdentity knowledgeIdentity() {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            1L, "tenant-A", "RISK_GENDER", KnowledgeDomain.GUIDELINE,
            "性别风险评估知识", "dept-1", "规则命中引用的已审核知识",
            KnowledgeIdentityStatus.ACTIVE, 100L,
            now, "tester", now, "tester");
    }

    private KnowledgeAssetVersion knowledgeVersion() {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            100L, "tenant-A", 1L, "2026.1", "2026 年第 1 版",
            10L, 20L, "sha256:knowledge-risk-gender", "{\"anchors\":[\"§1\"]}",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE, GradeEvidenceQuality.HIGH,
            GradeRecommendationStrength.STRONG,
            "{\"decision\":\"accepted\"}", "tenant:tenant-A",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(
                1L, "tenant:tenant-A", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            now.minusSeconds(3600), null, "reviewer", now.minusSeconds(3000),
            now.minusSeconds(3000), null, null, null,
            now, "tester", now, "tester", 12, null);
    }

    private KnowledgeIdentity platformKnowledgeIdentity() {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            2L, "t-1", "RISK_GENDER", KnowledgeDomain.GUIDELINE,
            "平台性别风险评估知识", "dept-1", "平台主源已审核知识",
            KnowledgeIdentityStatus.ACTIVE, 200L,
            now, "tester", now, "tester");
    }

    private KnowledgeAssetVersion platformKnowledgeVersion() {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            200L, "t-1", 2L, "2026.1", "2026 年第 1 版",
            10L, 20L, "sha256:platform-knowledge-risk-gender", "{\"anchors\":[\"§1\"]}",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE, GradeEvidenceQuality.HIGH,
            GradeRecommendationStrength.STRONG,
            "{\"decision\":\"accepted\"}", "tenant:t-1",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(
                2L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            now.minusSeconds(3600), null, "reviewer", now.minusSeconds(3000),
            now.minusSeconds(3000), null, null, null,
            now, "tester", now, "tester", 12, null);
    }

    private PatientPathway patientPathway() {
        Instant now = Instant.now();
        return new PatientPathway(
            1L, "pathway-1", "tenant-A", "patient-1", "enc-1",
            "template-1", "release-H1", "av-pathway-v1",
            "START", PatientPathwayStatus.ENTERED,
            now.minusSeconds(60), null, null, null, "event-1",
            now, "tester", now, "tester", "trace-cdss");
    }

    private PathwayTemplate pathwayTemplate() {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "template-1", "tenant-A", "PATH.RISK", "风险评估路径",
            "RISK", 3, PathwayTemplateLevel.DEPARTMENT, PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.AUTO_SUGGEST, "START", "source:pathway", "路径说明", "{}", "{}",
            now, "tester", now, "tester", "trace-cdss");
    }

    private RecommendationDeterministicMatcher matcherWith(RuleDslEvaluator evaluator) {
        return new RecommendationDeterministicMatcher(
            snapshots,
            ruleDefinitions,
            ruleVersions,
            runtimeRuleSelector,
            evaluator,
            new RuleApplicabilityService(
                ruleApplicabilities, new RuleApplicabilityEvaluator(json), json),
            patientPathways,
            pathwayTemplates,
            effectiveKnowledgeVersions,
            redlineMatcher,
            json
        );
    }

    @SuppressWarnings("unchecked")
    private RuleDslEvaluator materializingRuleEvaluator() {
        RuleDslAssetMaterializer materializer = new RuleDslAssetMaterializer(
            json,
            (tenantId, runtimeReleaseId, assetType, assetIdentity) -> {
                assertThat(tenantId).isEqualTo("tenant-A");
                assertThat(runtimeReleaseId).isEqualTo("runtime-release-test");
                assertThat(assetType).isEqualTo(VersionedAssetType.ACTION_CARD);
                assertThat(assetIdentity).isEqualTo("CARD.K.RECHECK");
                return Optional.of(new ResolvedDeclarativeAsset(
                    VersionedAssetType.ACTION_CARD,
                    assetIdentity,
                    "4",
                    runtimeReleaseId,
                    """
                    {"actionCode":"REMIND","atSeverity":"HIGH","indicator":"warning",
                     "summary":"推荐高钾复核提醒","detail":"请结合标本状态复核。",
                     "source":{"label":"检验危急值制度","evidenceLevel":"院内制度"},
                     "suggestions":[],"overrideReasons":["已复核标本"],"requiresPhysicianConfirmation":true}
                    """,
                    "hash-card"
                ));
            });
        ObjectProvider<RuleDslAssetMaterializer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(materializer);
        return new RuleDslEvaluator(json, new ConditionEvaluator(json), provider);
    }
}
