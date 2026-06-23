package com.medkernel.engine.pathway;

/**
 * 当前临床触发点可供医师确认的路径摘要。
 *
 * <p>不暴露运行修订或资产版本选择参数，入径时由服务端再次按快照校验。
 */
public record PathwayEntryCandidate(
    String templateId,
    String templateCode,
    String name,
    String diseaseCode
) {
}
