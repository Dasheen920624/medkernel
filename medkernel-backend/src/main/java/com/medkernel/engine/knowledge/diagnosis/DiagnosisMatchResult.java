package com.medkernel.engine.knowledge.diagnosis;

import java.util.List;

/** 单候选命中结果：置信 + 支持/反对证据 + 缺失必需项 + 是否命中排除。 */
public record DiagnosisMatchResult(
    DiagnosisConfidence confidence,
    List<String> supporting,
    List<String> refuting,
    List<String> missingRequired,
    boolean hitExclusion) {}
