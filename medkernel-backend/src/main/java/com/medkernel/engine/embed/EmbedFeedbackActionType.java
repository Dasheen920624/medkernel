package com.medkernel.engine.embed;

import java.util.Locale;

/**
 * 嵌入反馈动作受控枚举，覆盖采纳、不采纳、稍后处理、忽略和关闭。
 */
public enum EmbedFeedbackActionType {
    ADOPT,
    REJECT,
    LATER,
    IGNORE,
    CLOSE;

    public static EmbedFeedbackActionType fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("嵌入反馈动作缺失");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return EmbedFeedbackActionType.valueOf(normalized);
    }
}
