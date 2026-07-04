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
const mockUseOrgUnits = vi.fn();
const mockUseOrgUsers = vi.fn();
const mockUseSecurityProfile = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useQualityAlerts: (params: unknown) => mockUseQualityAlerts(params),
  useDispatchRectification: () => mockUseDispatchRectification(),
  useAcknowledgeQualityAlert: () => mockUseAcknowledgeQualityAlert(),
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
      title: "高风险质控问题待闭环：术前记录缺失",
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
  });

  it("renders real quality alerts from SVC-QUALITY-01 instead of the old rectification workbench", () => {
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

    expect(mockUseQualityAlerts).toHaveBeenCalledWith(
      expect.objectContaining({ status: "OPEN", severity: "HIGH_RISK", page: 1, size: 20 }),
    );
    expect(screen.getByRole("combobox", { name: "预警级别" })).toBeInTheDocument();
    expect(screen.queryByLabelText("科室范围")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "质量问题与整改" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "质量问题" })).not.toBeInTheDocument();
    expect(screen.getByText("当前筛选问题总数")).toBeInTheDocument();
    expect(screen.getByText("1 条")).toBeInTheDocument();
    expect(screen.getByText("共 1 条质量问题，当前显示 1-1 条")).toBeInTheDocument();
    expect(screen.getByText("高风险质控问题待闭环：术前记录缺失")).toBeInTheDocument();
    expect(screen.getAllByText("心内科").length).toBeGreaterThan(0);
    expect(screen.queryByText("心内科 · CARDIO")).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getByText("高风险阈值已关联")).toBeInTheDocument();
    expect(screen.getByText("质控问题来源")).toBeInTheDocument();
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
        title: `高风险质控问题第 ${page}-${index + 1} 项`,
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

    await userEvent.click(screen.getByRole("combobox", { name: "预警级别" }));
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
    expect(screen.getByText("预警处置证据")).toBeInTheDocument();
    expect(screen.getByLabelText("整改截止时间")).not.toHaveAttribute("type", "datetime-local");
    expect(screen.getByLabelText("整改截止时间")).toHaveValue("2026年06月12日 17:00");
    expect(screen.queryByDisplayValue("2026-06-12T17:00")).not.toBeInTheDocument();
    expect(screen.getAllByText("高风险质控事实仍未闭环").length).toBeGreaterThan(0);
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
    expect(refetch).toHaveBeenCalled();
  });

  it("证据详情打开后展示预警阈值、来源和追踪原始字段", async () => {
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
    await userEvent.click(screen.getByRole("button", { name: "确认预警" }));

    await waitFor(() => {
      expect(acknowledge).toHaveBeenCalledWith("HIGH_RISK_FINDING:quality_finding:finding-p1");
    });
    expect(refetch).toHaveBeenCalled();
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
