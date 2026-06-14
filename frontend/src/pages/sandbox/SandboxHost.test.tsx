import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import SandboxHost from "./SandboxHost";

const sandboxHookMocks = vi.hoisted(() => ({
  run: vi.fn(),
  useSandboxScenarios: vi.fn(),
  useRunSandboxScenario: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useSandboxScenarios: sandboxHookMocks.useSandboxScenarios,
  useRunSandboxScenario: sandboxHookMocks.useRunSandboxScenario,
}));

const scenarios = [
  {
    id: "sbx-lab-critical-k",
    servicePackage: "clinical-collaboration",
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
    id: "sbx-unapproved",
    servicePackage: "clinical-collaboration",
    engine: "rule",
    playbook: "RULE_ONLY",
    triggerPoint: "order-sign",
    title: "待评审场景",
    narrative: "待临床评审。",
    hostSummary: "院内业务系统模拟场景",
    patientId: "patient-2",
    encounterId: "encounter-2",
    expectedRuleCode: "SBX.PENDING",
    expectedAction: "REMIND",
    expectedSeverity: "HIGH",
    expectedAssetCode: null,
    status: "clinical-review-required",
    statusReason: "临床评审通过后开放",
    input: { kind: "unavailable" },
  },
  {
    id: "sbx-recommendation-composite",
    servicePackage: "engine-orchestration",
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
    status: "ready",
    statusReason: "可运行",
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
  });

  it("runs the selected scenario with canonical context and renders the real result", async () => {
    sandboxHookMocks.run.mockResolvedValue({
      scenarioId: "sbx-lab-critical-k",
      traceId: "trace-sandbox-host-1",
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
  });

  it("keeps a truthful failure state when orchestration cannot complete", async () => {
    sandboxHookMocks.run.mockRejectedValue(new Error("沙盘编排服务暂不可用"));

    renderSandboxHost();
    fireEvent.click(screen.getByRole("button", { name: "医生复核并触发 MedKernel" }));

    expect(await screen.findByText("沙盘编排服务暂不可用")).toBeInTheDocument();
    expect(screen.queryByTitle("临床嵌入式终端")).not.toBeInTheDocument();
  });

  it("shows the clinical gate and removes the run action for unapproved content", async () => {
    renderSandboxHost();

    fireEvent.click(screen.getByRole("button", { name: /待评审场景.*待临床评审/ }));

    expect(await screen.findByText(/临床评审通过后开放/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "医生复核并触发 MedKernel" }),
    ).not.toBeInTheDocument();
    expect(sandboxHookMocks.run).not.toHaveBeenCalled();
  });

  it("runs an outer-engine playbook without fabricated clinical input", async () => {
    sandboxHookMocks.run.mockResolvedValue({
      scenarioId: "sbx-recommendation-composite",
      traceId: "trace-outer-1",
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
          occurredAt: expect.any(String),
          parentOrigin: window.location.origin,
          integrationMode: "IFRAME",
        },
      }),
    );
    expect(await screen.findByText("trace-outer-1")).toBeInTheDocument();
  });
});
