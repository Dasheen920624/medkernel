package com.medkernel.compliance.exportconfirmation;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.export.ExportCompletionRequested;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExportConfirmationCompletionListenerTest {

    @Test
    void registersCompletedBackgroundExportAgainstItsFrozenConfirmation() {
        ExportConfirmationService service = mock(ExportConfirmationService.class);
        ExportConfirmationCompletionListener listener =
            new ExportConfirmationCompletionListener(service);

        listener.onExportCompleted(new ExportCompletionRequested(
            "tenant-1",
            "idem-1",
            "job-1",
            "后台异步导出任务已生成真实文件",
            "engine-operator-1"
        ));

        verify(service).completeExportFromJobByIdempotencyKey(
            "tenant-1",
            "idem-1",
            "job-1",
            "后台异步导出任务已生成真实文件",
            "engine-operator-1"
        );
    }
}
