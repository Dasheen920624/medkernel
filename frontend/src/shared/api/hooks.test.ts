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
  useCreatePackage,
  useActivateOnboardingReadiness,
  useImplementationSteps,
  useInstantiatePilotTemplate,
  useOnboardingReadiness,
  useOrgUnits,
  usePackages,
  usePackageAssetReadiness,
  usePackageSyncLogs,
  usePilotPackageTemplates,
  useSyncPackage,
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
