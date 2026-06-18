package com.medkernel.engine.domainfacade;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * X-DOMAIN 领域门面组合目录服务。
 *
 * <p>本服务是 Phase 7 T7.1 的代码落点：把 17 张 X-DOMAIN 卡映射为可查询、可测试的组合目录。
 * 它只声明复用的 RULE/PATHWAY/KNOWLEDGE/CDSS/EMBED/EVALUATION/FOLLOWUP 等既有链路，不生产医学内容、
 * 不新增领域专属业务引擎。
 */
@Service
public class DomainFacadeCatalogService {

    private static final boolean B0_READY = true;
    private static final boolean MODEL_OPTIONAL = true;
    private static final boolean NO_CLINICAL_CONTENT = false;
    private static final boolean NO_NEW_ENGINE = false;

    private static final List<DomainFacadeDefinition> DEFINITIONS = List.of(
        domain("NURSING-01", "护理专业", scenarios("S20", "S35"),
            engines(DomainFacadeEngine.RULE, DomainFacadeEngine.PATHWAY, DomainFacadeEngine.CDSS,
                DomainFacadeEngine.EVALUATION, DomainFacadeEngine.FOLLOWUP, DomainFacadeEngine.KNOWLEDGE),
            cards("RULE-01", "PATH-01", "CDSS-01", "EVAL-01", "FOLLOW-01", "KNOWGEN-09", "SVC-CLINICAL-03"),
            workflows("分级", "评估", "计划复评", "交班质控"), false),
        domain("REPORT-01", "医技报告解读", scenarios("S17", "S36"),
            engines(DomainFacadeEngine.CDSS, DomainFacadeEngine.RULE, DomainFacadeEngine.EMBED,
                DomainFacadeEngine.KNOWLEDGE),
            cards("CDSS-01", "RULE-01", "EMBED-01", "KNOWGEN-10", "KNOWGEN-04"),
            workflows("报告解读", "危急值闭环", "趋势判读", "工作站嵌入"), false),
        domain("POC-KNOW-01", "床旁知识查阅", scenarios("S37"),
            engines(DomainFacadeEngine.KNOWLEDGE, DomainFacadeEngine.EMBED),
            cards("KNOWGEN-11", "EMBED-01", "LLM-06", "LLM-05"),
            workflows("关键词检索", "现行权威过滤", "低打扰嵌入"), false),
        domain("PHARMACY-01", "药事与药物治疗", scenarios("S18", "S31"),
            engines(DomainFacadeEngine.RULE, DomainFacadeEngine.CDSS, DomainFacadeEngine.SAFETY,
                DomainFacadeEngine.DOSAGE_CALCULATION, DomainFacadeEngine.KNOWLEDGE),
            cards("RULE-01", "CDSS-01", "OPT-04", "MED-C2", "KNOWGEN-01", "KNOWGEN-02", "KNOWGEN-04"),
            workflows("用药审查", "剂量校验", "抗菌分级", "处方点评"), false),
        domain("CRITICAL-01", "急诊重症与生命支持", scenarios("S19", "S24", "S27"),
            engines(DomainFacadeEngine.RULE, DomainFacadeEngine.PATHWAY, DomainFacadeEngine.CDSS,
                DomainFacadeEngine.SAFETY, DomainFacadeEngine.KNOWLEDGE),
            cards("RULE-01", "PATH-01", "CDSS-01", "OPT-04", "KNOWGEN-04", "KNOWGEN-05"),
            workflows("分诊", "恶化预警", "危急值闭环", "重症路径"), false),
        domain("SPECIAL-POP-01", "妇产儿科老年特殊人群", scenarios("S28"),
            engines(DomainFacadeEngine.RULE, DomainFacadeEngine.PATHWAY, DomainFacadeEngine.CDSS,
                DomainFacadeEngine.SAFETY, DomainFacadeEngine.DOSAGE_CALCULATION, DomainFacadeEngine.KNOWLEDGE),
            cards("MED-C2", "OPT-04", "PATH-01", "KNOWGEN-19"),
            workflows("人群标识", "人群剂量", "特殊禁忌", "专科路径"), false),
        domain("PERIOP-01", "围术期麻醉输血介入", scenarios("S26", "S33"),
            engines(DomainFacadeEngine.PATHWAY, DomainFacadeEngine.RULE, DomainFacadeEngine.CDSS,
                DomainFacadeEngine.SAFETY, DomainFacadeEngine.KNOWLEDGE),
            cards("PATH-01", "RULE-01", "CDSS-01", "OPT-04", "KNOWGEN-05", "KNOWGEN-23"),
            workflows("围术路径", "安全核查", "用血器械准入", "时序校验"), false),
        domain("ONCO-RENAL-01", "肿瘤透析移植生殖日间", scenarios("S29"),
            engines(DomainFacadeEngine.PATHWAY, DomainFacadeEngine.CDSS, DomainFacadeEngine.FOLLOWUP,
                DomainFacadeEngine.DOSAGE_CALCULATION, DomainFacadeEngine.KNOWLEDGE),
            cards("PATH-01", "CDSS-01", "FOLLOW-01", "MED-C2", "KNOWGEN-05"),
            workflows("周期方案", "周期监测", "并发症预警", "长期随访"), false),
        domain("ALLIED-CARE-01", "康复营养心理疼痛安宁照护", scenarios("S38"),
            engines(DomainFacadeEngine.EVALUATION, DomainFacadeEngine.PATHWAY, DomainFacadeEngine.FOLLOWUP,
                DomainFacadeEngine.KNOWLEDGE),
            cards("EVAL-01", "PATH-01", "FOLLOW-01", "KNOWGEN-16"),
            workflows("专科评估", "照护计划", "转介", "连续照护"), false),
        domain("TCM-HEALTH-01", "中医药中西医结合健康管理", scenarios("S39"),
            engines(DomainFacadeEngine.PATHWAY, DomainFacadeEngine.RULE, DomainFacadeEngine.CDSS,
                DomainFacadeEngine.KNOWLEDGE),
            cards("PATH-01", "RULE-01", "CDSS-01", "KNOWGEN-12"),
            workflows("辨证链", "独立中医路径", "结合分支", "方药安全"), false),
        domain("INFECTION-PH-01", "院感公卫预防职业健康", scenarios("S21"),
            engines(DomainFacadeEngine.RULE, DomainFacadeEngine.CDSS, DomainFacadeEngine.FOLLOWUP,
                DomainFacadeEngine.KNOWLEDGE),
            cards("RULE-01", "CDSS-01", "FOLLOW-01", "KNOWGEN-14"),
            workflows("感染监测", "报告卡预填", "法定上报", "干预闭环"), false),
        domain("PRIMARY-CARE-01", "基层慢病双向转诊", scenarios("S30"),
            engines(DomainFacadeEngine.PATHWAY, DomainFacadeEngine.FOLLOWUP, DomainFacadeEngine.ORGANIZATION,
                DomainFacadeEngine.KNOWLEDGE),
            cards("PATH-01", "FOLLOW-01", "BASE-01"),
            workflows("慢病分层", "双向转诊", "复诊提醒", "连续随访"), false),
        domain("REGION-COLLAB-01", "医技互认远程协同", scenarios("S40"),
            engines(DomainFacadeEngine.INTEGRATION, DomainFacadeEngine.KNOWLEDGE),
            cards("INTEG-01", "OPT-01", "EVID-01"),
            workflows("结果互认", "来源证据", "远程协同", "FHIR 互操作"), false),
        domain("SPECIALTY-EXT-01", "扩展专科", scenarios("S33", "S34"),
            engines(DomainFacadeEngine.RULE, DomainFacadeEngine.PATHWAY, DomainFacadeEngine.CDSS,
                DomainFacadeEngine.AUTHORING_TEMPLATE),
            cards("RULE-01", "PATH-01", "CDSS-01", "AIK-STD-12"),
            workflows("专科实例化", "资产接入点", "专科规则路径", "缺资产诚实空态"), true),
        domain("RWD-01", "科研真实世界数据服务", scenarios("S34"),
            engines(DomainFacadeEngine.DATA_SERVICE, DomainFacadeEngine.EVALUATION, DomainFacadeEngine.KNOWLEDGE),
            cards("SYS-06", "EVAL-01", "OPT-09"),
            workflows("脱敏队列", "指标数据集", "伦理授权", "最小化审计"), false),
        servicePackage("SVC-DOMAIN-01", "专病路径服务包", scenarios("S25", "S26", "S27", "S28", "S29", "S30", "S39"),
            cards("SYS-04", "PKG-01"),
            members("CRITICAL-01", "PERIOP-01", "ONCO-RENAL-01", "SPECIAL-POP-01", "TCM-HEALTH-01",
                "PRIMARY-CARE-01", "INFECTION-PH-01")),
        servicePackage("SVC-DOMAIN-02", "专业协同服务包", scenarios("S17", "S18", "S20", "S31", "S34", "S35", "S36", "S37", "S38", "S40"),
            cards("SYS-04", "PKG-01"),
            members("NURSING-01", "PHARMACY-01", "REPORT-01", "POC-KNOW-01", "ALLIED-CARE-01", "RWD-01",
                "REGION-COLLAB-01"))
    );

    /** 返回 17 张 X-DOMAIN 卡的组合目录，顺序与总计划一致。 */
    public List<DomainFacadeDefinition> listDefinitions() {
        return DEFINITIONS;
    }

    /** 按门面代码查询定义，不存在则返回标准 404。 */
    public DomainFacadeDefinition requireDefinition(String code) {
        String normalized = normalize(code);
        return DEFINITIONS.stream()
            .filter(definition -> definition.code().equals(normalized))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "领域门面不存在: " + normalized));
    }

    private static DomainFacadeDefinition domain(String code, String displayName, List<String> scenarios,
            List<DomainFacadeEngine> engines, List<String> cards, List<String> workflows, boolean honestEmpty) {
        return new DomainFacadeDefinition(code, displayName, DomainFacadeKind.DOMAIN, scenarios, engines, cards, workflows,
            List.of(), B0_READY, MODEL_OPTIONAL, NO_CLINICAL_CONTENT, NO_NEW_ENGINE, honestEmpty);
    }

    private static DomainFacadeDefinition servicePackage(String code, String displayName, List<String> scenarios,
            List<String> cards, List<String> members) {
        return new DomainFacadeDefinition(code, displayName, DomainFacadeKind.SERVICE_PACKAGE, scenarios,
            engines(DomainFacadeEngine.PACKAGE), cards, workflows("统一治理", "服务包交付", "单一归属", "B0 一致"),
            members, B0_READY, MODEL_OPTIONAL, NO_CLINICAL_CONTENT, NO_NEW_ENGINE, false);
    }

    private static List<String> scenarios(String... values) {
        return List.of(values);
    }

    private static List<DomainFacadeEngine> engines(DomainFacadeEngine... values) {
        return List.of(values);
    }

    private static List<String> cards(String... values) {
        return List.of(values);
    }

    private static List<String> workflows(String... values) {
        return List.of(values);
    }

    private static List<String> members(String... values) {
        return List.of(values);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
