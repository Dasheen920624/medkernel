/**
 * 上下文字段目录维护抽屉（RULE-01 / PATH-01，P2/P5 前台可维护）。
 *
 * <p>信息科按业务层级浏览字段目录（系统派生只读 + 租户自定义），并新增/删除租户自定义字段。
 * 系统字段标 SYSTEM 不可删；租户字段标 TENANT 可删。系统字段由 canonical 派生，诚实只读。
 */
import { useMemo, useState } from "react";
import { App, Button, Drawer, Form, Input, Select, Space, Table, Tag } from "antd";
import type { TableProps } from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";

import {
  useContextFieldCatalog,
  useCreateContextField,
  useDeleteContextField,
  type ContextFieldDescriptor,
} from "@/shared/api/hooks";

const { Option } = Select;

const DATA_TYPE_OPTIONS = ["number", "string", "boolean", "date", "code", "list"];

export interface FieldCatalogManagerProps {
  open: boolean;
  onClose: () => void;
}

interface CreateFormValues {
  category: string;
  group: string;
  resourceType: string;
  fieldPath: string;
  displayName: string;
  dataType: string;
  unit?: string;
  codeSystem?: string;
  description?: string;
}

export function FieldCatalogManager({ open, onClose }: FieldCatalogManagerProps) {
  const { message } = App.useApp();
  const catalog = useContextFieldCatalog();
  const createField = useCreateContextField();
  const deleteField = useDeleteContextField();
  const [form] = Form.useForm<CreateFormValues>();
  const [keyword, setKeyword] = useState("");

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

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await createField.mutateAsync(values);
      message.success("已新增租户自定义字段");
      form.resetFields();
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error("新增字段失败");
    }
  };

  const handleDelete = async (record: ContextFieldDescriptor) => {
    if (!record.fieldId) return;
    try {
      await deleteField.mutateAsync(record.fieldId);
      message.success("已删除租户自定义字段");
    } catch {
      message.error("删除字段失败");
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
      title: "来源",
      dataIndex: "source",
      width: 90,
      render: (source?: string | null) =>
        source === "TENANT" ? <Tag color="green">租户</Tag> : <Tag>系统</Tag>,
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
            aria-label={`删除字段 ${record.fieldPath}`}
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
          <Form.Item name="category" rules={[{ required: true, message: "业务域" }]}>
            <Input aria-label="业务域" placeholder="业务域，如 医嘱信息" />
          </Form.Item>
          <Form.Item name="group" rules={[{ required: true, message: "分组" }]}>
            <Input aria-label="分组" placeholder="分组，如 用药医嘱" />
          </Form.Item>
          <Form.Item name="resourceType" rules={[{ required: true, message: "资源" }]}>
            <Input aria-label="资源类型" placeholder="资源类型，如 Medication" />
          </Form.Item>
          <Form.Item name="fieldPath" rules={[{ required: true, message: "路径" }]}>
            <Input aria-label="字段路径" placeholder="字段路径，如 medications[].xx" />
          </Form.Item>
          <Form.Item name="displayName" rules={[{ required: true, message: "字段名" }]}>
            <Input aria-label="字段名" placeholder="字段名" />
          </Form.Item>
          <Form.Item
            name="dataType"
            rules={[{ required: true, message: "类型" }]}
            initialValue="string"
          >
            <Select aria-label="数据类型" className="w-28">
              {DATA_TYPE_OPTIONS.map((t) => (
                <Option key={t} value={t}>
                  {t}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="codeSystem">
            <Input aria-label="绑定字典" placeholder="字典(可空) 如 ICD-10" />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              loading={createField.isPending}
              onClick={handleCreate}
            >
              新增字段
            </Button>
          </Form.Item>
        </Form>

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
          pagination={{ pageSize: 12, showTotal: (t) => `共 ${t} 个字段` }}
          className="medkernel-table"
        />
      </Space>
    </Drawer>
  );
}

export default FieldCatalogManager;
