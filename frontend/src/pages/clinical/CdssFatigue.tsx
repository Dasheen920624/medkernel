import { useState } from "react";
import {
  Table,
  Button,
  Drawer,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  Card,
  Descriptions,
  Badge,
  Alert,
  message,
  Tabs,
  Row,
  Col,
  Timeline,
  Progress,
  Empty,
} from "antd";
import type { BadgeProps, TableProps } from "antd";
import {
  BugOutlined,
  BookOutlined,
  CheckCircleOutlined,
  DashboardOutlined,
  FireOutlined,
  ReadOutlined,
  AuditOutlined,
  UserOutlined,
  CalendarOutlined,
  ExclamationCircleOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { ContextSnapshotSelector } from "@/shared/ui/ContextSnapshotSelector";
import { applyApiFieldErrors, getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import {
  useClinicalRecommendationCards,
  useContextSnapshotDetail,
  useContextSnapshots,
  useEvaluateRecommendations,
  useRecommendationCardDetail,
  useRecommendationCardSources,
  useRecommendationStats,
  useSubmitRecommendationFeedback,
  useRecommendationFatigueSignals,
  useRecommendationTriggerDiagnose,
} from "@/shared/api/hooks";
import type {
  ClinicalRecommendationCard,
  RecommendationCard,
  RecommendationSource,
  RecommendationFatigueSignal,
  RecommendationCardStatus,
  RecommendationRiskLevel,
  RecommendationFeedbackType,
} from "@/shared/api/hooks";
import {
  customerDisplayText,
  customerEnumLabel,
  riskLabel,
} from "@/shared/config/customerLabels";
import { roleLabel } from "@/shared/config/roleCatalog";
import styles from "./Clinical.module.css";

const { TextArea } = Input;
const { Option } = Select;
const FATIGUE_POLICY_CONFIG_KEY = "medkernel.cdss.fatigue.policy";

type RecommendationBadgeStatus = Exclude<BadgeProps["status"], undefined>;
type RecommendationFatigueGovernance = {
  isNonSuppressible: boolean;
  label: string;
  color: string;
  description: string;
};

const interruptLevelLabels: Record<string, string> = {
  HARD: "强阻断",
  STRONG_INTERRUPTIVE: "强阻断",
  SOFT: "软提醒",
  NONE: "不打断",
};

const scenarioLabels: Record<string, string> = {
  WARD_ORDER: "住院医嘱",
  "order-sign": "医嘱签署",
  "patient-view": "患者查看",
  DISCHARGE: "出院",
  DEFAULT: "通用场景",
};

const FORBIDDEN_ERROR_CODES = new Set([
  "ENG-API-004",
  "ENG-BASE-002",
  "ENG-BASE-003",
  "ENG-BASE-004",
  "PERMISSION_DENIED",
]);

function isForbiddenApiError(code?: string) {
  return Boolean(code && FORBIDDEN_ERROR_CODES.has(code));
}

function isClinicalRedlineCard(
  card?: Pick<RecommendationCard, "cardCode" | "fatigueKey" | "riskLevel" | "sourceSummary"> | null,
): boolean {
  const fatigueKey = card?.fatigueKey?.toUpperCase() ?? "";
  const cardCode = card?.cardCode?.toUpperCase() ?? "";
  return (
    fatigueKey.startsWith("REDLINE:") ||
    cardCode.includes("REDLINE") ||
    card?.riskLevel === "CRITICAL" ||
    card?.sourceSummary?.includes("红线") === true
  );
}

function getFatigueGovernance(
  card?: Pick<
    RecommendationCard,
    "cardCode" | "fatigueKey" | "interruptLevel" | "riskLevel" | "sourceSummary"
  > | null,
): RecommendationFatigueGovernance {
  if (isClinicalRedlineCard(card)) {
    return {
      isNonSuppressible: true,
      label: "红线不可抑制",
      color: "red",
      description: "疲劳信号仅用于审计和质控，不会静音或降级临床安全红线。",
    };
  }
  if (
    card?.riskLevel === "HIGH" ||
    card?.interruptLevel === "HARD" ||
    card?.interruptLevel === "STRONG_INTERRUPTIVE"
  ) {
    return {
      isNonSuppressible: true,
      label: "高风险不可抑制",
      color: "volcano",
      description: "高风险或强打断提醒必须保留医师确认链路，不参与疲劳静音。",
    };
  }
  return {
    isNonSuppressible: false,
    label: "按科室阈值治理",
    color: "blue",
    description: "低价值重复提醒按配置中心阈值进入低打扰治理。",
  };
}

function getFatigueProgressPercent(signal: RecommendationFatigueSignal): number {
  if (signal.governanceThreshold <= 0) return 100;
  return Math.min(100, Math.floor((signal.triggerCount / signal.governanceThreshold) * 100));
}

type RecommendationJourneyStep = {
  title: string;
  status: string;
  description: string;
  evidence?: string;
};

function textOrDash(value?: string | null) {
  return value && value.trim() ? value : "暂无";
}

function getRecommendationJourneySteps(
  detail: {
    card: RecommendationCard;
    trigger?: {
      triggerId?: string;
      triggerType?: string;
      sourceEventId?: string;
      patientPathwayId?: string;
      traceId?: string;
    };
    feedback: Array<{
      feedbackType: RecommendationFeedbackType;
      operatorRole?: string;
      reasonText?: string;
    }>;
    traceId: string;
  },
  sources: RecommendationSource[] | undefined,
  diagnose?: {
    traceId?: string;
    ruleId?: string;
    explanationSnapshot?: string;
    statusHistory?: Array<{ summary?: string }>;
  } | null,
): RecommendationJourneyStep[] {
  const sourceTitle = sources?.[0]?.title ?? detail.card.sourceSummary;
  const latestFeedback = detail.feedback[0];
  return [
    {
      title: "触发事件",
      status: textOrDash(detail.trigger?.triggerType || detail.card.scenarioCode),
      description: "来自患者上下文、医嘱签署、检验回报或外部系统事件。",
      evidence: detail.trigger?.sourceEventId || detail.trigger?.triggerId || detail.card.triggerId,
    },
    {
      title: "命中规则",
      status: textOrDash(diagnose?.ruleId || detail.card.cardCode || detail.card.fatigueKey),
      description: "规则引擎与红线检查给出风险级别和推荐动作。",
      evidence: diagnose?.explanationSnapshot || detail.card.summary,
    },
    {
      title: "知识来源",
      status: sourceTitle ? "已有来源" : "待补来源",
      description: sourceTitle || "该卡片暂未返回来源解释，页面不做兜底伪造。",
      evidence: sources?.[0]?.sourceRef,
    },
    {
      title: "路径上下文",
      status: textOrDash(detail.trigger?.patientPathwayId),
      description: "把推荐放回患者路径位置，辅助医生判断下一步。",
      evidence: detail.trigger?.patientPathwayId,
    },
    {
      title: "待办 / 通知",
      status: detail.card.status,
      description: "推荐卡会同步为医生待办或通知；状态以后端闭环为准。",
      evidence: `traceId: ${diagnose?.traceId || detail.trigger?.traceId || detail.traceId}`,
    },
    {
      title: "医生反馈",
      status: latestFeedback ? latestFeedback.feedbackType : "待处理",
      description: latestFeedback?.reasonText || "医生采纳或不采纳时必须留下真实理由。",
      evidence: latestFeedback?.operatorRole
        ? roleLabel(latestFeedback.operatorRole)
        : undefined,
    },
    {
      title: "药师复核",
      status: detail.feedback.some((item) => item.operatorRole === "PHARMACIST")
        ? "已复核"
        : "按需复核",
      description: "高风险 DDI 覆盖进入药师或质控复核视角。",
      evidence: diagnose?.statusHistory?.[0]?.summary,
    },
  ];
}

/** 计算输入载荷的真实 SHA-256 摘要（不伪造哈希）。 */
export default function CdssFatigue() {
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [triggerModalVisible, setTriggerModalVisible] = useState<boolean>(false);
  const [diagnoseDrawerVisible, setDiagnoseDrawerVisible] = useState<boolean>(false);
  const [snapshotPatientId, setSnapshotPatientId] = useState("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState("");
  const [selectedSnapshotId, setSelectedSnapshotId] = useState("");

  // 分页与过滤状态
  const [page, setPage] = useState<number>(1);
  const [size] = useState<number>(10);
  const [statusFilter, setStatusFilter] = useState<RecommendationCardStatus | undefined>(undefined);
  const [riskFilter, setRiskFilter] = useState<RecommendationRiskLevel | undefined>(undefined);
  const [quickSearch, setQuickSearch] = useState<string>("");

  // 医师反馈表单绑定
  const [feedbackForm] = Form.useForm();
  const [triggerForm] = Form.useForm();

  // 真实推荐卡列表：后端分页 + 服务端过滤，页面不伪造卡片。
  const {
    data: cardsPage,
    isLoading: cardsLoading,
    isError: cardsError,
    error: cardsQueryError,
    refetch: refetchCards,
  } = useClinicalRecommendationCards({
    status: statusFilter,
    riskLevel: riskFilter,
    page,
    size,
  });
  const cards: ClinicalRecommendationCard[] = cardsPage?.items ?? [];
  const quickSearchTerm = quickSearch.trim().toLowerCase();
  const visibleCards = quickSearchTerm
    ? cards.filter((card) =>
        [
          card.cardId,
          card.triggerId,
          card.patientId,
          card.encounterId,
          card.patientPathwayId,
          card.traceId,
          card.title,
          card.sourceSummary,
        ].some((value) => value?.toLowerCase().includes(quickSearchTerm)),
      )
    : cards;
  const cardsParsedError = cardsError ? parseApiError(cardsQueryError, "推荐卡列表加载失败") : null;
  let cardsPageState: "forbidden" | "error" | "ready" = "ready";
  if (cardsParsedError) {
    cardsPageState = isForbiddenApiError(cardsParsedError.code) ? "forbidden" : "error";
  }

  const { data: statsData } = useRecommendationStats({
    status: statusFilter,
    riskLevel: riskFilter,
  });

  const { data: detailData, refetch: refetchDetail } = useRecommendationCardDetail(
    selectedCardId || "",
  );

  const { data: sourcesData, refetch: refetchSources } = useRecommendationCardSources(
    selectedCardId || "",
  );

  // 获取该卡片相关的疲劳静音治理信号
  const { data: fatigueSignalsData, refetch: refetchFatigue } = useRecommendationFatigueSignals({
    fatigueKey: detailData?.card.fatigueKey || undefined,
    page: 1,
    size: 20,
  });

  // 获取该卡片的诊断可审计链
  const { data: diagnoseData, refetch: refetchDiagnose } = useRecommendationTriggerDiagnose(
    detailData?.card.triggerId || "",
  );

  const patientFilter = snapshotPatientId.trim();
  const encounterFilter = snapshotEncounterId.trim();
  const hasSnapshotFilter = Boolean(patientFilter || encounterFilter);
  const snapshotsQuery = useContextSnapshots(
    {
      patientId: patientFilter || undefined,
      encounterId: encounterFilter || undefined,
      status: "ACTIVE",
      page: 1,
      size: 20,
      sort: "createdAt,desc",
    },
    { enabled: triggerModalVisible && hasSnapshotFilter },
  );
  const snapshotDetailQuery = useContextSnapshotDetail(selectedSnapshotId, {
    enabled: triggerModalVisible && Boolean(selectedSnapshotId),
  });
  const selectedSnapshot = snapshotsQuery.data?.items.find(
    (snapshot) => snapshot.snapshotId === selectedSnapshotId,
  );

  // 突变动作
  const triggerCdssMutation = useEvaluateRecommendations();
  const feedbackMutation = useSubmitRecommendationFeedback(selectedCardId || "");
  const selectedFatigueGovernance = getFatigueGovernance(detailData?.card);
  const selectedJourneySteps = detailData
    ? getRecommendationJourneySteps(detailData, sourcesData ?? detailData.sources, diagnoseData)
    : [];

  // ACTIVE 临床快照是评估上下文的唯一来源，患者、就诊与配置包版本由服务端再次校验。
  const handleTriggerCdss = async () => {
    try {
      const values = await triggerForm.validateFields();
      if (!selectedSnapshot || !snapshotDetailQuery.data?.packageVersion) {
        message.error("请先选择包含配置包版本的 ACTIVE 临床快照");
        return;
      }

      const res = await triggerCdssMutation.mutateAsync({
        triggerCode: `CDSS-MANUAL-${values.triggerType}`,
        triggerType: values.triggerType,
        scenarioCode: values.triggerType,
        contextSnapshotId: selectedSnapshot.snapshotId,
        patientId: selectedSnapshot.patientId,
        encounterId: selectedSnapshot.encounterId || undefined,
        packageVersion: snapshotDetailQuery.data.packageVersion,
      });

      message.success(
        `推荐评估已完成：展示 ${res.visibleCardCount} 张，疲劳策略抑制 ${res.suppressedCardCount} 张。`,
      );
      closeTriggerModal();
      refetchCards();
    } catch (error: unknown) {
      if (applyApiFieldErrors(triggerForm, error)) return;
      message.error(getApiErrorMessage(error, "触发 CDSS 计算失败，请稍后重试"));
    }
  };

  const closeTriggerModal = () => {
    setTriggerModalVisible(false);
    setSnapshotPatientId("");
    setSnapshotEncounterId("");
    setSelectedSnapshotId("");
    triggerForm.resetFields();
  };

  // 提交医师反馈 (ACCEPT / REJECT)。操作者身份由后端从登录态取真实用户，前端绝不伪造 physicianId。
  const handleFeedback = async (feedbackType: RecommendationFeedbackType) => {
    if (!selectedCardId) return;
    try {
      const values = await feedbackForm.validateFields();
      await feedbackMutation.mutateAsync({
        feedbackType,
        reasonCode: feedbackType === "ACCEPT" ? "CONFIRMED" : values.rejectReason,
        reasonText:
          feedbackType === "ACCEPT" ? values.comments || "医师确认采纳提醒建议" : values.comments,
        operatorRole: "DOCTOR",
      });

      message.success(
        feedbackType === "ACCEPT"
          ? "已登记采纳，已生成临床决策证据；是否下达医嘱请在 HIS 中确认。"
          : "已登记不采纳反馈，建议已封存归档。",
      );
      feedbackForm.resetFields();

      // 反馈后刷新真实数据，状态以后端为准
      refetchCards();
      refetchDetail();
      refetchSources();
      refetchFatigue();
      refetchDiagnose();
    } catch (error: unknown) {
      if (applyApiFieldErrors(feedbackForm, error)) return;
      message.error(getApiErrorMessage(error, "反馈提交失败，卡片可能已过期或已处于终止态"));
    }
  };

  // 表格列
  const columns: TableProps<ClinicalRecommendationCard>["columns"] = [
    {
      title: "卡片编号",
      dataIndex: "cardId",
      key: "cardId",
      render: (text: string) => (
        <span className={`${styles.codeText} ${styles.textStrong}`}>{text}</span>
      ),
    },
    {
      title: "提醒摘要",
      dataIndex: "title",
      key: "title",
      className: styles.textStrong,
      width: 280,
    },
    {
      title: "患者 ID",
      dataIndex: "patientId",
      key: "patientId",
    },
    {
      title: "严重度",
      dataIndex: "riskLevel",
      key: "riskLevel",
      render: (level: RecommendationRiskLevel) => {
        const colors: Record<string, string> = {
          CRITICAL: "red",
          HIGH: "red",
          MEDIUM: "orange",
          LOW: "green",
        };
        return <Tag color={colors[level] || "blue"}>{riskLabel(level)}</Tag>;
      },
    },
    {
      title: "拦截级别",
      dataIndex: "interruptLevel",
      key: "interruptLevel",
      render: (level: string) => {
        const colors = { HARD: "purple", SOFT: "volcano", NONE: "default" };
        return (
          <Tag color={colors[level as keyof typeof colors] || "blue"}>
            {interruptLevelLabels[level] ?? "未识别级别"}
          </Tag>
        );
      },
    },
    {
      title: "就诊场景",
      dataIndex: "scenarioCode",
      key: "scenarioCode",
      render: (c: string) => <Tag color="cyan">{scenarioLabels[c] ?? "其他场景"}</Tag>,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: RecommendationCardStatus) => {
        const config: Record<string, { status: RecommendationBadgeStatus; text: string }> = {
          PENDING: { status: "warning", text: "待处理" },
          VIEWED: { status: "processing", text: "已查看依据" },
          ACCEPTED: { status: "success", text: "已采纳" },
          REJECTED: { status: "error", text: "已驳回" },
          DEFERRED: { status: "default", text: "稍后处理" },
          DISMISSED: { status: "default", text: "已关闭" },
          SUPPRESSED: { status: "default", text: "疲劳抑制" },
          EXPIRED: { status: "default", text: "已失效" },
        };
        const current = config[status] ?? { status: "default", text: "未识别状态" };
        return <Badge status={current.status} text={current.text} />;
      },
    },
    {
      title: "管理",
      key: "action",
      render: (record: ClinicalRecommendationCard) => (
        <Button
          type="link"
          icon={<AuditOutlined />}
          onClick={() => {
            setSelectedCardId(record.cardId);
            setDiagnoseDrawerVisible(false);
          }}
          className={styles.linkButton}
        >
          查看与人机反馈
        </Button>
      ),
    },
  ];

  let cardsTableContent = (
    <Table
      columns={columns}
      dataSource={visibleCards}
      rowKey="cardId"
      loading={cardsLoading}
      pagination={{
        current: page,
        pageSize: size,
        total: quickSearchTerm ? visibleCards.length : (cardsPage?.total ?? 0),
        onChange: (p) => setPage(p),
        showTotal: (t) => `共 ${t} 张临床运行提醒卡`,
      }}
      className="medkernel-table"
    />
  );
  if (cardsPageState === "forbidden" && cardsParsedError) {
    cardsTableContent = (
      <PageState
        state="forbidden"
        title="当前权限不足"
        description={cardsParsedError.message}
        traceId={cardsParsedError.traceId}
      />
    );
  } else if (cardsPageState === "error" && cardsParsedError) {
    cardsTableContent = (
      <PageState
        state="error"
        title="推荐卡列表读取失败"
        description={cardsParsedError.message}
        onRetry={() => refetchCards()}
        traceId={cardsParsedError.traceId ?? cardsPage?.traceId}
      />
    );
  }

  return (
    <PageShell
      title="提醒与推荐中枢"
      description="把临床推荐卡、待办、通知、医生反馈、知识来源和审计追溯放在同一页，医生先处理风险，质控再复核证据。"
    >
      <div className={`${styles.surface} ${styles.journeyOverview}`}>
        <div className={styles.rowBetween}>
          <div>
            <div className={styles.sectionTitle}>
              <AuditOutlined className={styles.iconInfo} />
              <span>推荐链路总览</span>
            </div>
            <div className={styles.textSmall}>
              从触发事件到医生反馈，按同一条 traceId 解释推荐为什么出现、现在处理到哪一步。
            </div>
          </div>
          <Tag color="blue">一图贯穿</Tag>
        </div>
        <div className={styles.journeyPillGrid}>
          {[
            "触发事件",
            "命中规则",
            "知识来源",
            "路径上下文",
            "待办 / 通知",
            "医生反馈",
            "药师复核",
          ].map((label) => (
            <div className={styles.journeyPill} key={label}>
              {label}
            </div>
          ))}
        </div>
      </div>

      <div className={`${styles.surface} ${styles.filterSurface}`}>
        <div className={`${styles.sectionTitle} ${styles.sectionGap}`}>
          <SearchOutlined className={styles.iconInfo} />
          <span>按患者 ID / traceId / 来源对象查推荐</span>
        </div>
        <Form layout="vertical" className={styles.inlineForm}>
          <Form.Item label="状态">
            <Select
              placeholder="全部状态"
              allowClear
              value={statusFilter}
              onChange={(v) => {
                setStatusFilter(v);
                setPage(1);
              }}
              className={styles.controlSm}
            >
              <Option value="PENDING">待处理</Option>
              <Option value="ACCEPTED">已采纳</Option>
              <Option value="REJECTED">已驳回</Option>
              <Option value="EXPIRED">已失效</Option>
            </Select>
          </Form.Item>
          <Form.Item label="严重度风险">
            <Select
              placeholder="全部严重度"
              allowClear
              value={riskFilter}
              onChange={(v) => {
                setRiskFilter(v);
                setPage(1);
              }}
              className={styles.controlSm}
            >
              <Option value="HIGH">高风险（红线强阻断）</Option>
              <Option value="MEDIUM">中风险（黄线软提醒）</Option>
              <Option value="LOW">低风险（绿线低打扰）</Option>
            </Select>
          </Form.Item>
          <Form.Item label="患者或 traceId" htmlFor="recommendation-quick-search">
            <Input
              id="recommendation-quick-search"
              placeholder="输入患者 ID、traceId、卡片或触发事件"
              allowClear
              value={quickSearch}
              onChange={(e) => {
                setQuickSearch(e.target.value);
                setPage(1);
              }}
              className={styles.controlLg}
            />
          </Form.Item>
          <Form.Item className={styles.actionItem}>
            <Button
              type="primary"
              icon={<FireOutlined />}
              onClick={() => setTriggerModalVisible(true)}
            >
              登记触发评估
            </Button>
          </Form.Item>
        </Form>
      </div>

      <div className={styles.statsGrid}>
        <div className={styles.metricCard}>
          <div className={styles.metricLabel}>提醒总数</div>
          <div className={styles.metricNumber}>{statsData?.totalCount ?? 0}</div>
        </div>
        <div className={styles.metricCard}>
          <div className={styles.metricLabel}>待处理</div>
          <div className={`${styles.metricNumber} ${styles.metricPending}`}>
            {statsData?.pendingCount ?? 0}
          </div>
        </div>
        <div className={styles.metricCard}>
          <div className={styles.metricLabel}>已采纳</div>
          <div className={`${styles.metricNumber} ${styles.metricAccepted}`}>
            {statsData?.acceptedCount ?? 0}
          </div>
        </div>
        <div className={styles.metricCard}>
          <div className={styles.metricLabel}>不采纳</div>
          <div className={`${styles.metricNumber} ${styles.metricRejected}`}>
            {statsData?.rejectedCount ?? 0}
          </div>
        </div>
        <div className={styles.metricCard}>
          <div className={styles.metricLabel}>采纳率</div>
          <div className={`${styles.metricNumber} ${styles.metricRate}`}>
            {statsData?.acceptanceRatePercent ?? 0}%
          </div>
        </div>
      </div>

      <div className={styles.surface}>{cardsTableContent}</div>

      <Modal
        title="登记一次推荐触发评估"
        open={triggerModalVisible}
        onOk={handleTriggerCdss}
        onCancel={closeTriggerModal}
        okText="执行推荐评估"
        cancelText="取消"
        okButtonProps={{
          disabled: !selectedSnapshotId || snapshotDetailQuery.isLoading,
        }}
        width={720}
        confirmLoading={triggerCdssMutation.isPending}
        destroyOnClose
      >
        <Alert
          message="推荐引擎将读取所选 ACTIVE 临床快照，执行已激活规则与红线检查；模型不可用时保持确定性规则链路。"
          type="info"
          showIcon
          className={styles.sectionGap}
        />
        <Form
          form={triggerForm}
          layout="vertical"
          className={styles.marginTopSm}
          initialValues={{ triggerType: "order-sign" }}
        >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item label="患者 ID" htmlFor="cdss-snapshot-patient">
                <Input
                  id="cdss-snapshot-patient"
                  placeholder="输入患者 ID 检索 ACTIVE 快照"
                  value={snapshotPatientId}
                  onChange={(event) => {
                    setSnapshotPatientId(event.target.value);
                    setSelectedSnapshotId("");
                  }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="就诊 ID" htmlFor="cdss-snapshot-encounter">
                <Input
                  id="cdss-snapshot-encounter"
                  placeholder="可单独按就诊 ID 检索"
                  value={snapshotEncounterId}
                  onChange={(event) => {
                    setSnapshotEncounterId(event.target.value);
                    setSelectedSnapshotId("");
                  }}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="triggerType" label="触发时点" rules={[{ required: true }]}>
            <Select
              options={[
                { value: "patient-view", label: "查看患者" },
                { value: "order-select", label: "选择医嘱" },
                { value: "order-sign", label: "签署医嘱" },
              ]}
            />
          </Form.Item>
          <ContextSnapshotSelector
            enabled={hasSnapshotFilter}
            loading={snapshotsQuery.isLoading}
            error={snapshotsQuery.isError}
            snapshots={snapshotsQuery.data?.items ?? []}
            selectedSnapshotId={selectedSnapshotId}
            onSelect={setSelectedSnapshotId}
          />
          {snapshotDetailQuery.data && (
            <Descriptions bordered size="small" column={3} className={styles.sectionGap}>
              <Descriptions.Item label="配置包">
                {snapshotDetailQuery.data.packageVersion}
              </Descriptions.Item>
              <Descriptions.Item label="质量">
                {customerDisplayText(snapshotDetailQuery.data.qualityStatus)}
              </Descriptions.Item>
              <Descriptions.Item label="traceId">
                {snapshotDetailQuery.data.traceId || "未返回"}
              </Descriptions.Item>
            </Descriptions>
          )}
        </Form>
      </Modal>

      <Drawer
        title={
          <div className={styles.drawerTitle}>
            <BookOutlined className={styles.iconInfo} />
            <span>智能建议人机闭环反馈与疲劳治理控制台</span>
          </div>
        }
        width={900}
        onClose={() => setSelectedCardId(null)}
        open={!!selectedCardId}
        forceRender
      >
        {detailData && (
          <div>
            <Card
              size="small"
              title="这条推荐是怎么来的"
              className={`${styles.detailCard} ${styles.sectionGapLg}`}
            >
              <div className={styles.journeyGrid}>
                {selectedJourneySteps.map((step) => (
                  <div className={styles.journeyStep} key={step.title}>
                    <div className={styles.journeyStepTitle}>{step.title}</div>
                    <Tag
                      color={
                        step.status === "待处理" || step.status === "PENDING" ? "orange" : "blue"
                      }
                    >
                      {customerDisplayText(step.status)}
                    </Tag>
                    <div className={styles.timelineMeta}>{step.description}</div>
                    {step.evidence && <div className={styles.journeyEvidence}>{step.evidence}</div>}
                  </div>
                ))}
              </div>
            </Card>

            <Descriptions
              title="推荐卡主数据"
              bordered
              column={3}
              size="small"
              className={styles.sectionGapLg}
            >
              <Descriptions.Item label="卡片编号">
                <span className={`${styles.codeText} ${styles.textStrong}`}>
                  {detailData.card.cardId}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="患者 ID">
                <span className={styles.textStrong}>
                  {detailData.trigger?.patientId || "未关联"}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="就诊编码">
                <span className={styles.codeText}>
                  {detailData.trigger?.encounterId || "未关联"}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="决策场景">
                <Tag color="cyan">
                  {detailData.trigger?.scenarioCode
                    ? scenarioLabels[detailData.trigger.scenarioCode] ?? "其他场景"
                    : "未关联"}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="风险分级">
                <Tag
                  color={
                    detailData.card.riskLevel === "CRITICAL" || detailData.card.riskLevel === "HIGH"
                      ? "red"
                      : "orange"
                  }
                >
                  {riskLabel(detailData.card.riskLevel)}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="拦截定位">
                <Tag color={detailData.card.interruptLevel === "HARD" ? "purple" : "volcano"}>
                  {interruptLevelLabels[detailData.card.interruptLevel] ?? "未识别级别"}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="疲劳治理策略" span={3}>
                <Tag color={selectedFatigueGovernance.color}>{selectedFatigueGovernance.label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="提醒摘要描述" span={3}>
                <span className={styles.textStrong}>{detailData.card.summary}</span>
              </Descriptions.Item>
            </Descriptions>

            {detailData.feedback.length > 0 && (
              <Card size="small" className={`${styles.detailCard} ${styles.sectionGapLg}`}>
                <div className={`${styles.textStrong} ${styles.sectionGap}`}>已记录医师反馈</div>
                <Timeline
                  items={detailData.feedback.map((item) => ({
                    key: item.feedbackId,
                    color: item.feedbackType === "ACCEPT" ? "green" : "red",
                    children: (
                      <div>
                        <div className={styles.timelineTitle}>
                          {item.operatorId} · {roleLabel(item.operatorRole || "DOCTOR")} ·{" "}
                          {customerEnumLabel(item.feedbackType)}
                        </div>
                        <div className={styles.timelineMeta}>
                          {item.reasonText || item.reasonCode || "未记录说明"}
                        </div>
                      </div>
                    ),
                  }))}
                />
              </Card>
            )}

            <Tabs
              defaultActiveKey={detailData.card.status === "PENDING" ? "feedback" : "sources"}
              items={[
                {
                  key: "sources",
                  label: (
                    <span>
                      <ReadOutlined /> 临床指南与来源证据
                    </span>
                  ),
                  children: (
                    <div
                      className={`${styles.stackLg} ${styles.scrollPanel} ${styles.marginTopSm}`}
                    >
                      {sourcesData && sourcesData.length > 0 ? (
                        sourcesData.map((source: RecommendationSource) => (
                          <Card
                            key={source.sourceId}
                            size="small"
                            title={
                              <div className={styles.rowBetween}>
                                <span className={styles.timelineTitle}>{source.title}</span>
                                <Tag
                                  color={
                                    typeof source.authorityScore === "number" ? "purple" : "default"
                                  }
                                >
                                  {typeof source.authorityScore === "number"
                                    ? `权威度评分: ${source.authorityScore}分`
                                    : "权威度评分未提供"}
                                </Tag>
                              </div>
                            }
                            className={styles.compactCard}
                          >
                            <div className={styles.contentText}>{source.content}</div>
                            <Descriptions size="small" column={2} className={styles.evidenceBox}>
                              <Descriptions.Item label="指南/文献出处">
                                <span className={styles.textStrong}>{source.sourceRef}</span>
                              </Descriptions.Item>
                              <Descriptions.Item label="证据级别">
                                {source.evidenceLevel ? (
                                  <Tag color="cyan">{source.evidenceLevel}</Tag>
                                ) : (
                                  <Tag>未提供</Tag>
                                )}
                              </Descriptions.Item>
                            </Descriptions>
                          </Card>
                        ))
                      ) : (
                        <Empty description="该提醒卡暂无来源解释证据（仅展示后端真实来源，不做任何兜底伪造）" />
                      )}
                    </div>
                  ),
                },

                {
                  key: "feedback",
                  disabled: detailData.card.status !== "PENDING",
                  label: (
                    <span>
                      <CheckCircleOutlined /> 医师反馈
                    </span>
                  ),
                  children: (
                    <Card className={`${styles.detailCard} ${styles.marginTopSm}`}>
                      <Form form={feedbackForm} layout="vertical">
                        <Alert
                          message="合理化医师反馈是临床合理处方闭环的核心留痕。选择不采纳时，请录入客观严谨的临床医学抗拒理由，以便医疗质控追溯与持续优化CDSS阈值。操作者身份由系统按登录态如实记录。"
                          type="info"
                          showIcon
                          className={styles.sectionGap}
                        />

                        <Tabs
                          defaultActiveKey="accept"
                          type="card"
                          size="small"
                          className={styles.sectionGap}
                          items={[
                            {
                              key: "accept",
                              label: "采纳合理建议 (ACCEPT)",
                              children: (
                                <>
                                  <div className={styles.successEvidence}>
                                    确认采纳此建议。系统将登记采纳反馈并生成临床决策证据；是否下达/撤销医嘱由医师在
                                    HIS 中确认。
                                  </div>
                                  <Form.Item name="comments" label="采纳说明 (非必填)">
                                    <Input placeholder="输入采纳说明，如：遵照指南撤销不合理克拉霉素..." />
                                  </Form.Item>
                                  <Button
                                    type="primary"
                                    onClick={() => handleFeedback("ACCEPT")}
                                    loading={feedbackMutation.isPending}
                                    className={styles.fullWidth}
                                  >
                                    确认并予以采纳 (ACCEPT)
                                  </Button>
                                </>
                              ),
                            },

                            {
                              key: "reject",
                              label: "拒绝驳回建议 (REJECT)",
                              children: (
                                <>
                                  <Form.Item
                                    name="rejectReason"
                                    label="医生拒绝/不采纳的临床抗拒原因"
                                    rules={[
                                      { required: true, message: "请选择拒绝采纳的临床理由" },
                                    ]}
                                  >
                                    <Select placeholder="选择合理的抗拒指征原因">
                                      <Option value="方案不合个体指征">
                                        方案不合个体指征 (患者存在基因多态或联合耐药事实)
                                      </Option>
                                      <Option value="已有替代有效疗法">
                                        已有替代有效疗法 (临床已采取其它合理对症治疗手段)
                                      </Option>
                                      <Option value="数据存在延迟偏差">
                                        数据存在延迟偏差 (系统检测到的就诊或过敏事实与临床现状不符)
                                      </Option>
                                      <Option value="其他合理临床抉择">
                                        其他合理临床抉择 (需要医生在下方输入备注具体说明)
                                      </Option>
                                    </Select>
                                  </Form.Item>
                                  <Form.Item
                                    name="comments"
                                    label="备注/不采纳详细医学判定说明"
                                    rules={[{ required: true, message: "请输入详细拒绝说明" }]}
                                  >
                                    <TextArea
                                      rows={2}
                                      placeholder="请录入专业客观的临床诊断说明以便应对质控核查..."
                                    />
                                  </Form.Item>
                                  <Button
                                    type="primary"
                                    danger
                                    onClick={() => handleFeedback("REJECT")}
                                    loading={feedbackMutation.isPending}
                                    className={styles.fullWidth}
                                  >
                                    确认拒绝采纳该建议 (REJECT)
                                  </Button>
                                </>
                              ),
                            },
                          ]}
                        />
                      </Form>
                    </Card>
                  ),
                },

                {
                  key: "fatigue",
                  label: (
                    <span>
                      <DashboardOutlined /> 提醒频次治理
                    </span>
                  ),
                  children: (
                    <div className={styles.marginTopSm}>
                      <Alert
                        message="为防范“提醒狼来了麻木”，MedKernel 引擎引入高阶提醒疲劳度限流控制事实。当特定场景超频触发且被医生频繁驳回时，系统会触发静音/限频甚至全面物理拦截阻断。"
                        type="warning"
                        showIcon
                        className={styles.sectionGap}
                      />
                      <Alert
                        message="疲劳治理策略来自配置中心"
                        description={
                          <div className={styles.contentText}>
                            <span>科室级疲劳阈值读取 </span>
                            <Tag color="blue" className={styles.inlineTag}>
                              {FATIGUE_POLICY_CONFIG_KEY}
                            </Tag>
                            <span>{selectedFatigueGovernance.description}</span>
                          </div>
                        }
                        type={selectedFatigueGovernance.isNonSuppressible ? "error" : "info"}
                        showIcon
                        className={styles.sectionGap}
                      />

                      {fatigueSignalsData?.items && fatigueSignalsData.items.length > 0 ? (
                        fatigueSignalsData.items.map((signal: RecommendationFatigueSignal) => (
                          <Card
                            key={signal.signalId}
                            size="small"
                            className={`${styles.compactCard} ${styles.sectionGap}`}
                          >
                            <Descriptions size="small" column={2}>
                              <Descriptions.Item label="疲劳 Key">
                                <span className={`${styles.codeText} ${styles.textStrong}`}>
                                  {signal.fatigueKey}
                                </span>
                              </Descriptions.Item>
                              <Descriptions.Item label="信号定位">
                                <Tag color={signal.signalType === "MUTE" ? "orange" : "red"}>
                                  {signal.signalType}
                                </Tag>
                              </Descriptions.Item>
                              <Descriptions.Item label="治理来源">
                                <Tag color="blue">配置中心</Tag>
                              </Descriptions.Item>
                              <Descriptions.Item label="阈值作用域">
                                <span className={styles.textSmall}>科室/场景</span>
                              </Descriptions.Item>
                            </Descriptions>
                            <div className={styles.marginTopMd}>
                              <div className={`${styles.rowBetween} ${styles.textSmall}`}>
                                <span>疲劳触发进度 (当前触发 / 疲劳静音阈值)</span>
                                <span className={styles.textStrong}>
                                  {signal.triggerCount} / {signal.governanceThreshold} 次
                                </span>
                              </div>
                              <Progress
                                percent={getFatigueProgressPercent(signal)}
                                status={
                                  signal.triggerCount >= signal.governanceThreshold
                                    ? "exception"
                                    : "active"
                                }
                              />
                            </div>
                            {signal.summary && (
                              <div className={`${styles.evidenceBox} ${styles.italic}`}>
                                {signal.summary}
                              </div>
                            )}
                          </Card>
                        ))
                      ) : (
                        <Empty description="该场景暂无疲劳治理信号（仅展示后端真实采集信号）" />
                      )}
                    </div>
                  ),
                },
              ]}
            />

            <div className={styles.centerAction}>
              <Button
                type="default"
                icon={<BugOutlined />}
                onClick={() => {
                  setDiagnoseDrawerVisible(true);
                  refetchDiagnose();
                }}
                className={styles.wideAction}
              >
                查看决策链追溯
              </Button>
            </div>
          </div>
        )}
      </Drawer>

      <Drawer
        title={
          <div className={styles.drawerTitle}>
            <BugOutlined className={styles.iconInfo} />
            <span>推荐决策链可信归因审计</span>
          </div>
        }
        width={640}
        onClose={() => setDiagnoseDrawerVisible(false)}
        open={diagnoseDrawerVisible}
        destroyOnClose
      >
        {diagnoseData ? (
          <div>
            <Alert
              message="决策解释追溯数据提取自底座 StateTransitionRecorder 物理事件留痕，保证透明、可复核、可审计。"
              type="info"
              showIcon
              className={styles.sectionGapLg}
            />

            <Descriptions
              title="求值Trace元数据"
              bordered
              column={1}
              size="small"
              className={styles.sectionGapLg}
            >
              <Descriptions.Item label="推荐 Trigger ID">
                <span className={styles.codeText}>{diagnoseData.executionId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="链路 Trace ID">
                <span className={styles.codeText}>{diagnoseData.traceId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="输入 Payload 摘要 (SHA-256)">
                <span className={styles.codeText}>{diagnoseData.inputPayloadSummary || "—"}</span>
              </Descriptions.Item>
              <Descriptions.Item label="提醒卡风险定级">
                <Tag color={diagnoseData.riskLevel === "HIGH" ? "red" : "orange"}>
                  {riskLabel(diagnoseData.riskLevel || "LOW")}
                </Tag>
              </Descriptions.Item>
            </Descriptions>

            <Card
              title={
                <div className={styles.sectionTitle}>
                  <UserOutlined className={styles.iconInfo} />
                  <span>推荐决策求值证据与可信解释</span>
                </div>
              }
              className={`${styles.detailCard} ${styles.sectionGapLg}`}
            >
              <div className={styles.detailBody}>
                {diagnoseData.explanationSnapshot || "暂无决策解释快照"}
              </div>
            </Card>

            <Card
              title={
                <div className={styles.sectionTitle}>
                  <CalendarOutlined className={styles.iconInfo} />
                  <span>审计流转历史 (State History)</span>
                </div>
              }
              className={styles.detailCard}
            >
              <Timeline>
                {diagnoseData.statusHistory?.map((h, idx) => (
                  <Timeline.Item key={idx} color={h.status === "SIGNED" ? "green" : "blue"}>
                    <div className={`${styles.rowBetween} ${styles.timelineTitle}`}>
                      <span>状态：{customerEnumLabel(h.status)}</span>
                      <span className={styles.timelineMuted}>
                        {new Date(h.changedAt).toLocaleString()}
                      </span>
                    </div>
                    <div className={styles.timelineMeta}>
                      <span>操作人: </span>
                      <Tag color="cyan" icon={<UserOutlined />}>
                        {h.changedBy}
                      </Tag>
                    </div>
                    <div className={`${styles.timelineMeta} ${styles.italic}`}>{h.summary}</div>
                  </Timeline.Item>
                ))}
              </Timeline>
            </Card>
          </div>
        ) : (
          <div className={`${styles.emptyState} ${styles.emptyStateCompact}`}>
            <ExclamationCircleOutlined className={styles.emptyIcon} />
            <span>无法获取该推荐触发实例的决策追溯链。</span>
          </div>
        )}
      </Drawer>
    </PageShell>
  );
}
