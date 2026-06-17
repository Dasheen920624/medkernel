import {
  AuditOutlined,
  BranchesOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
  SwapOutlined,
} from "@ant-design/icons";
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Divider,
  Drawer,
  Form,
  Input,
  Modal,
  Progress,
  Radio,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useDeprecateKnowledgeIdentity,
  useCreateKnowledgeCustomization,
  useCreateKnowledgeProductionJob,
  useKnowledgeCustomizations,
  useKnowledgeCandidateDiff,
  useKnowledgeCandidates,
  useCancelKnowledgeProductionJob,
  useKnowledgeProductionCandidates,
  useKnowledgeProductionGateResults,
  useKnowledgeProductionJobs,
  useKnowledgeProductionReadiness,
  useKnowledgeProductionShadowRuns,
  useKnowledgeProductionTriageResults,
  useCandidateProvenance,
  useCandidateCoexistence,
  useAssetTemplates,
  useKnowledgeIdentities,
  usePackages,
  usePublishKnowledgeCustomization,
  useRestorePlatformKnowledge,
  useReviewKnowledgeCandidate,
  useSecurityProfile,
  type CandidateClassification,
  type AikGateResult,
  type CandidateCoexistenceView,
  type CandidateCoexistenceVersionSnapshot,
  type CreateKnowledgeProductionJobRequest,
  type GenerationTriage,
  type CandidateProvenanceView,
  type KnowledgeProductionCandidateView,
  type KnowledgeProductionJob,
  type KnowledgeShadowRun,
  type KnowledgeAssetVersion,
  type KnowledgeCandidateReviewDecision,
  type KnowledgeReviewFeedbackType,
  type KnowledgeReviewFollowupAction,
  type KnowledgeDomain,
  type KnowledgeIdentity,
  type KnowledgeCustomization,
  type KnowledgeIdentityStatus,
  type VersionPublishEvidence,
} from "@/shared/api/hooks";
import {
  knowledgeCustomizationStatusLabel,
  knowledgeDomainLabel,
  knowledgeSourceLabel,
  lifecycleStatusLabel,
  riskLabel,
  sourceAuthorityLabel,
} from "@/shared/config/customerLabels";
import {
  KNOWLEDGE_DOMAIN_OPTIONS,
  KNOWLEDGE_IDENTITY_STATUS_OPTIONS,
  KNOWLEDGE_QUALITY_GATE_OPTIONS,
  KNOWLEDGE_TRIAGE_STATE_META,
} from "@/shared/config/knowledgeReview";
import { platformTenantId } from "@/shared/config/tenantDictionary";
import { OrgUnitSelect } from "@/shared/ui/OrgUnitSelect";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { SourceInfo } from "@/shared/ui/SourceInfo";

const { Text, Title } = Typography;

const CLASSIFICATION_LABELS: Record<string, string> = {
  NEW_ASSET: "全新资产",
  SAME_IDENTITY_NEW_VERSION: "同身份新版",
  DUPLICATE: "重复候选",
  CONFLICT: "冲突候选",
};

const REVIEW_STATUS_LABELS: Record<string, string> = {
  PENDING_REPLACEMENT_REVIEW: "待替换审核",
  DUPLICATE_SKIPPED: "重复已跳过",
  APPROVED: "已通过",
  REJECTED: "已驳回",
  RETURNED: "已退修",
};

const REVIEW_DECISION_SUCCESS_MESSAGES: Record<KnowledgeCandidateReviewDecision, string> = {
  APPROVE: "候选已通过审核并交由权威替换流程",
  RETURN: "候选已退修，退回生产者修订重提",
  REJECT: "候选已驳回并留档",
};

const REVIEW_FOLLOWUP_BY_FEEDBACK: Record<KnowledgeReviewFeedbackType, KnowledgeReviewFollowupAction> = {
  ACCEPTED: "NONE",
  NOT_ADOPTED: "ARCHIVE_REJECTED",
  CONTENT_GAP: "CREATE_REVISION_CANDIDATE",
  SOURCE_BLANK: "REQUEST_SOURCE_EVIDENCE",
  FALSE_POSITIVE: "MARK_FALSE_POSITIVE",
};

const VERSION_STATUS_LABELS: Record<string, string> = {
  DRAFT: "草稿",
  CANDIDATE: "候选",
  PENDING_REPLACEMENT_REVIEW: "待替换审核",
  UNDER_REVIEW: "审核中",
  ACTIVE: "当前权威",
  SUPERSEDED: "已替代",
  WITHDRAWN: "已撤回",
  REJECTED: "已驳回",
};
const KNOWLEDGE_REVIEW_PACKAGE_REFERENCE_PAGE_SIZE = 20;
const KNOWLEDGE_CUSTOMIZATION_PAGE_SIZE = 20;
const KNOWLEDGE_CANDIDATE_PAGE_SIZE = 20;

const RISK_COLORS: Record<string, "default" | "success" | "warning" | "error"> = {
  LOW: "success",
  MEDIUM: "warning",
  HIGH: "error",
};

// AIK-STD-12：AI 工厂生产器中文标识（aiGenerated 据 producer≠MANUAL，由后端判定）
const PRODUCER_LABELS: Record<string, string> = {
  API_MODEL: "API 大模型",
  AGENT_TOOL: "Agent 工具",
  LOCAL_MODEL: "本地模型",
  MANUAL: "人工录入",
};

const PIPELINE_META: Record<
  string,
  {
    label: string;
    color: "blue" | "green" | "default";
    boundaryLabel: string;
    summary: string;
    description: string;
  }
> = {
  PLATFORM_SOURCE: {
    label: "平台主源",
    color: "blue",
    boundaryLabel: "平台主源只读",
    summary: "平台主源只读发布账本",
    description: "归属 t-1 平台主租户，机构只能订阅和派生，不允许直接编辑或反写。",
  },
  TENANT_OVERLAY: {
    label: "院内覆盖",
    color: "green",
    boundaryLabel: "院内覆盖可治理",
    summary: "院内覆盖本机构治理",
    description: "归属当前机构租户，只影响本机构继承范围，禁止污染平台主源。",
  },
};
const PIPELINE_KEYS = ["PLATFORM_SOURCE", "TENANT_OVERLAY"] as const;

const PRODUCTION_JOB_STATUS_LABELS: Record<string, string> = {
  PENDING: "待开始",
  RUNNING: "进行中",
  COMPLETED: "已完成",
  FAILED: "失败",
  CANCELLED: "已中止",
};

function producerLabel(producer?: string) {
  if (!producer) return "未知来源";
  return PRODUCER_LABELS[producer] ?? producer;
}

function pipelineMeta(pipeline?: string | null) {
  if (!pipeline) return { ...PIPELINE_META.TENANT_OVERLAY, label: "未返回管道" };
  return (
    PIPELINE_META[pipeline] ?? {
      label: pipeline,
      color: "default" as const,
      boundaryLabel: "未知管道",
      summary: pipeline,
      description: "服务端返回了暂未登记的知识生产管道，请核查配置。",
    }
  );
}

function feedbackTypeForDecision(
  decision: KnowledgeCandidateReviewDecision,
  selected?: KnowledgeReviewFeedbackType,
): KnowledgeReviewFeedbackType {
  if (decision === "APPROVE") return "ACCEPTED";
  if (selected) return selected;
  return decision === "RETURN" ? "CONTENT_GAP" : "NOT_ADOPTED";
}

function PipelineBoundaryCard({ title = "双形态知识分区" }: { title?: string }) {
  return (
    <Card title={title}>
      <Row gutter={[16, 16]}>
        {PIPELINE_KEYS.map((pipeline) => {
          const meta = pipelineMeta(pipeline);
          return (
            <Col xs={24} lg={12} key={pipeline}>
              <Alert
                type={pipeline === "PLATFORM_SOURCE" ? "info" : "success"}
                showIcon
                message={
                  <Space size={4} wrap>
                    <Tag color={meta.color}>{meta.label}</Tag>
                    <Tag>{meta.boundaryLabel}</Tag>
                    <Text strong>{meta.summary}</Text>
                  </Space>
                }
                description={meta.description}
              />
            </Col>
          );
        })}
      </Row>
    </Card>
  );
}

function productionStatusColor(status?: string | null) {
  if (status === "COMPLETED" || status === "PASSED") return "success";
  if (status === "RUNNING" || status === "PENDING") return "processing";
  if (status === "FAILED") return "error";
  if (status === "CANCELLED") return "default";
  return "default";
}

function canCancelProductionJob(status?: string | null) {
  return status === "PENDING" || status === "RUNNING";
}

function confidenceText(confidence?: number | null) {
  return confidence === null || confidence === undefined ? "未返回置信" : `置信 ${confidence}`;
}

function fallbackText(provenance: CandidateProvenanceView) {
  if (!provenance.fallbackUsed) return "未降级";
  return `降级：${provenance.fallbackReason || "已降级，未返回原因"}`;
}

function productionProgressPercent(
  job: KnowledgeProductionJob | undefined,
  candidateCount: number,
  gateCount: number,
  triageCount: number,
  shadowCount: number,
) {
  if (!job) return 0;
  if (job.status === "COMPLETED") return 100;
  if (job.status === "FAILED" || job.status === "CANCELLED") return 100;
  const finishedStages = [
    candidateCount > 0,
    gateCount > 0,
    triageCount > 0,
    shadowCount > 0,
  ].filter(Boolean).length;
  return Math.max(job.status === "RUNNING" ? 20 : 5, finishedStages * 25);
}

function triageLabel(state?: string | null) {
  return (
    KNOWLEDGE_TRIAGE_STATE_META.find((item) => item.state === state)?.label ?? state ?? "未分流"
  );
}

function reviewTaskReminder(coexistence?: CandidateCoexistenceView) {
  if (!coexistence) return "未返回审后任务提醒";
  if (coexistence.approvalOutcome === "APPROVE_ACTIVATE_FIRST_VERSION") {
    return "审核通过后创建 SYS-08 首次激活、投影刷新与院内同步任务；审核前候选仍不可执行。";
  }
  if (coexistence.approvalOutcome === "APPROVE_REPLACE_ACTIVE") {
    return "审核通过后创建 SYS-08 原子替换、投影刷新与院内同步任务；审核前不改变执行版本。";
  }
  return coexistence.replacementReminder || "审核结论生成后进入对应发布任务。";
}

function snapshotValue(
  snapshot: CandidateCoexistenceVersionSnapshot | undefined | null,
  key: keyof CandidateCoexistenceVersionSnapshot,
) {
  const value = snapshot?.[key];
  return value === undefined || value === null || value === "" ? "未返回" : String(value);
}

function booleanGateLabel(passed: boolean) {
  return passed ? "通过" : "阻断";
}

type ReviewFormValues = {
  packageVersion: string;
  reason: string;
  signatureId?: string;
  signerId?: string;
  signerName?: string;
  signedAt?: string;
  signatureHash?: string;
  feedbackType?: KnowledgeReviewFeedbackType;
  qualityGates?: string[];
  qualitySummary?: string;
};

type RetirementFormValues = {
  successorIdentityId?: number;
  gracePeriodEnd: string;
  migrationGuidance: string;
};

const EMPTY_RETIREMENT_FORM: RetirementFormValues = {
  successorIdentityId: undefined,
  gracePeriodEnd: "",
  migrationGuidance: "",
};

type KnowledgeGovernanceMode = "review" | "institution" | "production";

interface KnowledgeGovernanceProps {
  mode?: KnowledgeGovernanceMode;
}

function versionTitle(version?: KnowledgeAssetVersion) {
  if (!version) return "未返回版本";
  return version.versionLabel || `v${version.versionNo}`;
}

function versionSubtitle(version?: KnowledgeAssetVersion) {
  if (!version) return "暂无版本信息";
  return `${VERSION_STATUS_LABELS[version.status] ?? lifecycleStatusLabel(version.status)} · ${riskLabel(
    version.riskLevel,
  )} · ${sourceAuthorityLabel(version.authorityLevel)}`;
}

function classificationFor(
  classifications: CandidateClassification[],
  candidateVersionId?: number,
) {
  return classifications.find((item) => item.candidateVersionId === candidateVersionId);
}

function tagColorForReview(status?: string | null) {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "error";
  if (status === "RETURNED") return "warning";
  if (status === "DUPLICATE_SKIPPED") return "default";
  return "processing";
}

function customizationStatusColor(status: string) {
  if (status === "ACTIVE") return "success";
  if (status === "DRAFT") return "processing";
  return "default";
}

export function InstitutionKnowledge() {
  return <KnowledgeGovernance mode="institution" />;
}

export function KnowledgeProduction() {
  return <KnowledgeGovernance mode="production" />;
}

export default function KnowledgeGovernance({ mode = "review" }: KnowledgeGovernanceProps) {
  const { message, modal } = AntdApp.useApp();
  const [domain, setDomain] = useState<KnowledgeDomain>("GUIDELINE");
  const [status, setStatus] = useState<KnowledgeIdentityStatus>("ACTIVE");
  const [keyword, setKeyword] = useState("");
  const [identityPage, setIdentityPage] = useState(1);
  const [candidatePage, setCandidatePage] = useState(1);
  const [customizationPage, setCustomizationPage] = useState(1);
  const [productionJobCode, setProductionJobCode] = useState<string>();
  const [productionCandidateRef, setProductionCandidateRef] = useState<string>();
  const [reviewPackageSearch, setReviewPackageSearch] = useState("");
  const [selectedIdentityId, setSelectedIdentityId] = useState<number>();
  const [selectedCandidateId, setSelectedCandidateId] = useState<number>();
  const [retirementIdentity, setRetirementIdentity] = useState<KnowledgeIdentity>();
  const [retirementDraft, setRetirementDraft] =
    useState<RetirementFormValues>(EMPTY_RETIREMENT_FORM);
  const [successorKeyword, setSuccessorKeyword] = useState("");
  const [customizeIdentity, setCustomizeIdentity] = useState<KnowledgeIdentity>();
  const [customizationAction, setCustomizationAction] = useState<{
    type: "publish" | "restore";
    item: KnowledgeCustomization;
  }>();
  const [reviewForm] = Form.useForm<ReviewFormValues>();
  const [customizeForm] = Form.useForm<{
    targetOrgUnitId: string;
    applicableScope: string;
    reason: string;
  }>();
  const [customizationActionForm] = Form.useForm<{
    reason: string;
    signatureId?: string;
    signerId?: string;
    signerName?: string;
    signedAt?: string;
    signatureHash?: string;
  }>();
  const [productionJobForm] = Form.useForm<CreateKnowledgeProductionJobRequest>();
  const security = useSecurityProfile();
  const knowledgePackagesQuery = usePackages({
    page: 1,
    size: KNOWLEDGE_REVIEW_PACKAGE_REFERENCE_PAGE_SIZE,
    assetType: "KNOWLEDGE",
    keyword: reviewPackageSearch || undefined,
  });

  const identitiesQuery = useKnowledgeIdentities({
    domain,
    status,
    keyword: keyword.trim() || undefined,
    page: identityPage,
    size: 20,
    sort: "updatedAt,desc",
  });
  const identities = useMemo(
    () => identitiesQuery.data?.items ?? [],
    [identitiesQuery.data?.items],
  );
  const successorsQuery = useKnowledgeIdentities({
    domain: retirementIdentity?.domain,
    status: "ACTIVE",
    keyword: successorKeyword.trim() || undefined,
    page: 1,
    size: 20,
    sort: "updatedAt,desc",
    enabled: Boolean(retirementIdentity),
  });
  const successorOptions = useMemo(
    () =>
      (successorsQuery.data?.items ?? [])
        .filter(
          (identity) => identity.id !== retirementIdentity?.id && identity.status === "ACTIVE",
        )
        .map((identity) => ({
          value: identity.id,
          label: `${identity.subject} · ${identity.identityCode}`,
        })),
    [retirementIdentity?.id, successorsQuery.data?.items],
  );
  const canScheduleRetirement =
    security.data?.dataScope.tenantId === platformTenantId &&
    security.data.permissions.some((permission) => permission.code === "knowledge.publish");
  const currentTenantId = security.data?.dataScope.tenantId;
  const isPlatformTenant = currentTenantId === platformTenantId;
  const defaultProductionTargetPipeline = isPlatformTenant ? "PLATFORM_SOURCE" : "TENANT_OVERLAY";
  const customizationsQuery = useKnowledgeCustomizations(
    { page: customizationPage, size: KNOWLEDGE_CUSTOMIZATION_PAGE_SIZE },
    Boolean(currentTenantId && !isPlatformTenant),
  );
  const customizationItems = useMemo(
    () => customizationsQuery.data?.items ?? [],
    [customizationsQuery.data?.items],
  );
  const createCustomization = useCreateKnowledgeCustomization();
  const publishCustomization = usePublishKnowledgeCustomization();
  const restorePlatformKnowledge = useRestorePlatformKnowledge();
  const createProductionJobMutation = useCreateKnowledgeProductionJob();
  const cancelProductionJobMutation = useCancelKnowledgeProductionJob();
  const canCustomize =
    !isPlatformTenant &&
    security.data?.permissions.some((permission) => permission.code === "knowledge.write");
  const canWriteKnowledge =
    security.data?.permissions.some((permission) => permission.code === "knowledge.write") ?? false;
  const canPublishKnowledge =
    security.data?.permissions.some((permission) => permission.code === "knowledge.publish") ??
    false;
  const canPublishCustomization =
    security.data?.permissions.some((permission) => permission.code === "knowledge.publish") &&
    security.data?.permissions.some((permission) => permission.code === "tenant.override");
  const canRestoreCustomization =
    security.data?.permissions.some((permission) => permission.code === "knowledge.withdraw") &&
    security.data?.permissions.some((permission) => permission.code === "tenant.override");
  const productionReadinessQuery = useKnowledgeProductionReadiness({ producer: "API_MODEL" });
  const productionJobsQuery = useKnowledgeProductionJobs({ page: 1, size: 20 });
  const productionJobs = useMemo(
    () => productionJobsQuery.data?.items ?? [],
    [productionJobsQuery.data?.items],
  );
  const selectedProductionJobCode = productionJobCode ?? productionJobs[0]?.jobCode;
  const selectedProductionJob = productionJobs.find(
    (job) => job.jobCode === selectedProductionJobCode,
  );
  const productionCandidatesQuery = useKnowledgeProductionCandidates(selectedProductionJobCode);
  const productionGateResultsQuery = useKnowledgeProductionGateResults(selectedProductionJobCode);
  const productionTriageResultsQuery =
    useKnowledgeProductionTriageResults(selectedProductionJobCode);
  const productionShadowRunsQuery = useKnowledgeProductionShadowRuns(selectedProductionJobCode);
  const firstProductionCandidateRef = (productionCandidatesQuery.data ?? [])[0]?.candidateRef;
  const selectedProductionCandidateRef = productionCandidateRef ?? firstProductionCandidateRef;
  const productionCoexistenceQuery = useCandidateCoexistence(selectedProductionCandidateRef);

  useEffect(() => {
    setProductionCandidateRef(undefined);
  }, [selectedProductionJobCode]);

  useEffect(() => {
    productionJobForm.setFieldsValue({ targetPipeline: defaultProductionTargetPipeline });
  }, [defaultProductionTargetPipeline, productionJobForm]);

  useEffect(() => {
    if (identities.length === 0) {
      setSelectedIdentityId(undefined);
      if (identityPage > 1 && (identitiesQuery.data?.total ?? 0) > 0) {
        setIdentityPage(1);
      }
      return;
    }
    if (!selectedIdentityId || !identities.some((identity) => identity.id === selectedIdentityId)) {
      setSelectedIdentityId(identities[0].id);
    }
  }, [identities, identitiesQuery.data?.total, identityPage, selectedIdentityId]);

  const selectedIdentity = identities.find((identity) => identity.id === selectedIdentityId);
  useEffect(() => {
    setCandidatePage(1);
    setSelectedCandidateId(undefined);
  }, [selectedIdentityId]);

  const candidatesQuery = useKnowledgeCandidates(selectedIdentityId, {
    page: candidatePage,
    size: KNOWLEDGE_CANDIDATE_PAGE_SIZE,
  });
  const candidateResponse = candidatesQuery.data;
  const candidatePageData = candidateResponse?.candidates;
  const candidates = useMemo(() => candidatePageData?.items ?? [], [candidatePageData?.items]);
  const classifications = useMemo(
    () => candidateResponse?.classifications ?? [],
    [candidateResponse],
  );

  // AIK-STD-12：审核台批量反查候选 AI 工厂生产来源（候选版本引用 kv:{identityId}:{versionNo}）。
  const candidateRefs = useMemo(
    () => candidates.map((candidate) => `kv:${candidate.identityId}:${candidate.versionNo}`),
    [candidates],
  );
  const provenanceQuery = useCandidateProvenance(candidateRefs);

  // AIK-STD-12 FR-1：按候选所属领域匹配全专业标准资产模板，供审核人对照核查完整性。
  const templatesQuery = useAssetTemplates();
  const domainTemplate = useMemo(
    () =>
      (templatesQuery.data ?? []).find(
        (template) =>
          template.assetType === "KNOWLEDGE" &&
          template.knowledgeDomain === selectedIdentity?.domain,
      ),
    [templatesQuery.data, selectedIdentity?.domain],
  );
  const provenanceByRef = useMemo(() => {
    const map = new Map<string, CandidateProvenanceView>();
    for (const view of provenanceQuery.data ?? []) {
      map.set(view.candidateRef, view);
    }
    return map;
  }, [provenanceQuery.data]);
  const provenanceFor = (version?: KnowledgeAssetVersion) =>
    version ? provenanceByRef.get(`kv:${version.identityId}:${version.versionNo}`) : undefined;
  const selectedCandidate = candidates.find((candidate) => candidate.id === selectedCandidateId);
  const diffQuery = useKnowledgeCandidateDiff(selectedCandidateId);
  const reviewMutation = useReviewKnowledgeCandidate();
  const retirementMutation = useDeprecateKnowledgeIdentity();

  const diffCandidates = diffQuery.data?.candidates.items ?? [];
  const diffClassifications = diffQuery.data?.classifications ?? classifications;
  const selectedClassification = classificationFor(diffClassifications, selectedCandidateId);
  const activeVersion =
    diffCandidates.find((version) => version.id === selectedClassification?.activeVersionId) ??
    diffCandidates.find((version) => version.status === "ACTIVE");
  const candidateVersion =
    diffCandidates.find((version) => version.id === selectedCandidateId) ?? selectedCandidate;
  const reviewPackageOptions = (knowledgePackagesQuery.data?.items ?? [])
    .filter((item) => item.status !== "OFFLINE" && item.status !== "ARCHIVED")
    .map((item) => ({
      value: item.packageVersion,
      label: `${item.packageVersion} · ${item.name}`,
    }));
  const defaultReviewPackageVersion =
    reviewPackageOptions.length === 1 ? reviewPackageOptions[0].value : undefined;
  const platformPublishing = security.data?.dataScope.tenantId === platformTenantId;
  const publishEvidenceRequired = platformPublishing || candidateVersion?.riskLevel === "HIGH";

  const pendingCount = candidatePageData?.total ?? 0;
  const conflictCount = useMemo(
    () => classifications.filter((item) => item.classification === "CONFLICT").length,
    [classifications],
  );
  const highRiskCount = useMemo(
    () => candidates.filter((candidate) => candidate.riskLevel === "HIGH").length,
    [candidates],
  );

  function openCandidate(candidate: KnowledgeAssetVersion) {
    setSelectedCandidateId(candidate.id);
    reviewForm.setFieldsValue({
      packageVersion: defaultReviewPackageVersion,
      reason: "",
      feedbackType: undefined,
      qualityGates: [],
    });
  }

  async function reviewCandidate(decision: KnowledgeCandidateReviewDecision) {
    if (!selectedCandidateId) return;
    if (!selectedClassification?.id) {
      message.error("未找到候选分类审核记录");
      return;
    }
    const classificationReviewId = selectedClassification.id;
    try {
      const fields = ["packageVersion", "reason"];
      if (decision === "APPROVE" && publishEvidenceRequired) {
        fields.push("signatureId", "signerId", "signerName", "signedAt", "signatureHash");
      }
      if (decision === "APPROVE" && platformPublishing) {
        fields.push("qualityGates");
      }
      const values = await reviewForm.validateFields(fields);
      const selectedFeedbackType = reviewForm.isFieldTouched("feedbackType")
        ? (reviewForm.getFieldValue("feedbackType") as KnowledgeReviewFeedbackType | undefined)
        : undefined;
      const feedbackType = feedbackTypeForDecision(decision, selectedFeedbackType);
      const followupAction = REVIEW_FOLLOWUP_BY_FEEDBACK[feedbackType];
      let publishEvidence: VersionPublishEvidence | undefined;
      if (decision === "APPROVE" && publishEvidenceRequired) {
        const signatureId = values.signatureId?.trim();
        const signerId = values.signerId?.trim();
        const signerName = values.signerName?.trim();
        const signedAt = values.signedAt?.trim();
        const signatureHash = values.signatureHash?.trim();
        if (!signatureId || !signerId || !signerName || !signedAt || !signatureHash) {
          throw new Error("电子签名信息不完整");
        }
        const gates = new Set(values.qualityGates ?? []);
        publishEvidence = {
          electronicSignature: {
            signatureId,
            signerId,
            signerName,
            signedAt: new Date(signedAt).toISOString(),
            signatureHash,
          },
          ...(platformPublishing
            ? {
                qualityGate: {
                  schemaValid: gates.has("schemaValid"),
                  terminologyBindingComplete: gates.has("terminologyBindingComplete"),
                  dependencyIntegrityVerified: gates.has("dependencyIntegrityVerified"),
                  safetyMonotonicityVerified: gates.has("safetyMonotonicityVerified"),
                  impactSimulationPassed: gates.has("impactSimulationPassed"),
                  peerReviewSigned: gates.has("peerReviewSigned"),
                  summary: values.qualitySummary?.trim() || undefined,
                },
              }
            : {}),
        };
      }
      await reviewMutation.mutateAsync({
        candidateId: classificationReviewId,
        packageVersion: values.packageVersion,
        request: {
          decision,
          reason: values.reason.trim(),
          feedbackType,
          followupAction,
          ...(publishEvidence ? { publishEvidence } : {}),
        },
        idempotencyKey: `knowledge-review-${classificationReviewId}-${decision.toLowerCase()}`,
      });
      message.success(REVIEW_DECISION_SUCCESS_MESSAGES[decision]);
      await Promise.all([identitiesQuery.refetch(), candidatesQuery.refetch()]);
    } catch (error) {
      message.error(getApiErrorMessage(error, "知识候选审核失败"));
    }
  }

  function requestCancelProductionJob(job: KnowledgeProductionJob) {
    modal.confirm({
      title: `中止生产任务 ${job.jobCode}`,
      content: "仅中止 PENDING/RUNNING job；已入审核的候选仍按治理链路留痕处理，不会伪造发布成功。",
      okText: "确认中止",
      cancelText: "取消",
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await cancelProductionJobMutation.mutateAsync(job.jobCode);
          message.success("生产任务已中止");
          await productionJobsQuery.refetch();
        } catch (error) {
          message.error(getApiErrorMessage(error, "中止生产任务失败"));
        }
      },
    });
  }

  async function submitProductionJob(values: CreateKnowledgeProductionJobRequest) {
    try {
      await createProductionJobMutation.mutateAsync({
        ...values,
        sourceScope: values.sourceScope.trim(),
        modelStrategy: values.modelStrategy?.trim() || undefined,
      });
      message.success("生产任务已创建，等待统一候选流水线处理");
      productionJobForm.resetFields(["sourceScope"]);
      await productionJobsQuery.refetch();
      await productionReadinessQuery.refetch();
    } catch (error) {
      message.error(getApiErrorMessage(error, "创建生产任务失败"));
    }
  }

  function openRetirement(identity: KnowledgeIdentity) {
    setRetirementIdentity(identity);
    setRetirementDraft(EMPTY_RETIREMENT_FORM);
    setSuccessorKeyword("");
  }

  async function scheduleRetirement() {
    if (!retirementIdentity) return;
    try {
      if (!retirementDraft.successorIdentityId) {
        message.error("请选择同域且已发布的后继知识身份");
        return;
      }
      if (!retirementDraft.migrationGuidance.trim()) {
        message.error("请填写面向引用方的迁移指引");
        return;
      }
      const gracePeriodEnd = new Date(retirementDraft.gracePeriodEnd);
      if (Number.isNaN(gracePeriodEnd.getTime()) || gracePeriodEnd.getTime() <= Date.now()) {
        message.error("宽限期结束时间必须晚于当前时间");
        return;
      }
      await retirementMutation.mutateAsync({
        identityId: retirementIdentity.id,
        successorIdentityId: retirementDraft.successorIdentityId,
        gracePeriodEnd: gracePeriodEnd.toISOString(),
        migrationGuidance: retirementDraft.migrationGuidance.trim(),
      });
      message.success("知识身份已进入迁移宽限期");
      setRetirementIdentity(undefined);
      setRetirementDraft(EMPTY_RETIREMENT_FORM);
      setSuccessorKeyword("");
      await identitiesQuery.refetch();
    } catch (error) {
      message.error(getApiErrorMessage(error, "安排知识弃用失败"));
    }
  }

  async function submitCustomization(values: {
    targetOrgUnitId: string;
    applicableScope: string;
    reason: string;
  }) {
    if (!customizeIdentity) return;
    try {
      await createCustomization.mutateAsync({
        platformIdentityId: customizeIdentity.id,
        targetOrgUnitId: values.targetOrgUnitId,
        applicableScope: values.applicableScope,
        reason: values.reason.trim(),
      });
      message.success("已从平台标准创建机构定制草稿");
      setCustomizeIdentity(undefined);
      customizeForm.resetFields();
    } catch (error) {
      message.error(getApiErrorMessage(error, "创建机构知识定制失败"));
    }
  }

  async function submitCustomizationAction(values: {
    reason: string;
    signatureId?: string;
    signerId?: string;
    signerName?: string;
    signedAt?: string;
    signatureHash?: string;
  }) {
    if (!customizationAction) return;
    try {
      if (customizationAction.type === "publish") {
        const requiresIndependentReview = customizationAction.item.riskLevel === "HIGH";
        let publishEvidence: VersionPublishEvidence | undefined;
        if (requiresIndependentReview) {
          const signatureId = values.signatureId?.trim();
          const signerId = values.signerId?.trim();
          const signerName = values.signerName?.trim();
          const signedAt = values.signedAt?.trim();
          const signatureHash = values.signatureHash?.trim();
          if (!signatureId || !signerId || !signerName || !signedAt || !signatureHash) {
            throw new Error("高风险知识发布必须填写完整电子签名");
          }
          publishEvidence = {
            electronicSignature: {
              signatureId,
              signerId,
              signerName,
              signedAt: new Date(signedAt).toISOString(),
              signatureHash,
            },
          };
        }
        await publishCustomization.mutateAsync({
          customizationId: customizationAction.item.customizationId,
          reason: values.reason.trim(),
          ...(publishEvidence ? { publishEvidence } : {}),
        });
        message.success("机构定制已发布并在目标组织生效");
      } else {
        await restorePlatformKnowledge.mutateAsync({
          customizationId: customizationAction.item.customizationId,
          reason: values.reason.trim(),
        });
        message.success("已恢复使用平台标准，历史定制继续保留");
      }
      setCustomizationAction(undefined);
      customizationActionForm.resetFields();
    } catch (error) {
      message.error(
        getApiErrorMessage(
          error,
          customizationAction.type === "publish" ? "发布机构定制失败" : "恢复平台标准失败",
        ),
      );
    }
  }

  function sourceTypeFor(identity: KnowledgeIdentity) {
    if (identity.tenantId === platformTenantId) return "PLATFORM_STANDARD";
    if (customizationItems.some((customization) => customization.localIdentityId === identity.id)) {
      return "LOCAL_CUSTOMIZATION";
    }
    return "LOCAL_ORIGINAL";
  }

  const platformStandardIdentities = useMemo(
    () =>
      identities.filter(
        (identity) => identity.tenantId === platformTenantId && identity.status === "ACTIVE",
      ),
    [identities],
  );

  const identityColumns: ColumnsType<KnowledgeIdentity> = [
    {
      title: "知识身份",
      dataIndex: "subject",
      key: "subject",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.subject}</Text>
          <Text type="secondary">{record.identityCode}</Text>
          <Tag color={record.tenantId === platformTenantId ? "blue" : "cyan"}>
            {knowledgeSourceLabel(sourceTypeFor(record))}
          </Tag>
        </Space>
      ),
    },
    {
      title: "领域 / 状态",
      key: "domain",
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag>{knowledgeDomainLabel(record.domain)}</Tag>
          <Tag color={record.status === "ACTIVE" ? "success" : "default"}>
            {lifecycleStatusLabel(record.status)}
          </Tag>
        </Space>
      ),
    },
    {
      title: "专科 / 当前版本",
      key: "scope",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.specialtyId || "未限定专科"}</Text>
          <Text type="secondary">统一版本按当前组织解析</Text>
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Space wrap>
          <Button
            aria-label="查看候选"
            type={record.id === selectedIdentityId ? "primary" : "default"}
            onClick={() => setSelectedIdentityId(record.id)}
          >
            查看候选
          </Button>
          {canScheduleRetirement && record.status === "ACTIVE" && (
            <Button
              aria-label={`安排弃用：${record.subject}`}
              icon={<SwapOutlined />}
              onClick={() => openRetirement(record)}
            >
              安排弃用
            </Button>
          )}
          {canCustomize && record.tenantId === platformTenantId && record.status === "ACTIVE" && (
            <Button
              icon={<BranchesOutlined />}
              onClick={() => {
                setCustomizeIdentity(record);
                customizeForm.setFieldsValue({ applicableScope: "ALL" });
              }}
            >
              定制为本机构版本
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const institutionIdentityColumns: ColumnsType<KnowledgeIdentity> = [
    {
      title: "平台标准",
      dataIndex: "subject",
      key: "subject",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.subject}</Text>
          <Text type="secondary">{record.identityCode}</Text>
          <Space size={4} wrap>
            <Tag color={PIPELINE_META.PLATFORM_SOURCE.color}>{PIPELINE_META.PLATFORM_SOURCE.label}</Tag>
            <Tag>{PIPELINE_META.PLATFORM_SOURCE.boundaryLabel}</Tag>
          </Space>
        </Space>
      ),
    },
    {
      title: "领域 / 风险",
      key: "domain",
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag>{knowledgeDomainLabel(record.domain)}</Tag>
          <Tag>{lifecycleStatusLabel(record.status)}</Tag>
        </Space>
      ),
    },
    {
      title: "适用范围",
      key: "scope",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.specialtyId || "未限定专科"}</Text>
          <Text type="secondary">机构版本继承平台证据链后单独审核发布</Text>
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) =>
        canCustomize ? (
          <Button
            icon={<BranchesOutlined />}
            onClick={() => {
              setCustomizeIdentity(record);
              customizeForm.setFieldsValue({ applicableScope: "ALL" });
            }}
          >
            定制为本机构版本
          </Button>
        ) : (
          <Text type="secondary">无机构定制权限</Text>
        ),
    },
  ];

  const candidateColumns: ColumnsType<KnowledgeAssetVersion> = [
    {
      title: "候选版本",
      key: "version",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{versionTitle(record)}</Text>
          <Text type="secondary">v{record.versionNo}</Text>
        </Space>
      ),
    },
    {
      title: "判定 / 审核",
      key: "classification",
      render: (_, record) => {
        const classification = classificationFor(classifications, record.id);
        return (
          <Space direction="vertical" size={2}>
            <Tag color={classification?.classification === "CONFLICT" ? "error" : "processing"}>
              {CLASSIFICATION_LABELS[classification?.classification ?? ""] ?? "未返回判定"}
            </Tag>
            <Tag color={tagColorForReview(classification?.reviewStatus)}>
              {REVIEW_STATUS_LABELS[classification?.reviewStatus ?? ""] ?? "未返回状态"}
            </Tag>
          </Space>
        );
      },
    },
    {
      title: "AI 来源",
      key: "provenance",
      render: (_, record) => {
        const provenance = provenanceFor(record);
        if (!provenance) {
          return <Text type="secondary">非工厂候选</Text>;
        }
        return (
          <Space direction="vertical" size={2}>
            {provenance.aiGenerated ? <Tag color="purple">AI 生成</Tag> : <Tag>人工生产</Tag>}
            {provenance.modelMode ? <Tag color="geekblue">{provenance.modelMode}</Tag> : null}
            <Text>{producerLabel(provenance.producer)}</Text>
            <Text type="secondary">job：{provenance.jobCode}</Text>
            {provenance.modelVersion ? (
              <Text type="secondary">模型：{provenance.modelVersion}</Text>
            ) : null}
            {provenance.confidence !== null && provenance.confidence !== undefined ? (
              <Text type="secondary">{confidenceText(provenance.confidence)}</Text>
            ) : null}
          </Space>
        );
      },
    },
    {
      title: "依据与差异",
      key: "basis",
      render: (_, record) => {
        const classification = classificationFor(classifications, record.id);
        return (
          <Space direction="vertical" size={0}>
            <Text>{classification?.basis ?? "未返回分类依据"}</Text>
            <Text type="secondary">{classification?.diffSummary ?? "未返回差异摘要"}</Text>
          </Space>
        );
      },
    },
    {
      title: "来源 / 风险",
      key: "source",
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Text>
            来源文献：{record.sourceDocumentId ?? "无"} / 来源版本：{" "}
            {record.sourceVersionId ?? "无"}
          </Text>
          <Space size={4} wrap>
            <Tag color={RISK_COLORS[record.riskLevel ?? ""] ?? "default"}>
              {riskLabel(record.riskLevel)}
            </Tag>
            <Tag>{sourceAuthorityLabel(record.authorityLevel)}</Tag>
          </Space>
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Button aria-label="查看审核对照" onClick={() => openCandidate(record)}>
          查看审核对照
        </Button>
      ),
    },
  ];

  const productionJobColumns: ColumnsType<KnowledgeProductionJob> = [
    {
      title: "生产任务",
      key: "job",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.jobCode}</Text>
          <Text type="secondary">{producerLabel(record.producer)}</Text>
        </Space>
      ),
    },
    {
      title: "管道 / 状态",
      key: "status",
      render: (_, record) => {
        const meta = pipelineMeta(record.targetPipeline);
        return (
          <Space direction="vertical" size={2}>
            <Space size={4} wrap>
              <Tag color={meta.color}>{meta.label}</Tag>
              <Tag>{meta.boundaryLabel}</Tag>
            </Space>
            <Tag color={productionStatusColor(record.status)}>
              {PRODUCTION_JOB_STATUS_LABELS[record.status] ?? record.status}
            </Tag>
          </Space>
        );
      },
    },
    {
      title: "领域 / 候选",
      key: "domain",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>{knowledgeDomainLabel(record.domain)}</Text>
          <Text type="secondary">候选 {record.candidateCount} 条</Text>
        </Space>
      ),
    },
    {
      title: "模型策略",
      dataIndex: "modelStrategy",
      render: (value?: string | null) => value || "未配置",
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Button
          type={record.jobCode === selectedProductionJobCode ? "primary" : "default"}
          onClick={() => setProductionJobCode(record.jobCode)}
        >
          查看生产证据
        </Button>
      ),
    },
  ];

  const productionCandidateColumns: ColumnsType<KnowledgeProductionCandidateView> = [
    {
      title: "候选引用",
      dataIndex: "candidateRef",
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: "资产身份 / hash",
      key: "identity",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.assetIdentity || "未返回身份"}</Text>
          <Text type="secondary">{record.contentHash || "未返回 hash"}</Text>
        </Space>
      ),
    },
    {
      title: "风险 / 会签",
      key: "routing",
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag color={RISK_COLORS[record.riskLevel ?? ""] ?? "default"}>
            {riskLabel(record.riskLevel)}
          </Tag>
          <Text type="secondary">
            {record.routing?.requiresDualSign ? "高风险双签" : "单签审核"}
          </Text>
        </Space>
      ),
    },
  ];

  const productionGateColumns: ColumnsType<AikGateResult> = [
    {
      title: "门禁",
      dataIndex: "gateCode",
    },
    {
      title: "结果",
      dataIndex: "passed",
      render: (passed: boolean) => (
        <Tag color={passed ? "success" : "error"}>{booleanGateLabel(passed)}</Tag>
      ),
    },
    {
      title: "原因",
      dataIndex: "reason",
      render: (value?: string | null) => value || "无",
    },
  ];

  const productionTriageColumns: ColumnsType<GenerationTriage> = [
    {
      title: "8 态",
      dataIndex: "triageState",
      render: (value: string) => (
        <Space size={4} wrap>
          <Tag
            color={
              KNOWLEDGE_TRIAGE_STATE_META.find((item) => item.state === value)?.color ??
              (value === "CONFLICT" ? "error" : "processing")
            }
          >
            {triageLabel(value)}
          </Tag>
          <Text type="secondary">{value}</Text>
        </Space>
      ),
    },
    {
      title: "动作",
      dataIndex: "action",
    },
    {
      title: "依据",
      dataIndex: "basis",
      render: (value?: string | null) => value || "未返回依据",
    },
  ];

  const productionShadowColumns: ColumnsType<KnowledgeShadowRun> = [
    {
      title: "状态",
      dataIndex: "status",
      render: (value: string) => <Tag color={productionStatusColor(value)}>{value}</Tag>,
    },
    {
      title: "样本",
      key: "cases",
      render: (_, record) => (
        <Text>
          {record.totalCases} 例 / 命中 {record.hitCount} / 误报 {record.falsePositiveCount} / 漏报{" "}
          {record.missCount}
        </Text>
      ),
    },
    {
      title: "裁决",
      key: "ready",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Tag color={record.readyForReview ? "success" : "error"}>
            {record.readyForReview ? "可提审" : "不可提审"}
          </Tag>
          <Text type="secondary">{record.basis || "未返回依据"}</Text>
        </Space>
      ),
    },
  ];

  let pageState: "loading" | "error" | "empty" | "ready" = "ready";
  if (identitiesQuery.isLoading) {
    pageState = "loading";
  } else if (identitiesQuery.isError) {
    pageState = "error";
  } else if (identities.length === 0) {
    pageState = "empty";
  }

  let pageStateProps;
  if (pageState === "loading") {
    pageStateProps = {
      title: "正在加载知识候选审核",
      description: "正在读取真实知识身份与候选审核队列。",
    };
  } else if (pageState === "error") {
    pageStateProps = {
      title: "知识审核数据读取失败",
      description: getApiErrorMessage(identitiesQuery.error, "无法读取知识审核数据"),
      onRetry: () => void identitiesQuery.refetch(),
    };
  } else if (pageState === "empty") {
    pageStateProps = {
      title: "当前筛选下暂无待审核知识身份",
      description: "知识候选经来源采集、去重和分流后展示；本页只负责审核与发布。",
    };
  }

  let candidatePanel: ReactNode;
  if (candidatesQuery.isLoading) {
    candidatePanel = <Alert type="info" showIcon message="正在读取所选知识身份的候选。" />;
  } else if (candidatesQuery.isError) {
    candidatePanel = (
      <Alert
        type="error"
        showIcon
        message="知识候选读取失败"
        description={getApiErrorMessage(candidatesQuery.error, "无法读取知识候选")}
      />
    );
  } else if (candidates.length === 0) {
    candidatePanel = (
      <Alert
        type="info"
        showIcon
        message="所选知识身份暂无待审候选"
        description="候选仅来自真实来源采集或治理流程分流；本页只负责审核与发布。"
      />
    );
  } else {
    candidatePanel = (
      <Table
        rowKey="id"
        columns={candidateColumns}
        dataSource={candidates}
        pagination={{
          current: candidatePageData?.page ?? candidatePage,
          pageSize: candidatePageData?.size ?? KNOWLEDGE_CANDIDATE_PAGE_SIZE,
          total: candidatePageData?.total ?? 0,
          showSizeChanger: false,
          onChange: setCandidatePage,
        }}
        size="middle"
      />
    );
  }

  const productionWorkbench = (
    <Card>
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Space direction="vertical" size={4}>
          <Title level={4} className="mk-title-tight">
            生产者工作台
          </Title>
          <Space size={[8, 8]} wrap>
            {["下任务", "看进度", "审候选", "影响", "结论"].map((step) => (
              <Tag key={step} color="processing">
                {step}
              </Tag>
            ))}
          </Space>
        </Space>
        <Divider className="mk-divider-tight" />
        <Title level={5}>下任务</Title>
        <Form<CreateKnowledgeProductionJobRequest>
          form={productionJobForm}
          layout="vertical"
          initialValues={{
            sourceScope: "",
            assetType: "KNOWLEDGE",
            producer: "API_MODEL",
            targetPipeline: defaultProductionTargetPipeline,
            domain: "GUIDELINE",
            modelStrategy: "gpt-pipeline",
          }}
          onFinish={submitProductionJob}
        >
          <Row gutter={[16, 0]}>
            <Col xs={24} lg={8}>
              <Form.Item
                label="来源范围"
                name="sourceScope"
                rules={[{ required: true, whitespace: true, message: "请填写真实来源范围" }]}
              >
                <Input placeholder="例如 acquisition-run:guideline-2026" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={4}>
              <Form.Item label="资产类型" name="assetType">
                <Select
                  options={[
                    { value: "KNOWLEDGE", label: "知识" },
                    { value: "RULE", label: "规则" },
                    { value: "PATHWAY", label: "路径" },
                    { value: "RECOMMENDATION", label: "推荐" },
                    { value: "METRIC", label: "指标" },
                    { value: "FOLLOWUP", label: "随访" },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={4}>
              <Form.Item label="生产器" name="producer">
                <Select
                  options={[
                    { value: "API_MODEL", label: "API 大模型" },
                    { value: "AGENT_TOOL", label: "Agent 工具" },
                    { value: "LOCAL_MODEL", label: "本地模型" },
                    { value: "MANUAL", label: "人工录入" },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={4}>
              <Form.Item label="目标管道" name="targetPipeline">
                <Select
                  options={[
                    { value: "PLATFORM_SOURCE", label: "平台主源" },
                    { value: "TENANT_OVERLAY", label: "院内覆盖" },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={4}>
              <Form.Item label="领域" name="domain">
                <Select options={KNOWLEDGE_DOMAIN_OPTIONS} />
              </Form.Item>
            </Col>
            <Col xs={24} lg={8}>
              <Form.Item label="模型策略" name="modelStrategy">
                <Input placeholder="B0 / 本地 / 外部策略标识" />
              </Form.Item>
            </Col>
            <Col xs={24} lg={8}>
              <Form.Item label="提交" colon={false}>
                <Button
                  type="primary"
                  htmlType="submit"
                  icon={<PlusOutlined />}
                  aria-label="创建生产任务"
                  disabled={!canWriteKnowledge}
                  loading={createProductionJobMutation.isPending}
                >
                  创建生产任务
                </Button>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Space>
    </Card>
  );
  const productionPipelinePartition = <PipelineBoundaryCard title="双形态生产分区" />;

  let productionCenterContent: ReactNode;
  if (productionReadinessQuery.isLoading || productionJobsQuery.isLoading) {
    productionCenterContent = (
      <Space direction="vertical" size="large" className="mk-full-width">
        {productionWorkbench}
        {productionPipelinePartition}
        <PageState state="loading" title="正在读取知识生产中心" />
      </Space>
    );
  } else if (productionReadinessQuery.isError || productionJobsQuery.isError) {
    productionCenterContent = (
      <Space direction="vertical" size="large" className="mk-full-width">
        {productionWorkbench}
        {productionPipelinePartition}
        <PageState
          state="error"
          title="知识生产中心读取失败"
          description={getApiErrorMessage(
            productionReadinessQuery.error ?? productionJobsQuery.error,
            "无法读取知识生产中心",
          )}
          onRetry={() => {
            void productionReadinessQuery.refetch();
            void productionJobsQuery.refetch();
          }}
        />
      </Space>
    );
  } else if (productionJobs.length === 0) {
    productionCenterContent = (
      <Space direction="vertical" size="large" className="mk-full-width">
        {productionWorkbench}
        {productionPipelinePartition}
        <Card title="模型生产 readiness">
          <Table
            rowKey="code"
            columns={[
              { title: "前置项", dataIndex: "code" },
              {
                title: "状态",
                dataIndex: "ready",
                render: (ready: boolean) => (
                  <Tag color={ready ? "success" : "error"}>{ready ? "满足" : "阻断"}</Tag>
                ),
              },
              { title: "说明", dataIndex: "message" },
              { title: "证据", dataIndex: "evidence" },
            ]}
            dataSource={productionReadinessQuery.data?.items ?? []}
            pagination={false}
            size="small"
          />
        </Card>
        <PageState
          state="empty"
          title="暂无生产 job"
          description="尚未有知识生产任务进入统一候选流水线。"
        />
      </Space>
    );
  } else {
    const readiness = productionReadinessQuery.data;
    const productionCandidates = productionCandidatesQuery.data ?? [];
    const productionGateResults = productionGateResultsQuery.data ?? [];
    const productionTriageResults = productionTriageResultsQuery.data ?? [];
    const productionShadowRuns = productionShadowRunsQuery.data ?? [];
    const coexistence = productionCoexistenceQuery.data;
    const selectedProductionCandidate =
      productionCandidates.find(
        (candidate) => candidate.candidateRef === selectedProductionCandidateRef,
      ) ?? productionCandidates[0];
    const selectedProductionBatch = selectedProductionCandidate ? [selectedProductionCandidate] : [];
    const batchApprovalLocked = selectedProductionBatch.some(
      (candidate) => candidate.riskLevel === "HIGH" || candidate.routing?.requiresDualSign,
    );
    const productionEvidenceErrors = [
      productionCandidatesQuery.isError
        ? `候选血缘：${getApiErrorMessage(productionCandidatesQuery.error, "候选血缘读取失败")}`
        : null,
      productionGateResultsQuery.isError
        ? `门禁结果：${getApiErrorMessage(productionGateResultsQuery.error, "门禁结果读取失败")}`
        : null,
      productionTriageResultsQuery.isError
        ? `8 态分流：${getApiErrorMessage(productionTriageResultsQuery.error, "8 态分流读取失败")}`
        : null,
      productionShadowRunsQuery.isError
        ? `影子评测：${getApiErrorMessage(productionShadowRunsQuery.error, "影子评测读取失败")}`
        : null,
      productionCoexistenceQuery.isError
        ? `共存替换提醒：${getApiErrorMessage(productionCoexistenceQuery.error, "共存替换提醒读取失败")}`
        : null,
    ].filter((message): message is string => Boolean(message));
    const triageCounts = new Map<string, number>();
    productionTriageResults.forEach((row) => {
      triageCounts.set(row.triageState, (triageCounts.get(row.triageState) ?? 0) + 1);
    });
    const productionProgress = productionProgressPercent(
      selectedProductionJob,
      productionCandidates.length,
      productionGateResults.length,
      productionTriageResults.length,
      productionShadowRuns.length,
    );
    const selectedJobCanBeCancelled =
      canWriteKnowledge &&
      selectedProductionJob !== undefined &&
      canCancelProductionJob(selectedProductionJob.status);
    let productionCandidateLineageContent: ReactNode;
    if (productionCandidatesQuery.isLoading) {
      productionCandidateLineageContent = <PageState state="loading" title="正在读取候选血缘" />;
    } else if (productionCandidatesQuery.isError) {
      productionCandidateLineageContent = (
        <PageState
          state="error"
          title="候选血缘读取失败"
          description={getApiErrorMessage(productionCandidatesQuery.error, "无法读取候选血缘")}
        />
      );
    } else {
      productionCandidateLineageContent = (
        <Table
          rowKey="candidateRef"
          columns={productionCandidateColumns}
          dataSource={productionCandidates}
          rowSelection={{
            type: "radio",
            selectedRowKeys: selectedProductionCandidate
              ? [selectedProductionCandidate.candidateRef]
              : [],
            onChange: (keys) => setProductionCandidateRef(String(keys[0])),
          }}
          pagination={false}
          size="small"
        />
      );
    }
    productionCenterContent = (
      <Space direction="vertical" size="large" className="mk-full-width">
        {productionWorkbench}
        {productionPipelinePartition}
        <Card title="模型生产 readiness">
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Alert
              type={readiness?.ready ? "success" : "warning"}
              showIcon
              message={readiness?.ready ? "模型生产前置已满足" : "模型生产前置仍有阻断"}
              description={
                readiness?.modelInvocationAllowed
                  ? "模型生产器可进入候选生产，但候选仍必须走门禁、评测、分流和审核。"
                  : "readiness 未通过时不得调用外部模型或伪造候选。"
              }
            />
            <Table
              rowKey="code"
              columns={[
                { title: "前置项", dataIndex: "code" },
                {
                  title: "状态",
                  dataIndex: "ready",
                  render: (ready: boolean) => (
                    <Tag color={ready ? "success" : "error"}>{ready ? "满足" : "阻断"}</Tag>
                  ),
                },
                { title: "说明", dataIndex: "message" },
                {
                  title: "证据",
                  dataIndex: "evidence",
                  render: (value?: string | null) => value || "无",
                },
              ]}
              dataSource={readiness?.items ?? []}
              pagination={false}
              size="small"
            />
          </Space>
        </Card>

        <Card title="生产 job">
          <Space direction="vertical" size="middle" className="mk-full-width">
            {selectedProductionJob ? (
              <Alert
                type={selectedProductionJob.status === "RUNNING" ? "info" : "warning"}
                showIcon
                message={
                  selectedProductionJob.producer === "AGENT_TOOL"
                    ? "Agent 进度与中止"
                    : "生产进度与中止"
                }
                description={
                  <Space direction="vertical" size={4}>
                    <Space size="middle" wrap>
                      <Text>生成候选 {selectedProductionJob.candidateCount} 条</Text>
                      <Text>门禁 {productionGateResults.length} 项</Text>
                      <Text>8 态 {productionTriageResults.length} 条</Text>
                      <Text>影子评测 {productionShadowRuns.length} 次</Text>
                    </Space>
                    <Progress
                      percent={productionProgress}
                      size="small"
                      status={selectedProductionJob.status === "FAILED" ? "exception" : "active"}
                    />
                  </Space>
                }
                action={
                  selectedJobCanBeCancelled ? (
                    <Button
                      danger
                      aria-label="中止生产任务"
                      icon={<StopOutlined />}
                      loading={cancelProductionJobMutation.isPending}
                      onClick={() => requestCancelProductionJob(selectedProductionJob)}
                    >
                      中止生产任务
                    </Button>
                  ) : undefined
                }
              />
            ) : null}
            <Table
              rowKey="jobCode"
              columns={productionJobColumns}
              dataSource={productionJobs}
              pagination={false}
              size="middle"
            />
          </Space>
        </Card>

        {productionEvidenceErrors.length > 0 ? (
          <Alert
            type="warning"
            showIcon
            message="生产证据部分读取失败"
            description={
              <Space direction="vertical" size={0}>
                {productionEvidenceErrors.map((message) => (
                  <Text key={message}>{message}</Text>
                ))}
              </Space>
            }
          />
        ) : null}

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={12}>
            <Card title="候选血缘">{productionCandidateLineageContent}</Card>
          </Col>
          <Col xs={24} xl={12}>
            <Card title="门禁结果">
              <Table
                rowKey={(record) => `${record.gateCode}-${record.contentHash ?? ""}`}
                columns={productionGateColumns}
                dataSource={productionGateResults}
                loading={productionGateResultsQuery.isLoading}
                pagination={false}
                size="small"
              />
            </Card>
          </Col>
          <Col xs={24} xl={12}>
            <Card title="8 态分流">
              <Space direction="vertical" size="middle" className="mk-full-width">
                <Text strong>8 态队列</Text>
                <Space size={[8, 8]} wrap>
                  {KNOWLEDGE_TRIAGE_STATE_META.map((item) => (
                    <Space key={item.state} size={4}>
                      <Text>{item.label}</Text>
                      <Tag color={item.color}>{triageCounts.get(item.state) ?? 0}</Tag>
                    </Space>
                  ))}
                </Space>
                <Table
                  rowKey={(record) => `${record.triageState}-${record.contentHash ?? ""}`}
                  columns={productionTriageColumns}
                  dataSource={productionTriageResults}
                  loading={productionTriageResultsQuery.isLoading}
                  pagination={false}
                  size="small"
                />
              </Space>
            </Card>
          </Col>
          <Col xs={24} xl={12}>
            <Card title="影子评测">
              <Table
                rowKey={(record) => `${record.status}-${record.contentHash ?? ""}`}
                columns={productionShadowColumns}
                dataSource={productionShadowRuns}
                loading={productionShadowRunsQuery.isLoading}
                pagination={false}
                size="small"
              />
            </Card>
          </Col>
        </Row>

        <Card title="共存替换提醒">
          {selectedProductionJob ? (
            <Space direction="vertical" size="middle" className="mk-full-width">
              <Descriptions column={1} bordered size="small">
                <Descriptions.Item label="当前 job">
                  {selectedProductionJob.jobCode}
                </Descriptions.Item>
                <Descriptions.Item label="候选执行状态">
                  <Tag color={coexistence?.candidateExecutable ? "success" : "error"}>
                    {coexistence?.candidateExecutable ? "候选可执行" : "候选不可执行"}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="现行权威状态">
                  <Tag color={coexistence?.activeExecutable ? "success" : "warning"}>
                    {coexistence?.activeExecutable ? "现行权威继续执行" : "暂无现行权威"}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="审核状态">
                  {coexistence?.reviewStatus ?? "未返回审核状态"}
                </Descriptions.Item>
                <Descriptions.Item label="差异摘要">
                  {coexistence?.diffSummary ?? "未返回差异摘要"}
                </Descriptions.Item>
                <Descriptions.Item label="替换提醒">
                  {coexistence?.replacementReminder ?? "未返回替换提醒"}
                </Descriptions.Item>
                <Descriptions.Item label="安全说明">
                  {coexistence?.safetyNotice ?? "候选仅供审核，不参与临床执行。"}
                </Descriptions.Item>
              </Descriptions>
              <Row gutter={[16, 16]}>
                <Col xs={24} lg={12}>
                  <Space direction="vertical" size="small" className="mk-full-width">
                    <Title level={5}>待审候选版本</Title>
                    <Descriptions column={1} bordered size="small">
                      <Descriptions.Item label="版本">
                        {snapshotValue(coexistence?.candidateVersion, "versionNo")}
                      </Descriptions.Item>
                      <Descriptions.Item label="状态">
                        {snapshotValue(coexistence?.candidateVersion, "status")}
                      </Descriptions.Item>
                      <Descriptions.Item label="风险 / 权威">
                        <Space size={4} wrap>
                          <Tag
                            color={
                              RISK_COLORS[coexistence?.candidateVersion?.riskLevel ?? ""] ??
                              "default"
                            }
                          >
                            {riskLabel(coexistence?.candidateVersion?.riskLevel)}
                          </Tag>
                          <Tag>
                            {sourceAuthorityLabel(coexistence?.candidateVersion?.authorityLevel)}
                          </Tag>
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label="hash">
                        {snapshotValue(coexistence?.candidateVersion, "contentHash")}
                      </Descriptions.Item>
                      <Descriptions.Item label="适用域">
                        {snapshotValue(coexistence?.candidateVersion, "applicableScope")}
                      </Descriptions.Item>
                    </Descriptions>
                  </Space>
                </Col>
                <Col xs={24} lg={12}>
                  <Space direction="vertical" size="small" className="mk-full-width">
                    <Title level={5}>现行权威版本</Title>
                    <Descriptions column={1} bordered size="small">
                      <Descriptions.Item label="版本">
                        {snapshotValue(coexistence?.activeVersion, "versionNo")}
                      </Descriptions.Item>
                      <Descriptions.Item label="状态">
                        {snapshotValue(coexistence?.activeVersion, "status")}
                      </Descriptions.Item>
                      <Descriptions.Item label="风险 / 权威">
                        <Space size={4} wrap>
                          <Tag
                            color={
                              RISK_COLORS[coexistence?.activeVersion?.riskLevel ?? ""] ?? "default"
                            }
                          >
                            {riskLabel(coexistence?.activeVersion?.riskLevel)}
                          </Tag>
                          <Tag>
                            {sourceAuthorityLabel(coexistence?.activeVersion?.authorityLevel)}
                          </Tag>
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label="hash">
                        {snapshotValue(coexistence?.activeVersion, "contentHash")}
                      </Descriptions.Item>
                      <Descriptions.Item label="适用域">
                        {snapshotValue(coexistence?.activeVersion, "applicableScope")}
                      </Descriptions.Item>
                    </Descriptions>
                  </Space>
                </Col>
              </Row>
              <Alert
                type="warning"
                showIcon
                message="审后任务化提醒"
                description={reviewTaskReminder(coexistence)}
              />
            </Space>
          ) : (
            <PageState state="empty" title="未选择生产 job" />
          )}
        </Card>

        <Card title="结论">
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Space size="middle" wrap>
              <Tag color={selectedProductionBatch.length > 0 ? "processing" : "default"}>
                批处置候选 {selectedProductionBatch.length} 条
              </Tag>
              {batchApprovalLocked ? (
                <Tag color="error">高风险或双签候选必须逐条进入审核台确认</Tag>
              ) : (
                <Tag color="success">低风险候选可进入批处置预备</Tag>
              )}
            </Space>
            <Space size="small" wrap>
              <Button
                type="primary"
                disabled={
                  batchApprovalLocked || selectedProductionBatch.length === 0 || !canPublishKnowledge
                }
                aria-label={
                  batchApprovalLocked ? "批量通过候选（高风险已锁定）" : "批量通过候选"
                }
              >
                {batchApprovalLocked ? "批量通过候选（高风险已锁定）" : "批量通过候选"}
              </Button>
              <Button disabled={selectedProductionBatch.length === 0}>转审核台逐条处理</Button>
            </Space>
            <Text type="secondary">
              生产面只汇总候选批次、影响和处置预案；最终通过、退修、驳回仍由审核台按来源、双签和发布证据执行。
            </Text>
          </Space>
        </Card>
      </Space>
    );
  }

  const customizationColumns: ColumnsType<KnowledgeCustomization> = [
    {
      title: "知识来源",
      key: "source",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Space size={4} wrap>
            <Tag color={PIPELINE_META.TENANT_OVERLAY.color}>{PIPELINE_META.TENANT_OVERLAY.label}</Tag>
            <Tag>{PIPELINE_META.TENANT_OVERLAY.boundaryLabel}</Tag>
          </Space>
          <Tag color="cyan">{knowledgeSourceLabel(record.sourceType)}</Tag>
          <Text type="secondary">基于平台版本 {record.platformVersionNo}</Text>
          <Tag color={RISK_COLORS[record.riskLevel] ?? "default"}>
            {riskLabel(record.riskLevel)}
          </Tag>
        </Space>
      ),
    },
    {
      title: "生效机构",
      key: "organization",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.targetOrganizationName}</Text>
          <Text type="secondary">
            {record.applicableScope === "ALL" ? "全部适用人群" : "限定适用范围"}
          </Text>
        </Space>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <Tag color={customizationStatusColor(value)}>
            {knowledgeCustomizationStatusLabel(value)}
          </Tag>
          {record.platformUpdateAvailable && <Tag color="warning">平台已有新版</Tag>}
        </Space>
      ),
    },
    {
      title: "定制原因",
      dataIndex: "reason",
      render: (value: string | null) => value || "未填写",
    },
    {
      title: "操作",
      key: "actions",
      render: (_, record) => (
        <Space wrap>
          {record.status === "DRAFT" && canPublishCustomization && (
            <Button
              type="primary"
              onClick={() => setCustomizationAction({ type: "publish", item: record })}
            >
              发布机构版本
            </Button>
          )}
          {record.status === "ACTIVE" && canRestoreCustomization && (
            <Button onClick={() => setCustomizationAction({ type: "restore", item: record })}>
              恢复平台标准
            </Button>
          )}
          {record.status === "RESTORED" && <Text type="secondary">历史保留</Text>}
        </Space>
      ),
    },
  ];

  let customizationListContent: ReactNode;
  if (customizationsQuery.isLoading) {
    customizationListContent = <PageState state="loading" title="正在读取机构知识" />;
  } else if (customizationsQuery.isError) {
    customizationListContent = (
      <PageState
        state="error"
        title="机构知识读取失败"
        action={<Button onClick={() => customizationsQuery.refetch()}>重试</Button>}
      />
    );
  } else {
    customizationListContent = (
      <Card title="机构知识血缘">
        <Table
          rowKey="customizationId"
          columns={customizationColumns}
          dataSource={customizationItems}
          locale={{ emptyText: "当前机构全部使用平台标准" }}
          pagination={{
            current: customizationsQuery.data?.page ?? customizationPage,
            pageSize: customizationsQuery.data?.size ?? KNOWLEDGE_CUSTOMIZATION_PAGE_SIZE,
            total: customizationsQuery.data?.total ?? 0,
            showSizeChanger: false,
            hideOnSinglePage: true,
            onChange: (page) => setCustomizationPage(page),
          }}
        />
      </Card>
    );
  }

  let institutionKnowledgeContent: ReactNode;
  if (isPlatformTenant) {
    institutionKnowledgeContent = (
      <Alert
        type="info"
        showIcon
        message="当前位于平台治理空间"
        description="平台负责维护权威标准；机构定制、发布和恢复操作在对应医疗机构空间内完成。"
      />
    );
  } else {
    institutionKnowledgeContent = (
      <Space direction="vertical" size="large" className="mk-full-width">
        <Alert
          type="info"
          showIcon
          message="默认复用平台标准，只有确需调整时才创建机构版本"
          description="机构定制会复制当前平台版本及完整证据链；发布后只影响所选组织及其继承范围，随时可以恢复平台标准。"
        />
        <PipelineBoundaryCard />
        <Card title="平台标准知识">
          <Table
            rowKey="id"
            columns={institutionIdentityColumns}
            dataSource={platformStandardIdentities}
            locale={{ emptyText: "当前筛选下暂无可派生的平台标准" }}
            pagination={{
              current: identityPage,
              pageSize: identitiesQuery.data?.size ?? 20,
              total: identitiesQuery.data?.total ?? 0,
              showSizeChanger: false,
              hideOnSinglePage: true,
              onChange: setIdentityPage,
            }}
            size="middle"
          />
        </Card>
        {customizationListContent}
      </Space>
    );
  }

  const reviewContent = (
    <Space direction="vertical" size="large" className="mk-full-width">
      <Card title="默认筛选">
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Select
              aria-label="知识域"
              className="mk-full-width"
              value={domain}
              options={KNOWLEDGE_DOMAIN_OPTIONS}
              onChange={(value) => {
                setDomain(value);
                setIdentityPage(1);
              }}
            />
          </Col>
          <Col xs={24} md={8}>
            <Select
              aria-label="身份状态"
              className="mk-full-width"
              value={status}
              options={KNOWLEDGE_IDENTITY_STATUS_OPTIONS}
              onChange={(value) => {
                setStatus(value);
                setIdentityPage(1);
              }}
            />
          </Col>
          <Col xs={24} md={8}>
            <Input.Search
              aria-label="知识关键词"
              placeholder="按主题或编码搜索"
              allowClear
              onSearch={(value) => {
                setKeyword(value);
                setIdentityPage(1);
              }}
            />
          </Col>
        </Row>
      </Card>

      {pageState === "ready" ? (
        <>
          <Card>
            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Statistic title="待审核候选总数" value={pendingCount} prefix={<AuditOutlined />} />
              </Col>
              <Col xs={24} md={8}>
                <Statistic title="冲突候选" value={conflictCount} />
              </Col>
              <Col xs={24} md={8}>
                <Statistic title="高风险候选" value={highRiskCount} />
              </Col>
            </Row>
          </Card>

          <Card title="知识身份台账">
            <Table
              rowKey="id"
              columns={identityColumns}
              dataSource={identities}
              pagination={{
                current: identityPage,
                pageSize: identitiesQuery.data?.size ?? 20,
                total: identitiesQuery.data?.total ?? 0,
                showSizeChanger: false,
                onChange: setIdentityPage,
              }}
              size="middle"
            />
          </Card>

          <Card title="待审候选">{candidatePanel}</Card>
        </>
      ) : (
        <PageState state={pageState} {...pageStateProps} />
      )}
    </Space>
  );

  let customizationActionMessage = "恢复后新请求将重新使用平台标准";
  if (customizationAction?.type === "publish") {
    customizationActionMessage =
      customizationAction.item.riskLevel === "HIGH"
        ? "高风险知识必须完成电子签名"
        : "发布后将接管所选机构的知识解析";
  }

  const pageMeta = {
    review: {
      title: "知识审核与发布",
      description: "统一审核知识候选、发布结论和替换恢复",
    },
    institution: {
      title: "机构知识",
      description: "维护院内覆盖、机构定制、换基线和恢复平台标准",
    },
    production: {
      title: "知识生产",
      description: "核查生产流水线 readiness、job、门禁、8 态分流和影子证据",
    },
  }[mode];

  let pageContent: ReactNode = reviewContent;
  if (mode === "institution") {
    pageContent = institutionKnowledgeContent;
  } else if (mode === "production") {
    pageContent = productionCenterContent;
  }

  let pageExtras: ReactNode = (
    <Button
      aria-label="刷新知识审核与发布"
      icon={<ReloadOutlined />}
      onClick={() => {
        void identitiesQuery.refetch();
        void candidatesQuery.refetch();
      }}
    >
      刷新
    </Button>
  );
  if (mode === "institution") {
    pageExtras = (
      <Button
        aria-label="刷新机构知识"
        icon={<ReloadOutlined />}
        onClick={() => void customizationsQuery.refetch()}
      >
        刷新
      </Button>
    );
  } else if (mode === "production") {
    pageExtras = (
      <Button
        aria-label="刷新知识生产"
        icon={<ReloadOutlined />}
        onClick={() => {
          void productionReadinessQuery.refetch();
          void productionJobsQuery.refetch();
        }}
      >
        刷新
      </Button>
    );
  }

  return (
    <>
      <PageShell title={pageMeta.title} description={pageMeta.description} extras={pageExtras}>
        {pageContent}
      </PageShell>

      <Modal
        title={`定制机构知识${customizeIdentity ? ` · ${customizeIdentity.subject}` : ""}`}
        open={Boolean(customizeIdentity)}
        okText="创建定制草稿"
        cancelText="取消"
        confirmLoading={createCustomization.isPending}
        onOk={() => customizeForm.submit()}
        onCancel={() => {
          setCustomizeIdentity(undefined);
          customizeForm.resetFields();
        }}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          message="平台标准保持不变"
          description="系统将复制当前平台版本、来源文献和引用证据，形成可独立审核的机构草稿。"
        />
        <Form
          form={customizeForm}
          layout="vertical"
          initialValues={{ applicableScope: "ALL" }}
          onFinish={submitCustomization}
        >
          <Form.Item
            name="targetOrgUnitId"
            label="生效机构"
            rules={[{ required: true, message: "请选择生效机构" }]}
          >
            <OrgUnitSelect scope="BUSINESS_SCOPE" placeholder="从组织树选择" />
          </Form.Item>
          <Form.Item name="applicableScope" label="适用人群" rules={[{ required: true }]}>
            <Select options={[{ value: "ALL", label: "全部适用人群" }]} />
          </Form.Item>
          <Form.Item
            name="reason"
            label="定制原因"
            rules={[
              { required: true, whitespace: true, message: "请说明为什么需要机构定制" },
              { min: 4, message: "定制原因至少 4 个字符" },
            ]}
          >
            <Input.TextArea rows={4} maxLength={1000} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={customizationAction?.type === "publish" ? "发布机构知识" : "恢复平台标准"}
        open={Boolean(customizationAction)}
        okText={customizationAction?.type === "publish" ? "确认发布" : "确认恢复"}
        cancelText="取消"
        okButtonProps={{ danger: customizationAction?.type === "restore" }}
        confirmLoading={publishCustomization.isPending || restorePlatformKnowledge.isPending}
        onOk={() => customizationActionForm.submit()}
        onCancel={() => {
          setCustomizationAction(undefined);
          customizationActionForm.resetFields();
        }}
        destroyOnClose
      >
        <Alert
          type={customizationAction?.type === "publish" ? "warning" : "info"}
          showIcon
          message={customizationActionMessage}
          description={
            customizationAction?.type === "publish" && customizationAction.item.riskLevel === "HIGH"
              ? "当前登录人负责发布，电子签名必须由另一位具备资质的复核人完成；历史版本、证据、差异和审计记录都会保留。"
              : "历史版本、证据、差异和审计记录都会保留。"
          }
        />
        <Form form={customizationActionForm} layout="vertical" onFinish={submitCustomizationAction}>
          <Form.Item
            name="reason"
            label={customizationAction?.type === "publish" ? "发布依据" : "恢复原因"}
            rules={[
              { required: true, whitespace: true, message: "请填写完整原因" },
              { min: 4, message: "原因至少 4 个字符" },
            ]}
          >
            <Input.TextArea rows={4} maxLength={1000} showCount />
          </Form.Item>
          {customizationAction?.type === "publish" &&
            customizationAction.item.riskLevel === "HIGH" && (
              <>
                <Divider orientation="left">独立复核签名</Divider>
                <Row gutter={12}>
                  <Col xs={24} md={12}>
                    <Form.Item
                      name="signatureId"
                      label="签名编号"
                      rules={[{ required: true, whitespace: true, message: "请填写签名编号" }]}
                    >
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item
                      name="signedAt"
                      label="签名时间"
                      rules={[{ required: true, message: "请选择签名时间" }]}
                    >
                      <Input type="datetime-local" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item
                      name="signerId"
                      label="复核人工号"
                      rules={[{ required: true, whitespace: true, message: "请填写复核人工号" }]}
                    >
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item
                      name="signerName"
                      label="复核人姓名"
                      rules={[{ required: true, whitespace: true, message: "请填写复核人姓名" }]}
                    >
                      <Input />
                    </Form.Item>
                  </Col>
                </Row>
                <Form.Item
                  name="signatureHash"
                  label="签名摘要"
                  extra="由院内电子签名服务生成的 64 位小写 SHA-256 摘要"
                  rules={[
                    { required: true, message: "请填写签名摘要" },
                    {
                      pattern: /^[0-9a-f]{64}$/,
                      message: "签名摘要必须是 64 位小写 SHA-256",
                    },
                  ]}
                >
                  <Input />
                </Form.Item>
              </>
            )}
        </Form>
      </Modal>

      <Drawer
        title="知识候选审核对照"
        open={Boolean(selectedCandidateId)}
        width={720}
        onClose={() => setSelectedCandidateId(undefined)}
      >
        <Space direction="vertical" size="large" className="mk-full-width">
          <Alert
            type={selectedClassification?.classification === "CONFLICT" ? "warning" : "info"}
            showIcon
            message={diffQuery.data?.message ?? candidateResponse?.message ?? "候选对照已读取"}
            description={selectedClassification?.diffSummary}
          />

          <Descriptions column={1} bordered size="small" title="知识身份">
            <Descriptions.Item label="主题">
              {selectedIdentity?.subject ?? "未选择"}
            </Descriptions.Item>
            <Descriptions.Item label="编码">
              {selectedIdentity?.identityCode ?? "未返回编码"}
            </Descriptions.Item>
            <Descriptions.Item label="分类依据">
              {selectedClassification?.basis ?? "未返回分类依据"}
            </Descriptions.Item>
          </Descriptions>

          <Descriptions column={1} bordered size="small" title="现行权威版本">
            <Descriptions.Item label="版本">{versionTitle(activeVersion)}</Descriptions.Item>
            <Descriptions.Item label="状态">{versionSubtitle(activeVersion)}</Descriptions.Item>
            <Descriptions.Item label="contentHash">
              {activeVersion?.contentHash ?? "未返回摘要"}
            </Descriptions.Item>
          </Descriptions>
          <SourceInfo
            sourceDocumentId={activeVersion?.sourceDocumentId}
            sourceVersionId={activeVersion?.sourceVersionId}
            authorityLevel={activeVersion?.authorityLevel}
            anchors={activeVersion?.anchors}
            reviewedBy={activeVersion?.reviewedBy}
            reviewedAt={activeVersion?.reviewedAt}
          />

          <Descriptions column={1} bordered size="small" title="待审候选版本">
            <Descriptions.Item label="版本">{versionTitle(candidateVersion)}</Descriptions.Item>
            <Descriptions.Item label="状态">{versionSubtitle(candidateVersion)}</Descriptions.Item>
            <Descriptions.Item label="contentHash">
              {candidateVersion?.contentHash ?? "未返回摘要"}
            </Descriptions.Item>
            <Descriptions.Item label="替换策略">
              {candidateVersion?.conflictArbitration ?? "未返回替换策略"}
            </Descriptions.Item>
          </Descriptions>
          <SourceInfo
            sourceDocumentId={candidateVersion?.sourceDocumentId}
            sourceVersionId={candidateVersion?.sourceVersionId}
            authorityLevel={candidateVersion?.authorityLevel}
            anchors={candidateVersion?.anchors}
            reviewedBy={candidateVersion?.reviewedBy}
            reviewedAt={candidateVersion?.reviewedAt}
          />

          {(() => {
            const provenance = provenanceFor(candidateVersion);
            if (!provenance) {
              return null;
            }
            const meta = pipelineMeta(provenance.targetPipeline);
            return (
              <Descriptions column={1} bordered size="small" title="AI 生产来源溯源">
                <Descriptions.Item label="AI 标识">
                  {provenance.aiGenerated ? <Tag color="purple">AI 生成</Tag> : <Tag>人工生产</Tag>}
                </Descriptions.Item>
                <Descriptions.Item label="生产器">
                  {producerLabel(provenance.producer)}
                </Descriptions.Item>
                <Descriptions.Item label="生产任务 job">{provenance.jobCode}</Descriptions.Item>
                <Descriptions.Item label="目标管道">
                  <Space size={4} wrap>
                    <Tag color={meta.color}>{meta.label}</Tag>
                    <Tag>{meta.boundaryLabel}</Tag>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="模型策略">
                  {provenance.modelStrategy || "无"}
                </Descriptions.Item>
                <Descriptions.Item label="模型模式">
                  {provenance.modelMode || "未返回"}
                </Descriptions.Item>
                <Descriptions.Item label="模型版本">
                  {provenance.modelVersion || "未返回"}
                </Descriptions.Item>
                <Descriptions.Item label="提示词版本">
                  {provenance.promptVersion || "未返回"}
                </Descriptions.Item>
                <Descriptions.Item label="工具版本">
                  {provenance.toolVersion || "未返回"}
                </Descriptions.Item>
                <Descriptions.Item label="置信 / 降级">
                  <Space direction="vertical" size={2}>
                    <Text>{confidenceText(provenance.confidence)}</Text>
                    <Text>{fallbackText(provenance)}</Text>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="来源引用">
                  {provenance.sourceCitations || "未返回来源引用"}
                </Descriptions.Item>
                <Descriptions.Item label="生产时点 / 人">
                  {provenance.producedAt ?? "未返回"} / {provenance.producedBy ?? "未返回"}
                </Descriptions.Item>
              </Descriptions>
            );
          })()}

          {domainTemplate ? (
            <Descriptions
              column={1}
              bordered
              size="small"
              title={`专业标准模板 · ${domainTemplate.displayName}`}
            >
              {domainTemplate.sections.map((section) => (
                <Descriptions.Item key={section.key} label={section.label}>
                  <Space size="small">
                    {section.required ? <Tag color="red">必备</Tag> : <Tag>建议</Tag>}
                    <span>{section.hint}</span>
                  </Space>
                </Descriptions.Item>
              ))}
            </Descriptions>
          ) : (
            <Alert
              type="info"
              showIcon
              message="该领域暂无标准模板"
              description="审核人按来源与现行版本对照核查，不臆造结构。"
            />
          )}

          <Form
            form={reviewForm}
            layout="vertical"
            initialValues={{
              packageVersion: defaultReviewPackageVersion,
              reason: "",
              qualityGates: [],
            }}
          >
            <Form.Item
              name="packageVersion"
              label="审核上下文包版本"
              rules={[{ required: true, message: "请选择审核上下文包版本" }]}
            >
              <Select
                showSearch
                filterOption={false}
                onSearch={setReviewPackageSearch}
                placeholder="选择已存在的知识配置包版本"
                options={reviewPackageOptions}
                loading={knowledgePackagesQuery.isLoading}
                notFoundContent={
                  knowledgePackagesQuery.isError ? "配置包版本读取失败" : "暂无知识配置包版本"
                }
              />
            </Form.Item>
            <Form.Item
              name="reason"
              label="审核理由"
              rules={[{ required: true, message: "请填写审核理由" }]}
            >
              <Input.TextArea rows={4} />
            </Form.Item>
            <Form.Item name="feedbackType" label="审核反馈类型">
              <Radio.Group>
                <Space wrap>
                  <Radio value="CONTENT_GAP">内容缺口</Radio>
                  <Radio value="SOURCE_BLANK">来源空白</Radio>
                  <Radio value="FALSE_POSITIVE">误报</Radio>
                  <Radio value="NOT_ADOPTED">不采纳</Radio>
                </Space>
              </Radio.Group>
            </Form.Item>
            {publishEvidenceRequired && (
              <>
                <Divider orientation="left">发布签名</Divider>
                <Row gutter={12}>
                  <Col xs={24} md={12}>
                    <Form.Item
                      name="signatureId"
                      label="签名 ID"
                      rules={[{ required: true, message: "请填写签名 ID" }]}
                    >
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item
                      name="signedAt"
                      label="签名时间"
                      rules={[{ required: true, message: "请选择签名时间" }]}
                    >
                      <Input type="datetime-local" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item
                      name="signerId"
                      label="签名人 ID"
                      rules={[{ required: true, message: "请填写签名人 ID" }]}
                    >
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item
                      name="signerName"
                      label="签名人姓名"
                      rules={[{ required: true, message: "请填写签名人姓名" }]}
                    >
                      <Input />
                    </Form.Item>
                  </Col>
                </Row>
                <Form.Item
                  name="signatureHash"
                  label="签名摘要"
                  rules={[
                    { required: true, message: "请填写签名摘要" },
                    {
                      pattern: /^[0-9a-f]{64}$/,
                      message: "签名摘要必须是 64 位小写 SHA-256",
                    },
                  ]}
                >
                  <Input />
                </Form.Item>
              </>
            )}
            {platformPublishing && (
              <>
                <Divider orientation="left">平台发布质量门</Divider>
                <Form.Item
                  name="qualityGates"
                  label="质量门"
                  rules={[
                    {
                      validator: (_, value?: string[]) =>
                        value?.length === KNOWLEDGE_QUALITY_GATE_OPTIONS.length
                          ? Promise.resolve()
                          : Promise.reject(new Error("请确认全部平台发布质量门")),
                    },
                  ]}
                >
                  <Checkbox.Group options={KNOWLEDGE_QUALITY_GATE_OPTIONS} />
                </Form.Item>
                <Form.Item name="qualitySummary" label="质量门摘要">
                  <Input.TextArea rows={2} />
                </Form.Item>
              </>
            )}
            <Space wrap>
              <Button
                type="primary"
                loading={reviewMutation.isPending}
                onClick={() => void reviewCandidate("APPROVE")}
              >
                通过并发布
              </Button>
              <Button
                loading={reviewMutation.isPending}
                onClick={() => void reviewCandidate("RETURN")}
              >
                退修
              </Button>
              <Button
                danger
                loading={reviewMutation.isPending}
                onClick={() => void reviewCandidate("REJECT")}
              >
                驳回候选
              </Button>
            </Space>
          </Form>
        </Space>
      </Drawer>

      <Modal
        title="安排知识身份弃用"
        open={Boolean(retirementIdentity)}
        okText="确认安排弃用"
        cancelText="取消"
        confirmLoading={retirementMutation.isPending}
        onOk={() => void scheduleRetirement()}
        onCancel={() => {
          setRetirementIdentity(undefined);
          setRetirementDraft(EMPTY_RETIREMENT_FORM);
          setSuccessorKeyword("");
        }}
        destroyOnClose
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="warning"
            showIcon
            message={retirementIdentity?.subject ?? "未选择知识身份"}
            description="宽限期内旧身份继续可读并显示迁移提示；到期后旧版本撤回，服务机构覆盖进入迁移悬置态。"
          />
          <Form layout="vertical">
            <Form.Item label="后继知识身份" required>
              <Select
                aria-label="后继知识身份"
                className="mk-full-width"
                showSearch
                filterOption={false}
                options={successorOptions}
                loading={successorsQuery.isLoading}
                placeholder="选择后继知识身份"
                onSearch={setSuccessorKeyword}
                value={retirementDraft.successorIdentityId}
                onChange={(successorIdentityId) =>
                  setRetirementDraft((current) => ({ ...current, successorIdentityId }))
                }
              />
            </Form.Item>
            <Form.Item label="宽限期结束时间" required>
              <Input
                aria-label="宽限期结束时间"
                type="datetime-local"
                value={retirementDraft.gracePeriodEnd}
                onChange={(event) =>
                  setRetirementDraft((current) => ({
                    ...current,
                    gracePeriodEnd: event.target.value,
                  }))
                }
              />
            </Form.Item>
            <Form.Item label="迁移指引" required>
              <Input.TextArea
                aria-label="迁移指引"
                rows={4}
                maxLength={1000}
                showCount
                placeholder="说明替代范围、覆盖复核要求和迁移完成条件"
                value={retirementDraft.migrationGuidance}
                onChange={(event) =>
                  setRetirementDraft((current) => ({
                    ...current,
                    migrationGuidance: event.target.value,
                  }))
                }
              />
            </Form.Item>
          </Form>
        </Space>
      </Modal>
    </>
  );
}
