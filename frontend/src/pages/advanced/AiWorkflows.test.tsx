import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { AxiosAdapter, InternalAxiosRequestConfig } from "axios";
import { afterEach, describe, expect, it } from "vitest";

import { apiClient } from "@/shared/api/client";
import AiWorkflows from "./AiWorkflows";

const originalAdapter = apiClient.defaults.adapter;

afterEach(() => {
  apiClient.defaults.adapter = originalAdapter;
});

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <AntdApp>
          <AiWorkflows />
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

function response(config: InternalAxiosRequestConfig, data: unknown) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: {},
    config,
  };
}

function securityProfile(permissionCodes: string[]) {
  return {
    data: {
      userId: "user-1",
      username: "测试用户",
      roles: [{ code: "platform-admin", displayName: "平台管理员" }],
      permissions: permissionCodes.map((code) => ({
        code,
        dimension: code.startsWith("menu.") ? "MENU" : "ACTION",
        target: code,
        displayName: code,
        risk: "LOW",
      })),
      menuKeys: permissionCodes.includes("menu.ai-workflows") ? ["ai-workflows"] : [],
      environmentKeys: ["test"],
      dataScope: {
        tenantId: "tenant-1",
        groupId: null,
        hospitalId: "hospital-1",
        campusId: null,
        siteId: null,
        departmentId: null,
        specialtyId: null,
      },
      mustChangePwd: false,
      mfaRequired: false,
      mfaBound: true,
    },
  };
}

const statusItems = [
  {
    capabilityCode: "knowledge.discovery",
    displayName: "临床知识关联发现",
    description: "从临床事实中检索并关联可信知识依据。",
    category: "知识资产",
    routeStrategy: "BASELINE",
    desensitizeStrategy: "DEFAULT",
    expectedSchema: null,
    fallbackOrder: ["BASELINE"],
    timeoutMs: 60000,
    rateLimitPerMinute: null,
    policyScopeType: "TENANT",
    policyScopeRef: "tenant-1",
    inherited: false,
    configured: false,
    fallbackAvailable: true,
    fallbackReason: "未配置专属策略，使用系统 B0 基线",
  },
  {
    capabilityCode: "rule.draft",
    displayName: "临床规则草案拟定",
    description: "基于可信依据生成待人工审核的规则草案。",
    category: "规则引擎",
    routeStrategy: "DISABLED",
    desensitizeStrategy: "MASK_ALL",
    expectedSchema: '{"required":["status"]}',
    fallbackOrder: [],
    timeoutMs: 60000,
    rateLimitPerMinute: null,
    policyScopeType: "HOSPITAL",
    policyScopeRef: "hospital-a",
    inherited: true,
    configured: true,
    fallbackAvailable: false,
    fallbackReason: "已被路由策略禁用",
  },
];

describe("AiWorkflows", () => {
  it("能力状态读取期间显示加载态而不是误报空态", async () => {
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(config, securityProfile(["menu.ai-workflows", "llm.read"]));
      }
      if (config.url === "/model-capabilities/status") {
        return new Promise(() => undefined);
      }
      throw new Error(`未预期接口: ${config.url ?? ""}`);
    }) as AxiosAdapter;

    renderPage();

    expect(await screen.findByLabelText("正在读取模型能力状态")).toBeInTheDocument();
    expect(screen.queryByText("当前组织没有已启用的模型能力")).not.toBeInTheDocument();
  });

  it("只读取真实能力状态，不暴露执行和管理入口", async () => {
    const requests: string[] = [];
    apiClient.defaults.adapter = (async (config) => {
      requests.push(`${config.method ?? "get"} ${config.url ?? ""}`);
      if (config.url === "/security/me") {
        return response(config, securityProfile(["menu.ai-workflows", "llm.read"]));
      }
      if (config.url === "/model-capabilities/status" && config.method === "get") {
        return response(config, { data: statusItems });
      }
      throw new Error(`未预期接口: ${config.method ?? ""} ${config.url ?? ""}`);
    }) as AxiosAdapter;

    renderPage();

    expect(await screen.findByRole("heading", { name: "模型能力" })).toBeInTheDocument();
    expect(await screen.findByText("临床知识关联发现")).toBeInTheDocument();
    expect(screen.getByText(/公网模型可在授权用途内使用患者上下文/)).toBeInTheDocument();
    expect(screen.getByText(/核心标识字段先遮蔽/)).toBeInTheDocument();
    expect(screen.getByText("临床规则草案拟定")).toBeInTheDocument();
    expect(screen.getAllByText("基础规则能力").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("模型能力已关闭")).toBeInTheDocument();
    expect(screen.getByText("默认脱敏")).toBeInTheDocument();
    expect(screen.getByText("全量掩码")).toBeInTheDocument();
    expect(screen.getAllByText("规则链路可用").length).toBeGreaterThan(0);
    expect(screen.getByText("医院:hospital-a")).toBeInTheDocument();
    expect(screen.getByText("继承配置")).toBeInTheDocument();
    expect(screen.getByText("未配置专属策略，使用系统无模型规则链路")).toBeInTheDocument();
    expect(screen.queryByText("基线可用")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /提交|运行|重试|配置|编辑|新增|保存/ })).toBeNull();
    expect(requests).toEqual(["get /security/me", "get /model-capabilities/status"]);
  });

  it("允许具备权限的实施人员为公网模型使用患者上下文配置外调安全策略", async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    apiClient.defaults.adapter = (async (config) => {
      requests.push(`${config.method ?? "get"} ${config.url ?? ""}`);
      if (config.url === "/security/me") {
        return response(
          config,
          securityProfile(["menu.ai-workflows", "llm.read", "llm.egress.manage"]),
        );
      }
      if (config.url === "/model-capabilities/status" && config.method === "get") {
        return response(config, {
          data: [
            {
              ...statusItems[0],
              capabilityCode: "clinical.explanation",
              displayName: "患者解释生成",
              routeStrategy: "EXTERNAL_MODEL",
              fallbackOrder: ["EXTERNAL_MODEL", "LOCAL_MODEL", "BASELINE"],
              desensitizeStrategy: "DEFAULT",
            },
          ],
        });
      }
      if (
        config.url === "/data-minimization/policies/model-egress/clinical.explanation" &&
        config.method === "put"
      ) {
        expect(JSON.parse(config.data as string)).toEqual({
          allowedFields: ["prompt"],
          sensitivityLevel: "HIGH",
          desensitizationRules: { prompt: "MASK_ALL" },
          confirmationThresholdLevel: "HIGH",
        });
        return response(config, {
          data: {
            capabilityCode: "clinical.explanation",
            allowedFields: '["prompt"]',
            sensitivityLevel: "HIGH",
          },
        });
      }
      if (
        config.url === "/data-minimization/policies/model-egress/confirmations" &&
        config.method === "post"
      ) {
        expect(JSON.parse(config.data as string)).toEqual({
          capabilityCode: "clinical.explanation",
          payloadHash: "sha256:payload-001",
          purpose: "向患者解释检查结果，仅使用已脱敏字段",
        });
        return response(config, {
          data: {
            id: 7,
            capabilityCode: "clinical.explanation",
            payloadHash: "sha256:payload-001",
            purpose: "向患者解释检查结果，仅使用已脱敏字段",
            confirmedBy: "operator-001",
            confirmedAt: "2026-06-25T19:45:00Z",
          },
        });
      }
      throw new Error(`未预期接口: ${config.method ?? ""} ${config.url ?? ""}`);
    }) as AxiosAdapter;

    renderPage();

    expect(await screen.findByText("患者解释生成")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "配置 患者解释生成 外调安全策略" }));
    const dialog = await screen.findByRole("dialog", { name: "配置外调安全策略" });
    expect(dialog).toHaveTextContent("公网外部模型可使用患者上下文");
    expect(dialog).not.toHaveTextContent("字段" + "出" + "域" + "预览");
    expect(within(dialog).getByText("模型使用字段预览")).toBeInTheDocument();
    expect(within(dialog).getAllByText("提示词内容").length).toBeGreaterThan(0);
    expect(within(dialog).getAllByText("全量遮蔽").length).toBeGreaterThan(0);
    expect(within(dialog).getByText("患者姓名")).toBeInTheDocument();
    expect(within(dialog).getAllByText("核心标识默认不提供给模型").length).toBeGreaterThan(0);
    expect(within(dialog).getByText(/高敏用途达到阈值时/)).toBeInTheDocument();
    expect(within(dialog).getByText(/每次发送给模型前需要责任确认/)).toBeInTheDocument();
    expect(within(dialog).getByText("本次外调用途确认")).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText("脱敏载荷摘要"), "sha256:payload-001");
    await user.type(
      within(dialog).getByLabelText("用途说明"),
      "向患者解释检查结果，仅使用已脱敏字段",
    );
    await user.click(within(dialog).getByRole("button", { name: "记录用途确认" }));
    await user.click(screen.getByRole("button", { name: "保存外调安全策略" }));

    await waitFor(() =>
      expect(requests).toContain(
        "put /data-minimization/policies/model-egress/clinical.explanation",
      ),
    );
    await waitFor(() =>
      expect(requests).toContain("post /data-minimization/policies/model-egress/confirmations"),
    );
  });

  it("部分能力不可用时显示诚实的部分成功状态", async () => {
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(config, securityProfile(["menu.ai-workflows", "llm.read"]));
      }
      if (config.url === "/model-capabilities/status") {
        return response(config, {
          data: [
            statusItems[0],
            {
              ...statusItems[1],
              capabilityCode: "quality.semantic-check",
              displayName: "病历内涵质控",
              routeStrategy: "EXTERNAL_MODEL",
              fallbackOrder: ["EXTERNAL_MODEL", "LOCAL_MODEL", "BASELINE"],
              fallbackReason: "外部模型未连接且没有可用基线",
            },
          ],
        });
      }
      throw new Error(`未预期接口: ${config.url ?? ""}`);
    }) as AxiosAdapter;

    renderPage();

    expect(await screen.findByText("部分模型能力当前不可用")).toBeInTheDocument();
    expect(
      screen.getByText("1 项能力没有可用路由或规则链路，其他能力仍可查看。"),
    ).toBeInTheDocument();
    expect(screen.getByText("暂不可用")).toBeInTheDocument();
  });

  it("无读取权限时显示拒绝态且不请求能力数据", async () => {
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(config, securityProfile(["menu.ai-workflows"]));
      }
      throw new Error(`无权限时不应请求: ${config.url ?? ""}`);
    }) as AxiosAdapter;

    renderPage();

    expect(await screen.findByText("无权查看模型能力")).toBeInTheDocument();
    expect(screen.getByText("需要模型能力读取权限。")).toBeInTheDocument();
  });

  it("真实能力为空时显示诚实空态", async () => {
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(config, securityProfile(["menu.ai-workflows", "llm.read"]));
      }
      if (config.url === "/model-capabilities/status") {
        return response(config, { data: [] });
      }
      throw new Error(`未预期接口: ${config.url ?? ""}`);
    }) as AxiosAdapter;

    renderPage();

    expect(await screen.findByText("当前组织没有已启用的模型能力")).toBeInTheDocument();
    expect(screen.getByText("未使用本地默认项补齐真实结果。")).toBeInTheDocument();
  });

  it("状态读取失败时显示错误并可重新读取", async () => {
    let attempts = 0;
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(config, securityProfile(["menu.ai-workflows", "llm.read"]));
      }
      if (config.url === "/model-capabilities/status") {
        attempts += 1;
        if (attempts === 1) {
          throw new Error("状态服务暂不可用");
        }
        return response(config, { data: statusItems.slice(0, 1) });
      }
      throw new Error(`未预期接口: ${config.url ?? ""}`);
    }) as AxiosAdapter;

    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("模型能力状态读取失败")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "重新读取" }));

    expect(await screen.findByText("临床知识关联发现")).toBeInTheDocument();
    await waitFor(() => expect(attempts).toBe(2));
  });
});
