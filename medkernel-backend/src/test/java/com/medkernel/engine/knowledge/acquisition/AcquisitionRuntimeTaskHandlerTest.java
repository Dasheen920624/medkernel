package com.medkernel.engine.knowledge.acquisition;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.knowledge.parsing.DocumentFormat;
import com.medkernel.shared.observability.PayloadRef;
import com.medkernel.shared.observability.PayloadStoragePort;
import com.medkernel.shared.runtime.task.RuntimeTaskExecutionCommand;
import com.medkernel.shared.runtime.task.RuntimeTaskExecutionResult;
import com.medkernel.shared.runtime.task.RuntimeTaskMode;
import com.medkernel.shared.runtime.task.RuntimeTaskStatus;

class AcquisitionRuntimeTaskHandlerTest {

    private final PayloadStoragePort payloadStorage = mock(PayloadStoragePort.class);
    private final AcquisitionOrchestrationService orchestration = mock(AcquisitionOrchestrationService.class);
    private final AcquisitionRuntimeTaskHandler handler =
        new AcquisitionRuntimeTaskHandler(payloadStorage, orchestration, new ObjectMapper());

    @Test
    void executesBatchItemsThroughScheduledAcquisitionPipelineAndReportsPartialFailures() {
        PayloadRef ref = new PayloadRef(PayloadRef.STORAGE_INLINE, "sha256:payload", "db://payload", 512,
            "application/json");
        when(payloadStorage.get(ref)).thenReturn("""
            {
              "items": [
                {
                  "itemId": "NHC-HTN",
                  "payloadJson": "{\\"sourceCode\\":\\"NHC-HTN\\",\\"url\\":\\"https://guideline.example.org/htn.txt\\",\\"versionNo\\":\\"schedule-20260617020000\\",\\"format\\":\\"STRUCTURED_TEXT\\",\\"generation\\":null}"
                },
                {
                  "itemId": "NHC-DM",
                  "payloadJson": "{\\"sourceCode\\":\\"NHC-DM\\",\\"url\\":\\"https://guideline.example.org/dm.txt\\",\\"versionNo\\":\\"schedule-20260617020000\\",\\"format\\":\\"STRUCTURED_TEXT\\",\\"generation\\":null}"
                }
              ]
            }
            """.getBytes(UTF_8));
        when(orchestration.runScheduled(any()))
            .thenReturn(response("run-1", KnowledgeAcquisitionRunStatus.SUCCEEDED, null))
            .thenReturn(response("run-2", KnowledgeAcquisitionRunStatus.FAILED, "公域资料抓取失败：timeout"));

        RuntimeTaskExecutionResult result = handler.execute(command(ref));

        assertThat(result.status()).isEqualTo(RuntimeTaskStatus.PARTIAL_SUCCESS);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.retryableCount()).isEqualTo(1);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.itemId()).isEqualTo("NHC-DM");
            assertThat(failure.errorCode()).isEqualTo("KNOWLEDGE_ACQUISITION_FAILED");
            assertThat(failure.message()).contains("timeout");
            assertThat(failure.retryable()).isTrue();
        });
        ArgumentCaptor<KnowledgeAcquisitionRunRequest> request =
            ArgumentCaptor.forClass(KnowledgeAcquisitionRunRequest.class);
        verify(orchestration, times(2)).runScheduled(request.capture());
        assertThat(request.getAllValues()).hasSize(2);
        assertThat(request.getAllValues().getFirst().sourceCode()).isEqualTo("NHC-HTN");
        assertThat(request.getAllValues().getFirst().format()).isEqualTo(DocumentFormat.STRUCTURED_TEXT);
    }

    @Test
    void supportsOnlyKnowledgeAcquisitionDiscoveryTaskType() {
        assertThat(handler.supports(AcquisitionRuntimeTaskHandler.TASK_TYPE)).isTrue();
        assertThat(handler.supports("RUNTIME_SELF_CHECK")).isFalse();
    }

    @Test
    void returnsFailedResultWhenTaskPayloadCannotBeParsed() {
        PayloadRef ref = new PayloadRef(PayloadRef.STORAGE_INLINE, "sha256:payload", "db://payload", 32,
            "application/json");
        when(payloadStorage.get(ref)).thenReturn("{broken-json".getBytes(UTF_8));

        RuntimeTaskExecutionResult result = handler.execute(command(ref));

        assertThat(result.status()).isEqualTo(RuntimeTaskStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("KNOWLEDGE_ACQUISITION_PAYLOAD_INVALID");
        assertThat(result.message()).contains("payload");
        verify(orchestration, times(0)).runScheduled(any());
    }

    private RuntimeTaskExecutionCommand command(PayloadRef ref) {
        return new RuntimeTaskExecutionCommand(
            "task-acq",
            "tenant-1",
            "tenant-1",
            RuntimeTaskMode.BATCH,
            AcquisitionRuntimeTaskHandler.TASK_TYPE,
            ref,
            2,
            "trace-acq");
    }

    private KnowledgeAcquisitionRunResponse response(String runCode,
                                                     KnowledgeAcquisitionRunStatus status,
                                                     String failureReason) {
        return new KnowledgeAcquisitionRunResponse(
            runCode,
            status,
            "NHC",
            "https://guideline.example.org/doc.txt",
            "guideline.example.org",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            failureReason,
            null);
    }
}
