package com.medkernel.engine.datasvc;

import java.time.Instant;

/**
 * 知识存在性读模型（DATASVC-01 PR2-b，受控工具 {@code checkKnowledgeExistence} 的 D1 输出）。
 *
 * <p>对一个知识身份编码回答「是否存在」并附最小已发布资产元数据（领域/状态），均为 D1「已发布资产元数据」，
 * 不含患者字段。<b>真实不存在＝ {@code exists=false} 且 {@code degraded=false}</b>（诚实回答而非报错或降级，铁律 #1）；
 * 仅上游不可用时 {@code degraded=true}（未确认存在性，不以「不存在」伪装）。
 */
public record KnowledgeExistence(
    String identityCode,
    boolean exists,
    String domain,
    String status,
    EngineDataLevel dataLevel,
    Instant generatedAt,
    boolean degraded,
    String degradeReason
) {}
