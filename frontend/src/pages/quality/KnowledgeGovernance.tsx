import { AuditOutlined, ReloadOutlined } from "@ant-design/icons";
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
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
  useKnowledgeCandidateDiff,
  useKnowledgeCandidates,
  useKnowledgeIdentities,
  useReviewKnowledgeCandidate,
  type CandidateClassification,
  type KnowledgeAssetVersion,
  type KnowledgeCandidateReviewDecision,
  type KnowledgeDomain,
  type KnowledgeIdentity,
  type KnowledgeIdentityStatus,
} from "@/shared/api/hooks";
import {
  KNOWLEDGE_DOMAIN_OPTIONS,
  KNOWLEDGE_IDENTITY_STATUS_OPTIONS,
} from "@/shared/config/knowledgeReview";
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
};

function versionTitle(version?: KnowledgeAssetVersion) {
  if (!version) return "未返回版本";
  return version.versionLabel || `v${version.versionNo}`;
}

function versionSubtitle(version?: KnowledgeAssetVersion) {
  if (!version) return "暂无版本信息";
  return `${VERSION_STATUS_LABELS[version.status] ?? version.status} · ${version.riskLevel ?? "未分级"} · ${
    version.authorityLevel ?? "来源分级未返回"
  }`;
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

export default function KnowledgeGovernance() {
  const { message } = AntdApp.useApp();
  const [domain, setDomain] = useState<KnowledgeDomain>("GUIDELINE");
  const [status, setStatus] = useState<KnowledgeIdentityStatus>("ACTIVE");
  const [keyword, setKeyword] = useState("");
  const [selectedIdentityId, setSelectedIdentityId] = useState<number>();
  const [selectedCandidateId, setSelectedCandidateId] = useState<number>();
  const [reviewForm] = Form.useForm<ReviewFormValues>();

  const identitiesQuery = useKnowledgeIdentities({
    domain,
    status,
    keyword: keyword.trim() || undefined,
    page: 1,
    size: 20,
    sort: "updatedAt,desc",
  });
  const identities = useMemo(
    () => identitiesQuery.data?.items ?? [],
    [identitiesQuery.data?.items],
  );

  useEffect(() => {
    if (identities.length === 0) {
      setSelectedIdentityId(undefined);
      return;
    }
    if (!selectedIdentityId || !identities.some((identity) => identity.id === selectedIdentityId)) {
      setSelectedIdentityId(identities[0].id);
    }
  }, [identities, selectedIdentityId]);

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

  const diffCandidates = diffQuery.data?.candidates ?? [];
  const diffClassifications = diffQuery.data?.classifications ?? classifications;
  const selectedClassification = classificationFor(diffClassifications, selectedCandidateId);
  const activeVersion =
    diffCandidates.find((version) => version.id === selectedClassification?.activeVersionId) ??
    diffCandidates.find((version) => version.status === "ACTIVE");
  const candidateVersion =
    diffCandidates.find((version) => version.id === selectedCandidateId) ?? selectedCandidate;

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
    });
  }

  async function reviewCandidate(decision: KnowledgeCandidateReviewDecision) {
    if (!selectedCandidateId) return;
    try {
      const values = await reviewForm.validateFields();
      await reviewMutation.mutateAsync({
        candidateId: selectedCandidateId,
        packageVersion: values.packageVersion,
        request: {
          decision,
          reason: values.reason.trim(),
        },
        idempotencyKey: `knowledge-review-${selectedCandidateId}-${decision.toLowerCase()}`,
      });
      message.success(
        decision === "APPROVE" ? "候选已通过审核并交由权威替换流程" : "候选已驳回并留档",
      );
      await Promise.all([identitiesQuery.refetch(), candidatesQuery.refetch()]);
    } catch (error) {
      message.error(getApiErrorMessage(error, "知识候选审核失败"));
    }
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
        </Space>
      ),
    },
    {
      title: "领域 / 状态",
      key: "domain",
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Tag>{record.domain}</Tag>
          <Tag color={record.status === "ACTIVE" ? "success" : "default"}>{record.status}</Tag>
        </Space>
      ),
    },
    {
      title: "专科 / 当前版本",
      key: "scope",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.specialtyId || "未限定专科"}</Text>
          <Text type="secondary">currentVersionId: {record.currentVersionId ?? "无"}</Text>
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Button
          aria-label="查看候选"
          type={record.id === selectedIdentityId ? "primary" : "default"}
          onClick={() => setSelectedIdentityId(record.id)}
        >
          查看候选
        </Button>
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
              {CLASSIFICATION_LABELS[classification?.classification ?? ""] ??
                classification?.classification ??
                "未返回判定"}
            </Tag>
            <Tag color={tagColorForReview(classification?.reviewStatus)}>
              {REVIEW_STATUS_LABELS[classification?.reviewStatus ?? ""] ??
                classification?.reviewStatus ??
                "未返回状态"}
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
            sourceDocumentId: {record.sourceDocumentId ?? "无"} / sourceVersionId:{" "}
            {record.sourceVersionId ?? "无"}
          </Text>
          <Space size={4} wrap>
            <Tag color={RISK_COLORS[record.riskLevel ?? ""] ?? "default"}>
              {record.riskLevel ?? "未分级"}
            </Tag>
            <Tag>{record.authorityLevel ?? "来源未分级"}</Tag>
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
      description: "知识候选由 KNOW-02 工作流分流后展示，本页不触发生成。",
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
        description="候选只来自真实来源导入或 KNOW-02 分流，本页不生成候选。"
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

  return (
    <>
      <PageShell
        title="知识治理"
        description="统一审核知识候选并维护结构化诊断知识"
        extras={
          <Button
            aria-label="刷新知识治理"
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
              children:
                pageState === "ready" ? (
                  <Space direction="vertical" size="large" className="mk-full-width">
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

                    <Card title="默认筛选">
                      <Row gutter={[16, 16]}>
                        <Col xs={24} md={8}>
                          <Select
                            aria-label="知识域"
                            className="mk-full-width"
                            value={domain}
                            options={KNOWLEDGE_DOMAIN_OPTIONS}
                            onChange={(value) => setDomain(value)}
                          />
                        </Col>
                        <Col xs={24} md={8}>
                          <Select
                            aria-label="身份状态"
                            className="mk-full-width"
                            value={status}
                            options={KNOWLEDGE_IDENTITY_STATUS_OPTIONS}
                            onChange={(value) => setStatus(value)}
                          />
                        </Col>
                        <Col xs={24} md={8}>
                          <Input.Search
                            aria-label="知识关键词"
                            placeholder="按主题或编码搜索"
                            allowClear
                            onSearch={(value) => setKeyword(value)}
                          />
                        </Col>
                      </Row>
                    </Card>

                    <Card title="知识身份台账">
                      <Table
                        rowKey="id"
                        columns={identityColumns}
                        dataSource={identities}
                        pagination={false}
                        size="middle"
                      />
                    </Card>

                    <Card title="待审候选">{candidatePanel}</Card>
                  </Space>
                ) : (
                  <PageState state={pageState} {...pageStateProps} />
                ),
            },
            {
              key: "diagnosis",
              label: "诊断知识",
              children: <DiagnosisKnowledgePanel />,
            },
          ]}
        />
      </PageShell>

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
    </>
  );
}
