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
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Table,
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
  useSubmitFollowupQuestionnaire,
  useReportFollowupAbnormal,
} from "@/shared/api/hooks";
import type {
  FollowupAbnormalReportResponse,
  FollowupPlanDetailResponse,
  FollowupPlanStatus,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { ContextSnapshotSelector } from "@/shared/ui/ContextSnapshotSelector";
import {
  customerDisplayText,
  customerEnumLabel,
} from "@/shared/config/customerLabels";

import styles from "./Clinical.module.css";

const { TextArea } = Input;

const planStatusConfig: Record<FollowupPlanStatus, { status: BadgeProps["status"]; text: string }> =
  {
    DRAFT: { status: "default", text: "草案" },
    ACTIVE: { status: "processing", text: "执行中" },
    COMPLETED: { status: "success", text: "已结案" },
    CANCELLED: { status: "error", text: "已取消" },
  };

const taskTypeOptions = [
  { value: "QUESTIONNAIRE", label: "问卷回收" },
  { value: "EXAM", label: "检查复查" },
  { value: "LAB", label: "检验报告跟踪" },
  { value: "OUTPATIENT", label: "门诊复诊" },
];

export default function Followup() {
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [generateModalVisible, setGenerateModalVisible] = useState(false);
  const [patientFilter, setPatientFilter] = useState("");
  const [snapshotPatientId, setSnapshotPatientId] = useState("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState("");
  const [selectedSnapshotId, setSelectedSnapshotId] = useState("");
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [abnormalEvidence, setAbnormalEvidence] = useState<FollowupAbnormalReportResponse | null>(
    null,
  );

  const [generateForm] = Form.useForm();
  const [questionnaireForm] = Form.useForm();
  const [abnormalForm] = Form.useForm();

  const {
    data: apiPlansData,
    refetch: refetchPlans,
    isLoading,
    isError,
  } = useFollowupPlans({
    patientId: patientFilter.trim() || undefined,
    page: 1,
    size: 100,
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
  const submitQuestionnaireMutation = useSubmitFollowupQuestionnaire();
  const reportAbnormalMutation = useReportFollowupAbnormal();
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
        riskLevel: values.riskLevel,
        taskTypes: values.taskTypes,
        idempotencyKey: buildPlanIdempotencyKey(
          selectedSnapshotId,
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
        executorType: "PHYSICIAN",
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
      message.warning("随访异常事件已上报，请以审计与刷新后的任务状态为准");
      abnormalForm.resetFields();
      await refreshFollowupData();
    } catch (error: unknown) {
      if (applyApiFieldErrors(abnormalForm, error)) return;
      message.error(getApiErrorMessage(error, "异常上报失败"));
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
      title: "患者 ID",
      dataIndex: "patientId",
      key: "patientId",
    },
    {
      title: "就诊 ID",
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

  return (
    <PageShell
      title="智能随访工作台"
      description="查看真实随访计划、提交问卷回收内容，并上报随访异常事件。页面只展示后端接口返回的数据。"
    >
      <Row gutter={[16, 16]} className={styles.sectionGapLg}>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic
              title="作用域随访计划数"
              value={statsLoading ? "..." : stats.totalPlans}
              prefix={<FileTextOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic
              title="作用域执行中计划"
              value={statsLoading ? "..." : stats.activePlans}
              prefix={<CompassOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic
              title="作用域已完成任务"
              value={statsLoading ? "..." : stats.completedTasks}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic
              title="作用域任务完成率"
              value={statsLoading ? "..." : stats.taskCompletionRatePercent}
              suffix="%"
              prefix={<AlertOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="作用域异常回院率"
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
              placeholder="按患者 ID 检索"
              allowClear
              value={patientFilter}
              onChange={(event) => setPatientFilter(event.target.value)}
              onPressEnter={() => void refreshFollowupData()}
              className={styles.searchInput}
            />
            <Button onClick={() => void refreshFollowupData()}>查询</Button>
          </Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setSelectedSnapshotId("");
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
          message="随访计划接口读取失败"
          description="请检查登录权限、服务空间或后端接口状态。"
        />
      )}

      {statsError && (
        <Alert
          type="error"
          showIcon
          className={styles.sectionGap}
          message="随访统计接口读取失败"
          description="看板统计来自后端作用域聚合，当前不可用时不使用当前页数据冒充全局统计。"
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
            pageSize: 10,
            showTotal: (total) => `共 ${total} 个随访计划`,
          }}
        />
      </Card>

      <Modal
        title="生成随访计划"
        open={generateModalVisible}
        onOk={handleGeneratePlan}
        onCancel={() => {
          setGenerateModalVisible(false);
          setSnapshotPatientId("");
          setSnapshotEncounterId("");
          setSelectedSnapshotId("");
          generateForm.resetFields();
        }}
        confirmLoading={generatePlanMutation.isPending}
        destroyOnClose
        okText="生成"
        cancelText="取消"
      >
        <Form form={generateForm} layout="vertical" className={styles.formGap}>
          <Space wrap className={styles.fullWidth}>
            <Form.Item label="患者 ID">
              <Input
                aria-label="随访快照患者 ID"
                placeholder="输入患者 ID 检索 ACTIVE 快照"
                value={snapshotPatientId}
                onChange={(event) => {
                  setSnapshotPatientId(event.target.value);
                  setSelectedSnapshotId("");
                }}
              />
            </Form.Item>
            <Form.Item label="就诊 ID">
              <Input
                aria-label="随访快照就诊 ID"
                placeholder="可单独按就诊 ID 检索"
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
              <Descriptions.Item label="配置包版本">
                {snapshotDetailQuery.data.packageVersion ?? "未标注"}
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
                    throw new Error("请选择 ACTIVE 随访上下文快照");
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
              <Descriptions.Item label="服务空间">
                {selectedPlanDetail.tenantId}
              </Descriptions.Item>
              <Descriptions.Item label="患者 ID">{selectedPlanDetail.patientId}</Descriptions.Item>
              <Descriptions.Item label="就诊 ID">
                {selectedPlanDetail.encounterId}
              </Descriptions.Item>
              <Descriptions.Item label="病种编码">
                {selectedPlanDetail.diseaseCode}
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
                >
                  <Alert
                    type="info"
                    showIcon
                    className={styles.sectionGap}
                    message={`正在提交随访任务：${selectedTaskId}`}
                  />
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

            <Card title="异常事件上报">
              <Form form={abnormalForm} layout="vertical" onFinish={handleReportAbnormal}>
                <Form.Item
                  name="severity"
                  label="严重性"
                  rules={[{ required: true, message: "请选择严重性" }]}
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
                  label="异常表现"
                  rules={[{ required: true, message: "请输入异常表现" }]}
                >
                  <TextArea rows={3} placeholder="请录入真实随访反馈中的异常表现" />
                </Form.Item>
                <Form.Item
                  name="remark"
                  label="处理建议"
                  rules={[{ required: true, message: "请输入处理建议" }]}
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
                  上报异常事件
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
                      <Tag color="red">异常事件 {abnormalEvidence.eventId}</Tag>
                      <Tag color="orange">回院任务 {abnormalEvidence.returnTaskId}</Tag>
                      <Tag color="gold">通知事件 {abnormalEvidence.notificationEventId}</Tag>
                      <Tag>追踪链路 {abnormalEvidence.traceId}</Tag>
                    </Space>
                  }
                />
              )}
            </Card>
          </Space>
        )}
      </Drawer>
    </PageShell>
  );
}

function buildPlanIdempotencyKey(snapshotId: string, riskLevel: string, taskTypes: string[]) {
  return `followup-plan-${snapshotId}-${riskLevel}-${[...taskTypes].sort().join("-")}`.slice(
    0,
    160,
  );
}
