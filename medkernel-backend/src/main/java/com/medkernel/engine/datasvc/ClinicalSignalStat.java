package com.medkernel.engine.datasvc;

import java.time.Instant;

/**
 * 临床信号统计聚合行（DATASVC-01，D2 去标识）。
 *
 * <p>从 {@code recommendation_card} 真实投递的 CDSS 决策信号事实按 {@code card_type}（信号类别）聚合：
 * 信号总数、高危信号数（risk_level 为 HIGH/CRITICAL）、采纳数（status=ACCEPTED）、驳回数（status=REJECTED）、
 * 最近信号时刻。{@code acceptedCount}/{@code rejectedCount} 均为真实记录状态的计数（非伪造采纳率，铁律 #1）；
 * 仅聚合计量，不含患者标识（D2）。
 */
public record ClinicalSignalStat(
    String signalType,
    long totalSignals,
    long highRiskCount,
    long acceptedCount,
    long rejectedCount,
    Instant lastSignalAt
) {}
