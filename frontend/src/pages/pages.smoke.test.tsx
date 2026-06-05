import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import type { AxiosAdapter } from "axios";
import { afterEach, describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { apiClient } from "@/shared/api/client";
import ConfigPackages from "./tenant/ConfigPackages";
import Followup from "./clinical/Followup";
import WorkflowTodos from "./clinical/WorkflowTodos";
import Notifications from "./clinical/Notifications";
import QcAlerts from "./quality/QcAlerts";
import AdminUsers from "./compliance/AdminUsers";
import GraphExplore from "./advanced/GraphExplore";
import AiWorkflows from "./advanced/AiWorkflows";
import Provenance from "./advanced/Provenance";
import Dashboard from "./Dashboard";
import Login from "./Login";
import AdapterHub from "./tenant/AdapterHub";
import EmbedLaunch from "./clinical/EmbedLaunch";
import QcEvalResults from "./quality/QcEvalResults";
import QcEvalSets from "./quality/QcEvalSets";
import PatientPathways from "./clinical/PatientPathways";
import Mpi from "./clinical/Mpi";
import CdssFatigue from "./clinical/CdssFatigue";
import PathwayTemplates from "./tenant/PathwayTemplates";
import RuleDefinitions from "./tenant/RuleDefinitions";
import AdminAudit from "./compliance/AdminAudit";
import TerminologyMapping from "./tenant/TerminologyMapping";

const testQueryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

const originalApiAdapter = apiClient.defaults.adapter;

afterEach(() => {
  apiClient.defaults.adapter = originalApiAdapter;
  testQueryClient.clear();
});

function mockDelegatedAuthStatus(options?: { hasCustomerTenants?: boolean }) {
  apiClient.defaults.adapter = (async (config) => {
    if (config.url === "/auth/delegated/status") {
      return {
        data: {
          data: {
            mode: "BOTH",
            enabled: true,
            status: "NOT_CONNECTED",
            providers: ["OIDC", "CAS", "SAML", "国密CA"],
            message: "院方统一身份入口已开放，但当前未配置真实 IdP 连接器。",
          },
        },
        status: 200,
        statusText: "OK",
        headers: {},
        config,
      };
    }
    if (config.url === "/auth/login-tenants") {
      const hasCustomerTenants = options?.hasCustomerTenants ?? false;
      return {
        data: {
          data: {
            primaryTenants: hasCustomerTenants
              ? [{ tenantId: "t-hospital", name: "集团总院", kind: "CUSTOMER" }]
              : [{ tenantId: "t-1", name: "平台主租户（唯一内置）", kind: "PLATFORM" }],
            platformTenant: { tenantId: "t-1", name: "平台主租户（唯一内置）", kind: "PLATFORM" },
            hasCustomerTenants,
          },
        },
        status: 200,
        statusText: "OK",
        headers: {},
        config,
      };
    }
    if (config.url === "/experience/theme-preference") {
      return {
        data: {
          data: {
            mode: "DEFAULT",
            version: 1,
            updatedAt: null,
            updatedBy: null,
          },
        },
        status: 200,
        statusText: "OK",
        headers: {},
        config,
      };
    }
    throw new Error(`未预期的测试接口请求：${config.url ?? ""}`);
  }) as AxiosAdapter;
}

function mockWorkflowCollaboration() {
  apiClient.defaults.adapter = (async (config) => {
    if (config.url === "/engine/workflow/todos") {
      return {
        data: {
          data: {
            items: [],
            page: 1,
            size: 10,
            total: 0,
            hasNext: false,
          },
        },
        status: 200,
        statusText: "OK",
        headers: {},
        config,
      };
    }
    if (config.url === "/engine/notifications") {
      return {
        data: {
          data: {
            items: [],
            page: 1,
            size: 10,
            total: 0,
            hasNext: false,
          },
        },
        status: 200,
        statusText: "OK",
        headers: {},
        config,
      };
    }
    throw new Error(`未预期的测试接口请求：${config.url ?? ""}`);
  }) as AxiosAdapter;
}

function renderPage(page: React.ReactElement) {
  return render(
    <QueryClientProvider client={testQueryClient}>
      <ConfigProvider>
        <AntdApp>{page}</AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

describe("page smoke coverage", () => {
  it("renders the tenant config-packages console", () => {
    renderPage(<ConfigPackages />);
    expect(screen.getByRole("heading", { name: "配置包中心" })).toBeInTheDocument();
    expect(screen.getByText("正在加载配置包中心")).toBeInTheDocument();
  });

  it("renders the tenant adapter-hub console", () => {
    renderPage(<AdapterHub />);
    expect(screen.getByRole("heading", { name: "适配器中心" })).toBeInTheDocument();
    expect(screen.getByText("正在加载适配器中心")).toBeInTheDocument();
  });

  it("renders the clinical embed-launch console in fallback isolation state", () => {
    renderPage(
      <MemoryRouter initialEntries={["/embed/launch"]}>
        <EmbedLaunch />
      </MemoryRouter>,
    );
    expect(screen.getByText(/页面嵌入式临床建议会话已安全隔离/)).toBeInTheDocument();
  });

  it("renders the clinical workflow-todos console with the real empty state", async () => {
    mockWorkflowCollaboration();
    renderPage(<WorkflowTodos />);
    expect(screen.getByRole("heading", { name: "工作流协同待办中心" })).toBeInTheDocument();
    expect(await screen.findByText("当前暂无协同待办")).toBeInTheDocument();
    expect(screen.queryByText("待办接口尚未接入")).not.toBeInTheDocument();
  });

  it("renders the clinical notifications console with the real empty state", async () => {
    mockWorkflowCollaboration();
    renderPage(<Notifications />);
    expect(screen.getByRole("heading", { name: "通知中心" })).toBeInTheDocument();
    expect(await screen.findByText("当前暂无通知")).toBeInTheDocument();
    expect(screen.queryByText("通知接口尚未接入")).not.toBeInTheDocument();
  });

  it("renders the clinical followup console without local demo plans", () => {
    renderPage(<Followup />);
    expect(screen.getByRole("heading", { name: "智能随访工作台" })).toBeInTheDocument();
    expect(screen.queryByText("FP-2026001")).not.toBeInTheDocument();
    expect(screen.getByText("当前暂无随访计划")).toBeInTheDocument();
  });

  it("renders the quality qc-alerts page with the real empty state", () => {
    renderPage(<QcAlerts />);
    expect(screen.getByRole("heading", { name: "质控预警" })).toBeInTheDocument();
    expect(screen.getByText("当前筛选下暂无真实质控预警")).toBeInTheDocument();
  });

  it("renders the compliance admin-users console", () => {
    renderPage(<AdminUsers />);
    expect(screen.getByRole("heading", { name: "用户与角色数据范围管理" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "新增角色分配关系" })).toBeInTheDocument();
  });

  it("renders an advanced tool page with advanced-only messaging", () => {
    renderPage(<GraphExplore />);
    expect(screen.getByRole("heading", { name: "图谱查询" })).toBeInTheDocument();
    expect(screen.getAllByText(/高级工具/).length).toBeGreaterThan(0);
  });

  it("renders the advanced ai-workflows engine workbench", () => {
    renderPage(<AiWorkflows />);
    expect(screen.getByRole("heading", { name: "大模型网关与 AI 工作流配置" })).toBeInTheDocument();
    expect(screen.getByText(/混合路由去向策略/)).toBeInTheDocument();
    expect(screen.getByText(/AI 推理脱敏与降级物理沙盒输入端/)).toBeInTheDocument();
  });

  it("renders the advanced provenance audit console", () => {
    renderPage(<Provenance />);
    expect(screen.getByRole("heading", { name: "来源与临床证据追溯" })).toBeInTheDocument();
    expect(screen.getAllByText("真实证据快照").length).toBeGreaterThan(0);
    expect(screen.getByText("真实审计事件")).toBeInTheDocument();
  });

  it("renders the dashboard workbench without the old aggregation-api placeholder", () => {
    renderPage(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Dashboard />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: "工作台" })).toBeInTheDocument();
    expect(screen.getByText("正在确认当前角色")).toBeInTheDocument();
    expect(screen.queryByText("真实工作台聚合数据待接入")).toBeNull();
    expect(screen.queryByText("等待真实聚合 API")).toBeNull();
    expect(screen.queryByText("本周建议动作")).toBeNull();
    expect(screen.queryByText("演示与校验")).toBeNull();
  });

  it("renders the login page as a focused identity entry", async () => {
    mockDelegatedAuthStatus();
    renderPage(
      <MemoryRouter>
        <Login />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "登录工作台" })).toBeInTheDocument();
    expect(screen.getByText("MedKernel")).toBeInTheDocument();
    expect(screen.getByText("使用平台账号继续")).toBeInTheDocument();
    expect(screen.getByText("平台主租户自动进入")).toBeInTheDocument();
    expect(screen.getByText("平台主租户（唯一内置）")).toBeInTheDocument();
    expect(screen.queryByLabelText("登录类型切换")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("租户标识")).not.toBeInTheDocument();
    expect(screen.queryByText("安全审计已开启")).toBeNull();
    expect(screen.getByRole("button", { name: /默认/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /进入工作台/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "登录帮助" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "院方统一身份认证" })).not.toBeInTheDocument();
    expect(screen.queryByText("首次登录")).toBeNull();
  });

  it("renders delegated identity only for customer tenant login", async () => {
    mockDelegatedAuthStatus({ hasCustomerTenants: true });
    renderPage(
      <MemoryRouter>
        <Login />
      </MemoryRouter>,
    );

    expect(await screen.findByLabelText("登录类型切换")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "集团院内户" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "主平台户" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(await screen.findByRole("button", { name: /集团总院/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "院方统一身份认证" }));

    expect(await screen.findByText("统一身份暂未接入")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "OIDC（NOT_CONNECTED）" })).toBeDisabled();
    expect(screen.getByText(/真实院方 IdP/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "CAS（待院方配置）" })).not.toBeInTheDocument();
  });

  it("renders the quality qc-eval-results console", () => {
    renderPage(<QcEvalResults />);
    expect(screen.getByRole("heading", { name: "评估结果" })).toBeInTheDocument();
    expect(screen.getByText(/真实评估结果总数/)).toBeInTheDocument();
  });

  it("renders the quality qc-eval-sets scan with real snapshot filters", async () => {
    renderPage(<QcEvalSets />);
    expect(screen.getByRole("heading", { name: "评估指标库" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /质控扫描试运行/ }));

    expect(screen.getByText("患者 ID")).toBeInTheDocument();
    expect(screen.getByText("就诊 ID")).toBeInTheDocument();
    expect(screen.getByText(/请输入患者 ID 或就诊 ID 读取真实快照/)).toBeInTheDocument();
  });

  it("renders the clinical patient-pathways console", () => {
    renderPage(<PatientPathways />);
    expect(screen.getByRole("heading", { name: "患者路径" })).toBeInTheDocument();
    expect(screen.getByText("正在加载患者路径")).toBeInTheDocument();
  });

  it("renders the clinical mpi console", () => {
    renderPage(<Mpi />);
    expect(screen.getByRole("heading", { name: "患者主索引 MPI" })).toBeInTheDocument();
    expect(screen.getByText(/活跃患者主索引/)).toBeInTheDocument();
  });

  it("renders the clinical cdss-fatigue console", () => {
    renderPage(<CdssFatigue />);
    expect(screen.getByRole("heading", { name: "智能建议治理" })).toBeInTheDocument();
    expect(screen.getByText("全部状态")).toBeInTheDocument();
  });

  it("renders the tenant pathway-templates console", () => {
    renderPage(<PathwayTemplates />);
    expect(screen.getByRole("heading", { name: "路径中枢" })).toBeInTheDocument();
    expect(screen.getByText("病种编码")).toBeInTheDocument();
  });

  it("renders the tenant rule-definitions console", () => {
    renderPage(<RuleDefinitions />);
    expect(screen.getByRole("heading", { name: "规则中枢" })).toBeInTheDocument();
    expect(screen.getByText("全部评级")).toBeInTheDocument();
  });

  it("renders the compliance admin-audit console", () => {
    renderPage(<AdminAudit />);
    expect(screen.getByRole("heading", { name: "审计日志" })).toBeInTheDocument();
    expect(screen.getByText("导出审计快照")).toBeInTheDocument();
  });

  it("renders the tenant terminology-mapping console", () => {
    renderPage(<TerminologyMapping />);
    expect(screen.getByRole("heading", { name: "字典映射" })).toBeInTheDocument();
  });
});
