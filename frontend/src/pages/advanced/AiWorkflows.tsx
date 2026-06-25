import { useMemo, useState } from "react";
import {
  Alert,
  App,
  Button,
  Descriptions,
  Empty,
  Form,
  Modal,
  Result,
  Select,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { TableProps } from "antd";
import { ReloadOutlined, SafetyCertificateOutlined } from "@ant-design/icons";

import {
  useModelCapabilitiesStatus,
  useSaveModelEgressPolicy,
  useSecurityProfile,
} from "@/shared/api/hooks";
import type {
  ModelCapabilityStatusResponse,
  ModelEgressDesensitizationOperator,
  ModelEgressSensitivityLevel,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import { customerDisplayText, customerEnumLabel } from "@/shared/config/customerLabels";
import { PageShell } from "@/shared/ui/PageShell";

import styles from "./AiWorkflows.module.css";

const { Text } = Typography;

const MODEL_CAPABILITY_TITLE = "模型能力";
const MODEL_CAPABILITY_DESCRIPTION = "核查模型能力、路由与降级状态";

const routeStrategyView: Record<string, { color: string; label: string }> = {
  BASELINE: { color: "blue", label: "基础规则能力" },
  DISABLED: { color: "default", label: "模型能力已关闭" },
  LOCAL_MODEL: { color: "cyan", label: "本地模型策略" },
  EXTERNAL_MODEL: { color: "geekblue", label: "外部模型策略" },
};

const desensitizeStrategyView: Record<string, string> = {
  DEFAULT: "默认脱敏",
  MASK_ALL: "全量掩码",
  NONE: "仅核心敏感遮蔽",
};

const egressOperatorView: Record<ModelEgressDesensitizationOperator, string> = {
  MASK: "遮蔽",
  MASK_ALL: "全量遮蔽",
  GENERALIZE: "泛化",
  NULLIFY: "清空",
  NONE: "保留非核心业务值",
};

const sensitivityLevelOptions: Array<{ value: ModelEgressSensitivityLevel; label: string }> = [
  { value: "HIGH", label: "高敏" },
  { value: "MEDIUM", label: "中敏" },
  { value: "LOW", label: "低敏" },
];

const egressOperatorOptions: Array<{
  value: ModelEgressDesensitizationOperator;
  label: string;
}> = [
  { value: "MASK_ALL", label: "全量遮蔽" },
  { value: "GENERALIZE", label: "泛化" },
  { value: "NULLIFY", label: "清空" },
  { value: "NONE", label: "保留非核心业务值" },
];

const policyScopeView: Record<string, string> = {
  TENANT: "服务机构",
  GROUP: "集团",
  HOSPITAL: "医院",
  CAMPUS: "院区",
  SITE: "站点",
  DEPARTMENT: "科室",
  WARD: "病区",
};

function routeView(routeStrategy: string) {
  return (
    routeStrategyView[routeStrategy] ?? {
      color: "default",
      label: customerEnumLabel(routeStrategy || "NOT_AVAILABLE"),
    }
  );
}

function availabilityView(item: ModelCapabilityStatusResponse) {
  if (item.routeStrategy === "DISABLED") {
    return { color: "default", label: "已停用" };
  }
  if (item.fallbackAvailable) {
    return { color: "success", label: "规则链路可用" };
  }
  return { color: "warning", label: "暂不可用" };
}

function fallbackOrderLabel(order: string[]) {
  return order.map((strategy) => routeView(strategy).label).join(" → ");
}

function configurationModeLabel(item: ModelCapabilityStatusResponse) {
  if (!item.configured) {
    return "系统默认";
  }
  return item.inherited ? "继承配置" : "当前作用域配置";
}

function capabilityDetails(item: ModelCapabilityStatusResponse) {
  const scopeLabel = `${policyScopeView[item.policyScopeType] ?? customerEnumLabel(item.policyScopeType)}:${item.policyScopeRef}`;
  const externalEnabled =
    item.routeStrategy === "EXTERNAL_MODEL" || item.fallbackOrder.includes("EXTERNAL_MODEL");
  return (
    <Descriptions className={styles.details} column={{ xs: 1, sm: 2, lg: 3 }} size="small">
      <Descriptions.Item label="能力代码">
        <Text code>{item.capabilityCode}</Text>
      </Descriptions.Item>
      <Descriptions.Item label="路由策略">{routeView(item.routeStrategy).label}</Descriptions.Item>
      <Descriptions.Item label="脱敏策略">
        {desensitizeStrategyView[item.desensitizeStrategy] ??
          customerEnumLabel(item.desensitizeStrategy)}
      </Descriptions.Item>
      <Descriptions.Item label="服务空间专属配置">{configurationModeLabel(item)}</Descriptions.Item>
      <Descriptions.Item label="策略作用域">
        <Text code>{scopeLabel}</Text>
      </Descriptions.Item>
      <Descriptions.Item label="降级顺序">
        {fallbackOrderLabel(item.fallbackOrder)}
      </Descriptions.Item>
      <Descriptions.Item label="调用预算">
        {item.timeoutMs}ms
        {item.rateLimitPerMinute ? ` / ${item.rateLimitPerMinute} 次每分钟` : ""}
      </Descriptions.Item>
      <Descriptions.Item label="结构约束">
        {item.expectedSchema ? "已配置输出格式" : "未配置"}
      </Descriptions.Item>
      <Descriptions.Item label="状态说明">
        {customerDisplayText(item.fallbackReason)}
      </Descriptions.Item>
      <Descriptions.Item label="外调边界" span={3}>
        {externalEnabled
          ? "公网外部模型可在授权用途内使用患者上下文，运行时仍会先执行字段允许范围、核心敏感遮蔽、责任确认和证据留痕。"
          : "当前能力不走公网外部模型；如后续切到外部模型，仍需先配置外调安全策略。"}
      </Descriptions.Item>
      {item.expectedSchema ? (
        <Descriptions.Item label="输出格式明细" span={3}>
          <Text code className={styles.schemaText}>
            {item.expectedSchema}
          </Text>
        </Descriptions.Item>
      ) : null}
    </Descriptions>
  );
}

type EgressPolicyForm = {
  allowedFields: string[];
  operator: ModelEgressDesensitizationOperator;
  sensitivityLevel: ModelEgressSensitivityLevel;
  confirmationThresholdLevel: ModelEgressSensitivityLevel;
};

type EgressFieldCandidate = {
  value: string;
  label: string;
  category: string;
  coreSensitive: boolean;
};

type EgressPreviewRow = {
  field: string;
  label: string;
  category: string;
  status: string;
  reason: string;
  coreSensitive: boolean;
};

const egressFieldCatalog: EgressFieldCandidate[] = [
  { value: "prompt", label: "提示词内容", category: "任务上下文", coreSensitive: false },
  { value: "patient.age", label: "患者年龄", category: "患者上下文", coreSensitive: false },
  { value: "patient.sex", label: "患者性别", category: "患者上下文", coreSensitive: false },
  { value: "patient.diagnosis", label: "诊断摘要", category: "患者上下文", coreSensitive: false },
  { value: "patient.name", label: "患者姓名", category: "核心标识", coreSensitive: true },
  { value: "patient.identityNo", label: "证件号码", category: "核心标识", coreSensitive: true },
  { value: "patient.phone", label: "手机号码", category: "核心标识", coreSensitive: true },
  { value: "patient.address", label: "联系地址", category: "核心标识", coreSensitive: true },
  { value: "patient.mpiId", label: "患者编号", category: "核心标识", coreSensitive: true },
];

const egressFieldOptions = egressFieldCatalog.map((field) => ({
  value: field.value,
  label: field.label,
}));

function normalizeAllowedFields(fields?: string[]) {
  return Array.from(new Set((fields ?? []).map((field) => field.trim()).filter(Boolean)));
}

function buildEgressPreviewRows(
  allowedFields: string[] | undefined,
  operator: ModelEgressDesensitizationOperator | undefined,
): EgressPreviewRow[] {
  const allowed = new Set(normalizeAllowedFields(allowedFields));
  const catalogValues = new Set(egressFieldCatalog.map((field) => field.value));
  const customAllowedFields = Array.from(allowed)
    .filter((field) => !catalogValues.has(field))
    .map<EgressFieldCandidate>((field) => ({
      value: field,
      label: field,
      category: "自定义字段",
      coreSensitive: false,
    }));
  const selectedOperator = operator ?? "MASK_ALL";
  return [...egressFieldCatalog, ...customAllowedFields].map((field) => {
    const selected = allowed.has(field.value);
    if (field.coreSensitive) {
      return {
        field: field.value,
        label: field.label,
        category: field.category,
        status: selected ? "核心标识强制遮蔽" : "核心标识默认不出域",
        reason: selected
          ? "即使纳入允许字段，运行时仍只可传出遮蔽后值"
          : "公网外调默认不发送姓名、证件、电话、地址和患者编号明文",
        coreSensitive: true,
      };
    }
    return {
      field: field.value,
      label: field.label,
      category: field.category,
      status: selected ? egressOperatorView[selectedOperator] : "不出域",
      reason: selected
        ? "按当前字段处理策略进入模型请求"
        : "未纳入允许字段，运行时不会传给外部模型",
      coreSensitive: false,
    };
  });
}

function egressPreviewStatusColor(item: EgressPreviewRow) {
  if (item.coreSensitive) {
    return "warning";
  }
  if (item.status === "不出域") {
    return "default";
  }
  return "blue";
}

const egressPreviewColumns: TableProps<EgressPreviewRow>["columns"] = [
  {
    title: "字段",
    key: "field",
    width: 180,
    render: (_value, item) => (
      <div className={styles.previewFieldCell}>
        <Text strong>{item.label}</Text>
        <Text code>{item.field}</Text>
      </div>
    ),
  },
  {
    title: "分类",
    dataIndex: "category",
    width: 110,
  },
  {
    title: "外调结果",
    key: "status",
    width: 150,
    render: (_value, item) => <Tag color={egressPreviewStatusColor(item)}>{item.status}</Tag>,
  },
  {
    title: "说明",
    dataIndex: "reason",
    render: (value: string) => <Text type="secondary">{value}</Text>,
  },
];

export default function AiWorkflows() {
  const { message } = App.useApp();
  const securityQuery = useSecurityProfile();
  const permissionCodes = useMemo(
    () => new Set(securityQuery.data?.permissions.map((permission) => permission.code) ?? []),
    [securityQuery.data],
  );
  const canRead = permissionCodes.has("llm.read");
  const canManageEgress = permissionCodes.has("llm.egress.manage");
  const statusQuery = useModelCapabilitiesStatus(canRead);
  const saveEgressPolicy = useSaveModelEgressPolicy();
  const [egressForm] = Form.useForm<EgressPolicyForm>();
  const selectedEgressOperator = Form.useWatch("operator", egressForm) as
    | ModelEgressDesensitizationOperator
    | undefined;
  const selectedAllowedFields = Form.useWatch("allowedFields", egressForm) as string[] | undefined;
  const [egressCapability, setEgressCapability] = useState<ModelCapabilityStatusResponse | null>(
    null,
  );
  const capabilities = useMemo(() => statusQuery.data ?? [], [statusQuery.data]);
  const egressPreviewRows = useMemo(
    () => buildEgressPreviewRows(selectedAllowedFields ?? ["prompt"], selectedEgressOperator),
    [selectedAllowedFields, selectedEgressOperator],
  );

  const summary = useMemo(
    () => ({
      total: capabilities.length,
      baseline: capabilities.filter((item) => item.routeStrategy === "BASELINE").length,
      configured: capabilities.filter((item) => item.configured).length,
      disabled: capabilities.filter((item) => item.routeStrategy === "DISABLED").length,
    }),
    [capabilities],
  );
  const unavailableCount = useMemo(
    () =>
      capabilities.filter((item) => item.routeStrategy !== "DISABLED" && !item.fallbackAvailable)
        .length,
    [capabilities],
  );

  function openEgressPolicy(item: ModelCapabilityStatusResponse) {
    setEgressCapability(item);
    egressForm.setFieldsValue({
      allowedFields: ["prompt"],
      operator: "MASK_ALL",
      sensitivityLevel: "HIGH",
      confirmationThresholdLevel: "HIGH",
    });
  }

  async function saveCurrentEgressPolicy() {
    if (!egressCapability) return;
    try {
      const values = await egressForm.validateFields();
      const allowedFields = normalizeAllowedFields(values.allowedFields);
      const operator = values.operator ?? "MASK_ALL";
      const desensitizationRules = Object.fromEntries(
        allowedFields.map((field) => [field, operator]),
      ) as Record<string, ModelEgressDesensitizationOperator>;
      await saveEgressPolicy.mutateAsync({
        capabilityCode: egressCapability.capabilityCode,
        policy: {
          allowedFields,
          sensitivityLevel: values.sensitivityLevel,
          desensitizationRules,
          confirmationThresholdLevel: values.confirmationThresholdLevel,
        },
      });
      message.success("外调安全策略已保存");
      setEgressCapability(null);
      egressForm.resetFields();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "外调安全策略保存失败"));
    }
  }

  const columns: TableProps<ModelCapabilityStatusResponse>["columns"] = [
    {
      title: "能力",
      key: "capability",
      width: 320,
      render: (_value, item) => (
        <div className={styles.capabilityCell}>
          <Text strong>{item.displayName}</Text>
          <Text type="secondary">{item.description}</Text>
          <Text code className={styles.capabilityCode}>
            {item.capabilityCode}
          </Text>
        </div>
      ),
    },
    {
      title: "业务分类",
      dataIndex: "category",
      key: "category",
      width: 140,
    },
    {
      title: "运行方式",
      dataIndex: "routeStrategy",
      key: "routeStrategy",
      width: 150,
      render: (value: string) => {
        const view = routeView(value);
        return <Tag color={view.color}>{view.label}</Tag>;
      },
    },
    {
      title: "数据保护",
      dataIndex: "desensitizeStrategy",
      key: "desensitizeStrategy",
      width: 130,
      render: (value: string) => desensitizeStrategyView[value] ?? customerEnumLabel(value),
    },
    {
      title: "结构约束",
      dataIndex: "expectedSchema",
      key: "expectedSchema",
      width: 120,
      render: (value: string | null) => (value ? "已配置" : "未配置"),
    },
    {
      title: "降级顺序",
      key: "fallbackOrder",
      width: 180,
      render: (_value, item) => (
        <Text type="secondary">{fallbackOrderLabel(item.fallbackOrder)}</Text>
      ),
    },
    {
      title: "策略来源",
      key: "policyScope",
      width: 180,
      render: (_value, item) => {
        const scopeLabel = `${policyScopeView[item.policyScopeType] ?? customerEnumLabel(item.policyScopeType)}:${item.policyScopeRef}`;
        const modeLabel = configurationModeLabel(item);
        return (
          <div className={styles.statusCell}>
            <Tag color={item.configured ? "processing" : "default"}>{modeLabel}</Tag>
            <Text code>{scopeLabel}</Text>
          </div>
        );
      },
    },
    {
      title: "当前状态",
      key: "status",
      width: 260,
      render: (_value, item) => {
        const view = availabilityView(item);
        return (
          <div className={styles.statusCell}>
            <Tag color={view.color}>{view.label}</Tag>
            <Text type="secondary">{customerDisplayText(item.fallbackReason)}</Text>
          </div>
        );
      },
    },
  ];
  if (canManageEgress) {
    columns.push({
      title: "外调安全",
      key: "egressPolicy",
      width: 120,
      render: (_value, item) => (
        <Tooltip title="配置字段允许范围、脱敏规则和责任确认阈值">
          <Button
            aria-label={`配置 ${item.displayName} 外调安全策略`}
            icon={<SafetyCertificateOutlined />}
            onClick={() => openEgressPolicy(item)}
          />
        </Tooltip>
      ),
    });
  }

  if (securityQuery.isLoading) {
    return (
      <PageShell title={MODEL_CAPABILITY_TITLE} description={MODEL_CAPABILITY_DESCRIPTION}>
        <Spin aria-label="正在核验访问权限" />
      </PageShell>
    );
  }

  if (securityQuery.isError || !canRead) {
    return (
      <PageShell title={MODEL_CAPABILITY_TITLE} description={MODEL_CAPABILITY_DESCRIPTION}>
        <Result status="403" title="无权查看模型能力" subTitle="需要模型能力读取权限。" />
      </PageShell>
    );
  }

  if (statusQuery.isError) {
    return (
      <PageShell title={MODEL_CAPABILITY_TITLE} description={MODEL_CAPABILITY_DESCRIPTION}>
        <Result
          status="error"
          title="模型能力状态读取失败"
          subTitle="未使用本地默认项替代真实状态。"
          extra={
            <Button type="primary" onClick={() => statusQuery.refetch()}>
              重新读取
            </Button>
          }
        />
      </PageShell>
    );
  }

  if (statusQuery.isLoading) {
    return (
      <PageShell title={MODEL_CAPABILITY_TITLE} description={MODEL_CAPABILITY_DESCRIPTION}>
        <div className={styles.loadingState}>
          <Spin aria-label="正在读取模型能力状态" size="large" />
          <Text type="secondary">正在读取真实能力目录与运行状态</Text>
        </div>
      </PageShell>
    );
  }

  return (
    <PageShell
      title={MODEL_CAPABILITY_TITLE}
      description="查看已登记能力、路由策略与降级状态"
      extras={
        <Tooltip title="刷新能力状态">
          <Button
            aria-label="刷新能力状态"
            icon={<ReloadOutlined />}
            loading={statusQuery.isFetching}
            onClick={() => statusQuery.refetch()}
          />
        </Tooltip>
      }
    >
      <div className={styles.pageStack}>
        <section className={styles.summaryStrip} aria-label="模型能力状态摘要">
          <div className={styles.summaryItem}>
            <Text type="secondary">已登记能力</Text>
            <strong>{summary.total}</strong>
          </div>
          <div className={styles.summaryItem}>
            <Text type="secondary">基础规则能力</Text>
            <strong>{summary.baseline}</strong>
          </div>
          <div className={styles.summaryItem}>
            <Text type="secondary">服务空间已配置</Text>
            <strong>{summary.configured}</strong>
          </div>
          <div className={styles.summaryItem}>
            <Text type="secondary">已停用</Text>
            <strong>{summary.disabled}</strong>
          </div>
        </section>

        {unavailableCount > 0 ? (
          <Alert
            type="warning"
            showIcon
            message="部分模型能力当前不可用"
            description={`${unavailableCount} 项能力没有可用路由或规则链路，其他能力仍可查看。`}
          />
        ) : null}

        <Alert
          type="info"
          showIcon
          message="患者上下文外调边界"
          description="公网模型可在授权用途内使用患者上下文，姓名、证件号、手机号、地址、患者编号等核心标识字段先遮蔽；院内本地模型按授权使用必要信息，日志与证据不留患者明文。"
        />

        {capabilities.length === 0 ? (
          <div className={styles.emptyState}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <div className={styles.emptyDescription}>
                  <Text>当前组织没有已启用的模型能力</Text>
                  <Text type="secondary">未使用本地默认项补齐真实结果。</Text>
                </div>
              }
            />
          </div>
        ) : (
          <div className={styles.tableWrap}>
            <Table
              rowKey="capabilityCode"
              columns={columns}
              dataSource={capabilities}
              loading={statusQuery.isLoading}
              pagination={false}
              scroll={{ x: 1184 }}
              tableLayout="fixed"
              expandable={{
                expandedRowRender: capabilityDetails,
                rowExpandable: () => true,
                columnTitle: "详情",
                columnWidth: 64,
              }}
            />
          </div>
        )}
        <Modal
          title="配置外调安全策略"
          open={Boolean(egressCapability)}
          width={760}
          okText="保存外调安全策略"
          okButtonProps={{ "aria-label": "保存外调安全策略" }}
          confirmLoading={saveEgressPolicy.isPending}
          onOk={() => void saveCurrentEgressPolicy()}
          onCancel={() => setEgressCapability(null)}
          destroyOnClose
        >
          {egressCapability ? (
            <Form<EgressPolicyForm>
              form={egressForm}
              layout="vertical"
              initialValues={{
                allowedFields: ["prompt"],
                operator: "MASK_ALL",
                sensitivityLevel: "HIGH",
                confirmationThresholdLevel: "HIGH",
              }}
            >
              <Alert
                type="warning"
                showIcon
                message="公网外部模型可使用患者上下文"
                description="外调前必须完成字段最小化、核心敏感信息遮蔽、责任确认和证据留痕；保留非核心业务值时，核心患者标识仍由后端强制遮蔽。"
              />
              <Descriptions className={styles.egressCapability} column={1} size="small">
                <Descriptions.Item label="模型能力">
                  {egressCapability.displayName}
                </Descriptions.Item>
                <Descriptions.Item label="能力代码">
                  <Text code>{egressCapability.capabilityCode}</Text>
                </Descriptions.Item>
              </Descriptions>
              <Form.Item
                name="allowedFields"
                label="外调允许字段"
                rules={[{ required: true, type: "array", min: 1, message: "请至少保留一个字段" }]}
              >
                <Select
                  mode="tags"
                  tokenSeparators={[","]}
                  options={egressFieldOptions}
                  placeholder="输入字段后回车"
                />
              </Form.Item>
              <Form.Item name="operator" label="字段处理" rules={[{ required: true }]}>
                <Select options={egressOperatorOptions} />
              </Form.Item>
              <Form.Item name="sensitivityLevel" label="敏感级别" rules={[{ required: true }]}>
                <Select options={sensitivityLevelOptions} />
              </Form.Item>
              <Form.Item
                name="confirmationThresholdLevel"
                label="责任确认阈值"
                rules={[{ required: true }]}
              >
                <Select options={sensitivityLevelOptions} />
              </Form.Item>
              <Alert
                type="info"
                showIcon
                message={`当前字段处理：${egressOperatorView[selectedEgressOperator ?? "MASK_ALL"]}`}
              />
              <div className={styles.egressPreviewPanel}>
                <div className={styles.egressPreviewHeader}>
                  <Text strong>字段出域预览</Text>
                  <Text type="secondary">
                    高敏用途达到阈值时，每次模型出域前需要责任确认；证据只保存字段清单、处理策略和摘要，不保存患者明文。
                  </Text>
                </div>
                <Table
                  size="small"
                  rowKey="field"
                  columns={egressPreviewColumns}
                  dataSource={egressPreviewRows}
                  pagination={false}
                  scroll={{ x: 640 }}
                />
              </div>
            </Form>
          ) : null}
        </Modal>
      </div>
    </PageShell>
  );
}
