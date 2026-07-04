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
import { findProductRoleJourney } from "@/shared/config/productRoleJourneys";

const legacyWorkbenchAggregationPlaceholder = ["真实工作台", "聚合数据待接入"].join("");
const legacyWorkbenchAggregationApiPlaceholder = ["等待真实", "聚合 API"].join("");
const legacyWorkbenchSelfProofPattern = new RegExp(["工作台不", "伪造"].join(""));
const legacyWorkbenchAggregationPattern = new RegExp(["伪造", "汇总数据"].join(""));

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
    mfaVerified: true,
  };
}

function sourcePermissionCodesFor(roleCode: string): string[] {
  if (roleCode === "platform-admin") {
    return ["system.read", "audit.read", "tenant.read"];
  }
  if (roleCode === "engine-operator") {
    return ["audit.read"];
  }
  if (roleCode === "auditor") {
    return ["system.read", "audit.read"];
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
      detail: "能力开关关闭，未连接图谱投影",
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
    evidence: "运行环境快照",
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
  activatedModules: "机构生效版本,审计",
  activatedPathways: "",
  updatedAt: "2026-06-01T00:00:00Z",
  updatedBy: "platform-admin",
};

const auditEvents: AuditEventRow[] = [
  {
    id: "audit-1",
    eventId: "evt-1",
    occurredAt: "2026-06-01T00:00:00Z",
    actorUserId: "doctor-1",
    summary: "生效版本发布申请",
    actionCode: "RELEASE_SUBMIT",
    resourceType: "runtime_release",
    resourceId: "runtime-release-1",
    traceId: "trace-audit-1",
    signature: "sig-audit-1",
    status: "SUCCESS",
  },
];

function setLoadedState(roleCode = "platform-admin", displayName = "平台管理员") {
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
  const journey = findProductRoleJourney(roleCode);
  const markerByKind = {
    operations: "系统健康",
    knowledge: "知识审核发布中心",
    access: "人员与账号",
    clinical: "我的待办",
    "clinical-governance": "临床知识治理",
    medication: "药事安全复核",
    diagnostic: "医技协同",
    quality: "质量风险概览",
    audit: "最近变化",
    tenant: "治理概览",
  } as const;
  return {
    heading: journey?.title ?? `${displayName}工作台`,
    marker: journey ? markerByKind[journey.kind] : "治理概览",
  };
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

  it("renders the platform operations view without customer-visible technical English", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-02T12:00:00Z"));
    renderWorkbench();

    expect(screen.getByRole("heading", { name: "平台管理员工作台" })).toBeInTheDocument();
    expect(screen.getByText("系统健康")).toBeInTheDocument();
    expect(screen.getByText("外部依赖连通")).toBeInTheDocument();
    expect(screen.getByText("服务运行保障可查看数据库和依赖明细")).toBeInTheDocument();
    expect(screen.getByText(/运行数据服务/)).toBeInTheDocument();
    expect(screen.queryByText(/数据库：/)).not.toBeInTheDocument();
    expect(screen.queryByText(/关系数据库/)).not.toBeInTheDocument();
    expect(screen.getAllByText(/知识关系同步/).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: /查看知识关系/ })).toBeInTheDocument();
    expect(screen.queryByText(/知识图谱投影/)).not.toBeInTheDocument();
    expect(screen.queryByText(/图谱投影能力开关/)).not.toBeInTheDocument();
    expect(screen.getByText("生效版本发布申请")).toBeInTheDocument();
    expect(screen.getByText(/操作人已登记/)).toBeInTheDocument();
    expect(screen.queryByText(/doctor-1/)).not.toBeInTheDocument();
    expect(screen.queryByText(legacyWorkbenchAggregationPlaceholder)).not.toBeInTheDocument();
    expect(screen.queryByText(legacyWorkbenchAggregationApiPlaceholder)).not.toBeInTheDocument();
    expect(screen.queryByText(/暂无工作台独立聚合/)).not.toBeInTheDocument();
  });

  it("renders an explicit responsibility-specific landing view for all four launch roles", () => {
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

  it("separates medical engine operations from clinical execution", () => {
    setLoadedState("engine-operator", "医疗引擎运营员");
    const knowledge = renderWorkbench();
    expect(screen.getByRole("heading", { name: "医疗引擎运营员工作台" })).toBeInTheDocument();
    expect(screen.getAllByText("知识审核发布中心").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: /进入知识生产/ })).toBeInTheDocument();
    expect(screen.getAllByText("质量问题与整改").length).toBeGreaterThan(0);
    expect(
      screen.getByText("进入质量风险概览核查指标口径、责任对象、整改进度和医保审核入口。"),
    ).toBeInTheDocument();
    expect(screen.queryByText("临床协同入口")).not.toBeInTheDocument();
    knowledge.unmount();

    setLoadedState("clinical-user", "临床使用者");
    renderWorkbench();
    expect(screen.getByRole("heading", { name: "临床使用者工作台" })).toBeInTheDocument();
    expect(screen.getByText("临床协同入口")).toBeInTheDocument();
    expect(screen.queryByText("知识审核发布中心")).not.toBeInTheDocument();
  });

  it("prioritizes my todo state for clinical users without fabricating task counts", () => {
    setLoadedState("clinical-user", "临床使用者");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "临床使用者工作台" })).toBeInTheDocument();
    expect(screen.getByText("我的待办")).toBeInTheDocument();
    const todoCard = screen.getByTestId("workbench-card-todo");
    expect(within(todoCard).getByText("无待办")).toBeInTheDocument();
    expect(within(todoCard).getByText("当前组织暂无待办")).toBeInTheDocument();
    expect(
      within(todoCard).getByText(
        "当前组织暂无待办；可进入患者路径、提醒与推荐、随访协同或消息通知查看实时事项。",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/发布治理状态/)).not.toBeInTheDocument();
    expect(
      screen.getByText(
        "进入患者路径、提醒与推荐、随访协同与消息通知；各页面展示对应真实数据和处理入口。",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("外部依赖连通")).not.toBeInTheDocument();
    expect(screen.queryByText("该域未启用")).not.toBeInTheDocument();
    expect(screen.queryByText(legacyWorkbenchSelfProofPattern)).not.toBeInTheDocument();
    expect(screen.getByText("临床协同入口")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "患者路径" }));
    expect(navigateSpy).toHaveBeenCalledWith("/pathway/patients");
  });

  it("does not query system or audit sources for clinical users without source permissions", () => {
    setLoadedState("clinical-user", "临床使用者");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "临床使用者工作台" })).toBeInTheDocument();
    expect(screen.getByText("我的待办")).toBeInTheDocument();
    expect(screen.getByText("临床协同入口")).toBeInTheDocument();
    expect(screen.queryByText("工作台暂时不可用")).not.toBeInTheDocument();
    expect(hookState.runtimeEnabledCalls.at(-1)).toBe(false);
    expect(hookState.auditEnabledCalls.at(-1)).toBe(false);
  });

  it("keeps the engine view focused on medical assets and quality work", () => {
    setLoadedState("engine-operator", "医疗引擎运营员");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "医疗引擎运营员工作台" })).toBeInTheDocument();
    expect(screen.getAllByText("知识审核发布中心").length).toBeGreaterThan(0);
    expect(screen.getAllByText("质量问题与整改").length).toBeGreaterThan(0);
    expect(
      screen.getByText(
        "追溯知识来源和派生关系，复核术语映射与发布影响；汇总数据以各治理页面为准。",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("外部依赖连通")).not.toBeInTheDocument();
    expect(screen.queryByText(/traceId/i)).not.toBeInTheDocument();
    expect(screen.queryByText("该域未启用")).not.toBeInTheDocument();
    expect(screen.queryByText(legacyWorkbenchAggregationPattern)).not.toBeInTheDocument();

    fireEvent.click(
      within(screen.getByTestId("workbench-card-engine-quality")).getByRole("button", {
        name: "质量问题与整改",
      }),
    );
    expect(navigateSpy).toHaveBeenCalledWith("/qc/alerts");
  });

  it("shows partial success when one source fails while keeping other source cards visible", () => {
    hookState.audit = {
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          data: {
            detail: "最近变化暂时不可用",
            traceId: "trace-dashboard-1",
          },
        },
      },
      refetch: vi.fn(),
    };

    renderWorkbench();

    expect(screen.getByText("部分来源暂时不可用")).toBeInTheDocument();
    expect(screen.getAllByText(/最近变化暂时不可用/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/ECONNREFUSED/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\/audit/)).not.toBeInTheDocument();
    expect(screen.queryByText(/trace-dashboard-1/)).not.toBeInTheDocument();
    expect(screen.queryByText(/追踪号/)).not.toBeInTheDocument();
    expect(screen.getAllByText("失败已留痕，可在审计证据中追溯。").length).toBeGreaterThan(0);
    expect(screen.getByText("系统健康")).toBeInTheDocument();
    expect(screen.getByText(/运行数据服务/)).toBeInTheDocument();
    expect(screen.queryByText(/关系数据库/)).not.toBeInTheDocument();
  });

  it("uses an actionable empty state instead of future-source promises", () => {
    hookState.audit = {
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    };

    renderWorkbench();

    const auditCard = screen.getByTestId("workbench-card-audit");
    expect(
      within(auditCard).getByText("当前组织暂无可展示内容，请确认组织范围或进入对应页面处理。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/后续来源上线|自动回灌/)).not.toBeInTheDocument();
  });

  it("renders a forbidden state when the loaded security profile cannot access workbench", () => {
    hookState.security = {
      data: {
        ...profile("clinical-user", "临床使用者"),
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
    setLoadedState("clinical-user", "临床使用者");

    renderWorkbench();

    const clinicalCard = screen.getByTestId("workbench-card-clinical");
    expect(within(clinicalCard).getByText("临床协同入口")).toBeInTheDocument();
    expect(within(clinicalCard).getByRole("button", { name: "患者路径" })).toBeInTheDocument();
    expect(screen.queryByText("该域未启用")).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\/workbench/)).not.toBeInTheDocument();
  });

  it("renders one role-specific primary action and three default filters", () => {
    renderWorkbench();

    expect(screen.getByRole("button", { name: /维护人员与账号/ })).toHaveClass("ant-btn-primary");
    expect(screen.queryByRole("button", { name: /管理账号/ })).not.toBeInTheDocument();
    expect(document.querySelectorAll(".ant-btn-primary")).toHaveLength(1);
    expect(screen.getAllByTestId(/^workbench-filter-/)).toHaveLength(3);
    expect(screen.getByText("组织范围")).toBeInTheDocument();
    expect(screen.getByText("上线状态")).toBeInTheDocument();
    expect(screen.queryByText("病种")).not.toBeInTheDocument();
    expect(screen.getByText("时间")).toBeInTheDocument();
  });

  it("uses responsibility-specific default filter dimensions for all launch roles", () => {
    const expectedFilterLabels = new Map([
      ["platform-admin", "上线状态"],
      ["engine-operator", "资产类型"],
      ["clinical-user", "临床场景"],
      ["auditor", "证据类型"],
    ]);

    ROLE_OPTIONS.forEach(({ code, name }) => {
      setLoadedState(code, name);

      const { unmount } = renderWorkbench();

      expect(screen.getAllByTestId(/^workbench-filter-/)).toHaveLength(3);
      expect(screen.getByText("组织范围")).toBeInTheDocument();
      expect(screen.getByText(expectedFilterLabels.get(code) ?? "")).toBeInTheDocument();
      expect(screen.getByText("时间")).toBeInTheDocument();
      expect(screen.queryByText("病种")).not.toBeInTheDocument();

      unmount();
    });
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
    setLoadedState("engine-operator", "医疗引擎运营员");

    renderWorkbench();

    fireEvent.click(within(screen.getByTestId("workbench-card-audit")).getByText("查看最近变化"));
    expect(navigateSpy).toHaveBeenCalledWith("/admin/audit");

    fireEvent.click(
      within(screen.getByTestId("workbench-card-engine-quality")).getByRole("button", {
        name: "质量问题与整改",
      }),
    );
    expect(navigateSpy).toHaveBeenCalledWith("/qc/alerts");
    expect(screen.queryByText(/\/api\/v1\/workbench/)).not.toBeInTheDocument();
  });

  it("shows lifecycle governance slices and weekly suggestions for platform users", () => {
    setLoadedState("platform-admin", "平台管理员");

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "平台管理员工作台" })).toBeInTheDocument();
    expect(screen.getByText("治理概览")).toBeInTheDocument();
    expect(screen.getAllByTestId(/^workbench-governance-slice-/)).toHaveLength(3);
    expect(screen.getByText("本周建议动作")).toBeInTheDocument();
    expect(screen.getByText("核对实施进度")).toBeInTheDocument();
    expect(screen.getByText("查看实施阶段、系统接入状态与上线准备项。")).toBeInTheDocument();
    expect(screen.queryByText("查看实施阶段、机构生效版本与上线准备项。")).not.toBeInTheDocument();
    expect(screen.getByText("核对服务运行保障")).toBeInTheDocument();
    expect(screen.queryByText("复核生效版本")).not.toBeInTheDocument();
    const weeklyActionButtons = screen.getAllByRole("button", { name: "进入" });
    fireEvent.click(weeklyActionButtons[1]);
    expect(navigateSpy).toHaveBeenLastCalledWith("/system/providers");
    expect(hookState.successPlanEnabledCalls).toContain(true);
  });

  it("shows actionable knowledge sync status when the runtime source has no graph dependency", () => {
    setLoadedState("platform-admin", "平台管理员");
    hookState.runtime = {
      ...hookState.runtime,
      data: {
        ...runtimeSnapshot,
        dependencies: runtimeSnapshot.dependencies.filter((item) => !item.key.includes("graph")),
      },
    };

    renderWorkbench();

    expect(screen.getByRole("heading", { name: "平台管理员工作台" })).toBeInTheDocument();
    expect(screen.getByText("知识关系同步来源待配置")).toBeInTheDocument();
    expect(
      screen.getByText(
        "当前运行状态未返回知识关系同步来源，请在服务运行保障中核查知识关系同步配置。",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/图谱投影配置/)).not.toBeInTheDocument();
    expect(screen.queryByText("未接入")).not.toBeInTheDocument();
  });
});
