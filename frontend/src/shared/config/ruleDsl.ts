/**
 * 规则 DSL 装配/解析桥接（RULE-01，OpenSpec pathway-rule-authoring-overhaul P1-2）。
 *
 * <p>在递归条件模型 {@link ./conditionModel} 之上，组装/还原完整规则 DSL
 * （`when` 条件树 + `then` 动作 + `explain` 解释），并提供临床原型模板。
 * 与后端 {@code RuleDslEvaluator} 的 `when/then/explain` 契约对齐；条件部分支持任意深度嵌套。
 */
import {
  countLeaves,
  createGroup,
  createLeaf,
  fromLegacyWhen,
  nodeToDsl,
  type RuleGroup,
} from "./conditionModel";

/** 动作风险等级（与后端 RuleRiskLevel 对齐，含 CRITICAL 前向兼容）。 */
export type RuleSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

/** 命中动作。 */
export interface RuleAction {
  actionCode: string;
  severity: RuleSeverity;
  message: string;
  requiresPhysicianConfirmation: boolean;
}

/** 规则草稿：条件根组 + 动作 + 解释摘要。 */
export interface RuleDraft {
  root: RuleGroup;
  action: RuleAction;
  explanationSummary: string;
}

/** 临床原型模板键。 */
export type RuleArchetypeKey =
  | "clinical_quality_monitor"
  | "drug_safety_review"
  | "insurance_policy_review";

export interface RuleArchetype {
  key: RuleArchetypeKey;
  title: string;
  description: string;
  ruleType: "DRUG_SAFETY" | "INSURANCE_AUDIT" | "CLINICAL_QUALITY";
  riskLevel: RuleSeverity;
  build: () => RuleDraft;
}

export const DEFAULT_ACTION: RuleAction = {
  actionCode: "REVIEW_REQUIRED",
  severity: "LOW",
  message: "命中后提交人工审核，不自动写入医嘱",
  requiresPhysicianConfirmation: true,
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readString(source: unknown, key: string, fallback: string): string {
  return isRecord(source) && typeof source[key] === "string" ? (source[key] as string) : fallback;
}

function readSeverity(source: unknown, key: string, fallback: RuleSeverity): RuleSeverity {
  const value = readString(source, key, fallback);
  return value === "LOW" || value === "MEDIUM" || value === "HIGH" || value === "CRITICAL"
    ? value
    : fallback;
}

function readBoolean(source: unknown, key: string, fallback: boolean): boolean {
  return isRecord(source) && typeof source[key] === "boolean" ? (source[key] as boolean) : fallback;
}

/** 组装完整规则 DSL。 */
export function buildRuleDsl(draft: RuleDraft): Record<string, unknown> {
  return {
    when: nodeToDsl(draft.root),
    then: [{ ...draft.action }],
    explain: {
      summary: draft.explanationSummary,
      authoring: {
        layer: "L2_VISUAL_TREE",
        conditionCount: countLeaves(draft.root),
      },
    },
  };
}

/** 从 DSL 还原规则草稿（兼容旧扁平 when）。 */
export function parseRuleDsl(dsl: unknown): RuleDraft {
  if (!isRecord(dsl) || !("when" in dsl)) {
    throw new Error("规则 DSL 缺少 when 条件");
  }
  const root = fromLegacyWhen((dsl as { when: unknown }).when);
  const then = Array.isArray((dsl as { then?: unknown }).then)
    ? ((dsl as { then: unknown[] }).then[0] as unknown)
    : undefined;
  const explain = isRecord((dsl as { explain?: unknown }).explain)
    ? (dsl as { explain: Record<string, unknown> }).explain
    : undefined;
  return {
    root,
    action: {
      actionCode: readString(then, "actionCode", DEFAULT_ACTION.actionCode),
      severity: readSeverity(then, "severity", DEFAULT_ACTION.severity),
      message: readString(then, "message", DEFAULT_ACTION.message),
      requiresPhysicianConfirmation: readBoolean(
        then,
        "requiresPhysicianConfirmation",
        DEFAULT_ACTION.requiresPhysicianConfirmation,
      ),
    },
    explanationSummary:
      typeof explain?.summary === "string" && (explain.summary as string).trim()
        ? (explain.summary as string)
        : "依据真实上下文快照进行确定性判断",
  };
}

/** 生成解释模板（供 explanationJson）。 */
export function buildExplanation(draft: RuleDraft): Record<string, unknown> {
  return {
    template: `${draft.explanationSummary}。命中后处置动作：${draft.action.message}`,
    authoring: {
      layer: "L1/L2/L3",
      conditionCount: countLeaves(draft.root),
      logic: draft.root.logic,
    },
  };
}

export function formatRuleJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

export function parseRuleJson(text: string): unknown {
  const normalized = text.trim();
  if (!normalized) {
    throw new Error("JSON 内容为空");
  }
  return JSON.parse(normalized) as unknown;
}

/** 临床原型模板（条件均为占位字段，禁止内置具体患者/药品/诊断值）。 */
export const RULE_ARCHETYPES: RuleArchetype[] = [
  {
    key: "clinical_quality_monitor",
    title: "临床质控阈值",
    description: "从真实快照取一个数值字段，超过阈值后提交人工复核。",
    ruleType: "CLINICAL_QUALITY",
    riskLevel: "MEDIUM",
    build: () => ({
      root: createGroup({
        logic: "all",
        children: [
          createLeaf({
            label: "真实快照数值字段",
            fact: "context.<字段路径>",
            operator: "gte",
            value: "",
            valueKind: "number",
          }),
        ],
      }),
      action: { ...DEFAULT_ACTION, severity: "MEDIUM" },
      explanationSummary: "依据真实上下文快照中的数值字段进行确定性判断",
    }),
  },
  {
    key: "drug_safety_review",
    title: "合理用药复核",
    description: "检查真实上下文中是否存在受控字段，命中后要求人工复核。",
    ruleType: "DRUG_SAFETY",
    riskLevel: "LOW",
    build: () => ({
      root: createGroup({
        logic: "all",
        children: [
          createLeaf({
            label: "待复核上下文字段",
            fact: "context.<字段路径>",
            operator: "exists",
            valueKind: "empty",
          }),
        ],
      }),
      action: { ...DEFAULT_ACTION },
      explanationSummary: "依据真实上下文快照中的受控字段进行确定性判断",
    }),
  },
  {
    key: "insurance_policy_review",
    title: "医保规范核查",
    description: "检查真实上下文编码或状态字段是否进入受控集合。",
    ruleType: "INSURANCE_AUDIT",
    riskLevel: "MEDIUM",
    build: () => ({
      root: createGroup({
        logic: "any",
        children: [
          createLeaf({
            label: "受控编码或状态字段",
            fact: "context.<字段路径>",
            operator: "in",
            value: "",
            valueKind: "list",
          }),
        ],
      }),
      action: { ...DEFAULT_ACTION, severity: "MEDIUM" },
      explanationSummary: "依据真实上下文快照中的编码或状态集合进行确定性判断",
    }),
  },
];

export function findArchetype(key: RuleArchetypeKey): RuleArchetype {
  return RULE_ARCHETYPES.find((item) => item.key === key) ?? RULE_ARCHETYPES[0];
}

export function instantiateArchetype(key: RuleArchetypeKey): RuleDraft {
  return findArchetype(key).build();
}
