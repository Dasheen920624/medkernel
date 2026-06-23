package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.MissingFieldEntry;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuthoringPreviewRunServiceTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Mock
    ContextSnapshotService snapshots;

    private AuthoringPreviewRunService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(json);
        service = new AuthoringPreviewRunService(
            json,
            snapshots,
            new RuleDslEvaluator(json, conditionEvaluator),
            conditionEvaluator);
    }

    @Test
    void previewRunsDraftRuleAgainstActiveSnapshotAndReturnsEvidence() throws Exception {
        when(snapshots.findById("ctx-001")).thenReturn(activeSnapshot());

        AuthoringPreviewRunResponse response = service.run(new AuthoringPreviewRunRequest(
            apiContext(),
            AuthoringPreviewSubject.RULE_CONDITION,
            "ctx-001",
            json.readTree("""
                {
                  "trigger": "result-review",
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
                    {
                      "atSeverity": "CRITICAL",
                      "actionCode": "STRONG_REMINDER",
                      "indicator": "critical",
                      "summary": "血钾危急值回报",
                      "detail": "15 分钟内回报并留痕",
                      "source": {"label": "检验危急值制度"},
                      "suggestions": [],
                      "overrideReasons": []
                    }
                  ],
                  "explain": {"summary": "依据真实快照试运行"}
                }
                """),
            null,
            List.of()
        ));

        assertThat(response.subject()).isEqualTo(AuthoringPreviewSubject.RULE_CONDITION);
        assertThat(response.snapshotId()).isEqualTo("ctx-001");
        assertThat(response.matched()).isTrue();
        assertThat(response.hit()).isTrue();
        assertThat(response.severity()).isEqualTo("CRITICAL");
        assertThat(response.contextQualityStatus()).isEqualTo(QualityStatus.PARTIAL);
        assertThat(response.missingFields()).containsExactly(new MissingFieldEntry("OBSERVATION", "unit", "WARN"));
        assertThat(response.conditionEvidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.fact()).isEqualTo("observations[].valueNumeric");
            assertThat(evidence.matched()).isTrue();
            assertThat(evidence.formula()).contains("latest");
        });
    }

    @Test
    void previewRunRejectsNonActiveSnapshotInsteadOfFallingBackToFakeData() throws Exception {
        when(snapshots.findById("ctx-draft")).thenReturn(new ContextSnapshotResponse(
            "ctx-draft",
            ContextSnapshotStatus.DRAFT,
            null,
            "runtime-release-test",
            QualityStatus.PARTIAL,
            List.of(),
            Map.of(),
            Instant.parse("2026-06-02T08:00:00Z"),
            "trace-draft"));

        assertThatThrownBy(() -> service.run(new AuthoringPreviewRunRequest(
            apiContext(),
            AuthoringPreviewSubject.RULE_CONDITION,
            "ctx-draft",
            json.readTree("""
                {"trigger":"result-review","when":{"all":[]},"then":[]}
                """),
            null,
            List.of()
        )))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.ENG_CONTEXT_003);
    }

    @Test
    void previewRunsDraftPathwayAndLocatesSelectedEdgeEvidence() throws Exception {
        when(snapshots.findById("ctx-001")).thenReturn(activeSnapshot());

        AuthoringPreviewRunResponse response = service.run(new AuthoringPreviewRunRequest(
            apiContext(),
            AuthoringPreviewSubject.PATHWAY_GUARD,
            "ctx-001",
            json.readTree("""
                {
                  "startNodeCode": "ASSESS",
                  "nodes": [
                    {"nodeCode": "ASSESS", "name": "急诊评估"},
                    {"nodeCode": "DISPOSITION", "name": "去向决策", "terminal": true}
                  ],
                  "edges": [
                    {
                      "edgeCode": "E-ASSESS-DISPOSITION",
                      "fromNodeCode": "ASSESS",
                      "toNodeCode": "DISPOSITION",
                      "condition": {
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
                      }
                    }
                  ]
                }
                """),
            "ASSESS",
            List.of()
        ));

        assertThat(response.subject()).isEqualTo(AuthoringPreviewSubject.PATHWAY_GUARD);
        assertThat(response.matched()).isTrue();
        assertThat(response.selectedEdgeCode()).isEqualTo("E-ASSESS-DISPOSITION");
        assertThat(response.nodeTrajectory()).containsExactly("ASSESS", "DISPOSITION");
        assertThat(response.conditionEvidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.fact()).isEqualTo("observations[].valueNumeric");
            assertThat(evidence.matched()).isTrue();
        });
    }

    private ContextSnapshotResponse activeSnapshot() {
        CanonicalPatient patient = new CanonicalPatient(
            "MPI-001",
            "脱敏患者",
            LocalDate.of(1970, 1, 1),
            "M",
            List.of(),
            "HIS",
            "P-001",
            "HIS-2026.06",
            Instant.parse("2026-06-02T08:00:00Z"),
            Instant.parse("2026-06-02T08:01:00Z"),
            QualityStatus.VALID);
        CanonicalObservation potassium = new CanonicalObservation(
            "obs-k-1",
            "K",
            "血钾",
            BigDecimal.valueOf(6.8),
            null,
            "mmol/L",
            "3.5-5.5",
            "HH",
            "LIS",
            "OBS-001",
            "LIS-2026.06",
            Instant.parse("2026-06-02T08:00:00Z"),
            Instant.parse("2026-06-02T08:01:00Z"),
            QualityStatus.VALID);
        return new ContextSnapshotResponse(
            "ctx-001",
            ContextSnapshotStatus.ACTIVE,
            new ContextSnapshotResources(
                patient,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(potassium),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ContextSnapshotResources.emptyExtensions()),
            "runtime-release-test",
            QualityStatus.PARTIAL,
            List.of(new MissingFieldEntry("OBSERVATION", "unit", "WARN")),
            Map.of(),
            Instant.parse("2026-06-02T08:00:00Z"),
            "trace-ctx");
    }

    private static AuthoringApiContext apiContext() {
        return new AuthoringApiContext(
            "req-preview-run",
            "trace-preview-run",
            "tenant-A",
            null,
            null,
            null,
            null,
            null,
            null,
            "author-1",
            List.of("engine-operator")
        );
    }
}
