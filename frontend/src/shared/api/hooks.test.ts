import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "./client";
import {
  downloadPackageOfflineExport,
  downloadPackageSyncEvidenceExport,
  bindBootstrapMfa,
  changePassword,
  checkBootstrapInitToken,
  createBootstrapAdmin,
  fetchDelegatedAuthStatus,
  fetchThemePreference,
  fetchSavedViews,
  importPackageOfflinePackage,
  saveThemePreference,
  saveExperienceViewSnapshot,
  submitLargeListExport,
  useCompleteWorkflowTodo,
  useCreatePackage,
  useAdvanceIntegrationOnboarding,
  useActivateOnboardingReadiness,
  useBatchConfirmTerminologyCandidates,
  useBuildTerminologyPackage,
  useConfirmTerminologyCandidate,
  useCreateIntegrationOnboarding,
  useGenerateDataQualityReport,
  useGenerateTerminologyCandidates,
  useIntegrationOnboardings,
  useReplayDeadLetter,
  useImplementationSteps,
  useInstantiatePilotTemplate,
  useLocalTerms,
  useOnboardingReadiness,
  useOrgUnits,
  usePackages,
  usePackageAssetReadiness,
  usePackageSyncLogs,
  usePilotPackageTemplates,
  usePublishTerminologyPackage,
  useRollbackTerminologyPackage,
  useSyncPackage,
  useStandardTerms,
  useTerminologyCandidates,
  useTerminologyConflicts,
  useTerminologyPackages,
  useReadWorkflowNotification,
  useWorkflowNotifications,
  useWorkflowTodos,
} from "./hooks";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

function securityProfile() {
  return {
    userId: "user-1",
    username: "it-ops",
    roles: [
      {
        code: "it-ops",
        displayName: "信息科",
        source: "DEFAULT",
        scopeLevel: null,
        scopeCode: null,
      },
    ],
    permissions: [],
    menuKeys: [],
    environmentKeys: [],
    dataScope: {
      tenantId: "tenant-A",
      groupId: "group-A",
      hospitalId: "hospital-A",
      campusId: "campus-A",
      siteId: "site-A",
      departmentId: "dept-A",
      specialtyId: "specialty-A",
    },
    mustChangePwd: false,
    mfaRequired: false,
    mfaBound: true,
  };
}

function renderApiHook<T>(hook: () => T) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  client.setQueryData(["security", "me"], securityProfile());
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  return renderHook(hook, { wrapper });
}

describe("package export api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("downloads an offline package from the integrity-protected export endpoint", async () => {
    const offlineBlob = new Blob(["offline-package"]);
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: offlineBlob });

    const result = await downloadPackageOfflineExport("pkg-1");

    expect(result).toBe(offlineBlob);
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pkg-1/offline/export", {
      responseType: "blob",
    });
  });

  it("downloads sync evidence from the dedicated sync-log export endpoint", async () => {
    const evidenceBlob = new Blob(['{"event":"PACKAGE_SYNC_EVIDENCE_SUMMARY"}\n']);
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: evidenceBlob });

    const result = await downloadPackageSyncEvidenceExport("pkg-1");

    expect(result).toBe(evidenceBlob);
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pkg-1/sync-logs/export", {
      responseType: "blob",
    });
  });

  it("imports an offline package through the integrity-checking endpoint", async () => {
    const response = {
      packageId: "pkg-imported",
      packageCode: "PKG.IMPORT",
      packageVersion: "2026.06.01",
      status: "DRAFT",
      itemCount: 2,
      payloadSha256: "a".repeat(64),
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const result = await importPackageOfflinePackage('{"format":"MEDKERNEL_PACKAGE_OFFLINE_V1"}');

    expect(result).toBe(response);
    expect(apiClient.post).toHaveBeenCalledWith("/engine/pkg/packages/offline/import", {
      offlinePackageJson: '{"format":"MEDKERNEL_PACKAGE_OFFLINE_V1"}',
    });
  });

  it("creates a package through the API-10 root with standard context fields", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000001");
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { packageId: "pkg-1", packageCode: "PKG.COPD" } },
    });

    const { result } = renderApiHook(() => useCreatePackage());

    await result.current.mutateAsync({
      packageCode: "PKG.COPD",
      packageVersion: "1.0.0",
      name: "配置包",
      description: "真实资产集合",
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/pkg/packages",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000001",
        trace_id: "00000000-0000-4000-8000-000000000001",
        tenant_id: "tenant-A",
        role_codes: ["it-ops"],
        package_version: "1.0.0",
        packageCode: "PKG.COPD",
      }),
    );
  });

  it("syncs a package through the API-10 root with standard context fields", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000002");
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { planId: "plan-1", packageId: "pkg-1", status: "NOT_SYNCED", logs: [] } },
    });

    const { result } = renderApiHook(() => useSyncPackage());

    await result.current.mutateAsync({
      packageId: "pkg-1",
      request: {
        packageVersion: "1.0.0",
        targetOrgUnitId: "org-1",
        strategy: "GRAYSCALE",
        scopeType: "DEPARTMENT",
        scopeValue: "dept-A",
        targetIds: ["target-1"],
      },
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/pkg/packages/pkg-1/sync",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000002",
        trace_id: "00000000-0000-4000-8000-000000000002",
        tenant_id: "tenant-A",
        package_version: "1.0.0",
        targetIds: ["target-1"],
      }),
    );
  });

  it("loads persisted sync logs from the API-10 sync-log endpoint", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: [{ logId: "log-1", status: "NOT_SYNCED", syncEvidence: null }] },
    });

    const { result } = renderApiHook(() => usePackageSyncLogs("pkg-1"));

    await waitFor(() =>
      expect(result.current.data).toEqual([
        { logId: "log-1", status: "NOT_SYNCED", syncEvidence: null },
      ]),
    );
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pkg-1/sync-logs");
  });

  it("loads workflow todos from the unified workflow endpoint with server-side filters", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          items: [],
          page: 0,
          size: 10,
          total: 0,
          hasNext: false,
        },
      },
    });

    const { result } = renderApiHook(() =>
      useWorkflowTodos({ status: "PENDING", priority: "HIGH", page: 0, size: 10 }),
    );

    await waitFor(() => expect(result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/workflow/todos", {
      params: { status: "PENDING", priority: "HIGH", page: 0, size: 10 },
    });
  });

  it("completes workflow todos through the auditable completion endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { todoId: "todo-real-1", status: "COMPLETED" } },
    });

    const { result } = renderApiHook(() => useCompleteWorkflowTodo());

    await result.current.mutateAsync({
      todoId: "todo-real-1",
      request: { completionReason: "已完成真实处理" },
    });

    expect(apiClient.post).toHaveBeenCalledWith("/engine/workflow/todos/todo-real-1/complete", {
      completionReason: "已完成真实处理",
    });
  });

  it("loads workflow notifications from the notification endpoint with unread filters", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          items: [],
          page: 0,
          size: 10,
          total: 0,
          hasNext: false,
        },
      },
    });

    const { result } = renderApiHook(() =>
      useWorkflowNotifications({ status: "UNREAD", level: "HIGH", page: 0, size: 10 }),
    );

    await waitFor(() => expect(result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/notifications", {
      params: { status: "UNREAD", level: "HIGH", page: 0, size: 10 },
    });
  });

  it("marks workflow notifications read through the backend endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { notificationId: "notify-real-1", status: "READ" } },
    });

    const { result } = renderApiHook(() => useReadWorkflowNotification());

    await result.current.mutateAsync("notify-real-1");

    expect(apiClient.post).toHaveBeenCalledWith("/engine/notifications/notify-real-1/read");
  });

  it("loads package list through API-13 style server-side paging and filters", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          items: [],
          page: 1,
          size: 10,
          total: 0,
          hasNext: false,
          totalEstimated: false,
        },
      },
    });

    const { result } = renderApiHook(() =>
      usePackages({ page: 0, size: 10, keyword: "COPD", status: "DRAFT" }),
    );

    await waitFor(() => expect(result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages", {
      params: { page: 0, size: 10, keyword: "COPD", status: "DRAFT" },
    });
  });

  it("loads pilot package templates from the dedicated API-10 endpoint", async () => {
    const templates = [
      {
        templateId: "tpl-first-run",
        templateCode: "TPL.FIRST_RUN",
        tenantId: "t-1",
        name: "首发模板",
        description: "试点首发配置包",
        packageCodePrefix: "PILOT.FIRST",
        defaultPackageVersion: "2026.06.01",
        itemCount: 1,
        items: [
          {
            assetType: "KNOWLEDGE",
            assetId: "KN.COPD",
            assetVersion: "2026.06",
            required: true,
            sortOrder: 0,
            dependencyNote: "首发知识库",
          },
        ],
      },
    ];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: templates } });

    const { result } = renderApiHook(() => usePilotPackageTemplates());

    await waitFor(() => expect(result.current.data).toBe(templates));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pilot-templates");
  });

  it("loads asset readiness from the dedicated API-10 endpoint", async () => {
    const readiness = {
      tenantId: "tenant-A",
      ready: true,
      templateCount: 1,
      draftPackageCount: 1,
      releasedPackageCount: 1,
      activePackageCount: 1,
      grayscaleReady: true,
      readyPackageId: "pkg-ready",
      blockers: [],
      checkedAt: "2026-06-03T00:00:00Z",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: readiness } });

    const { result } = renderApiHook(() => usePackageAssetReadiness());

    await waitFor(() => expect(result.current.data).toBe(readiness));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/asset-readiness");
  });

  it("loads implementation guide steps from the tenant engine readiness endpoint", async () => {
    const steps = [
      {
        key: "organization",
        title: "组织树",
        status: "DONE",
        blockers: [],
        targetPath: "/tenant/onboarding",
        evidence: "已存在医院组织",
      },
      {
        key: "adapters",
        title: "适配器",
        status: "BLOCKED",
        blockers: ["尚未配置 HIS 适配器"],
        targetPath: "/adapter/hub",
        evidence: null,
      },
    ];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: steps } });

    const { result } = renderApiHook(() => useImplementationSteps());

    await waitFor(() => expect(result.current.data).toBe(steps));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/tenant/implementation-steps");
  });

  it("loads tenant onboarding readiness from the tenant engine gate endpoint", async () => {
    const readiness = {
      tenantId: "tenant-A",
      ready: false,
      steps: [
        {
          key: "organization",
          title: "组织树",
          status: "BLOCKED",
          blockers: ["组织树缺少租户根或医院节点"],
          targetPath: "/tenant/onboarding",
          evidence: null,
        },
      ],
      blockers: ["组织树缺少租户根或医院节点"],
      checkedAt: "2026-06-03T00:00:00Z",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: readiness } });

    const { result } = renderApiHook(() => useOnboardingReadiness());

    await waitFor(() => expect(result.current.data).toBe(readiness));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/tenant/onboarding-readiness");
  });

  it("activates tenant onboarding only through the tenant engine readiness gate", async () => {
    const readiness = {
      tenantId: "tenant-A",
      ready: true,
      steps: [],
      blockers: [],
      checkedAt: "2026-06-03T00:00:00Z",
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: readiness } });

    const { result } = renderApiHook(() => useActivateOnboardingReadiness());

    await expect(result.current.mutateAsync()).resolves.toBe(readiness);
    expect(apiClient.post).toHaveBeenCalledWith("/engine/tenant/onboarding-readiness/activate");
  });

  it("loads organization units from the engine org API root instead of the legacy tenant root", async () => {
    const page = {
      items: [{ id: "org-1", level: "TENANT", code: "T-1", name: "平台主租户" }],
      page: 1,
      size: 100,
      total: 1,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: page } });

    const { result } = renderApiHook(() => useOrgUnits({ size: 100 }));

    await waitFor(() => expect(result.current.data).toBe(page));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/org/org-units", {
      params: { size: 100 },
    });
  });

  it("instantiates a pilot template through API-10 with standard context fields", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000003");
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          templateCode: "TPL.FIRST_RUN",
          packageInfo: { packageId: "pkg-first", packageCode: "PILOT.FIRST" },
          items: [],
        },
      },
    });

    const { result } = renderApiHook(() => useInstantiatePilotTemplate());

    await result.current.mutateAsync({
      templateCode: "TPL.FIRST_RUN",
      request: {
        packageCode: "PILOT.FIRST",
        packageVersion: "2026.06.03",
        name: "首发配置包",
        description: "由首发模板生成",
      },
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/pkg/packages/pilot-templates/TPL.FIRST_RUN/instantiate",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000003",
        trace_id: "00000000-0000-4000-8000-000000000003",
        tenant_id: "tenant-A",
        role_codes: ["it-ops"],
        package_version: "2026.06.03",
        packageCode: "PILOT.FIRST",
        packageVersion: "2026.06.03",
        name: "首发配置包",
      }),
    );
  });
});

describe("terminology mapping api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000004");
  });

  it("loads standard and local dictionaries from the API-04 paged endpoints", async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 20, total: 0 } } })
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 20, total: 0 } } });

    const standard = renderApiHook(() =>
      useStandardTerms({ page: 0, size: 20, standardSystem: "LOINC", status: "ACTIVE" }),
    );
    const local = renderApiHook(() =>
      useLocalTerms({ page: 0, size: 20, sourceSystem: "LIS", status: "UNMAPPED" }),
    );

    await waitFor(() => expect(standard.result.current.data?.items).toEqual([]));
    await waitFor(() => expect(local.result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/engine/terminology/terms/standard", {
      params: { page: 0, size: 20, standardSystem: "LOINC", status: "ACTIVE" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/engine/terminology/terms/local", {
      params: { page: 0, size: 20, sourceSystem: "LIS", status: "UNMAPPED" },
    });
  });

  it("loads candidates, conflicts and mapping packages from the API-04 roots", async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 10, total: 0 } } })
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 10, total: 0 } } })
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 10, total: 0 } } });

    const candidates = renderApiHook(() =>
      useTerminologyCandidates({ page: 0, size: 10, status: "PENDING", riskLevel: "HIGH" }),
    );
    const conflicts = renderApiHook(() =>
      useTerminologyConflicts({ page: 0, size: 10, status: "OPEN" }),
    );
    const packages = renderApiHook(() =>
      useTerminologyPackages({ page: 0, size: 10, status: "DRAFT" }),
    );

    await waitFor(() => expect(candidates.result.current.data?.items).toEqual([]));
    await waitFor(() => expect(conflicts.result.current.data?.items).toEqual([]));
    await waitFor(() => expect(packages.result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/engine/terminology/mappings/candidates", {
      params: { page: 0, size: 10, status: "PENDING", riskLevel: "HIGH" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/engine/terminology/mappings/conflicts", {
      params: { page: 0, size: 10, status: "OPEN" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(3, "/engine/terminology/mapping-packages", {
      params: { page: 0, size: 10, status: "DRAFT" },
    });
  });

  it("submits terminology candidate generation and confirmation with standard context fields", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: { generatedCount: 1, candidates: [] } } })
      .mockResolvedValueOnce({ data: { data: { id: 10, status: "CONFIRMED" } } })
      .mockResolvedValueOnce({
        data: { data: { confirmedCount: 1, confirmedCandidateIds: [11] } },
      });

    const generate = renderApiHook(() => useGenerateTerminologyCandidates());
    const confirm = renderApiHook(() => useConfirmTerminologyCandidate());
    const batchConfirm = renderApiHook(() => useBatchConfirmTerminologyCandidates());

    await generate.result.current.mutateAsync({
      packageVersion: "2026.06",
      sourceSystem: "LIS",
      minimumScore: 0.6,
      semanticAssistEnabled: false,
    });
    await confirm.result.current.mutateAsync({
      candidateId: 10,
      request: {
        packageVersion: "2026.06",
        reviewNote: "逐条确认高危候选",
        highRiskAcknowledged: true,
        highRiskReason: "已核对来源版本",
      },
    });
    await batchConfirm.result.current.mutateAsync({
      candidateIds: [11],
      request: { packageVersion: "2026.06", reviewNote: "批量确认普通候选" },
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/terminology/mappings/candidates",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000004",
        trace_id: "00000000-0000-4000-8000-000000000004",
        tenant_id: "tenant-A",
        role_codes: ["it-ops"],
        package_version: "2026.06",
        sourceSystem: "LIS",
        semanticAssistEnabled: false,
      }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/terminology/mappings/10/confirm",
      expect.objectContaining({
        package_version: "2026.06",
        highRiskAcknowledged: true,
        highRiskReason: "已核对来源版本",
      }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      3,
      "/engine/terminology/mappings/batch-confirm",
      expect.objectContaining({
        package_version: "2026.06",
        candidateIds: [11],
      }),
    );
  });

  it("builds, publishes and rolls back terminology mapping packages through API-04 roots", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: { id: 30, status: "DRAFT" } } })
      .mockResolvedValueOnce({ data: { data: { id: 30, status: "GRAY" } } })
      .mockResolvedValueOnce({ data: { data: { id: 30, status: "ROLLED_BACK" } } });

    const build = renderApiHook(() => useBuildTerminologyPackage());
    const publish = renderApiHook(() => usePublishTerminologyPackage());
    const rollback = renderApiHook(() => useRollbackTerminologyPackage());

    await build.result.current.mutateAsync({
      packageCode: "TERM.LAB",
      packageVersion: "2026.06",
      scopeLevel: "HOSPITAL",
      scopeCode: "hospital-A",
      displayName: "检验字典映射包",
    });
    await publish.result.current.mutateAsync({
      packageId: 30,
      request: {
        packageVersion: "2026.06",
        releaseMode: "GRAY",
        reason: "首发检验字典灰度验证",
        grayScopeJson: '{"percent":10}',
      },
    });
    await rollback.result.current.mutateAsync({
      packageId: 30,
      request: {
        packageVersion: "2026.06",
        targetPackageId: 29,
        reason: "灰度验证失败",
      },
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/terminology/mapping-packages",
      expect.objectContaining({
        packageCode: "TERM.LAB",
        packageVersion: "2026.06",
        package_version: "2026.06",
      }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/terminology/mapping-packages/30/publish",
      expect.objectContaining({
        releaseMode: "GRAY",
        reason: "首发检验字典灰度验证",
      }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      3,
      "/engine/terminology/mapping-packages/30/rollback",
      expect.objectContaining({
        targetPackageId: 29,
        reason: "灰度验证失败",
      }),
    );
  });
});

describe("integration adapter api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("loads integration onboarding lifecycle records from the SVC-INTEGRATION-01 endpoint", async () => {
    const onboardings = [
      {
        onboardingId: "onb-his",
        name: "HIS 主数据接入申请",
        status: "MAPPING_CONFIGURED",
        routeType: "ADAPTER",
        routeReference: "/api/v1/engine/integration/adapters/his-main",
        healthStatus: "NOT_CONNECTED",
        mappedFieldCount: 12,
        blockers: ["外部连接器未连通"],
        sourceSystem: "HIS",
        businessScenario: "门诊患者主数据",
        orgPath: "集团/医院",
        callbackWebhookId: null,
        createdAt: "2026-06-03T08:00:00Z",
        updatedAt: "2026-06-03T08:00:00Z",
      },
    ];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: onboardings } });

    const { result } = renderApiHook(() => useIntegrationOnboardings());

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(onboardings);
    expect(apiClient.get).toHaveBeenCalledWith("/api/v1/engine/integration/onboardings");
  });

  it("creates and advances integration onboarding records without inventing endpoints", async () => {
    const response = {
      onboardingId: "onb-his",
      name: "HIS 主数据接入申请",
      status: "REQUESTED",
      routeType: "ADAPTER",
      routeReference: "/api/v1/engine/integration/adapters/his-main",
      healthStatus: "NOT_CONNECTED",
      mappedFieldCount: 0,
      blockers: ["接入申请已创建，待配置鉴权与字段映射"],
      sourceSystem: "HIS",
      businessScenario: "门诊患者主数据",
      orgPath: "集团/医院",
      callbackWebhookId: null,
      createdAt: "2026-06-03T08:00:00Z",
      updatedAt: "2026-06-03T08:00:00Z",
    };
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: response } })
      .mockResolvedValueOnce({ data: { data: { ...response, status: "ONLINE" } } });

    const create = renderApiHook(() => useCreateIntegrationOnboarding());
    const advance = renderApiHook(() => useAdvanceIntegrationOnboarding());

    await create.result.current.mutateAsync({
      onboardingId: "onb-his",
      name: "HIS 主数据接入申请",
      accessMode: "ADAPTER",
      adapterId: "his-main",
      sourceSystem: "HIS",
      businessScenario: "门诊患者主数据",
      orgPath: "集团/医院",
    });
    await advance.result.current.mutateAsync({
      onboardingId: "onb-his",
      targetStatus: "ONLINE",
      evidenceText: "字段映射 12 项，外部连接仍按 NOT_CONNECTED 展示。",
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/api/v1/engine/integration/onboardings", {
      onboardingId: "onb-his",
      name: "HIS 主数据接入申请",
      accessMode: "ADAPTER",
      adapterId: "his-main",
      sourceSystem: "HIS",
      businessScenario: "门诊患者主数据",
      orgPath: "集团/医院",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/api/v1/engine/integration/onboardings/onb-his/advance",
      {
        targetStatus: "ONLINE",
        evidenceText: "字段映射 12 项，外部连接仍按 NOT_CONNECTED 展示。",
      },
    );
  });

  it("generates data quality reports and replays dead letters through real integration endpoints", async () => {
    const report = {
      reportId: "dqr-1",
      tenantId: "tenant-A",
      generatedAt: "2026-06-03T08:10:00Z",
      requiredFieldTotal: 100,
      requiredFieldPresent: 82,
      requiredFieldRate: 82,
      adapterTotal: 2,
      mappedAdapterCount: 1,
      mappingRate: 50,
      timelyAdapterCount: 1,
      timelinessRate: 50,
      notConnectedCount: 1,
      misconfiguredCount: 1,
      gapSummary: "HIS 断连，LIS 配置非法",
      createdAt: "2026-06-03T08:10:00Z",
      createdBy: "it-1",
    };
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: report } })
      .mockResolvedValueOnce({
        data: {
          data: {
            sourceMessageId: "msg-dead",
            replayMessageId: "msg-replay",
            traceId: "trace-replay",
            status: "NOT_CONNECTED",
            blocksMainFlow: false,
            message: "已重放为补偿消息",
          },
        },
      });

    const quality = renderApiHook(() => useGenerateDataQualityReport());
    const replay = renderApiHook(() => useReplayDeadLetter());

    await quality.result.current.mutateAsync();
    await replay.result.current.mutateAsync("msg-dead");

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/api/v1/engine/integration/data-quality/reports",
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/api/v1/engine/integration/dead-letter/msg-dead/replay",
    );
  });
});

describe("experience foundation api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000001");
  });

  it("loads saved views by page key", async () => {
    const views = [{ savedViewId: "sv-1", pageKey: "terminology.mapping" }];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: views } });

    const result = await fetchSavedViews("terminology.mapping");

    expect(result).toBe(views);
    expect(apiClient.get).toHaveBeenCalledWith("/experience/saved-views", {
      params: { pageKey: "terminology.mapping" },
    });
  });

  it("saves view snapshots as backend JSON definition", async () => {
    const saved = { savedViewId: "sv-1", pageKey: "terminology.mapping" };
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: saved } });

    const result = await saveExperienceViewSnapshot({
      pageKey: "terminology.mapping",
      viewName: "默认视图",
      defaultView: true,
      snapshot: {
        viewKey: "terminology.mapping",
        filters: [{ key: "status", value: "DRAFT" }],
        pageRequest: { pageNumber: 1, pageSize: 20, filters: { status: "DRAFT" } },
        visibleColumnKeys: ["status"],
        expertMode: false,
        capturedAt: "2026-06-01T00:00:00.000Z",
      },
    });

    expect(result).toBe(saved);
    expect(apiClient.put).toHaveBeenCalledWith(
      "/experience/saved-views",
      expect.objectContaining({
        pageKey: "terminology.mapping",
        viewName: "默认视图",
        defaultView: true,
        definitionJson: expect.stringContaining('"status":"DRAFT"'),
      }),
    );
  });

  it("submits large-list export with idempotency key and filters", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { jobId: "job-1", status: "PENDING", message: "ok" } },
    });

    const result = await submitLargeListExport({
      resourceType: "TERMINOLOGY_MAPPING",
      requestSnapshot: {
        viewKey: "terminology.mapping",
        filters: [{ key: "sourceSystem", value: "HIS" }],
        pageRequest: { pageNumber: 1, pageSize: 20, filters: { status: "DRAFT" } },
        visibleColumnKeys: ["status"],
        expertMode: false,
        capturedAt: "2026-06-01T00:00:00.000Z",
      },
      selectedScope: "currentPage",
      reason: "导出字典映射核查结果",
      idempotencyKey: "idem-from-action",
    });

    expect(result).toEqual(expect.objectContaining({ jobId: "job-1", status: "pending" }));
    expect(apiClient.post).toHaveBeenCalledWith(
      "/large-lists/exports",
      expect.objectContaining({
        resourceType: "TERMINOLOGY_MAPPING",
        filters: { status: "DRAFT", sourceSystem: "HIS" },
        selectedScope: "CURRENT_PAGE",
        idempotencyKey: "idem-from-action",
      }),
      { headers: { "Idempotency-Key": "idem-from-action" } },
    );
  });

  it("loads the authenticated user's theme preference", async () => {
    const preference = {
      mode: "elder",
      version: 2,
      updatedAt: "2026-06-01T00:00:00Z",
      updatedBy: "doctor-1",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: preference } });

    const result = await fetchThemePreference();

    expect(result).toBe(preference);
    expect(apiClient.get).toHaveBeenCalledWith("/experience/theme-preference");
  });

  it("saves only supported theme modes to the backend preference endpoint", async () => {
    const preference = {
      mode: "eye",
      version: 3,
      updatedAt: "2026-06-01T00:00:00Z",
      updatedBy: "doctor-1",
    };
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: preference } });

    const result = await saveThemePreference("eye");

    expect(result).toBe(preference);
    expect(apiClient.put).toHaveBeenCalledWith("/experience/theme-preference", { mode: "eye" });

    await expect(saveThemePreference("contrast" as never)).rejects.toThrow("主题模式");
    expect(apiClient.put).toHaveBeenCalledTimes(1);
  });
});

describe("bootstrap identity api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("checks the init token without creating a login session", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { valid: true, expiresAt: "2026-06-01T09:00:00Z" } },
    });

    const result = await checkBootstrapInitToken("raw-init-token");

    expect(result.valid).toBe(true);
    expect(apiClient.post).toHaveBeenCalledWith("/bootstrap/init-token", {
      token: "raw-init-token",
    });
  });

  it("creates the first platform admin from the password step", async () => {
    const response = {
      userId: "platform-owner",
      tenantId: "t-1",
      username: "platform-owner",
      roles: ["platform-admin"],
      mustChangePwd: true,
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const result = await createBootstrapAdmin({
      token: "raw-init-token",
      tenantId: "t-1",
      username: "platform-owner",
      password: "Init@2026pw",
    });

    expect(result).toBe(response);
    expect(apiClient.post).toHaveBeenCalledWith("/bootstrap/password", {
      token: "raw-init-token",
      tenantId: "t-1",
      username: "platform-owner",
      password: "Init@2026pw",
    });
  });

  it("changes password and binds MFA through authenticated bootstrap continuation", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: null } })
      .mockResolvedValueOnce({
        data: {
          data: {
            mfaBound: false,
            secret: "JBSWY3DPEHPK3PXP",
            otpauthUri:
              "otpauth://totp/MedKernel:platform-owner?secret=JBSWY3DPEHPK3PXP&issuer=MedKernel",
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            mfaBound: true,
            recoveryCode: "RECOVERY-CODE-ONCE",
          },
        },
      });

    await changePassword({ oldPassword: "Init@2026pw", newPassword: "Owner@2026pw" });
    const setup = await bindBootstrapMfa({ label: "值班安全终端" });
    const mfa = await bindBootstrapMfa({
      label: "值班安全终端",
      secret: "JBSWY3DPEHPK3PXP",
      code: "123456",
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/auth/change-password", {
      oldPassword: "Init@2026pw",
      newPassword: "Owner@2026pw",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/bootstrap/mfa", {
      label: "值班安全终端",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(3, "/bootstrap/mfa", {
      label: "值班安全终端",
      secret: "JBSWY3DPEHPK3PXP",
      code: "123456",
    });
    expect(setup.mfaBound).toBe(false);
    expect(setup.secret).toBe("JBSWY3DPEHPK3PXP");
    expect(mfa.recoveryCode).toBe("RECOVERY-CODE-ONCE");
  });
});

describe("auth identity api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("loads delegated auth status from the public auth endpoint", async () => {
    const status = {
      mode: "BOTH",
      enabled: true,
      status: "NOT_CONNECTED",
      providers: ["OIDC", "CAS", "SAML", "国密CA"],
      message: "院方统一身份入口已开放，但当前未配置真实 IdP 连接器。",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: status } });

    const result = await fetchDelegatedAuthStatus();

    expect(result).toBe(status);
    expect(apiClient.get).toHaveBeenCalledWith("/auth/delegated/status");
  });
});
