package com.medkernel.engine.sandbox;

/** 演练机构沙盘运行绑定状态。 */
public enum SandboxRuntimeBindingStatus {
    /** 当前唯一生效绑定。 */
    ACTIVE,
    /** 历史绑定，只用于审计。 */
    INACTIVE
}
