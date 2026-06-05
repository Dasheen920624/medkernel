import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import QcEvalSets from "./QcEvalSets";

const mockUseEvaluationIndicators = vi.fn();
const mockUseCreateEvaluationIndicator = vi.fn();
const mockUseSubmitEvaluationIndicator = vi.fn();
const mockUsePublishEvaluationIndicator = vi.fn();
const mockUseActivateEvaluationIndicator = vi.fn();
const mockUseEvaluateSnapshot = vi.fn();
const mockUseContextSnapshots = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useEvaluationIndicators: (params: unknown) => mockUseEvaluationIndicators(params),
  useCreateEvaluationIndicator: () => mockUseCreateEvaluationIndicator(),
  useSubmitEvaluationIndicator: () => mockUseSubmitEvaluationIndicator(),
  usePublishEvaluationIndicator: () => mockUsePublishEvaluationIndicator(),
  useActivateEvaluationIndicator: () => mockUseActivateEvaluationIndicator(),
  useEvaluateSnapshot: () => mockUseEvaluateSnapshot(),
  useContextSnapshots: (params: unknown, options: unknown) =>
    mockUseContextSnapshots(params, options),
}));

const realIndicator = {
  indicatorId: "indicator-real-1",
  tenantId: "tenant-A",
  indicatorCode: "IND.VTE.REAL",
  versionNo: 2,
  name: "外科 VTE 风险评估率",
  subjectType: "MEDICAL_RECORD",
  denominatorDefinition: JSON.stringify({
    all: [{ fact: "encounters.0.admissionType", operator: "equals", value: "SURGICAL" }],
  }),
  numeratorDefinition: JSON.stringify({
    all: [{ fact: "observations.0.code", operator: "exists" }],
  }),
  exclusionDefinition: "",
  scoringDefinition: "P1:扣 100 分",
  timeWindow: "DISCHARGE+24H",
  organizationScope: "全院",
  responsibleDepartmentId: "医务处",
  sourceRef: "真实指南 2026",
  packageVersion: "2026.06",
  status: "DRAFT",
  traceId: "trace-indicator-real",
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
          <QcEvalSets />
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

let refetch: ReturnType<typeof vi.fn>;
let createIndicator: ReturnType<typeof vi.fn>;
let submitIndicator: ReturnType<typeof vi.fn>;
let publishIndicator: ReturnType<typeof vi.fn>;
let activateIndicator: ReturnType<typeof vi.fn>;
let evaluateSnapshot: ReturnType<typeof vi.fn>;

beforeEach(() => {
  refetch = vi.fn();
  createIndicator = vi.fn().mockResolvedValue(realIndicator);
  submitIndicator = vi.fn().mockResolvedValue({ ...realIndicator, status: "PENDING_REVIEW" });
  publishIndicator = vi.fn().mockResolvedValue({ ...realIndicator, status: "PUBLISHED" });
  activateIndicator = vi.fn().mockResolvedValue({ ...realIndicator, status: "ACTIVE" });
  evaluateSnapshot = vi.fn().mockResolvedValue({
    runId: "run-real-1",
    status: "RECORDED",
    resultCount: 1,
    findingCount: 0,
    taskCount: 0,
    traceId: "trace-run-real",
  });

  mockUseEvaluationIndicators.mockReset();
  mockUseCreateEvaluationIndicator.mockReset();
  mockUseSubmitEvaluationIndicator.mockReset();
  mockUsePublishEvaluationIndicator.mockReset();
  mockUseActivateEvaluationIndicator.mockReset();
  mockUseEvaluateSnapshot.mockReset();
  mockUseContextSnapshots.mockReset();

  mockUseEvaluationIndicators.mockReturnValue({
    data: { items: [realIndicator], page: 1, size: 20, total: 1, hasNext: false },
    isLoading: false,
    isError: false,
    error: undefined,
    refetch,
  });
  mockUseCreateEvaluationIndicator.mockReturnValue({
    mutateAsync: createIndicator,
    isPending: false,
  });
  mockUseSubmitEvaluationIndicator.mockReturnValue({
    mutateAsync: submitIndicator,
    isPending: false,
  });
  mockUsePublishEvaluationIndicator.mockReturnValue({
    mutateAsync: publishIndicator,
    isPending: false,
  });
  mockUseActivateEvaluationIndicator.mockReturnValue({
    mutateAsync: activateIndicator,
    isPending: false,
  });
  mockUseEvaluateSnapshot.mockReturnValue({
    mutateAsync: evaluateSnapshot,
    isPending: false,
  });
  mockUseContextSnapshots.mockReturnValue({
    data: {
      items: [
        {
          snapshotId: "snapshot-real-1",
          patientId: "patient-real-1",
          encounterId: "enc-real-1",
          status: "ACTIVE",
          qualityStatus: "PASS",
          createdAt: "2026-06-06T00:00:00Z",
        },
      ],
      page: 1,
      size: 20,
      total: 1,
    },
    isLoading: false,
    isError: false,
  });
});

describe("QcEvalSets", () => {
  it("loads real indicators with API-13 pagination and renders the 7-step configuration flow", () => {
    renderPage();

    expect(mockUseEvaluationIndicators).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1, size: 20 }),
    );
    expect(screen.getByRole("heading", { name: "评估指标库" })).toBeInTheDocument();
    expect(screen.getByText("真实评估指标总数")).toBeInTheDocument();
    expect(screen.getAllByText("IND.VTE.REAL").length).toBeGreaterThan(0);
    expect(screen.getByText("外科 VTE 风险评估率")).toBeInTheDocument();
    expect(screen.getByText("trace-indicator-real")).toBeInTheDocument();
    expect(screen.getByText("选模板/导入")).toBeInTheDocument();
    expect(screen.getByText("留证据/可回滚")).toBeInTheDocument();
    expect(
      screen.queryByText(/接口尚未接入|本地违规病例样例|TRACE_NOT_FOUND/),
    ).not.toBeInTheDocument();

    const pagePrimaryButtons = screen
      .getAllByRole("button")
      .filter(
        (button) =>
          button.className.includes("ant-btn-primary") &&
          !button.closest(".ant-modal") &&
          !button.closest(".ant-drawer"),
      );
    expect(pagePrimaryButtons).toHaveLength(1);
  });

  it("creates a draft indicator from condition-tree DSL instead of raw hard-coded JSON text", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "新建指标" }));

    fireEvent.change(screen.getByLabelText("指标编码"), { target: { value: "IND.NEW.VTE" } });
    fireEvent.change(screen.getByLabelText("指标名称"), { target: { value: "新 VTE 指标" } });
    fireEvent.change(screen.getByLabelText("责任科室"), { target: { value: "骨科" } });
    fireEvent.change(screen.getByLabelText("来源依据"), { target: { value: "院内真实指南 2026" } });

    const factInputs = screen.getAllByLabelText("上下文字段路径");
    const valueInputs = screen.getAllByLabelText("比较值");
    fireEvent.change(factInputs[0], { target: { value: "encounters.0.admissionType" } });
    fireEvent.change(valueInputs[0], { target: { value: "SURGICAL" } });
    fireEvent.change(factInputs[1], { target: { value: "observations.0.code" } });
    fireEvent.change(valueInputs[1], { target: { value: "VTE_ASSESSMENT" } });

    await user.click(screen.getByRole("button", { name: "创建指标草稿" }));

    await waitFor(() => expect(createIndicator).toHaveBeenCalledTimes(1));
    const payload = createIndicator.mock.calls[0][0];
    expect(payload).toEqual(
      expect.objectContaining({
        indicatorCode: "IND.NEW.VTE",
        name: "新 VTE 指标",
        responsibleDepartmentId: "骨科",
      }),
    );
    expect(JSON.parse(payload.denominatorDefinition)).toEqual({
      all: [
        expect.objectContaining({
          fact: "encounters.0.admissionType",
          operator: "equals",
          value: "SURGICAL",
        }),
      ],
    });
    expect(JSON.parse(payload.numeratorDefinition)).toEqual({
      all: [
        expect.objectContaining({
          fact: "observations.0.code",
          operator: "equals",
          value: "VTE_ASSESSMENT",
        }),
      ],
    });
  });

  it("submits draft indicators through the real lifecycle endpoint", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看指标详情" }));
    expect(screen.getAllByText("条件根组").length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "提交审核" }));

    await waitFor(() => expect(submitIndicator).toHaveBeenCalledWith("indicator-real-1"));
    expect(refetch).toHaveBeenCalled();
  });

  it("runs snapshot simulation through the canonical evaluation endpoint", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "仿真评估" }));
    fireEvent.change(screen.getByLabelText("患者 ID"), { target: { value: "patient-real-1" } });
    await waitFor(() =>
      expect(mockUseContextSnapshots).toHaveBeenLastCalledWith(
        expect.objectContaining({
          patientId: "patient-real-1",
          status: "ACTIVE",
          page: 1,
          size: 20,
        }),
        expect.objectContaining({ enabled: true }),
      ),
    );

    await user.click(screen.getByRole("button", { name: "选择 snapshot-real-1" }));
    fireEvent.change(screen.getByLabelText("配置包版本"), { target: { value: "2026.06" } });
    await user.click(screen.getByRole("button", { name: "执行仿真评估" }));

    await waitFor(() =>
      expect(evaluateSnapshot).toHaveBeenCalledWith({
        contextSnapshotId: "snapshot-real-1",
        scenarioCode: "DISCHARGE",
        packageVersion: "2026.06",
      }),
    );
    expect(await screen.findByText("run-real-1")).toBeInTheDocument();
    expect(screen.getByText("trace-run-real")).toBeInTheDocument();
  });
});
