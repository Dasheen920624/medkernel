/**
 * 字段目录草稿抽屉。
 *
 * <p>具备 {@code context.write} 权限的当前授权责任人编排工作字段目录：平台字段只允许覆盖展示元数据，院内新增字段统一进入
 * {@code extensions.local.*} 命名空间并形成真实运行数据落点。当前调整结果显式固化为统一资产草稿后，
 * 才可进入平台标准版本或机构生效版本；当前已生效版本始终保持不可变。
 */
import { useEffect, useMemo, useState } from "react";
import { Alert, App, Button, Drawer, Form, Input, Select, Space, Table, Tag } from "antd";
import type { TableProps } from "antd";
import { DeleteOutlined, PlusOutlined, SaveOutlined } from "@ant-design/icons";

import {
  useContextFieldCatalog,
  useCreateContextField,
  useDeleteContextField,
  useSecurityProfile,
  useSnapshotContextFieldCatalogDraft,
  useUpdateContextField,
  type ContextFieldDescriptor,
  type ContextFieldUpsertPayload,
} from "@/shared/api/hooks";

export interface FieldCatalogManagerProps {
  open: boolean;
  onClose: () => void;
}

interface FieldOverrideFormValues {
  selectedFieldPath?: string;
  extensionKey?: string;
  category?: string;
  group?: string;
  displayName: string;
  dataType?: string;
  unit?: string;
  codeSystem?: string;
  description?: string;
}

type MaintenanceMode = "override" | "extension";

const EXTENSION_PREFIX = "extensions.local.";

function isExtensionField(field: ContextFieldDescriptor): boolean {
  return field.resourceType === "Extension" || field.fieldPath.startsWith(EXTENSION_PREFIX);
}

export function FieldCatalogManager({ open, onClose }: FieldCatalogManagerProps) {
  const { message } = App.useApp();
  const security = useSecurityProfile();
  const canManage =
    security.data?.permissions.some((permission) => permission.code === "context.write") ?? false;
  const catalog = useContextFieldCatalog(undefined, { enabled: open });
  const createField = useCreateContextField();
  const updateField = useUpdateContextField();
  const deleteField = useDeleteContextField();
  const snapshotDraft = useSnapshotContextFieldCatalogDraft();
  const [form] = Form.useForm<FieldOverrideFormValues>();
  const dataType = Form.useWatch("dataType", form);
  const [keyword, setKeyword] = useState("");
  const [selectedField, setSelectedField] = useState<ContextFieldDescriptor | null>(null);
  const [mode, setMode] = useState<MaintenanceMode>("override");
  let saveButtonText = "保存覆盖";
  if (mode === "extension") {
    saveButtonText = selectedField ? "更新扩展字段" : "保存扩展字段";
  }

  useEffect(() => {
    if (mode !== "extension") return;
    if (dataType !== "number" && form.getFieldValue("unit")) {
      form.setFieldValue("unit", undefined);
    }
    if (dataType !== "code" && form.getFieldValue("codeSystem")) {
      form.setFieldValue("codeSystem", undefined);
    }
  }, [dataType, form, mode]);

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
    if (isExtensionField(field)) {
      setMode("extension");
      form.setFieldsValue({
        selectedFieldPath: undefined,
        extensionKey: field.fieldPath.slice(EXTENSION_PREFIX.length),
        category: field.category,
        group: field.group,
        displayName: field.displayName,
        dataType: field.dataType,
        unit: field.unit ?? undefined,
        codeSystem: field.codeSystem ?? undefined,
        description: field.description ?? undefined,
      });
      return;
    }
    setMode("override");
    form.setFieldsValue({
      selectedFieldPath: field.fieldPath,
      extensionKey: undefined,
      category: undefined,
      group: undefined,
      displayName: field.displayName,
      dataType: undefined,
      unit: undefined,
      codeSystem: field.codeSystem ?? undefined,
      description: field.description ?? undefined,
    });
  };

  const buildOverridePayload = (
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

  const beginExtension = () => {
    if (!canManage) return;
    setMode("extension");
    setSelectedField(null);
    form.resetFields();
    form.setFieldsValue({
      category: "院内扩展",
      dataType: "string",
    });
  };

  const beginOverride = () => {
    if (!canManage) return;
    setMode("override");
    setSelectedField(null);
    form.resetFields();
  };

  const handleSave = async () => {
    if (!canManage) return;
    try {
      const values = await form.validateFields();
      if (mode === "extension") {
        const extensionKey = values.extensionKey?.trim();
        if (!extensionKey) return;
        const payload: ContextFieldUpsertPayload = {
          category: values.category?.trim() ?? "",
          group: values.group?.trim() ?? "",
          resourceType: "Extension",
          fieldPath: `${EXTENSION_PREFIX}${extensionKey}`,
          displayName: values.displayName.trim(),
          dataType: values.dataType ?? "string",
          unit: values.unit?.trim() || undefined,
          codeSystem: values.codeSystem?.trim() || undefined,
          description: values.description?.trim() || undefined,
        };
        if (selectedField?.source === "TENANT" && selectedField.fieldId) {
          await updateField.mutateAsync({ fieldId: selectedField.fieldId, payload });
        } else {
          await createField.mutateAsync(payload);
        }
        message.success(selectedField ? "已更新院内扩展字段" : "已新增院内扩展字段");
        return;
      }
      const field =
        selectedField ??
        (catalog.data ?? []).find((item) => item.fieldPath === values.selectedFieldPath) ??
        null;
      if (!field) {
        message.warning("请先选择字段");
        return;
      }
      const payload = buildOverridePayload(field, values);
      if (field.source === "TENANT" && field.fieldId) {
        await updateField.mutateAsync({ fieldId: field.fieldId, payload });
      } else {
        await createField.mutateAsync(payload);
      }
      message.success("已保存字段覆盖");
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(mode === "extension" ? "保存院内扩展字段失败" : "保存字段覆盖失败");
    }
  };

  const handleDelete = async (record: ContextFieldDescriptor) => {
    if (!canManage || !record.fieldId) return;
    try {
      await deleteField.mutateAsync(record.fieldId);
      if (selectedField?.fieldId === record.fieldId) {
        setSelectedField(null);
        form.resetFields();
      }
      message.success(isExtensionField(record) ? "已删除院内扩展字段" : "已删除字段覆盖");
    } catch {
      message.error(isExtensionField(record) ? "删除院内扩展字段失败" : "删除字段覆盖失败");
    }
  };

  const handleSnapshotDraft = async () => {
    if (!canManage) return;
    try {
      const draft = await snapshotDraft.mutateAsync();
      message.success(`字段目录草稿 ${draft.versionNo} 已固化`);
    } catch {
      message.error("固化字段目录草稿失败");
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
      title: "字段结构",
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
            aria-label={`${isExtensionField(record) ? "删除扩展" : "删除覆盖"} ${record.fieldPath}`}
            icon={<DeleteOutlined />}
            loading={deleteField.isPending}
            disabled={!canManage}
            onClick={(event) => {
              event.stopPropagation();
              handleDelete(record);
            }}
          />
        ) : (
          <span className="text-gray-400 text-xs">只读</span>
        ),
    },
  ];

  return (
    <Drawer title="字段目录草稿" width={920} open={open} onClose={onClose} destroyOnClose>
      <Space direction="vertical" size="large" className="mk-full-width">
        <Alert
          showIcon
          type="info"
          message="这里编排下一版本的字段目录"
          description="本次调整需固化为自动编号的字段目录草稿，才可进入平台标准版本或机构生效版本；当前已激活版本不会被直接修改。"
        />
        {!canManage ? (
          <Alert
            showIcon
            type="info"
            message="当前账号仅可查看字段目录"
            description="覆盖平台字段、新增院内扩展字段和删除机构字段均需要字段写入权限。"
          />
        ) : null}
        <Space wrap>
          <Button
            type={mode === "override" ? "primary" : "default"}
            disabled={!canManage}
            onClick={beginOverride}
          >
            调整平台字段展示
          </Button>
          <Button
            type={mode === "extension" ? "primary" : "default"}
            icon={<PlusOutlined />}
            aria-label="新增院内扩展字段"
            disabled={!canManage}
            onClick={beginExtension}
          >
            新增院内扩展字段
          </Button>
          <Button
            icon={<SaveOutlined />}
            aria-label="固化为字段目录草稿"
            disabled={!canManage || catalog.isError}
            loading={snapshotDraft.isPending}
            onClick={handleSnapshotDraft}
          >
            固化为字段目录草稿
          </Button>
        </Space>
        <Form form={form} layout="inline" className="flex flex-wrap gap-2">
          {mode === "override" ? (
            <Form.Item
              label="选择字段"
              name="selectedFieldPath"
              rules={[{ required: true, message: "请选择字段" }]}
            >
              <Select
                className="min-w-72"
                showSearch
                disabled={catalog.isError}
                placeholder="选择平台字段"
                optionFilterProp="label"
                onChange={handleSelectField}
                options={(catalog.data ?? [])
                  .filter((field) => !isExtensionField(field))
                  .map((field) => ({
                    value: field.fieldPath,
                    label: `${field.displayName} ${field.fieldPath}`,
                  }))}
              />
            </Form.Item>
          ) : (
            <>
              <Form.Item
                label="扩展字段键"
                name="extensionKey"
                rules={[
                  { required: true, message: "请输入扩展字段键" },
                  {
                    pattern: /^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*){0,2}$/,
                    message: "仅支持小写字母、数字、下划线，最多三级",
                  },
                ]}
              >
                <Input
                  aria-label="扩展字段键"
                  addonBefore={EXTENSION_PREFIX}
                  disabled={Boolean(selectedField?.fieldId)}
                  placeholder="dialysis_access_type"
                />
              </Form.Item>
              <Form.Item
                label="业务域"
                name="category"
                rules={[{ required: true, message: "请输入业务域" }]}
              >
                <Input aria-label="业务域" placeholder="院内扩展" />
              </Form.Item>
              <Form.Item
                label="字段分组"
                name="group"
                rules={[{ required: true, message: "请输入字段分组" }]}
              >
                <Input aria-label="字段分组" placeholder="专科数据" />
              </Form.Item>
            </>
          )}
          <Form.Item name="displayName" rules={[{ required: true, message: "字段名" }]}>
            <Input aria-label="展示名" placeholder="展示名" />
          </Form.Item>
          {mode === "extension" ? (
            <>
              <Form.Item
                label="数据类型"
                name="dataType"
                rules={[{ required: true, message: "请选择数据类型" }]}
              >
                <Select
                  className="min-w-28"
                  options={[
                    { value: "string", label: "文本" },
                    { value: "number", label: "数值" },
                    { value: "boolean", label: "布尔" },
                    { value: "date", label: "日期" },
                    { value: "code", label: "编码" },
                    { value: "list", label: "列表" },
                  ]}
                />
              </Form.Item>
              <Form.Item name="unit">
                <Input
                  aria-label="单位"
                  placeholder="仅数值字段可填"
                  disabled={dataType !== "number"}
                />
              </Form.Item>
            </>
          ) : null}
          <Form.Item
            name="codeSystem"
            dependencies={["dataType"]}
            rules={[
              ({ getFieldValue }) => ({
                validator: async (_rule, value) => {
                  if (mode === "extension" && getFieldValue("dataType") === "code" && !value) {
                    throw new Error("编码字段必须绑定字典");
                  }
                },
              }),
            ]}
          >
            <Input
              aria-label="绑定字典"
              placeholder="绑定字典，如 ICD-10"
              disabled={mode === "extension" && dataType !== "code"}
            />
          </Form.Item>
          <Form.Item name="description">
            <Input aria-label="说明" placeholder="说明(可空)" />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={createField.isPending || updateField.isPending}
              disabled={catalog.isError || !canManage}
              onClick={handleSave}
            >
              {saveButtonText}
            </Button>
          </Form.Item>
        </Form>
        {catalog.isError ? (
          <Alert
            showIcon
            type="warning"
            message="字段目录暂不可用"
            description="当前无法读取标准字段目录，已暂停保存覆盖；请恢复字段目录服务后再维护字段元数据。"
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
