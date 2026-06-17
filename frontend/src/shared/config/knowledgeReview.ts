import type { KnowledgeDomain, KnowledgeIdentityStatus } from "@/shared/api/hooks";

export const KNOWLEDGE_DOMAIN_OPTIONS: Array<{ value: KnowledgeDomain; label: string }> = [
  { value: "GUIDELINE", label: "指南 / 共识" },
  { value: "DRUG", label: "药品说明书" },
  { value: "PATHWAY_KNOWLEDGE", label: "路径知识" },
  { value: "DIAGNOSIS", label: "诊断知识" },
  { value: "PROTOCOL", label: "院内制度" },
  { value: "POLICY", label: "政策" },
];

export const KNOWLEDGE_IDENTITY_STATUS_OPTIONS: Array<{
  value: KnowledgeIdentityStatus;
  label: string;
}> = [
  { value: "ACTIVE", label: "有效身份" },
  { value: "DEPRECATED", label: "迁移宽限期" },
  { value: "WITHDRAWN", label: "已撤回" },
  { value: "ARCHIVED", label: "已归档" },
];

export const KNOWLEDGE_QUALITY_GATE_OPTIONS = [
  { label: "结构校验", value: "schemaValid" },
  { label: "术语绑定", value: "terminologyBindingComplete" },
  { label: "依赖完整性", value: "dependencyIntegrityVerified" },
  { label: "安全单调性", value: "safetyMonotonicityVerified" },
  { label: "影响模拟", value: "impactSimulationPassed" },
  { label: "同行复核", value: "peerReviewSigned" },
];

export const KNOWLEDGE_TRIAGE_STATE_META: Array<{
  state: string;
  label: string;
  color: "default" | "success" | "processing" | "warning" | "error";
}> = [
  { state: "NEW_ASSET", label: "新资产", color: "success" },
  { state: "DUPLICATE", label: "重复", color: "default" },
  { state: "MINOR_REVISION", label: "小修订", color: "processing" },
  { state: "MAJOR_UPGRADE", label: "重大升级", color: "warning" },
  { state: "CONFLICT", label: "冲突仲裁", color: "error" },
  { state: "DOWNGRADE", label: "降级风险", color: "warning" },
  { state: "DEPRECATION", label: "废止退役", color: "warning" },
  { state: "UNCERTAIN", label: "人工分流", color: "processing" },
];
