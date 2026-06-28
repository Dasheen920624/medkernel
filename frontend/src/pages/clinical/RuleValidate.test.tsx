import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useContextSnapshotDetail,
  useContextSnapshots,
  useEvaluateRules,
  useRuleExecutions,
  useRuleExecutionExplain,
  useCaptureRuleOverride,
} from "@/shared/api/hooks";

import RuleValidate from "./RuleValidate";

vi.mock("@/shared/api/hooks", () => ({
  useContextSnapshotDetail: vi.fn(),
  useContextSnapshots: vi.fn(),
  useEvaluateRules: vi.fn(),
  useRuleExecutions: vi.fn(),
  useRuleExecutionExplain: vi.fn(),
  useCaptureRuleOverride: vi.fn(),
}));

const mockUseContextSnapshotDetail = vi.mocked(useContextSnapshotDetail);
const mockUseContextSnapshots = vi.mocked(useContextSnapshots);
const mockUseEvaluateRules = vi.mocked(useEvaluateRules);
const mockUseRuleExecutions = vi.mocked(useRuleExecutions);
const mockUseRuleExecutionExplain = vi.mocked(useRuleExecutionExplain);
const mockUseCaptureRuleOverride = vi.mocked(useCaptureRuleOverride);

function renderRuleValidate() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <RuleValidate />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("RuleValidate", () => {
  const evaluate = vi.fn();
  const captureOverride = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    evaluate.mockResolvedValue({
      traceId: "trace-rule",
      requestId: "eval-real-1",
      highestSeverity: "HIGH",
      cards: [],
      items: [
        {
          executionId: "exec-item-1",
          ruleId: "rule-real-1",
          versionId: "rv-real-1",
          hit: true,
          severity: "HIGH",
          status: "SUCCESS",
          suppressedBy: null,
          deduplicatedFromExecutionId: null,
          actions: [
            {
              actionCode: "BLOCK",
              severity: "HIGH",
              indicator: "critical",
              summary: "必须阻断高危用药",
              detail: "必须阻断高危用药",
              source: { label: "高危用药规则" },
              suggestions: [],
              overrideReasons: [],
              requiresPhysicianConfirmation: true,
            },
          ],
          explanation: { reason: "抗凝规则命中" },
        },
      ],
    });
    mockUseEvaluateRules.mockReturnValue({
      mutateAsync: evaluate,
      isPending: false,
    } as unknown as ReturnType<typeof useEvaluateRules>);
    captureOverride.mockResolvedValue({
      overrideId: "rov-1",
      executionId: "exec-item-1",
      ruleId: "rule-real-1",
      actionCode: "BLOCK",
      reason: "已完成临床复核",
      overriddenBy: "doctor-1",
      overriddenAt: "2026-06-07T08:00:00Z",
      traceId: "trace-rule",
    });
    mockUseCaptureRuleOverride.mockReturnValue({
      mutateAsync: captureOverride,
      isPending: false,
    } as unknown as ReturnType<typeof useCaptureRuleOverride>);
    mockUseContextSnapshots.mockReturnValue({
      data: {
        items: [
          {
            snapshotId: "snapshot-real-1",
            patientId: "patient-real-1",
            encounterId: "encounter-real-1",
            status: "ACTIVE",
            qualityStatus: "VALID",
          },
        ],
        total: 1,
        page: 1,
        size: 20,
        totalPages: 1,
        hasNext: false,
        totalEstimated: false,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useContextSnapshots>);
    mockUseContextSnapshotDetail.mockImplementation(
      (snapshotId: string) =>
        ({
          data:
            snapshotId === "snapshot-real-1"
              ? {
                  snapshotId,
                  status: "ACTIVE",
                  resources: { patient: { patientId: "patient-real-1" } },
                  runtimeReleaseId: "runtime-release-rule",
                  qualityStatus: "VALID",
                  missingFields: [],
                  mappingStatus: {},
                  traceId: "trace-snapshot",
                }
              : undefined,
          isLoading: false,
          isError: false,
        }) as unknown as ReturnType<typeof useContextSnapshotDetail>,
    );
    mockUseRuleExecutionExplain.mockReturnValue({
      data: null,
      isLoading: false,
    } as unknown as ReturnType<typeof useRuleExecutionExplain>);
    mockUseRuleExecutions.mockReturnValue({
      data: {
        items: [
          {
            executionId: "exec-history-1",
            ruleId: "rule-history-1",
            versionId: "rv-history-1",
            triggerPoint: "order-sign",
            hit: true,
            severity: "HIGH",
            status: "SUCCESS",
            executedAt: "2026-06-07T08:00:00Z",
            traceId: "trace-history",
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
        totalEstimated: false,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useRuleExecutions>);
  });

  it("evaluates rules with CDS Hooks trigger point and renders rule DTO fields", async () => {
    const user = userEvent.setup();
    renderRuleValidate();

    expect(screen.getByRole("combobox", { name: "触发时点" })).toBeInTheDocument();
    expect(screen.getByText("签署医嘱")).toBeInTheDocument();

    expect(screen.queryByLabelText(/Payload JSON/)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("患者信息"), "patient-real-1");
    await user.click(screen.getByRole("button", { name: "选择第 1 个临床快照" }));
    await user.click(screen.getByRole("button", { name: /执行匹配校验/ }));

    await waitFor(() => {
      expect(evaluate).toHaveBeenCalledWith({
        triggerPoint: "order-sign",
        contextSnapshotId: "snapshot-real-1",
      });
    });
    expect(screen.getByText("机构生效版本已确认")).toBeInTheDocument();
    expect(screen.getByText("评估已留痕")).toBeInTheDocument();
    expect(screen.getByText("评估请求已记录")).toBeInTheDocument();
    expect(await screen.findByText("规则已命中")).toBeInTheDocument();
    expect(screen.getByText("版本证据已关联")).toBeInTheDocument();
    expect(screen.getByText("必须阻断高危用药")).toBeInTheDocument();
    expect(screen.getByText(/抗凝规则命中/)).toBeInTheDocument();
    expect(screen.queryByText("runtime-release-rule")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-rule")).not.toBeInTheDocument();
    expect(screen.queryByText("eval-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("rule-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("rv-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("BLOCK")).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("runtime-release-rule")).toBeInTheDocument();
    expect(screen.getByText("trace-rule")).toBeInTheDocument();
    expect(screen.getByText("eval-real-1")).toBeInTheDocument();
    expect(screen.getByText("rule-real-1")).toBeInTheDocument();
    expect(screen.getByText("rv-real-1")).toBeInTheDocument();
    expect(screen.getByText("BLOCK")).toBeInTheDocument();
  });

  it("highlights critical redline hits as non-ignorable clinical safety evidence", async () => {
    const user = userEvent.setup();
    evaluate.mockResolvedValue({
      traceId: "trace-redline-rule",
      requestId: "eval-redline-1",
      highestSeverity: "CRITICAL",
      cards: [],
      items: [
        {
          executionId: "exec-redline-item-1",
          ruleId: "RDL-DDI-001",
          versionId: "rv-redline-2026.2",
          hit: true,
          severity: "CRITICAL",
          status: "SUCCESS",
          suppressedBy: null,
          deduplicatedFromExecutionId: null,
          actions: [
            {
              actionCode: "BLOCK",
              severity: "CRITICAL",
              indicator: "critical",
              summary: "安全红线禁止忽略",
              detail: "安全红线禁止忽略",
              source: { label: "临床安全红线" },
              suggestions: [],
              overrideReasons: [],
              requiresPhysicianConfirmation: true,
            },
          ],
          explanation: { reason: "华法林与 NSAID 联用触发红线" },
        },
      ],
    });

    renderRuleValidate();

    await user.type(screen.getByLabelText("患者信息"), "patient-real-1");
    await user.click(screen.getByRole("button", { name: "选择第 1 个临床快照" }));
    await user.click(screen.getByRole("button", { name: /执行匹配校验/ }));

    expect(await screen.findByText("安全红线不可忽略")).toBeInTheDocument();
    expect(screen.getAllByText("CRITICAL").length).toBeGreaterThan(0);
    expect(screen.getByText("安全红线禁止忽略")).toBeInTheDocument();
    expect(screen.queryByText("BLOCK")).not.toBeInTheDocument();
    expect(screen.getByText(/该校验只提示和阻断，不自动改写医嘱/)).toBeInTheDocument();
  });

  it("requires and submits a reason when overriding a blocking action", async () => {
    const user = userEvent.setup();
    renderRuleValidate();

    await user.type(screen.getByLabelText("患者信息"), "patient-real-1");
    await user.click(screen.getByRole("button", { name: "选择第 1 个临床快照" }));
    await user.click(screen.getByRole("button", { name: /执行匹配校验/ }));
    await user.click(await screen.findByRole("button", { name: "记录人工继续" }));

    const dialog = await screen.findByRole("dialog", { name: "记录人工继续" });
    await user.type(within(dialog).getByLabelText("越权理由"), "已完成临床复核");
    await user.click(within(dialog).getByRole("button", { name: "确认留痕" }));

    await waitFor(() =>
      expect(captureOverride).toHaveBeenCalledWith({
        executionId: "exec-item-1",
        actionCode: "BLOCK",
        reason: "已完成临床复核",
      }),
    );
  });

  it("replays a historical rule execution explanation from the tenant execution directory", async () => {
    const user = userEvent.setup();
    mockUseRuleExecutionExplain.mockImplementation(
      (executionId: string) =>
        ({
          data:
            executionId === "exec-history-1"
              ? {
                  executionId: "exec-history-1",
                  ruleId: "rule-history-1",
                  versionId: "rv-history-1",
                  triggerPoint: "order-sign",
                  eventId: "event-history-1",
                  inputDigest: "sha256:history",
                  hit: true,
                  severity: "HIGH",
                  actions: [
                    {
                      actionCode: "STRONG_REMINDER",
                      severity: "HIGH",
                      indicator: "critical",
                      summary: "历史执行动作",
                      detail: "历史执行动作",
                      source: { label: "历史规则来源" },
                      suggestions: [],
                      overrideReasons: [],
                      requiresPhysicianConfirmation: true,
                    },
                  ],
                  explanation: { summary: "历史红线回放解释" },
                  status: "SUCCESS",
                  traceId: "trace-history",
                }
              : null,
          isLoading: false,
        }) as unknown as ReturnType<typeof useRuleExecutionExplain>,
    );

    renderRuleValidate();

    expect(screen.queryByPlaceholderText("输入历史执行 ExecutionId")).not.toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: "历史执行记录" }));
    await user.click(await screen.findByText(/规则执行已记录 · 签署医嘱 · 成功/));
    await user.click(screen.getByRole("button", { name: /回放执行解释/ }));

    expect(await screen.findByText("执行记录已关联")).toBeInTheDocument();
    expect(screen.getByText("追踪证据已记录")).toBeInTheDocument();
    expect(screen.getByText("输入摘要已校验")).toBeInTheDocument();
    expect(screen.queryByText("exec-history-1")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-history")).not.toBeInTheDocument();
    expect(screen.queryByText("sha256:history")).not.toBeInTheDocument();
    expect(screen.getByText(/历史红线回放解释/)).toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect((await screen.findAllByText("exec-history-1")).length).toBeGreaterThan(0);
    expect(screen.getByText("trace-history")).toBeInTheDocument();
    expect(screen.getByText("sha256:history")).toBeInTheDocument();
    expect(mockUseRuleExecutionExplain).toHaveBeenLastCalledWith("exec-history-1");
    expect(mockUseRuleExecutions).toHaveBeenCalledWith({ page: 1, size: 20 });
  });
});
