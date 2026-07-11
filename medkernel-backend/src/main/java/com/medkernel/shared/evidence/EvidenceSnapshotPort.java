package com.medkernel.shared.evidence;

/**
 * 可信存证快照端口。
 *
 * <p>跨域调用方只依赖本端口，具体存储、签名、验签和审计实现由合规模块提供。
 */
public interface EvidenceSnapshotPort {

    /**
     * 创建可信存证快照。
     *
     * @param tenantId 租户标识
     * @param command 创建命令
     * @return 已创建的证据快照
     */
    EvidenceSnapshotView createSnapshot(String tenantId, EvidenceSnapshotCreateCommand command);

    /**
     * 校验证据快照签名和摘要。
     *
     * @param tenantId 租户标识
     * @param evidenceId 证据标识
     * @return 验签结果
     */
    EvidenceVerificationView verifyEvidence(String tenantId, String evidenceId);

    /**
     * 读取证据快照详情。
     *
     * @param tenantId 租户标识
     * @param evidenceId 证据标识
     * @return 证据快照
     */
    EvidenceSnapshotView getEvidenceById(String tenantId, String evidenceId);
}
