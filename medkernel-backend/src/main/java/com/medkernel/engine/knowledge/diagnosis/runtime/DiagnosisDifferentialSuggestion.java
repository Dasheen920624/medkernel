package com.medkernel.engine.knowledge.diagnosis.runtime;

/**
 * 辅助诊疗候选中的鉴别诊断要点。
 *
 * <p>来源于诊断知识版本维护的鉴别清单，只提示医师进一步判断，不自动形成诊断。
 */
public record DiagnosisDifferentialSuggestion(
    Long differentialIdentityId,
    String identityCode,
    String diagnosisName,
    String keyPoint,
    String suggestedWorkup
) {
}
