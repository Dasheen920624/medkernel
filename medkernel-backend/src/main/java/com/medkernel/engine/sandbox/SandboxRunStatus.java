package com.medkernel.engine.sandbox;

/** 沙盘运行账本状态。 */
public enum SandboxRunStatus {
    /** 正在解析并冻结运行基线。 */
    PREPARING,
    /** 基线已冻结，领域链路正在执行。 */
    RUNNING,
    /** 全部计划步骤执行成功。 */
    PASSED,
    /** 基线解析或任一领域步骤失败。 */
    FAILED
}
