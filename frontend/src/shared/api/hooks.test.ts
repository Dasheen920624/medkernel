import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "./client";
import {
  downloadPackageOfflineExport,
  fetchThemePreference,
  fetchSavedViews,
  importPackageOfflinePackage,
  saveThemePreference,
  saveExperienceViewSnapshot,
  submitLargeListExport,
} from "./hooks";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

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
    expect(apiClient.get).toHaveBeenCalledWith("/engine/packages/pkg-1/offline/export", {
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
    expect(apiClient.post).toHaveBeenCalledWith("/engine/packages/offline/import", {
      offlinePackageJson: '{"format":"MEDKERNEL_PACKAGE_OFFLINE_V1"}',
    });
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
