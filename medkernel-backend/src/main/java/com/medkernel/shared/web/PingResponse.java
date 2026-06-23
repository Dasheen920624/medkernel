package com.medkernel.shared.web;

import java.time.Instant;

/**
 * 系统心跳响应。作为后端 ApiResult 范例的最小 DTO；
 * 任何后续 Controller 都应遵循"Record DTO + ApiResult 包络"的模式。
 *
 * @param product    产品名（固定 "MedKernel"）
 * @param version    版本号
 * @param stage      当前阶段口径（v1.0 GA / 引擎全能力上线 / 业务服务组合）
 * @param serverTime 服务端时间戳
 */
public record PingResponse(
    String product,
    String version,
    String stage,
    Instant serverTime
) {
}
