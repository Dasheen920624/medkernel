import {
  Alert,
  Button,
  Card,
  Divider,
  Form,
  Input,
  Select,
  Space,
  Tag,
  Typography,
  theme,
} from "antd";
import {
  DownOutlined,
  IdcardOutlined,
  LockOutlined,
  LoginOutlined,
  QuestionCircleOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import { ThemeSwitcher } from "@/features/theme-switcher/ThemeSwitcher";
import {
  useDelegatedAuthStatus,
  useLogin,
  useLoginTenantDirectory,
  type DelegatedAuthStatus,
  type LoginTenantOption,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import {
  defaultTenantId,
  platformTenantDescription,
  platformTenantLabel,
} from "@/shared/config/tenantDictionary";
import styles from "./Login.module.css";

const { Title, Text } = Typography;

const helpItems = [
  { label: "首次登录", value: "使用管理员开通的账号进入，首次登录后按医院策略改密。" },
  { label: "忘记密码", value: "请联系本院管理员重置密码，重置操作会进入审计留痕。" },
  { label: "统一身份", value: "接入状态由后端实时返回；未接入时页面只展示状态，不伪造入口。" },
];

const fallbackDelegatedProviders = ["OIDC", "CAS", "SAML", "国密CA"];
const fallbackPlatformTenant: LoginTenantOption = {
  tenantId: defaultTenantId,
  name: platformTenantLabel,
  kind: "PLATFORM",
};

type DelegatedAlert = {
  type: "info" | "error" | "success" | "warning";
  message: string;
  description: string;
};

function buildDelegatedAlert({
  status,
  state,
  isLoading,
  isError,
  error,
}: {
  status?: DelegatedAuthStatus;
  state: string;
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
}): DelegatedAlert {
  if (isLoading) {
    return {
      type: "info",
      message: "正在读取统一身份状态",
      description: "请稍候，系统正在确认院方统一身份认证的接入状态。",
    };
  }

  if (isError) {
    return {
      type: "error",
      message: "统一身份状态读取失败",
      description: getApiErrorMessage(
        error,
        "暂时无法读取统一身份状态，请先使用医院账号密码登录。",
      ),
    };
  }

  if (!status?.enabled) {
    return {
      type: "info",
      message: "统一身份未开放",
      description: status?.message || "当前登录模式未开放统一身份入口，请使用医院账号密码登录。",
    };
  }

  if (state === "READY") {
    return {
      type: "success",
      message: "统一身份已接入",
      description:
        status.message ||
        "统一身份由医院信息中心配置；双因素认证、国密与国产 CA 由系统按策略自动选择。",
    };
  }

  return {
    type: "warning",
    message: "统一身份暂未接入",
    description:
      status.message ||
      "统一身份由医院信息中心配置；双因素认证、国密与国产 CA 由系统按策略自动选择。",
  };
}

/**
 * 默认登录路径 + 双因素认证/统一身份折叠区。
 *
 * 与 docs/CONSTITUTION.md §1 第 6 条对齐：
 * - 默认只有账号密码 1 个主动作
 * - 统一身份认证（CAS/OIDC/SAML）作为次级折叠区
 * - 双因素认证 / 国密策略不让用户手动选，由系统按医院策略
 * - ICP/公安备案、用户协议、隐私政策必须保留
 */
export default function Login() {
  const [showSso, setShowSso] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [showPlatformTenant, setShowPlatformTenant] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [loginForm] = Form.useForm<{
    username: string;
    password: string;
    tenantId?: string;
  }>();
  const navigate = useNavigate();
  const login = useLogin();
  const delegatedAuthStatus = useDelegatedAuthStatus(showSso);
  const loginTenantDirectory = useLoginTenantDirectory();
  const { token } = theme.useToken();

  const loginThemeStyle = {
    "--mk-login-text": token.colorText,
    "--mk-login-text-muted": token.colorTextDescription,
    "--mk-login-text-secondary": token.colorTextSecondary,
    "--mk-login-primary": token.colorPrimary,
    "--mk-login-surface": token.colorBgContainer,
    "--mk-login-surface-tint": token.colorPrimaryBg,
    "--mk-login-page-bg": `linear-gradient(135deg, ${token.colorPrimaryBg}, ${token.colorBgLayout})`,
    "--mk-login-border": token.colorBorderSecondary,
    "--mk-login-primary-border": token.colorPrimaryBorder,
    "--mk-login-fill": token.colorFillQuaternary,
    "--mk-login-layout": token.colorBgLayout,
    "--mk-login-shadow": token.boxShadow,
    "--mk-login-radius": `${token.borderRadius}px`,
    "--mk-login-heading-1": `${token.fontSizeHeading1}px`,
    "--mk-login-heading-2": `${token.fontSizeHeading2}px`,
    "--mk-login-font-lg": `${token.fontSizeLG}px`,
    "--mk-login-font-sm": `${token.fontSizeSM}px`,
    "--mk-login-control-font": `${Math.max(token.fontSize, token.fontSizeLG)}px`,
    "--mk-login-control-height": `${Math.max(token.controlHeight, token.controlHeightLG)}px`,
  } as CSSProperties;

  async function handleSubmit(values: { username: string; password: string; tenantId?: string }) {
    setErrorMsg(null);
    try {
      const result = await login.mutateAsync({
        username: values.username,
        password: values.password,
        tenantId: values.tenantId?.trim() || undefined,
      });
      if (result.mustChangePwd || (result.mfaRequired && !result.mfaBound)) {
        navigate("/bootstrap", {
          state: {
            phase: result.mustChangePwd ? "change-password" : "mfa",
            login: result,
            username: values.username,
            tenantId: result.tenantId,
          },
        });
        return;
      }
      navigate("/dashboard");
    } catch (err: unknown) {
      if (applyApiFieldErrors(loginForm, err)) {
        setErrorMsg(getApiErrorMessage(err, "登录失败：用户名或密码不正确"));
        return;
      }
      setErrorMsg(getApiErrorMessage(err, "登录失败：用户名或密码不正确"));
    }
  }

  const delegatedStatus = delegatedAuthStatus.data;
  const delegatedProviders =
    delegatedStatus?.providers && delegatedStatus.providers.length > 0
      ? delegatedStatus.providers
      : fallbackDelegatedProviders;
  const delegatedState = delegatedStatus?.status ?? "NOT_CONNECTED";
  const delegatedAlert = buildDelegatedAlert({
    status: delegatedStatus,
    state: delegatedState,
    isLoading: delegatedAuthStatus.isLoading,
    isError: delegatedAuthStatus.isError,
    error: delegatedAuthStatus.error,
  });
  const tenantDirectory = loginTenantDirectory.data;
  const hasCustomerTenants = tenantDirectory?.hasCustomerTenants ?? false;
  const platformTenant = tenantDirectory?.platformTenant ?? fallbackPlatformTenant;
  const primaryTenantOptions = useMemo(() => {
    const tenants = tenantDirectory?.primaryTenants?.length
      ? tenantDirectory.primaryTenants
      : [{ tenantId: defaultTenantId, name: platformTenantLabel, kind: "PLATFORM" }];
    return tenants.map(toSelectOption);
  }, [tenantDirectory?.primaryTenants]);
  const platformTenantOption = useMemo(() => toSelectOption(platformTenant), [platformTenant]);
  const tenantOptions = useMemo(
    () =>
      hasCustomerTenants && !showPlatformTenant ? primaryTenantOptions : [platformTenantOption],
    [hasCustomerTenants, platformTenantOption, primaryTenantOptions, showPlatformTenant],
  );
  const tenantFieldLabel =
    hasCustomerTenants && !showPlatformTenant ? "客户 / 集团租户" : "租户标识";
  let tenantFieldExtra = platformTenantDescription;
  if (hasCustomerTenants && showPlatformTenant) {
    tenantFieldExtra = "仅平台开发者和运维人员管理全局知识源时使用；客户定制不会回写平台主租户。";
  }
  if (hasCustomerTenants && !showPlatformTenant) {
    tenantFieldExtra = "优先使用已开通的客户或集团租户；平台主租户在下方第二层。";
  }
  const activeTenantLayerLabel =
    hasCustomerTenants && !showPlatformTenant ? "客户 / 集团租户" : "平台主租户";

  useEffect(() => {
    const nextTenantId = tenantOptions[0]?.value ?? defaultTenantId;
    loginForm.setFieldValue("tenantId", nextTenantId);
  }, [loginForm, tenantOptions]);

  return (
    <main
      aria-busy={login.isPending}
      aria-label="登录 MedKernel 工作台"
      className={styles.page}
      style={loginThemeStyle}
    >
      <div className={styles.themeSwitcher}>
        <ThemeSwitcher syncRemote={false} />
      </div>

      <Card className={styles.loginCard} bordered={false}>
        <div className={styles.cardStack}>
          <div className={styles.cardHeader}>
            <Space size={8} wrap className={styles.kickerTags}>
              <Tag color="processing">{activeTenantLayerLabel}</Tag>
              <Tag>安全登录</Tag>
              <Tag>内网可用</Tag>
            </Space>
            <Title level={2} className={styles.cardTitle}>
              登录工作台
            </Title>
            <Text type="secondary">使用医院账号或统一身份继续</Text>
          </div>

          {errorMsg && <Alert type="error" showIcon message="登录失败" description={errorMsg} />}

          <Form
            disabled={login.isPending}
            form={loginForm}
            initialValues={{ tenantId: defaultTenantId }}
            layout="vertical"
            requiredMark={false}
            onFinish={handleSubmit}
          >
            <Form.Item
              label="工号 / 账号"
              name="username"
              rules={[{ required: true, message: "请输入工号或账号" }]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="请输入医院账号"
                size="large"
                autoComplete="username"
              />
            </Form.Item>
            <Form.Item
              label="密码"
              name="password"
              rules={[{ required: true, message: "请输入密码" }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="请输入密码"
                size="large"
                autoComplete="current-password"
              />
            </Form.Item>
            <Form.Item label={tenantFieldLabel} name="tenantId" extra={tenantFieldExtra}>
              <Select
                loading={loginTenantDirectory.isLoading}
                options={tenantOptions}
                optionFilterProp="label"
                placeholder={
                  hasCustomerTenants && !showPlatformTenant
                    ? "请选择客户或集团租户"
                    : "请选择平台主租户"
                }
                showSearch
                size="large"
              />
            </Form.Item>
            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                block
                size="large"
                icon={<LoginOutlined />}
                loading={login.isPending}
              >
                进入工作台
              </Button>
            </Form.Item>
          </Form>

          <div className={styles.policyStrip}>
            <SafetyCertificateOutlined aria-hidden="true" />
            <Text>系统将按医院策略自动校验双因素认证、国密通道与会话安全。</Text>
          </div>

          <Button
            aria-label="首次部署接管"
            className={styles.secondaryEntry}
            icon={<RocketOutlined />}
            onClick={() => navigate("/bootstrap")}
          >
            首次部署接管
          </Button>

          {hasCustomerTenants && (
            <div className={styles.tenantLayer}>
              <Button
                type="link"
                size="small"
                className={styles.tenantLayerButton}
                onClick={() => setShowPlatformTenant(!showPlatformTenant)}
              >
                {showPlatformTenant ? "返回客户/集团登录" : "平台主租户登录"}
              </Button>
              {showPlatformTenant && (
                <Text type="secondary" className={styles.helperText}>
                  平台主租户（唯一内置）只维护全局知识源和标准包；客户和集团定制在各自租户内新增或覆盖。
                </Text>
              )}
            </div>
          )}

          <div>
            <Divider className={styles.divider}>
              <Button
                type="link"
                size="small"
                className={styles.ssoToggle}
                aria-controls="delegated-auth-panel"
                aria-expanded={showSso}
                aria-label={showSso ? "收起统一身份认证" : "院方统一身份认证"}
                onClick={() => setShowSso(!showSso)}
              >
                <IdcardOutlined aria-hidden="true" />
                {showSso ? "收起统一身份认证" : "院方统一身份认证"}
                <DownOutlined
                  aria-hidden="true"
                  className={showSso ? styles.toggleIconOpen : styles.toggleIcon}
                />
              </Button>
            </Divider>
            {showSso && (
              <div id="delegated-auth-panel" className={styles.ssoStack}>
                <Alert
                  className={styles.ssoStatus}
                  type={delegatedAlert.type}
                  showIcon
                  message={delegatedAlert.message}
                  description={delegatedAlert.description}
                />
                <div className={styles.providerGrid} aria-label="统一身份方式">
                  {delegatedProviders.map((provider) => (
                    <Button
                      block
                      disabled
                      className={styles.providerButton}
                      key={provider}
                      loading={delegatedAuthStatus.isLoading}
                    >
                      {provider}（{delegatedState}）
                    </Button>
                  ))}
                </div>
                <Text type="secondary" className={styles.helperText}>
                  当前页只展示已配置状态；真实院方 IdP、证书链和回调地址完成配置后才会开放跳转。
                </Text>
              </div>
            )}
          </div>

          <div>
            <Divider className={styles.divider}>
              <Button
                type="link"
                size="small"
                className={styles.helpToggle}
                aria-controls="login-help-panel"
                aria-expanded={showHelp}
                aria-label="登录帮助"
                onClick={() => setShowHelp(!showHelp)}
              >
                <QuestionCircleOutlined aria-hidden="true" />
                登录帮助
                <DownOutlined
                  aria-hidden="true"
                  className={showHelp ? styles.toggleIconOpen : styles.toggleIcon}
                />
              </Button>
            </Divider>
            {showHelp && (
              <div id="login-help-panel" className={styles.helpList} aria-label="登录帮助内容">
                {helpItems.map((item) => (
                  <div className={styles.helpItem} key={item.label}>
                    <Text strong>{item.label}</Text>
                    <Text type="secondary">{item.value}</Text>
                  </div>
                ))}
              </div>
            )}
          </div>

          <footer className={`${styles.complianceFooter} ${styles.compactFooter}`}>
            <Text type="secondary" className={styles.helperText}>
              用户协议 · 隐私政策 · 个人信息收集清单由部署方在正式上线前配置
            </Text>
            <Text type="secondary" className={styles.helperText}>
              ICP 备案号待填 · 公安备案号待填 · 等保 2.0 三级 · 商密评测预审中
            </Text>
          </footer>
        </div>
      </Card>
    </main>
  );
}

function toSelectOption(tenant: LoginTenantOption) {
  return {
    label: tenant.name,
    value: tenant.tenantId,
  };
}
