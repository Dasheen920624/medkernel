package com.medkernel.engine.datasvc;

import java.time.Instant;
import java.util.List;

/**
 * 知识使用统计查询响应（DATASVC-01 PR1）。
 *
 * <p>{@code dataLevel} 本次返回数据级别（知识使用聚合恒为 D2 去标识）；{@code total} 服务端聚合分组总数；
 * {@code generatedAt} 数据生成时刻（不伪装实时）；{@code degraded}/{@code degradeReason} 上游不可用时诚实降级，
 * 不以空数据伪装（铁律 #1）。空结果（{@code total=0}）是真实「无数据」，非降级。
 */
public record KnowledgeUsageStatsResponse(
    EngineDataLevel dataLevel,
    long total,
    int page,
    int size,
    List<KnowledgeUsageStat> rows,
    Instant generatedAt,
    boolean degraded,
    String degradeReason
) {}
