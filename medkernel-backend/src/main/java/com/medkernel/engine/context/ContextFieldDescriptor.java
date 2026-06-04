package com.medkernel.engine.context;

/**
 * 上下文字段目录条目（RULE-01 / PATH-01 字段选择器数据源，OpenSpec pathway-rule-authoring-overhaul P2）。
 *
 * <p>从 {@code com.medkernel.engine.context.canonical.*} 标准资源的真实记录字段派生，
 * 供规则条件与路径守卫的「上下文字段路径」可视化选择，替代手敲。仅描述字段元数据，
 * 不含任何具体患者/药品/诊断值。
 *
 * @param resourceType canonical 资源类型（如 {@code Observation}）
 * @param fieldPath    建议字段路径（集合用 {@code []} 表示，如 {@code observations[].valueNumeric}）
 * @param displayName  中文展示名
 * @param dataType     数据类型：number/string/boolean/date/code/list
 * @param unit         数值单位（无则为 {@code null}）
 * @param codeSystem   编码类字段绑定的标准字典/编码系统（如 ICD-10/LOINC/ATC；非编码字段为 {@code null}），
 *                     供比较值从标准字典候选选择（P5）
 * @param description  业务说明
 */
public record ContextFieldDescriptor(
    String resourceType,
    String fieldPath,
    String displayName,
    String dataType,
    String unit,
    String codeSystem,
    String description) {

    public static ContextFieldDescriptor of(
        String resourceType, String fieldPath, String displayName, String dataType) {
        return new ContextFieldDescriptor(resourceType, fieldPath, displayName, dataType, null, null, null);
    }

    public static ContextFieldDescriptor of(
        String resourceType,
        String fieldPath,
        String displayName,
        String dataType,
        String unit,
        String description) {
        return new ContextFieldDescriptor(
            resourceType, fieldPath, displayName, dataType, unit, null, description);
    }

    /** 编码类字段（dataType=code）绑定标准字典。 */
    public static ContextFieldDescriptor ofCode(
        String resourceType,
        String fieldPath,
        String displayName,
        String codeSystem,
        String description) {
        return new ContextFieldDescriptor(
            resourceType, fieldPath, displayName, "code", null, codeSystem, description);
    }
}
