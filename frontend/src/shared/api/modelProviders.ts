import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiClient } from "./client";
import type { PageResponse } from "./hooks";

export type ModelProviderType = "OLLAMA" | "OPENAI_COMPATIBLE" | "CLAUDE" | "DIFY";
export type ModelProviderCredentialSource = "VAULT" | "NONE";

export interface ModelProviderGovernanceView {
  providerCode: string;
  providerType: ModelProviderType;
  endpointUri: string;
  credentialConfigured: boolean;
  credentialSource: ModelProviderCredentialSource;
  credentialLast4?: string | null;
  credentialVersion?: number | null;
  credentialUpdatedAt?: string | null;
  credentialUpdatedBy?: string | null;
  modelVersion: string;
  enabled: boolean;
  status: "NOT_CONNECTED" | "HEALTHY" | "UNHEALTHY" | string;
  version: number;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface ModelProviderPageParams {
  page?: number;
  size?: number;
}

export interface ModelProviderUpsertPayload {
  providerCode: string;
  providerType: ModelProviderType;
  endpointUri: string;
  modelVersion: string;
  expectedVersion?: number | null;
}

export interface ModelProviderCredentialUpsertPayload {
  providerCode: string;
  credential: string;
  reason: string;
  expectedVersion?: number | null;
  confirmedHighRisk: boolean;
}

export interface ModelProviderCredentialRemovalPayload {
  providerCode: string;
  reason: string;
  expectedVersion: number;
  confirmedHighRisk: boolean;
}

export interface ModelProviderActivationPayload {
  providerCode: string;
  enabled: boolean;
  capabilityCode?: string | null;
  reason: string;
  expectedVersion: number;
  confirmedHighRisk: boolean;
}

const QUERY_KEY = ["model-providers"] as const;

function useInvalidateModelProviders() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: QUERY_KEY });
}

export function useModelProviders(params: ModelProviderPageParams = {}, enabled = true) {
  const requestParams = { page: params.page ?? 1, size: params.size ?? 20 };
  return useQuery({
    queryKey: [...QUERY_KEY, requestParams],
    enabled,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<ModelProviderGovernanceView> }>(
        "/model-providers",
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export function useUpsertModelProvider() {
  const invalidate = useInvalidateModelProviders();
  return useMutation({
    mutationFn: async ({ providerCode, ...request }: ModelProviderUpsertPayload) => {
      const { data } = await apiClient.put<{ data: ModelProviderGovernanceView }>(
        `/model-providers/${encodeURIComponent(providerCode)}`,
        request,
      );
      return data.data;
    },
    onSuccess: invalidate,
  });
}

export function useSaveModelProviderCredential() {
  const invalidate = useInvalidateModelProviders();
  return useMutation({
    mutationFn: async ({ providerCode, ...request }: ModelProviderCredentialUpsertPayload) => {
      const { data } = await apiClient.put<{ data: ModelProviderGovernanceView }>(
        `/model-providers/${encodeURIComponent(providerCode)}/credential`,
        request,
      );
      return data.data;
    },
    onSuccess: invalidate,
  });
}

export function useRemoveModelProviderCredential() {
  const invalidate = useInvalidateModelProviders();
  return useMutation({
    mutationFn: async ({ providerCode, ...request }: ModelProviderCredentialRemovalPayload) => {
      const { data } = await apiClient.delete<{ data: ModelProviderGovernanceView }>(
        `/model-providers/${encodeURIComponent(providerCode)}/credential`,
        { data: request },
      );
      return data.data;
    },
    onSuccess: invalidate,
  });
}

export function useCheckModelProviderHealth() {
  const invalidate = useInvalidateModelProviders();
  return useMutation({
    mutationFn: async (providerCode: string) => {
      const { data } = await apiClient.post<{ data: ModelProviderGovernanceView }>(
        `/model-providers/${encodeURIComponent(providerCode)}/health-check`,
      );
      return data.data;
    },
    onSuccess: invalidate,
  });
}

export function useSetModelProviderEnabled() {
  const invalidate = useInvalidateModelProviders();
  return useMutation({
    mutationFn: async ({ providerCode, enabled, ...request }: ModelProviderActivationPayload) => {
      const action = enabled ? "enable" : "disable";
      const { data } = await apiClient.post<{ data: ModelProviderGovernanceView }>(
        `/model-providers/${encodeURIComponent(providerCode)}/${action}`,
        request,
      );
      return data.data;
    },
    onSuccess: invalidate,
  });
}
