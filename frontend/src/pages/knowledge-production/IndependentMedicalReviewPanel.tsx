import { ReloadOutlined } from "@ant-design/icons";
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Drawer,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import { useState } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  type ModelEvaluationCaseEvidence,
  type ModelEvaluationRunDetail,
  type ModelEvaluationRunSummary,
  type ModelEvaluationStatus,
  useModelEvaluationRunDetail,
  useModelEvaluationRuns,
  useSecurityProfile,
  useSignOffModelEvaluation,
} from "@/shared/api/hooks";
import { useExpertModeStore } from "@/shared/lib/expertModeStore";
import { ExpertModeToggle } from "@/shared/ui/ExpertModeToggle";
import { PageState } from "@/shared/ui/PageState";
import { canUseExpertMode } from "@/shared/ui/expertModeAccess";

const { Paragraph, Text } = Typography;

const STATUS_META: Record<ModelEvaluationStatus, { label: string; color: string }> = {
  PENDING_REVIEW: { label: "待复核", color: "processing" },
  PASSED: { label: "已通过", color: "success" },
  FAILED: { label: "已阻断", color: "error" },
};

const FAILURE_LABELS: Record<string, string> = {
  EXPECTED_PHRASE_MISSING: "未命中安全期望",
  SOURCE_REFERENCE_MISSING: "来源引用未核验",
  RED_LINE_BREACH: "突破医学红线",
  EVIDENCE_FORMAT_INVALID: "证据格式异常",
};

const CAPABILITY_LABELS: Record<string, string> = {
  "knowledge.discovery": "临床知识关联发现",
  "knowledge.extract": "病历语义实体提取",
  "terminology.map": "标准术语映射",
  "rule.draft": "临床规则草案拟定",
  "pathway.draft": "临床路径草案拟定",
  "cdss.explain": "临床决策解释",
  "quality.semantic-check": "病历内涵质控",
  "followup.draft": "随访草案拟定",
};

function capabilityLabel(capabilityCode: string) {
  return CAPABILITY_LABELS[capabilityCode] ?? "其他医学能力";
}

function formatDateTime(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function reviewAlert(detail: ModelEvaluationRunDetail) {
  if (detail.run.status === "PASSED") {
    if (detail.releaseCurrent) {
      return {
        type: "success" as const,
        message: "已由独立专家签署放行",
        description: "本次签署仅对当前运行制品、当前基准集和当前逐例证据有效。",
      };
    }
    return {
      type: "warning" as const,
      message: "历史制品签署仅保留审计，不可用于当前放行",
      description: detail.reviewBlockReason ?? "该评测属于历史运行制品，必须在当前制品重新运行。",
    };
  }
  if (detail.reviewable) {
    return {
      type: "success" as const,
      message: "证据完整且基准未变化",
      description: "请逐例核对后，由非评测执行人完成独立签字。",
    };
  }
  return {
    type: "error" as const,
    message: "当前运行不可签字",
    description: detail.reviewBlockReason ?? "请逐例核对后，由非评测执行人完成独立签字。",
  };
}

function evidenceState(evidence: ModelEvaluationCaseEvidence) {
  if (evidence.redLineBreach) return <Tag color="error">红线突破</Tag>;
  return evidence.passed ? <Tag color="success">通过</Tag> : <Tag color="error">未通过</Tag>;
}

function citationReviewLabel(evidence: ModelEvaluationCaseEvidence) {
  if (!evidence.citationRequired) return "无需引用";
  return evidence.citationVerified ? "已核验" : "未通过";
}

function redLineReviewLabel(evidence: ModelEvaluationCaseEvidence) {
  if (!evidence.redLineCase) return "非红线用例";
  return evidence.redLineBreach ? "已突破" : "未突破";
}

export default function IndependentMedicalReviewPanel() {
  const security = useSecurityProfile();
  const allowed =
    security.data?.permissions.some((permission) => permission.code === "llm.eval.manage") ?? false;
  const [status, setStatus] = useState<ModelEvaluationStatus>("PENDING_REVIEW");
  const [page, setPage] = useState(1);
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null);
  const [signModalOpen, setSignModalOpen] = useState(false);
  const [evidenceAcknowledged, setEvidenceAcknowledged] = useState(false);
  const [reviewComment, setReviewComment] = useState("");
  const runs = useModelEvaluationRuns({ status, page, size: 20 }, allowed);
  const detail = useModelEvaluationRunDetail(selectedRunId, allowed && selectedRunId !== null);
  const signOff = useSignOffModelEvaluation();
  const globalExpertMode = useExpertModeStore((state) => state.enabled);
  const expertMode = canUseExpertMode(security.data) && globalExpertMode;

  if (security.isLoading) {
    return (
      <Card title="独立复核">
        <PageState state="loading" />
      </Card>
    );
  }
  if (security.isError) {
    return (
      <Card title="独立复核">
        <PageState state="error" onRetry={() => void security.refetch()} />
      </Card>
    );
  }
  if (!allowed) {
    return (
      <Card title="独立复核">
        <PageState
          state="forbidden"
          description="由质量治理专家逐例核查并独立签署；当前职责可继续查看其他生产步骤。"
        />
      </Card>
    );
  }
  if (runs.isLoading) {
    return (
      <Card title="独立复核">
        <PageState state="loading" />
      </Card>
    );
  }
  if (runs.isError) {
    return (
      <Card title="独立复核">
        <PageState
          state="error"
          description={getApiErrorMessage(runs.error, "请稍后重试，或凭追踪号联系信息科。")}
          onRetry={() => void runs.refetch()}
        />
      </Card>
    );
  }

  const pageData = runs.data;
  const selected = detail.data;
  const selectedAlert = selected ? reviewAlert(selected) : undefined;
  const currentUserIsAuthor = selected?.run.createdBy === security.data?.userId;
  const canSign = Boolean(selected?.reviewable && !currentUserIsAuthor);
  const trimmedComment = reviewComment.trim();
  const confirmEnabled = evidenceAcknowledged && trimmedComment.length >= 10;

  const resetSignModal = () => {
    setSignModalOpen(false);
    setEvidenceAcknowledged(false);
    setReviewComment("");
  };

  const submitSignOff = async () => {
    if (!selected || !confirmEnabled) return;
    try {
      await signOff.mutateAsync({
        runId: selected.run.runId,
        evidenceAcknowledged: true,
        reviewComment: trimmedComment,
      });
      resetSignModal();
      message.success("独立医学复核已签字留痕");
    } catch (error) {
      message.error(getApiErrorMessage(error, "专家复核签字失败，请重新核查运行状态。"));
    }
  };

  const columns = [
    {
      title: "评测时间",
      dataIndex: "createdAt",
      key: "createdAt",
      width: 180,
      render: (value: string) => formatDateTime(value),
    },
    { title: "模型版本", dataIndex: "modelVersion", key: "modelVersion", width: 160 },
    {
      title: "医学能力",
      dataIndex: "capabilityCode",
      key: "capabilityCode",
      width: 170,
      render: (value: string) => capabilityLabel(value),
    },
    {
      title: "逐例结果",
      key: "caseResult",
      width: 150,
      render: (_: unknown, item: ModelEvaluationRunSummary) =>
        `${item.passedCases}/${item.totalCases} 通过`,
    },
    {
      title: "安全判定",
      key: "safety",
      width: 130,
      render: (_: unknown, item: ModelEvaluationRunSummary) =>
        item.fakeCitationDetected || item.redLineBreach || item.hallucinationDetected ? (
          <Tag color="error">已阻断</Tag>
        ) : (
          <Tag color="success">未见红线</Tag>
        ),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 100,
      render: (value: ModelEvaluationStatus) => (
        <Tag color={STATUS_META[value]?.color}>{STATUS_META[value]?.label ?? value}</Tag>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 110,
      fixed: "right" as const,
      render: (_: unknown, item: ModelEvaluationRunSummary) => (
        <Button type="link" onClick={() => setSelectedRunId(item.runId)}>
          核查证据
        </Button>
      ),
    },
  ];

  const reviewContent = (
    <>
      <Space direction="vertical" size="large" className="mk-full-width">
        <Alert
          type="info"
          showIcon
          message="签字只放行当前基准集与本次逐例证据"
          description="旧运行、缺失逐例证据、基准已变化、伪造引用或医学红线突破均会阻断签字。"
        />
        <Card>
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Select<ModelEvaluationStatus>
              aria-label="复核状态"
              value={status}
              options={Object.entries(STATUS_META).map(([value, meta]) => ({
                value: value as ModelEvaluationStatus,
                label: meta.label,
              }))}
              onChange={(nextStatus) => {
                setStatus(nextStatus);
                setPage(1);
              }}
            />
            {!pageData || pageData.items.length === 0 ? (
              <PageState
                state="empty"
                title={`${STATUS_META[status].label}运行为空`}
                description="当前筛选条件下没有医学回归运行。"
              />
            ) : (
              <Table<ModelEvaluationRunSummary>
                rowKey="runId"
                columns={columns}
                dataSource={pageData.items}
                scroll={{ x: 970 }}
                pagination={{
                  current: pageData.page,
                  pageSize: pageData.size,
                  total: pageData.total,
                  showSizeChanger: false,
                  onChange: setPage,
                }}
              />
            )}
          </Space>
        </Card>
      </Space>

      <Drawer
        title="逐例医学回归证据"
        width="min(760px, 100vw)"
        open={selectedRunId !== null}
        onClose={() => {
          setSelectedRunId(null);
          resetSignModal();
        }}
        extra={
          canSign ? (
            <Button type="primary" onClick={() => setSignModalOpen(true)}>
              专家复核签字
            </Button>
          ) : null
        }
      >
        {detail.isLoading ? <PageState state="loading" /> : null}
        {detail.isError ? (
          <PageState
            state="error"
            description={getApiErrorMessage(detail.error, "逐例证据读取失败，请重试。")}
            onRetry={() => void detail.refetch()}
          />
        ) : null}
        {selected && selectedAlert ? (
          <Space direction="vertical" size="large" className="mk-full-width">
            <Alert
              type={selectedAlert.type}
              showIcon
              message={selectedAlert.message}
              description={selectedAlert.description}
            />
            {currentUserIsAuthor ? (
              <Alert
                type="warning"
                showIcon
                message="评测执行人不得复核本人运行"
                description="请由另一名具备质量治理职责并完成 MFA 的专家独立复核。"
              />
            ) : null}
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label="模型版本">{selected.run.modelVersion}</Descriptions.Item>
              <Descriptions.Item label="医学能力">
                {capabilityLabel(selected.run.capabilityCode)}
              </Descriptions.Item>
              <Descriptions.Item label="运行时间">
                {formatDateTime(selected.run.createdAt)}
              </Descriptions.Item>
              {selected.run.reviewer ? (
                <Descriptions.Item label="独立审核人">{selected.run.reviewer}</Descriptions.Item>
              ) : null}
              {selected.run.signedAt ? (
                <Descriptions.Item label="签署时间">
                  {formatDateTime(selected.run.signedAt)}
                </Descriptions.Item>
              ) : null}
              {selected.run.reviewComment ? (
                <Descriptions.Item label="复核意见">{selected.run.reviewComment}</Descriptions.Item>
              ) : null}
              {expertMode ? (
                <>
                  <Descriptions.Item label="服务商代码">
                    {selected.run.providerCode}
                  </Descriptions.Item>
                  <Descriptions.Item label="能力代码">
                    {selected.run.capabilityCode}
                  </Descriptions.Item>
                  <Descriptions.Item label="提示词版本">
                    {selected.run.promptVersion}
                  </Descriptions.Item>
                  <Descriptions.Item label="工具版本">{selected.run.toolVersion}</Descriptions.Item>
                  <Descriptions.Item label="运行制品指纹">
                    {selected.run.releaseFingerprint ?? "历史运行未记录"}
                  </Descriptions.Item>
                  <Descriptions.Item label="运行编号">{selected.run.runId}</Descriptions.Item>
                </>
              ) : null}
            </Descriptions>
            {selected.cases.map((item, index) => (
              <Card key={item.evidenceId} title={`用例 ${index + 1}`} extra={evidenceState(item)}>
                <Space direction="vertical" size="middle" className="mk-full-width">
                  <div>
                    <Text strong>评测输入</Text>
                    <Paragraph>{item.caseInput}</Paragraph>
                  </div>
                  <div>
                    <Text strong>安全期望</Text>
                    <Paragraph>{item.expectedPhrase}</Paragraph>
                  </div>
                  <div>
                    <Text strong>模型真实输出</Text>
                    <Paragraph>{item.outputContent}</Paragraph>
                  </div>
                  <Descriptions size="small" column={1} bordered>
                    <Descriptions.Item label="来源依据">{item.sourceReference}</Descriptions.Item>
                    <Descriptions.Item label="引用核验">
                      {citationReviewLabel(item)}
                    </Descriptions.Item>
                    <Descriptions.Item label="医学红线">
                      {redLineReviewLabel(item)}
                    </Descriptions.Item>
                    {item.failureReasons.length > 0 ? (
                      <Descriptions.Item label="未通过原因">
                        {item.failureReasons
                          .map((reason) => FAILURE_LABELS[reason] ?? reason)
                          .join("；")}
                      </Descriptions.Item>
                    ) : null}
                    {expertMode ? (
                      <>
                        <Descriptions.Item label="用例版本">{item.caseVersion}</Descriptions.Item>
                        <Descriptions.Item label="红线类型">
                          {item.redLineType ?? "—"}
                        </Descriptions.Item>
                        <Descriptions.Item label="原始引用载荷">
                          {item.sourceCitations || "—"}
                        </Descriptions.Item>
                        <Descriptions.Item label="证据编号">{item.evidenceId}</Descriptions.Item>
                      </>
                    ) : null}
                  </Descriptions>
                </Space>
              </Card>
            ))}
            {selected.run.reviewer ? (
              <Card title="已签字复核">
                <Paragraph>{selected.run.reviewComment}</Paragraph>
                <Text type="secondary">
                  {selected.run.reviewer} · {formatDateTime(selected.run.signedAt)}
                </Text>
              </Card>
            ) : null}
          </Space>
        ) : null}
      </Drawer>

      <Modal
        title="确认专家复核签字"
        open={signModalOpen}
        okText="确认专家复核"
        cancelText="取消"
        okButtonProps={{ disabled: !confirmEnabled, loading: signOff.isPending }}
        onOk={() => void submitSignOff()}
        onCancel={resetSignModal}
        destroyOnClose
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="warning"
            showIcon
            message="签字后该运行将成为模型版本上线门禁依据"
            description="签字不会自动开立医嘱，也不能替代临床医师对具体患者的判断。"
          />
          <Checkbox
            checked={evidenceAcknowledged}
            onChange={(event) => setEvidenceAcknowledged(event.target.checked)}
          >
            我已逐例核对模型输出、来源引用与医学红线判定
          </Checkbox>
          <label htmlFor="model-independent-review-comment">
            <Text strong>复核意见</Text>
          </label>
          <Input.TextArea
            id="model-independent-review-comment"
            aria-label="复核意见"
            value={reviewComment}
            rows={4}
            maxLength={1000}
            showCount
            placeholder="说明核查结论与放行依据（至少 10 个字符）"
            onChange={(event) => setReviewComment(event.target.value)}
          />
        </Space>
      </Modal>
    </>
  );

  return (
    <Card
      title="独立复核"
      extra={
        <Space wrap>
          <Button
            icon={<ReloadOutlined />}
            loading={runs.isFetching}
            onClick={() => void runs.refetch()}
          >
            刷新
          </Button>
          <ExpertModeToggle securityProfile={security.data} />
        </Space>
      }
    >
      {reviewContent}
    </Card>
  );
}
