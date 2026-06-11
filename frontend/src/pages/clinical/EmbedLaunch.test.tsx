import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    <MemoryRouter
      initialEntries={["/embed/launch?token=launch-token"]}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
    >
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
        roleCode: "clinical-decision-user",
        tenantId: "tenant-A",
        patientId: "MPI-1001",
        encounterId: "ENC-2001",
        triggerPoint: "ORDER_ENTRY",
        active: true,
        traceId: "trace-real-1",
        parentOrigin: "https://his.hospital.com",
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

  it("posts physician feedback only to the validated parent origin", async () => {
    const submitFeedback = vi.fn().mockResolvedValue({
      callbackStatus: "CONNECTED",
      callbackDelivered: true,
      degradationReason: null,
      traceId: "trace-feedback",
    });
    mockUseSubmitEmbedFeedback.mockReturnValue({
      mutateAsync: submitFeedback,
      isPending: false,
    } as unknown as ReturnType<typeof useSubmitEmbedFeedback>);
    mockUseRecommendationCards.mockReturnValue({
      data: {
        items: [
          {
            cardId: "card-1",
            tenantId: "tenant-A",
            triggerId: "trigger-1",
            cardType: "CLINICAL_QUALITY",
            title: "临床建议",
            summary: "需要医师确认的真实建议",
            riskLevel: "HIGH",
            interruptLevel: "SOFT",
            status: "PENDING",
            severity: "HIGH",
            recommendations: [
              {
                actionCode: "REVIEW",
                actionType: "REVIEW",
                description: "复核当前处置",
              },
            ],
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        totalPages: 1,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useRecommendationCards>);
    const postMessage = vi.spyOn(window, "postMessage").mockImplementation(() => undefined);

    renderEmbedLaunch();
    await userEvent.click(screen.getByRole("button", { name: /符合指征/ }));

    await waitFor(() => {
      expect(postMessage).toHaveBeenCalledWith(
        expect.objectContaining({ source: "MEDKERNEL_CDSS_EMBED", action: "ADOPT" }),
        "https://his.hospital.com",
      );
    });
    expect(postMessage).not.toHaveBeenCalledWith(expect.anything(), "*");
  });
});
