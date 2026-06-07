export const CLINICAL_TRIGGER_POINT_OPTIONS = [
  { value: "patient-view", label: "查看患者" },
  { value: "order-sign", label: "签署医嘱" },
  { value: "medication-prescribe", label: "开立用药" },
  { value: "result-review", label: "审核结果" },
  { value: "discharge-sign", label: "签署出院" },
  { value: "followup-alert", label: "随访提醒" },
] as const;

export type ClinicalTriggerPoint = (typeof CLINICAL_TRIGGER_POINT_OPTIONS)[number]["value"];

const CLINICAL_TRIGGER_POINTS = new Set<string>(
  CLINICAL_TRIGGER_POINT_OPTIONS.map(({ value }) => value),
);

export function isClinicalTriggerPoint(value: unknown): value is ClinicalTriggerPoint {
  return typeof value === "string" && CLINICAL_TRIGGER_POINTS.has(value);
}
