import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvaluateRules, useRuleExecutionExplain } from "@/shared/api/hooks";

import RuleValidate from "./RuleValidate";

vi.mock("@/shared/api/hooks", () => ({
  useEvaluateRules: vi.fn(),
  useRuleExecutionExplain: vi.fn(),
}));

const mockUseEvaluateRules = vi.mocked(useEvaluateRules);
const mockUseRuleExecutionExplain = vi.mocked(useRuleExecutionExplain);

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

  beforeEach(() => {
    vi.clearAllMocks();
    evaluate.mockResolvedValue({
      traceId: "trace-rule",
      executionId: "exec-real-1",
      highestSeverity: "HIGH",
      items: [
        {
          executionId: "exec-item-1",
          ruleId: "rule-real-1",
          versionId: "rv-real-1",
          hit: true,
          severity: "HIGH",
          actions: [
            {
              actionCode: "HARD_STOP",
              actionType: "BLOCK",
              message: "必须阻断高危用药",
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
    mockUseRuleExecutionExplain.mockReturnValue({
      data: null,
      isLoading: false,
    } as unknown as ReturnType<typeof useRuleExecutionExplain>);
  });

  it("evaluates rules with CDS Hooks trigger point and renders backend rule DTO fields", async () => {
    const user = userEvent.setup();
    renderRuleValidate();

    expect(screen.getByPlaceholderText("输入触发时点编码")).toHaveValue("order-sign");

    await user.type(screen.getByPlaceholderText("输入本次规则求值绑定的配置包版本"), "pkg-2026.1");
    fireEvent.change(screen.getByPlaceholderText(/粘贴由上下文快照接口返回的脱敏 JSON/), {
      target: { value: '{"patientId":"patient-real-1","orders":["warfarin"]}' },
    });
    await user.click(screen.getByRole("button", { name: /执行匹配校验/ }));

    await waitFor(() => {
      expect(evaluate).toHaveBeenCalledWith({
        triggerPoint: "order-sign",
        patientId: undefined,
        packageVersion: "pkg-2026.1",
        payloadJson: '{"patientId":"patient-real-1","orders":["warfarin"]}',
      });
    });
    expect(await screen.findByText("rule-real-1")).toBeInTheDocument();
    expect(screen.getByText("rv-real-1")).toBeInTheDocument();
    expect(screen.getByText("HARD_STOP")).toBeInTheDocument();
    expect(screen.getByText(/抗凝规则命中/)).toBeInTheDocument();
  });
});
