package com.medkernel.engine.embed;

import java.util.Locale;

/**
 * 嵌入反馈动作受控枚举，只表达医师对嵌入建议的采纳或拒绝，不承载宿主回调送达状态。
 */
public enum EmbedFeedbackActionType {
    ADOPT,
    REJECT;

    public static EmbedFeedbackActionType fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("嵌入反馈动作缺失");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("ACCEPT".equals(normalized)) {
            return ADOPT;
        }
        return EmbedFeedbackActionType.valueOf(normalized);
    }
}
