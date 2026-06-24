package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetDependencyDeclaration;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 自动生成资产入口契约：模型、模板或导入候选只能进入统一资产草稿版本，
 * 不得携带调用方运行定位或手工版本号，也不得绕过规则/路径/知识的类型结构校验。
 */
class AssetAuthoringRegistryTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AssetVersionService versions = mock(AssetVersionService.class);
    private final AssetAuthoringRegistry registry = new AssetAuthoringRegistry(
        json,
        versions,
        List.of(
            new GeneratedKnowledgeCandidateValidator(json),
            new GeneratedRuleCandidateValidator(json),
            new GeneratedPathwayCandidateValidator(json)));

    @Test
    void materializesKnowledgeCandidateAsUnifiedDraftWithSourceAnchorsAndHash() throws Exception {
        when(versions.registerDraft(any())).thenReturn(assetVersion(
            "av-knowledge-1", VersionedAssetType.KNOWLEDGE, "KNOW.CKD.MEDICATION", "V3"));

        GeneratedAssetDraftResponse response = registry.materializeDraft(new GeneratedAssetCandidateRequest(
            "tenant-A",
            VersionedAssetType.KNOWLEDGE,
            "KNOW.CKD.MEDICATION",
            "/platform",
            "ALL",
            "source-version:sv-ckd-2026",
            "author-1",
            "trace-gen-1",
            read("""
                {
                  "schemaVersion": "1.0",
                  "domainSuggestion": "PHARMACY",
                  "title": "慢性肾病用药安全说明",
                  "summary": "根据肾功能调整潜在肾毒性药物使用。",
                  "body": "eGFR 下降时需要复核剂量、间隔和替代方案。",
                  "sources": [{
                    "sourceRef": "doc:ckd-guideline-2026",
                    "anchorPath": "section[3]/paragraph[2]",
                    "authorityLevel": "GUIDELINE"
                  }]
                }
                """),
            List.of()));

        ArgumentCaptor<AssetVersionRegisterCommand> command =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versions).registerDraft(command.capture());
        assertThat(response.versionId()).isEqualTo("av-knowledge-1");
        assertThat(response.status()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(command.getValue().assetType()).isEqualTo(VersionedAssetType.KNOWLEDGE);
        assertThat(command.getValue().assetIdentity()).isEqualTo("KNOW.CKD.MEDICATION");
        assertThat(command.getValue().content()).contains("section[3]/paragraph[2]");
        assertThat(command.getValue().contentHash()).matches("[0-9a-f]{64}");
        assertThat(command.getValue().dependencies()).isEmpty();
    }

    @Test
    void materializesRuleCandidateWithoutRuntimeSelectorOrManualVersionAndPreservesRuntimeDependencies() throws Exception {
        when(versions.registerDraft(any())).thenReturn(assetVersion(
            "av-rule-1", VersionedAssetType.RULE, "RULE.CKD.DOSE", "V1"));

        registry.materializeDraft(new GeneratedAssetCandidateRequest(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.CKD.DOSE",
            "/hospital/H1",
            "FACILITY:H1",
            "source-version:sv-renal-dose",
            "author-1",
            "trace-rule-1",
            read("""
                {
                  "schemaVersion": "1.0",
                  "ruleCode": "RULE.CKD.DOSE",
                  "name": "肾功能下降用药剂量复核",
                  "fieldCatalogIdentity": "FIELD.CATALOG.CLINICAL_CONTEXT",
                  "fieldBindings": ["observations[].valueNumeric", "medications[].code"],
                  "terminologyRefs": ["TERM.LOINC", "TERM.ATC"],
                  "triggerBindings": [{
                    "triggerPoint": "ORDER_SIGN",
                    "purpose": "RULE_EXECUTION"
                  }],
                  "dsl": {
                    "when": {
                      "all": [{
                        "field": "observations[].valueNumeric",
                        "operator": "<",
                        "value": 30
                      }]
                    },
                    "then": [{
                      "actionCardRef": "ACTION.CKD.DOSE_REVIEW"
                    }],
                    "explain": {
                      "message": "肾功能下降时需复核剂量。"
                    }
                  }
                }
                """),
            List.of()));

        ArgumentCaptor<AssetVersionRegisterCommand> command =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versions).registerDraft(command.capture());
        assertThat(command.getValue().assetType()).isEqualTo(VersionedAssetType.RULE);
        assertThat(command.getValue().content()).contains("ORDER_SIGN");
        assertThat(command.getValue().content()).doesNotContain("packageVersion");
        assertThat(command.getValue().dependencies())
            .extracting(AssetDependencyDeclaration::dependsOnAssetType,
                AssetDependencyDeclaration::dependsOnIdentity,
                AssetDependencyDeclaration::kind)
            .contains(
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.FIELD_CATALOG, "FIELD.CATALOG.CLINICAL_CONTEXT", AssetDependencyKind.FIELD),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.TERMINOLOGY, "TERM.LOINC", AssetDependencyKind.TERMINOLOGY),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.TERMINOLOGY, "TERM.ATC", AssetDependencyKind.TERMINOLOGY),
                org.assertj.core.groups.Tuple.tuple(
                    VersionedAssetType.ACTION_CARD, "ACTION.CKD.DOSE_REVIEW", AssetDependencyKind.OTHER));
    }

    @Test
    void rejectsRuleCandidateUsingRetiredThenActionsWrapper() throws Exception {
        assertThatThrownBy(() -> registry.materializeDraft(new GeneratedAssetCandidateRequest(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.CKD.DOSE",
            "/hospital/H1",
            "FACILITY:H1",
            "source-version:sv-renal-dose",
            "author-1",
            "trace-rule-retired-then",
            read("""
                {
                  "schemaVersion": "1.0",
                  "ruleCode": "RULE.CKD.DOSE",
                  "name": "肾功能下降用药剂量复核",
                  "fieldCatalogIdentity": "FIELD.CATALOG.CLINICAL_CONTEXT",
                  "fieldBindings": ["observations[].valueNumeric"],
                  "terminologyRefs": ["TERM.LOINC"],
                  "triggerBindings": [{"triggerPoint": "ORDER_SIGN", "purpose": "RULE_EXECUTION"}],
                  "dsl": {
                    "when": {"all": [{"field": "observations[].valueNumeric", "operator": "<", "value": 30}]},
                    "then": {"actions": [{"type": "ACTION_CARD", "assetIdentity": "ACTION.CKD.DOSE_REVIEW"}]},
                    "explain": {"message": "x"}
                  }
                }
                """),
            List.of())))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("then 必须是数组");

        verify(versions, never()).registerDraft(any());
    }

    @Test
    void rejectsRuleCandidateUsingRetiredFieldCatalogIdentity() throws Exception {
        assertThatThrownBy(() -> registry.materializeDraft(new GeneratedAssetCandidateRequest(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.CKD.DOSE",
            "/hospital/H1",
            "FACILITY:H1",
            "source-version:sv-renal-dose",
            "author-1",
            "trace-rule-legacy-field",
            read("""
                {
                  "schemaVersion": "1.0",
                  "ruleCode": "RULE.CKD.DOSE",
                  "name": "肾功能下降用药剂量复核",
                  "fieldCatalogIdentity": "FIELD.CLINICAL_CORE",
                  "fieldBindings": ["patient.eGfr"],
                  "terminologyRefs": ["TERM.LOINC"],
                  "triggerBindings": [{"triggerPoint": "ORDER_SIGN", "purpose": "RULE_EXECUTION"}],
                  "dsl": {
                    "when": {"all": [{"field": "patient.eGfr", "operator": "<", "value": 30}]},
                    "then": [{"actionCardRef": "ACTION.TEST"}],
                    "explain": {"message": "x"}
                  }
                }
                """),
            List.of())))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("统一临床上下文字段目录资产")
            .hasMessageContaining("FIELD.CATALOG.CLINICAL_CONTEXT");

        verify(versions, never()).registerDraft(any());
    }

    @Test
    void rejectsRuleCandidateUsingFieldsOutsideContextCatalog() throws Exception {
        assertThatThrownBy(() -> registry.materializeDraft(new GeneratedAssetCandidateRequest(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.CKD.DOSE",
            "/hospital/H1",
            "FACILITY:H1",
            "source-version:sv-renal-dose",
            "author-1",
            "trace-rule-unknown-field",
            read("""
                {
                  "schemaVersion": "1.0",
                  "ruleCode": "RULE.CKD.DOSE",
                  "name": "肾功能下降用药剂量复核",
                  "fieldCatalogIdentity": "FIELD.CATALOG.CLINICAL_CONTEXT",
                  "fieldBindings": ["patient.eGfr", "orders[].drugCode"],
                  "terminologyRefs": ["TERM.LOINC"],
                  "triggerBindings": [{"triggerPoint": "ORDER_SIGN", "purpose": "RULE_EXECUTION"}],
                  "dsl": {
                    "when": {"all": [{"field": "patient.eGfr", "operator": "<", "value": 30}]},
                    "then": [{"actionCardRef": "ACTION.TEST"}],
                    "explain": {"message": "x"}
                  }
                }
                """),
            List.of())))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("字段目录不存在")
            .hasMessageContaining("patient.eGfr")
            .hasMessageContaining("orders[].drugCode");

        verify(versions, never()).registerDraft(any());
    }

    @Test
    void rejectsGeneratedCandidatesThatStillCarryRuntimeSelectorOrManualVersionInputs() throws Exception {
        assertThatThrownBy(() -> registry.materializeDraft(new GeneratedAssetCandidateRequest(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.LEGACY",
            "/hospital/H1",
            "FACILITY:H1",
            "source-version:sv-legacy",
            "author-1",
            "trace-legacy",
            read("""
                {
                  "schemaVersion": "1.0",
                  "ruleCode": "RULE.LEGACY",
                  "packageVersion": "pkg-2026.06",
                  "versionNo": "V99",
                  "fieldCatalogIdentity": "FIELD.CATALOG.CLINICAL_CONTEXT",
                  "fieldBindings": ["patient.age"],
                  "terminologyRefs": ["TERM.LOINC"],
                  "triggerBindings": [{"triggerPoint": "ORDER_SIGN", "purpose": "RULE_EXECUTION"}],
                  "dsl": {"when": {"all": []}, "then": [{"actionCardRef": "ACTION.TEST"}], "explain": {"message": "x"}}
                }
                """),
            List.of())))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("调用方运行定位")
            .hasMessageContaining("手工版本");

        verify(versions, never()).registerDraft(any());
    }

    @Test
    void materializesPathwayCandidateWithRuleStableIdentityDependencies() throws Exception {
        when(versions.registerDraft(any())).thenReturn(assetVersion(
            "av-pathway-1", VersionedAssetType.PATHWAY, "PATH.CKD.MEDICATION", "V1"));

        GeneratedAssetDraftResponse response = registry.materializeDraft(new GeneratedAssetCandidateRequest(
            "tenant-A",
            VersionedAssetType.PATHWAY,
            "PATH.CKD.MEDICATION",
            "/hospital/H1",
            "FACILITY:H1",
            "source-version:sv-path",
            "author-1",
            "trace-path-1",
            read("""
                {
                  "schemaVersion": "1.0",
                  "pathwayCode": "PATH.CKD.MEDICATION",
                  "name": "慢性肾病用药复核路径",
                  "startNodeCode": "start",
                  "terminalNodeCodes": ["end"],
                  "triggerBindings": [{
                    "triggerPoint": "DIAGNOSIS_CONFIRM",
                    "purpose": "PATHWAY_ENTRY_CANDIDATE"
                  }],
                  "ruleReferences": ["RULE.CKD.DOSE"],
                  "nodes": [
                    {"nodeCode": "start", "nodeType": "START", "fields": ["observations[].valueNumeric"]},
                    {"nodeCode": "end", "nodeType": "END", "fields": []}
                  ],
                  "edges": [{
                    "edgeCode": "e-start-end",
                    "fromNodeCode": "start",
                    "toNodeCode": "end",
                    "condition": {"ruleIdentity": "RULE.CKD.DOSE"}
                  }]
                }
                """),
            List.of()));

        ArgumentCaptor<AssetVersionRegisterCommand> command =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versions).registerDraft(command.capture());
        assertThat(response.assetType()).isEqualTo(VersionedAssetType.PATHWAY);
        assertThat(command.getValue().dependencies())
            .extracting(AssetDependencyDeclaration::dependsOnAssetType,
                AssetDependencyDeclaration::dependsOnIdentity,
                AssetDependencyDeclaration::kind)
            .contains(org.assertj.core.groups.Tuple.tuple(
                VersionedAssetType.RULE, "RULE.CKD.DOSE", AssetDependencyKind.RULE));
    }

    @Test
    void rejectsPathwayCandidateUsingFieldsOutsideContextCatalog() throws Exception {
        assertThatThrownBy(() -> registry.materializeDraft(new GeneratedAssetCandidateRequest(
            "tenant-A",
            VersionedAssetType.PATHWAY,
            "PATH.CKD.MEDICATION",
            "/hospital/H1",
            "FACILITY:H1",
            "source-version:sv-path",
            "author-1",
            "trace-path-unknown-field",
            read("""
                {
                  "schemaVersion": "1.0",
                  "pathwayCode": "PATH.CKD.MEDICATION",
                  "name": "慢性肾病用药复核路径",
                  "startNodeCode": "start",
                  "terminalNodeCodes": ["end"],
                  "triggerBindings": [{"triggerPoint": "DIAGNOSIS_CONFIRM", "purpose": "PATHWAY_ENTRY_CANDIDATE"}],
                  "ruleReferences": [],
                  "nodes": [
                    {"nodeCode": "start", "nodeType": "START", "fields": ["patient.eGfr"]},
                    {"nodeCode": "end", "nodeType": "END", "fields": []}
                  ],
                  "edges": [{"edgeCode": "e", "fromNodeCode": "start", "toNodeCode": "end"}]
                }
                """),
            List.of())))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("字段目录不存在")
            .hasMessageContaining("patient.eGfr");

        verify(versions, never()).registerDraft(any());
    }

    @Test
    void rejectsPathwayCandidatesWithSubpathsFragmentsOrCycles() throws Exception {
        JsonNode withSubPath = read("""
            {
              "schemaVersion": "1.0",
              "pathwayCode": "PATH.BAD.SUB",
              "name": "错误子路径",
              "startNodeCode": "start",
              "terminalNodeCodes": ["end"],
              "triggerBindings": [{"triggerPoint": "DIAGNOSIS_CONFIRM", "purpose": "PATHWAY_ENTRY_CANDIDATE"}],
              "subPaths": [{"pathwayCode": "PATH.OTHER"}],
              "nodes": [{"nodeCode": "start", "nodeType": "START"}, {"nodeCode": "end", "nodeType": "END"}],
              "edges": [{"edgeCode": "e1", "fromNodeCode": "start", "toNodeCode": "end"}]
            }
            """);
        assertThatThrownBy(() -> pathway(withSubPath))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("子路径");

        JsonNode withCycle = read("""
            {
              "schemaVersion": "1.0",
              "pathwayCode": "PATH.BAD.CYCLE",
              "name": "错误环路",
              "startNodeCode": "start",
              "terminalNodeCodes": ["end"],
              "triggerBindings": [{"triggerPoint": "DIAGNOSIS_CONFIRM", "purpose": "PATHWAY_ENTRY_CANDIDATE"}],
              "nodes": [
                {"nodeCode": "start", "nodeType": "START"},
                {"nodeCode": "review", "nodeType": "TASK"},
                {"nodeCode": "end", "nodeType": "END"}
              ],
              "edges": [
                {"edgeCode": "e1", "fromNodeCode": "start", "toNodeCode": "review"},
                {"edgeCode": "e2", "fromNodeCode": "review", "toNodeCode": "start"}
              ]
            }
            """);
        assertThatThrownBy(() -> pathway(withCycle))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("环");

        verify(versions, never()).registerDraft(any());
    }

    private void pathway(JsonNode content) {
        registry.materializeDraft(new GeneratedAssetCandidateRequest(
            "tenant-A",
            VersionedAssetType.PATHWAY,
            content.path("pathwayCode").asText(),
            "/hospital/H1",
            "FACILITY:H1",
            "source-version:sv-path",
            "author-1",
            "trace-path-bad",
            content,
            List.of()));
    }

    private JsonNode read(String raw) throws Exception {
        return json.readTree(raw);
    }

    private AssetVersion assetVersion(String versionId, VersionedAssetType type, String identity, String versionNo) {
        Instant now = Instant.parse("2026-06-23T00:00:00Z");
        return new AssetVersion(
            1L,
            versionId,
            "tenant-A",
            type,
            identity,
            versionNo,
            "/hospital/H1",
            "FACILITY:H1",
            "0".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT,
            "version:" + versionId,
            "source-version:sv",
            null,
            null,
            now,
            "author-1",
            now,
            "author-1",
            "trace-gen");
    }
}
