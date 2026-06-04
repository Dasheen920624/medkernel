package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.List;

import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidence;

/** 单个鉴别诊断候选（可解释、可追溯到诊断知识版本；置信分级非概率）。 */
public record DiagnosisCandidate(
    Long identityId, String diagnosisName, String icdCode,
    DiagnosisConfidence confidence,
    List<String> supporting, List<String> refuting, List<String> missingRequired,
    String authorityLevel, boolean redline, Long sourceVersionId) {}
