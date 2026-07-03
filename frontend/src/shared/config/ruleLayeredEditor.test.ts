import { describe, expect, it } from "vitest";

import {
  conditionTreeToDsl,
  createDefaultRuleApplicability,
  createExplanationTemplate,
  dslToConditionTree,
  instantiateRuleTemplate,
  type RuleConditionTree,
  type RuleDslNode,
} from "./ruleLayeredEditor";

type RuleDslConditionNode = Extract<RuleDslNode, { operator: unknown }>;

function expectFlatDslConditions(nodes: RuleDslNode[] | undefined): RuleDslConditionNode[] {
  expect(nodes).toBeDefined();
  const conditions = nodes ?? [];
  conditions.forEach((node) => expect("operator" in node).toBe(true));
  return conditions as RuleDslConditionNode[];
}

function expectFlatTreeRoundTrip(restored: RuleConditionTree, tree: RuleConditionTree) {
  expect(restored.triggerPoint).toBe(tree.triggerPoint);
  expect(restored.applicability).toEqual(tree.applicability);
  expect(restored.logic).toBe(tree.logic);
  expect(restored.conditions).toEqual(tree.conditions);
  expect(restored.root).toBeDefined();
  expect(restored.root?.logic).toBe(tree.logic);
  expect(restored.root?.children).toEqual(tree.conditions);
  expect(restored.actions).toEqual(tree.actions);
  expect(restored.explanationSummary).toBe(tree.explanationSummary);
}

describe("RULE-01 三层规则编辑模型", () => {
  it("L1 规则原型实例化为可编辑 L2 条件树，不内置具体患者、药品或诊断值", () => {
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
      triggerPoint: "result-review",
      applicability: {
        population: {
          exclude: {
            all: [
              {
                fact: "patient.specialPopulations",
                operator: "contains",
                value: "PREGNANT",
              },
            ],
          },
        },
        orgScope: {
          hospitalIds: ["hospital-1"],
          deptIds: ["dept-1"],
        },
        settings: ["INPATIENT", "ED"],
        effective: {
          from: "2026-06-01",
          to: "2026-12-31",
          rolloutPercent: 25,
        },
      },
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
      actions: [
        {
          actionCode: "BLOCK",
          atSeverity: "HIGH",
          indicator: "critical",
          summary: "高风险结果需要人工复核",
          detail: "命中后提交人工审核，不自动写入医嘱",
          source: {
            label: "院内高风险结果管理规范",
            evidenceLevel: "A",
          },
          suggestions: [
            {
              label: "补充专科复核",
              actionType: "REMIND",
            },
          ],
          overrideReasons: ["紧急处置", "已完成专科会诊"],
          requiresPhysicianConfirmation: true,
        },
        {
          actionCode: "AUTO_DOCUMENT",
          atSeverity: "LOW",
          indicator: "info",
          summary: "记录规则命中",
          detail: "仅记录命中事实。",
          source: {
            label: "规则运行记录",
          },
          suggestions: [],
          overrideReasons: [],
          requiresPhysicianConfirmation: false,
        },
      ],
      explanationSummary: "依据真实上下文快照中的两个字段进行确定性判断",
    };

    const dsl = conditionTreeToDsl(tree);

    expect(dsl).toEqual({
      applicability: tree.applicability,
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
          actionCode: "BLOCK",
          atSeverity: "HIGH",
          indicator: "critical",
          summary: "高风险结果需要人工复核",
          detail: "命中后提交人工审核，不自动写入医嘱",
          source: {
            label: "院内高风险结果管理规范",
            evidenceLevel: "A",
          },
          suggestions: [
            {
              label: "补充专科复核",
              actionType: "REMIND",
            },
          ],
          overrideReasons: ["紧急处置", "已完成专科会诊"],
          requiresPhysicianConfirmation: true,
        },
        {
          actionCode: "AUTO_DOCUMENT",
          atSeverity: "LOW",
          indicator: "info",
          summary: "记录规则命中",
          detail: "仅记录命中事实。",
          source: {
            label: "规则运行记录",
          },
          suggestions: [],
          overrideReasons: [],
          requiresPhysicianConfirmation: false,
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
    expect(dsl).not.toHaveProperty("trigger");
    expectFlatTreeRoundTrip(dslToConditionTree(dsl, tree.triggerPoint), tree);
  });

  it("递归根组通过统一转换保留 A 且非(B 或 C) 结构", () => {
    const tree: RuleConditionTree = {
      triggerPoint: "order-sign",
      applicability: createDefaultRuleApplicability(),
      logic: "all",
      conditions: [],
      root: {
        id: "root",
        logic: "all",
        children: [
          {
            id: "condition-a",
            label: "用药编码",
            fact: "medications[].code",
            operator: "equals",
            value: "ANTIBIOTIC",
            valueKind: "string",
          },
          {
            id: "group-not",
            logic: "any",
            negate: true,
            children: [
              {
                id: "condition-b",
                label: "青霉素过敏",
                fact: "allergyIntolerances[].code",
                operator: "contains",
                value: "PENICILLIN",
                valueKind: "string",
              },
              {
                id: "condition-c",
                label: "头孢过敏",
                fact: "allergyIntolerances[].code",
                operator: "contains",
                value: "CEPHALOSPORIN",
                valueKind: "string",
              },
            ],
          },
        ],
      },
      actions: [
        {
          actionCode: "REMIND",
          atSeverity: "MEDIUM",
          indicator: "warning",
          summary: "抗菌药使用复核",
          detail: "命中后提交人工审核，不自动写入医嘱",
          source: { label: "院内抗菌药管理规范" },
          suggestions: [],
          overrideReasons: [],
          requiresPhysicianConfirmation: true,
        },
      ],
      explanationSummary: "递归条件应无损保存",
    };

    const dsl = conditionTreeToDsl(tree);

    expect(dsl.when).toMatchObject({
      all: [
        { fact: "medications[].code", operator: "equals" },
        {
          not: {
            any: [
              { fact: "allergyIntolerances[].code", operator: "contains", value: "PENICILLIN" },
              { fact: "allergyIntolerances[].code", operator: "contains", value: "CEPHALOSPORIN" },
            ],
          },
        },
      ],
    });
    expect(dsl.explain.authoring.conditionCount).toBe(3);

    const restored = dslToConditionTree(dsl, tree.triggerPoint);

    expect(restored.root).toBeDefined();
    expect(restored.root?.logic).toBe("all");
    expect(restored.root?.children).toHaveLength(2);
    expect(restored.root?.children[1]).toMatchObject({
      logic: "any",
      negate: true,
    });
  });

  it("L2 条件树可配置 MED-C2 已实现的临床算子并无损回填", () => {
    const tree: RuleConditionTree = {
      triggerPoint: "result-review",
      applicability: createDefaultRuleApplicability(),
      logic: "all",
      conditions: [
        {
          id: "condition-between",
          label: "血钾目标区间",
          fact: "lab.potassium",
          operator: "between",
          value: {
            min: 3.5,
            max: 5.5,
            includeMin: true,
            includeMax: false,
            unit: "mmol/L",
          },
          valueKind: "range",
        },
        {
          id: "condition-unit",
          label: "血糖跨单位比较",
          fact: "lab.glucose",
          operator: "unit_compare",
          value: {
            comparison: "gte",
            value: 7,
            unit: "mmol/L",
            analyte: "glucose",
          },
          valueKind: "measurement",
        },
        {
          id: "condition-temporal",
          label: "48 小时连续高钾",
          fact: "observations.potassium",
          operator: "temporal",
          value: {
            mode: "sustained",
            window: "PT48H",
            referenceTime: "2026-06-03T00:00:00Z",
            count: 2,
            condition: { operator: "gt", value: 6, unit: "mmol/L" },
          },
          valueKind: "temporal",
        },
        {
          id: "condition-derived",
          label: "eGFR 可选医学公式",
          fact: "derived.egfr",
          operator: "derived",
          value: {
            formula: "CKD_EPI_2021_EGFR",
            comparison: "gte",
            value: 60,
            unit: "mL/min/1.73m2",
            parameters: {
              creatinine: "labs.creatinine",
              age: "patient.age",
              sex: "patient.sex",
            },
          },
          valueKind: "derived",
        },
      ],
      actions: [
        {
          actionCode: "STRONG_REMINDER",
          atSeverity: "HIGH",
          indicator: "critical",
          summary: "临床算子命中",
          detail: "命中后提交人工审核，不自动写入医嘱。",
          source: { label: "临床算子规则依据" },
          suggestions: [],
          overrideReasons: [],
          requiresPhysicianConfirmation: true,
        },
      ],
      explanationSummary: "依据 MED-C2 临床算子进行确定性判断",
    };

    const dsl = conditionTreeToDsl(tree);
    const conditions = expectFlatDslConditions(dsl.when.all);

    expect(conditions.map((condition) => condition.operator)).toEqual([
      "between",
      "unit_compare",
      "temporal",
      "derived",
    ]);
    expect(conditions[0].value).toMatchObject({ min: 3.5, max: 5.5, unit: "mmol/L" });
    expect(conditions[1].value).toMatchObject({ analyte: "glucose", comparison: "gte" });
    expect(conditions[2].value).toMatchObject({
      mode: "sustained",
      condition: { operator: "gt", unit: "mmol/L" },
    });
    expect(conditions[3].value).toMatchObject({
      formula: "CKD_EPI_2021_EGFR",
      parameters: { creatinine: "labs.creatinine" },
    });
    expectFlatTreeRoundTrip(dslToConditionTree(dsl, tree.triggerPoint), tree);
  });

  it("L2 条件树可配置表达式聚合并保留 where 与 over", () => {
    const tree: RuleConditionTree = {
      triggerPoint: "result-review",
      applicability: createDefaultRuleApplicability(),
      logic: "all",
      conditions: [
        {
          id: "condition-expression",
          label: "最近肌酐",
          fact: "observations[].value",
          expr: {
            field: "observations[].value",
            select: "latest",
            where: {
              all: [
                {
                  expr: { field: "observations[].code" },
                  operator: "equals",
                  value: { const: "CREATININE" },
                },
              ],
            },
            over: "PT48H",
            referenceTime: "2026-06-03T00:00:00Z",
          },
          operator: "gte",
          value: 1.2,
          valueKind: "number",
        },
      ],
      actions: [
        {
          actionCode: "STRONG_REMINDER",
          atSeverity: "HIGH",
          indicator: "critical",
          summary: "表达式聚合命中",
          detail: "命中后提交人工审核，不自动写入医嘱。",
          source: { label: "表达式聚合规则依据" },
          suggestions: [],
          overrideReasons: [],
          requiresPhysicianConfirmation: true,
        },
      ],
      explanationSummary: "依据表达式聚合进行确定性判断",
    };

    const dsl = conditionTreeToDsl(tree);
    const conditions = expectFlatDslConditions(dsl.when.all);

    expect(conditions[0]).toMatchObject({
      expr: {
        field: "observations[].value",
        select: "latest",
        over: "PT48H",
        referenceTime: "2026-06-03T00:00:00Z",
      },
      operator: "gte",
      value: 1.2,
    });
    expect(JSON.stringify(conditions[0].expr)).toContain("CREATININE");
    expectFlatTreeRoundTrip(dslToConditionTree(dsl, tree.triggerPoint), tree);
  });

  it("L3 DSL 回填时保留服务已实现的临床算子，不静默降级为 exists", () => {
    const dsl = {
      applicability: createDefaultRuleApplicability(),
      when: {
        all: [
          {
            fact: "lab.potassium",
            operator: "not_between",
            value: { min: 3.5, max: 5.5, unit: "mmol/L" },
          },
          { fact: "lab.potassium", operator: "within_ref" },
          { fact: "lab.potassium", operator: "above_ref" },
          { fact: "lab.potassium", operator: "below_ref" },
          { fact: "lab.potassium", operator: "is_missing" },
          {
            fact: "lab.potassium",
            operator: "is_critical",
            value: { criticalValues: ["HH", "LL"] },
          },
          {
            fact: "lab.potassium",
            operator: "is_stale",
            value: { maxAge: "PT24H", referenceTime: "2026-06-06T00:00:00Z" },
          },
        ],
      },
      then: [
        {
          actionCode: "REMIND",
          atSeverity: "LOW",
          indicator: "info",
          summary: "临床算子提醒",
          detail: "保留临床算子配置。",
          source: { label: "临床算子规则依据" },
          suggestions: [],
          overrideReasons: [],
          requiresPhysicianConfirmation: false,
        },
      ],
      explain: { summary: "保留临床算子" },
    };

    const tree = dslToConditionTree(dsl, "result-review");

    expect(tree.conditions.map((condition) => condition.operator)).toEqual([
      "not_between",
      "within_ref",
      "above_ref",
      "below_ref",
      "is_missing",
      "is_critical",
      "is_stale",
    ]);
    const conditions = expectFlatDslConditions(conditionTreeToDsl(tree).when.all);
    expect(conditions.map((condition) => condition.operator)).toEqual([
      "not_between",
      "within_ref",
      "above_ref",
      "below_ref",
      "is_missing",
      "is_critical",
      "is_stale",
    ]);
  });

  it("L3 DSL 包含未知算子时直接报错，避免错误规则被伪装成存在性判断", () => {
    expect(() =>
      dslToConditionTree(
        {
          when: { all: [{ fact: "lab.potassium", operator: "unknown_operator" }] },
          then: [],
          explain: { summary: "未知算子" },
        },
        "result-review",
      ),
    ).toThrow("不支持的规则算子");
  });

  it("L3 DSL 缺少适用域时直接报错", () => {
    const dsl = conditionTreeToDsl(instantiateRuleTemplate("clinical_quality_monitor"));
    const invalid = { ...dsl } as Record<string, unknown>;
    delete invalid.applicability;

    expect(() => dslToConditionTree(invalid, "result-review")).toThrow("缺少 applicability");
  });

  it("L3 DSL 动作缺少服务必填数组时直接报错，避免提交后才失败", () => {
    const action = {
      actionCode: "REMIND",
      atSeverity: "LOW",
      indicator: "info",
      summary: "低风险提醒",
      detail: "提交人工复核。",
      source: { label: "院内规则依据" },
      requiresPhysicianConfirmation: false,
    };
    const baseDsl = {
      applicability: createDefaultRuleApplicability(),
      when: { all: [{ fact: "lab.potassium", operator: "exists" }] },
      explain: { summary: "校验动作契约" },
    };

    expect(() =>
      dslToConditionTree(
        {
          ...baseDsl,
          then: [{ ...action, overrideReasons: [] }],
        },
        "result-review",
      ),
    ).toThrow("suggestions 必须是数组");
    expect(() =>
      dslToConditionTree(
        {
          ...baseDsl,
          then: [{ ...action, suggestions: [] }],
        },
        "result-review",
      ),
    ).toThrow("overrideReasons 必须是数组");
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
