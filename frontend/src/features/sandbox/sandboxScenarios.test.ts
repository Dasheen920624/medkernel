import { describe, expect, it } from "vitest";

import {
  SANDBOX_SCENARIOS,
  mergeSandboxCatalog,
  scenariosByServicePackage,
} from "./sandboxScenarios";

describe("sandboxScenarios", () => {
  it("uses an honest disabled placeholder until the backend-owned catalog loads", () => {
    expect(SANDBOX_SCENARIOS).toEqual([
      expect.objectContaining({
        id: "backend-catalog-required",
        status: "clinical-review-required",
        inputKind: "unavailable",
      }),
    ]);
    expect(scenariosByServicePackage()["clinical-collaboration"]).toHaveLength(1);
    expect(scenariosByServicePackage()["quality-improvement"]).toHaveLength(0);
    expect(scenariosByServicePackage()["engine-orchestration"]).toHaveLength(0);
  });

  it("maps backend numeric and orchestration catalog items without frontend clinical constants", () => {
    const scenarios = mergeSandboxCatalog([
      {
        id: "sbx-lab-critical-k",
        servicePackage: "clinical-collaboration",
        engine: "rule",
        playbook: "RULE_ONLY",
        triggerPoint: "result-review",
        title: "受控数值场景",
        narrative: "后端目录描述。",
        hostSummary: "院内业务系统复核",
        patientId: "patient-1",
        encounterId: "encounter-1",
        expectedRuleCode: "SBX.LAB.CRITICAL.K",
        expectedAction: "STRONG_REMINDER",
        expectedSeverity: "CRITICAL",
        status: "ready",
        statusReason: "可运行",
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
        servicePackage: "engine-orchestration",
        engine: "recommendation",
        playbook: "RECOMMENDATION_COMPOSITE",
        triggerPoint: "patient-view",
        title: "编排场景",
        narrative: "后端目录描述。",
        hostSummary: "院内业务系统编排",
        patientId: "patient-2",
        encounterId: "encounter-2",
        expectedRuleCode: null,
        expectedAction: "SUGGEST_ORDER",
        expectedSeverity: "MEDIUM",
        status: "ready",
        statusReason: "可运行",
        input: { kind: "orchestration" },
      },
    ]);

    expect(scenarios).toContainEqual(
      expect.objectContaining({
        id: "sbx-lab-critical-k",
        inputKind: "numeric",
        observationCode: "2823-3",
        defaultNumericValue: 6.8,
      }),
    );
    expect(scenarios).toContainEqual(
      expect.objectContaining({
        id: "sbx-recommendation-composite",
        inputKind: "orchestration",
      }),
    );
  });
});
