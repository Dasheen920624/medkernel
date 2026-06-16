package com.medkernel.engine.context;

/**
 * 上下文字段目录条目（RULE-01 / PATH-01 字段选择器数据源，OpenSpec pathway-rule-authoring-overhaul P2/P5）。
 *
 * <p>从 {@code com.medkernel.engine.context.canonical.*} 标准资源的真实记录字段派生，
 * 供规则条件与路径守卫的「上下文字段路径」可视化选择，替代手敲。仅描述字段元数据，
 * 不含任何具体患者/药品/诊断值。
 *
 * <p>字段按业务层级组织：{@code category}（一级业务域，如「医嘱信息」）→ {@code group}
 * （二级分组，如「用药医嘱」）→ 字段，贴合临床认知；{@code resourceType} 为底层 canonical
 * 技术类型，保留用于过滤与追溯。
 *
 * @param category     一级业务域（基本信息/诊断信息/医嘱信息/检验检查/…）
 * @param group        二级业务分组（患者基本信息/用药医嘱/检验体征结果/…）
 * @param resourceType canonical 资源类型（如 {@code Observation}）
 * @param fieldPath    建议字段路径（集合用 {@code []} 表示，如 {@code observations[].valueNumeric}）
 * @param displayName  中文展示名
 * @param dataType     数据类型：number/string/boolean/date/code/list
 * @param unit         数值单位（无则为 {@code null}）
 * @param codeSystem   编码类字段绑定的标准字典/编码系统（如 ICD-10/LOINC/ATC；非编码字段为 {@code null}）
 * @param description  业务说明
 * @param payloadKey   第三方接入 payload 顶层键（如 {@code observations} / {@code patient}）
 * @param propertyName 第三方接入资源内字段名（如 {@code valueNumeric} / {@code age}）
 * @param jsonSchemaType JSON Schema 数据类型
 * @param externalWritable 第三方是否可直接传入；派生字段由引擎计算，必须为 {@code false}
 */
public record ContextFieldDescriptor(
    String category,
    String group,
    String resourceType,
    String fieldPath,
    String displayName,
    String dataType,
    String unit,
    String codeSystem,
    String description,
    /** 来源：PLATFORM（平台派生只读）/ TENANT（租户自定义可维护）。 */
    String source,
    /** 租户自定义字段的业务主键（仅 TENANT 字段有值，供前台删除）；平台字段为 {@code null}。 */
    String fieldId,
    /** 是否为求值期计算的派生字段（如 {@code patient.age} 由出生日期算得），而非原始存储字段。 */
    boolean derived,
    String payloadKey,
    String propertyName,
    String jsonSchemaType,
    boolean externalWritable) {

    public ContextFieldDescriptor(
        String category,
        String group,
        String resourceType,
        String fieldPath,
        String displayName,
        String dataType,
        String unit,
        String codeSystem,
        String description,
        String source,
        String fieldId,
        boolean derived) {
        this(category, group, resourceType, fieldPath, displayName, dataType, unit, codeSystem,
            description, source, fieldId, derived, payloadKey(fieldPath), propertyName(fieldPath),
            jsonSchemaType(dataType), !derived);
    }

    private static String payloadKey(String fieldPath) {
        String normalized = fieldPath == null ? "" : fieldPath.trim();
        int dot = normalized.indexOf('.');
        String head = dot < 0 ? normalized : normalized.substring(0, dot);
        return head.replace("[]", "");
    }

    private static String propertyName(String fieldPath) {
        String normalized = fieldPath == null ? "" : fieldPath.trim();
        int dot = normalized.lastIndexOf('.');
        String tail = dot < 0 ? normalized : normalized.substring(dot + 1);
        return tail.replace("[]", "");
    }

    private static String jsonSchemaType(String dataType) {
        return switch (dataType) {
            case "number" -> "number";
            case "boolean" -> "boolean";
            case "list" -> "array";
            default -> "string";
        };
    }
}
