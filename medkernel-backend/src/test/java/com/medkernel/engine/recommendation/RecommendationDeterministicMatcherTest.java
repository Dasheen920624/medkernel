package com.medkernel.engine.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
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
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleApplicabilityEvaluator;
import com.medkernel.engine.rule.RuleApplicabilityRepository;
import com.medkernel.engine.rule.RuleApplicabilityService;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.engine.safety.ClinicalRedlineMatcher;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationDeterministicMatcherTest {

    private final ContextSnapshotService snapshots = mock(ContextSnapshotService.class);
    private final RuleDefinitionRepository ruleDefinitions = mock(RuleDefinitionRepository.class);
    private final RuleVersionRepository ruleVersions = mock(RuleVersionRepository.class);
    private final RuleApplicabilityRepository ruleApplicabilities =
        mock(RuleApplicabilityRepository.class);
    private final AssetVersionRepository assetVersions = mock(AssetVersionRepository.class);
    private final PatientPathwayRepository patientPathways = mock(PatientPathwayRepository.class);
    private final PathwayTemplateRepository pathwayTemplates = mock(PathwayTemplateRepository.class);
    private final KnowledgeIdentityRepository knowledgeIdentities = mock(KnowledgeIdentityRepository.class);
    private final KnowledgeAssetVersionRepository knowledgeVersions = mock(KnowledgeAssetVersionRepository.class);
    private final ClinicalRedlineMatcher redlineMatcher = mock(ClinicalRedlineMatcher.class);
    private final InheritanceResolver inheritanceResolver = mock(InheritanceResolver.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final RecommendationDeterministicMatcher matcher = new RecommendationDeterministicMatcher(
        snapshots,
        ruleDefinitions,
        ruleVersions,
        assetVersions,
        new RuleDslEvaluator(json),
        new RuleApplicabilityService(
            ruleApplicabilities, new RuleApplicabilityEvaluator(json), json),
        patientPathways,
        pathwayTemplates,
        knowledgeIdentities,
        knowledgeVersions,
        redlineMatcher,
        inheritanceResolver,
        json
    );

    @BeforeEach
    void setUp() {
        when(redlineMatcher.match(any(), any(), any())).thenReturn(List.of());
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
        when(ruleVersions.findByVersionIdAndTenantId("rv-risk-v1", "tenant-A"))
            .thenReturn(Optional.of(ruleVersion()));
        stubRuleAsset("tenant-A", "RISK_GENDER", "1", AssetVersionStatus.PUBLISHED);
        when(knowledgeIdentities.findByTenantIdAndIdentityCode("tenant-A", "RISK_GENDER"))
            .thenReturn(Optional.of(knowledgeIdentity()));
        when(knowledgeVersions.findByTenantIdAndId("tenant-A", 100L))
            .thenReturn(Optional.of(knowledgeVersion()));
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
        assertThat(knowledge.citationLocator()).isEqualTo("knowledge_version:100");
        assertThat(knowledge.sourceHash()).isEqualTo("sha256:knowledge-risk-gender");
    }

    @Test
    void reviewedRuleDoesNotProduceRecommendationBeforeUnifiedActivation() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(ruleDefinition()));
        when(ruleVersions.findByVersionIdAndTenantId("rv-risk-v1", "tenant-A"))
            .thenReturn(Optional.of(ruleVersion()));
        stubRuleAsset("tenant-A", "RISK_GENDER", "1", AssetVersionStatus.APPROVED);

        List<RecommendationCardRequest> matches = matcher.match(triggerRequestWithoutPathway());

        assertThat(matches).isEmpty();
    }

    @Test
    void doesNotProduceRecommendationWhenRuleIsOutsideClinicalSetting() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(ruleDefinition()));
        when(ruleVersions.findByVersionIdAndTenantId("rv-risk-v1", "tenant-A"))
            .thenReturn(Optional.of(ruleVersionForSetting("OUTPATIENT")));
        stubRuleAsset("tenant-A", "RISK_GENDER", "1", AssetVersionStatus.PUBLISHED);

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
        when(ruleVersions.findByVersionIdAndTenantId("rv-platform-risk-v1", "t-1"))
            .thenReturn(Optional.of(platformRuleVersion()));
        stubRuleAsset("t-1", "RISK_GENDER", "1", AssetVersionStatus.PUBLISHED);
        when(knowledgeIdentities.findByTenantIdAndIdentityCode("tenant-A", "RISK_GENDER"))
            .thenReturn(Optional.empty());
        when(knowledgeIdentities.findByTenantIdAndIdentityCode("t-1", "RISK_GENDER"))
            .thenReturn(Optional.of(platformKnowledgeIdentity()));
        when(knowledgeVersions.findByTenantIdAndId("t-1", 200L))
            .thenReturn(Optional.of(platformKnowledgeVersion()));

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
        when(ruleVersions.findByVersionIdAndTenantId("rv-risk-v1", "tenant-A"))
            .thenReturn(Optional.of(ruleVersion()));
        when(ruleVersions.findByVersionIdAndTenantId("rv-platform-risk-v1", "t-1"))
            .thenReturn(Optional.of(platformRuleVersion()));
        stubRuleAsset("tenant-A", "RISK_GENDER", "1", AssetVersionStatus.APPROVED);
        stubRuleAsset("t-1", "RISK_GENDER", "1", AssetVersionStatus.PUBLISHED);
        when(knowledgeIdentities.findByTenantIdAndIdentityCode("tenant-A", "RISK_GENDER"))
            .thenReturn(Optional.empty());
        when(knowledgeIdentities.findByTenantIdAndIdentityCode("t-1", "RISK_GENDER"))
            .thenReturn(Optional.of(platformKnowledgeIdentity()));
        when(knowledgeVersions.findByTenantIdAndId("t-1", 200L))
            .thenReturn(Optional.of(platformKnowledgeVersion()));

        List<RecommendationCardRequest> matches = matcher.match(triggerRequestWithoutPathway());

        assertThat(matches).singleElement().satisfies(card ->
            assertThat(card.sources().get(0).sourceRefId()).isEqualTo("rule-platform-risk"));
    }

    @Test
    void resolvesRecommendationRuleThroughOrgInheritanceAndRecordsResolutionSource() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-cdss",
            new OrgScope("tenant-A", "group-A", "hospital-A", "campus-A", "site-A", "dept-A", "specialty-A"),
            "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(ruleDefinitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(ruleDefinition()));
        when(ruleDefinitions.findPublishedByTenantId("t-1")).thenReturn(List.of(platformRuleDefinition()));
        when(inheritanceResolver.resolve(any(InheritanceResolveQuery.class))).thenReturn(new ResolvedAssetVersion(
            tenantOverrideRuleAsset(),
            "dept-A",
            false,
            true,
            false,
            null,
            SourceTier.ORG
        ));
        when(ruleDefinitions.findByTenantIdAndRuleCode("tenant-A", "RISK_GENDER"))
            .thenReturn(Optional.of(ruleDefinition()));
        when(ruleVersions.findByRuleIdAndTenantIdAndVersionNo("rule-risk", "tenant-A", 2))
            .thenReturn(Optional.of(ruleVersionNo("rv-risk-v2", 2)));
        when(knowledgeIdentities.findByTenantIdAndIdentityCode("tenant-A", "RISK_GENDER"))
            .thenReturn(Optional.of(knowledgeIdentity()));
        when(knowledgeVersions.findByTenantIdAndId("tenant-A", 100L))
            .thenReturn(Optional.of(knowledgeVersion()));

        List<RecommendationCardRequest> matches = matcher.match(triggerRequestWithoutPathway());

        assertThat(matches).hasSize(1);
        RecommendationCardRequest card = matches.get(0);
        assertThat(card.cardCode()).isEqualTo("RULE.RISK_GENDER.v2");
        assertThat(card.sourceSummary())
            .contains("RISK_GENDER")
            .contains("来源=ORG")
            .contains("sha256:tenant-rule-v2");
        RecommendationSourceRequest ruleSource = card.sources().get(0);
        assertThat(ruleSource.sourceHash()).isEqualTo("sha256:tenant-rule-v2");
        assertThat(ruleSource.summary()).contains("ORG").contains("dept-A");
        assertThat(card.explanationJson())
            .contains("\"sourceTier\":\"ORG\"")
            .contains("\"sourceOrgPath\":\"dept-A\"")
            .contains("\"contentHash\":\"sha256:tenant-rule-v2\"");
        verify(inheritanceResolver).resolve(argThat(query ->
            "tenant-A".equals(query.tenantId())
                && query.assetType() == VersionedAssetType.RULE
                && "RISK_GENDER".equals(query.assetIdentity())
                && "dept-A".equals(query.targetOrgUnitId())));
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

    private RecommendationTriggerRequest triggerRequest() {
        return new RecommendationTriggerRequest(
            "TRG.ORDER", "order-sign", "event-1", "snapshot-1",
            "patient-1", "enc-1", "pathway-1", "WARD_ORDER",
            "1.0.0", "sha256:trigger", Instant.now(), List.of());
    }

    private RecommendationTriggerRequest triggerRequestWithoutPathway() {
        return new RecommendationTriggerRequest(
            "TRG.ORDER", "order-sign", "event-1", "snapshot-1",
            "patient-1", "enc-1", null, "WARD_ORDER",
            "1.0.0", "sha256:trigger", Instant.now(), List.of());
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
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
            "1.0.0",
            QualityStatus.VALID, List.of(), java.util.Map.of(), Instant.now(), "trace-cdss");
    }

    private void stubRuleAsset(String tenantId, String identity, String versionNo,
                               AssetVersionStatus status) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            tenantId, VersionedAssetType.RULE, identity, versionNo))
            .thenReturn(Optional.of(new AssetVersion(
                1L,
                "av-" + identity + "-" + versionNo,
                tenantId,
                VersionedAssetType.RULE,
                identity,
                versionNo,
                "tenant:" + tenantId,
                "1.0.0",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                AssetVersionSafetyPolicy.NORMAL,
                AssetVersionOverridePolicy.FREE,
                status,
                "version:" + identity + "-" + versionNo,
                "测试规则",
                null,
                null,
                now,
                "tester",
                now,
                "tester",
                "trace-cdss"
            )));
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
            100, null, 0, RuleDefinitionStatus.PUBLISHED, "rv-risk-v1", "rule-1", "dept-1",
            now, "tester", now, "tester", "trace-cdss");
    }

    private RuleVersion ruleVersion() {
        return ruleVersionForSetting("INPATIENT");
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

    private RuleDefinition platformRuleDefinition() {
        Instant now = Instant.now();
        return new RuleDefinition(
            2L, "rule-platform-risk", "t-1", "RISK_GENDER", "性别风险评估",
            RuleType.DIAGNOSIS, RuleAuthoringMode.DSL, RuleRiskLevel.MEDIUM,
            100, null, 0, RuleDefinitionStatus.PUBLISHED, "rv-platform-risk-v1", "rule-platform-package", "dept-1",
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
            now, "tester", now, "tester");
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
            now, "tester", now, "tester");
    }

    private PatientPathway patientPathway() {
        Instant now = Instant.now();
        return new PatientPathway(
            1L, "pathway-1", "tenant-A", "patient-1", "enc-1",
            "template-1", "START", PatientPathwayStatus.ENTERED,
            now.minusSeconds(60), null, null, null, "event-1",
            now, "tester", now, "tester", "trace-cdss");
    }

    private PathwayTemplate pathwayTemplate() {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "template-1", "tenant-A", "pkg-1", "PATH.RISK", "风险评估路径",
            "RISK", 3, PathwayTemplateLevel.DEPARTMENT, PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.AUTO_SUGGEST, "START", "source:pathway", "路径说明", "{}", "{}",
            now, "tester", now, "tester", "trace-cdss");
    }
}
