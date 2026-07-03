import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import QcEvalSets from "./QcEvalSets";

const mockUseEvaluationIndicators = vi.fn();
const mockUseCreateEvaluationIndicator = vi.fn();
const mockUseSubmitEvaluationIndicator = vi.fn();
const mockUsePublishEvaluationIndicator = vi.fn();
const mockUseGrayEvaluationIndicator = vi.fn();
const mockUseActivateEvaluationIndicator = vi.fn();
const mockUseEvaluateSnapshot = vi.fn();
const mockUseContextSnapshots = vi.fn();
const mockUseOrgUnits = vi.fn();
const mockUseSecurityProfile = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useEvaluationIndicators: (params: unknown) => mockUseEvaluationIndicators(params),
  useSecurityProfile: () => mockUseSecurityProfile(),
  useCreateEvaluationIndicator: () => mockUseCreateEvaluationIndicator(),
  useSubmitEvaluationIndicator: () => mockUseSubmitEvaluationIndicator(),
  usePublishEvaluationIndicator: () => mockUsePublishEvaluationIndicator(),
  useGrayEvaluationIndicator: () => mockUseGrayEvaluationIndicator(),
  useActivateEvaluationIndicator: () => mockUseActivateEvaluationIndicator(),
  useEvaluateSnapshot: () => mockUseEvaluateSnapshot(),
  useContextSnapshots: (params: unknown, options: unknown) =>
    mockUseContextSnapshots(params, options),
  useOrgUnits: (params: unknown) => mockUseOrgUnits(params),
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
  responsibleDepartmentId: "质量管理组",
  sourceRef: "真实指南 2026",
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
      <ConfigProvider theme={{ token: { motion: false } }}>
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
let grayIndicator: ReturnType<typeof vi.fn>;
let activateIndicator: ReturnType<typeof vi.fn>;
let evaluateSnapshot: ReturnType<typeof vi.fn>;

beforeEach(() => {
  useEvidenceDetailsStore.setState({ enabled: false });
  refetch = vi.fn();
  createIndicator = vi.fn().mockResolvedValue(realIndicator);
  submitIndicator = vi.fn().mockResolvedValue({ ...realIndicator, status: "PENDING_REVIEW" });
  publishIndicator = vi.fn().mockResolvedValue({ ...realIndicator, status: "PUBLISHED" });
  grayIndicator = vi.fn().mockResolvedValue({ ...realIndicator, status: "GRAY" });
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
  mockUseSecurityProfile.mockReset();
  mockUseSecurityProfile.mockReturnValue({
    data: {
      permissions: [{ code: "evaluation.read" }],
      roles: [{ code: "quality-manager", displayName: "质控人员" }],
      menuKeys: ["qc-eval-sets"],
    },
  });
  mockUseOrgUnits.mockReset();
  mockUseOrgUnits.mockReturnValue({
    data: {
      items: [
        {
          id: "dept-ortho",
          level: "DEPARTMENT",
          code: "ORTHO",
          name: "骨科",
          status: "ACTIVE",
        },
      ],
    },
    isLoading: false,
  });
  mockUseCreateEvaluationIndicator.mockReset();
  mockUseSubmitEvaluationIndicator.mockReset();
  mockUsePublishEvaluationIndicator.mockReset();
  mockUseGrayEvaluationIndicator.mockReset();
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
  mockUseGrayEvaluationIndicator.mockReturnValue({
    mutateAsync: grayIndicator,
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
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getByLabelText("评价指标身份筛选")).toBeInTheDocument();
    expect(screen.queryByLabelText("指标编码筛选")).not.toBeInTheDocument();
    expect(screen.getAllByText("指标已登记").length).toBeGreaterThan(0);
    expect(screen.getByText("外科 VTE 风险评估率")).toBeInTheDocument();
    expect(screen.getAllByText("指标证据已记录").length).toBeGreaterThan(0);
    expect(screen.queryByText("IND.VTE.REAL")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-indicator-real")).not.toBeInTheDocument();
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

  it("证据详情打开后展示指标编码、追踪号和机构生效版本证据", async () => {
    renderPage();

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getAllByText("IND.VTE.REAL").length).toBeGreaterThan(0);
    expect(screen.getByText("trace-indicator-real")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "查看指标详情" }));

    expect(screen.getByText("indicator-real-1")).toBeInTheDocument();
    expect(screen.getAllByText("trace-indicator-real").length).toBeGreaterThan(0);
  });

  it("默认用业务语言展示责任科室、时间窗口和组织范围", async () => {
    const user = userEvent.setup();
    mockUseEvaluationIndicators.mockReturnValue({
      data: {
        items: [
          {
            ...realIndicator,
            responsibleDepartmentId: "dept-ortho",
            timeWindow: "DISCHARGE+24H",
            organizationScope: "p5-hospital",
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch,
    });
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看指标详情" }));

    expect(screen.getAllByText("骨科").length).toBeGreaterThan(0);
    expect(screen.getByText("出院后 24 小时")).toBeInTheDocument();
    expect(screen.getByText("当前医院")).toBeInTheDocument();
    expect(screen.queryByText("dept-ortho")).not.toBeInTheDocument();
    expect(screen.queryByText("DISCHARGE+24H")).not.toBeInTheDocument();
    expect(screen.queryByText("p5-hospital")).not.toBeInTheDocument();
  });

  it("loads department references without exposing an evaluation package selector", () => {
    renderPage();

    expect(mockUseOrgUnits).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      sort: "name,asc",
      level: "DEPARTMENT",
      status: "ACTIVE",
    });
    expect(screen.queryByLabelText("配置" + "包版本")).not.toBeInTheDocument();
  });

  it("creates a draft indicator from condition-tree DSL instead of raw hard-coded JSON text", async () => {
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "新建指标" }));

    expect(screen.queryByLabelText("版本号")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("稳定评价指标身份"), {
      target: { value: "IND.NEW.VTE" },
    });
    fireEvent.change(screen.getByLabelText("指标名称"), { target: { value: "新 VTE 指标" } });
    await userEvent.click(screen.getByRole("combobox", { name: "责任科室" }));
    await userEvent.click(await screen.findByText("骨科 · ORTHO"));
    expect(screen.getAllByText("出院后 24 小时").length).toBeGreaterThan(0);
    expect(screen.getAllByText("全院").length).toBeGreaterThan(0);
    expect(screen.queryByText("DISCHARGE+24H")).not.toBeInTheDocument();
    expect(screen.getByText("指标版本独立维护")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("来源依据"), { target: { value: "院内真实指南 2026" } });

    const factInputs = screen.getAllByRole("combobox", { name: "上下文字段路径" });
    const valueInputs = screen.getAllByLabelText("比较值");
    fireEvent.change(factInputs[0], { target: { value: "encounters.0.admissionType" } });
    fireEvent.change(valueInputs[0], { target: { value: "SURGICAL" } });
    fireEvent.change(factInputs[1], { target: { value: "observations.0.code" } });
    fireEvent.change(valueInputs[1], { target: { value: "VTE_ASSESSMENT" } });

    fireEvent.click(screen.getByRole("button", { name: "创建指标草稿" }));

    await waitFor(() => expect(createIndicator).toHaveBeenCalledTimes(1));
    const payload = createIndicator.mock.calls[0][0];
    expect(payload).not.toHaveProperty("versionNo");
    expect(payload).toEqual(
      expect.objectContaining({
        indicatorCode: "IND.NEW.VTE",
        name: "新 VTE 指标",
        timeWindow: "DISCHARGE+24H",
        organizationScope: "全院",
        responsibleDepartmentId: "dept-ortho",
      }),
    );
    expect(payload).not.toHaveProperty("packageVersion");
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
    expect(screen.getAllByText("条件根组 · 第 1 层").length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "提交安全复核" }));

    await waitFor(() => expect(submitIndicator).toHaveBeenCalledWith("indicator-real-1"));
    expect(refetch).toHaveBeenCalled();
    expect(screen.queryByText(/医务处|信息科主任|多人审核/)).not.toBeInTheDocument();
  });

  it("requires release evidence before starting the default gray rollout", async () => {
    mockUseEvaluationIndicators.mockReturnValue({
      data: {
        items: [{ ...realIndicator, status: "PUBLISHED" }],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch,
    });
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "查看指标详情" }));
    fireEvent.click(await screen.findByRole("button", { name: "开始灰度" }));
    fireEvent.change(await screen.findByLabelText("发布说明"), {
      target: { value: "先在 10% 床位观察 24 小时" },
    });
    fireEvent.click(screen.getByRole("button", { name: "确认灰度" }));

    await waitFor(() =>
      expect(grayIndicator).toHaveBeenCalledWith({
        indicatorId: "indicator-real-1",
        reason: "先在 10% 床位观察 24 小时",
      }),
    );
    expect(refetch).toHaveBeenCalled();
  });

  it("runs snapshot simulation through the canonical evaluation endpoint", async () => {
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "仿真评估" }));
    expect(screen.queryByLabelText("临床快照 ID")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("可按住院号、门诊号或就诊信息检索")).toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText("可按住院号、门诊号或就诊标识检索"),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("患者信息"), { target: { value: "patient-real-1" } });
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

    fireEvent.click(await screen.findByRole("button", { name: "选择第 1 个临床快照" }));
    fireEvent.click(screen.getByRole("button", { name: "执行仿真评估" }));

    await waitFor(() =>
      expect(evaluateSnapshot).toHaveBeenCalledWith({
        contextSnapshotId: "snapshot-real-1",
        scenarioCode: "DISCHARGE",
      }),
    );
    expect(await screen.findByText("评估运行已记录")).toBeInTheDocument();
    expect(screen.getByText("仿真证据已记录")).toBeInTheDocument();
    expect(screen.queryByText("run-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-run-real")).not.toBeInTheDocument();
  });
});
