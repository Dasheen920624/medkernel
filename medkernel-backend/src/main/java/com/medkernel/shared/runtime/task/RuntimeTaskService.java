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
 * SYS-05 PR1 运行任务编排服务。
 */
@Service
public class RuntimeTaskService {

    private static final String TARGET_TYPE = "sys_task";
    private static final String SYSTEM_ACTOR = "system";
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;

    private final RuntimeTaskRepository repository;
    private final PayloadStoragePort payloadStorage;
    private final RuntimeTaskExecutorPort executor;
    private final AuditRecorder auditRecorder;
    private final StateTransitionRecorder transitions;
    private final ObjectMapper objectMapper;

    public RuntimeTaskService(RuntimeTaskRepository repository,
                              PayloadStoragePort payloadStorage,
                              RuntimeTaskExecutorPort executor,
                              AuditRecorder auditRecorder,
                              StateTransitionRecorder transitions,
                              ObjectMapper objectMapper) {
        this.repository = repository;
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
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
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
        RuntimeTaskExecutionCommand command = new RuntimeTaskExecutionCommand(
            saved.taskId(),
            saved.tenantId(),
            saved.orgPath(),
            request.mode(),
            saved.taskType(),
            payloadRef,
            request.items().size(),
            saved.traceId()
        );
        RuntimeTaskExecutionResult result = normalizedResult(executor.execute(command), request);
        RuntimeTaskRecord terminal = saved.withTerminalResult(result, failureDetails(result.failures()), Instant.now(), actor);
        RuntimeTaskRecord persisted = repository.save(terminal);
        transitions.record(TARGET_TYPE, persisted.taskId(), RuntimeTaskStatus.PROCESSING.name(),
            persisted.status(), transitionReason(result), null);
        auditRecorder.record(auditCommand(AuditAction.EXECUTE, persisted, saved, "运行任务执行结束"));
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
        if (taskId == null || taskId.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "taskId 不能为空");
        }
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        RuntimeTaskRecord record = repository.findByTenantIdAndTaskId(scope.tenantId(), taskId.trim())
            .orElseThrow(() -> ApiException.notFound("运行任务"));
        return toResponse(record);
    }

    private void validate(RuntimeTaskSubmitRequest request) {
        if (request == null || request.mode() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "任务模式不能为空");
        }
        if (request.taskType() == null || request.taskType().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "任务类型不能为空");
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
                request.mode(), request.taskType(), request.payloadJson(), request.items()));
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "任务 payload 无法序列化", e);
        }
    }

    private int initialTotal(RuntimeTaskSubmitRequest request) {
        return request.mode() == RuntimeTaskMode.BATCH ? request.items().size() : 1;
    }

    private RuntimeTaskExecutionResult normalizedResult(RuntimeTaskExecutionResult result, RuntimeTaskSubmitRequest request) {
        if (result == null) {
            return RuntimeTaskExecutionResult.failed("RUNTIME_EXECUTOR_EMPTY", "执行器未返回结果，任务按失败处理");
        }
        if (result.status() == RuntimeTaskStatus.UNREAD || result.status() == RuntimeTaskStatus.PROCESSING) {
            return RuntimeTaskExecutionResult.failed("RUNTIME_EXECUTOR_NON_TERMINAL", "执行器返回非终态，任务按失败处理");
        }
        if (request.mode() == RuntimeTaskMode.BATCH) {
            return normalizedBatchResult(result, request.items().size());
        }
        return result;
    }

    private RuntimeTaskExecutionResult normalizedBatchResult(RuntimeTaskExecutionResult result, int submittedCount) {
        int total = Math.max(1, submittedCount);
        return switch (result.status()) {
            case COMPLETED -> new RuntimeTaskExecutionResult(
                RuntimeTaskStatus.COMPLETED, result.message(), result.errorCode(), total, total, 0, 0, List.of());
            case FAILED -> new RuntimeTaskExecutionResult(
                RuntimeTaskStatus.FAILED, result.message(), result.errorCode(), total, 0, total, 0, batchFailures(result));
            case ESCALATED -> new RuntimeTaskExecutionResult(
                RuntimeTaskStatus.ESCALATED, result.message(), result.errorCode(), total, 0, total, total,
                batchFailures(result));
            case PARTIAL_SUCCESS -> normalizedPartialBatchResult(result, total);
            case UNREAD, PROCESSING -> result;
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
            result.status() == RuntimeTaskStatus.ESCALATED
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
            Map.entry("errorCode", record.errorCode() == null ? "" : record.errorCode()),
            Map.entry("traceId", record.traceId() == null ? "" : record.traceId())
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
        List<RuntimeTaskBatchItem> items
    ) {
    }
}
