package com.medkernel.shared.runtime.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.PayloadRef;
import com.medkernel.shared.observability.PayloadStoragePort;
import com.medkernel.shared.observability.StateTransitionRecorder;

class RuntimeTaskServiceTest {

    private final PayloadStoragePort payloadStorage = mock(PayloadStoragePort.class);
    private final RuntimeTaskRepository repository = mock(RuntimeTaskRepository.class);
    private final RuntimeTaskDeadLetterRepository deadLetterRepository = mock(RuntimeTaskDeadLetterRepository.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final StateTransitionRecorder transitions = mock(StateTransitionRecorder.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<RuntimeTaskRecord> latestRecord = new AtomicReference<>();
    private final AtomicReference<RuntimeTaskDeadLetterRecord> latestDeadLetter = new AtomicReference<>();
    private final AtomicLong idSequence = new AtomicLong(1);
    private final AtomicLong deadLetterIdSequence = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-runtime",
            new OrgScope("tenant-A", "group-A", "hospital-A", null, null, "dept-A", null),
            "tester"));
        when(payloadStorage.put(any(), any())).thenReturn(new PayloadRef(
            PayloadRef.STORAGE_INLINE,
            "sha256:payload",
            "db://mk_obs_payload_store/pl-runtime",
            42,
            "application/json"
        ));
        when(repository.save(any(RuntimeTaskRecord.class))).thenAnswer(invocation -> {
            RuntimeTaskRecord record = invocation.getArgument(0);
            RuntimeTaskRecord saved = record.id() == null ? record.withId(idSequence.getAndIncrement()) : record;
            latestRecord.set(saved);
            return saved;
        });
        when(repository.findByTenantIdAndTaskId(any(), any())).thenAnswer(invocation -> {
            RuntimeTaskRecord record = latestRecord.get();
            if (record == null) {
                return Optional.empty();
            }
            String tenantId = invocation.getArgument(0);
            String taskId = invocation.getArgument(1);
            return record.tenantId().equals(tenantId) && record.taskId().equals(taskId)
                ? Optional.of(record)
                : Optional.empty();
        });
        when(deadLetterRepository.save(any(RuntimeTaskDeadLetterRecord.class))).thenAnswer(invocation -> {
            RuntimeTaskDeadLetterRecord record = invocation.getArgument(0);
            RuntimeTaskDeadLetterRecord saved = record.id() == null
                ? record.withId(deadLetterIdSequence.getAndIncrement())
                : record;
            latestDeadLetter.set(saved);
            return saved;
        });
        when(deadLetterRepository.findByTenantIdAndDeadLetterId(any(), any())).thenAnswer(invocation -> {
            RuntimeTaskDeadLetterRecord record = latestDeadLetter.get();
            if (record == null) {
                return Optional.empty();
            }
            String tenantId = invocation.getArgument(0);
            String deadLetterId = invocation.getArgument(1);
            return record.tenantId().equals(tenantId) && record.deadLetterId().equals(deadLetterId)
                ? Optional.of(record)
                : Optional.empty();
        });
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void onlineTimeoutReturnsEscalatedWithoutThrowingAndAudits() {
        RuntimeTaskExecutorPort executor = command ->
            RuntimeTaskExecutionResult.timeout("ONLINE_TIMEOUT", "同步任务超时，主流程未阻断");
        RuntimeTaskService service = serviceWith(executor);

        RuntimeTaskResponse response = service.submit(new RuntimeTaskSubmitRequest(
            RuntimeTaskMode.ONLINE,
            "RUNTIME_SELF_CHECK",
            "{\"source\":\"unit\"}",
            List.of()
        ));

        assertThat(response.status()).isEqualTo(RuntimeTaskStatus.ESCALATED);
        assertThat(response.message()).contains("主流程未阻断");
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.failureCount()).isEqualTo(1);
        verify(transitions).record("sys_task", response.taskId(), "PROCESSING", "ESCALATED",
            "ONLINE_TIMEOUT", null);
        verify(auditRecorder).record(argThat(command ->
            command.action() == AuditAction.EXECUTE
                && command.targetType().equals("sys_task")
                && command.targetId().equals(response.taskId())
                && String.valueOf(command.after()).contains("ESCALATED")
                && String.valueOf(command.after()).contains("ONLINE_TIMEOUT")));
    }

    @Test
    void asyncSubmitPersistsUnreadTaskAndStatusCanBePolled() {
        RuntimeTaskExecutorPort executor = mock(RuntimeTaskExecutorPort.class);
        RuntimeTaskService service = serviceWith(executor);

        RuntimeTaskResponse submitted = service.submit(new RuntimeTaskSubmitRequest(
            RuntimeTaskMode.ASYNC,
            "RUNTIME_SELF_CHECK",
            "{\"source\":\"unit\"}",
            List.of()
        ));
        RuntimeTaskResponse polled = service.getTask(submitted.taskId());

        assertThat(submitted.status()).isEqualTo(RuntimeTaskStatus.UNREAD);
        assertThat(polled.taskId()).isEqualTo(submitted.taskId());
        assertThat(polled.status()).isEqualTo(RuntimeTaskStatus.UNREAD);
        verify(executor, never()).execute(any());
    }

    @Test
    void batchPartialSuccessPersistsCountsAndRetryableFailures() {
        RuntimeTaskExecutorPort executor = command -> RuntimeTaskExecutionResult.partialSuccess(
            "批量任务部分成功",
            3,
            2,
            1,
            List.of(new RuntimeTaskFailureItem("item-2", "VALIDATION_FAILED", "数据缺失", true))
        );
        RuntimeTaskService service = serviceWith(executor);

        RuntimeTaskResponse response = service.submit(new RuntimeTaskSubmitRequest(
            RuntimeTaskMode.BATCH,
            "RUNTIME_SELF_CHECK",
            "{\"source\":\"unit\"}",
            List.of(
                new RuntimeTaskBatchItem("item-1", "{\"ok\":true}"),
                new RuntimeTaskBatchItem("item-2", "{\"ok\":false}"),
                new RuntimeTaskBatchItem("item-3", "{\"ok\":true}")
            )
        ));

        assertThat(response.status()).isEqualTo(RuntimeTaskStatus.PARTIAL_SUCCESS);
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.retryableCount()).isEqualTo(1);
        assertThat(response.failures()).extracting(RuntimeTaskFailureItem::itemId).containsExactly("item-2");
        assertThat(latestRecord.get().finishedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void batchCompletedResultUsesSubmittedItemCount() {
        RuntimeTaskService service = serviceWith(command -> RuntimeTaskExecutionResult.completed("批量任务全部完成"));

        RuntimeTaskResponse response = service.submit(new RuntimeTaskSubmitRequest(
            RuntimeTaskMode.BATCH,
            "RUNTIME_SELF_CHECK",
            "{\"source\":\"unit\"}",
            List.of(
                new RuntimeTaskBatchItem("item-1", "{\"ok\":true}"),
                new RuntimeTaskBatchItem("item-2", "{\"ok\":true}"),
                new RuntimeTaskBatchItem("item-3", "{\"ok\":true}")
            )
        ));

        assertThat(response.status()).isEqualTo(RuntimeTaskStatus.COMPLETED);
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(3);
        assertThat(response.failureCount()).isZero();
        assertThat(response.retryableCount()).isZero();
    }

    @Test
    void offlineModeRunsWithLocalExecutorAndNoExternalDependency() {
        RuntimeTaskService service = serviceWith(command -> RuntimeTaskExecutionResult.completed("离线任务本地完成"));

        RuntimeTaskResponse response = service.submit(new RuntimeTaskSubmitRequest(
            RuntimeTaskMode.OFFLINE,
            "RUNTIME_SELF_CHECK",
            "{\"source\":\"offline\"}",
            List.of()
        ));

        assertThat(response.mode()).isEqualTo(RuntimeTaskMode.OFFLINE);
        assertThat(response.status()).isEqualTo(RuntimeTaskStatus.COMPLETED);
        assertThat(response.message()).contains("离线任务本地完成");
        verify(auditRecorder).record(argThat(command ->
            command.action() == AuditAction.EXECUTE
                && command.targetType().equals("sys_task")
                && String.valueOf(command.after()).contains("OFFLINE")));
    }

    @Test
    void notConnectedResultIsPersistedHonestlyWithoutSuccess() {
        RuntimeTaskService service = serviceWith(command ->
            RuntimeTaskExecutionResult.notConnected("NOT_CONNECTED", "外部执行器未连接，任务未执行"));

        RuntimeTaskResponse response = service.submit(new RuntimeTaskSubmitRequest(
            RuntimeTaskMode.ONLINE,
            "EXTERNAL_SYNC",
            "{\"source\":\"unit\"}",
            List.of()
        ));

        assertThat(response.status()).isEqualTo(RuntimeTaskStatus.NOT_CONNECTED);
        assertThat(response.successCount()).isZero();
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.errorCode()).isEqualTo("NOT_CONNECTED");
        assertThat(response.message()).contains("未连接");
    }

    @Test
    void retryExhaustionMovesTaskToDeadLetterAndReplayCreatesNewCompletedTask() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeTaskService service = serviceWith(command -> attempts.incrementAndGet() < 3
            ? RuntimeTaskExecutionResult.failed("DOWNSTREAM_FAILED", "下游失败")
            : RuntimeTaskExecutionResult.completed("人工回放成功"));

        RuntimeTaskResponse failed = service.submit(new RuntimeTaskSubmitRequest(
            RuntimeTaskMode.ONLINE,
            "EXTERNAL_SYNC",
            "{\"source\":\"unit\"}",
            List.of(),
            1
        ));
        RuntimeTaskResponse dead = service.retryTask(failed.taskId());
        RuntimeTaskResponse replayed = service.replayDeadLetter(dead.deadLetterId());

        assertThat(failed.status()).isEqualTo(RuntimeTaskStatus.FAILED);
        assertThat(dead.status()).isEqualTo(RuntimeTaskStatus.DEAD_LETTER);
        assertThat(dead.retryCount()).isEqualTo(1);
        assertThat(dead.deadLetterId()).isNotBlank();
        assertThat(replayed.status()).isEqualTo(RuntimeTaskStatus.COMPLETED);
        assertThat(replayed.replayedFromTaskId()).isEqualTo(failed.taskId());
        verify(deadLetterRepository).save(argThat(record ->
            record.deadLetterId().equals(dead.deadLetterId())
                && replayed.taskId().equals(record.replayTaskId())));
    }

    private RuntimeTaskService serviceWith(RuntimeTaskExecutorPort executor) {
        return new RuntimeTaskService(repository, deadLetterRepository, payloadStorage, executor,
            auditRecorder, transitions, objectMapper);
    }
}
