import { Alert, Card, Form, Input, Button, Typography, Divider, Space, Tag, theme } from "antd";
import {
  ApartmentOutlined,
  AuditOutlined,
  LockOutlined,
  LoginOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { useState, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import { ThemeSwitcher } from "@/features/theme-switcher/ThemeSwitcher";
import { useLogin } from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import styles from "./Login.module.css";

const { Title, Text } = Typography;

const entrySignals = [
  {
    icon: <ApartmentOutlined />,
    label: "试点组织",
    value: "集团总院 · 信息科联调环境",
  },
  {
    icon: <AuditOutlined />,
    label: "安全审计",
    value: "安全审计已开启",
  },
  {
    icon: <SafetyCertificateOutlined />,
    label: "身份策略",
    value: "MFA / 国密 / 国产 CA 自动匹配",
  },
];

const helpItems = [
  { label: "首次登录", value: "使用管理员开通的账号进入，首次登录后按医院策略改密。" },
  { label: "忘记密码", value: "请联系本院管理员重置密码，重置操作会进入审计留痕。" },
  { label: "统一身份", value: "CAS / OIDC / SAML 接入后会在此入口启用。" },
];

/**
 * 默认登录路径 + MFA/SSO 折叠区。
 *
 * 与 docs/CONSTITUTION.md §1 第 6 条对齐：
 * - 默认只有账号密码 1 个主动作
 * - 统一身份认证（CAS/OIDC/SAML）作为次级折叠区
 * - MFA / 国密 / 演示账号 不让用户手动选，由系统按医院策略
 * - ICP/公安备案、用户协议、隐私政策必须保留
 */
export default function Login() {
  const [showSso, setShowSso] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [loginForm] = Form.useForm<{
    username: string;
    password: string;
    tenantId?: string;
  }>();
  const navigate = useNavigate();
  const login = useLogin();
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

  return (
    <main className={styles.page} style={loginThemeStyle}>
      <div className={styles.themeSwitcher}>
        <ThemeSwitcher syncRemote={false} />
      </div>

      <section className={styles.contextPanel} aria-label="登录上下文">
        <Space size={8} wrap>
          <Tag color="processing">MedKernel</Tag>
          <Tag>v1.0 GA</Tag>
          <Tag>等保三级预审</Tag>
        </Space>
        <Title level={1} className={styles.brandTitle}>
          集团医疗智能中枢
        </Title>
        <Text className={styles.primaryGoal}>确认身份后进入工作台，系统会按角色展示对应菜单。</Text>

        <ul className={styles.signalList} aria-label="当前入口状态">
          {entrySignals.map((item) => (
            <li className={styles.signalItem} key={item.label}>
              <span className={styles.signalIcon} aria-hidden="true">
                {item.icon}
              </span>
              <span className={styles.signalText}>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
              </span>
            </li>
          ))}
        </ul>

        <Text className={styles.safetyCopy}>
          登录只确认身份；发布、回滚、临床建议等高风险动作仍会在业务页面内二次确认并留痕。
        </Text>
        <Button
          aria-label="首次部署接管"
          className={styles.bootstrapEntry}
          icon={<RocketOutlined />}
          onClick={() => navigate("/bootstrap")}
        >
          首次部署接管
        </Button>
      </section>

      <Card className={styles.loginCard} bordered={false}>
        <div className={styles.cardStack}>
          <div className={styles.cardHeader}>
            <Title level={2} className={styles.cardTitle}>
              登录工作台
            </Title>
            <Text type="secondary">使用医院账号或统一身份继续</Text>
          </div>

          {errorMsg && <Alert type="error" showIcon message="登录失败" description={errorMsg} />}

          <Form form={loginForm} layout="vertical" requiredMark={false} onFinish={handleSubmit}>
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
            <Form.Item
              label="租户标识"
              name="tenantId"
              extra="院内单租户可留空；跨院区或外网租户登录时填写。"
            >
              <Input
                placeholder="选填，如 t-hospital-01"
                size="large"
                autoComplete="organization"
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
            <Text>系统将按医院策略自动校验 MFA、国密通道与会话安全。</Text>
          </div>

          <div>
            <Divider className={styles.divider}>
              <Button
                type="link"
                size="small"
                className={styles.ssoToggle}
                onClick={() => setShowSso(!showSso)}
              >
                {showSso ? "收起" : "院方统一身份认证"}
              </Button>
            </Divider>
            {showSso && (
              <div className={styles.ssoStack}>
                <Button block disabled>
                  CAS（待院方配置）
                </Button>
                <Button block disabled>
                  OIDC（待院方配置）
                </Button>
                <Button block disabled>
                  SAML（待院方配置）
                </Button>
                <Text type="secondary" className={styles.helperText}>
                  统一身份由医院信息中心配置。MFA / 国密 / 国产 CA 由系统按策略自动选择。
                </Text>
              </div>
            )}
          </div>

          <div className={styles.helpList} aria-label="登录帮助">
            {helpItems.map((item) => (
              <div className={styles.helpItem} key={item.label}>
                <Text strong>{item.label}</Text>
                <Text type="secondary">{item.value}</Text>
              </div>
            ))}
          </div>

          <footer className={styles.complianceFooter}>
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
