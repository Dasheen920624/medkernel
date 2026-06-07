package com.medkernel.engine.list;

/**
 * 已完成大列表导出的可信产物投影。
 *
 * @param jobId 导出任务 ID
 * @param resourceType 导出资源类型
 * @param requestSnapshot 服务端保存的导出范围快照
 * @param idempotencyKey 导出任务幂等键
 * @param downloadUri 受鉴权保护的下载地址
 * @param exportDigest 服务器按真实文件计算的 SM3 摘要
 */
public record LargeListExportArtifact(
    String jobId,
    String resourceType,
    String requestSnapshot,
    String idempotencyKey,
    String downloadUri,
    String exportDigest
) {
}
