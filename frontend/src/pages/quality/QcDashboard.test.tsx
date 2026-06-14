import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import QcDashboard from "./QcDashboard";

const mockUseQualityDashboard = vi.fn();
const mockUseQualityDashboardDrilldown = vi.fn();
const mockUseOrgUnits = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useQualityDashboard: (params: unknown) => mockUseQualityDashboard(params),
  useQualityDashboardDrilldown: (params: unknown) => mockUseQualityDashboardDrilldown(params),
  useOrgUnits: (params: unknown) => mockUseOrgUnits(params),
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
        <QcDashboard />
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

const dashboardData = {
  summary: {
    totalFindings: 12,
    openFindings: 5,
    closedFindings: 6,
    waivedFindings: 1,
    overdueRectificationTasks: 2,
    activeAlerts: 2,
  },
  heatmap: [
    {
      departmentId: "dept-card",
      totalFindings: 8,
      openFindings: 3,
      highRiskFindings: 2,
      hitRate: 0.67,
      maxSeverity: "P1",
      heatToken: "risk-high",
    },
  ],
  valueMetrics: {
    metrics: [
      {
        id: "RECTIFICATION_CLOSURE_RATE",
        metricCode: "RECTIFICATION_CLOSURE_RATE",
        displayName: "整改闭环率",
        formula: "已闭环整改 / 全部整改",
        formulaVersion: "v1",
        status: "AVAILABLE",
        numerator: 6,
        denominator: 10,
        value: 0.6,
        unit: "%",
        dataSources: [],
        explanation: "来自 rectification_task 真实闭环事实",
        calculatedAt: "2026-06-05T10:00:00Z",
      },
      {
        id: "INSURANCE_VIOLATION_REDUCTION",
        metricCode: "INSURANCE_VIOLATION_REDUCTION",
        displayName: "医保违规减少",
        formula: "本期违规减少量",
        formulaVersion: "v1",
        status: "NOT_AVAILABLE",
        numerator: 0,
        denominator: 0,
        value: null,
        unit: "%",
        dataSources: [],
        explanation: "当前作用域缺少医保结算事实",
        calculatedAt: "2026-06-05T10:00:00Z",
      },
    ],
  },
  activeAlerts: [
    {
      alertId: "alert-p1",
      alertType: "HIGH_RISK_FINDING",
      status: "OPEN",
      departmentId: "dept-card",
      sourceType: "quality_finding",
      sourceId: "finding-p1",
      severity: "P1",
      thresholdCode: "HIGH_RISK_FINDING",
      thresholdValue: 1,
      actualValue: 2,
      title: "P1 问题聚集",
      evidenceSummary: "2 项 P1 问题仍未闭环",
      createdAt: "2026-06-05T10:00:00Z",
      updatedAt: "2026-06-05T10:00:00Z",
      traceId: "trace-alert",
    },
  ],
  generatedAt: "2026-06-05T10:00:00Z",
};

describe("QcDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseOrgUnits.mockReturnValue({
      data: {
        items: [
          {
            id: "dept-card",
            level: "DEPARTMENT",
            code: "CARD",
            name: "心内科",
            status: "ACTIVE",
          },
        ],
      },
      isLoading: false,
      isError: false,
    });
  });

  it("renders real dashboard aggregation instead of the old placeholder metrics", () => {
    mockUseQualityDashboard.mockReturnValue({
      data: dashboardData,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseQualityDashboardDrilldown.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getByRole("heading", { name: "质量管理概览" })).toBeInTheDocument();
    expect(screen.getByText("真实质控问题总数")).toBeInTheDocument();
    expect(screen.getByText("12 项")).toBeInTheDocument();
    expect(screen.getByText("心内科")).toBeInTheDocument();
    expect(screen.getByText("整改闭环率")).toBeInTheDocument();
    expect(screen.getByText("60.0%")).toBeInTheDocument();
    expect(screen.getByText("医保违规减少")).toBeInTheDocument();
    expect(screen.getByText("暂不可用")).toBeInTheDocument();
    expect(screen.getByText("P1 问题聚集")).toBeInTheDocument();
    expect(screen.queryByText("质控驾驶舱汇总接口尚未接入")).not.toBeInTheDocument();
    expect(screen.queryByText(/485|92\.8|演示/)).not.toBeInTheDocument();
  });

  it("opens a real drilldown evidence drawer from the dashboard scope", async () => {
    mockUseQualityDashboard.mockReturnValue({
      data: dashboardData,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseQualityDashboardDrilldown.mockReturnValue({
      data: {
        type: "FINDING",
        items: [
          {
            sourceType: "quality_finding",
            sourceId: "finding-p1",
            departmentId: "dept-card",
            severity: "P1",
            status: "OPEN",
            title: "病例 A 质控缺陷",
            evidenceSummary: "真实评估结果 finding-p1",
            occurredAt: "2026-06-05T09:00:00Z",
            traceId: "trace-finding",
          },
        ],
        evidencePackage: {
          packageId: "SVC-QUALITY-01.FINDING.0.20",
          generatedAt: "2026-06-05T10:00:00Z",
          scopeDigest: "digest-real",
          itemCount: 1,
          items: [],
        },
        offset: 0,
        limit: 20,
        total: 25,
        hasNext: true,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "下钻问题证据" }));

    expect(mockUseQualityDashboardDrilldown).toHaveBeenCalledWith(
      expect.objectContaining({ type: "FINDING", page: 1, size: 20 }),
    );
    expect(screen.getByText("真实下钻证据")).toBeInTheDocument();
    expect(screen.getByText("病例 A 质控缺陷")).toBeInTheDocument();
    expect(screen.getByText("trace-finding")).toBeInTheDocument();

    await userEvent.click(screen.getByTitle("2"));
    expect(mockUseQualityDashboardDrilldown).toHaveBeenLastCalledWith(
      expect.objectContaining({ type: "FINDING", page: 2, size: 20 }),
    );
  });

  it("filters the dashboard with a real department selection", async () => {
    mockUseQualityDashboard.mockReturnValue({
      data: dashboardData,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseQualityDashboardDrilldown.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    await userEvent.click(screen.getByRole("combobox", { name: "科室范围" }));
    await userEvent.click(await screen.findByText("心内科 · CARD"));

    expect(mockUseQualityDashboard).toHaveBeenLastCalledWith(
      expect.objectContaining({ departmentId: "dept-card" }),
    );
  });
});
