import { ConfigProvider } from "antd";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRunModelEvaluation, useSecurityProfile } from "@/shared/api/hooks";
import { useModelProviders } from "@/shared/api/modelProviders";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import MedicalEvaluationPanel from "./MedicalEvaluationPanel";

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: vi.fn(),
  useRunModelEvaluation: vi.fn(),
}));
vi.mock("@/shared/api/modelProviders", () => ({ useModelProviders: vi.fn() }));
const runEvaluation = vi.fn();

describe("MedicalEvaluationPanel", () => {
  beforeEach(() => {
    window.localStorage.clear();
    useEvidenceDetailsStore.setState({ enabled: false });
    vi.clearAllMocks();
    vi.mocked(useSecurityProfile).mockReturnValue({
      data: { permissions: [{ code: "llm.eval.manage" }] },
      isLoading: false,
      isError: false,
    } as never);
    vi.mocked(useModelProviders).mockReturnValue({
      data: {
        items: [
          {
            providerCode: "medical-model",
            providerType: "OPENAI_COMPATIBLE",
            modelVersion: "medical-v1",
            status: "HEALTHY",
            enabled: false,
          },
        ],
      },
      isLoading: false,
      isError: false,
    } as never);
    vi.mocked(useRunModelEvaluation).mockReturnValue({
      mutateAsync: runEvaluation,
      isPending: false,
    } as never);
    runEvaluation.mockResolvedValue(undefined);
  });

  it("runs a selected healthy provider and model against a medical capability", async () => {
    const user = userEvent.setup();
    render(
      <ConfigProvider>
        <MedicalEvaluationPanel />
      </ConfigProvider>,
    );

    expect(useModelProviders).toHaveBeenCalledWith({ page: 1, size: 50 }, true);
    expect(screen.getByText(/院外模型评测只发送脱敏交付内容/)).toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: "模型服务" }));
    expect(screen.getByText("OpenAI 兼容服务 · medical-v1")).toBeInTheDocument();
    expect(screen.queryByText(/medical-model/)).not.toBeInTheDocument();
    await user.click(screen.getByText("OpenAI 兼容服务 · medical-v1"));
    await user.click(screen.getByRole("combobox", { name: "医学能力" }));
    await user.click(screen.getByText("临床规则草案拟定"));
    expect(screen.queryByText(/rule\.draft/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "运行当前交付文件评测" }));

    await waitFor(() =>
      expect(runEvaluation).toHaveBeenCalledWith({
        providerCode: "medical-model",
        modelVersion: "medical-v1",
        capabilityCode: "rule.draft",
      }),
    );
    expect(screen.getByText(/评测通过后直接作为当前交付文件的模型放行证据/)).toBeInTheDocument();
    expect(screen.queryByText("独立复核")).not.toBeInTheDocument();
  });

  it("证据详情打开后可追溯模型服务与医学能力原始合同", async () => {
    const user = userEvent.setup();
    render(
      <ConfigProvider>
        <MedicalEvaluationPanel />
      </ConfigProvider>,
    );

    await user.click(screen.getByRole("switch", { name: "证据详情" }));
    await user.click(screen.getByRole("combobox", { name: "模型服务" }));
    expect(screen.getByText("medical-model · medical-v1")).toBeInTheDocument();

    await user.click(screen.getByRole("combobox", { name: "医学能力" }));
    expect(screen.getByText("临床规则草案拟定（rule.draft）")).toBeInTheDocument();
  });
});
