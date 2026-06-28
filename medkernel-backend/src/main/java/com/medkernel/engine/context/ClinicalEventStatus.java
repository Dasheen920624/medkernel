package com.medkernel.engine.context;

/**
 * 临床事件处理状态。
 *
 * <p>覆盖临床事件从接收、映射、处理完成到失败或被替代的完整状态机。
 */
public enum ClinicalEventStatus {
    RECEIVED,
    MAPPED,
    PROCESSED,
    FAILED,
    SUPERSEDED
}
