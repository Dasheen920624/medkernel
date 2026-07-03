import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useBatchConfirmTerminologyCandidates,
  useConfirmTerminologyCandidate,
  useCreateTerminologyAssetDraft,
  useGenerateTerminologyCandidates,
  useLargeListExportJob,
  useLocalTerms,
  useRejectTerminologyCandidate,
  useRegisterStandardTerm,
  useResolveTerminologyConflict,
  useSaveView,
  useSavedViews,
  useSecurityProfile,
  useStandardTerms,
  useSubmitLargeListExport,
  useTerminologyCandidateGenerationJob,
  useTerminologyCandidates,
  useTerminologyConflicts,
  useTerminologyMappings,
  type LocalTerm,
  type MappingConflict,
  type SecurityProfile,
  type StandardTerm,
  type TermMapping,
  type TermMappingCandidate,
  type TerminologyCandidateGenerationJob,
} from "@/shared/api/hooks";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import TerminologyMapping from "./TerminologyMapping";

vi.mock("@/shared/api/hooks", () => ({
  parseSavedExperienceView: vi.fn((view) =>
    view?.definitionJson ? JSON.parse(view.definitionJson) : null,
  ),
  useBatchConfirmTerminologyCandidates: vi.fn(),
  useConfirmTerminologyCandidate: vi.fn(),
  useCreateTerminologyAssetDraft: vi.fn(),
  useGenerateTerminologyCandidates: vi.fn(),
  useLargeListExportJob: vi.fn(),
  useLocalTerms: vi.fn(),
  useRejectTerminologyCandidate: vi.fn(),
  useRegisterStandardTerm: vi.fn(),
  useResolveTerminologyConflict: vi.fn(),
  useSaveView: vi.fn(),
  useSavedViews: vi.fn(),
  useSecurityProfile: vi.fn(),
  useStandardTerms: vi.fn(),
  useSubmitLargeListExport: vi.fn(),
  useTerminologyCandidateGenerationJob: vi.fn(),
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
  confirmedBy: "维护员",
  confirmedAt: "2026-05-25T00:00:00.000Z",
  updatedAt: "2026-05-26T00:00:00.000Z",
};

const profile: SecurityProfile = {
  userId: "user-1",
  username: "engine.owner",
  roles: [
    {
      code: "engine-operator",
      displayName: "医疗引擎运营员",
      source: "DEFAULT",
      scopeLevel: null,
      scopeCode: null,
    },
  ],
  mustChangePwd: false,
  mfaRequired: false,
  mfaBound: false,
  mfaVerified: true,
  permissions: [
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
      displayName: "维护字典映射",
      risk: "MEDIUM",
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
  evidenceText: "LOINC 权威来源",
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

const completedGenerationJob: TerminologyCandidateGenerationJob = {
  id: 88,
  tenantId: "tenant-1",
  jobCode: "term-job-1",
  sourceSystem: "LIS",
  minimumScore: 0.2,
  semanticAssistEnabled: true,
  requestedBy: "engine.owner",
  status: "SUCCEEDED",
  progress: 100,
  generatedCount: 42,
  candidatePageUri:
    "/api/v1/engine/terminology/mappings/candidates?status=PENDING&generationJobCode=term-job-1",
  errorMessage: null,
  createdAt: "2026-06-17T00:00:00.000Z",
  startedAt: "2026-06-17T00:00:01.000Z",
  completedAt: "2026-06-17T00:00:03.000Z",
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
    evidenceDetailsEnabled: true,
    capturedAt: "2026-06-01T00:00:00.000Z",
  }),
  defaultView: true,
  version: 3,
  updatedAt: "2026-06-01T00:00:00.000Z",
  updatedBy: "operator-1",
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
  vi.mocked(useTerminologyCandidateGenerationJob).mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useConfirmTerminologyCandidate).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useRejectTerminologyCandidate).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useRegisterStandardTerm).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useResolveTerminologyConflict).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useBatchConfirmTerminologyCandidates).mockReturnValue({
    mutateAsync: vi.fn(),
  } as never);
  vi.mocked(useCreateTerminologyAssetDraft).mockReturnValue({
    mutateAsync: vi.fn(),
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
  vi.mocked(useTerminologyMappings).mockReturnValue({
    data: defaultData,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...queryOverrides,
  } as never);
}

function pageTree() {
  return (
    <ConfigProvider>
      <AntdApp>
        <TerminologyMapping />
      </AntdApp>
    </ConfigProvider>
  );
}

function renderPage() {
  return render(pageTree());
}

function apiFailure(detail: string) {
  return Object.assign(new Error(detail), { response: { data: { detail } } });
}

describe("TerminologyMapping", () => {
  beforeEach(() => {
    window.localStorage.clear();
    useEvidenceDetailsStore.setState({ enabled: false });
    vi.clearAllMocks();
    configureQuery();
  });

  it("renders the complete mapping workspace without package publishing controls", async () => {
    renderPage();

    expect(screen.getByText(/核查院内码与标准码的映射关系/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认候选" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "生成候选" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "生成术语版本" })).toBeEnabled();
    expect(screen.getByText("候选映射")).toBeInTheDocument();
    expect(screen.getByText("冲突待裁")).toBeInTheDocument();
    expect(screen.getByText("术语维护与上线修订分离")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "发布映射包" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "回滚映射包" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "批量确认候选" })).toBeDisabled();
    expect(screen.getAllByText("高危候选").length).toBeGreaterThan(0);
    expect(screen.getByText("普通候选")).toBeInTheDocument();
    expect(screen.queryByText(["#", "901"].join(""))).not.toBeInTheDocument();
    expect(screen.queryByText(["#", "902"].join(""))).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "查看 1" }));
    expect(screen.getByText("实施核查证据")).toBeInTheDocument();
  });

  it("registers a standard terminology entry from the standard dictionary workspace", async () => {
    const register = vi.fn().mockResolvedValue({
      ...standardTerm,
      standardSystem: "TERM.LAB",
      termCode: "TERM.LAB.FRONTDESK.K",
      displayName: "前台演练血钾",
      versionNo: "2026.07",
    });
    const refetch = vi.fn();
    vi.mocked(useRegisterStandardTerm).mockReturnValue({ mutateAsync: register } as never);
    vi.mocked(useStandardTerms).mockReturnValue({
      data: pageData([]),
      isLoading: false,
      isError: false,
      refetch,
    } as never);

    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "登记标准术语" }));
    await userEvent.clear(screen.getByLabelText("标准体系"));
    await userEvent.type(screen.getByLabelText("标准体系"), "TERM.LAB");
    await userEvent.type(screen.getByLabelText("标准编码"), "TERM.LAB.FRONTDESK.K");
    await userEvent.type(screen.getByLabelText("标准名称"), "前台演练血钾");
    await userEvent.type(screen.getByLabelText("依据说明"), "上线演练登记标准术语");
    await userEvent.click(screen.getByRole("button", { name: "提交登记" }));

    expect(register).toHaveBeenCalledWith({
      standardSystem: "TERM.LAB",
      termCode: "TERM.LAB.FRONTDESK.K",
      category: "LAB",
      displayName: "前台演练血钾",
      normalizedName: "前台演练血钾",
      versionNo: "2026.07",
      evidenceText: "上线演练登记标准术语",
    });
    expect(refetch).toHaveBeenCalled();
    expect(await screen.findByText(/标准术语 前台演练血钾 已登记/)).toBeInTheDocument();
  });

  it("starts and tracks candidate generation without a package version", async () => {
    const generate = vi.fn().mockResolvedValue({
      ...completedGenerationJob,
      status: "PENDING",
      progress: 0,
      generatedCount: 0,
    });
    vi.mocked(useGenerateTerminologyCandidates).mockReturnValue({ mutateAsync: generate } as never);
    vi.mocked(useTerminologyCandidateGenerationJob).mockReturnValue({
      data: completedGenerationJob,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);

    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "生成候选" }));
    await userEvent.click(screen.getByRole("button", { name: "提交生成" }));

    await waitFor(() =>
      expect(generate).toHaveBeenCalledWith({
        sourceSystem: "LIS",
        minimumScore: 0.2,
        semanticAssistEnabled: true,
      }),
    );
    expect(screen.getByText("候选生成任务已提交")).toBeInTheDocument();
    expect(screen.getByText("候选分页入口已生成")).toBeInTheDocument();
    expect(screen.queryByText("term-job-1")).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\/engine\/terminology/)).not.toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
  });

  it("confirms a high-risk candidate with one operator note and no second-sign fields", async () => {
    const confirm = vi.fn().mockResolvedValue({ ...mapping, status: "CONFIRMED" });
    vi.mocked(useConfirmTerminologyCandidate).mockReturnValue({ mutateAsync: confirm } as never);

    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "确认候选" }));
    await userEvent.click(screen.getByRole("button", { name: "提交确认" }));
    expect(confirm).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText("核对依据"), "已核对 LIS 原始值和 LOINC 权威来源");
    await userEvent.click(screen.getByRole("button", { name: "提交确认" }));

    expect(confirm).toHaveBeenCalledWith({
      candidateId: 901,
      request: {
        reviewNote: "逐条确认高危候选",
        evidenceOverride: "已核对 LIS 原始值和 LOINC 权威来源",
      },
    });
  });

  it("acts on the candidate selected from its own row", async () => {
    const confirm = vi.fn().mockResolvedValue({ ...mapping, status: "CONFIRMED" });
    vi.mocked(useConfirmTerminologyCandidate).mockReturnValue({ mutateAsync: confirm } as never);

    renderPage();
    const ordinaryRow = screen.getByRole("row", { name: /编码和名称精确匹配/ });
    await userEvent.click(within(ordinaryRow).getByRole("button", { name: /^确\s*认$/ }));
    await userEvent.type(screen.getByLabelText("确认说明"), "编码与名称精确匹配");
    await userEvent.click(screen.getByRole("button", { name: "提交确认" }));

    expect(confirm).toHaveBeenCalledWith({
      candidateId: 902,
      request: {
        reviewNote: "确认普通候选",
        evidenceOverride: "编码与名称精确匹配",
      },
    });
  });

  it("rejects and arbitrates with mandatory traceable reasons", async () => {
    const reject = vi.fn().mockResolvedValue({ ...highRiskCandidate, status: "REJECTED" });
    const resolve = vi.fn().mockResolvedValue({ ...openConflict, status: "RESOLVED" });
    vi.mocked(useRejectTerminologyCandidate).mockReturnValue({ mutateAsync: reject } as never);
    vi.mocked(useResolveTerminologyConflict).mockReturnValue({ mutateAsync: resolve } as never);

    renderPage();
    const candidateRow = screen.getByRole("row", { name: /钾 \/ 钠不可互换/ });
    await userEvent.click(within(candidateRow).getByRole("button", { name: /驳\s*回/ }));
    await userEvent.type(screen.getByLabelText("驳回理由"), "钾钠互斥错配");
    await userEvent.click(screen.getByRole("button", { name: "提交驳回" }));
    expect(reject).toHaveBeenCalledWith({
      candidateId: 901,
      request: { reviewNote: "钾钠互斥错配" },
    });

    const conflictRow = screen.getByRole("row", { name: /一对多冲突/ });
    await userEvent.click(within(conflictRow).getByRole("button", { name: /裁\s*决/ }));
    await userEvent.type(screen.getByLabelText("裁决依据"), "保留 LOINC 2951-2");
    await userEvent.click(screen.getByRole("button", { name: "提交裁决" }));
    expect(resolve).toHaveBeenCalledWith({
      conflictId: 301,
      request: { resolutionNote: "保留 LOINC 2951-2" },
    });
  });

  it("batch confirms only ordinary candidates", async () => {
    const batchConfirm = vi.fn().mockResolvedValue({
      confirmedCount: 1,
      confirmedCandidateIds: [902],
    });
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
    await userEvent.click(screen.getByRole("button", { name: "批量确认候选" }));

    expect(batchConfirm).toHaveBeenCalledWith({
      candidateIds: [902],
      request: { reviewNote: "批量确认普通候选" },
    });
  });

  it("creates an automatically versioned terminology asset for the current scope", async () => {
    const createDraft = vi.fn().mockResolvedValue({
      assetIdentity: "TERM.LAB",
      versionId: "av-term-1",
      versionNo: "V1",
      status: "DRAFT",
      organizationScope: "tenant:tenant-1",
      contentHash: "a".repeat(64),
      mappingCount: 1,
    });
    vi.mocked(useCreateTerminologyAssetDraft).mockReturnValue({
      mutateAsync: createDraft,
    } as never);

    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "生成术语版本" }));
    expect(screen.getByText("版本号由系统自动生成")).toBeInTheDocument();
    expect(screen.getByText("当前服务机构")).toBeInTheDocument();
    expect(screen.queryByText("当前服务机构 · tenant-1")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("新版本")).not.toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText("稳定术语资产身份"));
    await userEvent.type(screen.getByLabelText("稳定术语资产身份"), "TERM.LAB");
    await userEvent.click(screen.getByRole("button", { name: "生成草稿版本" }));

    expect(createDraft).toHaveBeenCalledWith({
      assetIdentity: "TERM.LAB",
      name: "术语映射",
      scopeLevel: "TENANT",
      scopeCode: "tenant-1",
    });
    expect(await screen.findByText(/术语资产版本 V1 已生成/)).toBeInTheDocument();
  });

  it("allows the terminology workspace itself to reveal trace evidence without provenance access", async () => {
    useEvidenceDetailsStore.setState({ enabled: true });
    configureQuery({}, { ...profile, menuKeys: ["terminology-mapping"] });

    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "查看 1" }));

    expect(screen.getByRole("switch", { name: "证据详情" })).toBeChecked();
    expect(screen.getByText("映射 ID")).toBeInTheDocument();
    expect(screen.getByText("院内编码 ID")).toBeInTheDocument();
    expect(screen.getByText("标准编码 ID")).toBeInTheDocument();
    expect(screen.getByText("追踪号：trace-list")).toBeInTheDocument();
  });

  it("keeps errors visible and the asset modal open", async () => {
    const createDraft = vi
      .fn()
      .mockRejectedValue(apiFailure("当前范围没有已确认映射，无法生成术语资产草稿"));
    vi.mocked(useCreateTerminologyAssetDraft).mockReturnValue({
      mutateAsync: createDraft,
    } as never);

    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "生成术语版本" }));
    await userEvent.click(screen.getByRole("button", { name: "生成草稿版本" }));

    expect(
      await screen.findByText("当前范围没有已确认映射，无法生成术语资产草稿"),
    ).toBeInTheDocument();
    expect(screen.getByText("生成术语资产版本")).toBeInTheDocument();
  });

  it("loads and saves service view snapshots", async () => {
    const saveView = vi.fn().mockResolvedValue(defaultSavedView);
    vi.mocked(useSavedViews).mockReturnValue({ data: [defaultSavedView] } as never);
    vi.mocked(useSaveView).mockReturnValue({ mutateAsync: saveView } as never);

    renderPage();
    expect(await screen.findByDisplayValue("HIS")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "保存视图" }));

    expect(saveView).toHaveBeenCalledWith(
      expect.objectContaining({
        pageKey: "terminology.mapping",
        snapshot: expect.objectContaining({ viewKey: "terminology.mapping" }),
      }),
    );
  });

  it("submits async export from the current view", async () => {
    const submit = vi.fn().mockResolvedValue({
      jobId: "job-1",
      status: "running",
      submittedAt: "2026-06-01T00:00:00.000Z",
      submittedBy: "operator-1",
      traceId: "trace-export",
    });
    const poll = vi.fn().mockResolvedValue({
      jobId: "job-1",
      status: "succeeded",
      submittedAt: "2026-06-01T00:00:00.000Z",
      submittedBy: "operator-1",
      traceId: "trace-export",
      downloadUrl: "/api/v1/large-lists/exports/job-1/download",
    });
    vi.mocked(useSubmitLargeListExport).mockReturnValue({ mutateAsync: submit } as never);
    vi.mocked(useLargeListExportJob).mockReturnValue({ mutateAsync: poll } as never);

    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "导出" }));
    await userEvent.click(screen.getByRole("button", { name: "提交导出任务" }));

    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ resourceType: "TERMINOLOGY_MAPPING" }),
    );
    expect(await screen.findByText("导出已完成")).toBeInTheDocument();
  });

  it("renders loading, empty, error and forbidden states", () => {
    configureQuery({ isLoading: true, data: undefined });
    const view = renderPage();
    expect(screen.getByText("正在加载")).toBeInTheDocument();

    configureQuery({ data: { ...defaultData, items: [], total: 0, hasNext: false } });
    vi.mocked(useTerminologyCandidates).mockReturnValue({
      data: pageData([]),
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);
    vi.mocked(useTerminologyConflicts).mockReturnValue({
      data: pageData([]),
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);
    view.rerender(pageTree());
    expect(screen.getByText("暂无术语映射条目")).toBeInTheDocument();

    configureQuery({ isError: true, data: undefined });
    view.rerender(pageTree());
    expect(screen.getByText("页面暂时不可用")).toBeInTheDocument();

    configureQuery({}, { ...profile, menuKeys: [] });
    view.rerender(pageTree());
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
  });
});
