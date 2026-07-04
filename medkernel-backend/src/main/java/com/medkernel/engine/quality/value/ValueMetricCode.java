package com.medkernel.engine.quality.value;

/**
 * OPT-08 价值指标口径代码。
 *
 * <p>枚举即当前 B0 口径目录，所有指标统一使用 {@code OPT-08.v1} 公式版本，
 * 下游驾驶舱不得自行另造同名 ROI 口径。
 */
public enum ValueMetricCode {
    ADOPTION_RATE("采纳率", "已采纳推荐卡 / 已闭环推荐卡", "RATE"),
    FALSE_POSITIVE_RATE("误报率", "已豁免质量问题 / 已复核质量问题", "RATE"),
    MISSED_CASE_RETROSPECTIVE("漏报回溯", "漏报回溯问题数", "CASE_COUNT"),
    PATHWAY_COMPLETION_RATE("路径完成率", "已完成患者路径 / 已入径患者路径", "RATE"),
    RECTIFICATION_CLOSURE_RATE("整改闭环率", "已关闭或豁免整改任务 / 全部整改任务", "RATE"),
    INSURANCE_VIOLATION_REDUCTION("医保违规减少", "医保违规减少量 / 基线违规量", "RATE");

    private final String displayName;
    private final String formula;
    private final String unit;

    ValueMetricCode(String displayName, String formula, String unit) {
        this.displayName = displayName;
        this.formula = formula;
        this.unit = unit;
    }

    public String displayName() {
        return displayName;
    }

    public String formula() {
        return formula;
    }

    public String unit() {
        return unit;
    }
}
