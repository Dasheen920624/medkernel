import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "./client";
import { downloadPackageOfflineExport, importPackageOfflinePackage } from "./hooks";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe("package export api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
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
