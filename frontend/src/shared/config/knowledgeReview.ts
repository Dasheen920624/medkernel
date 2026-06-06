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
  { value: "WITHDRAWN", label: "已撤回" },
  { value: "ARCHIVED", label: "已归档" },
];
