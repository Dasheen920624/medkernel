import { App as AntdApp, ConfigProvider } from "antd";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import DomesticCheck from "./advanced/DomesticCheck";
import DevConsole from "./advanced/DevConsole";
import IdentityBinding from "./compliance/IdentityBinding";
import SecurityBaseline from "./compliance/SecurityBaseline";
import {
  downloadDomesticCompatibilityReport,
  useCreateIdentityBinding,
  useDelegatedAuthStatus,
  useDeveloperApiContracts,
  useDisablePlugin,
  useGrantPlugin,
  useIdentityBindings,
  useLoginTenantDirectory,
  useOrgUsers,
  usePersonnel,
  usePreviewPersonnelImport,
  useCommitPersonnelImport,
  usePlugins,
  useRegisterPlugin,
  useRuntimeOperations,
  useSecurityProfile,
  useSystemRuntime,
  useTraceDiagnosis,
  useUnbindIdentityBinding,
} from "@/shared/api/hooks";
import type {
  DelegatedAuthStatus,
  DeveloperApiContractDirectory,
  IdentityBinding as IdentityBindingRecord,
  LoginTenantDirectory,
  PluginList,
  RuntimeOperationsSnapshot,
  SecurityProfile,
} from "@/shared/api/hooks";

vi.mock("@/shared/api/hooks", () => ({
  downloadDomesticCompatibilityReport: vi.fn(),
  useCreateIdentityBinding: vi.fn(),
  useDelegatedAuthStatus: vi.fn(),
  useDeveloperApiContracts: vi.fn(),
  useDisablePlugin: vi.fn(),
  useGrantPlugin: vi.fn(),
  useIdentityBindings: vi.fn(),
  useLoginTenantDirectory: vi.fn(),
  useOrgUsers: vi.fn(),
  usePersonnel: vi.fn(),
  usePreviewPersonnelImport: vi.fn(),
  useCommitPersonnelImport: vi.fn(),
  usePlugins: vi.fn(),
  useRegisterPlugin: vi.fn(),
  useRuntimeOperations: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSystemRuntime: vi.fn(),
  useTraceDiagnosis: vi.fn(),
  useUnbindIdentityBinding: vi.fn(),
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

const delegatedAuth: DelegatedAuthStatus = {
  mode: "BOTH",
  enabled: true,
  status: "NOT_CONNECTED",
  providers: ["OIDC", "CAS", "SAML", "国密CA"],
  message: "院方统一身份入口已开放，但当前未配置真实 IdP 连接器。",
};

const tenantDirectory: LoginTenantDirectory = {
  primaryTenants: [
    { tenantId: "t-platform", name: "平台治理空间", kind: "PLATFORM" },
    { tenantId: "t-hospital", name: "集团总院", kind: "CUSTOMER" },
  ],
  platformTenant: { tenantId: "t-platform", name: "平台治理空间", kind: "PLATFORM" },
  hasCustomerTenants: true,
};

const identityBindings: IdentityBindingRecord[] = [
  {
    bindingId: "idb-1",
    userId: "doctor-1",
    providerType: "EMPLOYEE_NO",
    subjectHint: "****-001",
    status: "ACTIVE",
    version: 1,
    createdAt: "2026-06-06T00:00:00Z",
    updatedAt: "2026-06-06T00:00:00Z",
  },
];

const createIdentityBinding = vi.fn();
const unbindIdentityBinding = vi.fn();

const securityProfile: SecurityProfile = {
  userId: "u-chief",
  username: "chief",
  roles: [
    {
      code: "security-admin",
      displayName: "安全管理员",
      source: "PLATFORM_SEED",
      scopeLevel: "TENANT",
      scopeCode: "t-hospital",
    },
  ],
  permissions: [
    {
      code: "admin.users.write",
      dimension: "ACTION",
      target: "admin-users",
      displayName: "用户写入",
      risk: "HIGH",
    },
    {
      code: "org.write",
      dimension: "ACTION",
      target: "org",
      displayName: "身份绑定管理",
      risk: "MEDIUM",
    },
    {
      code: "system.providers.read",
      dimension: "MENU",
      target: "system-providers",
      displayName: "运行状态",
      risk: "LOW",
    },
  ],
  menuKeys: ["system-providers", "security-baseline", "identity-binding"],
  environmentKeys: ["prod"],
  dataScope: {
    tenantId: "t-hospital",
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
};

const runtimeSnapshot: RuntimeOperationsSnapshot = {
  serviceName: "medkernel",
  environment: "container",
  deploymentMode: "docker-core",
  databaseDialect: "postgres",
  migrationLocation: "classpath:db/migration/postgres",
  activeProfiles: ["dev", "container"],
  healthStatus: "UP",
  jvm: {
    javaVersion: "21.0.8",
    javaVendor: "Eclipse Adoptium",
    vmName: "OpenJDK 64-Bit Server VM",
    virtualThreadsEnabled: false,
    availableProcessors: 8,
  },
  os: {
    name: "Mac OS X",
    version: "15.5",
    arch: "aarch64",
  },
  featureFlags: [
    {
      key: "graph-projection",
      displayName: "知识图谱投影",
      enabled: false,
      risk: "MEDIUM",
      owner: "信息科 / 架构组",
      description: "控制 Neo4j 图谱投影和图谱查询能力是否参与运行。",
      source: "SAFE_DEFAULT",
      warning: "图谱未连接时按关系库权威源降级。",
    },
    {
      key: "dify-workflow",
      displayName: "Dify 工作流",
      enabled: false,
      risk: "MEDIUM",
      owner: "AI 平台组",
      description: "控制模型工作流接入。",
      source: "SAFE_DEFAULT",
      warning: "未配置模型网关时禁用。",
    },
  ],
  dependencies: [
    {
      key: "database",
      displayName: "关系数据库",
      status: "UP",
      detail: "postgres · classpath:db/migration/postgres",
    },
    {
      key: "backup-restore",
      displayName: "备份恢复",
      status: "DEGRADED",
      detail: "已配置脚本，等待本次恢复演练证据。",
    },
    {
      key: "idp",
      displayName: "统一身份 IdP",
      status: "NOT_CONNECTED",
      detail: "未配置真实院方 IdP。",
    },
  ],
  backup: {
    enabled: true,
    rpo: "24 小时",
    rto: "4 小时",
    backupScript: "./deploy/docker/scripts/backup.sh",
    restoreScript: "./deploy/docker/scripts/restore.sh",
    checksumPolicy: "SHA-256 摘要随备份文件生成，恢复前自动校验",
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
    targetOs: "麒麟 / 统信 / openEuler",
    targetJdk: "KAE-JDK 21 / BiSheng JDK 21",
    databaseVendors: ["达梦", "人大金仓"],
    cryptoAlgorithms: ["SM2", "SM3", "SM4"],
    evidence: "国产化自检、五方言迁移合同、国密算法 smoke",
  },
  domesticCompatibility: {
    overallStatus: "WARN",
    summary: "0 项通过，4 项警告，0 项失败，3 项待现场确认",
    checkedAt: "2026-06-06T04:00:00Z",
    items: [
      {
        key: "os",
        category: "OS",
        displayName: "操作系统",
        status: "WARN",
        actualValue: "Mac OS X 15.5 aarch64",
        expectedValue: "麒麟 / 统信 / openEuler",
        reason: "当前操作系统未命中国产化目标清单，不标记通过。",
        recommendation: "在目标国产 OS 重新运行自检。",
        evidence: "System.getProperty(os.name/os.version/os.arch)",
      },
      {
        key: "database",
        category: "DATABASE",
        displayName: "关系数据库",
        status: "WARN",
        actualValue: "postgres · classpath:db/migration/postgres",
        expectedValue: "达梦 / 人大金仓",
        reason: "当前数据库方言未命中国产化目标清单，不标记通过。",
        recommendation: "切换 dm/kingbase profile 后重新运行迁移烟测。",
        evidence: "medkernel.runtime.database-dialect",
      },
      {
        key: "browser",
        category: "BROWSER",
        displayName: "国产浏览器",
        status: "UNKNOWN",
        actualValue: "服务端快照无法读取客户端浏览器",
        expectedValue: "国产浏览器现场版本",
        reason: "服务端无法读取客户端浏览器，不标记通过。",
        recommendation: "在交付现场用目标浏览器打开本页并保存报告。",
        evidence: "前端现场验收",
      },
    ],
  },
  generatedAt: "2026-06-06T04:00:00Z",
};

const systemRuntime = {
  status: "UP",
  service: "medkernel",
  version: "1.0.0-local",
  activeProfiles: ["dev", "container"],
  databaseDialect: "postgres",
  runtime: "Java 21",
};

const developerContracts: DeveloperApiContractDirectory = {
  contracts: [
    {
      id: "runtime-operations",
      title: "运行状态服务",
      basePath: "/api/v1/system",
      openApiPaths: ["/api/v1/system/**"],
      permissions: [{ code: "system.read", dimension: "ACTION", purpose: "查看运行状态" }],
      auditPoints: [],
      publicEndpoints: [],
    },
    {
      id: "rule",
      title: "规则引擎服务",
      basePath: "/api/v1/engine/rule",
      openApiPaths: ["/api/v1/engine/rule/**"],
      permissions: [{ code: "rule.publish", dimension: "ACTION", purpose: "发布规则" }],
      auditPoints: [{ action: "PUBLISH", targetType: "rule_definition", purpose: "发布规则" }],
      publicEndpoints: [],
    },
    {
      id: "third-party-knowledge-runtime",
      title: "第三方知识运行时服务",
      basePath: "/api/v1/engine/integration/knowledge-runtime",
      openApiPaths: ["/api/v1/engine/integration/knowledge-runtime/**"],
      permissions: [{ code: "package.read", dimension: "ACTION", purpose: "查看配置包" }],
      auditPoints: [],
      publicEndpoints: [],
      contractVersion: "v1",
      openApiDocumentUrl: "/v3/api-docs/medkernel-third-party-integration",
      fieldContractUrl: "/api/v1/engine/integration/data-contract?packageVersion={packageVersion}",
    },
  ],
};

const pluginList: PluginList = {
  items: [
    {
      pluginId: "plug-1",
      pluginCode: "ward-read-model",
      displayName: "病区只读看板",
      status: "PENDING_REVIEW",
      authorityBoundary: "READ_ONLY",
      capabilities: [
        {
          capabilityKey: "read-runtime",
          capabilityType: "READ",
          serviceContractId: "runtime-operations",
          serviceContractTitle: "运行状态服务",
          clinicalData: false,
        },
      ],
      version: 1,
      updatedAt: "2026-06-07T00:00:00Z",
    },
  ],
};

function renderPage(page: React.ReactElement) {
  return render(
    <ConfigProvider>
      <AntdApp>{page}</AntdApp>
    </ConfigProvider>,
  );
}

describe("operational control pages", () => {
  beforeEach(() => {
    vi.mocked(useDelegatedAuthStatus).mockReturnValue(query(delegatedAuth) as never);
    vi.mocked(useIdentityBindings).mockReturnValue(query(identityBindings) as never);
    vi.mocked(useLoginTenantDirectory).mockReturnValue(query(tenantDirectory) as never);
    vi.mocked(useOrgUsers).mockReturnValue(
      query({
        items: [
          { userId: "doctor-1", displayName: "张医生" },
          { userId: "doctor-2", displayName: "李医生" },
        ],
        page: 1,
        size: 200,
        total: 2,
        totalPages: 1,
      }) as never,
    );
    vi.mocked(usePersonnel).mockReturnValue(
      query({
        items: [
          {
            personId: "person-1",
            employeeNo: "EMP-001",
            displayName: "张医生",
            userId: "doctor-1",
            accountState: "ACTIVE",
            identityCount: 1,
          },
          {
            personId: "person-2",
            employeeNo: "EMP-002",
            displayName: "李医生",
            userId: "doctor-2",
            accountState: "ACTIVE",
            identityCount: 0,
          },
        ],
        page: 1,
        size: 100,
        total: 2,
      }) as never,
    );
    vi.mocked(usePreviewPersonnelImport).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    vi.mocked(useCommitPersonnelImport).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    createIdentityBinding.mockReset();
    createIdentityBinding.mockResolvedValue(identityBindings[0]);
    unbindIdentityBinding.mockReset();
    unbindIdentityBinding.mockResolvedValue({
      ...identityBindings[0],
      status: "UNBOUND",
      version: 2,
    });
    vi.mocked(useCreateIdentityBinding).mockReturnValue({
      mutateAsync: createIdentityBinding,
      isPending: false,
    } as never);
    vi.mocked(useUnbindIdentityBinding).mockReturnValue({
      mutateAsync: unbindIdentityBinding,
      isPending: false,
    } as never);
    vi.mocked(useSecurityProfile).mockReturnValue(query(securityProfile) as never);
    vi.mocked(useRuntimeOperations).mockReturnValue(query(runtimeSnapshot) as never);
    vi.mocked(useSystemRuntime).mockReturnValue(query(systemRuntime) as never);
    vi.mocked(useDeveloperApiContracts).mockReturnValue(query(developerContracts) as never);
    vi.mocked(usePlugins).mockReturnValue(query(pluginList) as never);
    vi.mocked(useTraceDiagnosis).mockReturnValue(query(undefined) as never);
    vi.mocked(useRegisterPlugin).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    vi.mocked(useGrantPlugin).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    vi.mocked(useDisablePlugin).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    vi.mocked(downloadDomesticCompatibilityReport).mockResolvedValue(
      new Blob(["MedKernel 国产化自检报告"]),
    );
  }, 15_000);

  it("renders and submits a real identity binding workflow", async () => {
    const user = userEvent.setup();
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    renderPage(<IdentityBinding />);

    expect(screen.getByRole("heading", { name: "身份来源" })).toBeInTheDocument();
    expect(screen.getByText(/平台账号与机构统一身份/)).toBeInTheDocument();
    expect(screen.getAllByText("未接通").length).toBeGreaterThan(0);
    expect(screen.getByText("张医生")).toBeInTheDocument();
    expect(screen.getByText("****-001")).toBeInTheDocument();
    expect(screen.getByText(/未配置真实 IdP/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /单个绑定/ }));
    expect(screen.getByRole("dialog", { name: "单个绑定身份来源" })).toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: "人员账号" }));
    await user.click(
      await screen.findByText("李医生 · EMP-002", {
        selector: ".ant-select-item-option-content",
      }),
    );
    await user.type(screen.getByLabelText("院内身份标识"), "EMP-002");
    await user.type(screen.getByLabelText("绑定原因"), "新员工统一身份接入");
    await user.click(screen.getByRole("button", { name: "确认绑定" }));

    await waitFor(() =>
      expect(createIdentityBinding).toHaveBeenCalledWith({
        userId: "doctor-2",
        providerType: "EMPLOYEE_NO",
        externalSubject: "EMP-002",
        reason: "新员工统一身份接入",
      }),
    );
    const contextWarnings = consoleError.mock.calls.filter((args) =>
      args.some((value) => String(value).includes("Static function can not consume context")),
    );
    consoleError.mockRestore();
    expect(contextWarnings).toEqual([]);
  }, 15_000);

  it("uses the Ant Design app context for identity binding feedback", () => {
    const source = readFileSync(
      resolve(process.cwd(), "src/pages/compliance/IdentityBinding.tsx"),
      "utf8",
    );

    expect(source).toContain("App.useApp()");
    expect(source).not.toMatch(/\bmessage,\s*\n?\} from "antd"/);
  });

  it("renders security baseline from current profile and runtime facts", () => {
    renderPage(<SecurityBaseline />);

    expect(screen.getByRole("heading", { name: "安全基线与系统配置" })).toBeInTheDocument();
    expect(screen.getByText("安全管理员")).toBeInTheDocument();
    expect(screen.getAllByText("MFA 已绑定").length).toBeGreaterThan(0);
    expect(screen.getAllByText("高风险权限").length).toBeGreaterThan(0);
    expect(screen.getByText("用户写入")).toBeInTheDocument();
    expect(screen.getAllByText("关系数据库").length).toBeGreaterThan(0);
    expect(screen.getByText("备份恢复")).toBeInTheDocument();
    expect(screen.queryByText("安全基线自查接口尚未接入")).not.toBeInTheDocument();
  });

  it("unbinds an active identity with an audit reason and optimistic version", async () => {
    const user = userEvent.setup();
    renderPage(<IdentityBinding />);

    await user.click(screen.getByRole("button", { name: /解绑/ }));
    expect(screen.getByRole("dialog", { name: "解除身份来源" })).toBeInTheDocument();
    await user.type(screen.getByLabelText("解绑原因"), "员工离职停用统一身份");
    await user.click(screen.getByRole("button", { name: "确认解绑" }));

    await waitFor(() =>
      expect(unbindIdentityBinding).toHaveBeenCalledWith({
        bindingId: "idb-1",
        reason: "员工离职停用统一身份",
        expectedVersion: 1,
      }),
    );
  }, 15_000);

  it("renders domestic compatibility evidence from the runtime operations snapshot", async () => {
    const user = userEvent.setup();
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    renderPage(<DomesticCheck />);

    expect(screen.getByRole("heading", { name: "国产化自检" })).toBeInTheDocument();
    expect(screen.getByText("WARN")).toBeInTheDocument();
    expect(screen.getByText(/0 项通过，4 项警告/)).toBeInTheDocument();
    expect(screen.getAllByText("麒麟 / 统信 / openEuler").length).toBeGreaterThan(0);
    expect(screen.getAllByText("KAE-JDK 21 / BiSheng JDK 21").length).toBeGreaterThan(0);
    expect(screen.getByText("达梦")).toBeInTheDocument();
    expect(screen.getByText("人大金仓")).toBeInTheDocument();
    expect(screen.getByText("SM3")).toBeInTheDocument();
    expect(screen.getAllByText("关系数据库").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/不标记通过/).length).toBeGreaterThan(0);
    expect(screen.getByText("国产浏览器")).toBeInTheDocument();
    expect(screen.getByText(/国产化自检、五方言迁移合同/)).toBeInTheDocument();
    expect(screen.queryByText("入口暂未激活")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /导出报告/ }));
    await waitFor(() => expect(downloadDomesticCompatibilityReport).toHaveBeenCalledTimes(1));
    expect(consoleError.mock.calls.flat().join("\n")).not.toContain(
      "Sum of column `span` in a line not match `column`",
    );
    consoleError.mockRestore();
  });

  it("renders API contracts, trace diagnosis and plugin management tools", async () => {
    const user = userEvent.setup();
    renderPage(<DevConsole />);

    expect(screen.getByRole("heading", { name: "开发者控制台" })).toBeInTheDocument();
    expect(screen.getByText("系统运行快照")).toBeInTheDocument();
    expect(screen.getAllByText("medkernel").length).toBeGreaterThan(0);
    expect(screen.getByText("docker-core")).toBeInTheDocument();
    expect(screen.getByText("Java 21")).toBeInTheDocument();
    expect(
      within(screen.getByTestId("developer-dependencies")).getByText("统一身份 IdP"),
    ).toBeInTheDocument();
    expect(screen.getByText("运行状态服务")).toBeInTheDocument();
    expect(screen.getByText("rule.publish")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "OpenAPI" })).toHaveAttribute(
      "href",
      "/v3/api-docs/medkernel-third-party-integration",
    );
    expect(screen.getByText("字段契约")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Trace 诊断" }));
    expect(screen.getByPlaceholderText("输入 Trace ID")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "插件管理" }));
    expect(screen.getByText("病区只读看板")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /注册插件/ })).toBeInTheDocument();
    expect(screen.queryByText("入口暂未激活")).not.toBeInTheDocument();
  });

  it("does not emit row key or dynamic form key warnings", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    vi.mocked(useTraceDiagnosis).mockReturnValue(
      query({
        traceId: "trace-console",
        startedAt: "2026-06-07T04:00:00Z",
        endedAt: "2026-06-07T04:00:01Z",
        durationMs: 1000,
        stateHistory: [
          {
            fromStatus: "PENDING",
            toStatus: "SUCCEEDED",
            reason: "浏览器验收",
            actor: "it-ops-1",
            traceId: "trace-console",
            occurredAt: "2026-06-07T04:00:01Z",
          },
        ],
        payloads: [],
      }) as never,
    );

    try {
      renderPage(<DevConsole />);
      fireEvent.click(screen.getByRole("tab", { name: "Trace 诊断" }));
      const traceInput = screen.getByPlaceholderText("输入 Trace ID");
      fireEvent.change(traceInput, { target: { value: "trace-console" } });
      fireEvent.keyDown(traceInput, { key: "Enter", code: "Enter" });
      expect(await screen.findByText("PENDING → SUCCEEDED")).toBeInTheDocument();

      fireEvent.click(screen.getByRole("tab", { name: "插件管理" }));
      fireEvent.click(screen.getByRole("button", { name: /授权/ }));
      const grantDialog = screen.getByRole("dialog", { name: /授权 病区只读看板/ });
      expect(grantDialog).toBeInTheDocument();
      fireEvent.click(within(grantDialog).getByRole("button", { name: "取 消" }));

      fireEvent.click(screen.getByRole("button", { name: /注册插件/ }));
      expect(screen.getByRole("dialog", { name: "注册插件" })).toBeInTheDocument();

      const warnings = consoleError.mock.calls.flat().join("\n");
      expect(warnings).not.toContain("`index` parameter of `rowKey` function is deprecated");
      expect(warnings).not.toContain('props object containing a "key" prop is being spread');
      expect(warnings).not.toContain("Instance created by `useForm` is not connected");
    } finally {
      consoleError.mockRestore();
    }
  });
});
