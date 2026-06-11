import { fireEvent, render, screen, within } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { MemoryRouter } from "react-router-dom";
import type * as ReactRouterDom from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { WorkbenchPanel } from "./WorkbenchPanel";
import type {
  AuditEventRow,
  RuntimeOperationsSnapshot,
  SecurityProfile,
  SuccessPlan,
} from "@/shared/api/hooks";
import { ROLE_OPTIONS } from "@/shared/config/roleCatalog";

const hookState = vi.hoisted(() => ({
  security: {} as Record<string, unknown>,
  runtime: {} as Record<string, unknown>,
  audit: {} as Record<string, unknown>,
  successPlan: {} as Record<string, unknown>,
  runtimeEnabledCalls: [] as unknown[],
  auditEnabledCalls: [] as unknown[],
  successPlanEnabledCalls: [] as unknown[],
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => hookState.security,
  useRuntimeOperations: (enabled?: boolean) => {
    hookState.runtimeEnabledCalls.push(enabled);
    return hookState.runtime;
  },
  useAuditEvents: (enabled?: boolean) => {
    hookState.auditEnabledCalls.push(enabled);
    return hookState.audit;
  },
  useSuccessPlan: (enabled?: boolean) => {
    hookState.successPlanEnabledCalls.push(enabled);
    return hookState.successPlan;
  },
  useTransitionSuccessStage: () => ({ mutate: vi.fn(), isPending: false }),
}));

const navigateSpy = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof ReactRouterDom>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigateSpy,
  };
});

function renderWorkbench() {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <ConfigProvider>
        <WorkbenchPanel />
      </ConfigProvider>
    </MemoryRouter>,
  );
}

function profile(roleCode: string, displayName: string): SecurityProfile {
  return {
    userId: `${roleCode}-1`,
    username: roleCode,
    roles: [
      {
        code: roleCode,
        displayName,
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
      ...sourcePermissionCodesFor(roleCode).map((code) => ({
        code,
        dimension: "ACTION",
        target: code,
        displayName: code,
        risk: "LOW" as const,
      })),
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

function sourcePermissionCodesFor(roleCode: string): string[] {
  if (
    [
      "platform-governance-admin",
      "organization-admin",
      "organization-admin",
      "integration-operator",
      "implementation-operator",
    ].includes(roleCode)
  ) {
    return ["system.read", "audit.read", "tenant.read"];
  }
  if (
    [
      "platform-knowledge-governor",
      "knowledge-governor",
      "clinical-governor",
      "quality-governor",
      "compliance-auditor",
    ].includes(roleCode)
  ) {
    return ["audit.read"];
  }
  return [];
}

function hasSourcePermission(roleCode: string, code: string): boolean {
  return sourcePermissionCodesFor(roleCode).includes(code);
}

const runtimeSnapshot: RuntimeOperationsSnapshot = {
  serviceName: "medkernel",
  environment: "container",
  deploymentMode: "docker-core",
  databaseDialect: "postgres",
  migrationLocation: "classpath:db/migration/postgres",
  activeProfiles: ["container"],
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
  featureFlags: [
    {
      key: "graph-projection",
      displayName: "知识图谱投影",
      enabled: false,
      risk: "MEDIUM",
      owner: "信息科",
      description: "控制知识图谱投影是否参与运行。",
      source: "SAFE_DEFAULT",
      warning: "配置中心读取失败，已使用启动安全默认。",
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
      key: "graph-projection",
      displayName: "知识图谱投影",
      status: "NOT_CONNECTED",
      detail: "Feature Flag 关闭，未连接图谱投影",
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
    targetOs: "Linux",
    targetJdk: "JDK 21",
    databaseVendors: ["PostgreSQL", "Oracle"],
    cryptoAlgorithms: ["SM2", "SM3", "SM4"],
    evidence: "运行底座快照",
  },
  domesticCompatibility: {
    overallStatus: "WARN",
    summary: "2 项通过，1 项警告，0 项失败，4 项待现场确认",
    checkedAt: "2026-06-01T00:00:00Z",
    items: [],
  },
  generatedAt: "2026-06-01T00:00:00Z",
};

const successPlan: SuccessPlan = {
  tenantId: "t-1",
  currentStage: "PILOT",
  healthScore: 86,
  activatedModules: "配置包,审计",
  activatedPathways: "",
  updatedAt: "2026-06-01T00:00:00Z",
  updatedBy: "implementation-operator",
};

const auditEvents: AuditEventRow[] = [
  {
    id: "audit-1",
    eventId: "evt-1",
    occurredAt: "2026-06-01T00:00:00Z",
    actorUserId: "doctor-1",
    summary: "配置包发布申请",
    actionCode: "PACKAGE_SUBMIT",
    resourceType: "package",
    resourceId: "pkg-1",
    traceId: "trace-audit-1",
    signature: "sig-audit-1",
    status: "SUCCESS",
  },
];

function setLoadedState(roleCode = "integration-operator", displayName = "信息科") {
  hookState.security = {
    data: profile(roleCode, displayName),
    isLoading: false,
    isError: false,
  };
  hookState.runtime = {
    data: runtimeSnapshot,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  };
  hookState.audit = {
    data: auditEvents,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  };
  hookState.successPlan = {
    data: successPlan,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  };
}

function expectedLandingFor(roleCode: string, displayName: string) {
  if (roleCode === "integration-operator") {
    return { heading: "信息科工作台", marker: "系统健康" };
  }
  if (["platform-knowledge-governor", "knowledge-governor"].includes(roleCode)) {
    return { heading: `${displayName}工作台`, marker: "知识治理" };
  }
  if (roleCode === "identity-access-admin") {
    return { heading: "人员与访问工作台", marker: "人员与账号" };
  }
  if (
    [
      "clinical-decision-user",
      "nursing-collaborator",
      "diagnostic-service-user",
      "medication-safety-user",
    ].includes(roleCode)
  ) {
    return { heading: `${displayName}工作台`, marker: "我的待办" };
  }
  if (["clinical-governor", "quality-governor"].includes(roleCode)) {
    return { heading: `${displayName}工作台`, marker: "价值指标" };
  }
  if (roleCode === "compliance-auditor") {
    return { heading: "合规审计工作台", marker: "最近变化" };
  }
  return { heading: `${displayName}工作台`, marker: "治理切片" };
}

describe("WorkbenchPanel", () => {
  beforeEach(() => {
    setLoadedState();
    hookState.runtimeEnabledCalls = [];
    hookState.auditEnabledCalls = [];
    hookState.successPlanEnabledCalls = [];
    navigateSpy.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders the integration operations view without customer-visible technical English", () => {
    renderWorkbench();

    expect(screen.getByRole("heading", { name: "信息科工作台" })).toBeInTheDocument();
    expect(screen.getByText("系统健康")).toBeInTheDocument();
    expect(screen.getByText("外部依赖连通")).toBeInTheDocument();
    expect(screen.getByText(/关系数据库/)).toBeInTheDocument();
    expect(screen.getAllByText(/知识图谱投影/).length).toBeGreaterThan(0);
    expect(screen.queryByText("真实工作台聚合数据待接入")).not.toBeInTheDocument();
    expect(screen.queryByText("等待真实聚合 API")).not.toBeInTheDocument();
  });

  it("renders an explicit responsibility-specific landing view for all 14 customer roles", () => {
    ROLE_OPTIONS.forEach(({ code, name }) => {
      setLoadedState(code, name);

      const { unmount } = renderWorkbench();
      const expected = expectedLandingFor(code, name);

      expect(screen.getByRole("heading", { name: expected.heading })).toBeInTheDocument();
      expect(screen.getAllByText(expected.marker).length).toBeGreaterThan(0);
      expect(screen.queryByText("当前权限不足")).not.toBeInTheDocument();
      expect(hookState.runtimeEnabledCalls.at(-1)).toBe(hasSourcePermission(code, "system.read"));
      expect(hookState.auditEnabledCalls.at(-1)).toBe(hasSourcePermission(code, "audit.read"));

      unmount();
    });
  });

  it("separates knowledge governance and personnel access from clinical and quality work", () => {
    setLoadedState("platform-knowledge-governor", "平台知识治理员");
    const knowledge = renderWorkbench();
    expect(screen.getByRole("heading", { name: "平台知识治理员工作台" })).toBeInTheDocument();
    expect(screen.getByText("知识治理")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "知识资产治理" })).toBeInTheDocument();
    expect(screen.queryByText("质控整改")).not.toBeInTheDocument();
    knowledge.unmount();

    setLoadedState("identity-access-admin", "人员与访问管理员");
    renderWorkbench();
    expect(screen.getByRole("heading", { name: "人员与访问工作台" })).toBeInTheDocument();
    expect(screen.getByText("人员与账号")).toBeInTheDocument();
    expect(screen.getByText("身份来源")).toBeInTheDocument();
    expect(screen.queryByText("质控整改")).not.toBeInTheDocument();
  });

  it("prioritizes my todo state for clinical users without fabricating task counts", () => {
    setLoadedState("clinical-decision-user", "临床医生");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "临床医生工作台" })).toBeInTheDocument();
    expect(screen.getByText("我的待办")).toBeInTheDocument();
    expect(screen.getAllByText(/当前组织暂无待办/).length).toBeGreaterThan(0);
    expect(screen.queryByText("外部依赖连通")).not.toBeInTheDocument();
    expect(screen.queryByText("该域未启用")).not.toBeInTheDocument();
    expect(screen.queryByText(/D3 临床运行域完成后/)).not.toBeInTheDocument();
    expect(screen.getByText("临床运行入口")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "患者路径" }));
    expect(navigateSpy).toHaveBeenCalledWith("/pathway/patients");
  });

  it("does not query system or audit sources for clinical users without source permissions", () => {
    setLoadedState("clinical-decision-user", "临床医生");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "临床医生工作台" })).toBeInTheDocument();
    expect(screen.getByText("我的待办")).toBeInTheDocument();
    expect(screen.getByText("临床运行入口")).toBeInTheDocument();
    expect(screen.queryByText("工作台暂时不可用")).not.toBeInTheDocument();
    expect(hookState.runtimeEnabledCalls.at(-1)).toBe(false);
    expect(hookState.auditEnabledCalls.at(-1)).toBe(false);
  });

  it("keeps the medical-affairs view free from technical source wording", () => {
    setLoadedState("clinical-governor", "医务处");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "医务处工作台" })).toBeInTheDocument();
    expect(screen.getByText("价值指标")).toBeInTheDocument();
    expect(screen.getByText("质控整改")).toBeInTheDocument();
    expect(screen.queryByText("外部依赖连通")).not.toBeInTheDocument();
    expect(screen.queryByText(/traceId/i)).not.toBeInTheDocument();
    expect(screen.queryByText("该域未启用")).not.toBeInTheDocument();
    expect(screen.queryByText(/D4 质控改进域完成后/)).not.toBeInTheDocument();
    expect(screen.getByText("质控整改入口")).toBeInTheDocument();

    fireEvent.click(
      within(screen.getByTestId("workbench-card-quality")).getByRole("button", {
        name: "院级质控驾驶舱",
      }),
    );
    expect(navigateSpy).toHaveBeenCalledWith("/qc/dashboard");
  });

  it("shows partial success when one source fails while keeping other source cards visible", () => {
    hookState.audit = {
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          data: {
            detail: "审计服务暂时不可用",
            traceId: "trace-audit-down",
          },
        },
      },
      refetch: vi.fn(),
    };

    renderWorkbench();

    expect(screen.getByText("部分来源暂时不可用")).toBeInTheDocument();
    expect(screen.getAllByText(/审计服务暂时不可用/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/trace-audit-down/).length).toBeGreaterThan(0);
    expect(screen.getByText("系统健康")).toBeInTheDocument();
    expect(screen.getByText(/关系数据库/)).toBeInTheDocument();
  });

  it("renders a forbidden state when the loaded security profile cannot access workbench", () => {
    hookState.security = {
      data: {
        ...profile("clinical-decision-user", "临床医生"),
        permissions: [],
        menuKeys: ["patient-pathways"],
      },
      isLoading: false,
      isError: false,
    };

    renderWorkbench();

    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
    expect(screen.queryByText("系统健康")).not.toBeInTheDocument();
    expect(hookState.runtimeEnabledCalls.at(-1)).toBe(false);
    expect(hookState.auditEnabledCalls.at(-1)).toBe(false);
    expect(hookState.successPlanEnabledCalls.at(-1)).toBe(false);
  });

  it("does not enable source requests before the role profile is confirmed", () => {
    hookState.security = {
      data: undefined,
      isLoading: true,
      isError: false,
    };

    renderWorkbench();

    expect(screen.getByText("正在确认当前角色")).toBeInTheDocument();
    expect(hookState.runtimeEnabledCalls.at(-1)).toBe(false);
    expect(hookState.auditEnabledCalls.at(-1)).toBe(false);
    expect(hookState.successPlanEnabledCalls.at(-1)).toBe(false);
  });

  it("keeps domain entry cards on real product pages instead of fake workbench endpoints", () => {
    setLoadedState("clinical-governor", "医务处");

    renderWorkbench();

    const qualityCard = screen.getByTestId("workbench-card-quality");
    expect(within(qualityCard).getByText("质控整改入口")).toBeInTheDocument();
    expect(within(qualityCard).getByRole("button", { name: "评估结果" })).toBeInTheDocument();
    expect(screen.queryByText("该域未启用")).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\/workbench/)).not.toBeInTheDocument();
  });

  it("renders one role-specific primary action and three default filters", () => {
    renderWorkbench();

    expect(screen.getByRole("button", { name: /查看运行状态/ })).toHaveClass("ant-btn-primary");
    expect(document.querySelectorAll(".ant-btn-primary")).toHaveLength(1);
    expect(screen.getAllByTestId(/^workbench-filter-/)).toHaveLength(3);
    expect(screen.getByText("组织范围")).toBeInTheDocument();
    expect(screen.getByText("病种")).toBeInTheDocument();
    expect(screen.getByText("时间")).toBeInTheDocument();
  });

  it("applies the time filter to recent audit changes", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-02T12:00:00Z"));
    hookState.audit = {
      data: [
        {
          ...auditEvents[0],
          id: "audit-today",
          summary: "今日权限复核",
          occurredAt: "2026-06-02T08:00:00Z",
        },
        {
          ...auditEvents[0],
          id: "audit-yesterday",
          summary: "昨日配置核验",
          occurredAt: "2026-06-01T08:00:00Z",
        },
      ],
      isLoading: false,
      isError: false,
    };

    renderWorkbench();

    expect(screen.getByText("今日权限复核")).toBeInTheDocument();
    expect(screen.getByText("昨日配置核验")).toBeInTheDocument();

    fireEvent.click(screen.getByText("今日"));

    expect(screen.getByText("今日权限复核")).toBeInTheDocument();
    expect(screen.queryByText("昨日配置核验")).not.toBeInTheDocument();
  });

  it("offers drilldowns for built source cards and domain entry cards", () => {
    setLoadedState("clinical-governor", "医务处");

    renderWorkbench();

    fireEvent.click(within(screen.getByTestId("workbench-card-audit")).getByText("查看最近变化"));
    expect(navigateSpy).toHaveBeenCalledWith("/admin/audit");

    fireEvent.click(
      within(screen.getByTestId("workbench-card-quality")).getByRole("button", {
        name: "评估结果",
      }),
    );
    expect(navigateSpy).toHaveBeenCalledWith("/qc/eval/results");
    expect(screen.queryByText(/\/api\/v1\/workbench/)).not.toBeInTheDocument();
  });

  it("shows lifecycle governance slices and weekly suggestions for platform users", () => {
    setLoadedState("platform-governance-admin", "平台管理员");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "平台管理员工作台" })).toBeInTheDocument();
    expect(screen.getByText("治理切片")).toBeInTheDocument();
    expect(screen.getAllByTestId(/^workbench-governance-slice-/)).toHaveLength(3);
    expect(screen.getByText("本周建议动作")).toBeInTheDocument();
    expect(screen.getByText("核对实施进度")).toBeInTheDocument();
    expect(hookState.successPlanEnabledCalls).toContain(true);
  });
});
