import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider, message } from "antd";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  useAdapterHubStatus,
  useAdvanceIntegrationOnboarding,
  useCheckAdapterHealth,
  useCreateAdapter,
  useCreateIntegrationOnboarding,
  useCreateWebhook,
  useGenerateDataQualityReport,
  useIntegrationDataContract,
  useIntegrationAdapters,
  useIntegrationLogs,
  useIntegrationOnboardings,
  useMasterDataReconciliation,
  useOrgUnits,
  useRegionalSources,
  useRegisterRegionalSource,
  useReplayDeadLetter,
  useRetryMessage,
  useSecurityProfile,
  useTestWebhookSignature,
  useUpdateAdapter,
  useWebhooks,
  type AdapterHubStatus,
  type DataQualityReport,
  type IntegrationDataContractResponse,
  type IntegrationAdapter,
  type IntegrationMessageLog,
  type IntegrationOnboarding,
  type IntegrationWebhookConfig,
  type MasterDataReconciliation,
  type RegionalSource,
  type SecurityProfile,
} from "@/shared/api/hooks";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import AdapterHub from "./AdapterHub";

const ADAPTER_INTERACTION_TIMEOUT_MS = 15_000;

vi.mock("@/shared/api/hooks", () => ({
  useAdapterHubStatus: vi.fn(),
  useAdvanceIntegrationOnboarding: vi.fn(),
  useCheckAdapterHealth: vi.fn(),
  useCreateAdapter: vi.fn(),
  useCreateIntegrationOnboarding: vi.fn(),
  useCreateWebhook: vi.fn(),
  useGenerateDataQualityReport: vi.fn(),
  useIntegrationDataContract: vi.fn(),
  useIntegrationAdapters: vi.fn(),
  useIntegrationLogs: vi.fn(),
  useIntegrationOnboardings: vi.fn(),
  useMasterDataReconciliation: vi.fn(),
  useOrgUnits: vi.fn(),
  useRegionalSources: vi.fn(),
  useRegisterRegionalSource: vi.fn(),
  useReplayDeadLetter: vi.fn(),
  useRetryMessage: vi.fn(),
  useSecurityProfile: vi.fn(),
  useTestWebhookSignature: vi.fn(),
  useUpdateAdapter: vi.fn(),
  useWebhooks: vi.fn(),
}));

const profile: SecurityProfile = {
  userId: "it-1",
  username: "it.owner",
  roles: [
    {
      code: "platform-admin",
      displayName: "信息科",
      source: "DEFAULT",
      scopeLevel: null,
      scopeCode: null,
    },
  ],
  mustChangePwd: false,
  mfaRequired: false,
  mfaBound: false,
  mfaVerified: true,
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
  requiredSources: [
    {
      sourceSystem: "HIS",
      label: "HIS 医院信息系统",
      adapterId: "his-main",
      adapterName: "HIS 主数据接入",
      protocolType: "REST",
      status: "BOUND",
      healthStatus: "NOT_CONNECTED",
      mappedFieldCount: 12,
      lastHeartbeatAt: "2026-06-03T08:00:00Z",
      ready: false,
      gaps: ["未连接真实外部系统"],
    },
    {
      sourceSystem: "EMR",
      label: "EMR 电子病历系统",
      adapterId: null,
      adapterName: null,
      protocolType: null,
      status: "MISSING",
      healthStatus: "NOT_CONNECTED",
      mappedFieldCount: 0,
      lastHeartbeatAt: null,
      ready: false,
      gaps: ["缺少 EMR 适配器"],
    },
    {
      sourceSystem: "LIS",
      label: "LIS 检验信息系统",
      adapterId: null,
      adapterName: null,
      protocolType: null,
      status: "MISSING",
      healthStatus: "NOT_CONNECTED",
      mappedFieldCount: 0,
      lastHeartbeatAt: null,
      ready: false,
      gaps: ["缺少 LIS 适配器"],
    },
  ],
};

const masterDataReconciliation: MasterDataReconciliation = {
  sourceSystem: "HIS",
  lastSuccessfulBatchId: "batch-20260614-001",
  cursor: "cursor-42",
  lastSyncedAt: "2026-06-14T00:30:00Z",
  resources: [
    { resourceType: "ORG_UNIT", activeCount: 24, disabledCount: 1 },
    { resourceType: "PERSON", activeCount: 860, disabledCount: 12 },
    { resourceType: "LOCAL_TERM", activeCount: 4_200, disabledCount: 18 },
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

const dataContract: IntegrationDataContractResponse = {
  contractId: "context-field-contract:runtime-H7",
  runtimeReleaseId: "runtime-H7",
  schemaVersion: "medkernel.context-field-contract.v1",
  accessGuide: ["字段要求由当前机构生效版本 runtime-H7 自动确定"],
  resources: {
    Patient: {
      resourceType: "Patient",
      payloadKey: "patient",
      array: false,
      jsonSchema: { type: "object", required: ["id"], properties: {} },
    },
  },
  fields: [
    {
      resourceType: "Patient",
      fieldPath: "patient.id",
      payloadKey: "patient",
      propertyName: "id",
      displayName: "患者标识",
      dataType: "string",
      jsonSchemaType: "string",
      unit: null,
      codeSystem: null,
      required: true,
      derived: false,
      externalWritable: true,
      description: "患者主索引标识",
    },
    {
      resourceType: "Patient",
      fieldPath: "patient.age",
      payloadKey: "patient",
      propertyName: "age",
      displayName: "年龄",
      dataType: "number",
      jsonSchemaType: "number",
      unit: "岁",
      codeSystem: null,
      required: false,
      derived: true,
      externalWritable: false,
      description: "由出生日期计算",
    },
  ],
};

const webhook: IntegrationWebhookConfig = {
  id: 21,
  webhookId: "clinical-events",
  name: "临床事件回调",
  callbackUrl: "https://his.example.test/medkernel/events",
  eventsSubscribed: "clinical.event.accepted",
  status: "ACTIVE",
  createdAt: "2026-06-03T08:00:00Z",
  updatedAt: "2026-06-03T08:00:00Z",
};

const regionalSource: RegionalSource = {
  sourceId: "regional-lab",
  regionalNetworkName: "区域检验互认平台",
  sourceOrganizationId: "hospital-2",
  sourceOrganizationName: "市二院",
  trustLevel: "HIGH",
  evidenceText: "区域平台签约清单与接口验收记录",
  adapterId: "his-main",
  onboardingId: "onb-his",
  orgPath: "集团/总院",
  status: "ACTIVE",
  createdAt: "2026-06-03T08:00:00Z",
  updatedAt: "2026-06-03T08:00:00Z",
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

function adapterPage(items: IntegrationAdapter[], total = items.length) {
  return {
    items,
    page: 1,
    size: 20,
    total,
    hasNext: total > items.length,
    totalEstimated: false,
  };
}

function pageData<T>(items: T[], total = items.length) {
  return {
    items,
    page: 1,
    size: 20,
    total,
    hasNext: total > items.length,
    totalEstimated: false,
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
  vi.mocked(useOrgUnits).mockReturnValue(
    query({
      items: [
        {
          id: "hospital-1",
          tenantId: "tenant-1",
          parentId: "tenant-root",
          orgPath: "/tenant-1/hospital-1",
          level: "FACILITY",
          facilityType: "HOSPITAL",
          code: "HOSP-1",
          name: "示范医院",
          status: "ACTIVE",
        },
      ],
      page: 1,
      size: 500,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    }) as never,
  );
  vi.mocked(useIntegrationAdapters).mockReturnValue(query(adapterPage([hisAdapter])) as never);
  vi.mocked(useAdapterHubStatus).mockReturnValue(query(status) as never);
  vi.mocked(useIntegrationDataContract).mockReturnValue(query(undefined) as never);
  vi.mocked(useIntegrationLogs).mockReturnValue(
    query({ items: [failedLog, deadLetterLog], total: 2 }) as never,
  );
  vi.mocked(useIntegrationOnboardings).mockReturnValue(query(pageData([onboarding])) as never);
  vi.mocked(useMasterDataReconciliation).mockReturnValue(query(masterDataReconciliation) as never);
  vi.mocked(useWebhooks).mockReturnValue(query(pageData([webhook])) as never);
  vi.mocked(useRegionalSources).mockReturnValue(query(pageData([regionalSource])) as never);
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
  vi.mocked(useCreateWebhook).mockReturnValue(
    mutation({ ...webhook, sharedSecret: "whsec_once_only" }) as never,
  );
  vi.mocked(useTestWebhookSignature).mockReturnValue(
    mutation({
      webhookId: webhook.webhookId,
      callbackUrl: webhook.callbackUrl,
      timestamp: 1780743600,
      signature: "sha256=preview-signature",
      status: "SIGNATURE_GENERATED",
      connectionStatus: "NOT_TESTED",
      message: "签名已在本地生成，未向外部地址发起请求。",
    }) as never,
  );
  vi.mocked(useRegisterRegionalSource).mockReturnValue(mutation(regionalSource) as never);
  vi.mocked(useAdvanceIntegrationOnboarding).mockReturnValue(
    mutation({ ...onboarding, status: "ONLINE" }) as never,
  );
}

function renderPage() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <AdapterHub />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("AdapterHub", () => {
  afterEach(async () => {
    await act(async () => {
      message.destroy();
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  });

  beforeEach(() => {
    window.localStorage.clear();
    useEvidenceDetailsStore.setState({ enabled: false });
    vi.clearAllMocks();
    setupMocks();
  });

  it("renders the unified adapter workspace without the old launch-token console", async () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "系统接入" })).toBeInTheDocument();
    expect(useIntegrationAdapters).toHaveBeenCalledWith({ page: 1, size: 20 });
    expect(screen.getAllByRole("button", { name: "新增适配器" })).toHaveLength(1);
    expect(screen.getAllByText("未接通").length).toBeGreaterThan(0);
    expect(screen.getByText("缺少检查报告时间映射")).toBeInTheDocument();
    expect(screen.getByText("死信重放")).toBeInTheDocument();
    expect(screen.getByText("数据质量看板")).toBeInTheDocument();
    expect(screen.getByText("接入向导")).toBeInTheDocument();
    expect(screen.getByText("回调通道")).toBeInTheDocument();
    expect(screen.getByText("区域来源")).toBeInTheDocument();
    expect(screen.getByText("必接系统清单")).toBeInTheDocument();
    expect(screen.getByText("HIS 医院信息系统")).toBeInTheDocument();
    expect(screen.getByText("EMR 电子病历系统")).toBeInTheDocument();
    expect(screen.getByText("LIS 检验信息系统")).toBeInTheDocument();
    expect(screen.getByText("缺少 EMR 适配器")).toBeInTheDocument();
    expect(screen.getByText("数据接入契约")).toBeInTheDocument();
    expect(screen.getByText("选模板/导入")).toBeInTheDocument();
    expect(screen.getAllByText("2026年06月03日 16:00").length).toBeGreaterThan(0);
    expect(screen.queryByText(/6\/3\/2026/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "数据质量看板" }));
    expect(screen.getByText("尚未生成本轮数据质量报告")).toBeInTheDocument();
    expect(screen.getByText(/当前服务机构的适配器、字段映射和探活事实/)).toBeInTheDocument();

    expect(screen.queryByText(/Webhook 回调订阅安全自研沙箱/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Launch Token/)).not.toBeInTheDocument();
  });

  it("exposes hospital master-data reconciliation in the ordinary integration workspace", async () => {
    render(<AdapterHub />);

    await userEvent.click(screen.getByRole("tab", { name: "主数据同步" }));
    await userEvent.type(screen.getByPlaceholderText("例如 HIS、LIS、HRP"), "HIS");
    await userEvent.click(screen.getByRole("button", { name: "查询对账" }));

    expect(useMasterDataReconciliation).toHaveBeenLastCalledWith("HIS", true);
    expect(screen.getByText("batch-20260614-001")).toBeInTheDocument();
    expect(screen.getByText("院内人员")).toBeInTheDocument();
    expect(screen.getByText("860")).toBeInTheDocument();
    expect(screen.getByText("2026年06月14日 08:30")).toBeInTheDocument();
  });

  it("does not load a package selector for the current runtime data contract", () => {
    renderPage();
  });

  it("loads adapter hub maintenance ledgers through small server-side pages", () => {
    renderPage();

    expect(useIntegrationOnboardings).toHaveBeenCalledWith({ page: 1, size: 20 });
    expect(useWebhooks).toHaveBeenCalledWith({ page: 1, size: 20 });
    expect(useRegionalSources).toHaveBeenCalledWith({ page: 1, size: 20 });
  });

  it("keeps repeated onboarding applications distinguishable by latest maintenance time", async () => {
    vi.mocked(useIntegrationOnboardings).mockReturnValue(
      query(
        pageData(
          Array.from({ length: 20 }, (_, index) => ({
            ...onboarding,
            onboardingId: `onb-frontdesk-${index + 1}`,
            name: "接入配置已登记",
            updatedAt: new Date(Date.UTC(2026, 5, 30 - index, 15, 18, 0)).toISOString(),
          })),
          24,
        ),
      ) as never,
    );

    renderPage();

    await userEvent.click(screen.getByRole("tab", { name: "接入向导" }));
    const bodyRows = screen.getAllByRole("row").slice(1);
    expect(within(bodyRows[0]).getByText("最近更新 2026年06月30日 23:18")).toBeInTheDocument();
    expect(screen.getByText("共 24 条接入申请，当前显示 1-20 条")).toBeInTheDocument();
  });

  it("loads the data contract summary from the current hospital runtime", () => {
    vi.mocked(useIntegrationDataContract).mockReturnValue(query(dataContract) as never);

    renderPage();

    expect(useIntegrationDataContract).toHaveBeenCalledWith(true);
    expect(screen.queryByRole("combobox", { name: "版本号" })).not.toBeInTheDocument();
    expect(screen.getByText("当前机构字段契约已生成")).toBeInTheDocument();
    expect(screen.queryByText("context-field-contract:runtime-H7")).not.toBeInTheDocument();
    expect(screen.getByText("资源 1 类")).toBeInTheDocument();
    expect(screen.getByText("字段 2 项")).toBeInTheDocument();
    expect(screen.getAllByText("patient.id").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("patient.age").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("必传")).toBeInTheDocument();
    expect(screen.getByText("派生不可写")).toBeInTheDocument();
  });

  it("does not request the current-hospital data contract outside hospital scope", () => {
    vi.mocked(useSecurityProfile).mockReturnValue(
      query({
        ...profile,
        dataScope: {
          ...profile.dataScope,
          hospitalId: null,
        },
      }) as never,
    );

    renderPage();

    expect(useIntegrationDataContract).toHaveBeenCalledWith(false);
    expect(screen.getByText("请先切换到具体医院后查看当前生效版本字段要求。")).toBeInTheDocument();
  });

  it("keeps required source and data contract configuration visible before any adapter is created", () => {
    vi.mocked(useIntegrationAdapters).mockReturnValue(query(adapterPage([])) as never);
    vi.mocked(useAdapterHubStatus).mockReturnValue(
      query({
        ...status,
        totalAdapters: 0,
        healthyAdapters: 0,
        notConnectedAdapters: 0,
        misconfiguredAdapters: 0,
        mappedAdapters: 0,
        sources: [],
      }) as never,
    );
    vi.mocked(useIntegrationLogs).mockReturnValue(query({ items: [], total: 0 }) as never);
    vi.mocked(useIntegrationOnboardings).mockReturnValue(query(pageData([])) as never);

    renderPage();

    expect(screen.getByRole("heading", { name: "系统接入" })).toBeInTheDocument();
    expect(screen.getByText("必接系统清单")).toBeInTheDocument();
    expect(screen.getByText("HIS 医院信息系统")).toBeInTheDocument();
    expect(screen.getByText("EMR 电子病历系统")).toBeInTheDocument();
    expect(screen.getByText("LIS 检验信息系统")).toBeInTheDocument();
    expect(screen.getByText("数据接入契约")).toBeInTheDocument();
    expect(screen.queryByText("暂无适配器接入记录")).not.toBeInTheDocument();
  });

  it("默认展示业务接入摘要，证据详情打开后才显示低频技术标识", async () => {
    vi.mocked(useIntegrationDataContract).mockReturnValue(query(dataContract) as never);
    const user = userEvent.setup();
    renderPage();

    expect(screen.getAllByText("HIS 主数据接入").length).toBeGreaterThan(0);
    expect(screen.getByText("适配器已登记")).toBeInTheDocument();
    expect(screen.getByText("当前机构字段契约已生成")).toBeInTheDocument();
    expect(screen.getByText("患者标识")).toBeInTheDocument();
    expect(screen.getByText("接入要求")).toBeInTheDocument();
    expect(screen.queryByText("字段结构")).not.toBeInTheDocument();
    expect(screen.queryByText(/his-main/)).not.toBeInTheDocument();
    expect(screen.queryByText(/context-field-contract:runtime-H7/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "生成质量报告" }));
    expect(await screen.findByText("数据质量报告已生成")).toBeInTheDocument();
    expect(screen.getByText("追踪证据已记录")).toBeInTheDocument();
    expect(screen.queryByText(/dqr-1/)).not.toBeInTheDocument();
    expect(screen.queryByText(/trace-dqr/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "死信重放" }));
    expect(screen.getAllByText("消息证据已记录").length).toBeGreaterThan(0);
    expect(screen.getAllByText("追踪证据已记录").length).toBeGreaterThan(0);
    expect(screen.queryByText(/msg-failed/)).not.toBeInTheDocument();
    expect(screen.queryByText(/trace-failed/)).not.toBeInTheDocument();
    expect(screen.queryByText(/msg-dead/)).not.toBeInTheDocument();
    expect(screen.queryByText(/trace-dead/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "接入向导" }));
    expect(screen.getByText("接入申请已登记")).toBeInTheDocument();
    expect(screen.queryByText(/onb-his/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "回调通道" }));
    expect(screen.getByText("回调通道已登记")).toBeInTheDocument();
    expect(screen.getByText("回调地址已配置")).toBeInTheDocument();
    expect(screen.getAllByText("2026年06月03日 16:00").length).toBeGreaterThan(0);
    expect(screen.queryByText(/clinical-events/)).not.toBeInTheDocument();
    expect(screen.queryByText(webhook.callbackUrl)).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "区域来源" }));
    expect(screen.getByText("来源已登记")).toBeInTheDocument();
    expect(screen.getByText("适配器：已绑定")).toBeInTheDocument();
    expect(screen.getByText("接入申请：已绑定")).toBeInTheDocument();
    expect(screen.queryByText("来源编号：regional-lab")).not.toBeInTheDocument();
    expect(screen.queryByText("适配器：his-main")).not.toBeInTheDocument();
    expect(screen.queryByText("接入申请：onb-his")).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));
    expect(await screen.findByText("来源编号：regional-lab")).toBeInTheDocument();
    expect(screen.getByText("适配器：his-main")).toBeInTheDocument();
    expect(screen.getByText("接入申请：onb-his")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "回调通道" }));
    expect(screen.getByText("clinical-events")).toBeInTheDocument();
    expect(screen.getByText(webhook.callbackUrl)).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "死信重放" }));
    expect(screen.getByText("msg-failed")).toBeInTheDocument();
    expect(screen.getByText(/trace-failed/)).toBeInTheDocument();
  }, 15_000);

  it("uses stable business identity labels for adapter setup", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "新增适配器" }));
    expect(screen.getByLabelText("稳定适配器身份")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("输入稳定适配器身份")).toBeInTheDocument();
    expect(screen.queryByLabelText("适配器标识")).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("输入真实适配器标识")).not.toBeInTheDocument();
  });

  it("uses stable business identity labels for onboarding setup", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "接入向导" }));
    await user.click(screen.getByRole("button", { name: "新增接入申请" }));
    expect(screen.getByLabelText("稳定接入申请身份")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("输入稳定接入申请身份")).toBeInTheDocument();
    expect(screen.queryByLabelText("接入申请标识")).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("输入真实接入申请标识")).not.toBeInTheDocument();
    expect(screen.getByLabelText("绑定适配器")).toBeInTheDocument();
    expect(screen.queryByLabelText("绑定适配器标识")).not.toBeInTheDocument();
    expect(screen.getByLabelText("回调通道")).toBeInTheDocument();
    expect(screen.queryByLabelText("回调通道标识")).not.toBeInTheDocument();
  });

  it("uses stable business identity labels for callback setup", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "回调通道" }));
    await user.click(screen.getByRole("button", { name: "新增回调通道" }));
    expect(screen.getByLabelText("稳定回调通道身份")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("例如 clinical-events")).toBeInTheDocument();
    expect(screen.queryByLabelText("回调标识")).not.toBeInTheDocument();
  });

  it(
    "creates adapters with the service protocol contract and structured field mappings",
    async () => {
      const user = userEvent.setup();
      const createAdapter = mutation(hisAdapter);
      vi.mocked(useCreateAdapter).mockReturnValue(createAdapter as never);

      renderPage();
      await user.click(screen.getByRole("button", { name: "新增适配器" }));

      expect(screen.queryByLabelText("连接与字段映射配置")).not.toBeInTheDocument();
      fireEvent.change(screen.getByLabelText("稳定适配器身份"), {
        target: { value: "his-outpatient" },
      });
      fireEvent.change(screen.getByLabelText("系统名称"), {
        target: { value: "门诊 HIS" },
      });
      fireEvent.change(screen.getByLabelText("服务地址"), {
        target: { value: "https://his.example.test/api" },
      });
      fireEvent.change(screen.getByLabelText("来源字段路径"), {
        target: { value: "/patient/id" },
      });
      fireEvent.change(screen.getByLabelText("标准字段路径"), {
        target: { value: "/patient/id" },
      });
      fireEvent.change(screen.getByLabelText("目标标准字典"), {
        target: { value: "ICD-10" },
      });
      await user.click(screen.getByRole("combobox", { name: "术语分类" }));
      await user.click(await screen.findByText("诊断"));
      await user.click(screen.getByRole("button", { name: "提交适配器" }));

      await waitFor(() => {
        expect(createAdapter.mutateAsync).toHaveBeenCalledWith({
          adapterId: "his-outpatient",
          name: "门诊 HIS",
          protocolType: "REST",
          configJson: JSON.stringify({
            baseUrl: "https://his.example.test/api",
            healthPath: "/health",
            outboundPath: "/messages",
            connectTimeoutMs: 2000,
            requestTimeoutMs: 5000,
            fieldMappings: [
              {
                sourcePath: "/patient/id",
                targetPath: "/patient/id",
                targetDictionaryKey: "ICD-10",
                category: "DIAGNOSIS",
              },
            ],
          }),
        });
      });
    },
    ADAPTER_INTERACTION_TIMEOUT_MS,
  );

  it("creates a callback with one-time secret and previews signatures without claiming connectivity", async () => {
    const user = userEvent.setup();
    const createWebhook = mutation({ ...webhook, sharedSecret: "whsec_once_only" });
    const testSignature = mutation({
      webhookId: webhook.webhookId,
      callbackUrl: webhook.callbackUrl,
      timestamp: 1780743600,
      signature: "sha256=preview-signature",
      status: "SIGNATURE_GENERATED",
      connectionStatus: "NOT_TESTED",
      message: "签名已在本地生成，未向外部地址发起请求。",
    });
    vi.mocked(useCreateWebhook).mockReturnValue(createWebhook as never);
    vi.mocked(useTestWebhookSignature).mockReturnValue(testSignature as never);

    renderPage();
    await user.click(screen.getByRole("tab", { name: "回调通道" }));
    expect(screen.getByText("回调地址已配置")).toBeInTheDocument();
    expect(screen.queryByText(webhook.callbackUrl)).not.toBeInTheDocument();
    expect(screen.queryByText(/whsec_/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "新增回调通道" }));
    expect(screen.getByLabelText("稳定回调通道身份")).toBeInTheDocument();
    expect(screen.queryByLabelText("回调标识")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("稳定回调通道身份"), {
      target: { value: "quality-events" },
    });
    fireEvent.change(screen.getByLabelText("通道名称"), {
      target: { value: "质控事件回调" },
    });
    fireEvent.change(screen.getByLabelText("回调地址"), {
      target: { value: "https://quality.example.test/medkernel/events" },
    });
    fireEvent.change(screen.getByLabelText("订阅事件"), {
      target: { value: "quality.alert.opened" },
    });
    await user.click(screen.getByRole("button", { name: "创建回调通道" }));

    await waitFor(() =>
      expect(createWebhook.mutateAsync).toHaveBeenCalledWith({
        webhookId: "quality-events",
        name: "质控事件回调",
        callbackUrl: "https://quality.example.test/medkernel/events",
        eventsSubscribed: "quality.alert.opened",
      }),
    );
    expect(await screen.findByText("whsec_once_only")).toBeInTheDocument();
    expect(screen.getByText(/仅显示一次/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "我已安全保存" }));
    expect(screen.queryByLabelText("签名预览载荷")).not.toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: "预览事件" }));
    await user.click(
      await screen.findByText("临床事件联调", { selector: ".ant-select-item-option-content" }),
    );
    fireEvent.change(screen.getByLabelText("样例患者（非真实）"), {
      target: { value: "联调患者（非真实）" },
    });
    fireEvent.change(screen.getByLabelText("样例就诊（非真实）"), {
      target: { value: "门诊联调就诊" },
    });
    fireEvent.change(screen.getByLabelText("事件摘要"), {
      target: { value: "签名预览联调事件" },
    });
    await user.click(screen.getByRole("button", { name: "生成签名预览" }));

    expect(testSignature.mutateAsync).toHaveBeenCalledWith({
      webhookId: "clinical-events",
      payload: JSON.stringify({
        event: "clinical.test",
        patient: "联调患者（非真实）",
        encounter: "门诊联调就诊",
        summary: "签名预览联调事件",
      }),
    });
    expect(await screen.findByText("签名已在本地生成，未向外部地址发起请求。")).toBeInTheDocument();
    expect(screen.getByText("sha256=preview-signature")).toBeInTheDocument();
  }, 15_000);

  it("registers a graded regional source instead of leaving the service-only done item unusable", async () => {
    const user = userEvent.setup();
    const registerSource = mutation(regionalSource);
    vi.mocked(useRegisterRegionalSource).mockReturnValue(registerSource as never);

    renderPage();
    await user.click(screen.getByRole("tab", { name: "区域来源" }));
    expect(screen.getByText("区域检验互认平台")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "登记区域来源" }));
    expect(screen.getByLabelText("稳定来源身份")).toBeInTheDocument();
    expect(screen.queryByLabelText("来源标识")).not.toBeInTheDocument();
    expect(screen.getByLabelText("来源机构身份")).toBeInTheDocument();
    expect(screen.queryByLabelText("来源机构标识")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("稳定来源身份"), {
      target: { value: "regional-image" },
    });
    fireEvent.change(screen.getByLabelText("区域网络"), {
      target: { value: "区域影像互认平台" },
    });
    fireEvent.change(screen.getByLabelText("来源机构身份"), {
      target: { value: "hospital-3" },
    });
    fireEvent.change(screen.getByLabelText("来源机构名称"), {
      target: { value: "市三院" },
    });
    await user.click(screen.getByLabelText("可信等级"));
    await user.click(screen.getByTitle("中可信"));
    fireEvent.change(screen.getByLabelText("可信证据"), {
      target: { value: "区域互认协议与接口验收单" },
    });
    await user.click(screen.getByLabelText("组织范围"));
    await user.click(await screen.findByText("示范医院 · 医疗服务机构"));
    await user.click(screen.getByRole("button", { name: "保存区域来源" }));

    await waitFor(() => {
      expect(registerSource.mutateAsync).toHaveBeenCalledWith({
        sourceId: "regional-image",
        regionalNetworkName: "区域影像互认平台",
        sourceOrganizationId: "hospital-3",
        sourceOrganizationName: "市三院",
        trustLevel: "MEDIUM",
        evidenceText: "区域互认协议与接口验收单",
        orgPath: "/tenant-1/hospital-1",
      });
    });
  }, 15_000);

  it("checks adapter health and keeps disconnected adapters honest as NOT_CONNECTED", async () => {
    const user = userEvent.setup();
    const health = mutation({ ...hisAdapter, healthStatus: "NOT_CONNECTED", rttMs: 0 });
    vi.mocked(useCheckAdapterHealth).mockReturnValue(health as never);

    renderPage();

    await user.click(screen.getByRole("button", { name: "健康诊断" }));

    expect(health.mutateAsync).toHaveBeenCalledWith("his-main");
    expect(await screen.findByText("外部系统当前不可达")).toBeInTheDocument();
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

    const failedRow = screen.getByText("外部系统断连").closest("tr");
    const deadRow = screen.getByText("重试超限").closest("tr");
    expect(failedRow).not.toBeNull();
    expect(deadRow).not.toBeNull();
    expect(screen.queryByText("msg-failed")).not.toBeInTheDocument();
    expect(screen.queryByText("msg-dead")).not.toBeInTheDocument();

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

  it("renders guarded page states from real query status while keeping zero-record setup actionable", () => {
    vi.mocked(useIntegrationAdapters).mockReturnValue(
      query(adapterPage([]), { isLoading: true }) as never,
    );
    const { rerender } = renderPage();
    expect(screen.getByText("正在加载系统接入")).toBeInTheDocument();

    vi.mocked(useIntegrationAdapters).mockReturnValue(query(adapterPage([])) as never);
    vi.mocked(useAdapterHubStatus).mockReturnValue(query({ ...status, totalAdapters: 0 }) as never);
    vi.mocked(useIntegrationLogs).mockReturnValue(query({ items: [], total: 0 }) as never);
    vi.mocked(useIntegrationOnboardings).mockReturnValue(query(pageData([])) as never);
    rerender(
      <ConfigProvider>
        <AdapterHub />
      </ConfigProvider>,
    );
    expect(screen.getByText("必接系统清单")).toBeInTheDocument();
    expect(screen.getByText("数据接入契约")).toBeInTheDocument();
    expect(screen.queryByText("暂无适配器接入记录")).not.toBeInTheDocument();

    vi.mocked(useIntegrationAdapters).mockReturnValue(
      query(adapterPage([]), { isError: true, error: new Error("boom") }) as never,
    );
    rerender(
      <ConfigProvider>
        <AdapterHub />
      </ConfigProvider>,
    );
    expect(screen.getByText("系统接入暂时不可用")).toBeInTheDocument();

    vi.mocked(useIntegrationAdapters).mockReturnValue(query(adapterPage([hisAdapter])) as never);
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
