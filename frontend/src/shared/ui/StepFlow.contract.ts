import type { ChangeStatus } from "./StatusBadge.contract";

export type StepKey =
  | "select_template"
  | "auto_validate"
  | "impact_preview"
  | "submit_review"
  | "canary_release"
  | "full_rollout"
  | "evidence_rollback";

export interface StepMeta {
  key: StepKey;
  title: string;
  description: string;
}

export const SEVEN_STEPS: StepMeta[] = [
  { key: "select_template", title: "选模板/导入", description: "从专病模板或文件开始" },
  { key: "auto_validate", title: "自动校验", description: "字段格式 + 业务规则 + 来源核对" },
  { key: "impact_preview", title: "看影响", description: "影响科室、患者、规则、风险" },
  { key: "submit_review", title: "安全复核", description: "当前授权责任人完成安全复核" },
  { key: "canary_release", title: "灰度发布", description: "默认 10% 床位 / 一个科室" },
  { key: "full_rollout", title: "全量", description: "核对灰度与影响证据后全院生效" },
  { key: "evidence_rollback", title: "留证据/可回滚", description: "审计快照 + 回滚入口" },
];

export const STEP_CHANGE_STATUS: Record<StepKey, ChangeStatus> = {
  select_template: "pending",
  auto_validate: "pending",
  impact_preview: "pending",
  submit_review: "pending",
  canary_release: "canary",
  full_rollout: "rolled_out",
  evidence_rollback: "rolled_back",
};
