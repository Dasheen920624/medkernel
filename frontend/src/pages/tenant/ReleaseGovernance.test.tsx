import { App as AntdApp, ConfigProvider } from "antd";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type * as ApiHooks from "@/shared/api/hooks";
import ReleaseGovernance from "./ReleaseGovernance";

const publishPlatformAsync = vi.fn();
const activateHospitalAsync = vi.fn();
const rollbackHospitalAsync = vi.fn();
const useOrgUnitsMock = vi.fn();

vi.mock("@/shared/api/hooks", async () => {
  const actual = await vi.importActual<typeof ApiHooks>("@/shared/api/hooks");
  return {
    ...actual,
    useSecurityProfile: () => ({
      data: {
        userId: "operator-a",
        roles: [{ code: "platform-admin", displayName: "平台管理员" }],
        permissions: [],
        menuKeys: ["release-governance"],
        dataScope: { tenantId: "tenant-a", hospitalId: null },
      },
    }),
    useOrgUnits: (params: unknown) => {
      useOrgUnitsMock(params);
      return {
        data: {
          items: [
            {
              id: "hospital-a",
              code: "HOSP-A",
              name: "中心医院",
              level: "FACILITY",
              facilityType: "HOSPITAL",
              orgPath: "/tenant-a/hospital-a",
            },
          ],
        },
        isLoading: false,
      };
    },
    useCurrentPlatformBaseline: () => ({
      data: {
        release: {
          baselineReleaseId: "baseline-A8",
          revisionNo: 8,
          manifestSha256: "a".repeat(64),
          publishedAt: "2026-06-23T08:00:00Z",
          publishedBy: "operator-platform",
        },
        items: [
          {
            sourceTenantId: "platform",
            assetType: "RULE",
            assetIdentity: "RULE.CKD",
            entryState: "ACTIVE",
            versionId: "rule-v1",
            versionNo: "V1",
            contentHash: "1".repeat(64),
          },
          {
            sourceTenantId: "platform",
            assetType: "PATHWAY",
            assetIdentity: "PATH.OLD",
            entryState: "ACTIVE",
            versionId: "path-old-v1",
            versionNo: "V1",
            contentHash: "2".repeat(64),
          },
        ],
      },
      isLoading: false,
      isError: false,
    }),
    usePlatformReleaseCandidates: () => ({
      data: {
        items: [
          {
            sourceLayer: "PLATFORM",
            assetType: "RULE",
            assetIdentity: "RULE.CKD",
            versionId: "rule-v2",
            versionNo: "V2",
            status: "DRAFT",
            organizationScope: "/platform",
            contentHash: "3".repeat(64),
            sourceRef: "指南 2026",
            updatedAt: "2026-06-23T09:00:00Z",
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
        totalEstimated: false,
      },
      isLoading: false,
    }),
    usePublishPlatformBaseline: () => ({
      mutateAsync: publishPlatformAsync,
      isPending: false,
    }),
    useCurrentHospitalRuntime: (hospitalId: string | undefined) => ({
      data: hospitalId
        ? {
            release: {
              releaseId: "runtime-H9",
              tenantId: "tenant-a",
              hospitalId,
              revisionNo: 9,
              platformBaselineReleaseId: "baseline-A8",
              manifestSha256: "b".repeat(64),
              activatedAt: "2026-06-23T09:00:00Z",
              activatedBy: "operator-a",
            },
            items: [
              {
                releaseId: "runtime-H9",
                sourceTenantId: "platform",
                sourceLayer: "PLATFORM",
                assetType: "RULE",
                assetIdentity: "RULE.CKD",
                entryState: "ACTIVE",
                versionId: "rule-v1",
                versionNo: "V1",
              },
            ],
          }
        : undefined,
      isLoading: false,
      isError: false,
    }),
    useHospitalRuntimeCandidates: () => ({
      data: {
        items: [
          {
            sourceLayer: "HOSPITAL",
            assetType: "PATHWAY",
            assetIdentity: "PATH.CKD.LOCAL",
            versionId: "path-v3",
            versionNo: "V3",
            status: "PUBLISHED",
            organizationScope: "/tenant-a/hospital-a",
            contentHash: "4".repeat(64),
            sourceRef: "本院路径",
            updatedAt: "2026-06-23T09:30:00Z",
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
        totalEstimated: false,
      },
      isLoading: false,
    }),
    useHospitalRuntimeHistory: () => ({
      data: {
        items: [
          {
            releaseId: "runtime-H9",
            revisionNo: 9,
            platformBaselineReleaseId: "baseline-A8",
            manifestSha256: "b".repeat(64),
            activatedAt: "2026-06-23T09:00:00Z",
            activatedBy: "operator-a",
          },
          {
            releaseId: "runtime-H7",
            revisionNo: 7,
            platformBaselineReleaseId: "baseline-A7",
            manifestSha256: "c".repeat(64),
            activatedAt: "2026-06-22T09:00:00Z",
            activatedBy: "operator-a",
          },
        ],
        page: 1,
        size: 20,
        total: 2,
        hasNext: false,
        totalEstimated: false,
      },
      isLoading: false,
    }),
    useActivateHospitalRuntime: () => ({
      mutateAsync: activateHospitalAsync,
      isPending: false,
    }),
    useRollbackHospitalRuntime: () => ({
      mutateAsync: rollbackHospitalAsync,
      isPending: false,
    }),
  };
});

function renderPage() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <ReleaseGovernance />
        </MemoryRouter>
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("ReleaseGovernance", () => {
  beforeEach(() => {
    publishPlatformAsync.mockReset();
    activateHospitalAsync.mockReset();
    rollbackHospitalAsync.mockReset();
    useOrgUnitsMock.mockReset();
    publishPlatformAsync.mockResolvedValue({ revisionNo: 9 });
    activateHospitalAsync.mockResolvedValue({ revisionNo: 10 });
    rollbackHospitalAsync.mockResolvedValue({ revisionNo: 10 });
  });

  it("publishes the selected platform draft and explicit tombstone as the next platform standard version", async () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "发布治理" })).toBeInTheDocument();
    expect(screen.getByText("当前平台标准版本 第 8 版")).toBeInTheDocument();
    expect(
      screen.queryByText(new RegExp(`灰度|覆盖模板|配置${"包"}版本|候选版本 ID`)),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("发布 RULE.CKD V2"));
    fireEvent.click(screen.getByLabelText("停用 PATH.OLD"));
    fireEvent.click(screen.getByRole("button", { name: "发布新平台标准版本" }));

    await waitFor(() =>
      expect(publishPlatformAsync).toHaveBeenCalledWith({
        publishVersionIds: ["rule-v2"],
        disabledAssets: [{ assetType: "PATHWAY", assetIdentity: "PATH.OLD" }],
      }),
    );
  });

  it("builds one institution effective version from platform and local asset selections", async () => {
    renderPage();
    fireEvent.click(screen.getByRole("tab", { name: "机构生效版本" }));
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "目标医院" }));
    fireEvent.click(await screen.findByText(/中心医院/));

    expect(await screen.findByText("当前机构生效版本 第 9 版")).toBeInTheDocument();
    expect(screen.getByLabelText("启用平台内容 RULE.CKD")).toBeChecked();
    fireEvent.click(screen.getByLabelText("启用本地内容 PATH.CKD.LOCAL V3"));
    fireEvent.click(screen.getByRole("button", { name: "生成新机构生效版本" }));

    await waitFor(() =>
      expect(activateHospitalAsync).toHaveBeenCalledWith({
        hospitalId: "hospital-a",
        request: {
          platformBaselineReleaseId: "baseline-A8",
          expectedCurrentReleaseId: "runtime-H9",
          activeAssets: [
            { assetType: "RULE", assetIdentity: "RULE.CKD", versionId: null },
            {
              assetType: "PATHWAY",
              assetIdentity: "PATH.CKD.LOCAL",
              versionId: "path-v3",
            },
          ],
        },
      }),
    );
  });

  it("rolls back only by selecting a real historical institution effective version", async () => {
    renderPage();
    fireEvent.click(screen.getByRole("tab", { name: "机构生效版本" }));
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "目标医院" }));
    fireEvent.click(await screen.findByText(/中心医院/));
    fireEvent.click(await screen.findByRole("button", { name: "回滚到 第 7 版" }));
    fireEvent.click(await screen.findByRole("button", { name: "确认回滚" }));

    await waitFor(() =>
      expect(rollbackHospitalAsync).toHaveBeenCalledWith({
        hospitalId: "hospital-a",
        targetReleaseId: "runtime-H7",
      }),
    );
  });

  it("loads hospitals through bounded server-side organization filtering", () => {
    renderPage();

    expect(useOrgUnitsMock).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 1,
        size: 50,
        level: "FACILITY",
        status: "ACTIVE",
      }),
    );
  });
});
