package com.medkernel.engine.sandbox;

/** 沙盘基线解析来源。 */
public enum SandboxResolutionSource {
    /** 认证医院当前不可变临床运行修订。 */
    CURRENT_RUNTIME_RELEASE,
    /** 历史重放清单。 */
    REPLAY_MANIFEST
}
