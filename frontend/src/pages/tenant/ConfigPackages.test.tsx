import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ConfigPackages from "./ConfigPackages";
import {
  downloadPackageOfflineExport,
  downloadPackageSyncEvidenceExport,
} from "@/shared/api/hooks";

const PAGE_INTERACTION_TIMEOUT_MS = 15_000;

const apiMocks = vi.hoisted(() => ({
  downloadPackageOfflineExport: vi.fn(),
  downloadPackageSyncEvidenceExport: vi.fn(),
  importOfflinePackage: vi.fn(),
  createPackage: vi.fn(),
  addPackageItem: vi.fn(),
  instantiatePilotTemplate: vi.fn(),
  releasePackage: vi.fn(),
  refetchPackages: vi.fn(),
  refetchPackageDetail: vi.fn(),
  refetchAssetReadiness: vi.fn(),
  useRuleDefinitions: vi.fn(),
  usePathwayTemplates: vi.fn(),
  useEvaluationIndicators: vi.fn(),
  packagesData: null as Record<string, unknown> | null,
  packagesLoading: false,
  packagesError: false,
  pilotTemplates: [] as Array<Record<string, unknown>>,
  pilotTemplatesLoading: false,
  assetReadiness: null as Record<string, unknown> | null,
  assetReadinessLoading: false,
  assetReadinessError: false,
  releaseAdapters: [] as Array<Record<string, unknown>>,
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({
    data: {
      userId: "implementation-1",
      username: "implementation.engineer",
      roles: [
        {
          code: "implementation-engineer",
          displayName: "实施工程师",
          source: "tenant",
          scopeLevel: "HOSPITAL",
          scopeCode: "hospital-1",
        },
      ],
      permissions: [],
      menuKeys: [],
      environmentKeys: [],
      dataScope: {
        tenantId: "tenant-A",
        groupId: "group-1",
        hospitalId: "hospital-1",
        campusId: null,
        siteId: null,
        departmentId: null,
        specialtyId: null,
      },
      mustChangePwd: false,
      mfaRequired: false,
      mfaBound: true,
    },
  }),
  usePackageReleaseAdapters: () => ({
    data: apiMocks.releaseAdapters,
  }),
  useOrgUnits: () => ({
    data: {
      items: [
        {
          id: "hospital-1",
          tenantId: "tenant-A",
          orgPath: "tenant-A/hospital-1",
          level: "HOSPITAL",
          code: "HOSPITAL-1",
          name: "总院",
          status: "ACTIVE",
        },
      ],
      page: 1,
      size: 100,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    },
    isLoading: false,
  }),
  useCreatePackage: () => ({ mutateAsync: apiMocks.createPackage, isPending: false }),
  usePackages: () => ({
    data: apiMocks.packagesData ?? {
      items: [
        {
          packageId: "pkg-offline",
          tenantId: "tenant-A",
          packageCode: "PKG.TEST",
          packageVersion: "3.0.0",
          name: "通用配置包",
          description: "真实资产集合",
          status: "DRAFT",
          createdAt: "2026-06-01T00:00:00Z",
          createdBy: "tester",
          updatedAt: "2026-06-01T00:00:00Z",
          updatedBy: "tester",
          traceId: "trace-test",
        },
      ],
      page: 1,
      size: 10,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    },
    isLoading: apiMocks.packagesLoading,
    isError: apiMocks.packagesError,
    refetch: apiMocks.refetchPackages,
  }),
  usePackageDetail: () => ({
    data: { items: [] },
    refetch: apiMocks.refetchPackageDetail,
  }),
  useAddPackageItem: () => ({ mutateAsync: apiMocks.addPackageItem, isPending: false }),
  useCalculateDiff: () => ({ data: null }),
  useSyncPackage: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useReleasePackage: () => ({ mutateAsync: apiMocks.releasePackage, isPending: false }),
  usePackageSyncLogs: () => ({ data: [] }),
  useRollbackPackage: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useImportOfflinePackage: () => ({ mutateAsync: apiMocks.importOfflinePackage, isPending: false }),
  usePilotPackageTemplates: () => ({
    data: apiMocks.pilotTemplates,
    isLoading: apiMocks.pilotTemplatesLoading,
  }),
  usePackageAssetReadiness: () => ({
    data: apiMocks.assetReadiness,
    refetch: apiMocks.refetchAssetReadiness,
    isLoading: apiMocks.assetReadinessLoading,
    isError: apiMocks.assetReadinessError,
  }),
  useInstantiatePilotTemplate: () => ({
    mutateAsync: apiMocks.instantiatePilotTemplate,
    isPending: false,
  }),
  useRuleDefinitions: (...args: unknown[]) => apiMocks.useRuleDefinitions(...args),
  usePathwayTemplates: (...args: unknown[]) => apiMocks.usePathwayTemplates(...args),
  useEvaluationIndicators: (...args: unknown[]) => apiMocks.useEvaluationIndicators(...args),
  useTerminologyPackages: () => ({
    data: {
      items: [
        {
          id: 301,
          tenantId: "tenant-A",
          packageCode: "TERM.LAB",
          packageVersion: "2026.06",
          displayName: "检验术语映射包",
          scopeLevel: "DEPARTMENT",
          scopeCode: "CARD",
          status: "PUBLISHED",
          mappingCount: 1,
          contentHash: "b".repeat(64),
          createdAt: "2026-06-01T00:00:00Z",
          createdBy: "tester",
          updatedAt: "2026-06-01T00:00:00Z",
          updatedBy: "tester",
        },
      ],
    },
  }),
  downloadPackageDiffExport: vi.fn(),
  downloadPackageOfflineExport: apiMocks.downloadPackageOfflineExport,
  downloadPackageSyncEvidenceExport: apiMocks.downloadPackageSyncEvidenceExport,
}));

describe("ConfigPackages offline package export", () => {
  beforeEach(() => {
    apiMocks.downloadPackageOfflineExport.mockReset();
    apiMocks.downloadPackageOfflineExport.mockResolvedValue(new Blob(["offline-package"]));
    apiMocks.downloadPackageSyncEvidenceExport.mockReset();
    apiMocks.downloadPackageSyncEvidenceExport.mockResolvedValue(new Blob(["sync-evidence"]));
    apiMocks.importOfflinePackage.mockReset();
    apiMocks.createPackage.mockReset();
    apiMocks.addPackageItem.mockReset();
    apiMocks.instantiatePilotTemplate.mockReset();
    apiMocks.refetchAssetReadiness.mockReset();
    apiMocks.useRuleDefinitions.mockReset();
    apiMocks.useRuleDefinitions.mockReturnValue({ data: { items: [] } });
    apiMocks.usePathwayTemplates.mockReset();
    apiMocks.usePathwayTemplates.mockReturnValue({ data: { items: [] } });
    apiMocks.useEvaluationIndicators.mockReset();
    apiMocks.useEvaluationIndicators.mockReturnValue({ data: { items: [] } });
    apiMocks.packagesData = null;
    apiMocks.packagesLoading = false;
    apiMocks.packagesError = false;
    apiMocks.pilotTemplatesLoading = false;
    apiMocks.assetReadinessLoading = false;
    apiMocks.assetReadinessError = false;
    apiMocks.releaseAdapters = [
      {
        adapterId: "target-his",
        adapterName: "院内 HIS 同步通道",
        protocolType: "REST",
        status: "ACTIVE",
        healthStatus: "HEALTHY",
        rttMs: 8,
        lastHeartbeatAt: "2026-06-07T00:00:00Z",
        connectorAvailable: true,
      },
      {
        adapterId: "target-graph",
        adapterName: "图谱同步通道",
        protocolType: "REST",
        status: "ACTIVE",
        healthStatus: "HEALTHY",
        rttMs: 12,
        lastHeartbeatAt: "2026-06-07T00:00:00Z",
        connectorAvailable: true,
      },
    ];
    apiMocks.pilotTemplates = [
      {
        templateId: "tpl-first-run",
        templateCode: "TPL.FIRST_RUN",
        tenantId: "t-1",
        name: "COPD 首发模板",
        description: "包含知识、术语、规则、路径四类必需资产",
        packageCodePrefix: "PILOT.COPD",
        defaultPackageVersion: "2026.06.01",
        itemCount: 4,
        items: [
          {
            assetType: "KNOWLEDGE",
            assetId: "KN.COPD",
            assetVersion: "2026.06",
            required: true,
            sortOrder: 0,
            dependencyNote: "首发知识库",
          },
          {
            assetType: "TERMINOLOGY",
            assetId: "TERM.LAB|DEPARTMENT|CARD",
            assetVersion: "2026.06",
            required: true,
            sortOrder: 1,
            dependencyNote: "术语映射包",
          },
          {
            assetType: "RULE",
            assetId: "RULE.COPD",
            assetVersion: "2026.06",
            required: true,
            sortOrder: 2,
            dependencyNote: "规则版本",
          },
          {
            assetType: "PATHWAY",
            assetId: "PATH.COPD",
            assetVersion: "2026.06",
            required: true,
            sortOrder: 3,
            dependencyNote: "路径模板",
          },
        ],
      },
    ];
    apiMocks.assetReadiness = {
      tenantId: "tenant-A",
      ready: true,
      templateCount: 1,
      draftPackageCount: 1,
      releasedPackageCount: 1,
      activePackageCount: 0,
      grayscaleReady: true,
      readyPackageId: "pkg-offline",
      blockers: [],
      checkedAt: "2026-06-03T00:00:00Z",
    };
    apiMocks.addPackageItem.mockResolvedValue({
      itemId: "item-term",
      packageId: "pkg-offline",
      assetType: "TERMINOLOGY",
      assetId: "TERM.LAB|DEPARTMENT|CARD",
      assetVersion: "2026.06",
    });
    apiMocks.importOfflinePackage.mockResolvedValue({
      packageId: "pkg-imported",
      packageCode: "PKG.IMPORT",
      packageVersion: "2026.06.01",
      status: "DRAFT",
      itemCount: 2,
      payloadSha256: "a".repeat(64),
    });
    apiMocks.createPackage.mockResolvedValue({
      packageId: "pkg-created",
      packageCode: "PKG.CARDIOLOGY",
      packageVersion: "1.0.0",
      status: "DRAFT",
    });
    apiMocks.instantiatePilotTemplate.mockResolvedValue({
      templateCode: "TPL.FIRST_RUN",
      packageInfo: {
        packageId: "pkg-first-run",
        packageCode: "PILOT.COPD.001",
        packageVersion: "2026.06.03",
        status: "DRAFT",
      },
      items: [],
    });
    apiMocks.releasePackage.mockReset();
    apiMocks.releasePackage.mockResolvedValue({
      status: "NOT_SYNCED",
      logs: [
        {
          logId: "log-1",
          planId: "plan-1",
          adapterId: "target-his",
          status: "NOT_SYNCED",
          errorMessage: "未接入真实同步通道",
          syncEvidence: "",
          createdAt: "2026-06-01T00:00:00Z",
        },
      ],
    });
    Object.defineProperty(window.URL, "createObjectURL", {
      configurable: true,
      value: vi.fn(() => "blob:offline-package"),
    });
    Object.defineProperty(window.URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  });

  it(
    "exports an effective offline package for a target organization",
    async () => {
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      await userEvent.click(screen.getByRole("button", { name: "导出离线包" }));
      fireEvent.mouseDown(screen.getByLabelText("接收组织单元"));
      await userEvent.click(await screen.findByText("总院 · HOSPITAL · HOSPITAL-1"));
      await userEvent.click(screen.getByRole("button", { name: "导出有效快照" }));

      await waitFor(() => {
        expect(downloadPackageOfflineExport).toHaveBeenCalledWith("pkg-offline", "hospital-1");
      });
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it("renders the real 7-step release flow and keeps publish as the only page primary action", () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.getByText("选模板/导入")).toBeInTheDocument();
    expect(screen.getAllByText("自动校验").length).toBeGreaterThan(0);
    expect(screen.getAllByText("看影响").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "发布配置包" })).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: /发布配置包|从首发模板创建|一键创建/ }),
    ).toHaveLength(2);
  });

  it("uses PageShell loading, error and empty states for package readiness data", () => {
    apiMocks.packagesLoading = true;

    const { rerender } = render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.getByText("正在加载配置包中心")).toBeInTheDocument();

    apiMocks.packagesLoading = false;
    apiMocks.packagesError = true;
    rerender(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.getByText("配置包中心读取失败")).toBeInTheDocument();

    apiMocks.packagesError = false;
    apiMocks.packagesData = {
      items: [],
      page: 1,
      size: 10,
      total: 0,
      hasNext: false,
      totalEstimated: false,
    };
    rerender(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.getByText("暂无配置包")).toBeInTheDocument();
  });

  it("uses the backend page total for the cumulative package statistic", async () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    const cumulativeStatistic = screen.getByText("总配置包版本 (累计)").closest(".ant-statistic");
    expect(cumulativeStatistic).not.toBeNull();
    expect(within(cumulativeStatistic as HTMLElement).getByText("1")).toBeInTheDocument();
  });

  it("does not enable optional source asset queries when the package operator lacks source permissions", () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(apiMocks.useRuleDefinitions).toHaveBeenCalledWith({ size: 100 }, { enabled: false });
    expect(apiMocks.usePathwayTemplates).toHaveBeenCalledWith({ size: 100 }, { enabled: false });
    expect(apiMocks.useEvaluationIndicators).toHaveBeenCalledWith(
      { size: 100 },
      { enabled: false },
    );
  });

  it(
    "shows asset readiness and instantiates a draft package from the first-run template",
    async () => {
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      expect(screen.getByText("首发资产准备")).toBeInTheDocument();
      expect(screen.getByText(/模板 1 个/)).toBeInTheDocument();

      await userEvent.click(screen.getByRole("button", { name: "从首发模板创建" }));

      expect(screen.getByText("COPD 首发模板")).toBeInTheDocument();
      expect(screen.getByText(/KNOWLEDGE/)).toBeInTheDocument();

      fireEvent.change(screen.getByLabelText("配置包编码"), {
        target: { value: "PILOT.COPD.001" },
      });
      fireEvent.change(screen.getByLabelText("配置包版本"), {
        target: { value: "2026.06.03" },
      });
      fireEvent.change(screen.getByLabelText("配置包名称"), {
        target: { value: "COPD 首发配置包" },
      });
      fireEvent.change(screen.getByLabelText("说明"), {
        target: { value: "从真实首发模板生成" },
      });

      await userEvent.click(screen.getByRole("button", { name: "生成配置包草案" }));

      await waitFor(() => {
        expect(apiMocks.instantiatePilotTemplate).toHaveBeenCalledWith({
          templateCode: "TPL.FIRST_RUN",
          request: {
            packageCode: "PILOT.COPD.001",
            packageVersion: "2026.06.03",
            name: "COPD 首发配置包",
            description: "从真实首发模板生成",
          },
        });
      });
      expect(apiMocks.refetchPackages).toHaveBeenCalled();
      expect(apiMocks.refetchAssetReadiness).toHaveBeenCalled();
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it("在没有首发模板时仍允许从空白草案启动配置包主流程", async () => {
    apiMocks.packagesData = {
      items: [],
      page: 1,
      size: 10,
      total: 0,
      hasNext: false,
      totalEstimated: false,
    };
    apiMocks.pilotTemplates = [];
    apiMocks.assetReadiness = {
      tenantId: "tenant-A",
      ready: false,
      templateCount: 0,
      draftPackageCount: 0,
      releasedPackageCount: 0,
      activePackageCount: 0,
      grayscaleReady: false,
      readyPackageId: null,
      blockers: ["尚未配置首发模板", "尚未完成灰度发布证据"],
      checkedAt: "2026-06-03T00:00:00Z",
    };

    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.queryByRole("button", { name: "从首发模板创建" })).not.toBeInTheDocument();
    expect(screen.getByText("尚未配置首发模板")).toBeInTheDocument();
    expect(screen.getByText("尚未完成灰度发布证据")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "导入离线包" })).toBeEnabled();
    await userEvent.click(screen.getByRole("button", { name: "新建配置包草案" }));
    const createDialog = screen
      .getByRole("button", { name: "提交创建草案" })
      .closest('[role="dialog"]') as HTMLElement | null;
    expect(createDialog).not.toBeNull();
    if (!createDialog) {
      throw new Error("未找到配置包创建对话框");
    }
    fireEvent.change(within(createDialog).getByLabelText("配置包编码"), {
      target: { value: "PKG.CARDIOLOGY" },
    });
    fireEvent.change(within(createDialog).getByLabelText("配置包版本"), {
      target: { value: "1.0.0" },
    });
    fireEvent.change(within(createDialog).getByLabelText("配置包名称"), {
      target: { value: "心血管配置包" },
    });
    await userEvent.click(within(createDialog).getByRole("button", { name: "提交创建草案" }));

    await waitFor(() => {
      expect(apiMocks.createPackage).toHaveBeenCalledWith({
        packageCode: "PKG.CARDIOLOGY",
        packageVersion: "1.0.0",
        name: "心血管配置包",
        description: undefined,
      });
    });
    expect(apiMocks.refetchPackages).toHaveBeenCalled();
  });

  it(
    "offers a clear offline package import flow",
    async () => {
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      await userEvent.click(screen.getByRole("button", { name: "导入离线包" }));
      expect(screen.queryByLabelText("离线包 JSON")).not.toBeInTheDocument();
      const offlineJson = JSON.stringify({
        format: "MEDKERNEL_PACKAGE_OFFLINE_V2",
        manifest: {
          packageCode: "PKG.OFFLINE",
          packageVersion: "1.0.0",
          itemCount: 3,
        },
      });
      const fileInput = document.querySelector<HTMLInputElement>('input[type="file"]');
      expect(fileInput).not.toBeNull();
      await userEvent.upload(
        fileInput as HTMLInputElement,
        new File([offlineJson], "package-offline.json", { type: "application/json" }),
      );
      expect(await screen.findByText("package-offline.json")).toBeInTheDocument();
      expect(screen.getByText("PKG.OFFLINE / 1.0.0")).toBeInTheDocument();
      await userEvent.click(screen.getByRole("button", { name: "导入并校验" }));

      await waitFor(() => {
        expect(apiMocks.importOfflinePackage).toHaveBeenCalledWith(offlineJson);
      });
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "uses clear in-hospital release wording and removes old projection copy",
    async () => {
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      expect(screen.queryByText(/物理投影|物理长链接|物理同步投影/)).not.toBeInTheDocument();

      await userEvent.click(screen.getByRole("button", { name: "院内同步发布" }));

      expect(screen.getByText("院内同步发布中心")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /开始同步发布/ })).toBeInTheDocument();
      expect(screen.queryByText(/物理投影|物理长链接|物理同步投影/)).not.toBeInTheDocument();
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "defaults package release to grayscale rollout instead of direct full rollout",
    async () => {
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      await userEvent.click(screen.getByRole("button", { name: "院内同步发布" }));

      expect(screen.getByLabelText("灰度发布 (GRAYSCALE)")).toBeChecked();
      expect(screen.getByLabelText("全量发布 (FULL)")).toBeDisabled();
      expect(screen.getByText(/默认按接收组织内 10% 床位进入灰度/)).toBeInTheDocument();
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "blocks release and links to adapter hub when no healthy connector is available",
    async () => {
      apiMocks.releaseAdapters = [
        {
          adapterId: "target-offline",
          adapterName: "未连通通道",
          protocolType: "REST",
          status: "ACTIVE",
          healthStatus: "NOT_CONNECTED",
          rttMs: 0,
          lastHeartbeatAt: null,
          connectorAvailable: true,
        },
      ];
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      await userEvent.click(screen.getByRole("button", { name: "院内同步发布" }));

      expect(screen.getByText("暂无可用同步适配器")).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "前往适配器中心" })).toHaveAttribute(
        "href",
        "/adapter/hub",
      );
      expect(screen.getByRole("button", { name: /开始同步发布/ })).toBeDisabled();
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "submits the default release request as grayscale rollout",
    async () => {
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      await userEvent.click(screen.getByRole("button", { name: "院内同步发布" }));

      fireEvent.mouseDown(screen.getByLabelText("选择发布适配器"));
      await userEvent.click(await screen.findByText(/院内 HIS 同步通道/));
      fireEvent.mouseDown(screen.getByLabelText("接收组织单元"));
      await userEvent.click(await screen.findByText("总院 · HOSPITAL · HOSPITAL-1"));
      await userEvent.type(screen.getByLabelText("发布说明"), "先按默认灰度验证");
      await userEvent.click(screen.getByRole("button", { name: /开始同步发布/ }));

      await waitFor(() => {
        expect(apiMocks.releasePackage).toHaveBeenCalledWith({
          packageId: "pkg-offline",
          request: {
            reason: "先按默认灰度验证",
            targetOrgUnitId: "hospital-1",
            strategy: "GRAYSCALE",
            scopeType: "ALL",
            scopeValue: "",
            adapterIds: ["target-his"],
            packageVersion: "3.0.0",
          },
        });
      });
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "shows failed and not-connected adapters and exports sync evidence",
    async () => {
      apiMocks.releasePackage.mockResolvedValueOnce({
        status: "FAILED",
        logs: [
          {
            logId: "log-fail",
            planId: "plan-1",
            adapterId: "target-his",
            status: "FAILED",
            errorCode: "ENG-PACKAGE-005",
            errorMessage: "目标库写入失败",
            retryCount: 0,
            syncEvidence: null,
          },
          {
            logId: "log-not-synced",
            planId: "plan-1",
            adapterId: "target-graph",
            status: "NOT_SYNCED",
            errorCode: "NOT_SYNCED",
            errorMessage: "未配置真实同步适配器",
            retryCount: 0,
            syncEvidence: null,
          },
        ],
      });
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      await userEvent.click(screen.getByRole("button", { name: "院内同步发布" }));
      fireEvent.mouseDown(screen.getByLabelText("选择发布适配器"));
      await userEvent.click(await screen.findByText(/院内 HIS 同步通道/));
      fireEvent.mouseDown(screen.getByLabelText("接收组织单元"));
      await userEvent.click(await screen.findByText("总院 · HOSPITAL · HOSPITAL-1"));
      await userEvent.type(screen.getByLabelText("发布说明"), "验证失败适配器证据");
      await userEvent.click(screen.getByRole("button", { name: /开始同步发布/ }));

      expect(await screen.findByText("失败 / 未连通适配器")).toBeInTheDocument();
      expect(screen.getAllByText("院内 HIS 同步通道").length).toBeGreaterThan(0);
      expect(screen.getAllByText("图谱同步通道").length).toBeGreaterThan(0);
      expect(screen.getAllByText("目标库写入失败").length).toBeGreaterThan(0);
      expect(screen.getAllByText("未配置真实同步适配器").length).toBeGreaterThan(0);

      await userEvent.click(screen.getByRole("button", { name: "导出同步证据" }));

      await waitFor(() => {
        expect(downloadPackageSyncEvidenceExport).toHaveBeenCalledWith("pkg-offline");
      });
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "adds terminology package assets with the stable package scope key",
    async () => {
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      await userEvent.click(screen.getByRole("button", { name: "办理细项" }));

      fireEvent.mouseDown(screen.getByLabelText("资产类型"));
      await userEvent.click(await screen.findByText("术语字典映射 (TERMINOLOGY)"));

      fireEvent.mouseDown(screen.getByLabelText("选择已发布的临床资产"));
      await userEvent.click(await screen.findByText(/检验术语映射包/));
      expect(screen.getByLabelText("资产快照版本")).toHaveValue("2026.06");
      await userEvent.click(screen.getByRole("button", { name: "确认将此资产关联加入当前包草稿" }));

      await waitFor(() => {
        expect(apiMocks.addPackageItem).toHaveBeenCalledWith({
          packageId: "pkg-offline",
          request: {
            assetType: "TERMINOLOGY",
            assetId: "TERM.LAB|DEPARTMENT|CARD",
            assetVersion: "2026.06",
            packageVersion: "3.0.0",
          },
        });
      });
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );
});
