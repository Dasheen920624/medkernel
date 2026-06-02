import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ConfigPackages from "./ConfigPackages";
import { downloadPackageOfflineExport } from "@/shared/api/hooks";

const apiMocks = vi.hoisted(() => ({
  downloadPackageOfflineExport: vi.fn(),
  importOfflinePackage: vi.fn(),
  addPackageItem: vi.fn(),
  releasePackage: vi.fn(),
  refetchPackages: vi.fn(),
  refetchPackageDetail: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useSyncTargets: () => ({
    data: [{ targetId: "target-his", targetName: "院内 HIS 同步通道" }],
  }),
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
          status: "DRAFT",
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
  useAddPackageItem: () => ({ mutateAsync: apiMocks.addPackageItem, isPending: false }),
  useCalculateDiff: () => ({ data: null }),
  useSyncPackage: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useReleasePackage: () => ({ mutateAsync: apiMocks.releasePackage, isPending: false }),
  usePackageSyncLogs: () => ({ data: [] }),
  useRollbackPackage: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useImportOfflinePackage: () => ({ mutateAsync: apiMocks.importOfflinePackage, isPending: false }),
  useRuleDefinitions: () => ({ data: { items: [] } }),
  usePathwayTemplates: () => ({ data: { items: [] } }),
  useEvaluationIndicators: () => ({ data: { items: [] } }),
  useTerminologyPackages: () => ({
    data: {
      items: [
        {
          id: 301,
          tenantId: "tenant-A",
          packageCode: "TERM.LAB",
          packageVersion: "2026.06",
          displayName: "检验术语映射包",
          scopeLevel: "DEPARTMENT",
          scopeCode: "CARD",
          status: "PUBLISHED",
          mappingCount: 1,
          contentHash: "b".repeat(64),
          createdAt: "2026-06-01T00:00:00Z",
          createdBy: "tester",
          updatedAt: "2026-06-01T00:00:00Z",
          updatedBy: "tester",
        },
      ],
    },
  }),
  downloadPackageDiffExport: vi.fn(),
  downloadPackageOfflineExport: apiMocks.downloadPackageOfflineExport,
}));

describe("ConfigPackages offline package export", () => {
  beforeEach(() => {
    apiMocks.downloadPackageOfflineExport.mockReset();
    apiMocks.downloadPackageOfflineExport.mockResolvedValue(new Blob(["offline-package"]));
    apiMocks.importOfflinePackage.mockReset();
    apiMocks.addPackageItem.mockReset();
    apiMocks.addPackageItem.mockResolvedValue({
      itemId: "item-term",
      packageId: "pkg-offline",
      assetType: "TERMINOLOGY",
      assetId: "TERM.LAB|DEPARTMENT|CARD",
      assetVersion: "2026.06",
    });
    apiMocks.importOfflinePackage.mockResolvedValue({
      packageId: "pkg-imported",
      packageCode: "PKG.IMPORT",
      packageVersion: "2026.06.01",
      status: "DRAFT",
      itemCount: 2,
      payloadSha256: "a".repeat(64),
    });
    apiMocks.releasePackage.mockReset();
    apiMocks.releasePackage.mockResolvedValue({
      status: "NOT_SYNCED",
      logs: [
        {
          logId: "log-1",
          planId: "plan-1",
          targetId: "target-his",
          status: "NOT_SYNCED",
          errorMessage: "未接入真实同步通道",
          syncEvidence: "",
          createdAt: "2026-06-01T00:00:00Z",
        },
      ],
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

  it("uses clear in-hospital release wording and removes old projection copy", async () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.queryByText(/物理投影|物理长链接|物理同步投影/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "院内同步发布" }));

    expect(screen.getByText("院内同步发布中心")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /开始同步发布/ })).toBeInTheDocument();
    expect(screen.queryByText(/物理投影|物理长链接|物理同步投影/)).not.toBeInTheDocument();
  });

  it("adds terminology package assets with the stable package scope key", async () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <ConfigPackages />
        </AntdApp>
      </ConfigProvider>,
    );

    await userEvent.click(screen.getByRole("button", { name: "办理细项" }));

    fireEvent.mouseDown(screen.getByLabelText("资产类型"));
    await userEvent.click(await screen.findByText("术语字典映射 (TERMINOLOGY)"));

    fireEvent.mouseDown(screen.getByLabelText("选择已发布的临床资产"));
    await userEvent.click(await screen.findByText(/检验术语映射包/));
    expect(screen.getByLabelText("资产快照版本")).toHaveValue("2026.06");
    await userEvent.click(screen.getByRole("button", { name: "确认将此资产关联加入当前包草稿" }));

    await waitFor(() => {
      expect(apiMocks.addPackageItem).toHaveBeenCalledWith({
        packageId: "pkg-offline",
        request: {
          assetType: "TERMINOLOGY",
          assetId: "TERM.LAB|DEPARTMENT|CARD",
          assetVersion: "2026.06",
          packageVersion: "3.0.0",
        },
      });
    });
  });
});
