package com.medkernel.engine.factory;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 全专业领域标准资产模板注册表（AIK-STD-12 FR-1）。
 *
 * <p>确定性代码态目录，不建表、不做租户自定义（无消费者需要）。覆盖术语/规则/路径/推荐/指标/随访/
 * 护理/医技项目/中医/医保 + 指南/药品/诊断 + 评分/计算器骨架。章节为既有专业文书结构标准，
 * 供生产/审核对照完整性，不预填医学内容（守铁律 #1，正文须按真实来源填充）。
 */
@Service
public class ProfessionalAssetTemplateRegistry {

    private static final List<ProfessionalAssetTemplate> TEMPLATES = List.of(
        // —— 医学领域型（assetType=KNOWLEDGE × domain）：知识审核台按 identity.domain 匹配 ——
        knowledge("GUIDELINE", "指南共识", KnowledgeDomain.GUIDELINE,
            req("recommendation", "推荐意见"), req("evidence", "证据等级"),
            opt("population", "适用人群"), opt("implementation", "实施要点"), req("references", "参考文献")),
        knowledge("DRUG", "药品说明书", KnowledgeDomain.DRUG,
            req("indication", "适应症"), req("dosage", "用法用量"), req("contraindication", "禁忌"),
            req("adverse", "不良反应"), opt("interaction", "药物相互作用"),
            opt("precaution", "注意事项"), opt("special_population", "特殊人群用药")),
        knowledge("NURSING", "护理", KnowledgeDomain.NURSING,
            req("assessment", "护理评估"), req("diagnosis", "护理诊断"), req("goal", "护理目标"),
            req("intervention", "护理措施"), req("evaluation", "护理评价")),
        knowledge("DIAGNOSTIC_ITEM", "医技项目说明书", KnowledgeDomain.DIAGNOSTIC_ITEM,
            req("item_definition", "项目定义"), req("preparation", "检查前准备"),
            req("reference_basis", "参考区间与方法学依据"), req("limitations", "局限与干扰因素"),
            req("clinical_meaning", "临床意义"), opt("recheck", "复查注意事项"),
            req("references", "参考来源")),
        knowledge("TCM", "中医药", KnowledgeDomain.TCM,
            req("syndrome", "病名证候"), req("differentiation", "辨证分型"), req("therapy", "治法"),
            req("prescription", "方药"), opt("technique", "适宜技术"), opt("regimen", "调护")),
        knowledge("POLICY", "医保政策", KnowledgeDomain.POLICY,
            req("basis", "政策依据"), req("scope", "适用范围"), opt("admission", "准入条件"),
            req("payment", "支付标准"), opt("execution", "执行要点")),
        knowledge("DIAGNOSIS", "诊断", KnowledgeDomain.DIAGNOSIS,
            req("criteria", "诊断标准"), req("differential", "鉴别诊断"),
            opt("staging", "分型分期"), opt("indication", "诊疗指针")),
        knowledge("PATHWAY_KNOWLEDGE", "路径性知识", KnowledgeDomain.PATHWAY_KNOWLEDGE,
            req("entry", "进入条款"), req("staging", "分型分期"), req("branch", "分支条款"),
            req("exit", "退出条款"), opt("variance", "变异处理"), req("references", "参考来源")),
        knowledge("PROTOCOL", "院内制度", KnowledgeDomain.PROTOCOL,
            req("basis", "制度依据"), req("scope", "适用范围"), req("responsibilities", "职责分工"),
            req("process", "执行流程"), opt("exceptions", "例外处置"), req("source", "来源依据")),
        knowledge("LITERATURE", "学术文献", KnowledgeDomain.LITERATURE,
            req("question", "研究问题"), req("design", "研究设计"), req("population", "研究人群"),
            opt("intervention", "干预或暴露"), req("outcome", "结局"), req("limitations", "局限性"),
            req("references", "文献来源")),
        knowledge("OTHER", "其他知识", KnowledgeDomain.OTHER,
            req("definition", "主题定义"), req("scope", "适用范围"), req("content", "结构化内容"),
            req("source", "来源依据")),
        // —— 结构型（domain 空）：目录完整性，供编著/生产工作台 ——
        structural("TERMINOLOGY", "术语", VersionedAssetType.TERMINOLOGY,
            req("term", "标准术语"), req("code", "编码体系"), opt("synonym", "同义词"),
            opt("mapping", "映射关系"), req("source", "术语来源")),
        structural("RULE", "规则", VersionedAssetType.RULE,
            req("trigger", "触发条件"), req("logic", "判定逻辑"), req("action", "动作建议"),
            req("risk", "风险级别"), opt("redline", "红线标识"),
            req("test_cases", "阳性/阴性/边界验证病例"), req("source", "来源依据")),
        structural("PATHWAY", "路径", VersionedAssetType.PATHWAY,
            req("admission", "准入标准"), req("branch", "分型分支"), req("stage", "阶段节点"),
            req("exit", "退出条件"), opt("variance", "变异处理")),
        structural("EVALUATION", "指标", VersionedAssetType.EVALUATION,
            req("definition", "指标定义"), req("formula", "计算口径"), req("data_source", "数据来源"),
            req("threshold", "阈值标准"), opt("cycle", "评价周期")),
        structural("FOLLOWUP", "随访", VersionedAssetType.FOLLOWUP,
            req("population", "随访人群"), req("cycle", "随访周期"), req("item", "随访项目"),
            opt("alert", "异常预警"), opt("return_indication", "回院指针")),
        structural("FORMULA", "评分量表与计算器", VersionedAssetType.FORMULA,
            req("inputs", "输入项与单位"), req("algorithm", "算法表达式"),
            req("thresholds", "分级阈值"), req("test_vectors", "复算测试向量"), req("source", "来源依据")),
        structural("FIELD_CATALOG", "医疗数据元与字段目录", VersionedAssetType.FIELD_CATALOG,
            req("data_element_id", "稳定数据元标识"), req("name_zh", "中文名称"),
            req("definition", "定义"), req("data_type", "数据类型"), req("cardinality", "基数"),
            opt("unit", "单位"), opt("value_domain", "值域"), req("privacy_level", "隐私分级"),
            req("source_manifest", "权威来源发行清单")),
        structural("VALUE_SET", "值集", VersionedAssetType.VALUE_SET,
            req("canonical_id", "稳定值集标识"), req("code_system", "编码体系"),
            req("members", "成员清单"), req("version", "官方版本"), opt("effective_period", "有效期"),
            req("source_manifest", "权威来源发行清单")),
        structural("SAFETY", "安全红线", VersionedAssetType.SAFETY,
            req("scenario", "适用场景"), req("trigger", "触发条件"), req("redline", "红线判定"),
            req("action", "受控动作"), req("override_policy", "越权处置策略"),
            req("test_cases", "阳性/阴性/边界验证病例"), req("source", "来源依据")),
        structural("CDSS_RISK", "CDSS 风险矩阵", VersionedAssetType.CDSS_RISK,
            req("risk_level", "风险等级"), req("review_requirement", "审核要求"),
            req("response_sla", "响应时限"), req("escalation", "升级策略"), req("source", "来源依据")),
        structural("ORDER_SET", "医嘱套餐", VersionedAssetType.ORDER_SET,
            req("indication", "适用指征"), req("orders", "医嘱项"), req("sequencing", "顺序与依赖"),
            req("contraindications", "禁忌"), req("confirmation", "医师确认要求"),
            req("source", "来源依据")),
        structural("ACTION_CARD", "临床提示卡", VersionedAssetType.ACTION_CARD,
            req("scenario", "建议场景"), req("trigger", "触发条件"),
            req("summary", "摘要"), req("detail", "详细说明"),
            req("suggestions", "医生可选操作"), opt("evidence", "证据强度"),
            req("confirmation", "医师确认要求"),
            req("source", "来源依据"))
    );

    public List<ProfessionalAssetTemplate> listAll() {
        return TEMPLATES;
    }

    public Optional<ProfessionalAssetTemplate> findByAssetTypeAndDomain(VersionedAssetType assetType,
            KnowledgeDomain domain) {
        return TEMPLATES.stream()
            .filter(t -> t.assetType() == assetType && t.knowledgeDomain() == domain)
            .findFirst();
    }

    private static ProfessionalAssetTemplate knowledge(String code, String name, KnowledgeDomain domain,
            TemplateSection... sections) {
        return new ProfessionalAssetTemplate(code, name, VersionedAssetType.KNOWLEDGE, domain, List.of(sections));
    }

    private static ProfessionalAssetTemplate structural(String code, String name, VersionedAssetType type,
            TemplateSection... sections) {
        return new ProfessionalAssetTemplate(code, name, type, null, List.of(sections));
    }

    private static TemplateSection req(String key, String label) {
        return new TemplateSection(key, label, true, label + "（必备结构）");
    }

    private static TemplateSection opt(String key, String label) {
        return new TemplateSection(key, label, false, label + "（建议结构）");
    }
}
