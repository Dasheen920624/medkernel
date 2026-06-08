package com.medkernel.engine.context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class ClinicalEventOutboxWorkerTest {

    private ClinicalEventOutboxRepository outbox;
    private ClinicalEventProcessor processor;
    private ClinicalEventOutboxWorker worker;

    @BeforeEach
    void setUp() {
        outbox = mock(ClinicalEventOutboxRepository.class);
        processor = mock(ClinicalEventProcessor.class);
        worker = new ClinicalEventOutboxWorker(
            outbox, processor,
            new ClinicalEventProperties(1024, java.time.Duration.ofMillis(50), 10, 3, List.of(1L, 5L)));
    }

    @Test
    void pollClaimsAndProcessesReadyOutbox() {
        ClinicalEventOutbox row = row(1L, 0);
        when(outbox.findReadyToClaim(any(), eq(10))).thenReturn(List.of(row));
        when(outbox.claim(eq(1L), eq("CLAIMED"), any(), any())).thenReturn(1);

        worker.pollOnce();

        verify(processor).process("evt-1", "tenant-A");
        verify(outbox).markProcessed(eq(1L), any());
    }

    @Test
    void pollSkipsRowsClaimedByAnotherWorker() {
        ClinicalEventOutbox row = row(1L, 0);
        when(outbox.findReadyToClaim(any(), eq(10))).thenReturn(List.of(row));
        when(outbox.claim(eq(1L), eq("CLAIMED"), any(), any())).thenReturn(0);

        worker.pollOnce();

        verify(processor, never()).process(any(), any());
        verify(outbox, never()).markProcessed(any(), any());
    }

    @Test
    void pollRetriesFailureBeforeMaxRetries() {
        ClinicalEventOutbox row = row(1L, 1);
        when(outbox.findReadyToClaim(any(), eq(10))).thenReturn(List.of(row));
        when(outbox.claim(eq(1L), eq("CLAIMED"), any(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new ApiException(ErrorCode.ENG_EVENT_004))
            .when(processor).process("evt-1", "tenant-A");

        worker.pollOnce();

        verify(outbox).markFailed(eq(1L), eq("PENDING"), eq(2),
            eq(ErrorCode.ENG_EVENT_004.code()), any());
        verify(processor).markFailed(eq("evt-1"), eq("tenant-A"),
            eq(ErrorCode.ENG_EVENT_004), eq(2), eq(false), any());
    }

    @Test
    void pollRetriesOutputDistributionUnavailableWithHonestCode() {
        ClinicalEventOutbox row = row(1L, 1);
        when(outbox.findReadyToClaim(any(), eq(10))).thenReturn(List.of(row));
        when(outbox.claim(eq(1L), eq("CLAIMED"), any(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new ApiException(
                ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "临床事件产出分发不可用: 协同库不可达"))
            .when(processor).process("evt-1", "tenant-A");

        worker.pollOnce();

        verify(outbox).markFailed(eq(1L), eq("PENDING"), eq(2),
            eq(ErrorCode.DOWNSTREAM_UNAVAILABLE.code()), any());
        verify(processor).markFailed(eq("evt-1"), eq("tenant-A"),
            eq(ErrorCode.DOWNSTREAM_UNAVAILABLE), eq(2), eq(false), any());
    }

    @Test
    void pollMarksDeadWhenMaxRetriesReached() {
        ClinicalEventOutbox row = row(1L, 2);
        when(outbox.findReadyToClaim(any(), eq(10))).thenReturn(List.of(row));
        when(outbox.claim(eq(1L), eq("CLAIMED"), any(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new ApiException(ErrorCode.ENG_EVENT_005))
            .when(processor).process("evt-1", "tenant-A");

        worker.pollOnce();

        verify(outbox).markFailed(eq(1L), eq("DEAD"), eq(3),
            eq(ErrorCode.ENG_EVENT_005.code()), any());
        verify(processor).markFailed(eq("evt-1"), eq("tenant-A"),
            eq(ErrorCode.ENG_EVENT_005), eq(3), eq(true), any());
    }

    private ClinicalEventOutbox row(Long id, int retryCount) {
        return new ClinicalEventOutbox(
            id, "evt-1", "tenant-A", "trace-event", "tester", "PENDING",
            null, null, Instant.now(), retryCount, null, Instant.now(), null);
    }
}
