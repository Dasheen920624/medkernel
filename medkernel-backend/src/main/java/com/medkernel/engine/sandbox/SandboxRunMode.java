package com.medkernel.engine.sandbox;

/** 沙盘运行模式。 */
public enum SandboxRunMode {
    /** 使用当前机构明确绑定的运行基线。 */
    CURRENT,
    /** 使用不可变现场重放清单中的历史基线。 */
    HISTORICAL_EXACT,
    /** 对同一脱敏上下文比较历史与当前基线。 */
    COMPARE
}
