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
  Grid,
} from "antd";
import type { BadgeProps, TableProps } from "antd";
import {
  PlusOutlined,
  CompassOutlined,
  FileTextOutlined,
  CalendarOutlined,
  RightCircleOutlined,
  WarningOutlined,
  DisconnectOutlined,
  BranchesOutlined,
} from "@ant-design/icons";
import {
  useContextSnapshots,
  usePathwayEntryCandidates,
  usePathwayTemplateDetail,
  useEnterPatientPathway,
  usePatientPathways,
  usePatientPathwayDetail,
  useAdvancePatientPathway,
  usePatientPathwayClocks,
  usePatientPathwayVariances,
  useSecurityProfile,
} from "@/shared/api/hooks";
import type {
  PatientPathway,
  PatientPathwayStatus,
  PathwayMilestoneRuntimeStatus,
  PathwayOutcomeBinding,
  PathwayCoordinationWarning,
  PathwayVariance,
  ClinicalClock,
  PathwayNode,
  PathwayEdge,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import { findRouteByPath } from "@/shared/config/routes";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { ContextSnapshotSelector } from "@/shared/ui/ContextSnapshotSelector";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { formatClinicalDateTime } from "@/shared/lib/dateTimeText";
import styles from "./Clinical.module.css";

const { TextArea } = Input;
const { Option } = Select;
const PATHWAY_RUNTIME_TRIGGER = "patient-view";
const route = findRouteByPath("/pathway/patients");
const PAGE_META = {
  title: route?.title ?? "患者路径",
  experience: route?.experience ?? {
    primaryRole: "临床使用者",
    goal: "查看患者路径运行事项",
    defaultView: "待处理节点",
    defaultFilters: [],
    evidenceDetailContent: [
      "患者路径实例编号",
      "患者编号",
      "就诊编号",
      "节点编码",
      "时钟编号",
      "追踪号",
    ],
    interruptionLevel: "info" as const,
    evidence: "患者入径、推进、变异和退径均保留路径运行证据",
    dataScale: {
      expected: "large" as const,
      pagination: "page" as const,
      exportStrategy: "none" as const,
    },
    riskLevel: "medium" as const,
  },
};

type PathwayBadgeStatus = Exclude<BadgeProps["status"], undefined>;

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

function milestoneStatusColor(status?: string) {
  if (status === "ACHIEVED") return "green";
  if (status === "CURRENT") return "blue";
  if (status === "OVERDUE") return "red";
  return "gray";
}

function milestoneStatusText(status?: string) {
  const text: Record<string, string> = {
    ACHIEVED: "已达成",
    CURRENT: "当前里程碑",
    PENDING: "待执行",
    OVERDUE: "已超期",
  };
  return status ? (text[status] ?? "状态待确认") : "待执行";
}

function clockStatusColor(status?: string) {
  if (status === "TIMEOUT") return "red";
  if (status === "COMPLETED") return "green";
  if (status === "VARIANCE") return "orange";
  if (status === "MISSING_DATA") return "gold";
  return "blue";
}

function clockStatusText(status?: string) {
  const text: Record<string, string> = {
    RUNNING: "运行中",
    COMPLETED: "已完成",
    TIMEOUT: "已超时",
    MISSING_DATA: "缺少数据",
    VARIANCE: "变异暂停",
  };
  return status ? (text[status] ?? "状态待确认") : "未记录";
}

function clockEscalationText(level?: string) {
  const text: Record<string, string> = {
    NONE: "未升级",
    REMINDER: "提醒",
    REPORT: "上报",
    QUALITY_RECORD: "质控记录",
  };
  return level ? (text[level] ?? "级别待确认") : "未升级";
}

function outcomeScopeText(scope?: string) {
  if (scope === "PHASE") return "阶段";
  if (scope === "MILESTONE") return "里程碑";
  if (scope === "TEMPLATE") return "全路径";
  return "范围待确认";
}

function formatDateTime(value?: string) {
  return formatClinicalDateTime(value);
}

function pathwayNodeTypeText(type?: string) {
  const text: Record<string, string> = {
    SCREENING: "筛查",
    ASSESSMENT: "评估",
    EXAM: "检查",
    LAB: "检验",
    MEDICATION: "用药",
    SURGERY: "手术",
    NURSING: "护理",
    REHAB: "康复",
    DISCHARGE: "出院",
    FOLLOWUP: "随访",
    QUALITY: "质控",
    DECISION: "分支决策",
    PARALLEL: "并行节点",
    WAIT_TIMER: "等待时钟",
    MANUAL_GATE: "人工确认",
    ORDER_SET: "医嘱组",
  };
  return type ? (text[type] ?? type) : "未标注";
}

function pathwayEdgeTypeText(type?: string) {
  const text: Record<string, string> = {
    DEFAULT: "标准流转",
    CONDITION: "条件流转",
    RISK_STRATIFICATION: "风险分层",
    PATIENT_CHOICE: "患者选择",
    RESOURCE_UNAVAILABLE: "资源不可用",
    PHYSICIAN_DECISION: "医生决策",
    ROLLBACK: "回退",
    JOIN: "汇合",
  };
  return type ? (text[type] ?? type) : "流转";
}

function formatWindowMinutes(minutes?: number) {
  if (!minutes || minutes <= 0) return "无固定时窗";
  if (minutes < 60) return `${minutes} 分钟`;
  if (minutes % 1440 === 0) return `${minutes / 1440} 天`;
  if (minutes % 60 === 0) return `${minutes / 60} 小时`;
  return `${minutes} 分钟`;
}

function parseJsonRecord(value?: string | null) {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

function nodeVisibleSummary(node: PathwayNode) {
  const config = parseJsonRecord(node.configJson);
  const summary = config?.visibleSummary;
  return typeof summary === "string" && summary.trim() ? summary.trim() : null;
}

function pathwayRuntimeNodeState(
  node: PathwayNode,
  pathway: PatientPathway,
  currentSortOrder: number,
) {
  if (pathway.status === "EXITED") {
    return { label: "已退径", color: "red", variant: "paused" as const };
  }
  if (pathway.status === "COMPLETED") {
    return { label: "已完成", color: "green", variant: "completed" as const };
  }
  if (node.nodeCode === pathway.currentNodeCode) {
    return { label: "当前节点", color: "blue", variant: "current" as const };
  }
  if (currentSortOrder > 0 && node.sortOrder < currentSortOrder) {
    return { label: "已完成", color: "green", variant: "completed" as const };
  }
  return { label: "待执行", color: "default", variant: "pending" as const };
}

function nodeDisplayName(
  nodes: PathwayNode[],
  nodeCode?: string | null,
  evidenceDetailsEnabled = false,
) {
  if (!nodeCode) return "未记录";
  const node = nodes.find((item) => item.nodeCode === nodeCode);
  if (!node) return evidenceDetailsEnabled ? nodeCode : "已配置环节";
  return evidenceDetailsEnabled ? `${node.name} (${node.nodeCode})` : node.name;
}

function edgeReadableLabel(
  edge: PathwayEdge,
  nodes: PathwayNode[],
  evidenceDetailsEnabled: boolean,
) {
  return `${pathwayEdgeTypeText(edge.edgeType)}：${nodeDisplayName(
    nodes,
    edge.fromNodeCode,
    evidenceDetailsEnabled,
  )} → ${nodeDisplayName(nodes, edge.toNodeCode, evidenceDetailsEnabled)}`;
}

function firstClockForNode(clocks: ClinicalClock[], nodeCode: string) {
  return clocks.find((clock) => clock.nodeCode === nodeCode);
}

export default function PatientPathways() {
  const [selectedPathwayId, setSelectedPathwayId] = useState<string | null>(null);
  const [enterModalVisible, setEnterModalVisible] = useState<boolean>(false);
  const [varianceDrawerVisible, setVarianceDrawerVisible] = useState<boolean>(false);
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const screens = Grid.useBreakpoint();
  const isWideViewport =
    screens.md ?? (typeof window === "undefined" ? true : window.innerWidth >= 768);
  const pathwayFactColumn = isWideViewport ? 3 : 1;

  // 分页状态
  const [page, setPage] = useState<number>(1);
  const [size] = useState<number>(10);
  const [patientFilter, setPatientFilter] = useState<string>("");
  const [enterPatientFilter, setEnterPatientFilter] = useState<string>("");
  const [enterEncounterFilter, setEnterEncounterFilter] = useState<string>("");
  const [selectedContextSnapshotId, setSelectedContextSnapshotId] = useState<string>("");

  const hasEnterSnapshotFilter = Boolean(enterPatientFilter.trim() || enterEncounterFilter.trim());
  const enterSnapshotsQuery = useContextSnapshots(
    {
      patientId: enterPatientFilter.trim() || undefined,
      encounterId: enterEncounterFilter.trim() || undefined,
      status: "ACTIVE",
      page: 1,
      size: 20,
      sort: "createdAt,desc",
    },
    { enabled: enterModalVisible && hasEnterSnapshotFilter },
  );
  const selectedContextSnapshot = enterSnapshotsQuery.data?.items.find(
    (snapshot) => snapshot.snapshotId === selectedContextSnapshotId,
  );
  const entryCandidatesQuery = usePathwayEntryCandidates(
    selectedContextSnapshotId,
    PATHWAY_RUNTIME_TRIGGER,
  );
  const entryCandidateCount = entryCandidatesQuery.data?.candidates?.length ?? 0;
  const entryCandidateOptions = (entryCandidatesQuery.data?.candidates ?? []).map((candidate) => ({
    value: candidate.templateId,
    label: `${candidate.name} · ${candidate.diseaseCode}`,
  }));
  let entryCandidateStatusMessage = "请先选择已生效临床快照，再读取当前机构生效候选路径。";
  if (selectedContextSnapshotId) {
    if (entryCandidatesQuery.isLoading) {
      entryCandidateStatusMessage = "正在读取当前机构生效候选路径。";
    } else if (entryCandidateCount > 0) {
      entryCandidateStatusMessage = `已读取 ${entryCandidateCount} 条当前机构生效候选路径，确认后才会入径。`;
    } else {
      entryCandidateStatusMessage = "当前机构生效版本无可用候选路径。";
    }
  }

  const {
    data: patientPathwaysData,
    isLoading: patientPathwaysLoading,
    isError: patientPathwaysIsError,
    error: patientPathwaysError,
    refetch: refetchPathways,
  } = usePatientPathways({
    patientId: patientFilter.trim() || undefined,
    page,
    size,
  });
  const patientPathwayRows = patientPathwaysData?.items ?? [];

  // 获得已选择的患者入径实例的详情事实
  const { data: detailData, refetch: refetchDetail } = usePatientPathwayDetail(
    selectedPathwayId || "",
  );

  // 同时拉取该路径的标准拓扑节点详情，用于左侧 Milestone Timeline 精准对比绘制。
  const { data: templateDetail } = usePathwayTemplateDetail(
    detailData?.patientPathway.templateId || "",
  );
  const { data: clocksData, refetch: refetchClocks } = usePatientPathwayClocks(
    selectedPathwayId || "",
  );

  const { data: variancesData, refetch: refetchVariances } = usePatientPathwayVariances(
    selectedPathwayId || "",
  );

  // 办理入径/流转推进突变
  const enterPathwayMutation = useEnterPatientPathway();
  const advancePathwayMutation = useAdvancePatientPathway();

  // 表单对象
  const [enterForm] = Form.useForm();
  const [advanceForm] = Form.useForm();
  const [varianceForm] = Form.useForm();
  const [exitForm] = Form.useForm();
  const varianceResolutionDecision = Form.useWatch("resolutionDecision", varianceForm);

  const isPathwayMutable = (status?: PatientPathwayStatus) =>
    status === "ENTERED" || status === "NODE_EXECUTING" || status === "VARIANCE";

  const templateNodes = templateDetail?.nodes ?? [];
  const sortedTemplateNodes = [...templateNodes].sort(
    (left, right) => left.sortOrder - right.sortOrder,
  );
  const runtimeClocks = clocksData ?? detailData?.clocks ?? [];
  const currentTemplateSortOrder =
    templateNodes.find((node) => node.nodeCode === detailData?.patientPathway.currentNodeCode)
      ?.sortOrder ?? 0;
  const runtimeNodeStateClass = {
    completed: styles.pathwayRuntimeNodeCompleted,
    current: styles.pathwayRuntimeNodeCurrent,
    paused: styles.pathwayRuntimeNodePaused,
    pending: styles.pathwayRuntimeNodePending,
  };
  const milestoneTimelineItems = (detailData?.milestoneStatuses ?? []).map(
    (milestone: PathwayMilestoneRuntimeStatus) => {
      const nodeCodes = milestone.nodeCodes ?? [];
      const clocksForMilestone = runtimeClocks.filter((clock) =>
        nodeCodes.includes(clock.nodeCode),
      );

      return {
        key: milestone.milestoneId,
        color: milestoneStatusColor(milestone.status),
        children: (
          <>
            <div className={`${styles.rowBetween} ${styles.timelineTitle}`}>
              <span>{milestone.name}</span>
              <Tag color={milestoneStatusColor(milestone.status)}>
                {milestoneStatusText(milestone.status)}
              </Tag>
            </div>
            <div className={`${styles.codeText} ${styles.timelineMuted}`}>
              {milestone.phaseName} / 第 {milestone.dayOffset ?? "-"} 天
              {evidenceDetailsEnabled ? ` / ${milestone.milestoneCode}` : ""}
            </div>
            <div className={styles.evidenceBox}>
              <div className={styles.timelineMeta}>
                {evidenceDetailsEnabled
                  ? `节点: ${nodeCodes.length > 0 ? nodeCodes.join("、") : "未绑定"}`
                  : `已绑定 ${nodeCodes.length} 个路径环节`}
              </div>
              <div className={styles.timelineMeta}>
                预期: {formatDateTime(milestone.expectedAt)}
              </div>
              <div className={styles.timelineMeta}>
                达成: {formatDateTime(milestone.achievedAt)}
              </div>
              {clocksForMilestone.map((clock) => (
                <div key={clock.clockId} className={styles.timelineMeta}>
                  {evidenceDetailsEnabled ? (
                    <>
                      <span>时钟: </span>
                      <Tag color="purple" className={styles.tagCompact}>
                        {clock.clockId}
                      </Tag>
                    </>
                  ) : (
                    <span>关键时钟: </span>
                  )}
                  <span>状态: </span>
                  <Tag color={clockStatusColor(clock.status)} className={styles.tagCompact}>
                    {clockStatusText(clock.status)}
                  </Tag>
                  <span>升级: {clockEscalationText(clock.escalationLevel)}</span>
                  {evidenceDetailsEnabled && clock.metricCode && (
                    <span>关联评价指标: {clock.metricCode}</span>
                  )}
                  <span>目标: {formatDateTime(clock.targetDueAt ?? clock.dueAt)}</span>
                  <span>最晚: {formatDateTime(clock.maxDueAt)}</span>
                </div>
              ))}
            </div>
          </>
        ),
      };
    },
  );

  const patientPathwaysParsedError = patientPathwaysIsError
    ? parseApiError(patientPathwaysError, "患者路径读取失败")
    : null;
  let patientPathwaysPageState: "loading" | "forbidden" | "error" | "ready" = "ready";
  if (patientPathwaysLoading && !patientPathwaysData) {
    patientPathwaysPageState = "loading";
  } else if (patientPathwaysParsedError) {
    patientPathwaysPageState = isForbiddenApiError(patientPathwaysParsedError.code)
      ? "forbidden"
      : "error";
  }

  let patientPathwaysStateProps:
    | {
        title: string;
        description: string;
        traceId?: string;
        onRetry?: () => void;
      }
    | undefined;
  if (patientPathwaysPageState === "loading") {
    patientPathwaysStateProps = {
      title: "正在加载患者路径",
      description: "正在读取当前组织范围内的患者路径实例。",
    };
  } else if (patientPathwaysPageState === "forbidden" && patientPathwaysParsedError) {
    patientPathwaysStateProps = {
      title: "当前权限不足",
      description: patientPathwaysParsedError.message,
      traceId: patientPathwaysParsedError.traceId,
    };
  } else if (patientPathwaysPageState === "error" && patientPathwaysParsedError) {
    patientPathwaysStateProps = {
      title: "患者路径读取失败",
      description: patientPathwaysParsedError.message,
      traceId: patientPathwaysParsedError.traceId,
      onRetry: () => {
        void refetchPathways();
      },
    };
  }

  // 办理患者入径
  const handleEnterPathway = async () => {
    try {
      const values = await enterForm.validateFields();
      if (!selectedContextSnapshot) {
        message.error("请先选择已生效临床快照");
        return;
      }
      await enterPathwayMutation.mutateAsync({
        contextSnapshotId: selectedContextSnapshot.snapshotId,
        templateId: values.templateId,
        triggerPoint: PATHWAY_RUNTIME_TRIGGER,
        startNodeCode: values.startNodeCode?.trim() || undefined,
      });

      message.success("患者已入径，路径列表已刷新");
      setEnterModalVisible(false);
      setEnterPatientFilter("");
      setEnterEncounterFilter("");
      setSelectedContextSnapshotId("");
      enterForm.resetFields();
      refetchPathways();
    } catch (error: unknown) {
      if (applyApiFieldErrors(enterForm, error)) return;
      message.error(getApiErrorMessage(error, "办理患者入径失败"));
    }
  };

  // 标准节点推进 (COMPLETE)
  const handleCompleteAdvance = async () => {
    if (!selectedPathwayId || !detailData) return;
    try {
      const values = await advanceForm.validateFields();
      await advancePathwayMutation.mutateAsync({
        patientPathwayId: selectedPathwayId,
        eventType: "COMPLETE",
        triggerPoint: PATHWAY_RUNTIME_TRIGGER,
        currentNodeCode: detailData.patientPathway.currentNodeCode,
        requestedNextNodeCode: values.requestedNextNodeCode,
      });

      message.success("患者路径节点标准流转成功");
      advanceForm.resetFields();

      refetchDetail();
      refetchClocks();
      refetchVariances();
      refetchPathways();
    } catch (error: unknown) {
      if (applyApiFieldErrors(advanceForm, error)) return;
      message.error(getApiErrorMessage(error, "节点流转失败"));
    }
  };

  // 偏离路径登记变异 (VARIANCE)
  const handleVarianceAdvance = async () => {
    if (!selectedPathwayId || !detailData) return;
    try {
      const values = await varianceForm.validateFields();
      await advancePathwayMutation.mutateAsync({
        patientPathwayId: selectedPathwayId,
        eventType: "VARIANCE",
        triggerPoint: PATHWAY_RUNTIME_TRIGGER,
        currentNodeCode: detailData.patientPathway.currentNodeCode,
        requestedNextNodeCode:
          values.resolutionDecision === "REENTER" ? values.continueNodeCode : undefined,
        varianceType: values.varianceType,
        varianceReasonCode: values.reasonCode,
        varianceReason: values.reason,
        responsibleRole: values.responsibleRole,
        resolutionDecision: values.resolutionDecision,
        resolutionAction: values.resolutionAction,
        exitReason: values.resolutionDecision === "TERMINATE" ? values.exitReason : undefined,
      });

      message.warning("患者路径偏离事实变异登记成功");
      varianceForm.resetFields();

      refetchDetail();
      refetchClocks();
      refetchVariances();
      refetchPathways();
    } catch (error: unknown) {
      if (applyApiFieldErrors(varianceForm, error)) return;
      message.error(getApiErrorMessage(error, "变异登记流转失败"));
    }
  };

  // 退出路径 (EXIT)
  const handleExitAdvance = async () => {
    if (!selectedPathwayId || !detailData) return;
    try {
      const values = await exitForm.validateFields();
      await advancePathwayMutation.mutateAsync({
        patientPathwayId: selectedPathwayId,
        eventType: "EXIT",
        triggerPoint: PATHWAY_RUNTIME_TRIGGER,
        currentNodeCode: detailData.patientPathway.currentNodeCode,
        exitReason: values.exitReason,
      });

      message.info("患者已退出临床路径");
      exitForm.resetFields();

      refetchDetail();
      refetchClocks();
      refetchVariances();
      refetchPathways();
    } catch (error: unknown) {
      if (applyApiFieldErrors(exitForm, error)) return;
      message.error(getApiErrorMessage(error, "路径退径失败"));
    }
  };

  const outcomeColumns: TableProps<PathwayOutcomeBinding>["columns"] = [
    {
      title: "作用域",
      dataIndex: "scope",
      render: (scope: string) => <Tag color="blue">{outcomeScopeText(scope)}</Tag>,
    },
    {
      title: "引用",
      key: "refCode",
      render: (_value, binding) =>
        binding.scope === "TEMPLATE" ? "全路径" : binding.refCode || "-",
    },
    { title: "结局指标身份", dataIndex: "indicatorCode" },
  ];

  const warningColumns: TableProps<PathwayCoordinationWarning>["columns"] = [
    {
      title: "类型",
      dataIndex: "warningType",
      render: (type: string) => <Tag color="orange">{type}</Tag>,
    },
    {
      title: "共享引用",
      dataIndex: "sharedRef",
      render: (value?: string | null) => value ?? "-",
    },
    {
      title: "冲突路径",
      key: "conflict",
      render: (_value, warning) => (
        <span className={styles.codeText}>
          {warning.conflictWithPatientPathwayId ?? "-"} / {warning.conflictWithNodeCode ?? "-"}
        </span>
      ),
    },
    { title: "提示", dataIndex: "message" },
  ];

  const columns: TableProps<PatientPathway>["columns"] = [
    ...(evidenceDetailsEnabled
      ? [
          {
            title: "实例编号",
            dataIndex: "patientPathwayId",
            key: "patientPathwayId",
            render: (text: string) => (
              <span className={`${styles.codeText} ${styles.textStrong}`}>{text}</span>
            ),
          },
          {
            title: "患者编号",
            dataIndex: "patientId",
            key: "patientId",
            className: styles.textStrong,
          },
          {
            title: "就诊编号",
            dataIndex: "encounterId",
            key: "encounterId",
            render: (text: string) => <Tag color="blue">{text}</Tag>,
          },
        ]
      : [
          {
            title: "患者与就诊",
            key: "patientContext",
            render: () => <span className={styles.textStrong}>已关联患者与就诊</span>,
          },
        ]),
    {
      title: "当前路径",
      dataIndex: "templateId",
      key: "templateId",
      render: (text: string) => {
        const templateName =
          templateDetail?.template.templateId === text ? templateDetail.template.name : undefined;
        return (
          <Tag color="geekblue">
            {evidenceDetailsEnabled ? text : (templateName ?? "当前运行路径")}
          </Tag>
        );
      },
    },
    {
      title: "当前流转节点",
      dataIndex: "currentNodeCode",
      key: "currentNodeCode",
      render: (c: string, record: PatientPathway) => {
        if (record.status === "EXITED") return <Tag color="red">已退径</Tag>;
        if (record.status === "COMPLETED") return <Tag color="green">已完成路径</Tag>;
        return (
          <Tag color="orange">{nodeDisplayName(templateNodes, c, evidenceDetailsEnabled)}</Tag>
        );
      },
    },
    {
      title: "流转状态",
      dataIndex: "status",
      key: "status",
      render: (status: PatientPathwayStatus) => {
        const config: Record<string, { status: PathwayBadgeStatus; text: string }> = {
          ENTERED: { status: "processing", text: "已进入路径" },
          NODE_EXECUTING: { status: "processing", text: "节点执行中" },
          VARIANCE: { status: "warning", text: "变异处理中" },
          COMPLETED: { status: "success", text: "已完成" },
          EXITED: { status: "error", text: "已退出路径" },
        };
        const current = config[status] ?? {
          status: "default",
          text: customerEnumLabel(status),
        };
        return <Badge status={current.status} text={current.text} />;
      },
    },
    {
      title: "操作",
      key: "action",
      render: (record: PatientPathway) => (
        <Button
          type="link"
          icon={<CompassOutlined />}
          onClick={() => {
            setSelectedPathwayId(record.patientPathwayId);
            setVarianceDrawerVisible(false);
          }}
          className={styles.linkButton}
        >
          办理推进与解释追溯
        </Button>
      ),
    },
  ];

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
      state={patientPathwaysPageState}
      stateProps={patientPathwaysStateProps}
    >
      <div className={`${styles.surface} ${styles.filterSurface}`}>
        <Form layout="inline" className={styles.inlineForm}>
          <Form.Item label="患者检索" htmlFor="patient-pathway-list-patient-filter">
            <Input
              id="patient-pathway-list-patient-filter"
              placeholder="输入姓名、门急诊号或院内患者编号"
              allowClear
              value={patientFilter}
              onChange={(e) => setPatientFilter(e.target.value)}
              className={styles.searchInput}
            />
          </Form.Item>
          <Form.Item className={styles.actionItem}>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEnterPatientFilter("");
                setEnterEncounterFilter("");
                setSelectedContextSnapshotId("");
                enterForm.resetFields();
                setEnterModalVisible(true);
              }}
            >
              办理患者入径
            </Button>
          </Form.Item>
        </Form>
      </div>

      <div className={styles.surface}>
        <Table
          columns={columns}
          dataSource={patientPathwayRows}
          rowKey="patientPathwayId"
          loading={patientPathwaysLoading}
          pagination={{
            current: page,
            pageSize: size,
            total: patientPathwaysData?.total ?? 0,
            onChange: (p) => setPage(p),
            showTotal: (t) => `共 ${t} 个临床协同中的患者实例`,
          }}
          locale={{
            emptyText: "暂无患者路径实例。请先办理患者入径，或调整检索条件。",
          }}
          className="medkernel-table"
        />
      </div>

      <Modal
        title="办理患者临床路径准入"
        open={enterModalVisible}
        onOk={handleEnterPathway}
        onCancel={() => {
          setEnterModalVisible(false);
          setEnterPatientFilter("");
          setEnterEncounterFilter("");
          setSelectedContextSnapshotId("");
          enterForm.resetFields();
        }}
        width="min(680px, calc(100vw - 32px))"
        confirmLoading={enterPathwayMutation.isPending}
        destroyOnClose
      >
        <Form form={enterForm} layout="vertical" className={styles.formGap}>
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item label="患者信息" htmlFor="patient-pathway-entry-patient-filter">
                <Input
                  id="patient-pathway-entry-patient-filter"
                  allowClear
                  placeholder="输入姓名、门急诊号或院内患者编号"
                  value={enterPatientFilter}
                  onChange={(event) => {
                    setEnterPatientFilter(event.target.value);
                    setSelectedContextSnapshotId("");
                  }}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="就诊信息" htmlFor="patient-pathway-entry-encounter-filter">
                <Input
                  id="patient-pathway-entry-encounter-filter"
                  allowClear
                  placeholder="输入门急诊号、住院号或就诊编号"
                  value={enterEncounterFilter}
                  onChange={(event) => {
                    setEnterEncounterFilter(event.target.value);
                    setSelectedContextSnapshotId("");
                  }}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="已生效临床快照" required>
            <ContextSnapshotSelector
              enabled={hasEnterSnapshotFilter}
              loading={enterSnapshotsQuery.isLoading}
              error={enterSnapshotsQuery.isError}
              snapshots={enterSnapshotsQuery.data?.items ?? []}
              selectedSnapshotId={selectedContextSnapshotId}
              onSelect={setSelectedContextSnapshotId}
              evidenceDetailsEnabled={evidenceDetailsEnabled}
            />
          </Form.Item>
          <Form.Item
            name="templateId"
            label="选择当前运行候选路径"
            rules={[{ required: true, message: "请选择当前运行候选路径" }]}
          >
            <Select
              showSearch
              virtual={false}
              optionFilterProp="label"
              placeholder="选择当前机构生效版本允许的路径"
              loading={entryCandidatesQuery.isLoading}
              notFoundContent={
                selectedContextSnapshotId ? "当前机构生效版本无可用候选路径" : "请先选择临床快照"
              }
              options={entryCandidateOptions}
            />
          </Form.Item>
          {entryCandidatesQuery.isError ? (
            <Alert
              type="error"
              showIcon
              message={getApiErrorMessage(entryCandidatesQuery.error, "运行候选路径读取失败")}
            />
          ) : (
            <Alert type="info" showIcon message={entryCandidateStatusMessage} />
          )}
          <Form.Item name="startNodeCode" label="起始临床推进节点 (可选，留空使用路径起点)">
            <Input placeholder="留空使用已发布临床路径的起始节点" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        aria-label="患者路径推进与解释追溯"
        title={
          <div className={styles.drawerTitle}>
            <CompassOutlined className={styles.iconInfo} />
            <span>患者路径推进与解释追溯</span>
          </div>
        }
        width="min(960px, 100vw)"
        onClose={() => setSelectedPathwayId(null)}
        open={!!selectedPathwayId}
        destroyOnClose
      >
        {detailData && (
          <div>
            <Descriptions
              title="入径运行事实"
              bordered
              column={pathwayFactColumn}
              size="small"
              className={styles.sectionGapLg}
            >
              {evidenceDetailsEnabled ? (
                <>
                  <Descriptions.Item label="患者编号">
                    <span className={styles.textStrong}>{detailData.patientPathway.patientId}</span>
                  </Descriptions.Item>
                  <Descriptions.Item label="就诊编号">
                    <span className={styles.codeText}>{detailData.patientPathway.encounterId}</span>
                  </Descriptions.Item>
                  <Descriptions.Item label="临床路径版本编号">
                    <Tag color="geekblue">{detailData.patientPathway.templateId}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="入径实例编号">
                    <span className={styles.codeText}>
                      {detailData.patientPathway.patientPathwayId}
                    </span>
                  </Descriptions.Item>
                </>
              ) : (
                <>
                  <Descriptions.Item label="患者与就诊">
                    <span className={styles.textStrong}>患者与就诊已关联</span>
                  </Descriptions.Item>
                  <Descriptions.Item label="当前路径">
                    <Tag color="geekblue">{templateDetail?.template.name ?? "当前运行路径"}</Tag>
                  </Descriptions.Item>
                </>
              )}
              <Descriptions.Item label="当前激活节点">
                <Tag color="orange">
                  {nodeDisplayName(
                    templateNodes,
                    detailData.patientPathway.currentNodeCode || null,
                    evidenceDetailsEnabled,
                  ) || "无/已结径"}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="运行状态">
                <Badge
                  status={
                    isPathwayMutable(detailData.patientPathway.status) ? "processing" : "default"
                  }
                  text={customerEnumLabel(detailData.patientPathway.status)}
                />
              </Descriptions.Item>
              <Descriptions.Item label="准入时间" span={pathwayFactColumn}>
                {formatClinicalDateTime(detailData.patientPathway.enteredAt, "未知")}
              </Descriptions.Item>
            </Descriptions>

            {templateDetail && sortedTemplateNodes.length > 0 && (
              <Card
                title={
                  <div className={styles.sectionTitle}>
                    <BranchesOutlined className={styles.iconInfo} />
                    <span>医生只读路径图</span>
                  </div>
                }
                className={`${styles.detailCard} ${styles.sectionGapLg}`}
              >
                <section aria-label="医生只读路径图" className={styles.pathwayReadOnlyGraph}>
                  <div className={styles.pathwayGraphHeader}>
                    <div>
                      <div className={styles.pathwayGraphTitle}>{templateDetail.template.name}</div>
                      <div className={styles.contentText}>
                        {templateDetail.template.description &&
                        templateDetail.template.description !== templateDetail.template.name
                          ? templateDetail.template.description
                          : "按已发布临床路径版本显示运行态全貌。"}
                      </div>
                    </div>
                    <Tag color="blue" className={styles.pathwayCurrentTag}>
                      当前患者位置
                    </Tag>
                  </div>
                  <Alert
                    type="info"
                    showIcon
                    message="已完成/当前/待执行只读展示，不自动开立或修改医嘱。"
                    className={styles.sectionGap}
                  />
                  <div className={styles.pathwayRuntimeLegend} aria-label="路径状态图例">
                    <span>
                      <Badge status="success" text="已完成" />
                    </span>
                    <span>
                      <Badge status="processing" text="当前节点" />
                    </span>
                    <span>
                      <Badge status="default" text="待执行" />
                    </span>
                  </div>
                  <div className={styles.pathwayRuntimeGraph}>
                    {sortedTemplateNodes.map((node) => {
                      const state = pathwayRuntimeNodeState(
                        node,
                        detailData.patientPathway,
                        currentTemplateSortOrder,
                      );
                      const activeClock = firstClockForNode(runtimeClocks, node.nodeCode);
                      const summary = nodeVisibleSummary(node);
                      const outgoingEdges = (templateDetail.edges ?? [])
                        .filter((edge) => edge.fromNodeCode === node.nodeCode)
                        .sort((left, right) => left.priority - right.priority);

                      return (
                        <div key={node.nodeId} className={styles.pathwayRuntimeSegment}>
                          <article
                            className={`${styles.pathwayRuntimeNode} ${
                              runtimeNodeStateClass[state.variant]
                            }`}
                            aria-label={`路径节点 ${
                              evidenceDetailsEnabled ? node.nodeCode : node.name
                            } ${state.label}`}
                          >
                            <div className={styles.pathwayRuntimeNodeHeader}>
                              {evidenceDetailsEnabled && (
                                <Tag color="geekblue">{node.nodeCode}</Tag>
                              )}
                              <Tag color={state.color}>{state.label}</Tag>
                            </div>
                            <div className={styles.pathwayRuntimeNodeName}>{node.name}</div>
                            <div className={styles.pathwayRuntimeNodeMeta}>
                              <span>{pathwayNodeTypeText(node.nodeType)}</span>
                              <span>{node.responsibleRole || "责任角色未配置"}</span>
                              <span>{formatWindowMinutes(node.timeWindowMinutes)}</span>
                              {node.terminalFlag && <span>终点</span>}
                            </div>
                            {activeClock && (
                              <div className={styles.pathwayRuntimeEvidence}>
                                <span>
                                  {evidenceDetailsEnabled
                                    ? `时钟 ${activeClock.clockId}`
                                    : "关键时钟"}
                                </span>
                                <Tag
                                  color={clockStatusColor(activeClock.status)}
                                  className={styles.tagCompact}
                                >
                                  {clockStatusText(activeClock.status)}
                                </Tag>
                                {evidenceDetailsEnabled && activeClock.metricCode && (
                                  <span>评价指标: {activeClock.metricCode}</span>
                                )}
                              </div>
                            )}
                            {summary && <div className={styles.contentText}>{summary}</div>}
                          </article>
                          {outgoingEdges.length > 0 && (
                            <div className={styles.pathwayRuntimeEdges}>
                              {outgoingEdges.map((edge) => (
                                <div key={edge.edgeId} className={styles.pathwayRuntimeEdge}>
                                  <span>
                                    {edgeReadableLabel(
                                      edge,
                                      sortedTemplateNodes,
                                      evidenceDetailsEnabled,
                                    )}
                                  </span>
                                  {evidenceDetailsEnabled && edge.conditionJson && (
                                    <span className={styles.codeText}>条件已配置</span>
                                  )}
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </section>
              </Card>
            )}

            {(detailData.coordinationWarnings ?? []).length > 0 && (
              <Card
                title={
                  <div className={styles.sectionTitle}>
                    <WarningOutlined className={styles.iconInfo} />
                    <span>多路径协调提示</span>
                  </div>
                }
                className={`${styles.detailCard} ${styles.sectionGapLg}`}
              >
                <Alert
                  type="warning"
                  showIcon
                  message="存在并行路径协调提示，系统仅提示协调，不自动改医嘱。"
                  className={styles.sectionGap}
                />
                <Table
                  dataSource={detailData.coordinationWarnings ?? []}
                  rowKey={(warning) =>
                    `${warning.warningType}-${warning.sharedRef ?? ""}-${warning.conflictWithPatientPathwayId ?? ""}`
                  }
                  columns={warningColumns}
                  pagination={false}
                  size="small"
                  className="medkernel-table"
                />
              </Card>
            )}

            {(detailData.outcomeBindings ?? []).length > 0 && (
              <Card
                title={
                  <div className={styles.sectionTitle}>
                    <FileTextOutlined className={styles.iconInfo} />
                    <span>结局指标闭环</span>
                  </div>
                }
                className={`${styles.detailCard} ${styles.sectionGapLg}`}
              >
                <Table
                  dataSource={detailData.outcomeBindings ?? []}
                  rowKey={(binding) =>
                    `${binding.scope}-${binding.refCode ?? "TEMPLATE"}-${binding.indicatorCode}`
                  }
                  columns={outcomeColumns}
                  pagination={false}
                  size="small"
                  className="medkernel-table"
                />
              </Card>
            )}

            <Row gutter={[16, 16]}>
              <Col xs={24} xl={10}>
                <Card
                  title={
                    <div className={styles.sectionTitle}>
                      <CalendarOutlined className={styles.iconInfo} />
                      <span>时间线与关键时钟 (Milestones)</span>
                    </div>
                  }
                  className={styles.detailCard}
                >
                  <div className={styles.scrollPanel}>
                    <Timeline
                      className={styles.marginTopMd}
                      items={
                        milestoneTimelineItems.length > 0
                          ? milestoneTimelineItems
                          : templateNodes.map((node) => {
                              const isCurrent =
                                node.nodeCode === detailData.patientPathway.currentNodeCode;
                              const activeClock = clocksData?.find(
                                (c) => c.nodeCode === node.nodeCode,
                              );

                              let color = "gray";
                              if (isCurrent) color = "blue";
                              else if (node.sortOrder < currentTemplateSortOrder) {
                                color = "green";
                              }

                              return {
                                key: node.nodeId,
                                color,
                                children: (
                                  <>
                                    <div className={`${styles.rowBetween} ${styles.timelineTitle}`}>
                                      <span>{node.name}</span>
                                      {isCurrent && <Tag color="blue">当前活动</Tag>}
                                    </div>
                                    <div className={`${styles.codeText} ${styles.timelineMuted}`}>
                                      {evidenceDetailsEnabled
                                        ? node.nodeCode
                                        : pathwayNodeTypeText(node.nodeType)}
                                    </div>

                                    {activeClock && (
                                      <div className={styles.evidenceBox}>
                                        <div className={styles.rowBetween}>
                                          <span>
                                            {evidenceDetailsEnabled ? (
                                              <>
                                                时钟:{" "}
                                                <Tag color="purple" className={styles.tagCompact}>
                                                  {activeClock.clockId}
                                                </Tag>
                                              </>
                                            ) : (
                                              "关键时钟"
                                            )}
                                          </span>
                                          <span>
                                            状态:{" "}
                                            <Tag
                                              color={clockStatusColor(activeClock.status)}
                                              className={styles.tagCompact}
                                            >
                                              {clockStatusText(activeClock.status)}
                                            </Tag>
                                          </span>
                                        </div>
                                        <div className={styles.timelineMeta}>
                                          开始:{" "}
                                          {new Date(activeClock.startedAt).toLocaleTimeString()}
                                        </div>
                                        <div className={styles.timelineMeta}>
                                          目标:{" "}
                                          {formatDateTime(
                                            activeClock.targetDueAt ?? activeClock.dueAt,
                                          )}
                                        </div>
                                        <div className={styles.timelineMeta}>
                                          最早/最晚: {formatDateTime(activeClock.minDueAt)} /{" "}
                                          {formatDateTime(activeClock.maxDueAt)}
                                        </div>
                                        <div className={styles.timelineMeta}>
                                          超时升级:{" "}
                                          {clockEscalationText(activeClock.escalationLevel)}
                                        </div>
                                        {evidenceDetailsEnabled && activeClock.metricCode && (
                                          <div className={styles.timelineMeta}>
                                            关联评价指标: {activeClock.metricCode}
                                          </div>
                                        )}
                                      </div>
                                    )}
                                  </>
                                ),
                              };
                            })
                      }
                    />
                  </div>
                </Card>
              </Col>

              <Col xs={24} xl={14}>
                <Card
                  title={
                    <div className={styles.sectionTitle}>
                      <RightCircleOutlined className={styles.iconInfo} />
                      <span>受控推进决策</span>
                    </div>
                  }
                  className={styles.detailCard}
                >
                  <Tabs
                    defaultActiveKey="complete"
                    size="small"
                    items={[
                      {
                        key: "complete",
                        disabled: !isPathwayMutable(detailData.patientPathway.status),
                        label: (
                          <span>
                            <RightCircleOutlined /> 标准流转
                          </span>
                        ),
                        children: (
                          <Form
                            form={advanceForm}
                            layout="vertical"
                            className={styles.marginTopSm}
                            onFinish={handleCompleteAdvance}
                          >
                            <Alert
                              message="标准推进：完成当前节点并进入下一个标准路径节点。系统会按临床路径出边计算下一步并刷新关键时钟。"
                              type="success"
                              showIcon
                              className={styles.sectionGap}
                            />
                            <Form.Item
                              name="requestedNextNodeCode"
                              label="指定流转目标节点"
                              rules={[{ required: true }]}
                            >
                              <Select
                                virtual={false}
                                placeholder="选择出边允许推进的下一节点"
                                aria-label="指定流转目标节点"
                              >
                                {templateDetail?.edges
                                  .filter(
                                    (e) =>
                                      e.fromNodeCode === detailData.patientPathway.currentNodeCode,
                                  )
                                  .map((e) => {
                                    const targetNode = templateDetail.nodes.find(
                                      (n) => n.nodeCode === e.toNodeCode,
                                    );
                                    return (
                                      <Option key={e.edgeId} value={e.toNodeCode}>
                                        {targetNode
                                          ? nodeDisplayName(
                                              templateDetail.nodes,
                                              targetNode.nodeCode,
                                              evidenceDetailsEnabled,
                                            )
                                          : "未知"}
                                      </Option>
                                    );
                                  })}
                              </Select>
                            </Form.Item>
                            <Button
                              type="primary"
                              htmlType="submit"
                              icon={<RightCircleOutlined />}
                              loading={advancePathwayMutation.isPending}
                              className={styles.fullWidth}
                            >
                              完成当前节点并推进
                            </Button>
                          </Form>
                        ),
                      },

                      {
                        key: "variance",
                        disabled: !isPathwayMutable(detailData.patientPathway.status),
                        label: (
                          <span>
                            <WarningOutlined /> 登记变异
                          </span>
                        ),
                        children: (
                          <Form
                            form={varianceForm}
                            layout="vertical"
                            className={styles.marginTopSm}
                            onFinish={handleVarianceAdvance}
                          >
                            <Alert
                              message="临床变异：登记患者偏离当前路径节点的事实、原因码、责任角色和处置决策，平台统一生成审计事实。"
                              type="warning"
                              showIcon
                              className={styles.sectionGap}
                            />
                            <Row gutter={12}>
                              <Col span={12}>
                                <Form.Item
                                  name="varianceType"
                                  label="变异分类"
                                  rules={[{ required: true }]}
                                >
                                  <Select placeholder="选择分类" aria-label="变异分类">
                                    <Option value="CLINICAL">临床原因</Option>
                                    <Option value="SYSTEM">系统原因</Option>
                                    <Option value="PATIENT">患者原因</Option>
                                    <Option value="FAMILY">家属原因</Option>
                                  </Select>
                                </Form.Item>
                              </Col>
                              <Col span={12}>
                                <Form.Item
                                  name="resolutionDecision"
                                  label="处置决策"
                                  rules={[{ required: true }]}
                                >
                                  <Select placeholder="选择处置决策" aria-label="处置决策">
                                    <Option value="HOLD">暂停观察</Option>
                                    <Option value="REENTER">再次进入路径</Option>
                                    <Option value="TERMINATE">终止路径</Option>
                                  </Select>
                                </Form.Item>
                              </Col>
                            </Row>
                            <Row gutter={12}>
                              <Col span={12}>
                                <Form.Item
                                  name="reasonCode"
                                  label="原因码"
                                  rules={[{ required: true }]}
                                >
                                  <Input placeholder="如 CLINICAL_ESCALATION" />
                                </Form.Item>
                              </Col>
                              <Col span={12}>
                                <Form.Item
                                  name="responsibleRole"
                                  label="责任角色"
                                  rules={[{ required: true }]}
                                >
                                  <Input placeholder="如 主管医师" />
                                </Form.Item>
                              </Col>
                            </Row>
                            <Form.Item
                              name="reason"
                              label="变异偏离事实说明"
                              rules={[{ required: true }]}
                            >
                              <TextArea rows={2} placeholder="请输入经医师确认的变异事实说明" />
                            </Form.Item>
                            {varianceResolutionDecision === "REENTER" && (
                              <Form.Item
                                name="continueNodeCode"
                                label="再入径节点"
                                dependencies={["resolutionDecision"]}
                                rules={[{ required: true, message: "请选择再入径节点" }]}
                              >
                                <Select
                                  virtual={false}
                                  placeholder="选择目标节点"
                                  aria-label="再入径节点"
                                >
                                  {templateDetail?.nodes.map((n) => (
                                    <Option key={n.nodeId} value={n.nodeCode}>
                                      {nodeDisplayName(
                                        templateDetail.nodes,
                                        n.nodeCode,
                                        evidenceDetailsEnabled,
                                      )}
                                    </Option>
                                  ))}
                                </Select>
                              </Form.Item>
                            )}
                            {varianceResolutionDecision === "TERMINATE" && (
                              <Form.Item name="exitReason" label="终止原因">
                                <Input placeholder="默认使用变异事实说明" />
                              </Form.Item>
                            )}
                            <Form.Item
                              name="resolutionAction"
                              label="处置动作说明"
                              rules={[{ required: true }]}
                            >
                              <Input placeholder="请输入已确认的处置动作" />
                            </Form.Item>
                            <Button
                              type="primary"
                              htmlType="submit"
                              danger
                              icon={<WarningOutlined />}
                              loading={advancePathwayMutation.isPending}
                              className={`${styles.fullWidth} ${styles.marginTopSm}`}
                            >
                              提交变异决策
                            </Button>
                          </Form>
                        ),
                      },

                      {
                        key: "exit",
                        disabled: !isPathwayMutable(detailData.patientPathway.status),
                        label: (
                          <span>
                            <DisconnectOutlined /> 退径
                          </span>
                        ),
                        children: (
                          <Form
                            form={exitForm}
                            layout="vertical"
                            className={styles.marginTopSm}
                            onFinish={handleExitAdvance}
                          >
                            <Alert
                              message="人工退径：将患者从此专病路径中退出。该操作属于高危行为，会关闭当前关键时钟，并需要接受临床质量管理监督。"
                              type="error"
                              showIcon
                              className={styles.sectionGap}
                            />
                            <Form.Item
                              name="exitReason"
                              label="申请强制退径理由"
                              rules={[{ required: true }]}
                            >
                              <TextArea
                                rows={3}
                                placeholder="如：患者由于转院、临床急救或死亡，需要立刻断开受控路径..."
                              />
                            </Form.Item>
                            <Button
                              type="primary"
                              htmlType="submit"
                              danger
                              icon={<DisconnectOutlined />}
                              loading={advancePathwayMutation.isPending}
                              className={`${styles.fullWidth} ${styles.marginTopSm}`}
                            >
                              确认退出路径
                            </Button>
                          </Form>
                        ),
                      },
                    ]}
                  />
                </Card>
              </Col>
            </Row>

            <div className={styles.centerAction}>
              <Button
                type="default"
                icon={<WarningOutlined />}
                onClick={() => {
                  setVarianceDrawerVisible(true);
                  refetchVariances();
                }}
                className={styles.wideAction}
              >
                查看变异事实与审计线索
              </Button>
            </div>
          </div>
        )}
      </Drawer>

      <Drawer
        aria-label="临床路径变异事实"
        title={
          <div className={styles.drawerTitle}>
            <WarningOutlined className={styles.iconInfo} />
            <span>临床路径变异事实</span>
          </div>
        }
        width="min(640px, 100vw)"
        onClose={() => setVarianceDrawerVisible(false)}
        open={varianceDrawerVisible}
        destroyOnClose
      >
        {(variancesData ?? detailData?.variances ?? []).length > 0 ? (
          <div>
            <Alert
              message="这里仅展示已记录的路径变异事实，页面不补写原因、不生成本地变异记录。"
              type="info"
              showIcon
              className={styles.sectionGapLg}
            />

            <Card
              title={
                <div className={styles.sectionTitle}>
                  <FileTextOutlined className={styles.iconInfo} />
                  <span>变异记录</span>
                </div>
              }
              className={`${styles.detailCard} ${styles.sectionGapLg}`}
            >
              <Timeline
                items={(variancesData ?? detailData?.variances ?? []).map(
                  (variance: PathwayVariance) => ({
                    key: variance.varianceId,
                    color: "orange",
                    children: (
                      <div>
                        <div className={styles.timelineTitle}>
                          {nodeDisplayName(
                            templateNodes,
                            variance.nodeCode,
                            evidenceDetailsEnabled,
                          )}{" "}
                          · {customerEnumLabel(variance.varianceType)}
                        </div>
                        {evidenceDetailsEnabled && (
                          <div className={styles.timelineMeta}>
                            <span>变异编号：</span>
                            <span>{variance.varianceId}</span>
                          </div>
                        )}
                        <div className={styles.timelineMeta}>
                          <span>变异原因：</span>
                          <Tag color="orange">
                            {evidenceDetailsEnabled ? variance.reasonCode : "已分类"}
                          </Tag>
                        </div>
                        <div className={styles.timelineMeta}>{variance.reason}</div>
                        <div className={styles.timelineMeta}>
                          <span>责任角色：</span>
                          <span>{variance.responsibleRole}</span>
                        </div>
                        <div className={styles.timelineMeta}>
                          <span>处置决策：</span>
                          <Tag color={variance.resolutionDecision === "TERMINATE" ? "red" : "blue"}>
                            {customerEnumLabel(variance.resolutionDecision)}
                          </Tag>
                        </div>
                        <div className={styles.timelineMeta}>
                          <span>处置动作：</span>
                          <span>{variance.resolutionAction || "未记录"}</span>
                        </div>
                        {variance.continueNodeCode && (
                          <div className={styles.timelineMeta}>
                            <span>继续节点：</span>
                            <Tag color="blue">
                              {nodeDisplayName(
                                templateNodes,
                                variance.continueNodeCode,
                                evidenceDetailsEnabled,
                              )}
                            </Tag>
                          </div>
                        )}
                        {evidenceDetailsEnabled && variance.traceId && (
                          <div className={`${styles.timelineMeta} ${styles.timelineMuted}`}>
                            追踪号：{variance.traceId}
                          </div>
                        )}
                        <div className={`${styles.timelineMeta} ${styles.timelineMuted}`}>
                          {formatClinicalDateTime(variance.createdAt, "未返回时间")}
                        </div>
                      </div>
                    ),
                  }),
                )}
              />
            </Card>
          </div>
        ) : (
          <div className={`${styles.emptyState} ${styles.emptyStateCompact}`}>
            <WarningOutlined className={styles.emptyIcon} />
            <span>当前路径实例暂无变异记录。</span>
          </div>
        )}
      </Drawer>
    </PageExperienceShell>
  );
}
