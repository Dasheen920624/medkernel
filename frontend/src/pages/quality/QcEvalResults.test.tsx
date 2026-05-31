import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import QcEvalResults from "./QcEvalResults";

const mockUseEvaluationResults = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useEvaluationResults: (params: unknown) => mockUseEvaluationResults(params),
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <QcEvalResults />
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

describe("QcEvalResults", () => {
  it("derives dashboard metrics from real query results instead of local KPI constants", () => {
    mockUseEvaluationResults.mockReturnValue({
      data: { items: [], total: 0 },
      refetch: vi.fn(),
      isLoading: false,
      isError: false,
    });

    renderPage();

    expect(screen.getByRole("heading", { name: "评估结果" })).toBeInTheDocument();
    expect(screen.getByText("暂无真实评估结果")).toBeInTheDocument();
    expect(screen.getAllByText(/0/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/485|152|92\.8|6 项/)).not.toBeInTheDocument();
  });
});
