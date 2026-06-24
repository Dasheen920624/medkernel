package com.medkernel.engine.knowledge;

/**
 * 知识资产领域分类。对应 {@code knowledge_identity.domain} CHECK 约束（5 方言一致）。
 *
 * <p>覆盖详细规范 §8.4 / §S29-S40 提到的医疗资产分类：
 * 指南 / 药品说明书 / 路径性知识 / 护理 / 医技项目说明书 / 中医药 / 院内制度 / 政策 / 文献。
 *
 * <p>新增分类只能追加；不得删除已发布枚举值（DB 已有数据会失效）。
 */
public enum KnowledgeDomain {
    /** 指南、共识、专业标准（国家、行业、学会） */
    GUIDELINE,
    /** 药品说明书、药品 GCP、合理用药 */
    DRUG,
    /** 路径性知识：进入/分型/分支/退出条款（与路径模板配对） */
    PATHWAY_KNOWLEDGE,
    /** 护理评估、护理计划、护理风险 */
    NURSING,
    /** 检验、影像、病理、功能等医技项目的可复用说明书知识，不含患者报告解读结果 */
    DIAGNOSTIC_ITEM,
    /** 中医药病名/证候/治法/方药/适宜技术 */
    TCM,
    /** 院内 SOP、手术规范、感控制度 */
    PROTOCOL,
    /** 医保、公卫、行政管理政策 */
    POLICY,
    /** 学术文献（个案、综述、RCT） */
    LITERATURE,
    /** 其他 */
    OTHER,
    /** 诊断知识：疾病诊断标准、鉴别与诊疗指针（CDSS 辅助诊断）。 */
    DIAGNOSIS
}
