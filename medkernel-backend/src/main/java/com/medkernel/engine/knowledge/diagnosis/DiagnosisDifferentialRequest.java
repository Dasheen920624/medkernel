package com.medkernel.engine.knowledge.diagnosis;

import jakarta.validation.constraints.NotNull;

/** 新增鉴别清单请求：被鉴别诊断身份 + 鉴别要点 + 建议补充检查。 */
public record DiagnosisDifferentialRequest(
    @NotNull Long differentialIdentityId,
    String keyPoint,
    String suggestedWorkup,
    boolean bidirectional
) {}
