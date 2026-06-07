package com.medkernel.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceRepository;
import com.medkernel.engine.context.ContextIdempotencyKeyRepository;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotRepository;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextValidator;
import com.medkernel.engine.context.PackageVersionPort;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.TerminologyMappingPort;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalNursingAssessment;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.integration.fhir.FhirCanonicalMappingRequest;
import com.medkernel.engine.integration.fhir.FhirR4CanonicalMapper;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.pathway.PathwayAdvanceEventType;
import com.medkernel.engine.pathway.PathwayEdge;
import com.medkernel.engine.pathway.PathwayEdgeType;
import com.medkernel.engine.pathway.PathwayGraph;
import com.medkernel.engine.pathway.PathwayNode;
import com.medkernel.engine.pathway.PathwayNodeType;
import com.medkernel.engine.pathway.PathwayProgressCommand;
import com.medkernel.engine.pathway.PathwayProgressor;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;

class ThirdPartyProjectionRulePathwayEndToEndTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void thirdPartyObservationProjectionAndTerminologyMappingCanDriveRuleAndPathway() throws Exception {
        FhirR4CanonicalMapper mapper = new FhirR4CanonicalMapper(json, terminologyReturning("VALID"));
        var mapping = mapper.fromR4(new FhirCanonicalMappingRequest(
            "tenant-A",
            "ctx-third-party",
            1,
            "trace-third-party",
            Instant.parse("2026-06-03T00:05:00Z"),
            json.readTree("""
                {
                  "resourceType": "Observation",
                  "id": "obs-third-party-hb",
                  "code": {
                    "coding": [
                      {
                        "system": "urn:local:lis",
                        "code": "HB",
                        "display": "血红蛋白"
                      }
                    ]
                  },
                  "effectiveDateTime": "2026-06-03T00:00:00Z",
                  "valueQuantity": {
                    "value": 132,
                    "unit": "g/L"
                  }
                }
                """)));
        CanonicalResource projected = mapping.resource();
        CanonicalObservation observation =
            json.readValue(projected.resourcePayloadJson(), CanonicalObservation.class);
        assertThat(projected.qualityStatus()).isEqualTo(QualityStatus.VALID);
        assertThat(observation.code()).isEqualTo("HB");
        assertThat(observation.valueNumeric()).isEqualByComparingTo(new BigDecimal("132"));

        ContextSnapshotService snapshotService = snapshotServiceWithConfirmedTerminology();
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-third-party",
            OrgScope.tenant("tenant-A"),
            "integration-tester"));
        var resources = new ContextSnapshotResources(
            new CanonicalPatient(
                "MPI-THIRD",
                "脱敏患者",
                LocalDate.parse("1980-01-01"),
                "F",
                List.of(),
                "HIS",
                "PAT-THIRD",
                "FHIR_R4:Patient",
                Instant.parse("2026-06-03T00:00:00Z"),
                Instant.parse("2026-06-03T00:05:00Z"),
                QualityStatus.VALID),
            List.of(),
            List.of(),
            List.of(),
            List.<CanonicalNursingAssessment>of(),
            List.of(observation),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

        var snapshot = snapshotService.create(new ContextSnapshotRequest(
            "req-third-party",
            "trace-client",
            "tenant-A",
            null,
            null,
            null,
            null,
            null,
            null,
            "integration-tester",
            List.of("IT_OPS"),
            "MPI-THIRD",
            "ENC-THIRD",
            "ORG-THIRD",
            "pkg-2026.06",
            resources), null);

        assertThat(snapshot.mappingStatus())
            .containsEntry("OBSERVATION:obs-third-party-hb:code:HB", "CONFIRMED");
        Map<String, Object> canonicalFacts =
            json.convertValue(snapshot.resources(), new TypeReference<>() {});

        RuleDslEvaluation rule = new RuleDslEvaluator(json).evaluate(json.readTree("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {"expr": {"field": "observations[].code"}, "operator": "equals", "value": "HB"},
                  {"expr": {"field": "observations[].valueNumeric"}, "operator": "gte", "value": 120}
                ]
              },
              "then": [
                {"actionCode": "PROMPT", "severity": "MEDIUM", "message": "血红蛋白结果达标"}
              ],
              "explain": {"title": "第三方检验规则样例"}
            }
            """), json.valueToTree(canonicalFacts));

        assertThat(rule.hit()).isTrue();
        assertThat(rule.actions()).extracting("actionCode").containsExactly("PROMPT");

        var decision = new PathwayProgressor(json, new ConditionEvaluator(json)).advance(new PathwayProgressCommand(
            pathwayGraph(),
            "REVIEW",
            PathwayAdvanceEventType.COMPLETE,
            null,
            canonicalFacts));

        assertThat(decision.status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        assertThat(decision.nextNodeCode()).isEqualTo("FOLLOWUP");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.CONDITION);
        assertThat(decision.evidence()).containsKey("observations[].valueNumeric");
    }

    private ContextSnapshotService snapshotServiceWithConfirmedTerminology() {
        ContextSnapshotRepository snapshots = mock(ContextSnapshotRepository.class);
        CanonicalResourceRepository resources = mock(CanonicalResourceRepository.class);
        ContextIdempotencyKeyRepository idemRepo = mock(ContextIdempotencyKeyRepository.class);
        TerminologyMappingPort terminology = mock(TerminologyMappingPort.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        IsolatedAuditPublisher isolatedAudit = mock(IsolatedAuditPublisher.class);
        StateTransitionRecorder transitions = mock(StateTransitionRecorder.class);
        DiagnoseResponseAssembler diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resources.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(terminology.evaluate(eq("tenant-A"), anyList()))
            .thenReturn(Map.of("OBSERVATION:obs-third-party-hb:code:HB", "CONFIRMED"));
        PackageVersionPort packageVersions = mock(PackageVersionPort.class);
        when(packageVersions.exists("tenant-A", "pkg-2026.06")).thenReturn(true);
        return new ContextSnapshotService(
            snapshots,
            resources,
            idemRepo,
            new ContextValidator(),
            packageVersions,
            terminology,
            auditRecorder,
            isolatedAudit,
            transitions,
            diagnoseAssembler,
            json);
    }

    private PathwayGraph pathwayGraph() {
        String tenantId = "tenant-A";
        String templateId = "pt-third-party";
        Instant now = Instant.parse("2026-06-03T00:00:00Z");
        return new PathwayGraph(
            List.of(
                node("REVIEW", templateId, now, false),
                node("FOLLOWUP", templateId, now, true),
                node("PLAN", templateId, now, false)
            ),
            List.of(
                new PathwayEdge(
                    null,
                    "edge-hb-ok",
                    tenantId,
                    templateId,
                    "EDGE.REVIEW.FOLLOWUP",
                    "REVIEW",
                    "FOLLOWUP",
                    PathwayEdgeType.CONDITION,
                    "{\"expr\":{\"field\":\"observations[].valueNumeric\"},\"operator\":\"gte\",\"value\":120}",
                    1,
                    now,
                    "tester",
                    now,
                    "tester",
                    "trace-pathway"),
                new PathwayEdge(
                    null,
                    "edge-default",
                    tenantId,
                    templateId,
                    "EDGE.REVIEW.PLAN",
                    "REVIEW",
                    "PLAN",
                    PathwayEdgeType.DEFAULT,
                    null,
                    2,
                    now,
                    "tester",
                    now,
                    "tester",
                    "trace-pathway")
            ));
    }

    private static PathwayNode node(String code, String templateId, Instant now, boolean terminal) {
        return new PathwayNode(
            null,
            "pn-" + code,
            "tenant-A",
            templateId,
            code,
            code,
            PathwayNodeType.ASSESSMENT,
            1,
            "医生",
            null,
            null,
            terminal,
            null,
            now,
            "tester",
            now,
            "tester",
            "trace-pathway");
    }

    private static TerminologyMappingPort terminologyReturning(String status) {
        return (tenantId, anchors) -> anchors.stream()
            .collect(Collectors.toMap(anchor -> anchor.key(), anchor -> status, (left, right) -> left));
    }
}
