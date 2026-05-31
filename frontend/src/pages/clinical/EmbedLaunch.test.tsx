import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEmbedLaunch, useRecommendationCards, useSubmitEmbedFeedback } from "@/shared/api/hooks";

import EmbedLaunch from "./EmbedLaunch";

vi.mock("@/shared/api/hooks", () => ({
  useEmbedLaunch: vi.fn(),
  useRecommendationCards: vi.fn(),
  useSubmitEmbedFeedback: vi.fn(),
}));

const mockUseEmbedLaunch = vi.mocked(useEmbedLaunch);
const mockUseRecommendationCards = vi.mocked(useRecommendationCards);
const mockUseSubmitEmbedFeedback = vi.mocked(useSubmitEmbedFeedback);

function renderEmbedLaunch() {
  return render(
    <MemoryRouter initialEntries={["/embed/launch?token=launch-token"]}>
      <ConfigProvider>
        <EmbedLaunch />
      </ConfigProvider>
    </MemoryRouter>,
  );
}

describe("EmbedLaunch", () => {
  beforeEach(() => {
    mockUseEmbedLaunch.mockReturnValue({
      data: {
        userId: "doctor-1",
        roleCode: "doctor",
        tenantId: "tenant-A",
        patientId: "MPI-1001",
        encounterId: "ENC-2001",
        triggerPoint: "ORDER_ENTRY",
        active: true,
        traceId: "trace-real-1",
      },
      isLoading: false,
      isError: false,
    } as ReturnType<typeof useEmbedLaunch>);
    mockUseRecommendationCards.mockReturnValue({
      data: { items: [], page: 1, size: 20, total: 0, totalPages: 0 },
    } as unknown as ReturnType<typeof useRecommendationCards>);
    mockUseSubmitEmbedFeedback.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useSubmitEmbedFeedback>);
  });

  it("shows an honest empty recommendation state instead of local fallback cards", () => {
    renderEmbedLaunch();

    expect(screen.getByText("当前就诊暂无可显示的临床建议")).toBeInTheDocument();
    expect(screen.queryByText(/下肢深静脉血栓风险高危预警/)).not.toBeInTheDocument();
    expect(screen.queryByText("tr-local-embed-9122")).not.toBeInTheDocument();
  });
});
