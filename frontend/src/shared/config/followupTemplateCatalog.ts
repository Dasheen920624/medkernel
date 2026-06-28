export type FollowupTemplateOption = {
  value: string;
  label: string;
};

export const organizationScopeOptions: FollowupTemplateOption[] = [
  { value: "p5-hospital", label: "当前医院" },
  { value: "p5-respiratory", label: "呼吸与危重症科" },
  { value: "p5-community", label: "医联体随访范围" },
];

export const followupDiseaseOptions: FollowupTemplateOption[] = [
  { value: "COPD", label: "慢阻肺" },
  { value: "HTN", label: "高血压" },
  { value: "DIABETES", label: "糖尿病" },
  { value: "CHF", label: "心衰" },
];

export const questionnaireTemplateOptions: FollowupTemplateOption[] = [
  { value: "FOLLOWUP_QUESTIONNAIRE_DEFAULT", label: "默认出院随访问卷" },
  { value: "FOLLOWUP_QUESTIONNAIRE_REAL_FRONTDESK", label: "真实前台慢病随访问卷" },
  { value: "FOLLOWUP_QUESTIONNAIRE_CARDIO_METABOLIC", label: "心脑代谢随访问卷" },
];

export const followupQuestionOptions: FollowupTemplateOption[] = [
  { value: "dyspnea", label: "呼吸困难变化" },
  { value: "medication_adherence", label: "用药依从性" },
  { value: "warning_signs", label: "异常症状报告" },
];

export const followupSourceOptions: FollowupTemplateOption[] = [
  { value: "FOLLOWUP_TEMPLATE_STANDARD", label: "院内随访制度" },
  { value: "REAL_FRONTDESK_FOLLOWUP_TEMPLATE", label: "真实前台演练随访制度" },
  { value: "DISCHARGE_PLAN_FOLLOWUP_POLICY", label: "出院计划随访要求" },
];

export const defaultFollowupTemplateFormValues = {
  organizationScope: "p5-hospital",
  applicableScope: "COPD",
  questionnaireTemplateId: "FOLLOWUP_QUESTIONNAIRE_DEFAULT",
  questionCode: "dyspnea",
  questionType: "TEXT",
  questionnaireDelayDays: 7,
  outpatientDelayDays: 14,
  abnormalCondition: "出现呼吸困难加重或血氧下降",
  notifyTarget: "责任医生与随访护士",
  sourceRef: "FOLLOWUP_TEMPLATE_STANDARD",
};
