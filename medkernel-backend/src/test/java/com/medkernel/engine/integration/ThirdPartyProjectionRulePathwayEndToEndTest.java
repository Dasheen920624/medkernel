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
import com.medkernel.engine.clinical.model.ClinicalClaimRepository;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceRepository;
import com.medkernel.engine.context.ClinicalEvent;
import com.medkernel.engine.context.ClinicalEventContext;
import com.medkernel.engine.context.ClinicalEventContextFactory;
import com.medkernel.engine.context.ClinicalEventPayload;
import com.medkernel.engine.context.ClinicalEventStatus;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.context.ClinicalEventType;
import com.medkernel.engine.context.ContextIdempotencyKeyRepository;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotRepository;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextValidator;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.CurrentClinicalRuntimeReleaseResolver;
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
import com.medkernel.engine.rule.RuleActionCode;
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
            "runtime-release-1",
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
            List.of(),
            ContextSnapshotResources.emptyExtensions());

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
                {"actionCode": "REMIND", "atSeverity": "MEDIUM", "indicator": "warning", "summary": "血红蛋白结果达标", "detail": "血红蛋白结果达标", "source": {"label": "规则测试来源"}, "suggestions": [], "overrideReasons": []}
              ],
              "explain": {"title": "第三方检验规则样例"}
            }
            """), json.valueToTree(canonicalFacts));

        assertThat(rule.hit()).isTrue();
        assertThat(rule.actions()).extracting("actionCode").containsExactly(RuleActionCode.REMIND);

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

    @Test
    void orderClinicalEventNormalizesLocalMedicationCodeBeforeRuleAndPathwayEvaluation() throws Exception {
        ClinicalEventContext context = new ClinicalEventContextFactory(json).from(orderEvent(), orderPayload());

        assertThat(context.payload().path("medications").path(0).path("code").asText())
            .isEqualTo("ATC-J01CA04");
        assertThat(context.codeMappingAnchors()).anySatisfy(anchor -> {
            assertThat(anchor.localCode()).isEqualTo("HIS-AMOX");
            assertThat(anchor.mappedVersion()).isEqualTo("TERM-2026.06");
        });

        RuleDslEvaluation rule = new RuleDslEvaluator(json).evaluate(json.readTree("""
            {
              "trigger": "order-sign",
              "when": {
                "expr": {"field": "medications[].code"},
                "operator": "equals",
                "value": "ATC-J01CA04"
              },
              "then": [
                {"actionCode": "REMIND", "atSeverity": "MEDIUM", "indicator": "warning", "summary": "抗菌药医嘱复核", "detail": "抗菌药医嘱复核", "source": {"label": "规则测试来源"}, "suggestions": [], "overrideReasons": []}
              ],
              "explain": {"title": "开医嘱触发规则样例"}
            }
            """), context.payload());

        assertThat(rule.hit()).isTrue();
        assertThat(rule.actions()).extracting("actionCode").containsExactly(RuleActionCode.REMIND);

        var decision = new PathwayProgressor(json, new ConditionEvaluator(json)).advance(new PathwayProgressCommand(
            orderPathwayGraph(),
            "ORDER_REVIEW",
            PathwayAdvanceEventType.COMPLETE,
            null,
            json.convertValue(context.payload(), new TypeReference<Map<String, Object>>() {})));

        assertThat(decision.status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        assertThat(decision.nextNodeCode()).isEqualTo("PHARMACY_REVIEW");
        assertThat(decision.evidence()).containsKey("medications[].code");
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
        when(terminology.evaluate(
            eq("tenant-A"), eq("runtime-release-test"), anyList()))
            .thenReturn(Map.of("OBSERVATION:obs-third-party-hb:code:HB", "CONFIRMED"));
        CurrentClinicalRuntimeReleaseResolver runtimeReleases =
            mock(CurrentClinicalRuntimeReleaseResolver.class);
        when(runtimeReleases.resolve(any(OrgScope.class))).thenReturn(new ClinicalRuntimeRelease(
            1L, "runtime-release-test", "tenant-A", "hospital-A", 1L, "baseline-1",
            "a".repeat(64), null, Instant.now(), "tester", Instant.now(), "tester", "trace-third-party"));
        return new ContextSnapshotService(
            snapshots,
            resources,
            idemRepo,
            new ContextValidator(),
            mock(ClinicalClaimRepository.class),
            runtimeReleases,
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

    private PathwayGraph orderPathwayGraph() {
        String tenantId = "tenant-A";
        String templateId = "pt-order";
        Instant now = Instant.parse("2026-06-03T00:00:00Z");
        return new PathwayGraph(
            List.of(
                node("ORDER_REVIEW", templateId, now, false),
                node("PHARMACY_REVIEW", templateId, now, true),
                node("MANUAL_REVIEW", templateId, now, false)
            ),
            List.of(
                new PathwayEdge(
                    null,
                    "edge-order-medication",
                    tenantId,
                    templateId,
                    "EDGE.ORDER.PHARMACY",
                    "ORDER_REVIEW",
                    "PHARMACY_REVIEW",
                    PathwayEdgeType.CONDITION,
                    "{\"expr\":{\"field\":\"medications[].code\"},\"operator\":\"equals\",\"value\":\"ATC-J01CA04\"}",
                    1,
                    now,
                    "tester",
                    now,
                    "tester",
                    "trace-pathway"),
                new PathwayEdge(
                    null,
                    "edge-order-default",
                    tenantId,
                    templateId,
                    "EDGE.ORDER.MANUAL",
                    "ORDER_REVIEW",
                    "MANUAL_REVIEW",
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

    private ClinicalEvent orderEvent() {
        return new ClinicalEvent(
            1L,
            "evt-order-e2e",
            "tenant-A",
            ClinicalEventType.ORDER,
            ClinicalEventTriggerPoint.ORDER_SIGN,
            null,
            null,
            "{\"tenantId\":\"tenant-A\",\"departmentId\":\"dept-A\"}",
            "MPI-ORDER",
            "ENC-ORDER",
            com.medkernel.engine.context.canonical.ClinicalSetting.INPATIENT,
            "HIS",
            "runtime-release-test",
            "sha256:order",
            Instant.parse("2026-06-03T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:01Z"),
            null,
            ClinicalEventStatus.MAPPED,
            null,
            null,
            0,
            null,
            "trace-order");
    }

    private ClinicalEventPayload orderPayload() {
        String payload = """
            {
              "orders": [
                {
                  "orderId": "ord-1",
                  "localCode": "HIS-AMOX",
                  "standardCode": "ATC-J01CA04",
                  "displayName": "阿莫西林",
                  "dose": 0.5,
                  "doseUnit": "g",
                  "route": "PO",
                  "frequency": "TID",
                  "status": "ACTIVE",
                  "sourceRecordId": "his-order-1",
                  "mappedVersion": "TERM-2026.06"
                }
              ]
            }
            """;
        return new ClinicalEventPayload(
            1L,
            "evt-order-e2e",
            "tenant-A",
            payload,
            null,
            "INLINE",
            "application/json",
            "sha256:order",
            (long) payload.length(),
            Instant.parse("2026-06-03T00:00:01Z"),
            null);
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
            null,
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
        return (tenantId, runtimeReleaseId, anchors) -> anchors.stream()
            .collect(Collectors.toMap(anchor -> anchor.key(), anchor -> status, (left, right) -> left));
    }
}
