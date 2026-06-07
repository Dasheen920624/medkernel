import { useState } from "react";
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import { DisconnectOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useCreateIdentityBinding,
  useDelegatedAuthStatus,
  useIdentityBindings,
  useOrgUsers,
  useSecurityProfile,
  useUnbindIdentityBinding,
} from "@/shared/api/hooks";
import type {
  IdentityBinding as IdentityBindingRecord,
  IdentityProviderType,
  SecurityProfile,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";

const { Text } = Typography;
const { TextArea } = Input;

const STATUS_LABEL: Record<string, string> = {
  READY: "就绪",
  NOT_CONNECTED: "未连接",
  DISABLED: "未启用",
};

const STATUS_COLOR: Record<string, string> = {
  READY: "success",
  NOT_CONNECTED: "default",
  DISABLED: "warning",
};

const PROVIDER_LABEL: Record<IdentityProviderType, string> = {
  OIDC: "OIDC",
  CAS: "CAS",
  SAML: "SAML",
  EMPLOYEE_NO: "员工号",
  SM_CA: "国密 CA",
};

const PROVIDER_OPTIONS = Object.entries(PROVIDER_LABEL).map(([value, label]) => ({
  value: value as IdentityProviderType,
  label,
}));

interface CreateBindingForm {
  userId: string;
  providerType: IdentityProviderType;
  externalSubject: string;
  reason: string;
}

interface UnbindForm {
  reason: string;
}

function hasPermission(profile: SecurityProfile | undefined, code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function formatTime(value: string) {
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return value;
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
  const bindings = useIdentityBindings();
  const [userSearch, setUserSearch] = useState("");
  const users = useOrgUsers({ page: 1, size: 50, keyword: userSearch || undefined });
  const createMutation = useCreateIdentityBinding();
  const unbindMutation = useUnbindIdentityBinding();
  const [createOpen, setCreateOpen] = useState(false);
  const [unbindTarget, setUnbindTarget] = useState<IdentityBindingRecord | null>(null);
  const [createForm] = Form.useForm<CreateBindingForm>();
  const [unbindForm] = Form.useForm<UnbindForm>();

  const canManage = hasPermission(security.data, "org.write");

  const refresh = async () => {
    await Promise.all([
      bindings.refetch(),
      delegated.refetch(),
      security.refetch(),
      users.refetch(),
    ]);
  };

  const closeCreate = () => {
    setCreateOpen(false);
    createForm.resetFields();
  };

  const submitCreate = async (values: CreateBindingForm) => {
    try {
      await createMutation.mutateAsync({
        userId: values.userId.trim(),
        providerType: values.providerType,
        externalSubject: values.externalSubject.trim(),
        reason: values.reason.trim(),
      });
      message.success("身份绑定已生效");
      closeCreate();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "身份绑定失败"));
    }
  };

  const closeUnbind = () => {
    setUnbindTarget(null);
    unbindForm.resetFields();
  };

  const submitUnbind = async (values: UnbindForm) => {
    if (!unbindTarget) return;
    try {
      await unbindMutation.mutateAsync({
        bindingId: unbindTarget.bindingId,
        reason: values.reason.trim(),
        expectedVersion: unbindTarget.version,
      });
      message.success("身份绑定已解除，历史记录已保留");
      closeUnbind();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "身份解绑失败"));
    }
  };

  if (security.isLoading || bindings.isLoading || delegated.isLoading) {
    return (
      <PageShell title="身份绑定" description="正在读取当前租户的身份绑定">
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (security.isError || bindings.isError || delegated.isError) {
    return (
      <PageShell title="身份绑定" description="身份绑定状态读取失败">
        <PageState
          state="error"
          title="暂时无法读取身份绑定"
          description="请检查登录状态、租户上下文和统一身份服务。"
          action={
            <Button icon={<ReloadOutlined />} onClick={refresh}>
              重试
            </Button>
          }
        />
      </PageShell>
    );
  }

  const delegatedStatus = delegated.data;
  if (!security.data || !delegatedStatus || !bindings.data) {
    return (
      <PageShell title="身份绑定" description="当前租户暂无可用身份数据">
        <PageState state="empty" title="暂无身份绑定状态" />
      </PageShell>
    );
  }

  const statusLabel = STATUS_LABEL[delegatedStatus.status] ?? delegatedStatus.status;
  const statusColor = STATUS_COLOR[delegatedStatus.status] ?? "default";
  const userOptions = (users.data?.items ?? []).map((user) => ({
    value: user.userId,
    label: `${user.displayName} · ${user.userId}`,
  }));
  const userNames = new Map(
    (users.data?.items ?? []).map((user) => [user.userId, user.displayName]),
  );
  let alertType: "success" | "warning" | "info" = "info";
  if (delegatedStatus.status === "READY") {
    alertType = "success";
  } else if (delegatedStatus.enabled) {
    alertType = "warning";
  }

  return (
    <>
      <PageShell
        title="身份绑定"
        description="管理员工号、统一身份和国密证书与系统用户的唯一关系"
        primary={
          canManage ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setUserSearch("");
                setCreateOpen(true);
              }}
            >
              新增绑定
            </Button>
          ) : undefined
        }
        extras={
          <Button icon={<ReloadOutlined />} onClick={refresh}>
            刷新
          </Button>
        }
      >
        <Space direction="vertical" size="large" className="mk-full-width">
          {!canManage && (
            <Alert
              type="info"
              showIcon
              message="当前为只读视图"
              description="只有具备组织管理权限的管理员可以新增或解除身份绑定。"
            />
          )}

          <Alert
            type={alertType}
            showIcon
            message={
              <Space wrap>
                <Text strong>统一身份模式</Text>
                <Tag>{delegatedStatus.mode}</Tag>
                <Tag color={statusColor}>{statusLabel}</Tag>
              </Space>
            }
            description={delegatedStatus.message}
          />

          <Card title="绑定记录">
            <Table<IdentityBindingRecord>
              rowKey="bindingId"
              dataSource={bindings.data}
              pagination={{
                pageSize: 20,
                showSizeChanger: false,
                showTotal: (total) => `共 ${total} 条记录`,
              }}
              locale={{ emptyText: "当前租户暂无身份绑定" }}
              scroll={{ x: 760 }}
              columns={[
                {
                  title: "系统用户",
                  dataIndex: "userId",
                  width: 180,
                  render: (userId: string) => (
                    <Space direction="vertical" size={0}>
                      <Text>{userNames.get(userId) ?? "显示名未加载"}</Text>
                      <Text type="secondary">{userId}</Text>
                    </Space>
                  ),
                },
                {
                  title: "身份源",
                  dataIndex: "providerType",
                  width: 130,
                  render: (provider: IdentityProviderType) => (
                    <Tag>{PROVIDER_LABEL[provider] ?? provider}</Tag>
                  ),
                },
                {
                  title: "外部身份",
                  dataIndex: "subjectHint",
                  width: 180,
                },
                {
                  title: "状态",
                  dataIndex: "status",
                  width: 110,
                  render: (status: IdentityBindingRecord["status"]) => (
                    <Tag color={status === "ACTIVE" ? "success" : "default"}>
                      {status === "ACTIVE" ? "已绑定" : "已解绑"}
                    </Tag>
                  ),
                },
                {
                  title: "最近更新",
                  dataIndex: "updatedAt",
                  width: 190,
                  render: formatTime,
                },
                {
                  title: "操作",
                  key: "actions",
                  width: 100,
                  fixed: "right",
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
        title="新增身份绑定"
        open={createOpen}
        okText="确认绑定"
        cancelText="取消"
        confirmLoading={createMutation.isPending}
        onOk={() => createForm.submit()}
        onCancel={closeCreate}
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
            label="系统用户"
            rules={[{ required: true, message: "请选择系统用户" }]}
          >
            <Select
              showSearch
              filterOption={false}
              onSearch={setUserSearch}
              placeholder="选择当前租户用户"
              options={userOptions}
              loading={users.isLoading}
              disabled={users.isError}
              notFoundContent={users.isError ? "用户目录读取失败" : "暂无可绑定用户"}
            />
          </Form.Item>
          <Form.Item
            name="providerType"
            label="身份源"
            rules={[{ required: true, message: "请选择身份源" }]}
          >
            <Select options={PROVIDER_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="externalSubject"
            label="外部身份"
            rules={[
              { required: true, whitespace: true, message: "请输入外部身份标识" },
              { max: 512, message: "外部身份不能超过 512 个字符" },
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
        title="解除身份绑定"
        open={Boolean(unbindTarget)}
        okText="确认解绑"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={unbindMutation.isPending}
        onOk={() => unbindForm.submit()}
        onCancel={closeUnbind}
        destroyOnClose
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="warning"
            showIcon
            message="解绑后该外部身份不能继续登录"
            description="历史记录和审计证据会保留；需要恢复时应重新绑定。"
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
        </Space>
      </Modal>
    </>
  );
}
