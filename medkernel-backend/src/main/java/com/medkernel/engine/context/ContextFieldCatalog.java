package com.medkernel.engine.context;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 上下文字段目录（P2）。从 canonical 标准资源真实记录字段派生的只读字段清单，
 * 供规则 / 路径创作时的字段选择器消费，解决「上下文没有字典 / 数据源可选」。
 *
 * <p>当前为代码内派生的权威清单（与 {@code engine.context.canonical.*} 记录字段一一对应），
 * 不内置任何业务数据；后续可扩展为可前台维护的持久化目录（见 OpenSpec 设计附录 B）。
 */
@Component
public class ContextFieldCatalog {

    private static final List<ContextFieldDescriptor> FIELDS = List.of(
        // Patient（单数路径 patient.*）
        ContextFieldDescriptor.of("Patient", "patient.birthDate", "出生日期", "date", null, "用于派生年龄"),
        ContextFieldDescriptor.of("Patient", "patient.gender", "性别", "string"),
        ContextFieldDescriptor.of("Patient", "patient.allergies", "过敏列表（粗粒度）", "list", null,
            "院内粗粒度过敏编码列表；结构化过敏待 P0 资源补齐"),
        ContextFieldDescriptor.of("Patient", "patient.specialPopulations", "特殊人群标记", "list", null,
            "如妊娠 / 儿童 / 老年，用于规则适用域"),

        // Observation（集合 observations[].*）
        ContextFieldDescriptor.of("Observation", "observations[].code", "检验/体征编码", "code"),
        ContextFieldDescriptor.of("Observation", "observations[].displayName", "检验/体征名称", "string"),
        ContextFieldDescriptor.of("Observation", "observations[].valueNumeric", "数值结果", "number"),
        ContextFieldDescriptor.of("Observation", "observations[].valueString", "文本结果", "string"),
        ContextFieldDescriptor.of("Observation", "observations[].unit", "单位", "string", null,
            "数值结果单位，用于单位换算比较"),
        ContextFieldDescriptor.of("Observation", "observations[].referenceRange", "参考范围", "string", null,
            "支持参考范围算子 above_ref / within_ref"),
        ContextFieldDescriptor.of("Observation", "observations[].criticalFlag", "危急值标记", "string", null,
            "支持危急值算子 is_critical"),
        ContextFieldDescriptor.of("Observation", "observations[].eventTime", "发生时间", "date", null,
            "用于时间窗 / 趋势算子"),

        // Condition（集合 conditions[].*）
        ContextFieldDescriptor.of("Condition", "conditions[].code", "诊断编码", "code"),
        ContextFieldDescriptor.of("Condition", "conditions[].codeSystem", "诊断编码系统", "string"),
        ContextFieldDescriptor.of("Condition", "conditions[].displayName", "诊断名称", "string"),
        ContextFieldDescriptor.of("Condition", "conditions[].severity", "严重程度", "string"),
        ContextFieldDescriptor.of("Condition", "conditions[].onsetTime", "发病时间", "date"),

        // Medication（集合 medications[].*）
        ContextFieldDescriptor.of("Medication", "medications[].code", "药品编码", "code"),
        ContextFieldDescriptor.of("Medication", "medications[].displayName", "药品名称", "string"),
        ContextFieldDescriptor.of("Medication", "medications[].dose", "剂量", "number"),
        ContextFieldDescriptor.of("Medication", "medications[].doseUnit", "剂量单位", "string"),
        ContextFieldDescriptor.of("Medication", "medications[].route", "给药途径", "string"),
        ContextFieldDescriptor.of("Medication", "medications[].frequency", "频次", "string"),
        ContextFieldDescriptor.of("Medication", "medications[].prescriptionStatus", "处方状态", "string", null,
            "如 ACTIVE，用于在用药物判断"),
        ContextFieldDescriptor.of("Medication", "medications[].eventTime", "开立时间", "date"),

        // Encounter（集合 encounters[].*）
        ContextFieldDescriptor.of("Encounter", "encounters[].encounterType", "就诊类型", "string", null,
            "如住院 / 门诊 / 急诊"));

    /**
     * 查询字段目录，可按资源类型与关键词过滤。
     *
     * @param resourceType 资源类型过滤（忽略大小写；空表示不限）
     * @param keyword      关键词过滤，匹配字段路径或中文名（空表示不限）
     */
    public List<ContextFieldDescriptor> query(String resourceType, String keyword) {
        String type = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return FIELDS.stream()
            .filter(field -> type.isEmpty() || field.resourceType().toLowerCase(Locale.ROOT).equals(type))
            .filter(field -> kw.isEmpty()
                || field.fieldPath().toLowerCase(Locale.ROOT).contains(kw)
                || field.displayName().toLowerCase(Locale.ROOT).contains(kw))
            .toList();
    }
}
