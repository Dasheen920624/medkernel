package com.medkernel.engine.knowledge.diagnosis.runtime;

import jakarta.validation.constraints.NotBlank;

/** 鉴别诊断请求：以已建上下文快照为输入（统一上下文由 RequestContext 提供租户）。 */
public record DiagnosisAssistRequest(@NotBlank String contextSnapshotId) {}
