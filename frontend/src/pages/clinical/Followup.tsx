import { useMemo, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  message,
} from "antd";
import type { BadgeProps, TableProps } from "antd";
import {
  AlertOutlined,
  CheckCircleOutlined,
  CompassOutlined,
  FileTextOutlined,
  PlusOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useFollowupPlans,
  useFollowupStats,
  useGenerateFollowupPlan,
  useContextSnapshotDetail,
  useContextSnapshots,
  useCreateFollowupTemplate,
  useFollowupTemplates,
  usePublishFollowupTemplate,
  useSubmitFollowupQuestionnaire,
  useReportFollowupAbnormal,
} from "@/shared/api/hooks";
import type {
  FollowupAbnormalReportResponse,
  FollowupPlanDetailResponse,
  FollowupPlanStatus,
  FollowupTemplateResponse,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { ContextSnapshotSelector } from "@/shared/ui/ContextSnapshotSelector";
import { customerDisplayText, customerEnumLabel } from "@/shared/config/customerLabels";
import {
  defaultFollowupTemplateFormValues,
  followupDiseaseOptions,
  followupQuestionOptions,
  followupSourceOptions,
  organizationScopeOptions,
  questionnaireTemplateOptions,
} from "@/shared/config/followupTemplateCatalog";

import styles from "./Clinical.module.css";

const { TextArea } = Input;
const FOLLOWUP_PLAN_PAGE_SIZE = 20;
const FOLLOWUP_TEMPLATE_PAGE_SIZE = 20;

const planStatusConfig: Record<FollowupPlanStatus, { status: BadgeProps["status"]; text: string }> =
  {
    DRAFT: { status: "default", text: "待完善" },
    ACTIVE: { status: "processing", text: "执行中" },
    COMPLETED: { status: "success", text: "已结案" },
    CANCELLED: { status: "error", text: "已取消" },
  };

const templateStatusConfig: Record<string, { color: string; text: string }> = {
  PUBLISHED: { color: "green", text: "已发布" },
  DRAFT: { color: "gold", text: "待发布" },
};

const taskTypeOptions = [
  { value: "QUESTIONNAIRE", label: "问卷回收" },
  { value: "EXAM", label: "检查复查" },
  { value: "LAB", label: "检验报告跟踪" },
  { value: "OUTPATIENT", label: "门诊复诊" },
];

export default function Followup() {
  const [activeTab, setActiveTab] = useState("plans");
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [generateModalVisible, setGenerateModalVisible] = useState(false);
  const [templateModalVisible, setTemplateModalVisible] = useState(false);
  const [patientFilter, setPatientFilter] = useState("");
  const [planPage, setPlanPage] = useState(1);
  const [templatePage, setTemplatePage] = useState(1);
  const [templateSearch, setTemplateSearch] = useState("");
  const [publishedTemplateSearch, setPublishedTemplateSearch] = useState("");
  const [snapshotPatientId, setSnapshotPatientId] = useState("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState("");
  const [selectedSnapshotId, setSelectedSnapshotId] = useState("");
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [abnormalEvidence, setAbnormalEvidence] = useState<FollowupAbnormalReportResponse | null>(
    null,
  );

  const [generateForm] = Form.useForm();
  const [templateForm] = Form.useForm();
  const [questionnaireForm] = Form.useForm();
  const [abnormalForm] = Form.useForm();

  const {
    data: apiPlansData,
    refetch: refetchPlans,
    isLoading,
    isError,
  } = useFollowupPlans({
    patientId: patientFilter.trim() || undefined,
    page: planPage,
    size: FOLLOWUP_PLAN_PAGE_SIZE,
  });
  const {
    data: statsData,
    refetch: refetchStats,
    isLoading: statsLoading,
    isError: statsError,
  } = useFollowupStats({
    patientId: patientFilter.trim() || undefined,
  });

  const generatePlanMutation = useGenerateFollowupPlan();
  const createTemplateMutation = useCreateFollowupTemplate();
  const publishTemplateMutation = usePublishFollowupTemplate();
  const submitQuestionnaireMutation = useSubmitFollowupQuestionnaire();
  const reportAbnormalMutation = useReportFollowupAbnormal();
  const templateKeyword = templateSearch.trim();
  const publishedTemplateKeyword = publishedTemplateSearch.trim();
  const templatesQuery = useFollowupTemplates({
    ...(templateKeyword ? { keyword: templateKeyword } : {}),
    page: templatePage,
    size: FOLLOWUP_TEMPLATE_PAGE_SIZE,
    sort: "updatedAt,desc",
  });
  const publishedTemplatesQuery = useFollowupTemplates({
    assetStatus: "PUBLISHED",
    ...(publishedTemplateKeyword ? { keyword: publishedTemplateKeyword } : {}),
    page: 1,
    size: FOLLOWUP_TEMPLATE_PAGE_SIZE,
    sort: "updatedAt,desc",
  });
  const hasSnapshotFilter = Boolean(snapshotPatientId.trim() || snapshotEncounterId.trim());
  const snapshotsQuery = useContextSnapshots(
    {
      patientId: snapshotPatientId.trim() || undefined,
      encounterId: snapshotEncounterId.trim() || undefined,
      status: "ACTIVE",
      page: 1,
      size: 20,
      sort: "createdAt,desc",
    },
    { enabled: generateModalVisible && hasSnapshotFilter },
  );
  const snapshotDetailQuery = useContextSnapshotDetail(selectedSnapshotId, {
    enabled: generateModalVisible && Boolean(selectedSnapshotId),
  });

  const displayPlans = useMemo(() => apiPlansData?.items ?? [], [apiPlansData?.items]);
  const templates = useMemo(() => templatesQuery.data?.items ?? [], [templatesQuery.data?.items]);
  const publishedTemplates = useMemo(
    () =>
      (publishedTemplatesQuery.data?.items ?? []).filter(
        (template) => template.assetStatus === "PUBLISHED",
      ),
    [publishedTemplatesQuery.data?.items],
  );
  const templateOptions = useMemo(
    () =>
      publishedTemplates.map((template) => ({
        value: template.templateId,
        label: `${template.name} · v${template.versionNo}`,
      })),
    [publishedTemplates],
  );
  const selectedPlanDetail = displayPlans.find((plan) => plan.planId === selectedPlanId);
  const selectedTask =
    selectedPlanDetail?.tasks.find((task) => task.taskId === selectedTaskId) ?? null;

  const stats = statsData ?? {
    totalPlans: 0,
    activePlans: 0,
    totalTasks: 0,
    completedTasks: 0,
    abnormalReturnTasks: 0,
    taskCompletionRatePercent: 0,
    abnormalReturnRatePercent: 0,
    traceId: "",
  };

  const refreshFollowupData = async () => {
    await Promise.all([refetchPlans(), refetchStats()]);
  };

  const handleGeneratePlan = async () => {
    try {
      const values = await generateForm.validateFields();
      const response = await generatePlanMutation.mutateAsync({
        contextSnapshotId: selectedSnapshotId,
        templateId: values.templateId,
        riskLevel: values.riskLevel,
        taskTypes: values.taskTypes,
        idempotencyKey: buildPlanIdempotencyKey(
          selectedSnapshotId,
          values.templateId,
          values.riskLevel,
          values.taskTypes,
        ),
      });

      message.success(`随访计划已生成：${response.planId}`);
      setGenerateModalVisible(false);
      generateForm.resetFields();
      setSnapshotPatientId("");
      setSnapshotEncounterId("");
      setSelectedSnapshotId("");
      setSelectedPlanId(response.planId);
      await refreshFollowupData();
    } catch (error: unknown) {
      if (applyApiFieldErrors(generateForm, error)) return;
      message.error(getApiErrorMessage(error, "随访计划生成失败"));
    }
  };

  const handleCreateTemplate = async () => {
    try {
      const values = await templateForm.validateFields();
      await createTemplateMutation.mutateAsync({
        templateCode: values.templateCode,
        name: values.name,
        description: values.description,
        organizationScope: values.organizationScope,
        applicableScope: values.applicableScope,
        tasks: [
          {
            taskType: "QUESTIONNAIRE",
            delayDays: Number(values.questionnaireDelayDays),
            questionnaireTemplateId: values.questionnaireTemplateId,
          },
          {
            taskType: "OUTPATIENT",
            delayDays: Number(values.outpatientDelayDays),
          },
        ],
        questionnaireDefinition: JSON.stringify({
          questions: [
            {
              code: values.questionCode,
              type: values.questionType,
              required: true,
            },
          ],
        }),
        abnormalActionDefinition: JSON.stringify({
          condition: values.abnormalCondition,
          notifyTarget: values.notifyTarget,
        }),
        sourceRef: values.sourceRef,
      });

      message.success("随访模板已创建，请发布后用于计划生成");
      setTemplateModalVisible(false);
      templateForm.resetFields();
      setTemplatePage(1);
      await templatesQuery.refetch();
    } catch (error: unknown) {
      if (applyApiFieldErrors(templateForm, error)) return;
      message.error(getApiErrorMessage(error, "随访模板创建失败"));
    }
  };

  const handlePublishTemplate = async (template: FollowupTemplateResponse) => {
    try {
      await publishTemplateMutation.mutateAsync({
        templateId: template.templateId,
        request: {
          impactDigest:
            template.contentHash ||
            `followup-template-${template.templateId}-v${template.versionNo}`,
          reason: "随访模板发布",
        },
      });
      message.success("随访模板已发布，可用于新随访计划");
      await Promise.all([templatesQuery.refetch(), publishedTemplatesQuery.refetch()]);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "随访模板发布失败"));
    }
  };

  const handleSubmitQuestionnaire = async () => {
    if (!selectedTask?.questionnaireTemplateId) {
      message.error("当前任务没有可用问卷模板，不能提交问卷。");
      return;
    }
    try {
      const values = await questionnaireForm.validateFields();
      const answerData = JSON.stringify({
        content: values.content,
        submittedAt: new Date().toISOString(),
      });
      await submitQuestionnaireMutation.mutateAsync({
        taskId: selectedTask.taskId,
        questionnaireTemplateId: selectedTask.questionnaireTemplateId,
        formData: JSON.stringify({
          templateId: selectedTask.questionnaireTemplateId,
          taskId: selectedTask.taskId,
        }),
        answerData,
        idempotencyKey: `questionnaire-${selectedTask.taskId}-${crypto.randomUUID()}`,
        executorType: values.executorType,
      });

      message.success("随访问卷内容已提交，请以刷新后的任务状态为准");
      questionnaireForm.resetFields();
      setSelectedTaskId(null);
      await refreshFollowupData();
    } catch (error: unknown) {
      if (applyApiFieldErrors(questionnaireForm, error)) return;
      message.error(getApiErrorMessage(error, "问卷提交失败"));
    }
  };

  const handleReportAbnormal = async () => {
    if (!selectedPlanId) return;
    try {
      const values = await abnormalForm.validateFields();
      const response = await reportAbnormalMutation.mutateAsync({
        planId: selectedPlanId,
        eventType: "ABNORMAL_RETURN",
        payload: JSON.stringify({
          severity: values.severity,
          symptoms: values.symptoms,
          remark: values.remark,
          reportedAt: new Date().toISOString(),
        }),
      });

      setAbnormalEvidence(response);
      message.warning("异常回院已登记，请以审计与刷新后的任务状态为准");
      abnormalForm.resetFields();
      await refreshFollowupData();
    } catch (error: unknown) {
      if (applyApiFieldErrors(abnormalForm, error)) return;
      message.error(getApiErrorMessage(error, "异常回院登记失败"));
    }
  };

  const columns: TableProps<FollowupPlanDetailResponse>["columns"] = [
    {
      title: "计划编号",
      dataIndex: "planId",
      key: "planId",
      render: (planId: string) => <span className={styles.textStrong}>{planId}</span>,
    },
    {
      title: "患者标识",
      dataIndex: "patientId",
      key: "patientId",
    },
    {
      title: "就诊标识",
      dataIndex: "encounterId",
      key: "encounterId",
      render: (encounterId: string) => <Tag color="blue">{encounterId}</Tag>,
    },
    {
      title: "病种编码",
      dataIndex: "diseaseCode",
      key: "diseaseCode",
      render: (code: string) => <Tag>{code}</Tag>,
    },
    {
      title: "模板",
      key: "template",
      render: (_value: unknown, record) =>
        record.templateId ? (
          <Tag color="purple">
            {record.templateId}
            {record.templateVersion ? ` · v${record.templateVersion}` : ""}
          </Tag>
        ) : (
          <Tag>未绑定</Tag>
        ),
    },
    {
      title: "任务进度",
      key: "progress",
      render: (_value: unknown, record) => {
        const total = record.tasks.length;
        const done = record.tasks.filter((task) => task.status === "COMPLETED").length;
        const percent = total > 0 ? Math.round((done / total) * 100) : 0;
        return (
          <Space className={styles.progressRow}>
            <Progress percent={percent} size="small" className={styles.progress} />
            <span className={styles.textSmall}>
              {done}/{total}
            </span>
          </Space>
        );
      },
    },
    {
      title: "计划状态",
      dataIndex: "status",
      key: "status",
      render: (status: FollowupPlanStatus) => {
        const current = planStatusConfig[status] ?? {
          status: "default",
          text: customerEnumLabel(status),
        };
        return <Badge status={current.status} text={current.text} />;
      },
    },
    {
      title: "操作",
      key: "action",
      render: (_value: unknown, record) => (
        <Button
          type="link"
          icon={<CompassOutlined />}
          onClick={() => {
            setSelectedPlanId(record.planId);
            setSelectedTaskId(null);
            setAbnormalEvidence(null);
          }}
          className={styles.buttonLink}
        >
          查看与办理
        </Button>
      ),
    },
  ];

  const templateColumns: TableProps<FollowupTemplateResponse>["columns"] = [
    {
      title: "模板名称",
      dataIndex: "name",
      key: "name",
      render: (name: string, record) => (
        <Space direction="vertical" size={0}>
          <span className={styles.textStrong}>{name}</span>
          <span className={styles.textMuted}>
            {record.templateCode} · v{record.versionNo}
          </span>
        </Space>
      ),
    },
    {
      title: "适用范围",
      dataIndex: "applicableScope",
      key: "applicableScope",
      render: (scope: string) => <Tag>{scope}</Tag>,
    },
    {
      title: "任务",
      key: "tasks",
      render: (_value: unknown, record) => (
        <Space wrap>
          {record.tasks.map((task) => (
            <Tag key={`${record.templateId}-${task.taskType}`}>
              {customerEnumLabel(task.taskType)} +{task.delayDays}天
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: "状态",
      dataIndex: "assetStatus",
      key: "assetStatus",
      render: (status: string) => {
        const current = templateStatusConfig[status] ?? {
          color: "default",
          text: customerEnumLabel(status),
        };
        return <Tag color={current.color}>{current.text}</Tag>;
      },
    },
    {
      title: "操作",
      key: "action",
      render: (_value: unknown, record) =>
        record.assetStatus === "PUBLISHED" ? (
          <Tag color="green">可用于计划生成</Tag>
        ) : (
          <Button
            type="link"
            onClick={() => void handlePublishTemplate(record)}
            loading={publishTemplateMutation.isPending}
            className={styles.buttonLink}
          >
            发布模板
          </Button>
        ),
    },
  ];

  return (
    <PageShell
      title="随访协同"
      description="按当前组织范围查看随访计划、患者问卷回收、护士代填和异常回院处理。"
    >
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: "plans", label: "计划执行" },
          { key: "templates", label: "随访模板" },
        ]}
      />
      {activeTab === "plans" ? (
        <>
          <Row gutter={[16, 16]} className={styles.sectionGapLg}>
            <Col xs={24} sm={12} xl={6}>
              <Card>
                <Statistic
                  title="当前范围随访计划"
                  value={statsLoading ? "..." : stats.totalPlans}
                  prefix={<FileTextOutlined />}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <Card>
                <Statistic
                  title="当前范围执行中计划"
                  value={statsLoading ? "..." : stats.activePlans}
                  prefix={<CompassOutlined />}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <Card>
                <Statistic
                  title="当前范围已完成任务"
                  value={statsLoading ? "..." : stats.completedTasks}
                  prefix={<CheckCircleOutlined />}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <Card>
                <Statistic
                  title="当前范围任务完成率"
                  value={statsLoading ? "..." : stats.taskCompletionRatePercent}
                  suffix="%"
                  prefix={<AlertOutlined />}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic
                  title="当前范围异常回院率"
                  value={statsLoading ? "..." : stats.abnormalReturnRatePercent}
                  suffix="%"
                  prefix={<WarningOutlined />}
                />
              </Card>
            </Col>
          </Row>

          <Card className={styles.sectionGapLg}>
            <Space wrap className={styles.rowBetween}>
              <Space wrap>
                <Input
                  placeholder="按患者标识检索"
                  allowClear
                  value={patientFilter}
                  onChange={(event) => {
                    setPatientFilter(event.target.value);
                    setPlanPage(1);
                  }}
                  onPressEnter={() => void refreshFollowupData()}
                  className={styles.searchInput}
                />
                <Button
                  onClick={() => {
                    setPlanPage(1);
                    void refreshFollowupData();
                  }}
                >
                  查询
                </Button>
              </Space>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setSelectedSnapshotId("");
                  setPublishedTemplateSearch("");
                  setGenerateModalVisible(true);
                }}
              >
                生成随访计划
              </Button>
            </Space>
          </Card>

          {isError && (
            <Alert
              type="error"
              showIcon
              className={styles.sectionGap}
              message="随访计划读取失败"
              description="请确认登录状态、组织范围；若持续失败，请联系信息科核查随访服务。"
            />
          )}

          {statsError && (
            <Alert
              type="error"
              showIcon
              className={styles.sectionGap}
              message="随访统计读取失败"
              description="看板统计来自当前组织范围聚合，暂不可用时不使用当前页列表冒充全局统计。"
            />
          )}

          <Card>
            <Table
              columns={columns}
              dataSource={displayPlans}
              rowKey="planId"
              loading={isLoading}
              locale={{ emptyText: "当前暂无随访计划" }}
              pagination={{
                current: apiPlansData?.page ?? planPage,
                pageSize: apiPlansData?.size ?? FOLLOWUP_PLAN_PAGE_SIZE,
                total: apiPlansData?.total ?? displayPlans.length,
                showSizeChanger: false,
                onChange: (page) => setPlanPage(page),
                showTotal: (total) => `共 ${total} 个随访计划`,
              }}
            />
          </Card>
        </>
      ) : (
        <Space direction="vertical" size="large" className={styles.fullWidth}>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={8}>
              <Card>
                <Statistic title="模板总数" value={templates.length} />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card>
                <Statistic title="已发布模板" value={publishedTemplates.length} />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card>
                <Statistic
                  title="待发布模板"
                  value={
                    templates.filter((template) => template.assetStatus !== "PUBLISHED").length
                  }
                />
              </Card>
            </Col>
          </Row>
          <Card>
            <Space wrap className={styles.rowBetween}>
              <Space direction="vertical" size={0}>
                <span className={styles.textStrong}>随访模板</span>
                <span className={styles.textMuted}>
                  模板发布后才能用于生成随访计划，已生成计划继续保留原模板版本。
                </span>
              </Space>
              <Input
                placeholder="按模板编码、名称或适用范围检索"
                allowClear
                value={templateSearch}
                onChange={(event) => {
                  setTemplateSearch(event.target.value);
                  setTemplatePage(1);
                }}
                className={styles.searchInput}
              />
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setTemplateModalVisible(true)}
              >
                新建模板
              </Button>
            </Space>
          </Card>
          <Card>
            <Table
              columns={templateColumns}
              dataSource={templates}
              rowKey="templateId"
              loading={templatesQuery.isLoading}
              locale={{ emptyText: "当前暂无随访模板" }}
              pagination={{
                current: templatesQuery.data?.page ?? templatePage,
                pageSize: templatesQuery.data?.size ?? FOLLOWUP_TEMPLATE_PAGE_SIZE,
                total: templatesQuery.data?.total ?? templates.length,
                showSizeChanger: false,
                onChange: (page) => setTemplatePage(page),
                showTotal: (total) => `共 ${total} 个随访模板`,
              }}
            />
          </Card>
        </Space>
      )}

      <Modal
        title="生成随访计划"
        open={generateModalVisible}
        onOk={handleGeneratePlan}
        onCancel={() => {
          setGenerateModalVisible(false);
          setSnapshotPatientId("");
          setSnapshotEncounterId("");
          setSelectedSnapshotId("");
          setPublishedTemplateSearch("");
          generateForm.resetFields();
        }}
        confirmLoading={generatePlanMutation.isPending}
        destroyOnClose
        okText="生成"
        cancelText="取消"
      >
        <Form form={generateForm} layout="vertical" className={styles.formGap}>
          <Space wrap className={styles.fullWidth}>
            <Form.Item label="患者标识">
              <Input
                aria-label="随访快照患者标识"
                placeholder="输入患者标识检索已生效快照"
                value={snapshotPatientId}
                onChange={(event) => {
                  setSnapshotPatientId(event.target.value);
                  setSelectedSnapshotId("");
                }}
              />
            </Form.Item>
            <Form.Item label="就诊标识">
              <Input
                aria-label="随访快照就诊标识"
                placeholder="可单独按就诊标识检索"
                value={snapshotEncounterId}
                onChange={(event) => {
                  setSnapshotEncounterId(event.target.value);
                  setSelectedSnapshotId("");
                }}
              />
            </Form.Item>
          </Space>
          <ContextSnapshotSelector
            enabled={hasSnapshotFilter}
            loading={snapshotsQuery.isLoading}
            error={snapshotsQuery.isError}
            snapshots={snapshotsQuery.data?.items ?? []}
            selectedSnapshotId={selectedSnapshotId}
            onSelect={setSelectedSnapshotId}
            noun="随访上下文快照"
          />
          {snapshotDetailQuery.data ? (
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="机构生效版本">
                {snapshotDetailQuery.data.runtimeReleaseId ?? "由当前机构生效版本确认"}
              </Descriptions.Item>
              <Descriptions.Item label="质量状态">
                {customerDisplayText(snapshotDetailQuery.data.qualityStatus)}
              </Descriptions.Item>
            </Descriptions>
          ) : null}
          <Form.Item
            name="contextSnapshotId"
            hidden
            rules={[
              {
                validator: async () => {
                  if (!selectedSnapshotId) {
                    throw new Error("请选择已生效随访上下文快照");
                  }
                },
              },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="riskLevel"
            label="随访风险分层"
            rules={[{ required: true, message: "请选择风险分层" }]}
          >
            <Select
              options={[
                { value: "LOW", label: "低风险" },
                { value: "MEDIUM", label: "中风险" },
                { value: "HIGH", label: "高风险" },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="templateId"
            label="随访模板"
            rules={[{ required: true, message: "请选择已发布随访模板" }]}
          >
            <Select
              showSearch
              allowClear
              filterOption={false}
              onSearch={setPublishedTemplateSearch}
              onClear={() => setPublishedTemplateSearch("")}
              loading={publishedTemplatesQuery.isLoading}
              options={templateOptions}
              placeholder="选择已发布随访模板"
              notFoundContent="当前暂无已发布随访模板"
            />
          </Form.Item>
          <Form.Item
            name="taskTypes"
            label="随访任务类型"
            rules={[{ required: true, message: "请至少选择一种任务类型" }]}
            initialValue={["QUESTIONNAIRE"]}
          >
            <Checkbox.Group options={taskTypeOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="随访计划办理"
        width={860}
        open={!!selectedPlanId}
        onClose={() => {
          setSelectedPlanId(null);
          setSelectedTaskId(null);
          setAbnormalEvidence(null);
        }}
        destroyOnClose
      >
        {selectedPlanDetail && (
          <Space direction="vertical" size="large" className={styles.fullWidth}>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="计划编号">{selectedPlanDetail.planId}</Descriptions.Item>
              <Descriptions.Item label="服务机构">{selectedPlanDetail.tenantId}</Descriptions.Item>
              <Descriptions.Item label="患者标识">{selectedPlanDetail.patientId}</Descriptions.Item>
              <Descriptions.Item label="就诊标识">
                {selectedPlanDetail.encounterId}
              </Descriptions.Item>
              <Descriptions.Item label="病种编码">
                {selectedPlanDetail.diseaseCode}
              </Descriptions.Item>
              <Descriptions.Item label="随访模板">
                {selectedPlanDetail.templateId ? (
                  <Tag color="purple">
                    {selectedPlanDetail.templateId}
                    {selectedPlanDetail.templateVersion
                      ? ` · v${selectedPlanDetail.templateVersion}`
                      : ""}
                  </Tag>
                ) : (
                  "未绑定"
                )}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Badge
                  status={planStatusConfig[selectedPlanDetail.status]?.status ?? "default"}
                  text={
                    planStatusConfig[selectedPlanDetail.status]?.text ?? selectedPlanDetail.status
                  }
                />
              </Descriptions.Item>
            </Descriptions>

            <Card title="随访任务">
              <Space direction="vertical" className={styles.fullWidth}>
                {selectedPlanDetail.tasks.map((task) => (
                  <Card key={task.taskId} size="small">
                    <Space wrap className={styles.rowBetween}>
                      <Space wrap>
                        <Tag color={task.status === "COMPLETED" ? "green" : "blue"}>
                          {customerEnumLabel(task.status)}
                        </Tag>
                        <span className={styles.textStrong}>{task.taskId}</span>
                        <span>{customerEnumLabel(task.taskType)}</span>
                        <span className={styles.textMuted}>
                          截止：{new Date(task.dueDate).toLocaleDateString()}
                        </span>
                      </Space>
                      {task.taskType === "QUESTIONNAIRE" &&
                        task.questionnaireTemplateId &&
                        task.status === "PENDING" &&
                        selectedPlanDetail.status === "ACTIVE" && (
                          <Button size="small" onClick={() => setSelectedTaskId(task.taskId)}>
                            填报
                          </Button>
                        )}
                    </Space>
                  </Card>
                ))}
              </Space>
            </Card>

            <Card title="问卷回收">
              {selectedTaskId ? (
                <Form
                  form={questionnaireForm}
                  layout="vertical"
                  onFinish={handleSubmitQuestionnaire}
                  initialValues={{ executorType: "PATIENT" }}
                >
                  <Alert
                    type="info"
                    showIcon
                    className={styles.sectionGap}
                    message={`正在提交问卷任务：${selectedTaskId}`}
                  />
                  <Form.Item
                    name="executorType"
                    label="提交来源"
                    rules={[{ required: true, message: "请选择问卷提交来源" }]}
                  >
                    <Select
                      options={[
                        { value: "PATIENT", label: "患者自填" },
                        { value: "NURSE", label: "护士代填" },
                        { value: "PHYSICIAN", label: "医生复核录入" },
                      ]}
                    />
                  </Form.Item>
                  <Form.Item
                    name="content"
                    label="问卷回收内容"
                    rules={[{ required: true, message: "请输入问卷回收内容" }]}
                  >
                    <TextArea rows={4} placeholder="请录入来自真实随访渠道的回收内容" />
                  </Form.Item>
                  <Button
                    type="primary"
                    htmlType="submit"
                    icon={<CheckCircleOutlined />}
                    loading={submitQuestionnaireMutation.isPending}
                  >
                    提交问卷
                  </Button>
                </Form>
              ) : (
                <Alert type="info" showIcon message="请选择一个待办随访任务后提交问卷回收内容" />
              )}
            </Card>

            <Card title="异常回院登记">
              <Form form={abnormalForm} layout="vertical" onFinish={handleReportAbnormal}>
                <Form.Item
                  name="severity"
                  label="回院风险等级"
                  rules={[{ required: true, message: "请选择回院风险等级" }]}
                >
                  <Select
                    options={[
                      { value: "LOW", label: "低风险" },
                      { value: "MEDIUM", label: "中风险" },
                      { value: "HIGH", label: "高风险" },
                    ]}
                  />
                </Form.Item>
                <Form.Item
                  name="symptoms"
                  label="异常症状或情况"
                  rules={[{ required: true, message: "请输入异常症状或情况" }]}
                >
                  <TextArea rows={3} placeholder="请录入真实随访反馈中的异常表现" />
                </Form.Item>
                <Form.Item
                  name="remark"
                  label="医护处理建议"
                  rules={[{ required: true, message: "请输入医护处理建议" }]}
                >
                  <TextArea rows={3} placeholder="请录入当前医护人员给出的处理建议" />
                </Form.Item>
                <Button
                  type="primary"
                  danger
                  htmlType="submit"
                  icon={<WarningOutlined />}
                  loading={reportAbnormalMutation.isPending}
                  disabled={selectedPlanDetail.status !== "ACTIVE"}
                >
                  登记异常回院
                </Button>
              </Form>
              {abnormalEvidence && (
                <Alert
                  type="warning"
                  showIcon
                  className={styles.formGap}
                  message="异常回院证据已登记"
                  description={
                    <Space wrap>
                      <Tag color="red">异常记录 {abnormalEvidence.eventId}</Tag>
                      <Tag color="orange">回院任务 {abnormalEvidence.returnTaskId}</Tag>
                      <Tag color="gold">通知记录 {abnormalEvidence.notificationEventId}</Tag>
                      <Tag>追踪号 {abnormalEvidence.traceId}</Tag>
                    </Space>
                  }
                />
              )}
            </Card>
          </Space>
        )}
      </Drawer>
      <Modal
        title="新建随访模板"
        open={templateModalVisible}
        width={720}
        onOk={handleCreateTemplate}
        onCancel={() => {
          setTemplateModalVisible(false);
          templateForm.resetFields();
        }}
        confirmLoading={createTemplateMutation.isPending}
        destroyOnClose
        okText="创建"
        cancelText="取消"
      >
        <Form
          form={templateForm}
          layout="vertical"
          className={styles.formGap}
          initialValues={defaultFollowupTemplateFormValues}
        >
          <Alert
            type="info"
            showIcon
            className={styles.sectionGap}
            message="按临床随访方案填写"
            description="页面显示医生、护士和实施人员能直接判断的业务内容；保存时同步生成标准编码、依据和版本证据。"
          />
          <div className={styles.formSectionTitle}>方案与适用范围</div>
          <Form.Item
            name="templateCode"
            label="院内方案编号"
            rules={[{ required: true, message: "请输入院内方案编号" }]}
          >
            <Input placeholder="例如 FUP.COPD.DISCHARGE 或 慢阻肺出院随访-2026" />
          </Form.Item>
          <Form.Item
            name="name"
            label="模板名称"
            rules={[{ required: true, message: "请输入模板名称" }]}
          >
            <Input placeholder="例如 慢阻肺出院随访" />
          </Form.Item>
          <Form.Item name="description" label="模板说明">
            <TextArea rows={2} placeholder="说明适用场景、随访目标和触发条件" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="organizationScope"
                label="适用机构范围"
                rules={[{ required: true, message: "请选择适用机构范围" }]}
              >
                <Select options={organizationScopeOptions} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="applicableScope"
                label="随访病种"
                rules={[{ required: true, message: "请选择随访病种" }]}
              >
                <Select options={followupDiseaseOptions} />
              </Form.Item>
            </Col>
          </Row>
          <div className={styles.formSectionTitle}>问卷与异常处理</div>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="questionnaireDelayDays"
                label="问卷延迟天数"
                rules={[{ required: true, message: "请输入问卷延迟天数" }]}
              >
                <InputNumber min={0} className={styles.fullWidth} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="outpatientDelayDays"
                label="复诊延迟天数"
                rules={[{ required: true, message: "请输入复诊延迟天数" }]}
              >
                <InputNumber min={0} className={styles.fullWidth} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="questionnaireTemplateId"
            label="问卷内容模板"
            rules={[{ required: true, message: "请选择问卷内容模板" }]}
          >
            <Select options={questionnaireTemplateOptions} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="questionCode"
                label="核心随访问题"
                rules={[{ required: true, message: "请选择核心随访问题" }]}
              >
                <Select options={followupQuestionOptions} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="questionType"
                label="回答方式"
                rules={[{ required: true, message: "请选择回答方式" }]}
              >
                <Select
                  options={[
                    { value: "TEXT", label: "文本" },
                    { value: "SINGLE_CHOICE", label: "单选" },
                    { value: "NUMBER", label: "数值" },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="abnormalCondition"
            label="异常触发条件"
            rules={[{ required: true, message: "请输入异常触发条件" }]}
          >
            <TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name="notifyTarget"
            label="通知对象"
            rules={[{ required: true, message: "请输入通知对象" }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="sourceRef"
            label="院内依据"
            rules={[{ required: true, message: "请选择院内依据" }]}
          >
            <Select options={followupSourceOptions} />
          </Form.Item>
        </Form>
      </Modal>
    </PageShell>
  );
}

function buildPlanIdempotencyKey(
  snapshotId: string,
  templateId: string,
  riskLevel: string,
  taskTypes: string[],
) {
  return `followup-plan-${snapshotId}-${templateId}-${riskLevel}-${[...taskTypes]
    .sort()
    .join("-")}`.slice(0, 160);
}
