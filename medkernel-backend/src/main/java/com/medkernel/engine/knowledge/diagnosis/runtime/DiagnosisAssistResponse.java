package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.List;

/** 鉴别诊断响应：候选并列 + 未标准化清单 + 辅助声明（空态非排除诊断）+ trace。 */
public record DiagnosisAssistResponse(
    List<DiagnosisCandidate> candidates, List<String> unmappedFindings,
    String advisoryNote, String traceId) {}
