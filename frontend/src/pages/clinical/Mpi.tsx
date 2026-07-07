import { useState, useMemo, useCallback, type ReactNode } from "react";
import {
  App as AntdApp,
  Table,
  Alert,
  Button,
  Descriptions,
  Drawer,
  Input,
  InputNumber,
  List,
  Select,
  Modal,
  Form,
  Tag,
  Tooltip,
  Space,
  Typography,
  Badge,
} from "antd";
import {
  UserOutlined,
  MergeCellsOutlined,
  SearchOutlined,
  ReloadOutlined,
  WarningOutlined,
  TeamOutlined,
  CalendarOutlined,
  UserAddOutlined,
  BranchesOutlined,
  FileDoneOutlined,
  ReadOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import {
  useCreateContextSnapshot,
  useCreateMpiPatient,
  useMpiPatientDetail,
  useMpiPatients,
  useMpiStats,
  useMergeMpiPatients,
  useSecurityProfile,
  useSplitMpiPatient,
  type MpiPatientCreatePayload,
  type MpiPatient,
  type MpiPatientDetailResponse,
  type SecurityProfile,
  type ContextSnapshotCreatePayload,
  type FrontdeskEncounterType,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import { findRouteByPath } from "@/shared/config/routes";
import { customerDisplayText, customerEnumLabel } from "@/shared/config/customerLabels";
import {
  contextRiskLevelOptions,
  defaultContextSnapshotFormValues,
  frontdeskEncounterTypeOptions,
} from "@/shared/config/clinicalContext";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import styles from "./Mpi.module.css";

const { Option } = Select;
const { Text } = Typography;
const route = findRouteByPath("/mpi");
const PAGE_META = {
  title: route?.title ?? "患者索引",
  experience: route?.experience ?? {
    primaryRole: "临床使用者",
    goal: "查阅授权范围内的患者索引状态",
    defaultView: "待核查记录",
    defaultFilters: [],
    evidenceDetailContent: ["患者主索引编号", "临床快照编号", "路径实例编号", "追踪号"],
    interruptionLevel: "info" as const,
    evidence: "患者身份合并、拆分和 360 视图均保留审计证据",
    dataScale: {
      expected: "large" as const,
      pagination: "page" as const,
      exportStrategy: "none" as const,
    },
    riskLevel: "medium" as const,
  },
};

type ContextSnapshotFormValues = {
  encounterType: FrontdeskEncounterType;
  diseaseCode: string;
  riskLevel: ContextSnapshotCreatePayload["riskLevel"];
  currentMedicationText?: string;
  allergyIntoleranceText?: string;
  heightCm?: number | null;
  weightKg?: number | null;
  diagnosticReportType?: string;
  diagnosticReportConclusion?: string;
  diagnosticReportKeyFindingsText?: string;
  nursingAssessmentType?: string;
  nursingRiskLevel?: ContextSnapshotCreatePayload["riskLevel"];
  nursingAssessmentStatus?: string;
  carePlanPathwayId?: string;
  carePlanCurrentNodeId?: string;
  carePlanVarianceCode?: string;
  carePlanPlannedFinishAt?: string;
  insuranceClaimDrgCode?: string;
  insuranceClaimTotalCost?: number | null;
  insuranceClaimPaidAmount?: number | null;
  reason: string;
};

function hasPermission(profile: SecurityProfile | undefined, code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function canOpenMenu(profile: SecurityProfile | undefined, menuKey: string) {
  return (
    profile?.menuKeys?.includes(menuKey) ||
    profile?.permissions.some((permission) => permission.code === `menu.${menuKey}`) ||
    false
  );
}

function snapshotEncounterId(
  snapshot:
    | MpiPatientDetailResponse["latestContextSnapshot"]
    | MpiPatientDetailResponse["contextSnapshot"]
    | null,
) {
  if (snapshot && "encounterId" in snapshot) return snapshot.encounterId;
  return "暂无";
}

function getPatientDetailSnapshot(detail: MpiPatientDetailResponse) {
  return detail.latestContextSnapshot ?? detail.contextSnapshot ?? null;
}

function genderLabel(gender?: string) {
  if (gender === "M") return "男";
  if (gender === "F") return "女";
  return "未知";
}

function mergedIntoText(
  patient: MpiPatientDetailResponse["patient"],
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) return patient.mergedIntoMpiId ?? "未合并";
  return patient.mergedIntoMpiId ? "已合并至目标主索引" : "未合并";
}

function renderPatient360Detail(
  detail: MpiPatientDetailResponse,
  evidenceDetailsEnabled: boolean,
  contextAction?: ReactNode,
) {
  const snapshot = getPatientDetailSnapshot(detail);
  const patient = detail.patient;

  return (
    <Space direction="vertical" size="large" className={styles.fullWidth}>
      <Descriptions title="患者主索引" bordered column={2} size="small">
        {evidenceDetailsEnabled && (
          <Descriptions.Item label="患者主索引编号">{patient.mpiId}</Descriptions.Item>
        )}
        <Descriptions.Item label="患者">{patient.maskedName}</Descriptions.Item>
        <Descriptions.Item label="性别">{customerDisplayText(patient.gender)}</Descriptions.Item>
        <Descriptions.Item label="年龄">{patient.age} 岁</Descriptions.Item>
        <Descriptions.Item label="身份证后四位">*** {patient.idLast4}</Descriptions.Item>
        <Descriptions.Item label="主索引状态">
          {customerEnumLabel(patient.status)}
        </Descriptions.Item>
        <Descriptions.Item label="合并指向">
          {mergedIntoText(patient, evidenceDetailsEnabled)}
        </Descriptions.Item>
        <Descriptions.Item label="已并入数">{patient.mergedCount}</Descriptions.Item>
      </Descriptions>

      <Descriptions title="上下文快照" bordered column={2} size="small">
        {evidenceDetailsEnabled ? (
          <>
            <Descriptions.Item label="快照编号">
              {snapshot?.snapshotId ?? "暂无快照"}
            </Descriptions.Item>
            <Descriptions.Item label="就诊编号">{snapshotEncounterId(snapshot)}</Descriptions.Item>
          </>
        ) : (
          <Descriptions.Item label="患者身份与就诊上下文" span={2}>
            {snapshot ? "患者身份与就诊上下文已关联" : "暂无已生效上下文"}
          </Descriptions.Item>
        )}
        <Descriptions.Item label="快照状态">
          {snapshot?.status ? customerEnumLabel(snapshot.status) : "暂无"}
        </Descriptions.Item>
        <Descriptions.Item label="质量状态">
          {snapshot?.qualityStatus ? customerDisplayText(snapshot.qualityStatus) : "暂无"}
        </Descriptions.Item>
      </Descriptions>

      {contextAction}

      <List
        header={`在径路径 ${detail.activePathwayCount} 个`}
        bordered
        dataSource={detail.activePathways}
        locale={{ emptyText: "暂无在径路径" }}
        renderItem={(pathway) => (
          <List.Item>
            <List.Item.Meta
              title={
                evidenceDetailsEnabled ? (
                  <Text code>{pathway.patientPathwayId}</Text>
                ) : (
                  "当前在径路径"
                )
              }
              description={
                <Space direction="vertical" size={2}>
                  <Text type="secondary">路径状态：{customerEnumLabel(pathway.status)}</Text>
                  <Text type="secondary">
                    当前临床环节：{pathway.currentNodeCode ? "已记录" : "暂无"}
                  </Text>
                  {evidenceDetailsEnabled && (
                    <>
                      <Text type="secondary">临床路径版本编号：{pathway.templateId}</Text>
                      <Text type="secondary">当前节点：{pathway.currentNodeCode ?? "暂无"}</Text>
                      {pathway.traceId && <Text type="secondary">追踪号：{pathway.traceId}</Text>}
                    </>
                  )}
                </Space>
              }
            />
          </List.Item>
        )}
      />

      {evidenceDetailsEnabled && <Text type="secondary">追踪号：{detail.traceId}</Text>}
    </Space>
  );
}

export default function Mpi() {
  const { message: messageApi } = AntdApp.useApp();
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const canCreatePatient = hasPermission(security.data, "mpi.create");
  const canManageMpiIdentity = hasPermission(security.data, "mpi.write");
  const canCreateContextSnapshot = hasPermission(security.data, "context.write");
  const canOpenCdssFatigue =
    hasPermission(security.data, "cdss.read") || canOpenMenu(security.data, "cdss-fatigue");

  // 查询参数缓存，以便在点击查询时才触发真正的 API 过滤
  const [filterKeyword, setFilterKeyword] = useState("");
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined);
  const [mergePatientSearch, setMergePatientSearch] = useState("");

  // API 数据读取
  const {
    data: patientData,
    isLoading: listLoading,
    refetch: refetchList,
  } = useMpiPatients({
    keyword: filterKeyword || undefined,
    status: filterStatus || undefined,
    page,
    size,
  });
  const activeDirectoryQuery = useMpiPatients({
    keyword: mergePatientSearch || undefined,
    status: "ACTIVE",
    page: 1,
    size: 50,
  });

  const { data: stats, isLoading: statsLoading, refetch: refetchStats } = useMpiStats();

  // 合并数据突变
  const createMutation = useCreateMpiPatient();
  const createContextSnapshotMutation = useCreateContextSnapshot(security.data);
  const mergeMutation = useMergeMpiPatients();
  const splitMutation = useSplitMpiPatient();

  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);
  const [isContextModalVisible, setIsContextModalVisible] = useState(false);
  // 合并弹窗状态
  const [isMergeModalVisible, setIsMergeModalVisible] = useState(false);
  const [isSplitModalVisible, setIsSplitModalVisible] = useState(false);
  const [splitPatientRecord, setSplitPatientRecord] = useState<MpiPatient | null>(null);
  const [detailMpiId, setDetailMpiId] = useState<string | undefined>(undefined);
  const [createIdempotencyKey, setCreateIdempotencyKey] = useState("");
  const [contextSnapshotIdempotencyKey, setContextSnapshotIdempotencyKey] = useState("");
  const [contextSnapshotPatient, setContextSnapshotPatient] = useState<MpiPatient | null>(null);
  const [mergeIdempotencyKey, setMergeIdempotencyKey] = useState("");
  const [splitIdempotencyKey, setSplitIdempotencyKey] = useState("");
  const [createForm] = Form.useForm<MpiPatientCreatePayload>();
  const [contextSnapshotForm] = Form.useForm<ContextSnapshotFormValues>();
  const [mergeForm] = Form.useForm<{ sourceMpiId: string; targetMpiId: string }>();
  const [splitForm] = Form.useForm<{ reviewReason: string }>();
  const selectedSourceMpiId = Form.useWatch("sourceMpiId", mergeForm);
  const {
    data: patientDetail,
    isLoading: detailLoading,
    isError: detailIsError,
    error: detailError,
    refetch: refetchDetail,
  } = useMpiPatientDetail(detailMpiId);

  // 触发查询
  const handleSearch = () => {
    setFilterKeyword(keyword);
    setFilterStatus(status);
    setPage(1);
  };

  // 重置条件
  const handleReset = () => {
    setKeyword("");
    setStatus(undefined);
    setFilterKeyword("");
    setFilterStatus(undefined);
    setPage(1);
  };

  // 打开合并弹窗
  const showMergeModal = useCallback(
    (record?: MpiPatient) => {
      mergeForm.resetFields();
      setMergePatientSearch("");
      setMergeIdempotencyKey(`mpi-merge-${crypto.randomUUID()}`);
      if (record) {
        mergeForm.setFieldsValue({
          sourceMpiId: record.mpiId,
        });
      }
      setIsMergeModalVisible(true);
    },
    [mergeForm],
  );

  const showCreateModal = () => {
    createForm.resetFields();
    setCreateIdempotencyKey(`mpi-create-${crypto.randomUUID()}`);
    setIsCreateModalVisible(true);
  };

  const showSplitModal = useCallback(
    (record: MpiPatient) => {
      splitForm.resetFields();
      setSplitIdempotencyKey(`mpi-split-${crypto.randomUUID()}`);
      setSplitPatientRecord(record);
      setIsSplitModalVisible(true);
    },
    [splitForm],
  );

  const showDetailDrawer = useCallback((record: MpiPatient) => {
    setDetailMpiId(record.mpiId);
  }, []);

  const showContextSnapshotModal = useCallback(
    (patient: MpiPatient) => {
      contextSnapshotForm.resetFields();
      contextSnapshotForm.setFieldsValue(defaultContextSnapshotFormValues);
      setContextSnapshotPatient(patient);
      setContextSnapshotIdempotencyKey(`context-snapshot-${crypto.randomUUID()}`);
      setIsContextModalVisible(true);
    },
    [contextSnapshotForm],
  );

  const openReportInterpretation = useCallback(
    (detail: MpiPatientDetailResponse) => {
      const snapshot = getPatientDetailSnapshot(detail);
      if (!snapshot) {
        messageApi.warning("请先建立当前就诊上下文，再生成报告解读");
        return;
      }

      navigate("/cdss/fatigue", {
        state: {
          reportInterpretation: {
            snapshotId: snapshot.snapshotId,
            patientLabel: detail.patient.maskedName,
          },
        },
      });
    },
    [messageApi, navigate],
  );

  const handleCreateSubmit = async () => {
    try {
      const values = await createForm.validateFields();
      const result = await createMutation.mutateAsync({
        maskedName: values.maskedName.trim(),
        gender: values.gender,
        age: Number(values.age),
        idLast4: values.idLast4.trim(),
        idempotencyKey: createIdempotencyKey,
      });

      messageApi.success(
        evidenceDetailsEnabled
          ? `患者主索引 ${result.mpiId} 已创建，列表和统计已刷新`
          : "患者主索引已创建，列表和统计已刷新",
      );
      setIsCreateModalVisible(false);
      createForm.resetFields();
      refetchList();
      refetchStats();
    } catch (error: unknown) {
      if (applyApiFieldErrors(createForm, error)) return;
      messageApi.error(getApiErrorMessage(error, "创建失败，请检查患者主索引信息"));
    }
  };

  const handleCreateContextSnapshotSubmit = async () => {
    if (!contextSnapshotPatient) return;
    try {
      const values = await contextSnapshotForm.validateFields();
      const diseaseText = values.diseaseCode.trim();
      const currentMedicationText = values.currentMedicationText?.trim();
      const allergyIntoleranceText = values.allergyIntoleranceText?.trim();
      const diagnosticReportType = values.diagnosticReportType?.trim();
      const diagnosticReportConclusion = values.diagnosticReportConclusion?.trim();
      const diagnosticReportKeyFindingsText = values.diagnosticReportKeyFindingsText?.trim();
      const nursingAssessmentType = values.nursingAssessmentType?.trim();
      const nursingAssessmentStatus = values.nursingAssessmentStatus?.trim();
      const carePlanPathwayId = values.carePlanPathwayId?.trim();
      const carePlanCurrentNodeId = values.carePlanCurrentNodeId?.trim();
      const carePlanVarianceCode = values.carePlanVarianceCode?.trim();
      const hasDiagnosticReportInput = !!(
        diagnosticReportType ||
        diagnosticReportConclusion ||
        diagnosticReportKeyFindingsText
      );
      if (hasDiagnosticReportInput && (!diagnosticReportType || !diagnosticReportConclusion)) {
        messageApi.error("请同时填写医技报告项目和报告结论，或清空报告字段");
        return;
      }
      const hasNursingAssessmentInput = !!(nursingAssessmentType || values.nursingRiskLevel);
      if (hasNursingAssessmentInput && (!nursingAssessmentType || !values.nursingRiskLevel)) {
        messageApi.error("请同时填写护理评估类型和护理风险等级，或清空护理评估字段");
        return;
      }
      const hasCarePlanInput = !!(
        carePlanPathwayId ||
        carePlanCurrentNodeId ||
        carePlanVarianceCode ||
        values.carePlanPlannedFinishAt
      );
      if (hasCarePlanInput && (!carePlanPathwayId || !carePlanCurrentNodeId)) {
        messageApi.error("请同时填写护理计划路径和当前护理节点，或清空护理计划字段");
        return;
      }
      const insuranceClaimDrgCode = values.insuranceClaimDrgCode?.trim();
      const insuranceClaimTotalCost = values.insuranceClaimTotalCost ?? undefined;
      const insuranceClaimPaidAmount = values.insuranceClaimPaidAmount ?? undefined;
      const hasInsuranceClaimInput =
        !!insuranceClaimDrgCode ||
        insuranceClaimTotalCost !== undefined ||
        insuranceClaimPaidAmount !== undefined;
      if (
        hasInsuranceClaimInput &&
        (!insuranceClaimDrgCode ||
          insuranceClaimTotalCost === undefined ||
          insuranceClaimTotalCost <= 0)
      ) {
        messageApi.error("请同时填写 DRG/DIP 分组和本次结算金额，或清空医保结算字段");
        return;
      }
      if (
        insuranceClaimPaidAmount !== undefined &&
        insuranceClaimTotalCost !== undefined &&
        insuranceClaimPaidAmount > insuranceClaimTotalCost
      ) {
        messageApi.error("医保支付金额不能大于本次结算金额");
        return;
      }
      await createContextSnapshotMutation.mutateAsync({
        patient: contextSnapshotPatient,
        encounterType: values.encounterType,
        diseaseCode: diseaseText,
        diseaseName: diseaseText,
        riskLevel: values.riskLevel,
        ...(currentMedicationText ? { currentMedicationText } : {}),
        ...(allergyIntoleranceText ? { allergyIntoleranceText } : {}),
        ...(values.heightCm !== undefined && values.heightCm !== null
          ? { heightCm: values.heightCm }
          : {}),
        ...(values.weightKg !== undefined && values.weightKg !== null
          ? { weightKg: values.weightKg }
          : {}),
        ...(diagnosticReportType ? { diagnosticReportType } : {}),
        ...(diagnosticReportConclusion ? { diagnosticReportConclusion } : {}),
        ...(diagnosticReportKeyFindingsText ? { diagnosticReportKeyFindingsText } : {}),
        ...(nursingAssessmentType && values.nursingRiskLevel
          ? {
              nursingAssessmentType,
              nursingRiskLevel: values.nursingRiskLevel,
              ...(nursingAssessmentStatus ? { nursingAssessmentStatus } : {}),
            }
          : {}),
        ...(carePlanPathwayId && carePlanCurrentNodeId
          ? {
              carePlanPathwayId,
              carePlanCurrentNodeId,
              ...(carePlanVarianceCode ? { carePlanVarianceCode } : {}),
              ...(values.carePlanPlannedFinishAt
                ? { carePlanPlannedFinishAt: values.carePlanPlannedFinishAt }
                : {}),
            }
          : {}),
        ...(insuranceClaimDrgCode && insuranceClaimTotalCost !== undefined
          ? {
              insuranceClaimDrgCode,
              insuranceClaimTotalCost,
              ...(insuranceClaimPaidAmount !== undefined ? { insuranceClaimPaidAmount } : {}),
            }
          : {}),
        reason: values.reason.trim(),
        idempotencyKey: contextSnapshotIdempotencyKey,
      });

      messageApi.success("当前就诊上下文已建立，可用于随访、路径和 CDSS");
      setIsContextModalVisible(false);
      setContextSnapshotPatient(null);
      contextSnapshotForm.resetFields();
      refetchDetail();
      refetchList();
    } catch (error: unknown) {
      if (applyApiFieldErrors(contextSnapshotForm, error)) return;
      messageApi.error(getApiErrorMessage(error, "建立上下文失败，请核查患者 360 与机构生效版本"));
    }
  };

  const handleSplitSubmit = async () => {
    if (!splitPatientRecord) return;
    try {
      const values = await splitForm.validateFields();
      const result = await splitMutation.mutateAsync({
        sourceMpiId: splitPatientRecord.mpiId,
        reviewReason: values.reviewReason.trim(),
        idempotencyKey: splitIdempotencyKey,
      });

      messageApi.success(result?.message || "患者主索引合并关系已拆分");
      setIsSplitModalVisible(false);
      setSplitPatientRecord(null);
      splitForm.resetFields();
      refetchList();
      refetchStats();
    } catch (error: unknown) {
      if (applyApiFieldErrors(splitForm, error)) return;
      messageApi.error(getApiErrorMessage(error, "拆分失败，请检查主索引状态"));
    }
  };

  // 执行患者合并
  const handleMergeSubmit = async () => {
    try {
      const values = await mergeForm.validateFields();
      if (values.sourceMpiId === values.targetMpiId) {
        messageApi.error("源患者与目标患者不能是同一个患者，无法合并！");
        return;
      }

      const result = await mergeMutation.mutateAsync({
        sourceMpiId: values.sourceMpiId,
        targetMpiId: values.targetMpiId,
        idempotencyKey: mergeIdempotencyKey,
      });

      messageApi.success(result?.message || "患者主索引合并成功，已记录审计证据");
      setIsMergeModalVisible(false);
      mergeForm.resetFields();

      // 刷新数据
      refetchList();
      refetchStats();
    } catch (error: unknown) {
      if (applyApiFieldErrors(mergeForm, error)) return;
      const parsed = parseApiError(error, "合并失败，请核查患者身份信息");
      if (parsed.code === "MPI_MERGE_REQUIRES_REVIEW") {
        messageApi.warning(parsed.message);
        return;
      }
      messageApi.error(getApiErrorMessage(error, "合并失败，请核查患者身份信息"));
    }
  };

  const activePatients = useMemo(
    () => (activeDirectoryQuery.data?.items ?? []).filter((patient) => patient.status === "ACTIVE"),
    [activeDirectoryQuery.data?.items],
  );
  const patientOptions = useMemo(
    () =>
      activePatients.map((patient) => ({
        value: patient.mpiId,
        label: evidenceDetailsEnabled
          ? `${patient.maskedName} · ${patient.mpiId} · ***${patient.idLast4}`
          : `${patient.maskedName} · ${genderLabel(patient.gender)} · ${patient.age} 岁 · ***${patient.idLast4}`,
      })),
    [activePatients, evidenceDetailsEnabled],
  );
  const targetPatientOptions = useMemo(
    () => patientOptions.filter((option) => option.value !== selectedSourceMpiId),
    [patientOptions, selectedSourceMpiId],
  );

  // 定义表格列
  const columns = useMemo(
    () => [
      ...(evidenceDetailsEnabled
        ? [
            {
              title: "患者主索引编号",
              dataIndex: "mpiId",
              key: "mpiId",
              render: (mpiId: string) => <span className={styles.mpiBadge}>{mpiId}</span>,
            },
          ]
        : []),
      {
        title: "脱敏姓名",
        dataIndex: "maskedName",
        key: "maskedName",
        render: (name: string) => <Text strong>{name}</Text>,
      },
      {
        title: "性别",
        dataIndex: "gender",
        key: "gender",
        render: (gender: string) => {
          if (gender === "M") {
            return <Tag color="blue">男</Tag>;
          } else if (gender === "F") {
            return <Tag color="pink">女</Tag>;
          } else {
            return <Tag color="default">未知</Tag>;
          }
        },
      },
      {
        title: "年龄",
        dataIndex: "age",
        key: "age",
        render: (age: number) => <span>{age} 岁</span>,
      },
      {
        title: "身份证后4位",
        dataIndex: "idLast4",
        key: "idLast4",
        render: (last4: string) => <Text type="secondary">*** {last4}</Text>,
      },
      {
        title: "已并入数",
        dataIndex: "mergedCount",
        key: "mergedCount",
        render: (count: number) => {
          if (count > 0) {
            return (
              <Tooltip title={`该主索引已合并 ${count} 个历史主索引记录`}>
                <Badge count={`+${count}`} className={styles.badgeSuccess} />
              </Tooltip>
            );
          }
          return <Text type="secondary">0</Text>;
        },
      },
      {
        title: "主索引状态",
        dataIndex: "status",
        key: "status",
        render: (currStatus: string) => {
          if (currStatus === "ACTIVE") {
            return <Tag color="success">当前有效</Tag>;
          }
          return <Tag color="default">已合并</Tag>;
        },
      },
      ...(evidenceDetailsEnabled
        ? [
            {
              title: "合并指向编号",
              dataIndex: "mergedIntoMpiId",
              key: "mergedIntoMpiId",
              render: (targetId: string | null) => {
                if (targetId) {
                  return (
                    <Tooltip title={`已合并至目标患者主索引：${targetId}`}>
                      <Tag color="orange" icon={<MergeCellsOutlined />}>
                        {targetId}
                      </Tag>
                    </Tooltip>
                  );
                }
                return <Text type="secondary">-</Text>;
              },
            },
          ]
        : []),
      {
        title: "操作",
        key: "action",
        render: (_: unknown, record: MpiPatient) => (
          <Space size="middle">
            <Button size="small" icon={<UserOutlined />} onClick={() => showDetailDrawer(record)}>
              患者360
            </Button>
            {canManageMpiIdentity && record.status === "ACTIVE" ? (
              <Button
                size="small"
                icon={<MergeCellsOutlined />}
                onClick={() => showMergeModal(record)}
              >
                合并患者
              </Button>
            ) : null}
            {canManageMpiIdentity && record.status !== "ACTIVE" ? (
              <Tooltip title="需要人工核查理由，拆分后源主索引恢复为活跃">
                <Button
                  size="small"
                  icon={<BranchesOutlined />}
                  onClick={() => showSplitModal(record)}
                >
                  拆分归并
                </Button>
              </Tooltip>
            ) : null}
          </Space>
        ),
      },
    ],
    [
      canManageMpiIdentity,
      evidenceDetailsEnabled,
      showDetailDrawer,
      showMergeModal,
      showSplitModal,
    ],
  );

  let detailDrawerContent = <Alert message="暂无患者 360 详情" type="info" showIcon />;
  if (detailLoading) {
    detailDrawerContent = <Alert message="正在读取患者 360 详情" type="info" showIcon />;
  } else if (detailIsError) {
    detailDrawerContent = (
      <Alert
        message="患者 360 详情暂时不可用"
        description={getApiErrorMessage(
          detailError,
          "请稍后重试；若持续失败，请联系信息科核查患者主索引服务。失败已留痕，可在审计证据中追溯。",
        )}
        type="error"
        showIcon
        action={
          <Button size="small" onClick={() => refetchDetail()}>
            重试
          </Button>
        }
      />
    );
  } else if (patientDetail) {
    const patient = patientDetail.patient;
    const hasSnapshot = !!getPatientDetailSnapshot(patientDetail);
    let contextAction: ReactNode = null;
    if (canCreateContextSnapshot && patient.status === "ACTIVE" && !hasSnapshot) {
      contextAction = (
        <Alert
          message="暂无已生效上下文"
          description="建立当前就诊上下文后，随访、路径和 CDSS 将统一使用该患者的标准临床快照。"
          type="info"
          showIcon
          action={
            <Button
              aria-label="建立当前就诊上下文"
              icon={<FileDoneOutlined />}
              onClick={() => showContextSnapshotModal(patient)}
            >
              建立当前就诊上下文
            </Button>
          }
        />
      );
    } else if (
      patient.status === "ACTIVE" &&
      hasSnapshot &&
      (canCreateContextSnapshot || canOpenCdssFatigue)
    ) {
      contextAction = (
        <Alert
          message="已关联当前就诊上下文"
          description="可直接带入该患者当前临床快照生成医技报告解读；报告解读仅辅助阅读，不改写已签发报告。"
          type="success"
          showIcon
          action={
            <Space wrap>
              {canCreateContextSnapshot && (
                <Button
                  aria-label="更新当前就诊上下文"
                  icon={<FileDoneOutlined />}
                  onClick={() => showContextSnapshotModal(patient)}
                >
                  更新当前就诊上下文
                </Button>
              )}
              {canOpenCdssFatigue && (
                <Button
                  aria-label="生成报告解读"
                  icon={<ReadOutlined />}
                  onClick={() => openReportInterpretation(patientDetail)}
                >
                  生成报告解读
                </Button>
              )}
            </Space>
          }
        />
      );
    }
    detailDrawerContent = renderPatient360Detail(
      patientDetail,
      evidenceDetailsEnabled,
      contextAction,
    );
  }

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
      primary={
        canCreatePatient ? (
          <Button type="primary" icon={<UserAddOutlined />} onClick={showCreateModal}>
            新增患者
          </Button>
        ) : undefined
      }
    >
      <div className={styles.container}>
        {/* 驾驶舱统计指标 */}
        <div className={styles.statsRow}>
          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>活跃患者主索引</span>
              <TeamOutlined className={styles.statIcon} />
            </div>
            <div className={styles.statValue}>
              {statsLoading ? "..." : (stats?.activeCount ?? 0)}
            </div>
            <div className={styles.statSubtext}>
              当前组织范围内仍作为主记录使用的患者数；活跃路径实例{" "}
              {statsLoading ? "..." : (stats?.activePathwayCount ?? 0)} 个
            </div>
          </div>

          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>已合并患者主索引</span>
              <MergeCellsOutlined className={`${styles.statIcon} ${styles.statIconSuccess}`} />
            </div>
            <div className={styles.statValue}>
              {statsLoading ? "..." : (stats?.mergedCount ?? 0)}
            </div>
            <div className={styles.statSubtext}>因确认身份重合而归档的源主索引数</div>
          </div>

          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>活跃患者平均年龄</span>
              <CalendarOutlined className={`${styles.statIcon} ${styles.statIconWarning}`} />
            </div>
            <div className={styles.statValue}>
              {statsLoading ? "..." : `${(stats?.averageAge ?? 0).toFixed(1)}`}
              <span className={styles.ageUnit}>岁</span>
            </div>
            <div className={styles.statSubtext}>基于活跃 MPI 数据计算的群体平均岁数</div>
          </div>

          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>群体性别分布</span>
              <UserOutlined className={`${styles.statIcon} ${styles.statIconInfo}`} />
            </div>
            <div className={`${styles.statValue} ${styles.genderValue}`}>
              {statsLoading
                ? "..."
                : `男性 ${stats?.genderCounts?.M ?? 0} 人 / 女性 ${stats?.genderCounts?.F ?? 0} 人`}
            </div>
            <div className={styles.statSubtext}>
              性别待确认/其他 {statsLoading ? "..." : (stats?.genderCounts?.UNKNOWN ?? 0)} 人
            </div>
          </div>
        </div>

        {/* 检索过滤面板 */}
        <div className={styles.filterCard}>
          <div className={styles.filterForm}>
            <div className={styles.filterItem}>
              <span className={styles.filterLabel}>患者检索:</span>
              <Input
                placeholder="支持按姓名或院内患者编号检索..."
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                className={styles.searchInput}
                prefix={<SearchOutlined />}
                onPressEnter={handleSearch}
              />
            </div>

            <div className={styles.filterItem}>
              <span className={styles.filterLabel}>索引状态:</span>
              <Select
                placeholder="全部状态"
                aria-label="索引状态"
                value={status}
                onChange={(value) => setStatus(value)}
                className={styles.statusSelect}
                allowClear
              >
                <Option value="ACTIVE">当前有效</Option>
                <Option value="MERGED_INTO">已合并</Option>
              </Select>
            </div>

            <Space>
              <Button icon={<SearchOutlined />} onClick={handleSearch}>
                检索过滤
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
              {canManageMpiIdentity ? (
                <Button
                  type="dashed"
                  icon={<MergeCellsOutlined />}
                  onClick={() => showMergeModal()}
                >
                  快速合并
                </Button>
              ) : null}
            </Space>
          </div>
        </div>

        {/* 列表表格面板 */}
        <div className={styles.tableCard}>
          <Table
            dataSource={patientData?.items ?? []}
            columns={columns}
            rowKey="id"
            loading={listLoading}
            pagination={{
              current: page,
              pageSize: size,
              total: patientData?.total ?? 0,
              showSizeChanger: true,
              pageSizeOptions: ["10", "20", "50", "100"],
              onChange: (p, s) => {
                setPage(p);
                setSize(s);
              },
            }}
          />
        </div>

        <Drawer
          title="患者 360 视图"
          open={!!detailMpiId}
          onClose={() => setDetailMpiId(undefined)}
          width={720}
          destroyOnClose
        >
          {detailDrawerContent}
        </Drawer>

        <Modal
          title={
            <Space>
              <UserAddOutlined />
              <span>新增患者主索引</span>
            </Space>
          }
          open={isCreateModalVisible}
          onOk={handleCreateSubmit}
          onCancel={() => setIsCreateModalVisible(false)}
          confirmLoading={createMutation.isPending}
          okText="保存患者"
          cancelText="取消返回"
          width={560}
          forceRender
          destroyOnClose
        >
          <Form form={createForm} layout="vertical">
            <Form.Item
              name="maskedName"
              label="脱敏姓名"
              rules={[{ required: true, message: "请输入脱敏姓名" }]}
            >
              <Input placeholder="例如：李*四" />
            </Form.Item>
            <Form.Item
              name="gender"
              label="性别"
              rules={[{ required: true, message: "请选择性别" }]}
            >
              <Select placeholder="请选择性别" aria-label="性别">
                <Option value="M">男</Option>
                <Option value="F">女</Option>
                <Option value="UNKNOWN">未知</Option>
              </Select>
            </Form.Item>
            <Form.Item name="age" label="年龄" rules={[{ required: true, message: "请输入年龄" }]}>
              <InputNumber
                min={0}
                precision={0}
                placeholder="例如：36"
                className={styles.fullWidth}
              />
            </Form.Item>
            <Form.Item
              name="idLast4"
              label="身份证后四位"
              rules={[
                { required: true, message: "请输入身份证后四位" },
                { pattern: /^\d{4}$/, message: "身份证后四位必须为 4 位数字" },
              ]}
            >
              <Input placeholder="例如：9876" />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={
            <Space>
              <BranchesOutlined className={styles.warningIcon} />
              <span>拆分患者主索引归并关系</span>
            </Space>
          }
          open={isSplitModalVisible}
          onOk={handleSplitSubmit}
          onCancel={() => {
            setIsSplitModalVisible(false);
            setSplitPatientRecord(null);
          }}
          confirmLoading={splitMutation.isPending}
          okText="确认拆分"
          cancelText="取消返回"
          width={560}
          forceRender
          destroyOnClose
        >
          <div className={styles.warningBox}>
            <div className={styles.warningTitle}>
              <WarningOutlined />
              <span>拆分前必须完成人工核查</span>
            </div>
            <div className={styles.warningText}>
              源患者 {splitPatientRecord?.maskedName ?? "-"}{" "}
              将恢复为活跃状态，目标主索引的合并计数会同步扣减。
              {evidenceDetailsEnabled && (
                <>
                  <br />
                  源主索引 {splitPatientRecord?.mpiId ?? "-"}；目标主索引{" "}
                  {splitPatientRecord?.mergedIntoMpiId ?? "-"}。
                </>
              )}
            </div>
          </div>
          <Form form={splitForm} layout="vertical">
            <Form.Item
              name="reviewReason"
              label={<Text strong>人工核查结论</Text>}
              rules={[{ required: true, message: "请输入人工核查结论" }]}
            >
              <Input.TextArea placeholder="请输入人工核查结论" rows={4} />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={
            <Space>
              <FileDoneOutlined />
              <span>建立当前就诊上下文</span>
            </Space>
          }
          open={isContextModalVisible}
          onOk={handleCreateContextSnapshotSubmit}
          onCancel={() => {
            setIsContextModalVisible(false);
            setContextSnapshotPatient(null);
          }}
          confirmLoading={createContextSnapshotMutation.isPending}
          okText="生成上下文快照"
          cancelText="取消返回"
          width={560}
          zIndex={1300}
          forceRender
          destroyOnClose
        >
          <Alert
            message="仅写入脱敏患者标识、当前就诊、诊断、风险分层、必要用药、护理、已签发报告与医保结算事实"
            description="本操作用于生成随访、路径、CDSS、药师复核、护理连续照护和报告解读共用的标准上下文，不会自动开嘱，也不会写入患者姓名、证件号、电话或住址。"
            type="info"
            showIcon
            className={styles.modalFormItem}
          />
          <Form form={contextSnapshotForm} layout="vertical">
            <Form.Item
              name="encounterType"
              label="就诊类型"
              rules={[{ required: true, message: "请选择就诊类型" }]}
            >
              <Select aria-label="就诊类型" options={frontdeskEncounterTypeOptions} />
            </Form.Item>
            <Form.Item
              name="diseaseCode"
              label="诊断/随访病种"
              rules={[{ required: true, whitespace: true, message: "请输入诊断或随访病种" }]}
            >
              <Input aria-label="诊断/随访病种" placeholder="输入当前诊断、病种或随访主题" />
            </Form.Item>
            <Form.Item
              name="riskLevel"
              label="风险分层"
              rules={[{ required: true, message: "请选择风险分层" }]}
            >
              <Select aria-label="风险分层" options={contextRiskLevelOptions} />
            </Form.Item>
            <Form.Item name="currentMedicationText" label="当前用药">
              <Input.TextArea
                aria-label="当前用药"
                placeholder="可选，填写当前用药名称，多个项目用顿号或逗号分隔"
                rows={2}
              />
            </Form.Item>
            <Form.Item name="allergyIntoleranceText" label="过敏/不良反应">
              <Input.TextArea
                aria-label="过敏/不良反应"
                placeholder="可选，填写药物或物质及反应，例如：青霉素：皮疹；头孢菌素：呼吸困难"
                rows={2}
              />
            </Form.Item>
            <Text strong>护理高风险评估（可选）</Text>
            <Form.Item name="nursingAssessmentType" label="护理评估类型">
              <Input
                aria-label="护理评估类型"
                placeholder="可选，例如 跌倒风险评估、压疮风险评估"
              />
            </Form.Item>
            <Form.Item name="nursingRiskLevel" label="护理风险等级">
              <Select
                aria-label="护理风险等级"
                allowClear
                placeholder="可选，选择护理评估风险等级"
                options={contextRiskLevelOptions}
              />
            </Form.Item>
            <Form.Item name="nursingAssessmentStatus" label="护理评估状态">
              <Input
                aria-label="护理评估状态"
                placeholder="可选，默认 CONFIRMED，表示已由有资质人员确认"
              />
            </Form.Item>
            <Text strong>护理计划事实（可选）</Text>
            <Form.Item name="carePlanPathwayId" label="护理计划路径">
              <Input
                aria-label="护理计划路径"
                placeholder="可选，填写护理/康复/宣教路径或计划身份"
              />
            </Form.Item>
            <Form.Item name="carePlanCurrentNodeId" label="当前护理节点">
              <Input
                aria-label="当前护理节点"
                placeholder="可选，填写当前护理计划节点，例如 REHAB_EDUCATION"
              />
            </Form.Item>
            <Form.Item name="carePlanVarianceCode" label="护理计划变异">
              <Input
                aria-label="护理计划变异"
                placeholder="可选，填写变异编码或人工复评说明"
              />
            </Form.Item>
            <Form.Item name="carePlanPlannedFinishAt" label="计划完成时间">
              <Input
                aria-label="计划完成时间"
                placeholder="可选，ISO 时间，例如 2026-07-20T08:00:00.000Z"
              />
            </Form.Item>
            <Space wrap className="mk-full-width">
              <Form.Item name="heightCm" label="身高 cm">
                <InputNumber
                  aria-label="身高 cm"
                  min={30}
                  max={260}
                  precision={1}
                  placeholder="可选"
                />
              </Form.Item>
              <Form.Item name="weightKg" label="体重 kg">
                <InputNumber
                  aria-label="体重 kg"
                  min={1}
                  max={400}
                  precision={1}
                  placeholder="可选"
                />
              </Form.Item>
            </Space>
            <Text strong>已签发医技报告（可选）</Text>
            <Form.Item name="diagnosticReportType" label="医技报告项目">
              <Input aria-label="医技报告项目" placeholder="可选，填写已签发医技报告项目名称" />
            </Form.Item>
            <Form.Item name="diagnosticReportConclusion" label="报告结论">
              <Input.TextArea
                aria-label="报告结论"
                placeholder="可选，概括已签发报告结论、复核状态与需关注事项"
                rows={2}
              />
            </Form.Item>
            <Form.Item name="diagnosticReportKeyFindingsText" label="异常重点">
              <Input.TextArea
                aria-label="异常重点"
                placeholder="可选，多个重点用顿号或逗号分隔，避免录入患者核心标识明文"
                rows={2}
              />
            </Form.Item>
            <Text strong>医保结算事实（可选）</Text>
            <Form.Item name="insuranceClaimDrgCode" label="DRG/DIP 分组">
              <Input aria-label="DRG/DIP 分组" placeholder="可选，填写本次结算分组编码" />
            </Form.Item>
            <Form.Item name="insuranceClaimTotalCost" label="本次结算金额">
              <InputNumber
                aria-label="本次结算金额"
                min={0}
                precision={2}
                className={styles.fullWidth}
                placeholder="可选，填写本次医保审核需要核验的结算金额"
              />
            </Form.Item>
            <Form.Item name="insuranceClaimPaidAmount" label="医保支付金额">
              <InputNumber
                aria-label="医保支付金额"
                min={0}
                precision={2}
                className={styles.fullWidth}
                placeholder="可选，填写医保支付金额"
              />
            </Form.Item>
            <Form.Item
              name="reason"
              label="建立原因"
              rules={[{ required: true, whitespace: true, message: "请输入建立原因" }]}
            >
              <Input.TextArea
                aria-label="建立原因"
                placeholder="说明本次建立上下文的业务原因"
                rows={4}
              />
            </Form.Item>
          </Form>
        </Modal>

        {/* 合并弹窗 */}
        <Modal
          title={
            <Space>
              <MergeCellsOutlined className={styles.warningIcon} />
              <span>合并重复患者主索引</span>
            </Space>
          }
          open={isMergeModalVisible}
          onOk={handleMergeSubmit}
          onCancel={() => setIsMergeModalVisible(false)}
          confirmLoading={mergeMutation.isPending}
          okText="确认合并"
          cancelText="取消返回"
          width={560}
          forceRender
          destroyOnClose
        >
          {/* 安全警告说明 */}
          <div className={styles.warningBox}>
            <div className={styles.warningTitle}>
              <WarningOutlined />
              <span>高风险医疗合规安全警示</span>
            </div>
            <div className={styles.warningText}>
              合并患者主索引是<strong>高风险</strong>操作。合并后：
              <br />
              1. <strong>源患者（被合并人）</strong>
              将归档为已合并，合并后以目标患者主索引为准。
              <br />
              2. 如人工核查发现合并错误，必须通过拆分归并流程留痕恢复。
              <br />
              3. 身份证后四位、性别或年龄存在风险差异时，系统会生成审核单，不会自动合并。
            </div>
          </div>

          {activeDirectoryQuery.isError && (
            <Alert
              message="活跃患者目录暂时不可用"
              description={getApiErrorMessage(
                activeDirectoryQuery.error,
                "无法读取当前组织范围的活跃患者，请重试后再执行合并。",
              )}
              type="error"
              showIcon
              action={
                <Button size="small" onClick={() => activeDirectoryQuery.refetch()}>
                  重试
                </Button>
              }
            />
          )}

          <Form form={mergeForm} layout="vertical">
            <Form.Item
              name="sourceMpiId"
              label={<Text strong>源患者（合并后归档）</Text>}
              rules={[{ required: true, message: "请选择源患者" }]}
              className={styles.modalFormItem}
            >
              <Select
                aria-label="源患者"
                placeholder="选择需要归档的活跃患者"
                options={patientOptions}
                loading={activeDirectoryQuery.isLoading}
                showSearch
                filterOption={false}
                onSearch={setMergePatientSearch}
              />
            </Form.Item>

            <Form.Item
              name="targetMpiId"
              label={<Text strong>目标患者（最终保留）</Text>}
              rules={[{ required: true, message: "请选择目标患者" }]}
              className={styles.modalFormItem}
            >
              <Select
                aria-label="目标患者"
                placeholder="选择最终保留的活跃患者"
                options={targetPatientOptions}
                loading={activeDirectoryQuery.isLoading}
                showSearch
                filterOption={false}
                onSearch={setMergePatientSearch}
              />
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </PageExperienceShell>
  );
}
