import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import QcAlerts from "./QcAlerts";

const mockUseQualityAlerts = vi.fn();
const mockUseDispatchRectification = vi.fn();
const mockUseAcknowledgeQualityAlert = vi.fn();
const mockUseOrgUnits = vi.fn();
const mockUseOrgUsers = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useQualityAlerts: (params: unknown) => mockUseQualityAlerts(params),
  useDispatchRectification: () => mockUseDispatchRectification(),
  useAcknowledgeQualityAlert: () => mockUseAcknowledgeQualityAlert(),
  useOrgUnits: (params: unknown) => mockUseOrgUnits(params),
  useOrgUsers: (params: unknown) => mockUseOrgUsers(params),
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
    mockUseOrgUnits.mockReset();
    mockUseOrgUsers.mockReset();
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
    expect(screen.getByRole("heading", { name: "质量问题" })).toBeInTheDocument();
    expect(screen.getByText("真实质量问题总数")).toBeInTheDocument();
    expect(screen.getByText("1 条")).toBeInTheDocument();
    expect(screen.getByText("高风险质控问题待闭环：术前记录缺失")).toBeInTheDocument();
    expect(screen.getByText("心内科 · CARDIO")).toBeInTheDocument();
    expect(screen.getByText("trace-alert-p1")).toBeInTheDocument();
    expect(screen.queryByText("PDCA 质控整改与专家复核中心")).not.toBeInTheDocument();
    expect(screen.queryByText(/TRACE_NOT_FOUND|_TRACE/)).not.toBeInTheDocument();
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
    expect(screen.getByText("评估问题 finding-p1 仍未闭环")).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText("责任人"));
    await userEvent.click(screen.getByText("质控专员 · u-quality-1"));
    await userEvent.click(screen.getByRole("button", { name: "派发整改任务" }));

    await waitFor(() => {
      expect(dispatch).toHaveBeenCalledWith(
        expect.objectContaining({
          request: expect.objectContaining({
            findingId: "finding-p1",
            responsibleDepartmentId: "dept-cardio",
            assigneeUserId: "u-quality-1",
          }),
        }),
      );
    });
    expect(refetch).toHaveBeenCalled();
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

    expect(screen.getByRole("heading", { name: "质量问题" })).toBeInTheDocument();
    expect(screen.getByText("当前筛选下暂无真实质量问题")).toBeInTheDocument();
  });
});
