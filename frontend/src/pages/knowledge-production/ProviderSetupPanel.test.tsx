import { ConfigProvider } from "antd";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCheckModelProviderHealth,
  useModelProviders,
  useRemoveModelProviderCredential,
  useSaveModelProviderCredential,
  useSetModelProviderEnabled,
  useUpsertModelProvider,
} from "@/shared/api/modelProviders";
import { useSecurityProfile } from "@/shared/api/hooks";

import ProviderSetupPanel from "./ProviderSetupPanel";

vi.mock("@/shared/api/hooks", () => ({ useSecurityProfile: vi.fn() }));
vi.mock("@/shared/api/modelProviders", () => ({
  useModelProviders: vi.fn(),
  useUpsertModelProvider: vi.fn(),
  useSaveModelProviderCredential: vi.fn(),
  useRemoveModelProviderCredential: vi.fn(),
  useCheckModelProviderHealth: vi.fn(),
  useSetModelProviderEnabled: vi.fn(),
}));

const saveCredential = vi.fn();
const upsertProvider = vi.fn();
const removeCredential = vi.fn();
const setProviderEnabled = vi.fn();

function mutation(mutateAsync = vi.fn()) {
  return { mutateAsync, isPending: false };
}

function providerQuery(status: "HEALTHY" | "NOT_CONNECTED" = "NOT_CONNECTED") {
  return {
    data: {
      items: [
        {
          providerCode: "medical-model",
          providerType: "OPENAI_COMPATIBLE",
          endpointUri: "https://model.example.com/v1",
          credentialConfigured: true,
          credentialSource: "VAULT",
          credentialLast4: "1234",
          credentialVersion: 2,
          credentialUpdatedAt: "2026-06-20T05:00:00Z",
          credentialUpdatedBy: "platform-admin",
          modelVersion: "medical-v1",
          enabled: false,
          status,
          version: 4,
        },
      ],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    },
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  };
}

describe("ProviderSetupPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useSecurityProfile).mockReturnValue({
      data: {
        permissions: [{ code: "llm.provider.manage" }],
        roles: [{ code: "platform-admin" }],
      },
      isLoading: false,
      isError: false,
    } as never);
    vi.mocked(useModelProviders).mockReturnValue(providerQuery() as never);
    vi.mocked(useUpsertModelProvider).mockReturnValue(mutation(upsertProvider) as never);
    vi.mocked(useSaveModelProviderCredential).mockReturnValue(mutation(saveCredential) as never);
    vi.mocked(useRemoveModelProviderCredential).mockReturnValue(
      mutation(removeCredential) as never,
    );
    vi.mocked(useCheckModelProviderHealth).mockReturnValue(mutation() as never);
    vi.mocked(useSetModelProviderEnabled).mockReturnValue(mutation(setProviderEnabled) as never);
    saveCredential.mockResolvedValue(undefined);
    upsertProvider.mockResolvedValue(undefined);
    removeCredential.mockResolvedValue(undefined);
    setProviderEnabled.mockResolvedValue(undefined);
  });

  it("shows only safe credential metadata and never renders a stored secret", () => {
    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    expect(screen.getByText("模型服务与密钥")).toBeInTheDocument();
    expect(screen.getByText("尾号 1234")).toBeInTheDocument();
    expect(screen.getByText("待健康检查")).toBeInTheDocument();
    expect(screen.queryByText(/sk-/)).not.toBeInTheDocument();
  });

  it("blocks enablement until the latest real health check passes", () => {
    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    const enableButton = screen.getByRole("button", { name: /启\s*用/ });
    expect(enableButton).toBeDisabled();
    expect(enableButton).toHaveAttribute("title", "请先完成真实健康检查");
  });

  it("uses server-side pagination instead of hiding providers after the first page", async () => {
    const user = userEvent.setup();
    vi.mocked(useModelProviders).mockReturnValue({
      data: {
        items: [
          {
            providerCode: "medical-model",
            providerType: "OPENAI_COMPATIBLE",
            endpointUri: "https://model.example.com/v1",
            credentialConfigured: true,
            credentialSource: "VAULT",
            credentialLast4: "1234",
            credentialVersion: 2,
            modelVersion: "medical-v1",
            enabled: false,
            status: "NOT_CONNECTED",
            version: 4,
          },
        ],
        page: 1,
        size: 20,
        total: 41,
        hasNext: true,
        totalEstimated: false,
      },
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    } as never);

    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    await user.click(screen.getByTitle("2"));

    expect(useModelProviders).toHaveBeenLastCalledWith({ page: 2, size: 20 }, true);
  });

  it("keeps the wide provider table inside an internal scroll panel", () => {
    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    expect(screen.getByTestId("model-provider-table-panel")).toBeInTheDocument();
  });

  it("uses a non-autofilled password input and clears it after rotation", async () => {
    const user = userEvent.setup();
    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    await user.click(screen.getByRole("button", { name: "轮换密钥" }));
    const credential = screen.getByLabelText("模型密钥");
    expect(credential).toHaveAttribute("type", "password");
    expect(credential).toHaveAttribute("autocomplete", "new-password");

    await user.type(credential, "sk-fake-medical-key-5678");
    await user.type(screen.getByLabelText("变更原因"), "轮换生产模型凭据");
    await user.click(screen.getByRole("checkbox", { name: /我确认密钥变更将强制停用/ }));
    await user.click(screen.getByRole("button", { name: "保存并停用" }));

    await waitFor(() =>
      expect(saveCredential).toHaveBeenCalledWith({
        providerCode: "medical-model",
        credential: "sk-fake-medical-key-5678",
        reason: "轮换生产模型凭据",
        expectedVersion: 2,
        confirmedHighRisk: true,
      }),
    );
    expect(screen.queryByDisplayValue("sk-fake-medical-key-5678")).not.toBeInTheDocument();
  });

  it("blocks a vague credential change reason before calling the backend", async () => {
    const user = userEvent.setup();
    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    await screen.findByText("medical-model");
    await user.click(screen.getByRole("button", { name: "轮换密钥" }));
    await user.type(screen.getByLabelText("模型密钥"), "sk-fake-provider-key-1234");
    await user.type(screen.getByLabelText("变更原因"), "轮换");
    await user.click(screen.getByText("我确认密钥变更将强制停用模型服务并要求重新验证"));
    await user.click(screen.getByRole("button", { name: "保存并停用" }));

    expect(await screen.findByText("请填写至少 8 个字符的具体原因")).toBeInTheDocument();
    expect(saveCredential).not.toHaveBeenCalled();
  });

  it("shows the responsible role instead of disabled mutation controls without permission", () => {
    vi.mocked(useSecurityProfile).mockReturnValue({
      data: { permissions: [], roles: [{ code: "engine-operator" }] },
      isLoading: false,
      isError: false,
    } as never);

    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    expect(
      screen.getByText(/由医疗引擎运营员维护模型服务、密钥、健康检查和医学评测/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "轮换密钥" })).not.toBeInTheDocument();
  });

  it("edits an existing provider with optimistic locking", async () => {
    const user = userEvent.setup();
    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    await user.click(screen.getByRole("button", { name: "编辑配置" }));
    expect(screen.getByRole("dialog", { name: "编辑模型服务" })).toBeInTheDocument();
    expect(screen.getByLabelText("服务编码")).toBeDisabled();
    expect(screen.getByLabelText("服务编码")).toHaveValue("medical-model");

    const endpoint = screen.getByLabelText("服务地址");
    await user.clear(endpoint);
    await user.type(endpoint, "https://new-model.example.com/v1");
    await user.click(screen.getByRole("button", { name: "保存并保持停用" }));

    await waitFor(() =>
      expect(upsertProvider).toHaveBeenCalledWith({
        providerCode: "medical-model",
        providerType: "OPENAI_COMPATIBLE",
        endpointUri: "https://new-model.example.com/v1",
        modelVersion: "medical-v1",
        expectedVersion: 4,
      }),
    );
  });

  it("requires an explicit reason and confirmation before removing a key", async () => {
    const user = userEvent.setup();
    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    await user.click(screen.getByRole("button", { name: "移除密钥" }));
    await user.type(screen.getByLabelText("移除原因"), "供应商凭据已作废，执行受控撤销");
    await user.click(screen.getByRole("checkbox", { name: /我确认移除后将强制停用/ }));
    await user.click(screen.getByRole("button", { name: "确认移除" }));

    await waitFor(() =>
      expect(removeCredential).toHaveBeenCalledWith({
        providerCode: "medical-model",
        reason: "供应商凭据已作废，执行受控撤销",
        expectedVersion: 2,
        confirmedHighRisk: true,
      }),
    );
  });

  it("uses the governed medical capability catalog when enabling a provider", async () => {
    const user = userEvent.setup();
    vi.mocked(useModelProviders).mockReturnValue(providerQuery("HEALTHY") as never);
    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    await user.click(screen.getByRole("button", { name: /启\s*用/ }));
    await user.click(screen.getByRole("combobox", { name: "已通过评测的模型能力" }));
    await user.click(screen.getByText("临床规则草案拟定"));
    await user.type(screen.getByLabelText("启停原因"), "当前交付内容医学评测已通过");
    await user.click(screen.getByRole("checkbox", { name: /我确认本操作受医学评测/ }));
    await user.click(screen.getByRole("button", { name: "确认启用" }));

    await waitFor(() =>
      expect(setProviderEnabled).toHaveBeenCalledWith({
        providerCode: "medical-model",
        enabled: true,
        capabilityCode: "rule.draft",
        reason: "当前交付内容医学评测已通过",
        expectedVersion: 4,
        confirmedHighRisk: true,
      }),
    );
    expect(screen.queryByText(/独立签署|质量治理专家|集成运维员/)).not.toBeInTheDocument();
  });

  it("blocks a vague provider activation reason before calling the backend", async () => {
    const user = userEvent.setup();
    vi.mocked(useModelProviders).mockReturnValue(providerQuery("HEALTHY") as never);
    render(
      <ConfigProvider>
        <ProviderSetupPanel />
      </ConfigProvider>,
    );

    await user.click(screen.getByRole("button", { name: /启\s*用/ }));
    await user.click(screen.getByRole("combobox", { name: "已通过评测的模型能力" }));
    await user.click(screen.getByText("临床规则草案拟定"));
    await user.type(screen.getByLabelText("启停原因"), "启用");
    await user.click(screen.getByRole("checkbox", { name: /我确认本操作受医学评测/ }));
    await user.click(screen.getByRole("button", { name: "确认启用" }));

    expect(await screen.findByText("请填写至少 8 个字符的具体原因")).toBeInTheDocument();
    expect(setProviderEnabled).not.toHaveBeenCalled();
  });
});
