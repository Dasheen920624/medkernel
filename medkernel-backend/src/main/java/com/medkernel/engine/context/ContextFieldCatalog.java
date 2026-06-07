package com.medkernel.engine.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 上下文字段目录（P2/P5）。从 canonical 标准资源真实记录字段派生的只读字段清单，按业务层级
 * （一级业务域 → 二级分组 → 字段）组织，供规则 / 路径创作时的字段选择器消费，解决
 * 「上下文没有字典 / 数据源可选」并贴合临床认知。
 *
 * <p>当前为平台派生的权威清单（与 {@code engine.context.canonical.*} 记录字段一一对应），
 * 不内置任何业务数据；租户自定义扩展字段后续以持久化表叠加（见 OpenSpec 设计附录 B）。
 */
@Component
public class ContextFieldCatalog {

    // 一级业务域
    private static final String CAT_BASIC = "基本信息";
    private static final String CAT_ENCOUNTER = "就诊信息";
    private static final String CAT_DIAGNOSIS = "诊断信息";
    private static final String CAT_ORDER = "医嘱信息";
    private static final String CAT_LAB_EXAM = "检验检查";
    private static final String CAT_NURSING = "护理评估";
    private static final String CAT_FOLLOWUP = "随访信息";
    private static final String CAT_COST = "费用医保";
    private static final String CAT_PLAN_DOC = "诊疗计划与文书";

    private static final List<ContextFieldDescriptor> FIELDS = buildFields();

    private static List<ContextFieldDescriptor> buildFields() {
        List<ContextFieldDescriptor> f = new ArrayList<>();

        // 基本信息 · 患者基本信息
        f.add(field(CAT_BASIC, "患者基本信息", "Patient", "patient.birthDate", "出生日期", "date", null,
            "用于派生年龄"));
        f.add(derivedField(CAT_BASIC, "患者基本信息", "Patient", "patient.age", "年龄", "number", "岁",
            "求值期由出生日期与事件时间计算的整岁（派生字段，不可前台维护）"));
        f.add(field(CAT_BASIC, "患者基本信息", "Patient", "patient.gender", "性别", "string", null, null));
        // 基本信息 · 结构化过敏与特殊人群
        f.add(codeField(CAT_BASIC, "结构化过敏", "AllergyIntolerance", "allergyIntolerances[].code",
            "过敏物质编码", "ATC", "药物过敏场景优先绑定药品标准字典"));
        f.add(field(CAT_BASIC, "结构化过敏", "AllergyIntolerance", "allergyIntolerances[].codeSystem",
            "过敏物质编码系统", "string", null, null));
        f.add(field(CAT_BASIC, "结构化过敏", "AllergyIntolerance", "allergyIntolerances[].substance",
            "过敏物质名称", "string", null, null));
        f.add(field(CAT_BASIC, "结构化过敏", "AllergyIntolerance", "allergyIntolerances[].category",
            "过敏类别", "string", null, "如 medication / food / environment"));
        f.add(field(CAT_BASIC, "结构化过敏", "AllergyIntolerance", "allergyIntolerances[].criticality",
            "严重等级", "string", null, "用于高危用药拦截"));
        f.add(field(CAT_BASIC, "结构化过敏", "AllergyIntolerance", "allergyIntolerances[].reactions",
            "反应表现", "list", null, "如皮疹 / 喉头水肿"));
        f.add(field(CAT_BASIC, "结构化过敏", "AllergyIntolerance", "allergyIntolerances[].onsetTime",
            "发生时间", "date", null, null));
        f.add(field(CAT_BASIC, "结构化过敏", "AllergyIntolerance", "allergyIntolerances[].qualityStatus",
            "数据质量", "string", null, "用于规则证据展示与人工复核"));
        f.add(field(CAT_BASIC, "过敏与特殊人群", "Patient", "patient.specialPopulations", "特殊人群标记",
            "list", null, "如妊娠 / 儿童 / 老年，用于规则适用域"));

        // 就诊信息 · 就诊基本
        f.add(field(CAT_ENCOUNTER, "就诊基本", "Encounter", "encounters[].encounterType", "就诊类型",
            "string", null, "如住院 / 门诊 / 急诊"));
        f.add(field(CAT_ENCOUNTER, "就诊基本", "Encounter", "encounters[].departmentId", "就诊科室", "string",
            null, null));
        f.add(field(CAT_ENCOUNTER, "就诊基本", "Encounter", "encounters[].bedId", "床位", "string", null,
            null));
        // 就诊信息 · 就诊时间
        f.add(field(CAT_ENCOUNTER, "就诊时间", "Encounter", "encounters[].admissionTime", "入院时间", "date",
            null, "可作路径时钟基准事件"));
        f.add(field(CAT_ENCOUNTER, "就诊时间", "Encounter", "encounters[].dischargeTime", "出院时间", "date",
            null, null));

        // 诊断信息 · 诊断
        f.add(codeField(CAT_DIAGNOSIS, "诊断", "Condition", "conditions[].code", "诊断编码", "ICD-10",
            "诊断标准编码，比较值可从 ICD-10 标准字典选择"));
        f.add(field(CAT_DIAGNOSIS, "诊断", "Condition", "conditions[].codeSystem", "诊断编码系统", "string",
            null, null));
        f.add(field(CAT_DIAGNOSIS, "诊断", "Condition", "conditions[].displayName", "诊断名称", "string", null,
            null));
        f.add(field(CAT_DIAGNOSIS, "诊断", "Condition", "conditions[].severity", "严重程度", "string", null,
            null));
        f.add(field(CAT_DIAGNOSIS, "诊断", "Condition", "conditions[].onsetTime", "发病时间", "date", null,
            null));

        // 医嘱信息 · 用药医嘱
        f.add(codeField(CAT_ORDER, "用药医嘱", "Medication", "medications[].code", "药品编码", "ATC",
            "药品标准编码，比较值可从 ATC 标准字典选择"));
        f.add(field(CAT_ORDER, "用药医嘱", "Medication", "medications[].displayName", "药品名称", "string",
            null, null));
        f.add(field(CAT_ORDER, "用药医嘱", "Medication", "medications[].dose", "剂量", "number", null, null));
        f.add(field(CAT_ORDER, "用药医嘱", "Medication", "medications[].doseUnit", "剂量单位", "string", null,
            null));
        f.add(field(CAT_ORDER, "用药医嘱", "Medication", "medications[].route", "给药途径", "string", null,
            null));
        f.add(field(CAT_ORDER, "用药医嘱", "Medication", "medications[].frequency", "频次", "string", null,
            null));
        f.add(field(CAT_ORDER, "用药医嘱", "Medication", "medications[].prescriptionStatus", "处方状态",
            "string", null, "如 ACTIVE，用于在用药物判断"));
        f.add(field(CAT_ORDER, "用药医嘱", "Medication", "medications[].eventTime", "开立时间", "date", null,
            null));
        // 医嘱信息 · 手术操作
        f.add(codeField(CAT_ORDER, "手术操作", "Procedure", "procedures[].code", "手术操作编码", "ICD-9-CM-3",
            "手术操作标准编码，比较值可从 ICD-9-CM-3 标准字典选择"));
        f.add(field(CAT_ORDER, "手术操作", "Procedure", "procedures[].displayName", "手术操作名称", "string",
            null, null));
        f.add(field(CAT_ORDER, "手术操作", "Procedure", "procedures[].anesthesiaType", "麻醉方式", "string",
            null, null));
        f.add(field(CAT_ORDER, "手术操作", "Procedure", "procedures[].performedAt", "手术时间", "date", null,
            "可作路径时钟基准事件"));

        // 检验检查 · 检验/体征结果
        f.add(codeField(CAT_LAB_EXAM, "检验/体征结果", "Observation", "observations[].code", "检验/体征编码",
            "LOINC", "检验/体征项目标准编码，比较值可从 LOINC 标准字典选择"));
        f.add(field(CAT_LAB_EXAM, "检验/体征结果", "Observation", "observations[].displayName", "检验/体征名称",
            "string", null, null));
        f.add(field(CAT_LAB_EXAM, "检验/体征结果", "Observation", "observations[].valueNumeric", "数值结果",
            "number", null, null));
        f.add(field(CAT_LAB_EXAM, "检验/体征结果", "Observation", "observations[].valueString", "文本结果",
            "string", null, null));
        f.add(field(CAT_LAB_EXAM, "检验/体征结果", "Observation", "observations[].unit", "单位", "string", null,
            "数值结果单位，用于单位换算比较"));
        f.add(field(CAT_LAB_EXAM, "检验/体征结果", "Observation", "observations[].referenceRange", "参考范围",
            "string", null, "支持参考范围算子"));
        f.add(field(CAT_LAB_EXAM, "检验/体征结果", "Observation", "observations[].criticalFlag", "危急值标记",
            "string", null, "支持危急值算子"));
        f.add(field(CAT_LAB_EXAM, "检验/体征结果", "Observation", "observations[].eventTime", "发生时间", "date",
            null, "用于时间窗 / 趋势算子"));
        // 检验检查 · 检查报告
        f.add(field(CAT_LAB_EXAM, "检查报告", "DiagnosticReport", "diagnosticReports[].reportType", "报告类型",
            "string", null, null));
        f.add(field(CAT_LAB_EXAM, "检查报告", "DiagnosticReport", "diagnosticReports[].conclusion", "报告结论",
            "string", null, null));
        f.add(field(CAT_LAB_EXAM, "检查报告", "DiagnosticReport", "diagnosticReports[].signedAt", "报告签发时间",
            "date", null, null));

        // 护理评估
        f.add(field(CAT_NURSING, "护理评估", "NursingAssessment", "nursingAssessments[].assessmentType",
            "评估类型", "string", null, null));
        f.add(field(CAT_NURSING, "护理评估", "NursingAssessment", "nursingAssessments[].riskLevel", "风险等级",
            "string", null, null));
        f.add(field(CAT_NURSING, "护理评估", "NursingAssessment", "nursingAssessments[].status", "评估状态",
            "string", null, null));
        f.add(field(CAT_NURSING, "护理评估", "NursingAssessment", "nursingAssessments[].eventTime", "评估时间",
            "date", null, null));

        // 随访信息
        f.add(field(CAT_FOLLOWUP, "随访计划", "FollowUp", "followUps[].planType", "随访类型", "string", null,
            null));
        f.add(field(CAT_FOLLOWUP, "随访计划", "FollowUp", "followUps[].plannedAt", "计划随访时间", "date", null,
            null));
        f.add(field(CAT_FOLLOWUP, "随访计划", "FollowUp", "followUps[].abnormalFlag", "异常标记", "string", null,
            null));

        // 费用医保
        f.add(field(CAT_COST, "费用", "Claim", "claims[].drgCode", "DRG 分组编码", "string", null, null));
        f.add(field(CAT_COST, "费用", "Claim", "claims[].totalCost", "总费用", "number", null, null));
        f.add(field(CAT_COST, "费用", "Claim", "claims[].insurancePaid", "医保支付", "number", null, null));

        // 诊疗计划与文书
        f.add(field(CAT_PLAN_DOC, "路径计划", "CarePlan", "carePlans[].pathwayId", "所属路径", "string", null,
            null));
        f.add(field(CAT_PLAN_DOC, "路径计划", "CarePlan", "carePlans[].currentNodeId", "当前路径节点", "string",
            null, null));
        f.add(field(CAT_PLAN_DOC, "路径计划", "CarePlan", "carePlans[].varianceCode", "变异编码", "string", null,
            null));
        f.add(field(CAT_PLAN_DOC, "路径计划", "CarePlan", "carePlans[].plannedFinishAt", "计划完成时间", "date",
            null, null));
        f.add(field(CAT_PLAN_DOC, "病历文书", "Document", "documents[].documentType", "文书类型", "string", null,
            null));
        f.add(field(CAT_PLAN_DOC, "病历文书", "Document", "documents[].signedAt", "文书签署时间", "date", null,
            null));

        return List.copyOf(f);
    }

    private static ContextFieldDescriptor field(
        String category, String group, String resourceType, String fieldPath, String displayName,
        String dataType, String unit, String description) {
        return new ContextFieldDescriptor(
            category, group, resourceType, fieldPath, displayName, dataType, unit, null, description,
            "PLATFORM", null, false);
    }

    private static ContextFieldDescriptor codeField(
        String category, String group, String resourceType, String fieldPath, String displayName,
        String codeSystem, String description) {
        return new ContextFieldDescriptor(
            category, group, resourceType, fieldPath, displayName, "code", null, codeSystem, description,
            "PLATFORM", null, false);
    }

    /** 求值期计算的派生字段（如 {@code patient.age}），{@code derived=true}，不可前台维护。 */
    private static ContextFieldDescriptor derivedField(
        String category, String group, String resourceType, String fieldPath, String displayName,
        String dataType, String unit, String description) {
        return new ContextFieldDescriptor(
            category, group, resourceType, fieldPath, displayName, dataType, unit, null, description,
            "PLATFORM", null, true);
    }

    /**
     * 查询字段目录，可按资源类型与关键词过滤。
     *
     * @param resourceType 资源类型过滤（忽略大小写；空表示不限）
     * @param keyword      关键词过滤，匹配字段路径、中文名、业务域或分组（空表示不限）
     */
    public List<ContextFieldDescriptor> query(String resourceType, String keyword) {
        String type = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return FIELDS.stream()
            .filter(f -> type.isEmpty() || f.resourceType().toLowerCase(Locale.ROOT).equals(type))
            .filter(f -> kw.isEmpty()
                || f.fieldPath().toLowerCase(Locale.ROOT).contains(kw)
                || f.displayName().toLowerCase(Locale.ROOT).contains(kw)
                || f.category().toLowerCase(Locale.ROOT).contains(kw)
                || f.group().toLowerCase(Locale.ROOT).contains(kw))
            .toList();
    }
}
