package com.medkernel.engine.mpi;

import java.util.Map;

/**
 * 患者主索引（MPI）驾驶舱统计指标响应。
 *
 * @param activeCount  活跃主索引总数
 * @param mergedCount  已并入其他主索引的总数
 * @param activePathwayCount 当前租户在径患者路径实例总数
 * @param averageAge   活跃患者平均年龄
 * @param genderCounts 活跃患者性别统计分布（键为性别代码 M/F/UNKNOWN，值为人数）
 */
public record MpiStatsResponse(
    long activeCount,
    long mergedCount,
    long activePathwayCount,
    double averageAge,
    Map<String, Long> genderCounts
) {}
