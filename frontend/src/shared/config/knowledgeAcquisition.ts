/** AIK-STD-14 公域来源治理字段字典，与后端枚举保持一一对应。 */
export const KNOWLEDGE_ACQUISITION_SOURCE_TYPE_OPTIONS = [
  { value: "GUIDELINE", label: "临床指南" },
  { value: "DRUG_LABEL", label: "药品说明书" },
  { value: "STANDARD", label: "行业／国家标准" },
  { value: "POLICY", label: "医保／公卫／行政政策" },
  { value: "HOSPITAL_PROTOCOL", label: "院内制度／SOP" },
  { value: "TCM_CLASSIC", label: "中医典籍" },
  { value: "LITERATURE", label: "学术文献" },
  { value: "CONSENSUS", label: "专家共识" },
  { value: "OTHER", label: "其他" },
];

export const KNOWLEDGE_ACQUISITION_AUTHORITY_OPTIONS = [
  { value: "A_REGULATION", label: "A 法规／强制监管" },
  { value: "B_GUIDELINE", label: "B 国家或国际指南" },
  { value: "C_CONSENSUS_LITERATURE", label: "C 共识／文献" },
  { value: "D_HOSPITAL", label: "D 院内制度" },
  { value: "E_FEEDBACK", label: "E 反馈／其他" },
];

export const KNOWLEDGE_ACQUISITION_LICENSE_OPTIONS = [
  { value: "PERMITTED", label: "已核验允许用于知识生产" },
  { value: "RESTRICTED", label: "需另行授权" },
  { value: "FORBIDDEN", label: "禁止入库或二次使用" },
];

export const KNOWLEDGE_ACQUISITION_ROBOTS_OPTIONS = [
  { value: "ALLOW_FETCH", label: "robots／ToS 明确允许" },
  { value: "MANUAL_ALLOWED", label: "人工确认允许定向获取" },
  { value: "DISALLOW_FETCH", label: "明确禁止获取" },
];
