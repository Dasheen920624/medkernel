/**
 * 医学内容域与版本资产类型的前端单一真相源。
 *
 * 患者报告解读属于运行能力，不是知识内容域；各页面只能从本目录派生选项，
 * 不得重新维护不完整枚举。
 */
export const KNOWLEDGE_DOMAINS = [
  "GUIDELINE",
  "DRUG",
  "PATHWAY_KNOWLEDGE",
  "NURSING",
  "DIAGNOSTIC_ITEM",
  "TCM",
  "PROTOCOL",
  "POLICY",
  "LITERATURE",
  "OTHER",
  "DIAGNOSIS",
] as const;

export type KnowledgeDomain = (typeof KNOWLEDGE_DOMAINS)[number];

export const KNOWLEDGE_DOMAIN_OPTIONS: Array<{
  value: KnowledgeDomain;
  label: string;
}> = [
  { value: "GUIDELINE", label: "指南 / 共识" },
  { value: "DRUG", label: "药品说明书" },
  { value: "PATHWAY_KNOWLEDGE", label: "路径性知识" },
  { value: "NURSING", label: "护理知识" },
  { value: "DIAGNOSTIC_ITEM", label: "医技项目说明书" },
  { value: "TCM", label: "中医药" },
  { value: "PROTOCOL", label: "院内制度" },
  { value: "POLICY", label: "政策" },
  { value: "LITERATURE", label: "学术文献" },
  { value: "OTHER", label: "其他知识" },
  { value: "DIAGNOSIS", label: "诊断知识" },
];

export const ENGINE_ASSET_TYPES = [
  "KNOWLEDGE",
  "TERMINOLOGY",
  "RULE",
  "PATHWAY",
  "EVALUATION",
  "FOLLOWUP",
  "FIELD_CATALOG",
  "SAFETY",
  "CDSS_RISK",
  "VALUE_SET",
  "FORMULA",
  "ORDER_SET",
  "ACTION_CARD",
] as const;

export type EngineAssetType = (typeof ENGINE_ASSET_TYPES)[number];

export const ENGINE_ASSET_LABELS: Readonly<Record<EngineAssetType, string>> = {
  KNOWLEDGE: "知识",
  TERMINOLOGY: "术语与字典",
  RULE: "规则",
  PATHWAY: "路径",
  EVALUATION: "评价指标",
  FOLLOWUP: "随访",
  FIELD_CATALOG: "字段目录",
  SAFETY: "临床安全",
  CDSS_RISK: "CDSS 风险矩阵",
  VALUE_SET: "值集",
  FORMULA: "公式与量表",
  ORDER_SET: "医嘱套餐",
  ACTION_CARD: "临床提示卡",
};

export const ENGINE_ASSET_OPTIONS = ENGINE_ASSET_TYPES.map((value) => ({
  value,
  label: ENGINE_ASSET_LABELS[value],
}));

/** 已有独立编著工作台并可进入统一资产库的类型。 */
export const AUTHORING_ASSET_TYPES = [
  "RULE",
  "PATHWAY",
  "FOLLOWUP",
] as const satisfies readonly EngineAssetType[];

/** 发布治理必须能识别统一版本底座的全部资产类型。 */
export const RELEASE_ASSET_TYPES = ENGINE_ASSET_TYPES;

/** 可进入平台标准版本或机构生效版本的正式配置资产。 */
export const RUNTIME_ASSET_TYPES = ENGINE_ASSET_TYPES;

export const RUNTIME_ASSET_OPTIONS = RUNTIME_ASSET_TYPES.map((value) => ({
  value,
  label: ENGINE_ASSET_LABELS[value],
}));
