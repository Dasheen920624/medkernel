import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useLargeListExportJob,
  useSaveView,
  useSavedViews,
  useSecurityProfile,
  useSubmitLargeListExport,
  useBatchConfirmTerminologyCandidates,
  useBuildTerminologyKnowledgePackage,
  useConfirmTerminologyCandidate,
  useGenerateTerminologyCandidates,
  useLocalTerms,
  usePackages,
  usePackageReleaseAdapters,
  useReleasePackage,
  useRollbackPackage,
  useStandardTerms,
  useTerminologyCandidates,
  useTerminologyConflicts,
  useTerminologyMappings,
  type KnowledgePackage,
  type LocalTerm,
  type MappingConflict,
  type StandardTerm,
  type SecurityProfile,
  type TermMapping,
  type TermMappingCandidate,
} from "@/shared/api/hooks";

import TerminologyMapping from "./TerminologyMapping";

const TERMINOLOGY_INTERACTION_TIMEOUT_MS = 15_000;

vi.mock("@/shared/api/hooks", () => ({
  parseSavedExperienceView: vi.fn((view) =>
    view?.definitionJson ? JSON.parse(view.definitionJson) : null,
  ),
  useLargeListExportJob: vi.fn(),
  useBatchConfirmTerminologyCandidates: vi.fn(),
  useBuildTerminologyKnowledgePackage: vi.fn(),
  useConfirmTerminologyCandidate: vi.fn(),
  useGenerateTerminologyCandidates: vi.fn(),
  useLocalTerms: vi.fn(),
  usePackages: vi.fn(),
  usePackageReleaseAdapters: vi.fn(),
  useReleasePackage: vi.fn(),
  useRollbackPackage: vi.fn(),
  useSaveView: vi.fn(),
  useSavedViews: vi.fn(),
  useSecurityProfile: vi.fn(),
  useStandardTerms: vi.fn(),
  useSubmitLargeListExport: vi.fn(),
  useTerminologyCandidates: vi.fn(),
  useTerminologyConflicts: vi.fn(),
  useTerminologyMappings: vi.fn(),
}));

const mapping: TermMapping = {
  id: 1,
  tenantId: "tenant-1",
  localTermId: 100,
  standardTermId: 200,
  sourceSystem: "HIS",
  category: "ICD-10",
  confidence: 0.96,
  riskLevel: "MEDIUM",
  status: "DRAFT",
  evidenceText: "实施核查证据",
  confirmedBy: "审核员",
  confirmedAt: "2026-05-25T00:00:00.000Z",
  updatedAt: "2026-05-26T00:00:00.000Z",
};

const profile: SecurityProfile = {
  userId: "user-1",
  username: "it.owner",
  roles: [
    {
      code: "integration-operator",
      displayName: "信息科",
      source: "DEFAULT",
      scopeLevel: null,
      scopeCode: null,
    },
  ],
  mustChangePwd: false,
  mfaRequired: false,
  mfaBound: false,
  permissions: [
    {
      code: "advanced.read",
      dimension: "ACTION",
      target: "advanced",
      displayName: "高级工具",
      risk: "LOW",
    },
    {
      code: "list.export",
      dimension: "ACTION",
      target: "large-list.export",
      displayName: "列表导出",
      risk: "MEDIUM",
    },
    {
      code: "term.read",
      dimension: "ACTION",
      target: "terminology",
      displayName: "读取字典映射",
      risk: "LOW",
    },
    {
      code: "term.write",
      dimension: "ACTION",
      target: "terminology",
      displayName: "确认字典映射",
      risk: "MEDIUM",
    },
    {
      code: "term.publish",
      dimension: "ACTION",
      target: "terminology-package",
      displayName: "发布映射包",
      risk: "HIGH",
    },
    {
      code: "package.rollback",
      dimension: "ACTION",
      target: "terminology-package",
      displayName: "回滚映射包",
      risk: "HIGH",
    },
  ],
  menuKeys: ["terminology-mapping", "provenance"],
  environmentKeys: ["production"],
  dataScope: {
    tenantId: "tenant-1",
    groupId: null,
    hospitalId: null,
    campusId: null,
    siteId: null,
    departmentId: null,
    specialtyId: null,
  },
};

const standardTerm: StandardTerm = {
  id: 200,
  tenantId: "tenant-1",
  standardSystem: "LOINC",
  termCode: "2951-2",
  category: "LAB",
  displayName: "血清钠",
  normalizedName: "血清钠",
  versionNo: "2026.06",
  status: "ACTIVE",
  sourceVersionId: 12,
  evidenceText: "LOINC 2026-06 来源版本",
  updatedAt: "2026-06-01T00:00:00.000Z",
};

const localTerm: LocalTerm = {
  id: 100,
  tenantId: "tenant-1",
  sourceSystem: "LIS",
  localCode: "K",
  category: "LAB",
  localName: "血清钾",
  normalizedName: "血清钾",
  departmentId: "dept-lab",
  status: "UNMAPPED",
  lastSeenAt: "2026-06-01T00:00:00.000Z",
};

const highRiskCandidate: TermMappingCandidate = {
  id: 901,
  localTermId: 100,
  standardTermId: 200,
  semanticMatchScore: 0.44,
  highRiskFlag: true,
  riskLevel: "HIGH",
  source: "RULE",
  status: "PENDING",
  evidenceText: "钾 / 钠不可互换，高危近似规则命中",
};

const ordinaryCandidate: TermMappingCandidate = {
  id: 902,
  localTermId: 101,
  standardTermId: 201,
  semanticMatchScore: 0.92,
  highRiskFlag: false,
  riskLevel: "LOW",
  source: "RULE",
  status: "PENDING",
  evidenceText: "编码和名称精确匹配",
};

const openConflict: MappingConflict = {
  id: 301,
  tenantId: "tenant-1",
  conflictType: "ONE_TO_MANY",
  localTermId: 100,
  standardTermId: 200,
  mappingId: null,
  riskLevel: "HIGH",
  description: "同一院内检验项命中多个标准候选，需人工裁决。",
  status: "OPEN",
  createdAt: "2026-06-01T00:00:00.000Z",
};

const mappingPackage: KnowledgePackage = {
  id: 30,
  packageId: "term-pkg-30",
  tenantId: "tenant-1",
  packageCode: "TERM.LAB",
  packageVersion: "2026.06",
  name: "检验字典映射包",
  description: "术语映射快照",
  accessPolicy: "OPEN",
  status: "DRAFT",
  createdAt: "2026-06-01T00:00:00.000Z",
  createdBy: "it.owner",
  updatedAt: "2026-06-01T00:00:00.000Z",
  updatedBy: "it.owner",
  traceId: "trace-package",
  assetTypes: ["TERMINOLOGY"],
  primaryAssetId: "TERM.LAB|FACILITY|hospital-A",
  primaryAssetVersion: "2026.06",
  itemCount: 1,
};

const defaultData = {
  items: [mapping],
  page: 1,
  size: 20,
  total: 41,
  hasNext: true,
  totalEstimated: false,
  traceId: "trace-list",
};

const defaultSavedView = {
  savedViewId: "sv-default",
  pageKey: "terminology.mapping",
  viewName: "默认视图",
  definitionJson: JSON.stringify({
    viewKey: "terminology.mapping",
    filters: [{ key: "sourceSystem", value: "HIS" }],
    pageRequest: {
      pageNumber: 1,
      pageSize: 20,
      sortBy: "updatedAt",
      sortOrder: "desc",
      filters: { sourceSystem: "HIS" },
    },
    visibleColumnKeys: ["sourceSystem", "status", "updatedAt"],
    expertMode: true,
    capturedAt: "2026-06-01T00:00:00.000Z",
  }),
  defaultView: true,
  version: 3,
  updatedAt: "2026-06-01T00:00:00.000Z",
  updatedBy: "doctor-1",
};

function pageData<T>(items: T[]) {
  return {
    items,
    page: 1,
    size: 20,
    total: items.length,
    hasNext: false,
    totalEstimated: false,
    traceId: "trace-page",
  };
}

function configureQuery(
  queryOverrides: Record<string, unknown> = {},
  securityProfile: SecurityProfile = profile,
) {
  vi.mocked(useSecurityProfile).mockReturnValue({ data: securityProfile } as never);
  vi.mocked(useSavedViews).mockReturnValue({ data: [] } as never);
  vi.mocked(useSaveView).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useSubmitLargeListExport).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useLargeListExportJob).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useGenerateTerminologyCandidates).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useConfirmTerminologyCandidate).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useBatchConfirmTerminologyCandidates).mockReturnValue({
    mutateAsync: vi.fn(),
  } as never);
  vi.mocked(useBuildTerminologyKnowledgePackage).mockReturnValue({
    mutateAsync: vi.fn(),
  } as never);
  vi.mocked(useReleasePackage).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useRollbackPackage).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(usePackageReleaseAdapters).mockReturnValue({
    data: [
      {
        adapterId: "adapter-1",
        adapterName: "院内配置同步",
        protocolType: "REST",
        status: "ACTIVE",
        healthStatus: "HEALTHY",
        rttMs: 12,
        lastHeartbeatAt: "2026-06-01T00:00:00.000Z",
        connectorAvailable: true,
      },
    ],
  } as never);
  vi.mocked(useStandardTerms).mockReturnValue({
    data: pageData([standardTerm]),
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useLocalTerms).mockReturnValue({
    data: pageData([localTerm]),
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useTerminologyCandidates).mockReturnValue({
    data: pageData([highRiskCandidate, ordinaryCandidate]),
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useTerminologyConflicts).mockReturnValue({
    data: pageData([openConflict]),
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(usePackages).mockReturnValue({
    data: pageData([mappingPackage]),
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useTerminologyMappings).mockReturnValue({
    data: defaultData,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...queryOverrides,
  } as never);
}

function renderPage() {
  return render(
    <ConfigProvider>
      <TerminologyMapping />
    </ConfigProvider>,
  );
}

describe("TerminologyMapping experience sample", () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.clearAllMocks();
    configureQuery();
  });

  it("renders the real high-risk mapping workspace instead of a read-only table", async () => {
    renderPage();

    expect(screen.getByText(/核查院内码与标准码的映射关系/)).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "映射状态" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText("输入来源系统")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("输入院内码或标准码关键词")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认候选" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "确认候选" })).toHaveLength(1);
    expect(screen.getByText("选字典")).toBeInTheDocument();
    expect(screen.getByText("生成候选")).toBeInTheDocument();
    expect(screen.getByText("逐条确认")).toBeInTheDocument();
    expect(screen.getByText("灰度发布")).toBeInTheDocument();
    expect(screen.getByText("证据/回滚")).toBeInTheDocument();
    expect(screen.getByText("候选映射")).toBeInTheDocument();
    expect(screen.getByText("高危近似")).toBeInTheDocument();
    expect(screen.getByText("钾 / 钠不可互换，高危近似规则命中")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "批量确认候选" })).toBeDisabled();
    expect(screen.getByText("冲突待裁")).toBeInTheDocument();
    expect(screen.getByText("同一院内检验项命中多个标准候选，需人工裁决。")).toBeInTheDocument();
    expect(screen.getByText("映射包发布")).toBeInTheDocument();
    expect(screen.getByText("检验字典映射包")).toBeInTheDocument();
    expect(useTerminologyMappings).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1, size: 20, sort: "updatedAt,desc" }),
    );
    expect(useStandardTerms).toHaveBeenCalledWith(expect.objectContaining({ size: 20 }));
    expect(useLocalTerms).toHaveBeenCalledWith(expect.objectContaining({ size: 20 }));
    expect(useTerminologyCandidates).toHaveBeenCalledWith(
      expect.objectContaining({ status: "PENDING", riskLevel: "HIGH" }),
    );
    expect(useTerminologyConflicts).toHaveBeenCalledWith(
      expect.objectContaining({ status: "OPEN" }),
    );
    expect(usePackages).toHaveBeenCalledWith(
      expect.objectContaining({ size: 10, assetType: "TERMINOLOGY" }),
    );
    expect(usePackageReleaseAdapters).toHaveBeenCalledWith(true);

    expect(screen.getByRole("button", { name: "导出" })).toBeEnabled();

    await userEvent.click(screen.getByRole("button", { name: "查看 1" }));
    expect(screen.getByText("实施核查证据")).toBeInTheDocument();
    expect(screen.queryByText("院内编码 ID")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("switch", { name: "专家模式" }));
    expect(screen.getByText("院内编码 ID")).toBeInTheDocument();
    expect(screen.getByText(/trace-list/)).toBeInTheDocument();

    await userEvent.click(screen.getByTitle("2"));
    expect(useTerminologyMappings).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 2, size: 20, sort: "updatedAt,desc" }),
    );
  });

  it("separates specialist authoring from medical-affairs publishing", () => {
    const specialistProfile: SecurityProfile = {
      ...profile,
      roles: [
        {
          code: "knowledge-governor",
          displayName: "专科专家",
          source: "DEFAULT",
          scopeLevel: null,
          scopeCode: null,
        },
      ],
      permissions: profile.permissions.filter((permission) =>
        ["term.read", "term.write"].includes(permission.code),
      ),
    };
    configureQuery({}, specialistProfile);
    const view = renderPage();

    expect(screen.queryByText("当前权限不足")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认候选" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "构建映射包" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "发布映射包" })).toBeDisabled();
    expect(usePackageReleaseAdapters).toHaveBeenLastCalledWith(false);

    const medicalAffairsProfile: SecurityProfile = {
      ...profile,
      roles: [
        {
          code: "clinical-governor",
          displayName: "医务处",
          source: "DEFAULT",
          scopeLevel: null,
          scopeCode: null,
        },
      ],
      permissions: profile.permissions.filter((permission) =>
        ["term.read", "term.publish"].includes(permission.code),
      ),
    };
    configureQuery({}, medicalAffairsProfile);
    view.rerender(
      <ConfigProvider>
        <TerminologyMapping />
      </ConfigProvider>,
    );

    expect(screen.queryByText("当前权限不足")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认候选" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "构建映射包" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "发布映射包" })).toBeEnabled();
    expect(usePackageReleaseAdapters).toHaveBeenLastCalledWith(true);
  });

  it("requires per-candidate second confirmation before confirming a high-risk mapping", async () => {
    const confirmCandidate = vi.fn().mockResolvedValue({ ...mapping, status: "CONFIRMED" });
    vi.mocked(useConfirmTerminologyCandidate).mockReturnValue({
      mutateAsync: confirmCandidate,
    } as never);

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "确认候选" }));
    expect(screen.getByText("确认高危候选")).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText("已逐条核对高危近似风险"));
    await userEvent.type(
      screen.getByLabelText("高危确认理由"),
      "已核对 LIS 原始值和 LOINC 来源版本",
    );
    await userEvent.click(screen.getByRole("button", { name: "提交确认" }));

    expect(confirmCandidate).toHaveBeenCalledWith({
      candidateId: 901,
      request: expect.objectContaining({
        packageVersion: "2026.06",
        reviewNote: "逐条确认高危候选",
        highRiskAcknowledged: true,
        highRiskReason: "已核对 LIS 原始值和 LOINC 来源版本",
      }),
    });
  });

  it("batch confirms only ordinary candidates when the high-risk queue is absent", async () => {
    const batchConfirm = vi
      .fn()
      .mockResolvedValue({ confirmedCount: 1, confirmedCandidateIds: [902] });
    vi.mocked(useTerminologyCandidates).mockReturnValue({
      data: pageData([ordinaryCandidate]),
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);
    vi.mocked(useBatchConfirmTerminologyCandidates).mockReturnValue({
      mutateAsync: batchConfirm,
    } as never);

    renderPage();

    expect(screen.getByRole("button", { name: "批量确认候选" })).toBeEnabled();
    await userEvent.click(screen.getByRole("button", { name: "批量确认候选" }));

    expect(batchConfirm).toHaveBeenCalledWith({
      candidateIds: [902],
      request: expect.objectContaining({
        packageVersion: "2026.06",
        reviewNote: "批量确认普通候选",
      }),
    });
  });

  it("builds a new terminology package with an explicit version and current scope", async () => {
    const buildPackage = vi.fn().mockResolvedValue(mappingPackage);
    vi.mocked(useBuildTerminologyKnowledgePackage).mockReturnValue({
      mutateAsync: buildPackage,
    } as never);

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "构建映射包" }));
    expect(screen.getByText("构建术语映射包")).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText("新版本"));
    await userEvent.type(screen.getByLabelText("新版本"), "2026.06.1");
    await userEvent.click(screen.getByRole("button", { name: "创建草稿" }));

    expect(buildPackage).toHaveBeenCalledWith({
      packageCode: "TERM.LAB",
      packageVersion: "2026.06.1",
      name: "检验字典映射包",
      scopeLevel: "TENANT",
      scopeCode: "tenant-1",
    });
  });

  it(
    "publishes terminology packages through the governed release flow",
    async () => {
      const publishPackage = vi.fn().mockResolvedValue({ ...mappingPackage, status: "PUBLISHED" });
      vi.mocked(useReleasePackage).mockReturnValue({
        mutateAsync: publishPackage,
      } as never);

      renderPage();

      await userEvent.click(screen.getByRole("button", { name: "发布映射包" }));
      expect(screen.getByText("发布映射包")).toBeInTheDocument();
      expect(screen.getByRole("radio", { name: "10% 灰度" })).toBeChecked();
      await userEvent.type(screen.getByLabelText("发布原因"), "首发检验字典灰度验证");
      await userEvent.click(screen.getByRole("button", { name: "提交发布" }));

      expect(publishPackage).toHaveBeenCalledWith({
        packageId: "term-pkg-30",
        request: expect.objectContaining({
          packageVersion: "2026.06",
          strategy: "GRAYSCALE",
          targetOrgUnitId: "hospital-A",
          adapterIds: ["adapter-1"],
          reason: "首发检验字典灰度验证",
        }),
      });
    },
    TERMINOLOGY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "blocks grayscale release when the package carries a legacy organization scope",
    async () => {
      const publishPackage = vi.fn();
      vi.mocked(useReleasePackage).mockReturnValue({
        mutateAsync: publishPackage,
      } as never);
      vi.mocked(usePackages).mockReturnValue({
        data: pageData([
          {
            ...mappingPackage,
            primaryAssetId: "TERM.LAB|HOSPITAL|hospital-A",
          },
        ]),
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      } as never);

      renderPage();

      await userEvent.click(screen.getByRole("button", { name: "发布映射包" }));
      await userEvent.type(screen.getByLabelText("发布原因"), "验证非法组织范围必须被阻断");
      await userEvent.click(screen.getByRole("button", { name: "提交发布" }));

      expect(await screen.findByText("知识包缺少有效组织作用域，无法灰度发布")).toBeInTheDocument();
      expect(publishPackage).not.toHaveBeenCalled();
    },
    TERMINOLOGY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "rolls back a published package only to a selectable historical release",
    async () => {
      const rollbackPackage = vi
        .fn()
        .mockResolvedValue({ ...mappingPackage, status: "ROLLED_BACK" });
      const current = { ...mappingPackage, status: "ACTIVE" as const };
      const target = {
        ...mappingPackage,
        id: 29,
        packageId: "term-pkg-29",
        packageVersion: "2026.05",
        status: "OFFLINE" as const,
      };
      vi.mocked(usePackages).mockReturnValue({
        data: pageData([current, target]),
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      } as never);
      vi.mocked(useRollbackPackage).mockReturnValue({
        mutateAsync: rollbackPackage,
      } as never);

      renderPage();
      await userEvent.click(screen.getByRole("button", { name: "回滚映射包" }));
      await userEvent.click(screen.getByLabelText("回滚目标"));
      await userEvent.click(screen.getByText("2026.05 · 检验字典映射包"));
      await userEvent.type(screen.getByLabelText("回滚原因"), "灰度验证发现院内码需重裁");
      await userEvent.click(screen.getByLabelText("已确认回滚会切换当前生效术语版本"));
      await userEvent.click(screen.getByRole("button", { name: "提交回滚" }));

      expect(rollbackPackage).toHaveBeenCalledWith({
        packageId: "term-pkg-30",
        request: expect.objectContaining({
          packageVersion: "2026.06",
          targetPackageId: "term-pkg-29",
          confirmedTargetVersion: "2026.05",
          confirmedHighRisk: true,
          reason: "灰度验证发现院内码需重裁",
        }),
      });
    },
    TERMINOLOGY_INTERACTION_TIMEOUT_MS,
  );

  it("loads the backend default view snapshot", async () => {
    vi.mocked(useSavedViews).mockReturnValue({ data: [defaultSavedView] } as never);

    renderPage();

    expect(await screen.findByDisplayValue("HIS")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "专家模式" })).toBeChecked();
  });

  it("saves a non-sensitive view snapshot through the backend", async () => {
    const saveView = vi.fn().mockResolvedValue(defaultSavedView);
    vi.mocked(useSaveView).mockReturnValue({ mutateAsync: saveView } as never);

    renderPage();
    await userEvent.type(screen.getByPlaceholderText("输入来源系统"), "HIS");
    await userEvent.click(screen.getByRole("switch", { name: "专家模式" }));
    await userEvent.click(screen.getByRole("button", { name: "保存视图" }));

    expect(saveView).toHaveBeenCalledWith(
      expect.objectContaining({
        pageKey: "terminology.mapping",
        viewName: "默认视图",
        defaultView: true,
        snapshot: expect.objectContaining({ expertMode: true }),
      }),
    );
    expect(JSON.stringify(saveView.mock.calls[0][0].snapshot)).not.toMatch(
      /patient|token|身份证|患者/i,
    );
  });

  it("submits async export using the current backend view snapshot", async () => {
    const submitExport = vi.fn().mockResolvedValue({
      jobId: "job-1",
      status: "running",
      submittedAt: "2026-06-01T00:00:00.000Z",
      submittedBy: "doctor-1",
      traceId: "trace-export",
    });
    const pollExport = vi.fn().mockResolvedValue({
      jobId: "job-1",
      status: "succeeded",
      submittedAt: "2026-06-01T00:00:00.000Z",
      submittedBy: "doctor-1",
      traceId: "trace-export",
      downloadUrl: "/medkernel/api/v1/large-lists/exports/job-1/download",
    });
    vi.mocked(useSubmitLargeListExport).mockReturnValue({ mutateAsync: submitExport } as never);
    vi.mocked(useLargeListExportJob).mockReturnValue({ mutateAsync: pollExport } as never);

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "导出" }));
    await userEvent.click(screen.getByRole("button", { name: "提交导出任务" }));

    expect(submitExport).toHaveBeenCalledWith(
      expect.objectContaining({
        resourceType: "TERMINOLOGY_MAPPING",
        selectedScope: "currentPage",
        requestSnapshot: expect.objectContaining({ viewKey: "terminology.mapping" }),
      }),
    );
    expect(await screen.findByText("导出已完成")).toBeInTheDocument();
  });

  it("renders loading, empty, error, forbidden and partial states", () => {
    configureQuery({ isLoading: true, data: undefined });
    const view = renderPage();
    expect(screen.getByText("正在加载")).toBeInTheDocument();

    configureQuery({ data: { ...defaultData, items: [], total: 0, hasNext: false } });
    view.rerender(
      <ConfigProvider>
        <TerminologyMapping />
      </ConfigProvider>,
    );
    expect(screen.getByText("暂无字典映射条目")).toBeInTheDocument();

    configureQuery({ isError: true, data: undefined });
    view.rerender(
      <ConfigProvider>
        <TerminologyMapping />
      </ConfigProvider>,
    );
    expect(screen.getByText("页面暂时不可用")).toBeInTheDocument();

    configureQuery({}, { ...profile, menuKeys: [] });
    view.rerender(
      <ConfigProvider>
        <TerminologyMapping />
      </ConfigProvider>,
    );
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();

    configureQuery({
      data: {
        ...defaultData,
        partial: {
          successCount: 8,
          failureCount: 1,
          failures: [{ key: "1", reason: "证据补充失败", retryable: true }],
        },
      },
    });
    view.rerender(
      <ConfigProvider>
        <TerminologyMapping />
      </ConfigProvider>,
    );
    expect(screen.getByText(/8 项成功，1 项失败/)).toBeInTheDocument();
    expect(screen.getByText(/证据补充失败/)).toBeInTheDocument();
  });
});
