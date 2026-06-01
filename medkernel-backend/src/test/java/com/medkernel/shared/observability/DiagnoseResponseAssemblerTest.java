package com.medkernel.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiagnoseResponseAssemblerTest {

    private StateTransitionHistoryRepository historyRepo;
    private DiagnoseResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        historyRepo = mock(StateTransitionHistoryRepository.class);
        assembler = new DiagnoseResponseAssembler(historyRepo);
    }

    @Test
    void assemblesBasicDiagnose() {
        Instant now = Instant.now();
        when(historyRepo.findByEntityTypeAndEntityIdOrderByOccurredAtAsc("clinical_event", "evt-1"))
            .thenReturn(List.of(
                new StateTransitionHistory(1L, "clinical_event", "evt-1", "tenant-A",
                    null, "RECEIVED", "INITIAL_RECEIVE", "tester", "trace-x",
                    null, null, null, null, null, now),
                new StateTransitionHistory(2L, "clinical_event", "evt-1", "tenant-A",
                    "RECEIVED", "PROCESSED", "RULES_OK", "system", "trace-x",
                    null, null, null, null, null, now.plusSeconds(1))
            ));

        DiagnoseResponse resp = assembler.assemble(
            "clinical_event", "evt-1", "tenant-A", "PROCESSED",
            new SampleEntity("evt-1", "PROCESSED"),
            List.of(),
            Map.of(),
            null,
            "trace-x"
        );

        assertThat(resp.entityType()).isEqualTo("clinical_event");
        assertThat(resp.entityId()).isEqualTo("evt-1");
        assertThat(resp.currentStatus()).isEqualTo("PROCESSED");
        assertThat(resp.stateHistory()).hasSize(2);
        assertThat(resp.stateHistory().get(0).toStatus()).isEqualTo("RECEIVED");
        assertThat(resp.stateHistory().get(1).toStatus()).isEqualTo("PROCESSED");
        assertThat(resp.traceId()).isEqualTo("trace-x");
        assertThat(resp.links().self())
            .isEqualTo("/api/v1/engine/clinical_event/evt-1/diagnose");
        assertThat(resp.links().traceTimeline())
            .isEqualTo("/api/v1/engine/diagnose/traces/trace-x");
    }

    @Test
    void assemblesPayloadSummaryWhenRefProvided() {
        when(historyRepo.findByEntityTypeAndEntityIdOrderByOccurredAtAsc("clinical_event", "evt-1"))
            .thenReturn(List.of());

        PayloadRef ref = new PayloadRef(PayloadRef.STORAGE_INLINE, "abc123",
            "db://mk_obs_payload_store/pl-test", 1024L, "application/json");

        DiagnoseResponse resp = assembler.assemble(
            "clinical_event", "evt-1", "tenant-A", "PROCESSED",
            new SampleEntity("evt-1", "PROCESSED"),
            List.of(),
            Map.of(),
            ref,
            "trace-x"
        );

        assertThat(resp.payloadSummary()).isNotNull();
        assertThat(resp.payloadSummary().digest()).isEqualTo("abc123");
        assertThat(resp.payloadSummary().sizeBytes()).isEqualTo(1024L);
        assertThat(resp.payloadSummary().contentType()).isEqualTo("application/json");
        assertThat(resp.payloadSummary().storageType()).isEqualTo("INLINE");
        assertThat(resp.links().fetchPayload())
            .isEqualTo("/api/v1/engine/clinical_event/evt-1/payload");
    }

    @Test
    void translatesErrorIntoStateTransitionEntry() {
        Instant now = Instant.now();
        when(historyRepo.findByEntityTypeAndEntityIdOrderByOccurredAtAsc("clinical_event", "evt-1"))
            .thenReturn(List.of(
                new StateTransitionHistory(1L, "clinical_event", "evt-1", "tenant-A",
                    "MAPPED", "FAILED", "TERMINOLOGY_FAILED", "system", "trace-x",
                    "ENG-CONTEXT-001", "INPUT", "code missing", 2, now.plusSeconds(60), now)
            ));

        DiagnoseResponse resp = assembler.assemble(
            "clinical_event", "evt-1", "tenant-A", "FAILED",
            new SampleEntity("evt-1", "FAILED"),
            List.of(),
            Map.of(),
            null,
            "trace-x"
        );

        var entry = resp.stateHistory().get(0);
        assertThat(entry.error()).isNotNull();
        assertThat(entry.error().errorCode()).isEqualTo("ENG-CONTEXT-001");
        assertThat(entry.error().errorClass()).isEqualTo("INPUT");
        assertThat(entry.error().retryCount()).isEqualTo(2);
    }

    @Test
    void includesExecutionSummaryWithRuleVersionDurationAndDegradationReason() {
        Instant started = Instant.parse("2026-06-01T08:00:00Z");
        Instant ended = started.plusMillis(250);
        when(historyRepo.findByEntityTypeAndEntityIdOrderByOccurredAtAsc("rule_execution", "rex-1"))
            .thenReturn(List.of(
                new StateTransitionHistory(1L, "rule_execution", "rex-1", "tenant-A",
                    null, "RUNNING", "START", "tester", "trace-rule",
                    null, null, null, null, null, started),
                new StateTransitionHistory(2L, "rule_execution", "rex-1", "tenant-A",
                    "RUNNING", "DEGRADED", "MODEL_DISABLED", "tester", "trace-rule",
                    "ENG-SYS-003", "EXTERNAL", "模型不可用，已降级到无模型基线",
                    0, null, ended)
            ));

        DiagnoseResponse resp = assembler.assemble(
            "rule_execution", "rex-1", "tenant-A", "DEGRADED",
            new SampleEntity("rex-1", "DEGRADED"),
            List.of(),
            Map.of(),
            null,
            "trace-rule",
            new DiagnoseResponse.ExecutionSummary("rule-1", "version-1", null, "MODEL_DISABLED")
        );

        assertThat(resp.executionSummary()).isNotNull();
        assertThat(resp.executionSummary().matchedRuleId()).isEqualTo("rule-1");
        assertThat(resp.executionSummary().matchedVersionId()).isEqualTo("version-1");
        assertThat(resp.executionSummary().durationMs()).isEqualTo(250L);
        assertThat(resp.executionSummary().degradationReason()).isEqualTo("MODEL_DISABLED");
    }

    record SampleEntity(String id, String status) {}
}
