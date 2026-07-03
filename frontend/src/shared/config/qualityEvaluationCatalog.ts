export type QualityEvaluationOption = {
  value: string;
  label: string;
};

export const qualityTimeWindowLabels: Record<string, string> = {
  "DISCHARGE+24H": "出院后 24 小时",
  "ADMISSION+24H": "入院后 24 小时",
  "SURGERY+48H": "手术后 48 小时",
  "VISIT+7D": "就诊后 7 天",
};

export const qualityOrganizationScopeLabels: Record<string, string> = {
  HOSPITAL: "当前医院",
  CURRENT_HOSPITAL: "当前医院",
  "p5-hospital": "当前医院",
  DEPARTMENT: "当前科室",
  CURRENT_DEPARTMENT: "当前科室",
  TENANT: "当前服务机构",
  全院: "全院",
  本科室: "本科室",
  当前服务机构: "当前服务机构",
};

export const qualityTimeWindowOptions: QualityEvaluationOption[] = Object.entries(
  qualityTimeWindowLabels,
).map(([value, label]) => ({ value, label }));

export const qualityOrganizationScopeOptions: QualityEvaluationOption[] = [
  "全院",
  "本科室",
  "当前服务机构",
].map((value) => ({ value, label: qualityOrganizationScopeLabels[value] }));
