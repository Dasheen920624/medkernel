package com.medkernel.shared.evidence;

/**
 * 可信存证验签结果视图。
 *
 * @param evidenceId 证据唯一标识
 * @param valid 摘要和签名是否全部有效
 * @param calculatedHash 当前计算摘要
 * @param storedHash 已存摘要
 * @param signatureAlgorithm 签名算法
 * @param signatureValid 签名是否有效
 * @param fileUri 证据文件下载地址
 * @param fileDigest 证据文件摘要
 */
public record EvidenceVerificationView(
    String evidenceId,
    boolean valid,
    String calculatedHash,
    String storedHash,
    String signatureAlgorithm,
    boolean signatureValid,
    String fileUri,
    String fileDigest
) {}
