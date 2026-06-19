package com.medkernel.engine.sandbox.replay;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** 经摘要与去标识复验后可供执行内核读取的历史重放清单。 */
public record SandboxReplayResolvedCase(
    SandboxReplayCase replayCase,
    JsonNode contextSnapshot,
    List<SandboxReplayAssetBinding> assets
) {
    public SandboxReplayResolvedCase {
        assets = List.copyOf(assets);
    }
}
