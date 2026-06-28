import { Alert, Button, Card, Form, Input, Typography, theme } from "antd";
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
  useBootstrapStatus,
  useDelegatedAuthStatus,
  useLogin,
  useLoginTenantDirectory,
  type DelegatedAuthStatus,
  type LoginTenantOption,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { identityProviderLabel } from "@/shared/config/customerLabels";
import { platformTenantDescription } from "@/shared/config/tenantDictionary";
import styles from "./Login.module.css";

const { Title, Text } = Typography;

const helpItems = [
  { label: "首次登录", value: "使用管理员开通的账号进入，首次登录后按医院策略改密。" },
  { label: "忘记密码", value: "请联系本院管理员重置密码，重置操作会进入审计留痕。" },
  { label: "统一身份", value: "统一身份状态由服务返回；待配置时页面只展示状态，不伪造入口。" },
];

const delegatedConnectionStatusLabel = (state: string) => {
  if (state === "NOT_CONNECTED") {
    return "待配置";
  }
  if (state === "READY") {
    return "已启用";
  }
  if (state === "DISABLED") {
    return "未启用";
  }
  return state;
};

type DelegatedAlert = {
  type: "info" | "error" | "success" | "warning";
  message: string;
  description: string;
};

function selectVisibleTenants({
  hasCustomerTenants,
  showPlatformTenant,
  primaryTenants,
  platformTenant,
}: {
  hasCustomerTenants: boolean;
  showPlatformTenant: boolean;
  primaryTenants: LoginTenantOption[];
  platformTenant: LoginTenantOption | null;
}) {
  if (hasCustomerTenants && !showPlatformTenant) {
    return primaryTenants;
  }
  return platformTenant ? [platformTenant] : [];
}

function getPlatformContextDescription({
  activeTenant,
  isPlatformLayer,
  hasCustomerTenants,
}: {
  activeTenant?: LoginTenantOption;
  isPlatformLayer: boolean;
  hasCustomerTenants: boolean;
}) {
  if (!activeTenant) {
    return "正在等待服务端返回可登录的机构。";
  }
  if (isPlatformLayer && hasCustomerTenants) {
    return "仅供平台治理、知识标准维护和系统运维人员使用；机构定制不会回写平台标准。";
  }
  return platformTenantDescription;
}

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
        "统一身份由医院信息中心配置；多因素认证、国密与国产 CA 由系统按策略自动选择。",
    };
  }

  return {
    type: "warning",
    message: "统一身份服务待配置",
    description:
      status.message || "请先使用医院账号密码登录；信息科可在身份来源中完成院方统一身份配置。",
  };
}

/**
 * 默认登录路径 + 多因素认证/统一身份折叠区。
 *
 * 与 docs/CONSTITUTION.md §1 第 6 条对齐：
 * - 默认只有账号密码 1 个主动作
 * - 统一身份认证（CAS/OIDC/SAML）作为次级折叠区
 * - 多因素认证 / 国密策略不让用户手动选，由系统按医院策略
 * - ICP/公安备案、用户协议、隐私政策必须保留
 */
export default function Login() {
  const [showSso, setShowSso] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [showPlatformTenant, setShowPlatformTenant] = useState(false);
  const [selectedTenantId, setSelectedTenantId] = useState("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [loginForm] = Form.useForm<{
    username: string;
    password: string;
    tenantId?: string;
  }>();
  const navigate = useNavigate();
  const login = useLogin();
  const bootstrapStatus = useBootstrapStatus();
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

  const tenantDirectory = loginTenantDirectory.data;
  const hasCustomerTenants = tenantDirectory?.hasCustomerTenants ?? false;
  const platformTenant = tenantDirectory?.platformTenant ?? null;
  const primaryTenants = useMemo(() => {
    return tenantDirectory?.primaryTenants ?? [];
  }, [tenantDirectory?.primaryTenants]);
  const visibleTenants = useMemo(
    () =>
      selectVisibleTenants({
        hasCustomerTenants,
        showPlatformTenant,
        primaryTenants,
        platformTenant,
      }),
    [hasCustomerTenants, platformTenant, primaryTenants, showPlatformTenant],
  );
  const activeTenant =
    visibleTenants.find((tenant) => tenant.tenantId === selectedTenantId) ?? visibleTenants[0];
  const isPlatformLayer = activeTenant?.kind === "PLATFORM" || showPlatformTenant;
  const canUseDelegatedLogin =
    hasCustomerTenants && !showPlatformTenant && primaryTenants.length > 0;
  const tenantDirectoryUnavailable =
    !loginTenantDirectory.isLoading && !loginTenantDirectory.isError && !activeTenant;
  const platformContextDescription = getPlatformContextDescription({
    activeTenant,
    isPlatformLayer,
    hasCustomerTenants,
  });

  const delegatedAuthStatus = useDelegatedAuthStatus(showSso && canUseDelegatedLogin);

  async function handleSubmit(values: { username: string; password: string; tenantId?: string }) {
    setErrorMsg(null);
    if (!activeTenant) {
      setErrorMsg("机构目录未就绪，服务端未返回可登录机构，不能提交登录。");
      return;
    }
    try {
      const result = await login.mutateAsync({
        username: values.username,
        password: values.password,
        tenantId: activeTenant.tenantId,
      });
      if (result.mustChangePwd || result.mfaRequired) {
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
  const delegatedProviders = delegatedStatus?.providers ?? [];
  const delegatedState = delegatedStatus?.status ?? "NOT_CONNECTED";
  const delegatedAlert = buildDelegatedAlert({
    status: delegatedStatus,
    state: delegatedState,
    isLoading: delegatedAuthStatus.isLoading,
    isError: delegatedAuthStatus.isError,
    error: delegatedAuthStatus.error,
  });
  useEffect(() => {
    const tenantStillVisible = visibleTenants.some(
      (tenant) => tenant.tenantId === selectedTenantId,
    );
    if (!tenantStillVisible) {
      setSelectedTenantId(visibleTenants[0]?.tenantId ?? "");
    }
  }, [selectedTenantId, visibleTenants]);

  useEffect(() => {
    if (!canUseDelegatedLogin && showSso) {
      setShowSso(false);
    }
  }, [canUseDelegatedLogin, showSso]);

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
            <div className={styles.brandLockup}>
              <div className={styles.brandMark} aria-hidden="true">
                M
              </div>
              <div className={styles.brandCopy}>
                <Text strong className={styles.brandName}>
                  MedKernel
                </Text>
                <Text type="secondary" className={styles.brandSubtitle}>
                  集团医疗智能中枢
                </Text>
              </div>
            </div>
            <Title level={2} className={styles.cardTitle}>
              {isPlatformLayer ? "登录平台治理" : "登录机构工作台"}
            </Title>
            <Text type="secondary">
              {canUseDelegatedLogin ? "使用所在机构账号继续" : "使用平台治理账号继续"}
            </Text>
          </div>

          {hasCustomerTenants && (
            <div className={styles.loginModeSwitch} aria-label="登录类型切换">
              <Button
                aria-pressed={!showPlatformTenant}
                className={
                  showPlatformTenant ? styles.loginModeButton : styles.loginModeButtonActive
                }
                onClick={() => setShowPlatformTenant(false)}
              >
                机构用户
              </Button>
              <Button
                aria-pressed={showPlatformTenant}
                className={
                  showPlatformTenant ? styles.loginModeButtonActive : styles.loginModeButton
                }
                onClick={() => setShowPlatformTenant(true)}
              >
                平台治理
              </Button>
            </div>
          )}

          {errorMsg && <Alert type="error" showIcon message="登录失败" description={errorMsg} />}
          {loginTenantDirectory.isLoading && (
            <Alert
              type="info"
              showIcon
              message="正在读取机构目录"
              description="可登录机构以服务端目录为唯一来源，请稍候。"
            />
          )}
          {loginTenantDirectory.isError && (
            <Alert
              type="error"
              showIcon
              message="机构目录读取失败"
              description={getApiErrorMessage(
                loginTenantDirectory.error,
                "暂时无法读取服务端机构目录，登录入口已暂停提交。",
              )}
            />
          )}
          {tenantDirectoryUnavailable && (
            <Alert
              type="warning"
              showIcon
              message="没有可登录机构"
              description="服务端未返回平台治理入口或医疗服务机构，登录入口已暂停提交。"
            />
          )}

          <Form
            disabled={login.isPending}
            form={loginForm}
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
                placeholder={isPlatformLayer ? "请输入平台治理账号" : "请输入工号或机构账号"}
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
            {canUseDelegatedLogin ? (
              <div className={styles.tenantChoiceSection} aria-label="所在机构">
                <Text strong className={styles.fieldLabel}>
                  所在机构
                </Text>
                <div className={styles.tenantChoiceGroup}>
                  {visibleTenants.map((tenant) => {
                    const selected = activeTenant.tenantId === tenant.tenantId;
                    return (
                      <Button
                        key={tenant.tenantId}
                        aria-pressed={selected}
                        className={selected ? styles.tenantChoiceActive : styles.tenantChoice}
                        loading={loginTenantDirectory.isLoading}
                        disabled={!activeTenant}
                        onClick={() => setSelectedTenantId(tenant.tenantId)}
                      >
                        <span className={styles.tenantChoiceName}>{tenant.name}</span>
                        <span className={styles.tenantChoiceMeta}>{tenantKindLabel(tenant)}</span>
                      </Button>
                    );
                  })}
                </div>
                <Text type="secondary" className={styles.helperText}>
                  请选择本次工作的集团、医院或医疗服务机构。
                </Text>
              </div>
            ) : (
              <div className={styles.platformContext}>
                <SafetyCertificateOutlined aria-hidden="true" />
                <div>
                  <Text strong>{activeTenant ? "平台治理入口" : "机构目录未就绪"}</Text>
                  <Text type="secondary" className={styles.helperText}>
                    {platformContextDescription}
                  </Text>
                  <Text type="secondary" className={styles.helperText}>
                    {activeTenant ? "平台标准与全局治理入口" : "无可登录机构"}
                  </Text>
                </div>
              </div>
            )}
            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                block
                size="large"
                icon={<LoginOutlined />}
                loading={login.isPending}
                disabled={
                  !activeTenant || loginTenantDirectory.isLoading || loginTenantDirectory.isError
                }
              >
                进入工作台
              </Button>
            </Form.Item>
          </Form>

          <div className={styles.policyStrip}>
            <SafetyCertificateOutlined aria-hidden="true" />
            <Text>系统将按医院策略自动校验多因素认证、国密通道与会话安全。</Text>
          </div>

          {bootstrapStatus.data?.initialized === false && (
            <Button
              aria-label="首次部署接管"
              className={styles.secondaryEntry}
              icon={<RocketOutlined />}
              onClick={() => navigate("/bootstrap")}
            >
              首次部署接管
            </Button>
          )}

          <div className={styles.utilityRow}>
            {canUseDelegatedLogin && (
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
            )}
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
          </div>

          {canUseDelegatedLogin && showSso && (
            <div id="delegated-auth-panel" className={styles.ssoStack}>
              <Alert
                className={styles.ssoStatus}
                type={delegatedAlert.type}
                showIcon
                message={delegatedAlert.message}
                description={delegatedAlert.description}
              />
              <div className={styles.providerGrid} aria-label="统一身份方式">
                {delegatedProviders.length > 0 ? (
                  delegatedProviders.map((provider) => (
                    <Button
                      block
                      disabled
                      className={styles.providerButton}
                      key={provider}
                      loading={delegatedAuthStatus.isLoading}
                    >
                      {identityProviderLabel(provider)}（
                      {delegatedConnectionStatusLabel(delegatedState)}）
                    </Button>
                  ))
                ) : (
                  <Text type="secondary" className={styles.helperText}>
                    当前未返回统一身份方式，暂不展示登录跳转入口。
                  </Text>
                )}
              </div>
              <Text type="secondary" className={styles.helperText}>
                当前页只展示已配置状态；院方身份来源、证书链和回调地址完成配置后才会开放跳转。
              </Text>
            </div>
          )}

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

function tenantKindLabel(tenant: LoginTenantOption) {
  if (tenant.kind === "GROUP") {
    return "医疗集团";
  }
  if (tenant.kind === "HOSPITAL") {
    return "医院";
  }
  return tenant.kind === "PLATFORM" ? "平台治理" : "医疗服务机构";
}
