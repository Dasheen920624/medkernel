import { useMemo, useState } from "react";
import {
  App as AntdApp,
  Alert,
  Badge,
  Button,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { BadgeProps, RadioChangeEvent, TableProps } from "antd";
import {
  ApartmentOutlined,
  BranchesOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CodeOutlined,
  FileSearchOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  PlayCircleOutlined,
  SyncOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useRuleDefinitions,
  useRuleDetail,
  useCreateRule,
  useAddTestCase,
  useSimulateRule,
  usePublishRule,
} from "@/shared/api/hooks";
import type { RuleDefinition, RuleEvaluationItem } from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import {
  RULE_LAYER_TEMPLATES,
  conditionNeedsValue,
  conditionTreeToDsl,
  createExplanationTemplate,
  dslToConditionTree,
  formatRuleJson,
  instantiateRuleTemplate,
  parseRuleJson,
  type RuleCondition,
  type RuleConditionTree,
  type RuleLogic,
  type RuleOperator,
  type RuleSeverity,
  type RuleTemplateKey,
  type RuleValueKind,
} from "@/shared/config/ruleLayeredEditor";

const { TextArea } = Input;
const { Option } = Select;
const { Text } = Typography;

type RuleStatusBadge = Exclude<BadgeProps["status"], undefined>;
type CreateLayerKey = "l1" | "l2" | "l3";

const DEFAULT_TEMPLATE_KEY: RuleTemplateKey = "clinical_quality_monitor";

const RULE_TYPE_LABELS: Record<string, string> = {
  DRUG_SAFETY: "合理用药安全",
  INSURANCE_AUDIT: "医保规范核查",
  CLINICAL_QUALITY: "临床诊疗质控",
};

const RISK_LABELS: Record<RuleSeverity, string> = {
  LOW: "低风险",
  MEDIUM: "中风险",
  HIGH: "高风险",
};

const OPERATOR_LABELS: Record<RuleOperator, string> = {
  exists: "存在",
  equals: "等于",
  not_equals: "不等于",
  contains: "包含",
  gt: "大于",
  gte: "大于等于",
  lt: "小于",
  lte: "小于等于",
  in: "属于集合",
  not_in: "不属于集合",
};

const VALUE_KIND_LABELS: Record<RuleValueKind, string> = {
  empty: "无比较值",
  string: "文本",
  number: "数值",
  boolean: "布尔",
  list: "集合",
};

function parseJsonInput(value: string, errorMessage: string, onError: (message: string) => void) {
  try {
    return parseRuleJson(value);
  } catch {
    onError(errorMessage);
    return null;
  }
}

function parseStoredJson(value?: string | null) {
  if (!value) return null;
  try {
    return JSON.parse(value) as unknown;
  } catch {
    return null;
  }
}

function toStoredConditionTree(value?: string | null) {
  const parsed = parseStoredJson(value);
  if (!parsed) return null;
  try {
    return dslToConditionTree(parsed);
  } catch {
    return null;
  }
}

function hasUnresolvedPlaceholder(tree: RuleConditionTree) {
  return tree.conditions.some(
    (condition) => !condition.fact.trim() || condition.fact.includes("<字段路径>"),
  );
}

function findTemplate(key: RuleTemplateKey) {
  return RULE_LAYER_TEMPLATES.find((item) => item.key === key) ?? RULE_LAYER_TEMPLATES[0];
}

function createDefaultTree() {
  return instantiateRuleTemplate(DEFAULT_TEMPLATE_KEY);
}

function createDefaultDslText() {
  return formatRuleJson(conditionTreeToDsl(createDefaultTree()));
}

function renderRiskTag(level: string) {
  const colors: Record<string, string> = {
    LOW: "green",
    MEDIUM: "orange",
    HIGH: "red",
  };
  return (
    <Tag color={colors[level] ?? "default"}>{RISK_LABELS[level as RuleSeverity] ?? level}</Tag>
  );
}

function renderStatus(status: string) {
  const statusMap: Record<string, { text: string; status: RuleStatusBadge }> = {
    DRAFT: { text: "草稿设计中", status: "warning" },
    PUBLISHED: { text: "已上线运行", status: "success" },
    OFFLINE: { text: "已下线封存", status: "default" },
    ARCHIVED: { text: "已归档历史", status: "default" },
  };
  const config = statusMap[status] || { text: status, status: "processing" };
  return <Badge status={config.status} text={config.text} />;
}

function conditionValueText(condition: RuleCondition) {
  if (!conditionNeedsValue(condition.operator)) return "不需要比较值";
  if (Array.isArray(condition.value)) return condition.value.join("、") || "待填写";
  if (condition.value === undefined || condition.value === null || condition.value === "") {
    return "待填写";
  }
  return String(condition.value);
}

function valueKindForOperator(operator: RuleOperator, currentKind: RuleValueKind): RuleValueKind {
  if (!conditionNeedsValue(operator)) return "empty";
  return currentKind === "empty" ? "string" : currentKind;
}

function valueForOperator(operator: RuleOperator, currentValue: RuleCondition["value"]) {
  if (!conditionNeedsValue(operator)) return undefined;
  return currentValue ?? "";
}

export default function RuleDefinitions() {
  const { message, modal } = AntdApp.useApp();
  const [page, setPage] = useState(1);
  const [size] = useState(10);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [typeFilter, setTypeFilter] = useState<string | undefined>(undefined);
  const [riskFilter, setRiskFilter] = useState<string | undefined>(undefined);
  const [selectedRuleId, setSelectedRuleId] = useState<string | null>(null);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [activeCreateLayer, setActiveCreateLayer] = useState<CreateLayerKey>("l1");
  const [selectedTemplateKey, setSelectedTemplateKey] =
    useState<RuleTemplateKey>(DEFAULT_TEMPLATE_KEY);
  const [conditionTree, setConditionTree] = useState<RuleConditionTree>(createDefaultTree);
  const [dslEditorValue, setDslEditorValue] = useState(createDefaultDslText);
  const [createForm] = Form.useForm();
  const [caseModalVisible, setCaseModalVisible] = useState(false);
  const [caseForm] = Form.useForm();
  const [simulatePayload, setSimulatePayload] = useState<string>("");
  const [simulateResult, setSimulateResult] = useState<RuleEvaluationItem | null>(null);

  const {
    data: listData,
    isLoading: listLoading,
    isError: listIsError,
    error: listError,
    refetch: refetchList,
  } = useRuleDefinitions({
    page,
    size,
    status: statusFilter,
    ruleType: typeFilter,
    riskLevel: riskFilter,
  });

  const {
    data: detailData,
    isLoading: detailLoading,
    refetch: refetchDetail,
  } = useRuleDetail(selectedRuleId || "");
  const selectedRulePackageVersion = detailData?.definition.packageVersion ?? "";
  const detailDsl = useMemo(
    () => parseStoredJson(detailData?.version?.dslJson),
    [detailData?.version?.dslJson],
  );
  const detailTree = useMemo(
    () => toStoredConditionTree(detailData?.version?.dslJson),
    [detailData?.version?.dslJson],
  );
  const detailExplanation = useMemo(
    () => parseStoredJson(detailData?.version?.explanationJson),
    [detailData?.version?.explanationJson],
  );

  const createRuleMutation = useCreateRule();
  const addTestCaseMutation = useAddTestCase(selectedRuleId || "");
  const simulateMutation = useSimulateRule(selectedRuleId || "");
  const publishMutation = usePublishRule();

  const resetLayeredAuthoring = (templateKey: RuleTemplateKey = DEFAULT_TEMPLATE_KEY) => {
    const template = findTemplate(templateKey);
    const nextTree = instantiateRuleTemplate(templateKey);
    setSelectedTemplateKey(templateKey);
    setConditionTree(nextTree);
    setDslEditorValue(formatRuleJson(conditionTreeToDsl(nextTree)));
    setActiveCreateLayer("l1");
    createForm.setFieldsValue({
      ruleType: template.ruleType,
      riskLevel: template.riskLevel,
      changeSummary: "初始化创建草稿版本",
    });
  };

  const openCreateModal = () => {
    createForm.resetFields();
    resetLayeredAuthoring();
    setCreateModalVisible(true);
  };

  const applyTemplate = (templateKey: RuleTemplateKey) => {
    const template = findTemplate(templateKey);
    const nextTree = instantiateRuleTemplate(templateKey);
    setSelectedTemplateKey(templateKey);
    setConditionTree(nextTree);
    setDslEditorValue(formatRuleJson(conditionTreeToDsl(nextTree)));
    createForm.setFieldsValue({
      ruleType: template.ruleType,
      riskLevel: template.riskLevel,
    });
    setActiveCreateLayer("l2");
  };

  const syncTreeToDsl = () => {
    setDslEditorValue(formatRuleJson(conditionTreeToDsl(conditionTree)));
    setActiveCreateLayer("l3");
    message.success("已从 L2 条件树同步到 L3 DSL");
  };

  const syncDslToTree = () => {
    try {
      const parsed = parseRuleJson(dslEditorValue);
      const nextTree = dslToConditionTree(parsed);
      setConditionTree(nextTree);
      setActiveCreateLayer("l2");
      message.success("已从 L3 DSL 回填到 L2 条件树");
    } catch {
      message.error("L3 DSL JSON 不合法，无法回填到条件树。");
    }
  };

  const updateCondition = (index: number, patch: Partial<RuleCondition>) => {
    setConditionTree((current) => ({
      ...current,
      conditions: current.conditions.map((condition, currentIndex) =>
        currentIndex === index ? { ...condition, ...patch } : condition,
      ),
    }));
  };

  const addCondition = () => {
    setConditionTree((current) => ({
      ...current,
      conditions: [
        ...current.conditions,
        {
          id: `condition-${current.conditions.length + 1}`,
          label: `条件 ${current.conditions.length + 1}`,
          fact: "",
          operator: "equals",
          value: "",
          valueKind: "string",
        },
      ],
    }));
  };

  const removeCondition = (index: number) => {
    setConditionTree((current) => {
      if (current.conditions.length <= 1) return current;
      return {
        ...current,
        conditions: current.conditions.filter((_condition, currentIndex) => currentIndex !== index),
      };
    });
  };

  const handleCreateRule = async () => {
    try {
      const values = await createForm.validateFields();
      let parsedDsl: unknown;
      let submitTree: RuleConditionTree;
      try {
        parsedDsl = parseRuleJson(dslEditorValue);
        submitTree = dslToConditionTree(parsedDsl);
      } catch {
        message.error("L3 DSL JSON 不合法，请先从 L2 同步或修正后再提交。");
        setActiveCreateLayer("l3");
        return;
      }
      if (hasUnresolvedPlaceholder(submitTree)) {
        message.error("请在 L2 条件树填写真实上下文字段路径，不能提交模板占位符。");
        setActiveCreateLayer("l2");
        return;
      }

      await createRuleMutation.mutateAsync({
        ruleCode: values.ruleCode,
        name: values.name,
        ruleType: values.ruleType,
        authoringMode: "VISUAL",
        riskLevel: values.riskLevel,
        packageVersion: values.packageVersion,
        sourceRef: values.sourceRef,
        changeSummary: values.changeSummary,
        dslJson: parsedDsl,
        explanationJson: createExplanationTemplate(submitTree),
      });

      message.success("新规则创建成功，状态为草稿");
      setCreateModalVisible(false);
      createForm.resetFields();
      resetLayeredAuthoring();
      refetchList();
    } catch (error: unknown) {
      if (applyApiFieldErrors(createForm, error)) return;
      message.error(getApiErrorMessage(error, "创建规则失败"));
    }
  };

  const handleAddTestCase = async () => {
    try {
      const values = await caseForm.validateFields();
      const inputPayload = parseJsonInput(
        values.inputPayload,
        "请粘贴真实脱敏测试输入载荷 JSON",
        message.error,
      );
      if (!inputPayload) return;

      await addTestCaseMutation.mutateAsync({
        packageVersion: selectedRulePackageVersion,
        caseType: values.caseType,
        inputPayload,
        expectedHit: values.expectedHit,
        expectedSeverity: values.expectedSeverity,
        expectedActionCode: values.expectedActionCode,
      });

      message.success("成功新增测试用例");
      setCaseModalVisible(false);
      caseForm.resetFields();
      refetchDetail();
    } catch (error: unknown) {
      if (applyApiFieldErrors(caseForm, error)) return;
      message.error(getApiErrorMessage(error, "添加用例失败"));
    }
  };

  const handleSimulate = async () => {
    try {
      const inputPayload = parseJsonInput(
        simulatePayload,
        "请先粘贴真实脱敏上下文快照 JSON",
        message.error,
      );
      if (!inputPayload) return;

      const result = await simulateMutation.mutateAsync({
        packageVersion: selectedRulePackageVersion,
        inputPayload,
      });
      setSimulateResult(result);
      message.success("规则试运行成功，已返回求值结果");
      refetchDetail();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "规则试运行失败"));
    }
  };

  const handlePublish = async () => {
    if (!selectedRuleId) return;
    try {
      await publishMutation.mutateAsync({
        ruleId: selectedRuleId,
        packageVersion: selectedRulePackageVersion,
      });
      message.success("发布成功，门禁测试通过");
      refetchDetail();
      refetchList();
    } catch (error: unknown) {
      modal.error({
        title: "规则发布门禁拒绝",
        content: getApiErrorMessage(
          error,
          "发布门禁校验未通过，请检查测试用例是否齐全且全部通过。",
        ),
      });
    }
  };

  const columns: TableProps<RuleDefinition>["columns"] = [
    {
      title: "规则编码",
      dataIndex: "ruleCode",
      key: "ruleCode",
      render: (text: string) => <Tag color="blue">{text}</Tag>,
    },
    {
      title: "规则名称",
      dataIndex: "name",
      key: "name",
      className: "font-semibold text-gray-800",
    },
    {
      title: "规则类别",
      dataIndex: "ruleType",
      key: "ruleType",
      render: (type: string) => RULE_TYPE_LABELS[type] || type,
    },
    {
      title: "风险评级",
      dataIndex: "riskLevel",
      key: "riskLevel",
      render: renderRiskTag,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: renderStatus,
    },
    {
      title: "当前包版本",
      dataIndex: "packageVersion",
      key: "packageVersion",
      render: (val: string) => val || <span className="text-gray-400">未锁定</span>,
    },
    {
      title: "操作",
      key: "action",
      render: (_value: unknown, record: RuleDefinition) => (
        <Button type="link" onClick={() => setSelectedRuleId(record.ruleId)}>
          查看配置与试运行
        </Button>
      ),
    },
  ];

  const detailLayerItems = detailData
    ? [
        {
          key: "l1",
          label: (
            <span>
              <ApartmentOutlined /> L1 模板
            </span>
          ),
          children: (
            <Descriptions bordered column={1}>
              <Descriptions.Item label="创作方式">
                {detailData.definition.authoringMode === "VISUAL"
                  ? "三层编辑器"
                  : detailData.definition.authoringMode}
              </Descriptions.Item>
              <Descriptions.Item label="来源依据">
                {detailData.version?.sourceRef}
              </Descriptions.Item>
              <Descriptions.Item label="变更说明">
                {detailData.version?.changeSummary || "未填写"}
              </Descriptions.Item>
            </Descriptions>
          ),
        },
        {
          key: "l2",
          label: (
            <span>
              <BranchesOutlined /> L2 条件树
            </span>
          ),
          children: detailTree ? (
            <Space direction="vertical" className="mk-full-width">
              <Alert
                type="info"
                showIcon
                message={`当前条件组为「${detailTree.logic === "all" ? "全部满足" : "任一满足"}」，共 ${detailTree.conditions.length} 个条件。`}
              />
              {detailTree.conditions.map((condition) => (
                <Descriptions key={condition.id} bordered column={2} size="small">
                  <Descriptions.Item label="条件标签">{condition.label}</Descriptions.Item>
                  <Descriptions.Item label="字段路径">{condition.fact}</Descriptions.Item>
                  <Descriptions.Item label="算子">
                    {OPERATOR_LABELS[condition.operator]}
                  </Descriptions.Item>
                  <Descriptions.Item label="比较值">
                    {conditionValueText(condition)}
                  </Descriptions.Item>
                </Descriptions>
              ))}
              <Descriptions bordered column={1} size="small">
                <Descriptions.Item label="命中动作">
                  {detailTree.action.actionCode} · {RISK_LABELS[detailTree.action.severity]}
                </Descriptions.Item>
                <Descriptions.Item label="解释摘要">
                  {detailTree.explanationSummary}
                </Descriptions.Item>
              </Descriptions>
            </Space>
          ) : (
            <Alert
              type="warning"
              showIcon
              message="该版本 DSL 无法无损还原为当前 L2 条件树，请在 L3 专家视图核查。"
            />
          ),
        },
        {
          key: "l3",
          label: (
            <span>
              <CodeOutlined /> L3 DSL
            </span>
          ),
          children: (
            <Row gutter={16}>
              <Col span={12}>
                <div className="bg-gray-50 p-4 rounded-lg overflow-auto max-h-96 text-xs font-normal">
                  {detailDsl ? formatRuleJson(detailDsl) : "当前版本 DSL 无法解析"}
                </div>
              </Col>
              <Col span={12}>
                <div className="bg-gray-50 p-4 rounded-lg overflow-auto max-h-96 text-xs font-normal">
                  {detailExplanation
                    ? formatRuleJson(detailExplanation)
                    : "当前版本解释模板为空或无法解析"}
                </div>
              </Col>
            </Row>
          ),
        },
        {
          key: "cases",
          label: (
            <span>
              <InfoCircleOutlined /> 发布门禁测试用例 ({detailData.testCases.length})
            </span>
          ),
          children: (
            <>
              <div className="flex justify-between items-center mb-4">
                <Text type="secondary">
                  发布前必须覆盖阳性、阴性、边界和冲突用例，且所有用例通过。
                </Text>
                {detailData.definition.status === "DRAFT" && (
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => {
                      caseForm.setFieldsValue({
                        inputPayload: "",
                        expectedHit: true,
                        expectedSeverity: "LOW",
                        expectedActionCode: "REVIEW_REQUIRED",
                        caseType: "POSITIVE",
                      });
                      setCaseModalVisible(true);
                    }}
                  >
                    新增测试用例
                  </Button>
                )}
              </div>

              <Table
                dataSource={detailData.testCases}
                rowKey="caseId"
                pagination={false}
                columns={[
                  {
                    title: "用例类别",
                    dataIndex: "caseType",
                    key: "caseType",
                    render: (t) => <Tag color="blue">{t}</Tag>,
                  },
                  {
                    title: "期望命中",
                    dataIndex: "expectedHit",
                    key: "expectedHit",
                    render: (val: boolean) => (val ? "应该触发" : "不该触发"),
                  },
                  {
                    title: "期望严重度",
                    dataIndex: "expectedSeverity",
                    key: "expectedSeverity",
                  },
                  {
                    title: "最新执行结果",
                    key: "lastStatus",
                    render: (_, row) => {
                      if (!row.lastStatus || row.lastStatus === "PENDING") {
                        return <Badge status="default" text="待执行" />;
                      }
                      return row.lastStatus === "PASS" ? (
                        <span className="text-green-600 font-medium">
                          <CheckCircleOutlined className="mr-1" /> PASS
                        </span>
                      ) : (
                        <Tooltip title={row.lastMessage}>
                          <span className="text-red-600 font-medium cursor-help">
                            <CloseCircleOutlined className="mr-1" /> FAIL
                          </span>
                        </Tooltip>
                      );
                    },
                  },
                  {
                    title: "执行时间",
                    dataIndex: "lastRunAt",
                    key: "lastRunAt",
                    render: (val) => (val ? new Date(val).toLocaleTimeString() : "-"),
                  },
                ]}
              />
            </>
          ),
        },
        {
          key: "simulate",
          label: (
            <span>
              <PlayCircleOutlined /> 真实快照试运行
            </span>
          ),
          children: (
            <Row gutter={16}>
              <Col span={12}>
                <Form layout="vertical">
                  <Form.Item label="真实脱敏上下文快照 JSON">
                    <TextArea
                      rows={12}
                      value={simulatePayload}
                      onChange={(e) => setSimulatePayload(e.target.value)}
                      placeholder="粘贴由上下文快照接口返回的脱敏 JSON，不在页面内预置患者、诊断或药品。"
                      className="font-normal text-xs"
                    />
                  </Form.Item>
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    onClick={handleSimulate}
                    loading={simulateMutation.isPending}
                    block
                  >
                    运行规则试运行求值
                  </Button>
                </Form>
              </Col>
              <Col span={12}>
                {simulateResult ? (
                  <div className="bg-gray-50 p-4 rounded-lg min-h-64 overflow-auto max-h-96">
                    <Descriptions column={1} size="small" bordered className="mb-4">
                      <Descriptions.Item label="规则是否命中">
                        {simulateResult.hit ? (
                          <Tag color="red">命中</Tag>
                        ) : (
                          <Tag color="green">未命中</Tag>
                        )}
                      </Descriptions.Item>
                      {simulateResult.hit && (
                        <>
                          <Descriptions.Item label="动作代码">
                            {simulateResult.actionCode}
                          </Descriptions.Item>
                          <Descriptions.Item label="最高严重等级">
                            {simulateResult.severity}
                          </Descriptions.Item>
                        </>
                      )}
                    </Descriptions>
                    <Text strong>详细决策动作说明</Text>
                    <div className="text-xs text-gray-600 bg-white p-3 rounded border border-gray-200 font-normal mt-2">
                      {simulateResult.explanation || "未命中，无动作输出。"}
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-col items-center justify-center min-h-64 text-gray-400">
                    <PlayCircleOutlined className="text-[48px] mb-4" />
                    <span>粘贴真实脱敏上下文快照后，点击运行开始求值</span>
                  </div>
                )}
              </Col>
            </Row>
          ),
        },
      ]
    : [];

  const createLayerItems = [
    {
      key: "l1",
      label: (
        <span>
          <ApartmentOutlined /> L1 模板
        </span>
      ),
      children: (
        <>
          <Alert
            type="info"
            showIcon
            message="模板只提供规则结构，不内置患者、药品、诊断或剂量常量；提交前必须在 L2 填写真实上下文字段。"
            className="mb-4"
          />
          <Radio.Group
            value={selectedTemplateKey}
            onChange={(event: RadioChangeEvent) =>
              applyTemplate(event.target.value as RuleTemplateKey)
            }
          >
            <Space direction="vertical" className="mk-full-width">
              {RULE_LAYER_TEMPLATES.map((template) => (
                <Radio key={template.key} value={template.key}>
                  <Space direction="vertical" size={0}>
                    <Text strong>{template.title}</Text>
                    <Text type="secondary">{template.description}</Text>
                  </Space>
                </Radio>
              ))}
            </Space>
          </Radio.Group>
        </>
      ),
    },
    {
      key: "l2",
      label: (
        <span>
          <BranchesOutlined /> L2 条件树
        </span>
      ),
      children: (
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="条件组关系">
                <Select
                  value={conditionTree.logic}
                  onChange={(value: RuleLogic) =>
                    setConditionTree((current) => ({ ...current, logic: value }))
                  }
                >
                  <Option value="all">全部条件满足</Option>
                  <Option value="any">任一条件满足</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item label="解释摘要">
                <Input
                  value={conditionTree.explanationSummary}
                  onChange={(event) =>
                    setConditionTree((current) => ({
                      ...current,
                      explanationSummary: event.target.value,
                    }))
                  }
                />
              </Form.Item>
            </Col>
          </Row>

          {conditionTree.conditions.map((condition, index) => {
            const needsValue = conditionNeedsValue(condition.operator);
            return (
              <div key={condition.id} className="rounded-lg border border-gray-100 p-4">
                <div className="flex items-center justify-between mb-3">
                  <Text strong>{condition.label || `条件 ${index + 1}`}</Text>
                  <Button
                    size="small"
                    onClick={() => removeCondition(index)}
                    disabled={conditionTree.conditions.length <= 1}
                  >
                    移除
                  </Button>
                </div>
                <Row gutter={16}>
                  <Col span={8}>
                    <Form.Item label="条件标签">
                      <Input
                        value={condition.label}
                        onChange={(event) => updateCondition(index, { label: event.target.value })}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={10}>
                    <Form.Item
                      label="上下文字段路径"
                      htmlFor={index === 0 ? "rule-condition-fact" : undefined}
                    >
                      <Input
                        id={index === 0 ? "rule-condition-fact" : undefined}
                        value={condition.fact}
                        onChange={(event) => updateCondition(index, { fact: event.target.value })}
                        placeholder="如 observations.0.value"
                      />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item label="算子">
                      <Select
                        value={condition.operator}
                        onChange={(value: RuleOperator) =>
                          updateCondition(index, {
                            operator: value,
                            valueKind: valueKindForOperator(value, condition.valueKind),
                            value: valueForOperator(value, condition.value),
                          })
                        }
                      >
                        {Object.entries(OPERATOR_LABELS).map(([value, label]) => (
                          <Option key={value} value={value}>
                            {label}
                          </Option>
                        ))}
                      </Select>
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={16}>
                  <Col span={8}>
                    <Form.Item label="比较值类型">
                      <Select
                        value={condition.valueKind}
                        disabled={!needsValue}
                        onChange={(value: RuleValueKind) =>
                          updateCondition(index, { valueKind: value })
                        }
                      >
                        {Object.entries(VALUE_KIND_LABELS).map(([value, label]) => (
                          <Option key={value} value={value}>
                            {label}
                          </Option>
                        ))}
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={16}>
                    <Form.Item
                      label="比较值"
                      htmlFor={index === 0 ? "rule-condition-value" : undefined}
                    >
                      <Input
                        id={index === 0 ? "rule-condition-value" : undefined}
                        value={String(condition.value ?? "")}
                        disabled={!needsValue}
                        onChange={(event) => updateCondition(index, { value: event.target.value })}
                        placeholder={
                          condition.valueKind === "list"
                            ? "多个值用英文逗号分隔"
                            : "输入来自已审核规则来源的比较值"
                        }
                      />
                    </Form.Item>
                  </Col>
                </Row>
              </div>
            );
          })}

          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="动作代码">
              <Input
                value={conditionTree.action.actionCode}
                onChange={(event) =>
                  setConditionTree((current) => ({
                    ...current,
                    action: { ...current.action, actionCode: event.target.value },
                  }))
                }
              />
            </Descriptions.Item>
            <Descriptions.Item label="动作风险">
              <Select
                value={conditionTree.action.severity}
                onChange={(value: RuleSeverity) =>
                  setConditionTree((current) => ({
                    ...current,
                    action: { ...current.action, severity: value },
                  }))
                }
                className="mk-full-width"
              >
                <Option value="LOW">低风险</Option>
                <Option value="MEDIUM">中风险</Option>
                <Option value="HIGH">高风险</Option>
              </Select>
            </Descriptions.Item>
            <Descriptions.Item label="动作说明" span={2}>
              <Input
                value={conditionTree.action.message}
                onChange={(event) =>
                  setConditionTree((current) => ({
                    ...current,
                    action: { ...current.action, message: event.target.value },
                  }))
                }
              />
            </Descriptions.Item>
          </Descriptions>

          <Space>
            <Button icon={<PlusOutlined />} onClick={addCondition}>
              新增条件
            </Button>
            <Button
              type="primary"
              icon={<SyncOutlined />}
              aria-label="同步到 DSL"
              onClick={syncTreeToDsl}
            >
              同步到 DSL
            </Button>
          </Space>
        </Space>
      ),
    },
    {
      key: "l3",
      label: (
        <span>
          <CodeOutlined /> L3 DSL
        </span>
      ),
      children: (
        <>
          <Alert
            type="warning"
            showIcon
            message="L3 是专家模式，保存前必须能回填到 L2 条件树；不允许提交无法解释的裸 DSL。"
            className="mb-4"
          />
          <Form.Item label="规则 DSL JSON" htmlFor="ruleDslJson">
            <TextArea
              id="ruleDslJson"
              rows={16}
              value={dslEditorValue}
              onChange={(event) => setDslEditorValue(event.target.value)}
              className="font-normal text-xs"
            />
          </Form.Item>
          <Space>
            <Button icon={<BranchesOutlined />} aria-label="回填到条件树" onClick={syncDslToTree}>
              回填到条件树
            </Button>
            <Button icon={<FileSearchOutlined />} aria-label="重新生成 DSL" onClick={syncTreeToDsl}>
              重新生成 DSL
            </Button>
          </Space>
        </>
      ),
    },
  ];

  const pageState = listIsError ? "error" : "ready";

  return (
    <PageShell
      title="规则中枢"
      description="配置规则资产，完成测试、解释和发布门禁。"
      primary={
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
          新建规则模板
        </Button>
      }
      state={pageState}
      stateProps={{
        title: "规则列表读取失败",
        description: getApiErrorMessage(listError, "请稍后重试，或联系信息科核查规则 API。"),
        onRetry: refetchList,
      }}
    >
      <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-100 mb-6">
        <Form layout="inline" className="flex flex-wrap gap-4 items-center">
          <Form.Item label="状态">
            <Select
              placeholder="全部状态"
              allowClear
              value={statusFilter}
              onChange={setStatusFilter}
              className="w-[140px]"
            >
              <Option value="DRAFT">草稿设计中</Option>
              <Option value="PUBLISHED">已上线运行</Option>
              <Option value="OFFLINE">已下线封存</Option>
            </Select>
          </Form.Item>
          <Form.Item label="类别">
            <Select
              placeholder="全部类别"
              allowClear
              value={typeFilter}
              onChange={setTypeFilter}
              className="w-[150px]"
            >
              <Option value="DRUG_SAFETY">合理用药安全</Option>
              <Option value="INSURANCE_AUDIT">医保规范核查</Option>
              <Option value="CLINICAL_QUALITY">临床诊疗质控</Option>
            </Select>
          </Form.Item>
          <Form.Item label="风险评级">
            <Select
              placeholder="全部评级"
              allowClear
              value={riskFilter}
              onChange={setRiskFilter}
              className="w-[120px]"
            >
              <Option value="LOW">低风险</Option>
              <Option value="MEDIUM">中风险</Option>
              <Option value="HIGH">高风险</Option>
            </Select>
          </Form.Item>
        </Form>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-100 overflow-hidden">
        <Table
          columns={columns}
          dataSource={listData?.items || []}
          rowKey="ruleId"
          loading={listLoading}
          locale={{ emptyText: "当前筛选条件下暂无规则资产，可从右上角创建草稿。" }}
          pagination={{
            current: page,
            pageSize: size,
            total: listData?.total || 0,
            onChange: (p) => setPage(p),
            showTotal: (t) => `共 ${t} 个受控规则实体`,
          }}
          className="medkernel-table"
        />
      </div>

      <Drawer
        title={
          <div className="flex items-center justify-between w-full">
            <span>规则配置详情与试运行</span>
            {detailData?.definition.status === "DRAFT" && (
              <Button type="primary" onClick={handlePublish} loading={publishMutation.isPending}>
                提交发布门禁
              </Button>
            )}
          </div>
        }
        width={980}
        onClose={() => {
          setSelectedRuleId(null);
          setSimulateResult(null);
        }}
        open={!!selectedRuleId}
        loading={detailLoading}
        destroyOnClose
      >
        {detailData && (
          <Space direction="vertical" size="large" className="mk-full-width">
            <Alert
              message={
                detailData.definition.status === "PUBLISHED"
                  ? "当前规则已上线，修改需新建草稿版本并重新走发布门禁。"
                  : "当前规则为草稿，可补测试用例、试运行，并在全绿后提交发布。"
              }
              type={detailData.definition.status === "PUBLISHED" ? "success" : "info"}
              showIcon
            />

            <Descriptions title="基本元数据" bordered column={2}>
              <Descriptions.Item label="规则编码">
                {detailData.definition.ruleCode}
              </Descriptions.Item>
              <Descriptions.Item label="名称">{detailData.definition.name}</Descriptions.Item>
              <Descriptions.Item label="类型">
                {RULE_TYPE_LABELS[detailData.definition.ruleType] ?? detailData.definition.ruleType}
              </Descriptions.Item>
              <Descriptions.Item label="风险级别">
                {renderRiskTag(detailData.definition.riskLevel)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {renderStatus(detailData.definition.status)}
              </Descriptions.Item>
              <Descriptions.Item label="当前版本">
                {detailData.version?.versionNo || 0} 版
              </Descriptions.Item>
            </Descriptions>

            <Tabs defaultActiveKey="l2" items={detailLayerItems} />
          </Space>
        )}
      </Drawer>

      <Modal
        title="创建新临床规则"
        open={createModalVisible}
        onOk={handleCreateRule}
        onCancel={() => setCreateModalVisible(false)}
        width={920}
        okText="创建草稿"
        cancelText="取消"
        confirmLoading={createRuleMutation.isPending}
        forceRender
      >
        <Form form={createForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="ruleCode"
                label="规则唯一业务编码"
                rules={[{ required: true, message: "请输入编码，同租户下不可重复" }]}
              >
                <Input placeholder="输入规则业务编码" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="name"
                label="规则显示名称"
                rules={[{ required: true, message: "请输入规则名称" }]}
              >
                <Input placeholder="输入规则显示名称" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="ruleType" label="规则门类" rules={[{ required: true }]}>
                <Select>
                  <Option value="DRUG_SAFETY">合理用药安全</Option>
                  <Option value="INSURANCE_AUDIT">医保规范核查</Option>
                  <Option value="CLINICAL_QUALITY">临床诊疗质控</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="riskLevel" label="风险严重等级" rules={[{ required: true }]}>
                <Select>
                  <Option value="LOW">低风险</Option>
                  <Option value="MEDIUM">中风险</Option>
                  <Option value="HIGH">高风险</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="sourceRef"
                label="医学依据/来源"
                rules={[{ required: true, message: "请输入依据来源" }]}
              >
                <Input placeholder="输入已审核医学依据、院内制度或配置包来源" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="packageVersion"
            label="标准上下文包版本"
            rules={[{ required: true, message: "请输入本规则绑定的配置包版本" }]}
          >
            <Input placeholder="输入当前已审核的标准上下文包版本" />
          </Form.Item>
          <Form.Item name="changeSummary" label="初始化变更内容说明" rules={[{ required: true }]}>
            <Input placeholder="本次创建版本的修改概述" />
          </Form.Item>

          <Tabs
            activeKey={activeCreateLayer}
            items={createLayerItems}
            onChange={(key) => setActiveCreateLayer(key as CreateLayerKey)}
          />
        </Form>
      </Modal>

      <Modal
        title="新增测试用例"
        open={caseModalVisible}
        onOk={handleAddTestCase}
        onCancel={() => setCaseModalVisible(false)}
        okText="保存用例"
        cancelText="取消"
        confirmLoading={addTestCaseMutation.isPending}
        forceRender
      >
        <Form form={caseForm} layout="vertical">
          <Form.Item name="caseType" label="用例类别" rules={[{ required: true }]}>
            <Select>
              <Option value="POSITIVE">阳性命中用例</Option>
              <Option value="NEGATIVE">阴性不命中用例</Option>
              <Option value="BOUNDARY">边界条件用例</Option>
              <Option value="CONFLICT">规则冲突校验用例</Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="inputPayload"
            label="真实脱敏测试输入 JSON 快照"
            rules={[{ required: true, message: "请输入快照" }]}
          >
            <TextArea
              rows={8}
              placeholder="粘贴真实脱敏上下文快照 JSON"
              className="font-normal text-xs"
            />
          </Form.Item>
          <Form.Item name="expectedHit" label="期望求值结果">
            <Select>
              <Option value={true}>应当触发规则命中</Option>
              <Option value={false}>不应当命中</Option>
            </Select>
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="expectedSeverity"
                label="期望动作严重度"
                rules={[{ required: true }]}
              >
                <Select>
                  <Option value="LOW">低风险</Option>
                  <Option value="MEDIUM">中风险</Option>
                  <Option value="HIGH">高风险</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="expectedActionCode"
                label="期望动作代码"
                rules={[{ required: true }]}
              >
                <Input placeholder="如 REVIEW_REQUIRED / BLOCK" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </PageShell>
  );
}
