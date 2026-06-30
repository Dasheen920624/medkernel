import { useEffect, useState } from "react";
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
import { PageState } from "@/shared/ui/PageState";
import { ContextSnapshotSelector } from "@/shared/ui/ContextSnapshotSelector";
import { applyApiFieldErrors, getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import {
  useClinicalRecommendationCards,
  useContextSnapshotDetail,
  useContextSnapshots,
  useEvaluateRecommendations,
  useInterpretDiagnosticReport,
  useRecommendationCardDetail,
  useRecommendationCardSources,
  useRecommendationStats,
  useSubmitRecommendationFeedback,
  useRecommendationFatigueSignals,
  useRecommendationTriggerDiagnose,
  useSecurityProfile,
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
import { customerDisplayText, customerEnumLabel, riskLabel } from "@/shared/config/customerLabels";
import { findRouteByPath } from "@/shared/config/routes";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { roleLabel } from "@/shared/config/roleCatalog";
import { CLINICAL_TRIGGER_POINT_OPTIONS } from "@/shared/config/clinicalTriggerPoints";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import { useLocation, useNavigate } from "react-router-dom";
import styles from "./Clinical.module.css";

const { TextArea } = Input;
const { Option } = Select;
const FATIGUE_POLICY_CONFIG_KEY = "medkernel.cdss.fatigue.policy";
let manualRecommendationTriggerSequence = 0;
const route = findRouteByPath("/cdss/fatigue");
const PAGE_META = {
  title: route?.title ?? "提醒与推荐",
  experience: route?.experience ?? {
    primaryRole: "临床使用者",
    goal: "查看临床提醒负担和治理线索",
    defaultView: "需关注提醒",
    defaultFilters: [],
    evidenceDetailContent: ["推荐卡编号", "患者上下文编号", "触发追踪号", "决策输入摘要"],
    interruptionLevel: "info" as const,
    evidence: "推荐触发、医生反馈、知识来源和频次治理均保留审计证据",
    dataScale: {
      expected: "large" as const,
      pagination: "page" as const,
      exportStrategy: "none" as const,
    },
    riskLevel: "medium" as const,
  },
};

type RecommendationBadgeStatus = Exclude<BadgeProps["status"], undefined>;
type RecommendationFatigueGovernance = {
  isNonSuppressible: boolean;
  label: string;
  color: string;
  description: string;
};

const recommendationStatusLabels: Record<RecommendationCardStatus, string> = {
  PENDING: "待处理",
  VIEWED: "已查看依据",
  ACCEPTED: "已采纳",
  REJECTED: "未采纳",
  DEFERRED: "稍后处理",
  DISMISSED: "已关闭",
  SUPPRESSED: "已限频",
  EXPIRED: "已失效",
};

const feedbackTypeLabels: Record<RecommendationFeedbackType, string> = {
  VIEW_SOURCE: "查看依据",
  ACCEPT: "采纳建议",
  REJECT: "不采纳建议",
  DEFER: "稍后处理",
  DISMISS: "关闭忽略",
};

function buildManualRecommendationTriggerCode(triggerType: string, snapshotId: string) {
  manualRecommendationTriggerSequence = (manualRecommendationTriggerSequence + 1) % 46_656;
  const suffix = `${Date.now().toString(36)}-${manualRecommendationTriggerSequence.toString(36)}`;
  const prefix = [
    "CDSS-MANUAL",
    sanitizeTriggerCodePart(triggerType),
    sanitizeTriggerCodePart(snapshotId),
  ].join("-");
  const maxPrefixLength = 128 - suffix.length - 1;
  return `${prefix.slice(0, Math.max(1, maxPrefixLength))}-${suffix}`;
}

function sanitizeTriggerCodePart(value: string) {
  const normalized = value
    .trim()
    .replace(/[^A-Za-z0-9._-]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
  return normalized || "NA";
}

const signalTypeLabels: Record<string, string> = {
  MUTE: "减少展示",
  BLOCK: "必须保留确认",
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

const clinicalRoleLabels: Record<string, string> = {
  DOCTOR: "医生",
  NURSE: "护士",
  PHARMACIST: "药师",
  TECHNICIAN: "医技人员",
  QUALITY_MANAGER: "质控人员",
  PATIENT: "患者",
};

function clinicalRoleLabel(value?: string | null) {
  if (!value) return "未设置角色";
  return clinicalRoleLabels[value] ?? roleLabel(value);
}

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
      label: "红线必须保留",
      color: "red",
      description: "重复提醒信号仅用于质量复核，不会自动减少或隐藏临床安全红线。",
    };
  }
  if (
    card?.riskLevel === "HIGH" ||
    card?.interruptLevel === "HARD" ||
    card?.interruptLevel === "STRONG_INTERRUPTIVE"
  ) {
    return {
      isNonSuppressible: true,
      label: "高风险必须确认",
      color: "volcano",
      description: "高风险或强打断提醒必须保留医师确认链路，不参与低价值提醒限频。",
    };
  }
  return {
    isNonSuppressible: false,
    label: "按科室频次治理",
    color: "blue",
    description: "低价值重复提醒按配置中心阈值进入低打扰治理。",
  };
}

function getRecommendationStatusLabel(status: string): string {
  return (
    recommendationStatusLabels[status as RecommendationCardStatus] ?? customerDisplayText(status)
  );
}

function getFeedbackTypeLabel(feedbackType: RecommendationFeedbackType): string {
  return feedbackTypeLabels[feedbackType] ?? customerEnumLabel(feedbackType);
}

function getFeedbackTimelineLabel(item: {
  feedbackType: RecommendationFeedbackType;
  operatorRole?: string | null;
}) {
  if (item.operatorRole === "PHARMACIST" && item.feedbackType === "VIEW_SOURCE") {
    return "完成复核";
  }
  return getFeedbackTypeLabel(item.feedbackType);
}

function getFeedbackTimelineColor(item: {
  feedbackType: RecommendationFeedbackType;
  operatorRole?: string | null;
}) {
  if (item.operatorRole === "PHARMACIST") return "blue";
  if (item.feedbackType === "ACCEPT") return "green";
  if (item.feedbackType === "REJECT") return "red";
  if (item.feedbackType === "DEFER") return "orange";
  return "gray";
}

function getSignalTypeLabel(signalType: string): string {
  return signalTypeLabels[signalType] ?? customerDisplayText(signalType);
}

function getFatigueSignalPolicyText(
  signal: RecommendationFatigueSignal,
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) return signal.fatigueKey;
  return signal.signalType === "BLOCK" ? "高危红线必须保留" : "科室/场景频次策略";
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
type TriggerModalMode = "RECOMMENDATION" | "REPORT_INTERPRETATION";

type ReportInterpretationRouteState = {
  reportInterpretation?: {
    snapshotId?: string;
    patientLabel?: string;
  };
};

function getReportInterpretationRouteState(state: unknown) {
  if (!state || typeof state !== "object") return null;
  const routeState = state as ReportInterpretationRouteState;
  const request = routeState.reportInterpretation;
  if (!request?.snapshotId) return null;
  return {
    snapshotId: request.snapshotId,
    patientLabel: request.patientLabel ?? "",
  };
}

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
  evidenceDetailsEnabled = false,
): RecommendationJourneyStep[] {
  const sourceTitle = sources?.[0]?.title ?? detail.card.sourceSummary;
  const latestFeedback = detail.feedback[0];
  const triggerScenario = detail.trigger?.triggerType ?? detail.card.scenarioCode ?? "";
  return [
    {
      title: "触发事件",
      status: scenarioLabels[triggerScenario] ?? "已接收事件",
      description: "来自患者上下文、医嘱签署、检验回报或外部系统事件。",
      evidence: evidenceDetailsEnabled
        ? detail.trigger?.sourceEventId || detail.trigger?.triggerId || detail.card.triggerId
        : undefined,
    },
    {
      title: "命中规则",
      status: evidenceDetailsEnabled
        ? textOrDash(diagnose?.ruleId || detail.card.cardCode || detail.card.fatigueKey)
        : riskLabel(detail.card.riskLevel),
      description: "规则引擎与红线检查给出风险级别和推荐动作。",
      evidence: evidenceDetailsEnabled
        ? diagnose?.explanationSnapshot || detail.card.summary
        : undefined,
    },
    {
      title: "知识来源",
      status: sourceTitle ? "已有来源" : "待补来源",
      description: sourceTitle || "该卡片暂未返回来源解释，暂不展示来源证据。",
      evidence: evidenceDetailsEnabled ? sources?.[0]?.sourceRef : undefined,
    },
    {
      title: "路径上下文",
      status: detail.trigger?.patientPathwayId ? "已关联路径" : "未关联路径",
      description: "把推荐放回患者路径位置，辅助医生判断下一步。",
      evidence: evidenceDetailsEnabled ? detail.trigger?.patientPathwayId : undefined,
    },
    {
      title: "待办 / 通知",
      status: getRecommendationStatusLabel(detail.card.status),
      description: "推荐卡会同步为医生待办或通知；状态以真实闭环为准。",
      evidence: evidenceDetailsEnabled
        ? `追踪号：${diagnose?.traceId || detail.trigger?.traceId || detail.traceId}`
        : undefined,
    },
    {
      title: "医生反馈",
      status: latestFeedback ? getFeedbackTypeLabel(latestFeedback.feedbackType) : "待处理",
      description: latestFeedback?.reasonText || "医生采纳或不采纳时必须留下真实理由。",
      evidence: latestFeedback?.operatorRole
        ? clinicalRoleLabel(latestFeedback.operatorRole)
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

function renderPatientContextCell(
  patientId: string | undefined,
  record: ClinicalRecommendationCard,
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) {
    return (
      <div>
        <div className={styles.textStrong}>{patientId || "未关联"}</div>
        {record.encounterId && <div className={styles.textSmall}>{record.encounterId}</div>}
      </div>
    );
  }
  return patientId ? "已关联患者" : "未关联患者";
}

/** 计算输入载荷的真实 SHA-256 摘要（不伪造哈希）。 */
export default function CdssFatigue() {
  const location = useLocation();
  const navigate = useNavigate();
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [triggerModalVisible, setTriggerModalVisible] = useState<boolean>(false);
  const [triggerModalMode, setTriggerModalMode] = useState<TriggerModalMode>("RECOMMENDATION");
  const [diagnoseDrawerVisible, setDiagnoseDrawerVisible] = useState<boolean>(false);
  const [snapshotPatientId, setSnapshotPatientId] = useState("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState("");
  const [selectedSnapshotId, setSelectedSnapshotId] = useState("");
  const [prefilledSnapshotLabel, setPrefilledSnapshotLabel] = useState("");

  // 分页与过滤状态
  const [page, setPage] = useState<number>(1);
  const [size] = useState<number>(10);
  const [statusFilter, setStatusFilter] = useState<RecommendationCardStatus | undefined>(undefined);
  const [riskFilter, setRiskFilter] = useState<RecommendationRiskLevel | undefined>(undefined);
  const [quickSearch, setQuickSearch] = useState<string>("");

  // 医师反馈与药师复核表单绑定
  const [feedbackForm] = Form.useForm();
  const [pharmacistReviewForm] = Form.useForm();
  const [triggerForm] = Form.useForm();
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;

  // 真实推荐卡列表：服务分页 + 服务端过滤，页面不伪造卡片。
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

  // 获取该卡片相关的提醒频次治理信号
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

  useEffect(() => {
    const reportInterpretation = getReportInterpretationRouteState(location.state);
    if (!reportInterpretation) return;

    setTriggerModalMode("REPORT_INTERPRETATION");
    setSnapshotPatientId("");
    setSnapshotEncounterId("");
    setSelectedSnapshotId(reportInterpretation.snapshotId);
    setPrefilledSnapshotLabel(reportInterpretation.patientLabel || "患者 360 当前患者");
    setTriggerModalVisible(true);
    navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate]);

  // 突变动作
  const triggerCdssMutation = useEvaluateRecommendations();
  const reportInterpretationMutation = useInterpretDiagnosticReport();
  const feedbackMutation = useSubmitRecommendationFeedback(selectedCardId || "");
  const selectedFatigueGovernance = getFatigueGovernance(detailData?.card);
  const selectedJourneySteps = detailData
    ? getRecommendationJourneySteps(
        detailData,
        sourcesData ?? detailData.sources,
        diagnoseData,
        evidenceDetailsEnabled,
      )
    : [];

  const openTriggerModal = (mode: TriggerModalMode) => {
    setTriggerModalMode(mode);
    setTriggerModalVisible(true);
  };

  // 已生效临床快照是评估上下文的唯一来源；生效内容由服务端按机构当前版本解析。
  const handleTriggerCdss = async () => {
    try {
      if (!selectedSnapshotId) {
        message.error("请先选择已生效临床快照");
        return;
      }

      if (triggerModalMode === "REPORT_INTERPRETATION") {
        const res = await reportInterpretationMutation.mutateAsync({
          contextSnapshotId: selectedSnapshotId,
        });
        message.success(
          `报告解读已生成：${res.interpretations.length} 项；相关提示已进入临床提示卡。`,
        );
        closeTriggerModal();
        refetchCards();
        return;
      }

      if (!selectedSnapshot) {
        message.error("请先选择已生效临床快照");
        return;
      }

      const values = await triggerForm.validateFields();

      const res = await triggerCdssMutation.mutateAsync({
        triggerCode: buildManualRecommendationTriggerCode(
          values.triggerType,
          selectedSnapshot.snapshotId,
        ),
        triggerType: values.triggerType,
        scenarioCode: values.triggerType,
        contextSnapshotId: selectedSnapshot.snapshotId,
        patientId: selectedSnapshot.patientId,
        encounterId: selectedSnapshot.encounterId || undefined,
      });

      message.success(
        `推荐评估已完成：展示 ${res.visibleCardCount} 张，频次策略减少展示 ${res.suppressedCardCount} 张。`,
      );
      closeTriggerModal();
      refetchCards();
    } catch (error: unknown) {
      if (applyApiFieldErrors(triggerForm, error)) return;
      message.error(
        getApiErrorMessage(
          error,
          triggerModalMode === "REPORT_INTERPRETATION"
            ? "生成报告解读失败，请稍后重试"
            : "触发推荐评估失败，请稍后重试",
        ),
      );
    }
  };

  const closeTriggerModal = () => {
    setTriggerModalVisible(false);
    setTriggerModalMode("RECOMMENDATION");
    setSnapshotPatientId("");
    setSnapshotEncounterId("");
    setSelectedSnapshotId("");
    setPrefilledSnapshotLabel("");
    triggerForm.resetFields();
  };

  // 提交医师采纳或不采纳反馈。操作者身份由平台从登录态取真实用户，前端绝不伪造 physicianId。
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

      // 反馈后刷新真实数据，状态以服务回写为准
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

  const handlePharmacistReview = async () => {
    if (!selectedCardId) return;
    try {
      const values = await pharmacistReviewForm.validateFields();
      await feedbackMutation.mutateAsync({
        feedbackType: "VIEW_SOURCE",
        reasonCode: "PHARMACIST_REVIEWED",
        reasonText: values.reviewNote,
        operatorRole: "PHARMACIST",
      });

      message.success("已登记药师复核；医生确认与医嘱执行仍在院内业务系统完成。");
      pharmacistReviewForm.resetFields();
      refetchCards();
      refetchDetail();
      refetchSources();
      refetchFatigue();
      refetchDiagnose();
    } catch (error: unknown) {
      if (applyApiFieldErrors(pharmacistReviewForm, error)) return;
      message.error(getApiErrorMessage(error, "药师复核登记失败，请确认提醒卡仍可处理"));
    }
  };

  // 表格列
  const columns: TableProps<ClinicalRecommendationCard>["columns"] = [
    ...(evidenceDetailsEnabled
      ? [
          {
            title: "卡片编号",
            dataIndex: "cardId",
            key: "cardId",
            render: (text: string) => (
              <span className={`${styles.codeText} ${styles.textStrong}`}>{text}</span>
            ),
          },
        ]
      : []),
    {
      title: "提醒摘要",
      dataIndex: "title",
      key: "title",
      className: styles.textStrong,
      width: 280,
    },
    {
      title: evidenceDetailsEnabled ? "患者编号" : "患者上下文",
      dataIndex: "patientId",
      key: "patientId",
      render: (patientId: string | undefined, record) =>
        renderPatientContextCell(patientId, record, evidenceDetailsEnabled),
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
          PENDING: { status: "warning", text: getRecommendationStatusLabel("PENDING") },
          VIEWED: { status: "processing", text: getRecommendationStatusLabel("VIEWED") },
          ACCEPTED: { status: "success", text: getRecommendationStatusLabel("ACCEPTED") },
          REJECTED: { status: "error", text: getRecommendationStatusLabel("REJECTED") },
          DEFERRED: { status: "default", text: getRecommendationStatusLabel("DEFERRED") },
          DISMISSED: { status: "default", text: getRecommendationStatusLabel("DISMISSED") },
          SUPPRESSED: { status: "default", text: getRecommendationStatusLabel("SUPPRESSED") },
          EXPIRED: { status: "default", text: getRecommendationStatusLabel("EXPIRED") },
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
        showTotal: (t) => `共 ${t} 张临床协同提醒卡`,
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
    <PageExperienceShell meta={PAGE_META} securityProfile={security.data}>
      <div className={`${styles.surface} ${styles.journeyOverview}`}>
        <div className={styles.rowBetween}>
          <div>
            <div className={styles.sectionTitle}>
              <AuditOutlined className={styles.iconInfo} />
              <span>推荐链路总览</span>
            </div>
            <div className={styles.textSmall}>
              从触发事件到医生反馈，按同一追踪号解释推荐为什么出现、现在处理到哪一步。
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

      <div className={`${styles.surface} ${styles.capabilitySurface}`}>
        <div className={styles.sectionTitle}>
          <CheckCircleOutlined className={styles.iconInfo} />
          <span>临床处置边界</span>
        </div>
        <div className={styles.capabilityGrid}>
          <div className={styles.capabilityItem}>
            <Tag color="volcano">医生</Tag>
            <span className={styles.textStrong}>必须医师确认</span>
            <span className={styles.textSmall}>
              高风险提醒只生成待确认建议，采纳、不采纳和医嘱执行均由医生在业务系统确认。
            </span>
          </div>
          <div className={styles.capabilityItem}>
            <Tag color="purple">药师</Tag>
            <span className={styles.textStrong}>联合用药 / DDI 用药风险</span>
            <span className={styles.textSmall}>
              高风险用药提醒进入药师复核视角，来源证据与医生反馈保持同链路追溯。
            </span>
          </div>
          <div className={styles.capabilityItem}>
            <Tag color="geekblue">医技</Tag>
            <span className={styles.textStrong}>医技报告解读</span>
            <span className={styles.textStrong}>不会改写已签发报告</span>
            <span className={styles.textSmall}>报告解读仅辅助阅读，不自动开立医嘱。</span>
          </div>
        </div>
      </div>

      <div className={`${styles.surface} ${styles.filterSurface}`}>
        <div className={`${styles.sectionTitle} ${styles.sectionGap}`}>
          <SearchOutlined className={styles.iconInfo} />
          <span>按患者信息 / 风险 / 证据线索查推荐</span>
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
              <Option value="REJECTED">未采纳</Option>
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
          <Form.Item label="患者或证据线索" htmlFor="recommendation-quick-search">
            <Input
              id="recommendation-quick-search"
              placeholder="输入姓名、门急诊号、院内患者编号或证据线索"
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
              onClick={() => openTriggerModal("RECOMMENDATION")}
            >
              登记触发评估
            </Button>
            <Button
              icon={<ReadOutlined />}
              onClick={() => openTriggerModal("REPORT_INTERPRETATION")}
            >
              生成报告解读
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
        title={
          triggerModalMode === "REPORT_INTERPRETATION" ? "生成医技报告解读" : "登记一次推荐触发评估"
        }
        open={triggerModalVisible}
        onOk={handleTriggerCdss}
        onCancel={closeTriggerModal}
        okText={triggerModalMode === "REPORT_INTERPRETATION" ? "生成报告解读" : "执行推荐评估"}
        cancelText="取消"
        okButtonProps={{
          disabled: !selectedSnapshotId || snapshotDetailQuery.isLoading,
        }}
        width={720}
        confirmLoading={triggerCdssMutation.isPending || reportInterpretationMutation.isPending}
        destroyOnClose
      >
        <Alert
          message={
            triggerModalMode === "REPORT_INTERPRETATION"
              ? "系统将读取所选已生效临床快照中的医技报告，并按机构生效版本生成辅助解读；不会改写已签发报告，也不会自动开立医嘱。"
              : "推荐引擎将读取所选已生效临床快照，执行已激活规则与红线检查；模型不可用时保持确定性规则链路。"
          }
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
              <Form.Item label="患者信息" htmlFor="cdss-snapshot-patient">
                <Input
                  id="cdss-snapshot-patient"
                  placeholder="输入姓名、门急诊号或院内患者编号"
                  value={snapshotPatientId}
                  onChange={(event) => {
                    setSnapshotPatientId(event.target.value);
                    setSelectedSnapshotId("");
                    setPrefilledSnapshotLabel("");
                  }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="就诊信息" htmlFor="cdss-snapshot-encounter">
                <Input
                  id="cdss-snapshot-encounter"
                  placeholder="可按门急诊号或就诊信息缩小范围"
                  value={snapshotEncounterId}
                  onChange={(event) => {
                    setSnapshotEncounterId(event.target.value);
                    setSelectedSnapshotId("");
                    setPrefilledSnapshotLabel("");
                  }}
                />
              </Form.Item>
            </Col>
          </Row>
          {triggerModalMode === "RECOMMENDATION" && (
            <Form.Item name="triggerType" label="触发时点" rules={[{ required: true }]}>
              <Select
                options={CLINICAL_TRIGGER_POINT_OPTIONS.map(({ value, label }) => ({
                  value,
                  label,
                }))}
              />
            </Form.Item>
          )}
          {prefilledSnapshotLabel && selectedSnapshotId ? (
            <Alert
              message="已从患者 360 带入当前上下文"
              description={`${prefilledSnapshotLabel} 的已生效临床快照已选定；如需更换患者或就诊，请在上方重新检索。`}
              type="success"
              showIcon
              className={styles.sectionGap}
            />
          ) : (
            <ContextSnapshotSelector
              enabled={hasSnapshotFilter}
              loading={snapshotsQuery.isLoading}
              error={snapshotsQuery.isError}
              snapshots={snapshotsQuery.data?.items ?? []}
              selectedSnapshotId={selectedSnapshotId}
              onSelect={setSelectedSnapshotId}
              evidenceDetailsEnabled={evidenceDetailsEnabled}
            />
          )}
          {snapshotDetailQuery.data && (
            <Descriptions bordered size="small" column={3} className={styles.sectionGap}>
              <Descriptions.Item label="机构生效版本">
                {snapshotDetailQuery.data.runtimeReleaseId || "由当前机构生效版本确认"}
              </Descriptions.Item>
              <Descriptions.Item label="质量">
                {customerDisplayText(snapshotDetailQuery.data.qualityStatus)}
              </Descriptions.Item>
              {evidenceDetailsEnabled && (
                <Descriptions.Item label="追踪号">
                  {snapshotDetailQuery.data.traceId || "未返回"}
                </Descriptions.Item>
              )}
            </Descriptions>
          )}
        </Form>
      </Modal>

      <Drawer
        aria-label="推荐详情与反馈闭环"
        title={
          <div className={styles.drawerTitle}>
            <BookOutlined className={styles.iconInfo} />
            <span>推荐详情与反馈闭环</span>
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
              {evidenceDetailsEnabled ? (
                <>
                  <Descriptions.Item label="卡片编号">
                    <span className={`${styles.codeText} ${styles.textStrong}`}>
                      {detailData.card.cardId}
                    </span>
                  </Descriptions.Item>
                  <Descriptions.Item label="患者编号">
                    <span className={styles.textStrong}>
                      {detailData.trigger?.patientId || "未关联"}
                    </span>
                  </Descriptions.Item>
                  <Descriptions.Item label="就诊编码">
                    <span className={styles.codeText}>
                      {detailData.trigger?.encounterId || "未关联"}
                    </span>
                  </Descriptions.Item>
                </>
              ) : (
                <Descriptions.Item label="患者与就诊" span={3}>
                  患者与就诊已关联
                </Descriptions.Item>
              )}
              <Descriptions.Item label="决策场景">
                <Tag color="cyan">
                  {detailData.trigger?.scenarioCode
                    ? (scenarioLabels[detailData.trigger.scenarioCode] ?? "其他场景")
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
              <Descriptions.Item label="提醒频次策略" span={3}>
                <Tag color={selectedFatigueGovernance.color}>{selectedFatigueGovernance.label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="提醒摘要描述" span={3}>
                <span className={styles.textStrong}>{detailData.card.summary}</span>
              </Descriptions.Item>
            </Descriptions>

            {detailData.feedback.length > 0 && (
              <Card size="small" className={`${styles.detailCard} ${styles.sectionGapLg}`}>
                <div className={`${styles.textStrong} ${styles.sectionGap}`}>已记录反馈与复核</div>
                <Timeline
                  items={detailData.feedback.map((item) => ({
                    key: item.feedbackId,
                    color: getFeedbackTimelineColor(item),
                    children: (
                      <div>
                        <div className={styles.timelineTitle}>
                          {evidenceDetailsEnabled
                            ? `${item.operatorId} · ${clinicalRoleLabel(item.operatorRole || "DOCTOR")} · ${getFeedbackTimelineLabel(item)}`
                            : `${clinicalRoleLabel(item.operatorRole || "DOCTOR")} · ${getFeedbackTimelineLabel(item)}`}
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
                        <Empty description="该提醒卡暂无来源解释证据；请结合患者病情与院内制度复核。" />
                      )}
                    </div>
                  ),
                },

                {
                  key: "pharmacist-review",
                  disabled: detailData.card.status !== "PENDING",
                  label: (
                    <span>
                      <AuditOutlined /> 药师复核
                    </span>
                  ),
                  children: (
                    <Card className={`${styles.detailCard} ${styles.marginTopSm}`}>
                      <Form form={pharmacistReviewForm} layout="vertical">
                        <Alert
                          message="药师复核只登记联合用药和 DDI 风险复核证据；医生确认、医嘱调整和线下处置仍由院内业务系统完成。"
                          type="info"
                          showIcon
                          className={styles.sectionGap}
                        />
                        <Form.Item
                          name="reviewNote"
                          label="药师复核说明"
                          rules={[{ required: true, message: "请输入药师复核说明" }]}
                        >
                          <TextArea
                            rows={3}
                            placeholder="记录联合用药、禁忌、剂量或监测建议，不填写患者姓名、电话、证件号等核心敏感信息"
                          />
                        </Form.Item>
                        <Button
                          type="primary"
                          onClick={handlePharmacistReview}
                          loading={feedbackMutation.isPending}
                          className={styles.fullWidth}
                        >
                          登记药师复核
                        </Button>
                      </Form>
                    </Card>
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
                          message="医师反馈会进入临床决策证据链。采纳或不采纳都需记录真实理由；系统按登录态记录操作者身份，不由前端填写。"
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
                              label: "采纳建议",
                              children: (
                                <>
                                  <div className={styles.successEvidence}>
                                    确认采纳后，系统登记反馈并生成临床决策证据；是否下达或调整医嘱仍由医师在
                                    HIS 中确认。
                                  </div>
                                  <Form.Item name="comments" label="采纳说明（可选）">
                                    <Input placeholder="输入采纳说明，如：结合当前病情确认按院内制度处理" />
                                  </Form.Item>
                                  <Button
                                    type="primary"
                                    onClick={() => handleFeedback("ACCEPT")}
                                    loading={feedbackMutation.isPending}
                                    className={styles.fullWidth}
                                  >
                                    确认采纳建议
                                  </Button>
                                </>
                              ),
                            },

                            {
                              key: "reject",
                              label: "不采纳建议",
                              children: (
                                <>
                                  <Form.Item
                                    name="rejectReason"
                                    label="不采纳理由"
                                    rules={[{ required: true, message: "请选择不采纳理由" }]}
                                  >
                                    <Select placeholder="选择不采纳原因">
                                      <Option value="不符合当前患者指征">不符合当前患者指征</Option>
                                      <Option value="已有替代处理方案">已有替代处理方案</Option>
                                      <Option value="数据与当前病情不一致">
                                        数据与当前病情不一致
                                      </Option>
                                      <Option value="其他临床判断">其他临床判断</Option>
                                    </Select>
                                  </Form.Item>
                                  <Form.Item
                                    name="comments"
                                    label="不采纳说明"
                                    rules={[{ required: true, message: "请输入不采纳说明" }]}
                                  >
                                    <TextArea
                                      rows={2}
                                      placeholder="请记录当前患者情况、依据和已采取的处理方式"
                                    />
                                  </Form.Item>
                                  <Button
                                    type="primary"
                                    danger
                                    onClick={() => handleFeedback("REJECT")}
                                    loading={feedbackMutation.isPending}
                                    className={styles.fullWidth}
                                  >
                                    确认不采纳建议
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
                        message="提醒频次治理用于减少低价值重复提醒；高危红线和必须医师确认的提醒不会被自动减少或隐藏。"
                        type="warning"
                        showIcon
                        className={styles.sectionGap}
                      />
                      <Alert
                        message="提醒频次策略来自配置中心"
                        description={
                          <div className={styles.contentText}>
                            <span>科室级限频阈值读取 </span>
                            <Tag color="blue" className={styles.inlineTag}>
                              {evidenceDetailsEnabled
                                ? FATIGUE_POLICY_CONFIG_KEY
                                : "配置中心已关联"}
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
                              <Descriptions.Item
                                label={evidenceDetailsEnabled ? "频次策略身份" : "频次策略"}
                              >
                                <span className={`${styles.codeText} ${styles.textStrong}`}>
                                  {getFatigueSignalPolicyText(signal, evidenceDetailsEnabled)}
                                </span>
                              </Descriptions.Item>
                              <Descriptions.Item label="治理动作">
                                <Tag color={signal.signalType === "MUTE" ? "orange" : "red"}>
                                  {getSignalTypeLabel(signal.signalType)}
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
                                <span>重复触发进度（当前触发 / 科室级限频阈值）</span>
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
                        <Empty description="该场景暂无已采集的提醒频次治理信号" />
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
                查看决策依据
              </Button>
            </div>
          </div>
        )}
      </Drawer>

      <Drawer
        title={
          <div className={styles.drawerTitle}>
            <BugOutlined className={styles.iconInfo} />
            <span>推荐决策依据与审计证据</span>
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
              message="这里展示推荐产生的可复核依据；原始执行编号、追踪号和输入摘要仅在证据详情中展开。"
              type="info"
              showIcon
              className={styles.sectionGapLg}
            />

            <Descriptions
              title="评估概览"
              bordered
              column={1}
              size="small"
              className={styles.sectionGapLg}
            >
              <Descriptions.Item label="提醒卡风险定级">
                <Tag color={diagnoseData.riskLevel === "HIGH" ? "red" : "orange"}>
                  {riskLabel(diagnoseData.riskLevel || "LOW")}
                </Tag>
              </Descriptions.Item>
              {evidenceDetailsEnabled && (
                <>
                  <Descriptions.Item label="推荐触发编号">
                    <span className={styles.codeText}>{diagnoseData.executionId}</span>
                  </Descriptions.Item>
                  <Descriptions.Item label="追踪号">
                    <span className={styles.codeText}>{diagnoseData.traceId}</span>
                  </Descriptions.Item>
                  <Descriptions.Item label="输入内容校验码">
                    <span className={styles.codeText}>
                      {diagnoseData.inputPayloadSummary || "—"}
                    </span>
                  </Descriptions.Item>
                </>
              )}
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
                  <span>审计流转历史</span>
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
                      <span>处理角色: </span>
                      <Tag color="cyan" icon={<UserOutlined />}>
                        {evidenceDetailsEnabled ? h.changedBy : "已记录"}
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
    </PageExperienceShell>
  );
}
