import { fireEvent, render, screen, within } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { MemoryRouter } from "react-router-dom";
import type * as ReactRouterDom from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import DemoValidation from "./DemoValidation";
import type { RuntimeOperationsSnapshot, SecurityProfile } from "@/shared/api/hooks";

const hookState = vi.hoisted(() => ({
  security: {} as Record<string, unknown>,
  runtime: {} as Record<string, unknown>,
  runtimeEnabledCalls: [] as unknown[],
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => hookState.security,
  useRuntimeOperations: (enabled?: boolean) => {
    hookState.runtimeEnabledCalls.push(enabled);
    return hookState.runtime;
  },
}));

const navigateSpy = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof ReactRouterDom>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigateSpy,
  };
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/workbench/demo-validation"]}>
      <ConfigProvider>
        <DemoValidation />
      </ConfigProvider>
    </MemoryRouter>,
  );
}

function profile(roleCode = "implementation-engineer"): SecurityProfile {
  return {
    userId: `${roleCode}-1`,
    username: roleCode,
    roles: [
      {
        code: roleCode,
        displayName: "实施工程师",
        source: "DEFAULT",
        scopeLevel: "HOSPITAL",
        scopeCode: "h-1",
      },
    ],
    permissions: [
      {
        code: "menu.workbench",
        dimension: "MENU",
        target: "workbench",
        displayName: "查看工作台",
        risk: "LOW",
      },
      {
        code: "workbench:demo:view",
        dimension: "ACTION",
        target: "workbench:demo:view",
        displayName: "查看演示与校验",
        risk: "LOW",
      },
    ],
    menuKeys: ["workbench"],
    environmentKeys: ["production"],
    dataScope: {
      tenantId: "t-1",
      groupId: null,
      hospitalId: "h-1",
      campusId: null,
      siteId: null,
      departmentId: null,
      specialtyId: null,
    },
    mustChangePwd: false,
    mfaRequired: false,
    mfaBound: true,
  };
}

const runtimeSnapshot: RuntimeOperationsSnapshot = {
  serviceName: "medkernel",
  environment: "container",
  deploymentMode: "docker-core",
  databaseDialect: "postgres",
  migrationLocation: "classpath:db/migration/postgres",
  activeProfiles: ["container"],
  healthStatus: "UP",
  featureFlags: [
    {
      key: "graph-projection",
      displayName: "知识图谱投影",
      enabled: false,
      risk: "MEDIUM",
      owner: "信息科",
      description: "控制知识图谱投影是否参与运行。",
      source: "CONFIG_CENTER",
      warning: "配置关闭时不参与演示。",
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
      key: "provider-his",
      displayName: "HIS Provider",
      status: "NOT_CONNECTED",
      detail: "未接入真实 HIS 连接器",
    },
    {
      key: "graph-projection",
      displayName: "图谱投影",
      status: "MODEL_DISABLED",
      detail: "图谱投影 Feature Flag 已关闭",
    },
    {
      key: "terminology-sync",
      displayName: "字典同步",
      status: "UNKNOWN",
      detail: "当前快照未采集到字典同步状态",
    },
  ],
  backup: {
    enabled: true,
    rpo: "24 小时",
    rto: "4 小时",
    backupScript: "./deploy/docker/scripts/backup.sh",
    restoreScript: "./deploy/docker/scripts/restore.sh",
    checksumPolicy: "SHA-256 摘要随备份文件生成，恢复前自动校验",
    source: "CONFIG_CENTER",
    warning: null,
  },
  domesticProfile: {
    targetOs: "Linux",
    targetJdk: "JDK 21",
    databaseVendors: ["PostgreSQL", "Oracle"],
    cryptoAlgorithms: ["SM2", "SM3", "SM4"],
    evidence: "运行底座快照",
  },
  generatedAt: "2026-06-01T00:00:00Z",
};

function setLoadedState(roleCode = "implementation-engineer") {
  hookState.security = {
    data: profile(roleCode),
    isLoading: false,
    isError: false,
  };
  hookState.runtime = {
    data: runtimeSnapshot,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  };
}

describe("DemoValidation", () => {
  beforeEach(() => {
    setLoadedState();
    hookState.runtimeEnabledCalls = [];
    navigateSpy.mockReset();
  });

  it("renders honest readiness counts from the runtime snapshot without workbench endpoints", () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "演示与校验" })).toBeInTheDocument();
    expect(screen.getByText("3 可演示 / 2 阻塞 / 2 未启用")).toBeInTheDocument();
    expect(screen.getByText("存在阻塞项，演示前需处理")).toBeInTheDocument();
    expect(screen.getByTestId("demo-validation-tabs")).toBeInTheDocument();
    expect(screen.getAllByTestId(/^demo-validation-filter-/)).toHaveLength(3);
    expect(screen.queryByText(/\/api\/v1\/workbench/)).not.toBeInTheDocument();
  });

  it("keeps blockers actionable with Chinese reasons and real repair links", () => {
    renderPage();

    const providerRow = screen.getByTestId("demo-validation-item-provider-his");
    expect(within(providerRow).getByText("阻塞")).toBeInTheDocument();
    expect(within(providerRow).getByText(/未接入真实 HIS 连接器/)).toBeInTheDocument();

    fireEvent.click(within(providerRow).getByRole("button", { name: "去修复" }));

    expect(navigateSpy).toHaveBeenCalledWith("/system/providers");
  });

  it("shows a forbidden state for clinical roles and does not query runtime sources", () => {
    hookState.security = {
      data: {
        ...profile("doctor"),
        permissions: [
          {
            code: "menu.workbench",
            dimension: "MENU",
            target: "workbench",
            displayName: "查看工作台",
            risk: "LOW",
          },
        ],
      },
      isLoading: false,
      isError: false,
    };

    renderPage();

    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
    expect(hookState.runtimeEnabledCalls.at(-1)).toBe(false);
  });

  it("surfaces partial success when any self-check source is unknown", () => {
    renderPage();

    expect(screen.getByText("部分状态未采集")).toBeInTheDocument();
    expect(screen.getByText(/字典同步状态未采集/)).toBeInTheDocument();
  });

  it("shows traceId on runtime errors and keeps one retry primary action", () => {
    hookState.runtime = {
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          data: {
            detail: "运行底座暂时不可用",
            traceId: "trace-demo-001",
          },
        },
      },
      refetch: vi.fn(),
    };

    renderPage();

    expect(screen.getByText("运行底座暂时不可用")).toBeInTheDocument();
    expect(screen.getByText(/trace-demo-001/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "重新自检" }));
    expect(hookState.runtime.refetch).toHaveBeenCalledTimes(1);
  });

  it("switches back to the workbench through the merged workbench tab", () => {
    renderPage();

    fireEvent.click(within(screen.getByTestId("demo-validation-tabs")).getByText("工作台"));

    expect(navigateSpy).toHaveBeenCalledWith("/dashboard");
  });
});
