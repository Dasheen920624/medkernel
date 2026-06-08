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

const openSelectByLabel = (label: string, root: HTMLElement = document.body) => {
  const labelElement = within(root).getByText(label, { selector: "label" });
  const selector = labelElement
    .closest(".ant-form-item")
    ?.querySelector<HTMLElement>(".ant-select-selector");
  if (!selector) {
    throw new Error(`未找到 ${label} 下拉选择器`);
  }
  fireEvent.mouseDown(selector);
};

const chooseVisibleSelectOption = async (label: string | RegExp) => {
  const dropdown = Array.from(document.querySelectorAll<HTMLElement>(".ant-select-dropdown"))
    .filter((element) => !element.className.includes("ant-select-dropdown-hidden"))
    .filter((element) => within(element).queryByText(label))
    .at(-1);
  if (!dropdown) {
    throw new Error("未找到展开的下拉列表");
  }
  await userEvent.click(within(dropdown).getByText(label));
};

const apiMocks = vi.hoisted(() => ({
  downloadPackageOfflineExport: vi.fn(),
  downloadPackageSyncEvidenceExport: vi.fn(),
  importOfflinePackage: vi.fn(),
  createPackage: vi.fn(),
  addPackageItem: vi.fn(),
  applyPilotTemplateReferences: vi.fn(),
  releasePackage: vi.fn(),
  refetchPackages: vi.fn(),
  refetchPackageDetail: vi.fn(),
  refetchAssetReadiness: vi.fn(),
  useAuthoringAssets: vi.fn(),
  useEvaluationIndicators: vi.fn(),
  permissions: [] as Array<{ code: string }>,
  packagesData: null as Record<string, unknown> | null,
  packagesLoading: false,
  packagesError: false,
  pilotTemplates: [] as Array<Record<string, unknown>>,
  pilotTemplatesLoading: false,
  assetReadiness: null as Record<string, unknown> | null,
  assetReadinessLoading: false,
  assetReadinessError: false,
  inheritanceImpact: null as Record<string, unknown> | null,
  inheritanceImpactLoading: false,
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
      permissions: apiMocks.permissions,
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
  usePackageInheritanceImpact: () => ({
    data: apiMocks.inheritanceImpact,
    isFetching: apiMocks.inheritanceImpactLoading,
  }),
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
  useApplyPilotTemplateReferences: () => ({
    mutateAsync: apiMocks.applyPilotTemplateReferences,
    isPending: false,
  }),
  useAuthoringAssets: (...args: unknown[]) => apiMocks.useAuthoringAssets(...args),
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
    apiMocks.applyPilotTemplateReferences.mockReset();
    apiMocks.refetchAssetReadiness.mockReset();
    apiMocks.useAuthoringAssets.mockReset();
    apiMocks.useAuthoringAssets.mockReturnValue({ data: { items: [] } });
    apiMocks.useEvaluationIndicators.mockReset();
    apiMocks.useEvaluationIndicators.mockReturnValue({ data: { items: [] } });
    apiMocks.permissions = [];
    apiMocks.packagesData = null;
    apiMocks.packagesLoading = false;
    apiMocks.packagesError = false;
    apiMocks.pilotTemplatesLoading = false;
    apiMocks.assetReadinessLoading = false;
    apiMocks.assetReadinessError = false;
    apiMocks.inheritanceImpactLoading = false;
    apiMocks.inheritanceImpact = {
      tenantId: "tenant-A",
      assetType: "RULE",
      assetIdentity: "RULE.COPD",
      applicableScope: "adult|inpatient",
      upstreamBaseVersion: "2026.05",
      upstreamTargetVersion: "2026.06",
      autoInheritedCount: 1,
      rebaseRequiredCount: 1,
      upstreamDiff: {
        packageId: "RULE.COPD",
        baseVersion: "2026.05",
        targetVersion: "2026.06",
        addedCount: 0,
        updatedCount: 1,
        removedCount: 0,
        affectedDepartments: ["呼吸科"],
        changes: [],
      },
      targets: [
        {
          orgUnitId: "hospital-1",
          orgPath: "/TENANT-A/HOSPITAL-1",
          impactType: "AUTO_INHERITS_UPSTREAM",
          effectiveVersionId: "av-platform-copd-v1",
          effectiveVersionNo: "2026.05",
          sourceTier: "PLATFORM",
          diffSummary: null,
          rebasePrompt: "平台新版本激活后自动继承",
        },
        {
          orgUnitId: "dept-resp",
          orgPath: "/TENANT-A/HOSPITAL-1/RESP",
          impactType: "REBASE_RECOMMENDED",
          effectiveVersionId: "av-local-copd-v1",
          effectiveVersionNo: "2026.05-local",
          sourceTier: "ORG",
          diffSummary: "本级阈值调整",
          rebasePrompt: "已定制版本建议 rebase",
        },
        {
          orgUnitId: "tenant-root",
          orgPath: "/TENANT-A",
          impactType: "DISABLE_REVIEW_RECOMMENDED",
          effectiveVersionId: null,
          effectiveVersionNo: null,
          sourceTier: "TENANT",
          diffSummary: "租户层停用旧版流程",
          rebasePrompt: "租户覆盖需复核停用理由",
        },
      ],
    };
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
      activePackageReferenceCount: 1,
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
    apiMocks.applyPilotTemplateReferences.mockResolvedValue({
      templateCode: "TPL.FIRST_RUN",
      references: [
        {
          referenceId: "ref-first-run",
          tenantId: "tenant-A",
          platformTenantId: "t-1",
          platformPackageId: "pkg-platform-copd",
          packageCode: "PKG.COPD",
          packageVersion: "2026.06.03",
          targetOrgUnitId: "hospital-1",
          sourceTemplateCode: "TPL.FIRST_RUN",
          status: "ACTIVE",
        },
      ],
      initialOverrides: [],
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
      screen.getAllByRole("button", { name: /发布配置包|应用首发引用|一键创建/ }),
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

    expect(apiMocks.useAuthoringAssets).toHaveBeenCalledWith(
      { assetType: "RULE", size: 100 },
      { enabled: false },
    );
    expect(apiMocks.useEvaluationIndicators).toHaveBeenCalledWith(
      { size: 100 },
      { enabled: false },
    );
  });

  it(
    "shows asset readiness and applies platform package references from the first-run template",
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

      await userEvent.click(screen.getByRole("button", { name: "应用首发引用" }));

      expect(screen.getByText("COPD 首发模板")).toBeInTheDocument();
      expect(screen.getByText(/KNOWLEDGE/)).toBeInTheDocument();

      await userEvent.click(screen.getByRole("button", { name: "应用平台引用" }));

      await waitFor(() => {
        expect(apiMocks.applyPilotTemplateReferences).toHaveBeenCalledWith({
          templateCode: "TPL.FIRST_RUN",
          packageVersion: "2026.06.01",
          request: {
            target_org_unit_id: "hospital-1",
            initial_overrides: [],
          },
        });
      });
      expect(apiMocks.refetchAssetReadiness).toHaveBeenCalled();
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "submits curated inheritance overrides with the first-run template instead of an empty override list",
    async () => {
      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      await userEvent.click(screen.getByRole("button", { name: "应用首发引用" }));

      const dialog = screen
        .getByRole("button", { name: "应用平台引用" })
        .closest('[role="dialog"]') as HTMLElement | null;
      expect(dialog).not.toBeNull();
      if (!dialog) {
        throw new Error("未找到首发引用对话框");
      }

      fireEvent.mouseDown(within(dialog).getByLabelText("覆盖方式"));
      await userEvent.click(await screen.findByText("新增本院专有 (ADD)"));
      fireEvent.change(within(dialog).getByLabelText("覆盖资产身份"), {
        target: { value: "RULE.COPD.LOCAL" },
      });
      fireEvent.change(within(dialog).getByLabelText("覆盖版本 ID"), {
        target: { value: "av-local-copd-add-v1" },
      });
      fireEvent.change(within(dialog).getByLabelText("覆盖适用范围"), {
        target: { value: "adult|inpatient" },
      });
      fireEvent.mouseDown(within(dialog).getByLabelText("传播范围"));
      await userEvent.click(await screen.findByText("独有，仅本组织 (EXCLUSIVE)"));
      fireEvent.change(within(dialog).getByLabelText("差异说明"), {
        target: { value: "本院新增 COPD 宣教规则" },
      });
      fireEvent.change(within(dialog).getByLabelText("覆盖原因"), {
        target: { value: "呼吸科首发需本地宣教规则" },
      });
      fireEvent.change(within(dialog).getByLabelText("影响范围"), {
        target: { value: "总院成人住院" },
      });

      await userEvent.click(within(dialog).getByRole("button", { name: "加入覆盖清单" }));
      expect(within(dialog).getByText("RULE.COPD.LOCAL")).toBeInTheDocument();

      await userEvent.click(within(dialog).getByRole("button", { name: "应用平台引用" }));

      await waitFor(() => {
        expect(apiMocks.applyPilotTemplateReferences).toHaveBeenCalledWith({
          templateCode: "TPL.FIRST_RUN",
          packageVersion: "2026.06.01",
          request: {
            target_org_unit_id: "hospital-1",
            initial_overrides: [
              {
                asset_type: "RULE",
                asset_identity: "RULE.COPD.LOCAL",
                inherited_version_id: undefined,
                override_version_id: "av-local-copd-add-v1",
                target_org_unit_id: "hospital-1",
                applicable_scope: "adult|inpatient",
                override_mode: "ADD",
                propagation: "EXCLUSIVE",
                diff_summary: "本院新增 COPD 宣教规则",
                override_reason: "呼吸科首发需本地宣教规则",
                impact_scope: "总院成人住院",
              },
            ],
          },
        });
      });
    },
    PAGE_INTERACTION_TIMEOUT_MS,
  );

  it("shows platform tenant organization perspectives and upstream inheritance impact", async () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.getByText("继承治理")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "平台视角" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "租户视角" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "当前机构" })).toBeChecked();
    expect(screen.getByText("本级定制")).toBeInTheDocument();
    expect(screen.getAllByText("建议 rebase").length).toBeGreaterThan(0);
    expect(screen.queryByText("平台新版本激活后自动继承")).not.toBeInTheDocument();

    await userEvent.click(screen.getByText("平台视角"));
    expect(screen.getByRole("radio", { name: "平台视角" })).toBeChecked();
    expect(screen.getByText("平台基线")).toBeInTheDocument();
    expect(screen.getByText("自动继承上游")).toBeInTheDocument();
    expect(screen.getByText("平台新版本激活后自动继承")).toBeInTheDocument();

    await userEvent.click(screen.getByText("租户视角"));
    expect(screen.getByRole("radio", { name: "租户视角" })).toBeChecked();
    expect(screen.getByText("租户定制")).toBeInTheDocument();
    expect(screen.getByText("租户覆盖需复核停用理由")).toBeInTheDocument();
    expect(screen.queryByText("平台新版本激活后自动继承")).not.toBeInTheDocument();
  });

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
      activePackageReferenceCount: 0,
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

    expect(screen.queryByRole("button", { name: "应用首发引用" })).not.toBeInTheDocument();
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
    "adds reusable condition fragments from the unified authoring asset library",
    async () => {
      apiMocks.permissions = [{ code: "rule.read" }, { code: "pathway.read" }];
      apiMocks.useAuthoringAssets.mockReturnValue({
        data: {
          items: [
            {
              assetType: "CONDITION_FRAGMENT",
              assetId: "frag-ckd",
              assetCode: "FRAG.CKD",
              name: "CKD 条件片段",
              category: "慢病",
              tags: ["复用"],
              version: "1",
              status: "ACTIVE",
              packageVersion: "3.0.0",
              favorite: true,
              cloneable: true,
              updatedAt: "2026-06-08T00:00:00Z",
            },
          ],
        },
      });
      apiMocks.addPackageItem.mockResolvedValue({
        itemId: "item-frag",
        packageId: "pkg-offline",
        assetType: "CONDITION_FRAGMENT",
        assetId: "frag-ckd",
        assetVersion: "1",
      });

      render(
        <ConfigProvider>
          <AntdApp>
            <ConfigPackages />
          </AntdApp>
        </ConfigProvider>,
      );

      await userEvent.click(screen.getByRole("button", { name: "办理细项" }));
      const drawer = screen.getByRole("dialog", { name: "办理包内资产细项" });

      openSelectByLabel("资产类型", drawer);
      await chooseVisibleSelectOption("条件片段 (CONDITION_FRAGMENT)");

      openSelectByLabel("选择已发布的临床资产", drawer);
      await chooseVisibleSelectOption(/CKD 条件片段/);
      expect(screen.getByLabelText("资产快照版本")).toHaveValue("1");
      await userEvent.click(screen.getByRole("button", { name: "确认将此资产关联加入当前包草稿" }));

      await waitFor(() => {
        expect(apiMocks.addPackageItem).toHaveBeenCalledWith({
          packageId: "pkg-offline",
          request: {
            assetType: "CONDITION_FRAGMENT",
            assetId: "frag-ckd",
            assetVersion: "1",
            packageVersion: "3.0.0",
          },
        });
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
      const drawer = screen.getByRole("dialog", { name: "办理包内资产细项" });

      openSelectByLabel("资产类型", drawer);
      await chooseVisibleSelectOption("术语字典映射 (TERMINOLOGY)");

      openSelectByLabel("选择已发布的临床资产", drawer);
      await chooseVisibleSelectOption(/检验术语映射包/);
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
