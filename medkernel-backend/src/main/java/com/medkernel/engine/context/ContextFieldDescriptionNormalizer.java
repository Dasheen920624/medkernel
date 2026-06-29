package com.medkernel.engine.context;

/**
 * 上下文字段说明规范化。
 *
 * <p>字段目录说明用于系统接入契约、规则字段选择和审计解释；历史生效版本或机构扩展
 * 遗留空说明时，运行链路应补齐客户可读说明，不能让接入页因低风险元数据缺口中断。
 */
final class ContextFieldDescriptionNormalizer {

    private ContextFieldDescriptionNormalizer() {
    }

    static String normalize(String description, String displayName, String fieldPath) {
        String normalized = trimToNull(description);
        if (normalized != null) {
            return normalized;
        }
        String label = trimToNull(displayName);
        if (label == null) {
            label = trimToNull(fieldPath);
        }
        return (label == null ? "上下文字段" : label) + "字段说明";
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
