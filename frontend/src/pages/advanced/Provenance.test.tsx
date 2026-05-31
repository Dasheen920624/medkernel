import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import Provenance from "./Provenance";

const mockUseAuditEvents = vi.fn();
const mockUseEvidences = vi.fn();
const mockUseVerifyEvidence = vi.fn();
const mockUseExportEvidences = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useAuditEvents: () => mockUseAuditEvents(),
  useEvidences: (params: unknown) => mockUseEvidences(params),
  useVerifyEvidence: () => mockUseVerifyEvidence(),
  useExportEvidences: () => mockUseExportEvidences(),
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
        <Provenance />
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

describe("Provenance", () => {
  it("renders honest empty evidence state without local demo provenance chains", () => {
    mockUseAuditEvents.mockReturnValue({
      data: [],
      isLoading: false,
      refetch: vi.fn(),
    });
    mockUseEvidences.mockReturnValue({
      data: { items: [], total: 0 },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
    mockUseVerifyEvidence.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseExportEvidences.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    renderPage();

    expect(screen.getByRole("heading", { name: "来源与临床证据追溯" })).toBeInTheDocument();
    expect(screen.getByText("暂无真实证据快照")).toBeInTheDocument();
    expect(screen.queryByText(/tr-stk-proof-009/)).not.toBeInTheDocument();
    expect(screen.queryByText(/演示证据链|防伪盖章|自校验沙箱/)).not.toBeInTheDocument();
  });
});
