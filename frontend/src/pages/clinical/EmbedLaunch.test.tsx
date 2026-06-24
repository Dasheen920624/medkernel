import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useEmbedLaunch,
  useEmbedRecommendationCards,
  useSubmitEmbedFeedback,
} from "@/shared/api/hooks";

import EmbedLaunch from "./EmbedLaunch";

vi.mock("@/shared/api/hooks", () => ({
  useEmbedLaunch: vi.fn(),
  useEmbedRecommendationCards: vi.fn(),
  useSubmitEmbedFeedback: vi.fn(),
}));

const mockUseEmbedLaunch = vi.mocked(useEmbedLaunch);
const mockUseEmbedRecommendationCards = vi.mocked(useEmbedRecommendationCards);
const mockUseSubmitEmbedFeedback = vi.mocked(useSubmitEmbedFeedback);

const recommendationCard = {
  cardId: "card-1",
  title: "临床建议",
  summary: "需要医师确认的真实建议",
  suggestedAction: "复核当前处置",
  riskLevel: "HIGH" as const,
  interruptLevel: "SOFT" as const,
  status: "PENDING" as const,
  requiresPhysicianConfirmation: true,
  aiGenerated: false,
  sourceSummary: "规则 R-1001 与患者当前就诊上下文",
  traceId: "trace-card-1",
};

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
        roleCode: "clinical-user",
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
    mockUseEmbedRecommendationCards.mockReturnValue({
      data: { items: [], traceId: "trace-real-1" },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useEmbedRecommendationCards>);
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

  it("submits the selected card and posts physician feedback only to the validated parent origin", async () => {
    const submitFeedback = vi.fn().mockResolvedValue({
      token: "launch-token",
      cardId: "card-1",
      actionType: "ADOPT",
      recommendationStatus: "ACCEPTED",
      callbackStatus: "NOT_CONNECTED",
      callbackDelivered: false,
      degradationReason: "HOST_CALLBACK_NOT_CONFIGURED",
      traceId: "trace-feedback",
    });
    mockUseSubmitEmbedFeedback.mockReturnValue({
      mutateAsync: submitFeedback,
      isPending: false,
    } as unknown as ReturnType<typeof useSubmitEmbedFeedback>);
    mockUseEmbedRecommendationCards.mockReturnValue({
      data: {
        items: [recommendationCard],
        traceId: "trace-real-1",
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useEmbedRecommendationCards>);
    const postMessage = vi.spyOn(window, "postMessage").mockImplementation(() => undefined);

    renderEmbedLaunch();
    await userEvent.click(screen.getByRole("button", { name: /采纳建议/ }));

    await waitFor(() => {
      expect(submitFeedback).toHaveBeenCalledWith({
        token: "launch-token",
        cardId: "card-1",
        actionType: "ADOPT",
        reason: "医师确认符合临床指征并采纳建议",
      });
      expect(postMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          source: "MEDKERNEL_CDSS_EMBED",
          action: "ADOPT",
          cardId: "card-1",
        }),
        "https://his.hospital.com",
      );
    });
    expect(postMessage).not.toHaveBeenCalledWith(expect.anything(), "*");
  });

  it.each([
    ["稍后处理", "LATER"],
    ["忽略本次", "IGNORE"],
    ["关闭建议", "CLOSE"],
  ] as const)("supports the host action %s with the selected card", async (label, actionType) => {
    const submitFeedback = vi.fn().mockResolvedValue({
      token: "launch-token",
      cardId: "card-1",
      actionType,
      recommendationStatus: actionType === "LATER" ? "DEFERRED" : "DISMISSED",
      callbackStatus: "NOT_CONNECTED",
      callbackDelivered: false,
      degradationReason: "HOST_CALLBACK_NOT_CONFIGURED",
      traceId: "trace-feedback",
    });
    mockUseSubmitEmbedFeedback.mockReturnValue({
      mutateAsync: submitFeedback,
      isPending: false,
    } as unknown as ReturnType<typeof useSubmitEmbedFeedback>);
    mockUseEmbedRecommendationCards.mockReturnValue({
      data: { items: [recommendationCard], traceId: "trace-real-1" },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useEmbedRecommendationCards>);

    renderEmbedLaunch();
    await userEvent.click(screen.getByRole("button", { name: /其他处理/ }));
    await userEvent.click(await screen.findByText(label));

    await waitFor(() => {
      expect(submitFeedback).toHaveBeenCalledWith({
        token: "launch-token",
        cardId: "card-1",
        actionType,
        reason: expect.any(String),
      });
    });
  });

  it("requires a reason when rejecting and submits it against the selected card", async () => {
    const submitFeedback = vi.fn().mockResolvedValue({
      token: "launch-token",
      cardId: "card-1",
      actionType: "REJECT",
      recommendationStatus: "REJECTED",
      callbackStatus: "NOT_CONNECTED",
      callbackDelivered: false,
      degradationReason: "HOST_CALLBACK_NOT_CONFIGURED",
      traceId: "trace-feedback",
    });
    mockUseSubmitEmbedFeedback.mockReturnValue({
      mutateAsync: submitFeedback,
      isPending: false,
    } as unknown as ReturnType<typeof useSubmitEmbedFeedback>);
    mockUseEmbedRecommendationCards.mockReturnValue({
      data: { items: [recommendationCard], traceId: "trace-real-1" },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useEmbedRecommendationCards>);

    renderEmbedLaunch();
    await userEvent.click(screen.getByRole("button", { name: /不采纳/ }));
    await userEvent.click(screen.getByText("患者临床表现及风险指征不符"));
    await userEvent.click(screen.getByRole("button", { name: "提交备案" }));

    await waitFor(() => {
      expect(submitFeedback).toHaveBeenCalledWith({
        token: "launch-token",
        cardId: "card-1",
        actionType: "REJECT",
        reason: "CLINICAL_MISMATCH",
      });
    });
  });
});
