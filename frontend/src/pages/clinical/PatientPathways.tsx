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
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useSpecialtyPackages,
  useContextSnapshotDetail,
  useContextSnapshots,
  usePathwayTemplates,
  usePathwayTemplateDetail,
  useEnterPatientPathway,
  usePatientPathways,
  usePatientPathwayDetail,
  useAdvancePatientPathway,
  usePatientPathwayClocks,
  usePatientPathwayVariances,
} from "@/shared/api/hooks";
import type {
  PathwayTemplate,
  PatientPathway,
  PatientPathwayStatus,
  PathwayVariance,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import { ContextSnapshotSelector } from "@/shared/ui/ContextSnapshotSelector";
import styles from "./Clinical.module.css";

const { TextArea } = Input;
const { Option } = Select;

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

export default function PatientPathways() {
  const [selectedPathwayId, setSelectedPathwayId] = useState<string | null>(null);
  const [enterModalVisible, setEnterModalVisible] = useState<boolean>(false);
  const [varianceDrawerVisible, setVarianceDrawerVisible] = useState<boolean>(false);

  // 分页状态
  const [page, setPage] = useState<number>(1);
  const [size] = useState<number>(10);
  const [patientFilter, setPatientFilter] = useState<string>("");
  const [enterPatientFilter, setEnterPatientFilter] = useState<string>("");
  const [enterEncounterFilter, setEnterEncounterFilter] = useState<string>("");
  const [selectedContextSnapshotId, setSelectedContextSnapshotId] = useState<string>("");

  const { data: templatesData } = usePathwayTemplates({
    status: "PUBLISHED",
    page: 1,
    size: 100,
  });
  const { data: packagesData } = useSpecialtyPackages({
    page: 1,
    size: 100,
  });
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
  const selectedSnapshotDetailQuery = useContextSnapshotDetail(selectedContextSnapshotId, {
    enabled: enterModalVisible && Boolean(selectedContextSnapshotId),
  });
  const selectedContextSnapshot = enterSnapshotsQuery.data?.items.find(
    (snapshot) => snapshot.snapshotId === selectedContextSnapshotId,
  );

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

  // 同时拉取该路径的模板拓扑节点详情，用于左侧 Milestone Timeline 精准对比绘制
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

  const packageVersionForTemplate = (templateId?: string | null) => {
    const selectedTemplate = templateDetail?.template;
    const template =
      selectedTemplate?.templateId === templateId
        ? selectedTemplate
        : templatesData?.items?.find((item) => item.templateId === templateId);
    const specialtyPackage = packagesData?.items?.find(
      (item) => item.packageId === template?.packageId,
    );
    return specialtyPackage?.packageVersion ?? (template ? String(template.templateVersion) : "");
  };

  const selectedTemplatePackageVersion = () =>
    packageVersionForTemplate(templateDetail?.template.templateId);

  const isPathwayMutable = (status?: PatientPathwayStatus) =>
    status === "ENTERED" || status === "NODE_EXECUTING" || status === "VARIANCE";

  const templateNodes = templateDetail?.nodes ?? [];
  const currentTemplateSortOrder =
    templateNodes.find((node) => node.nodeCode === detailData?.patientPathway.currentNodeCode)
      ?.sortOrder ?? 0;

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
      const packageVersion = selectedSnapshotDetailQuery.data?.packageVersion?.trim();
      if (!selectedContextSnapshot || !packageVersion) {
        message.error("请先选择包含配置包版本的 ACTIVE 临床快照");
        return;
      }
      const res = await enterPathwayMutation.mutateAsync({
        contextSnapshotId: selectedContextSnapshot.snapshotId,
        templateId: values.templateId,
        startNodeCode: values.startNodeCode?.trim() || undefined,
        packageVersion,
      });

      message.success(
        `患者 ${selectedContextSnapshot.patientId} 入径成功，Trace ID: ${res?.traceId || ""}`,
      );
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
        packageVersion: selectedTemplatePackageVersion(),
        eventType: "COMPLETE",
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
        packageVersion: selectedTemplatePackageVersion(),
        eventType: "VARIANCE",
        currentNodeCode: detailData.patientPathway.currentNodeCode,
        requestedNextNodeCode: values.continueNodeCode,
        varianceType: values.varianceType,
        varianceReason: values.reason,
        resolutionAction: values.resolutionAction,
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
        packageVersion: selectedTemplatePackageVersion(),
        eventType: "EXIT",
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

  const columns: TableProps<PatientPathway>["columns"] = [
    {
      title: "实例编号",
      dataIndex: "patientPathwayId",
      key: "patientPathwayId",
      render: (text: string) => (
        <span className={`${styles.codeText} ${styles.textStrong}`}>{text}</span>
      ),
    },
    {
      title: "患者 ID",
      dataIndex: "patientId",
      key: "patientId",
      className: styles.textStrong,
    },
    {
      title: "就诊 ID",
      dataIndex: "encounterId",
      key: "encounterId",
      render: (text: string) => <Tag color="blue">{text}</Tag>,
    },
    {
      title: "路径模板",
      dataIndex: "templateId",
      key: "templateId",
      render: (text: string) => <Tag color="geekblue">{text}</Tag>,
    },
    {
      title: "当前流转节点",
      dataIndex: "currentNodeCode",
      key: "currentNodeCode",
      render: (c: string, record: PatientPathway) => {
        if (record.status === "EXITED") return <Tag color="red">已退径</Tag>;
        if (record.status === "COMPLETED") return <Tag color="green">已完成路径</Tag>;
        return <Tag color="orange">{c}</Tag>;
      },
    },
    {
      title: "流转状态",
      dataIndex: "status",
      key: "status",
      render: (status: PatientPathwayStatus) => {
        const config: Record<string, { status: PathwayBadgeStatus; text: string }> = {
          ENTERED: { status: "processing", text: "已入径 (ENTERED)" },
          NODE_EXECUTING: { status: "processing", text: "节点执行中 (NODE_EXECUTING)" },
          VARIANCE: { status: "warning", text: "变异处理中 (VARIANCE)" },
          COMPLETED: { status: "success", text: "已完成 (COMPLETED)" },
          EXITED: { status: "error", text: "已退出 (EXITED)" },
        };
        const current = config[status] ?? { status: "default", text: status };
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
    <PageShell
      title="患者路径"
      description="办理临床患者入径，提供标准路径 Milestone 状态时间线，支撑医护标准推进、临床变异登记与可审计链追溯。"
      state={patientPathwaysPageState}
      stateProps={patientPathwaysStateProps}
    >
      <div className={`${styles.surface} ${styles.filterSurface}`}>
        <Form layout="inline" className={styles.inlineForm}>
          <Form.Item label="患者 ID 检索">
            <Input
              placeholder="输入患者 ID"
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
            showTotal: (t) => `共 ${t} 个临床运行中的患者实例`,
          }}
          locale={{
            emptyText: "暂无患者路径实例。请先办理患者入径，或调整检索条件。",
          }}
          className="medkernel-table"
        />
      </div>

      <Modal
        title="办理患者临床路径准入 (入径)"
        open={enterModalVisible}
        onOk={handleEnterPathway}
        onCancel={() => {
          setEnterModalVisible(false);
          setEnterPatientFilter("");
          setEnterEncounterFilter("");
          setSelectedContextSnapshotId("");
          enterForm.resetFields();
        }}
        width={680}
        confirmLoading={enterPathwayMutation.isPending}
        destroyOnClose
      >
        <Form form={enterForm} layout="vertical" className={styles.formGap}>
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item label="患者 ID">
                <Input
                  allowClear
                  placeholder="按患者 ID 查询快照"
                  value={enterPatientFilter}
                  onChange={(event) => {
                    setEnterPatientFilter(event.target.value);
                    setSelectedContextSnapshotId("");
                  }}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="就诊 ID">
                <Input
                  allowClear
                  placeholder="按就诊 ID 查询快照"
                  value={enterEncounterFilter}
                  onChange={(event) => {
                    setEnterEncounterFilter(event.target.value);
                    setSelectedContextSnapshotId("");
                  }}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="ACTIVE 临床快照" required>
            <ContextSnapshotSelector
              enabled={hasEnterSnapshotFilter}
              loading={enterSnapshotsQuery.isLoading}
              error={enterSnapshotsQuery.isError}
              snapshots={enterSnapshotsQuery.data?.items ?? []}
              selectedSnapshotId={selectedContextSnapshotId}
              onSelect={setSelectedContextSnapshotId}
            />
          </Form.Item>
          <Form.Item name="templateId" label="选择受控专病路径模板" rules={[{ required: true }]}>
            <Select placeholder="选择已发布上线的临床路径模型" notFoundContent="暂无已发布路径模板">
              {templatesData?.items?.map((t: PathwayTemplate) => (
                <Option key={t.templateId} value={t.templateId}>
                  {t.name} (v{t.templateVersion}.0)
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="startNodeCode" label="起始临床推进节点 (可选，留空使用模板起点)">
            <Input placeholder="留空使用已发布模板的起始节点" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <div className={styles.drawerTitle}>
            <CompassOutlined className={styles.iconInfo} />
            <span>患者临床路径推进与解释追溯控制台</span>
          </div>
        }
        width={960}
        onClose={() => setSelectedPathwayId(null)}
        open={!!selectedPathwayId}
        destroyOnClose
      >
        {detailData && (
          <div>
            <Descriptions
              title="入径运行事实 facts"
              bordered
              column={3}
              size="small"
              className={styles.sectionGapLg}
            >
              <Descriptions.Item label="患者 ID">
                <span className={styles.textStrong}>{detailData.patientPathway.patientId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="就诊编号">
                <span className={styles.codeText}>{detailData.patientPathway.encounterId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="所用模型">
                <Tag color="geekblue">{detailData.patientPathway.templateId}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="入径实例">
                <span className={styles.codeText}>
                  {detailData.patientPathway.patientPathwayId}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="当前激活节点">
                <Tag color="orange">{detailData.patientPathway.currentNodeCode || "无/已结径"}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="运行状态">
                <Badge
                  status={
                    isPathwayMutable(detailData.patientPathway.status) ? "processing" : "default"
                  }
                  text={detailData.patientPathway.status}
                />
              </Descriptions.Item>
              <Descriptions.Item label="准入时间" span={3}>
                {detailData.patientPathway.enteredAt
                  ? new Date(detailData.patientPathway.enteredAt).toLocaleString()
                  : "未知"}
              </Descriptions.Item>
            </Descriptions>

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
                      items={templateNodes.map((node) => {
                        const isCurrent =
                          node.nodeCode === detailData.patientPathway.currentNodeCode;
                        const activeClock = clocksData?.find((c) => c.nodeCode === node.nodeCode);

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
                                {node.nodeCode}
                              </div>

                              {activeClock && (
                                <div className={styles.evidenceBox}>
                                  <div className={styles.rowBetween}>
                                    <span>
                                      时钟:{" "}
                                      <Tag color="purple" className={styles.tagCompact}>
                                        {activeClock.clockId}
                                      </Tag>
                                    </span>
                                    <span>
                                      状态:{" "}
                                      <Tag
                                        color={activeClock.status === "OVERDUE" ? "red" : "green"}
                                        className={styles.tagCompact}
                                      >
                                        {activeClock.status}
                                      </Tag>
                                    </span>
                                  </div>
                                  <div className={styles.timelineMeta}>
                                    开始: {new Date(activeClock.startedAt).toLocaleTimeString()}
                                  </div>
                                  <div className={styles.timelineMeta}>
                                    截止: {new Date(activeClock.dueAt).toLocaleTimeString()}
                                  </div>
                                  {activeClock.metricCode && (
                                    <div className={styles.timelineMeta}>
                                      关联质控指标: {activeClock.metricCode}
                                    </div>
                                  )}
                                </div>
                              )}
                            </>
                          ),
                        };
                      })}
                    />
                  </div>
                </Card>
              </Col>

              <Col xs={24} xl={14}>
                <Card
                  title={
                    <div className={styles.sectionTitle}>
                      <RightCircleOutlined className={styles.iconInfo} />
                      <span>受控推进决策控制台 (Advance)</span>
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
                              message="标准推进：完成当前节点并进入下一个标准路径节点。系统会按模板出边计算下一步并刷新关键时钟。"
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
                                        {targetNode?.name || "未知"} ({e.toNodeCode})
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
                              message="临床变异：当患者疾病进展偏离指南或自主拒绝时，在此登记医学/患者偏离事实。这允许偏离标准出边强制推进，并记录审计追溯依据。"
                              type="warning"
                              showIcon
                              className={styles.sectionGap}
                            />
                            <Row gutter={12}>
                              <Col span={12}>
                                <Form.Item
                                  name="varianceType"
                                  label="变异偏离类型"
                                  rules={[{ required: true }]}
                                >
                                  <Select placeholder="选择类型" aria-label="变异偏离类型">
                                    <Option value="MEDICAL">MEDICAL (医学指征原因)</Option>
                                    <Option value="PATIENT_REASON">
                                      PATIENT_REASON (患者拒绝或意愿)
                                    </Option>
                                    <Option value="RESOURCE_REASON">
                                      RESOURCE_REASON (医院资源限制)
                                    </Option>
                                    <Option value="DOCTOR_CHOICE">
                                      DOCTOR_CHOICE (医生临床抉择)
                                    </Option>
                                    <Option value="SYSTEM_REASON">
                                      SYSTEM_REASON (系统非预期偏离)
                                    </Option>
                                  </Select>
                                </Form.Item>
                              </Col>
                              <Col span={12}>
                                <Form.Item name="continueNodeCode" label="强制调整/继续节点 (可选)">
                                  <Select
                                    placeholder="选择目标节点"
                                    aria-label="强制调整或继续节点"
                                  >
                                    {templateDetail?.nodes.map((n) => (
                                      <Option key={n.nodeId} value={n.nodeCode}>
                                        {n.name} ({n.nodeCode})
                                      </Option>
                                    ))}
                                  </Select>
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
                            <Form.Item
                              name="resolutionAction"
                              label="变异处置干预动作"
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
                              提交路径变异并强制推进
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
        title={
          <div className={styles.drawerTitle}>
            <WarningOutlined className={styles.iconInfo} />
            <span>临床路径变异事实</span>
          </div>
        }
        width={640}
        onClose={() => setVarianceDrawerVisible(false)}
        open={varianceDrawerVisible}
        destroyOnClose
      >
        {(variancesData ?? detailData?.variances ?? []).length > 0 ? (
          <div>
            <Alert
              message="这里仅展示后端返回的路径变异事实，页面不补写原因、不生成本地变异记录。"
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
                          {variance.nodeCode} · {variance.varianceType}
                        </div>
                        <div className={styles.timelineMeta}>
                          <span>变异 ID：</span>
                          <span>{variance.varianceId}</span>
                        </div>
                        <div className={styles.timelineMeta}>{variance.reason}</div>
                        <div className={styles.timelineMeta}>
                          <span>处置动作：</span>
                          <span>{variance.resolutionAction || "未记录"}</span>
                        </div>
                        {variance.continueNodeCode && (
                          <div className={styles.timelineMeta}>
                            <span>继续节点：</span>
                            <Tag color="blue">{variance.continueNodeCode}</Tag>
                          </div>
                        )}
                        {variance.traceId && (
                          <div className={`${styles.timelineMeta} ${styles.timelineMuted}`}>
                            traceId: {variance.traceId}
                          </div>
                        )}
                        <div className={`${styles.timelineMeta} ${styles.timelineMuted}`}>
                          {variance.createdAt
                            ? new Date(variance.createdAt).toLocaleString()
                            : "未返回时间"}
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
    </PageShell>
  );
}
