import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ModelProductionConsole from "./ModelProductionConsole";

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

describe("ModelProductionConsole", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/knowledge/production");
  });

  it("orders the complete model production workflow on one page", () => {
    render(<ModelProductionConsole />);

    expect(screen.getByRole("heading", { name: "模型生产控制台" })).toBeInTheDocument();
    const labels = ["模型服务与 Key", "医学评测", "八项生产闸", "开始生产"];
    for (let index = 1; index < labels.length; index += 1) {
      const previous = screen.getAllByText(labels[index - 1], { exact: true })[0];
      const current = screen.getAllByText(labels[index], { exact: true })[0];
      expect(previous.compareDocumentPosition(current) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
        Node.DOCUMENT_POSITION_FOLLOWING,
      );
    }
    expect(screen.getByText("provider-panel")).toBeInTheDocument();
    expect(screen.getByText("evaluation-panel")).toBeInTheDocument();
    expect(screen.queryByText("独立复核")).not.toBeInTheDocument();
    expect(screen.getByText("readiness-panel")).toBeInTheDocument();
    expect(screen.getByText("production-panel")).toBeInTheDocument();
  });
});
