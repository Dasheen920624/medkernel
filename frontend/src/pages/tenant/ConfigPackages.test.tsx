import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ConfigPackages from "./ConfigPackages";
import { downloadPackageOfflineExport } from "@/shared/api/hooks";

const apiMocks = vi.hoisted(() => ({
  downloadPackageOfflineExport: vi.fn(),
  importOfflinePackage: vi.fn(),
  refetchPackages: vi.fn(),
  refetchPackageDetail: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useSyncTargets: () => ({ data: [] }),
  useCreatePackage: () => ({ mutateAsync: vi.fn(), isPending: false }),
  usePackages: () => ({
    data: {
      items: [
        {
          packageId: "pkg-offline",
          tenantId: "tenant-A",
          packageCode: "PKG.TEST",
          packageVersion: "3.0.0",
          name: "通用配置包",
          description: "真实资产集合",
          status: "PUBLISHED",
          createdAt: "2026-06-01T00:00:00Z",
          createdBy: "tester",
          updatedAt: "2026-06-01T00:00:00Z",
          updatedBy: "tester",
          traceId: "trace-test",
        },
      ],
      page: 1,
      size: 10,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    },
    refetch: apiMocks.refetchPackages,
  }),
  usePackageDetail: () => ({
    data: { items: [] },
    refetch: apiMocks.refetchPackageDetail,
  }),
  useAddPackageItem: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useCalculateDiff: () => ({ data: null }),
  useSyncPackage: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useRollbackPackage: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useImportOfflinePackage: () => ({ mutateAsync: apiMocks.importOfflinePackage, isPending: false }),
  useRuleDefinitions: () => ({ data: { items: [] } }),
  usePathwayTemplates: () => ({ data: { items: [] } }),
  useEvaluationIndicators: () => ({ data: { items: [] } }),
  useTerminologyMappings: () => ({ data: { items: [] } }),
  downloadPackageDiffExport: vi.fn(),
  downloadPackageOfflineExport: apiMocks.downloadPackageOfflineExport,
}));

describe("ConfigPackages offline package export", () => {
  beforeEach(() => {
    apiMocks.downloadPackageOfflineExport.mockReset();
    apiMocks.downloadPackageOfflineExport.mockResolvedValue(new Blob(["offline-package"]));
    apiMocks.importOfflinePackage.mockReset();
    apiMocks.importOfflinePackage.mockResolvedValue({
      packageId: "pkg-imported",
      packageCode: "PKG.IMPORT",
      packageVersion: "2026.06.01",
      status: "DRAFT",
      itemCount: 2,
      payloadSha256: "a".repeat(64),
    });
    Object.defineProperty(window.URL, "createObjectURL", {
      configurable: true,
      value: vi.fn(() => "blob:offline-package"),
    });
    Object.defineProperty(window.URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  });

  it("offers a real offline package download action for each package row", async () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    await userEvent.click(screen.getByRole("button", { name: "导出离线包" }));

    await waitFor(() => {
      expect(downloadPackageOfflineExport).toHaveBeenCalledWith("pkg-offline");
    });
  });

  it("uses the backend page total for the cumulative package statistic", async () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    const cumulativeStatistic = screen.getByText("总配置包版本 (累计)").closest(".ant-statistic");
    expect(cumulativeStatistic).not.toBeNull();
    expect(within(cumulativeStatistic as HTMLElement).getByText("1")).toBeInTheDocument();
  });

  it("offers a clear offline package import flow", async () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    await userEvent.click(screen.getByRole("button", { name: "导入离线包" }));
    fireEvent.change(screen.getByLabelText("离线包 JSON"), {
      target: { value: '{"format":"MEDKERNEL_PACKAGE_OFFLINE_V1"}' },
    });
    await userEvent.click(screen.getByRole("button", { name: "导入并校验" }));

    await waitFor(() => {
      expect(apiMocks.importOfflinePackage).toHaveBeenCalledWith(
        '{"format":"MEDKERNEL_PACKAGE_OFFLINE_V1"}',
      );
    });
  });
});
