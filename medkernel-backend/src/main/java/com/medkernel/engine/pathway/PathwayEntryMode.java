package com.medkernel.engine.pathway;

/**
 * 路径入径模式。
 *
 * <p>AUTO_SUGGEST 表示系统按真实入径条件自动建议入径；MANUAL_CONFIRM 表示需要人工确认后入径。
 */
public enum PathwayEntryMode {
    AUTO_SUGGEST,
    MANUAL_CONFIRM
}
