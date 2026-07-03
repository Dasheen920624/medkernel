import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";

import KnowledgeGovernance, {
  InstitutionKnowledge,
  KnowledgeProduction,
} from "./KnowledgeGovernance";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

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
const mockUseKnowledgeProductionReadiness = vi.fn();
const mockUseKnowledgeProductionJobs = vi.fn();
const mockUseKnowledgeProductionCandidates = vi.fn();
const mockUseKnowledgeProductionGateResults = vi.fn();
const mockUseKnowledgeProductionTriageResults = vi.fn();
const mockUseKnowledgeProductionShadowRuns = vi.fn();
const mockUseConfirmModelEgress = vi.fn();
const mockUseCandidateCoexistence = vi.fn();
const mockUseCreateKnowledgeProductionJob = vi.fn();
const mockUseGenerateKnowledgeModelCandidate = vi.fn();
const mockUseCancelKnowledgeProductionJob = vi.fn();
const mockUseKnowledgeAcquisitionSources = vi.fn();
const mockUseSaveKnowledgeAcquisitionSourceDraft = vi.fn();
const mockUseEnableKnowledgeAcquisitionSource = vi.fn();
const mockUseDisableKnowledgeAcquisitionSource = vi.fn();
const mockUseKnowledgeInitializationBatches = vi.fn();
const mockUseApproveLowKnowledgeInitializationBatch = vi.fn();
const mockUseRefreshKnowledgeInitializationBatch = vi.fn();

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
  useKnowledgeProductionReadiness: (params: unknown, enabled?: boolean) =>
    mockUseKnowledgeProductionReadiness(params, enabled),
  useKnowledgeProductionJobs: (params: unknown, enabled?: boolean) =>
    mockUseKnowledgeProductionJobs(params, enabled),
  useKnowledgeProductionCandidates: (jobCode?: string) =>
    mockUseKnowledgeProductionCandidates(jobCode),
  useKnowledgeProductionGateResults: (jobCode?: string) =>
    mockUseKnowledgeProductionGateResults(jobCode),
  useKnowledgeProductionTriageResults: (jobCode?: string) =>
    mockUseKnowledgeProductionTriageResults(jobCode),
  useKnowledgeProductionShadowRuns: (jobCode?: string) =>
    mockUseKnowledgeProductionShadowRuns(jobCode),
  useConfirmModelEgress: () => mockUseConfirmModelEgress(),
  useCandidateCoexistence: (candidateRef?: string) => mockUseCandidateCoexistence(candidateRef),
  useCreateKnowledgeProductionJob: () => mockUseCreateKnowledgeProductionJob(),
  useGenerateKnowledgeModelCandidate: () => mockUseGenerateKnowledgeModelCandidate(),
  useCancelKnowledgeProductionJob: () => mockUseCancelKnowledgeProductionJob(),
  useKnowledgeAcquisitionSources: (params: unknown) => mockUseKnowledgeAcquisitionSources(params),
  useSaveKnowledgeAcquisitionSourceDraft: () => mockUseSaveKnowledgeAcquisitionSourceDraft(),
  useEnableKnowledgeAcquisitionSource: () => mockUseEnableKnowledgeAcquisitionSource(),
  useDisableKnowledgeAcquisitionSource: () => mockUseDisableKnowledgeAcquisitionSource(),
  useKnowledgeInitializationBatches: (enabled?: boolean) =>
    mockUseKnowledgeInitializationBatches(enabled),
  useApproveLowKnowledgeInitializationBatch: () => mockUseApproveLowKnowledgeInitializationBatch(),
  useRefreshKnowledgeInitializationBatch: () => mockUseRefreshKnowledgeInitializationBatch(),
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
  reviewedBy: "knowledge-reviewer-1",
  reviewedAt: "2026-05-01T01:00:00Z",
  activatedAt: "2026-05-01T02:00:00Z",
  createdAt: "2026-05-01T00:00:00Z",
  createdBy: "u-knowledge",
  updatedAt: "2026-05-01T02:00:00Z",
  updatedBy: "knowledge-reviewer-1",
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
  diffSummary: "新增围手术期高危禁忌条款，需责任人确认后替换现行版。",
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

function renderPage(page: ReactElement = <KnowledgeGovernance />) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <AntdApp>{page}</AntdApp>
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
let createProductionJob: ReturnType<typeof vi.fn>;
let generateModelCandidate: ReturnType<typeof vi.fn>;
let confirmModelEgress: ReturnType<typeof vi.fn>;
let cancelProductionJob: ReturnType<typeof vi.fn>;
let approveLowInitializationBatch: ReturnType<typeof vi.fn>;
let refreshInitializationBatch: ReturnType<typeof vi.fn>;

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
  window.localStorage.clear();
  useEvidenceDetailsStore.setState({ enabled: false });
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
  createProductionJob = vi.fn().mockResolvedValue({
    jobCode: "job-new-1",
    status: "PENDING",
  });
  confirmModelEgress = vi.fn().mockResolvedValue({
    id: 12,
    capabilityCode: "knowledge.production.knowledge",
    payloadHash: "sha256-confirmation-required",
    purpose: "确认用于知识候选生成",
  });
  generateModelCandidate = vi.fn().mockResolvedValue({
    jobCode: "job-ai-1",
    modelTaskId: "task-model-1",
    modelMode: "B2",
    summary: {
      candidates: [{ jobCode: "job-ai-1", candidateRef: "kv:42:ai-draft-task-model-1" }],
      skipped: [],
      blocked: [],
    },
  });
  cancelProductionJob = vi.fn().mockResolvedValue({
    jobCode: "job-ai-1",
    status: "CANCELLED",
  });
  approveLowInitializationBatch = vi.fn().mockResolvedValue({
    batch: {
      batchCode: "foundation-f1-1.0.0",
      status: "IN_REVIEW",
    },
    items: [],
  });
  refreshInitializationBatch = vi.fn().mockResolvedValue({
    batch: {
      batchCode: "foundation-f1-1.0.0",
      status: "IN_REVIEW",
    },
    items: [],
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
  mockUseKnowledgeProductionReadiness.mockReset();
  mockUseKnowledgeProductionJobs.mockReset();
  mockUseKnowledgeProductionCandidates.mockReset();
  mockUseKnowledgeProductionGateResults.mockReset();
  mockUseKnowledgeProductionTriageResults.mockReset();
  mockUseKnowledgeProductionShadowRuns.mockReset();
  mockUseCandidateCoexistence.mockReset();
  mockUseCreateKnowledgeProductionJob.mockReset();
  mockUseGenerateKnowledgeModelCandidate.mockReset();
  mockUseCancelKnowledgeProductionJob.mockReset();
  mockUseKnowledgeAcquisitionSources.mockReset();
  mockUseSaveKnowledgeAcquisitionSourceDraft.mockReset();
  mockUseEnableKnowledgeAcquisitionSource.mockReset();
  mockUseDisableKnowledgeAcquisitionSource.mockReset();
  mockUseKnowledgeInitializationBatches.mockReset();
  mockUseApproveLowKnowledgeInitializationBatch.mockReset();
  mockUseRefreshKnowledgeInitializationBatch.mockReset();

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
          code: "MODEL_PROVIDER",
          ready: false,
          required: true,
          message: "模型服务未就绪",
          evidence: "模型服务未配置",
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
    data: pageResponse([
      {
        jobCode: "job-ai-1",
        assetIdentity: "rule:ai:vte",
        contentHash: "a".repeat(64),
        candidateRef: "kv:42:2026.06",
        riskLevel: "HIGH",
        createdAt: "2026-06-16T10:02:00Z",
        routing: {
          reviewerRole: "engine-operator",
          domain: "GUIDELINE",
        },
      },
    ]),
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
  mockUseCreateKnowledgeProductionJob.mockReturnValue({
    mutateAsync: createProductionJob,
    isPending: false,
  });
  mockUseGenerateKnowledgeModelCandidate.mockReturnValue({
    mutateAsync: generateModelCandidate,
    isPending: false,
  });
  mockUseConfirmModelEgress.mockReturnValue({
    mutateAsync: confirmModelEgress,
    isPending: false,
  });
  mockUseCancelKnowledgeProductionJob.mockReturnValue({
    mutateAsync: cancelProductionJob,
    isPending: false,
  });
  mockUseKnowledgeAcquisitionSources.mockReturnValue({
    data: customizationPage(),
    isLoading: false,
    isError: false,
    error: undefined,
    refetch: vi.fn(),
  });
  mockUseSaveKnowledgeAcquisitionSourceDraft.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
  });
  mockUseEnableKnowledgeAcquisitionSource.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
  });
  mockUseDisableKnowledgeAcquisitionSource.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
  });
  mockUseKnowledgeInitializationBatches.mockReturnValue({
    data: [
      {
        id: 10,
        tenantId: "tenant-A",
        batchCode: "foundation-f1-1.0.0",
        releaseType: "FOUNDATION",
        releaseVersion: "1.0.0",
        foundationReleaseVersion: null,
        phase: "F8",
        status: "IN_REVIEW",
        sourceManifestHash: "c".repeat(64),
        candidateManifestHash: "d".repeat(64),
        overallHash: "e".repeat(64),
        sourceCount: 3,
        candidateCount: 3,
        lowCount: 1,
        mediumCount: 1,
        highCount: 1,
        templateVersion: "template-v1",
        modelVersion: null,
        summary: "基础知识初始化发行",
        createdAt: "2026-06-19T01:00:00Z",
        createdBy: "engine-operator",
        updatedAt: "2026-06-19T01:00:00Z",
        updatedBy: "engine-operator",
      },
    ],
    isLoading: false,
    isError: false,
    error: undefined,
    refetch: vi.fn(),
  });
  mockUseApproveLowKnowledgeInitializationBatch.mockReturnValue({
    mutateAsync: approveLowInitializationBatch,
    isPending: false,
  });
  mockUseRefreshKnowledgeInitializationBatch.mockReturnValue({
    mutateAsync: refreshInitializationBatch,
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
      menuKeys: ["knowledge-governance"],
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

afterEach(() => {
  vi.restoreAllMocks();
});

describe("KnowledgeGovernance", () => {
  it(
    "lets a medical institution derive a governed local draft from platform knowledge",
    async () => {
      const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
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

      renderPage(<InstitutionKnowledge />);
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
      expect(
        consoleError.mock.calls.some(([message]) =>
          String(message).includes("Instance created by `useForm` is not connected"),
        ),
      ).toBe(false);
    },
    KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS,
  );

  it(
    "allows the responsible operator to publish a high-risk institution version",
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

      renderPage(<InstitutionKnowledge />);
      await user.click(screen.getByRole("button", { name: "发布机构版本" }));

      await user.type(screen.getByLabelText("发布依据"), "医务与质控联合复核通过");
      await user.click(screen.getByRole("button", { name: "确认发布" }));

      await waitFor(() => {
        expect(publishCustomization).toHaveBeenCalledWith({
          customizationId: "kc-high-risk",
          reason: "医务与质控联合复核通过",
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

    expect(screen.getByRole("heading", { name: "知识审核发布中心" })).toBeInTheDocument();
    expect(screen.getByText("待审核候选总数")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("按主题或知识身份搜索")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("按主题或编码搜索")).not.toBeInTheDocument();
    expect(screen.getAllByText("冲突候选").length).toBeGreaterThan(0);
    expect(screen.getByText("高风险候选")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getAllByText("知识身份已关联").length).toBeGreaterThan(0);
    expect(screen.getAllByText("来源证据已记录").length).toBeGreaterThan(0);
    expect(screen.getByText("VTE 防治指南")).toBeInTheDocument();
    expect(screen.getByText("待审 VTE 指南 2026.06")).toBeInTheDocument();
    expect(
      screen.getByText("同一 identity 下来源版本更新，GRADE 强度与现行版冲突"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("新增围手术期高危禁忌条款，需责任人确认后替换现行版。"),
    ).toBeInTheDocument();
    expect(screen.queryByText("入口暂未激活")).not.toBeInTheDocument();
    expect(screen.queryByText("KNOW.VTE.GUIDE")).not.toBeInTheDocument();
    expect(screen.queryByText("candidate-real-hash")).not.toBeInTheDocument();
    expect(screen.queryByText(/来源文献：3002/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /生成|AI 生成|创建候选/ })).not.toBeInTheDocument();
  });

  it("证据详情打开后展示知识身份编码、hash 和来源编号", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("KNOW.VTE.GUIDE")).toBeInTheDocument();
    expect(screen.getByText(/来源文献：3002/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));

    expect(screen.getAllByText("KNOW.VTE.GUIDE").length).toBeGreaterThan(0);
    expect(screen.getByText("active-real-hash")).toBeInTheDocument();
    expect(screen.getByText("candidate-real-hash")).toBeInTheDocument();
    expect(screen.getByText(/来源文档 #3001/)).toBeInTheDocument();
    expect(screen.getByText(/来源文档 #3002/)).toBeInTheDocument();
  });

  it("keeps review desk focused on candidate review without institution or production tabs", () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "知识审核发布中心" })).toBeInTheDocument();
    expect(screen.getByText("待审核候选总数")).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "机构知识" })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "知识生产" })).not.toBeInTheDocument();
    expect(screen.queryByText("机构知识血缘")).not.toBeInTheDocument();
    expect(screen.queryByText("模型生产上线准备")).not.toBeInTheDocument();
  });

  it("renders institution knowledge as a standalone maintenance entry", () => {
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        permissions: [{ code: "knowledge.write" }],
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

    renderPage(<InstitutionKnowledge />);

    expect(screen.getByRole("heading", { name: "机构知识库" })).toBeInTheDocument();
    expect(screen.getByText("平台标准知识")).toBeInTheDocument();
    expect(screen.getAllByText("平台主源只读").length).toBeGreaterThan(0);
    expect(screen.getByText("机构知识血缘")).toBeInTheDocument();
    expect(screen.getAllByText("院内覆盖可治理").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: /定制为本机构版本/ })).toBeInTheDocument();
    expect(screen.queryByText("待审核候选总数")).not.toBeInTheDocument();
    expect(screen.queryByText("模型生产上线准备")).not.toBeInTheDocument();
  });

  it("keeps platform-source guidance in service institution language", () => {
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "t-1" },
        permissions: [{ code: "knowledge.write" }],
      },
    });

    renderPage(<InstitutionKnowledge />);

    expect(screen.getByText("当前位于平台治理入口")).toBeInTheDocument();
    expect(
      screen.getByText("平台负责维护权威标准；机构定制、发布和恢复操作在对应医疗机构内完成。"),
    ).toBeInTheDocument();
  });

  it("separates platform-source and tenant-overlay production lanes with visible ownership labels", async () => {
    const user = userEvent.setup();
    mockUseKnowledgeProductionJobs.mockReturnValue({
      data: {
        items: [
          {
            jobCode: "job-platform-1",
            producer: "API_MODEL",
            targetPipeline: "PLATFORM_SOURCE",
            domain: "GUIDELINE",
            modelStrategy: "gpt-platform",
            status: "RUNNING",
            candidateCount: 2,
            createdAt: "2026-06-16T10:00:00Z",
          },
          {
            jobCode: "job-overlay-1",
            producer: "LOCAL_MODEL",
            targetPipeline: "TENANT_OVERLAY",
            domain: "PROTOCOL",
            modelStrategy: "ollama-local",
            status: "PENDING",
            candidateCount: 1,
            createdAt: "2026-06-16T11:00:00Z",
          },
        ],
        page: 1,
        size: 20,
        total: 2,
        hasNext: false,
        totalEstimated: false,
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });

    renderPage(<KnowledgeProduction />);

    expect(screen.getByText("双形态生产分区")).toBeInTheDocument();
    expect(screen.getByText("平台主源只读发布账本")).toBeInTheDocument();
    expect(screen.getByText("院内覆盖本机构治理")).toBeInTheDocument();
    expect(screen.getAllByText("生产任务已登记").length).toBeGreaterThan(0);
    expect(screen.queryByText("job-platform-1")).not.toBeInTheDocument();
    expect(screen.queryByText("job-overlay-1")).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getAllByText("job-platform-1").length).toBeGreaterThan(0);
    expect(screen.getAllByText("job-overlay-1").length).toBeGreaterThan(0);
  });

  it("renders the knowledge production center as a standalone production entry", async () => {
    const user = userEvent.setup();
    renderPage(<KnowledgeProduction />);

    expect(mockUseKnowledgeProductionReadiness).toHaveBeenCalledWith(
      { producer: "API_MODEL" },
      true,
    );
    expect(mockUseKnowledgeProductionJobs).toHaveBeenCalledWith({ page: 1, size: 20 }, true);
    expect(mockUseKnowledgeProductionCandidates).toHaveBeenCalledWith("job-ai-1");
    expect(mockUseKnowledgeProductionGateResults).toHaveBeenCalledWith("job-ai-1");
    expect(mockUseKnowledgeProductionTriageResults).toHaveBeenCalledWith("job-ai-1");
    expect(mockUseKnowledgeProductionShadowRuns).toHaveBeenCalledWith("job-ai-1");
    expect(mockUseCandidateCoexistence).toHaveBeenCalledWith("kv:42:2026.06");
    expect(mockUseKnowledgeAcquisitionSources).toHaveBeenCalledWith({ page: 1, size: 20 });

    expect(screen.getByText("公域来源治理")).toBeInTheDocument();
    expect(screen.getByText("模型生产上线准备")).toBeInTheDocument();
    expect(screen.getByText("模型服务未就绪")).toBeInTheDocument();
    expect(screen.getAllByText("生产任务").length).toBeGreaterThan(0);
    expect(screen.getAllByText("模型生产策略").length).toBeGreaterThan(1);
    expect(screen.getAllByText("生产任务已登记").length).toBeGreaterThan(0);
    expect(screen.getByText("模型生产策略已配置")).toBeInTheDocument();
    expect(screen.getByText("八类状态分流")).toBeInTheDocument();
    expect(screen.getByText("八类状态队列")).toBeInTheDocument();
    expect(screen.queryByText("job-ai-1")).not.toBeInTheDocument();
    expect(screen.queryByText("gpt-pipeline")).not.toBeInTheDocument();
    expect(screen.queryByText("SOURCE_ANCHOR")).not.toBeInTheDocument();
    expect(screen.queryByText("8 态分流")).not.toBeInTheDocument();
    expect(screen.queryByText("8 态队列")).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getAllByText("job-ai-1").length).toBeGreaterThan(0);
    expect(screen.getByText("gpt-pipeline")).toBeInTheDocument();
    expect(screen.getAllByText("统一模型服务").length).toBeGreaterThan(0);
    expect(screen.getByText("生产安全校验结果")).toBeInTheDocument();
    expect(screen.getByText("来源锚点 · SOURCE_ANCHOR")).toBeInTheDocument();
    expect(screen.getByText("八类状态分流")).toBeInTheDocument();
    expect(screen.getByText("CONFLICT")).toBeInTheDocument();
    expect(screen.getByText("影子评测")).toBeInTheDocument();
    expect(screen.getByText("误报率超过阈值")).toBeInTheDocument();
    expect(screen.getByTestId("production-readiness-table")).toBeInTheDocument();
    expect(screen.getByTestId("production-jobs-table")).toBeInTheDocument();
    expect(screen.getByTestId("production-candidate-lineage-table")).toBeInTheDocument();
    expect(screen.getByTestId("production-gate-results-table")).toBeInTheDocument();
    expect(screen.getByTestId("production-triage-results-table")).toBeInTheDocument();
    expect(screen.getByTestId("production-shadow-runs-table")).toBeInTheDocument();
    expect(screen.getByText("共存替换提醒")).toBeInTheDocument();
    expect(screen.getByText("候选不可执行")).toBeInTheDocument();
    expect(screen.getAllByText("审核通过后将触发 SYS-08 原子替换").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /生成|AI 生成|创建候选/ })).not.toBeInTheDocument();
  });

  it("does not load review-desk candidates while rendering the production center", () => {
    renderPage(<KnowledgeProduction />);

    expect(
      mockUseKnowledgeCandidates.mock.calls.every(([identityId]) => identityId === undefined),
    ).toBe(true);
  });

  it("blocks production job creation while model production prerequisites are not ready", () => {
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        permissions: [{ code: "knowledge.write" }],
      },
    });

    renderPage(<KnowledgeProduction />);

    expect(screen.getByText("模型生产前置仍有阻断")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "创建生产任务" })).toBeDisabled();
  });

  it("默认用业务语言展示模型生产上线准备，证据详情打开后才展示原始追溯字段", async () => {
    const user = userEvent.setup();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        menuKeys: ["knowledge-production"],
        permissions: [{ code: "knowledge.write" }, { code: "asset.read" }],
      },
    });
    mockUseKnowledgeProductionReadiness.mockReturnValue({
      data: {
        tenantId: "tenant-A",
        producer: "API_MODEL",
        capabilityCode: "knowledge.production.knowledge",
        providerCode: "provider-openai",
        deploymentForm: "EXTERNAL",
        ready: false,
        modelInvocationAllowed: false,
        items: [
          {
            code: "LITERATURE_ROOT",
            ready: true,
            required: true,
            message: "平台知识文献资料库根地址已配置",
            evidence: "file:///zoesoft/medkernel/var/platform-knowledge/literature-materials/",
          },
          {
            code: "MODEL_PROVIDER",
            ready: false,
            required: true,
            message: "未发现匹配的模型服务",
            evidence: "生产方式：模型服务生产",
          },
          {
            code: "EGRESS_GOVERNANCE",
            ready: false,
            required: true,
            message: "公网模型使用边界不可执行",
            evidence: "能力：knowledge.production.knowledge；原因：未配置字段允许字段",
          },
          {
            code: "VERSION_TRIPLE",
            ready: false,
            required: true,
            message: "已生效模型版本、提示词和工具版本组合不可执行",
            evidence:
              "能力：knowledge.production.knowledge；原因：当前能力方案未包含提示词、工具与模型版本组合",
          },
        ],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });

    renderPage(<KnowledgeProduction />);

    expect(screen.getByText("文献资料库")).toBeInTheDocument();
    expect(screen.getByText("模型服务")).toBeInTheDocument();
    expect(screen.getByText("模型使用边界")).toBeInTheDocument();
    expect(screen.getByText("提示词、工具与模型版本")).toBeInTheDocument();
    expect(screen.getAllByText("证据已记录").length).toBeGreaterThan(0);
    expect(screen.queryByText("LITERATURE_ROOT")).not.toBeInTheDocument();
    expect(screen.queryByText("MODEL_PROVIDER")).not.toBeInTheDocument();
    expect(screen.queryByText("VERSION_TRIPLE")).not.toBeInTheDocument();
    expect(screen.queryByText(/file:\/\/\/zoesoft/)).not.toBeInTheDocument();
    expect(screen.queryByText(/knowledge\.production\.knowledge/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("文献资料库 · LITERATURE_ROOT")).toBeInTheDocument();
    expect(screen.getByText("模型服务 · MODEL_PROVIDER")).toBeInTheDocument();
    expect(screen.getByText("提示词、工具与模型版本 · VERSION_TRIPLE")).toBeInTheDocument();
    expect(screen.getByText(/file:\/\/\/zoesoft/)).toBeInTheDocument();
    expect(screen.getAllByText(/knowledge\.production\.knowledge/).length).toBeGreaterThan(0);
  });

  it("creates production jobs and exposes only persisted initialization batches for bulk approval", async () => {
    const user = userEvent.setup();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        permissions: [{ code: "knowledge.write" }, { code: "knowledge.review" }],
      },
    });
    mockUseKnowledgeProductionReadiness.mockReturnValue({
      data: {
        tenantId: "tenant-A",
        producer: "API_MODEL",
        capabilityCode: "knowledge-generation",
        providerCode: "provider-openai",
        deploymentForm: "EXTERNAL",
        ready: true,
        modelInvocationAllowed: true,
        items: [],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });

    renderPage(<KnowledgeProduction />);

    expect(screen.getByRole("heading", { name: "知识生产任务办理" })).toBeInTheDocument();
    expect(screen.getAllByText("新建任务").length).toBeGreaterThan(0);
    expect(screen.getAllByText("查看进度").length).toBeGreaterThan(0);
    expect(screen.getAllByText("审核候选").length).toBeGreaterThan(0);
    expect(screen.getAllByText("评估影响").length).toBeGreaterThan(0);
    expect(screen.getAllByText("记录结论").length).toBeGreaterThan(0);
    expect(screen.getByDisplayValue("统一模型服务（本地或外部模型服务）")).toBeDisabled();
    expect(screen.getByDisplayValue("医学知识")).toBeDisabled();
    expect(screen.queryByRole("option", { name: "规则" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("生产器")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("例如 院内模型知识生产策略")).toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText("例如 gpt-pipeline / 外部模型策略标识"),
    ).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue("gpt-pipeline")).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText("来源范围"));
    await user.type(screen.getByLabelText("来源范围"), "acquisition-run:guideline-2026");
    await user.click(screen.getByRole("button", { name: "创建生产任务" }));

    await waitFor(() =>
      expect(createProductionJob).toHaveBeenCalledWith({
        sourceScope: "acquisition-run:guideline-2026",
        assetType: "KNOWLEDGE",
        targetPipeline: "TENANT_OVERLAY",
        domain: "GUIDELINE",
        modelStrategy: undefined,
      }),
    );
    expect(mockUseKnowledgeInitializationBatches).toHaveBeenCalledWith(true);
    expect(screen.getAllByText("高风险必须逐条确认并保留完整证据").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /批量通过候选/ })).not.toBeInTheDocument();
    expect(screen.getByText("初始化发行批次")).toBeInTheDocument();
    expect(screen.getAllByText("foundation-f1-1.0.0").length).toBeGreaterThan(0);
    expect(screen.getByText(/基础知识发行 · 1\.0\.0 · 总验收与发行证据/)).toBeInTheDocument();
    expect(screen.getByText("发行摘要已冻结并校验")).toBeInTheDocument();
    expect(screen.queryByText("FOUNDATION")).not.toBeInTheDocument();
    expect(screen.queryByText("F8")).not.toBeInTheDocument();
    expect(screen.getByText("低风险 1 · 可原子批审")).toBeInTheDocument();
    expect(screen.getByText("中风险 1 · 必须逐条审核")).toBeInTheDocument();
    expect(screen.getByText("高风险 1 · 必须逐条确认并保留证据")).toBeInTheDocument();
  });

  it("uses knowledge identity language when model generation creates a new target", async () => {
    const user = userEvent.setup();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        permissions: [{ code: "knowledge.write" }],
      },
    });
    mockUseKnowledgeProductionReadiness.mockReturnValue({
      data: {
        tenantId: "tenant-A",
        producer: "API_MODEL",
        capabilityCode: "knowledge.production.knowledge",
        providerCode: "provider-openai",
        deploymentForm: "EXTERNAL",
        ready: true,
        modelInvocationAllowed: true,
        items: [],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });

    renderPage(<KnowledgeProduction />);

    await user.click(screen.getByRole("button", { name: "启动大模型生成" }));
    expect(screen.getByText("生成正式知识候选")).toBeInTheDocument();
    await user.click(screen.getByLabelText("创建新身份候选"));
    expect(screen.getByLabelText("新知识身份")).toBeInTheDocument();
    expect(screen.queryByText("新身份编码")).not.toBeInTheDocument();
  });

  it("uses business choices in model generation instead of exposing raw capability and identity codes", async () => {
    const user = userEvent.setup();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        permissions: [{ code: "knowledge.write" }],
      },
    });
    mockUseKnowledgeProductionReadiness.mockReturnValue({
      data: {
        tenantId: "tenant-A",
        producer: "API_MODEL",
        capabilityCode: "knowledge.production.knowledge",
        providerCode: "provider-openai",
        deploymentForm: "EXTERNAL",
        ready: true,
        modelInvocationAllowed: true,
        items: [],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });

    renderPage(<KnowledgeProduction />);

    await user.click(screen.getByRole("button", { name: "启动大模型生成" }));
    const dialog = screen.getByRole("dialog", { name: "生成正式知识候选" });

    expect(within(dialog).getAllByText("正式知识候选生成").length).toBeGreaterThan(0);
    expect(within(dialog).getByText("统一模型服务")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("知识主题")).toHaveValue("VTE 防治指南");
    expect(within(dialog).queryByLabelText("资产身份")).not.toBeInTheDocument();
    expect(
      within(dialog).queryByDisplayValue("knowledge.production.knowledge"),
    ).not.toBeInTheDocument();
    expect(within(dialog).queryByDisplayValue("provider-openai")).not.toBeInTheDocument();
    expect(within(dialog).queryByDisplayValue("KNOW.VTE.GUIDE")).not.toBeInTheDocument();
    expect(within(dialog).queryByPlaceholderText("例如 KNOW.VTE.GUIDE")).not.toBeInTheDocument();
    expect(
      within(dialog).queryByPlaceholderText("例如 GL-VTE-2026:v1:section-2"),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).getByPlaceholderText("填写指南章节、制度条款或文献段落定位"),
    ).toBeInTheDocument();

    await user.click(within(dialog).getByRole("combobox", { name: "现有知识身份" }));
    expect(
      await screen.findByText("VTE 防治指南", { selector: ".ant-select-item-option-content" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("VTE 防治指南 · KNOW.VTE.GUIDE", {
        selector: ".ant-select-item-option-content",
      }),
    ).not.toBeInTheDocument();
  });

  it("starts real model generation for the selected job with controlled source and identity", async () => {
    const user = userEvent.setup();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        permissions: [{ code: "knowledge.write" }],
      },
    });
    mockUseKnowledgeProductionReadiness.mockReturnValue({
      data: {
        tenantId: "tenant-A",
        producer: "API_MODEL",
        capabilityCode: "knowledge.production.knowledge",
        providerCode: "provider-openai",
        deploymentForm: "EXTERNAL",
        ready: true,
        modelInvocationAllowed: true,
        items: [],
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });

    renderPage(<KnowledgeProduction />);

    await user.click(screen.getByRole("button", { name: "启动大模型生成" }));
    expect(screen.getByText("生成正式知识候选")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("来源锚点"));
    await user.type(screen.getByLabelText("来源锚点"), "GL-VTE-2026:v1:section-2");
    await user.clear(screen.getByLabelText("生成提示"));
    await user.type(screen.getByLabelText("生成提示"), "请依据来源锚点生成结构化候选知识。");
    await user.click(screen.getByRole("button", { name: "开始生成候选" }));

    await waitFor(() =>
      expect(generateModelCandidate).toHaveBeenCalledWith({
        jobCode: "job-ai-1",
        request: {
          capabilityCode: "knowledge.production.knowledge",
          prompt: "请依据来源锚点生成结构化候选知识。",
          providerCode: "provider-openai",
          timeoutSeconds: 90,
          assetIdentity: "KNOW.VTE.GUIDE",
          subject: "VTE 防治指南",
          sources: [
            {
              sourceRef: "GL-VTE-2026:v1:section-2",
              authorityLevel: "B_GUIDELINE",
            },
          ],
          trustLevel: "B_GUIDELINE",
          riskLevel: "MEDIUM",
          target: { targetIdentityId: 42 },
        },
      }),
    );
  });

  it(
    "confirms actionable egress purpose and retries model production with the same request",
    async () => {
      const user = userEvent.setup();
      generateModelCandidate.mockResolvedValueOnce({
        jobCode: "job-ai-1",
        modelTaskId: "task-confirm-1",
        modelMode: "B2",
        summary: {
          candidates: [],
          skipped: [],
          blocked: [
            {
              assetType: "KNOWLEDGE",
              jobCode: "job-ai-1",
              failedGates: [
                {
                  code: "MODEL_EGRESS_CONFIRMATION",
                  reason:
                    "模型外调已阻断，需先完成本次用途与责任确认：载荷摘要=sha256-confirmation-required",
                },
              ],
            },
          ],
        },
        egressConfirmation: {
          capabilityCode: "knowledge.production.knowledge",
          payloadHash: "sha256-confirmation-required",
          egressFields: ["prompt"],
          providerCode: "provider-openai",
          message: "高敏患者上下文外调前需要责任确认",
        },
      });
      mockUseSecurityProfile.mockReturnValue({
        data: {
          dataScope: { tenantId: "tenant-A" },
          permissions: [{ code: "knowledge.write" }],
        },
      });
      mockUseKnowledgeProductionReadiness.mockReturnValue({
        data: {
          tenantId: "tenant-A",
          producer: "API_MODEL",
          capabilityCode: "knowledge.production.knowledge",
          providerCode: "provider-openai",
          deploymentForm: "EXTERNAL",
          ready: true,
          modelInvocationAllowed: true,
          items: [],
        },
        isLoading: false,
        isError: false,
        error: undefined,
        refetch: vi.fn(),
      });

      renderPage(<KnowledgeProduction />);

      await user.click(screen.getByRole("button", { name: "启动大模型生成" }));
      await user.clear(screen.getByLabelText("来源锚点"));
      await user.type(screen.getByLabelText("来源锚点"), "GL-VTE-2026:v1:section-2");
      await user.clear(screen.getByLabelText("生成提示"));
      await user.type(screen.getByLabelText("生成提示"), "请依据来源锚点生成结构化候选知识。");
      await user.click(screen.getByRole("button", { name: "开始生成候选" }));

      await waitFor(() => expect(generateModelCandidate).toHaveBeenCalled());
      expect(await screen.findByText("确认模型外调用途")).toBeInTheDocument();
      const egressDialog = screen.getByText("确认模型外调用途").closest(".ant-modal");
      expect(egressDialog).toBeTruthy();
      const egressScope = within(egressDialog as HTMLElement);
      expect(egressScope.getByText(/高敏患者上下文外调前需要责任确认/)).toBeInTheDocument();
      expect(egressScope.getByText("正式知识候选生成")).toBeInTheDocument();
      expect(egressScope.getByText("统一模型服务")).toBeInTheDocument();
      expect(egressScope.getByText("脱敏摘要已登记")).toBeInTheDocument();
      expect(egressScope.getByText("生成提示")).toBeInTheDocument();
      expect(egressScope.queryByText("sha256-confirmation-required")).not.toBeInTheDocument();
      expect(egressScope.queryByText("knowledge.production.knowledge")).not.toBeInTheDocument();
      expect(egressScope.queryByText("provider-openai")).not.toBeInTheDocument();

      await user.type(
        screen.getByLabelText("用途说明"),
        "确认用于知识候选生成，患者上下文已按最小必要出域",
      );
      await user.click(screen.getByRole("button", { name: "记录确认并重试" }));

      await waitFor(() =>
        expect(confirmModelEgress).toHaveBeenCalledWith({
          capabilityCode: "knowledge.production.knowledge",
          payloadHash: "sha256-confirmation-required",
          purpose: "确认用于知识候选生成，患者上下文已按最小必要出域",
        }),
      );
      await waitFor(() => expect(generateModelCandidate).toHaveBeenCalledTimes(2));
    },
    KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS,
  );

  it("approves only the frozen LOW subset of an initialization batch", async () => {
    const user = userEvent.setup();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        permissions: [{ code: "knowledge.review" }],
      },
    });

    renderPage(<KnowledgeProduction />);
    await user.click(screen.getByRole("button", { name: "批准低风险候选" }));

    expect(screen.getByText("确认批量批准低风险候选")).toBeInTheDocument();
    expect(screen.getByText(/仅处理服务端冻结清单中的低风险条目/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "确认批准" }));

    await waitFor(() =>
      expect(approveLowInitializationBatch).toHaveBeenCalledWith({
        batchCode: "foundation-f1-1.0.0",
        expectedOverallHash: "e".repeat(64),
        idempotencyKey: expect.stringMatching(
          /^knowledge-initialization-foundation-f1-1\.0\.0-low-\d+$/,
        ),
        reason: "初始化发行清单低风险候选原子批审",
      }),
    );
  });

  it("keeps initialization release batches operable before the first production job exists", () => {
    mockUseKnowledgeProductionJobs.mockReturnValue({
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
      error: undefined,
      refetch: vi.fn(),
    });

    renderPage(<KnowledgeProduction />);

    expect(screen.getByText("暂无生产任务")).toBeInTheDocument();
    expect(screen.getByText("初始化发行批次")).toBeInTheDocument();
    expect(screen.getAllByText("foundation-f1-1.0.0").length).toBeGreaterThan(0);
  });

  it("keeps the production center visible when downstream evidence queries partially fail", async () => {
    mockUseKnowledgeProductionGateResults.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error("生产安全校验接口断开"),
    });
    mockUseKnowledgeProductionShadowRuns.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error("影子评测接口断开"),
    });

    renderPage(<KnowledgeProduction />);

    expect(screen.getByText("模型生产上线准备")).toBeInTheDocument();
    expect(screen.getByText("生产证据部分读取失败")).toBeInTheDocument();
    expect(screen.getByText(/生产安全校验结果：生产安全校验接口断开/)).toBeInTheDocument();
    expect(screen.getByText(/影子评测：影子评测接口断开/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /生成|AI 生成|创建候选/ })).not.toBeInTheDocument();
  });

  it("shows agent progress, triage queue, side-by-side coexistence and a cancellable job action", async () => {
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
      data: pageResponse([
        {
          jobCode: "job-agent-7",
          assetIdentity: "rule:agent:vte",
          contentHash: "b".repeat(64),
          candidateRef: "kv:42:2026.07",
          riskLevel: "HIGH",
        },
      ]),
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

    renderPage(<KnowledgeProduction />);

    expect(screen.getByText("Agent 进度与中止")).toBeInTheDocument();
    expect(screen.getByText("Agent 工具")).toBeInTheDocument();
    expect(screen.getByText("生成候选 4 条")).toBeInTheDocument();
    expect(screen.getByText("八类状态队列")).toBeInTheDocument();
    expect(screen.queryByText("8 态队列")).not.toBeInTheDocument();
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
    expect(screen.queryByText("job-agent-7")).not.toBeInTheDocument();
    expect(screen.queryByText("agent-verified")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "中止生产任务" }));
    expect(await screen.findByText("确认中止生产任务")).toBeInTheDocument();
    expect(screen.getByText(/将中止当前选中的生产任务/)).toBeInTheDocument();
    expect(screen.queryByText("job-agent-7")).not.toBeInTheDocument();
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
          modelTaskId: "task-vte-ai",
          modelMode: "B2",
          modelVersion: "claude-opus-4",
          promptVersion: "prompt:aikstd13-v1",
          toolVersion: "tool:submit-candidate-v1",
          sourceCitations: '[{"anchor":"source-fragment-candidate","version":"sv-2026"}]',
          confidence: 0.87,
          fallbackUsed: true,
          fallbackReason: "B2 -> B1：外部模型服务限流，本地模型成功",
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
    expect(screen.getByText(/统一模型服务/)).toBeInTheDocument();
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
    expect(screen.getByText("现行摘要")).toBeInTheDocument();
    expect(screen.getByText("现行摘要已记录")).toBeInTheDocument();
    expect(screen.getByText("候选摘要")).toBeInTheDocument();
    expect(screen.getByText("候选摘要已记录")).toBeInTheDocument();
    expect(screen.queryByText("contentHash")).not.toBeInTheDocument();
    expect(screen.queryByText("active-real-hash")).not.toBeInTheDocument();
    expect(screen.queryByText("source-fragment-active")).not.toBeInTheDocument();
    expect(screen.queryByText("candidate-real-hash")).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getAllByText("contentHash").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("active-real-hash").length).toBeGreaterThan(0);
    expect(screen.getByText("source-fragment-active")).toBeInTheDocument();
    expect(screen.getAllByText("candidate-real-hash").length).toBeGreaterThan(0);
    expect(screen.getByText("source-fragment-candidate")).toBeInTheDocument();
    expect(screen.getByText("候选与现行权威版本存在高危条款冲突。")).toBeInTheDocument();
  });

  it("shows AI production provenance in hospital language without exposing technical tokens by default", async () => {
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
          modelTaskId: "task-vte-ai",
          modelMode: "B2",
          modelVersion: "claude-opus-4",
          promptVersion: "prompt:aikstd13-v1",
          toolVersion: "tool:submit-candidate-v1",
          sourceCitations: '[{"anchor":"source-fragment-candidate","version":"sv-2026"}]',
          confidence: 0.87,
          fallbackUsed: true,
          fallbackReason: "B2 -> B1：外部模型服务限流，本地模型成功",
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
    expect(screen.getByText("院内覆盖")).toBeInTheDocument();
    expect(screen.getAllByText("置信 0.87").length).toBeGreaterThan(0);
    expect(screen.getAllByText("已启用备用生产能力，候选仍需人工复核").length).toBeGreaterThan(0);
    expect(screen.getAllByText("已记录来源引用，审核时以来源锚点为准").length).toBeGreaterThan(0);
    expect(screen.queryByText("job-vte-ai")).not.toBeInTheDocument();
    expect(screen.queryByText("task-vte-ai")).not.toBeInTheDocument();
    expect(screen.queryByText("gpt-pipeline")).not.toBeInTheDocument();
    expect(screen.queryByText("B2")).not.toBeInTheDocument();
    expect(screen.queryByText("claude-opus-4")).not.toBeInTheDocument();
    expect(screen.queryByText("prompt:aikstd13-v1")).not.toBeInTheDocument();
    expect(screen.queryByText("tool:submit-candidate-v1")).not.toBeInTheDocument();
    expect(
      screen.queryByText("降级：B2 -> B1：外部模型服务限流，本地模型成功"),
    ).not.toBeInTheDocument();
  });

  it("reveals low-frequency AI provenance from contextual evidence details", async () => {
    const user = userEvent.setup();
    mockUseSecurityProfile.mockReturnValue({
      data: {
        dataScope: { tenantId: "tenant-A" },
        menuKeys: ["knowledge-production"],
        permissions: [{ code: "knowledge.publish" }],
      },
    });
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
          modelTaskId: "task-vte-ai",
          modelMode: "B2",
          modelVersion: "claude-opus-4",
          promptVersion: "prompt:aikstd13-v1",
          toolVersion: "tool:submit-candidate-v1",
          sourceCitations: '[{"anchor":"source-fragment-candidate","version":"sv-2026"}]',
          confidence: 0.87,
          fallbackUsed: true,
          fallbackReason: "B2 -> B1：外部模型服务限流，本地模型成功",
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
    expect(screen.queryByText("job-vte-ai")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "生产证据详情" }));

    expect(screen.getByText("job-vte-ai")).toBeInTheDocument();
    expect(screen.getByText("task-vte-ai")).toBeInTheDocument();
    expect(screen.getByText("gpt-pipeline")).toBeInTheDocument();
    expect(screen.getAllByText("B2").length).toBeGreaterThan(0);
    expect(screen.getAllByText("claude-opus-4").length).toBeGreaterThan(0);
    expect(screen.getByText("prompt:aikstd13-v1")).toBeInTheDocument();
    expect(screen.getByText("tool:submit-candidate-v1")).toBeInTheDocument();
    expect(
      screen.getAllByText("降级：B2 -> B1：外部模型服务限流，本地模型成功").length,
    ).toBeGreaterThan(0);
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

  it("locks review to the selected knowledge version without a package selector", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));

    expect(screen.queryByLabelText("审核上下文" + "包版本")).not.toBeInTheDocument();
    expect(screen.getByText("审核对象已锁定为当前候选版本")).toBeInTheDocument();
  });

  it("does not load production evidence from the review workspace before needed", () => {
    renderPage();

    expect(mockUseKnowledgeProductionReadiness).toHaveBeenCalledWith(
      { producer: "API_MODEL" },
      false,
    );
    expect(mockUseKnowledgeProductionJobs).toHaveBeenCalledWith({ page: 1, size: 20 }, false);
  });

  it("returns a candidate for revision through the RETURN review decision with a mandatory reason", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));
    fireEvent.change(screen.getByLabelText("审核理由"), {
      target: { value: "请补充禁忌章节后重提。" },
    });
    await user.click(screen.getByLabelText("内容缺口"));
    fireEvent.click(screen.getByRole("button", { name: /退\s*修/ }));

    await waitFor(() => {
      expect(reviewCandidate).toHaveBeenCalledWith({
        candidateId: 9001,
        request: {
          decision: "RETURN",
          reason: "请补充禁忌章节后重提。",
          feedbackType: "CONTENT_GAP",
          followupAction: "CREATE_REVISION_CANDIDATE",
        },
        idempotencyKey: expect.stringContaining("knowledge-review-9001"),
      });
    });
  });

  it("does not submit a return decision when the revision reason is blank", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));
    fireEvent.click(screen.getByRole("button", { name: /退\s*修/ }));

    await waitFor(() => {
      expect(screen.getByText("请填写审核理由")).toBeInTheDocument();
    });
    expect(reviewCandidate).not.toHaveBeenCalled();
  });

  it("rejects a candidate with not-adopted feedback when no explicit feedback is selected", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));
    fireEvent.change(screen.getByLabelText("审核理由"), {
      target: { value: "来源证据与现行权威冲突，当前不采纳。" },
    });
    fireEvent.click(screen.getByRole("button", { name: "驳回候选" }));

    await waitFor(() => {
      expect(reviewCandidate).toHaveBeenCalledWith({
        candidateId: 9001,
        request: {
          decision: "REJECT",
          reason: "来源证据与现行权威冲突，当前不采纳。",
          feedbackType: "NOT_ADOPTED",
          followupAction: "ARCHIVE_REJECTED",
        },
        idempotencyKey: expect.stringContaining("knowledge-review-9001"),
      });
    });
  });

  it(
    "reviews a candidate through the KNOW-02 classification review endpoint instead of mutating local state",
    async () => {
      const user = userEvent.setup();
      renderPage();

      await user.click(screen.getByRole("button", { name: "查看审核对照" }));
      expect(screen.getByText("审核对象已锁定为当前候选版本")).toBeInTheDocument();
      fireEvent.change(screen.getByLabelText("审核理由"), {
        target: { value: "已核对来源锚点和现行版差异，允许替换。" },
      });
      for (const label of ["结构校验", "术语绑定", "依赖完整性", "安全单调性", "影响评估"]) {
        fireEvent.click(screen.getByRole("checkbox", { name: label }));
      }
      fireEvent.click(screen.getByRole("button", { name: "通过并发布" }));

      await waitFor(() => {
        expect(reviewCandidate).toHaveBeenCalledWith({
          candidateId: 9001,
          request: {
            decision: "APPROVE",
            reason: "已核对来源锚点和现行版差异，允许替换。",
            feedbackType: "ACCEPTED",
            followupAction: "NONE",
            publishEvidence: {
              qualityGate: {
                schemaValid: true,
                terminologyBindingComplete: true,
                dependencyIntegrityVerified: true,
                safetyMonotonicityVerified: true,
                impactSimulationPassed: true,
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
      expect(
        await screen.findByText("VTE 防治指南 2026", {
          selector: ".ant-select-item-option-content",
        }),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("VTE 防治指南 2026 · plat:drug:vte-guide-2026", {
          selector: ".ant-select-item-option-content",
        }),
      ).not.toBeInTheDocument();
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
