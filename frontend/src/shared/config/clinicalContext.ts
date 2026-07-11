import type { ContextSnapshotCreatePayload, FrontdeskEncounterType } from "@/shared/api/hooks";

export const frontdeskEncounterTypeOptions: Array<{
  label: string;
  value: FrontdeskEncounterType;
}> = [
  { label: "门诊复诊", value: "OUTPATIENT" },
  { label: "住院就诊", value: "INPATIENT" },
  { label: "急诊就诊", value: "ED" },
  { label: "随访回收", value: "FOLLOWUP" },
];

export const contextRiskLevelOptions: Array<{
  label: string;
  value: ContextSnapshotCreatePayload["riskLevel"];
}> = [
  { label: "低风险", value: "LOW" },
  { label: "中风险", value: "MEDIUM" },
  { label: "高风险", value: "HIGH" },
];

export const defaultContextSnapshotFormValues: Partial<ContextSnapshotCreatePayload> = {
  encounterType: "OUTPATIENT",
  riskLevel: "MEDIUM",
  reason: "患者 360 已完成身份核查，建立当前就诊上下文用于随访、路径和 CDSS。",
};
