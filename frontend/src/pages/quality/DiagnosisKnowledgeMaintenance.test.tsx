import { render, screen } from "@testing-library/react";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

import DiagnosisKnowledgeMaintenance from "./DiagnosisKnowledgeMaintenance";

vi.mock("./DiagnosisKnowledgePanel", () => ({
  default: () => <div>诊断知识维护工作台</div>,
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <AntdApp>
          <DiagnosisKnowledgeMaintenance />
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

describe("DiagnosisKnowledgeMaintenance", () => {
  it("hosts manual diagnosis maintenance outside the review workspace", () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "诊断知识维护" })).toBeInTheDocument();
    expect(screen.getByText("诊断知识维护工作台")).toBeInTheDocument();
    expect(
      screen.getByText(
        "在统一知识治理下维护诊断身份、诊断标准、鉴别诊断、验证病例与来源证据；发布后再进入平台标准版本或机构生效版本。",
      ),
    ).toBeInTheDocument();
  });
});
