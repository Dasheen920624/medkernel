import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import QcDashboard from "./QcDashboard";

const mockUseQualityDashboard = vi.fn();
const mockUseQualityDashboardDrilldown = vi.fn();
const mockUseOrgUnits = vi.fn();
const mockUseSecurityProfile = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useQualityDashboard: (params: unknown) => mockUseQualityDashboard(params),
  useQualityDashboardDrilldown: (params: unknown) => mockUseQualityDashboardDrilldown(params),
  useOrgUnits: (params: unknown) => mockUseOrgUnits(params),
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
    useEvidenceDetailsStore.setState({ enabled: false });
    mockUseSecurityProfile.mockReturnValue({
      data: {
        permissions: [{ code: "evaluation.read" }],
        roles: [{ code: "quality-user", displayName: "质控人员" }],
        menuKeys: ["qc-dashboard"],
      },
    });
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

    expect(screen.getByRole("heading", { name: "质量风险概览" })).toBeInTheDocument();
    expect(screen.getByText("质量指标、风险热力与整改闭环")).toBeInTheDocument();
    expect(screen.getByText("质量问题总数")).toBeInTheDocument();
    expect(screen.queryByText("真实指标、风险热力与闭环价值")).not.toBeInTheDocument();
    expect(screen.queryByText("真实质量问题总数")).not.toBeInTheDocument();
    expect(screen.queryByText("质控问题总数")).not.toBeInTheDocument();
    expect(screen.getByText("12 项")).toBeInTheDocument();
    expect(screen.getByText("心内科")).toBeInTheDocument();
    expect(screen.getByText("整改闭环率")).toBeInTheDocument();
    expect(screen.getByText("60.0%")).toBeInTheDocument();
    expect(screen.getByText("医保违规减少")).toBeInTheDocument();
    expect(screen.getByText("暂不可用")).toBeInTheDocument();
    expect(screen.getByText("P1 问题聚集")).toBeInTheDocument();
    expect(screen.getByText("生成时间：2026年06月05日 18:00")).toBeInTheDocument();
    expect(screen.getAllByText("计算时间：2026年06月05日 18:00").length).toBeGreaterThan(0);
    expect(screen.queryByText("生成时间：2026-06-05 10:00")).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.queryByText("RECTIFICATION_CLOSURE_RATE")).not.toBeInTheDocument();
    expect(screen.queryByText("risk-high")).not.toBeInTheDocument();
    expect(screen.queryByText("追踪号：trace-alert")).not.toBeInTheDocument();
    expect(screen.queryByText("质控驾驶舱汇总接口尚未接入")).not.toBeInTheDocument();
    expect(screen.queryByText(/485|92\.8|演示/)).not.toBeInTheDocument();
  });

  it("uses quality management wording when the current scope has no overview data", () => {
    mockUseQualityDashboard.mockReturnValue({
      data: {
        ...dashboardData,
        summary: {
          totalFindings: 0,
          openFindings: 0,
          closedFindings: 0,
          waivedFindings: 0,
          overdueRectificationTasks: 0,
          activeAlerts: 0,
        },
        heatmap: [],
        valueMetrics: { metrics: [] },
        activeAlerts: [],
      },
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

    expect(screen.getByText("当前筛选下暂无质量数据")).toBeInTheDocument();
    expect(screen.queryByText("当前筛选下暂无质控数据")).not.toBeInTheDocument();
    expect(screen.queryByText("当前筛选下暂无真实质控数据")).not.toBeInTheDocument();
  });

  it("uses the current quality overview name in error states", () => {
    mockUseQualityDashboard.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { response: { status: 500, data: {} } },
      refetch: vi.fn(),
    });
    mockUseQualityDashboardDrilldown.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getByText("质量风险概览读取失败")).toBeInTheDocument();
    expect(screen.getByText("请检查登录权限、组织范围或质量管理服务状态。")).toBeInTheDocument();
    expect(screen.queryByText(/质控驾驶舱/)).not.toBeInTheDocument();
    expect(screen.queryByText(/质控服务状态/)).not.toBeInTheDocument();
  });

  it("keeps dashboard actions concise and links accumulated alerts to the full worklist", () => {
    const activeAlerts = Array.from({ length: 8 }, (_, index) => ({
      ...dashboardData.activeAlerts[0],
      alertId: `alert-preview-${index + 1}`,
      sourceId: `finding-preview-${index + 1}`,
      title: `高优先待处置问题 ${index + 1}`,
      createdAt: `2026-06-05T10:${String(index).padStart(2, "0")}:00Z`,
      updatedAt: `2026-06-05T10:${String(index).padStart(2, "0")}:00Z`,
    }));
    mockUseQualityDashboard.mockReturnValue({
      data: {
        ...dashboardData,
        summary: {
          ...dashboardData.summary,
          activeAlerts: activeAlerts.length,
        },
        activeAlerts,
      },
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

    expect(screen.getByText("最高优先问题")).toBeInTheDocument();
    expect(screen.getByText("共 8 条待处置问题，当前展示 5 条")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "查看全部质量问题" })).toHaveAttribute(
      "href",
      "/qc/alerts",
    );
    expect(screen.getByText("高优先待处置问题 5")).toBeInTheDocument();
    expect(screen.queryByText("高优先待处置问题 6")).not.toBeInTheDocument();
  });

  it("renders backend metric unit codes as院长可读的业务单位", () => {
    mockUseQualityDashboard.mockReturnValue({
      data: {
        ...dashboardData,
        valueMetrics: {
          metrics: [
            {
              ...dashboardData.valueMetrics.metrics[0],
              id: "ADOPTION_RATE",
              metricCode: "ADOPTION_RATE",
              displayName: "采纳率",
              value: 1,
              unit: "RATE",
            },
            {
              ...dashboardData.valueMetrics.metrics[0],
              id: "MISSED_CASE_REVIEW",
              metricCode: "MISSED_CASE_REVIEW",
              displayName: "漏报回溯",
              value: 0,
              unit: "CASE_COUNT",
            },
          ],
        },
      },
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

    expect(screen.getByText("采纳率")).toBeInTheDocument();
    expect(screen.getByText("100.0%")).toBeInTheDocument();
    expect(screen.getByText("漏报回溯")).toBeInTheDocument();
    expect(screen.getByText("0 例")).toBeInTheDocument();
    expect(screen.queryByText("1 RATE")).not.toBeInTheDocument();
    expect(screen.queryByText("0 CASE_COUNT")).not.toBeInTheDocument();
  });

  it("默认用业务语言打开下钻证据并收起低频追溯编号", async () => {
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
        evidenceExport: {
          exportId: "SVC-QUALITY-01.FINDING.0.20",
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
    expect(screen.getByText("问题下钻证据")).toBeInTheDocument();
    expect(screen.queryByText("真实下钻证据")).not.toBeInTheDocument();
    expect(screen.getByText("当前页处理摘要")).toBeInTheDocument();
    expect(screen.getByText(/先处理 1 项高风险证据/)).toBeInTheDocument();
    expect(screen.getByText(/心内科 1 项/)).toBeInTheDocument();
    expect(screen.getByText(/未闭环 1 项/)).toBeInTheDocument();
    expect(screen.getByText("病例 A 质量缺陷")).toBeInTheDocument();
    expect(screen.queryByText("病例 A 质控缺陷")).not.toBeInTheDocument();
    expect(screen.getByText("证据包已生成")).toBeInTheDocument();
    expect(screen.getByText("来源已关联")).toBeInTheDocument();
    expect(screen.queryByText("追踪号：trace-finding")).not.toBeInTheDocument();
    expect(screen.queryByText("来源编号：finding-p1")).not.toBeInTheDocument();
    expect(screen.queryByText("证据导出编号：SVC-QUALITY-01.FINDING.0.20")).not.toBeInTheDocument();
    expect(screen.queryByText(/证据范围摘要：digest-real/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByTitle("2"));
    expect(mockUseQualityDashboardDrilldown).toHaveBeenLastCalledWith(
      expect.objectContaining({ type: "FINDING", page: 2, size: 20 }),
    );
  });

  it("默认隐藏下钻整改任务原始编号并用真实条数说明证据包", async () => {
    mockUseQualityDashboard.mockReturnValue({
      data: dashboardData,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseQualityDashboardDrilldown.mockReturnValue({
      data: {
        type: "RECTIFICATION",
        items: [
          {
            sourceType: "rectification_task",
            sourceId: "rct-ins-raw-001",
            departmentId: "dept-card",
            severity: "P1",
            status: "ASSIGNED",
            title: "整改任务 rct-ins-raw-001",
            evidenceSummary: "医保问题整改任务 rct-ins-raw-001 已派发。",
            occurredAt: "2026-06-05T09:00:00Z",
            traceId: "trace-rct-raw-001",
          },
          {
            sourceType: "rectification_task",
            sourceId: "rct-ins-raw-002",
            departmentId: "dept-card",
            severity: "P1",
            status: "ASSIGNED",
            title: "整改任务 rct-ins-raw-002",
            evidenceSummary: "医保问题整改任务 rct-ins-raw-002 已派发。",
            occurredAt: "2026-06-05T09:10:00Z",
            traceId: "trace-rct-raw-002",
          },
        ],
        evidenceExport: {
          exportId: "SVC-QUALITY-01.RECTIFICATION.0.20",
          generatedAt: "2026-06-05T10:00:00Z",
          scopeDigest: "digest-real",
          itemCount: undefined as unknown as number,
          items: [],
        },
        offset: 0,
        limit: 20,
        total: 2,
        hasNext: false,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "下钻问题证据" }));

    expect(screen.getAllByText("整改任务 · 已派发")).toHaveLength(2);
    expect(screen.getByText(/当前页 2 项，共 2 项/)).toBeInTheDocument();
    expect(screen.getAllByText("整改任务证据已关联，责任科室需按当前状态复核闭环。")).toHaveLength(
      2,
    );
    expect(screen.queryByText(/undefined 项证据/)).not.toBeInTheDocument();
    expect(screen.queryByText(/rct-ins-raw/)).not.toBeInTheDocument();
    expect(screen.queryByText(/trace-rct-raw/)).not.toBeInTheDocument();
  });

  it("质量风险来源使用医疗场景口径而不是旧质控预警入口名", async () => {
    mockUseQualityDashboard.mockReturnValue({
      data: {
        ...dashboardData,
        activeAlerts: [
          {
            ...dashboardData.activeAlerts[0],
            sourceType: "quality_alert",
            sourceId: "alert-risk-raw-001",
            title: "质量提醒 alert-risk-raw-001",
            evidenceSummary: "质量提醒 alert-risk-raw-001 已触发。",
          },
        ],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseQualityDashboardDrilldown.mockReturnValue({
      data: {
        type: "ALERT",
        items: [
          {
            sourceType: "quality_alert",
            sourceId: "alert-risk-raw-001",
            departmentId: "dept-card",
            severity: "P1",
            status: "OPEN",
            title: "质量提醒 alert-risk-raw-001",
            evidenceSummary: "质量提醒 alert-risk-raw-001 已触发。",
            occurredAt: "2026-06-05T09:00:00Z",
            traceId: "trace-alert-risk-raw-001",
          },
        ],
        evidenceExport: {
          exportId: "SVC-QUALITY-01.ALERT.0.20",
          generatedAt: "2026-06-05T10:00:00Z",
          scopeDigest: "digest-real",
          itemCount: 1,
          items: [],
        },
        offset: 0,
        limit: 20,
        total: 1,
        hasNext: false,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getAllByText("质量风险提醒").length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText("质控预警")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "下钻问题证据" }));

    expect(screen.getAllByText("质量风险提醒").length).toBeGreaterThan(1);
    expect(screen.getByText("质量风险提醒证据已关联，需按当前状态处理。")).toBeInTheDocument();
    expect(screen.queryByText(/质控预警/)).not.toBeInTheDocument();
    expect(screen.queryByText(/alert-risk-raw/)).not.toBeInTheDocument();
  });

  it("证据详情打开后展示质量指标、热力、风险提醒和下钻导出的完整追溯字段", async () => {
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
        evidenceExport: {
          exportId: "SVC-QUALITY-01.FINDING.0.20",
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

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));
    expect(screen.getByText("RECTIFICATION_CLOSURE_RATE")).toBeInTheDocument();
    expect(screen.getByText("risk-high")).toBeInTheDocument();
    expect(screen.getByText("追踪号：trace-alert")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "下钻问题证据" }));

    expect(screen.getByText("来源编号：finding-p1")).toBeInTheDocument();
    expect(screen.getByText("追踪号：trace-finding")).toBeInTheDocument();
    expect(screen.getByText("证据导出编号：SVC-QUALITY-01.FINDING.0.20")).toBeInTheDocument();
    expect(screen.getByText(/证据范围摘要：digest-real/)).toBeInTheDocument();
  });

  it("默认将当前组织范围显示为业务标签，证据详情才展开原始组织编号", async () => {
    mockUseSecurityProfile.mockReturnValue({
      data: {
        permissions: [{ code: "evaluation.read" }],
        roles: [{ code: "quality-user", displayName: "质控人员" }],
        menuKeys: ["qc-dashboard"],
        dataScope: {
          tenantId: "tenant-rehearsal",
          groupId: null,
          hospitalId: "hospital-rehearsal",
          campusId: null,
          siteId: null,
          departmentId: null,
          wardId: null,
          specialtyId: null,
        },
      },
    });
    mockUseOrgUnits.mockReturnValue({
      data: { items: [] },
      isLoading: false,
      isError: false,
    });
    mockUseQualityDashboard.mockReturnValue({
      data: {
        ...dashboardData,
        heatmap: [{ ...dashboardData.heatmap[0], departmentId: "hospital-rehearsal" }],
        activeAlerts: [{ ...dashboardData.activeAlerts[0], departmentId: "hospital-rehearsal" }],
      },
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

    expect(screen.getByText("当前机构")).toBeInTheDocument();
    expect(screen.getByText("科室：当前机构")).toBeInTheDocument();
    expect(screen.queryByText(/hospital-rehearsal/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("当前机构 · hospital-rehearsal")).toBeInTheDocument();
    expect(screen.getByText("科室：当前机构 · hospital-rehearsal")).toBeInTheDocument();
  });

  it("仅有服务机构范围时默认不显示租户口径", async () => {
    mockUseSecurityProfile.mockReturnValue({
      data: {
        permissions: [{ code: "evaluation.read" }],
        roles: [{ code: "quality-user", displayName: "质控人员" }],
        menuKeys: ["qc-dashboard"],
        dataScope: {
          tenantId: "tenant-rehearsal",
          groupId: null,
          hospitalId: null,
          campusId: null,
          siteId: null,
          departmentId: null,
          wardId: null,
          specialtyId: null,
        },
      },
    });
    mockUseOrgUnits.mockReturnValue({
      data: { items: [] },
      isLoading: false,
      isError: false,
    });
    mockUseQualityDashboard.mockReturnValue({
      data: {
        ...dashboardData,
        heatmap: [{ ...dashboardData.heatmap[0], departmentId: "tenant-rehearsal" }],
        activeAlerts: [{ ...dashboardData.activeAlerts[0], departmentId: "tenant-rehearsal" }],
      },
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

    expect(screen.getByText("当前服务机构")).toBeInTheDocument();
    expect(screen.getByText("科室：当前服务机构")).toBeInTheDocument();
    expect(screen.queryByText(/当前租户/)).not.toBeInTheDocument();
    expect(screen.queryByText(/tenant-rehearsal/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("当前服务机构 · tenant-rehearsal")).toBeInTheDocument();
    expect(screen.getByText("科室：当前服务机构 · tenant-rehearsal")).toBeInTheDocument();
  });

  it("默认收起医保质控待处置摘要中的结算与规则追溯编号", async () => {
    mockUseQualityDashboard.mockReturnValue({
      data: {
        ...dashboardData,
        activeAlerts: [
          {
            ...dashboardData.activeAlerts[0],
            title: "高风险质量问题待闭环",
            evidenceSummary:
              "结算事实 claim-raw-1；规则 INS.REAL.FRONTDESK.FEE@2026.07；金额 1280.50；阈值 1000；结算金额超过当前演练阈值，需要责任科室提交整改证据。",
          },
        ],
      },
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

    expect(
      screen.getByText("医保审核问题已形成整改证据，需责任科室按规则阈值提交整改。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/claim-raw-1/)).not.toBeInTheDocument();
    expect(screen.queryByText(/INS\.REAL\.FRONTDESK/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText(/结算事实 claim-raw-1/)).toBeInTheDocument();
    expect(screen.getByText(/INS\.REAL\.FRONTDESK\.FEE@2026\.07/)).toBeInTheDocument();
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
