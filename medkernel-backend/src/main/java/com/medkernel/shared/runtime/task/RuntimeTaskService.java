package com.medkernel.shared.runtime.task;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.PayloadDescriptor;
import com.medkernel.shared.observability.PayloadRef;
import com.medkernel.shared.observability.PayloadStoragePort;
import com.medkernel.shared.observability.StateTransitionRecorder;

/**
 * SYS-05 运行任务编排服务。
 */
@Service
public class RuntimeTaskService {

    private static final String TARGET_TYPE = "sys_task";
    private static final String DEAD_LETTER_TARGET_TYPE = "sys_task_dead_letter";
    private static final String SYSTEM_ACTOR = "system";
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final int MAX_RETRIES_LIMIT = 10;

    private final RuntimeTaskRepository repository;
    private final RuntimeTaskDeadLetterRepository deadLetterRepository;
    private final PayloadStoragePort payloadStorage;
    private final RuntimeTaskExecutorPort executor;
    private final AuditRecorder auditRecorder;
    private final StateTransitionRecorder transitions;
    private final ObjectMapper objectMapper;

    public RuntimeTaskService(RuntimeTaskRepository repository,
                              RuntimeTaskDeadLetterRepository deadLetterRepository,
                              PayloadStoragePort payloadStorage,
                              RuntimeTaskExecutorPort executor,
                              AuditRecorder auditRecorder,
                              StateTransitionRecorder transitions,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.deadLetterRepository = deadLetterRepository;
        this.payloadStorage = payloadStorage;
        this.executor = executor;
        this.auditRecorder = auditRecorder;
        this.transitions = transitions;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交运行任务。
     *
     * @param request 提交请求
     * @return 当前任务状态
     */
    @Transactional
    public RuntimeTaskResponse submit(RuntimeTaskSubmitRequest request) {
        validate(request);
        OrgScope scope = currentTenantScope();
        String taskId = "task-" + UUID.randomUUID();
        String actor = RequestContext.currentUserId().orElse(SYSTEM_ACTOR);
        Instant now = Instant.now();
        PayloadRef payloadRef = storePayload(scope.tenantId(), taskId, request);
        RuntimeTaskStatus initialStatus = request.mode() == RuntimeTaskMode.ASYNC
            ? RuntimeTaskStatus.UNREAD
            : RuntimeTaskStatus.PROCESSING;
        RuntimeTaskRecord saved = repository.save(new RuntimeTaskRecord(
            null,
            taskId,
            scope.tenantId(),
            orgPath(scope),
            request.mode().name(),
            initialStatus.name(),
            request.taskType(),
            payloadRef.storageType(),
            payloadRef.uri(),
            payloadRef.digest(),
            payloadRef.sizeBytes(),
            initialTotal(request),
            0,
            0,
            0,
            0,
            maxRetries(request),
            null,
            null,
            null,
            null,
            "[]",
            initialMessage(request.mode()),
            null,
            RequestContext.currentTraceId(),
            initialStatus == RuntimeTaskStatus.PROCESSING ? now : null,
            null,
            now,
            actor,
            now,
            actor
        ));
        transitions.record(TARGET_TYPE, saved.taskId(), null, saved.status(), "TASK_SUBMITTED", null);
        auditRecorder.record(auditCommand(AuditAction.CREATE, saved, null, "运行任务已提交"));

        if (request.mode() == RuntimeTaskMode.ASYNC) {
            return toResponse(saved);
        }
        RuntimeTaskRecord persisted = executeAndPersist(saved, payloadRef, request.mode(), request.items().size(),
            actor, saved, RuntimeTaskStatus.PROCESSING.name(), "运行任务执行结束");
        return toResponse(persisted);
    }

    /**
     * 按租户查询任务状态。
     *
     * @param taskId 任务 ID
     * @return 任务状态
     */
    @Transactional(readOnly = true)
    public RuntimeTaskResponse getTask(String taskId) {
        RuntimeTaskRecord record = findTaskForCurrentTenant(taskId);
        return toResponse(record);
    }

    /**
     * 对失败、升级或未连接的任务执行一次人工重试。
     *
     * @param taskId 任务 ID
     * @return 重试后的任务状态
     */
    @Transactional
    public RuntimeTaskResponse retryTask(String taskId) {
        RuntimeTaskRecord record = findTaskForCurrentTenant(taskId);
        if (!isRetryableTerminal(record)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "只有失败、升级或未连接任务可以重试");
        }
        String actor = RequestContext.currentUserId().orElse(SYSTEM_ACTOR);
        Instant now = Instant.now();
        int retryCount = number(record.retryCount()) + 1;
        RuntimeTaskRecord processing = repository.save(record.withProcessingForRetry(retryCount, now, actor));
        transitions.record(TARGET_TYPE, processing.taskId(), record.status(), RuntimeTaskStatus.PROCESSING.name(),
            "TASK_RETRY", null);
        auditRecorder.record(auditCommand(AuditAction.UPDATE, processing, record, "运行任务开始重试"));

        PayloadRef payloadRef = payloadRef(processing);
        RuntimeTaskExecutionResult result = normalizedResult(
            executor.execute(commandFor(processing, payloadRef, number(processing.totalCount()))),
            RuntimeTaskMode.valueOf(processing.mode()),
            number(processing.totalCount())
        );
        RuntimeTaskRecord terminal = processing.withRetryTerminal(result, failureDetails(result.failures()),
            Instant.now(), actor);
        if (shouldMoveToDeadLetter(terminal)) {
            RuntimeTaskRecord dead = moveToDeadLetter(terminal, actor);
            return toResponse(dead);
        }

        RuntimeTaskRecord persisted = repository.save(terminal);
        transitions.record(TARGET_TYPE, persisted.taskId(), RuntimeTaskStatus.PROCESSING.name(),
            persisted.status(), transitionReason(result), null);
        auditRecorder.record(auditCommand(AuditAction.EXECUTE, persisted, processing, "运行任务重试结束"));
        return toResponse(persisted);
    }

    /**
     * 人工回放死信任务，创建新的运行任务并保留原失败证据。
     *
     * @param deadLetterId 死信 ID
     * @return 新任务执行后的状态
     */
    @Transactional
    public RuntimeTaskResponse replayDeadLetter(String deadLetterId) {
        if (deadLetterId == null || deadLetterId.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "deadLetterId 不能为空");
        }
        OrgScope scope = currentTenantScope();
        RuntimeTaskDeadLetterRecord deadLetter = deadLetterRepository
            .findByTenantIdAndDeadLetterId(scope.tenantId(), deadLetterId.trim())
            .orElseThrow(() -> ApiException.notFound("运行任务死信"));
        if (deadLetter.replayTaskId() != null && !deadLetter.replayTaskId().isBlank()) {
            return getTask(deadLetter.replayTaskId());
        }

        String actor = RequestContext.currentUserId().orElse(SYSTEM_ACTOR);
        Instant now = Instant.now();
        String replayTaskId = "task-" + UUID.randomUUID();
        RuntimeTaskRecord replay = repository.save(new RuntimeTaskRecord(
            null,
            replayTaskId,
            deadLetter.tenantId(),
            deadLetter.orgPath(),
            deadLetter.taskMode(),
            RuntimeTaskStatus.PROCESSING.name(),
            deadLetter.taskType(),
            deadLetter.payloadStorageType(),
            deadLetter.payloadUri(),
            deadLetter.payloadDigest(),
            deadLetter.payloadSizeBytes(),
            Math.max(1, number(deadLetter.totalCount())),
            0,
            0,
            0,
            0,
            DEFAULT_MAX_RETRIES,
            null,
            null,
            null,
            deadLetter.taskId(),
            "[]",
            "死信任务人工回放开始执行",
            null,
            RequestContext.currentTraceId(),
            now,
            null,
            now,
            actor,
            now,
            actor
        ).withReplaySource(deadLetter.taskId()));
        transitions.record(TARGET_TYPE, replay.taskId(), null, replay.status(), "DEAD_LETTER_REPLAY", null);
        auditRecorder.record(auditCommand(AuditAction.CREATE, replay, null, "死信任务已创建回放任务"));

        RuntimeTaskRecord persisted = executeAndPersist(replay, payloadRef(replay), RuntimeTaskMode.valueOf(replay.mode()),
            Math.max(1, number(replay.totalCount())), actor, replay, RuntimeTaskStatus.PROCESSING.name(),
            "死信任务回放执行结束");
        RuntimeTaskDeadLetterRecord updatedDeadLetter = deadLetterRepository.save(deadLetter.withReplay(
            Instant.now(), actor, persisted.taskId()));
        auditRecorder.record(deadLetterAuditCommand(AuditAction.UPDATE, updatedDeadLetter, deadLetter,
            "运行任务死信已回放"));
        return toResponse(persisted);
    }

    private RuntimeTaskRecord executeAndPersist(RuntimeTaskRecord record,
                                                PayloadRef payloadRef,
                                                RuntimeTaskMode mode,
                                                int batchItemCount,
                                                String actor,
                                                RuntimeTaskRecord before,
                                                String previousStatus,
                                                String auditSummary) {
        RuntimeTaskExecutionResult result = normalizedResult(
            executor.execute(commandFor(record, payloadRef, batchItemCount)),
            mode,
            batchItemCount
        );
        RuntimeTaskRecord terminal = record.withTerminalResult(result, failureDetails(result.failures()),
            Instant.now(), actor);
        RuntimeTaskRecord persisted = repository.save(terminal);
        transitions.record(TARGET_TYPE, persisted.taskId(), previousStatus, persisted.status(),
            transitionReason(result), null);
        auditRecorder.record(auditCommand(AuditAction.EXECUTE, persisted, before, auditSummary));
        return persisted;
    }

    private RuntimeTaskExecutionCommand commandFor(RuntimeTaskRecord record, PayloadRef payloadRef, int batchItemCount) {
        return new RuntimeTaskExecutionCommand(
            record.taskId(),
            record.tenantId(),
            record.orgPath(),
            RuntimeTaskMode.valueOf(record.mode()),
            record.taskType(),
            payloadRef,
            batchItemCount,
            record.traceId()
        );
    }

    private RuntimeTaskRecord moveToDeadLetter(RuntimeTaskRecord terminal, String actor) {
        Instant now = Instant.now();
        String deadLetterId = "dead-" + UUID.randomUUID();
        RuntimeTaskDeadLetterRecord deadLetter = deadLetterRepository.save(new RuntimeTaskDeadLetterRecord(
            null,
            deadLetterId,
            terminal.tenantId(),
            terminal.orgPath(),
            terminal.taskId(),
            terminal.mode(),
            terminal.taskType(),
            terminal.payloadStorageType(),
            terminal.payloadUri(),
            terminal.payloadDigest(),
            terminal.payloadSizeBytes(),
            terminal.totalCount(),
            terminal.retryCount(),
            terminal.failureDetailsJson(),
            terminal.errorCode(),
            terminal.message(),
            terminal.traceId(),
            now,
            actor,
            now,
            actor,
            null,
            null,
            null
        ));
        RuntimeTaskRecord dead = repository.save(terminal.withDeadLetter(deadLetter.deadLetterId(),
            number(terminal.retryCount()), now, actor));
        transitions.record(TARGET_TYPE, dead.taskId(), RuntimeTaskStatus.PROCESSING.name(),
            RuntimeTaskStatus.DEAD_LETTER.name(), "RETRY_EXHAUSTED", null);
        auditRecorder.record(auditCommand(AuditAction.UPDATE, dead, terminal, "运行任务重试耗尽进入死信"));
        auditRecorder.record(deadLetterAuditCommand(AuditAction.CREATE, deadLetter, null, "运行任务死信已创建"));
        return dead;
    }

    private RuntimeTaskRecord findTaskForCurrentTenant(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "taskId 不能为空");
        }
        OrgScope scope = currentTenantScope();
        return repository.findByTenantIdAndTaskId(scope.tenantId(), taskId.trim())
            .orElseThrow(() -> ApiException.notFound("运行任务"));
    }

    private OrgScope currentTenantScope() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope;
    }

    private void validate(RuntimeTaskSubmitRequest request) {
        if (request == null || request.mode() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "任务模式不能为空");
        }
        if (request.taskType() == null || request.taskType().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "任务类型不能为空");
        }
        if (request.maxRetries() != null && (request.maxRetries() < 0 || request.maxRetries() > MAX_RETRIES_LIMIT)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "任务最大重试次数必须在 0 到 10 之间");
        }
        if (request.payloadJson() != null
            && request.payloadJson().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "任务 payload 超过 1MB 上限");
        }
        if (request.mode() == RuntimeTaskMode.BATCH && request.items().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "批量模式必须提交至少一个批量项");
        }
    }

    private PayloadRef storePayload(String tenantId, String taskId, RuntimeTaskSubmitRequest request) {
        byte[] payload = payloadBytes(request);
        return payloadStorage.put(
            new PayloadDescriptor(tenantId, TARGET_TYPE, taskId, "application/json"),
            payload
        );
    }

    private byte[] payloadBytes(RuntimeTaskSubmitRequest request) {
        try {
            return objectMapper.writeValueAsBytes(new RuntimeTaskPayloadSnapshot(
                request.mode(), request.taskType(), request.payloadJson(), request.items(), request.maxRetries()));
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "任务 payload 无法序列化", e);
        }
    }

    private int initialTotal(RuntimeTaskSubmitRequest request) {
        return request.mode() == RuntimeTaskMode.BATCH ? request.items().size() : 1;
    }

    private int maxRetries(RuntimeTaskSubmitRequest request) {
        return request.maxRetries() == null ? DEFAULT_MAX_RETRIES : request.maxRetries();
    }

    private RuntimeTaskExecutionResult normalizedResult(RuntimeTaskExecutionResult result,
                                                        RuntimeTaskMode mode,
                                                        int submittedCount) {
        if (result == null) {
            return RuntimeTaskExecutionResult.failed("RUNTIME_EXECUTOR_EMPTY", "执行器未返回结果，任务按失败处理");
        }
        if (result.status() == RuntimeTaskStatus.UNREAD || result.status() == RuntimeTaskStatus.PROCESSING) {
            return RuntimeTaskExecutionResult.failed("RUNTIME_EXECUTOR_NON_TERMINAL", "执行器返回非终态，任务按失败处理");
        }
        if (result.status() == RuntimeTaskStatus.DEAD_LETTER) {
            return RuntimeTaskExecutionResult.failed("RUNTIME_EXECUTOR_DEAD_LETTER",
                "执行器不得直接写死信状态，任务按失败处理");
        }
        if (mode == RuntimeTaskMode.BATCH) {
            return normalizedBatchResult(result, submittedCount);
        }
        return result;
    }

    private RuntimeTaskExecutionResult normalizedBatchResult(RuntimeTaskExecutionResult result, int submittedCount) {
        int total = Math.max(1, submittedCount);
        return switch (result.status()) {
            case COMPLETED -> new RuntimeTaskExecutionResult(
                RuntimeTaskStatus.COMPLETED, result.message(), result.errorCode(), total, total, 0, 0, List.of());
            case FAILED -> new RuntimeTaskExecutionResult(
                RuntimeTaskStatus.FAILED, result.message(), result.errorCode(), total, 0, total, 0,
                batchFailures(result));
            case ESCALATED -> new RuntimeTaskExecutionResult(
                RuntimeTaskStatus.ESCALATED, result.message(), result.errorCode(), total, 0, total, total,
                batchFailures(result));
            case NOT_CONNECTED -> new RuntimeTaskExecutionResult(
                RuntimeTaskStatus.NOT_CONNECTED, result.message(), result.errorCode(), total, 0, total, total,
                batchFailures(result));
            case PARTIAL_SUCCESS -> normalizedPartialBatchResult(result, total);
            case UNREAD, PROCESSING, DEAD_LETTER -> result;
        };
    }

    private RuntimeTaskExecutionResult normalizedPartialBatchResult(RuntimeTaskExecutionResult result, int total) {
        int failureCount = clamp(result.failureCount(), 0, total);
        if (failureCount == 0 && !result.failures().isEmpty()) {
            failureCount = Math.min(result.failures().size(), total);
        }
        int successCount = clamp(result.successCount(), 0, total - failureCount);
        int retryableCount = clamp(result.retryableCount(), 0, failureCount);
        return new RuntimeTaskExecutionResult(
            RuntimeTaskStatus.PARTIAL_SUCCESS,
            result.message(),
            result.errorCode(),
            total,
            successCount,
            failureCount,
            retryableCount,
            result.failures()
        );
    }

    private List<RuntimeTaskFailureItem> batchFailures(RuntimeTaskExecutionResult result) {
        if (!result.failures().isEmpty()) {
            return result.failures();
        }
        return List.of(new RuntimeTaskFailureItem(
            "batch",
            result.errorCode() == null || result.errorCode().isBlank() ? "BATCH_FAILED" : result.errorCode(),
            result.message(),
            result.status() == RuntimeTaskStatus.ESCALATED || result.status() == RuntimeTaskStatus.NOT_CONNECTED
        ));
    }

    private String failureDetails(List<RuntimeTaskFailureItem> failures) {
        try {
            return objectMapper.writeValueAsString(failures == null ? List.of() : failures);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "任务失败明细无法序列化", e);
        }
    }

    private RuntimeTaskResponse toResponse(RuntimeTaskRecord record) {
        return new RuntimeTaskResponse(
            record.taskId(),
            RuntimeTaskMode.valueOf(record.mode()),
            RuntimeTaskStatus.valueOf(record.status()),
            record.taskType(),
            number(record.totalCount()),
            number(record.successCount()),
            number(record.failureCount()),
            number(record.retryableCount()),
            number(record.retryCount()),
            number(record.maxRetries()),
            record.nextAttemptAt(),
            record.deadLetterId(),
            record.replayedFromTaskId(),
            parseFailures(record.failureDetailsJson()),
            record.message(),
            record.errorCode(),
            record.traceId(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    private List<RuntimeTaskFailureItem> parseFailures(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RuntimeTaskFailureItem>>() {});
        } catch (JsonProcessingException e) {
            return List.of(new RuntimeTaskFailureItem("task", "FAILURE_DETAIL_UNREADABLE",
                "失败明细无法解析，请查看审计链路", false));
        }
    }

    private AuditRecordCommand auditCommand(AuditAction action,
                                            RuntimeTaskRecord after,
                                            RuntimeTaskRecord before,
                                            String summary) {
        return new AuditRecordCommand(
            action,
            TARGET_TYPE,
            after.taskId(),
            summary,
            before == null ? null : auditSnapshot(before),
            auditSnapshot(after),
            null
        );
    }

    private AuditRecordCommand deadLetterAuditCommand(AuditAction action,
                                                      RuntimeTaskDeadLetterRecord after,
                                                      RuntimeTaskDeadLetterRecord before,
                                                      String summary) {
        return new AuditRecordCommand(
            action,
            DEAD_LETTER_TARGET_TYPE,
            after.deadLetterId(),
            summary,
            before == null ? null : deadLetterAuditSnapshot(before),
            deadLetterAuditSnapshot(after),
            null
        );
    }

    private Map<String, Object> auditSnapshot(RuntimeTaskRecord record) {
        return Map.ofEntries(
            Map.entry("taskId", record.taskId()),
            Map.entry("tenantId", record.tenantId()),
            Map.entry("mode", record.mode()),
            Map.entry("status", record.status()),
            Map.entry("taskType", record.taskType()),
            Map.entry("totalCount", number(record.totalCount())),
            Map.entry("successCount", number(record.successCount())),
            Map.entry("failureCount", number(record.failureCount())),
            Map.entry("retryableCount", number(record.retryableCount())),
            Map.entry("retryCount", number(record.retryCount())),
            Map.entry("maxRetries", number(record.maxRetries())),
            Map.entry("deadLetterId", record.deadLetterId() == null ? "" : record.deadLetterId()),
            Map.entry("replayedFromTaskId", record.replayedFromTaskId() == null ? "" : record.replayedFromTaskId()),
            Map.entry("errorCode", record.errorCode() == null ? "" : record.errorCode()),
            Map.entry("traceId", record.traceId() == null ? "" : record.traceId())
        );
    }

    private Map<String, Object> deadLetterAuditSnapshot(RuntimeTaskDeadLetterRecord record) {
        return Map.ofEntries(
            Map.entry("deadLetterId", record.deadLetterId()),
            Map.entry("tenantId", record.tenantId()),
            Map.entry("taskId", record.taskId()),
            Map.entry("taskMode", record.taskMode()),
            Map.entry("taskType", record.taskType()),
            Map.entry("retryCount", number(record.retryCount())),
            Map.entry("errorCode", record.errorCode() == null ? "" : record.errorCode()),
            Map.entry("replayTaskId", record.replayTaskId() == null ? "" : record.replayTaskId()),
            Map.entry("traceId", record.traceId() == null ? "" : record.traceId())
        );
    }

    private boolean shouldMoveToDeadLetter(RuntimeTaskRecord record) {
        RuntimeTaskStatus status = RuntimeTaskStatus.valueOf(record.status());
        return isFailureLike(status) && number(record.retryCount()) >= number(record.maxRetries());
    }

    private boolean isRetryableTerminal(RuntimeTaskRecord record) {
        RuntimeTaskStatus status = RuntimeTaskStatus.valueOf(record.status());
        return isFailureLike(status);
    }

    private static boolean isFailureLike(RuntimeTaskStatus status) {
        return status == RuntimeTaskStatus.FAILED
            || status == RuntimeTaskStatus.ESCALATED
            || status == RuntimeTaskStatus.NOT_CONNECTED
            || status == RuntimeTaskStatus.PARTIAL_SUCCESS;
    }

    private static PayloadRef payloadRef(RuntimeTaskRecord record) {
        return new PayloadRef(
            record.payloadStorageType(),
            record.payloadDigest(),
            record.payloadUri(),
            record.payloadSizeBytes(),
            "application/json"
        );
    }

    private static String transitionReason(RuntimeTaskExecutionResult result) {
        return result.errorCode() == null || result.errorCode().isBlank()
            ? result.status().name()
            : result.errorCode();
    }

    private static String initialMessage(RuntimeTaskMode mode) {
        return switch (mode) {
            case ASYNC -> "异步任务已入队，等待 worker 消费";
            case BATCH -> "批量任务开始执行";
            case OFFLINE -> "离线任务开始执行";
            case ONLINE -> "在线任务开始执行";
        };
    }

    private static int number(Integer value) {
        return value == null ? 0 : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String orgPath(OrgScope scope) {
        if (scope == null) {
            return null;
        }
        String path = Stream.of(
                scope.tenantId(), scope.groupId(), scope.hospitalId(), scope.campusId(),
                scope.siteId(), scope.departmentId(), scope.specialtyId())
            .filter(value -> value != null && !value.isBlank())
            .reduce((left, right) -> left + "/" + right)
            .orElse(null);
        return path == null || path.isBlank() ? null : path;
    }

    private record RuntimeTaskPayloadSnapshot(
        RuntimeTaskMode mode,
        String taskType,
        String payloadJson,
        List<RuntimeTaskBatchItem> items,
        Integer maxRetries
    ) {
    }
}
