import type { RuleGovernanceState } from "@/shared/api/hooks";

export const RULE_GOVERNANCE_STAGES: ReadonlyArray<{
  key: RuleGovernanceState;
  title: string;
}> = [
  { key: "DRAFT", title: "草稿" },
  { key: "PEER_REVIEW", title: "同行评审" },
  { key: "COMMITTEE", title: "委员会会签" },
  { key: "SHADOW", title: "影子运行" },
  { key: "CANARY", title: "灰度" },
  { key: "FULL", title: "全量" },
  { key: "MONITOR", title: "监测" },
  { key: "RETIRED", title: "退役" },
];

export function ruleGovernanceLabel(state: RuleGovernanceState) {
  return RULE_GOVERNANCE_STAGES.find((stage) => stage.key === state)?.title ?? state;
}
