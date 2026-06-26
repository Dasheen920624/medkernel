import { describe, expect, it } from "vitest";

import {
  SANDBOX_SCENARIOS,
  buildSandboxContextOverride,
  isNumericScenario,
  mergeSandboxCatalog,
  scenariosByServiceLine,
} from "./sandboxScenarios";

describe("sandboxScenarios", () => {
  it("uses a hospital-facing disabled state until the scenario catalog loads", () => {
    expect(SANDBOX_SCENARIOS).toEqual([
      expect.objectContaining({
        id: "sandbox-catalog-required",
        title: "沙盘场景目录未就绪",
        narrative: "场景目录不可用时仅展示停用状态，不生成临床场景。",
        hostSummary: "场景目录不可用",
        status: "catalog-unavailable",
        statusReason: "请完成沙盘场景目录配置后再运行全真沙盘。",
        inputKind: "unavailable",
      }),
    ]);
    const visibleText = SANDBOX_SCENARIOS.map((scenario) =>
      [scenario.title, scenario.narrative, scenario.hostSummary, scenario.statusReason].join(" "),
    ).join(" ");
    expect(visibleText).not.toMatch(/后端|占位|模拟/);
    expect(scenariosByServiceLine()["clinical-collaboration"]).toHaveLength(1);
    expect(scenariosByServiceLine()["quality-improvement"]).toHaveLength(0);
    expect(scenariosByServiceLine()["engine-orchestration"]).toHaveLength(0);
  });

  it("maps catalog numeric and orchestration items without frontend clinical constants", () => {
    const scenarios = mergeSandboxCatalog([
      {
        id: "sbx-lab-critical-k",
        serviceLine: "clinical-collaboration",
        engine: "rule",
        playbook: "RULE_ONLY",
        triggerPoint: "result-review",
        title: "受控数值场景",
        narrative: "场景目录描述。",
        hostSummary: "院内业务系统复核",
        patientId: "patient-1",
        encounterId: "encounter-1",
        expectedRuleCode: "SBX.LAB.CRITICAL.K",
        expectedAction: "STRONG_REMINDER",
        expectedSeverity: "CRITICAL",
        status: "runtime-check",
        statusReason: "运行时解析",
        input: {
          kind: "numeric",
          code: "2823-3",
          label: "检验结果",
          defaultValue: 6.8,
          minValue: 1,
          maxValue: 12,
          step: 0.1,
          unit: "mmol/L",
          referenceRange: "3.5-5.5",
          upperReferenceValue: 5.5,
          encounterType: "ED",
        },
      },
      {
        id: "sbx-recommendation-composite",
        serviceLine: "engine-orchestration",
        engine: "recommendation",
        playbook: "RECOMMENDATION_COMPOSITE",
        triggerPoint: "patient-view",
        title: "编排场景",
        narrative: "场景目录描述。",
        hostSummary: "院内业务系统编排",
        patientId: "patient-2",
        encounterId: "encounter-2",
        expectedRuleCode: null,
        expectedAction: "SUGGEST_ORDER",
        expectedSeverity: "MEDIUM",
        status: "runtime-check",
        statusReason: "运行时解析",
        input: { kind: "orchestration" },
      },
    ]);

    expect(scenarios).toContainEqual(
      expect.objectContaining({
        id: "sbx-lab-critical-k",
        inputKind: "numeric",
        status: "runtime-check",
        observationCode: "2823-3",
        defaultNumericValue: 6.8,
      }),
    );
    expect(scenarios).toContainEqual(
      expect.objectContaining({
        id: "sbx-recommendation-composite",
        inputKind: "orchestration",
        status: "runtime-check",
      }),
    );
  });

  it("does not retain a fixed configuration package version in sandbox context data", () => {
    const [scenario] = mergeSandboxCatalog([
      {
        id: "sbx-lab-critical-k",
        status: "runtime-check",
        patientId: "patient-1",
        encounterId: "encounter-1",
        input: {
          kind: "numeric",
          code: "2823-3",
          label: "血清钾",
          defaultValue: 6.8,
          unit: "mmol/L",
          referenceRange: "3.5-5.5",
          encounterType: "ED",
        },
      },
    ]);
    expect(isNumericScenario(scenario)).toBe(true);
    if (!isNumericScenario(scenario)) throw new Error("测试场景必须为数值型");
    const context = buildSandboxContextOverride(scenario, 6.8, "2026-06-19T03:00:00Z");
    expect(context.patient.mappedVersion).toBe("sandbox-context-v1");
    expect(context.patient.mappedVersion).not.toBe("7.2.1");
  });

  it("keeps catalog fallback and contract-blocking reasons in product language", () => {
    const [defaultScenario, missingNumericContract, missingInputContract] = mergeSandboxCatalog([
      {
        id: "sbx-default-copy",
        status: "runtime-check",
        input: { kind: "orchestration" },
      },
      {
        id: "sbx-missing-numeric-contract",
        status: "runtime-check",
        input: { kind: "numeric", defaultValue: 6.8 },
      },
      {
        id: "sbx-missing-input-contract",
        status: "runtime-check",
      },
    ]);

    expect(defaultScenario).toEqual(
      expect.objectContaining({
        narrative: "按当前机构生效目录运行沙盘场景。",
        hostSummary: "院内业务系统复核",
        statusReason: "运行时按当前机构生效版本解析规则与资产。",
      }),
    );
    expect(missingNumericContract).toEqual(
      expect.objectContaining({
        status: "catalog-unavailable",
        inputKind: "unavailable",
        statusReason: "场景目录缺少数值录入契约，已阻断运行。",
      }),
    );
    expect(missingInputContract).toEqual(
      expect.objectContaining({
        status: "catalog-unavailable",
        inputKind: "unavailable",
        statusReason: "场景目录缺少可执行输入契约，已阻断运行。",
      }),
    );

    const visibleText = [defaultScenario, missingNumericContract, missingInputContract]
      .map((scenario) =>
        [scenario.narrative, scenario.hostSummary, scenario.statusReason].join(" "),
      )
      .join(" ");
    expect(visibleText).not.toMatch(/后端|占位|模拟/);
  });
});
