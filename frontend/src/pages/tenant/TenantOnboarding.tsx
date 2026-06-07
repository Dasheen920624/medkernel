import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  List,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  CheckCircleOutlined,
  ClusterOutlined,
  ExclamationCircleOutlined,
  PictureOutlined,
  PlusOutlined,
  SaveOutlined,
} from "@ant-design/icons";

import {
  useBranding,
  useCreateOrgUnit,
  useOnboardingReadiness,
  useOrgUnits,
  useProvisionTenant,
  useSecurityProfile,
  useTenants,
  useUpdateBranding,
  type ImplementationStep,
  type OrgUnit,
  type ProvisionTenantResult,
  type TenantSummary,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { platformTenantId } from "@/shared/config/tenantDictionary";
import { PageShell } from "@/shared/ui/PageShell";
import styles from "./Tenant.module.css";

const { Option } = Select;
const { Text, Title } = Typography;

type OrgLevelCode = OrgUnit["level"];

const levelRank: Record<OrgLevelCode, number> = {
  TENANT: 0,
  GROUP: 1,
  HOSPITAL: 2,
  CAMPUS: 3,
  SITE: 4,
  DEPARTMENT: 5,
};

const levelLabel: Record<OrgLevelCode, string> = {
  TENANT: "租户根",
  GROUP: "集团",
  HOSPITAL: "医院",
  CAMPUS: "院区",
  SITE: "社区服务点",
  DEPARTMENT: "科室",
};

const themeOptions = [
  { name: "深蓝", color: "var(--mk-theme-navy)", className: styles.themeNavy },
  { name: "青色", color: "var(--mk-theme-cyan)", className: styles.themeCyan },
  { name: "靛蓝", color: "var(--mk-theme-indigo)", className: styles.themeIndigo },
  { name: "紫色", color: "var(--mk-theme-violet)", className: styles.themeViolet },
  { name: "青绿", color: "var(--mk-theme-emerald)", className: styles.themeEmerald },
  { name: "金橙", color: "var(--mk-theme-amber)", className: styles.themeAmber },
];

function themeClassFor(color: string) {
  return themeOptions.find((theme) => theme.color === color)?.className ?? styles.themeNavy;
}

function stepTag(step: ImplementationStep) {
  if (step.status === "DONE") {
    return (
      <Tag icon={<CheckCircleOutlined />} color="success">
        已就绪
      </Tag>
    );
  }
  return (
    <Tag icon={<ExclamationCircleOutlined />} color="warning">
      阻塞
    </Tag>
  );
}

function orgStatusTag(status?: OrgUnit["status"]) {
  if (status === "SUSPENDED") {
    return <Tag color="warning">停用</Tag>;
  }
  if (status === "ARCHIVED") {
    return <Tag color="default">归档</Tag>;
  }
  return <Tag color="success">活跃</Tag>;
}

function readinessPercent(steps: ImplementationStep[]) {
  if (steps.length === 0) return 0;
  const done = steps.filter((step) => step.status === "DONE").length;
  return Math.round((done / steps.length) * 100);
}

function PlatformTenantProvisioning() {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [formOpen, setFormOpen] = useState(false);
  const [provisioned, setProvisioned] = useState<ProvisionTenantResult | null>(null);
  const { data: tenants = [], isLoading, isError, refetch } = useTenants();
  const provisionMutation = useProvisionTenant();

  const columns = useMemo<ColumnsType<TenantSummary>>(
    () => [
      {
        title: "租户标识",
        dataIndex: "tenantId",
        key: "tenantId",
        render: (tenantId: string) => <span className={styles.orgCode}>{tenantId}</span>,
      },
      {
        title: "租户名称",
        dataIndex: "name",
        key: "name",
        render: (name: string) => <Text strong>{name}</Text>,
      },
      {
        title: "状态",
        dataIndex: "status",
        key: "status",
        render: (status: string) => (
          <Tag color={status === "ACTIVE" ? "success" : "default"}>
            {status === "ACTIVE" ? "已启用" : status}
          </Tag>
        ),
      },
      {
        title: "开通时间",
        dataIndex: "createdAt",
        key: "createdAt",
        render: (createdAt: string) => new Date(createdAt).toLocaleString(),
      },
    ],
    [],
  );

  async function handleProvision() {
    try {
      const values = await form.validateFields();
      const result = await provisionMutation.mutateAsync({
        tenantId: values.tenantId.trim(),
        tenantName: values.tenantName.trim(),
        adminUsername: values.adminUsername.trim(),
      });
      setFormOpen(false);
      form.resetFields();
      setProvisioned(result);
      void refetch();
    } catch (error: unknown) {
      if (applyApiFieldErrors(form, error)) return;
      message.error(getApiErrorMessage(error, "客户租户开通失败"));
    }
  }

  if (isLoading) {
    return (
      <PageShell
        title="客户租户开通"
        description="读取客户租户台账"
        state="loading"
        stateProps={{
          title: "正在加载客户租户",
          description: "正在读取平台已开通的客户租户。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (isError) {
    return (
      <PageShell
        title="客户租户开通"
        description="请重试或联系平台运维"
        state="error"
        stateProps={{
          title: "客户租户台账读取失败",
          description: "请重试；若持续失败，请带 traceId 联系平台运维排查租户供应接口。",
          onRetry: () => refetch(),
        }}
      >
        <></>
      </PageShell>
    );
  }

  return (
    <>
      <PageShell
        title="客户租户开通"
        description="创建独立客户租户并交付首个管理员账号"
        primary={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setFormOpen(true)}>
            开通客户租户
          </Button>
        }
      >
        <Card title="客户租户">
          <Table
            dataSource={tenants}
            columns={columns}
            rowKey="tenantId"
            pagination={{ pageSize: 10 }}
            locale={{ emptyText: "尚未开通客户租户" }}
            size="small"
          />
        </Card>
      </PageShell>

      <Modal
        title="开通客户租户"
        open={formOpen}
        okText="确认开通"
        cancelText="取消"
        confirmLoading={provisionMutation.isPending}
        onOk={handleProvision}
        onCancel={() => {
          setFormOpen(false);
          form.resetFields();
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="tenantId"
            label="租户标识"
            rules={[
              { required: true, message: "请输入租户标识" },
              {
                pattern: /^[a-z0-9-]{2,64}$/,
                message: "仅允许 2-64 位小写字母、数字和连字符",
              },
            ]}
          >
            <Input placeholder="例如 t-renmin" autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="tenantName"
            label="租户名称"
            rules={[{ required: true, message: "请输入租户名称" }]}
          >
            <Input placeholder="例如 人民医院" autoComplete="organization" />
          </Form.Item>
          <Form.Item
            name="adminUsername"
            label="首个管理员登录名"
            rules={[{ required: true, message: "请输入首个管理员登录名" }]}
          >
            <Input placeholder="例如 renmin-admin" autoComplete="off" />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="系统将生成一次性临时密码"
            description="开通结果关闭后不再显示，请安全转交管理员；管理员首次登录必须修改密码。"
          />
        </Form>
      </Modal>

      <Modal
        title="客户租户已开通"
        open={Boolean(provisioned)}
        footer={
          <Button type="primary" onClick={() => setProvisioned(null)}>
            已安全记录
          </Button>
        }
        closable={false}
      >
        {provisioned && (
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Text>
              租户 <Text strong>{provisioned.tenantId}</Text> 与管理员{" "}
              <Text strong>{provisioned.adminUsername}</Text> 已创建。
            </Text>
            {provisioned.tempPassword ? (
              <Alert
                type="warning"
                showIcon
                message="一次性临时密码"
                description={<Text code>{provisioned.tempPassword}</Text>}
              />
            ) : (
              <Alert type="success" showIcon message="已使用指定初始密码" />
            )}
          </Space>
        )}
      </Modal>
    </>
  );
}

function CustomerTenantImplementation() {
  const { message } = App.useApp();
  const [activeTab, setActiveTab] = useState("org");
  const [form] = Form.useForm();
  const [brandForm] = Form.useForm();

  const {
    data: orgData,
    isLoading: orgLoading,
    isError: orgError,
    refetch: refetchOrgs,
  } = useOrgUnits({ size: 100 });
  const createOrgMutation = useCreateOrgUnit();

  const {
    data: readiness,
    isLoading: readinessLoading,
    isError: readinessError,
    refetch: refetchReadiness,
  } = useOnboardingReadiness();

  const {
    data: branding,
    isLoading: brandLoading,
    isError: brandError,
    refetch: refetchBranding,
  } = useBranding();
  const updateBrandingMutation = useUpdateBranding();

  useEffect(() => {
    if (!branding) return;
    brandForm.setFieldsValue({
      hospitalName: branding.hospitalName,
      logoUrl: branding.logoUrl,
      themeColor: branding.themeColor ?? "var(--mk-theme-navy)",
      expertMode: branding.expertMode ?? false,
    });
  }, [branding, brandForm]);

  const orgItems = useMemo(() => orgData?.items ?? [], [orgData?.items]);
  const readinessSteps = readiness?.steps ?? [];
  const blockerCount = readiness?.blockers.length ?? 0;
  const checkedAt = readiness?.checkedAt
    ? new Date(readiness.checkedAt).toLocaleString()
    : "未返回";
  const selectedLevel = Form.useWatch("level", form) as OrgLevelCode | undefined;
  const watchHospitalName =
    Form.useWatch("hospitalName", brandForm) ?? branding?.hospitalName ?? "未配置医院名称";
  const watchLogoUrl = Form.useWatch("logoUrl", brandForm) ?? branding?.logoUrl ?? "";
  const watchThemeColor =
    Form.useWatch("themeColor", brandForm) ?? branding?.themeColor ?? "var(--mk-theme-navy)";

  const parentCandidates = useMemo(() => {
    if (!selectedLevel || selectedLevel === "TENANT") return [];
    return orgItems.filter(
      (item) => item.status === "ACTIVE" && levelRank[item.level] < levelRank[selectedLevel],
    );
  }, [orgItems, selectedLevel]);

  const columns = useMemo<ColumnsType<OrgUnit>>(
    () => [
      {
        title: "组织编码",
        dataIndex: "code",
        key: "code",
        render: (code: string) => <span className={styles.orgCode}>{code}</span>,
      },
      {
        title: "名称",
        dataIndex: "name",
        key: "name",
        render: (name: string) => <Text strong>{name}</Text>,
      },
      {
        title: "层级",
        dataIndex: "level",
        key: "level",
        render: (level: OrgLevelCode) => (
          <Tag color={level === "TENANT" ? "blue" : "default"}>{levelLabel[level]}</Tag>
        ),
      },
      {
        title: "直接上级",
        dataIndex: "parentId",
        key: "parentId",
        render: (parentId: string | null) => parentId ?? <Text type="secondary">根节点</Text>,
      },
      {
        title: "状态",
        dataIndex: "status",
        key: "status",
        render: (status: OrgUnit["status"]) => orgStatusTag(status),
      },
    ],
    [],
  );

  async function handleOrgSubmit() {
    try {
      const values = await form.validateFields();
      await createOrgMutation.mutateAsync({
        parentId: values.parentId || null,
        level: values.level,
        code: values.code,
        name: values.name,
        namePinyin: values.namePinyin || null,
        specialtyId: values.specialtyId || null,
        status: "ACTIVE",
      });

      message.success("组织节点已创建");
      form.resetFields();
      void refetchOrgs();
      void refetchReadiness();
    } catch (error: unknown) {
      if (applyApiFieldErrors(form, error)) return;
      message.error(getApiErrorMessage(error, "组织节点创建失败"));
    }
  }

  async function handleBrandSubmit() {
    try {
      const values = await brandForm.validateFields();
      await updateBrandingMutation.mutateAsync({
        hospitalName: values.hospitalName,
        logoUrl: values.logoUrl || null,
        themeColor: values.themeColor,
        expertMode: values.expertMode,
      });

      message.success("品牌信息已保存");
      void refetchBranding();
    } catch (error: unknown) {
      if (applyApiFieldErrors(brandForm, error)) return;
      message.error(getApiErrorMessage(error, "品牌信息保存失败"));
    }
  }

  if (orgLoading || readinessLoading) {
    return (
      <PageShell
        title="租户实施配置"
        description="读取组织树与实施就绪状态"
        state="loading"
        stateProps={{
          title: "正在加载租户实施状态",
          description: "正在读取组织树、开通就绪门和当前品牌信息。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (orgError || readinessError) {
    return (
      <PageShell
        title="租户实施配置"
        description="请重试或联系信息科"
        state="error"
        stateProps={{
          title: "租户实施状态读取失败",
          description: "请重试；若持续失败，请带 traceId 联系信息科排查租户与组织引擎接口。",
          onRetry: () => {
            void refetchOrgs();
            void refetchReadiness();
          },
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (!readiness || readinessSteps.length === 0) {
    return (
      <PageShell
        title="租户实施配置"
        description="等待实施就绪检查返回步骤"
        state="empty"
        stateProps={{
          title: "暂无实施就绪步骤",
          description: "当前租户尚未返回实施就绪步骤，请确认租户上下文已经建立。",
          onRetry: () => {
            void refetchReadiness();
          },
        }}
      >
        <></>
      </PageShell>
    );
  }

  const ready = readiness.ready;

  return (
    <PageShell
      title="租户实施配置"
      description="配置当前客户租户的组织与品牌，实时核查实施就绪状态"
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Card className={styles.readinessSummaryCard}>
              <Text type="secondary">组织节点</Text>
              <Title level={3}>{orgData?.total ?? orgItems.length}</Title>
              <Text type="secondary">来自 engine org 组织树</Text>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card className={styles.readinessSummaryCard}>
              <Text type="secondary">就绪进度</Text>
              <Title level={3}>{readinessPercent(readinessSteps)}%</Title>
              <Progress
                percent={readinessPercent(readinessSteps)}
                status={ready ? "success" : "active"}
                size="small"
              />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card className={styles.readinessSummaryCard}>
              <Text type="secondary">阻塞项</Text>
              <Title level={3}>{blockerCount}</Title>
              <Text type="secondary">检查时间：{checkedAt}</Text>
            </Card>
          </Col>
        </Row>

        {ready ? (
          <Alert
            type="success"
            showIcon
            message="实施就绪检查已通过"
            description="组织、用户、权限、适配器、资产与灰度发布均已具备真实证据。"
          />
        ) : (
          <Alert
            type="warning"
            showIcon
            message="实施就绪检查未通过"
            description={
              <List
                size="small"
                split={false}
                dataSource={readiness.blockers}
                renderItem={(blocker) => (
                  <List.Item className={styles.blockerItem}>
                    <Text type="warning">{blocker}</Text>
                  </List.Item>
                )}
              />
            }
          />
        )}

        <Card title="实施就绪检查">
          <div className={styles.readinessGrid}>
            {readinessSteps.map((step) => (
              <Card key={step.key} size="small" className={styles.readinessStepCard}>
                <Space direction="vertical" size="small" className="mk-full-width">
                  <div className={styles.stepTitleRow}>
                    <Title level={5} className={styles.stepTitle}>
                      {step.title}
                    </Title>
                    {stepTag(step)}
                  </div>
                  <Text type={step.status === "DONE" ? "success" : "secondary"}>
                    {step.evidence ?? "阻塞原因见上方就绪门清单"}
                  </Text>
                </Space>
              </Card>
            ))}
          </div>
        </Card>

        {brandError && (
          <Alert
            type="warning"
            showIcon
            message="品牌信息暂未读取"
            description="组织树和开通门禁仍可继续处理；品牌信息保存前请先重试读取。"
            action={<Button onClick={() => refetchBranding()}>重试品牌信息</Button>}
          />
        )}

        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          className="mk-tabs-premium"
          items={[
            {
              key: "org",
              label: (
                <Space>
                  <ClusterOutlined />
                  <span>组织树</span>
                </Space>
              ),
              children: (
                <div className={styles.onboardingGrid}>
                  <Card title="新增组织节点" className={styles.onboardingPanel}>
                    <Form form={form} layout="vertical">
                      <Form.Item
                        name="level"
                        label="组织层级"
                        rules={[{ required: true, message: "请选择组织层级" }]}
                      >
                        <Select
                          placeholder="请选择组织层级"
                          onChange={() => form.setFieldValue("parentId", undefined)}
                        >
                          <Option value="GROUP">集团</Option>
                          <Option value="HOSPITAL">医院</Option>
                          <Option value="CAMPUS">院区</Option>
                          <Option value="SITE">社区服务点</Option>
                          <Option value="DEPARTMENT">科室</Option>
                        </Select>
                      </Form.Item>

                      <Form.Item
                        name="code"
                        label="组织编码"
                        rules={[{ required: true, message: "请输入组织编码" }]}
                      >
                        <Input placeholder="输入租户内唯一组织编码" />
                      </Form.Item>

                      <Form.Item
                        name="name"
                        label="组织名称"
                        rules={[{ required: true, message: "请输入组织名称" }]}
                      >
                        <Input placeholder="输入组织中文名称" />
                      </Form.Item>

                      <Form.Item
                        name="parentId"
                        label="直接上级"
                        rules={[
                          {
                            required: Boolean(selectedLevel),
                            message: "请选择直接上级组织节点",
                          },
                        ]}
                      >
                        <Select
                          placeholder={
                            selectedLevel ? "选择任一合法上级组织节点" : "请先选择组织层级"
                          }
                          allowClear
                          disabled={!selectedLevel}
                        >
                          {parentCandidates.map((parent) => (
                            <Option key={parent.id} value={parent.id}>
                              {parent.name}（{levelLabel[parent.level]}）
                            </Option>
                          ))}
                        </Select>
                      </Form.Item>

                      {selectedLevel && parentCandidates.length === 0 && (
                        <Alert
                          type="info"
                          showIcon
                          className={styles.formHint}
                          message={`当前没有可作为${levelLabel[selectedLevel]}上级的活跃组织节点。`}
                        />
                      )}

                      <Form.Item name="specialtyId" label="专病适用维度">
                        <Input placeholder="可选；用于规则、路径、知识包的横切适用范围" />
                      </Form.Item>

                      <Button
                        icon={<PlusOutlined />}
                        onClick={handleOrgSubmit}
                        loading={createOrgMutation.isPending}
                      >
                        新增组织节点
                      </Button>
                    </Form>
                  </Card>

                  <Card title="当前组织树" loading={orgLoading} className={styles.onboardingPanel}>
                    <Table
                      dataSource={orgItems}
                      columns={columns}
                      rowKey={(record) => record.id ?? `${record.level}-${record.code}`}
                      pagination={{ pageSize: 8 }}
                      size="small"
                    />
                  </Card>
                </div>
              ),
            },
            {
              key: "brand",
              label: (
                <Space>
                  <PictureOutlined />
                  <span>品牌信息</span>
                </Space>
              ),
              children: (
                <div className={styles.onboardingGrid}>
                  <Card title="品牌信息" loading={brandLoading} className={styles.onboardingPanel}>
                    <Form form={brandForm} layout="vertical">
                      <Form.Item
                        name="hospitalName"
                        label="医院名称"
                        rules={[{ required: true, message: "请输入医院名称" }]}
                      >
                        <Input placeholder="输入系统左上角显示的医院名称" />
                      </Form.Item>

                      <Form.Item name="logoUrl" label="Logo URL">
                        <Input placeholder="粘贴院方授权的 HTTPS Logo 地址" />
                      </Form.Item>

                      <Form.Item name="themeColor" label="主题色">
                        <Input placeholder="选择预设主题色或输入合法 CSS 变量" />
                      </Form.Item>

                      <Form.Item label="预设主题色">
                        <div className={styles.themeSelectorWrap}>
                          {themeOptions.map((theme) => (
                            <Tooltip key={theme.color} title={theme.name}>
                              <button
                                type="button"
                                aria-label={`选择${theme.name}`}
                                className={`${styles.themeDot} ${theme.className} ${
                                  watchThemeColor === theme.color ? styles.themeDotActive : ""
                                }`}
                                onClick={() => brandForm.setFieldValue("themeColor", theme.color)}
                              />
                            </Tooltip>
                          ))}
                        </div>
                      </Form.Item>

                      <Form.Item name="expertMode" label="专家模式" valuePropName="checked">
                        <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                      </Form.Item>

                      <Button
                        icon={<SaveOutlined />}
                        onClick={handleBrandSubmit}
                        loading={updateBrandingMutation.isPending}
                      >
                        保存品牌信息
                      </Button>
                    </Form>
                  </Card>

                  <div className={styles.brandPreview}>
                    <Title level={5} className={styles.brandPreviewTitle}>
                      品牌预览
                    </Title>
                    <div className={styles.brandPreviewContainer}>
                      <div
                        className={`${styles.brandPreviewHeader} ${themeClassFor(watchThemeColor)}`}
                      >
                        {watchLogoUrl ? (
                          <img
                            src={watchLogoUrl}
                            className={styles.brandPreviewLogo}
                            alt="医院 Logo"
                          />
                        ) : (
                          <div className={styles.brandPreviewLogoPlaceholder}>院</div>
                        )}
                        <Title level={5} className={styles.brandPreviewName}>
                          {watchHospitalName}
                        </Title>
                      </div>
                      <div className={styles.brandPreviewBody}>
                        <div className={styles.brandPreviewLine} />
                        <div className={styles.brandPreviewLineShort} />
                        <Tag color="success">{watchHospitalName} · 已连接当前租户</Tag>
                      </div>
                    </div>
                  </div>
                </div>
              ),
            },
          ]}
        />
      </Space>
    </PageShell>
  );
}

export default function TenantOnboarding() {
  const security = useSecurityProfile();

  if (security.isLoading) {
    return (
      <PageShell
        title="租户管理"
        description="读取当前租户上下文"
        state="loading"
        stateProps={{
          title: "正在确认租户范围",
          description: "正在读取当前用户的租户与权限画像。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  const tenantId = security.data?.dataScope?.tenantId;
  if (security.isError || !tenantId) {
    return (
      <PageShell
        title="租户管理"
        description="当前租户上下文不可用"
        state="error"
        stateProps={{
          title: "无法确认当前租户",
          description: "请重新登录；若持续失败，请带 traceId 联系平台运维排查安全画像。",
          onRetry: () => security.refetch(),
        }}
      >
        <></>
      </PageShell>
    );
  }

  return tenantId === platformTenantId ? (
    <PlatformTenantProvisioning />
  ) : (
    <CustomerTenantImplementation />
  );
}
