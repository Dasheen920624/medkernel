import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import InsuranceAudit from "./InsuranceAudit";

const mockUseInsuranceIssues = vi.fn();
const mockUseContextSnapshotDetail = vi.fn();
const mockUseContextSnapshots = vi.fn();
const mockUseRunQualityCaseReview = vi.fn();
const mockUseRunDrgGrouping = vi.fn();
const mockUseRunInsuranceAudit = vi.fn();
const mockUseEvaluationIndicators = vi.fn();
const mockUseOrgUnits = vi.fn();
const mockUseSecurityProfile = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useContextSnapshotDetail: (snapshotId: string, options: unknown) =>
    mockUseContextSnapshotDetail(snapshotId, options),
  useContextSnapshots: (params: unknown, options: unknown) =>
    mockUseContextSnapshots(params, options),
  useInsuranceIssues: (params: unknown) => mockUseInsuranceIssues(params),
  useEvaluationIndicators: (params: unknown, options: unknown) =>
    mockUseEvaluationIndicators(params, options),
  useOrgUnits: (params: unknown) => mockUseOrgUnits(params),
  useSecurityProfile: () => mockUseSecurityProfile(),
  useRunQualityCaseReview: () => mockUseRunQualityCaseReview(),
  useRunDrgGrouping: () => mockUseRunDrgGrouping(),
  useRunInsuranceAudit: () => mockUseRunInsuranceAudit(),
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
        <InsuranceAudit />
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

const insuranceIssuesPage = {
  items: [
    {
      issueId: "ins-fee-1",
      claimId: "claim-real-1",
      issueType: "FEE",
      severity: "P1",
      status: "OPEN",
      ruleCode: "RULE-FEE-A",
      ruleVersion: "2026-A",
      claimAmount: 1200,
      thresholdAmount: 1000,
      evidenceSummary: "结算事实 claim-real-1；规则 RULE-FEE-A@2026-A；金额 1200.00；阈值 1000.00",
      departmentId: "医保办",
      evaluationRunId: "run-ins-1",
      traceId: "trace-ins-1",
      createdAt: "2026-06-05T00:00:00Z",
    },
  ],
  page: 1,
  size: 20,
  total: 1,
  hasNext: false,
  totalEstimated: false,
};

describe("InsuranceAudit", () => {
  beforeEach(() => {
    useEvidenceDetailsStore.setState({ enabled: false });
    vi.clearAllMocks();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        permissions: [{ code: "evaluation.read" }],
        roles: [{ code: "insurance-user", displayName: "医保审核人员" }],
        menuKeys: ["insurance-audit"],
      },
    });
    mockUseOrgUnits.mockReturnValue({
      data: {
        items: [
          {
            id: "dept-insurance",
            level: "DEPARTMENT",
            code: "DEPT-INS",
            name: "医保管理科",
            status: "ACTIVE",
          },
        ],
      },
      isLoading: false,
    });
    mockUseEvaluationIndicators.mockReturnValue({
      data: {
        items: [
          {
            indicatorId: "indicator-insurance",
            indicatorCode: "INS.FEE",
            versionNo: 2,
            name: "医保违规费用率",
            status: "ACTIVE",
          },
        ],
      },
      isLoading: false,
    });
    mockUseContextSnapshots.mockReturnValue({
      data: {
        items: [
          {
            snapshotId: "snapshot-ins",
            patientId: "patient-ins",
            encounterId: "encounter-ins",
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
    });
    mockUseContextSnapshotDetail.mockImplementation((snapshotId: string) => ({
      data:
        snapshotId === "snapshot-ins"
          ? {
              snapshotId,
              status: "ACTIVE",
              resources: {},
              runtimeReleaseId: "runtime-release-ins",
              qualityStatus: "VALID",
              missingFields: [],
              mappingStatus: {},
              traceId: "trace-snapshot-ins",
            }
          : undefined,
      isLoading: false,
      isError: false,
    }));
  });

  it("renders real insurance issues from SVC-QUALITY-02 instead of the disconnected placeholder", () => {
    mockUseInsuranceIssues.mockReturnValue({
      data: insuranceIssuesPage,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseRunQualityCaseReview.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    mockUseRunDrgGrouping.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    mockUseRunInsuranceAudit.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });

    renderPage();

    expect(mockUseInsuranceIssues).toHaveBeenCalledWith(
      expect.objectContaining({ status: "OPEN", severity: "P1", page: 1, size: 20 }),
    );
    expect(screen.getByRole("heading", { name: "医保智能审核" })).toBeInTheDocument();
    expect(screen.getByText("真实医保问题总数")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getByText("医保结算已关联")).toBeInTheDocument();
    expect(screen.getByText("规则依据已关联")).toBeInTheDocument();
    expect(screen.getByText(/费用超阈值证据已记录/)).toBeInTheDocument();
    expect(screen.getByText("证据已记录")).toBeInTheDocument();
    expect(screen.queryByText("claim-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("RULE-FEE-A@2026-A")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-ins-1")).not.toBeInTheDocument();
    expect(screen.queryByText("医保审核接口尚未接入")).not.toBeInTheDocument();
    expect(screen.queryByText(/本地违规病例样例|申诉闭环/)).not.toBeInTheDocument();
  });

  it("证据详情打开后展示医保结算、规则和问题追溯字段", async () => {
    mockUseInsuranceIssues.mockReturnValue({
      data: insuranceIssuesPage,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseRunQualityCaseReview.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    mockUseRunDrgGrouping.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    mockUseRunInsuranceAudit.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });

    renderPage();

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("claim-real-1")).toBeInTheDocument();
    expect(screen.getByText("RULE-FEE-A@2026-A")).toBeInTheDocument();
    expect(screen.getByText(/结算事实 claim-real-1/)).toBeInTheDocument();
    expect(screen.getByText("trace-ins-1")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "查看证据" }));

    expect(screen.getByText("ins-fee-1")).toBeInTheDocument();
    expect(screen.getByText("run-ins-1")).toBeInTheDocument();
    expect(screen.getAllByText("trace-ins-1").length).toBeGreaterThan(0);
  });

  it("loads audit indicator selector through small server-side search pages", async () => {
    mockUseInsuranceIssues.mockReturnValue({
      data: insuranceIssuesPage,
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseRunQualityCaseReview.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    mockUseRunDrgGrouping.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    mockUseRunInsuranceAudit.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });

    renderPage();

    expect(mockUseOrgUnits).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      sort: "name,asc",
      level: "DEPARTMENT",
      status: "ACTIVE",
    });
    expect(mockUseEvaluationIndicators).toHaveBeenCalledWith(
      { status: "ACTIVE", page: 1, size: 20, sort: "name,asc" },
      { enabled: true },
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole("combobox", { name: "质控指标" }));
    await user.type(screen.getByRole("combobox", { name: "质控指标" }), "INS.FEE.2026");

    await waitFor(() => {
      expect(mockUseEvaluationIndicators).toHaveBeenCalledWith(
        {
          status: "ACTIVE",
          indicatorCode: "INS.FEE.2026",
          page: 1,
          size: 20,
          sort: "name,asc",
        },
        { enabled: true },
      );
    });
  });

  it("runs case review, DRG grouping and insurance audit through the real B0 endpoints", async () => {
    const refetch = vi.fn();
    const caseReview = vi.fn().mockResolvedValue({
      reviewId: "case-review-1",
      reviewStatus: "NON_COMPLIANT",
      evaluationRunId: "run-case-1",
      resultCount: 2,
      findingCount: 1,
      taskCount: 1,
      modelStatus: "MODEL_DISABLED",
      modelDowngradeReason: "MODEL_DISABLED_DETERMINISTIC_RULES",
      traceId: "trace-case",
    });
    const drgGrouping = vi.fn().mockResolvedValue({
      groupingId: "drg-1",
      groupingStatus: "MISMATCHED",
      expectedGroupCode: "DRG-A",
      actualGroupCode: "DRG-B",
      grouperVersion: "GROUPER-2026",
      explanation: "入组版本 GROUPER-2026，期望 DRG-A，实际 DRG-B；病案首页进入复核",
      traceId: "trace-drg",
    });
    const insuranceAudit = vi.fn().mockResolvedValue({
      auditId: "audit-1",
      auditStatus: "ISSUE_FOUND",
      issues: insuranceIssuesPage.items,
      evaluationRunId: "run-ins-1",
      findingCount: 1,
      taskCount: 1,
      traceId: "trace-audit",
    });
    mockUseInsuranceIssues.mockReturnValue({
      data: { ...insuranceIssuesPage, items: [], total: 0 },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch,
    });
    mockUseRunQualityCaseReview.mockReturnValue({ mutateAsync: caseReview, isPending: false });
    mockUseRunDrgGrouping.mockReturnValue({ mutateAsync: drgGrouping, isPending: false });
    mockUseRunInsuranceAudit.mockReturnValue({ mutateAsync: insuranceAudit, isPending: false });

    renderPage();

    expect(screen.queryByLabelText("病案快照 ID")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("可按住院号、门诊号或就诊信息检索")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("可按住院号、门诊号或就诊标识检索")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("患者信息"), { target: { value: "patient-ins" } });
    await userEvent.click(await screen.findByRole("button", { name: "选择第 1 个病案快照" }));
    await userEvent.click(screen.getByRole("combobox", { name: "责任科室" }));
    await userEvent.click(await screen.findByText("医保管理科 · DEPT-INS"));
    await userEvent.click(screen.getByRole("combobox", { name: "质控指标" }));
    await userEvent.click(await screen.findByText("医保违规费用率 · INS.FEE · v2"));
    expect(screen.getByLabelText("审核场景")).toBeInTheDocument();
    expect(screen.queryByLabelText("场景编码")).not.toBeInTheDocument();
    expect(screen.getByLabelText("医保规则依据")).toBeInTheDocument();
    expect(screen.queryByLabelText("规则编码")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("审核场景"), { target: { value: "A9" } });
    fireEvent.change(screen.getByLabelText("DRG 分组器版本"), {
      target: { value: "GROUPER-2026" },
    });
    fireEvent.change(screen.getByLabelText("期望入组"), { target: { value: "DRG-A" } });
    fireEvent.change(screen.getByLabelText("实际入组"), { target: { value: "DRG-B" } });
    fireEvent.change(screen.getByLabelText("入组说明"), {
      target: { value: "病案首页进入复核" },
    });
    fireEvent.change(screen.getByLabelText("医保规则依据"), {
      target: { value: "RULE-FEE-A" },
    });
    fireEvent.change(screen.getByLabelText("依据版本"), { target: { value: "2026-A" } });
    fireEvent.change(screen.getByLabelText("费用阈值"), { target: { value: "1000" } });
    fireEvent.change(screen.getByLabelText("整改截止时间"), {
      target: { value: "2026-06-12T00:00:00Z" },
    });
    fireEvent.change(screen.getByLabelText("规则说明"), {
      target: { value: "费用超过版本化规则阈值" },
    });

    await userEvent.click(screen.getByRole("button", { name: "执行审核并派整改" }));

    await waitFor(() => {
      expect(caseReview).toHaveBeenCalledWith(
        expect.objectContaining({
          contextSnapshotId: "snapshot-ins",
          scenarioCode: "A9",
          responsibleDepartmentId: "dept-insurance",
        }),
      );
      expect(drgGrouping).toHaveBeenCalledWith(
        expect.objectContaining({
          contextSnapshotId: "snapshot-ins",
          expectedGroupCode: "DRG-A",
          actualGroupCode: "DRG-B",
        }),
      );
      expect(insuranceAudit).toHaveBeenCalledWith(
        expect.objectContaining({
          indicatorId: "indicator-insurance",
          rules: [
            expect.objectContaining({
              ruleCode: "RULE-FEE-A",
              ruleVersion: "2026-A",
              maxAmount: 1000,
            }),
          ],
        }),
      );
    });
    expect(refetch).toHaveBeenCalled();
    expect(await screen.findByText("发现医保问题")).toBeInTheDocument();
    expect(screen.getByText("DRG/DIP 入组不一致")).toBeInTheDocument();
    expect(screen.getAllByText("评估运行已记录").length).toBeGreaterThan(0);
    expect(screen.getByText("审核证据已记录")).toBeInTheDocument();
    expect(screen.queryByText("run-ins-1")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-audit")).not.toBeInTheDocument();
    expect(screen.getByText("模型能力已关闭")).toBeInTheDocument();
    expect(screen.getByText("整改任务 1 个")).toBeInTheDocument();
  }, 15_000);

  it("uses an honest empty state when no real insurance issue is returned", () => {
    mockUseInsuranceIssues.mockReturnValue({
      data: { ...insuranceIssuesPage, items: [], total: 0 },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseRunQualityCaseReview.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    mockUseRunDrgGrouping.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    mockUseRunInsuranceAudit.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });

    renderPage();

    expect(screen.getByRole("heading", { name: "医保智能审核" })).toBeInTheDocument();
    expect(screen.getByText("当前筛选下暂无真实医保问题")).toBeInTheDocument();
  });
});
