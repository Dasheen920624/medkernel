import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import KnowledgeGovernance from "./KnowledgeGovernance";

const KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS = 15_000;

const mockUseKnowledgeIdentities = vi.fn();
const mockUseKnowledgeCandidates = vi.fn();
const mockUseCandidateProvenance = vi.fn();
const mockUseKnowledgeCandidateDiff = vi.fn();
const mockUseReviewKnowledgeCandidate = vi.fn();
const mockUseDeprecateKnowledgeIdentity = vi.fn();
const mockUseSecurityProfile = vi.fn();
const mockUseKnowledgeCustomizations = vi.fn();
const mockUseOrgUnits = vi.fn();
const mockUseCreateKnowledgeCustomization = vi.fn();
const mockUsePublishKnowledgeCustomization = vi.fn();
const mockUseRestorePlatformKnowledge = vi.fn();
const mockUseAssetTemplates = vi.fn();
const mockUsePackages = vi.fn();
const mockUseKnowledgeProductionReadiness = vi.fn();
const mockUseKnowledgeProductionJobs = vi.fn();
const mockUseKnowledgeProductionCandidates = vi.fn();
const mockUseKnowledgeProductionGateResults = vi.fn();
const mockUseKnowledgeProductionTriageResults = vi.fn();
const mockUseKnowledgeProductionShadowRuns = vi.fn();
const mockUseCandidateCoexistence = vi.fn();
const mockUseCancelKnowledgeProductionJob = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useKnowledgeIdentities: (params: unknown) => mockUseKnowledgeIdentities(params),
  useKnowledgeCandidates: (identityId?: number, params?: unknown) =>
    mockUseKnowledgeCandidates(identityId, params),
  useCandidateProvenance: (refs: string[]) => mockUseCandidateProvenance(refs),
  useAssetTemplates: () => mockUseAssetTemplates(),
  useKnowledgeCandidateDiff: (candidateId?: number) => mockUseKnowledgeCandidateDiff(candidateId),
  useReviewKnowledgeCandidate: () => mockUseReviewKnowledgeCandidate(),
  useDeprecateKnowledgeIdentity: () => mockUseDeprecateKnowledgeIdentity(),
  useSecurityProfile: () => mockUseSecurityProfile(),
  useKnowledgeCustomizations: (params: unknown, enabled?: boolean) =>
    mockUseKnowledgeCustomizations(params, enabled),
  useOrgUnits: (params: unknown) => mockUseOrgUnits(params),
  useCreateKnowledgeCustomization: () => mockUseCreateKnowledgeCustomization(),
  usePublishKnowledgeCustomization: () => mockUsePublishKnowledgeCustomization(),
  useRestorePlatformKnowledge: () => mockUseRestorePlatformKnowledge(),
  usePackages: (params: unknown) => mockUsePackages(params),
  useKnowledgeProductionReadiness: (params: unknown) => mockUseKnowledgeProductionReadiness(params),
  useKnowledgeProductionJobs: (params: unknown) => mockUseKnowledgeProductionJobs(params),
  useKnowledgeProductionCandidates: (jobCode?: string) =>
    mockUseKnowledgeProductionCandidates(jobCode),
  useKnowledgeProductionGateResults: (jobCode?: string) =>
    mockUseKnowledgeProductionGateResults(jobCode),
  useKnowledgeProductionTriageResults: (jobCode?: string) =>
    mockUseKnowledgeProductionTriageResults(jobCode),
  useKnowledgeProductionShadowRuns: (jobCode?: string) =>
    mockUseKnowledgeProductionShadowRuns(jobCode),
  useCandidateCoexistence: (candidateRef?: string) => mockUseCandidateCoexistence(candidateRef),
  useCancelKnowledgeProductionJob: () => mockUseCancelKnowledgeProductionJob(),
}));

vi.mock("./DiagnosisKnowledgePanel", () => ({
  default: () => <div>诊断知识工作台</div>,
}));

const realIdentity = {
  id: 42,
  tenantId: "tenant-A",
  identityCode: "KNOW.VTE.GUIDE",
  domain: "GUIDELINE",
  subject: "VTE 防治指南",
  specialtyId: "cardiology",
  description: "围手术期 VTE 风险评估与预防建议",
  status: "ACTIVE",
  currentVersionId: 1001,
  createdAt: "2026-06-06T01:00:00Z",
  createdBy: "u-knowledge",
  updatedAt: "2026-06-06T02:00:00Z",
  updatedBy: "u-knowledge",
};

const activeVersion = {
  id: 1001,
  tenantId: "tenant-A",
  identityId: 42,
  versionNo: "2026.05",
  versionLabel: "现行 VTE 指南",
  sourceDocumentId: 3001,
  sourceVersionId: 4001,
  contentHash: "active-real-hash",
  anchors: "source-fragment-active",
  status: "ACTIVE",
  riskLevel: "HIGH",
  authorityLevel: "B_GUIDELINE",
  gradeQuality: "MODERATE",
  gradeStrength: "STRONG",
  conflictArbitration: "保留现行高危条款",
  organizationScope: "hospital:hospital-A",
  applicableScope: "cardiology",
  activeScopeKey: "42|hospital:hospital-A|cardiology",
  effectiveFrom: "2026-05-01T00:00:00Z",
  reviewedBy: "expert-1",
  reviewedAt: "2026-05-01T01:00:00Z",
  activatedAt: "2026-05-01T02:00:00Z",
  createdAt: "2026-05-01T00:00:00Z",
  createdBy: "u-knowledge",
  updatedAt: "2026-05-01T02:00:00Z",
  updatedBy: "expert-1",
};

const candidateVersion = {
  id: 2002,
  tenantId: "tenant-A",
  identityId: 42,
  versionNo: "2026.06",
  versionLabel: "待审 VTE 指南 2026.06",
  sourceDocumentId: 3002,
  sourceVersionId: 4002,
  contentHash: "candidate-real-hash",
  anchors: "source-fragment-candidate",
  status: "PENDING_REPLACEMENT_REVIEW",
  riskLevel: "HIGH",
  authorityLevel: "A_REGULATION",
  gradeQuality: "HIGH",
  gradeStrength: "STRONG",
  conflictArbitration: "新增高危围手术期禁忌",
  organizationScope: "hospital:hospital-A",
  applicableScope: "cardiology",
  activeScopeKey: "version:2002",
  effectiveFrom: "2026-06-01T00:00:00Z",
  reviewedBy: null,
  reviewedAt: null,
  activatedAt: null,
  createdAt: "2026-06-06T01:00:00Z",
  createdBy: "ai-candidate-importer",
  updatedAt: "2026-06-06T01:10:00Z",
  updatedBy: "workflow-know-02",
};

const candidateClassification = {
  id: 9001,
  tenantId: "tenant-A",
  orgPath: "tenant-A/group-A/hospital-A",
  identityId: 42,
  candidateVersionId: 2002,
  activeVersionId: 1001,
  classification: "CONFLICT",
  reviewStatus: "PENDING_REPLACEMENT_REVIEW",
  contentHash: "candidate-real-hash",
  basis: "同一 identity 下来源版本更新，GRADE 强度与现行版冲突",
  diffSummary: "新增围手术期高危禁忌条款，需专家确认后替换现行版。",
  createdAt: "2026-06-06T01:12:00Z",
  createdBy: "workflow-know-02",
  updatedAt: "2026-06-06T01:12:00Z",
  updatedBy: "workflow-know-02",
};

const assetTemplates = [
  {
    professionCode: "GUIDELINE",
    displayName: "指南共识",
    assetType: "KNOWLEDGE",
    knowledgeDomain: "GUIDELINE",
    sections: [
      { key: "recommendation", label: "推荐意见", required: true, hint: "推荐意见（必备结构）" },
      { key: "evidence", label: "证据等级", required: true, hint: "证据等级（必备结构）" },
      { key: "references", label: "参考文献", required: true, hint: "参考文献（必备结构）" },
    ],
  },
  {
    professionCode: "RULE",
    displayName: "规则",
    assetType: "RULE",
    knowledgeDomain: null,
    sections: [{ key: "trigger", label: "触发条件", required: true, hint: "触发条件（必备结构）" }],
  },
];

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <AntdApp>
          <KnowledgeGovernance />
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

let refetchIdentities: ReturnType<typeof vi.fn>;
let refetchCandidates: ReturnType<typeof vi.fn>;
let reviewCandidate: ReturnType<typeof vi.fn>;
let deprecateIdentity: ReturnType<typeof vi.fn>;
let createCustomization: ReturnType<typeof vi.fn>;
let publishCustomization: ReturnType<typeof vi.fn>;
let cancelProductionJob: ReturnType<typeof vi.fn>;

function customizationPage(items: Array<Record<string, unknown>> = []) {
  return {
    items,
    page: 1,
    size: 20,
    total: items.length,
    hasNext: false,
    totalEstimated: false,
  };
}

function pageResponse<T>(items: T[], total = items.length, page = 1, size = 20) {
  return {
    items,
    page,
    size,
    total,
    hasNext: page * size < total,
    totalEstimated: false,
  };
}

beforeEach(() => {
  refetchIdentities = vi.fn();
  refetchCandidates = vi.fn();
  reviewCandidate = vi.fn().mockResolvedValue({
    identityId: 42,
    candidates: [{ ...candidateVersion, status: "ACTIVE" }],
    classifications: [{ ...candidateClassification, reviewStatus: "APPROVED" }],
    available: true,
    reasonCode: "CONFLICT",
    message: "候选已通过审核并交由 SYS-08 原子替换",
  });
  deprecateIdentity = vi.fn().mockResolvedValue({
    identityId: 42,
    successorIdentityId: 43,
    transitionType: "DEPRECATE",
  });
  createCustomization = vi.fn().mockResolvedValue({
    customizationId: "kc-1",
    status: "DRAFT",
  });
  publishCustomization = vi.fn().mockResolvedValue({
    customizationId: "kc-high-risk",
    status: "ACTIVE",
  });
  cancelProductionJob = vi.fn().mockResolvedValue({
    jobCode: "job-ai-1",
    status: "CANCELLED",
  });

  mockUseKnowledgeIdentities.mockReset();
  mockUseKnowledgeCandidates.mockReset();
  mockUseCandidateProvenance.mockReset();
  mockUseKnowledgeCandidateDiff.mockReset();
  mockUseReviewKnowledgeCandidate.mockReset();
  mockUseDeprecateKnowledgeIdentity.mockReset();
  mockUseSecurityProfile.mockReset();
  mockUseKnowledgeCustomizations.mockReset();
  mockUseOrgUnits.mockReset();
  mockUseCreateKnowledgeCustomization.mockReset();
  mockUsePublishKnowledgeCustomization.mockReset();
  mockUseRestorePlatformKnowledge.mockReset();
  mockUseAssetTemplates.mockReset();
  mockUsePackages.mockReset();
  mockUseKnowledgeProductionReadiness.mockReset();
  mockUseKnowledgeProductionJobs.mockReset();
  mockUseKnowledgeProductionCandidates.mockReset();
  mockUseKnowledgeProductionGateResults.mockReset();
  mockUseKnowledgeProductionTriageResults.mockReset();
  mockUseKnowledgeProductionShadowRuns.mockReset();
  mockUseCandidateCoexistence.mockReset();
  mockUseCancelKnowledgeProductionJob.mockReset();

  mockUseAssetTemplates.mockReturnValue({
    data: assetTemplates,
    isLoading: false,
    isError: false,
    error: undefined,
  });

  mockUseKnowledgeIdentities.mockReturnValue({
    data: { items: [realIdentity], page: 1, size: 20, total: 1, hasNext: false },
    refetch: refetchIdentities,
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUsePackages.mockReturnValue({
    data: {
      items: [
        {
          packageId: "pkg-knowledge-2026",
          packageCode: "PKG.KNOW",
          packageVersion: "PKG.KNOW.2026.06",
          name: "知识审核上下文包",
          status: "ACTIVE",
        },
      ],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
    },
    isLoading: false,
    isError: false,
  });
  mockUseKnowledgeProductionReadiness.mockReturnValue({
    data: {
      tenantId: "tenant-A",
      producer: "API_MODEL",
      capabilityCode: "knowledge-generation",
      providerCode: "provider-openai",
      deploymentForm: "EXTERNAL",
      ready: false,
      modelInvocationAllowed: false,
      items: [
        {
          code: "P6_ACCEPTANCE",
          ready: false,
          required: true,
          message: "P6 独立验收未放行",
          evidence: "配置中心 false",
        },
      ],
    },
    isLoading: false,
    isError: false,
    error: undefined,
    refetch: vi.fn(),
  });
  mockUseKnowledgeProductionJobs.mockReturnValue({
    data: {
      items: [
        {
          jobCode: "job-ai-1",
          producer: "API_MODEL",
          targetPipeline: "TENANT_OVERLAY",
          domain: "GUIDELINE",
          modelStrategy: "gpt-pipeline",
          status: "RUNNING",
          candidateCount: 1,
          createdAt: "2026-06-16T10:00:00Z",
        },
      ],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    },
    isLoading: false,
    isError: false,
    error: undefined,
    refetch: vi.fn(),
  });
  mockUseKnowledgeProductionCandidates.mockReturnValue({
    data: [
      {
        jobCode: "job-ai-1",
        assetIdentity: "rule:ai:vte",
        contentHash: "a".repeat(64),
        candidateRef: "kv:42:2026.06",
        riskLevel: "HIGH",
        createdAt: "2026-06-16T10:02:00Z",
        routing: {
          ownerReviewerRole: "INSTITUTION_KNOWLEDGE_GOVERNOR",
          domainReviewerRole: "CLINICAL_GOVERNANCE_LEAD",
          requiresDualSign: true,
          domain: "GUIDELINE",
        },
      },
    ],
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseKnowledgeProductionGateResults.mockReturnValue({
    data: [
      { jobCode: "job-ai-1", gateCode: "SOURCE_ANCHOR", passed: true, reason: "来源锚点可解析" },
      { jobCode: "job-ai-1", gateCode: "SHADOW_READY", passed: false, reason: "影子评测未达标" },
    ],
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseKnowledgeProductionTriageResults.mockReturnValue({
    data: [
      {
        jobCode: "job-ai-1",
        contentHash: "a".repeat(64),
        triageState: "CONFLICT",
        action: "REVIEW",
        basis: "同身份现行版本存在冲突",
      },
    ],
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseKnowledgeProductionShadowRuns.mockReturnValue({
    data: [
      {
        jobCode: "job-ai-1",
        status: "FAILED",
        totalCases: 12,
        hitCount: 8,
        falsePositiveCount: 2,
        missCount: 2,
        degradationDetected: true,
        readyForReview: false,
        basis: "误报率超过阈值",
      },
    ],
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseCandidateCoexistence.mockReturnValue({
    data: {
      candidateRef: "kv:42:2026.06",
      candidateExecutable: false,
      activeExecutable: true,
      replacementReminder: "审核通过后将触发 SYS-08 原子替换",
      safetyNotice: "候选处于待审共存态，仅供人工对照审核，不参与临床执行",
    },
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseCancelKnowledgeProductionJob.mockReturnValue({
    mutateAsync: cancelProductionJob,
    isPending: false,
  });
  mockUseKnowledgeCandidates.mockReturnValue({
    data: {
      identityId: 42,
      candidates: pageResponse([candidateVersion], 21),
      classifications: [candidateClassification],
      available: true,
      reasonCode: "CONFLICT",
      message: "存在冲突候选，需人工审核。",
    },
    refetch: refetchCandidates,
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseCandidateProvenance.mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseKnowledgeCandidateDiff.mockReturnValue({
    data: {
      identityId: 42,
      candidates: pageResponse([candidateVersion, activeVersion], 2),
      classifications: [candidateClassification],
      available: true,
      reasonCode: "CONFLICT",
      message: "候选与现行权威版本存在高危条款冲突。",
    },
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseReviewKnowledgeCandidate.mockReturnValue({
    mutateAsync: reviewCandidate,
    isPending: false,
  });
  mockUseDeprecateKnowledgeIdentity.mockReturnValue({
    mutateAsync: deprecateIdentity,
    isPending: false,
  });
  mockUseSecurityProfile.mockReturnValue({
    data: {
      dataScope: { tenantId: "t-1" },
      permissions: [{ code: "knowledge.publish" }],
    },
  });
  mockUseKnowledgeCustomizations.mockReturnValue({
    data: customizationPage(),
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  });
  mockUseOrgUnits.mockReturnValue({
    data: { items: [], page: 1, size: 500, total: 0 },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  });
  mockUseCreateKnowledgeCustomization.mockReturnValue({
    mutateAsync: createCustomization,
    isPending: false,
  });
  mockUsePublishKnowledgeCustomization.mockReturnValue({
    mutateAsync: publishCustomization,
    isPending: false,
  });
  mockUseRestorePlatformKnowledge.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
  });
});

describe("KnowledgeGovernance", () => {
  it(
    "lets a medical institution derive a governed local draft from platform knowledge",
    async () => {
      const user = userEvent.setup();
      mockUseSecurityProfile.mockReturnValue({
        data: {
          dataScope: { tenantId: "tenant-A" },
          permissions: [
            { code: "knowledge.write" },
            { code: "knowledge.publish" },
            { code: "knowledge.withdraw" },
            { code: "tenant.override" },
          ],
        },
      });
      mockUseKnowledgeIdentities.mockReturnValue({
        data: {
          items: [{ ...realIdentity, tenantId: "t-1" }],
          page: 1,
          size: 20,
          total: 1,
          hasNext: false,
        },
        refetch: refetchIdentities,
        isLoading: false,
        isError: false,
        error: undefined,
      });
      mockUseOrgUnits.mockReturnValue({
        data: {
          items: [
            {
              id: "hospital-a",
              level: "FACILITY",
              code: "HOSP-A",
              name: "示范医院",
              status: "ACTIVE",
            },
          ],
          page: 1,
          size: 500,
          total: 1,
        },
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      });

      renderPage();
      expect(mockUseKnowledgeCustomizations).toHaveBeenCalledWith({ page: 1, size: 20 }, true);
      await user.click(screen.getByRole("button", { name: /定制为本机构版本/ }));
      expect(screen.getByRole("dialog", { name: /定制机构知识/ })).toBeInTheDocument();
      await user.click(screen.getByRole("combobox", { name: "生效机构" }));
      await user.click(
        await screen.findByText("示范医院 · 医疗服务机构", {
          selector: ".ant-select-item-option-content",
        }),
      );
      await user.type(screen.getByLabelText("定制原因"), "适配本院诊疗流程");
      await user.click(screen.getByRole("button", { name: "创建定制草稿" }));

      await waitFor(() =>
        expect(createCustomization).toHaveBeenCalledWith({
          platformIdentityId: 42,
          targetOrgUnitId: "hospital-a",
          applicableScope: "ALL",
          reason: "适配本院诊疗流程",
        }),
      );
    },
    KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "requires an electronic signature before publishing a high-risk institution version",
    async () => {
      const user = userEvent.setup();
      mockUseSecurityProfile.mockReturnValue({
        data: {
          dataScope: { tenantId: "tenant-A" },
          permissions: [{ code: "knowledge.publish" }, { code: "tenant.override" }],
        },
      });
      mockUseKnowledgeCustomizations.mockReturnValue({
        data: customizationPage([
          {
            customizationId: "kc-high-risk",
            sourceType: "LOCAL_CUSTOMIZATION",
            status: "DRAFT",
            platformIdentityId: 42,
            platformVersionId: 1001,
            platformVersionNo: "2026.05",
            localIdentityId: 43,
            localVersionId: 2003,
            riskLevel: "HIGH",
            targetOrgUnitId: "hospital-a",
            targetOrganizationName: "示范医院",
            targetOrgPath: "/tenant-A/hospital-a",
            applicableScope: "ALL",
            reason: "适配本院高危诊疗流程",
            overrideId: null,
            platformUpdateAvailable: false,
            updatedAt: "2026-06-11T01:00:00Z",
          },
        ]),
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      });

      renderPage();
      await user.click(screen.getByRole("tab", { name: "机构知识" }));
      await user.click(screen.getByRole("button", { name: "发布机构版本" }));

      expect(screen.getByText("高风险知识必须完成电子签名")).toBeInTheDocument();
      await user.type(screen.getByLabelText("发布依据"), "医务与质控联合复核通过");
      await user.type(screen.getByLabelText("签名编号"), "sig-local-knowledge-1");
      await user.type(screen.getByLabelText("复核人工号"), "medical-reviewer-1");
      await user.type(screen.getByLabelText("复核人姓名"), "医务复核员");
      fireEvent.change(screen.getByLabelText("签名时间"), {
        target: { value: "2026-06-11T10:00" },
      });
      await user.type(screen.getByLabelText("签名摘要"), "b".repeat(64));
      await user.click(screen.getByRole("button", { name: "确认发布" }));

      await waitFor(() => {
        expect(publishCustomization).toHaveBeenCalledWith({
          customizationId: "kc-high-risk",
          reason: "医务与质控联合复核通过",
          publishEvidence: {
            electronicSignature: {
              signatureId: "sig-local-knowledge-1",
              signerId: "medical-reviewer-1",
              signerName: "医务复核员",
              signedAt: new Date("2026-06-11T10:00").toISOString(),
              signatureHash: "b".repeat(64),
            },
          },
        });
      });
    },
    KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS,
  );

  it("keeps diagnosis maintenance out of the review workspace while the candidate queue is loading", () => {
    mockUseKnowledgeIdentities.mockReturnValue({
      data: undefined,
      refetch: refetchIdentities,
      isLoading: true,
      isError: false,
      error: undefined,
    });

    renderPage();
    expect(screen.getByText("正在加载知识候选审核")).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "诊断知识" })).not.toBeInTheDocument();
    expect(screen.queryByText("诊断知识工作台")).not.toBeInTheDocument();
  });

  it("keeps the retirement successor query disabled until the dialog opens", () => {
    renderPage();

    expect(mockUseKnowledgeIdentities).toHaveBeenCalledWith(
      expect.objectContaining({
        domain: undefined,
        status: "ACTIVE",
        enabled: false,
      }),
    );
  });

  it("loads the requested server page when the identity ledger is paged", async () => {
    const user = userEvent.setup();
    mockUseKnowledgeIdentities.mockReturnValue({
      data: {
        items: [realIdentity],
        page: 1,
        size: 20,
        total: 41,
        hasNext: true,
      },
      refetch: refetchIdentities,
      isLoading: false,
      isError: false,
      error: undefined,
    });

    renderPage();
    await user.click(screen.getAllByTitle("2")[0]);

    await waitFor(() => {
      expect(mockUseKnowledgeIdentities).toHaveBeenCalledWith(
        expect.objectContaining({
          domain: "GUIDELINE",
          page: 2,
          size: 20,
        }),
      );
    });
  });

  it("keeps the knowledge filters usable when the current domain is empty", async () => {
    const user = userEvent.setup();
    mockUseKnowledgeIdentities.mockReturnValue({
      data: {
        items: [],
        page: 1,
        size: 20,
        total: 0,
        hasNext: false,
      },
      refetch: refetchIdentities,
      isLoading: false,
      isError: false,
      error: undefined,
    });

    renderPage();

    expect(screen.getByText("当前筛选下暂无待审核知识身份")).toBeInTheDocument();
    expect(
      screen.getByText("知识候选经来源采集、去重和分流后展示；本页只负责审核与发布。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/KNOW-02/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: "知识域" }));
    await user.click(screen.getByText("药品说明书"));

    await waitFor(() => {
      expect(mockUseKnowledgeIdentities).toHaveBeenCalledWith(
        expect.objectContaining({ domain: "DRUG" }),
      );
    });
  });

  it("loads real knowledge identities and candidate classifications instead of the inactive roadmap placeholder", () => {
    renderPage();

    expect(mockUseKnowledgeIdentities).toHaveBeenCalledWith(
      expect.objectContaining({
        domain: "GUIDELINE",
        status: "ACTIVE",
        page: 1,
        size: 20,
        sort: "updatedAt,desc",
      }),
    );
    expect(mockUseKnowledgeCandidates).toHaveBeenLastCalledWith(42, { page: 1, size: 20 });

    expect(screen.getByRole("heading", { name: "知识审核与发布" })).toBeInTheDocument();
    expect(screen.getByText("待审核候选总数")).toBeInTheDocument();
    expect(screen.getAllByText("冲突候选").length).toBeGreaterThan(0);
    expect(screen.getByText("高风险候选")).toBeInTheDocument();
    expect(screen.getByText("KNOW.VTE.GUIDE")).toBeInTheDocument();
    expect(screen.getByText("VTE 防治指南")).toBeInTheDocument();
    expect(screen.getByText("待审 VTE 指南 2026.06")).toBeInTheDocument();
    expect(
      screen.getByText("同一 identity 下来源版本更新，GRADE 强度与现行版冲突"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("新增围手术期高危禁忌条款，需专家确认后替换现行版。"),
    ).toBeInTheDocument();
    expect(screen.queryByText("入口暂未激活")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /生成|AI 生成|创建候选/ })).not.toBeInTheDocument();
  });

  it("renders the knowledge production center with readiness, job, gate, triage, shadow and coexistence evidence", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "知识生产" }));

    expect(mockUseKnowledgeProductionReadiness).toHaveBeenCalledWith({ producer: "API_MODEL" });
    expect(mockUseKnowledgeProductionJobs).toHaveBeenCalledWith({ page: 1, size: 20 });
    expect(mockUseKnowledgeProductionCandidates).toHaveBeenCalledWith("job-ai-1");
    expect(mockUseKnowledgeProductionGateResults).toHaveBeenCalledWith("job-ai-1");
    expect(mockUseKnowledgeProductionTriageResults).toHaveBeenCalledWith("job-ai-1");
    expect(mockUseKnowledgeProductionShadowRuns).toHaveBeenCalledWith("job-ai-1");
    expect(mockUseCandidateCoexistence).toHaveBeenCalledWith("kv:42:2026.06");

    expect(screen.getByText("模型生产 readiness")).toBeInTheDocument();
    expect(screen.getByText("P6 独立验收未放行")).toBeInTheDocument();
    expect(screen.getByText("生产 job")).toBeInTheDocument();
    expect(screen.getAllByText("job-ai-1").length).toBeGreaterThan(0);
    expect(screen.getByText("API 大模型")).toBeInTheDocument();
    expect(screen.getByText("门禁结果")).toBeInTheDocument();
    expect(screen.getByText("SOURCE_ANCHOR")).toBeInTheDocument();
    expect(screen.getByText("8 态分流")).toBeInTheDocument();
    expect(screen.getByText("CONFLICT")).toBeInTheDocument();
    expect(screen.getByText("影子评测")).toBeInTheDocument();
    expect(screen.getByText("误报率超过阈值")).toBeInTheDocument();
    expect(screen.getByText("共存替换提醒")).toBeInTheDocument();
    expect(screen.getByText("候选不可执行")).toBeInTheDocument();
    expect(screen.getAllByText("审核通过后将触发 SYS-08 原子替换").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /生成|AI 生成|创建候选/ })).not.toBeInTheDocument();
  });

  it("shows agent progress, 8-state queue, side-by-side coexistence and a cancellable job action", async () => {
    const user = userEvent.setup();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        permissions: [{ code: "knowledge.write" }, { code: "knowledge.publish" }],
      },
    });
    mockUseKnowledgeProductionJobs.mockReturnValue({
      data: {
        items: [
          {
            jobCode: "job-agent-7",
            producer: "AGENT_TOOL",
            targetPipeline: "TENANT_OVERLAY",
            domain: "GUIDELINE",
            modelStrategy: "agent-verified",
            status: "RUNNING",
            candidateCount: 4,
            createdAt: "2026-06-16T10:00:00Z",
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
        totalEstimated: false,
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseKnowledgeProductionCandidates.mockReturnValue({
      data: [
        {
          jobCode: "job-agent-7",
          assetIdentity: "rule:agent:vte",
          contentHash: "b".repeat(64),
          candidateRef: "kv:42:2026.07",
          riskLevel: "HIGH",
        },
      ],
      isLoading: false,
      isError: false,
      error: undefined,
    });
    mockUseKnowledgeProductionTriageResults.mockReturnValue({
      data: [
        "NEW_ASSET",
        "DUPLICATE",
        "MINOR_REVISION",
        "MAJOR_UPGRADE",
        "CONFLICT",
        "DOWNGRADE",
        "DEPRECATION",
        "UNCERTAIN",
      ].map((triageState) => ({
        jobCode: "job-agent-7",
        contentHash: triageState.toLowerCase(),
        triageState,
        action: `${triageState}_ACTION`,
        basis: `${triageState} 分流依据`,
      })),
      isLoading: false,
      isError: false,
      error: undefined,
    });
    mockUseKnowledgeProductionShadowRuns.mockReturnValue({
      data: [
        {
          jobCode: "job-agent-7",
          status: "PASSED",
          totalCases: 18,
          hitCount: 16,
          falsePositiveCount: 1,
          missCount: 1,
          degradationDetected: false,
          readyForReview: true,
          basis: "影子评测通过",
        },
      ],
      isLoading: false,
      isError: false,
      error: undefined,
    });
    mockUseCandidateCoexistence.mockReturnValue({
      data: {
        candidateRef: "kv:42:2026.07",
        candidateVersion: {
          versionNo: "2026.07",
          status: "PENDING_REPLACEMENT_REVIEW",
          contentHash: "candidate-real-hash",
          riskLevel: "HIGH",
          authorityLevel: "A_STANDARD",
          gradeQuality: "HIGH",
          gradeStrength: "STRONG",
          organizationScope: "hospital:hospital-A",
          applicableScope: "cardiology",
        },
        activeVersion: {
          versionNo: "2026.05",
          status: "ACTIVE",
          contentHash: "active-real-hash",
          riskLevel: "HIGH",
          authorityLevel: "B_GUIDELINE",
          gradeQuality: "MODERATE",
          gradeStrength: "STRONG",
          organizationScope: "hospital:hospital-A",
          applicableScope: "cardiology",
        },
        reviewStatus: "PENDING_REPLACEMENT_REVIEW",
        approvalOutcome: "APPROVE_REPLACE_ACTIVE",
        candidateExecutable: false,
        activeExecutable: true,
        replacementReminder: "审核通过后将触发 SYS-08 原子替换",
        safetyNotice: "审核前仍由现行 ACTIVE=2026.05 执行",
      },
      isLoading: false,
      isError: false,
      error: undefined,
    });

    renderPage();
    await user.click(screen.getByRole("tab", { name: "知识生产" }));

    expect(screen.getByText("Agent 进度与中止")).toBeInTheDocument();
    expect(screen.getByText("Agent 工具")).toBeInTheDocument();
    expect(screen.getByText("生成候选 4 条")).toBeInTheDocument();
    expect(screen.getByText("8 态队列")).toBeInTheDocument();
    for (const label of [
      "新资产",
      "重复",
      "小修订",
      "重大升级",
      "冲突仲裁",
      "降级风险",
      "废止退役",
      "人工分流",
    ]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }
    expect(screen.getByText("待审候选版本")).toBeInTheDocument();
    expect(screen.getByText("现行权威版本")).toBeInTheDocument();
    expect(screen.getByText("2026.07")).toBeInTheDocument();
    expect(screen.getByText("2026.05")).toBeInTheDocument();
    expect(screen.getByText("审后任务化提醒")).toBeInTheDocument();
    expect(
      screen.getByText(
        "审核通过后创建 SYS-08 原子替换、投影刷新与院内同步任务；审核前不改变执行版本。",
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "中止生产任务" }));
    await user.click(await screen.findByRole("button", { name: "确认中止" }));

    await waitFor(() => expect(cancelProductionJob).toHaveBeenCalledWith("job-agent-7"));
  });

  it("marks AI-factory candidates with an AI provenance tag and producer source", async () => {
    mockUseCandidateProvenance.mockReturnValue({
      data: [
        {
          candidateRef: "kv:42:2026.06",
          aiGenerated: true,
          producer: "API_MODEL",
          jobCode: "job-vte-ai",
          targetPipeline: "TENANT_OVERLAY",
          domain: "DRUG",
          modelStrategy: "gpt-pipeline",
          riskLevel: "HIGH",
          producedAt: "2026-06-06T01:05:00Z",
          producedBy: "ai-factory",
        },
      ],
      isLoading: false,
      isError: false,
      error: undefined,
    });

    renderPage();

    // 审核台批量反查候选版本 kv:{identityId}:{versionNo} 的生产来源
    await waitFor(() => {
      expect(mockUseCandidateProvenance).toHaveBeenCalledWith(
        expect.arrayContaining(["kv:42:2026.06"]),
      );
    });
    // AI 生成候选须带 AI 标识（Tag 非按钮，不触发生成）+ 生产器来源
    expect(await screen.findByText("AI 生成")).toBeInTheDocument();
    expect(screen.getByText(/API 大模型/)).toBeInTheDocument();
    // 仍不得出现 AI 生成按钮（本页只审不生成，B0 / AIREVIEW-01 边界）
    expect(screen.queryByRole("button", { name: /AI 生成|创建候选/ })).not.toBeInTheDocument();
  });

  it("opens the real candidate diff drawer with active and candidate version evidence", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));

    expect(mockUseKnowledgeCandidateDiff).toHaveBeenLastCalledWith(2002);
    expect(screen.getByText("知识候选审核对照")).toBeInTheDocument();
    expect(screen.getByText("现行 VTE 指南")).toBeInTheDocument();
    expect(screen.getAllByText("active-real-hash").length).toBeGreaterThan(0);
    expect(screen.getByText("source-fragment-active")).toBeInTheDocument();
    expect(screen.getAllByText("candidate-real-hash").length).toBeGreaterThan(0);
    expect(screen.getByText("source-fragment-candidate")).toBeInTheDocument();
    expect(screen.getByText("候选与现行权威版本存在高危条款冲突。")).toBeInTheDocument();
  });

  it("shows AI production provenance trace in the candidate review drawer", async () => {
    const user = userEvent.setup();
    mockUseCandidateProvenance.mockReturnValue({
      data: [
        {
          candidateRef: "kv:42:2026.06",
          aiGenerated: true,
          producer: "API_MODEL",
          jobCode: "job-vte-ai",
          targetPipeline: "TENANT_OVERLAY",
          domain: "DRUG",
          modelStrategy: "gpt-pipeline",
          riskLevel: "HIGH",
          producedAt: "2026-06-06T01:05:00Z",
          producedBy: "ai-factory",
        },
      ],
      isLoading: false,
      isError: false,
      error: undefined,
    });

    renderPage();
    await user.click(screen.getByRole("button", { name: "查看审核对照" }));

    expect(screen.getByText("AI 生产来源溯源")).toBeInTheDocument();
    expect(screen.getByText("job-vte-ai")).toBeInTheDocument();
    expect(screen.getByText("院内覆盖")).toBeInTheDocument();
    expect(screen.getByText("gpt-pipeline")).toBeInTheDocument();
  });

  it("shows the professional asset template matching the candidate domain for completeness review", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));

    // realIdentity.domain = GUIDELINE → 匹配「指南共识」KNOWLEDGE 模板，展示结构清单
    expect(screen.getByText("专业标准模板 · 指南共识")).toBeInTheDocument();
    expect(screen.getByText("推荐意见")).toBeInTheDocument();
    expect(screen.getByText("证据等级")).toBeInTheDocument();
  });

  it("loads knowledge review package selector through small server-side search pages", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));

    expect(mockUsePackages).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      assetType: "KNOWLEDGE",
      keyword: undefined,
    });
    expect(mockUsePackages).not.toHaveBeenCalledWith({
      page: 1,
      size: 100,
      assetType: "KNOWLEDGE",
    });
  });

  it("returns a candidate for revision through the RETURN review decision with a mandatory reason", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));
    fireEvent.change(screen.getByLabelText("审核上下文包版本"), {
      target: { value: "PKG.KNOW.2026.06" },
    });
    fireEvent.change(screen.getByLabelText("审核理由"), {
      target: { value: "请补充禁忌章节后重提。" },
    });
    fireEvent.click(screen.getByRole("button", { name: /退\s*修/ }));

    await waitFor(() => {
      expect(reviewCandidate).toHaveBeenCalledWith({
        candidateId: 9001,
        packageVersion: "PKG.KNOW.2026.06",
        request: {
          decision: "RETURN",
          reason: "请补充禁忌章节后重提。",
        },
        idempotencyKey: expect.stringContaining("knowledge-review-9001"),
      });
    });
  });

  it("does not submit a return decision when the revision reason is blank", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));
    fireEvent.change(screen.getByLabelText("审核上下文包版本"), {
      target: { value: "PKG.KNOW.2026.06" },
    });
    fireEvent.click(screen.getByRole("button", { name: /退\s*修/ }));

    await waitFor(() => {
      expect(screen.getByText("请填写审核理由")).toBeInTheDocument();
    });
    expect(reviewCandidate).not.toHaveBeenCalled();
  });

  it(
    "reviews a candidate through the KNOW-02 classification review endpoint instead of mutating local state",
    async () => {
      const user = userEvent.setup();
      renderPage();

      await user.click(screen.getByRole("button", { name: "查看审核对照" }));
      expect(screen.getByText("PKG.KNOW.2026.06 · 知识审核上下文包")).toBeInTheDocument();
      fireEvent.change(screen.getByLabelText("审核理由"), {
        target: { value: "已核对来源锚点和现行版差异，允许替换。" },
      });
      fireEvent.change(screen.getByLabelText("签名 ID"), {
        target: { value: "sig-knowledge-2002" },
      });
      fireEvent.change(screen.getByLabelText("签名时间"), {
        target: { value: "2026-06-09T16:00" },
      });
      fireEvent.change(screen.getByLabelText("签名人 ID"), {
        target: { value: "expert-1" },
      });
      fireEvent.change(screen.getByLabelText("签名人姓名"), {
        target: { value: "审核专家" },
      });
      fireEvent.change(screen.getByLabelText("签名摘要"), {
        target: { value: "a".repeat(64) },
      });
      for (const label of [
        "结构校验",
        "术语绑定",
        "依赖完整性",
        "安全单调性",
        "影响模拟",
        "同行复核",
      ]) {
        fireEvent.click(screen.getByRole("checkbox", { name: label }));
      }
      fireEvent.click(screen.getByRole("button", { name: "通过并发布" }));

      await waitFor(() => {
        expect(reviewCandidate).toHaveBeenCalledWith({
          candidateId: 9001,
          packageVersion: "PKG.KNOW.2026.06",
          request: {
            decision: "APPROVE",
            reason: "已核对来源锚点和现行版差异，允许替换。",
            publishEvidence: {
              electronicSignature: {
                signatureId: "sig-knowledge-2002",
                signerId: "expert-1",
                signerName: "审核专家",
                signedAt: new Date("2026-06-09T16:00").toISOString(),
                signatureHash: "a".repeat(64),
              },
              qualityGate: {
                schemaValid: true,
                terminologyBindingComplete: true,
                dependencyIntegrityVerified: true,
                safetyMonotonicityVerified: true,
                impactSimulationPassed: true,
                peerReviewSigned: true,
                summary: undefined,
              },
            },
          },
          idempotencyKey: expect.stringContaining("knowledge-review-9001"),
        });
      });
      expect(refetchCandidates).toHaveBeenCalled();
      expect(refetchIdentities).toHaveBeenCalled();
      expect(
        await screen.findByText(
          "候选已通过审核并交由权威替换流程",
          {},
          { timeout: KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS },
        ),
      ).toBeInTheDocument();
    },
    KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "lets platform governance schedule a successor and grace period",
    async () => {
      const retiringIdentity = {
        ...realIdentity,
        domain: "DRUG",
      };
      const successor = {
        ...retiringIdentity,
        id: 43,
        identityCode: "plat:drug:vte-guide-2026",
        subject: "VTE 防治指南 2026",
        currentVersionId: 1002,
      };
      mockUseKnowledgeIdentities.mockReturnValue({
        data: {
          items: [retiringIdentity, successor],
          page: 1,
          size: 20,
          total: 2,
          hasNext: false,
        },
        refetch: refetchIdentities,
        isLoading: false,
        isError: false,
        error: undefined,
      });
      const user = userEvent.setup();
      renderPage();

      await user.click(screen.getByRole("button", { name: "安排弃用：VTE 防治指南" }));
      const successorSelect = screen.getByRole("combobox", { name: "后继知识身份" });
      fireEvent.mouseDown(successorSelect);
      fireEvent.change(successorSelect, { target: { value: "2026" } });
      await waitFor(() => {
        expect(mockUseKnowledgeIdentities).toHaveBeenLastCalledWith(
          expect.objectContaining({
            domain: "DRUG",
            status: "ACTIVE",
            keyword: "2026",
            page: 1,
            size: 20,
          }),
        );
      });
      await user.click(
        await screen.findByText(/VTE 防治指南 2026/, {
          selector: ".ant-select-item-option-content",
        }),
      );
      const graceInput = screen.getByLabelText("宽限期结束时间", { selector: "input" });
      fireEvent.change(graceInput, {
        target: { value: "2099-07-09T08:00" },
      });
      const guidanceInput = screen.getByLabelText("迁移指引", { selector: "textarea" });
      fireEvent.change(guidanceInput, {
        target: { value: "迁移到新版指南并重新核对本地覆盖" },
      });
      expect(graceInput).toHaveValue("2099-07-09T08:00");
      expect(guidanceInput).toHaveValue("迁移到新版指南并重新核对本地覆盖");
      await user.click(screen.getByRole("button", { name: "确认安排弃用" }));

      await waitFor(() => {
        expect(deprecateIdentity).toHaveBeenCalledWith(
          expect.objectContaining({
            identityId: 42,
            successorIdentityId: 43,
            migrationGuidance: "迁移到新版指南并重新核对本地覆盖",
          }),
        );
      });
    },
    KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS,
  );
});
