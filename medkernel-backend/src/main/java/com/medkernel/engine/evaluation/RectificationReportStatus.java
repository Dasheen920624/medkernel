package com.medkernel.engine.evaluation;

/**
 * 整改报告状态。
 *
 * <p>{@code AVAILABLE} 表示存在真实整改任务可统计；{@code NO_TASKS} 表示当前过滤下无任务事实。
 */
public enum RectificationReportStatus {
    AVAILABLE,
    NO_TASKS
}
