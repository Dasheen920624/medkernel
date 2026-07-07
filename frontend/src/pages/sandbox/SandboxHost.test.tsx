import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import SandboxHost from "./SandboxHost";

const sandboxHostCss = readFileSync(
  resolve(process.cwd(), "src/pages/sandbox/SandboxHost.module.css"),
  "utf8",
);

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
  useSecurityProfile: () => ({
    data: {
      permissions: [{ code: "system.debug" }, { code: "sandbox.run" }, { code: "menu.sandbox" }],
      menuKeys: ["sandbox", "runtime-diagnostics"],
    },
  }),
}));

const scenarios = [
  {
    id: "sbx-lab-critical-k",
    serviceLine: "clinical-collaboration",
    engine: "rule",
    playbook: "RULE_ONLY",
    triggerPoint: "result-review",
    title: "检验复核受控场景",
    narrative: "由场景目录提供的受控场景。",
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
    narrative: "使用当前机构规则运行。",
    hostSummary: "院内业务系统处方复核",
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
    narrative: "真实协同编排。",
    hostSummary: "院内业务系统综合编排",
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
  {
    id: "sbx-pathway-composite",
    serviceLine: "engine-orchestration",
    engine: "pathway",
    playbook: "PATHWAY_COMPOSITE",
    triggerPoint: "patient-view",
    title: "路径协同编排",
    narrative: "使用当前机构临床路径运行。",
    hostSummary: "院内业务系统路径协同",
    patientId: "patient-4",
    encounterId: "encounter-4",
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
    useEvidenceDetailsStore.setState({ enabled: false });
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
      patientPathwayId: "pathway-instance-sbx-1",
      followupPlanId: "followup-plan-sbx-1",
      evaluationRunId: "evaluation-run-sbx-1",
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
    expect(screen.getByText("真实链路已完成")).toBeInTheDocument();
    expect(screen.queryByText("SBX.LAB.CRITICAL.K")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-sandbox-host-1")).not.toBeInTheDocument();
    expect(screen.queryByText("run-sandbox-host-1")).not.toBeInTheDocument();
    expect(screen.queryByText("baseline-sandbox-host-1")).not.toBeInTheDocument();
    expect(screen.queryByText("runtime-sandbox-1")).not.toBeInTheDocument();
    expect(screen.queryByText("pathway-instance-sbx-1")).not.toBeInTheDocument();
    expect(screen.queryByText("followup-plan-sbx-1")).not.toBeInTheDocument();
    expect(screen.queryByText("evaluation-run-sbx-1")).not.toBeInTheDocument();
    expect(screen.getAllByText("当前机构生效版本 · 第 7 版").length).toBeGreaterThan(0);
    expect(screen.getByText("路径实例已生成")).toBeInTheDocument();
    expect(screen.getByText("随访计划已登记")).toBeInTheDocument();
    expect(screen.getByText("评估运行已记录")).toBeInTheDocument();
    expect(screen.getAllByText("当前机构生效版本").length).toBeGreaterThan(0);
    expect(screen.getAllByText("外部副作用已关闭")).toHaveLength(2);

    const frame = screen.getByTitle("临床嵌入式终端") as HTMLIFrameElement;
    fireEvent(
      window,
      new MessageEvent("message", {
        origin: window.location.origin,
        source: frame.contentWindow,
        data: {
          source: "MEDKERNEL_CDSS_EMBED",
          action: "ADOPT",
          cardId: "card-1",
          recommendationStatus: "ADOPTED",
          traceId: "trace-sandbox-host-1",
        },
      }),
    );
    expect(await screen.findByText("宿主已收到采纳建议决策")).toBeInTheDocument();
    expect(screen.getByText("卡片：卡片证据已记录")).toBeInTheDocument();
    expect(screen.getByText("状态：建议已采纳")).toBeInTheDocument();
    expect(screen.getByText("追踪证据：已保留")).toBeInTheDocument();
    expect(screen.queryByText("ADOPT")).not.toBeInTheDocument();
    expect(screen.queryByText("ADOPTED")).not.toBeInTheDocument();
    expect(screen.queryByText("card-1")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-sandbox-host-1")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("switch", { name: "证据详情" }));
    expect(screen.getByText("pathway-instance-sbx-1")).toBeInTheDocument();
    expect(screen.getByText("followup-plan-sbx-1")).toBeInTheDocument();
    expect(screen.getByText("evaluation-run-sbx-1")).toBeInTheDocument();
    expect(screen.getByText("trace-sandbox-host-1")).toBeInTheDocument();
    expect(screen.getByText("run-sandbox-host-1")).toBeInTheDocument();
    expect(screen.getByText("baseline-sandbox-host-1")).toBeInTheDocument();
    expect(screen.getAllByText("runtime-sandbox-1 · 第 7 版").length).toBeGreaterThan(0);
    expect(screen.getByText("宿主已收到采纳建议（ADOPT）决策")).toBeInTheDocument();
    expect(screen.getByText("卡片：card-1")).toBeInTheDocument();
    expect(screen.getByText("状态：建议已采纳（ADOPTED）")).toBeInTheDocument();
  });

  it("stacks the sandbox workspace before the application sidebar can cause root overflow", () => {
    expect(sandboxHostCss).toContain("@media (max-width: 90rem)");
    expect(sandboxHostCss).not.toContain("@media (max-width: 78rem)");
  });

  it("does not declare a nested page main landmark inside the application shell", () => {
    renderSandboxHost();

    expect(screen.queryByRole("main")).not.toBeInTheDocument();
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

    expect(await screen.findByText("当前机构尚未发布可用版本。")).toBeInTheDocument();
    expect(screen.queryByText(/演练机构/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "运行真实引擎链路" })).not.toBeInTheDocument();
    expect(sandboxHookMocks.run).not.toHaveBeenCalled();
  });

  it("uses clinical pathway wording instead of engine terminology", () => {
    renderSandboxHost();
    fireEvent.click(screen.getByRole("button", { name: /路径协同编排/ }));

    expect(screen.getByText("临床路径")).toBeInTheDocument();
    expect(screen.queryByText("路径引擎")).not.toBeInTheDocument();
  });

  it("uses clinical rule wording instead of engine module terminology", () => {
    renderSandboxHost();
    fireEvent.click(screen.getByRole("button", { name: /抗菌药物处方复核/ }));

    expect(screen.getByText("临床规则")).toBeInTheDocument();
    expect(screen.queryByText("规则引擎")).not.toBeInTheDocument();
  });

  it("uses hospital-facing collaboration wording for orchestration scenarios", () => {
    renderSandboxHost();
    fireEvent.click(screen.getByRole("button", { name: /推荐综合卡/ }));

    expect(screen.getByText("医疗智能协同")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "医疗智能协同入口" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "运行真实协同链路" })).toBeInTheDocument();
    expect(screen.queryByText(/引擎编排|真实引擎|引擎能力|引擎处置建议/)).not.toBeInTheDocument();
  });

  it("shows the current institution effective version without exposing package selectors", () => {
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

    expect(screen.getByText("当前机构生效版本")).toBeInTheDocument();
    expect(screen.getByText("当前机构生效版本 · 第 9 版")).toBeInTheDocument();
    expect(screen.queryByText("runtime-platform-1")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("switch", { name: "证据详情" }));
    expect(screen.getByText("runtime-platform-1 · 第 9 版")).toBeInTheDocument();
  });

  it("shows a product-facing scenario catalog warning when the catalog cannot be read", () => {
    sandboxHookMocks.useSandboxScenarios.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: vi.fn(),
    });

    renderSandboxHost();

    expect(screen.getByText("沙盘场景目录暂不可用")).toBeInTheDocument();
    expect(
      screen.getByText("当前仅展示目录未就绪状态，不生成或暗示可运行临床场景。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/后端|前端内置|兜底/)).not.toBeInTheDocument();
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
    expect(screen.getByText("推荐综合卡编排")).toBeInTheDocument();
    expect(screen.getByText("提醒与推荐")).toBeInTheDocument();
    expect(screen.queryByText("RECOMMENDATION_COMPOSITE")).not.toBeInTheDocument();
    expect(screen.queryByText("recommendation")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "运行真实协同链路" }));

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
    expect(await screen.findByText("真实链路已完成")).toBeInTheDocument();
    expect(screen.queryByText("trace-outer-1")).not.toBeInTheDocument();
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
    expect(screen.getByLabelText("历史演练清单")).toBeInTheDocument();
    expect(screen.queryByLabelText("历史重放清单标识")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("历史演练清单"), {
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
    expect(screen.getByText("历史版本 7")).toBeInTheDocument();
    expect(screen.queryByText("RULE.OLD.K@7")).not.toBeInTheDocument();
    expect(screen.queryByText("sha256:old-7")).not.toBeInTheDocument();
    expect(screen.getByText("历史已退役")).toBeInTheDocument();
    expect(screen.queryByText("RETIRED")).not.toBeInTheDocument();
    expect(screen.getAllByText("危急风险").length).toBeGreaterThan(0);
    expect(screen.queryByText("CRITICAL")).not.toBeInTheDocument();
    expect(screen.getByText("历史高钾红线")).toBeInTheDocument();
    expect(screen.getAllByText("历史重放清单").length).toBeGreaterThan(0);
    expect(screen.queryByText("上下文原始 JSON")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("switch", { name: "证据详情" }));
    expect(screen.getByText("sha256:old-7 · 第 4 版")).toBeInTheDocument();
    expect(screen.getByText("RULE.OLD.K@7")).toBeInTheDocument();
    expect(screen.getByText("历史已退役（RETIRED）")).toBeInTheDocument();
    expect(screen.getByText("危急风险（CRITICAL）")).toBeInTheDocument();
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
    fireEvent.click(screen.getByRole("radio", { name: "版本差异评估" }));
    fireEvent.change(screen.getByLabelText("历史演练清单"), {
      target: { value: "replay-2025-001" },
    });
    fireEvent.click(screen.getByRole("button", { name: "运行版本差异评估" }));

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
    expect(await screen.findByRole("region", { name: "规则版本差异" })).toBeInTheDocument();
    expect(screen.getByText("高风险变化规则")).toBeInTheDocument();
    expect(screen.queryByText("RULE.RISK.UP")).not.toBeInTheDocument();
    expect(screen.getByText("严重度升高")).toBeInTheDocument();
    expect(screen.getAllByText("新增命中")).toHaveLength(2);
    expect(
      screen.getByText(/历史：平台标准 第 1 版 \/ 命中；当前：机构版本 第 2 版 \/ 命中/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/PLATFORM|ORG/)).not.toBeInTheDocument();
    expect(screen.queryByText("runtime-current-2")).not.toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("switch", { name: "证据详情" }));
    expect(screen.getByText("runtime-current-2 · 第 2 版")).toBeInTheDocument();
    expect(screen.getByText("RULE.RISK.UP")).toBeInTheDocument();
    expect(
      screen.getByText(
        /历史：平台标准（PLATFORM） 第 1 版 \/ 命中；当前：机构版本（ORG） 第 2 版 \/ 命中/,
      ),
    ).toBeInTheDocument();
  });
});
