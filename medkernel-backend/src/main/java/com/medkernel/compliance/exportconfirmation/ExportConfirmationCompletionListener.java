package com.medkernel.compliance.exportconfirmation;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.medkernel.shared.export.ExportCompletionRequested;

/**
 * 将后台真实导出文件完成事件登记到对应的导出确认记录。
 */
@Component
public class ExportConfirmationCompletionListener {

    private final ExportConfirmationService service;

    public ExportConfirmationCompletionListener(ExportConfirmationService service) {
        this.service = service;
    }

    @EventListener
    public void onExportCompleted(ExportCompletionRequested event) {
        service.completeExportFromJobByIdempotencyKey(
            event.tenantId(),
            event.idempotencyKey(),
            event.jobId(),
            event.reason(),
            event.actor()
        );
    }
}
