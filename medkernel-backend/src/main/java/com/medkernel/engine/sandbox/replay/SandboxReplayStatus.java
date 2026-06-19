package com.medkernel.engine.sandbox.replay;

/** 历史重放清单状态；撤销只改变可执行性，不删除不可变证据。 */
public enum SandboxReplayStatus {
    IMPORTED,
    REVOKED
}
