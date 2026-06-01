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
  PlayCircleOutlined,
  FolderOpenOutlined,
  ApartmentOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useSpecialtyPackages,
  useCreateSpecialtyPackage,
  usePathwayTemplates,
  usePathwayTemplateDetail,
  useCreatePathwayTemplate,
  usePublishPathwayTemplate,
  useSimulatePathway,
} from "@/shared/api/hooks";
import type {
  PathwayEdgeType,
  PathwayNodeType,
  PathwayTemplate,
  PathwayTemplateStatus,
  SpecialtyPackage,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";

const { TextArea } = Input;
const { Option } = Select;

const DEFAULT_NODES_JSON = `[
  { "nodeCode": "START", "name": "准入评估", "nodeType": "START", "sortOrder": 1, "responsibleRole": "PRIMARY_NURSE", "timeWindowMinutes": 30, "terminalFlag": false },
  { "nodeCode": "PLAN", "name": "方案确认", "nodeType": "PROCESS", "sortOrder": 2, "responsibleRole": "ATTENDING_PHYSICIAN", "timeWindowMinutes": 120, "terminalFlag": false },
  { "nodeCode": "REVIEW", "name": "阶段复核", "nodeType": "BRANCH", "sortOrder": 3, "responsibleRole": "ATTENDING_PHYSICIAN", "timeWindowMinutes": 1440, "terminalFlag": false },
  { "nodeCode": "EXIT", "name": "出径交接", "nodeType": "STOP", "sortOrder": 4, "responsibleRole": "PRIMARY_NURSE", "timeWindowMinutes": 180, "terminalFlag": true }
]`;

const DEFAULT_EDGES_JSON = `[
  { "edgeCode": "E1", "fromNodeCode": "START", "toNodeCode": "PLAN", "edgeType": "STANDARD", "priority": 1 },
  { "edgeCode": "E2", "fromNodeCode": "PLAN", "toNodeCode": "REVIEW", "edgeType": "STANDARD", "priority": 1 },
  { "edgeCode": "E3", "fromNodeCode": "REVIEW", "toNodeCode": "EXIT", "edgeType": "CONDITIONAL", "conditionJson": "{\\"fact\\": \\"context.readyForExit\\", \\"operator\\": \\"equals\\", \\"value\\": true}", "priority": 1 },
  { "edgeCode": "E4", "fromNodeCode": "REVIEW", "toNodeCode": "PLAN", "edgeType": "VARIANCE", "conditionJson": "{\\"fact\\": \\"context.needReplan\\", \\"operator\\": \\"equals\\", \\"value\\": true}", "priority": 2 }
]`;

type PathwayBadgeStatus = Exclude<BadgeProps["status"], undefined>;
type PathwayNodeDraft = {
  nodeCode: string;
  name: string;
  nodeType: PathwayNodeType;
  sortOrder: number;
  responsibleRole?: string;
  timeWindowMinutes?: number;
  terminalFlag: boolean;
  configJson?: string;
};
type PathwayEdgeDraft = {
  edgeCode: string;
  fromNodeCode: string;
  toNodeCode: string;
  edgeType: PathwayEdgeType;
  conditionJson?: string;
  priority: number;
};

function parseJsonInput(value: string, errorMessage: string) {
  const normalized = value.trim();
  if (!normalized) {
    message.error(errorMessage);
    return null;
  }
  try {
    return JSON.parse(normalized) as unknown;
  } catch {
    message.error("JSON 格式不合法，请检查后再提交。");
    return null;
  }
}

export default function PathwayTemplates() {
  const [page, setPage] = useState<number>(1);
  const [size] = useState<number>(10);

  const [statusFilter, setStatusFilter] = useState<PathwayTemplateStatus | undefined>(undefined);
  const [diseaseFilter, setDiseaseFilter] = useState<string>("");
  const [packageFilter, setPackageFilter] = useState<string>("");

  const [packageDrawerVisible, setPackageDrawerVisible] = useState<boolean>(false);
  const [createTemplateVisible, setCreateTemplateVisible] = useState<boolean>(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);

  const [simulateStartNode, setSimulateStartNode] = useState<string>("START");
  const [simulateContextJson, setSimulateContextJson] = useState<string>("");
  const [simulateResult, setSimulateResult] = useState<string[] | null>(null);

  const {
    data: listData,
    isLoading: listLoading,
    refetch: refetchList,
  } = usePathwayTemplates({
    status: statusFilter,
    diseaseCode: diseaseFilter || undefined,
    packageId: packageFilter || undefined,
    page,
    size,
  });

  const {
    data: detailData,
    isLoading: detailLoading,
    refetch: refetchDetail,
  } = usePathwayTemplateDetail(selectedTemplateId || "");

  const { data: packagesData, refetch: refetchPackages } = useSpecialtyPackages({
    page: 1,
    size: 100,
  });

  const createPackageMutation = useCreateSpecialtyPackage();
  const createTemplateMutation = useCreatePathwayTemplate();
  const publishTemplateMutation = usePublishPathwayTemplate();
  const simulateMutation = useSimulatePathway(selectedTemplateId || "");

  const [packageForm] = Form.useForm();
  const [templateForm] = Form.useForm();

  const handleCreatePackage = async () => {
    try {
      const values = await packageForm.validateFields();
      await createPackageMutation.mutateAsync(values);
      message.success("专病包资产草稿创建成功");
      packageForm.resetFields();
      refetchPackages();
    } catch (error: unknown) {
      if (applyApiFieldErrors(packageForm, error)) return;
      message.error(getApiErrorMessage(error, "创建专病包失败，请检查参数"));
    }
  };

  const handleCreateTemplate = async () => {
    try {
      const values = await templateForm.validateFields();
      const parsedNodes = parseJsonInput(values.nodesJson, "请输入生命周期节点配置 JSON");
      const parsedEdges = parseJsonInput(values.edgesJson, "请输入拓扑流转连线配置 JSON");
      if (!parsedNodes || !parsedEdges) return;

      await createTemplateMutation.mutateAsync({
        packageId: values.packageId,
        templateCode: values.templateCode,
        name: values.name,
        diseaseCode: values.diseaseCode,
        templateLevel: values.templateLevel,
        sourceRef: values.sourceRef,
        description: values.description,
        entryCriteriaJson: "{}",
        exitCriteriaJson: "{}",
        nodes: parsedNodes as PathwayNodeDraft[],
        edges: parsedEdges as PathwayEdgeDraft[],
      });

      message.success("专病路径模板草稿创建成功");
      setCreateTemplateVisible(false);
      templateForm.resetFields();
      refetchList();
    } catch (error: unknown) {
      if (applyApiFieldErrors(templateForm, error)) return;
      message.error(getApiErrorMessage(error, "创建路径模板失败"));
    }
  };

  const handlePublishTemplate = async () => {
    if (!selectedTemplateId) return;
    try {
      await publishTemplateMutation.mutateAsync(selectedTemplateId);
      message.success("路径模板发布成功，已正式上线运行！");
      refetchDetail();
      refetchList();
    } catch (error: unknown) {
      Modal.error({
        title: "路径发布门禁拒绝",
        content: getApiErrorMessage(error, "未通过路径闭环或时窗门禁核查，禁止上线。"),
      });
    }
  };

  const handleSimulate = async () => {
    if (!selectedTemplateId) return;
    try {
      const contextJson = simulateContextJson.trim();
      if (!contextJson) {
        message.error("请先粘贴真实脱敏路径上下文快照 JSON");
        return;
      }
      if (!parseJsonInput(contextJson, "请先粘贴真实脱敏路径上下文快照 JSON")) return;

      const result = await simulateMutation.mutateAsync({
        startNodeCode: simulateStartNode,
        contextJson,
      });
      setSimulateResult(result.simulatedPath || []);
      message.success("路径轨迹试运行成功");
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "路径试运行失败"));
    }
  };

  const columns: TableProps<PathwayTemplate>["columns"] = [
    {
      title: "模板代码",
      dataIndex: "templateCode",
      key: "templateCode",
      render: (text: string) => <Tag color="geekblue">{text}</Tag>,
    },
    {
      title: "路径名称",
      dataIndex: "name",
      key: "name",
      className: "font-semibold text-gray-800",
    },
    {
      title: "关联病种",
      dataIndex: "diseaseCode",
      key: "diseaseCode",
      render: (text: string) => <Tag color="cyan">{text}</Tag>,
    },
    {
      title: "层级",
      dataIndex: "templateLevel",
      key: "templateLevel",
    },
    {
      title: "版本",
      dataIndex: "templateVersion",
      key: "templateVersion",
      render: (v: number) => `v${v}.0`,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: PathwayTemplateStatus) => {
        const config: Record<PathwayTemplateStatus, { status: PathwayBadgeStatus; text: string }> =
          {
            DRAFT: { status: "warning", text: "设计中(DRAFT)" },
            PUBLISHED: { status: "success", text: "运行中(PUBLISHED)" },
            OFFLINE: { status: "default", text: "已下线(OFFLINE)" },
          };
        return <Badge status={config[status].status} text={config[status].text} />;
      },
    },
    {
      title: "管理动作",
      key: "action",
      render: (record: PathwayTemplate) => (
        <Button
          type="link"
          icon={<ApartmentOutlined />}
          onClick={() => {
            setSelectedTemplateId(record.templateId);
            setSimulateResult(null);
          }}
          className="text-emerald-600 hover:text-emerald-900 font-semibold"
        >
          设计与试运行
        </Button>
      ),
    },
  ];

  return (
    <PageShell
      title="路径中枢"
      description="配置并维护专病临床路径标准，设定生命周期节点与变异流转边拓扑，提供真实快照试运行与时窗门禁发布验证。"
    >
      {/* 筛选过滤条 */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 mb-6">
        <Form layout="inline" className="flex flex-wrap gap-4 items-center w-full">
          <Form.Item label="状态">
            <Select
              placeholder="选择状态"
              allowClear
              value={statusFilter}
              onChange={setStatusFilter}
              className="w-[140px]"
            >
              <Option value="DRAFT">设计中</Option>
              <Option value="PUBLISHED">运行中</Option>
              <Option value="OFFLINE">已下线</Option>
            </Select>
          </Form.Item>
          <Form.Item label="病种编码">
            <Input
              placeholder="输入真实病种编码"
              allowClear
              value={diseaseFilter}
              onChange={(e) => setDiseaseFilter(e.target.value)}
              className="w-[140px]"
            />
          </Form.Item>
          <Form.Item label="归属专病包">
            <Select
              placeholder="全部专病包"
              allowClear
              value={packageFilter}
              onChange={setPackageFilter}
              className="w-[200px]"
            >
              {packagesData?.items?.map((pkg: SpecialtyPackage) => (
                <Option key={pkg.packageId} value={pkg.packageId}>
                  {pkg.name} ({pkg.packageVersion})
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item className="ml-auto flex gap-2">
            <Button
              icon={<FolderOpenOutlined />}
              onClick={() => setPackageDrawerVisible(true)}
              className="rounded-lg font-medium border-emerald-500 text-emerald-600 hover:bg-emerald-50"
            >
              管理专病包
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                templateForm.setFieldsValue({
                  templateLevel: "CLINICAL",
                  nodesJson: DEFAULT_NODES_JSON,
                  edgesJson: DEFAULT_EDGES_JSON,
                });
                setCreateTemplateVisible(true);
              }}
              className="rounded-lg font-medium bg-emerald-600 border-emerald-600 hover:bg-emerald-700"
            >
              新建路径模板
            </Button>
          </Form.Item>
        </Form>
      </div>

      {/* 主数据台账列表 */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
        <Table
          columns={columns}
          dataSource={listData?.items || []}
          rowKey="id"
          loading={listLoading}
          pagination={{
            current: page,
            pageSize: size,
            total: listData?.total || 0,
            onChange: (p) => setPage(p),
            showTotal: (t) => `共 ${t} 个临床受控路径模型`,
          }}
          className="medkernel-table"
        />
      </div>

      {/* 专病包资产管理 Drawer */}
      <Drawer
        title="租户专病包资产管理"
        width={560}
        onClose={() => setPackageDrawerVisible(false)}
        open={packageDrawerVisible}
        destroyOnClose
      >
        <Alert
          message="专病包是临床路径和质控资产的容器实体，受租户级别物理强隔离与版本升级灰度发布控制。"
          type="info"
          showIcon
          className="mb-6 rounded-lg"
        />

        <Card title="新建专病包草稿" className="mb-6 border-gray-200 shadow-sm rounded-xl">
          <Form form={packageForm} layout="vertical" onFinish={handleCreatePackage}>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item name="packageCode" label="专病包编码" rules={[{ required: true }]}>
                  <Input placeholder="输入专病包编码" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="diseaseCode" label="病种代码 (ICD)" rules={[{ required: true }]}>
                  <Input placeholder="输入真实病种代码" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="name" label="专病包名称" rules={[{ required: true }]}>
              <Input placeholder="输入专病包名称" />
            </Form.Item>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item name="packageVersion" label="版本" rules={[{ required: true }]}>
                  <Input placeholder="输入版本号" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="sourceRef" label="知识来源" rules={[{ required: true }]}>
                  <Input placeholder="输入已审核指南、院内制度或配置包来源" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="description" label="功能说明与收治摘要">
              <TextArea rows={2} placeholder="输入专病画像说明..." />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              icon={<PlusOutlined />}
              loading={createPackageMutation.isPending}
              className="w-full bg-emerald-600 border-emerald-600 hover:bg-emerald-700 mt-2"
            >
              提交创建并留痕审计
            </Button>
          </Form>
        </Card>

        <div className="font-semibold text-gray-800 mb-3">已有专病包列表</div>
        <div className="flex flex-col gap-3 overflow-y-auto max-h-[300px]">
          {packagesData?.items?.map((pkg: SpecialtyPackage) => (
            <Card
              key={pkg.packageId}
              size="small"
              className="border-gray-100 bg-gray-50 rounded-lg shadow-sm"
            >
              <Descriptions size="small" column={1} bordered={false}>
                <Descriptions.Item label="名称">
                  <span className="font-semibold text-gray-800">{pkg.name}</span>
                </Descriptions.Item>
                <Descriptions.Item label="包编码">
                  <span className="font-normal text-xs">{pkg.packageCode}</span>
                </Descriptions.Item>
                <Descriptions.Item label="病种/版本">
                  <Tag color="cyan">{pkg.diseaseCode}</Tag>
                  <Tag color="purple">{pkg.packageVersion}</Tag>
                </Descriptions.Item>
              </Descriptions>
            </Card>
          ))}
        </div>
      </Drawer>

      {/* 新建路径模板 Modal */}
      <Modal
        title="新建路径模板模型"
        open={createTemplateVisible}
        onOk={handleCreateTemplate}
        onCancel={() => setCreateTemplateVisible(false)}
        width={780}
        confirmLoading={createTemplateMutation.isPending}
        destroyOnClose
      >
        <Form form={templateForm} layout="vertical" className="mt-4">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="packageId" label="归属专病包" rules={[{ required: true }]}>
                <Select placeholder="选择包">
                  {packagesData?.items?.map((pkg: SpecialtyPackage) => (
                    <Option key={pkg.packageId} value={pkg.packageId}>
                      {pkg.name} (v{pkg.packageVersion})
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="路径模型名称" rules={[{ required: true }]}>
                <Input placeholder="输入路径模型名称" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="templateCode" label="路径模型代码" rules={[{ required: true }]}>
                <Input placeholder="输入路径模型代码" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="diseaseCode" label="病种代码" rules={[{ required: true }]}>
                <Input placeholder="输入真实病种编码" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="templateLevel" label="路径层级" rules={[{ required: true }]}>
                <Select>
                  <Option value="CLINICAL">CLINICAL (临床规范级)</Option>
                  <Option value="BUSINESS">BUSINESS (业务质控级)</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="sourceRef" label="临床知识与指南基础" rules={[{ required: true }]}>
            <Input placeholder="输入已审核指南、院内制度或配置包来源" />
          </Form.Item>
          <Form.Item name="description" label="收治标准与排除指标">
            <TextArea rows={2} placeholder="输入路径说明..." />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="nodesJson"
                label="生命周期节点配置 (JSON 列表)"
                rules={[{ required: true }]}
              >
                <TextArea rows={8} className="font-normal text-xs" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="edgesJson"
                label="拓扑流转连线配置 (JSON 列表)"
                rules={[{ required: true }]}
              >
                <TextArea rows={8} className="font-normal text-xs" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* 路径详情配置与真实快照试运行 Drawer */}
      <Drawer
        title={
          <div className="flex items-center justify-between w-full">
            <span>路径配置与真实快照试运行控制台</span>
            {detailData?.template.status === "DRAFT" && (
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={handlePublishTemplate}
                loading={publishTemplateMutation.isPending}
                className="mr-6 bg-emerald-600 border-emerald-600 hover:bg-emerald-700"
              >
                校验并申请发布上线 (门禁校验)
              </Button>
            )}
          </div>
        }
        width={960}
        onClose={() => {
          setSelectedTemplateId(null);
          setSimulateResult(null);
        }}
        open={!!selectedTemplateId}
        loading={detailLoading}
        destroyOnClose
      >
        {detailData && (
          <div>
            <Alert
              message={
                detailData.template.status === "PUBLISHED"
                  ? "当前临床路径处于已上线（PUBLISHED）状态，为保障临床运行安全，拓扑结构已被写保护锁定。如需修改，请发布新版本包升级。"
                  : "当前临床路径处于设计中（DRAFT）状态，您可以预览拓扑，并使用真实脱敏上下文快照试运行后申请发布。"
              }
              type={detailData.template.status === "PUBLISHED" ? "success" : "info"}
              showIcon
              className="mb-6 rounded-lg"
            />

            <Descriptions title="路径主数据事实" bordered column={2} className="mb-6">
              <Descriptions.Item label="名称">{detailData.template.name}</Descriptions.Item>
              <Descriptions.Item label="代码编码">
                {detailData.template.templateCode}
              </Descriptions.Item>
              <Descriptions.Item label="相关病种">
                {detailData.template.diseaseCode}
              </Descriptions.Item>
              <Descriptions.Item label="发布版本">
                v{detailData.template.templateVersion}.0 版
              </Descriptions.Item>
              <Descriptions.Item label="层级定位">
                {detailData.template.templateLevel}
              </Descriptions.Item>
              <Descriptions.Item label="发布状态">
                <Badge
                  status={detailData.template.status === "PUBLISHED" ? "success" : "warning"}
                  text={detailData.template.status}
                />
              </Descriptions.Item>
              <Descriptions.Item label="学术指南基础" span={2}>
                {detailData.template.sourceRef}
              </Descriptions.Item>
            </Descriptions>

            <Tabs defaultActiveKey="topology">
              <Tabs.TabPane tab="标准节点 (Nodes)" key="nodes">
                <Table
                  dataSource={detailData.nodes}
                  rowKey="nodeId"
                  pagination={false}
                  size="small"
                  columns={[
                    {
                      title: "节点代码",
                      dataIndex: "nodeCode",
                      render: (c) => <Tag color="blue">{c}</Tag>,
                    },
                    { title: "名称", dataIndex: "name", className: "font-semibold" },
                    {
                      title: "节点类型",
                      dataIndex: "nodeType",
                      render: (t) => <Tag color="purple">{t}</Tag>,
                    },
                    {
                      title: "时窗限制",
                      dataIndex: "timeWindowMinutes",
                      render: (m) => (m ? `${m} 分钟` : "无限制"),
                    },
                    { title: "默认责任角色", dataIndex: "responsibleRole" },
                    {
                      title: "终止节点",
                      dataIndex: "terminalFlag",
                      render: (t) => (t ? "是" : "否"),
                    },
                  ]}
                  className="medkernel-table"
                />
              </Tabs.TabPane>

              <Tabs.TabPane tab="决策边拓扑 (Edges)" key="edges">
                <Table
                  dataSource={detailData.edges}
                  rowKey="edgeId"
                  pagination={false}
                  size="small"
                  columns={[
                    { title: "推进边代码", dataIndex: "edgeCode" },
                    {
                      title: "自源节点",
                      dataIndex: "fromNodeCode",
                      render: (c) => <Tag color="orange">{c}</Tag>,
                    },
                    {
                      title: "至目标节点",
                      dataIndex: "toNodeCode",
                      render: (c) => <Tag color="green">{c}</Tag>,
                    },
                    {
                      title: "流转类型",
                      dataIndex: "edgeType",
                      render: (t) => <Tag color="cyan">{t}</Tag>,
                    },
                    {
                      title: "流转条件 (DSL)",
                      dataIndex: "conditionJson",
                      render: (c) => (
                        <span className="font-normal text-xs">{c || "无条件直接推进"}</span>
                      ),
                    },
                    { title: "优先级", dataIndex: "priority" },
                  ]}
                  className="medkernel-table"
                />
              </Tabs.TabPane>

              <Tabs.TabPane tab="真实快照试运行" key="simulate">
                <Row gutter={16}>
                  <Col span={10}>
                    <Card
                      title="试运行输入设置"
                      size="small"
                      className="border-gray-200 shadow-sm rounded-lg"
                    >
                      <Form layout="vertical">
                        <Form.Item label="仿真流转起点节点">
                          <Select value={simulateStartNode} onChange={setSimulateStartNode}>
                            {detailData.nodes.map((n) => (
                              <Option key={n.nodeCode} value={n.nodeCode}>
                                {n.name} ({n.nodeCode})
                              </Option>
                            ))}
                          </Select>
                        </Form.Item>
                        <Form.Item label="真实脱敏路径上下文快照 JSON">
                          <TextArea
                            rows={7}
                            value={simulateContextJson}
                            onChange={(e) => setSimulateContextJson(e.target.value)}
                            placeholder="粘贴由上下文快照接口返回的脱敏 JSON，不在页面内预置患者或病种。"
                            className="font-normal text-xs"
                          />
                        </Form.Item>
                        <Button
                          type="primary"
                          icon={<PlayCircleOutlined />}
                          onClick={handleSimulate}
                          loading={simulateMutation.isPending}
                          className="w-full bg-emerald-600 border-emerald-600 hover:bg-emerald-700 mt-4"
                        >
                          开始路径试运行
                        </Button>
                      </Form>
                    </Card>
                  </Col>
                  <Col span={14}>
                    <Card
                      title="路径试运行轨迹"
                      size="small"
                      className="border-gray-200 shadow-sm rounded-lg"
                    >
                      {simulateResult ? (
                        <div className="p-4 bg-gray-50 rounded-lg min-h-48 flex flex-col justify-center">
                          <Timeline>
                            {simulateResult.map((nodeCode, idx) => {
                              const nodeDetail = detailData.nodes.find(
                                (n) => n.nodeCode === nodeCode,
                              );
                              return (
                                <Timeline.Item key={idx} color={idx === 0 ? "blue" : "green"}>
                                  <div className="font-semibold text-gray-800 text-xs">
                                    {nodeDetail?.name || "未知节点"}
                                  </div>
                                  <div className="text-gray-400 text-xs font-normal mt-0.5">
                                    {nodeCode}
                                  </div>
                                </Timeline.Item>
                              );
                            })}
                          </Timeline>
                        </div>
                      ) : (
                        <div className="flex flex-col items-center justify-center min-h-48 text-gray-400">
                          <ExclamationCircleOutlined className="text-48px mb-4" />
                          <span>粘贴真实脱敏上下文快照后，点击试运行以计算路径轨迹</span>
                        </div>
                      )}
                    </Card>
                  </Col>
                </Row>
              </Tabs.TabPane>
            </Tabs>
          </div>
        )}
      </Drawer>
    </PageShell>
  );
}
