package com.medkernel.engine.quality.dashboard;

import java.math.BigDecimal;

/**
 * 质控风险热力单元。
 *
 * @param departmentId 责任科室 ID
 * @param totalFindings 该科室问题数
 * @param openFindings 该科室待闭环问题数
 * @param highRiskFindings P0/P1 问题数
 * @param hitRate 该科室问题数占当前作用域问题总数的比例
 * @param maxSeverity 当前科室最高问题严重度
 * @param heatToken 前端设计 token 名称，不含颜色值
 */
public record QualityDashboardHeatmapCell(
    String departmentId,
    long totalFindings,
    long openFindings,
    long highRiskFindings,
    BigDecimal hitRate,
    String maxSeverity,
    String heatToken
) {}
