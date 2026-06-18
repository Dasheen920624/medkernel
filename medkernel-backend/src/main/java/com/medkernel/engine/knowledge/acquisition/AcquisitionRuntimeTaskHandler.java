package com.medkernel.engine.knowledge.acquisition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.observability.PayloadStoragePort;
import com.medkernel.shared.runtime.task.RuntimeTaskBatchItem;
import com.medkernel.shared.runtime.task.RuntimeTaskExecutionCommand;
import com.medkernel.shared.runtime.task.RuntimeTaskExecutionResult;
import com.medkernel.shared.runtime.task.RuntimeTaskFailureItem;
import com.medkernel.shared.runtime.task.RuntimeTaskHandler;
import com.medkernel.shared.runtime.task.RuntimeTaskStatus;

/**
 * AIK-STD-14 公域资料调度任务执行器，复用 SYS-05 批量任务和既有获取编排。
 */
@Component
public class AcquisitionRuntimeTaskHandler implements RuntimeTaskHandler {

    public static final String TASK_TYPE = "KNOWLEDGE_ACQUISITION_DISCOVERY";

    private static final String ERROR_CODE = "KNOWLEDGE_ACQUISITION_FAILED";
    private static final String PAYLOAD_ERROR_CODE = "KNOWLEDGE_ACQUISITION_PAYLOAD_INVALID";

    private final PayloadStoragePort payloadStorage;
    private final AcquisitionOrchestrationService orchestration;
    private final ObjectMapper objectMapper;

    public AcquisitionRuntimeTaskHandler(PayloadStoragePort payloadStorage,
                                         AcquisitionOrchestrationService orchestration,
                                         ObjectMapper objectMapper) {
        this.payloadStorage = payloadStorage;
        this.orchestration = orchestration;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String taskType) {
        return TASK_TYPE.equals(taskType);
    }

    @Override
    public RuntimeTaskExecutionResult execute(RuntimeTaskExecutionCommand command) {
        RuntimeTaskPayload payload;
        try {
            payload = readPayload(command);
        } catch (ApiException exception) {
            return RuntimeTaskExecutionResult.failed(PAYLOAD_ERROR_CODE, exception.getMessage());
        }
        if (payload.items().isEmpty()) {
            return RuntimeTaskExecutionResult.failed(PAYLOAD_ERROR_CODE, "公域资料调度任务没有可执行来源");
        }

        int successCount = 0;
        List<RuntimeTaskFailureItem> failures = new ArrayList<>();
        for (RuntimeTaskBatchItem item : payload.items()) {
            try {
                KnowledgeAcquisitionRunRequest request =
                    objectMapper.readValue(item.payloadJson(), KnowledgeAcquisitionRunRequest.class);
                KnowledgeAcquisitionRunResponse response = orchestration.runScheduled(request);
                if (isSuccessful(response.status())) {
                    successCount++;
                    continue;
                }
                failures.add(toFailure(item.itemId(), response));
            } catch (ApiException exception) {
                failures.add(new RuntimeTaskFailureItem(item.itemId(), PAYLOAD_ERROR_CODE,
                    exception.getMessage(), false));
            } catch (RuntimeException | IOException exception) {
                failures.add(new RuntimeTaskFailureItem(item.itemId(), ERROR_CODE,
                    exception.getMessage(), true));
            }
        }

        int total = payload.items().size();
        if (failures.isEmpty()) {
            return new RuntimeTaskExecutionResult(
                RuntimeTaskStatus.COMPLETED, "公域资料调度任务全部完成", null, total, total, 0, 0, List.of());
        }
        if (successCount == 0) {
            return new RuntimeTaskExecutionResult(
                RuntimeTaskStatus.FAILED, "公域资料调度任务全部失败", ERROR_CODE, total, 0, failures.size(),
                retryableCount(failures), failures);
        }
        return RuntimeTaskExecutionResult.partialSuccess(
            "公域资料调度任务部分成功", total, successCount, failures.size(), failures);
    }

    private RuntimeTaskPayload readPayload(RuntimeTaskExecutionCommand command) {
        try {
            byte[] bytes = payloadStorage.get(command.payloadRef());
            RuntimeTaskPayload payload = objectMapper.readValue(bytes, RuntimeTaskPayload.class);
            return payload.items() == null ? new RuntimeTaskPayload(List.of()) : payload;
        } catch (RuntimeException | IOException exception) {
            throw new ApiException(com.medkernel.shared.api.error.ErrorCode.VALIDATION_FAILED,
                "公域资料调度 payload 无法读取或解析", exception);
        }
    }

    private RuntimeTaskFailureItem toFailure(String itemId, KnowledgeAcquisitionRunResponse response) {
        boolean retryable = response.status() == KnowledgeAcquisitionRunStatus.FAILED;
        String message = response.failureReason() == null || response.failureReason().isBlank()
            ? response.status().name()
            : response.failureReason();
        return new RuntimeTaskFailureItem(itemId, ERROR_CODE, message, retryable);
    }

    private static boolean isSuccessful(KnowledgeAcquisitionRunStatus status) {
        return status == KnowledgeAcquisitionRunStatus.SUCCEEDED || status == KnowledgeAcquisitionRunStatus.DUPLICATE;
    }

    private static int retryableCount(List<RuntimeTaskFailureItem> failures) {
        return (int) failures.stream().filter(RuntimeTaskFailureItem::retryable).count();
    }

    private record RuntimeTaskPayload(List<RuntimeTaskBatchItem> items) {
        private RuntimeTaskPayload {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
