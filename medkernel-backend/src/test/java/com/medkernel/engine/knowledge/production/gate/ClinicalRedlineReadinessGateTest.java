package com.medkernel.engine.knowledge.production.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.production.generation.StrictB0TemplatePolicy;
import com.medkernel.engine.safety.ClinicalRedlineCatalogResponse;
import com.medkernel.engine.safety.ClinicalRedlineCategory;
import com.medkernel.engine.safety.ClinicalRedlineContentStatus;
import com.medkernel.engine.safety.ClinicalRedlineResponse;
import com.medkernel.engine.safety.ClinicalRedlineService;
import com.medkernel.engine.safety.ClinicalRedlineStatus;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.hash.Sha256ContentHash;

class ClinicalRedlineReadinessGateTest {

    private static final String PAYLOAD = "{\"template\":\"RULE\",\"sections\":{}}";

    private ClinicalRedlineService redlineService;
    private ClinicalRedlineReadinessGate gate;

    @BeforeEach
    void setUp() {
        redlineService = mock(ClinicalRedlineService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        gate = new ClinicalRedlineReadinessGate(
            redlineService, objectMapper, new StrictB0TemplatePolicy(objectMapper));
    }

    private KnowledgeAssetEnvelope envelope() {
        return envelope(PAYLOAD);
    }

    private KnowledgeAssetEnvelope envelope(String payload) {
        return new KnowledgeAssetEnvelope(VersionedAssetType.RULE, "identity:1", "主题", "v1",
            List.of(), SourceAuthorityLevel.B_GUIDELINE, null, null, KnowledgeRiskLevel.MEDIUM, "t-1",
            Sha256ContentHash.sha256(payload, "x"), payload, AssetVersionStatus.DRAFT);
    }

    private ClinicalRedlineResponse redline(ClinicalRedlineCategory category) {
        return new ClinicalRedlineResponse("rl-" + category.name(), category, category.name(), "v1",
            ClinicalRedlineStatus.ACTIVE, "标题", "危害", "{}", null, "matrix", "v1", null,
            24, "release", "source", "ref", 1L, false);
    }

    @Test
    void failsWhenRedlineCatalogNotConfigured() {
        when(redlineService.activeCatalog(null)).thenReturn(new ClinicalRedlineCatalogResponse(
            ClinicalRedlineContentStatus.NOT_CONFIGURED, ClinicalRedlineCategory.requiredSafetyCategories(),
            List.of(), "trace"));

        GateItemResult result = gate.evaluate(envelope(), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("未配置");
    }

    @Test
    void passesStrictB0TemplateWhenRedlineCatalogNotConfigured() {
        when(redlineService.activeCatalog(null)).thenReturn(new ClinicalRedlineCatalogResponse(
            ClinicalRedlineContentStatus.NOT_CONFIGURED, ClinicalRedlineCategory.requiredSafetyCategories(),
            List.of(), "trace"));
        String payload = """
            {
              "generationMode": "B0_TEMPLATE",
              "medicalContentStatus": "PENDING_AUTHORING",
              "generatedByModel": false,
              "template": "FIELD_CATALOG",
              "sections": {
                "scope": "待编著（结构：适用范围）"
              },
              "sourceEvidence": [
                {
                  "anchorPath": "registry/KNOWGEN-29",
                  "excerpt": "受控来源目录元数据",
                  "contentHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
              ]
            }
            """;

        GateItemResult result = gate.evaluate(envelope(payload), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isTrue();
    }

    @Test
    void failsWhenB0MarkerIsIncompleteAndRedlineCatalogNotConfigured() {
        when(redlineService.activeCatalog(null)).thenReturn(new ClinicalRedlineCatalogResponse(
            ClinicalRedlineContentStatus.NOT_CONFIGURED, ClinicalRedlineCategory.requiredSafetyCategories(),
            List.of(), "trace"));
        String payload = """
            {
              "generationMode": "B0_TEMPLATE",
              "medicalContentStatus": "PENDING_AUTHORING",
              "generatedByModel": true,
              "sections": {}
            }
            """;

        GateItemResult result = gate.evaluate(envelope(payload), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("未配置");
    }

    @Test
    void failsWhenB0SectionAppendsAuthoredClinicalLogicAndRedlineCatalogNotConfigured() {
        when(redlineService.activeCatalog(null)).thenReturn(new ClinicalRedlineCatalogResponse(
            ClinicalRedlineContentStatus.NOT_CONFIGURED, ClinicalRedlineCategory.requiredSafetyCategories(),
            List.of(), "trace"));
        String payload = """
            {
              "generationMode": "B0_TEMPLATE",
              "medicalContentStatus": "PENDING_AUTHORING",
              "generatedByModel": false,
              "template": "RULE",
              "sections": {
                "logic": "待编著（结构：判定逻辑）收缩压达到某阈值即触发"
              },
              "sourceEvidence": [
                {
                  "anchorPath": "registry/KNOWGEN-01",
                  "excerpt": "受控来源目录元数据",
                  "contentHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
              ]
            }
            """;

        GateItemResult result = gate.evaluate(envelope(payload), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("未配置");
    }

    @Test
    void failsWhenB0PayloadContainsStructuredRedlineChecksAndCatalogNotConfigured() {
        when(redlineService.activeCatalog(null)).thenReturn(new ClinicalRedlineCatalogResponse(
            ClinicalRedlineContentStatus.NOT_CONFIGURED, ClinicalRedlineCategory.requiredSafetyCategories(),
            List.of(), "trace"));
        String payload = """
            {
              "generationMode": "B0_TEMPLATE",
              "medicalContentStatus": "PENDING_AUTHORING",
              "generatedByModel": false,
              "sections": {},
              "clinicalRedlineChecks": [
                {
                  "category": "DOSE_LIMIT",
                  "redlineKey": "DOSE_LIMIT",
                  "outcome": "PASS",
                  "evidenceReference": "source-version:77#dose-limit"
                }
              ]
            }
            """;

        GateItemResult result = gate.evaluate(envelope(payload), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("未配置");
    }

    @Test
    void failsWhenRequiredRedlineCategoryMissing() {
        when(redlineService.activeCatalog(null)).thenReturn(new ClinicalRedlineCatalogResponse(
            ClinicalRedlineContentStatus.CONFIGURED, ClinicalRedlineCategory.requiredSafetyCategories(),
            List.of(redline(ClinicalRedlineCategory.DRUG_INTERACTION)), "trace"));

        GateItemResult result = gate.evaluate(envelope(), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains(ClinicalRedlineCategory.CRITICAL_VALUE.name());
    }

    @Test
    void passesWhenAllRequiredRedlineCategoriesConfigured() {
        when(redlineService.activeCatalog(null)).thenReturn(new ClinicalRedlineCatalogResponse(
            ClinicalRedlineContentStatus.CONFIGURED, ClinicalRedlineCategory.requiredSafetyCategories(),
            ClinicalRedlineCategory.requiredSafetyCategories().stream().map(this::redline).toList(), "trace"));

        GateItemResult result = gate.evaluate(envelope(), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isTrue();
    }

    @Test
    void failsWhenStructuredPayloadDeclaresActiveRedlineViolation() {
        when(redlineService.activeCatalog(null)).thenReturn(configuredCatalog());
        String payload = """
            {
              "template": "RULE",
              "clinicalSafety": {
                "redlineChecks": [
                  {
                    "category": "DOSE_LIMIT",
                    "redlineKey": "DOSE_LIMIT",
                    "outcome": "VIOLATION",
                    "evidenceReference": "source-version:77#dose-limit"
                  }
                ]
              }
            }
            """;

        GateItemResult result = gate.evaluate(envelope(payload), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason())
            .contains("命中临床安全红线")
            .contains("DOSE_LIMIT")
            .contains("source-version:77#dose-limit");
    }

    @Test
    void failsWhenStructuredPayloadReferencesUnknownActiveRedline() {
        when(redlineService.activeCatalog(null)).thenReturn(configuredCatalog());
        String payload = """
            {
              "template": "RULE",
              "clinicalRedlineChecks": [
                {
                  "category": "DRUG_INTERACTION",
                  "redlineKey": "UNKNOWN-DDI",
                  "outcome": "PASS"
                }
              ]
            }
            """;

        GateItemResult result = gate.evaluate(envelope(payload), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("未匹配已生效红线").contains("UNKNOWN-DDI");
    }

    @Test
    void passesWhenStructuredPayloadDocumentsNoViolationAgainstActiveRedline() {
        when(redlineService.activeCatalog(null)).thenReturn(configuredCatalog());
        String payload = """
            {
              "template": "RULE",
              "modelOutput": {
                "clinicalSafety": {
                  "redlineChecks": [
                    {
                      "category": "ANTIMICROBIAL_RESTRICTION",
                      "redlineKey": "ANTIMICROBIAL_RESTRICTION",
                      "outcome": "PASS",
                      "evidenceReference": "source-version:88#antimicrobial"
                    }
                  ]
                }
              }
            }
            """;

        GateItemResult result = gate.evaluate(envelope(payload), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isTrue();
    }

    private ClinicalRedlineCatalogResponse configuredCatalog() {
        return new ClinicalRedlineCatalogResponse(
            ClinicalRedlineContentStatus.CONFIGURED,
            ClinicalRedlineCategory.requiredSafetyCategories(),
            ClinicalRedlineCategory.requiredSafetyCategories().stream().map(this::redline).toList(),
            "trace");
    }
}
