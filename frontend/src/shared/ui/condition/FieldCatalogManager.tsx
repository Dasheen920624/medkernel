/**
 * 上下文字段目录维护抽屉（RULE-01 / PATH-01，P2/P5 前台可维护）。
 *
 * <p>信息科按业务层级浏览字段目录（平台派生只读 + 租户元数据覆盖），并维护展示名/字典/说明。
 * 字段路径、资源类型和数据类型由 canonical 目录派生，前台不允许手写，避免配置出规则引擎不可读路径。
 */
import { useMemo, useState } from "react";
import { Alert, App, Button, Drawer, Form, Input, Select, Space, Table, Tag } from "antd";
import type { TableProps } from "antd";
import { DeleteOutlined, SaveOutlined } from "@ant-design/icons";

import {
  useContextFieldCatalog,
  useCreateContextField,
  useDeleteContextField,
  useUpdateContextField,
  type ContextFieldDescriptor,
  type ContextFieldUpsertPayload,
} from "@/shared/api/hooks";

export interface FieldCatalogManagerProps {
  open: boolean;
  onClose: () => void;
}

interface FieldOverrideFormValues {
  selectedFieldPath: string;
  displayName: string;
  codeSystem?: string;
  description?: string;
}

export function FieldCatalogManager({ open, onClose }: FieldCatalogManagerProps) {
  const { message } = App.useApp();
  const catalog = useContextFieldCatalog();
  const createField = useCreateContextField();
  const updateField = useUpdateContextField();
  const deleteField = useDeleteContextField();
  const [form] = Form.useForm<FieldOverrideFormValues>();
  const [keyword, setKeyword] = useState("");
  const [selectedField, setSelectedField] = useState<ContextFieldDescriptor | null>(null);

  const rows = useMemo(() => {
    const list = catalog.data ?? [];
    const kw = keyword.trim().toLowerCase();
    if (!kw) return list;
    return list.filter(
      (f) =>
        f.fieldPath.toLowerCase().includes(kw) ||
        f.displayName.toLowerCase().includes(kw) ||
        f.category.toLowerCase().includes(kw) ||
        f.group.toLowerCase().includes(kw),
    );
  }, [catalog.data, keyword]);

  const handleSelectField = (fieldPath: string) => {
    const field = (catalog.data ?? []).find((item) => item.fieldPath === fieldPath);
    if (!field) return;
    setSelectedField(field);
    form.setFieldsValue({
      selectedFieldPath: field.fieldPath,
      displayName: field.displayName,
      codeSystem: field.codeSystem ?? undefined,
      description: field.description ?? undefined,
    });
  };

  const buildPayload = (
    field: ContextFieldDescriptor,
    values: FieldOverrideFormValues,
  ): ContextFieldUpsertPayload => ({
    category: field.category,
    group: field.group,
    resourceType: field.resourceType,
    fieldPath: field.fieldPath,
    displayName: values.displayName,
    dataType: field.dataType,
    unit: field.unit ?? undefined,
    codeSystem: values.codeSystem || undefined,
    description: values.description || undefined,
  });

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const field =
        selectedField ??
        (catalog.data ?? []).find((item) => item.fieldPath === values.selectedFieldPath) ??
        null;
      if (!field) {
        message.warning("请先选择字段");
        return;
      }
      const payload = buildPayload(field, values);
      if (field.source === "TENANT" && field.fieldId) {
        await updateField.mutateAsync({ fieldId: field.fieldId, payload });
      } else {
        await createField.mutateAsync(payload);
      }
      message.success("已保存字段覆盖");
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error("保存字段覆盖失败");
    }
  };

  const handleDelete = async (record: ContextFieldDescriptor) => {
    if (!record.fieldId) return;
    try {
      await deleteField.mutateAsync(record.fieldId);
      if (selectedField?.fieldId === record.fieldId) {
        setSelectedField(null);
        form.resetFields();
      }
      message.success("已删除字段覆盖");
    } catch {
      message.error("删除字段覆盖失败");
    }
  };

  const columns: TableProps<ContextFieldDescriptor>["columns"] = [
    { title: "业务域", dataIndex: "category", width: 110 },
    { title: "分组", dataIndex: "group", width: 130 },
    { title: "字段名", dataIndex: "displayName", width: 140 },
    {
      title: "字段路径",
      dataIndex: "fieldPath",
      render: (path: string) => <span className="font-normal text-xs">{path}</span>,
    },
    { title: "类型", dataIndex: "dataType", width: 80 },
    {
      title: "字典",
      dataIndex: "codeSystem",
      width: 100,
      render: (cs?: string | null) => (cs ? <Tag color="cyan">{cs}</Tag> : "—"),
    },
    {
      title: "接入字段",
      key: "contract",
      width: 150,
      render: (_v: unknown, record: ContextFieldDescriptor) =>
        record.payloadKey && record.propertyName
          ? `${record.payloadKey}.${record.propertyName}`
          : "—",
    },
    {
      title: "Schema",
      dataIndex: "jsonSchemaType",
      width: 90,
      render: (type?: string | null) => type || "—",
    },
    {
      title: "接入",
      dataIndex: "externalWritable",
      width: 90,
      render: (externalWritable?: boolean | null) =>
        externalWritable === false ? (
          <Tag color="orange">派生</Tag>
        ) : (
          <Tag color="green">可接入</Tag>
        ),
    },
    {
      title: "来源",
      dataIndex: "source",
      width: 90,
      render: (source?: string | null) =>
        source === "TENANT" ? <Tag color="green">服务机构</Tag> : <Tag>平台</Tag>,
    },
    {
      title: "操作",
      key: "action",
      width: 80,
      render: (_v: unknown, record: ContextFieldDescriptor) =>
        record.source === "TENANT" && record.fieldId ? (
          <Button
            size="small"
            danger
            aria-label={`删除覆盖 ${record.fieldPath}`}
            icon={<DeleteOutlined />}
            loading={deleteField.isPending}
            onClick={() => handleDelete(record)}
          />
        ) : (
          <span className="text-gray-400 text-xs">只读</span>
        ),
    },
  ];

  return (
    <Drawer title="上下文字段目录维护" width={920} open={open} onClose={onClose} destroyOnClose>
      <Space direction="vertical" size="large" className="mk-full-width">
        <Form form={form} layout="inline" className="flex flex-wrap gap-2">
          <Form.Item
            label="选择字段"
            name="selectedFieldPath"
            rules={[{ required: true, message: "请选择字段" }]}
          >
            <Select
              className="min-w-72"
              showSearch
              disabled={catalog.isError}
              placeholder="选择 canonical 字段"
              optionFilterProp="label"
              onChange={handleSelectField}
              options={(catalog.data ?? []).map((field) => ({
                value: field.fieldPath,
                label: `${field.displayName} ${field.fieldPath}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="displayName" rules={[{ required: true, message: "字段名" }]}>
            <Input aria-label="展示名" placeholder="展示名" />
          </Form.Item>
          <Form.Item name="codeSystem">
            <Input aria-label="绑定字典" placeholder="绑定字典，如 ICD-10" />
          </Form.Item>
          <Form.Item name="description">
            <Input aria-label="说明" placeholder="说明(可空)" />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={createField.isPending || updateField.isPending}
              disabled={catalog.isError}
              onClick={handleSave}
            >
              保存覆盖
            </Button>
          </Form.Item>
        </Form>
        {catalog.isError ? (
          <Alert
            showIcon
            type="warning"
            message="字段目录暂不可用"
            description="当前无法读取 canonical 字段目录，已暂停保存覆盖；请恢复接口后再维护字段元数据。"
          />
        ) : null}
        {selectedField ? (
          <Space size="small" wrap>
            <Tag color="blue">{selectedField.category}</Tag>
            <Tag>{selectedField.group}</Tag>
            <Tag>{selectedField.resourceType}</Tag>
            <Tag>{selectedField.dataType}</Tag>
            <span className="font-normal text-xs">{selectedField.fieldPath}</span>
          </Space>
        ) : null}

        <Input.Search
          aria-label="搜索字段"
          placeholder="搜索字段名 / 路径 / 业务域"
          allowClear
          onChange={(e) => setKeyword(e.target.value)}
          className="max-w-md"
        />

        <Table
          rowKey={(r) => r.fieldId ?? r.fieldPath}
          size="small"
          loading={catalog.isLoading}
          columns={columns}
          dataSource={rows}
          onRow={(record) => ({
            onClick: () => handleSelectField(record.fieldPath),
          })}
          pagination={{ pageSize: 12, showTotal: (t) => `共 ${t} 个字段` }}
          className="medkernel-table"
        />
      </Space>
    </Drawer>
  );
}

export default FieldCatalogManager;
