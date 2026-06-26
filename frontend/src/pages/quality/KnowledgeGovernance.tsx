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
  useConfirmModelEgress,
  useGenerateKnowledgeModelCandidate,
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
  useKnowledgeInitializationBatches,
  useApproveLowKnowledgeInitializationBatch,
  useRefreshKnowledgeInitializationBatch,
  useCandidateProvenance,
  useCandidateCoexistence,
  useAssetTemplates,
  useKnowledgeIdentities,
  usePublishKnowledgeCustomization,
  useRestorePlatformKnowledge,
  useReviewKnowledgeCandidate,
  useSecurityProfile,
  type CandidateClassification,
  type AikGateResult,
  type CandidateCoexistenceView,
  type CandidateCoexistenceVersionSnapshot,
  type CreateKnowledgeProductionJobRequest,
  type KnowledgeModelProductionResult,
  type KnowledgeModelCandidateRequest,
  type KnowledgeSourceAuthorityLevel,
  type ModelEgressConfirmationChallenge,
  type GenerationTriage,
  type CandidateProvenanceView,
  type KnowledgeProductionCandidateView,
  type KnowledgeProductionJob,
  type KnowledgeProductionReadinessItem,
  type KnowledgeInitializationBatch,
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
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { OrgUnitSelect } from "@/shared/ui/OrgUnitSelect";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { SourceInfo } from "@/shared/ui/SourceInfo";

import AcquisitionSourceGovernancePanel from "./AcquisitionSourceGovernancePanel";
import styles from "./Quality.module.css";

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

const REVIEW_FOLLOWUP_BY_FEEDBACK: Record<
  KnowledgeReviewFeedbackType,
  KnowledgeReviewFollowupAction
> = {
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
const KNOWLEDGE_CUSTOMIZATION_PAGE_SIZE = 20;
const KNOWLEDGE_CANDIDATE_PAGE_SIZE = 20;

const RISK_COLORS: Record<string, "default" | "success" | "warning" | "error"> = {
  LOW: "success",
  MEDIUM: "warning",
  HIGH: "error",
};

// AIK-STD-12：AI 工厂生产器中文标识（aiGenerated 据 producer≠MANUAL，由后端判定）
const PRODUCER_LABELS: Record<string, string> = {
  API_MODEL: "统一模型接口",
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
    description: "归属平台主源，机构只能订阅和派生，不允许直接编辑或反写。",
  },
  TENANT_OVERLAY: {
    label: "院内覆盖",
    color: "green",
    boundaryLabel: "院内覆盖可治理",
    summary: "院内覆盖本机构治理",
    description: "归属当前机构，只影响本机构继承范围，禁止污染平台主源。",
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

function tableText(value?: string | number | null, fallback = "无") {
  const text = value === undefined || value === null || value === "" ? fallback : String(value);
  return <span className={styles.wrapCell}>{text}</span>;
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

function hospitalFallbackText(provenance: CandidateProvenanceView) {
  if (!provenance.fallbackUsed) return "未启用备用生产能力";
  return "已启用备用生产能力，候选仍需人工复核";
}

function sourceCitationSummary(value?: string | null) {
  return value ? "已记录来源引用，审核时以来源锚点为准" : "未返回来源引用";
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
  reason: string;
  feedbackType?: KnowledgeReviewFeedbackType;
  qualityGates?: string[];
  qualitySummary?: string;
};

type RetirementFormValues = {
  successorIdentityId?: number;
  gracePeriodEnd: string;
  migrationGuidance: string;
};

type ModelGenerationFormValues = {
  capabilityCode: string;
  prompt: string;
  providerCode?: string;
  timeoutSeconds: number;
  assetIdentity: string;
  subject: string;
  sourceRef: string;
  trustLevel: KnowledgeSourceAuthorityLevel;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  targetMode: "EXISTING" | "NEW";
  targetIdentityId?: number;
  newIdentityCode?: string;
  newIdentityDomain?: KnowledgeDomain;
};

type PendingModelEgressConfirmation = {
  job: KnowledgeProductionJob;
  request: KnowledgeModelCandidateRequest;
  challenge: ModelEgressConfirmationChallenge;
};

const EMPTY_RETIREMENT_FORM: RetirementFormValues = {
  successorIdentityId: undefined,
  gracePeriodEnd: "",
  migrationGuidance: "",
};

type KnowledgeGovernanceMode = "review" | "institution" | "production";

interface KnowledgeGovernanceProps {
  mode?: KnowledgeGovernanceMode;
  embedded?: boolean;
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

function defaultCapabilityFor(_assetType?: string | null) {
  return "knowledge.production.knowledge";
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

function candidateReviewRouteDescription(riskLevel?: string | null) {
  if (riskLevel === "HIGH") {
    return "高风险必须逐条确认并保留完整证据";
  }
  if (riskLevel === "MEDIUM") {
    return "中风险必须逐条审核";
  }
  if (riskLevel === "LOW") {
    return "低风险可纳入初始化批次原子批审";
  }
  return "风险分级缺失，必须逐条审核";
}

function candidateReviewRouteColor(candidate?: KnowledgeProductionCandidateView) {
  if (!candidate) return "default";
  return candidate.riskLevel === "LOW" ? "success" : "warning";
}

function initializationBatchStatusLabel(status: KnowledgeInitializationBatch["status"]) {
  if (status === "VALIDATED") return "已校验";
  if (status === "IN_REVIEW") return "审核中";
  if (status === "COMPLETE") return "已完成";
  return "已阻断";
}

function initializationBatchStatusColor(status: KnowledgeInitializationBatch["status"]) {
  if (status === "COMPLETE") return "success";
  if (status === "IN_REVIEW") return "processing";
  if (status === "BLOCKED") return "error";
  return "default";
}

function initializationReleaseTypeLabel(type: KnowledgeInitializationBatch["releaseType"]) {
  if (type === "FOUNDATION") return "基础知识发行";
  if (type === "CLINICAL_CONTENT") return "临床内容发行";
  return "组合资产发行";
}

function initializationPhaseLabel(phase: KnowledgeInitializationBatch["phase"]) {
  const labels: Record<KnowledgeInitializationBatch["phase"], string> = {
    F0: "来源与许可",
    F1: "基础目录",
    F2: "证据分级",
    F3: "原始医学事实",
    F4: "确定性构件",
    F5: "高风险派生",
    F6: "组合资产",
    F7: "机构本地化",
    F8: "总验收与发行证据",
  };
  return labels[phase];
}

export function InstitutionKnowledge() {
  return <KnowledgeGovernance mode="institution" />;
}

export function KnowledgeProduction() {
  return <KnowledgeGovernance mode="production" />;
}

export function KnowledgeProductionWorkspace() {
  return <KnowledgeGovernance mode="production" embedded />;
}

export default function KnowledgeGovernance({
  mode = "review",
  embedded = false,
}: KnowledgeGovernanceProps) {
  const { message, modal } = AntdApp.useApp();
  const [domain, setDomain] = useState<KnowledgeDomain>("GUIDELINE");
  const [status, setStatus] = useState<KnowledgeIdentityStatus>("ACTIVE");
  const [keyword, setKeyword] = useState("");
  const [identityPage, setIdentityPage] = useState(1);
  const [candidatePage, setCandidatePage] = useState(1);
  const [customizationPage, setCustomizationPage] = useState(1);
  const [productionJobCode, setProductionJobCode] = useState<string>();
  const [productionCandidateRef, setProductionCandidateRef] = useState<string>();
  const [modelGenerationJob, setModelGenerationJob] = useState<KnowledgeProductionJob>();
  const [pendingModelEgressConfirmation, setPendingModelEgressConfirmation] =
    useState<PendingModelEgressConfirmation>();
  const [expandedProvenanceRefs, setExpandedProvenanceRefs] = useState<string[]>([]);
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
  const [customizationActionForm] = Form.useForm<{ reason: string }>();
  const [productionJobForm] = Form.useForm<CreateKnowledgeProductionJobRequest>();
  const [modelGenerationForm] = Form.useForm<ModelGenerationFormValues>();
  const [modelEgressConfirmationForm] = Form.useForm<{ purpose: string }>();
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const mayUseEvidenceDetails = canUseEvidenceDetails(security.data);
  const evidenceDetailsEnabled = mayUseEvidenceDetails && globalEvidenceDetails;
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
  const generateModelCandidateMutation = useGenerateKnowledgeModelCandidate();
  const confirmModelEgressMutation = useConfirmModelEgress();
  const cancelProductionJobMutation = useCancelKnowledgeProductionJob();
  const canCustomize =
    !isPlatformTenant &&
    security.data?.permissions.some((permission) => permission.code === "knowledge.write");
  const canWriteKnowledge =
    security.data?.permissions.some((permission) => permission.code === "knowledge.write") ?? false;
  const canReviewKnowledge =
    security.data?.permissions.some((permission) => permission.code === "knowledge.review") ??
    false;
  const canPublishCustomization =
    security.data?.permissions.some((permission) => permission.code === "knowledge.publish") &&
    security.data?.permissions.some((permission) => permission.code === "tenant.override");
  const canRestoreCustomization =
    security.data?.permissions.some((permission) => permission.code === "knowledge.withdraw") &&
    security.data?.permissions.some((permission) => permission.code === "tenant.override");
  const productionMode = mode === "production";
  const productionReadinessQuery = useKnowledgeProductionReadiness(
    { producer: "API_MODEL" },
    productionMode,
  );
  const productionJobsQuery = useKnowledgeProductionJobs({ page: 1, size: 20 }, productionMode);
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
  const initializationBatchesQuery = useKnowledgeInitializationBatches(mode === "production");
  const initializationBatches = initializationBatchesQuery.data ?? [];
  const approveLowInitializationBatchMutation = useApproveLowKnowledgeInitializationBatch();
  const refreshInitializationBatchMutation = useRefreshKnowledgeInitializationBatch();
  const firstProductionCandidateRef = productionCandidatesQuery.data?.items[0]?.candidateRef;
  const selectedProductionCandidateRef = productionCandidateRef ?? firstProductionCandidateRef;
  const productionCoexistenceQuery = useCandidateCoexistence(selectedProductionCandidateRef);

  useEffect(() => {
    setProductionCandidateRef(undefined);
  }, [selectedProductionJobCode]);

  useEffect(() => {
    if (mode !== "production") return;
    productionJobForm.setFieldsValue({ targetPipeline: defaultProductionTargetPipeline });
  }, [defaultProductionTargetPipeline, mode, productionJobForm]);

  useEffect(() => {
    if (!customizeIdentity) return;
    customizeForm.resetFields();
    customizeForm.setFieldsValue({ applicableScope: "ALL" });
  }, [customizeForm, customizeIdentity]);

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
  const isProvenanceDetailsOpen = (provenance?: CandidateProvenanceView) =>
    Boolean(provenance && expandedProvenanceRefs.includes(provenance.candidateRef));
  const toggleProvenanceDetails = (provenance: CandidateProvenanceView) => {
    setExpandedProvenanceRefs((refs) =>
      refs.includes(provenance.candidateRef)
        ? refs.filter((ref) => ref !== provenance.candidateRef)
        : [...refs, provenance.candidateRef],
    );
  };
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
  useEffect(() => {
    if (!selectedCandidateId) return;
    reviewForm.setFieldsValue({
      reason: "",
      feedbackType: undefined,
      qualityGates: [],
    });
  }, [reviewForm, selectedCandidateId]);
  const platformPublishing = security.data?.dataScope.tenantId === platformTenantId;

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
  }

  async function reviewCandidate(decision: KnowledgeCandidateReviewDecision) {
    if (!selectedCandidateId) return;
    if (!selectedClassification?.id) {
      message.error("未找到候选分类审核记录");
      return;
    }
    const classificationReviewId = selectedClassification.id;
    try {
      const fields = ["reason"];
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
      if (decision === "APPROVE" && platformPublishing) {
        const gates = new Set(values.qualityGates ?? []);
        publishEvidence = {
          qualityGate: {
            schemaValid: gates.has("schemaValid"),
            terminologyBindingComplete: gates.has("terminologyBindingComplete"),
            dependencyIntegrityVerified: gates.has("dependencyIntegrityVerified"),
            safetyMonotonicityVerified: gates.has("safetyMonotonicityVerified"),
            impactSimulationPassed: gates.has("impactSimulationPassed"),
            summary: values.qualitySummary?.trim() || undefined,
          },
        };
      }
      await reviewMutation.mutateAsync({
        candidateId: classificationReviewId,
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
      content:
        "仅中止待处理或运行中的生产任务；已入审核的候选仍按治理链路留痕处理，不会伪造发布成功。",
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

  function openModelGeneration(job: KnowledgeProductionJob) {
    const targetIdentity =
      identities.find((identity) => identity.id === selectedIdentityId) ?? identities[0];
    setProductionJobCode(job.jobCode);
    setModelGenerationJob(job);
    modelGenerationForm.setFieldsValue({
      capabilityCode:
        productionReadinessQuery.data?.capabilityCode || defaultCapabilityFor(job.assetType),
      prompt: "",
      providerCode: productionReadinessQuery.data?.providerCode || undefined,
      timeoutSeconds: 90,
      assetIdentity: targetIdentity?.identityCode || "",
      subject: targetIdentity?.subject || "",
      sourceRef: "",
      trustLevel: "B_GUIDELINE",
      riskLevel: "MEDIUM",
      targetMode: targetIdentity ? "EXISTING" : "NEW",
      targetIdentityId: targetIdentity?.id,
      newIdentityCode: targetIdentity ? undefined : "",
      newIdentityDomain: (targetIdentity?.domain ?? job.domain ?? "OTHER") as KnowledgeDomain,
    });
  }

  async function refreshProductionEvidence() {
    await Promise.all([
      productionJobsQuery.refetch(),
      productionCandidatesQuery.refetch(),
      productionGateResultsQuery.refetch(),
      productionTriageResultsQuery.refetch(),
      productionShadowRunsQuery.refetch(),
      identitiesQuery.refetch(),
    ]);
  }

  function firstBlockedGateReason(result: KnowledgeModelProductionResult) {
    return result.summary.blocked[0]?.failedGates?.[0]?.reason;
  }

  async function handleModelGenerationResult(
    result: KnowledgeModelProductionResult,
    job: KnowledgeProductionJob,
    request: KnowledgeModelCandidateRequest,
  ) {
    if (result.summary.candidates.length > 0) {
      message.success(`已生成 ${result.summary.candidates.length} 条待审核知识候选`);
      modelGenerationForm.resetFields();
      setPendingModelEgressConfirmation(undefined);
      setModelGenerationJob(undefined);
    } else if (result.egressConfirmation) {
      setPendingModelEgressConfirmation({
        job,
        request,
        challenge: result.egressConfirmation,
      });
      message.warning(firstBlockedGateReason(result) || "模型外调需要责任确认，未生成候选");
    } else if (result.summary.blocked.length > 0) {
      message.warning(firstBlockedGateReason(result) || "模型结果被生产安全校验阻断，未生成候选");
    } else {
      message.warning(result.summary.skipped[0]?.reason || "模型未生成可提交候选");
    }
    await refreshProductionEvidence();
  }

  async function submitModelGeneration(values: ModelGenerationFormValues) {
    if (!modelGenerationJob) return;
    const target: KnowledgeModelCandidateRequest["target"] =
      values.targetMode === "EXISTING"
        ? { targetIdentityId: values.targetIdentityId as number }
        : {
            newIdentity: {
              domain: values.newIdentityDomain ?? "OTHER",
              subject: values.subject.trim(),
              identityCode: values.newIdentityCode?.trim() ?? "",
            },
          };
    const request: KnowledgeModelCandidateRequest = {
      capabilityCode: values.capabilityCode.trim(),
      prompt: values.prompt.trim(),
      providerCode: values.providerCode?.trim() || undefined,
      timeoutSeconds: values.timeoutSeconds,
      assetIdentity: values.assetIdentity.trim(),
      subject: values.subject.trim(),
      sources: [
        {
          sourceRef: values.sourceRef.trim(),
          authorityLevel: values.trustLevel,
        },
      ],
      trustLevel: values.trustLevel,
      riskLevel: values.riskLevel,
      target,
    };
    try {
      const result = await generateModelCandidateMutation.mutateAsync({
        jobCode: modelGenerationJob.jobCode,
        request,
      });
      await handleModelGenerationResult(result, modelGenerationJob, request);
    } catch (error) {
      message.error(getApiErrorMessage(error, "大模型知识生成失败"));
    }
  }

  async function confirmPendingModelEgress(values: { purpose: string }) {
    if (!pendingModelEgressConfirmation) return;
    const purpose = values.purpose.trim();
    if (!purpose) {
      message.warning("请填写本次外调用途说明");
      return;
    }
    const pending = pendingModelEgressConfirmation;
    try {
      await confirmModelEgressMutation.mutateAsync({
        capabilityCode: pending.challenge.capabilityCode,
        payloadHash: pending.challenge.payloadHash,
        purpose,
      });
      message.success("外调用途确认已记录，正在重新启动模型生成");
      setPendingModelEgressConfirmation(undefined);
      modelEgressConfirmationForm.resetFields();
      const result = await generateModelCandidateMutation.mutateAsync({
        jobCode: pending.job.jobCode,
        request: pending.request,
      });
      await handleModelGenerationResult(result, pending.job, pending.request);
    } catch (error) {
      message.error(getApiErrorMessage(error, "外调用途确认或重试失败"));
    }
  }

  function requestApproveLowInitializationBatch(batch: KnowledgeInitializationBatch) {
    modal.confirm({
      title: "确认批量批准低风险候选",
      content: (
        <Space direction="vertical" size="small">
          <Text>批次：{batch.batchCode}</Text>
          <Text type="secondary">
            发行摘要：{evidenceDetailsEnabled ? batch.overallHash : "已由服务端冻结并校验"}
          </Text>
          <Text>仅处理服务端冻结清单中的低风险条目；中高风险仍须由医疗引擎运营人员逐条确认。</Text>
        </Space>
      ),
      okText: "确认批准",
      cancelText: "取消",
      onOk: async () => {
        try {
          await approveLowInitializationBatchMutation.mutateAsync({
            batchCode: batch.batchCode,
            expectedOverallHash: batch.overallHash,
            idempotencyKey: `knowledge-initialization-${batch.batchCode}-low-${Date.now()}`,
            reason: "初始化发行清单低风险候选原子批审",
          });
          message.success("低风险候选批审完成，中高风险审核状态保持不变");
          await initializationBatchesQuery.refetch();
        } catch (error) {
          message.error(getApiErrorMessage(error, "低风险候选批审失败"));
        }
      },
    });
  }

  async function refreshInitializationBatch(batchCode: string) {
    try {
      await refreshInitializationBatchMutation.mutateAsync(batchCode);
      message.success("初始化发行批次状态已按真实审核链刷新");
      await initializationBatchesQuery.refetch();
    } catch (error) {
      message.error(getApiErrorMessage(error, "刷新初始化发行批次失败"));
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
      customizeForm.resetFields();
      setCustomizeIdentity(undefined);
    } catch (error) {
      message.error(getApiErrorMessage(error, "创建机构知识定制失败"));
    }
  }

  async function submitCustomizationAction(values: { reason: string }) {
    if (!customizationAction) return;
    try {
      if (customizationAction.type === "publish") {
        await publishCustomization.mutateAsync({
          customizationId: customizationAction.item.customizationId,
          reason: values.reason.trim(),
        });
        message.success("机构定制已发布并在目标组织生效");
      } else {
        await restorePlatformKnowledge.mutateAsync({
          customizationId: customizationAction.item.customizationId,
          reason: values.reason.trim(),
        });
        message.success("已恢复使用平台标准，历史定制继续保留");
      }
      customizationActionForm.resetFields();
      setCustomizationAction(undefined);
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
            <Tag color={PIPELINE_META.PLATFORM_SOURCE.color}>
              {PIPELINE_META.PLATFORM_SOURCE.label}
            </Tag>
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
            <Text>{producerLabel(provenance.producer)}</Text>
            {provenance.confidence !== null && provenance.confidence !== undefined ? (
              <Text type="secondary">{confidenceText(provenance.confidence)}</Text>
            ) : null}
            <Text type="secondary">{hospitalFallbackText(provenance)}</Text>
            <Text type="secondary">{sourceCitationSummary(provenance.sourceCitations)}</Text>
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

  const productionReadinessColumns: ColumnsType<KnowledgeProductionReadinessItem> = [
    { title: "前置项", dataIndex: "code", width: 180, render: (value) => tableText(value) },
    {
      title: "状态",
      dataIndex: "ready",
      width: 96,
      render: (ready: boolean) => (
        <Tag color={ready ? "success" : "error"}>{ready ? "满足" : "阻断"}</Tag>
      ),
    },
    { title: "说明", dataIndex: "message", width: 360, render: (value) => tableText(value) },
    {
      title: "证据",
      dataIndex: "evidence",
      width: 360,
      render: (value?: string | null) => tableText(value, "无"),
    },
  ];

  const productionJobColumns: ColumnsType<KnowledgeProductionJob> = [
    {
      title: "生产任务",
      key: "job",
      width: 260,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong className={styles.wrapCell}>
            {record.jobCode}
          </Text>
          <Text type="secondary">{producerLabel(record.producer)}</Text>
        </Space>
      ),
    },
    {
      title: "管道 / 状态",
      key: "status",
      width: 220,
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
      width: 180,
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
      width: 180,
      render: (value?: string | null) => tableText(value, "未配置"),
    },
    {
      title: "操作",
      key: "action",
      width: 140,
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
      width: 220,
      render: (value: string) => (
        <Text strong className={styles.wrapCell}>
          {value}
        </Text>
      ),
    },
    {
      title: "资产身份 / hash",
      key: "identity",
      width: 280,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text className={styles.wrapCell}>{record.assetIdentity || "未返回身份"}</Text>
          <Text type="secondary" className={styles.wrapCell}>
            {record.contentHash || "未返回 hash"}
          </Text>
        </Space>
      ),
    },
    {
      title: "风险 / 审核",
      key: "routing",
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag color={RISK_COLORS[record.riskLevel ?? ""] ?? "default"}>
            {riskLabel(record.riskLevel)}
          </Tag>
          <Text type="secondary">{candidateReviewRouteDescription(record.riskLevel)}</Text>
        </Space>
      ),
    },
  ];

  const productionGateColumns: ColumnsType<AikGateResult> = [
    {
      title: "生产安全校验",
      dataIndex: "gateCode",
      width: 220,
      render: (value?: string | null) => tableText(value, "未返回门禁"),
    },
    {
      title: "结果",
      dataIndex: "passed",
      width: 96,
      render: (passed: boolean) => (
        <Tag color={passed ? "success" : "error"}>{booleanGateLabel(passed)}</Tag>
      ),
    },
    {
      title: "原因",
      dataIndex: "reason",
      width: 420,
      render: (value?: string | null) => tableText(value, "无"),
    },
  ];

  const productionTriageColumns: ColumnsType<GenerationTriage> = [
    {
      title: "8 态",
      dataIndex: "triageState",
      width: 220,
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
      width: 180,
      render: (value?: string | null) => tableText(value, "未返回动作"),
    },
    {
      title: "依据",
      dataIndex: "basis",
      width: 420,
      render: (value?: string | null) => tableText(value, "未返回依据"),
    },
  ];

  const productionShadowColumns: ColumnsType<KnowledgeShadowRun> = [
    {
      title: "状态",
      dataIndex: "status",
      width: 140,
      render: (value: string) => <Tag color={productionStatusColor(value)}>{value}</Tag>,
    },
    {
      title: "样本",
      key: "cases",
      width: 280,
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
      width: 360,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Tag color={record.readyForReview ? "success" : "error"}>
            {record.readyForReview ? "可提审" : "不可提审"}
          </Tag>
          <Text type="secondary" className={styles.wrapCell}>
            {record.basis || "未返回依据"}
          </Text>
        </Space>
      ),
    },
  ];

  const initializationBatchColumns: ColumnsType<KnowledgeInitializationBatch> = [
    {
      title: "发行批次",
      key: "batch",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.batchCode}</Text>
          <Text type="secondary">
            {initializationReleaseTypeLabel(record.releaseType)} · {record.releaseVersion} ·{" "}
            {initializationPhaseLabel(record.phase)}
          </Text>
        </Space>
      ),
    },
    {
      title: "状态 / 冻结摘要",
      key: "status",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Tag color={initializationBatchStatusColor(record.status)}>
            {initializationBatchStatusLabel(record.status)}
          </Tag>
          {evidenceDetailsEnabled ? (
            <Text type="secondary" copyable>
              {record.overallHash}
            </Text>
          ) : (
            <Text type="secondary">发行摘要已冻结并校验</Text>
          )}
        </Space>
      ),
    },
    {
      title: "风险审核路由",
      key: "risk",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>低风险 {record.lowCount} · 可原子批审</Text>
          <Text>中风险 {record.mediumCount} · 必须逐条审核</Text>
          <Text>高风险 {record.highCount} · 必须逐条确认并保留证据</Text>
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Space size="small" wrap>
          <Button
            type="primary"
            disabled={!canReviewKnowledge || record.status !== "IN_REVIEW" || record.lowCount === 0}
            loading={approveLowInitializationBatchMutation.isPending}
            onClick={() => requestApproveLowInitializationBatch(record)}
          >
            批准低风险候选
          </Button>
          <Button
            disabled={!canReviewKnowledge}
            loading={refreshInitializationBatchMutation.isPending}
            onClick={() => void refreshInitializationBatch(record.batchCode)}
          >
            刷新审核状态
          </Button>
        </Space>
      ),
    },
  ];
  let initializationBatchContent: ReactNode;
  if (initializationBatchesQuery.isLoading) {
    initializationBatchContent = (
      <PageState
        state="loading"
        title="正在读取初始化发行批次"
        description="正在核对服务端冻结清单、摘要和审核分层。"
      />
    );
  } else if (initializationBatchesQuery.isError) {
    initializationBatchContent = (
      <PageState
        state="error"
        title="初始化发行批次读取失败"
        description={getApiErrorMessage(initializationBatchesQuery.error, "无法读取初始化发行批次")}
        onRetry={() => void initializationBatchesQuery.refetch()}
      />
    );
  } else if (initializationBatches.length === 0) {
    initializationBatchContent = (
      <PageState
        state="empty"
        title="暂无初始化发行批次"
        description="先由服务端完成来源批准、候选解析和发行摘要冻结，再进入分层审核。"
      />
    );
  } else {
    initializationBatchContent = (
      <Table
        rowKey="batchCode"
        columns={initializationBatchColumns}
        dataSource={initializationBatches}
        pagination={false}
        size="small"
      />
    );
  }

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
        {productionReadinessQuery.data && !productionReadinessQuery.data.ready ? (
          <Alert
            type="warning"
            showIcon
            message="八项安全门尚未全部满足，暂不能创建正式生产任务"
            description="请先在模型生产控制台处理模型服务、医学评测和其余安全门阻断项。"
          />
        ) : null}
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
              <Form.Item name="assetType" hidden>
                <Input />
              </Form.Item>
              <Form.Item label="资产类型">
                <Input value="医学知识" disabled />
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
              <Form.Item label="生产方式">
                <Input value="统一模型接口（本地或外部模型服务）" disabled />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={4}>
              <Form.Item label="领域" name="domain">
                <Select options={KNOWLEDGE_DOMAIN_OPTIONS} />
              </Form.Item>
            </Col>
            <Col xs={24} lg={8}>
              <Form.Item label="模型策略" name="modelStrategy">
                <Input placeholder="例如 gpt-pipeline / 外部模型策略标识" />
              </Form.Item>
            </Col>
            <Col xs={24} lg={8}>
              <Form.Item label="提交" colon={false}>
                <Button
                  type="primary"
                  htmlType="submit"
                  icon={<PlusOutlined />}
                  aria-label="创建生产任务"
                  disabled={!canWriteKnowledge || !productionReadinessQuery.data?.ready}
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
  const productionAcquisitionGovernance = (
    <AcquisitionSourceGovernancePanel canWrite={canWriteKnowledge} />
  );
  const initializationBatchCard = <Card title="初始化发行批次">{initializationBatchContent}</Card>;

  let productionCenterContent: ReactNode;
  if (productionReadinessQuery.isLoading || productionJobsQuery.isLoading) {
    productionCenterContent = (
      <Space direction="vertical" size="large" className="mk-full-width">
        {productionWorkbench}
        {productionAcquisitionGovernance}
        {productionPipelinePartition}
        {initializationBatchCard}
        <PageState state="loading" title="正在读取知识生产中心" />
      </Space>
    );
  } else if (productionReadinessQuery.isError || productionJobsQuery.isError) {
    productionCenterContent = (
      <Space direction="vertical" size="large" className="mk-full-width">
        {productionWorkbench}
        {productionAcquisitionGovernance}
        {productionPipelinePartition}
        {initializationBatchCard}
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
        {productionAcquisitionGovernance}
        {productionPipelinePartition}
        {initializationBatchCard}
        <Card title="模型生产上线准备">
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
          title="暂无生产任务"
          description="尚未有知识生产任务进入统一候选流水线。"
        />
      </Space>
    );
  } else {
    const readiness = productionReadinessQuery.data;
    const productionCandidates = productionCandidatesQuery.data?.items ?? [];
    const productionGateResults = productionGateResultsQuery.data ?? [];
    const productionTriageResults = productionTriageResultsQuery.data ?? [];
    const productionShadowRuns = productionShadowRunsQuery.data ?? [];
    const coexistence = productionCoexistenceQuery.data;
    const selectedProductionCandidate =
      productionCandidates.find(
        (candidate) => candidate.candidateRef === selectedProductionCandidateRef,
      ) ?? productionCandidates[0];
    const productionEvidenceErrors = [
      productionCandidatesQuery.isError
        ? `候选血缘：${getApiErrorMessage(productionCandidatesQuery.error, "候选血缘读取失败")}`
        : null,
      productionGateResultsQuery.isError
        ? `生产安全校验结果：${getApiErrorMessage(productionGateResultsQuery.error, "生产安全校验结果读取失败")}`
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
        <div className={styles.tableViewport} data-testid="production-candidate-lineage-table">
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
            scroll={{ x: 760 }}
            size="small"
            tableLayout="fixed"
          />
        </div>
      );
    }
    productionCenterContent = (
      <Space direction="vertical" size="large" className="mk-full-width">
        {productionWorkbench}
        {productionAcquisitionGovernance}
        {productionPipelinePartition}
        <Card title="模型生产上线准备">
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Alert
              type={readiness?.ready ? "success" : "warning"}
              showIcon
              message={readiness?.ready ? "模型生产前置已满足" : "模型生产前置仍有阻断"}
              description={
                readiness?.modelInvocationAllowed
                  ? "模型生产器可进入候选生产，但候选仍必须走生产安全校验、评测、分流和审核。"
                  : "上线准备未通过时不得调用外部模型或伪造候选。"
              }
            />
            <div className={styles.tableViewport} data-testid="production-readiness-table">
              <Table
                rowKey="code"
                columns={productionReadinessColumns}
                dataSource={readiness?.items ?? []}
                pagination={false}
                scroll={{ x: 996 }}
                size="small"
                tableLayout="fixed"
              />
            </div>
          </Space>
        </Card>

        <Card title="生产任务">
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
                      <Text>生产安全校验 {productionGateResults.length} 项</Text>
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
                  <Space wrap>
                    {canWriteKnowledge &&
                    readiness?.modelInvocationAllowed &&
                    canCancelProductionJob(selectedProductionJob.status) ? (
                      <Button
                        type="primary"
                        aria-label="启动大模型生成"
                        onClick={() => openModelGeneration(selectedProductionJob)}
                      >
                        启动大模型生成
                      </Button>
                    ) : null}
                    {selectedJobCanBeCancelled ? (
                      <Button
                        danger
                        aria-label="中止生产任务"
                        icon={<StopOutlined />}
                        loading={cancelProductionJobMutation.isPending}
                        onClick={() => requestCancelProductionJob(selectedProductionJob)}
                      >
                        中止生产任务
                      </Button>
                    ) : null}
                  </Space>
                }
              />
            ) : null}
            <div className={styles.tableViewport} data-testid="production-jobs-table">
              <Table
                rowKey="jobCode"
                columns={productionJobColumns}
                dataSource={productionJobs}
                pagination={false}
                scroll={{ x: 880 }}
                size="middle"
                tableLayout="fixed"
              />
            </div>
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
            <Card title="生产安全校验结果">
              <div className={styles.tableViewport} data-testid="production-gate-results-table">
                <Table
                  rowKey={(record) => `${record.gateCode}-${record.contentHash ?? ""}`}
                  columns={productionGateColumns}
                  dataSource={productionGateResults}
                  loading={productionGateResultsQuery.isLoading}
                  pagination={false}
                  scroll={{ x: 736 }}
                  size="small"
                  tableLayout="fixed"
                />
              </div>
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
                <div className={styles.tableViewport} data-testid="production-triage-results-table">
                  <Table
                    rowKey={(record) => `${record.triageState}-${record.contentHash ?? ""}`}
                    columns={productionTriageColumns}
                    dataSource={productionTriageResults}
                    loading={productionTriageResultsQuery.isLoading}
                    pagination={false}
                    scroll={{ x: 820 }}
                    size="small"
                    tableLayout="fixed"
                  />
                </div>
              </Space>
            </Card>
          </Col>
          <Col xs={24} xl={12}>
            <Card title="影子评测">
              <div className={styles.tableViewport} data-testid="production-shadow-runs-table">
                <Table
                  rowKey={(record) => `${record.status}-${record.contentHash ?? ""}`}
                  columns={productionShadowColumns}
                  dataSource={productionShadowRuns}
                  loading={productionShadowRunsQuery.isLoading}
                  pagination={false}
                  scroll={{ x: 780 }}
                  size="small"
                  tableLayout="fixed"
                />
              </div>
            </Card>
          </Col>
        </Row>

        <Card title="共存替换提醒">
          {selectedProductionJob ? (
            <Space direction="vertical" size="middle" className="mk-full-width">
              <Descriptions column={1} bordered size="small">
                <Descriptions.Item label="当前生产任务">
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
            <PageState state="empty" title="未选择生产任务" />
          )}
        </Card>

        <Card title="结论">
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Space size="middle" wrap>
              <Tag color={selectedProductionCandidate ? "processing" : "default"}>
                当前候选 {selectedProductionCandidate ? 1 : 0} 条
              </Tag>
              <Tag color={candidateReviewRouteColor(selectedProductionCandidate)}>
                {candidateReviewRouteDescription(selectedProductionCandidate?.riskLevel)}
              </Tag>
            </Space>
            <Text type="secondary">
              生产候选本身不提供临时批量通过；只有服务端冻结并校验过摘要的初始化发行批次，才允许对其中
              低风险条目执行原子批审。
            </Text>
          </Space>
        </Card>

        {initializationBatchCard}
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
            <Tag color={PIPELINE_META.TENANT_OVERLAY.color}>
              {PIPELINE_META.TENANT_OVERLAY.label}
            </Tag>
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
    customizationActionMessage = "发布后将接管所选机构的知识解析";
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
      description: "核查生产流水线的上线准备、生产任务、生产安全校验、8 态分流和影子证据",
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

  const evidenceDetailsEnabledControl = mayUseEvidenceDetails ? (
    <EvidenceDetailsToggle securityProfile={security.data} />
  ) : null;
  const pageExtrasWithEvidenceDetails = evidenceDetailsEnabledControl ? (
    <Space wrap>
      {pageExtras}
      {evidenceDetailsEnabledControl}
    </Space>
  ) : (
    pageExtras
  );

  return (
    <>
      {embedded ? (
        pageContent
      ) : (
        <PageShell
          title={pageMeta.title}
          description={pageMeta.description}
          extras={pageExtrasWithEvidenceDetails}
        >
          {pageContent}
        </PageShell>
      )}

      <Modal
        title="生成正式知识候选"
        open={Boolean(modelGenerationJob)}
        okText="开始生成候选"
        cancelText="取消"
        confirmLoading={generateModelCandidateMutation.isPending}
        onOk={() => modelGenerationForm.submit()}
        onCancel={() => {
          modelGenerationForm.resetFields();
          setModelGenerationJob(undefined);
        }}
        destroyOnClose
        width={760}
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="info"
            showIcon
            message={`生产任务 ${modelGenerationJob?.jobCode ?? ""}`}
            description="大模型只生成待审核候选；来源锚点、目标身份、生产安全校验、分流和影子评测全部通过后才进入审核，绝不直接生效。"
          />
          <Form
            form={modelGenerationForm}
            layout="vertical"
            onFinish={submitModelGeneration}
            preserve={false}
          >
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item
                  name="capabilityCode"
                  label="模型能力"
                  rules={[{ required: true, whitespace: true, message: "请填写模型能力代码" }]}
                >
                  <Input placeholder="knowledge.production.knowledge" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="providerCode" label="模型服务">
                  <Input placeholder="留空则由服务端策略选择" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item
              name="prompt"
              label="生成提示"
              rules={[{ required: true, whitespace: true, message: "请填写生成提示" }]}
            >
              <Input.TextArea
                rows={4}
                placeholder="说明要生成的知识结构、适用范围和必须遵循的来源约束"
              />
            </Form.Item>
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item
                  name="assetIdentity"
                  label="资产身份"
                  rules={[{ required: true, whitespace: true, message: "请填写资产身份" }]}
                >
                  <Input placeholder="例如 KNOW.VTE.GUIDE" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item
                  name="subject"
                  label="知识主题"
                  rules={[{ required: true, whitespace: true, message: "请填写知识主题" }]}
                >
                  <Input placeholder="例如 VTE 防治指南" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item
              name="sourceRef"
              label="来源锚点"
              rules={[{ required: true, whitespace: true, message: "请填写可解析的来源锚点" }]}
            >
              <Input placeholder="例如 GL-VTE-2026:v1:section-2" />
            </Form.Item>
            <Row gutter={16}>
              <Col xs={24} md={8}>
                <Form.Item name="trustLevel" label="来源权威" rules={[{ required: true }]}>
                  <Select
                    options={[
                      { value: "A_REGULATION", label: "A 法规" },
                      { value: "B_GUIDELINE", label: "B 权威指南" },
                      { value: "C_CONSENSUS_LITERATURE", label: "C 共识文献" },
                      { value: "D_HOSPITAL", label: "D 院内制度" },
                      { value: "E_FEEDBACK", label: "E 反馈资料" },
                    ]}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="riskLevel" label="候选风险" rules={[{ required: true }]}>
                  <Select
                    options={[
                      { value: "LOW", label: "低风险" },
                      { value: "MEDIUM", label: "中风险" },
                      { value: "HIGH", label: "高风险" },
                    ]}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item
                  name="timeoutSeconds"
                  label="超时秒数"
                  rules={[{ required: true, type: "number", min: 10, max: 300 }]}
                >
                  <Input type="number" min={10} max={300} />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="targetMode" label="目标知识身份" rules={[{ required: true }]}>
              <Radio.Group>
                <Radio value="EXISTING">写入现有身份候选</Radio>
                <Radio value="NEW">创建新身份候选</Radio>
              </Radio.Group>
            </Form.Item>
            <Form.Item
              noStyle
              shouldUpdate={(previous, current) => previous.targetMode !== current.targetMode}
            >
              {({ getFieldValue }) =>
                getFieldValue("targetMode") === "NEW" ? (
                  <Row gutter={16}>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="newIdentityCode"
                        label="新身份编码"
                        rules={[{ required: true, whitespace: true, message: "请填写新身份编码" }]}
                      >
                        <Input placeholder="例如 KNOW.NEW.001" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="newIdentityDomain"
                        label="新身份领域"
                        rules={[{ required: true, message: "请选择新身份领域" }]}
                      >
                        <Select options={KNOWLEDGE_DOMAIN_OPTIONS} />
                      </Form.Item>
                    </Col>
                  </Row>
                ) : (
                  <Form.Item
                    name="targetIdentityId"
                    label="现有知识身份"
                    rules={[{ required: true, message: "请选择目标知识身份" }]}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      options={identities.map((identity) => ({
                        value: identity.id,
                        label: `${identity.subject} · ${identity.identityCode}`,
                      }))}
                      placeholder="选择知识身份"
                    />
                  </Form.Item>
                )
              }
            </Form.Item>
          </Form>
        </Space>
      </Modal>

      <Modal
        title="确认模型外调用途"
        open={Boolean(pendingModelEgressConfirmation)}
        okText="记录确认并重试"
        cancelText="暂不外调"
        confirmLoading={
          confirmModelEgressMutation.isPending || generateModelCandidateMutation.isPending
        }
        onOk={() => modelEgressConfirmationForm.submit()}
        onCancel={() => {
          modelEgressConfirmationForm.resetFields();
          setPendingModelEgressConfirmation(undefined);
        }}
        destroyOnClose
        width={720}
      >
        {pendingModelEgressConfirmation ? (
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Alert
              type="warning"
              showIcon
              message="本次模型生成需要先确认患者上下文外调用途"
              description={
                pendingModelEgressConfirmation.challenge.message ||
                "高敏患者上下文使用已暂停；确认后系统会使用同一生产请求重新生成候选。"
              }
            />
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="模型能力">
                {pendingModelEgressConfirmation.challenge.capabilityCode}
              </Descriptions.Item>
              <Descriptions.Item label="脱敏载荷摘要">
                {pendingModelEgressConfirmation.challenge.payloadHash}
              </Descriptions.Item>
              <Descriptions.Item label="拟供模型使用字段">
                {pendingModelEgressConfirmation.challenge.egressFields.join("、") ||
                  "未返回字段清单"}
              </Descriptions.Item>
              <Descriptions.Item label="模型服务">
                {pendingModelEgressConfirmation.challenge.providerCode || "服务端策略选择"}
              </Descriptions.Item>
            </Descriptions>
            <Form
              form={modelEgressConfirmationForm}
              layout="vertical"
              onFinish={confirmPendingModelEgress}
              preserve={false}
            >
              <Form.Item
                name="purpose"
                label="用途说明"
                rules={[
                  { required: true, whitespace: true, message: "请说明本次模型外调用途" },
                  { min: 6, message: "用途说明至少 6 个字符" },
                ]}
              >
                <Input.TextArea
                  rows={4}
                  maxLength={512}
                  showCount
                  placeholder="说明本次生成候选的业务目的、最小必要患者上下文和确认责任"
                />
              </Form.Item>
            </Form>
          </Space>
        ) : null}
      </Modal>

      <Modal
        title={`定制机构知识${customizeIdentity ? ` · ${customizeIdentity.subject}` : ""}`}
        open={Boolean(customizeIdentity)}
        okText="创建定制草稿"
        cancelText="取消"
        confirmLoading={createCustomization.isPending}
        onOk={() => customizeForm.submit()}
        onCancel={() => {
          customizeForm.resetFields();
          setCustomizeIdentity(undefined);
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
          customizationActionForm.resetFields();
          setCustomizationAction(undefined);
        }}
        destroyOnClose
      >
        <Alert
          type={customizationAction?.type === "publish" ? "warning" : "info"}
          showIcon
          message={customizationActionMessage}
          description="当前操作人核对发布依据；历史版本、证据、差异和审计记录都会保留。"
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
          <Alert
            type={candidateVersion?.riskLevel === "LOW" ? "info" : "warning"}
            showIcon
            message="审核路由"
            description={candidateReviewRouteDescription(candidateVersion?.riskLevel)}
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
                <Descriptions.Item label="生产任务">
                  已留痕，审核结论会保留任务证据
                </Descriptions.Item>
                <Descriptions.Item label="目标管道">
                  <Space size={4} wrap>
                    <Tag color={meta.color}>{meta.label}</Tag>
                    <Tag>{meta.boundaryLabel}</Tag>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="可信度 / 备用能力">
                  <Space direction="vertical" size={2}>
                    <Text>{confidenceText(provenance.confidence)}</Text>
                    <Text>{hospitalFallbackText(provenance)}</Text>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="来源依据">
                  {sourceCitationSummary(provenance.sourceCitations)}
                </Descriptions.Item>
                <Descriptions.Item label="生产时点">
                  {provenance.producedAt ?? "未返回"}
                </Descriptions.Item>
                <Descriptions.Item label="生产证据">
                  <Button
                    type="link"
                    size="small"
                    onClick={() => toggleProvenanceDetails(provenance)}
                  >
                    {isProvenanceDetailsOpen(provenance) ? "收起生产证据" : "生产证据详情"}
                  </Button>
                </Descriptions.Item>
                {isProvenanceDetailsOpen(provenance) ? (
                  <>
                    <Descriptions.Item label="生产任务编号">{provenance.jobCode}</Descriptions.Item>
                    <Descriptions.Item label="模型任务 ID">
                      {provenance.modelTaskId || "未返回"}
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
                    <Descriptions.Item label="技术降级原因">
                      {fallbackText(provenance)}
                    </Descriptions.Item>
                    <Descriptions.Item label="来源引用原文">
                      {provenance.sourceCitations || "未返回来源引用"}
                    </Descriptions.Item>
                    <Descriptions.Item label="生产执行人">
                      {provenance.producedBy ?? "未返回"}
                    </Descriptions.Item>
                  </>
                ) : null}
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
              reason: "",
              qualityGates: [],
            }}
          >
            <Alert
              type="info"
              showIcon
              message="审核对象已锁定为当前候选版本"
              description="审核结论只作用于当前知识版本；正式上线时由平台标准版本或机构生效版本选择该版本，审核环节不绑定上线范围或离线交付文件。"
            />
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
            {platformPublishing && (
              <>
                <Divider orientation="left">平台发布质量校验</Divider>
                <Form.Item
                  name="qualityGates"
                  label="发布质量校验"
                  rules={[
                    {
                      validator: (_, value?: string[]) =>
                        value?.length === KNOWLEDGE_QUALITY_GATE_OPTIONS.length
                          ? Promise.resolve()
                          : Promise.reject(new Error("请确认全部平台发布质量校验")),
                    },
                  ]}
                >
                  <Checkbox.Group options={KNOWLEDGE_QUALITY_GATE_OPTIONS} />
                </Form.Item>
                <Form.Item name="qualitySummary" label="校验说明">
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
