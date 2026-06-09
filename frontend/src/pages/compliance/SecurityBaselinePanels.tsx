import {
  Alert,
  App,
  Button,
  Checkbox,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Segmented,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from "antd";
import {
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import { useState } from "react";

import {
  useDataPermissionPolicies,
  useInteropAssessment,
  useMaskingRules,
  useOrgUnits,
  useSystemConfigs,
  useTenantSystemConfigs,
  useUpdateSystemConfig,
  useUpdateTenantSystemConfig,
  useUpsertDataPermissionPolicy,
  useUpsertMaskingRule,
  type DataPermissionPolicy,
  type DataPermissionPolicyPayload,
  type InteropAssessmentItem,
  type MaskingRule,
  type MaskingRulePayload,
  type OrgUnit,
  type SystemConfigItem,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import { PageState } from "@/shared/ui/PageState";

const { Text } = Typography;

function formatDateTime(value?: string | null) {
  if (!value) return "未返回";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function statusTag(status: string) {
  if (status === "ACTIVE" || status === "SATISFIED") {
    return <Tag color="success">{status === "ACTIVE" ? "启用" : "已满足"}</Tag>;
  }
  if (status === "MISSING_EVIDENCE") {
    return <Tag color="warning">缺少证据</Tag>;
  }
  if (status === "GAP") {
    return <Tag color="error">存在差距</Tag>;
  }
  return <Tag>{status === "INACTIVE" ? "停用" : status}</Tag>;
}

function riskTagColor(risk: string) {
  if (risk === "HIGH") return "error";
  if (risk === "MEDIUM") return "warning";
  return "blue";
}

type SystemConfigForm = {
  value: string | number | boolean;
  reason: string;
  confirmedHighRisk: boolean;
};

type SystemConfigScope = "system" | "tenant";

function isHardLockedConfig(item: SystemConfigItem) {
  return [
    "medkernel.runtime.feature-flags.audit-persistence.enabled",
    "medkernel.runtime.feature-flags.domestic-crypto.enabled",
  ].includes(item.key);
}

function configInput(item: SystemConfigItem | null) {
  if (item?.valueType === "BOOLEAN") {
    return (
      <Select
        options={[
          { value: "true", label: "启用" },
          { value: "false", label: "停用" },
        ]}
      />
    );
  }
  if (item?.valueType === "INTEGER" || item?.valueType === "LONG") {
    return <InputNumber min={0} className="mk-full-width" />;
  }
  return <Input />;
}

export function SystemConfigPanel({ canManage }: { canManage: boolean }) {
  const { message } = App.useApp();
  const [scope, setScope] = useState<SystemConfigScope>("system");
  const [tenantId, setTenantId] = useState("default");
  const systemConfigs = useSystemConfigs();
  const tenantConfigs = useTenantSystemConfigs(tenantId, undefined, scope === "tenant");
  const configs = scope === "tenant" ? tenantConfigs : systemConfigs;
  const update = useUpdateSystemConfig();
  const updateTenant = useUpdateTenantSystemConfig();
  const [selected, setSelected] = useState<SystemConfigItem | null>(null);
  const [form] = Form.useForm<SystemConfigForm>();

  function openEdit(item: SystemConfigItem) {
    setSelected(item);
    form.setFieldsValue({
      value:
        item.valueType === "INTEGER" || item.valueType === "LONG" ? Number(item.value) : item.value,
      reason: "",
      confirmedHighRisk: false,
    });
  }

  async function save() {
    if (!selected) return;
    try {
      const values = await form.validateFields();
      const payload = {
        value: String(values.value),
        reason: values.reason.trim(),
        expectedVersion:
          scope === "tenant" && selected.source === "SYSTEM_INHERITED"
            ? undefined
            : selected.version,
        confirmedHighRisk: Boolean(values.confirmedHighRisk),
      };
      if (scope === "tenant") {
        await updateTenant.mutateAsync({
          tenantId: tenantId.trim(),
          key: selected.key,
          payload,
        });
      } else {
        await update.mutateAsync({
          key: selected.key,
          payload,
        });
      }
      message.success(scope === "tenant" ? "租户配置已保存并记录审计" : "系统配置已保存并记录审计");
      setSelected(null);
      form.resetFields();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "系统配置保存失败"));
    }
  }

  if (configs.isLoading) return <PageState state="loading" />;
  if (configs.isError) {
    return (
      <PageState
        state="error"
        title="系统配置读取失败"
        action={
          <Button icon={<ReloadOutlined />} onClick={() => void configs.refetch()}>
            重试
          </Button>
        }
      />
    );
  }

  return (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Alert
        type="info"
        showIcon
        message="配置以数据库为唯一运行来源"
        description="高风险项必须确认影响；审计持久化与国密安全底座保持红线锁定，其余运行能力可按系统默认或租户覆盖灰度。"
      />
      <Space wrap>
        <Segmented
          value={scope}
          options={[
            { label: "系统默认", value: "system" },
            { label: "租户覆盖", value: "tenant" },
          ]}
          onChange={(value) => setScope(value as SystemConfigScope)}
        />
        {scope === "tenant" && (
          <Input
            aria-label="租户 ID"
            value={tenantId}
            onChange={(event) => setTenantId(event.target.value)}
            placeholder="tenant id"
            className="mk-config-tenant-input"
          />
        )}
      </Space>
      <Table<SystemConfigItem>
        rowKey="key"
        dataSource={configs.data ?? []}
        pagination={{ pageSize: 20, hideOnSinglePage: true }}
        scroll={{ x: "max-content" }}
        columns={[
          {
            title: "配置项",
            dataIndex: "displayName",
            render: (_value, item) => (
              <Space direction="vertical" size={0}>
                <Text strong>{item.displayName}</Text>
                <Text type="secondary">{item.key}</Text>
              </Space>
            ),
          },
          {
            title: "当前值",
            dataIndex: "value",
            render: (value, item) =>
              item.valueType === "BOOLEAN" ? (
                <Tag color={value === "true" ? "success" : "default"}>
                  {value === "true" ? "启用" : "停用"}
                </Tag>
              ) : (
                <Text code>{value}</Text>
              ),
          },
          {
            title: "来源",
            dataIndex: "source",
            render: (source) => (
              <Tag color={source === "SYSTEM_INHERITED" ? "default" : "blue"}>
                {source === "SYSTEM_INHERITED" ? "继承系统" : source}
              </Tag>
            ),
          },
          {
            title: "风险",
            dataIndex: "risk",
            render: (risk, item) => (
              <Space>
                <Tag color={riskTagColor(risk)}>{risk}</Tag>
                {item.protectedConfig && <Tag color="red">受保护</Tag>}
              </Space>
            ),
          },
          { title: "责任方", dataIndex: "owner" },
          {
            title: "更新时间",
            dataIndex: "updatedAt",
            render: formatDateTime,
          },
          {
            title: "操作",
            render: (_value, item) => (
              <Button
                aria-label={`编辑 ${item.displayName}`}
                icon={<EditOutlined />}
                disabled={
                  !canManage || isHardLockedConfig(item) || (scope === "tenant" && !tenantId.trim())
                }
                onClick={() => openEdit(item)}
              />
            ),
          },
        ]}
      />
      <Modal
        title={scope === "tenant" ? "编辑租户配置" : "编辑系统配置"}
        open={Boolean(selected)}
        okText="保存配置"
        okButtonProps={{ "aria-label": "保存配置" }}
        confirmLoading={scope === "tenant" ? updateTenant.isPending : update.isPending}
        onOk={() => void save()}
        onCancel={() => setSelected(null)}
        destroyOnClose
      >
        {selected && (
          <Form form={form} layout="vertical">
            <Alert
              type={selected.risk === "HIGH" ? "warning" : "info"}
              showIcon
              message={selected.displayName}
              description={selected.description}
            />
            <Form.Item name="value" label="配置值" rules={[{ required: true }]}>
              {configInput(selected)}
            </Form.Item>
            <Form.Item
              name="reason"
              label="变更原因"
              rules={[{ required: true, whitespace: true, message: "请填写可审计的变更原因" }]}
            >
              <Input.TextArea rows={3} maxLength={500} showCount />
            </Form.Item>
            {selected.risk === "HIGH" && (
              <Form.Item
                name="confirmedHighRisk"
                valuePropName="checked"
                rules={[
                  {
                    validator: (_rule, value) =>
                      value ? Promise.resolve() : Promise.reject(new Error("请确认高风险影响")),
                  },
                ]}
              >
                <Checkbox>确认高风险影响</Checkbox>
              </Form.Item>
            )}
          </Form>
        )}
      </Modal>
    </Space>
  );
}

type DataPermissionForm = Omit<DataPermissionPolicyPayload, "allowedColumns"> & {
  allowedColumns: string[];
};

const scopeLevels: Array<{
  field: keyof DataPermissionForm;
  orgLevel: OrgUnit["level"];
  facilityTypes?: Array<NonNullable<OrgUnit["facilityType"]>>;
  label: string;
}> = [
  { field: "groupId", orgLevel: "REGION", label: "集团/联合体" },
  { field: "hospitalId", orgLevel: "FACILITY", facilityTypes: ["HOSPITAL"], label: "医院" },
  { field: "campusId", orgLevel: "CAMPUS", label: "院区" },
  {
    field: "siteId",
    orgLevel: "FACILITY",
    facilityTypes: ["COMMUNITY_HEALTH_CENTER", "TOWNSHIP_CLINIC", "STATION"],
    label: "基层服务点",
  },
  { field: "departmentId", orgLevel: "DEPARTMENT", label: "科室" },
];

export function DataPermissionPanel({ canManage }: { canManage: boolean }) {
  const { message } = App.useApp();
  const policies = useDataPermissionPolicies();
  const [orgSearch, setOrgSearch] = useState("");
  const orgUnits = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    keyword: orgSearch || undefined,
    status: "ACTIVE",
  });
  const upsert = useUpsertDataPermissionPolicy();
  const [selected, setSelected] = useState<DataPermissionPolicy | null>();
  const [form] = Form.useForm<DataPermissionForm>();
  const modalOpen = selected !== undefined;
  const activeOrgUnits = (orgUnits.data?.items ?? []).filter(
    (unit) => unit.status === undefined || unit.status === "ACTIVE",
  );
  const scopeOptions = new Map(
    scopeLevels.map(({ field, orgLevel, facilityTypes }) => [
      field,
      activeOrgUnits
        .filter(
          (unit) =>
            unit.level === orgLevel &&
            (!facilityTypes ||
              (unit.facilityType !== null &&
                unit.facilityType !== undefined &&
                facilityTypes.includes(unit.facilityType))),
        )
        .map((unit) => ({
          value: unit.id ?? unit.code,
          label: `${unit.name} · ${unit.code}`,
        })),
    ]),
  );
  const specialtyOptions = Array.from(
    new Map(
      activeOrgUnits
        .filter((unit) => unit.specialtyId)
        .map((unit) => [
          unit.specialtyId as string,
          {
            value: unit.specialtyId as string,
            label: `${unit.specialtyId} · ${unit.name}`,
          },
        ]),
    ).values(),
  );

  function openCreate() {
    setSelected(null);
    form.setFieldsValue({
      action: "READ",
      minDataLevel: "HOSPITAL",
      allowedColumns: [],
      status: "ACTIVE",
      reason: "",
    });
  }

  function openEdit(policy: DataPermissionPolicy) {
    setSelected(policy);
    form.setFieldsValue({
      resourceType: policy.resourceType,
      action: policy.action,
      minDataLevel: policy.minDataLevel,
      allowedColumns: policy.allowedColumns,
      groupId: policy.groupId ?? undefined,
      hospitalId: policy.hospitalId ?? undefined,
      campusId: policy.campusId ?? undefined,
      siteId: policy.siteId ?? undefined,
      departmentId: policy.departmentId ?? undefined,
      specialtyId: policy.specialtyId ?? undefined,
      status: policy.status,
      reason: "",
      expectedVersion: policy.version,
    });
  }

  async function save() {
    try {
      const values = await form.validateFields();
      await upsert.mutateAsync({
        ...values,
        reason: values.reason.trim(),
        expectedVersion: selected?.version,
      });
      message.success("数据权限策略已保存");
      setSelected(undefined);
      form.resetFields();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "数据权限策略保存失败"));
    }
  }

  if (policies.isLoading) return <PageState state="loading" />;
  if (policies.isError) {
    return <PageState state="error" title="数据权限策略读取失败" onRetry={policies.refetch} />;
  }

  return (
    <Space direction="vertical" size="middle" className="mk-full-width">
      {orgUnits.isError && (
        <Alert
          type="warning"
          showIcon
          message="组织目录暂时不可用"
          description="当前仍可查看策略；恢复组织目录后才能新增或调整作用域。"
        />
      )}
      <Space className="mk-push-inline-start-auto">
        <Button
          type="primary"
          icon={<PlusOutlined />}
          disabled={!canManage || orgUnits.isError}
          onClick={openCreate}
        >
          新增策略
        </Button>
      </Space>
      <Table<DataPermissionPolicy>
        rowKey="policyId"
        dataSource={policies.data ?? []}
        pagination={false}
        scroll={{ x: "max-content" }}
        columns={[
          { title: "资源类型", dataIndex: "resourceType" },
          { title: "动作", dataIndex: "action", render: (value) => <Tag>{value}</Tag> },
          { title: "最小范围", dataIndex: "minDataLevel" },
          {
            title: "允许字段",
            dataIndex: "allowedColumns",
            render: (columns: string[]) => columns.join(", "),
          },
          { title: "状态", dataIndex: "status", render: statusTag },
          {
            title: "操作",
            render: (_value, policy) => (
              <Button
                aria-label={`编辑数据权限 ${policy.resourceType} ${policy.action}`}
                icon={<EditOutlined />}
                disabled={!canManage}
                onClick={() => openEdit(policy)}
              />
            ),
          },
        ]}
      />
      <Modal
        title={selected ? "编辑数据权限策略" : "新增数据权限策略"}
        open={modalOpen}
        okText="保存策略"
        confirmLoading={upsert.isPending}
        onOk={() => void save()}
        onCancel={() => setSelected(undefined)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="resourceType"
            label="资源类型"
            rules={[{ required: true, whitespace: true }]}
          >
            <Input placeholder="如 clinical_case" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="action" label="动作" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: "READ", label: "读取" },
                    { value: "EXPORT", label: "导出" },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="minDataLevel" label="最小数据范围" rules={[{ required: true }]}>
                <Select
                  options={["DEPARTMENT", "HOSPITAL", "GROUP"].map((value) => ({
                    value,
                    label: value,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="allowedColumns"
            label="允许字段"
            rules={[{ required: true, type: "array", min: 1 }]}
          >
            <Select mode="tags" tokenSeparators={[","]} placeholder="输入字段后回车" />
          </Form.Item>
          {scopeLevels.map(({ field }, index) =>
            index % 2 === 0 ? (
              <Row gutter={12} key={field}>
                {scopeLevels.slice(index, index + 2).map((scope) => (
                  <Col span={12} key={scope.field}>
                    <Form.Item name={scope.field} label={scope.label}>
                      <Select
                        allowClear
                        showSearch
                        filterOption={false}
                        onSearch={setOrgSearch}
                        placeholder={`选择${scope.label}`}
                        options={scopeOptions.get(scope.field)}
                        loading={orgUnits.isLoading}
                        disabled={orgUnits.isError}
                        notFoundContent={`暂无可选${scope.label}`}
                      />
                    </Form.Item>
                  </Col>
                ))}
              </Row>
            ) : null,
          )}
          <Form.Item name="specialtyId" label="专科">
            <Select
              allowClear
              showSearch
              filterOption={false}
              onSearch={setOrgSearch}
              placeholder="选择专科"
              options={specialtyOptions}
              loading={orgUnits.isLoading}
              disabled={orgUnits.isError}
              notFoundContent="组织目录中暂无专科"
            />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { value: "ACTIVE", label: "启用" },
                { value: "INACTIVE", label: "停用" },
              ]}
            />
          </Form.Item>
          <Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true }]}>
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

type MaskingForm = MaskingRulePayload;

export function MaskingRulePanel({ canManage }: { canManage: boolean }) {
  const { message } = App.useApp();
  const rules = useMaskingRules();
  const upsert = useUpsertMaskingRule();
  const [selected, setSelected] = useState<MaskingRule | null>();
  const [form] = Form.useForm<MaskingForm>();
  const modalOpen = selected !== undefined;

  function openCreate() {
    setSelected(null);
    form.setFieldsValue({
      scenarioCode: "DEFAULT",
      strategy: "KEEP_FIRST_LAST",
      maskChar: "*",
      prefixKeep: 1,
      suffixKeep: 0,
      status: "ACTIVE",
      reason: "",
    });
  }

  function openEdit(rule: MaskingRule) {
    setSelected(rule);
    form.setFieldsValue({
      resourceType: rule.resourceType,
      fieldName: rule.fieldName,
      scenarioCode: rule.scenarioCode ?? "DEFAULT",
      strategy: rule.strategy,
      maskChar: rule.maskChar,
      prefixKeep: rule.prefixKeep,
      suffixKeep: rule.suffixKeep,
      status: rule.status,
      reason: "",
      expectedVersion: rule.version,
    });
  }

  async function save() {
    try {
      const values = await form.validateFields();
      await upsert.mutateAsync({
        ...values,
        reason: values.reason.trim(),
        expectedVersion: selected?.version,
      });
      message.success("脱敏规则已保存");
      setSelected(undefined);
      form.resetFields();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "脱敏规则保存失败"));
    }
  }

  if (rules.isLoading) return <PageState state="loading" />;
  if (rules.isError) {
    return <PageState state="error" title="脱敏规则读取失败" onRetry={rules.refetch} />;
  }

  return (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Space className="mk-push-inline-start-auto">
        <Button type="primary" icon={<PlusOutlined />} disabled={!canManage} onClick={openCreate}>
          新增规则
        </Button>
      </Space>
      <Table<MaskingRule>
        rowKey="ruleId"
        dataSource={rules.data ?? []}
        pagination={false}
        scroll={{ x: "max-content" }}
        columns={[
          { title: "资源类型", dataIndex: "resourceType" },
          { title: "字段", dataIndex: "fieldName" },
          { title: "场景", dataIndex: "scenarioCode", render: (value) => value || "DEFAULT" },
          { title: "策略", dataIndex: "strategy", render: (value) => <Tag>{value}</Tag> },
          {
            title: "保留范围",
            render: (_value, rule) =>
              rule.strategy === "REDACT" || rule.strategy === "FIXED"
                ? "不保留原文"
                : `保留前 ${rule.prefixKeep} 位 / 后 ${rule.suffixKeep} 位`,
          },
          { title: "状态", dataIndex: "status", render: statusTag },
          {
            title: "操作",
            render: (_value, rule) => (
              <Button
                aria-label={`编辑脱敏规则 ${rule.fieldName}`}
                icon={<EditOutlined />}
                disabled={!canManage}
                onClick={() => openEdit(rule)}
              />
            ),
          },
        ]}
      />
      <Modal
        title={selected ? "编辑脱敏规则" : "新增脱敏规则"}
        open={modalOpen}
        okText="保存规则"
        confirmLoading={upsert.isPending}
        onOk={() => void save()}
        onCancel={() => setSelected(undefined)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="resourceType"
                label="资源类型"
                rules={[{ required: true, whitespace: true }]}
              >
                <Input placeholder="如 clinical_case" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="fieldName"
                label="字段名"
                rules={[
                  { required: true, whitespace: true },
                  { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: "字段名格式不合法" },
                ]}
              >
                <Input placeholder="如 patientName" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="scenarioCode" label="使用场景">
            <Input placeholder="DEFAULT" />
          </Form.Item>
          <Form.Item name="strategy" label="脱敏策略" rules={[{ required: true }]}>
            <Select
              options={[
                { value: "REDACT", label: "全部遮蔽" },
                { value: "KEEP_LAST", label: "保留末尾" },
                { value: "KEEP_FIRST_LAST", label: "保留首尾" },
                { value: "EMAIL", label: "邮箱脱敏" },
                { value: "FIXED", label: "固定替换" },
              ]}
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="maskChar" label="遮蔽字符" rules={[{ required: true }]}>
                <Input maxLength={4} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="prefixKeep" label="保留前缀" rules={[{ required: true }]}>
                <InputNumber min={0} max={32} className="mk-full-width" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="suffixKeep" label="保留后缀" rules={[{ required: true }]}>
                <InputNumber min={0} max={32} className="mk-full-width" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { value: "ACTIVE", label: "启用" },
                { value: "INACTIVE", label: "停用" },
              ]}
            />
          </Form.Item>
          <Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true }]}>
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

const DIMENSION_LABEL: Record<InteropAssessmentItem["dimension"], string> = {
  DATA_RESOURCE: "数据资源",
  STANDARDIZATION: "标准化",
  INFRASTRUCTURE: "基础设施",
  APPLICATION_EFFECT: "应用效果",
};

export function InteropAssessmentPanel() {
  const [versionInput, setVersionInput] = useState("IOT-2026");
  const [standardVersion, setStandardVersion] = useState("IOT-2026");
  const assessment = useInteropAssessment(standardVersion);

  if (assessment.isLoading) return <PageState state="loading" />;
  if (assessment.isError) {
    return (
      <PageState
        state="error"
        title="互操作测评读取失败"
        description="请核对测评标准版本，或稍后重试。"
        action={
          <Button icon={<ReloadOutlined />} onClick={() => void assessment.refetch()}>
            重试
          </Button>
        }
      />
    );
  }

  const snapshot = assessment.data;
  return (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Input.Search
        aria-label="测评标准版本"
        value={versionInput}
        onChange={(event) => setVersionInput(event.target.value)}
        onSearch={(value) => setStandardVersion(value.trim())}
        enterButton="读取测评"
        placeholder="输入测评标准版本"
      />
      {!snapshot ? (
        <PageState state="empty" title="暂无测评结果" />
      ) : (
        <>
          <Row gutter={[12, 12]}>
            <Col xs={12} lg={6}>
              <Statistic title="指标总数" value={snapshot.totalItems} />
            </Col>
            <Col xs={12} lg={6}>
              <Statistic title="已满足" value={snapshot.satisfiedItems} />
            </Col>
            <Col xs={12} lg={6}>
              <Statistic title="差距" value={snapshot.gapItems} />
            </Col>
            <Col xs={12} lg={6}>
              <Statistic title="缺少证据" value={snapshot.missingEvidenceItems} />
            </Col>
          </Row>
          <Alert
            type={snapshot.gapItems + snapshot.missingEvidenceItems > 0 ? "warning" : "success"}
            showIcon
            icon={<SafetyCertificateOutlined />}
            message={`测评版本 ${snapshot.standardVersion}`}
            description="只把已落库且可追溯的证据计为满足；缺少证据时保持差距状态，不推断达标。"
          />
          <Table<InteropAssessmentItem>
            rowKey="itemId"
            dataSource={snapshot.items}
            pagination={false}
            scroll={{ x: "max-content" }}
            columns={[
              { title: "指标编码", dataIndex: "itemCode" },
              { title: "指标", dataIndex: "itemName" },
              {
                title: "维度",
                dataIndex: "dimension",
                render: (value: InteropAssessmentItem["dimension"]) => DIMENSION_LABEL[value],
              },
              { title: "状态", dataIndex: "status", render: statusTag },
              { title: "证据数", dataIndex: "evidenceCount" },
              {
                title: "差距原因",
                dataIndex: "gapReason",
                render: (value) => value || "无",
              },
            ]}
            expandable={{
              expandedRowRender: (item) =>
                item.evidences.length === 0 ? (
                  <Text type="secondary">该指标暂无真实证据映射。</Text>
                ) : (
                  <Table
                    rowKey="mapId"
                    dataSource={item.evidences}
                    pagination={false}
                    size="small"
                    columns={[
                      { title: "来源", dataIndex: "sourceType" },
                      { title: "证据引用", dataIndex: "evidenceRef" },
                      { title: "摘要", dataIndex: "evidenceSummary" },
                      { title: "指纹", dataIndex: "payloadDigest" },
                    ]}
                  />
                ),
            }}
          />
        </>
      )}
    </Space>
  );
}
