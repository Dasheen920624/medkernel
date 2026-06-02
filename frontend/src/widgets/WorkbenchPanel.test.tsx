import { render, screen, within } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { describe, expect, it, vi, beforeEach } from "vitest";

import { WorkbenchPanel } from "./WorkbenchPanel";
import type {
  AuditEventRow,
  RuntimeOperationsSnapshot,
  SecurityProfile,
  SuccessPlan,
} from "@/shared/api/hooks";

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

function renderWorkbench() {
  return render(
    <ConfigProvider>
      <WorkbenchPanel />
    </ConfigProvider>,
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

const successPlan: SuccessPlan = {
  tenantId: "t-1",
  currentStage: "PILOT",
  healthScore: 86,
  activatedModules: "配置包,审计",
  activatedPathways: "",
  updatedAt: "2026-06-01T00:00:00Z",
  updatedBy: "implementation-engineer",
};

const auditEvents: AuditEventRow[] = [
  {
    id: "audit-1",
    eventId: "evt-1",
    occurredAt: "2026-06-01T00:00:00Z",
    user: "doctor-1",
    action: "配置包发布申请",
    actionCode: "PACKAGE_SUBMIT",
    resourceType: "package",
    resourceId: "pkg-1",
    traceId: "trace-audit-1",
    signature: "sig-audit-1",
    status: "SUCCESS",
  },
];

function setLoadedState(roleCode = "it-ops", displayName = "信息科") {
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

describe("WorkbenchPanel", () => {
  beforeEach(() => {
    setLoadedState();
    hookState.runtimeEnabledCalls = [];
    hookState.auditEnabledCalls = [];
    hookState.successPlanEnabledCalls = [];
  });

  it("renders the information-technology default view from existing source APIs", () => {
    renderWorkbench();

    expect(screen.getByRole("heading", { name: "信息科工作台" })).toBeInTheDocument();
    expect(screen.getByText("系统健康")).toBeInTheDocument();
    expect(screen.getByText("Provider 连通")).toBeInTheDocument();
    expect(screen.getByText(/关系数据库/)).toBeInTheDocument();
    expect(screen.getAllByText(/知识图谱投影/).length).toBeGreaterThan(0);
    expect(screen.queryByText("真实工作台聚合数据待接入")).not.toBeInTheDocument();
    expect(screen.queryByText("等待真实聚合 API")).not.toBeInTheDocument();
  });

  it("prioritizes my todo state for clinical users without fabricating task counts", () => {
    setLoadedState("doctor", "临床医生");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "临床医生工作台" })).toBeInTheDocument();
    expect(screen.getByText("我的待办")).toBeInTheDocument();
    expect(screen.getAllByText(/当前组织暂无待办/).length).toBeGreaterThan(0);
    expect(screen.queryByText("Provider 连通")).not.toBeInTheDocument();
  });

  it("keeps the medical-affairs view free from technical source wording", () => {
    setLoadedState("medical-affairs", "医务处");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "医务处工作台" })).toBeInTheDocument();
    expect(screen.getByText("价值指标")).toBeInTheDocument();
    expect(screen.getByText("质控整改")).toBeInTheDocument();
    expect(screen.queryByText("Provider 连通")).not.toBeInTheDocument();
    expect(screen.queryByText(/traceId/i)).not.toBeInTheDocument();
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
        ...profile("doctor", "临床医生"),
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

  it("keeps unavailable future domains honest instead of linking to fake workbench endpoints", () => {
    setLoadedState("medical-affairs", "医务处");

    renderWorkbench();

    const qualityCard = screen.getByTestId("workbench-card-quality");
    expect(within(qualityCard).getAllByText("该域未启用").length).toBeGreaterThan(0);
    expect(screen.queryByText(/\/api\/v1\/workbench/)).not.toBeInTheDocument();
  });
});
