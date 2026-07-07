package com.medkernel.shared.evidence;

import java.time.Instant;

/**
 * 可信存证快照视图。
 *
 * @param evidenceId 证据唯一标识
 * @param tenantId 租户标识
 * @param traceId 链路追踪标识
 * @param evidenceType 证据类型
 * @param action 审计动作
 * @param subjectType 业务主体类型
 * @param subjectId 业务主体标识
 * @param evidenceSummary 证据摘要
 * @param payloadSnapshot 业务快照正文
 * @param fileUri 证据文件下载地址
 * @param fileDigest 证据文件摘要
 * @param signatureAlgorithm 签名算法
 * @param valid 当前快照是否有效
 * @param createdAt 创建时间
 * @param createdBy 创建人
 */
public record EvidenceSnapshotView(
    String evidenceId,
    String tenantId,
    String traceId,
    String evidenceType,
    String action,
    String subjectType,
    String subjectId,
    String evidenceSummary,
    String payloadSnapshot,
    String fileUri,
    String fileDigest,
    String signatureAlgorithm,
    boolean valid,
    Instant createdAt,
    String createdBy
) {}
