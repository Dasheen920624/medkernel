import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import SandboxHost from "./SandboxHost";

const sandboxHookMocks = vi.hoisted(() => ({
  run: vi.fn(),
  useSandboxScenarios: vi.fn(),
  useSandboxRuntimeStatus: vi.fn(),
  useRunSandboxScenario: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useSandboxScenarios: sandboxHookMocks.useSandboxScenarios,
  useSandboxRuntimeStatus: sandboxHookMocks.useSandboxRuntimeStatus,
  useRunSandboxScenario: sandboxHookMocks.useRunSandboxScenario,
}));

const scenarios = [
  {
    id: "sbx-lab-critical-k",
    serviceLine: "clinical-collaboration",
    engine: "rule",
    playbook: "RULE_ONLY",
    triggerPoint: "result-review",
    title: "检验复核受控场景",
    narrative: "由后端目录提供的受控场景。",
    hostSummary: "院内业务系统检验复核",
    patientId: "patient-1",
    encounterId: "encounter-1",
    expectedRuleCode: "SBX.LAB.CRITICAL.K",
    expectedAction: "STRONG_REMINDER",
    expectedSeverity: "CRITICAL",
    expectedAssetCode: null,
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
    id: "sbx-antibiotic-review",
    serviceLine: "clinical-collaboration",
    engine: "rule",
    playbook: "RULE_ONLY",
    triggerPoint: "order-sign",
    title: "抗菌药物处方复核",
    narrative: "使用演练机构规则运行。",
    hostSummary: "院内业务系统模拟场景",
    patientId: "patient-2",
    encounterId: "encounter-2",
    expectedRuleCode: "SBX.ANTIBIOTIC.REVIEW",
    expectedAction: "REMIND",
    expectedSeverity: "HIGH",
    expectedAssetCode: null,
    status: "runtime-check",
    statusReason: "运行时解析",
    input: { kind: "orchestration" },
  },
  {
    id: "sbx-recommendation-composite",
    serviceLine: "engine-orchestration",
    engine: "recommendation",
    playbook: "RECOMMENDATION_COMPOSITE",
    triggerPoint: "patient-view",
    title: "推荐综合卡",
    narrative: "真实引擎编排。",
    hostSummary: "院内业务系统编排场景",
    patientId: "patient-3",
    encounterId: "encounter-3",
    expectedRuleCode: null,
    expectedAction: "SUGGEST_ORDER",
    expectedSeverity: "MEDIUM",
    expectedAssetCode: null,
    status: "runtime-check",
    statusReason: "运行时解析",
    input: { kind: "orchestration" },
  },
] as const;

function renderSandboxHost() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <SandboxHost />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("SandboxHost", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sandboxHookMocks.useRunSandboxScenario.mockReturnValue({
      isPending: false,
      mutateAsync: sandboxHookMocks.run,
    });
    sandboxHookMocks.useSandboxScenarios.mockReturnValue({
      data: scenarios,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
    sandboxHookMocks.useSandboxRuntimeStatus.mockReturnValue({
      data: {
        ready: true,
        targetOrgUnitId: "hospital-sandbox-1",
        runtimeReleaseId: "runtime-sandbox-1",
        runtimeRevisionNo: 7,
        platformBaselineReleaseId: "platform-baseline-1",
        manifestSha256: "a".repeat(64),
        resolutionSource: "CURRENT_RUNTIME_RELEASE",
        assetCount: 10,
        resolvedAt: "2026-06-19T03:00:00Z",
        externalSideEffects: false,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
  });

  it("runs the selected scenario with canonical context and renders the real result", async () => {
    sandboxHookMocks.run.mockResolvedValue({
      scenarioId: "sbx-lab-critical-k",
      traceId: "trace-sandbox-host-1",
      runId: "run-sandbox-host-1",
      baselineId: "baseline-sandbox-host-1",
      mode: "CURRENT",
      runtimeReleaseRef: "runtime-sandbox-1",
      runtimeRevisionNo: 7,
      resolutionSource: "CURRENT_RUNTIME_RELEASE",
      externalSideEffects: false,
      steps: [
        {
          stage: "CONTEXT",
          endpoint: "/engine/context/snapshots",
          request: {},
          response: { snapshotId: "ctx-sbx-1" },
          serverFacts: { snapshotId: "ctx-sbx-1" },
          status: "OK",
        },
        {
          stage: "RECOMMENDATION",
          endpoint: "/engine/recommendations/evaluate",
          request: {},
          response: { cardCount: 1 },
          serverFacts: { ruleCode: "SBX.LAB.CRITICAL.K" },
          status: "OK",
        },
        {
          stage: "TOKEN",
          endpoint: "/engine/embed/tokens",
          request: {},
          response: { embedUrl: "/embed/launch?token=masked" },
          serverFacts: { tokenIssued: true },
          status: "OK",
        },
      ],
      snapshotId: "ctx-sbx-1",
      triggerId: "trigger-sbx-1",
      cardCount: 1,
      embedToken: "masked",
      embedUrl: "/embed/launch?token=masked",
      result: "PASS",
    });

    renderSandboxHost();
    fireEvent.click(screen.getByRole("button", { name: "医生复核并触发 MedKernel" }));

    await waitFor(() =>
      expect(sandboxHookMocks.run).toHaveBeenCalledWith({
        scenarioId: "sbx-lab-critical-k",
        body: {
          entryMode: "SNAPSHOT",
          mode: "CURRENT",
          occurredAt: expect.any(String),
          parentOrigin: window.location.origin,
          integrationMode: "IFRAME",
          contextOverride: expect.objectContaining({
            patient: expect.objectContaining({
              mpi: "patient-1",
            }),
            observations: expect.arrayContaining([
              expect.objectContaining({
                code: "2823-3",
                valueNumeric: 6.8,
              }),
            ]),
          }),
        },
      }),
    );

    expect(await screen.findByTitle("临床嵌入式终端")).toHaveAttribute(
      "src",
      "/embed/launch?token=masked",
    );
    expect(screen.getByText("链路完成")).toBeInTheDocument();
    expect(screen.getByText("SBX.LAB.CRITICAL.K")).toBeInTheDocument();
    expect(screen.getByText("trace-sandbox-host-1")).toBeInTheDocument();
    expect(screen.getByText("run-sandbox-host-1")).toBeInTheDocument();
    expect(screen.getByText("baseline-sandbox-host-1")).toBeInTheDocument();
    expect(screen.getAllByText("医院当前运行修订").length).toBeGreaterThan(0);
    expect(screen.getAllByText("外部副作用已关闭")).toHaveLength(2);
  });

  it("keeps a truthful failure state when orchestration cannot complete", async () => {
    sandboxHookMocks.run.mockRejectedValue(new Error("沙盘编排服务暂不可用"));

    renderSandboxHost();
    fireEvent.click(screen.getByRole("button", { name: "医生复核并触发 MedKernel" }));

    expect(await screen.findByText("沙盘编排服务暂不可用")).toBeInTheDocument();
    expect(screen.queryByTitle("临床嵌入式终端")).not.toBeInTheDocument();
  });

  it("uses dynamic runtime readiness instead of a static clinical-review block", async () => {
    sandboxHookMocks.useSandboxRuntimeStatus.mockReturnValue({
      data: {
        ready: false,
        reasonCode: "SANDBOX_RUNTIME_BASELINE_MISSING",
        reason: "演练机构尚未发布沙盘运行修订",
        targetOrgUnitId: "hospital-sandbox-1",
        assetCount: 0,
        externalSideEffects: false,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
    renderSandboxHost();

    fireEvent.click(screen.getByRole("button", { name: /抗菌药物处方复核/ }));

    expect(await screen.findByText(/演练机构尚未发布沙盘运行修订/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "运行真实引擎链路" })).not.toBeInTheDocument();
    expect(sandboxHookMocks.run).not.toHaveBeenCalled();
  });

  it("shows the current hospital runtime revision without exposing package selectors", () => {
    sandboxHookMocks.useSandboxRuntimeStatus.mockReturnValue({
      data: {
        ready: true,
        targetOrgUnitId: "hospital-sandbox-1",
        runtimeReleaseId: "runtime-platform-1",
        runtimeRevisionNo: 9,
        platformBaselineReleaseId: "platform-baseline-9",
        manifestSha256: "b".repeat(64),
        resolutionSource: "CURRENT_RUNTIME_RELEASE",
        assetCount: 32,
        resolvedAt: "2026-06-19T04:00:00Z",
        externalSideEffects: false,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderSandboxHost();

    expect(screen.getByText("医院当前运行修订")).toBeInTheDocument();
    expect(screen.getByText("修订 #9 · runtime-platform-1")).toBeInTheDocument();
  });

  it("runs an outer-engine playbook without fabricated clinical input", async () => {
    sandboxHookMocks.run.mockResolvedValue({
      scenarioId: "sbx-recommendation-composite",
      traceId: "trace-outer-1",
      runId: "run-outer-1",
      baselineId: "baseline-outer-1",
      mode: "CURRENT",
      runtimeReleaseRef: "runtime-sandbox-1",
      runtimeRevisionNo: 7,
      resolutionSource: "CURRENT_RUNTIME_RELEASE",
      externalSideEffects: false,
      steps: [],
      snapshotId: "ctx-outer-1",
      triggerId: "trigger-outer-1",
      cardCount: 1,
      embedToken: "token-outer-1",
      embedUrl: "/embed/launch?token=token-outer-1",
      embedModes: ["IFRAME"],
      result: "PASS",
    });

    renderSandboxHost();
    fireEvent.click(screen.getByRole("button", { name: /推荐综合卡/ }));
    fireEvent.click(screen.getByRole("button", { name: "运行真实引擎链路" }));

    await waitFor(() =>
      expect(sandboxHookMocks.run).toHaveBeenCalledWith({
        scenarioId: "sbx-recommendation-composite",
        body: {
          entryMode: "SNAPSHOT",
          mode: "CURRENT",
          occurredAt: expect.any(String),
          parentOrigin: window.location.origin,
          integrationMode: "IFRAME",
        },
      }),
    );
    expect(await screen.findByText("trace-outer-1")).toBeInTheDocument();
  });

  it("runs an immutable historical manifest without current context overrides", async () => {
    sandboxHookMocks.run.mockResolvedValue({
      scenarioId: "sbx-lab-critical-k",
      traceId: "trace-history-1",
      runId: "run-history-1",
      baselineId: "baseline-history-1",
      mode: "HISTORICAL_EXACT",
      replayCaseId: "replay-2025-001",
      runtimeReleaseRef: "sha256:old-7",
      runtimeRevisionNo: 4,
      resolutionSource: "REPLAY_MANIFEST",
      externalSideEffects: false,
      steps: [],
      cardCount: 0,
      embedModes: [],
      replayRuleResults: [
        {
          ruleCode: "RULE.OLD.K",
          ruleName: "历史高钾规则",
          versionId: "rv-old-7",
          assetVersion: "7",
          historicalStatus: "RETIRED",
          contentHash: "a".repeat(64),
          hit: true,
          severity: "CRITICAL",
          actions: [{ summary: "历史高钾红线" }],
          explanation: {},
        },
      ],
      result: "PASS",
    });

    renderSandboxHost();
    fireEvent.click(screen.getByRole("radio", { name: "历史原样重放" }));
    fireEvent.change(screen.getByLabelText("历史重放清单标识"), {
      target: { value: "replay-2025-001" },
    });
    fireEvent.click(screen.getByRole("button", { name: "按清单原样重放" }));

    await waitFor(() =>
      expect(sandboxHookMocks.run).toHaveBeenCalledWith({
        scenarioId: "sbx-lab-critical-k",
        body: {
          entryMode: "SNAPSHOT",
          mode: "HISTORICAL_EXACT",
          replayCaseId: "replay-2025-001",
        },
      }),
    );
    expect(await screen.findByText("历史高钾规则")).toBeInTheDocument();
    expect(screen.getByText("RULE.OLD.K@7")).toBeInTheDocument();
    expect(screen.getByText("历史高钾红线")).toBeInTheDocument();
    expect(screen.getAllByText("历史重放清单").length).toBeGreaterThan(0);
    expect(screen.queryByText("上下文原始 JSON")).not.toBeInTheDocument();
  });

  it("compares historical and current frozen rules without sending a context override", async () => {
    sandboxHookMocks.run.mockResolvedValue({
      scenarioId: "sbx-lab-critical-k",
      traceId: "trace-compare-1",
      runId: "run-compare-1",
      baselineId: "baseline-compare-1",
      mode: "COMPARE",
      replayCaseId: "replay-2025-001",
      runtimeReleaseRef: "runtime-current-2",
      runtimeRevisionNo: 2,
      resolutionSource: "CURRENT_RUNTIME_RELEASE",
      externalSideEffects: false,
      steps: [],
      cardCount: 0,
      embedModes: [],
      replayRuleResults: [],
      comparison: {
        contextHash: "context-hash",
        summary: {
          differenceCount: 2,
          newHitCount: 1,
          noLongerHitCount: 0,
          highRiskChangeCount: 1,
          nonComparableCount: 0,
        },
        unchangedCount: 8,
        differences: [
          {
            ruleCode: "RULE.RISK.UP",
            ruleName: "高风险变化规则",
            comparable: true,
            changes: ["SEVERITY_INCREASED"],
            historical: {
              ruleCode: "RULE.RISK.UP",
              ruleName: "高风险变化规则",
              versionId: "old-v1",
              assetVersion: "1",
              sourceTier: "PLATFORM",
              sourceTenantId: "sha256:old",
              contentHash: "a".repeat(64),
              hit: true,
              severity: "LOW",
              actions: [],
              explanation: {},
            },
            current: {
              ruleCode: "RULE.RISK.UP",
              ruleName: "高风险变化规则",
              versionId: "new-v2",
              assetVersion: "2",
              sourceTier: "ORG",
              sourceTenantId: "tenant-1",
              contentHash: "b".repeat(64),
              hit: true,
              severity: "CRITICAL",
              actions: [],
              explanation: {},
            },
          },
          {
            ruleCode: "RULE.NEW.HIT",
            ruleName: "新增命中规则",
            comparable: true,
            changes: ["NEW_HIT"],
            historical: null,
            current: null,
          },
        ],
      },
      result: "PASS",
    });

    renderSandboxHost();
    fireEvent.click(screen.getByRole("radio", { name: "新旧对比" }));
    fireEvent.change(screen.getByLabelText("历史重放清单标识"), {
      target: { value: "replay-2025-001" },
    });
    fireEvent.click(screen.getByRole("button", { name: "运行新旧对比" }));

    await waitFor(() =>
      expect(sandboxHookMocks.run).toHaveBeenCalledWith({
        scenarioId: "sbx-lab-critical-k",
        body: {
          entryMode: "SNAPSHOT",
          mode: "COMPARE",
          replayCaseId: "replay-2025-001",
        },
      }),
    );
    expect(await screen.findByRole("region", { name: "新旧规则差异" })).toBeInTheDocument();
    expect(screen.getByText("高风险变化规则")).toBeInTheDocument();
    expect(screen.getByText("严重度升高")).toBeInTheDocument();
    expect(screen.getAllByText("新增命中")).toHaveLength(2);
    expect(screen.getByText("8")).toBeInTheDocument();
  });
});
