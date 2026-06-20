import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "./client";
import {
  useCheckModelProviderHealth,
  useModelProviders,
  useRemoveModelProviderCredential,
  useSaveModelProviderCredential,
  useSetModelProviderEnabled,
  useUpsertModelProvider,
} from "./modelProviders";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

function renderApiHook<T>(hook: () => T) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return renderHook(hook, {
    wrapper: ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children),
  });
}

describe("model provider api hooks", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads the tenant-scoped sanitized provider page", async () => {
    const page = {
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
      total: 1,
      hasNext: false,
      totalEstimated: false,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: page } });

    const hook = renderApiHook(() => useModelProviders({ page: 1, size: 20 }));

    await waitFor(() => expect(hook.result.current.data).toEqual(page));
    expect(apiClient.get).toHaveBeenCalledWith("/model-providers", {
      params: { page: 1, size: 20 },
    });
  });

  it("keeps provider configuration separate from the encrypted key", async () => {
    vi.mocked(apiClient.put).mockResolvedValueOnce({
      data: { data: { providerCode: "medical-model", credentialConfigured: false } },
    });
    const hook = renderApiHook(() => useUpsertModelProvider());

    await act(async () => {
      await hook.result.current.mutateAsync({
        providerCode: "medical-model",
        providerType: "OPENAI_COMPATIBLE",
        endpointUri: "https://model.example.com/v1",
        modelVersion: "medical-v1",
        expectedVersion: null,
      });
    });

    expect(apiClient.put).toHaveBeenCalledWith("/model-providers/medical-model", {
      providerType: "OPENAI_COMPATIBLE",
      endpointUri: "https://model.example.com/v1",
      modelVersion: "medical-v1",
      expectedVersion: null,
    });
  });

  it("rotates and removes a key without expecting the server to echo it", async () => {
    vi.mocked(apiClient.put).mockResolvedValueOnce({
      data: {
        data: {
          providerCode: "medical-model",
          credentialConfigured: true,
          credentialLast4: "5678",
        },
      },
    });
    vi.mocked(apiClient.delete).mockResolvedValueOnce({
      data: { data: { providerCode: "medical-model", credentialConfigured: false } },
    });
    const save = renderApiHook(() => useSaveModelProviderCredential());
    const remove = renderApiHook(() => useRemoveModelProviderCredential());

    await act(async () => {
      await save.result.current.mutateAsync({
        providerCode: "medical-model",
        credential: "sk-fake-medical-key-5678",
        reason: "轮换生产模型凭据",
        expectedVersion: 2,
        confirmedHighRisk: true,
      });
    });
    await act(async () => {
      await remove.result.current.mutateAsync({
        providerCode: "medical-model",
        reason: "撤销失效的生产模型凭据",
        expectedVersion: 3,
        confirmedHighRisk: true,
      });
    });

    expect(apiClient.put).toHaveBeenCalledWith("/model-providers/medical-model/credential", {
      credential: "sk-fake-medical-key-5678",
      reason: "轮换生产模型凭据",
      expectedVersion: 2,
      confirmedHighRisk: true,
    });
    expect(apiClient.delete).toHaveBeenCalledWith("/model-providers/medical-model/credential", {
      data: {
        reason: "撤销失效的生产模型凭据",
        expectedVersion: 3,
        confirmedHighRisk: true,
      },
    });
  });

  it("uses governed endpoints for health checks and high-risk enablement", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { providerCode: "medical-model" } },
    });
    const health = renderApiHook(() => useCheckModelProviderHealth());
    const enabled = renderApiHook(() => useSetModelProviderEnabled());

    await act(async () => {
      await health.result.current.mutateAsync("medical-model");
      await enabled.result.current.mutateAsync({
        providerCode: "medical-model",
        enabled: true,
        capabilityCode: "rule.draft",
        reason: "当前制品医学评测已独立签署",
        expectedVersion: 4,
        confirmedHighRisk: true,
      });
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/model-providers/medical-model/health-check",
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/model-providers/medical-model/enable", {
      capabilityCode: "rule.draft",
      reason: "当前制品医学评测已独立签署",
      expectedVersion: 4,
      confirmedHighRisk: true,
    });
  });
});
