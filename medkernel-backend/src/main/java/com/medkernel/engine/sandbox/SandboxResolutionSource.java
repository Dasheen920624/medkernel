package com.medkernel.engine.sandbox;

/** 沙盘基线解析来源。 */
public enum SandboxResolutionSource {
    /** 演练机构自有配置包。 */
    TENANT_PACKAGE,
    /** 平台主源配置包。 */
    PLATFORM_PACKAGE,
    /** 历史重放清单。 */
    REPLAY_MANIFEST
}
