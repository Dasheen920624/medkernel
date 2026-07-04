import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import QcEvalResults from "./QcEvalResults";

const mockUseEvaluationResults = vi.fn();
const mockUseQualityFindings = vi.fn();
const mockUseQualityFindingDetail = vi.fn();
const mockUseDispatchRectification = vi.fn();
const mockUseOrgUnits = vi.fn();
const mockUseOrgUsers = vi.fn();
const mockUseSecurityProfile = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useEvaluationResults: (params: unknown) => mockUseEvaluationResults(params),
  useQualityFindings: (params: unknown) => mockUseQualityFindings(params),
  useQualityFindingDetail: (findingId: string) => mockUseQualityFindingDetail(findingId),
  useDispatchRectification: () => mockUseDispatchRectification(),
  useOrgUnits: (params: unknown) => mockUseOrgUnits(params),
  useOrgUsers: (params: unknown) => mockUseOrgUsers(params),
  useSecurityProfile: () => mockUseSecurityProfile(),
}));

const realResult = {
  resultId: "result-real-1",
  tenantId: "tenant-A",
  runId: "run-real-1",
  indicatorId: "indicator-real-1",
  indicatorCode: "IND.VTE.REAL",
  indicatorVersion: 2,
  subjectType: "MEDICAL_RECORD",
  subjectRefId: "mr-real-1",
  scoreValue: 62.5,
  resultLevel: "NON_COMPLIANT",
  hitFlag: false,
  evidenceSummary: "病历 mr-real-1 缺少 VTE 风险评估记录",
  sourceRef: "canonical:medical-record:mr-real-1",
  responsibleDepartmentId: "dept-cardio",
  createdAt: "2026-06-06T01:00:00Z",
  traceId: "trace-result-real",
};

const realFinding = {
  findingId: "finding-real-1",
  tenantId: "tenant-A",
  runId: "run-real-1",
  resultId: "result-real-1",
  indicatorId: "indicator-real-1",
  findingCode: "QF.VTE.MISSING",
  title: "VTE 风险评估缺失",
  description: "出院前缺少 VTE 风险评估记录，需责任科室整改。",
  severity: "P1",
  status: "NEW",
  evidenceSummary: "来源 canonical:medical-record:mr-real-1，缺少 observations.VTE_ASSESSMENT",
  responsibleDepartmentId: "dept-cardio",
  dueAt: "2026-06-09T00:00:00Z",
  createdAt: "2026-06-06T01:01:00Z",
  traceId: "trace-finding-real",
};

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <AntdApp>
          <QcEvalResults />
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

let refetchResults: ReturnType<typeof vi.fn>;
let refetchFindings: ReturnType<typeof vi.fn>;
let dispatchRectification: ReturnType<typeof vi.fn>;

beforeEach(() => {
  refetchResults = vi.fn();
  refetchFindings = vi.fn();
  dispatchRectification = vi.fn().mockResolvedValue({
    taskId: "task-real-1",
    findingStatus: "ASSIGNED",
    taskStatus: "ASSIGNED",
    traceId: "trace-dispatch-real",
  });

  mockUseEvaluationResults.mockReset();
  mockUseQualityFindings.mockReset();
  mockUseQualityFindingDetail.mockReset();
  mockUseDispatchRectification.mockReset();
  mockUseOrgUnits.mockReset();
  mockUseOrgUsers.mockReset();
  mockUseSecurityProfile.mockReset();
  useEvidenceDetailsStore.setState({ enabled: false });
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

  mockUseEvaluationResults.mockReturnValue({
    data: { items: [realResult], page: 1, size: 20, total: 1, hasNext: false },
    refetch: refetchResults,
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseQualityFindings.mockReturnValue({
    data: { items: [realFinding], page: 1, size: 20, total: 1, hasNext: false },
    refetch: refetchFindings,
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseQualityFindingDetail.mockReturnValue({
    data: { finding: realFinding, task: undefined, reviews: [] },
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseDispatchRectification.mockReturnValue({
    mutateAsync: dispatchRectification,
    isPending: false,
  });
});

describe("QcEvalResults", () => {
  it("loads real results and findings with API-13 pagination instead of local KPI constants", () => {
    renderPage();

    expect(mockUseEvaluationResults).toHaveBeenCalledWith(
      expect.objectContaining({
        resultLevel: "NON_COMPLIANT",
        page: 1,
        size: 20,
        sort: "createdAt,desc",
      }),
    );
    expect(mockUseQualityFindings).toHaveBeenCalledWith(
      expect.objectContaining({
        status: "NEW",
        page: 1,
        size: 20,
        sort: "createdAt,desc",
      }),
    );

    expect(screen.getByRole("heading", { name: "质量问题来源" })).toBeInTheDocument();
    expect(screen.getByText("按评价结果追溯问题证据")).toBeInTheDocument();
    expect(screen.getByText("评价结果总数")).toBeInTheDocument();
    expect(screen.queryByText("按真实评价结果追溯问题证据")).not.toBeInTheDocument();
    expect(screen.queryByText("真实评价结果总数")).not.toBeInTheDocument();
    expect(screen.getByText("待整改问题总数")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getAllByText("评价指标已关联").length).toBeGreaterThan(0);
    expect(screen.getByText("第 2 版评价口径")).toBeInTheDocument();
    expect(screen.getByText("对象已关联")).toBeInTheDocument();
    expect(screen.getAllByText("病历证据已关联").length).toBeGreaterThan(0);
    expect(screen.getAllByText("证据已记录").length).toBeGreaterThan(0);
    expect(screen.getByText("VTE 风险评估缺失")).toBeInTheDocument();
    expect(screen.getAllByText("心内科").length).toBeGreaterThan(0);
    expect(screen.queryByText("心内科 · CARDIO")).not.toBeInTheDocument();
    expect(screen.queryByText("IND.VTE.REAL")).not.toBeInTheDocument();
    expect(screen.queryByText("v2")).not.toBeInTheDocument();
    expect(screen.queryByText(/mr-real-1/)).not.toBeInTheDocument();
    expect(screen.queryByText(/canonical:medical-record/)).not.toBeInTheDocument();
    expect(screen.queryByText("trace-result-real")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-finding-real")).not.toBeInTheDocument();
    expect(screen.queryByText("indicator-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("result-real-1")).not.toBeInTheDocument();
    expect(
      screen.queryByText(/485|152|92\.8|TRACE_NOT_FOUND|本地违规病例样例/),
    ).not.toBeInTheDocument();
  });

  it("uses quality source wording when there are no evaluation results", () => {
    mockUseEvaluationResults.mockReturnValue({
      data: { items: [], page: 1, size: 20, total: 0, hasNext: false },
      refetch: refetchResults,
      isLoading: false,
      isError: false,
      error: undefined,
    });
    mockUseQualityFindings.mockReturnValue({
      data: { items: [], page: 1, size: 20, total: 0, hasNext: false },
      refetch: refetchFindings,
      isLoading: false,
      isError: false,
      error: undefined,
    });

    renderPage();

    expect(screen.getByText("当前筛选下暂无评价结果")).toBeInTheDocument();
    expect(screen.queryByText("当前筛选下暂无真实评价结果")).not.toBeInTheDocument();
    expect(screen.queryByText("暂无真实评价结果")).not.toBeInTheDocument();
  });

  it("opens a real quality finding detail drawer with evidence and lifecycle facts", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看问题详情" }));

    expect(mockUseQualityFindingDetail).toHaveBeenLastCalledWith("finding-real-1");
    expect(screen.getByText("问题详情与病历证据")).toBeInTheDocument();
    expect(screen.getByText("问题身份")).toBeInTheDocument();
    expect(screen.queryByText("问题编码")).not.toBeInTheDocument();
    expect(screen.getAllByText("问题已登记").length).toBeGreaterThan(0);
    expect(screen.getAllByText("评价指标已关联").length).toBeGreaterThan(0);
    expect(screen.getAllByText("评价结果已关联").length).toBeGreaterThan(0);
    expect(screen.getByText("评估运行已记录")).toBeInTheDocument();
    expect(screen.getAllByText("病历证据已关联").length).toBeGreaterThan(0);
    expect(screen.queryByText("QF.VTE.MISSING")).not.toBeInTheDocument();
    expect(screen.queryByText("indicator-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("result-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-finding-real")).not.toBeInTheDocument();
    expect(screen.queryByText(/canonical:medical-record/)).not.toBeInTheDocument();
    expect(screen.getByText("暂无整改任务")).toBeInTheDocument();
  });

  it("证据详情打开后展示评价结果、问题和整改链路原始追溯字段", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("IND.VTE.REAL")).toBeInTheDocument();
    expect(screen.getByText("v2")).toBeInTheDocument();
    expect(screen.getByText("病历 mr-real-1 缺少 VTE 风险评估记录")).toBeInTheDocument();
    expect(screen.getByText("canonical:medical-record:mr-real-1")).toBeInTheDocument();
    expect(screen.getByText("trace-result-real")).toBeInTheDocument();
    expect(screen.getByText("trace-finding-real")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "查看问题详情" }));

    expect(screen.getAllByText("QF.VTE.MISSING").length).toBeGreaterThan(0);
    expect(screen.getAllByText("indicator-real-1").length).toBeGreaterThan(0);
    expect(screen.getAllByText("result-real-1").length).toBeGreaterThan(0);
    expect(screen.getByText("run-real-1")).toBeInTheDocument();
    expect(
      screen.getAllByText(
        "来源 canonical:medical-record:mr-real-1，缺少 observations.VTE_ASSESSMENT",
      ).length,
    ).toBeGreaterThan(0);
  });

  it("dispatches a rectification task for a real finding instead of mutating browser-only state", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看问题详情" }));
    expect(screen.getByLabelText("整改截止时间")).not.toHaveAttribute("type", "datetime-local");
    expect(screen.getByLabelText("整改截止时间")).toHaveValue("2026年06月09日 08:00");
    await user.click(screen.getByLabelText("责任人"));
    expect(screen.queryByText("质控专员 · u-quality-1")).not.toBeInTheDocument();
    await user.click(screen.getByText("质控专员"));
    fireEvent.change(screen.getByLabelText("整改截止时间"), {
      target: { value: "2026年06月09日 08:00" },
    });

    await user.click(screen.getByRole("button", { name: "派发整改任务" }));

    await waitFor(() => {
      expect(dispatchRectification).toHaveBeenCalledWith(
        expect.objectContaining({
          request: {
            findingId: "finding-real-1",
            responsibleDepartmentId: "dept-cardio",
            assigneeUserId: "u-quality-1",
            dueAt: "2026-06-09T00:00:00.000Z",
          },
          idempotencyKey: expect.stringContaining("finding-real-1"),
        }),
      );
    });
    expect(refetchFindings).toHaveBeenCalled();
    expect(refetchResults).toHaveBeenCalled();
    expect(screen.getByText("整改任务已派发")).toBeInTheDocument();
  });
});
