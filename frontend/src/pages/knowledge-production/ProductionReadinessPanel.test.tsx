import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useKnowledgeProductionReadiness } from "@/shared/api/hooks";

import ProductionReadinessPanel from "./ProductionReadinessPanel";

vi.mock("@/shared/api/hooks", () => ({
  useKnowledgeProductionReadiness: vi.fn(),
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
];

describe("ProductionReadinessPanel", () => {
  beforeEach(() => {
    vi.mocked(useKnowledgeProductionReadiness).mockReturnValue({
      data: {
        ready: false,
        modelInvocationAllowed: false,
        items: GATE_CODES.map((code) => ({
          code,
          ready: code === "LITERATURE_ROOT",
          message: code === "LITERATURE_ROOT" ? "文献根已配置" : `${code} 尚未满足`,
          evidence: code === "LITERATURE_ROOT" ? "file:///medkernel-data/" : null,
        })),
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);
  });

  it("renders all eight server-backed technical gates in production order", () => {
    render(<ProductionReadinessPanel />);

    const labels = [
      "1. 文献资料库",
      "2. 部署形态",
      "3. 模型服务",
      "4. 医学验证用例",
      "5. 医学评测",
      "6. 外调允许范围",
      "7. 模型策略",
      "8. 提示词、工具与模型版本",
    ];
    labels.forEach((label) => expect(screen.getByText(label)).toBeInTheDocument());
    expect(screen.getByText(/file:\/\/\/medkernel-data\//)).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "前往处理" })).toHaveLength(7);
    expect(
      screen
        .getAllByRole("link", { name: "前往处理" })
        .some((link) => link.getAttribute("href") === "/knowledge/production?step=provider"),
    ).toBe(true);
    expect(screen.getAllByText(/责任角色：医疗引擎运营员/)).toHaveLength(8);
    expect(screen.queryByText(/专家|集成运维员|平台治理管理员/)).not.toBeInTheDocument();
  });
});
