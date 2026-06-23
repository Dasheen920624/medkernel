package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.rule.RuleApplicabilityDecision;
import com.medkernel.engine.rule.RuleApplicabilityService;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;

class RuleReleaseSimulationReplayEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");

    @Test
    void replaysCurrentAndCandidateRulesWithoutWritingExecutionFacts() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        RuleDefinitionRepository definitions = mock(RuleDefinitionRepository.class);
        RuleVersionRepository versions = mock(RuleVersionRepository.class);
        ContextSnapshotService contextSnapshots = mock(ContextSnapshotService.class);
        RuleDslEvaluator dslEvaluator = mock(RuleDslEvaluator.class);
        RuleApplicabilityService applicability = mock(RuleApplicabilityService.class);
        OrgHierarchyRepository orgHierarchy = mock(OrgHierarchyRepository.class);
        RuleDefinition definition = definition();
        RuleVersion currentRule = ruleVersion("rv-1", 1, "current");
        RuleVersion candidateRule = ruleVersion("rv-2", 2, "candidate");
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.VTE.RISK"))
            .thenReturn(Optional.of(definition));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-1", "tenant-A", 1))
            .thenReturn(Optional.of(currentRule));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-1", "tenant-A", 2))
            .thenReturn(Optional.of(candidateRule));
        when(contextSnapshots.findById("ctx-1")).thenReturn(snapshotResponse("p1"));
        when(contextSnapshots.findById("ctx-2")).thenReturn(snapshotResponse("p2"));
        when(contextSnapshots.findById("ctx-3")).thenReturn(snapshotResponse("p3"));
        when(applicability.evaluate(any(), any(), any(), any()))
            .thenReturn(new RuleApplicabilityDecision(true, "APPLICABLE", "适用", json.createObjectNode()));
        when(dslEvaluator.evaluate(any(), any())).thenAnswer(invocation -> {
            JsonNode dsl = invocation.getArgument(0);
            JsonNode context = invocation.getArgument(1);
            String marker = dsl.path("marker").asText();
            String patient = context.path("patient").path("mpi").asText();
            if ("current".equals(marker) && "p1".equals(patient)) {
                return new RuleDslEvaluation(true, RuleRiskLevel.LOW, List.of(), json.createObjectNode());
            }
            if ("candidate".equals(marker) && "p1".equals(patient)) {
                return new RuleDslEvaluation(true, RuleRiskLevel.HIGH, List.of(), json.createObjectNode());
            }
            if ("candidate".equals(marker) && "p2".equals(patient)) {
                return new RuleDslEvaluation(true, RuleRiskLevel.MEDIUM, List.of(), json.createObjectNode());
            }
            return new RuleDslEvaluation(false, null, List.of(), json.createObjectNode());
        });

        RuleReleaseSimulationReplayEvaluator evaluator = new RuleReleaseSimulationReplayEvaluator(
            definitions,
            versions,
            contextSnapshots,
            dslEvaluator,
            applicability,
            orgHierarchy,
            json
        );

        ReleaseSimulationResult.Replay result = evaluator.replay(
            command(),
            assetVersion("av-v1", "1"),
            assetVersion("av-v2", "2"),
            List.of(snapshot("ctx-1"), snapshot("ctx-2"), snapshot("ctx-3"))
        );

        assertThat(result.status()).isEqualTo("SUPPORTED");
        assertThat(result.sampledCases()).isEqualTo(3);
        assertThat(result.changedCases()).isEqualTo(2);
        assertThat(result.triggerIncreases()).isEqualTo(1);
        assertThat(result.triggerDecreases()).isZero();
        assertThat(result.severityIncreases()).isEqualTo(1);
        assertThat(result.severityDecreases()).isZero();
        assertThat(result.highRiskSnapshotIds()).containsExactly("ctx-1");
    }

    private ReleaseSimulationCommand command() {
        return new ReleaseSimulationCommand(
            "tenant-A",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v2",
            List.of("hospital-A"),
            "/TENANT-A/HOSP-A",
            "adult|inpatient",
            RolloutPolicy.all(),
            30,
            100
        );
    }

    private AssetVersion assetVersion(String versionId, String versionNo) {
        return new AssetVersion(
            1L,
            versionId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionNo,
            "/TENANT-A/HOSP-A",
            "adult|inpatient",
            "hash-" + versionNo,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.REVIEW,
            AssetVersionStatus.PUBLISHED,
            "scope",
            "source",
            NOW,
            null,
            NOW,
            "author",
            NOW,
            "author",
            "trace"
        );
    }

    private RuleDefinition definition() {
        return new RuleDefinition(
            1L,
            "rule-1",
            "tenant-A",
            "RULE.VTE.RISK",
            "VTE 风险",
            RuleType.ORDER,
            RuleAuthoringMode.DSL,
            RuleRiskLevel.HIGH,
            100,
            null,
            0,
            RuleDefinitionStatus.PUBLISHED,
            "rv-1",
            "hospital-A",
            NOW,
            "author",
            NOW,
            "author",
            "trace"
        );
    }

    private RuleVersion ruleVersion(String versionId, int versionNo, String marker) {
        return new RuleVersion(
            1L,
            versionId,
            "tenant-A",
            "rule-1",
            versionNo,
            "source",
            "change",
            "{\"marker\":\"" + marker + "\"}",
            "{}",
            RuleVersionStatus.PUBLISHED,
            NOW,
            "author",
            null,
            NOW,
            "author",
            NOW,
            "author",
            "trace"
        );
    }

    private ContextSnapshot snapshot(String snapshotId) {
        return new ContextSnapshot(
            1L,
            snapshotId,
            "tenant-A",
            "hospital-A",
            "request-" + snapshotId,
            "/TENANT-A/HOSP-A",
            "runtime-release-test",
            "patient",
            "encounter",
            ContextSnapshotStatus.ACTIVE,
            "[]",
            "{}",
            "{}",
            QualityStatus.VALID,
            "trace",
            null,
            NOW,
            "author"
        );
    }

    private ContextSnapshotResponse snapshotResponse(String patientId) {
        CanonicalPatient patient = new CanonicalPatient(
            patientId,
            "测试患者",
            LocalDate.of(1980, 1, 1),
            "UNKNOWN",
            List.of(),
            "test",
            patientId,
            "1",
            NOW,
            NOW,
            QualityStatus.VALID
        );
        ContextSnapshotResources resources = new ContextSnapshotResources(
            patient,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            ContextSnapshotResources.emptyExtensions()
        );
        return new ContextSnapshotResponse(
            "ctx-" + patientId,
            ContextSnapshotStatus.ACTIVE,
            resources,
            "runtime-release-test",
            QualityStatus.VALID,
            List.of(),
            Map.of(),
            NOW,
            "trace"
        );
    }
}
