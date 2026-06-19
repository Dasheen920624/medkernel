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
    servicePackage: "clinical-collaboration",
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
        bindingId: "binding-1",
        packageOwnerTenantId: "tenant-sandbox-1",
        packageId: "pkg-1",
        packageCode: "PKG.SANDBOX",
        packageVersion: "7.2.1",
        resolutionSource: "TENANT_PACKAGE",
        assetCount: 10,
        warnings: [],
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
      resolvedPackageVersion: "7.2.1",
      resolutionSource: "TENANT_PACKAGE",
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
    expect(screen.getAllByText("演练机构规则").length).toBeGreaterThan(0);
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
        reason: "演练机构未激活沙盘运行绑定",
        targetOrgUnitId: "hospital-sandbox-1",
        assetCount: 0,
        warnings: [],
        externalSideEffects: false,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
    renderSandboxHost();

    fireEvent.click(screen.getByRole("button", { name: /抗菌药物处方复核/ }));

    expect(await screen.findByText(/演练机构未激活沙盘运行绑定/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "运行真实引擎链路" })).not.toBeInTheDocument();
    expect(sandboxHookMocks.run).not.toHaveBeenCalled();
  });

  it("shows a platform-source runtime binding without claiming institution ownership", () => {
    sandboxHookMocks.useSandboxRuntimeStatus.mockReturnValue({
      data: {
        ready: true,
        targetOrgUnitId: "hospital-sandbox-1",
        bindingId: "binding-platform-1",
        packageOwnerTenantId: "__platform__",
        packageId: "pkg-platform-1",
        packageCode: "PKG.PLATFORM.RULES",
        packageVersion: "9.0.0",
        resolutionSource: "PLATFORM_PACKAGE",
        assetCount: 32,
        warnings: [],
        resolvedAt: "2026-06-19T04:00:00Z",
        externalSideEffects: false,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderSandboxHost();

    expect(screen.getByText("平台主源规则")).toBeInTheDocument();
    expect(screen.getByText("PKG.PLATFORM.RULES@9.0.0")).toBeInTheDocument();
  });

  it("runs an outer-engine playbook without fabricated clinical input", async () => {
    sandboxHookMocks.run.mockResolvedValue({
      scenarioId: "sbx-recommendation-composite",
      traceId: "trace-outer-1",
      runId: "run-outer-1",
      baselineId: "baseline-outer-1",
      mode: "CURRENT",
      resolvedPackageVersion: "7.2.1",
      resolutionSource: "TENANT_PACKAGE",
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
});
