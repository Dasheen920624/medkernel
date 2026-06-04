import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useClinicalRecommendationCards,
  useCreateRecommendationTrigger,
  useRecommendationCardDetail,
  useRecommendationCardSources,
  useRecommendationCards,
  useRecommendationFatigueSignals,
  useRecommendationStats,
  useRecommendationTriggerDiagnose,
  useSubmitRecommendationFeedback,
} from "@/shared/api/hooks";

import CdssFatigue from "./CdssFatigue";

vi.mock("@/shared/api/hooks", () => ({
  useClinicalRecommendationCards: vi.fn(),
  useCreateRecommendationTrigger: vi.fn(),
  useRecommendationCardDetail: vi.fn(),
  useRecommendationCardSources: vi.fn(),
  useRecommendationCards: vi.fn(),
  useRecommendationFatigueSignals: vi.fn(),
  useRecommendationStats: vi.fn(),
  useRecommendationTriggerDiagnose: vi.fn(),
  useSubmitRecommendationFeedback: vi.fn(),
}));

const mockUseClinicalRecommendationCards = vi.mocked(useClinicalRecommendationCards);
const mockUseCreateRecommendationTrigger = vi.mocked(useCreateRecommendationTrigger);
const mockUseRecommendationCardDetail = vi.mocked(useRecommendationCardDetail);
const mockUseRecommendationCardSources = vi.mocked(useRecommendationCardSources);
const mockUseRecommendationCards = vi.mocked(useRecommendationCards);
const mockUseRecommendationFatigueSignals = vi.mocked(useRecommendationFatigueSignals);
const mockUseRecommendationStats = vi.mocked(useRecommendationStats);
const mockUseRecommendationTriggerDiagnose = vi.mocked(useRecommendationTriggerDiagnose);
const mockUseSubmitRecommendationFeedback = vi.mocked(useSubmitRecommendationFeedback);

function renderCdssFatigue() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <CdssFatigue />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("CdssFatigue", () => {
  const refetchCards = vi.fn();
  const refetchDetail = vi.fn();
  const refetchSources = vi.fn();
  const refetchFatigue = vi.fn();
  const refetchDiagnose = vi.fn();
  const submitFeedback = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseRecommendationCards.mockReturnValue({
      data: {
        items: [
          {
            cardId: "card-real-1",
            tenantId: "tenant-A",
            triggerId: "trigger-real-1",
            cardType: "MEDICATION",
            title: "抗凝用药风险提醒",
            summary: "患者当前医嘱满足抗凝风险规则",
            riskLevel: "HIGH",
            interruptLevel: "WEAK_INTERRUPTIVE",
            status: "PENDING",
            fatigueKey: "WARD_ORDER:ANTICOAG",
          },
        ],
        page: 1,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isLoading: false,
      isError: false,
      refetch: refetchCards,
    } as unknown as ReturnType<typeof useRecommendationCards>);
    mockUseClinicalRecommendationCards.mockReturnValue({
      data: {
        items: [
          {
            cardId: "card-real-1",
            triggerId: "trigger-real-1",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            patientPathwayId: "pathway-real-1",
            scenarioCode: "WARD_ORDER",
            triggerType: "order-sign",
            cardType: "MEDICATION",
            title: "抗凝用药风险提醒",
            summary: "患者当前医嘱满足抗凝风险规则",
            suggestedAction: "请确认出血风险评估",
            riskLevel: "HIGH",
            interruptLevel: "WEAK_INTERRUPTIVE",
            status: "PENDING",
            fatigueKey: "WARD_ORDER:ANTICOAG",
            requiresPhysicianConfirmation: true,
            aiGenerated: false,
            traceId: "trace-rec",
          },
        ],
        page: 1,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isLoading: false,
      isError: false,
      refetch: refetchCards,
    } as unknown as ReturnType<typeof useClinicalRecommendationCards>);
    mockUseRecommendationStats.mockReturnValue({
      data: {
        totalCount: 5,
        pendingCount: 1,
        acceptedCount: 2,
        rejectedCount: 1,
        dismissedCount: 0,
        deferredCount: 0,
        suppressedCount: 1,
        expiredCount: 0,
        acceptanceRatePercent: 66.7,
        traceId: "trace-rec",
      },
    } as unknown as ReturnType<typeof useRecommendationStats>);
    mockUseRecommendationCardDetail.mockReturnValue({
      data: {
        card: {
          cardId: "card-real-1",
          tenantId: "tenant-A",
          triggerId: "trigger-real-1",
          cardType: "MEDICATION",
          title: "抗凝用药风险提醒",
          summary: "患者当前医嘱满足抗凝风险规则",
          suggestedAction: "请确认出血风险评估",
          riskLevel: "HIGH",
          interruptLevel: "WEAK_INTERRUPTIVE",
          status: "PENDING",
          fatigueKey: "WARD_ORDER:ANTICOAG",
        },
        trigger: {
          triggerId: "trigger-real-1",
          patientId: "patient-real-1",
          encounterId: "enc-real-1",
          patientPathwayId: "pathway-real-1",
          scenarioCode: "WARD_ORDER",
          triggerType: "order-sign",
        },
        sources: [],
        feedback: [
          {
            feedbackId: "feedback-real-1",
            cardId: "card-real-1",
            feedbackType: "ACCEPT",
            reasonCode: "CONFIRMED",
            reasonText: "已确认风险，按指南处理。",
            operatorId: "doctor-real-1",
            operatorRole: "DOCTOR",
            createdAt: "2026-06-04T00:00:00Z",
          },
        ],
        fatigueSignals: [],
        traceId: "trace-rec",
      },
      refetch: refetchDetail,
    } as unknown as ReturnType<typeof useRecommendationCardDetail>);
    mockUseRecommendationCardSources.mockReturnValue({
      data: [],
      refetch: refetchSources,
    } as unknown as ReturnType<typeof useRecommendationCardSources>);
    mockUseRecommendationFatigueSignals.mockReturnValue({
      data: { items: [], total: 0 },
      refetch: refetchFatigue,
    } as unknown as ReturnType<typeof useRecommendationFatigueSignals>);
    mockUseRecommendationTriggerDiagnose.mockReturnValue({
      data: null,
      refetch: refetchDiagnose,
    } as unknown as ReturnType<typeof useRecommendationTriggerDiagnose>);
    mockUseCreateRecommendationTrigger.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useCreateRecommendationTrigger>);
    submitFeedback.mockResolvedValue({
      cardId: "card-real-1",
      status: "ACCEPTED",
      traceId: "trace-rec",
    });
    mockUseSubmitRecommendationFeedback.mockReturnValue({
      mutateAsync: submitFeedback,
      isPending: false,
    } as unknown as ReturnType<typeof useSubmitRecommendationFeedback>);
  });

  it("renders clinical reminder cards from trigger context and real aggregate stats", () => {
    renderCdssFatigue();

    expect(mockUseClinicalRecommendationCards).toHaveBeenCalledWith({
      status: undefined,
      riskLevel: undefined,
      patientId: undefined,
      page: 1,
      size: 10,
    });
    expect(screen.getAllByText("patient-real-1").length).toBeGreaterThan(0);
    expect(screen.getAllByText("WARD_ORDER").length).toBeGreaterThan(0);
    expect(screen.getByText("已采纳")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("采纳率")).toBeInTheDocument();
    expect(screen.getByText("66.7%")).toBeInTheDocument();
  });

  it("shows persisted physician feedback identity and never submits operatorId from the browser", async () => {
    const user = userEvent.setup();
    renderCdssFatigue();

    await user.click(screen.getByRole("button", { name: /查看与人机反馈/ }));

    expect(await screen.findByText(/doctor-real-1/)).toBeInTheDocument();
    expect(screen.getByText("已确认风险，按指南处理。")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /确认并予以采纳/ }));

    await waitFor(() => {
      expect(submitFeedback).toHaveBeenCalledWith({
        feedbackType: "ACCEPT",
        reasonCode: "CONFIRMED",
        reasonText: "医师确认采纳提醒建议",
        operatorRole: "DOCTOR",
      });
    });
    expect(submitFeedback.mock.calls[0][0]).not.toHaveProperty("operatorId");
  });
});
