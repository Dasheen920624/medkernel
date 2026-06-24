package com.medkernel.engine.sandbox;

/** 沙盘基线解析来源。 */
public enum SandboxResolutionSource {
    /** 认证医院当前不可变机构生效版本。 */
    CURRENT_RUNTIME_RELEASE,
    /** 历史重放清单。 */
    REPLAY_MANIFEST
}
