import { useState, useMemo } from "react";
import {
  Card,
  Form,
  Input,
  Select,
  Button,
  Space,
  Table,
  Tabs,
  Tag,
  Switch,
  message,
  Typography,
  Tooltip,
} from "antd";
import {
  PlusOutlined,
  SaveOutlined,
  PictureOutlined,
  ClusterOutlined,
  ExperimentOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { useOrgUnits, useCreateOrgUnit, useBranding, useUpdateBranding } from "@/shared/api/hooks";
import styles from "./Tenant.module.css";

const { Option } = Select;
const { Text, Title } = Typography;

// 集中维护品牌预设调色盘，供开通页与预览区复用
function getPresetThemes() {
  return [
    { name: "极客深蓝", color: "var(--mk-theme-navy)", className: styles.themeNavy },
    { name: "深海青色", color: "var(--mk-theme-cyan)", className: styles.themeCyan },
    { name: "典雅靛蓝", color: "var(--mk-theme-indigo)", className: styles.themeIndigo },
    { name: "明媚紫色", color: "var(--mk-theme-violet)", className: styles.themeViolet },
    { name: "森林青绿", color: "var(--mk-theme-emerald)", className: styles.themeEmerald },
    { name: "暖阳金橙", color: "var(--mk-theme-amber)", className: styles.themeAmber },
  ];
}

// 统一产出主题色预览样式，避免各处重复拼装样式对象
const getThemeStyle = (color: string) => ({
  backgroundColor: color,
});

/**
 * GA-TENANT-01 · 租户开通与品牌定制工作台
 */
export default function TenantOnboarding() {
  const [activeTab, setActiveTab] = useState("org");
  const [form] = Form.useForm();
  const [brandForm] = Form.useForm();

  // 1. 组织数据相关 hooks
  const { data: orgData, isLoading: orgLoading, refetch: refetchOrgs } = useOrgUnits({ size: 100 });
  const createOrgMutation = useCreateOrgUnit();

  // 2. 品牌定制相关 hooks
  const { data: branding, isLoading: brandLoading, refetch: refetchBranding } = useBranding();
  const updateBrandingMutation = useUpdateBranding();

  // 3. 品牌表单默认值绑定
  useMemo(() => {
    if (branding) {
      brandForm.setFieldsValue({
        hospitalName: branding.hospitalName,
        logoUrl: branding.logoUrl,
        themeColor: branding.themeColor ?? "var(--mk-theme-navy)",
        expertMode: branding.expertMode ?? false,
      });
    }
  }, [branding, brandForm]);

  // 4. 品牌实时预览状态
  const watchHospitalName =
    Form.useWatch("hospitalName", brandForm) ?? branding?.hospitalName ?? "MedKernel 智能示范医院";
  const watchLogoUrl = Form.useWatch("logoUrl", brandForm) ?? branding?.logoUrl ?? "";
  const watchThemeColor =
    Form.useWatch("themeColor", brandForm) ?? branding?.themeColor ?? "var(--mk-theme-navy)";

  // 获取预设调色盘
  const presetThemes = getPresetThemes();

  // 组织列表列定义
  const columns = useMemo(
    () => [
      {
        title: "机构编码 (Code)",
        dataIndex: "code",
        key: "code",
        render: (code: string) => <span className={styles.badgeMpi}>{code}</span>,
      },
      {
        title: "机构名称",
        dataIndex: "name",
        key: "name",
        render: (name: string) => <Text strong>{name}</Text>,
      },
      {
        title: "组织级别 (Level)",
        dataIndex: "level",
        key: "level",
        render: (level: string) => {
          const colors: Record<string, string> = {
            HOSPITAL: "blue",
            CAMPUS: "cyan",
            DEPARTMENT: "purple",
            SPECIALTY: "geekblue",
          };
          return <Tag color={colors[level] || "default"}>{level}</Tag>;
        },
      },
      {
        title: "上级机构 ID",
        dataIndex: "parentId",
        key: "parentId",
        render: (parentId: string | null) => parentId ?? <Text type="secondary">根节点</Text>,
      },
      {
        title: "专科标识",
        dataIndex: "specialtyId",
        key: "specialtyId",
        render: (specialtyId: string | null) => specialtyId ?? "-",
      },
      {
        title: "启用状态",
        dataIndex: "status",
        key: "status",
        render: (status: string) => (
          <Tag color={status === "ACTIVE" ? "success" : "error"}>
            {status === "ACTIVE" ? "活跃" : "挂起"}
          </Tag>
        ),
      },
    ],
    [],
  );

  // 组织单元提交创建
  const handleOrgSubmit = async () => {
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

      message.success("组织单元创建成功，已写入可观测性审计日志");
      form.resetFields();
      refetchOrgs();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      const errorMsg = err?.response?.data?.message || err?.message || "创建失败";
      message.error(`创建组织失败：${errorMsg}`);
    }
  };

  // 品牌个性化提交保存
  const handleBrandSubmit = async () => {
    try {
      const values = await brandForm.validateFields();
      await updateBrandingMutation.mutateAsync({
        hospitalName: values.hospitalName,
        logoUrl: values.logoUrl || null,
        themeColor: values.themeColor,
        expertMode: values.expertMode,
      });

      message.success("平台品牌定制个性化保存成功！已动态实时渲染。");
      refetchBranding();
    } catch {
      message.error("品牌配置保存失败。");
    }
  };

  // 过滤出可作为上级节点的组织列表
  const parentCandidates = useMemo(
    () => orgData?.items?.filter((it) => it.level === "HOSPITAL" || it.level === "CAMPUS") ?? [],
    [orgData],
  );

  return (
    <PageShell
      title="租户开通与品牌沙箱"
      description="自主注册管理当前租户的七层组织树机构，在此配置个性化品牌 Logo、医院命名及专属 UI 色系。"
    >
      <Tabs activeKey={activeTab} onChange={setActiveTab} className="mk-tabs-premium">
        <Tabs.TabPane
          tab={
            <Space>
              <ClusterOutlined />
              <span>组织结构登记</span>
            </Space>
          }
          key="org"
        >
          <div className={styles.sandboxLayout}>
            {/* 左侧创建表单 */}
            <div className={styles.sandboxForm}>
              <Card title="开通入驻新机构节点">
                <Form form={form} layout="vertical">
                  <Form.Item
                    name="level"
                    label="组织层级"
                    rules={[{ required: true, message: "请选择组织层级" }]}
                  >
                    <Select placeholder="请选择级别...">
                      <Option value="HOSPITAL">医院总部 (HOSPITAL)</Option>
                      <Option value="CAMPUS">分院院区 (CAMPUS)</Option>
                      <Option value="DEPARTMENT">临床科室 (DEPARTMENT)</Option>
                      <Option value="SPECIALTY">专病维度 (SPECIALTY)</Option>
                    </Select>
                  </Form.Item>

                  <Form.Item
                    name="code"
                    label="唯一识别编码 (Code)"
                    rules={[{ required: true, message: "请输入识别编码" }]}
                  >
                    <Input placeholder="例如：HOSP-002 或 DEPT-GYN" />
                  </Form.Item>

                  <Form.Item
                    name="name"
                    label="机构中文名称"
                    rules={[{ required: true, message: "请输入机构名称" }]}
                  >
                    <Input placeholder="例如：协和医院西院区" />
                  </Form.Item>

                  <Form.Item name="parentId" label="上级组织节点">
                    <Select placeholder="选择上级关联节点（可选）" allowClear>
                      {parentCandidates.map((p) => (
                        <Option key={p.id} value={p.id}>
                          {p.name} ({p.level})
                        </Option>
                      ))}
                    </Select>
                  </Form.Item>

                  <Form.Item name="specialtyId" label="专科 ID">
                    <Input placeholder="医学专科匹配（可选），如 Stroke" />
                  </Form.Item>

                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={handleOrgSubmit}
                    loading={createOrgMutation.isPending}
                    block
                  >
                    原子新增并持久化
                  </Button>
                </Form>
              </Card>
            </div>

            {/* 右侧树节点列表 */}
            <div className={styles.sandboxForm}>
              <Card title="当前已登记组织单元" loading={orgLoading}>
                <Table
                  dataSource={orgData?.items ?? []}
                  columns={columns}
                  rowKey="id"
                  pagination={{ pageSize: 5 }}
                  size="small"
                />
              </Card>
            </div>
          </div>
        </Tabs.TabPane>

        <Tabs.TabPane
          tab={
            <Space>
              <ExperimentOutlined />
              <span>品牌定制沙箱</span>
            </Space>
          }
          key="brand"
        >
          <div className={styles.sandboxLayout}>
            {/* 左侧配置项 */}
            <div className={styles.sandboxForm}>
              <Card title="平台视觉品牌配置" loading={brandLoading}>
                <Form form={brandForm} layout="vertical">
                  <Form.Item
                    name="hospitalName"
                    label="示范医院名称"
                    rules={[{ required: true, message: "医院物理名称不能为空" }]}
                  >
                    <Input placeholder="输入将在系统左上角呈现的医院定制名称" />
                  </Form.Item>

                  <Form.Item name="logoUrl" label="定制 Logo 图片 URL">
                    <Input
                      placeholder="Logo 线上 URL，如 http://assets/my-logo.png"
                      prefix={<PictureOutlined />}
                    />
                  </Form.Item>

                  <Form.Item name="themeColor" label="UI 主题色配置">
                    <Input placeholder="CSS 合法色值，支持 HEX / RGB" />
                  </Form.Item>

                  <Form.Item label="预设高保真调色盘">
                    <div className={styles.themeSelectorWrap}>
                      {presetThemes.map((theme) => (
                        <Tooltip key={theme.color} title={theme.name}>
                          <div
                            style={getThemeStyle(theme.color)}
                            className={`${styles.themeDot} ${
                              watchThemeColor === theme.color ? styles.themeDotActive : ""
                            }`}
                            onClick={() => brandForm.setFieldValue("themeColor", theme.color)}
                          />
                        </Tooltip>
                      ))}
                    </div>
                  </Form.Item>

                  <Form.Item name="expertMode" label="全面开启专家模式" valuePropName="checked">
                    <Switch checkedChildren="专家版" unCheckedChildren="标准版" />
                  </Form.Item>

                  <Button
                    type="primary"
                    icon={<SaveOutlined />}
                    onClick={handleBrandSubmit}
                    loading={updateBrandingMutation.isPending}
                    block
                  >
                    保存品牌定制
                  </Button>
                </Form>
              </Card>
            </div>

            {/* 右侧动态沙箱实时预览 */}
            <div className={styles.sandboxPreview}>
              <Title level={5} type="secondary" className={styles.sandboxPreviewTitle}>
                品牌定制效果实时沙箱预览
              </Title>
              <div className={styles.previewContainer}>
                {/* 动态主题头部 */}
                <div style={getThemeStyle(watchThemeColor)} className={styles.previewHeader}>
                  {watchLogoUrl ? (
                    <img src={watchLogoUrl} className={styles.previewLogo} alt="Hospital Logo" />
                  ) : (
                    <div className={styles.previewLogoPlaceholder}>H</div>
                  )}
                  <Title level={5} className={styles.previewTitle}>
                    {watchHospitalName}
                  </Title>
                </div>
                {/* 主题预览主体 */}
                <div className={styles.previewBody}>
                  <div className={styles.previewPlaceholderBar} />
                  <div className={styles.previewPlaceholderBarShort} />
                  <div className={styles.previewPlaceholderBar} />
                  <Tag color="success">{watchHospitalName} · 正常运行中</Tag>
                </div>
              </div>
            </div>
          </div>
        </Tabs.TabPane>
      </Tabs>
    </PageShell>
  );
}
