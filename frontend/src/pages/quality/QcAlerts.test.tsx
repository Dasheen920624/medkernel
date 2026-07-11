import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import QcAlerts from "./QcAlerts";

const mockUseQualityAlerts = vi.fn();
const mockUseDispatchRectification = vi.fn();
const mockUseAcknowledgeQualityAlert = vi.fn();
const mockUseQualityFindingDetail = vi.fn();
const mockUseSubmitRectification = vi.fn();
const mockUseReviewRectification = vi.fn();
const mockUseWaiveRectification = vi.fn();
const mockUseRectificationReport = vi.fn();
const mockUseOrgUnits = vi.fn();
const mockUseOrgUsers = vi.fn();
const mockUseSecurityProfile = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useQualityAlerts: (params: unknown) => mockUseQualityAlerts(params),
  useDispatchRectification: () => mockUseDispatchRectification(),
  useAcknowledgeQualityAlert: () => mockUseAcknowledgeQualityAlert(),
  useQualityFindingDetail: (findingId: string) => mockUseQualityFindingDetail(findingId),
  useSubmitRectification: (taskId: string) => mockUseSubmitRectification(taskId),
  useReviewRectification: (taskId: string) => mockUseReviewRectification(taskId),
  useWaiveRectification: (taskId: string) => mockUseWaiveRectification(taskId),
  useRectificationReport: (params: unknown) => mockUseRectificationReport(params),
  useOrgUnits: (params: unknown) => mockUseOrgUnits(params),
  useOrgUsers: (params: unknown) => mockUseOrgUsers(params),
  useSecurityProfile: () => mockUseSecurityProfile(),
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
        <QcAlerts />
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

const alertsData = {
  items: [
    {
      alertId: "HIGH_RISK_FINDING:quality_finding:finding-p1",
      alertType: "HIGH_RISK_FINDING",
      status: "OPEN",
      departmentId: "dept-cardio",
      sourceType: "quality_finding",
      sourceId: "finding-p1",
      severity: "P1",
      thresholdCode: "OPEN_P0_P1_FINDING",
      thresholdValue: 0,
      actualValue: 1,
      title: "高风险质量问题待闭环：术前记录缺失",
      evidenceSummary: "评估问题 finding-p1 仍未闭环",
      createdAt: "2026-06-05T09:00:00Z",
      updatedAt: "2026-06-05T10:00:00Z",
      traceId: "trace-alert-p1",
    },
  ],
  offset: 0,
  limit: 20,
  total: 1,
  hasNext: false,
};

describe("QcAlerts", () => {
  beforeEach(() => {
    useEvidenceDetailsStore.setState({ enabled: false });
    mockUseOrgUnits.mockReset();
    mockUseOrgUsers.mockReset();
    mockUseQualityFindingDetail.mockReset();
    mockUseSubmitRectification.mockReset();
    mockUseReviewRectification.mockReset();
    mockUseWaiveRectification.mockReset();
    mockUseRectificationReport.mockReset();
    mockUseSecurityProfile.mockReset();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        permissions: [{ code: "evaluation.read" }],
        roles: [{ code: "quality-user", displayName: "质控人员" }],
        menuKeys: ["qc-alerts"],
      },
    });
    mockUseOrgUnits.mockReturnValue({
      data: {
        items: [
          {
            id: "dept-cardio",
            level: "DEPARTMENT",
            code: "CARDIO",
            name: "心内科",
            status: "ACTIVE",
          },
        ],
      },
      isLoading: false,
    });
    mockUseOrgUsers.mockReturnValue({
      data: {
        items: [{ userId: "u-quality-1", displayName: "质控专员" }],
      },
      isLoading: false,
    });
    mockUseQualityFindingDetail.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
    });
    mockUseSubmitRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseReviewRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseWaiveRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseRectificationReport.mockReturnValue({
      data: {
        status: "AVAILABLE",
        totalTasks: 12,
        openTasks: 3,
        closedTasks: 9,
        waivedTasks: 1,
        overdueTasks: 2,
        highPriorityOpenTasks: 1,
        closureRate: 0.75,
        sourceTable: "rectification_task",
        traceId: "trace-report",
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
  });

  it("renders real quality alerts from SVC-QUALITY-01 instead of the old rectification workbench", () => {
    mockUseQualityAlerts.mockReturnValue({
      data: {
        ...alertsData,
        items: [
          {
            ...alertsData.items[0],
            alertId:
              "HIGH_RISK_FINDING:quality_finding:finding-p1-with-extra-long-risk-source-identifier-for-real-frontdesk-idempotency-limit",
          },
        ],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    renderPage();

    expect(mockUseQualityAlerts).toHaveBeenCalledWith(
      expect.objectContaining({ status: "OPEN", severity: "HIGH_RISK", page: 1, size: 20 }),
    );
    expect(screen.getByRole("combobox", { name: "处置状态" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "发现时间" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "风险级别" })).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "预警状态" })).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "预警时间" })).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "预警级别" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("科室范围")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "质量问题与整改" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "质量问题" })).not.toBeInTheDocument();
    expect(screen.getByText("当前筛选问题总数")).toBeInTheDocument();
    expect(screen.getByText("1 条")).toBeInTheDocument();
    expect(screen.getByText("整改闭环报告")).toBeInTheDocument();
    expect(screen.getByText("整改任务总数")).toBeInTheDocument();
    expect(screen.getByText("12 个")).toBeInTheDocument();
    expect(screen.getByText("闭环率")).toBeInTheDocument();
    expect(screen.getByText("75.0%")).toBeInTheDocument();
    expect(screen.getByText("统计来源")).toBeInTheDocument();
    expect(screen.getByText("整改任务事实已关联")).toBeInTheDocument();
    expect(screen.queryByText("rectification_task")).not.toBeInTheDocument();
    expect(screen.getByText("共 1 条质量问题，当前显示 1-1 条")).toBeInTheDocument();
    expect(screen.getByText("高风险质量问题待闭环：术前记录缺失")).toBeInTheDocument();
    expect(screen.queryByText("高风险质控问题待闭环：术前记录缺失")).not.toBeInTheDocument();
    expect(screen.getAllByText("心内科").length).toBeGreaterThan(0);
    expect(screen.queryByText("心内科 · CARDIO")).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getByText("高风险阈值已关联")).toBeInTheDocument();
    expect(screen.getByText("质量问题来源")).toBeInTheDocument();
    expect(screen.queryByText("质控问题来源")).not.toBeInTheDocument();
    expect(screen.getByText("证据已记录")).toBeInTheDocument();
    expect(screen.queryByText("trace-alert-p1")).not.toBeInTheDocument();
    expect(screen.queryByText("OPEN_P0_P1_FINDING")).not.toBeInTheDocument();
    expect(screen.queryByText("finding-p1")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "查看评价结果来源" })).toHaveAttribute(
      "href",
      "/qc/eval/results",
    );
    expect(screen.queryByText("PDCA 质控整改与专家复核中心")).not.toBeInTheDocument();
    expect(screen.queryByText(/TRACE_NOT_FOUND|_TRACE/)).not.toBeInTheDocument();
  });

  it("keeps accumulated quality alerts reachable through server pagination", async () => {
    mockUseQualityAlerts.mockImplementation((params: { page?: number } = {}) => {
      const page = params.page ?? 1;
      const pageItems = Array.from({ length: 20 }, (_, index) => ({
        ...alertsData.items[0],
        alertId: `HIGH_RISK_FINDING:quality_finding:finding-page-${page}-${index + 1}`,
        sourceId: `finding-page-${page}-${index + 1}`,
        title: `高风险质量问题第 ${page}-${index + 1} 项`,
      }));
      return {
        data: {
          ...alertsData,
          items: pageItems,
          offset: (page - 1) * 20,
          limit: 20,
          total: 45,
          hasNext: page < 3,
        },
        isLoading: false,
        isError: false,
        error: undefined,
        refetch: vi.fn(),
      };
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    renderPage();

    expect(mockUseQualityAlerts).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: "OPEN", severity: "HIGH_RISK", page: 1, size: 20 }),
    );
    expect(screen.getByText("当前筛选问题总数")).toBeInTheDocument();
    expect(screen.getByText("共 45 条质量问题，当前显示 1-20 条")).toBeInTheDocument();

    await userEvent.click(screen.getByTitle("2"));

    await waitFor(() => {
      expect(mockUseQualityAlerts).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: "OPEN", severity: "HIGH_RISK", page: 2, size: 20 }),
      );
    });
    expect(screen.getByText("共 45 条质量问题，当前显示 21-40 条")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("combobox", { name: "风险级别" }));
    await userEvent.click(screen.getByText("全部级别"));

    await waitFor(() => {
      expect(mockUseQualityAlerts).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: "OPEN", severity: "ALL", page: 1, size: 20 }),
      );
    });
  });

  it("dispatches a real rectification task from a quality finding alert", async () => {
    const dispatch = vi.fn().mockResolvedValue({
      taskId: "rct-finding-p1",
      findingStatus: "ASSIGNED",
      taskStatus: "ASSIGNED",
      traceId: "trace-dispatch",
    });
    const refetch = vi.fn();
    mockUseQualityAlerts.mockReturnValue({
      data: alertsData,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch,
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: dispatch,
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));
    expect(screen.getByText("质量风险处置证据")).toBeInTheDocument();
    expect(screen.getByLabelText("整改截止时间")).not.toHaveAttribute("type", "datetime-local");
    expect(screen.getByLabelText("整改截止时间")).toHaveValue("2026年06月12日 17:00");
    expect(screen.queryByDisplayValue("2026-06-12T17:00")).not.toBeInTheDocument();
    expect(screen.getAllByText("高风险质量问题仍未闭环").length).toBeGreaterThan(0);
    expect(screen.getByText("来源事实已关联")).toBeInTheDocument();
    expect(screen.getAllByText("证据已记录").length).toBeGreaterThan(0);
    expect(screen.queryByText("finding-p1")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-alert-p1")).not.toBeInTheDocument();

    await userEvent.click(screen.getByLabelText("责任人"));
    expect(screen.queryByText("质控专员 · u-quality-1")).not.toBeInTheDocument();
    await userEvent.click(screen.getByText("质控专员"));
    await userEvent.click(screen.getByRole("button", { name: "派发整改任务" }));

    await waitFor(() => {
      expect(dispatch).toHaveBeenCalledWith(
        expect.objectContaining({
          request: expect.objectContaining({
            findingId: "finding-p1",
            responsibleDepartmentId: "dept-cardio",
            assigneeUserId: "u-quality-1",
            dueAt: "2026-06-12T09:00:00.000Z",
          }),
        }),
      );
    });
    const dispatchKey = dispatch.mock.calls[0]?.[0]?.idempotencyKey;
    expect(dispatchKey.length).toBeLessThanOrEqual(128);
    expect(refetch).toHaveBeenCalled();
  });

  it("does not emit an unmounted form warning when opening quality alert evidence", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const consoleWarn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    mockUseQualityAlerts.mockReturnValue({
      data: alertsData,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    try {
      renderPage();

      await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));
      expect(await screen.findByText("质量风险处置证据")).toBeInTheDocument();

      const warningText = [...consoleError.mock.calls, ...consoleWarn.mock.calls].flat().join("\n");
      expect(warningText).not.toContain("Instance created by `useForm` is not connected");
    } finally {
      consoleError.mockRestore();
      consoleWarn.mockRestore();
    }
  });

  it("exposes stable source identifiers so rehearsals can avoid historical alerts with the same title", () => {
    mockUseQualityAlerts.mockReturnValue({
      data: {
        ...alertsData,
        total: 2,
        items: [
          {
            ...alertsData.items[0],
            alertId: "HIGH_RISK_FINDING:quality_finding:finding-history",
            sourceId: "finding-history",
            title: "医保审核问题待整改",
          },
          {
            ...alertsData.items[0],
            alertId: "HIGH_RISK_FINDING:quality_finding:finding-current",
            sourceId: "finding-current",
            title: "医保审核问题待整改",
          },
        ],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    const { container } = renderPage();

    const rows = container.querySelectorAll('[data-source-id="finding-current"]');
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveAttribute(
      "data-alert-id",
      "HIGH_RISK_FINDING:quality_finding:finding-current",
    );
    expect(rows[0]).toHaveTextContent("医保审核问题待整改");
  });

  it("submits rectification evidence for a dispatched quality finding through its real task", async () => {
    const longRectificationSummary =
      "已补录术前风险评估记录并完成科室复核，整改说明包含责任科室复盘、病历补录、质控复核和上线演练证据追踪。";
    const longEvidenceRef =
      "EMR-20260705-risk-assessment-with-extra-long-real-frontdesk-evidence-reference";
    const submit = vi.fn().mockResolvedValue({
      taskId: "rct-finding-p1",
      findingStatus: "REMEDIATING",
      taskStatus: "SUBMITTED",
      traceId: "trace-submit",
    });
    const refetch = vi.fn();
    mockUseQualityAlerts.mockReturnValue({
      data: {
        ...alertsData,
        items: [
          {
            ...alertsData.items[0],
            status: "ACKNOWLEDGED",
          },
        ],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch,
    });
    mockUseQualityFindingDetail.mockReturnValue({
      data: {
        finding: {
          findingId: "finding-p1",
          status: "ASSIGNED",
          severity: "P1",
          title: "术前记录缺失",
        },
        rectificationTask: {
          taskId: "rct-finding-p1",
          findingId: "finding-p1",
          responsibleDepartmentId: "dept-cardio",
          status: "ASSIGNED",
          dueAt: "2026-06-12T09:00:00Z",
        },
        reviews: [],
      },
      isLoading: false,
      isError: false,
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseSubmitRectification.mockReturnValue({
      mutateAsync: submit,
      isPending: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));

    expect(mockUseQualityFindingDetail).toHaveBeenCalledWith("finding-p1");
    expect(await screen.findByText("整改任务 rct-finding-p1 已派发")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("整改说明"), longRectificationSummary);
    await userEvent.type(screen.getByLabelText("整改证据"), longEvidenceRef);
    await userEvent.click(screen.getByRole("button", { name: "提交整改证据" }));

    await waitFor(() => {
      expect(submit).toHaveBeenCalledWith({
        request: {
          rectificationSummary: longRectificationSummary,
          evidenceRef: longEvidenceRef,
        },
        idempotencyKey: expect.stringContaining("qc-alert-submit-rct-finding-p1"),
      });
    });
    const submitKey = submit.mock.calls[0]?.[0]?.idempotencyKey;
    expect(submitKey.length).toBeLessThanOrEqual(128);
    expect(refetch).toHaveBeenCalled();
  });

  it("reviews a submitted rectification task and closes the quality finding through the task endpoint", async () => {
    const review = vi.fn().mockResolvedValue({
      reviewId: "rr-rct-overdue",
      findingStatus: "CLOSED",
      taskStatus: "CLOSED",
      traceId: "trace-review",
    });
    const refetch = vi.fn();
    mockUseQualityAlerts.mockReturnValue({
      data: {
        ...alertsData,
        items: [
          {
            ...alertsData.items[0],
            alertId: "OVERDUE_RECTIFICATION:rectification_task:rct-overdue",
            alertType: "OVERDUE_RECTIFICATION",
            sourceType: "rectification_task",
            sourceId: "rct-overdue",
            severity: "SUBMITTED",
            thresholdCode: "RECTIFICATION_DUE_AT",
            title: "整改任务逾期未闭环：rct-overdue",
            evidenceSummary: "责任科室已提交补录证据",
          },
        ],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch,
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseReviewRectification.mockReturnValue({
      mutateAsync: review,
      isPending: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));
    await userEvent.type(screen.getByLabelText("复核意见"), "整改证据充分，允许关闭本轮质量问题");
    await userEvent.type(screen.getByLabelText("复核证据"), "QC-REVIEW-20260705");
    await userEvent.click(screen.getByRole("button", { name: "复核通过并关闭" }));

    await waitFor(() => {
      expect(review).toHaveBeenCalledWith({
        request: {
          decision: "APPROVED",
          comment: "整改证据充分，允许关闭本轮质量问题",
          evidenceRef: "QC-REVIEW-20260705",
        },
        idempotencyKey: expect.stringContaining("qc-alert-review-rct-overdue-APPROVED"),
      });
    });
    expect(refetch).toHaveBeenCalled();
  });

  it("returns a submitted rectification task without racing the approval decision", async () => {
    const review = vi.fn().mockResolvedValue({
      reviewId: "rr-rct-return-review",
      findingStatus: "REMEDIATING",
      taskStatus: "RETURNED",
      traceId: "trace-return-review",
    });
    mockUseQualityAlerts.mockReturnValue({
      data: {
        ...alertsData,
        items: [
          {
            ...alertsData.items[0],
            alertId: "OVERDUE_RECTIFICATION:rectification_task:rct-return-review",
            alertType: "OVERDUE_RECTIFICATION",
            sourceType: "rectification_task",
            sourceId: "rct-return-review",
            severity: "SUBMITTED",
            thresholdCode: "RECTIFICATION_DUE_AT",
            title: "整改任务待复核：rct-return-review",
            evidenceSummary: "责任科室证据仍需补充",
          },
        ],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseReviewRectification.mockReturnValue({
      mutateAsync: review,
      isPending: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));
    await userEvent.type(screen.getByLabelText("复核意见"), "证据缺少科主任确认，需要退回补充");
    await userEvent.click(screen.getByRole("button", { name: "退回继续整改" }));

    await waitFor(() => {
      expect(review).toHaveBeenCalledWith({
        request: {
          decision: "RETURNED",
          comment: "证据缺少科主任确认，需要退回补充",
          evidenceRef: undefined,
        },
        idempotencyKey: expect.stringContaining("qc-alert-review-rct-return-review-RETURNED"),
      });
    });
  });

  it("resubmits returned rectification task evidence through the task endpoint", async () => {
    const submit = vi.fn().mockResolvedValue({
      taskId: "rct-returned",
      findingStatus: "REMEDIATING",
      taskStatus: "SUBMITTED",
      traceId: "trace-resubmit",
    });
    mockUseQualityAlerts.mockReturnValue({
      data: {
        ...alertsData,
        items: [
          {
            ...alertsData.items[0],
            alertId: "OVERDUE_RECTIFICATION:rectification_task:rct-returned",
            alertType: "OVERDUE_RECTIFICATION",
            sourceType: "rectification_task",
            sourceId: "rct-returned",
            severity: "RETURNED",
            title: "整改任务已退回：rct-returned",
          },
        ],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseSubmitRectification.mockReturnValue({
      mutateAsync: submit,
      isPending: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));
    expect(await screen.findByText("整改任务 rct-returned 已退回")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("整改说明"), "已按复核意见重新补充病历整改材料");
    await userEvent.type(screen.getByLabelText("整改证据"), "EMR-RETURNED-FIX-20260705");
    await userEvent.click(screen.getByRole("button", { name: "提交整改证据" }));

    await waitFor(() => {
      expect(submit).toHaveBeenCalledWith({
        request: {
          rectificationSummary: "已按复核意见重新补充病历整改材料",
          evidenceRef: "EMR-RETURNED-FIX-20260705",
        },
        idempotencyKey: expect.stringContaining("qc-alert-submit-rct-returned"),
      });
    });
  });

  it("blocks waiver for submitted P0 quality finding tasks on the front desk", async () => {
    mockUseQualityAlerts.mockReturnValue({
      data: alertsData,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseQualityFindingDetail.mockReturnValue({
      data: {
        finding: {
          findingId: "finding-p1",
          status: "REMEDIATING",
          severity: "P0",
          title: "危急值未处理",
        },
        rectificationTask: {
          taskId: "rct-p0",
          findingId: "finding-p1",
          responsibleDepartmentId: "dept-cardio",
          status: "SUBMITTED",
          dueAt: "2026-06-12T09:00:00Z",
        },
        reviews: [],
      },
      isLoading: false,
      isError: false,
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));

    expect(await screen.findByText("整改任务 rct-p0 待复核")).toBeInTheDocument();
    expect(screen.getByText("安全红线问题不得在本页豁免")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "提交整改豁免" })).not.toBeInTheDocument();
  });

  it("submits a non-P0 rectification waiver through the task endpoint", async () => {
    const waive = vi.fn().mockResolvedValue({
      reviewId: "rr-waive-rct-submitted",
      findingStatus: "WAIVED",
      taskStatus: "WAIVED",
      traceId: "trace-waive",
    });
    mockUseQualityAlerts.mockReturnValue({
      data: {
        ...alertsData,
        items: [
          {
            ...alertsData.items[0],
            alertId: "OVERDUE_RECTIFICATION:rectification_task:rct-submitted",
            alertType: "OVERDUE_RECTIFICATION",
            sourceType: "rectification_task",
            sourceId: "rct-submitted",
            severity: "SUBMITTED",
            title: "整改任务待复核：rct-submitted",
          },
        ],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseWaiveRectification.mockReturnValue({
      mutateAsync: waive,
      isPending: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));
    await userEvent.type(screen.getByLabelText("豁免理由"), "专家组确认该问题不适用当前病例");
    await userEvent.type(screen.getByLabelText("决定依据"), "MDT-2026-07-05");
    await userEvent.type(screen.getByLabelText("豁免证据"), "WAIVE-PROOF-20260705");
    await userEvent.click(screen.getByRole("button", { name: "提交整改豁免" }));

    await waitFor(() => {
      expect(waive).toHaveBeenCalledWith({
        request: {
          reason: "专家组确认该问题不适用当前病例",
          decisionRef: "MDT-2026-07-05",
          evidenceRef: "WAIVE-PROOF-20260705",
        },
        idempotencyKey: expect.stringContaining("qc-alert-waive-rct-submitted"),
      });
    });
  });

  it("证据详情打开后展示风险阈值、来源和追踪原始字段", async () => {
    mockUseQualityAlerts.mockReturnValue({
      data: alertsData,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("OPEN_P0_P1_FINDING")).toBeInTheDocument();
    expect(screen.getByText("trace-alert-p1")).toBeInTheDocument();
    expect(screen.getByText(/评估问题 finding-p1 仍未闭环/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));

    expect(screen.getByText("finding-p1")).toBeInTheDocument();
    expect(screen.getAllByText("trace-alert-p1").length).toBeGreaterThan(0);
  });

  it("acknowledges an open alert through the real quality alert endpoint", async () => {
    const acknowledge = vi.fn().mockResolvedValue({
      ...alertsData.items[0],
      status: "ACKNOWLEDGED",
    });
    const refetch = vi.fn();
    mockUseQualityAlerts.mockReturnValue({
      data: alertsData,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch,
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: acknowledge,
      isPending: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "查看处置证据" }));
    await userEvent.click(screen.getByRole("button", { name: "确认风险提醒" }));

    await waitFor(() => {
      expect(acknowledge).toHaveBeenCalledWith("HIGH_RISK_FINDING:quality_finding:finding-p1");
    });
    expect(refetch).toHaveBeenCalled();
  });

  it("错误态使用质量问题与整改服务口径而不是旧质量预警服务", () => {
    mockUseQualityAlerts.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          data: {
            detail: "质量问题读取失败",
            traceId: "trace-qc-alert-error",
          },
        },
      },
      refetch: vi.fn(),
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    renderPage();

    expect(screen.getByText("质量问题读取失败")).toBeInTheDocument();
    expect(
      screen.getByText(
        "请稍后重试；若持续失败，请联系信息科核查质量问题与整改服务。失败已留痕，可在审计证据中追溯。",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/质量预警服务/)).not.toBeInTheDocument();
  });

  it("uses an honest empty state when the real alerts API has no rows", () => {
    mockUseQualityAlerts.mockReturnValue({
      data: { items: [], offset: 0, limit: 20, total: 0, hasNext: false },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseDispatchRectification.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseAcknowledgeQualityAlert.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });

    renderPage();

    expect(screen.getByRole("heading", { name: "质量问题与整改" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "质量问题" })).not.toBeInTheDocument();
    expect(screen.getByText("当前筛选下暂无待整改质量问题")).toBeInTheDocument();
    expect(screen.queryByText("当前筛选下暂无真实质量问题")).not.toBeInTheDocument();
  });
});
