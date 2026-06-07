package com.medkernel.engine.rule;

import jakarta.validation.constraints.Size;

/**
 * 规则历史回测请求，cohortRef 指向已脱敏并带金标准标注的样本集。
 */
public record RuleBacktestRequest(
    @Size(max = 120) String cohortRef
) {}
