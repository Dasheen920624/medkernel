import {
  DisconnectOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  Upload,
} from "antd";
import { useMemo, useState } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useCommitPersonnelImport,
  useCreateIdentityBinding,
  useDelegatedAuthStatus,
  useIdentityBindings,
  usePersonnel,
  usePreviewPersonnelImport,
  useSecurityProfile,
  useUnbindIdentityBinding,
  type IdentityBinding as IdentityBindingRecord,
  type IdentityProviderType,
  type PersonnelImportResponse,
  type SecurityProfile,
} from "@/shared/api/hooks";
import {
  connectionStatusLabel,
  delegatedModeLabel,
  identityProviderLabel,
  importRowStatusLabel,
} from "@/shared/config/customerLabels";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";

const { Text } = Typography;
const { TextArea } = Input;
const { Dragger } = Upload;
const IDENTITY_BINDING_PAGE_SIZE = 20;
const PERSONNEL_REFERENCE_PAGE_SIZE = 20;

type CreateBindingForm = {
  userId: string;
  providerType: IdentityProviderType;
  externalSubject: string;
  reason: string;
};

type UnbindForm = { reason: string };

const PERSONNEL_GOVERNANCE_ROLES = new Set([
  "system-superadmin",
  "platform-governance-admin",
  "organization-admin",
  "identity-access-admin",
]);

function hasPermission(profile: SecurityProfile | undefined, code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function hasPersonnelGovernanceRole(profile: SecurityProfile | undefined) {
  return profile?.roles.some((role) => PERSONNEL_GOVERNANCE_ROLES.has(role.code)) ?? false;
}

function formatTime(value: string) {
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return "时间未知";
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(timestamp);
}

export default function IdentityBinding() {
  const { message } = App.useApp();
  const security = useSecurityProfile();
  const delegated = useDelegatedAuthStatus();
  const [bindingPage, setBindingPage] = useState(1);
  const bindings = useIdentityBindings({ page: bindingPage, size: IDENTITY_BINDING_PAGE_SIZE });
  const [userSearch, setUserSearch] = useState("");
  const hasPersonnelRole = hasPersonnelGovernanceRole(security.data);
  const canReadPersonnel = hasPersonnelRole && hasPermission(security.data, "org.read");
  const canManage = hasPersonnelRole && hasPermission(security.data, "org.write");
  const personnel = usePersonnel(
    { page: 1, size: PERSONNEL_REFERENCE_PAGE_SIZE, keyword: userSearch || undefined },
    { enabled: canReadPersonnel },
  );
  const createMutation = useCreateIdentityBinding();
  const unbindMutation = useUnbindIdentityBinding();
  const previewMutation = usePreviewPersonnelImport();
  const commitMutation = useCommitPersonnelImport();
  const [createOpen, setCreateOpen] = useState(false);
  const [batchOpen, setBatchOpen] = useState(false);
  const [batchFile, setBatchFile] = useState<File>();
  const [batchResult, setBatchResult] = useState<PersonnelImportResponse>();
  const [unbindTarget, setUnbindTarget] = useState<IdentityBindingRecord | null>(null);
  const [createForm] = Form.useForm<CreateBindingForm>();
  const [unbindForm] = Form.useForm<UnbindForm>();

  const peopleByUserId = useMemo(
    () =>
      new Map(
        (personnel.data?.items ?? [])
          .filter((person) => person.userId)
          .map((person) => [person.userId as string, person]),
      ),
    [personnel.data?.items],
  );
  const userOptions = useMemo(
    () =>
      (personnel.data?.items ?? [])
        .filter((person) => person.userId)
        .map((person) => ({
          value: person.userId as string,
          label: `${person.displayName} · ${person.employeeNo}`,
        })),
    [personnel.data?.items],
  );

  const refresh = async () => {
    const refreshTasks: Array<Promise<unknown>> = [
      bindings.refetch(),
      delegated.refetch(),
      security.refetch(),
    ];
    if (canReadPersonnel) {
      refreshTasks.push(personnel.refetch());
    }
    await Promise.all(refreshTasks);
  };

  const submitCreate = async (values: CreateBindingForm) => {
    try {
      await createMutation.mutateAsync({
        userId: values.userId,
        providerType: values.providerType,
        externalSubject: values.externalSubject.trim(),
        reason: values.reason.trim(),
      });
      message.success("身份来源已绑定");
      setCreateOpen(false);
      createForm.resetFields();
    } catch (error) {
      message.error(getApiErrorMessage(error, "身份来源绑定失败"));
    }
  };

  const submitUnbind = async (values: UnbindForm) => {
    if (!unbindTarget) return;
    try {
      await unbindMutation.mutateAsync({
        bindingId: unbindTarget.bindingId,
        reason: values.reason.trim(),
        expectedVersion: unbindTarget.version,
      });
      message.success("身份来源已解绑，历史证据继续保留");
      setUnbindTarget(null);
      unbindForm.resetFields();
    } catch (error) {
      message.error(getApiErrorMessage(error, "身份来源解绑失败"));
    }
  };

  const previewBatch = async () => {
    if (!batchFile) {
      message.warning("请先选择身份匹配文件");
      return;
    }
    try {
      setBatchResult(await previewMutation.mutateAsync(batchFile));
    } catch (error) {
      message.error(getApiErrorMessage(error, "批量匹配预检失败"));
    }
  };

  const commitBatch = async () => {
    if (!batchResult) return;
    try {
      const result = await commitMutation.mutateAsync(batchResult.jobId);
      setBatchResult(result);
      message.success(`已完成 ${result.successRows} 人的身份匹配`);
      await bindings.refetch();
    } catch (error) {
      message.error(getApiErrorMessage(error, "批量匹配失败"));
    }
  };

  const closeBatch = () => {
    setBatchOpen(false);
    setBatchFile(undefined);
    setBatchResult(undefined);
  };

  if (security.isLoading || bindings.isLoading || delegated.isLoading) {
    return (
      <PageShell title="身份来源" description="正在读取机构统一身份状态">
        <PageState state="loading" />
      </PageShell>
    );
  }
  if (security.isError || bindings.isError || delegated.isError) {
    return (
      <PageShell title="身份来源" description="机构统一身份状态读取失败">
        <PageState
          state="error"
          title="暂时无法读取身份来源"
          description="请检查登录状态、机构范围和统一身份服务连接。"
          action={<Button onClick={refresh}>重试</Button>}
        />
      </PageShell>
    );
  }
  if (!security.data || !delegated.data || !bindings.data) {
    return (
      <PageShell title="身份来源" description="暂无可用身份数据">
        <PageState state="empty" title="暂无身份来源状态" />
      </PageShell>
    );
  }

  const bindingItems = bindings.data.items;
  const activeCount = bindingItems.filter((item) => item.status === "ACTIVE").length;
  const providerCount = new Set(
    bindingItems.filter((item) => item.status === "ACTIVE").map((item) => item.providerType),
  ).size;
  const isReady = delegated.data.status === "READY";
  let delegatedAlertType: "success" | "warning" | "info" = "info";
  if (delegated.data.enabled) delegatedAlertType = "warning";
  if (isReady) delegatedAlertType = "success";
  let batchActionLabel = "开始预检";
  if (batchResult) batchActionLabel = "确认匹配";
  if (batchResult?.status === "COMPLETED" || batchResult?.status === "PARTIAL") {
    batchActionLabel = "完成";
  }

  return (
    <>
      <PageShell
        title="身份来源"
        description="维护院内工号、统一认证和国密证书与人员账号的唯一关系"
        primary={
          canManage ? (
            <Button type="primary" icon={<UploadOutlined />} onClick={() => setBatchOpen(true)}>
              批量匹配身份
            </Button>
          ) : undefined
        }
        extras={
          <Space wrap>
            {canManage && (
              <Button icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                单个绑定
              </Button>
            )}
            <Button icon={<ReloadOutlined />} onClick={refresh}>
              刷新
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size="large" className="mk-full-width">
          {!canManage && (
            <Alert
              type="info"
              showIcon
              message="当前为只读视图"
              description="只有机构管理员或人员与访问管理员可以新增、批量匹配或解除身份来源。"
            />
          )}
          <Alert
            type={delegatedAlertType}
            showIcon
            message={`${delegatedModeLabel(delegated.data.mode)} · ${connectionStatusLabel(
              delegated.data.status,
            )}`}
            description={delegated.data.message}
          />
          <Card>
            <Space size="large" wrap>
              <Statistic title="有效身份关系" value={activeCount} />
              <Statistic title="已使用身份来源" value={providerCount} />
              <Statistic
                title="统一身份连接"
                value={connectionStatusLabel(delegated.data.status)}
              />
            </Space>
          </Card>
          <Card title="人员身份关系">
            <Table<IdentityBindingRecord>
              rowKey="bindingId"
              dataSource={bindingItems}
              pagination={{
                current: bindings.data.page,
                pageSize: bindings.data.size,
                total: bindings.data.total,
                showSizeChanger: false,
                showTotal: (total) => `共 ${total} 条`,
                onChange: (page) => setBindingPage(page),
              }}
              locale={{ emptyText: "当前机构尚未绑定身份来源" }}
              scroll={{ x: 820 }}
              columns={[
                {
                  title: "人员",
                  dataIndex: "userId",
                  render: (userId: string) => {
                    const person = peopleByUserId.get(userId);
                    return (
                      <Space direction="vertical" size={0}>
                        <Text strong>{person?.displayName ?? "人员信息待同步"}</Text>
                        <Text type="secondary">
                          {person ? `人员编号：${person.employeeNo}` : "账号已存在"}
                        </Text>
                      </Space>
                    );
                  },
                },
                {
                  title: "身份来源",
                  dataIndex: "providerType",
                  render: (value: string) => <Tag>{identityProviderLabel(value)}</Tag>,
                },
                { title: "脱敏标识", dataIndex: "subjectHint" },
                {
                  title: "状态",
                  dataIndex: "status",
                  render: (value: string) => (
                    <Tag color={value === "ACTIVE" ? "success" : "default"}>
                      {value === "ACTIVE" ? "已绑定" : "已解绑"}
                    </Tag>
                  ),
                },
                { title: "最近更新", dataIndex: "updatedAt", render: formatTime },
                {
                  title: "操作",
                  render: (_, record) =>
                    canManage && record.status === "ACTIVE" ? (
                      <Button
                        type="link"
                        danger
                        icon={<DisconnectOutlined />}
                        onClick={() => setUnbindTarget(record)}
                      >
                        解绑
                      </Button>
                    ) : (
                      <Text type="secondary">无操作</Text>
                    ),
                },
              ]}
            />
          </Card>
        </Space>
      </PageShell>

      <Modal
        title="单个绑定身份来源"
        open={createOpen}
        okText="确认绑定"
        cancelText="取消"
        confirmLoading={createMutation.isPending}
        onOk={() => createForm.submit()}
        onCancel={() => {
          setCreateOpen(false);
          createForm.resetFields();
        }}
        destroyOnClose
      >
        <Form<CreateBindingForm>
          form={createForm}
          layout="vertical"
          initialValues={{ providerType: "EMPLOYEE_NO" }}
          onFinish={submitCreate}
        >
          <Form.Item
            name="userId"
            label="人员账号"
            rules={[{ required: true, message: "请选择人员账号" }]}
          >
            <Select
              showSearch
              filterOption={false}
              onSearch={setUserSearch}
              placeholder="按姓名或人员编号搜索"
              options={userOptions}
              loading={personnel.isLoading}
              notFoundContent="没有已开通账号的人员"
            />
          </Form.Item>
          <Form.Item
            name="providerType"
            label="身份来源"
            rules={[{ required: true, message: "请选择身份来源" }]}
          >
            <Select
              options={["EMPLOYEE_NO", "SM_CA", "OIDC", "CAS", "SAML"].map((value) => ({
                value,
                label: identityProviderLabel(value),
              }))}
            />
          </Form.Item>
          <Form.Item
            name="externalSubject"
            label="院内身份标识"
            extra="系统只保存不可逆摘要与脱敏提示，不保存身份原文。"
            rules={[
              { required: true, whitespace: true, message: "请输入院内身份标识" },
              { max: 512, message: "院内身份标识不能超过 512 个字符" },
            ]}
          >
            <Input autoComplete="off" maxLength={512} />
          </Form.Item>
          <Form.Item
            name="reason"
            label="绑定原因"
            rules={[
              { required: true, whitespace: true, message: "请输入绑定原因" },
              { min: 4, message: "绑定原因至少 4 个字符" },
              { max: 500, message: "绑定原因不能超过 500 个字符" },
            ]}
          >
            <TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="批量匹配身份来源"
        open={batchOpen}
        width={880}
        okText={batchActionLabel}
        cancelText="取消"
        confirmLoading={previewMutation.isPending || commitMutation.isPending}
        okButtonProps={{ disabled: Boolean(batchResult?.conflictRows) }}
        onCancel={closeBatch}
        onOk={() => {
          if (batchResult?.status === "COMPLETED" || batchResult?.status === "PARTIAL") {
            closeBatch();
          } else if (batchResult) {
            void commitBatch();
          } else {
            void previewBatch();
          }
        }}
        destroyOnClose
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="info"
            showIcon
            message="按人员编号批量匹配，先预检后提交"
            description="使用人员导入模板填写身份来源和院内身份标识。已有人员会更新身份关系，新人员可同时完成建档与账号开通。"
          />
          {!batchResult && (
            <Dragger
              accept=".csv,text/csv"
              maxCount={1}
              beforeUpload={(file) => {
                setBatchFile(file);
                return false;
              }}
              onRemove={() => {
                setBatchFile(undefined);
                return true;
              }}
            >
              <p className="ant-upload-drag-icon">
                <UploadOutlined />
              </p>
              <p>点击或拖入身份匹配文件</p>
            </Dragger>
          )}
          {batchResult && (
            <>
              <Descriptions bordered size="small" column={3}>
                <Descriptions.Item label="总人数">{batchResult.totalRows}</Descriptions.Item>
                <Descriptions.Item label="可匹配">{batchResult.validRows}</Descriptions.Item>
                <Descriptions.Item label="需处理冲突">{batchResult.conflictRows}</Descriptions.Item>
              </Descriptions>
              <Table
                rowKey="rowNo"
                size="small"
                pagination={{ pageSize: 10, hideOnSinglePage: true }}
                dataSource={batchResult.rows}
                columns={[
                  { title: "人员编号", dataIndex: "employeeNo" },
                  { title: "姓名", dataIndex: "displayName" },
                  {
                    title: "校验结果",
                    dataIndex: "status",
                    render: (value: string) => (
                      <Tag color={value === "VALID" || value === "SUCCESS" ? "success" : "error"}>
                        {importRowStatusLabel(value)}
                      </Tag>
                    ),
                  },
                  { title: "说明", dataIndex: "message", render: (value) => value ?? "校验通过" },
                ]}
              />
            </>
          )}
        </Space>
      </Modal>

      <Modal
        title="解除身份来源"
        open={Boolean(unbindTarget)}
        okText="确认解绑"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={unbindMutation.isPending}
        onOk={() => unbindForm.submit()}
        onCancel={() => {
          setUnbindTarget(null);
          unbindForm.resetFields();
        }}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          message="解绑后该身份不能继续用于登录"
          description="人员档案、账号和历史审计不会删除；需要恢复时应重新绑定。"
        />
        <Form<UnbindForm> form={unbindForm} layout="vertical" onFinish={submitUnbind}>
          <Form.Item
            name="reason"
            label="解绑原因"
            rules={[
              { required: true, whitespace: true, message: "请输入解绑原因" },
              { min: 4, message: "解绑原因至少 4 个字符" },
              { max: 500, message: "解绑原因不能超过 500 个字符" },
            ]}
          >
            <TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
