package com.medkernel.shared.export;

/**
 * 已完成异步导出的可信产物投影（跨导出来源中性合同）。
 *
 * <p>导出审批的「登记完成」环节据此校验真实产物与审批申请一致，并按真实文件字节登记 SM3 摘要。
 * 各导出来源（大列表引擎 / 引擎数据服务层等）经 {@link ExportArtifactProvider} 产出统一形态产物。
 * 置于 shared 层，使业务包（compliance 导出审批）与引擎包（list/datasvc 导出来源）均可依赖，
 * 依赖方向恒为业务/引擎 → shared（SYS-02）。
 *
 * @param jobId 导出任务对外可见 ID
 * @param resourceType 导出资源类型（与审批申请的资源类型一致）
 * @param requestSnapshot 服务端保存的导出范围快照（与审批范围 JSON 一致）
 * @param idempotencyKey 导出任务幂等键（与审批申请的幂等键一致）
 * @param downloadUri 受鉴权保护的下载地址
 * @param exportDigest 服务器按真实导出文件计算的 {@code sm3:} 摘要
 */
public record ExportArtifact(
    String jobId,
    String resourceType,
    String requestSnapshot,
    String idempotencyKey,
    String downloadUri,
    String exportDigest
) {
}
