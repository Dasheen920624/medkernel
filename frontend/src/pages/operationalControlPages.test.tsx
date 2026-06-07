import { App as AntdApp, ConfigProvider } from "antd";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import DomesticCheck from "./advanced/DomesticCheck";
import DevConsole from "./advanced/DevConsole";
import IdentityBinding from "./compliance/IdentityBinding";
import SecurityBaseline from "./compliance/SecurityBaseline";
import {
  useDelegatedAuthStatus,
  useCreateIdentityBinding,
  useIdentityBindings,
  useLoginTenantDirectory,
  useOrgUsers,
  useRuntimeOperations,
  useSecurityProfile,
  useSystemRuntime,
  useUnbindIdentityBinding,
} from "@/shared/api/hooks";
import type {
  DelegatedAuthStatus,
  IdentityBinding as IdentityBindingRecord,
  LoginTenantDirectory,
  RuntimeOperationsSnapshot,
  SecurityProfile,
} from "@/shared/api/hooks";

vi.mock("@/shared/api/hooks", () => ({
  useDelegatedAuthStatus: vi.fn(),
  useCreateIdentityBinding: vi.fn(),
  useIdentityBindings: vi.fn(),
  useLoginTenantDirectory: vi.fn(),
  useOrgUsers: vi.fn(),
  useRuntimeOperations: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSystemRuntime: vi.fn(),
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
    { tenantId: "t-platform", name: "平台主租户", kind: "PLATFORM" },
    { tenantId: "t-hospital", name: "集团总院", kind: "CUSTOMER" },
  ],
  platformTenant: { tenantId: "t-platform", name: "平台主租户", kind: "PLATFORM" },
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
  });

  it("renders and submits a real identity binding workflow", async () => {
    const user = userEvent.setup();
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    renderPage(<IdentityBinding />);

    expect(screen.getByRole("heading", { name: "身份绑定" })).toBeInTheDocument();
    expect(screen.getByText("BOTH")).toBeInTheDocument();
    expect(screen.getAllByText("未连接").length).toBeGreaterThan(0);
    expect(screen.getByText("doctor-1")).toBeInTheDocument();
    expect(screen.getByText("****-001")).toBeInTheDocument();
    expect(screen.getByText(/未配置真实 IdP/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /新增绑定/ }));
    expect(screen.getByRole("dialog", { name: "新增身份绑定" })).toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: "系统用户" }));
    await user.click(
      await screen.findByText("李医生 · doctor-2", {
        selector: ".ant-select-item-option-content",
      }),
    );
    await user.type(screen.getByLabelText("外部身份"), "EMP-002");
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
  });

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
    expect(screen.getByText("关系数据库")).toBeInTheDocument();
    expect(screen.getByText("备份恢复")).toBeInTheDocument();
    expect(screen.queryByText("安全基线自查接口尚未接入")).not.toBeInTheDocument();
  });

  it("unbinds an active identity with an audit reason and optimistic version", async () => {
    const user = userEvent.setup();
    renderPage(<IdentityBinding />);

    await user.click(screen.getByRole("button", { name: /解绑/ }));
    expect(screen.getByRole("dialog", { name: "解除身份绑定" })).toBeInTheDocument();
    await user.type(screen.getByLabelText("解绑原因"), "员工离职停用统一身份");
    await user.click(screen.getByRole("button", { name: "确认解绑" }));

    await waitFor(() =>
      expect(unbindIdentityBinding).toHaveBeenCalledWith({
        bindingId: "idb-1",
        reason: "员工离职停用统一身份",
        expectedVersion: 1,
      }),
    );
  });

  it("renders domestic compatibility evidence from the runtime operations snapshot", () => {
    renderPage(<DomesticCheck />);

    expect(screen.getByRole("heading", { name: "国产化自检" })).toBeInTheDocument();
    expect(screen.getByText("麒麟 / 统信 / openEuler")).toBeInTheDocument();
    expect(screen.getByText("KAE-JDK 21 / BiSheng JDK 21")).toBeInTheDocument();
    expect(screen.getByText("达梦")).toBeInTheDocument();
    expect(screen.getByText("人大金仓")).toBeInTheDocument();
    expect(screen.getByText("SM3")).toBeInTheDocument();
    expect(screen.getByText(/国产化自检、五方言迁移合同/)).toBeInTheDocument();
    expect(screen.queryByText("入口暂未激活")).not.toBeInTheDocument();
  });

  it("renders a controlled developer console without exposing a raw placeholder", () => {
    renderPage(<DevConsole />);

    expect(screen.getByRole("heading", { name: "开发者控制台" })).toBeInTheDocument();
    expect(screen.getByText("系统运行快照")).toBeInTheDocument();
    expect(screen.getAllByText("medkernel").length).toBeGreaterThan(0);
    expect(screen.getByText("docker-core")).toBeInTheDocument();
    expect(screen.getByText("Java 21")).toBeInTheDocument();
    expect(
      within(screen.getByTestId("developer-dependencies")).getByText("统一身份 IdP"),
    ).toBeInTheDocument();
    expect(screen.queryByText("入口暂未激活")).not.toBeInTheDocument();
  });
});
