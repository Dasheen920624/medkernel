import { AuditOutlined, BranchesOutlined, ReloadOutlined, SwapOutlined } from "@ant-design/icons";
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
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useDeprecateKnowledgeIdentity,
  useCreateKnowledgeCustomization,
  useKnowledgeCustomizations,
  useKnowledgeCandidateDiff,
  useKnowledgeCandidates,
  useKnowledgeIdentities,
  usePublishKnowledgeCustomization,
  useRestorePlatformKnowledge,
  useReviewKnowledgeCandidate,
  useSecurityProfile,
  type CandidateClassification,
  type KnowledgeAssetVersion,
  type KnowledgeCandidateReviewDecision,
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
} from "@/shared/config/knowledgeReview";
import { platformTenantId } from "@/shared/config/tenantDictionary";
import { OrgUnitSelect } from "@/shared/ui/OrgUnitSelect";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { SourceInfo } from "@/shared/ui/SourceInfo";
import DiagnosisKnowledgePanel from "./DiagnosisKnowledgePanel";

const { Text } = Typography;

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

const RISK_COLORS: Record<string, "default" | "success" | "warning" | "error"> = {
  LOW: "success",
  MEDIUM: "warning",
  HIGH: "error",
};

type ReviewFormValues = {
  packageVersion: string;
  reason: string;
  signatureId?: string;
  signerId?: string;
  signerName?: string;
  signedAt?: string;
  signatureHash?: string;
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
  if (status === "DUPLICATE_SKIPPED") return "default";
  return "processing";
}

function customizationStatusColor(status: string) {
  if (status === "ACTIVE") return "success";
  if (status === "DRAFT") return "processing";
  return "default";
}

export default function KnowledgeGovernance() {
  const { message } = AntdApp.useApp();
  const [domain, setDomain] = useState<KnowledgeDomain>("GUIDELINE");
  const [status, setStatus] = useState<KnowledgeIdentityStatus>("ACTIVE");
  const [keyword, setKeyword] = useState("");
  const [identityPage, setIdentityPage] = useState(1);
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
  const security = useSecurityProfile();

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
  const customizationsQuery = useKnowledgeCustomizations(
    Boolean(currentTenantId && !isPlatformTenant),
  );
  const createCustomization = useCreateKnowledgeCustomization();
  const publishCustomization = usePublishKnowledgeCustomization();
  const restorePlatformKnowledge = useRestorePlatformKnowledge();
  const canCustomize =
    !isPlatformTenant &&
    security.data?.permissions.some((permission) => permission.code === "knowledge.write");
  const canPublishCustomization =
    security.data?.permissions.some((permission) => permission.code === "knowledge.publish") &&
    security.data?.permissions.some((permission) => permission.code === "tenant.override");
  const canRestoreCustomization =
    security.data?.permissions.some((permission) => permission.code === "knowledge.withdraw") &&
    security.data?.permissions.some((permission) => permission.code === "tenant.override");

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
  const candidatesQuery = useKnowledgeCandidates(selectedIdentityId);
  const candidateResponse = candidatesQuery.data;
  const candidates = useMemo(() => candidateResponse?.candidates ?? [], [candidateResponse]);
  const classifications = useMemo(
    () => candidateResponse?.classifications ?? [],
    [candidateResponse],
  );
  const selectedCandidate = candidates.find((candidate) => candidate.id === selectedCandidateId);
  const diffQuery = useKnowledgeCandidateDiff(selectedCandidateId);
  const reviewMutation = useReviewKnowledgeCandidate();
  const retirementMutation = useDeprecateKnowledgeIdentity();

  const diffCandidates = diffQuery.data?.candidates ?? [];
  const diffClassifications = diffQuery.data?.classifications ?? classifications;
  const selectedClassification = classificationFor(diffClassifications, selectedCandidateId);
  const activeVersion =
    diffCandidates.find((version) => version.id === selectedClassification?.activeVersionId) ??
    diffCandidates.find((version) => version.status === "ACTIVE");
  const candidateVersion =
    diffCandidates.find((version) => version.id === selectedCandidateId) ?? selectedCandidate;
  const platformPublishing = security.data?.dataScope.tenantId === platformTenantId;
  const publishEvidenceRequired = platformPublishing || candidateVersion?.riskLevel === "HIGH";

  const pendingCount = useMemo(
    () =>
      classifications.filter((item) => item.reviewStatus === "PENDING_REPLACEMENT_REVIEW").length,
    [classifications],
  );
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
      packageVersion: candidate.versionLabel || candidate.versionNo,
      reason: "",
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
          ...(publishEvidence ? { publishEvidence } : {}),
        },
        idempotencyKey: `knowledge-review-${classificationReviewId}-${decision.toLowerCase()}`,
      });
      message.success(
        decision === "APPROVE" ? "候选已通过审核并交由权威替换流程" : "候选已驳回并留档",
      );
      await Promise.all([identitiesQuery.refetch(), candidatesQuery.refetch()]);
    } catch (error) {
      message.error(getApiErrorMessage(error, "知识候选审核失败"));
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
    if (
      customizationsQuery.data?.some(
        (customization) => customization.localIdentityId === identity.id,
      )
    ) {
      return "LOCAL_CUSTOMIZATION";
    }
    return "LOCAL_ORIGINAL";
  }

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
        pagination={false}
        size="middle"
      />
    );
  }

  const customizationColumns: ColumnsType<KnowledgeCustomization> = [
    {
      title: "知识来源",
      key: "source",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
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
          dataSource={customizationsQuery.data ?? []}
          locale={{ emptyText: "当前机构全部使用平台标准" }}
          pagination={{ pageSize: 20, hideOnSinglePage: true }}
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
        {customizationListContent}
      </Space>
    );
  }

  let customizationActionMessage = "恢复后新请求将重新使用平台标准";
  if (customizationAction?.type === "publish") {
    customizationActionMessage =
      customizationAction.item.riskLevel === "HIGH"
        ? "高风险知识必须完成电子签名"
        : "发布后将接管所选机构的知识解析";
  }

  return (
    <>
      <PageShell
        title="知识审核与发布"
        description="统一审核知识候选并维护结构化诊断知识"
        extras={
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
        }
      >
        <Tabs
          items={[
            {
              key: "review",
              label: "候选审核",
              children: (
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
                            <Statistic
                              title="待审核候选总数"
                              value={pendingCount}
                              prefix={<AuditOutlined />}
                            />
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
              ),
            },
            {
              key: "diagnosis",
              label: "诊断知识",
              children: <DiagnosisKnowledgePanel />,
            },
            {
              key: "institution",
              label: "机构知识",
              children: institutionKnowledgeContent,
            },
          ]}
        />
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

          <Form
            form={reviewForm}
            layout="vertical"
            initialValues={{
              packageVersion: candidateVersion?.versionLabel || candidateVersion?.versionNo,
              reason: "",
              qualityGates: [],
            }}
          >
            <Form.Item
              name="packageVersion"
              label="审核上下文包版本"
              rules={[{ required: true, message: "请填写审核上下文包版本" }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="reason"
              label="审核理由"
              rules={[{ required: true, message: "请填写审核理由" }]}
            >
              <Input.TextArea rows={4} />
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
