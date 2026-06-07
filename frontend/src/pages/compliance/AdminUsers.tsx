import { useState } from "react";
import {
  Alert,
  App,
  Button,
  Descriptions,
  Divider,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import {
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useAssignComplianceUserRole,
  useComplianceUserDetail,
  useComplianceUsers,
  useCreateComplianceUser,
  useRemoveComplianceUserRole,
  useResetComplianceUserPassword,
  useSecurityProfile,
  useSetComplianceUserStatus,
} from "@/shared/api/hooks";
import type {
  ComplianceUserRole,
  ComplianceUserSummary,
  CreateComplianceUserPayload,
  SecurityProfile,
} from "@/shared/api/hooks";
import { ROLE_OPTIONS, SCOPE_LEVEL_OPTIONS } from "@/shared/config/roleCatalog";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";

const { Text, Title } = Typography;

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "正常",
  DISABLED: "已停用",
  LOCKED: "已锁定",
};

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: "success",
  DISABLED: "default",
  LOCKED: "error",
};

interface RoleFormValue {
  roleCode: string;
  scopeLevel: string;
  scopeCode: string;
}

function hasPermission(profile: SecurityProfile | undefined, code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function roleKey(role: ComplianceUserRole) {
  return `${role.code}:${role.scopeLevel}:${role.scopeCode}`;
}

function renderAccountSecurity(credentialManaged: boolean, mustChangePwd: boolean) {
  if (!credentialManaged) {
    return <Text type="secondary">外部身份源</Text>;
  }
  return mustChangePwd ? <Tag color="warning">待首次改密</Tag> : <Text>已完成设置</Text>;
}

function accountSecurityDescription(credentialManaged: boolean, mustChangePwd: boolean) {
  if (!credentialManaged) {
    return "由外部身份源负责认证";
  }
  return mustChangePwd ? "待首次改密" : "已完成首次安全设置";
}

function riskColor(risk: string) {
  if (risk === "HIGH") {
    return "error";
  }
  if (risk === "MEDIUM") {
    return "warning";
  }
  return "default";
}

export default function AdminUsers() {
  const { message } = App.useApp();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [oneTimeSecret, setOneTimeSecret] = useState<{
    username: string;
    password: string | null;
  } | null>(null);
  const [createForm] = Form.useForm<CreateComplianceUserPayload>();
  const [roleForm] = Form.useForm<RoleFormValue>();
  const credentialManaged = Form.useWatch("credentialManaged", createForm) ?? true;

  const security = useSecurityProfile();
  const users = useComplianceUsers({ page, size });
  const detail = useComplianceUserDetail(selectedUserId);
  const createMutation = useCreateComplianceUser();
  const assignRoleMutation = useAssignComplianceUserRole();
  const removeRoleMutation = useRemoveComplianceUserRole();
  const resetPasswordMutation = useResetComplianceUserPassword();
  const statusMutation = useSetComplianceUserStatus();

  const canRead = hasPermission(security.data, "org.read");
  const canManage = hasPermission(security.data, "org.write");

  const refresh = async () => {
    await Promise.all([users.refetch(), security.refetch()]);
    if (selectedUserId) {
      await detail.refetch();
    }
  };

  const closeCreate = () => {
    setCreateOpen(false);
    createForm.resetFields();
  };

  const submitCreate = async (values: CreateComplianceUserPayload) => {
    try {
      const result = await createMutation.mutateAsync({
        credentialManaged: values.credentialManaged,
        userId: values.userId?.trim() || undefined,
        displayName: values.displayName?.trim() || undefined,
        username: values.username?.trim() || undefined,
        roleCode: values.roleCode,
        initialPassword: values.initialPassword?.trim() || undefined,
      });
      closeCreate();
      if (result.user.credentialManaged) {
        setOneTimeSecret({
          username: result.user.username ?? result.user.displayName,
          password: result.tempPassword,
        });
      } else {
        message.success("外部身份用户已创建");
      }
      await users.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "用户创建失败"));
    }
  };

  const submitRole = async (values: RoleFormValue) => {
    if (!selectedUserId) return;
    try {
      await assignRoleMutation.mutateAsync({
        userId: selectedUserId,
        roleCode: values.roleCode,
        scopeLevel: values.scopeLevel,
        scopeCode: values.scopeCode.trim(),
      });
      roleForm.resetFields();
      message.success("角色范围已生效");
      await Promise.all([detail.refetch(), users.refetch()]);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "角色分配失败"));
    }
  };

  const removeRole = async (role: ComplianceUserRole) => {
    if (!selectedUserId) return;
    try {
      await removeRoleMutation.mutateAsync({
        userId: selectedUserId,
        roleCode: role.code,
        scopeLevel: role.scopeLevel,
        scopeCode: role.scopeCode,
      });
      message.success("角色范围已移除");
      await Promise.all([detail.refetch(), users.refetch()]);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "角色移除失败"));
    }
  };

  const resetPassword = async () => {
    if (!selectedUserId || !detail.data) return;
    try {
      const result = await resetPasswordMutation.mutateAsync(selectedUserId);
      setOneTimeSecret({
        username: detail.data.username ?? detail.data.displayName,
        password: result.tempPassword,
      });
      await Promise.all([detail.refetch(), users.refetch()]);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "密码重置失败"));
    }
  };

  const toggleStatus = async () => {
    if (!selectedUserId || !detail.data) return;
    const nextStatus = detail.data.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    try {
      await statusMutation.mutateAsync({ userId: selectedUserId, status: nextStatus });
      message.success(nextStatus === "ACTIVE" ? "用户已启用" : "用户已停用");
      await Promise.all([detail.refetch(), users.refetch()]);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "用户状态更新失败"));
    }
  };

  const pageContent = () => {
    if (security.isLoading || users.isLoading) {
      return <PageState state="loading" title="正在读取用户" />;
    }
    if (security.isError || users.isError) {
      return (
        <PageState
          state="error"
          title="用户列表读取失败"
          description="请检查登录状态、租户上下文和身份安全服务。"
          action={
            <Button icon={<ReloadOutlined />} aria-label="重试" onClick={refresh}>
              重试
            </Button>
          }
        />
      );
    }
    if (!canRead) {
      return <PageState state="forbidden" title="当前账号不能查看用户管理" />;
    }
    if (!users.data || users.data.items.length === 0) {
      return (
        <PageState state="empty" title="暂无用户" description="当前租户还没有可管理的用户。" />
      );
    }

    return (
      <Space direction="vertical" size="middle" className="mk-full-width">
        {users.data.partial && (
          <Alert
            type="warning"
            showIcon
            message={`${users.data.partial.successCount} 项已读取，${users.data.partial.failureCount} 项失败`}
            description={users.data.partial.failures.map((failure) => failure.reason).join("；")}
          />
        )}
        <Table<ComplianceUserSummary>
          rowKey="userId"
          dataSource={users.data.items}
          scroll={{ x: "max-content" }}
          pagination={{
            current: users.data.page,
            pageSize: users.data.size,
            total: users.data.total,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100, 200],
            onChange: (nextPage, nextSize) => {
              setPage(nextSize === size ? nextPage : 1);
              setSize(nextSize);
            },
          }}
          columns={[
            {
              title: "用户",
              dataIndex: "displayName",
              render: (value: string, record) => (
                <Space direction="vertical" size={0}>
                  <Text strong>{value}</Text>
                  <Text type="secondary">
                    {record.credentialManaged ? record.username : "外部身份"}
                  </Text>
                </Space>
              ),
            },
            {
              title: "角色",
              dataIndex: "roles",
              render: (roles: ComplianceUserRole[]) =>
                roles.length > 0 ? (
                  <Space wrap size={[4, 4]}>
                    {roles.slice(0, 3).map((role) => (
                      <Tag key={roleKey(role)}>{role.displayName}</Tag>
                    ))}
                    {roles.length > 3 && <Tag>+{roles.length - 3}</Tag>}
                  </Space>
                ) : (
                  <Text type="secondary">未分配</Text>
                ),
            },
            {
              title: "状态",
              dataIndex: "status",
              render: (status: string) => (
                <Tag color={STATUS_COLOR[status] ?? "default"}>
                  {STATUS_LABEL[status] ?? status}
                </Tag>
              ),
            },
            {
              title: "账号安全",
              dataIndex: "mustChangePwd",
              render: (mustChangePwd: boolean, record) =>
                renderAccountSecurity(record.credentialManaged, mustChangePwd),
            },
            {
              title: "操作",
              key: "actions",
              render: (_, record) => (
                <Button
                  type="link"
                  size="small"
                  aria-label={`查看 ${record.userId}`}
                  onClick={() => setSelectedUserId(record.userId)}
                >
                  查看
                </Button>
              ),
            },
          ]}
        />
      </Space>
    );
  };

  const selectedIsSystemAdmin =
    detail.data?.roles.some((role) => role.code === "system-superadmin") ?? false;

  return (
    <>
      <PageShell
        title="用户管理"
        description="管理当前租户用户、角色范围和账号状态"
        primary={
          canManage ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              aria-label="新建用户"
              onClick={() => setCreateOpen(true)}
            >
              新建用户
            </Button>
          ) : undefined
        }
        extras={
          <Button icon={<ReloadOutlined />} aria-label="刷新" onClick={refresh}>
            刷新
          </Button>
        }
      >
        {pageContent()}
      </PageShell>

      <Modal
        open={createOpen}
        title="新建用户"
        okText="创建"
        cancelText="取消"
        okButtonProps={{ "aria-label": "创建" }}
        cancelButtonProps={{ "aria-label": "取消" }}
        confirmLoading={createMutation.isPending}
        onCancel={closeCreate}
        onOk={() => createForm.submit()}
        destroyOnClose
      >
        <Form<CreateComplianceUserPayload>
          form={createForm}
          layout="vertical"
          initialValues={{ credentialManaged: true, roleCode: "doctor" }}
          onFinish={submitCreate}
          preserve={false}
        >
          <Form.Item name="credentialManaged" label="认证方式" rules={[{ required: true }]}>
            <Segmented
              block
              options={[
                { label: "平台账号", value: true },
                { label: "外部身份", value: false },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="显示名称"
            rules={
              credentialManaged
                ? []
                : [{ required: true, whitespace: true, message: "请输入显示名称" }]
            }
          >
            <Input
              autoComplete="off"
              placeholder={credentialManaged ? "留空时与登录名一致" : "例如：王医生"}
            />
          </Form.Item>
          <Form.Item
            name="username"
            label="登录名"
            hidden={!credentialManaged}
            rules={
              credentialManaged
                ? [{ required: true, whitespace: true, message: "请输入登录名" }]
                : []
            }
          >
            <Input autoComplete="off" placeholder="员工登录名或工号" />
          </Form.Item>
          <Form.Item
            name="userId"
            label="用户标识"
            rules={
              credentialManaged
                ? []
                : [{ required: true, whitespace: true, message: "请输入用户标识" }]
            }
          >
            <Input
              autoComplete="off"
              placeholder={credentialManaged ? "留空时与登录名一致" : "院方稳定人员标识"}
            />
          </Form.Item>
          <Form.Item name="roleCode" label="初始角色">
            <Select
              options={ROLE_OPTIONS.map((role) => ({ value: role.code, label: role.name }))}
            />
          </Form.Item>
          <Form.Item name="initialPassword" label="初始密码" hidden={!credentialManaged}>
            <Input.Password autoComplete="new-password" placeholder="留空时生成一次性临时密码" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={Boolean(oneTimeSecret)}
        title="账号凭证已更新"
        footer={
          <Button type="primary" onClick={() => setOneTimeSecret(null)}>
            已妥善记录
          </Button>
        }
        closable={false}
      >
        <Alert
          type="warning"
          showIcon
          message="临时密码仅显示一次"
          description={
            oneTimeSecret?.password ? (
              <Space direction="vertical" size="small">
                <Text>{oneTimeSecret.username}</Text>
                <Text code copyable>
                  {oneTimeSecret.password}
                </Text>
              </Space>
            ) : (
              "账号已使用管理员设置的初始密码，用户首次登录仍需改密。"
            )
          }
        />
      </Modal>

      <Drawer
        open={Boolean(selectedUserId)}
        title={detail.data ? `${detail.data.displayName} · 用户详情` : "用户详情"}
        width={720}
        onClose={() => {
          setSelectedUserId(null);
          roleForm.resetFields();
        }}
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => detail.refetch()}>
            刷新
          </Button>
        }
      >
        {detail.isLoading && <PageState state="loading" />}
        {!detail.isLoading && (detail.isError || !detail.data) && (
          <PageState
            state="error"
            title="用户详情读取失败"
            action={<Button onClick={() => detail.refetch()}>重试</Button>}
          />
        )}
        {!detail.isLoading && !detail.isError && detail.data && (
          <Space direction="vertical" size="large" className="mk-full-width">
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="显示名称">{detail.data.displayName}</Descriptions.Item>
              <Descriptions.Item label="认证方式">
                {detail.data.credentialManaged ? "平台账号" : "外部身份"}
              </Descriptions.Item>
              {detail.data.credentialManaged && (
                <Descriptions.Item label="登录名">{detail.data.username}</Descriptions.Item>
              )}
              <Descriptions.Item label="用户标识">{detail.data.userId}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_COLOR[detail.data.status] ?? "default"}>
                  {STATUS_LABEL[detail.data.status] ?? detail.data.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="账号安全">
                {accountSecurityDescription(
                  detail.data.credentialManaged,
                  detail.data.mustChangePwd,
                )}
              </Descriptions.Item>
            </Descriptions>

            {canManage && !selectedIsSystemAdmin && (
              <Space wrap>
                <Popconfirm
                  title={detail.data.status === "ACTIVE" ? "确认停用该用户？" : "确认启用该用户？"}
                  onConfirm={toggleStatus}
                >
                  <Button danger={detail.data.status === "ACTIVE"}>
                    {detail.data.status === "ACTIVE" ? "停用用户" : "启用用户"}
                  </Button>
                </Popconfirm>
                {detail.data.credentialManaged && (
                  <Popconfirm title="确认生成新的临时密码？" onConfirm={resetPassword}>
                    <Button icon={<KeyOutlined />}>重置密码</Button>
                  </Popconfirm>
                )}
              </Space>
            )}

            <Divider />
            <Title level={5}>角色与数据范围</Title>
            <Table<ComplianceUserRole>
              rowKey={roleKey}
              dataSource={detail.data.roles}
              pagination={false}
              scroll={{ x: "max-content" }}
              columns={[
                { title: "角色", dataIndex: "displayName" },
                { title: "范围层级", dataIndex: "scopeLevel" },
                { title: "范围", dataIndex: "scopeCode" },
                {
                  title: "操作",
                  key: "actions",
                  render: (_, role) =>
                    canManage && role.code !== "system-superadmin" ? (
                      <Popconfirm title="确认移除该角色范围？" onConfirm={() => removeRole(role)}>
                        <Button type="link" danger size="small">
                          移除
                        </Button>
                      </Popconfirm>
                    ) : (
                      <Text type="secondary">系统保护</Text>
                    ),
                },
              ]}
            />

            {canManage && !selectedIsSystemAdmin && (
              <Form<RoleFormValue>
                form={roleForm}
                layout="vertical"
                initialValues={{ roleCode: "doctor", scopeLevel: "TENANT" }}
                onFinish={submitRole}
              >
                <Space align="start" wrap>
                  <Form.Item name="roleCode" label="新增角色">
                    <Select
                      className="mk-select-medium"
                      options={ROLE_OPTIONS.map((role) => ({
                        value: role.code,
                        label: role.name,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item name="scopeLevel" label="范围层级">
                    <Select
                      className="mk-select-medium"
                      options={SCOPE_LEVEL_OPTIONS.map((scope) => ({
                        value: scope.code,
                        label: scope.name,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item
                    name="scopeCode"
                    label="范围编码"
                    rules={[{ required: true, whitespace: true, message: "请输入范围编码" }]}
                  >
                    <Input className="mk-input-medium" />
                  </Form.Item>
                  <Form.Item label=" ">
                    <Button
                      htmlType="submit"
                      icon={<SafetyCertificateOutlined />}
                      loading={assignRoleMutation.isPending}
                    >
                      添加角色
                    </Button>
                  </Form.Item>
                </Space>
              </Form>
            )}

            <Divider />
            <Title level={5}>当前组织范围内的有效权限</Title>
            <Table<(typeof detail.data.effectivePermissions)[number]>
              rowKey="code"
              dataSource={detail.data.effectivePermissions}
              pagination={{ pageSize: 10, hideOnSinglePage: true }}
              scroll={{ x: "max-content" }}
              columns={[
                { title: "权限", dataIndex: "displayName" },
                { title: "编码", dataIndex: "code" },
                { title: "维度", dataIndex: "dimension" },
                {
                  title: "风险",
                  dataIndex: "risk",
                  render: (risk: string) => <Tag color={riskColor(risk)}>{risk}</Tag>,
                },
              ]}
            />
          </Space>
        )}
      </Drawer>
    </>
  );
}
