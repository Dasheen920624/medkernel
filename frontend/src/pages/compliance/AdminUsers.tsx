import {
  DownloadOutlined,
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import {
  Alert,
  App,
  Button,
  Checkbox,
  Descriptions,
  Divider,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from "antd";
import { useState } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  downloadPersonnelImportTemplate,
  useAssignComplianceUserRole,
  useCommitPersonnelImport,
  useComplianceUserDetail,
  useCreatePersonnel,
  usePersonnel,
  usePersonnelDetail,
  usePreviewPersonnelImport,
  useRemoveComplianceUserRole,
  useResetComplianceUserPassword,
  useSecurityProfile,
  useSetComplianceUserStatus,
  type AppointmentType,
  type CreatePersonnelPayload,
  type OrgUnit,
  type PersonnelImportResponse,
  type PersonnelSummary,
  type SecurityProfile,
} from "@/shared/api/hooks";
import {
  accountStateLabel,
  appointmentTypeLabel,
  importRowActionLabel,
  importRowStatusLabel,
  importStatusLabel,
  orgLevelLabel,
  permissionDimensionLabel,
  riskLabel,
} from "@/shared/config/customerLabels";
import { ROLE_OPTIONS } from "@/shared/config/roleCatalog";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { OrgUnitSelect } from "@/shared/ui/OrgUnitSelect";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";

const { Text, Title } = Typography;
const { Dragger } = Upload;

type PersonnelFormValues = {
  employeeNo: string;
  displayName: string;
  organizationId: string;
  departmentId?: string;
  wardId?: string;
  appointmentType: AppointmentType;
  positionTitle?: string;
  openAccount: boolean;
  loginName?: string;
  roleCode?: string;
  bindIdentity: boolean;
  providerType?: "OIDC" | "CAS" | "SAML" | "EMPLOYEE_NO" | "SM_CA";
  externalSubject?: string;
};

type RoleFormValues = {
  roleCode: string;
  orgUnitId: string;
};

function hasPermission(profile: SecurityProfile | undefined, code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function appointmentColor(type?: string | null) {
  if (type === "EXTERNAL_COLLABORATOR") return "orange";
  if (type === "IMPLEMENTATION") return "purple";
  if (type === "GROUP_SHARED") return "cyan";
  return "blue";
}

function accountColor(state?: string | null) {
  if (state === "ACTIVE") return "success";
  if (state === "RESET_REQUIRED") return "warning";
  if (state === "LOCKED" || state === "DISABLED") return "error";
  return "default";
}

function personnelIdentityText(
  employeeNo: string | null | undefined,
  evidenceDetailsEnabled: boolean,
) {
  return evidenceDetailsEnabled && employeeNo ? `人员编号：${employeeNo}` : "人员档案已登记";
}

function accountLoginText(username: string | null | undefined, evidenceDetailsEnabled: boolean) {
  if (!username) return "未开通";
  return evidenceDetailsEnabled ? username : "登录账号已开通";
}

function identityBindingText(subjectHints: string[], evidenceDetailsEnabled: boolean) {
  if (subjectHints.length === 0) return "未绑定";
  return evidenceDetailsEnabled
    ? subjectHints.join("、")
    : `已绑定 ${subjectHints.length} 个身份来源`;
}

export default function AdminUsers() {
  const { message } = App.useApp();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importFile, setImportFile] = useState<File>();
  const [importResult, setImportResult] = useState<PersonnelImportResponse>();
  const [selectedPersonId, setSelectedPersonId] = useState<string | null>(null);
  const [selectedRoleOrg, setSelectedRoleOrg] = useState<OrgUnit>();
  const [activations, setActivations] = useState<
    Array<{ username: string; temporaryPassword: string }>
  >([]);
  const [personForm] = Form.useForm<PersonnelFormValues>();
  const [roleForm] = Form.useForm<RoleFormValues>();
  const openAccount = Form.useWatch("openAccount", personForm) ?? true;
  const bindIdentity = Form.useWatch("bindIdentity", personForm) ?? false;
  const selectedOrganizationId = Form.useWatch("organizationId", personForm);
  const selectedDepartmentId = Form.useWatch("departmentId", personForm);

  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const personnel = usePersonnel({ page, size, keyword: keyword.trim() || undefined });
  const detail = usePersonnelDetail(selectedPersonId);
  const accountDetail = useComplianceUserDetail(detail.data?.account?.userId ?? null);
  const createMutation = useCreatePersonnel();
  const previewMutation = usePreviewPersonnelImport();
  const commitMutation = useCommitPersonnelImport();
  const assignRoleMutation = useAssignComplianceUserRole();
  const removeRoleMutation = useRemoveComplianceUserRole();
  const resetPasswordMutation = useResetComplianceUserPassword();
  const statusMutation = useSetComplianceUserStatus();

  const canRead = hasPermission(security.data, "org.read");
  const canManage = hasPermission(security.data, "org.write");
  let importActionLabel = "开始预检";
  if (importResult) importActionLabel = "确认导入";
  if (importResult?.status === "COMPLETED" || importResult?.status === "PARTIAL") {
    importActionLabel = "完成";
  }
  const refresh = async () => {
    await Promise.all([personnel.refetch(), security.refetch()]);
    if (selectedPersonId) await detail.refetch();
  };

  const closeCreate = () => {
    setCreateOpen(false);
    personForm.resetFields();
  };

  const submitCreate = async (values: PersonnelFormValues) => {
    const payload: CreatePersonnelPayload = {
      employeeNo: values.employeeNo.trim(),
      displayName: values.displayName.trim(),
      appointment: {
        organizationId: values.organizationId,
        departmentId: values.departmentId,
        wardId: values.wardId,
        appointmentType: values.appointmentType,
        positionTitle: values.positionTitle?.trim() || undefined,
        primary: true,
      },
      account:
        values.openAccount && values.loginName
          ? {
              loginName: values.loginName.trim(),
              roleCode: values.roleCode,
            }
          : undefined,
      identity:
        values.bindIdentity && values.providerType && values.externalSubject
          ? {
              providerType: values.providerType,
              externalSubject: values.externalSubject.trim(),
            }
          : undefined,
    };
    try {
      const created = await createMutation.mutateAsync(payload);
      closeCreate();
      if (created.oneTimeActivation) {
        setActivations([
          {
            username: created.oneTimeActivation.username,
            temporaryPassword: created.oneTimeActivation.temporaryPassword,
          },
        ]);
      } else {
        message.success("人员档案已建立");
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, "新增人员失败"));
    }
  };

  const previewImport = async () => {
    if (!importFile) {
      message.warning("请先选择人员导入文件");
      return;
    }
    try {
      setImportResult(await previewMutation.mutateAsync(importFile));
    } catch (error) {
      message.error(getApiErrorMessage(error, "导入预检失败"));
    }
  };

  const commitImport = async () => {
    if (!importResult?.jobId) return;
    try {
      const result = await commitMutation.mutateAsync(importResult.jobId);
      setImportResult(result);
      if (result.oneTimeActivations.length > 0) {
        setActivations(result.oneTimeActivations);
      }
      message.success(`已处理 ${result.successRows} 人`);
    } catch (error) {
      message.error(getApiErrorMessage(error, "人员导入失败"));
    }
  };

  const closeImport = () => {
    setImportOpen(false);
    setImportFile(undefined);
    setImportResult(undefined);
  };

  const downloadTemplate = async () => {
    try {
      const blob = await downloadPersonnelImportTemplate();
      const href = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = href;
      anchor.download = "人员批量导入模板.csv";
      anchor.click();
      URL.revokeObjectURL(href);
    } catch (error) {
      message.error(getApiErrorMessage(error, "模板下载失败"));
    }
  };

  const assignRole = async (values: RoleFormValues) => {
    if (!detail.data?.account?.userId) return;
    const unit = selectedRoleOrg;
    if (!unit) {
      message.error("请选择有效的组织范围");
      return;
    }
    try {
      await assignRoleMutation.mutateAsync({
        userId: detail.data.account.userId,
        roleCode: values.roleCode,
        scopeLevel: unit.level,
        scopeCode: unit.id ?? "",
      });
      roleForm.resetFields();
      setSelectedRoleOrg(undefined);
      message.success("角色范围已生效");
      await accountDetail.refetch();
    } catch (error) {
      message.error(getApiErrorMessage(error, "角色分配失败"));
    }
  };

  const resetPassword = async () => {
    if (!detail.data?.account?.userId) return;
    try {
      const result = await resetPasswordMutation.mutateAsync(detail.data.account.userId);
      setActivations([
        {
          username: detail.data.account.username ?? detail.data.person.displayName,
          temporaryPassword: result.tempPassword,
        },
      ]);
      await accountDetail.refetch();
    } catch (error) {
      message.error(getApiErrorMessage(error, "密码重置失败"));
    }
  };

  const toggleAccount = async () => {
    const account = accountDetail.data;
    if (!account) return;
    const status = account.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    try {
      await statusMutation.mutateAsync({ userId: account.userId, status });
      message.success(status === "ACTIVE" ? "账号已启用" : "账号已停用");
      await accountDetail.refetch();
    } catch (error) {
      message.error(getApiErrorMessage(error, "账号状态更新失败"));
    }
  };

  let content;
  if (security.isLoading || personnel.isLoading) {
    content = <PageState state="loading" title="正在读取人员主数据" />;
  } else if (security.isError || personnel.isError) {
    content = (
      <PageState
        state="error"
        title="人员与账号读取失败"
        description="请检查登录状态、机构范围和身份安全服务。"
        action={<Button onClick={refresh}>重试</Button>}
      />
    );
  } else if (!canRead) {
    content = <PageState state="forbidden" title="当前账号不能查看人员与账号" />;
  } else if (!personnel.data?.items.length) {
    content = (
      <PageState
        state="empty"
        title="尚未建立人员档案"
        description="建议先下载模板批量导入院内人员，也可以单独新增一人。"
      />
    );
  } else {
    content = (
      <Table<PersonnelSummary>
        rowKey="personId"
        dataSource={personnel.data.items}
        scroll={{ x: 980 }}
        pagination={{
          current: personnel.data.page,
          pageSize: personnel.data.size,
          total: personnel.data.total,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100, 200],
          onChange: (nextPage, nextSize) => {
            setPage(nextSize === size ? nextPage : 1);
            setSize(nextSize);
          },
        }}
        columns={[
          {
            title: "人员",
            key: "person",
            render: (_, record) => (
              <Space direction="vertical" size={0}>
                <Text strong>{record.displayName}</Text>
                <Text type="secondary">
                  {personnelIdentityText(record.employeeNo, evidenceDetailsEnabled)}
                </Text>
              </Space>
            ),
          },
          {
            title: "主要任职",
            key: "appointment",
            render: (_, record) => (
              <Space direction="vertical" size={0}>
                <Text>{record.organizationName ?? "未设置机构"}</Text>
                <Text type="secondary">
                  {[record.departmentName, record.wardName, record.positionTitle]
                    .filter(Boolean)
                    .join(" · ") || "未设置科室、病区或岗位"}
                </Text>
              </Space>
            ),
          },
          {
            title: "人员类型",
            dataIndex: "appointmentType",
            render: (value: string | null) => (
              <Tag color={appointmentColor(value)}>{appointmentTypeLabel(value)}</Tag>
            ),
          },
          {
            title: "账号",
            key: "account",
            render: (_, record) => (
              <Space direction="vertical" size={0}>
                <Tag color={accountColor(record.accountState)}>
                  {accountStateLabel(record.accountState)}
                </Tag>
                {record.username && (
                  <Text type="secondary">
                    {accountLoginText(record.username, evidenceDetailsEnabled)}
                  </Text>
                )}
              </Space>
            ),
          },
          {
            title: "身份来源",
            dataIndex: "identityCount",
            render: (count: number) => (count > 0 ? `已绑定 ${count} 个` : "未绑定"),
          },
          {
            title: "操作",
            key: "action",
            render: (_, record) => (
              <Button type="link" onClick={() => setSelectedPersonId(record.personId)}>
                查看
              </Button>
            ),
          },
        ]}
      />
    );
  }

  return (
    <>
      <PageShell
        title="人员与账号"
        description="统一维护人员、任职、登录账号和院内身份来源"
        primary={
          canManage ? (
            <Button type="primary" icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
              批量导入人员
            </Button>
          ) : undefined
        }
        extras={
          <Space wrap>
            <EvidenceDetailsToggle securityProfile={security.data} />
            {canManage && (
              <Button icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                新增人员
              </Button>
            )}
            <Button icon={<ReloadOutlined />} onClick={refresh}>
              刷新
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="info"
            showIcon
            message="人员治理边界"
            description={
              <Space wrap>
                <Tag color="blue">任职</Tag>
                <Text>人员必须绑定真实机构、科室、病区或岗位后参与业务。</Text>
                <Tag color="green">登录账号</Tag>
                <Text>账号开通、停用、重置和身份来源均保留审计证据。</Text>
                <Tag color="purple">组织范围</Tag>
                <Text>角色授权必须选择组织范围，按集团、医院、科室或病区生效。</Text>
              </Space>
            }
          />
          <Input.Search
            allowClear
            aria-label="搜索人员"
            placeholder="按姓名、院内人员身份或登录名搜索"
            onSearch={(value) => {
              setKeyword(value);
              setPage(1);
            }}
          />
          {content}
        </Space>
      </PageShell>

      <Modal
        title="新增人员"
        open={createOpen}
        width={720}
        okText="建立人员档案"
        cancelText="取消"
        confirmLoading={createMutation.isPending}
        onOk={() => personForm.submit()}
        onCancel={closeCreate}
        destroyOnClose
      >
        <Form<PersonnelFormValues>
          form={personForm}
          layout="vertical"
          initialValues={{
            appointmentType: "INTERNAL",
            openAccount: true,
            bindIdentity: false,
            roleCode: "clinical-user",
            providerType: "EMPLOYEE_NO",
          }}
          onFinish={submitCreate}
          preserve={false}
        >
          <Space align="start" wrap className="mk-full-width">
            <Form.Item
              name="employeeNo"
              label="院内人员身份"
              rules={[{ required: true, whitespace: true, message: "请输入院内人员身份" }]}
            >
              <Input placeholder="优先使用院内稳定工号" />
            </Form.Item>
            <Form.Item
              name="displayName"
              label="姓名"
              rules={[{ required: true, whitespace: true, message: "请输入姓名" }]}
            >
              <Input />
            </Form.Item>
            <Form.Item name="appointmentType" label="人员类型" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: "INTERNAL", label: "本机构员工" },
                  { value: "GROUP_SHARED", label: "集团共享人员" },
                  { value: "EXTERNAL_COLLABORATOR", label: "院外协作人员" },
                  { value: "IMPLEMENTATION", label: "实施与运维人员" },
                ]}
              />
            </Form.Item>
          </Space>
          <Divider orientation="left">主要任职</Divider>
          <Form.Item
            name="organizationId"
            label="所属机构"
            rules={[{ required: true, message: "请选择所属机构" }]}
          >
            <OrgUnitSelect
              scope="SERVICE_ORGANIZATION"
              placeholder="从组织树选择集团、医院、分院或基层机构"
              onChange={() => {
                personForm.setFieldValue("departmentId", undefined);
                personForm.setFieldValue("wardId", undefined);
              }}
            />
          </Form.Item>
          <Space align="start" wrap className="mk-full-width">
            <Form.Item name="departmentId" label="所属科室">
              <OrgUnitSelect
                allowClear
                level="DEPARTMENT"
                ancestorId={selectedOrganizationId}
                disabled={!selectedOrganizationId}
                placeholder={selectedOrganizationId ? "可选" : "请先选择所属机构"}
                onChange={() => personForm.setFieldValue("wardId", undefined)}
              />
            </Form.Item>
            <Form.Item name="wardId" label="所属病区">
              <OrgUnitSelect
                allowClear
                level="WARD"
                ancestorId={selectedDepartmentId}
                disabled={!selectedDepartmentId}
                placeholder={selectedDepartmentId ? "可选" : "请先选择科室"}
              />
            </Form.Item>
            <Form.Item name="positionTitle" label="岗位或职务">
              <Input placeholder="例如：心内科主治医师" />
            </Form.Item>
          </Space>
          <Divider orientation="left">登录账号</Divider>
          <Form.Item name="openAccount" valuePropName="checked">
            <Checkbox>同时开通登录账号</Checkbox>
          </Form.Item>
          {openAccount && (
            <Space align="start" wrap className="mk-full-width">
              <Form.Item
                name="loginName"
                label="登录名"
                rules={[{ required: true, whitespace: true, message: "请输入登录名" }]}
              >
                <Input placeholder="建议使用院内人员身份" />
              </Form.Item>
              <Form.Item name="roleCode" label="初始角色">
                <Select
                  options={ROLE_OPTIONS.map((role) => ({
                    value: role.code,
                    label: role.name,
                  }))}
                />
              </Form.Item>
            </Space>
          )}
          <Form.Item name="bindIdentity" valuePropName="checked">
            <Checkbox disabled={!openAccount}>同时绑定院内身份来源</Checkbox>
          </Form.Item>
          {openAccount && bindIdentity && (
            <Space align="start" wrap className="mk-full-width">
              <Form.Item name="providerType" label="身份来源" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: "EMPLOYEE_NO", label: "院内工号" },
                    { value: "SM_CA", label: "国密数字证书" },
                    { value: "OIDC", label: "开放式身份认证（OIDC）" },
                    { value: "CAS", label: "统一认证服务（CAS）" },
                    { value: "SAML", label: "安全断言认证（SAML）" },
                  ]}
                />
              </Form.Item>
              <Form.Item
                name="externalSubject"
                label="院内人员身份"
                rules={[{ required: true, whitespace: true, message: "请输入院内人员身份" }]}
              >
                <Input />
              </Form.Item>
            </Space>
          )}
        </Form>
      </Modal>

      <Modal
        title="批量导入人员"
        open={importOpen}
        width={920}
        okText={importActionLabel}
        cancelText="取消"
        cancelButtonProps={{ "aria-label": "取消" }}
        confirmLoading={previewMutation.isPending || commitMutation.isPending}
        okButtonProps={{
          disabled:
            Boolean(importResult) &&
            importResult?.status !== "COMPLETED" &&
            importResult?.status !== "PARTIAL" &&
            importResult?.status !== "READY",
        }}
        onCancel={closeImport}
        onOk={() => {
          if (importResult?.status === "COMPLETED" || importResult?.status === "PARTIAL") {
            closeImport();
          } else if (importResult) {
            void commitImport();
          } else {
            void previewImport();
          }
        }}
        destroyOnClose
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="info"
            showIcon
            message="一次完成建档、任职、账号和身份来源匹配"
            description="系统先预检机构、科室、病区、重复人员、登录名和身份冲突；只有确认后才写入。单次最多 10,000 人。"
            action={
              <Button icon={<DownloadOutlined />} onClick={downloadTemplate}>
                下载模板
              </Button>
            }
          />
          {!importResult && (
            <Dragger
              accept=".csv,text/csv"
              maxCount={1}
              beforeUpload={(file) => {
                setImportFile(file);
                return false;
              }}
              onRemove={() => {
                setImportFile(undefined);
                return true;
              }}
            >
              <p className="ant-upload-drag-icon">
                <UploadOutlined />
              </p>
              <p>点击或拖入人员导入文件</p>
              <p>请使用系统模板并保持 UTF-8 编码</p>
            </Dragger>
          )}
          {importResult && (
            <>
              <Descriptions bordered size="small" column={4}>
                <Descriptions.Item label="处理状态">
                  {importStatusLabel(importResult.status)}
                </Descriptions.Item>
                <Descriptions.Item label="总人数">{importResult.totalRows}</Descriptions.Item>
                <Descriptions.Item label="可导入">{importResult.validRows}</Descriptions.Item>
                <Descriptions.Item label="需处理冲突">
                  {importResult.conflictRows}
                </Descriptions.Item>
              </Descriptions>
              {importResult.conflictRows > 0 && importResult.status === "HAS_ISSUES" && (
                <Alert
                  type="warning"
                  showIcon
                  message="请先修正冲突行再重新上传"
                  description="存在冲突时系统不会提交任何一行，避免人员与账号关系被部分写入。"
                />
              )}
              <Table
                rowKey="rowNo"
                size="small"
                pagination={{ pageSize: 10, hideOnSinglePage: true }}
                dataSource={importResult.rows}
                columns={[
                  { title: "行", dataIndex: "rowNo", width: 60 },
                  { title: "院内人员身份", dataIndex: "employeeNo" },
                  { title: "姓名", dataIndex: "displayName" },
                  {
                    title: "处理方式",
                    dataIndex: "action",
                    render: importRowActionLabel,
                  },
                  {
                    title: "结果",
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

      <Drawer
        title={detail.data ? `${detail.data.person.displayName} · 人员详情` : "人员详情"}
        open={Boolean(selectedPersonId)}
        width={760}
        onClose={() => {
          setSelectedPersonId(null);
          roleForm.resetFields();
        }}
      >
        {detail.isLoading && <PageState state="loading" />}
        {!detail.isLoading && (detail.isError || !detail.data) && (
          <PageState state="error" title="人员详情读取失败" />
        )}
        {detail.data && (
          <Space direction="vertical" size="large" className="mk-full-width">
            <Descriptions bordered size="small" column={1} title="人员档案">
              <Descriptions.Item label="姓名">{detail.data.person.displayName}</Descriptions.Item>
              <Descriptions.Item label="院内人员身份">
                {personnelIdentityText(detail.data.person.employeeNo, evidenceDetailsEnabled)}
              </Descriptions.Item>
              <Descriptions.Item label="主要任职">
                {detail.data.primaryAppointment
                  ? `${detail.data.primaryAppointment.organizationName}${
                      detail.data.primaryAppointment.departmentName
                        ? ` · ${detail.data.primaryAppointment.departmentName}`
                        : ""
                    }${
                      detail.data.primaryAppointment.wardName
                        ? ` · ${detail.data.primaryAppointment.wardName}`
                        : ""
                    }`
                  : "未设置"}
              </Descriptions.Item>
              <Descriptions.Item label="人员类型">
                {appointmentTypeLabel(detail.data.primaryAppointment?.appointmentType)}
              </Descriptions.Item>
            </Descriptions>

            <Descriptions bordered size="small" column={1} title="账号与身份来源">
              <Descriptions.Item label="账号状态">
                {accountStateLabel(detail.data.account?.state ?? "NOT_OPENED")}
              </Descriptions.Item>
              <Descriptions.Item label="登录名">
                {accountLoginText(detail.data.account?.username, evidenceDetailsEnabled)}
              </Descriptions.Item>
              <Descriptions.Item label="已绑定身份">
                {identityBindingText(
                  detail.data.identities.map((identity) => identity.subjectHint),
                  evidenceDetailsEnabled,
                )}
              </Descriptions.Item>
            </Descriptions>

            {canManage && accountDetail.data && (
              <Space wrap>
                <Popconfirm title="确认生成新的临时密码？" onConfirm={resetPassword}>
                  <Button icon={<KeyOutlined />}>重置密码</Button>
                </Popconfirm>
                <Popconfirm
                  title={
                    accountDetail.data.status === "ACTIVE" ? "确认停用该账号？" : "确认启用该账号？"
                  }
                  onConfirm={toggleAccount}
                >
                  <Button danger={accountDetail.data.status === "ACTIVE"}>
                    {accountDetail.data.status === "ACTIVE" ? "停用账号" : "启用账号"}
                  </Button>
                </Popconfirm>
              </Space>
            )}

            {accountDetail.data && (
              <>
                <Divider />
                <Title level={5}>角色与组织范围</Title>
                <Table
                  rowKey={(record) => `${record.code}:${record.scopeLevel}:${record.scopeCode}`}
                  pagination={false}
                  dataSource={accountDetail.data.roles}
                  columns={[
                    { title: "角色", dataIndex: "displayName" },
                    {
                      title: "范围层级",
                      dataIndex: "scopeLevel",
                      render: orgLevelLabel,
                    },
                    {
                      title: "组织范围",
                      dataIndex: "scopeName",
                    },
                    {
                      title: "操作",
                      render: (_, role) =>
                        canManage && role.code !== "system-superadmin" ? (
                          <Popconfirm
                            title="确认移除该角色范围？"
                            onConfirm={async () => {
                              await removeRoleMutation.mutateAsync({
                                userId: accountDetail.data.userId,
                                roleCode: role.code,
                                scopeLevel: role.scopeLevel,
                                scopeCode: role.scopeCode,
                              });
                              await accountDetail.refetch();
                            }}
                          >
                            <Button type="link" danger>
                              移除
                            </Button>
                          </Popconfirm>
                        ) : (
                          <Text type="secondary">系统保护</Text>
                        ),
                    },
                  ]}
                />
                {canManage && (
                  <Form<RoleFormValues>
                    form={roleForm}
                    layout="vertical"
                    initialValues={{ roleCode: "engine-operator" }}
                    onFinish={assignRole}
                  >
                    <Space align="start" wrap>
                      <Form.Item name="roleCode" label="新增角色" rules={[{ required: true }]}>
                        <Select
                          className="mk-select-medium"
                          options={ROLE_OPTIONS.map((role) => ({
                            value: role.code,
                            label: role.name,
                          }))}
                        />
                      </Form.Item>
                      <Form.Item
                        name="orgUnitId"
                        label="组织范围"
                        rules={[{ required: true, message: "请选择组织范围" }]}
                      >
                        <OrgUnitSelect
                          scope="BUSINESS_SCOPE"
                          className="mk-select-wide"
                          placeholder="搜索组织名称或稳定组织身份"
                          onUnitChange={setSelectedRoleOrg}
                        />
                      </Form.Item>
                      <Form.Item label=" ">
                        <Button htmlType="submit" loading={assignRoleMutation.isPending}>
                          添加角色
                        </Button>
                      </Form.Item>
                    </Space>
                  </Form>
                )}

                <Divider />
                <Title level={5}>当前有效权限</Title>
                <Table
                  rowKey="code"
                  dataSource={accountDetail.data.effectivePermissions}
                  pagination={{ pageSize: 10, hideOnSinglePage: true }}
                  columns={[
                    { title: "权限", dataIndex: "displayName" },
                    {
                      title: "权限维度",
                      dataIndex: "dimension",
                      render: permissionDimensionLabel,
                    },
                    {
                      title: "风险",
                      dataIndex: "risk",
                      render: (value: string) => <Tag>{riskLabel(value)}</Tag>,
                    },
                  ]}
                />
              </>
            )}
          </Space>
        )}
      </Drawer>

      <Modal
        title="一次性账号凭证"
        open={activations.length > 0}
        closable={false}
        footer={
          <Button type="primary" onClick={() => setActivations([])}>
            已妥善记录
          </Button>
        }
      >
        <Alert
          type="warning"
          showIcon
          message="临时密码仅显示一次"
          description="请通过安全渠道交付给本人，用户首次登录必须修改密码。"
        />
        <Table
          rowKey="username"
          size="small"
          pagination={false}
          dataSource={activations}
          columns={[
            { title: "登录名", dataIndex: "username" },
            {
              title: "临时密码",
              dataIndex: "temporaryPassword",
              render: (value: string) => (
                <Text code copyable>
                  {value}
                </Text>
              ),
            },
          ]}
        />
      </Modal>
    </>
  );
}
