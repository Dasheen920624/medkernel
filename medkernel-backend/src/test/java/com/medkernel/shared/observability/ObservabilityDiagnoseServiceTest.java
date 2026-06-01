package com.medkernel.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class ObservabilityDiagnoseServiceTest {

    private final StateTransitionHistoryRepository historyRepository =
        mock(StateTransitionHistoryRepository.class);
    private final PayloadStoragePort payloadStorage = mock(PayloadStoragePort.class);
    private final ObservabilityDiagnoseService service =
        new ObservabilityDiagnoseService(historyRepository, payloadStorage);

    @Test
    void buildsTraceDiagnosisFromStateHistoryAndPayloadRefs() {
        Instant started = Instant.parse("2026-06-01T08:00:00Z");
        Instant ended = started.plusMillis(1234);
        when(historyRepository.findByTraceIdOrderByOccurredAtAsc("trace-x")).thenReturn(List.of(
            new StateTransitionHistory(
                1L, "rule_execution", "exec-1", "tenant-A", "tenant-A/hospital-A",
                null, "RUNNING", "START", "doctor-1", "trace-x",
                null, null, null, null, null, started, started, "doctor-1"),
            new StateTransitionHistory(
                2L, "rule_execution", "exec-1", "tenant-A", "tenant-A/hospital-A",
                "RUNNING", "DEGRADED", "MODEL_DISABLED", "doctor-1", "trace-x",
                "ENG-SYS-003", "EXTERNAL", "模型不可用，已降级到无模型基线",
                0, null, ended, ended, "doctor-1")
        ));
        when(payloadStorage.findByTraceId("trace-x")).thenReturn(List.of(
            new PayloadRef(
                PayloadRef.STORAGE_INLINE,
                "sha256-a",
                "db://mk_obs_payload_store/pl-1",
                128L,
                "application/json")
        ));

        TraceDiagnoseResponse response = service.findByTraceId("trace-x");

        assertThat(response.traceId()).isEqualTo("trace-x");
        assertThat(response.startedAt()).isEqualTo(started);
        assertThat(response.endedAt()).isEqualTo(ended);
        assertThat(response.durationMs()).isEqualTo(1234L);
        assertThat(response.stateHistory()).hasSize(2);
        assertThat(response.stateHistory().get(1).error().errorCode()).isEqualTo("ENG-SYS-003");
        assertThat(response.payloads()).hasSize(1);
        assertThat(response.payloads().get(0).contentType()).isEqualTo("application/json");
        assertThat(response.payloads().get(0).fetchUri()).isEqualTo("db://mk_obs_payload_store/pl-1");
    }
}
