package com.medkernel.engine.pathway;

/**
 * 临床时钟超时升级级别。
 *
 * <p>从无升级到提醒、上报、质控记录，供路径 SLA 展示和后续领域事件消费。
 */
public enum ClinicalClockEscalationLevel {
    NONE,
    REMINDER,
    REPORT,
    QUALITY_RECORD
}
