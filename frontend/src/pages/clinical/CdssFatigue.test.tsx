import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useClinicalRecommendationCards,
  useContextSnapshotDetail,
  useContextSnapshots,
  useEvaluateRecommendations,
  useInterpretDiagnosticReport,
  useRecommendationCardDetail,
  useRecommendationCardSources,
  useRecommendationCards,
  useRecommendationFatigueSignals,
  useRecommendationStats,
  useRecommendationTriggerDiagnose,
  useSubmitRecommendationFeedback,
} from "@/shared/api/hooks";

import CdssFatigue from "./CdssFatigue";

const CDSS_INTERACTION_TIMEOUT_MS = 15_000;

vi.mock("@/shared/api/hooks", () => ({
  useClinicalRecommendationCards: vi.fn(),
  useContextSnapshotDetail: vi.fn(),
  useContextSnapshots: vi.fn(),
  useEvaluateRecommendations: vi.fn(),
  useInterpretDiagnosticReport: vi.fn(),
  useRecommendationCardDetail: vi.fn(),
  useRecommendationCardSources: vi.fn(),
  useRecommendationCards: vi.fn(),
  useRecommendationFatigueSignals: vi.fn(),
  useRecommendationStats: vi.fn(),
  useRecommendationTriggerDiagnose: vi.fn(),
  useSubmitRecommendationFeedback: vi.fn(),
}));

const mockUseClinicalRecommendationCards = vi.mocked(useClinicalRecommendationCards);
const mockUseContextSnapshotDetail = vi.mocked(useContextSnapshotDetail);
const mockUseContextSnapshots = vi.mocked(useContextSnapshots);
const mockUseEvaluateRecommendations = vi.mocked(useEvaluateRecommendations);
const mockUseInterpretDiagnosticReport = vi.mocked(useInterpretDiagnosticReport);
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
  const evaluateRecommendations = vi.fn();
  const interpretDiagnosticReport = vi.fn();

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
          {
            feedbackId: "feedback-real-2",
            cardId: "card-real-1",
            feedbackType: "REJECT",
            reasonCode: "不符合当前患者指征",
            reasonText: "患者当前情况不适用，已记录替代处理方案。",
            operatorId: "doctor-real-2",
            operatorRole: "DOCTOR",
            createdAt: "2026-06-04T00:10:00Z",
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
    mockUseContextSnapshots.mockReturnValue({
      data: {
        items: [
          {
            snapshotId: "snapshot-rec-1",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            status: "ACTIVE",
            qualityStatus: "VALID",
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
        totalEstimated: false,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useContextSnapshots>);
    mockUseContextSnapshotDetail.mockImplementation(
      (snapshotId: string) =>
        ({
          data:
            snapshotId === "snapshot-rec-1"
              ? {
                  snapshotId,
                  status: "ACTIVE",
                  resources: {},
                  runtimeReleaseId: "runtime-release-cdss",
                  qualityStatus: "VALID",
                  missingFields: [],
                  mappingStatus: {},
                  traceId: "trace-snapshot-rec",
                }
              : undefined,
          isLoading: false,
          isError: false,
        }) as unknown as ReturnType<typeof useContextSnapshotDetail>,
    );
    evaluateRecommendations.mockResolvedValue({
      triggerId: "trigger-evaluated-1",
      status: "EVALUATED",
      totalCardCount: 2,
      visibleCardCount: 1,
      suppressedCardCount: 1,
      modelStatus: "MODEL_DISABLED",
      cards: [],
      traceId: "trace-evaluate-rec",
    });
    mockUseEvaluateRecommendations.mockReturnValue({
      mutateAsync: evaluateRecommendations,
      isPending: false,
    } as unknown as ReturnType<typeof useEvaluateRecommendations>);
    interpretDiagnosticReport.mockResolvedValue({
      contextSnapshotId: "snapshot-rec-1",
      runtimeReleaseId: "runtime-release-report",
      interpretations: [
        {
          reportId: "report-k-1",
          reportType: "LAB.POTASSIUM",
          conclusion: "血钾 6.3 mmol/L，危急值，已复核",
          itemCode: "LAB.POTASSIUM",
          itemName: "血钾检验说明书",
          sourceVersionId: 21,
          versionNo: "v1.0",
          criticalRisk: true,
          summary: "已签发报告结合当前机构生效版本生成辅助解读。",
          abnormalHighlights: ["血钾升高", "危急值"],
          recommendations: ["请按本机构危急值闭环完成人工确认、回报和记录，系统不自动修改报告。"],
        },
      ],
      advisoryNote: "报告解读仅用于辅助阅读，不改写已签发报告，不替代医师判断。",
      traceId: "trace-report",
    });
    mockUseInterpretDiagnosticReport.mockReturnValue({
      mutateAsync: interpretDiagnosticReport,
      isPending: false,
    } as unknown as ReturnType<typeof useInterpretDiagnosticReport>);
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

    expect(screen.getByRole("heading", { name: "提醒与推荐" })).toBeInTheDocument();
    expect(screen.getByText("推荐链路总览")).toBeInTheDocument();
    expect(screen.getByText("按患者标识 / 追踪号 / 来源编号查推荐")).toBeInTheDocument();
    expect(screen.getByLabelText("患者或追踪号")).toBeInTheDocument();
    expect(mockUseClinicalRecommendationCards).toHaveBeenCalledWith({
      status: undefined,
      riskLevel: undefined,
      patientId: undefined,
      page: 1,
      size: 10,
    });
    expect(screen.getAllByText("patient-real-1").length).toBeGreaterThan(0);
    expect(screen.getAllByText("住院医嘱").length).toBeGreaterThan(0);
    expect(screen.getByText("已采纳")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("采纳率")).toBeInTheDocument();
    expect(screen.getByText("66.7%")).toBeInTheDocument();
  });

  it("uses clinical disposition wording for non-accepted and frequency-limited reminder statuses", () => {
    mockUseClinicalRecommendationCards.mockReturnValue({
      data: {
        items: [
          {
            cardId: "card-rejected-1",
            triggerId: "trigger-rejected-1",
            patientId: "patient-real-2",
            encounterId: "enc-real-2",
            patientPathwayId: "pathway-real-2",
            scenarioCode: "WARD_ORDER",
            triggerType: "order-sign",
            cardType: "MEDICATION",
            title: "已处理提醒",
            summary: "医师已记录不采纳原因",
            suggestedAction: "保留证据并归档",
            riskLevel: "MEDIUM",
            interruptLevel: "SOFT",
            status: "REJECTED",
            fatigueKey: "WARD_ORDER:ARCHIVED",
            requiresPhysicianConfirmation: false,
            aiGenerated: false,
            traceId: "trace-rejected",
          },
          {
            cardId: "card-limited-1",
            triggerId: "trigger-limited-1",
            patientId: "patient-real-3",
            encounterId: "enc-real-3",
            patientPathwayId: "pathway-real-3",
            scenarioCode: "WARD_ORDER",
            triggerType: "order-sign",
            cardType: "MEDICATION",
            title: "低价值重复提醒",
            summary: "已按科室频次策略减少展示",
            suggestedAction: "必要时进入详情复核",
            riskLevel: "LOW",
            interruptLevel: "NONE",
            status: "SUPPRESSED",
            fatigueKey: "WARD_ORDER:LOW_VALUE",
            requiresPhysicianConfirmation: false,
            aiGenerated: false,
            traceId: "trace-limited",
          },
        ],
        page: 1,
        size: 10,
        total: 2,
        hasNext: false,
      },
      isLoading: false,
      isError: false,
      refetch: refetchCards,
    } as unknown as ReturnType<typeof useClinicalRecommendationCards>);

    renderCdssFatigue();

    expect(screen.getByText("未采纳")).toBeInTheDocument();
    expect(screen.getByText("已限频")).toBeInTheDocument();
    expect(screen.queryByText(/已驳回|疲劳抑制/)).not.toBeInTheDocument();
  });

  it(
    "evaluates recommendations from a selected ACTIVE snapshot without manual JSON",
    async () => {
      const user = userEvent.setup();
      renderCdssFatigue();

      await user.click(screen.getByRole("button", { name: /登记触发评估/ }));
      expect(screen.queryByLabelText(/上下文 JSON/)).not.toBeInTheDocument();
      await user.type(screen.getByLabelText("患者 ID"), "patient-real-1");
      await user.click(screen.getByRole("button", { name: "选择 snapshot-rec-1" }));
      await user.click(screen.getByRole("button", { name: "执行推荐评估" }));

      await waitFor(() =>
        expect(evaluateRecommendations).toHaveBeenCalledWith({
          triggerCode: "CDSS-MANUAL-order-sign",
          triggerType: "order-sign",
          scenarioCode: "order-sign",
          contextSnapshotId: "snapshot-rec-1",
          patientId: "patient-real-1",
          encounterId: "enc-real-1",
        }),
      );
    },
    CDSS_INTERACTION_TIMEOUT_MS,
  );

  it(
    "generates report interpretation from a selected ACTIVE snapshot without trigger or version selectors",
    async () => {
      const user = userEvent.setup();
      renderCdssFatigue();

      await user.click(screen.getByRole("button", { name: /生成报告解读/ }));

      expect(screen.getByText("生成医技报告解读")).toBeInTheDocument();
      expect(screen.getByText(/不会改写已签发报告，也不会自动开立医嘱/)).toBeInTheDocument();
      expect(screen.queryByLabelText("触发时点")).not.toBeInTheDocument();

      await user.type(screen.getByLabelText("患者 ID"), "patient-real-1");
      await user.click(screen.getByRole("button", { name: "选择 snapshot-rec-1" }));
      const confirmButtons = screen.getAllByRole("button", { name: "生成报告解读" });
      await user.click(confirmButtons[confirmButtons.length - 1]);

      await waitFor(() =>
        expect(interpretDiagnosticReport).toHaveBeenCalledWith({
          contextSnapshotId: "snapshot-rec-1",
        }),
      );
      expect(evaluateRecommendations).not.toHaveBeenCalled();
    },
    CDSS_INTERACTION_TIMEOUT_MS,
  );

  it("shows persisted physician feedback identity and never submits operatorId from the browser", async () => {
    const user = userEvent.setup();
    renderCdssFatigue();

    await user.click(screen.getByRole("button", { name: /查看与人机反馈/ }));

    expect(await screen.findByText("这条推荐是怎么来的")).toBeInTheDocument();
    expect(screen.getAllByText("触发事件").length).toBeGreaterThan(0);
    expect(screen.getAllByText("命中规则").length).toBeGreaterThan(0);
    expect(screen.getAllByText("知识来源").length).toBeGreaterThan(0);
    expect(
      screen.getAllByText("该卡片暂未返回来源解释，暂不展示来源证据。").length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("路径上下文").length).toBeGreaterThan(0);
    expect(screen.getAllByText("待办 / 通知").length).toBeGreaterThan(0);
    expect(screen.getAllByText("医生反馈").length).toBeGreaterThan(0);
    expect(screen.getAllByText("药师复核").length).toBeGreaterThan(0);
    await user.click(screen.getByRole("tab", { name: /临床指南与来源证据/ }));
    expect(
      screen.getByText("该提醒卡暂无来源解释证据；请结合患者病情与院内制度复核。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/兜底伪造/)).not.toBeInTheDocument();
    expect(screen.getByText(/trace-rec/)).toBeInTheDocument();
    expect(await screen.findByText(/doctor-real-1/)).toBeInTheDocument();
    expect(screen.getAllByText("已确认风险，按指南处理。").length).toBeGreaterThan(0);
    expect(screen.getAllByText("不采纳建议").length).toBeGreaterThan(0);
    expect(screen.getByText("患者当前情况不适用，已记录替代处理方案。")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /医师反馈/ }));
    expect(
      screen.getByText(
        "医师反馈会进入临床决策证据链。采纳或不采纳都需记录真实理由；系统按登录态记录操作者身份，不由前端填写。",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "采纳建议" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "不采纳建议" })).toBeInTheDocument();
    expect(screen.queryByText(/ACCEPT|REJECT|抗拒|克拉霉素|驳回/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /确认采纳建议/ }));

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

  it("marks clinical redline reminders as non-suppressible while showing configured fatigue threshold", async () => {
    const user = userEvent.setup();
    mockUseClinicalRecommendationCards.mockReturnValue({
      data: {
        items: [
          {
            cardId: "card-redline-1",
            triggerId: "trigger-redline-1",
            patientId: "patient-redline-1",
            encounterId: "enc-redline-1",
            patientPathwayId: "pathway-redline-1",
            scenarioCode: "WARD_ORDER",
            triggerType: "order-sign",
            cardType: "MEDICATION",
            title: "华法林与 NSAID 联用红线",
            summary: "命中临床安全红线",
            suggestedAction: "立即复核联合用药风险",
            riskLevel: "CRITICAL",
            interruptLevel: "STRONG_INTERRUPTIVE",
            status: "PENDING",
            fatigueKey: "REDLINE:RDL-DDI-001",
            requiresPhysicianConfirmation: true,
            aiGenerated: false,
            traceId: "trace-redline",
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
    mockUseRecommendationCardDetail.mockReturnValue({
      data: {
        card: {
          cardId: "card-redline-1",
          tenantId: "tenant-A",
          triggerId: "trigger-redline-1",
          cardType: "MEDICATION",
          title: "华法林与 NSAID 联用红线",
          summary: "命中临床安全红线",
          suggestedAction: "立即复核联合用药风险",
          riskLevel: "CRITICAL",
          interruptLevel: "STRONG_INTERRUPTIVE",
          status: "PENDING",
          fatigueKey: "REDLINE:RDL-DDI-001",
        },
        trigger: {
          triggerId: "trigger-redline-1",
          patientId: "patient-redline-1",
          encounterId: "enc-redline-1",
          patientPathwayId: "pathway-redline-1",
          scenarioCode: "WARD_ORDER",
          triggerType: "order-sign",
        },
        sources: [],
        feedback: [],
        fatigueSignals: [],
        traceId: "trace-redline",
      },
      refetch: refetchDetail,
    } as unknown as ReturnType<typeof useRecommendationCardDetail>);
    mockUseRecommendationFatigueSignals.mockReturnValue({
      data: {
        items: [
          {
            signalId: "signal-redline-1",
            tenantId: "tenant-A",
            fatigueKey: "REDLINE:RDL-DDI-001",
            signalType: "BLOCK",
            triggerCount: 9,
            governanceThreshold: 3,
            summary: "红线命中频繁，但高危红线必须保留人工确认。",
            createdAt: "2026-06-04T00:00:00Z",
          },
        ],
        total: 1,
      },
      refetch: refetchFatigue,
    } as unknown as ReturnType<typeof useRecommendationFatigueSignals>);

    renderCdssFatigue();

    await user.click(screen.getByRole("button", { name: /查看与人机反馈/ }));
    await user.click(screen.getByRole("tab", { name: /提醒频次治理/ }));

    expect(
      await screen.findByText(
        "提醒频次治理用于减少低价值重复提醒；高危红线和必须医师确认的提醒不会被自动减少或隐藏。",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/狼来了|物理拦截|高阶|静音|抑制|疲劳/)).not.toBeInTheDocument();
    expect(await screen.findByText("红线必须保留")).toBeInTheDocument();
    expect(screen.getByText("medkernel.cdss.fatigue.policy")).toBeInTheDocument();
    expect(screen.getByText("必须保留确认")).toBeInTheDocument();
    expect(screen.queryByText(/MUTE|BLOCK|SUPPRESSED/)).not.toBeInTheDocument();
    expect(screen.getAllByText("REDLINE:RDL-DDI-001").length).toBeGreaterThan(0);
    expect(screen.getByText("9 / 3 次")).toBeInTheDocument();
    expect(screen.getAllByText(/科室级限频阈值/).length).toBeGreaterThan(0);
  });

  it("shows forbidden state when org data scope denies clinical reminder cards", () => {
    mockUseClinicalRecommendationCards.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          data: {
            code: "ENG-BASE-003",
            detail: "提醒治理数据范围权限不足",
            traceId: "trace-remind-scope",
          },
        },
      },
      refetch: refetchCards,
    } as unknown as ReturnType<typeof useClinicalRecommendationCards>);

    renderCdssFatigue();

    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
    expect(screen.getByText(/提醒治理数据范围权限不足/)).toBeInTheDocument();
    expect(screen.getByText(/trace-remind-scope/)).toBeInTheDocument();
  });
});
