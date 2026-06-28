package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.context.canonical.ClinicalSetting;
import com.medkernel.engine.workflow.WorkflowCollaborationService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import org.junit.jupiter.api.Test;

class ClinicalEventOutputProjectionListenerTest {

    @Test
    void processedEventProjectsWorkflowOutputs() {
        WorkflowCollaborationService workflow = mock(WorkflowCollaborationService.class);
        ClinicalEventOutputProjectionListener listener = new ClinicalEventOutputProjectionListener(workflow);
        ClinicalEventProcessedEvent event = processedEvent();

        listener.projectProcessedOutputs(event);

        verify(workflow).projectProcessedClinicalEvent(event);
    }

    @Test
    void workflowProjectionFailureBecomesHonestDownstreamUnavailable() {
        WorkflowCollaborationService workflow = mock(WorkflowCollaborationService.class);
        doThrow(new IllegalStateException("协同库不可达"))
            .when(workflow).projectProcessedClinicalEvent(any());
        ClinicalEventOutputProjectionListener listener = new ClinicalEventOutputProjectionListener(workflow);

        assertThatThrownBy(() -> listener.projectProcessedOutputs(processedEvent()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> {
                ApiException api = (ApiException) error;
                org.assertj.core.api.Assertions.assertThat(api.errorCode())
                    .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
                org.assertj.core.api.Assertions.assertThat(api.getMessage())
                    .contains("临床事件产出分发不可用", "协同库不可达");
            });
    }

    private ClinicalEventProcessedEvent processedEvent() {
        Instant now = Instant.parse("2026-06-08T08:00:00Z");
        return new ClinicalEventProcessedEvent(
            "evt-order-1",
            "tenant-A",
            "trace-order",
            new ClinicalEventContext(
                "evt-order-1",
                "tenant-A",
                new OrgScope("tenant-A", "group-A", "hospital-A", "campus-A", "site-A", "dept-A", null, "specialty-A"),
                ClinicalEventType.ORDER,
                ClinicalEventTriggerPoint.ORDER_SIGN,
                "patient-1",
                "enc-1",
                ClinicalSetting.INPATIENT,
                "snap-order-1",
                "HIS",
                "runtime-release-test",
                "digest-order-1",
                now,
                "HIS:order-sign",
                "trace-order",
                new ContextSnapshotResources(
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    ContextSnapshotResources.emptyExtensions()),
                null,
                List.of()));
    }
}
