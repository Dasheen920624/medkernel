import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import type { AxiosAdapter } from "axios";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { apiClient } from "@/shared/api/client";
import ReleaseGovernance from "./tenant/ReleaseGovernance";
import AuthoringAssets from "./tenant/AuthoringAssets";
import Followup from "./clinical/Followup";
import WorkflowTodos from "./clinical/WorkflowTodos";
import Notifications from "./clinical/Notifications";
import QcAlerts from "./quality/QcAlerts";
import InsuranceAudit from "./quality/InsuranceAudit";
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
import KnowledgeGovernance from "./quality/KnowledgeGovernance";
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
const legacyDelegatedUnavailableCopy = ["统一身份", "暂未接入"].join("");
const legacyDelegatedProviderPattern = new RegExp(`${["真实院方", " IdP"].join("")}|连接器|未接通`);
const legacyWorkbenchAggregationPlaceholder = ["真实工作台", "聚合数据待接入"].join("");
const legacyWorkbenchAggregationApiPlaceholder = ["等待真实", "聚合 API"].join("");

beforeEach(() => {
  apiClient.defaults.adapter = (() => new Promise(() => undefined)) as AxiosAdapter;
});

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
            message: "院方统一身份入口已开放，请由信息科在身份来源完成配置后启用。",
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
              : [{ tenantId: "t-1", name: "平台治理入口（唯一内置）", kind: "PLATFORM" }],
            platformTenant: { tenantId: "t-1", name: "平台治理入口（唯一内置）", kind: "PLATFORM" },
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
  it("renders the unified release governance console", () => {
    renderPage(<ReleaseGovernance />);
    expect(screen.getByRole("heading", { name: "机构生效版本" })).toBeInTheDocument();
    expect(screen.queryByText("配置" + "包与发布")).not.toBeInTheDocument();
  });

  it("renders the unified authoring asset library", () => {
    renderPage(<AuthoringAssets />);
    expect(screen.getByRole("heading", { name: "统一资产库" })).toBeInTheDocument();
    expect(screen.getByText("正在加载统一资产库")).toBeInTheDocument();
  });

  it("renders the tenant adapter-hub console", () => {
    renderPage(<AdapterHub />);
    expect(screen.getByRole("heading", { name: "系统接入" })).toBeInTheDocument();
    expect(screen.getByText("正在加载系统接入")).toBeInTheDocument();
  });

  it("renders the clinical embed-launch console in fallback isolation state", () => {
    renderPage(
      <MemoryRouter
        initialEntries={["/embed/launch"]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <EmbedLaunch />
      </MemoryRouter>,
    );
    expect(screen.getByText(/临床建议会话已安全隔离/)).toBeInTheDocument();
  });

  it("renders the clinical workflow-todos console with the real empty state", async () => {
    mockWorkflowCollaboration();
    renderPage(<WorkflowTodos />);
    expect(screen.getByRole("heading", { name: "协同任务" })).toBeInTheDocument();
    expect(await screen.findByText("当前暂无协同待办")).toBeInTheDocument();
    expect(screen.queryByText("待办接口尚未接入")).not.toBeInTheDocument();
  });

  it("renders the clinical notifications console with the real empty state", async () => {
    mockWorkflowCollaboration();
    renderPage(<Notifications />);
    expect(screen.getByRole("heading", { name: "消息通知" })).toBeInTheDocument();
    expect(await screen.findByText("当前暂无通知")).toBeInTheDocument();
    expect(screen.queryByText("通知接口尚未接入")).not.toBeInTheDocument();
  });

  it("renders the clinical followup console without local demo plans", () => {
    renderPage(<Followup />);
    expect(screen.getByRole("heading", { name: "随访协同" })).toBeInTheDocument();
    expect(screen.queryByText("FP-2026001")).not.toBeInTheDocument();
    expect(screen.getByText("当前暂无随访计划")).toBeInTheDocument();
  });

  it("renders the quality qc-alerts page with the real empty state", () => {
    renderPage(<QcAlerts />);
    expect(screen.getByRole("heading", { name: "质量问题" })).toBeInTheDocument();
    expect(screen.getByText("当前筛选下暂无真实质量问题")).toBeInTheDocument();
  });

  it("renders the quality insurance-audit page with the real empty state", () => {
    renderPage(<InsuranceAudit />);
    expect(screen.getByRole("heading", { name: "医保智能审核" })).toBeInTheDocument();
    expect(screen.getByText("当前筛选下暂无真实医保问题")).toBeInTheDocument();
  });

  it("renders the compliance admin-users console", () => {
    renderPage(<AdminUsers />);
    expect(screen.getByRole("heading", { name: "人员与账号" })).toBeInTheDocument();
    expect(screen.getByText("正在读取人员主数据")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /刷新/ })).toBeInTheDocument();
  });

  it("renders the knowledge graph page with projection-source messaging", () => {
    renderPage(<GraphExplore />);
    expect(screen.getByRole("heading", { name: "图谱查询" })).toBeInTheDocument();
    expect(screen.getByText("关系库权威源的可重建投影")).toBeInTheDocument();
  });

  it("renders the model capability status page", () => {
    renderPage(<AiWorkflows />);
    expect(screen.getByRole("heading", { name: "模型能力" })).toBeInTheDocument();
  });

  it("renders the knowledge provenance console", () => {
    renderPage(
      <MemoryRouter
        initialEntries={["/advanced/provenance"]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Provenance />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: "知识来源追溯" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText("输入知识主题或知识身份")).toBeInTheDocument();
    expect(screen.getByText("知识身份")).toBeInTheDocument();
  });

  it("renders the dashboard workbench without the old aggregation-api placeholder", () => {
    renderPage(
      <MemoryRouter
        initialEntries={["/dashboard"]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Dashboard />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: "工作台" })).toBeInTheDocument();
    expect(screen.getByText("正在确认当前角色")).toBeInTheDocument();
    expect(screen.queryByText(legacyWorkbenchAggregationPlaceholder)).toBeNull();
    expect(screen.queryByText(legacyWorkbenchAggregationApiPlaceholder)).toBeNull();
    expect(screen.queryByText("本周建议动作")).toBeNull();
    expect(screen.queryByText("验收自检")).toBeNull();
  });

  it("renders the login page as a focused identity entry", async () => {
    mockDelegatedAuthStatus();
    renderPage(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Login />
      </MemoryRouter>,
    );

    expect(await screen.findByRole("heading", { name: "登录平台治理" })).toBeInTheDocument();
    expect(screen.getByText("MedKernel")).toBeInTheDocument();
    expect(screen.getByText("使用平台治理账号继续")).toBeInTheDocument();
    expect(await screen.findByText("平台治理入口")).toBeInTheDocument();
    expect(screen.queryByLabelText("登录类型切换")).not.toBeInTheDocument();
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
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Login />
      </MemoryRouter>,
    );

    expect(await screen.findByLabelText("登录类型切换")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "机构用户" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "平台治理" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(await screen.findByRole("button", { name: /集团总院/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "院方统一身份认证" }));

    expect(await screen.findByText("统一身份服务待配置")).toBeInTheDocument();
    expect(screen.queryByText(legacyDelegatedUnavailableCopy)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "开放式身份认证（OIDC）（待配置）" })).toBeDisabled();
    expect(screen.queryByText(legacyDelegatedProviderPattern)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "CAS（待院方配置）" })).not.toBeInTheDocument();
  });

  it("renders the quality qc-eval-results console", () => {
    renderPage(<QcEvalResults />);
    expect(screen.getByRole("heading", { name: "质量问题来源" })).toBeInTheDocument();
    expect(screen.getByText("当前筛选下暂无真实评价结果")).toBeInTheDocument();
  });

  it("renders the quality qc-eval-sets simulation with real snapshot filters", async () => {
    renderPage(<QcEvalSets />);
    expect(screen.getByRole("heading", { name: "评估指标库" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "仿真评估" }));

    expect(screen.getByText("患者信息")).toBeInTheDocument();
    expect(screen.getByText("就诊信息")).toBeInTheDocument();
    expect(screen.queryByText("临床快照 ID")).not.toBeInTheDocument();
    expect(screen.getByText(/输入患者信息或就诊信息后读取已生效临床快照/)).toBeInTheDocument();
  });

  it("renders the knowledge governance page through the real candidate loading state", () => {
    renderPage(<KnowledgeGovernance />);
    expect(screen.getByRole("heading", { name: "知识审核发布中心" })).toBeInTheDocument();
    expect(screen.getByText("正在加载知识候选审核")).toBeInTheDocument();
  });

  it("renders the clinical patient-pathways console", () => {
    renderPage(<PatientPathways />);
    expect(screen.getByRole("heading", { name: "患者路径" })).toBeInTheDocument();
    expect(screen.getByText("正在加载患者路径")).toBeInTheDocument();
  });

  it("renders the clinical mpi console", () => {
    renderPage(
      <MemoryRouter
        initialEntries={["/mpi"]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Mpi />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: "患者索引" })).toBeInTheDocument();
    expect(screen.getByText(/活跃患者主索引/)).toBeInTheDocument();
  });

  it("renders the clinical reminder and recommendation page", () => {
    renderPage(
      <MemoryRouter
        initialEntries={["/cdss/fatigue"]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <CdssFatigue />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: "提醒与推荐" })).toBeInTheDocument();
    expect(screen.getByText("全部状态")).toBeInTheDocument();
  });

  it("renders the tenant pathway configuration page", () => {
    renderPage(<PathwayTemplates />);
    expect(screen.getByRole("heading", { name: "临床路径库" })).toBeInTheDocument();
    expect(screen.getByText("适用病种身份")).toBeInTheDocument();
  });

  it("renders the tenant rule configuration page", () => {
    renderPage(<RuleDefinitions />);
    expect(screen.getByRole("heading", { name: "临床规则" })).toBeInTheDocument();
    expect(screen.getByText("全部评级")).toBeInTheDocument();
  });

  it("renders the compliance admin-audit console", () => {
    renderPage(<AdminAudit />);
    expect(screen.getByRole("heading", { name: "审计与证据" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "审计事件" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "确认导出范围" })).not.toBeInTheDocument();
  });

  it("renders the tenant terminology-mapping console", () => {
    renderPage(<TerminologyMapping />);
    expect(screen.getByRole("heading", { name: "术语字典" })).toBeInTheDocument();
  });
});
