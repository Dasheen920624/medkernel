import { useEffect, useState } from "react";
import {
  Alert,
  App,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { DeleteOutlined, EditOutlined, PlusOutlined } from "@ant-design/icons";

import {
  useCreateDeclarativeAsset,
  useDeclarativeAsset,
  useDeclarativeAssets,
  useUpdateDeclarativeAsset,
  type DeclarativeAssetSummary,
  type DeclarativeAssetType,
  type DeclarativeAssetUpsertPayload,
} from "@/shared/api/hooks";
import { customerEnumLabel } from "@/shared/config/customerLabels";

const ASSET_TYPES: Array<{ value: DeclarativeAssetType; label: string }> = [
  { value: "VALUE_SET", label: "值集" },
  { value: "FORMULA", label: "公式与量表" },
  { value: "ORDER_SET", label: "医嘱套餐" },
  { value: "ACTION_CARD", label: "动作卡" },
];

const FORMULA_OPTIONS = [
  "CKD_EPI_2021_EGFR",
  "COCKCROFT_GAULT_CRCL",
  "MOSTELLER_BSA",
  "BMI",
].map((value) => ({ value, label: value }));

const ORDER_ITEM_OPTIONS = [
  { value: "MEDICATION", label: "药品" },
  { value: "LAB", label: "检验" },
  { value: "IMAGING", label: "影像" },
  { value: "PROCEDURE", label: "操作/治疗" },
  { value: "NURSING", label: "护理" },
];

const ACTION_OPTIONS = [
  { value: "NAVIGATE", label: "页面导航" },
  { value: "OPEN_FORM", label: "打开表单" },
  { value: "SUGGEST_ORDER", label: "建议医嘱" },
  { value: "ACKNOWLEDGE", label: "确认知晓" },
];

const ACTION_CODE_OPTIONS = [
  { value: "INFO", label: "信息提示" },
  { value: "REMIND", label: "提醒" },
  { value: "STRONG_REMINDER", label: "强提醒" },
  { value: "BLOCK", label: "阻断建议" },
  { value: "SUGGEST_ORDER", label: "建议医嘱" },
  { value: "AUTO_DOCUMENT", label: "辅助记录" },
];

const ACTION_SEVERITY_OPTIONS = [
  { value: "LOW", label: "低" },
  { value: "MEDIUM", label: "中" },
  { value: "HIGH", label: "高" },
  { value: "CRITICAL", label: "红线" },
];

const ACTION_INDICATOR_OPTIONS = [
  { value: "info", label: "信息" },
  { value: "warning", label: "警示" },
  { value: "critical", label: "严重" },
];

interface FormValues {
  assetIdentity: string;
  applicableScope: string;
  sourceRef: string;
  name?: string;
  codeSystem?: string;
  members?: Array<{ code: string; display: string }>;
  runtimeFunction?: string;
  inputs?: Array<{ name: string; fieldPath: string; unit?: string }>;
  outputUnit?: string;
  items?: Array<{
    itemType: string;
    codeSystem: string;
    code: string;
    display: string;
    required: boolean;
  }>;
  title?: string;
  actionCode?: string;
  atSeverity?: string;
  indicator?: string;
  summary?: string;
  detail?: string;
  sourceLabel?: string;
  sourceUrl?: string;
  sourceEvidenceLevel?: string;
  suggestions?: Array<{
    label: string;
    actionType: string;
    payloadJson?: string;
  }>;
  overrideReasons?: Array<{ reason: string }>;
  requiresPhysicianConfirmation?: boolean;
}

function initialValues(type: DeclarativeAssetType): Partial<FormValues> {
  if (type === "VALUE_SET") {
    return { applicableScope: "ALL", members: [{ code: "", display: "" }] };
  }
  if (type === "FORMULA") {
    return {
      applicableScope: "ALL",
      runtimeFunction: "BMI",
      inputs: [{ name: "", fieldPath: "", unit: "" }],
    };
  }
  if (type === "ORDER_SET") {
    return {
      applicableScope: "ALL",
      items: [
        {
          itemType: "LAB",
          codeSystem: "",
          code: "",
          display: "",
          required: false,
        },
      ],
    };
  }
  return {
    applicableScope: "ALL",
    actionCode: "REMIND",
    atSeverity: "LOW",
    indicator: "info",
    suggestions: [
      {
        label: "",
        actionType: "ACKNOWLEDGE",
        payloadJson: "",
      },
    ],
    overrideReasons: [],
    requiresPhysicianConfirmation: false,
  };
}

function valuesFromContent(
  asset: DeclarativeAssetSummary,
  content: Record<string, unknown>,
): FormValues {
  return {
    assetIdentity: asset.assetIdentity,
    applicableScope: asset.applicableScope,
    sourceRef: asset.sourceRef,
    name: typeof content.name === "string" ? content.name : undefined,
    codeSystem: typeof content.codeSystem === "string" ? content.codeSystem : undefined,
    members: Array.isArray(content.members) ? (content.members as FormValues["members"]) : undefined,
    runtimeFunction:
      typeof content.runtimeFunction === "string" ? content.runtimeFunction : undefined,
    inputs: Array.isArray(content.inputs) ? (content.inputs as FormValues["inputs"]) : undefined,
    outputUnit:
      typeof content.output === "object" &&
      content.output !== null &&
      typeof (content.output as { unit?: unknown }).unit === "string"
        ? (content.output as { unit: string }).unit
        : undefined,
    items: Array.isArray(content.items) ? (content.items as FormValues["items"]) : undefined,
    title: typeof content.title === "string" ? content.title : undefined,
    actionCode: typeof content.actionCode === "string" ? content.actionCode : undefined,
    atSeverity: typeof content.atSeverity === "string" ? content.atSeverity : undefined,
    indicator: typeof content.indicator === "string" ? content.indicator : undefined,
    summary: typeof content.summary === "string" ? content.summary : undefined,
    detail: typeof content.detail === "string" ? content.detail : undefined,
    sourceLabel:
      typeof content.source === "object" &&
      content.source !== null &&
      typeof (content.source as { label?: unknown }).label === "string"
        ? (content.source as { label: string }).label
        : undefined,
    sourceUrl:
      typeof content.source === "object" &&
      content.source !== null &&
      typeof (content.source as { url?: unknown }).url === "string"
        ? (content.source as { url: string }).url
        : undefined,
    sourceEvidenceLevel:
      typeof content.source === "object" &&
      content.source !== null &&
      typeof (content.source as { evidenceLevel?: unknown }).evidenceLevel === "string"
        ? (content.source as { evidenceLevel: string }).evidenceLevel
        : undefined,
    suggestions: Array.isArray(content.suggestions)
      ? (content.suggestions as Array<Record<string, unknown>>).map((suggestion) => ({
          label: typeof suggestion.label === "string" ? suggestion.label : "",
          actionType: typeof suggestion.actionType === "string" ? suggestion.actionType : "ACKNOWLEDGE",
          payloadJson:
            typeof suggestion.payload === "object" && suggestion.payload !== null
              ? JSON.stringify(suggestion.payload)
              : "",
        }))
      : undefined,
    overrideReasons: Array.isArray(content.overrideReasons)
      ? (content.overrideReasons as unknown[])
          .filter((reason): reason is string => typeof reason === "string")
          .map((reason) => ({ reason }))
      : undefined,
    requiresPhysicianConfirmation:
      typeof content.requiresPhysicianConfirmation === "boolean"
        ? content.requiresPhysicianConfirmation
        : undefined,
  };
}

function buildContent(type: DeclarativeAssetType, values: FormValues): Record<string, unknown> {
  if (type === "VALUE_SET") {
    return {
      schemaVersion: "1.0",
      name: values.name?.trim(),
      codeSystem: values.codeSystem?.trim(),
      members: (values.members ?? []).map((member) => ({
        code: member.code.trim(),
        display: member.display.trim(),
      })),
    };
  }
  if (type === "FORMULA") {
    return {
      schemaVersion: "1.0",
      name: values.name?.trim(),
      runtimeFunction: values.runtimeFunction,
      inputs: (values.inputs ?? []).map((input) => ({
        name: input.name.trim(),
        fieldPath: input.fieldPath.trim(),
        ...(input.unit?.trim() ? { unit: input.unit.trim() } : {}),
      })),
      output: { dataType: "number", unit: values.outputUnit?.trim() },
    };
  }
  if (type === "ORDER_SET") {
    return {
      schemaVersion: "1.0",
      name: values.name?.trim(),
      requiresPhysicianConfirmation: true,
      items: (values.items ?? []).map((item) => ({
        itemType: item.itemType,
        codeSystem: item.codeSystem.trim(),
        code: item.code.trim(),
        display: item.display.trim(),
        required: Boolean(item.required),
      })),
    };
  }
  return {
    schemaVersion: "1.0",
    title: values.title?.trim(),
    actionCode: values.actionCode,
    atSeverity: values.atSeverity,
    indicator: values.indicator,
    summary: values.summary?.trim(),
    detail: values.detail?.trim(),
    source: {
      label: values.sourceLabel?.trim(),
      ...(values.sourceUrl?.trim() ? { url: values.sourceUrl.trim() } : {}),
      ...(values.sourceEvidenceLevel?.trim()
        ? { evidenceLevel: values.sourceEvidenceLevel.trim() }
        : {}),
    },
    suggestions: (values.suggestions ?? []).map((suggestion) => ({
      label: suggestion.label.trim(),
      actionType: suggestion.actionType,
      ...parseSuggestionPayload(suggestion.payloadJson),
    })),
    overrideReasons: (values.overrideReasons ?? [])
      .map((item) => item.reason.trim())
      .filter(Boolean),
    requiresPhysicianConfirmation:
      shouldRequirePhysicianConfirmation(values) || Boolean(values.requiresPhysicianConfirmation),
  };
}

function parseSuggestionPayload(payloadJson: string | undefined): Record<string, unknown> {
  const source = payloadJson?.trim();
  if (!source) {
    return {};
  }
  const parsed = JSON.parse(source) as unknown;
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("动作卡建议内容必须是配置对象");
  }
  return { payload: parsed as Record<string, unknown> };
}

function shouldRequirePhysicianConfirmation(values: FormValues): boolean {
  return (
    values.atSeverity === "HIGH" ||
    values.atSeverity === "CRITICAL" ||
    values.actionCode === "BLOCK" ||
    values.actionCode === "STRONG_REMINDER" ||
    values.actionCode === "SUGGEST_ORDER" ||
    (values.suggestions ?? []).some((suggestion) => suggestion.actionType === "SUGGEST_ORDER")
  );
}

function ArrayRemoveButton({ onClick }: { onClick: () => void }) {
  return (
    <Button
      type="text"
      danger
      icon={<DeleteOutlined />}
      aria-label="删除条目"
      onClick={onClick}
    />
  );
}

function ValueSetFields() {
  return (
    <>
      <Form.Item name="name" label="名称" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.Item name="codeSystem" label="编码体系" rules={[{ required: true }]}>
        <Input placeholder="例如 ATC、LOINC 或院内标准字典编码" />
      </Form.Item>
      <Form.List name="members">
        {(fields, { add, remove }) => (
          <Space direction="vertical" className="mk-full-width">
            {fields.map(({ key, ...field }) => (
              <Space key={key} align="baseline" wrap>
                <Form.Item
                  {...field}
                  name={[field.name, "code"]}
                  label="成员编码"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  {...field}
                  name={[field.name, "display"]}
                  label="成员名称"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <ArrayRemoveButton onClick={() => remove(field.name)} />
              </Space>
            ))}
            <Button icon={<PlusOutlined />} onClick={() => add({ code: "", display: "" })}>
              添加成员
            </Button>
          </Space>
        )}
      </Form.List>
    </>
  );
}

function FormulaFields() {
  return (
    <>
      <Form.Item name="name" label="名称" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.Item name="runtimeFunction" label="受控公式" rules={[{ required: true }]}>
        <Select options={FORMULA_OPTIONS} />
      </Form.Item>
      <Form.Item name="outputUnit" label="输出单位" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.List name="inputs">
        {(fields, { add, remove }) => (
          <Space direction="vertical" className="mk-full-width">
            {fields.map(({ key, ...field }) => (
              <Space key={key} align="baseline" wrap>
                <Form.Item
                  {...field}
                  name={[field.name, "name"]}
                  label="输入名"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  {...field}
                  name={[field.name, "fieldPath"]}
                  label="字段路径"
                  rules={[{ required: true }]}
                >
                  <Input placeholder="例如 patient.weightKg" />
                </Form.Item>
                <Form.Item {...field} name={[field.name, "unit"]} label="输入单位">
                  <Input />
                </Form.Item>
                <ArrayRemoveButton onClick={() => remove(field.name)} />
              </Space>
            ))}
            <Button
              icon={<PlusOutlined />}
              onClick={() => add({ name: "", fieldPath: "", unit: "" })}
            >
              添加输入
            </Button>
          </Space>
        )}
      </Form.List>
    </>
  );
}

function OrderSetFields() {
  return (
    <>
      <Alert
        type="warning"
        showIcon
        message="医嘱套餐只产生建议，运行时始终要求医师逐次确认，禁止自动开嘱。"
      />
      <Form.Item name="name" label="名称" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.List name="items">
        {(fields, { add, remove }) => (
          <Space direction="vertical" className="mk-full-width">
            {fields.map(({ key, ...field }) => (
              <Space key={key} align="baseline" wrap>
                <Form.Item
                  {...field}
                  name={[field.name, "itemType"]}
                  label="项目类型"
                  rules={[{ required: true }]}
                >
                  <Select options={ORDER_ITEM_OPTIONS} className="mk-select-md" />
                </Form.Item>
                <Form.Item
                  {...field}
                  name={[field.name, "codeSystem"]}
                  label="编码体系"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  {...field}
                  name={[field.name, "code"]}
                  label="项目编码"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  {...field}
                  name={[field.name, "display"]}
                  label="项目名称"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item {...field} name={[field.name, "required"]} valuePropName="checked">
                  <Checkbox>必选</Checkbox>
                </Form.Item>
                <ArrayRemoveButton onClick={() => remove(field.name)} />
              </Space>
            ))}
            <Button
              icon={<PlusOutlined />}
              onClick={() =>
                add({
                  itemType: "LAB",
                  codeSystem: "",
                  code: "",
                  display: "",
                  required: false,
                })
              }
            >
              添加项目
            </Button>
          </Space>
        )}
      </Form.List>
    </>
  );
}

function ActionCardFields() {
  return (
    <>
      <Form.Item name="title" label="标题" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Space wrap className="mk-full-width">
        <Form.Item name="actionCode" label="动作码" rules={[{ required: true }]}>
          <Select options={ACTION_CODE_OPTIONS} className="mk-select-md" />
        </Form.Item>
        <Form.Item name="atSeverity" label="风险级别" rules={[{ required: true }]}>
          <Select options={ACTION_SEVERITY_OPTIONS} className="mk-select-md" />
        </Form.Item>
        <Form.Item name="indicator" label="卡片级别" rules={[{ required: true }]}>
          <Select options={ACTION_INDICATOR_OPTIONS} className="mk-select-md" />
        </Form.Item>
        <Form.Item name="requiresPhysicianConfirmation" valuePropName="checked">
          <Checkbox>需医师确认</Checkbox>
        </Form.Item>
      </Space>
      <Form.Item name="summary" label="摘要" rules={[{ required: true }]}>
        <Input.TextArea rows={2} />
      </Form.Item>
      <Form.Item name="detail" label="详细说明" rules={[{ required: true }]}>
        <Input.TextArea rows={3} />
      </Form.Item>
      <Space wrap className="mk-full-width">
        <Form.Item name="sourceLabel" label="来源标签" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="sourceEvidenceLevel" label="证据等级">
          <Input placeholder="例如 GUIDELINE、CONSENSUS" />
        </Form.Item>
        <Form.Item name="sourceUrl" label="来源链接">
          <Input />
        </Form.Item>
      </Space>
      <Form.List name="suggestions">
        {(fields, { add, remove }) => (
          <Space direction="vertical" className="mk-full-width">
            {fields.map(({ key, ...field }) => (
              <Space key={key} align="baseline" wrap>
                <Form.Item
                  {...field}
                  name={[field.name, "label"]}
                  label="建议名称"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  {...field}
                  name={[field.name, "actionType"]}
                  label="建议类型"
                  rules={[{ required: true }]}
                >
                  <Select options={ACTION_OPTIONS} className="mk-select-md" />
                </Form.Item>
                <Form.Item {...field} name={[field.name, "payloadJson"]} label="建议补充内容">
                  <Input.TextArea rows={2} placeholder='例如 {"orderSetRef":"ORDER.CKD.REVIEW"}' />
                </Form.Item>
                <ArrayRemoveButton onClick={() => remove(field.name)} />
              </Space>
            ))}
            <Button
              icon={<PlusOutlined />}
              onClick={() =>
                add({
                  label: "",
                  actionType: "ACKNOWLEDGE",
                  payloadJson: "",
                })
              }
            >
              添加建议
            </Button>
          </Space>
        )}
      </Form.List>
      <Form.List name="overrideReasons">
        {(fields, { add, remove }) => (
          <Space direction="vertical" className="mk-full-width">
            {fields.map(({ key, ...field }) => (
              <Space key={key} align="baseline" wrap>
                <Form.Item
                  {...field}
                  name={[field.name, "reason"]}
                  label="可覆盖原因"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <ArrayRemoveButton onClick={() => remove(field.name)} />
              </Space>
            ))}
            <Button icon={<PlusOutlined />} onClick={() => add({ reason: "" })}>
              添加可覆盖原因
            </Button>
          </Space>
        )}
      </Form.List>
    </>
  );
}

export interface DeclarativeAssetWorkbenchProps {
  canWrite: boolean;
}

export default function DeclarativeAssetWorkbench({
  canWrite,
}: DeclarativeAssetWorkbenchProps) {
  const { message } = App.useApp();
  const [assetType, setAssetType] = useState<DeclarativeAssetType>("VALUE_SET");
  const [editing, setEditing] = useState<DeclarativeAssetSummary | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<FormValues>();
  const list = useDeclarativeAssets(assetType, { page: 1, size: 50 });
  const detail = useDeclarativeAsset(editing?.versionId, open && Boolean(editing));
  const create = useCreateDeclarativeAsset();
  const update = useUpdateDeclarativeAsset();
  const typeLabel = ASSET_TYPES.find((item) => item.value === assetType)?.label ?? assetType;

  useEffect(() => {
    if (!open || !editing || !detail.data) return;
    form.setFieldsValue(valuesFromContent(editing, detail.data.content));
  }, [detail.data, editing, form, open]);

  const beginCreate = () => {
    setEditing(null);
    form.resetFields();
    setOpen(true);
  };

  const beginEdit = (asset: DeclarativeAssetSummary) => {
    setEditing(asset);
    form.resetFields();
    setOpen(true);
  };

  const save = async () => {
    try {
      const values = await form.validateFields();
      const request: DeclarativeAssetUpsertPayload = {
        assetType,
        assetIdentity: values.assetIdentity.trim(),
        applicableScope: values.applicableScope.trim(),
        sourceRef: values.sourceRef.trim(),
        content: buildContent(assetType, values),
      };
      if (editing) {
        await update.mutateAsync({ versionId: editing.versionId, request });
      } else {
        await create.mutateAsync(request);
      }
      message.success(editing ? "资产草稿已更新" : "资产草稿已创建");
      setOpen(false);
      setEditing(null);
    } catch (error: unknown) {
      if (Array.isArray((error as { errorFields?: unknown[] }).errorFields)) return;
      message.error("资产草稿保存失败，请按错误提示检查内容。");
    }
  };

  const columns: ColumnsType<DeclarativeAssetSummary> = [
    { title: "资产编码", dataIndex: "assetIdentity" },
    { title: "版本", dataIndex: "versionNo", width: 100 },
    {
      title: "状态",
      dataIndex: "status",
      width: 110,
      render: (status: string) => (
        <Tag color={status === "DRAFT" ? "default" : "success"}>{customerEnumLabel(status)}</Tag>
      ),
    },
    {
      title: "组织范围",
      dataIndex: "organizationScope",
    },
    {
      title: "适用范围",
      dataIndex: "applicableScope",
    },
    { title: "来源依据", dataIndex: "sourceRef" },
    {
      title: "操作",
      key: "actions",
      width: 100,
      render: (_value, record) => (
        <Button
          icon={<EditOutlined />}
          disabled={!canWrite || record.status !== "DRAFT"}
          onClick={() => beginEdit(record)}
        >
          编辑
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Alert
        type="info"
        showIcon
        message="配置资产独立维护"
        description="正文按类型校验，版本号自动递增；发布修订在上线时选取精确版本。已发布内容不可原地修改。字段目录与完整路径分别由各自工作台维护。"
      />
      <Tabs
        activeKey={assetType}
        onChange={(key) => setAssetType(key as DeclarativeAssetType)}
        items={ASSET_TYPES.map((item) => ({ key: item.value, label: item.label }))}
      />
      <Button
        type="primary"
        disabled={!canWrite}
        icon={<PlusOutlined />}
        aria-label={`新建${typeLabel}`}
        onClick={beginCreate}
      >
        新建{typeLabel}
      </Button>
      <Table
        rowKey="versionId"
        dataSource={list.data?.items ?? []}
        columns={columns}
        loading={list.isLoading}
        pagination={false}
        locale={{
          emptyText: list.isError ? "配置资产读取失败，请重试" : `暂无${typeLabel}`,
        }}
      />

      <Modal
        title={editing ? `编辑${typeLabel}草稿` : `新建${typeLabel}`}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={save}
        okText="保存草稿"
        cancelText="取消"
        width={920}
        confirmLoading={create.isPending || update.isPending}
        destroyOnClose
      >
        {detail.isError && <Alert type="error" message="资产正文读取失败，暂不能编辑。" />}
        <Form
          form={form}
          layout="vertical"
          disabled={detail.isLoading}
          initialValues={initialValues(assetType)}
        >
          <Form.Item name="assetIdentity" label="资产编码" rules={[{ required: true }]}>
            <Input disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item
            name="applicableScope"
            label="适用范围"
            rules={[{ required: true }]}
            extra="ALL 表示全部患者与上下文；需要限制时填写受控适用范围表达式。"
          >
            <Input />
          </Form.Item>
          <Form.Item name="sourceRef" label="来源依据" rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          {assetType === "VALUE_SET" && <ValueSetFields />}
          {assetType === "FORMULA" && <FormulaFields />}
          {assetType === "ORDER_SET" && <OrderSetFields />}
          {assetType === "ACTION_CARD" && <ActionCardFields />}
        </Form>
      </Modal>
    </Space>
  );
}
