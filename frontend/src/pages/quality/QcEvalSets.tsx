import { useState } from "react";
import { PageShell } from "@/shared/ui/PageShell";
import {
  Table,
  Button,
  Tag,
  Space,
  Input,
  Select,
  Drawer,
  Modal,
  Form,
  Card,
  Descriptions,
  message,
  Empty,
} from "antd";
import {
  PlusOutlined,
  SearchOutlined,
  PlayCircleOutlined,
  SlidersOutlined,
  CheckCircleOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import {
  useEvaluationIndicators,
  useCreateEvaluationIndicator,
  useSubmitEvaluationIndicator,
  usePublishEvaluationIndicator,
  useActivateEvaluationIndicator,
  useEvaluateSnapshot,
  useContextSnapshots,
} from "@/shared/api/hooks";
import type {
  EvaluationIndicator,
  EvaluationIndicatorStatus,
  EvaluationSubjectType,
  EvaluationRunResponse,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";

const { Option } = Select;

export default function QcEvalSets() {
  const [form] = Form.useForm();
  const [filterStatus, setFilterStatus] = useState<EvaluationIndicatorStatus | undefined>(
    undefined,
  );
  const [filterSubject, setFilterSubject] = useState<EvaluationSubjectType | undefined>(undefined);
  const [searchCode, setSearchCode] = useState<string>("");
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [selectedIndicator, setSelectedIndicator] = useState<EvaluationIndicator | null>(null);

  // 扫描试运行状态
  const [isSandboxOpen, setIsSandboxOpen] = useState(false);
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string>("");
  const [snapshotPatientId, setSnapshotPatientId] = useState<string>("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState<string>("");
  const [scenarioCode, setScenarioCode] = useState<string>("DISCHARGE");
  const [scanResult, setScanResult] = useState<EvaluationRunResponse | null>(null);

  // 加载指标库
  const {
    data: pageData,
    refetch,
    isLoading,
  } = useEvaluationIndicators({
    status: filterStatus,
    subjectType: filterSubject,
    indicatorCode: searchCode ? searchCode : undefined,
    page: 1,
    size: 50,
  });

  // 状态流转 hooks
  const createMutation = useCreateEvaluationIndicator();
  const submitMutation = useSubmitEvaluationIndicator();
  const publishMutation = usePublishEvaluationIndicator();
  const activateMutation = useActivateEvaluationIndicator();
  const scanMutation = useEvaluateSnapshot();
  const snapshotPatientFilter = snapshotPatientId.trim();
  const snapshotEncounterFilter = snapshotEncounterId.trim();
  const hasSnapshotFilter = Boolean(snapshotPatientFilter || snapshotEncounterFilter);
  const snapshotsQuery = useContextSnapshots(
    {
      patientId: snapshotPatientFilter || undefined,
      encounterId: snapshotEncounterFilter || undefined,
      status: "ACTIVE",
      page: 1,
      size: 20,
    },
    { enabled: hasSnapshotFilter },
  );
  const snapshots = snapshotsQuery.data?.items ?? [];

  const handleCreate = async (values: {
    indicatorCode: string;
    versionNo: string | number;
    name: string;
    subjectType: EvaluationSubjectType;
    denominatorDefinition?: string;
    numeratorDefinition?: string;
    exclusionDefinition?: string;
    scoringDefinition?: string;
    timeWindow?: string;
    organizationScope?: string;
    responsibleDepartmentId?: string;
    sourceRef?: string;
  }) => {
    try {
      await createMutation.mutateAsync({
        indicatorCode: values.indicatorCode,
        versionNo: Number(values.versionNo),
        name: values.name,
        subjectType: values.subjectType,
        denominatorDefinition: values.denominatorDefinition || "",
        numeratorDefinition: values.numeratorDefinition || "",
        exclusionDefinition: values.exclusionDefinition,
        scoringDefinition: values.scoringDefinition,
        timeWindow: values.timeWindow || "",
        organizationScope: values.organizationScope || "",
        responsibleDepartmentId: values.responsibleDepartmentId || "",
        sourceRef: values.sourceRef || "",
      });
      message.success("指标草稿版本创建成功");
      setIsCreateModalOpen(false);
      form.resetFields();
      refetch();
    } catch (error: unknown) {
      if (applyApiFieldErrors(form, error)) return;
      message.error(getApiErrorMessage(error, "创建失败：指标编码与版本号不能重复"));
    }
  };

  const handleSubmit = async (indicatorId: string) => {
    try {
      await submitMutation.mutateAsync(indicatorId);
      message.success("指标成功提交送审");
      refetch();
      setIsDetailOpen(false);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "流转送审失败"));
    }
  };

  const handlePublish = async (indicatorId: string) => {
    try {
      await publishMutation.mutateAsync(indicatorId);
      message.success("指标成功发布通过");
      refetch();
      setIsDetailOpen(false);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "发布失败"));
    }
  };

  const handleActivate = async (indicatorId: string) => {
    try {
      await activateMutation.mutateAsync(indicatorId);
      message.success("指标激活成功，同编码旧 ACTIVE 版本已自动 Offline 下线");
      refetch();
      setIsDetailOpen(false);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "激活失败"));
    }
  };

  const handleRunScan = async () => {
    if (!selectedSnapshotId) {
      message.warning("请选择或输入要扫描的临床快照 ID");
      return;
    }
    try {
      setScanResult(null);
      const res = await scanMutation.mutateAsync({
        contextSnapshotId: selectedSnapshotId,
        scenarioCode,
      });
      setScanResult(res);
      message.success("质控规则自动扫描求值完成");
    } catch (err: unknown) {
      message.error(
        getApiErrorMessage(err, "扫描计算失败：病例不符合分母入组规则或缺少 ACTIVE 指标"),
      );
    }
  };

  const renderSnapshotChoices = () => {
    if (!hasSnapshotFilter) {
      return <Empty description="请输入患者 ID 或就诊 ID 读取真实快照，或直接输入已知快照 ID" />;
    }
    if (snapshotsQuery.isLoading) {
      return <Card className="border-slate-100 rounded-lg">正在读取临床快照列表...</Card>;
    }
    if (snapshotsQuery.isError) {
      return <Empty description="临床快照接口读取失败，请稍后重试或直接输入已存在的快照 ID" />;
    }
    if (snapshots.length === 0) {
      return <Empty description="当前患者或就诊下暂无 ACTIVE 临床快照" />;
    }
    return snapshots.map((snap) => (
      <Card
        key={snap.snapshotId}
        hoverable
        className={`border-slate-100 rounded-lg cursor-pointer ${
          selectedSnapshotId === snap.snapshotId
            ? "border-amber-300 bg-amber-500/5 shadow-sm"
            : "bg-white"
        }`}
        onClick={() => setSelectedSnapshotId(snap.snapshotId)}
      >
        <div className="flex items-start gap-2.5">
          <span
            className={`w-3.5 h-3.5 rounded-full flex items-center justify-center text-[9px] font-bold text-white mt-0.5 ${
              selectedSnapshotId === snap.snapshotId ? "bg-amber-500 animate-pulse" : "bg-slate-400"
            }`}
          >
            {selectedSnapshotId === snap.snapshotId ? "✓" : "•"}
          </span>
          <div>
            <div className="font-semibold text-slate-800 text-xs">{snap.snapshotId}</div>
            <div className="text-slate-500 text-[11px] mt-1 leading-relaxed">
              患者 {snap.patientId} · 就诊 {snap.encounterId} · 状态 {snap.status} · 质量{" "}
              {snap.qualityStatus}
              {snap.createdAt ? ` · 创建 ${snap.createdAt}` : ""}
            </div>
          </div>
        </div>
      </Card>
    ));
  };

  // 精美渲染状态 Badge 六态
  const renderStatusBadge = (status: EvaluationIndicatorStatus) => {
    switch (status) {
      case "DRAFT":
        return <Tag color="default">草稿</Tag>;
      case "PENDING_REVIEW":
        return <Tag color="warning">待审核</Tag>;
      case "PUBLISHED":
        return <Tag color="processing">已发布</Tag>;
      case "ACTIVE":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-500">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            生效中
          </span>
        );
      case "OFFLINE":
        return <Tag color="error">已下线</Tag>;
      case "ARCHIVED":
        return <Tag color="default">已归档</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const columns = [
    {
      title: "指标编码",
      dataIndex: "indicatorCode",
      key: "indicatorCode",
      className: "font-semibold text-slate-700",
    },
    {
      title: "指标名称",
      dataIndex: "name",
      key: "name",
      className: "font-medium text-slate-800",
    },
    {
      title: "版本号",
      dataIndex: "versionNo",
      key: "versionNo",
      render: (v: number) => `v${v}.0`,
    },
    {
      title: "评估主体",
      dataIndex: "subjectType",
      key: "subjectType",
      render: (type: string) => {
        const mapping: Record<string, string> = {
          PATIENT: "患者主体",
          MEDICAL_RECORD: "临床病历",
          DEPARTMENT: "科室质控",
          DOCTOR: "医师效能",
          DISEASE: "专病包",
          PATHWAY: "临床路径",
          CLAIM: "医保合规",
          FOLLOWUP: "随访结果",
        };
        return mapping[type] || type;
      },
    },
    {
      title: "考核科室",
      dataIndex: "responsibleDepartmentId",
      key: "responsibleDepartmentId",
      render: (dept: string) => (
        <Tag className="border-slate-200 bg-slate-50 text-slate-600">{dept}</Tag>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: EvaluationIndicatorStatus) => renderStatusBadge(status),
    },
    {
      title: "操作",
      key: "action",
      render: (_: unknown, record: EvaluationIndicator) => (
        <Space size="middle">
          <Button
            type="link"
            className="p-0 text-sky-600 hover:text-sky-700"
            onClick={() => {
              setSelectedIndicator(record);
              setIsDetailOpen(true);
            }}
          >
            详情与生命周期
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <PageShell
      title="评估指标库"
      description="配置并管理医院临床医疗质量、安全防线及医保控费评估指标，支持口径版本化控制与质控扫描试运行"
    >
      <div className="space-y-6">
        {/* 顶部高级过滤及动作栏 */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 rounded-2xl bg-white border border-slate-100 shadow-sm">
          <div className="flex flex-wrap items-center gap-3">
            <Input
              placeholder="搜索指标编码..."
              prefix={<SearchOutlined className="text-slate-400" />}
              className="w-56 rounded-lg"
              value={searchCode}
              onChange={(e) => setSearchCode(e.target.value)}
              onPressEnter={() => refetch()}
            />
            <Select
              placeholder="指标状态"
              allowClear
              className="w-36"
              onChange={(v) => setFilterStatus(v)}
            >
              <Option value="DRAFT">草稿</Option>
              <Option value="PENDING_REVIEW">待审核</Option>
              <Option value="PUBLISHED">已发布</Option>
              <Option value="ACTIVE">生效中</Option>
              <Option value="OFFLINE">已下线</Option>
            </Select>
            <Select
              placeholder="评估主体"
              allowClear
              className="w-36"
              onChange={(v) => setFilterSubject(v)}
            >
              <Option value="PATIENT">患者主体</Option>
              <Option value="MEDICAL_RECORD">临床病历</Option>
              <Option value="PATHWAY">临床路径</Option>
              <Option value="CLAIM">医保合规</Option>
            </Select>
            <Button
              type="primary"
              className="bg-sky-600 hover:bg-sky-700 rounded-lg"
              onClick={() => refetch()}
            >
              过滤查询
            </Button>
          </div>
          <div className="flex items-center gap-3">
            <Button
              icon={<PlayCircleOutlined />}
              className="border-amber-200 hover:border-amber-300 text-amber-600 hover:text-amber-700 rounded-lg"
              onClick={() => {
                setScanResult(null);
                setIsSandboxOpen(true);
              }}
            >
              质控扫描试运行
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              className="bg-emerald-600 hover:bg-emerald-700 border-none rounded-lg"
              onClick={() => setIsCreateModalOpen(true)}
            >
              新增指标配置
            </Button>
          </div>
        </div>

        {/* 表格台账 */}
        <div className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
          <Table
            dataSource={pageData?.items || []}
            columns={columns}
            rowKey={(r) => r.indicatorId}
            loading={isLoading}
            pagination={{
              total: pageData?.total || 0,
              pageSize: 10,
              showSizeChanger: false,
            }}
          />
        </div>
      </div>

      {/* 侧拉抽屉：指标详情与状态流转控制 */}
      <Drawer
        title={
          <div className="flex items-center gap-3">
            <SlidersOutlined className="text-sky-600" />
            <span className="font-semibold text-lg">指标详情与生命周期流转</span>
          </div>
        }
        placement="right"
        width={650}
        onClose={() => setIsDetailOpen(false)}
        open={isDetailOpen}
        className="rounded-l-2xl"
      >
        {selectedIndicator ? (
          <div className="space-y-6">
            {/* 顶端快捷状态与主要生命周期流转动作 */}
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between">
              <div>
                <div className="text-xs text-slate-400">当前口径状态</div>
                <div className="mt-1">{renderStatusBadge(selectedIndicator.status)}</div>
              </div>
              <Space>
                {selectedIndicator.status === "DRAFT" && (
                  <Button
                    type="primary"
                    className="bg-amber-500 hover:bg-amber-600 border-none rounded-lg"
                    onClick={() => handleSubmit(selectedIndicator.indicatorId)}
                  >
                    提交审核
                  </Button>
                )}
                {selectedIndicator.status === "PENDING_REVIEW" && (
                  <Button
                    type="primary"
                    className="bg-sky-500 hover:bg-sky-600 border-none rounded-lg"
                    onClick={() => handlePublish(selectedIndicator.indicatorId)}
                  >
                    审核发布通过
                  </Button>
                )}
                {selectedIndicator.status === "PUBLISHED" && (
                  <Button
                    type="primary"
                    className="bg-emerald-600 hover:bg-emerald-700 border-none rounded-lg"
                    onClick={() => handleActivate(selectedIndicator.indicatorId)}
                  >
                    激活指标生效
                  </Button>
                )}
              </Space>
            </div>

            {/* 指标基本信息详情 */}
            <Descriptions title="指标元数据" bordered column={1} className="bg-white">
              <Descriptions.Item label="指标编码">
                {selectedIndicator.indicatorCode}
              </Descriptions.Item>
              <Descriptions.Item label="指标名称">{selectedIndicator.name}</Descriptions.Item>
              <Descriptions.Item label="口径版本号">{`v${selectedIndicator.versionNo}.0`}</Descriptions.Item>
              <Descriptions.Item label="评估主体">
                {selectedIndicator.subjectType}
              </Descriptions.Item>
              <Descriptions.Item label="评估考核频次/时间窗">
                {selectedIndicator.timeWindow}
              </Descriptions.Item>
              <Descriptions.Item label="责任科室 / 指标考核域">
                {selectedIndicator.responsibleDepartmentId}
              </Descriptions.Item>
              <Descriptions.Item label="文献/证据来源引用">
                <span className="text-xs text-slate-500">{selectedIndicator.sourceRef}</span>
              </Descriptions.Item>
            </Descriptions>

            {/* 指标三大核心规则表达式（分母/排除/分子） */}
            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-slate-800 border-l-2 border-sky-600 pl-2">
                质控审计规则表达式 (DQL/DSL)
              </h3>

              <div className="p-4 rounded-xl border border-slate-100 bg-emerald-500/5">
                <div className="text-xs font-semibold text-emerald-600 mb-2">
                  分母入组判定条件 (Denominator Definition)
                </div>
                <div className="text-xs font-normal text-emerald-800 bg-white p-3 rounded-lg border border-emerald-100 overflow-x-auto">
                  {selectedIndicator.denominatorDefinition
                    ? JSON.stringify(JSON.parse(selectedIndicator.denominatorDefinition), null, 2)
                    : "暂无配置"}
                </div>
              </div>

              <div className="p-4 rounded-xl border border-slate-100 bg-amber-500/5">
                <div className="text-xs font-semibold text-amber-600 mb-2">
                  排除特定患者判定条件 (Exclusion Definition)
                </div>
                <div className="text-xs font-normal text-amber-800 bg-white p-3 rounded-lg border border-amber-100 overflow-x-auto">
                  {selectedIndicator.exclusionDefinition
                    ? JSON.stringify(JSON.parse(selectedIndicator.exclusionDefinition), null, 2)
                    : "暂无排除条件配置"}
                </div>
              </div>

              <div className="p-4 rounded-xl border border-slate-100 bg-rose-500/5">
                <div className="text-xs font-semibold text-rose-600 mb-2">
                  分子达标规范判定条件 (Numerator Definition)
                </div>
                <div className="text-xs font-normal text-rose-800 bg-white p-3 rounded-lg border border-rose-100 overflow-x-auto">
                  {selectedIndicator.numeratorDefinition
                    ? JSON.stringify(JSON.parse(selectedIndicator.numeratorDefinition), null, 2)
                    : "暂无配置"}
                </div>
              </div>

              <div className="p-4 rounded-xl border border-slate-100 bg-slate-50">
                <div className="text-xs font-semibold text-slate-600 mb-2">
                  缺陷严重度与扣分配置 (Scoring Definition)
                </div>
                <div className="text-xs font-normal text-slate-700 bg-white p-3 rounded-lg border border-slate-200">
                  {selectedIndicator.scoringDefinition || "默认扣 100 分，不达标生成 P1 缺陷"}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <Empty description="选择指标查看详情" />
        )}
      </Drawer>

      {/* 新增指标配置对话框 Modal */}
      <Modal
        title={
          <div className="flex items-center gap-2 border-b border-slate-100 pb-3 font-semibold text-lg text-slate-800">
            <PlusOutlined className="text-emerald-600" />
            <span>配置创建新质控指标 (Draft)</span>
          </div>
        }
        open={isCreateModalOpen}
        onCancel={() => setIsCreateModalOpen(false)}
        onOk={() => form.submit()}
        width={750}
        okText="创建指标草稿"
        cancelText="取消"
        className="rounded-2xl"
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          className="mt-4 max-h-[500px] overflow-y-auto px-1 space-y-4"
          initialValues={{
            versionNo: 1,
            subjectType: "MEDICAL_RECORD",
            timeWindow: "DISCHARGE+24H",
            organizationScope: "全院",
            denominatorDefinition:
              '{"fact":"encounters.0.admissionType","operator":"equals","value":"SURGICAL"}',
            numeratorDefinition: '{"fact":"conditions.0.code","operator":"exists","value":""}',
          }}
        >
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Form.Item
              name="indicatorCode"
              label="指标代码 (Indicator Code)"
              rules={[{ required: true, message: "请输入指标编码，例如：IND.VTE.SURGERY" }]}
            >
              <Input placeholder="输入指标大类编码" />
            </Form.Item>
            <Form.Item
              name="versionNo"
              label="大版本号"
              rules={[{ required: true, message: "请输入版本号" }]}
            >
              <Input type="number" placeholder="默认 1" />
            </Form.Item>
          </div>

          <Form.Item
            name="name"
            label="指标口径展示名称"
            rules={[{ required: true, message: "请输入指标口径展示名称" }]}
          >
            <Input placeholder="例如：外科手术患者静脉血栓风险评估率" />
          </Form.Item>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Form.Item name="subjectType" label="评估对象主体">
              <Select>
                <Option value="PATIENT">患者主体</Option>
                <Option value="MEDICAL_RECORD">临床病历</Option>
                <Option value="CLAIM">医保病案</Option>
              </Select>
            </Form.Item>
            <Form.Item
              name="responsibleDepartmentId"
              label="考核质控核心科室"
              rules={[{ required: true, message: "请输入主要责任科室" }]}
            >
              <Input placeholder="例如：医务处、骨科" />
            </Form.Item>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Form.Item name="timeWindow" label="核算时间窗">
              <Input placeholder="默认 DISCHARGE+24H (出院24小时内)" />
            </Form.Item>
            <Form.Item name="organizationScope" label="核算机构范围">
              <Input placeholder="默认 全院" />
            </Form.Item>
          </div>

          <Form.Item
            name="sourceRef"
            label="临床指南与学术文献来源解释引用"
            rules={[{ required: true, message: "请输入文献来源引用" }]}
          >
            <Input placeholder="例如：《下肢深静脉血栓形成筛查与预防指南2025版》第14条" />
          </Form.Item>

          <Form.Item
            name="denominatorDefinition"
            label="分母入组判定条件 (JSON DSL)"
            rules={[{ required: true, message: "请输入有效的分母判定条件" }]}
          >
            <Input.TextArea rows={2} placeholder="分母入组 JSON..." />
          </Form.Item>

          <Form.Item name="exclusionDefinition" label="排除特定患者判定条件 (JSON DSL)">
            <Input.TextArea rows={2} placeholder="病例排除条件 JSON (可选)..." />
          </Form.Item>

          <Form.Item
            name="numeratorDefinition"
            label="分子规范达标判定条件 (JSON DSL)"
            rules={[{ required: true, message: "请输入有效的分子达标判定条件" }]}
          >
            <Input.TextArea rows={2} placeholder="分子达标 JSON..." />
          </Form.Item>

          <Form.Item name="scoringDefinition" label="缺陷严重度与惩罚配置">
            <Input placeholder="例如：P1:扣除100分，P0:红线指标阻断并产生限期整改" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 侧拉抽屉：指标质控扫描试运行 */}
      <Drawer
        title={
          <div className="flex items-center gap-3">
            <PlayCircleOutlined className="text-amber-500" />
            <span className="font-semibold text-lg">质控扫描试运行</span>
          </div>
        }
        placement="right"
        width={750}
        onClose={() => setIsSandboxOpen(false)}
        open={isSandboxOpen}
        className="rounded-l-2xl"
      >
        <div className="space-y-6">
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 space-y-4">
            <h3 className="text-sm font-semibold text-slate-800">第一步：选择真实就诊临床快照</h3>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <div className="text-xs text-slate-400 mb-1.5">患者 ID</div>
                <Input
                  value={snapshotPatientId}
                  onChange={(e) => setSnapshotPatientId(e.target.value)}
                  placeholder="按患者读取真实快照"
                  className="rounded-lg"
                />
              </div>
              <div>
                <div className="text-xs text-slate-400 mb-1.5">就诊 ID</div>
                <Input
                  value={snapshotEncounterId}
                  onChange={(e) => setSnapshotEncounterId(e.target.value)}
                  placeholder="按就诊读取真实快照"
                  className="rounded-lg"
                />
              </div>
            </div>

            <div className="space-y-2.5">{renderSnapshotChoices()}</div>

            <div className="grid grid-cols-2 gap-3 mt-3">
              <div>
                <div className="text-xs text-slate-400 mb-1.5">临床快照 ID</div>
                <Input
                  value={selectedSnapshotId}
                  onChange={(e) => setSelectedSnapshotId(e.target.value)}
                  placeholder="输入已存在的快照 ID"
                  className="rounded-lg"
                />
              </div>
              <div>
                <div className="text-xs text-slate-400 mb-1.5">就诊触发点</div>
                <Input
                  value={scenarioCode}
                  onChange={(e) => setScenarioCode(e.target.value)}
                  placeholder="默认 DISCHARGE"
                  className="rounded-lg"
                />
              </div>
            </div>

            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              className="w-full bg-amber-500 hover:bg-amber-600 border-none rounded-lg h-10 font-medium"
              loading={scanMutation.isPending}
              disabled={!selectedSnapshotId.trim()}
              onClick={handleRunScan}
            >
              执行自动评估质控扫描
            </Button>
          </div>

          {/* 结果求值呈现 */}
          {scanResult && (
            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-slate-800 pl-2 border-l-2 border-amber-500 flex items-center gap-1.5">
                扫描求值输出事实结论 (Audit Calc Outcomes)
                <span className="text-[11px] font-normal text-slate-400 font-normal">
                  TraceId: {scanResult.traceId}
                </span>
              </h3>

              {/* 结果大卡 */}
              <div className="p-4 rounded-xl border border-slate-100 shadow-sm flex items-center justify-between">
                <div className="space-y-1">
                  <div className="text-slate-400 text-xs">评估运行事实 ID</div>
                  <div className="font-normal font-semibold text-slate-800 text-sm">
                    {scanResult.runId}
                  </div>
                </div>
                <div className="flex items-center gap-6">
                  <div className="text-center">
                    <div className="text-slate-400 text-xs">考核结果条数</div>
                    <div className="font-bold text-slate-800 text-lg">{scanResult.resultCount}</div>
                  </div>
                  <div className="text-center">
                    <div className="text-slate-400 text-xs">质控缺陷数</div>
                    <div className="font-bold text-rose-500 text-lg flex items-center gap-1">
                      {scanResult.findingCount > 0 ? (
                        <>
                          <WarningOutlined />
                          {scanResult.findingCount}
                        </>
                      ) : (
                        "0"
                      )}
                    </div>
                  </div>
                  <div className="text-center">
                    <div className="text-slate-400 text-xs">自动派单任务</div>
                    <div className="font-bold text-slate-800 text-lg">{scanResult.taskCount}</div>
                  </div>
                </div>
              </div>

              {/* 缺陷警告 */}
              {scanResult.findingCount > 0 ? (
                <div className="p-4 rounded-xl border border-rose-100 bg-rose-500/5 flex items-start gap-3">
                  <WarningOutlined className="text-rose-500 text-lg mt-0.5" />
                  <div>
                    <div className="font-semibold text-rose-800 text-xs">
                      质量规范缺陷警报已触发
                    </div>
                    <div className="text-rose-600 text-[11px] mt-1 leading-relaxed">
                      经评估质控引擎扫描，当前就诊存在【{scanResult.findingCount}
                      项】未达标分子标准的重要质控问题；满足派单条件的项目已生成【
                      {scanResult.taskCount}】个整改任务。
                    </div>
                  </div>
                </div>
              ) : (
                <div className="p-4 rounded-xl border border-emerald-100 bg-emerald-500/5 flex items-start gap-3">
                  <CheckCircleOutlined className="text-emerald-500 text-lg mt-0.5" />
                  <div>
                    <div className="font-semibold text-emerald-800 text-xs">质量质控完全合格</div>
                    <div className="text-emerald-600 text-[11px] mt-1 leading-relaxed">
                      经评估质控引擎扫描，该患者就诊完全契合当前租户生效口径的医疗安全防线标准，质量评估得分
                      100 分。
                    </div>
                  </div>
                </div>
              )}

              {/* 运行追踪 */}
              <div className="grid grid-cols-2 gap-3">
                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <div className="text-slate-400 text-xs">运行状态</div>
                  <div className="mt-1 text-sm font-semibold text-slate-800">
                    {scanResult.status}
                  </div>
                </div>
                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <div className="text-slate-400 text-xs">追踪号</div>
                  <div className="mt-1 text-sm font-semibold text-slate-800 break-all">
                    {scanResult.traceId}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </Drawer>
    </PageShell>
  );
}
