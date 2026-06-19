package com.medkernel.engine.sandbox.replay;

import java.time.Instant;

/** 历史重放清单查询结果；不返回可反查现场租户的真实标识。 */
public record SandboxReplayCaseResponse(
    String replayCaseId,
    SandboxReplayStatus status,
    String packageCode,
    String packageVersion,
    String manifestHash,
    String deidentificationProfile,
    Instant occurredAt,
    Instant importedAt,
    Instant revokedAt,
    String revokeReason,
    int assetCount
) {
    static SandboxReplayCaseResponse from(SandboxReplayCase replayCase, int assetCount) {
        return new SandboxReplayCaseResponse(
            replayCase.replayCaseId(), replayCase.status(), replayCase.packageCode(),
            replayCase.packageVersion(), replayCase.manifestHash(),
            replayCase.deidentificationProfile(), replayCase.occurredAt(), replayCase.importedAt(),
            replayCase.revokedAt(), replayCase.revokeReason(), assetCount);
    }
}
