package com.medkernel.compliance.masking;

/**
 * SYS-06 脱敏策略。
 */
public enum MaskingStrategy {
    REDACT,
    KEEP_LAST,
    KEEP_FIRST_LAST,
    EMAIL,
    FIXED
}
