package com.medkernel.engine.context;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.medkernel.engine.workflow.WorkflowCollaborationService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 临床事件处理完成后的同步产出分发器。
 *
 * <p>本监听器运行在 outbox 处理事务内；协同中心不可达时抛出可重试的诚实降级错误，
 * 由临床事件 outbox 回写失败、退避重试并最终进入死信。
 */
@Component
public class ClinicalEventOutputProjectionListener {

    private final WorkflowCollaborationService workflow;

    public ClinicalEventOutputProjectionListener(WorkflowCollaborationService workflow) {
        this.workflow = workflow;
    }

    @EventListener
    public void projectProcessedOutputs(ClinicalEventProcessedEvent processed) {
        try {
            workflow.projectProcessedClinicalEvent(processed);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "临床事件产出分发不可用: " + safeMessage(exception), exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}
