import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useAdapterHubStatus,
  useAdvanceIntegrationOnboarding,
  useCheckAdapterHealth,
  useCreateAdapter,
  useCreateIntegrationOnboarding,
  useGenerateDataQualityReport,
  useIntegrationAdapters,
  useIntegrationLogs,
  useIntegrationOnboardings,
  useReplayDeadLetter,
  useRetryMessage,
  useSecurityProfile,
  useUpdateAdapter,
  type AdapterHubStatus,
  type DataQualityReport,
  type IntegrationAdapter,
  type IntegrationMessageLog,
  type IntegrationOnboarding,
  type SecurityProfile,
} from "@/shared/api/hooks";

import AdapterHub from "./AdapterHub";

vi.mock("@/shared/api/hooks", () => ({
  useAdapterHubStatus: vi.fn(),
  useAdvanceIntegrationOnboarding: vi.fn(),
  useCheckAdapterHealth: vi.fn(),
  useCreateAdapter: vi.fn(),
  useCreateIntegrationOnboarding: vi.fn(),
  useGenerateDataQualityReport: vi.fn(),
  useIntegrationAdapters: vi.fn(),
  useIntegrationLogs: vi.fn(),
  useIntegrationOnboardings: vi.fn(),
  useReplayDeadLetter: vi.fn(),
  useRetryMessage: vi.fn(),
  useSecurityProfile: vi.fn(),
  useUpdateAdapter: vi.fn(),
}));

const profile: SecurityProfile = {
  userId: "it-1",
  username: "it.owner",
  roles: [
    {
      code: "it-ops",
      displayName: "信息科",
      source: "DEFAULT",
      scopeLevel: null,
      scopeCode: null,
    },
  ],
  mustChangePwd: false,
  mfaRequired: false,
  mfaBound: false,
  permissions: [
    {
      code: "integration.read",
      dimension: "ACTION",
      target: "integration",
      displayName: "查看适配器",
      risk: "LOW",
    },
    {
      code: "integration.write",
      dimension: "ACTION",
      target: "integration",
      displayName: "管理适配器",
      risk: "MEDIUM",
    },
    {
      code: "integration.execute",
      dimension: "ACTION",
      target: "integration",
      displayName: "执行接入操作",
      risk: "MEDIUM",
    },
  ],
  menuKeys: ["adapter-hub", "provenance"],
  environmentKeys: ["production"],
  dataScope: {
    tenantId: "tenant-1",
    groupId: null,
    hospitalId: "h-1",
    campusId: null,
    siteId: null,
    departmentId: null,
    specialtyId: null,
  },
};

const hisAdapter: IntegrationAdapter = {
  id: 1,
  adapterId: "his-main",
  tenantId: "tenant-1",
  name: "HIS 主数据接入",
  protocolType: "REST",
  status: "ACTIVE",
  configJson: '{"fieldMappings":{"patientId":"PATIENT_ID"}}',
  healthStatus: "NOT_CONNECTED",
  rttMs: 0,
  lastHeartbeatAt: "2026-06-03T08:00:00Z",
  createdAt: "2026-06-01T08:00:00Z",
  updatedAt: "2026-06-03T08:00:00Z",
};

const status: AdapterHubStatus = {
  totalAdapters: 2,
  activeAdapters: 1,
  suspendedAdapters: 1,
  healthyAdapters: 0,
  notConnectedAdapters: 1,
  misconfiguredAdapters: 1,
  mappedAdapters: 1,
  generatedAt: "2026-06-03T08:00:00Z",
  sources: [
    {
      adapterId: "his-main",
      name: "HIS 主数据接入",
      protocolType: "REST",
      status: "ACTIVE",
      healthStatus: "NOT_CONNECTED",
      mappedFieldCount: 12,
      lastHeartbeatAt: "2026-06-03T08:00:00Z",
      gaps: ["缺少检查报告时间映射"],
    },
  ],
};

const failedLog: IntegrationMessageLog = {
  id: 10,
  messageId: "msg-failed",
  tenantId: "tenant-1",
  traceId: "trace-failed",
  direction: "INBOUND",
  systemName: "HIS",
  protocolType: "REST",
  payloadSummary: "患者主数据入站失败",
  payload: "{}",
  status: "FAILED",
  retryCount: 1,
  maxRetries: 3,
  errorMessage: "外部系统断连",
  createdAt: "2026-06-03T08:05:00Z",
  updatedAt: "2026-06-03T08:05:00Z",
};

const deadLetterLog: IntegrationMessageLog = {
  ...failedLog,
  id: 11,
  messageId: "msg-dead",
  traceId: "trace-dead",
  status: "DEAD_LETTER",
  retryCount: 3,
  errorMessage: "重试超限",
};

const onboarding: IntegrationOnboarding = {
  onboardingId: "onb-his",
  name: "HIS 主数据接入申请",
  status: "MAPPING_CONFIGURED",
  routeType: "ADAPTER",
  routeReference: "/api/v1/engine/integration/adapters/his-main",
  healthStatus: "NOT_CONNECTED",
  mappedFieldCount: 12,
  blockers: [],
  sourceSystem: "HIS",
  businessScenario: "门诊患者主数据",
  orgPath: "集团/医院",
  callbackWebhookId: null,
  createdAt: "2026-06-01T08:00:00Z",
  updatedAt: "2026-06-03T08:00:00Z",
};

const qualityReport: DataQualityReport = {
  reportId: "dqr-1",
  tenantId: "tenant-1",
  generatedAt: "2026-06-03T08:10:00Z",
  requiredFieldTotal: 100,
  requiredFieldPresent: 82,
  requiredFieldRate: 82,
  adapterTotal: 2,
  mappedAdapterCount: 1,
  mappingRate: 50,
  timelyAdapterCount: 1,
  timelinessRate: 50,
  notConnectedCount: 1,
  misconfiguredCount: 1,
  gapSummary: "HIS 断连，LIS 配置非法",
  createdAt: "2026-06-03T08:10:00Z",
  createdBy: "it-1",
  traceId: "trace-dqr",
};

function query<T>(data: T, overrides: Record<string, unknown> = {}) {
  return {
    data,
    isLoading: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  };
}

function mutation<T>(result: T) {
  return {
    mutateAsync: vi.fn().mockResolvedValue(result),
    isPending: false,
  };
}

function setupMocks() {
  vi.mocked(useSecurityProfile).mockReturnValue(query(profile) as never);
  vi.mocked(useIntegrationAdapters).mockReturnValue(query([hisAdapter]) as never);
  vi.mocked(useAdapterHubStatus).mockReturnValue(query(status) as never);
  vi.mocked(useIntegrationLogs).mockReturnValue(
    query({ items: [failedLog, deadLetterLog], total: 2 }) as never,
  );
  vi.mocked(useIntegrationOnboardings).mockReturnValue(query([onboarding]) as never);
  vi.mocked(useCreateAdapter).mockReturnValue(mutation(hisAdapter) as never);
  vi.mocked(useUpdateAdapter).mockReturnValue(mutation(hisAdapter) as never);
  vi.mocked(useCheckAdapterHealth).mockReturnValue(
    mutation({ ...hisAdapter, healthStatus: "NOT_CONNECTED", rttMs: 0 }) as never,
  );
  vi.mocked(useGenerateDataQualityReport).mockReturnValue(mutation(qualityReport) as never);
  vi.mocked(useRetryMessage).mockReturnValue(
    mutation({ ...failedLog, status: "RETRYING" }) as never,
  );
  vi.mocked(useReplayDeadLetter).mockReturnValue(
    mutation({
      sourceMessageId: "msg-dead",
      replayMessageId: "msg-replay",
      traceId: "trace-replay",
      status: "NOT_CONNECTED",
      blocksMainFlow: false,
      message: "已重放为补偿消息",
    }) as never,
  );
  vi.mocked(useCreateIntegrationOnboarding).mockReturnValue(mutation(onboarding) as never);
  vi.mocked(useAdvanceIntegrationOnboarding).mockReturnValue(
    mutation({ ...onboarding, status: "ONLINE" }) as never,
  );
}

function renderPage() {
  return render(
    <ConfigProvider>
      <AdapterHub />
    </ConfigProvider>,
  );
}

describe("AdapterHub", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupMocks();
  });

  it("renders the real adapter operations workspace without old webhook or launch-token console", async () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "适配器中心" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "新增适配器" })).toHaveLength(1);
    expect(screen.getAllByText("NOT_CONNECTED").length).toBeGreaterThan(0);
    expect(screen.getByText("缺少检查报告时间映射")).toBeInTheDocument();
    expect(screen.getByText("死信重放")).toBeInTheDocument();
    expect(screen.getByText("数据质量看板")).toBeInTheDocument();
    expect(screen.getByText("接入向导")).toBeInTheDocument();
    expect(screen.getByText("选模板/导入")).toBeInTheDocument();
    expect(screen.queryByText(/Webhook 回调订阅安全自研沙箱/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Launch Token/)).not.toBeInTheDocument();
  });

  it("checks adapter health and keeps disconnected adapters honest as NOT_CONNECTED", async () => {
    const user = userEvent.setup();
    const health = mutation({ ...hisAdapter, healthStatus: "NOT_CONNECTED", rttMs: 0 });
    vi.mocked(useCheckAdapterHealth).mockReturnValue(health as never);

    renderPage();

    await user.click(screen.getByRole("button", { name: "健康诊断" }));

    expect(health.mutateAsync).toHaveBeenCalledWith("his-main");
    expect(await screen.findByText("外部连通性未知")).toBeInTheDocument();
    expect(screen.queryByText("伪造在线")).not.toBeInTheDocument();
  });

  it("retries failed messages and replays only dead-letter messages", async () => {
    const user = userEvent.setup();
    const retry = mutation({ ...failedLog, status: "RETRYING" });
    const replay = mutation({
      sourceMessageId: "msg-dead",
      replayMessageId: "msg-replay",
      traceId: "trace-replay",
      status: "NOT_CONNECTED",
      blocksMainFlow: false,
      message: "已重放为补偿消息",
    });
    vi.mocked(useRetryMessage).mockReturnValue(retry as never);
    vi.mocked(useReplayDeadLetter).mockReturnValue(replay as never);

    renderPage();
    await user.click(screen.getByRole("tab", { name: "死信重放" }));

    const failedRow = screen.getByText("msg-failed").closest("tr");
    const deadRow = screen.getByText("msg-dead").closest("tr");
    expect(failedRow).not.toBeNull();
    expect(deadRow).not.toBeNull();

    await user.click(within(failedRow as HTMLElement).getByRole("button", { name: "重试" }));
    await user.click(within(deadRow as HTMLElement).getByRole("button", { name: "重放" }));

    expect(retry.mutateAsync).toHaveBeenCalledWith("msg-failed");
    expect(replay.mutateAsync).toHaveBeenCalledWith("msg-dead");
    expect(within(failedRow as HTMLElement).getByRole("button", { name: "重放" })).toBeDisabled();
    expect(within(deadRow as HTMLElement).getByRole("button", { name: "重试" })).toBeDisabled();
  });

  it("generates a real data quality report and advances onboarding with evidence", async () => {
    const user = userEvent.setup();
    const generateReport = mutation(qualityReport);
    const advanceOnboarding = mutation({ ...onboarding, status: "ONLINE" });
    vi.mocked(useGenerateDataQualityReport).mockReturnValue(generateReport as never);
    vi.mocked(useAdvanceIntegrationOnboarding).mockReturnValue(advanceOnboarding as never);

    renderPage();

    await user.click(screen.getByRole("button", { name: "生成质量报告" }));
    expect(generateReport.mutateAsync).toHaveBeenCalledTimes(1);
    expect(await screen.findByText("HIS 断连，LIS 配置非法")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "接入向导" }));
    await user.click(screen.getByRole("button", { name: "推进到上线" }));

    expect(advanceOnboarding.mutateAsync).toHaveBeenCalledWith({
      onboardingId: "onb-his",
      targetStatus: "ONLINE",
      evidenceText: expect.stringContaining("字段映射"),
    });
  });

  it("renders all six page states from real query status instead of local fallback data", () => {
    vi.mocked(useIntegrationAdapters).mockReturnValue(query([], { isLoading: true }) as never);
    const { rerender } = renderPage();
    expect(screen.getByText("正在加载适配器中心")).toBeInTheDocument();

    vi.mocked(useIntegrationAdapters).mockReturnValue(query([]) as never);
    vi.mocked(useAdapterHubStatus).mockReturnValue(query({ ...status, totalAdapters: 0 }) as never);
    vi.mocked(useIntegrationLogs).mockReturnValue(query({ items: [], total: 0 }) as never);
    vi.mocked(useIntegrationOnboardings).mockReturnValue(query([]) as never);
    rerender(
      <ConfigProvider>
        <AdapterHub />
      </ConfigProvider>,
    );
    expect(screen.getByText("暂无适配器接入记录")).toBeInTheDocument();

    vi.mocked(useIntegrationAdapters).mockReturnValue(
      query([], { isError: true, error: new Error("boom") }) as never,
    );
    rerender(
      <ConfigProvider>
        <AdapterHub />
      </ConfigProvider>,
    );
    expect(screen.getByText("适配器中心暂时不可用")).toBeInTheDocument();

    vi.mocked(useIntegrationAdapters).mockReturnValue(query([hisAdapter]) as never);
    vi.mocked(useAdapterHubStatus).mockReturnValue(
      query(undefined, { isError: true, error: new Error("status") }) as never,
    );
    rerender(
      <ConfigProvider>
        <AdapterHub />
      </ConfigProvider>,
    );
    expect(screen.getByText("部分接入需要处理")).toBeInTheDocument();

    vi.mocked(useSecurityProfile).mockReturnValue(
      query({ ...profile, permissions: [], menuKeys: [] }) as never,
    );
    rerender(
      <ConfigProvider>
        <AdapterHub />
      </ConfigProvider>,
    );
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
  });
});
