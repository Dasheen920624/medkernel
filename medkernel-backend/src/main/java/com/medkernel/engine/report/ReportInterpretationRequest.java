package com.medkernel.engine.report;

import jakarta.validation.constraints.NotBlank;

/**
 * 医技报告解读请求：以已生效标准上下文快照为输入。
 */
public record ReportInterpretationRequest(@NotBlank String contextSnapshotId) {}
