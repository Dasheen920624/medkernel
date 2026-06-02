import { describe, expect, it } from "vitest";

import {
  conditionTreeToDsl,
  createExplanationTemplate,
  dslToConditionTree,
  instantiateRuleTemplate,
  type RuleConditionTree,
} from "./ruleLayeredEditor";

describe("RULE-01 三层规则编辑模型", () => {
  it("L1 模板实例化为可编辑 L2 条件树，不内置具体患者、药品或诊断值", () => {
    const tree = instantiateRuleTemplate("drug_safety_review");

    expect(tree.logic).toBe("all");
    expect(tree.conditions).toHaveLength(1);
    expect(tree.conditions[0]).toMatchObject({
      fact: "context.<字段路径>",
      operator: "exists",
      valueKind: "empty",
    });

    const serialized = JSON.stringify(tree);
    expect(serialized).not.toContain("张三");
    expect(serialized).not.toContain("强力阿司匹林");
    expect(serialized).not.toContain("J44");
  });

  it("L2 条件树与 L3 DSL 双向转换保持条件、动作和解释摘要不丢失", () => {
    const tree: RuleConditionTree = {
      logic: "all",
      conditions: [
        {
          id: "condition-1",
          label: "首个真实快照字段",
          fact: "observations.0.value",
          operator: "gte",
          value: 6,
          valueKind: "number",
        },
        {
          id: "condition-2",
          label: "上下文场景字段",
          fact: "encounter.type",
          operator: "equals",
          value: "INPATIENT",
          valueKind: "string",
        },
      ],
      action: {
        actionCode: "REVIEW_REQUIRED",
        severity: "HIGH",
        message: "命中后提交人工审核，不自动写入医嘱",
        requiresPhysicianConfirmation: true,
      },
      explanationSummary: "依据真实上下文快照中的两个字段进行确定性判断",
    };

    const dsl = conditionTreeToDsl(tree);

    expect(dsl).toEqual({
      when: {
        all: [
          {
            fact: "observations.0.value",
            operator: "gte",
            value: 6,
            ui: {
              id: "condition-1",
              label: "首个真实快照字段",
              valueKind: "number",
            },
          },
          {
            fact: "encounter.type",
            operator: "equals",
            value: "INPATIENT",
            ui: {
              id: "condition-2",
              label: "上下文场景字段",
              valueKind: "string",
            },
          },
        ],
      },
      then: [
        {
          actionCode: "REVIEW_REQUIRED",
          severity: "HIGH",
          message: "命中后提交人工审核，不自动写入医嘱",
          requiresPhysicianConfirmation: true,
        },
      ],
      explain: {
        summary: "依据真实上下文快照中的两个字段进行确定性判断",
        authoring: {
          layer: "L2_VISUAL_TREE",
          conditionCount: 2,
        },
      },
    });
    expect(dslToConditionTree(dsl)).toEqual(tree);
  });

  it("解释模板由同一棵条件树生成，供 API explanation 字段留证", () => {
    const tree = instantiateRuleTemplate("clinical_quality_monitor");

    expect(createExplanationTemplate(tree)).toMatchObject({
      template: expect.stringContaining("真实上下文快照"),
      variables: {
        "context.<字段路径>": "由 L2 条件树选择的真实上下文字段",
      },
    });
  });
});
