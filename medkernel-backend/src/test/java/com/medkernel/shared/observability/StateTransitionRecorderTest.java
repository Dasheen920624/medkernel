package com.medkernel.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import com.medkernel.shared.api.error.ErrorCode.ErrorClass;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class StateTransitionRecorderTest {

    private StateTransitionHistoryRepository repository;
    private StateTransitionRecorder recorder;

    @BeforeEach
    void setUp() {
        repository = mock(StateTransitionHistoryRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        recorder = new StateTransitionRecorder(repository);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-test",
            new OrgScope("tenant-A", "group-A", "hospital-A", null, null, "dept-A", null),
            "tester"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void recordsSuccessTransitionWithRequestContext() {
        recorder.record("context_snapshot", "ctx-1", null, "ACTIVE", "INITIAL_CREATE", null);

        ArgumentCaptor<StateTransitionHistory> captor = ArgumentCaptor.forClass(StateTransitionHistory.class);
        verify(repository).save(captor.capture());
        StateTransitionHistory saved = captor.getValue();
        assertThat(saved.entityType()).isEqualTo("context_snapshot");
        assertThat(saved.entityId()).isEqualTo("ctx-1");
        assertThat(saved.tenantId()).isEqualTo("tenant-A");
        assertThat(saved.orgPath()).isEqualTo("tenant-A/group-A/hospital-A/dept-A");
        assertThat(saved.toStatus()).isEqualTo("ACTIVE");
        assertThat(saved.reason()).isEqualTo("INITIAL_CREATE");
        assertThat(saved.actor()).isEqualTo("tester");
        assertThat(saved.createdBy()).isEqualTo("tester");
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.traceId()).isEqualTo("trace-test");
        assertThat(saved.errorCode()).isNull();
    }

    @Test
    void recordsFailureWithStructuredError() {
        TransitionError error = TransitionError.of("ENG-CONTEXT-001", ErrorClass.INPUT,
            "schema invalid", 1, Instant.now().plusSeconds(30));

        recorder.record("clinical_event", "evt-1", "MAPPED", "FAILED", "TERMINOLOGY_FAILED", error);

        ArgumentCaptor<StateTransitionHistory> captor = ArgumentCaptor.forClass(StateTransitionHistory.class);
        verify(repository).save(captor.capture());
        StateTransitionHistory saved = captor.getValue();
        assertThat(saved.errorCode()).isEqualTo("ENG-CONTEXT-001");
        assertThat(saved.errorClass()).isEqualTo("INPUT");
        assertThat(saved.errorMessage()).isEqualTo("schema invalid");
        assertThat(saved.retryCount()).isEqualTo(1);
    }

    @Test
    void truncatesLongMessage() {
        String longMsg = "a".repeat(1000);
        TransitionError error = TransitionError.of("ENG-SYS-001", ErrorClass.INTERNAL, longMsg, 0, null);

        recorder.record("foo", "id-1", "A", "B", "TEST", error);

        ArgumentCaptor<StateTransitionHistory> captor = ArgumentCaptor.forClass(StateTransitionHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().errorMessage()).hasSize(512);
    }

    @Test
    void recordsWithMissingRequestContextDoesNotThrow() {
        RequestContext.clear();
        // 不应抛异常，actor/tenant/trace 可为 null
        recorder.record("entity", "id", null, "STATE", "REASON", null);

        ArgumentCaptor<StateTransitionHistory> captor = ArgumentCaptor.forClass(StateTransitionHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().traceId()).isNull();
        assertThat(captor.getValue().actor()).isNull();
    }

    @Test
    void runtimeExceptionInRepositoryDoesNotPropagate() {
        // 模拟 NPE / 序列化异常这类非业务异常：吞掉并 warn 日志
        when(repository.save(any())).thenThrow(new RuntimeException("unexpected"));

        // 不应抛 — recorder 内部 try-catch 兜底
        recorder.record("entity", "id", null, "STATE", "REASON", null);
    }

    @Test
    void dataAccessExceptionPropagatesToBusinessTx() {
        // DataAccessException 必须 propagate，让业务事务回滚
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        try {
            recorder.record("entity", "id", null, "STATE", "REASON", null);
            // 应该已抛出
            assertThat(false).as("DataAccessException 必须 propagate").isTrue();
        } catch (DataAccessResourceFailureException expected) {
            // OK
        }
    }
}
