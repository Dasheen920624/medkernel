package com.medkernel.engine.versioning;

/**
 * 配置资产历史重放端口。
 */
public interface ReplayPort {

    VersionReplayBinding bindRuntimeResult(VersionReplayBindingCommand command);

    VersionReplayResult replay(VersionReplayQuery query);
}
