package com.medkernel.engine.recommendation;

/**
 * CDSS 疲劳治理抑制策略。
 *
 * @param threshold 低价值信号达到该阈值后才允许抑制低/中风险卡
 * @param windowHours 统计低价值信号的回看小时数
 * @param source 策略来源，用于诊断和测试区分配置中心 / 请求兼容值
 */
public record RecommendationFatiguePolicy(
    int threshold,
    int windowHours,
    String source
) {
    public RecommendationFatiguePolicy {
        if (threshold <= 0 || windowHours <= 0) {
            throw new IllegalArgumentException("疲劳治理阈值和窗口必须为正整数");
        }
    }
}
