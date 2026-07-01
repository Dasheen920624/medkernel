import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import KnowledgeProductionPage from "./KnowledgeProductionPage";

vi.mock("./ProviderSetupPanel", () => ({
  default: () => <div>provider-panel</div>,
}));
vi.mock("./MedicalEvaluationPanel", () => ({
  default: () => <div>evaluation-panel</div>,
}));
vi.mock("./ProductionReadinessPanel", () => ({
  default: () => <div>readiness-panel</div>,
}));
vi.mock("@/pages/quality/KnowledgeGovernance", () => ({
  KnowledgeProductionWorkspace: () => <div>production-panel</div>,
}));

describe("KnowledgeProductionPage", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/knowledge/production");
  });

  it("orders the complete knowledge production workflow on one page", () => {
    render(<KnowledgeProductionPage />);

    expect(screen.getByRole("heading", { name: "知识生产" })).toBeInTheDocument();
    const labels = ["模型服务与密钥", "医学评测", "生产前校验", "开始生产"];
    for (let index = 1; index < labels.length; index += 1) {
      const previous = screen.getAllByText(labels[index - 1], { exact: true })[0];
      const current = screen.getAllByText(labels[index], { exact: true })[0];
      expect(previous.compareDocumentPosition(current) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
        Node.DOCUMENT_POSITION_FOLLOWING,
      );
    }
    expect(screen.getByText("provider-panel")).toBeInTheDocument();
    expect(screen.getByText("evaluation-panel")).toBeInTheDocument();
    expect(screen.getByText("正式知识不得绕过统一治理链")).toBeInTheDocument();
    expect(
      screen.getByText(/无模型时仍可完成来源登记、人工维护、确定性校验、审核发布/),
    ).toBeInTheDocument();
    expect(screen.queryByText("正式知识只允许大模型生产")).not.toBeInTheDocument();
    expect(screen.queryByText("独立复核")).not.toBeInTheDocument();
    expect(screen.getByText("readiness-panel")).toBeInTheDocument();
    expect(screen.getByText("production-panel")).toBeInTheDocument();
  });
});
