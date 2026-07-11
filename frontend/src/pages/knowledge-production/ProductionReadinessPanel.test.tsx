import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useKnowledgeProductionReadiness, useSecurityProfile } from "@/shared/api/hooks";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import ProductionReadinessPanel from "./ProductionReadinessPanel";

vi.mock("@/shared/api/hooks", () => ({
  useKnowledgeProductionReadiness: vi.fn(),
  useSecurityProfile: vi.fn(),
}));

const GATE_CODES = [
  "LITERATURE_ROOT",
  "DEPLOYMENT_FORM",
  "MODEL_PROVIDER",
  "REGRESSION_BASELINE",
  "MODEL_EVALUATION",
  "EGRESS_GOVERNANCE",
  "MODEL_POLICY",
  "VERSION_TRIPLE",
] as const;

const evidenceByGate: Partial<Record<(typeof GATE_CODES)[number], string>> = {
  LITERATURE_ROOT: "file:///medkernel-data/",
  MODEL_PROVIDER: "模型服务：mimo-public；模型版本：medical-v1",
  EGRESS_GOVERNANCE: "能力：knowledge.production.knowledge；原因：字段范围缺失",
  MODEL_POLICY: "策略：外部模型；适用范围：TENANT:t-hospital",
  VERSION_TRIPLE: "版本组合：12；提示词：prompt-v2；工具：tool-v1；模型：medical-v1",
};

describe("ProductionReadinessPanel", () => {
  beforeEach(() => {
    useEvidenceDetailsStore.setState({ enabled: false });
    vi.mocked(useKnowledgeProductionReadiness).mockReturnValue({
      data: {
        ready: false,
        modelInvocationAllowed: false,
        items: GATE_CODES.map((code) => ({
          code,
          ready: code === "LITERATURE_ROOT",
          message: code === "LITERATURE_ROOT" ? "文献资料库已配置" : "当前前置条件尚未满足",
          evidence: evidenceByGate[code] ?? null,
        })),
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);
    vi.mocked(useSecurityProfile).mockReturnValue({
      data: {
        permissions: [{ code: "asset.read" }],
        menuKeys: ["knowledge-production"],
      },
      isLoading: false,
      isError: false,
    } as never);
  });

  it("renders all eight server-backed business gates in production order", () => {
    render(<ProductionReadinessPanel />);

    const labels = [
      "1. 文献资料库",
      "2. 部署形态",
      "3. 模型服务",
      "4. 医学验证用例",
      "5. 医学评测",
      "6. 模型使用边界",
      "7. 模型生产策略",
      "8. 提示词、工具与模型版本",
    ];
    labels.forEach((label) => expect(screen.getByText(label)).toBeInTheDocument());
    expect(screen.queryByText("7. 模型策略")).not.toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "前往处理" })).toHaveLength(7);
    expect(
      screen
        .getAllByRole("link", { name: "前往处理" })
        .some((link) => link.getAttribute("href") === "/knowledge/production?step=provider"),
    ).toBe(true);
    expect(screen.getAllByText(/责任角色：医疗引擎运营员/)).toHaveLength(8);
    expect(screen.queryByText(/专家|集成运维员|平台治理管理员/)).not.toBeInTheDocument();
  });

  it("默认隐藏生产前校验低频证据，授权打开证据详情后再展示", async () => {
    const user = userEvent.setup();
    render(<ProductionReadinessPanel />);

    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getAllByText(/证据已记录/)).toHaveLength(5);
    expect(screen.queryByText(/file:\/\/\/medkernel-data\//)).not.toBeInTheDocument();
    expect(screen.queryByText(/mimo-public/)).not.toBeInTheDocument();
    expect(screen.queryByText(/knowledge\.production\.knowledge/)).not.toBeInTheDocument();
    expect(screen.queryByText(/TENANT:t-hospital/)).not.toBeInTheDocument();
    expect(screen.queryByText(/prompt-v2/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText(/file:\/\/\/medkernel-data\//)).toBeInTheDocument();
    expect(screen.getByText(/mimo-public/)).toBeInTheDocument();
    expect(screen.getByText(/knowledge\.production\.knowledge/)).toBeInTheDocument();
    expect(screen.getByText(/TENANT:t-hospital/)).toBeInTheDocument();
    expect(screen.getByText(/prompt-v2/)).toBeInTheDocument();
  });
});
