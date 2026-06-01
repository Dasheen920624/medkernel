import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "./client";
import { downloadPackageOfflineExport } from "./hooks";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe("package export api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
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
});
