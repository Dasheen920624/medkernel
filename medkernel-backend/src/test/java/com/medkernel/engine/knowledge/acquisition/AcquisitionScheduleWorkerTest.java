package com.medkernel.engine.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.runtime.task.RuntimeTaskMode;
import com.medkernel.shared.runtime.task.RuntimeTaskResponse;
import com.medkernel.shared.runtime.task.RuntimeTaskService;
import com.medkernel.shared.runtime.task.RuntimeTaskStatus;
import com.medkernel.shared.runtime.task.RuntimeTaskSubmitRequest;

class AcquisitionScheduleWorkerTest {

    private final KnowledgeAcquisitionSourceRepository repository = mock(KnowledgeAcquisitionSourceRepository.class);
    private final RuntimeTaskService tasks = mock(RuntimeTaskService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AcquisitionScheduleWorker worker =
        new AcquisitionScheduleWorker(repository, tasks, objectMapper);

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void submitsDueSourcesAsTenantScopedRuntimeBatchTaskAndAdvancesSchedule() throws Exception {
        KnowledgeAcquisitionSource due = scheduledSource("tenant-A", "NHC-HTN", "https://guideline.example.org/htn.txt");
        when(repository.findDueForSchedule(any(), anyInt())).thenReturn(List.of(due));
        when(repository.markScheduleSubmitted(any(), any(), any(), any(), any(), any())).thenReturn(1);
        AtomicReference<String> submittedTenant = new AtomicReference<>();
        when(tasks.submit(any())).thenAnswer(invocation -> {
            submittedTenant.set(RequestContext.currentOrgScope().tenantId());
            return taskResponse("task-acq-1");
        });

        AcquisitionScheduleSummary summary = worker.pollOnce();

        assertThat(summary.scannedCount()).isEqualTo(1);
        assertThat(summary.claimedCount()).isEqualTo(1);
        assertThat(summary.submittedItemCount()).isEqualTo(1);
        assertThat(submittedTenant).hasValue("tenant-A");

        verify(repository).markScheduleSubmitted(
            org.mockito.ArgumentMatchers.eq("tenant-A"),
            org.mockito.ArgumentMatchers.eq(11L),
            org.mockito.ArgumentMatchers.eq(0L),
            any(Instant.class),
            any(Instant.class),
            org.mockito.ArgumentMatchers.eq("knowledge-acquisition-scheduler"));
        ArgumentCaptor<RuntimeTaskSubmitRequest> request = ArgumentCaptor.forClass(RuntimeTaskSubmitRequest.class);
        verify(tasks).submit(request.capture());
        assertThat(request.getValue().mode()).isEqualTo(RuntimeTaskMode.BATCH);
        assertThat(request.getValue().taskType()).isEqualTo(AcquisitionRuntimeTaskHandler.TASK_TYPE);
        assertThat(request.getValue().maxRetries()).isEqualTo(2);
        assertThat(request.getValue().items()).hasSize(1);
        assertThat(request.getValue().items().getFirst().itemId()).isEqualTo("NHC-HTN");

        JsonNode item = objectMapper.readTree(request.getValue().items().getFirst().payloadJson());
        assertThat(item.path("sourceCode").asText()).isEqualTo("NHC-HTN");
        assertThat(item.path("url").asText()).isEqualTo("https://guideline.example.org/htn.txt");
        assertThat(item.path("format").asText()).isEqualTo("STRUCTURED_TEXT");
        assertThat(item.path("versionNo").asText()).startsWith("schedule-");
        assertThat(item.path("generation").path("targetPipeline").asText()).isEqualTo("PLATFORM_SOURCE");
    }

    @Test
    void skipsSourceWhenAtomicScheduleClaimFails() {
        KnowledgeAcquisitionSource due = scheduledSource("tenant-A", "NHC-HTN", "https://guideline.example.org/htn.txt");
        when(repository.findDueForSchedule(any(), anyInt())).thenReturn(List.of(due));
        when(repository.markScheduleSubmitted(any(), any(), any(), any(), any(), any())).thenReturn(0);

        AcquisitionScheduleSummary summary = worker.pollOnce();

        assertThat(summary.scannedCount()).isEqualTo(1);
        assertThat(summary.claimedCount()).isZero();
        assertThat(summary.submittedItemCount()).isZero();
        verify(tasks, never()).submit(any());
    }

    private KnowledgeAcquisitionSource scheduledSource(String tenantId, String sourceCode, String baseUrl) {
        return new KnowledgeAcquisitionSource(
            11L,
            tenantId,
            sourceCode,
            "guideline.example.org",
            baseUrl,
            SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE,
            "国家卫生健康委公开指南",
            "高血压诊疗指南",
            "国家卫生健康委",
            "公开资料许可",
            AcquisitionLicensePolicy.PERMITTED,
            AcquisitionRobotsPolicy.ALLOW_FETCH,
            "Y",
            "Y",
            60,
            Instant.parse("2026-06-17T02:00:00Z"),
            null,
            DocumentFormat.STRUCTURED_TEXT,
            """
                {
                  "targetPipeline": "PLATFORM_SOURCE",
                  "domain": "CLINICAL",
                  "items": [
                    {
                      "assetType": "RULE",
                      "target": {
                        "newIdentity": {
                          "domain": "GUIDELINE",
                          "subject": "高血压指南",
                          "identityCode": "RULE-HTN-2026"
                        }
                      }
                    }
                  ]
                }
                """,
            Instant.EPOCH,
            "super-admin",
            Instant.EPOCH,
            "super-admin",
            0L);
    }

    private RuntimeTaskResponse taskResponse(String taskId) {
        return new RuntimeTaskResponse(
            taskId,
            RuntimeTaskMode.BATCH,
            RuntimeTaskStatus.PROCESSING,
            AcquisitionRuntimeTaskHandler.TASK_TYPE,
            1,
            0,
            0,
            0,
            0,
            2,
            null,
            null,
            null,
            List.of(),
            "运行任务已提交",
            null,
            "trace-acq",
            Instant.EPOCH,
            Instant.EPOCH);
    }
}
