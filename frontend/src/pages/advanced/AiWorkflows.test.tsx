import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
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
  const view = render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <AntdApp>
          <AiWorkflows />
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
  return { ...view, queryClient };
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
      roles: [],
      permissions: permissionCodes.map((code) => ({
        code,
        dimension: "ACTION",
        target: code,
        displayName: code,
        risk: "LOW",
      })),
      menuKeys: ["ai-workflows"],
      environmentKeys: [],
      dataScope: {
        tenantId: "tenant-1",
        groupId: null,
        hospitalId: null,
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

describe("AiWorkflows", () => {
  it("只展示后端返回的真实能力，不用本地默认项补齐", async () => {
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(config, securityProfile(["llm.read", "llm.execute"]));
      }
      if (config.url === "/model-capabilities/status") {
        return response(config, {
          data: [
            {
              capabilityCode: "knowledge.discovery",
              displayName: "后端目录中的知识发现",
              description: "后端返回的能力说明",
              category: "知识资产",
              routeStrategy: "BASELINE",
              desensitizeStrategy: "DEFAULT",
              expectedSchema: null,
              configured: false,
              fallbackAvailable: true,
              fallbackReason: "未配置专属策略，使用系统 B0 基线",
            },
          ],
        });
      }
      throw new Error(`未预期接口: ${config.url ?? ""}`);
    }) as AxiosAdapter;

    renderPage();

    expect(await screen.findByText("后端目录中的知识发现")).toBeInTheDocument();
    expect(screen.getByText("后端返回的能力说明")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /配置策略/ })).toBeNull();
    expect(screen.getByText("1/1")).toBeInTheDocument();
    expect(screen.getByText("系统默认")).toBeInTheDocument();
  });

  it("不在浏览器伪造脱敏结果，只展示后端已执行的策略证据", async () => {
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(config, securityProfile(["llm.read", "llm.execute"]));
      }
      if (config.url === "/model-capabilities/status") {
        return response(config, {
          data: [
            {
              capabilityCode: "knowledge.extract",
              displayName: "电子病历语义实体提取",
              description: "提取病历中的结构化临床事实",
              category: "语义抽取",
              routeStrategy: "BASELINE",
              desensitizeStrategy: "DEFAULT",
              expectedSchema: null,
              configured: false,
              fallbackAvailable: true,
              fallbackReason: "使用系统 B0 基线",
            },
          ],
        });
      }
      if (config.url === "/model-capabilities/tasks" && config.method === "post") {
        return response(config, {
          data: {
            taskId: "task-privacy",
            status: "DEGRADED",
            outputContent: '{"status":"DEGRADED"}',
            modelMode: "B0",
            modelVersion: "B0-Deterministic-Baseline",
            promptVersion: "baseline",
            sourceCitations: "[]",
            confidence: null,
            riskLevel: "LOW",
            fallbackUsed: true,
            fallbackReason: "当前未接入真实模型 provider",
            timeCostMs: 8,
            traceId: "trace-privacy",
          },
        });
      }
      throw new Error(`未预期接口: ${config.method ?? ""} ${config.url ?? ""}`);
    }) as AxiosAdapter;

    const user = userEvent.setup();
    renderPage();

    await user.type(
      await screen.findByLabelText("运行输入（请使用已脱敏文本）"),
      "手机号13800138000",
    );
    await user.click(screen.getByRole("button", { name: /提交网关任务/ }));

    expect(await screen.findByText("后端已执行 DEFAULT 脱敏策略")).toBeInTheDocument();
    expect(screen.queryByText(/138\*{4}8000/)).toBeNull();
  });

  it("保存策略调用后端持久化端点", async () => {
    let saved = false;
    let putBody: unknown;
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(config, securityProfile(["llm.read", "llm.execute", "llm.manage"]));
      }
      if (config.url === "/model-capabilities/status") {
        return response(config, {
          data: [
            {
              capabilityCode: "knowledge.extract",
              displayName: "电子病历语义实体提取",
              description: "提取病历中的结构化临床事实",
              category: "语义抽取",
              routeStrategy: "BASELINE",
              desensitizeStrategy: "DEFAULT",
              expectedSchema: '{"required":["status","candidates"]}',
              configured: saved,
              fallbackAvailable: true,
              fallbackReason: "正常可用",
            },
          ],
        });
      }
      if (
        config.method === "put" &&
        config.url === "/model-capabilities/policies/knowledge.extract"
      ) {
        saved = true;
        putBody = typeof config.data === "string" ? JSON.parse(config.data) : config.data;
        return response(config, {
          data: {
            capabilityCode: "knowledge.extract",
            displayName: "电子病历语义实体提取",
            description: "提取病历中的结构化临床事实",
            category: "语义抽取",
            routeStrategy: "BASELINE",
            desensitizeStrategy: "DEFAULT",
            expectedSchema: '{"required":["status","candidates"]}',
            configured: true,
            fallbackAvailable: true,
            fallbackReason: "正常可用",
          },
        });
      }
      throw new Error(`未预期接口: ${config.url ?? ""}`);
    }) as AxiosAdapter;

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: /配置策略/ }));
    await user.click(screen.getByRole("button", { name: "校验并保存策略" }));

    await waitFor(() => expect(saved).toBe(true));
    expect(putBody).toEqual({
      routeStrategy: "BASELINE",
      desensitizeStrategy: "DEFAULT",
      expectedSchema: '{"required":["status","candidates"]}',
    });
  });

  it("系统治理角色可以新增模型能力目录", async () => {
    let savedDefinition: unknown;
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(
          config,
          securityProfile(["llm.read", "llm.execute", "llm.manage", "system.manage"]),
        );
      }
      if (config.url === "/model-capabilities/status") {
        return response(config, { data: [] });
      }
      if (config.url === "/model-capabilities/catalog" && config.method === "get") {
        return response(config, { data: [] });
      }
      if (config.method === "put" && config.url === "/model-capabilities/catalog/custom.summary") {
        savedDefinition = typeof config.data === "string" ? JSON.parse(config.data) : config.data;
        return response(config, {
          data: {
            capabilityCode: "custom.summary",
            ...(savedDefinition as object),
          },
        });
      }
      throw new Error(`未预期接口: ${config.method ?? ""} ${config.url ?? ""}`);
    }) as AxiosAdapter;

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: /能力目录/ }));
    fireEvent.change(screen.getByLabelText("能力代码"), {
      target: { value: "custom.summary" },
    });
    fireEvent.change(screen.getByLabelText("中文名称"), {
      target: { value: "病历摘要" },
    });
    fireEvent.change(screen.getByLabelText("业务分类"), {
      target: { value: "语义抽取" },
    });
    fireEvent.change(screen.getByLabelText("能力说明"), {
      target: { value: "生成待人工审核的结构化病历摘要。" },
    });
    await user.click(screen.getByRole("button", { name: "保存目录项" }));

    await waitFor(() => expect(savedDefinition).toBeDefined());
    expect(savedDefinition).toEqual({
      displayName: "病历摘要",
      description: "生成待人工审核的结构化病历摘要。",
      category: "语义抽取",
      enabled: true,
      sortOrder: 100,
    });
  });

  it("执行权限被收回后不再显示降级任务重试入口", async () => {
    apiClient.defaults.adapter = (async (config) => {
      if (config.url === "/security/me") {
        return response(config, securityProfile(["llm.read", "llm.execute"]));
      }
      if (config.url === "/model-capabilities/status") {
        return response(config, {
          data: [
            {
              capabilityCode: "knowledge.extract",
              displayName: "电子病历语义实体提取",
              description: "提取病历中的结构化临床事实",
              category: "语义抽取",
              routeStrategy: "BASELINE",
              desensitizeStrategy: "DEFAULT",
              expectedSchema: null,
              configured: false,
              fallbackAvailable: true,
              fallbackReason: "使用系统 B0 基线",
            },
          ],
        });
      }
      if (config.url === "/model-capabilities/tasks" && config.method === "post") {
        return response(config, {
          data: {
            taskId: "task-1",
            status: "DEGRADED",
            outputContent: '{"status":"DEGRADED"}',
            modelMode: "BASELINE",
            modelVersion: "B0",
            promptVersion: "none",
            sourceCitations: "[]",
            confidence: null,
            riskLevel: "LOW",
            fallbackUsed: true,
            fallbackReason: "外部模型未连接",
            timeCostMs: 12,
            traceId: "trace-1",
          },
        });
      }
      throw new Error(`未预期接口: ${config.method ?? ""} ${config.url ?? ""}`);
    }) as AxiosAdapter;

    const user = userEvent.setup();
    const { queryClient } = renderPage();

    await user.type(await screen.findByPlaceholderText(/粘贴已脱敏的运行文本/), "已脱敏病历");
    await user.click(screen.getByRole("button", { name: /提交网关任务/ }));
    expect(await screen.findByRole("button", { name: /按基线重试/ })).toBeInTheDocument();

    act(() => {
      queryClient.setQueryData(["security", "me"], securityProfile(["llm.read"]).data);
    });

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: /按基线重试/ })).toBeNull();
    });
  });
});
