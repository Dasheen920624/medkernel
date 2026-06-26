import { App as AntdApp, ConfigProvider } from "antd";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCheckDataPermission,
  useDataPermissionPolicies,
  useInteropAssessment,
  useMaskingRules,
  useOrgUnits,
  usePreviewMasking,
  useRuntimeOperations,
  useSecurityProfile,
  useSystemConfigs,
  useTenantSystemConfigs,
  useUpdateSystemConfig,
  useUpdateTenantSystemConfig,
  useUpsertDataPermissionPolicy,
  useUpsertMaskingRule,
} from "@/shared/api/hooks";

import SecurityBaseline from "./SecurityBaseline";

vi.mock("@/shared/api/hooks", () => ({
  useCheckDataPermission: vi.fn(),
  useDataPermissionPolicies: vi.fn(),
  useInteropAssessment: vi.fn(),
  useMaskingRules: vi.fn(),
  useOrgUnits: vi.fn(),
  usePreviewMasking: vi.fn(),
  useRuntimeOperations: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSystemConfigs: vi.fn(),
  useTenantSystemConfigs: vi.fn(),
  useUpdateSystemConfig: vi.fn(),
  useUpdateTenantSystemConfig: vi.fn(),
  useUpsertDataPermissionPolicy: vi.fn(),
  useUpsertMaskingRule: vi.fn(),
}));

function query<T>(data: T) {
  return {
    data,
    isLoading: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  };
}

function renderPage() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <SecurityBaseline />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("SecurityBaseline", () => {
  const updateConfig = vi.fn();
  const updateTenantConfig = vi.fn();
  const upsertPolicy = vi.fn();
  const upsertMasking = vi.fn();
  const checkDataPermission = vi.fn();
  const previewMasking = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useSecurityProfile).mockReturnValue(
      query({
        userId: "u-admin",
        username: "security-admin",
        roles: [
          {
            code: "platform-admin",
            displayName: "平台管理员",
            source: "PLATFORM_SEED",
            scopeLevel: "TENANT",
            scopeCode: "t-1",
          },
        ],
        permissions: [
          {
            code: "system.manage",
            dimension: "ACTION",
            target: "system",
            displayName: "系统配置",
            risk: "HIGH",
          },
        ],
        menuKeys: ["security-baseline"],
        environmentKeys: ["prod"],
        dataScope: {
          tenantId: "t-1",
          groupId: "g-1",
          hospitalId: "h-1",
          campusId: null,
          siteId: null,
          departmentId: null,
          specialtyId: null,
        },
        mustChangePwd: false,
        mfaRequired: true,
        mfaBound: true,
      }) as never,
    );
    vi.mocked(useRuntimeOperations).mockReturnValue(
      query({
        serviceName: "medkernel",
        environment: "container",
        deploymentMode: "docker-core",
        databaseDialect: "postgres",
        migrationLocation: "classpath:db/migration/postgres",
        activeProfiles: ["dev"],
        healthStatus: "UP",
        jvm: {
          javaVersion: "21.0.8",
          javaVendor: "Eclipse Adoptium",
          vmName: "OpenJDK 64-Bit Server VM",
          virtualThreadsEnabled: false,
          availableProcessors: 8,
        },
        os: {
          name: "Linux",
          version: "6.8",
          arch: "amd64",
        },
        featureFlags: [],
        dependencies: [
          {
            key: "database",
            displayName: "关系数据库",
            status: "UP",
            detail: "postgres",
          },
        ],
        backup: {
          enabled: true,
          rpo: "24 小时",
          rto: "4 小时",
          backupScript: "./backup.sh",
          restoreScript: "./restore.sh",
          checksumPolicy: "SM3",
          drillEvidence: {
            status: "NOT_AVAILABLE",
            completedAt: null,
            migrationCount: null,
            evidenceReference: null,
            detail: "尚未提供隔离恢复演练证据",
          },
          source: "CONFIG_CENTER",
          warning: null,
        },
        domesticProfile: {
          targetOs: "麒麟",
          targetJdk: "BiSheng JDK 21",
          databaseVendors: ["达梦"],
          cryptoAlgorithms: ["SM3"],
          evidence: "国产化自检",
        },
        domesticCompatibility: {
          overallStatus: "WARN",
          summary: "0 项通过，4 项警告，0 项失败，3 项待现场确认",
          checkedAt: "2026-06-06T00:00:00Z",
          items: [],
        },
        generatedAt: "2026-06-06T00:00:00Z",
      }) as never,
    );
    vi.mocked(useSystemConfigs).mockReturnValue(
      query([
        {
          key: "medkernel.auth.password.min-length",
          value: "12",
          valueType: "INTEGER",
          displayName: "口令最小长度",
          risk: "HIGH",
          owner: "安全组",
          description: "平台账号口令最小长度",
          source: "CONFIG_CENTER",
          protectedConfig: false,
          version: 1,
          updatedAt: "2026-06-06T00:00:00Z",
        },
        {
          key: "medkernel.runtime.feature-flags.domestic-crypto.enabled",
          value: "true",
          valueType: "BOOLEAN",
          displayName: "国密增强",
          risk: "HIGH",
          owner: "安全组",
          description: "国密增强不可关闭",
          source: "CONFIG_CENTER",
          protectedConfig: true,
          version: 1,
          updatedAt: "2026-06-06T00:00:00Z",
        },
        {
          key: "medkernel.knowledge.literature.material-root-uri",
          value: "",
          valueType: "STRING",
          displayName: "平台知识文献资料库根地址",
          risk: "HIGH",
          owner: "平台知识治理组 / 信息科",
          description: "主平台知识管理服务器使用的正式文献资料库根地址，禁止使用 tmp 临时目录。",
          source: "PLATFORM_SEED",
          protectedConfig: true,
          version: 1,
          updatedAt: "2026-06-06T00:00:00Z",
        },
      ]) as never,
    );
    vi.mocked(useTenantSystemConfigs).mockReturnValue(
      query([
        {
          key: "medkernel.runtime.feature-flags.authoring-clinical-operators.enabled",
          value: "false",
          valueType: "BOOLEAN",
          displayName: "规则临床算子",
          risk: "HIGH",
          owner: "当前授权责任人",
          description: "控制临床算子是否参与求值",
          source: "SYSTEM_INHERITED",
          protectedConfig: true,
          version: 1,
          updatedAt: "2026-06-06T00:00:00Z",
        },
      ]) as never,
    );
    vi.mocked(useDataPermissionPolicies).mockReturnValue(
      query({
        items: [
          {
            policyId: "policy-1",
            tenantId: "t-1",
            resourceType: "clinical_case",
            action: "READ",
            minDataLevel: "HOSPITAL",
            allowedColumns: ["patientId", "encounterId"],
            status: "ACTIVE",
            version: 1,
            createdAt: "2026-06-06T00:00:00Z",
            createdBy: "security-admin",
            updatedAt: "2026-06-06T00:00:00Z",
            updatedBy: "security-admin",
          },
        ],
        page: 1,
        size: 20,
        total: 21,
        hasNext: true,
        totalEstimated: false,
      }) as never,
    );
    vi.mocked(useMaskingRules).mockReturnValue(
      query({
        items: [
          {
            ruleId: "mask-1",
            tenantId: "t-1",
            resourceType: "clinical_case",
            fieldName: "patientName",
            scenarioCode: "DEFAULT",
            strategy: "KEEP_FIRST_LAST",
            maskChar: "*",
            prefixKeep: 1,
            suffixKeep: 0,
            status: "ACTIVE",
            version: 1,
            createdAt: "2026-06-06T00:00:00Z",
            createdBy: "security-admin",
            updatedAt: "2026-06-06T00:00:00Z",
            updatedBy: "security-admin",
          },
        ],
        page: 1,
        size: 20,
        total: 25,
        hasNext: true,
        totalEstimated: false,
      }) as never,
    );
    vi.mocked(useOrgUnits).mockReturnValue(
      query({
        items: [
          { id: "g-1", level: "REGION", code: "GROUP-1", name: "第一医疗集团" },
          {
            id: "h-1",
            level: "FACILITY",
            facilityType: "HOSPITAL",
            code: "HOSP-1",
            name: "集团总院",
          },
          { id: "c-1", level: "CAMPUS", code: "CAMPUS-1", name: "东院区" },
          {
            id: "s-1",
            level: "FACILITY",
            facilityType: "STATION",
            code: "SITE-1",
            name: "门诊服务点",
          },
          {
            id: "d-1",
            level: "DEPARTMENT",
            code: "DEPT-1",
            name: "心内科",
            specialtyId: "CARDIOLOGY",
          },
        ],
        page: 1,
        size: 200,
        total: 5,
        totalPages: 1,
      }) as never,
    );
    vi.mocked(useInteropAssessment).mockReturnValue(
      query({
        standardVersion: "IOT-2026",
        totalItems: 1,
        satisfiedItems: 0,
        gapItems: 0,
        missingEvidenceItems: 1,
        satisfactionRate: 0,
        items: [
          {
            itemId: "interop-1",
            standardVersion: "IOT-2026",
            dimension: "STANDARDIZATION",
            itemCode: "STD-01",
            itemName: "标准数据集覆盖",
            requirementSummary: "核心数据资源采用受控标准编码",
            status: "SATISFIED",
            evidenceCount: 1,
            sharedWithEmrLevel: true,
            gapReason: null,
            evidences: [
              {
                mapId: "map-emr-export-1",
                sourceType: "EMR_LEVEL_EVIDENCE_EXPORT",
                sourceId: "emr-export-001",
                evidenceRef: "EMR-LEVEL-EXPORT-001",
                evidenceSummary: "电子病历评级证据导出覆盖标准数据集",
                fileUri: "/evidence/emr-export-001.ndjson",
                payloadDigest: "sha256:emr-export-001",
                sharedWithEmrLevel: true,
              },
            ],
          },
        ],
      }) as never,
    );
    updateConfig.mockResolvedValue({});
    updateTenantConfig.mockResolvedValue({});
    upsertPolicy.mockResolvedValue({});
    upsertMasking.mockResolvedValue({});
    checkDataPermission.mockResolvedValue({
      policyId: "policy-1",
      resourceType: "clinical_case",
      action: "READ",
      requiredLevel: "HOSPITAL",
      rowAllowed: false,
      allowedColumns: ["patientId", "encounterId"],
      deniedColumns: ["patientName"],
    });
    previewMasking.mockResolvedValue({
      resourceType: "clinical_case",
      scenarioCode: "DEFAULT",
      values: {
        patientName: "张*",
        encounterId: "enc-1",
      },
      maskedFields: ["patientName"],
      rawAllowed: false,
    });
    vi.mocked(useUpdateSystemConfig).mockReturnValue({
      mutateAsync: updateConfig,
      isPending: false,
    } as never);
    vi.mocked(useUpdateTenantSystemConfig).mockReturnValue({
      mutateAsync: updateTenantConfig,
      isPending: false,
    } as never);
    vi.mocked(useUpsertDataPermissionPolicy).mockReturnValue({
      mutateAsync: upsertPolicy,
      isPending: false,
    } as never);
    vi.mocked(useUpsertMaskingRule).mockReturnValue({
      mutateAsync: upsertMasking,
      isPending: false,
    } as never);
    vi.mocked(useCheckDataPermission).mockReturnValue({
      mutateAsync: checkDataPermission,
      isPending: false,
    } as never);
    vi.mocked(usePreviewMasking).mockReturnValue({
      mutateAsync: previewMasking,
      isPending: false,
    } as never);
  });

  it("unifies runtime baseline, system config, data permission, masking and interop evidence", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("heading", { name: "安全基线与系统配置" })).toBeInTheDocument();
    expect(screen.getByText("关系数据库")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "系统配置" }));
    expect(screen.getByText("口令最小长度")).toBeInTheDocument();
    expect(screen.getByText("国密增强")).toBeInTheDocument();
    expect(screen.getByText("平台知识文献资料")).toBeInTheDocument();
    expect(screen.getAllByText("平台知识文献资料库根地址").length).toBeGreaterThan(0);
    expect(screen.getAllByText("未配置").length).toBeGreaterThan(0);
    expect(
      screen.getByText(/正式知识生产前必须通过配置中心维护受管本地磁盘、对象存储或 HTTPS 网关/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "编辑 国密增强" })).toBeDisabled();

    await user.click(screen.getByRole("tab", { name: "数据权限" }));
    expect(useDataPermissionPolicies).toHaveBeenCalledWith({ page: 1, size: 20 });
    expect(screen.getByText("clinical_case")).toBeInTheDocument();
    expect(screen.getByText("patientId, encounterId")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "脱敏规则" }));
    expect(useMaskingRules).toHaveBeenCalledWith({ page: 1, size: 20 });
    expect(
      screen.getByRole("row", { name: /clinical_case patientName DEFAULT KEEP_FIRST_LAST/ }),
    ).toBeInTheDocument();
    expect(screen.getByText(/保留前 1 位/)).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "互操作测评" }));
    expect(screen.getByText("标准数据集覆盖")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Expand row" }));
    expect(screen.getByText("电子病历评级证据导出")).toBeInTheDocument();
  }, 15_000);

  it("treats globally disabled multi-factor authentication as a valid passing configuration", () => {
    const currentProfile = vi.mocked(useSecurityProfile)().data;
    vi.mocked(useSecurityProfile).mockReturnValue(
      query({
        ...currentProfile,
        mfaRequired: false,
        mfaBound: false,
      }) as never,
    );

    renderPage();

    expect(screen.getAllByText("多因素认证全局配置已关闭").length).toBeGreaterThan(0);
    expect(
      screen.getByRole("row", { name: /多因素认证 通过 多因素认证全局配置已关闭/ }),
    ).toBeInTheDocument();
  });

  it("updates a real config item with version and high-risk confirmation", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "系统配置" }));
    await user.click(screen.getByRole("button", { name: "编辑 口令最小长度" }));
    const dialog = screen.getByRole("dialog", { name: "编辑系统配置" });
    const valueInput = within(dialog).getByRole("spinbutton", { name: "配置值" });
    fireEvent.change(valueInput, { target: { value: "14" } });
    fireEvent.change(within(dialog).getByRole("textbox", { name: "变更原因" }), {
      target: { value: "提升口令强度" },
    });
    await user.click(within(dialog).getByRole("checkbox", { name: "确认高风险影响" }));
    await user.click(within(dialog).getByRole("button", { name: "保存配置" }));

    await waitFor(() =>
      expect(updateConfig).toHaveBeenCalledWith({
        key: "medkernel.auth.password.min-length",
        payload: {
          value: "14",
          reason: "提升口令强度",
          expectedVersion: 1,
          confirmedHighRisk: true,
        },
      }),
    );
  });

  it("maintains the platform knowledge literature repository root through system config", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "系统配置" }));
    await user.click(screen.getByRole("button", { name: "编辑 平台知识文献资料库根地址" }));
    const dialog = screen.getByRole("dialog", { name: "编辑系统配置" });
    const yearlyManagedStorageUri =
      "file:///srv/medkernel/platform-knowledge/t-1/literature-materials/2026/";
    fireEvent.change(within(dialog).getByRole("textbox", { name: "配置值" }), {
      target: { value: yearlyManagedStorageUri },
    });
    fireEvent.change(within(dialog).getByRole("textbox", { name: "变更原因" }), {
      target: { value: "正式文献资料库按年度分层" },
    });
    await user.click(within(dialog).getByRole("checkbox", { name: "确认高风险影响" }));
    await user.click(within(dialog).getByRole("button", { name: "保存配置" }));

    await waitFor(() =>
      expect(updateConfig).toHaveBeenCalledWith({
        key: "medkernel.knowledge.literature.material-root-uri",
        payload: {
          value: yearlyManagedStorageUri,
          reason: "正式文献资料库按年度分层",
          expectedVersion: 1,
          confirmedHighRisk: true,
        },
      }),
    );
  });

  it("updates tenant feature flag override without inheriting the system version lock", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "系统配置" }));
    await user.click(screen.getByText("服务机构覆盖"));
    fireEvent.change(screen.getByRole("textbox", { name: "服务机构标识" }), {
      target: { value: "tenant-A" },
    });
    expect(screen.queryByRole("textbox", { name: "服务空间标识" })).not.toBeInTheDocument();
    expect(screen.getByText("规则临床算子")).toBeInTheDocument();
    expect(screen.getAllByText("继承系统")).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "编辑 规则临床算子" }));
    const dialog = screen.getByRole("dialog", { name: "编辑服务机构配置" });
    fireEvent.change(within(dialog).getByRole("textbox", { name: "变更原因" }), {
      target: { value: "服务机构灰度回退" },
    });
    await user.click(within(dialog).getByRole("checkbox", { name: "确认高风险影响" }));
    await user.click(within(dialog).getByRole("button", { name: "保存配置" }));

    await waitFor(() =>
      expect(updateTenantConfig).toHaveBeenCalledWith({
        tenantId: "tenant-A",
        key: "medkernel.runtime.feature-flags.authoring-clinical-operators.enabled",
        payload: {
          value: "false",
          reason: "服务机构灰度回退",
          expectedVersion: undefined,
          confirmedHighRisk: true,
        },
      }),
    );
  });

  it("selects every data permission scope from the organization directory", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "数据权限" }));
    await user.click(screen.getByRole("button", { name: /新增策略/ }));
    const dialog = screen.getByRole("dialog", { name: "新增数据权限策略" });

    expect(within(dialog).getByRole("combobox", { name: "集团/联合体" })).toBeInTheDocument();
    expect(within(dialog).getByRole("combobox", { name: "医院" })).toBeInTheDocument();
    expect(within(dialog).getByRole("combobox", { name: "院区" })).toBeInTheDocument();
    expect(within(dialog).getByRole("combobox", { name: "基层服务点" })).toBeInTheDocument();
    expect(within(dialog).getByRole("combobox", { name: "科室" })).toBeInTheDocument();
    expect(within(dialog).getByRole("combobox", { name: "专科" })).toBeInTheDocument();

    await user.click(within(dialog).getByRole("combobox", { name: "科室" }));
    expect(screen.getByRole("option", { name: "心内科 · DEPT-1" })).toBeInTheDocument();
  });

  it("runs a data permission trial against the data access decision contract", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "数据权限" }));
    expect(screen.getByRole("heading", { name: "权限试算" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "执行权限试算" }));

    await waitFor(() =>
      expect(checkDataPermission).toHaveBeenCalledWith({
        resourceType: "clinical_case",
        action: "READ",
        groupId: undefined,
        hospitalId: undefined,
        campusId: undefined,
        siteId: undefined,
        departmentId: undefined,
        specialtyId: undefined,
        requestedColumns: ["patientId", "encounterId"],
      }),
    );
    expect(await screen.findByText("行级不允许")).toBeInTheDocument();
    expect(screen.getByText("patientName")).toBeInTheDocument();
  });

  it("previews masking rules with explicit operator-provided values", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "脱敏规则" }));
    expect(screen.getByRole("heading", { name: "脱敏预览" })).toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: "预览样例值" }), {
      target: { value: "张三" },
    });
    await user.click(screen.getByRole("button", { name: "执行脱敏预览" }));

    await waitFor(() =>
      expect(previewMasking).toHaveBeenCalledWith({
        resourceType: "clinical_case",
        scenarioCode: "DEFAULT",
        sensitiveFields: ["patientName"],
        values: {
          patientName: "张三",
        },
      }),
    );
    expect(await screen.findByText("已按规则脱敏")).toBeInTheDocument();
    expect(screen.getByText("张*")).toBeInTheDocument();
  });
});
