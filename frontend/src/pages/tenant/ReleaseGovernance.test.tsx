import { App as AntdApp, ConfigProvider } from "antd";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type * as ApiHooks from "@/shared/api/hooks";
import ReleaseGovernance from "./ReleaseGovernance";

const simulateAsync = vi.fn();
const startRolloutAsync = vi.fn();
const observeAsync = vi.fn();
const rollbackRolloutAsync = vi.fn();
const createTemplateAsync = vi.fn();
const useOrgUnitsMock = vi.fn();
const useOverrideTemplatesMock = vi.fn();

vi.mock("@/shared/api/hooks", async () => {
  const actual = await vi.importActual<typeof ApiHooks>("@/shared/api/hooks");
  return {
    ...actual,
    useSecurityProfile: () => ({
      data: {
        userId: "publisher-a",
        roles: [{ code: "organization-admin", displayName: "院级管理员" }],
        permissions: [],
        menuKeys: ["config-packages"],
        dataScope: { tenantId: "tenant-a" },
      },
    }),
    useOrgUnits: (params: unknown) => {
      useOrgUnitsMock(params);
      return {
        data: {
          items: [
            {
              id: "org-a",
              code: "ORG-A",
              name: "中心医院",
              level: "FACILITY",
              facilityType: "HOSPITAL",
              orgPath: "/tenant-a/org-a",
            },
          ],
        },
      };
    },
    useReleaseSimulation: () => ({ mutateAsync: simulateAsync, isPending: false }),
    useStartReleaseRollout: () => ({ mutateAsync: startRolloutAsync, isPending: false }),
    useObserveReleaseRollout: () => ({ mutateAsync: observeAsync, isPending: false }),
    useRollbackRollout: () => ({ mutateAsync: rollbackRolloutAsync, isPending: false }),
    useOverrideTemplates: (params: unknown) => {
      useOverrideTemplatesMock(params);
      return {
        data: {
          items: [],
          page: 1,
          size: 20,
          total: 0,
          hasNext: false,
          totalEstimated: false,
        },
        isLoading: false,
        isError: false,
      };
    },
    useCreateOverrideTemplate: () => ({ mutateAsync: createTemplateAsync, isPending: false }),
    usePreviewOverrideBatch: () => ({ mutateAsync: vi.fn(), isPending: false }),
    useApplyOverrideBatch: () => ({ mutateAsync: vi.fn(), isPending: false }),
    useRevokeOverrideBatch: () => ({ mutateAsync: vi.fn(), isPending: false }),
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
    simulateAsync.mockReset();
    startRolloutAsync.mockReset();
    observeAsync.mockReset();
    rollbackRolloutAsync.mockReset();
    createTemplateAsync.mockReset();
    useOrgUnitsMock.mockReset();
    useOverrideTemplatesMock.mockReset();
    simulateAsync.mockResolvedValue({
      simulationDigest: "digest-a",
      candidateVersionId: "version-a",
      currentVersionId: "version-current",
      affectedOrganizations: [
        { orgUnitId: "org-a", orgPath: "/tenant-a/org-a", orgName: "中心医院" },
      ],
      applicableDimensions: ["ALL"],
      diff: {
        changeType: "MODIFIED",
        currentVersionNo: "1",
        candidateVersionNo: "2",
        currentContentHash: "old",
        candidateContentHash: "new",
      },
      replay: {
        status: "SUPPORTED",
        sampledCases: 40,
        changedCases: 6,
        triggerIncreases: 4,
        triggerDecreases: 2,
        severityIncreases: 1,
        severityDecreases: 0,
        highRiskSnapshotIds: [],
      },
      safety: { passed: true, issues: [] },
      dependencies: { passed: true, issues: [] },
      conflicts: [],
      releasable: true,
    });
    startRolloutAsync.mockResolvedValue({
      planId: "plan-a",
      versionId: "version-a",
      fromVersionId: "version-current",
      status: "GRAY",
      rolloutStageIndex: 0,
    });
    observeAsync.mockResolvedValue({
      plan: {
        planId: "plan-a",
        versionId: "version-a",
        fromVersionId: "version-current",
        status: "PAUSED",
        rolloutStageIndex: 0,
        rolloutPausedReason: "异常率超过阈值",
      },
      paused: true,
      readyForFullRelease: false,
      currentStagePercent: 10,
    });
    rollbackRolloutAsync.mockResolvedValue({
      planId: "plan-a",
      versionId: "version-a",
      fromVersionId: "version-current",
      status: "ROLLED_BACK",
      rolloutStageIndex: 0,
    });
    createTemplateAsync.mockResolvedValue({});
  });

  it("shows impact evidence and starts rollout with the confirmed server digest", async () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "发布治理" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /一键回退/ })).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("资产身份"), { target: { value: "RULE.VTE.RISK" } });
    fireEvent.change(screen.getByLabelText("候选版本 ID"), { target: { value: "version-a" } });
    fireEvent.mouseDown(screen.getByLabelText("目标组织"));
    fireEvent.click(await screen.findByText(/中心医院/));
    fireEvent.click(screen.getByRole("button", { name: /运行影响模拟/ }));

    expect(await screen.findByText("影响 1 个组织")).toBeInTheDocument();
    expect(screen.getByText("40 个病例样本")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /确认并启动灰度/ }));
    fireEvent.change(screen.getByLabelText("发布说明"), {
      target: { value: "完成临床与依赖复核" },
    });
    fireEvent.click(screen.getByRole("button", { name: /^启动灰度$/ }));

    await waitFor(() =>
      expect(startRolloutAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          confirmedSimulationDigest: "digest-a",
          simulation: expect.objectContaining({
            assetIdentity: "RULE.VTE.RISK",
            candidateVersionId: "version-a",
          }),
        }),
      ),
    );
  });

  it("searches the organization directory through bounded server pagination", async () => {
    renderPage();

    expect(useOrgUnitsMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, size: 50, keyword: undefined, status: "ACTIVE" }),
    );

    const organizationSelect = screen.getByLabelText("目标组织");
    fireEvent.mouseDown(organizationSelect);
    fireEvent.change(organizationSelect, { target: { value: "中心" } });

    await waitFor(() =>
      expect(useOrgUnitsMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1, size: 50, keyword: "中心", status: "ACTIVE" }),
      ),
    );
  });

  it("loads override templates through bounded server pagination", () => {
    renderPage();

    fireEvent.click(screen.getByRole("tab", { name: "覆盖模板与批量复用" }));

    expect(useOverrideTemplatesMock).toHaveBeenLastCalledWith({ page: 1, size: 20 });
  });

  it("updates the visible rollout state from observation and rolls back to the recorded pin", async () => {
    renderPage();
    fireEvent.change(screen.getByLabelText("资产身份"), { target: { value: "RULE.VTE.RISK" } });
    fireEvent.change(screen.getByLabelText("候选版本 ID"), { target: { value: "version-a" } });
    fireEvent.mouseDown(screen.getByLabelText("目标组织"));
    fireEvent.click(await screen.findByText(/中心医院/));
    fireEvent.click(screen.getByRole("button", { name: /运行影响模拟/ }));
    await screen.findByText("影响 1 个组织");
    fireEvent.click(screen.getByRole("button", { name: /确认并启动灰度/ }));
    fireEvent.change(screen.getByLabelText("发布说明"), {
      target: { value: "完成临床与依赖复核" },
    });
    fireEvent.click(screen.getByRole("button", { name: /^启动灰度$/ }));
    await screen.findByText("当前阶段 1");

    expect(screen.getByLabelText("样本数")).toHaveValue("100");
    fireEvent.change(screen.getByLabelText("样本数"), { target: { value: "100" } });
    fireEvent.change(screen.getByLabelText("命中数"), { target: { value: "30" } });
    fireEvent.change(screen.getByLabelText("阻断数"), { target: { value: "10" } });
    fireEvent.change(screen.getByLabelText("人工拒绝数"), { target: { value: "5" } });
    fireEvent.change(screen.getByLabelText("异常数"), { target: { value: "6" } });
    fireEvent.click(screen.getByRole("button", { name: "提交观察窗数据" }));

    expect(await screen.findByText("异常率超过阈值")).toBeInTheDocument();
    expect(screen.getByText("已暂停")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "提交观察窗数据" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "回退版本" }));
    const rollbackTitle = await screen.findByText("回退灰度计划");
    const rollbackDialog = rollbackTitle.closest('[role="dialog"]');
    expect(rollbackDialog).not.toBeNull();
    const dialog = within(rollbackDialog as HTMLElement);
    expect(dialog.getByText(/version-current/)).toBeInTheDocument();
    expect(dialog.queryByLabelText("当前版本 ID")).not.toBeInTheDocument();
    fireEvent.change(dialog.getByLabelText("回退原因"), {
      target: { value: "灰度异常，恢复上一钉点" },
    });
    fireEvent.click(dialog.getByLabelText("已确认停止本次灰度并恢复上一钉点"));
    fireEvent.click(dialog.getByRole("button", { name: "确认回退" }));

    await waitFor(() =>
      expect(rollbackRolloutAsync).toHaveBeenCalledWith({
        planId: "plan-a",
        reason: "灰度异常，恢复上一钉点",
        confirmedHighRisk: true,
      }),
    );
    expect(await screen.findByText("已回滚")).toBeInTheDocument();
  }, 15_000);

  it("collects complete governance evidence when creating an override template", async () => {
    renderPage();

    fireEvent.click(screen.getByRole("tab", { name: "覆盖模板与批量复用" }));
    fireEvent.click(screen.getByRole("button", { name: /新建模板/ }));
    const dialog = within(screen.getByRole("dialog", { name: "新建覆盖模板" }));
    fireEvent.change(dialog.getByLabelText("模板名称"), {
      target: { value: "儿科规则模板" },
    });
    fireEvent.change(dialog.getByLabelText("资产身份"), {
      target: { value: "RULE.PEDIATRIC.DOSE" },
    });
    fireEvent.change(dialog.getByLabelText("继承版本 ID"), {
      target: { value: "version-platform-1" },
    });
    fireEvent.change(dialog.getByLabelText("差异摘要"), {
      target: { value: "按儿科制度收紧剂量阈值" },
    });
    fireEvent.change(dialog.getByLabelText("覆盖原因"), {
      target: { value: "本院儿科用药制度要求" },
    });
    fireEvent.click(dialog.getByRole("button", { name: "创建模板" }));

    await waitFor(() =>
      expect(createTemplateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          applicableScope: "ALL",
          items: [
            expect.objectContaining({
              assetIdentity: "RULE.PEDIATRIC.DOSE",
              inheritedVersionId: "version-platform-1",
              applicableScope: "ALL",
              diffSummary: "按儿科制度收紧剂量阈值",
              overrideReason: "本院儿科用药制度要求",
            }),
          ],
        }),
      ),
    );
    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "新建覆盖模板" })).not.toBeInTheDocument(),
    );
  });
});
