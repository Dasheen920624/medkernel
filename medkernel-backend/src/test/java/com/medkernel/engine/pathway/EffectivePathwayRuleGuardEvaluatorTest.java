package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EffectivePathwayRuleGuardEvaluatorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ClinicalRuntimeReleaseContentResolver releases =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final RuleDefinitionRepository definitions = mock(RuleDefinitionRepository.class);
    private final RuleVersionRepository versions = mock(RuleVersionRepository.class);
    private final EffectivePathwayRuleGuardEvaluator evaluator =
        new EffectivePathwayRuleGuardEvaluator(
            json,
            releases,
            definitions,
            versions,
            new RuleDslEvaluator(json, new ConditionEvaluator(json)));

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void executesExactRuleVersionPinnedByHospitalRuntimeRelease() throws Exception {
        restoreTenant();
        RuleDefinition rule = publishedRule("tenant-A", "rv-current-v3");
        RuleVersion pinnedVersion = publishedVersion("tenant-A", 2);
        when(releases.resolve("tenant-A", "release-hospital-7"))
            .thenReturn(runtimeContent("tenant-A", "release-hospital-7", "2"));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.CKD.HIGH_RISK"))
            .thenReturn(Optional.of(rule));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-high-risk", "tenant-A", 2))
            .thenReturn(Optional.of(pinnedVersion));

        PathwayRuleGuardEvaluation result = evaluator.evaluate(
            json.readTree("""
                {
                  "ruleRef": "RULE.CKD.HIGH_RISK",
                  "ruleAssetId": "rule-high-risk"
                }
                """),
            json.readTree("""
                {"patient": {"riskLevel": "HIGH"}}
                """),
            "release-hospital-7");

        assertThat(result.matched()).isTrue();
        assertThat(result.ruleCode()).isEqualTo("RULE.CKD.HIGH_RISK");
        assertThat(result.ruleId()).isEqualTo("rule-high-risk");
        assertThat(result.versionId()).isEqualTo("rv-high-risk-v2");
        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.runtimeReleaseId()).isEqualTo("release-hospital-7");
        assertThat(result.sourceTenantId()).isEqualTo("tenant-A");
        assertThat(result.sourceLayer()).isEqualTo(ReleaseSourceLayer.HOSPITAL);
        verify(versions, never()).findByVersionIdAndTenantId("rv-current-v3", "tenant-A");
    }

    @Test
    void rejectsManualRuntimeSelectorsInRuleReference() throws Exception {
        restoreTenant();

        assertThatThrownBy(() -> evaluator.evaluate(
            json.readTree("""
                {
                  "ruleRef": "RULE.CKD.HIGH_RISK",
                  "ruleAssetId": "rule-high-risk",
                  "packageVersion": "2026.06"
                }
                """),
            json.createObjectNode(),
            "release-hospital-7"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不能混入内嵌条件字段: packageVersion");
    }

    @Test
    void rejectsRuleThatIsNotPartOfHospitalRuntimeRelease() throws Exception {
        restoreTenant();
        when(releases.resolve("tenant-A", "release-hospital-7"))
            .thenReturn(new ClinicalRuntimeReleaseContent(
                runtimeRelease("tenant-A", "release-hospital-7"), List.of()));

        assertThatThrownBy(() -> evaluator.evaluate(
            json.readTree("""
                {
                  "ruleRef": "RULE.CKD.HIGH_RISK",
                  "ruleAssetId": "rule-high-risk"
                }
                """),
            json.createObjectNode(),
            "release-hospital-7"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("规则未包含在机构生效版本中");
    }

    @Test
    void rejectsRuleReferenceWithoutStableAssetIdentity() throws Exception {
        restoreTenant();

        assertThatThrownBy(() -> evaluator.evaluate(
            json.readTree("""
                {"ruleRef": "RULE.CKD.HIGH_RISK"}
                """),
            json.createObjectNode(),
            "release-hospital-7"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("缺少字段: ruleAssetId");
    }

    private void restoreTenant() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-path-rule", OrgScope.tenant("tenant-A"), "clinical-user"));
    }

    private ClinicalRuntimeReleaseContent runtimeContent(
            String tenantId,
            String releaseId,
            String ruleVersion) {
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        ClinicalRuntimeReleaseItem item = new ClinicalRuntimeReleaseItem(
            1L,
            releaseId,
            tenantId,
            ReleaseSourceLayer.HOSPITAL,
            VersionedAssetType.RULE,
            "RULE.CKD.HIGH_RISK",
            ReleaseEntryState.ACTIVE,
            "asset-version-rule-high-risk",
            "V" + ruleVersion,
            "b".repeat(64),
            now,
            "tester",
            "trace-path-rule");
        return new ClinicalRuntimeReleaseContent(
            runtimeRelease(tenantId, releaseId),
            List.of(item));
    }

    private ClinicalRuntimeRelease runtimeRelease(String tenantId, String releaseId) {
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        return new ClinicalRuntimeRelease(
            1L, releaseId, tenantId, "hospital-A", 7L,
            "baseline-A12", "a".repeat(64), null,
            now, "tester", now, "tester", "trace-path-rule");
    }

    private RuleDefinition publishedRule(String tenantId, String activeVersionId) {
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        return new RuleDefinition(
            1L, "rule-high-risk", tenantId, "RULE.CKD.HIGH_RISK", "CKD 高风险判断",
            RuleType.PATHWAY, RuleAuthoringMode.DSL, RuleRiskLevel.HIGH,
            100, null, 0, RuleDefinitionStatus.PUBLISHED, activeVersionId,
            null,
            now, "tester", now, "tester", "trace-rule");
    }

    private RuleVersion publishedVersion(String tenantId, int versionNo) {
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        return new RuleVersion(
            1L, "rv-high-risk-v" + versionNo, tenantId, "rule-high-risk", versionNo,
            "院内 CKD 路径规范", "路径分支判断",
            """
                {
                  "when": {
                    "all": [
                      {"fact": "patient.riskLevel", "operator": "equals", "value": "HIGH"}
                    ]
                  },
                  "then": [
                    {
                      "atSeverity": "HIGH",
                      "actionCode": "INFO",
                      "indicator": "warning",
                      "summary": "进入高风险分支",
                      "detail": "规则仅用于路径分支判断，不自动生成医嘱。",
                      "source": {"label": "院内 CKD 路径规范"},
                      "suggestions": [],
                      "overrideReasons": [],
                      "requiresPhysicianConfirmation": false
                    }
                  ],
                  "explain": {"summary": "CKD 高风险路径分支"}
                }
                """,
            "{}", RuleVersionStatus.PUBLISHED, now, "tester", null,
            now, "tester", now, "tester", "trace-version");
    }
}
