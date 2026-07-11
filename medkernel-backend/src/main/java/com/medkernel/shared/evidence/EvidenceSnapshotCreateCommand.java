package com.medkernel.shared.evidence;

/**
 * 可信存证快照创建命令。
 *
 * @param evidenceId 证据唯一标识
 * @param traceId 链路追踪标识
 * @param evidenceType 证据类型
 * @param action 审计动作
 * @param subjectType 业务主体类型
 * @param subjectId 业务主体标识
 * @param evidenceSummary 证据摘要
 * @param payloadSnapshot 业务快照正文
 */
public record EvidenceSnapshotCreateCommand(
    String evidenceId,
    String traceId,
    String evidenceType,
    String action,
    String subjectType,
    String subjectId,
    String evidenceSummary,
    String payloadSnapshot
) {}
