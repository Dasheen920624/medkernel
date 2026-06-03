package com.medkernel.engine.recommendation;

/**
 * 疲劳治理信号类型：SHOWN 已展示 / SILENT_RECORDED 静默试运行 / VIEWED 用户查看 /
 * ACCEPTED 用户采纳 / REJECTED 用户不采纳 / DEFERRED 稍后处理 / DISMISSED 关闭忽略 /
 * SUPPRESSED 疲劳治理抑制。
 *
 * <p>API-07 只按调用方阈值做可解释抑制，高风险/红线卡不进入疲劳抑制。
 */
public enum RecommendationFatigueSignalType {
    SHOWN,
    SILENT_RECORDED,
    VIEWED,
    ACCEPTED,
    REJECTED,
    DEFERRED,
    DISMISSED,
    SUPPRESSED
}
