import { ENGINE_ASSET_LABELS, type EngineAssetType } from "./assetCatalog";

export const DECLARATIVE_ASSET_TYPES = [
  "VALUE_SET",
  "FORMULA",
  "ORDER_SET",
  "ACTION_CARD",
] as const satisfies readonly EngineAssetType[];

export type DeclarativeAuthoringAssetType = (typeof DECLARATIVE_ASSET_TYPES)[number];

export const DECLARATIVE_ASSET_TYPE_OPTIONS = DECLARATIVE_ASSET_TYPES.map((value) => ({
  value,
  label: ENGINE_ASSET_LABELS[value],
}));

export const DECLARATIVE_FORMULA_OPTIONS = [
  { value: "CKD_EPI_2021_EGFR", label: "eGFR（CKD-EPI 2021）" },
  { value: "COCKCROFT_GAULT_CRCL", label: "肌酐清除率（Cockcroft-Gault）" },
  { value: "MOSTELLER_BSA", label: "体表面积（Mosteller）" },
  { value: "BMI", label: "体质指数（BMI）" },
];

export const ORDER_SET_ITEM_TYPE_OPTIONS = [
  { value: "MEDICATION", label: "药品" },
  { value: "LAB", label: "检验项目" },
  { value: "IMAGING", label: "影像检查" },
  { value: "PROCEDURE", label: "治疗/操作项目" },
  { value: "NURSING", label: "护理项目" },
];

export const ACTION_CARD_SUGGESTION_TYPE_OPTIONS = [
  { value: "NAVIGATE", label: "打开相关页面" },
  { value: "OPEN_FORM", label: "打开记录表单" },
  { value: "SUGGEST_ORDER", label: "建议医嘱" },
  { value: "ACKNOWLEDGE", label: "记录已知晓" },
];

export const ACTION_CARD_ACTION_OPTIONS = [
  { value: "INFO", label: "信息提示" },
  { value: "REMIND", label: "普通提醒" },
  { value: "STRONG_REMINDER", label: "强提醒" },
  { value: "BLOCK", label: "红线拦截" },
  { value: "SUGGEST_ORDER", label: "建议医嘱" },
  { value: "AUTO_DOCUMENT", label: "辅助记录" },
];

export const ACTION_CARD_RISK_LEVEL_OPTIONS = [
  { value: "LOW", label: "低风险" },
  { value: "MEDIUM", label: "中风险" },
  { value: "HIGH", label: "高风险" },
  { value: "CRITICAL", label: "红线风险" },
];

export const ACTION_CARD_INDICATOR_OPTIONS = [
  { value: "info", label: "信息提示" },
  { value: "warning", label: "需要关注" },
  { value: "critical", label: "必须处理" },
];
