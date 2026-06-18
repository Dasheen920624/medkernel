import { fireEvent, render, screen, within } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { MemoryRouter } from "react-router-dom";
import type * as ReactRouterDom from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ReadinessValidation from "./ReadinessValidation";
import type { RuntimeOperationsSnapshot, SecurityProfile } from "@/shared/api/hooks";

const hookState = vi.hoisted(() => ({
  security: {} as Record<string, unknown>,
  runtime: {} as Record<string, unknown>,
  knowledgeReadiness: {} as Record<string, unknown>,
  runtimeEnabledCalls: [] as unknown[],
  knowledgeReadinessEnabledCalls: [] as unknown[],
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => hookState.security,
  useRuntimeOperations: (enabled?: boolean) => {
    hookState.runtimeEnabledCalls.push(enabled);
    return hookState.runtime;
  },
  useKnowledgeProductionReadiness: (_params?: unknown, enabled?: boolean) => {
    hookState.knowledgeReadinessEnabledCalls.push(enabled);
    return hookState.knowledgeReadiness;
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
    <MemoryRouter
      initialEntries={["/workbench/readiness-validation"]}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
    >
      <ConfigProvider>
        <ReadinessValidation />
      </ConfigProvider>
    </MemoryRouter>,
  );
}

function profile(roleCode = "implementation-operator"): SecurityProfile {
  return {
    userId: `${roleCode}-1`,
    username: roleCode,
    roles: [
      {
        code: roleCode,
        displayName: "实施运维员",
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
        code: "workbench:readiness:view",
        dimension: "ACTION",
        target: "workbench:readiness:view",
        displayName: "查看验收自检",
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

const knowledgeReadiness = {
  tenantId: "t-1",
  producer: "API_MODEL",
  capabilityCode: "rule.draft",
  providerCode: null,
  deploymentForm: "HOSPITAL_RUNTIME",
  ready: false,
  modelInvocationAllowed: false,
  items: [
    {
      code: "LITERATURE_ROOT",
      ready: false,
      required: true,
      message: "平台知识文献资料库根地址未配置",
      evidence: "medkernel.knowledge.literature.material-root-uri=<empty>",
    },
    {
      code: "DEPLOYMENT_FORM",
      ready: false,
      required: true,
      message: "当前不是 PRODUCTION_CENTER，禁止外部 API 模型生产知识",
      evidence: "deploymentForm=HOSPITAL_RUNTIME",
    },
    {
      code: "MODEL_PROVIDER",
      ready: false,
      required: true,
      message: "未找到匹配且启用的模型 provider",
      evidence: "producer=API_MODEL",
    },
    {
      code: "REGRESSION_BASELINE",
      ready: true,
      required: true,
      message: "医学回归基准集已配置",
      evidence: "caseCount=3",
    },
    {
      code: "MODEL_EVALUATION",
      ready: false,
      required: true,
      message: "provider/模型版本未找到 PASSED 医学回归评测",
      evidence: "provider=<missing>",
    },
    {
      code: "EGRESS_GOVERNANCE",
      ready: false,
      required: true,
      message: "外部模型生产缺少出域字段白名单",
      evidence: "capabilityCode=rule.draft",
    },
    {
      code: "MODEL_POLICY",
      ready: false,
      required: true,
      message: "模型能力策略未配置，不能进入正式模型生产",
      evidence: "capabilityCode=rule.draft",
    },
    {
      code: "VERSION_TRIPLE",
      ready: false,
      required: true,
      message: "模型生产任务必须声明 prompt/tool/model 版本三元组",
      evidence: "<empty>",
    },
    {
      code: "P6_ACCEPTANCE",
      ready: false,
      required: true,
      message: "P6 独立验收未放行，禁止正式模型生成知识",
      evidence: "medkernel.knowledge.production.p6-independent-acceptance=false",
    },
  ],
};

function setLoadedState(roleCode = "implementation-operator") {
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
  hookState.knowledgeReadiness = {
    data: knowledgeReadiness,
    isLoading: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  };
}

describe("ReadinessValidation", () => {
  beforeEach(() => {
    setLoadedState();
    hookState.runtimeEnabledCalls = [];
    hookState.knowledgeReadinessEnabledCalls = [];
    navigateSpy.mockReset();
  });

  it("renders honest readiness counts from runtime operations without workbench endpoints", () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "验收自检" })).toBeInTheDocument();
    expect(screen.getByText("3 PASS / 11 BLOCK / 2 未启用")).toBeInTheDocument();
    expect(screen.getByText("存在阻塞项，验收前需处理")).toBeInTheDocument();
    expect(screen.getByTestId("readiness-validation-tabs")).toBeInTheDocument();
    expect(screen.getAllByTestId(/^readiness-validation-filter-/)).toHaveLength(3);
    expect(screen.queryByText(/\/api\/v1\/workbench/)).not.toBeInTheDocument();
  });

  it("uses the canonical route contract for governance administrators", () => {
    setLoadedState("organization-admin");

    renderPage();

    expect(screen.queryByText("当前权限不足")).not.toBeInTheDocument();
    expect(screen.getByText("3 PASS / 11 BLOCK / 2 未启用")).toBeInTheDocument();
    expect(hookState.runtimeEnabledCalls.at(-1)).toBe(true);
    expect(hookState.knowledgeReadinessEnabledCalls.at(-1)).toBe(true);
  });

  it("keeps blockers actionable with Chinese reasons and real repair links", () => {
    renderPage();

    const providerRow = screen.getByTestId("readiness-validation-item-provider-his");
    expect(within(providerRow).getByText("BLOCK")).toBeInTheDocument();
    expect(within(providerRow).getByText(/未接入真实 HIS 连接器/)).toBeInTheDocument();
    const backupRow = screen.getByTestId("readiness-validation-item-backup-readiness");
    expect(within(backupRow).getByText("BLOCK")).toBeInTheDocument();
    expect(within(backupRow).getByText(/尚未提供隔离恢复演练证据/)).toBeInTheDocument();

    fireEvent.click(within(providerRow).getByRole("button", { name: "去修复" }));

    expect(navigateSpy).toHaveBeenCalledWith("/system/providers");
  });

  it("shows all nine production readiness gates with real configuration destinations", () => {
    renderPage();

    const literatureRoot = screen.getByTestId(
      "readiness-validation-item-knowledge-LITERATURE_ROOT",
    );
    expect(within(literatureRoot).getByText("文献资料库根 URI")).toBeInTheDocument();
    expect(within(literatureRoot).getByText("BLOCK")).toBeInTheDocument();
    expect(within(literatureRoot).getByText(/平台知识文献资料库根地址未配置/)).toBeInTheDocument();

    const regressionBaseline = screen.getByTestId(
      "readiness-validation-item-knowledge-REGRESSION_BASELINE",
    );
    expect(within(regressionBaseline).getByText("医学回归基准集")).toBeInTheDocument();
    expect(within(regressionBaseline).getByText("PASS")).toBeInTheDocument();
    expect(within(regressionBaseline).getByText(/caseCount=3/)).toBeInTheDocument();

    const policy = screen.getByTestId("readiness-validation-item-knowledge-MODEL_POLICY");
    expect(within(policy).getByText("能力策略")).toBeInTheDocument();
    fireEvent.click(within(policy).getByRole("button", { name: "去修复" }));
    expect(navigateSpy).toHaveBeenLastCalledWith("/advanced/ai-workflows");

    fireEvent.click(within(literatureRoot).getByRole("button", { name: "去修复" }));
    expect(navigateSpy).toHaveBeenLastCalledWith("/security/baseline");
  });

  it("shows a forbidden state for clinical roles and does not query runtime sources", () => {
    hookState.security = {
      data: {
        ...profile("clinical-decision-user"),
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
    expect(hookState.knowledgeReadinessEnabledCalls.at(-1)).toBe(false);
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

    fireEvent.click(within(screen.getByTestId("readiness-validation-tabs")).getByText("工作台"));

    expect(navigateSpy).toHaveBeenCalledWith("/dashboard");
  });
});
