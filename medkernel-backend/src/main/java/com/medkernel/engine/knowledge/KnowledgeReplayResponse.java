package com.medkernel.engine.knowledge;

import java.time.Instant;

/**
 * 知识历史版本重放响应。
 *
 * <p>历史重放只返回被指定版本自身的绑定内容与时间范围，不混入当前 ACTIVE 版本。
 */
public record KnowledgeReplayResponse(
    Long identityId,
    Long versionId,
    String versionNo,
    KnowledgeVersionStatus status,
    boolean historicalVersion,
    String snapshotId,
    String contentHash,
    String anchors,
    Instant effectiveFrom,
    Instant effectiveTo
) {
}
