import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Result,
  Space,
  Steps,
  Tag,
  Typography,
  theme,
} from "antd";
import {
  CheckCircleOutlined,
  KeyOutlined,
  LockOutlined,
  LoginOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { useMemo, useState, type CSSProperties } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ThemeSwitcher } from "@/features/theme-switcher/ThemeSwitcher";
import {
  useBindBootstrapMfa,
  useChangePassword,
  useCheckBootstrapInitToken,
  useCreateBootstrapAdmin,
  type BootstrapAdminResult,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import styles from "./Bootstrap.module.css";

const { Title, Text, Paragraph } = Typography;

type BootstrapPhase =
  | "init-token"
  | "password"
  | "login-required"
  | "change-password"
  | "mfa"
  | "done";

interface BootstrapLocationState {
  phase?: BootstrapPhase;
  login?: {
    userId?: string;
    tenantId?: string;
    mustChangePwd?: boolean;
    mfaRequired?: boolean;
    mfaBound?: boolean;
  };
  username?: string;
  tenantId?: string;
}

function mapBootstrapField(field: string) {
  return field === "initToken" ? "token" : field;
}

function normalizePhase(value: unknown): BootstrapPhase {
  return value === "change-password" || value === "mfa" ? value : "init-token";
}

function formatTime(value: string | null) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", { hour12: false });
}

export default function Bootstrap() {
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as BootstrapLocationState;
  const [phase, setPhase] = useState<BootstrapPhase>(() => normalizePhase(state.phase));
  const [initToken, setInitToken] = useState("");
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [admin, setAdmin] = useState<BootstrapAdminResult | null>(null);
  const [recoveryCode, setRecoveryCode] = useState<string | null>(null);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [tokenForm] = Form.useForm<{ token: string }>();
  const [adminForm] = Form.useForm<{
    tenantId?: string;
    username: string;
    password: string;
    confirmPassword: string;
  }>();
  const [passwordForm] = Form.useForm<{
    oldPassword: string;
    newPassword: string;
    confirmPassword: string;
  }>();
  const [mfaForm] = Form.useForm<{ label: string }>();
  const checkToken = useCheckBootstrapInitToken();
  const createAdmin = useCreateBootstrapAdmin();
  const changePassword = useChangePassword();
  const bindMfa = useBindBootstrapMfa();
  const { token } = theme.useToken();

  const pageStyle = {
    "--mk-bootstrap-text": token.colorText,
    "--mk-bootstrap-muted": token.colorTextDescription,
    "--mk-bootstrap-primary": token.colorPrimary,
    "--mk-bootstrap-surface": token.colorBgContainer,
    "--mk-bootstrap-surface-tint": token.colorPrimaryBg,
    "--mk-bootstrap-layout": token.colorBgLayout,
    "--mk-bootstrap-border": token.colorBorderSecondary,
    "--mk-bootstrap-fill": token.colorFillQuaternary,
    "--mk-bootstrap-shadow": token.boxShadow,
    "--mk-bootstrap-radius": `${token.borderRadius}px`,
    "--mk-bootstrap-heading-1": `${token.fontSizeHeading1}px`,
    "--mk-bootstrap-heading-2": `${token.fontSizeHeading2}px`,
    "--mk-bootstrap-font-lg": `${token.fontSizeLG}px`,
    "--mk-bootstrap-font-sm": `${token.fontSizeSM}px`,
    "--mk-bootstrap-control-font": `${Math.max(token.fontSize, token.fontSizeLG)}px`,
    "--mk-bootstrap-control-height": `${Math.max(token.controlHeight, token.controlHeightLG)}px`,
  } as CSSProperties;

  const currentStep = useMemo(() => {
    if (phase === "init-token") return 0;
    if (phase === "password" || phase === "login-required") return 1;
    if (phase === "change-password") return 2;
    if (phase === "mfa") return 3;
    return 4;
  }, [phase]);

  async function submitToken(values: { token: string }) {
    setGlobalError(null);
    try {
      const normalizedToken = values.token.trim();
      const result = await checkToken.mutateAsync(normalizedToken);
      setInitToken(normalizedToken);
      setExpiresAt(result.expiresAt);
      setPhase("password");
    } catch (err) {
      const errorMessage = getApiErrorMessage(err, "init token 校验失败");
      if (!applyApiFieldErrors(tokenForm, err, { fieldNameMap: mapBootstrapField })) {
        tokenForm.setFields([{ name: "token", errors: [errorMessage] }]);
      }
      setGlobalError(errorMessage);
    }
  }

  async function submitAdmin(values: {
    tenantId?: string;
    username: string;
    password: string;
    confirmPassword: string;
  }) {
    setGlobalError(null);
    try {
      const result = await createAdmin.mutateAsync({
        token: initToken,
        tenantId: values.tenantId?.trim() || undefined,
        username: values.username.trim(),
        password: values.password,
      });
      setAdmin(result);
      setPhase("login-required");
    } catch (err) {
      const errorMessage = getApiErrorMessage(err, "首发管理员创建失败");
      if (!applyApiFieldErrors(adminForm, err)) {
        adminForm.setFields([{ name: "username", errors: [errorMessage] }]);
      }
      setGlobalError(errorMessage);
    }
  }

  async function submitPassword(values: {
    oldPassword: string;
    newPassword: string;
    confirmPassword: string;
  }) {
    setGlobalError(null);
    try {
      await changePassword.mutateAsync({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      if (state.login?.mfaRequired && !state.login.mfaBound) {
        setPhase("mfa");
      } else {
        setPhase("done");
      }
    } catch (err) {
      const errorMessage = getApiErrorMessage(err, "首次改密失败");
      if (!applyApiFieldErrors(passwordForm, err)) {
        passwordForm.setFields([{ name: "oldPassword", errors: [errorMessage] }]);
      }
      setGlobalError(errorMessage);
    }
  }

  async function submitMfa(values: { label: string }) {
    setGlobalError(null);
    try {
      const result = await bindMfa.mutateAsync({ label: values.label.trim() });
      setRecoveryCode(result.recoveryCode);
      setPhase("done");
    } catch (err) {
      const errorMessage = getApiErrorMessage(err, "MFA 绑定失败");
      if (!applyApiFieldErrors(mfaForm, err)) {
        mfaForm.setFields([{ name: "label", errors: [errorMessage] }]);
      }
      setGlobalError(errorMessage);
    }
  }

  return (
    <main className={styles.page} style={pageStyle}>
      <div className={styles.themeSwitcher}>
        <ThemeSwitcher syncRemote={false} />
      </div>

      <section className={styles.hero} aria-label="首次部署接管说明">
        <Space size={8} wrap>
          <Tag color="processing">BASE-11</Tag>
          <Tag>一次性 init token</Tag>
          <Tag>PostgreSQL / Oracle</Tag>
        </Space>
        <Title level={1} className={styles.title}>
          首次部署接管
        </Title>
        <Text className={styles.lead}>
          用部署期一次性 token 创建首发内置超级管理员，再完成改密与 MFA，避免生产环境写死账号。
        </Text>
        <div className={styles.guardRail}>
          <SafetyCertificateOutlined aria-hidden="true" />
          <Text>token 只校验和消费一次；恢复码只展示一次；高危动作会继续要求 MFA。</Text>
        </div>
      </section>

      <Card className={styles.panel} bordered={false}>
        <div className={styles.panelStack}>
          <Steps
            size="small"
            current={currentStep}
            items={[
              { title: "token" },
              { title: "账号" },
              { title: "改密" },
              { title: "MFA" },
              { title: "完成" },
            ]}
          />

          {globalError && <Alert type="error" showIcon message={globalError} />}

          {phase === "init-token" && (
            <section className={styles.stepSection}>
              <Title level={2} className={styles.stepTitle}>
                校验 init token
              </Title>
              <Paragraph type="secondary">
                输入由部署密钥系统注入的短期 token。这里不会创建登录态，也不会保存明文。
              </Paragraph>
              <Form form={tokenForm} layout="vertical" requiredMark={false} onFinish={submitToken}>
                <Form.Item
                  label="init token"
                  name="token"
                  rules={[{ required: true, message: "请输入 init token" }]}
                >
                  <Input.Password
                    prefix={<KeyOutlined />}
                    autoComplete="one-time-code"
                    size="large"
                    placeholder="输入部署期一次性 token"
                  />
                </Form.Item>
                <Form.Item className={styles.lastItem}>
                  <Button
                    aria-label="校验 init token"
                    type="primary"
                    htmlType="submit"
                    block
                    size="large"
                    loading={checkToken.isPending}
                    icon={<CheckCircleOutlined />}
                  >
                    校验 init token
                  </Button>
                </Form.Item>
              </Form>
            </section>
          )}

          {phase === "password" && (
            <section className={styles.stepSection}>
              <Title level={2} className={styles.stepTitle}>
                设置首发管理员
              </Title>
              <Paragraph type="secondary">
                token 有效期：{formatTime(expiresAt) ?? "以部署配置为准"}
                。首发账号创建后必须登录并改密。
              </Paragraph>
              <Form form={adminForm} layout="vertical" requiredMark={false} onFinish={submitAdmin}>
                <Form.Item
                  label="账号"
                  name="username"
                  rules={[{ required: true, message: "请输入首发管理员账号" }]}
                >
                  <Input prefix={<UserOutlined />} size="large" autoComplete="username" />
                </Form.Item>
                <Form.Item label="租户标识" name="tenantId" extra="留空时使用平台默认租户 t-1。">
                  <Input size="large" autoComplete="organization" />
                </Form.Item>
                <Form.Item
                  label="初始密码"
                  name="password"
                  rules={[{ required: true, min: 8, message: "初始密码至少 8 位" }]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    size="large"
                    autoComplete="new-password"
                  />
                </Form.Item>
                <Form.Item
                  label="确认初始密码"
                  name="confirmPassword"
                  dependencies={["password"]}
                  rules={[
                    { required: true, message: "请再次输入初始密码" },
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        return !value || getFieldValue("password") === value
                          ? Promise.resolve()
                          : Promise.reject(new Error("两次输入的密码不一致"));
                      },
                    }),
                  ]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    size="large"
                    autoComplete="new-password"
                  />
                </Form.Item>
                <Form.Item className={styles.lastItem}>
                  <Button
                    aria-label="创建首发管理员"
                    type="primary"
                    htmlType="submit"
                    block
                    size="large"
                    loading={createAdmin.isPending}
                    icon={<UserOutlined />}
                  >
                    创建首发管理员
                  </Button>
                </Form.Item>
              </Form>
            </section>
          )}

          {phase === "login-required" && (
            <Result
              status="success"
              title="首发管理员已创建"
              subTitle={`请使用首发账号登录并完成首次改密：${admin?.username ?? "首发管理员"}`}
              extra={[
                <Button
                  aria-label="前往登录"
                  type="primary"
                  key="login"
                  icon={<LoginOutlined />}
                  onClick={() => navigate("/login")}
                >
                  前往登录
                </Button>,
              ]}
            />
          )}

          {phase === "change-password" && (
            <section className={styles.stepSection}>
              <Title level={2} className={styles.stepTitle}>
                完成首次改密
              </Title>
              <Paragraph type="secondary">
                当前账号仍处于必须改密状态，完成前不能进入业务工作台。
              </Paragraph>
              <Form
                form={passwordForm}
                layout="vertical"
                requiredMark={false}
                onFinish={submitPassword}
              >
                <Form.Item
                  label="当前密码"
                  name="oldPassword"
                  rules={[{ required: true, message: "请输入当前密码" }]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    size="large"
                    autoComplete="current-password"
                  />
                </Form.Item>
                <Form.Item
                  label="新密码"
                  name="newPassword"
                  rules={[{ required: true, min: 8, message: "新密码至少 8 位" }]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    size="large"
                    autoComplete="new-password"
                  />
                </Form.Item>
                <Form.Item
                  label="确认新密码"
                  name="confirmPassword"
                  dependencies={["newPassword"]}
                  rules={[
                    { required: true, message: "请再次输入新密码" },
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        return !value || getFieldValue("newPassword") === value
                          ? Promise.resolve()
                          : Promise.reject(new Error("两次输入的新密码不一致"));
                      },
                    }),
                  ]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    size="large"
                    autoComplete="new-password"
                  />
                </Form.Item>
                <Form.Item className={styles.lastItem}>
                  <Button
                    aria-label="完成首次改密"
                    type="primary"
                    htmlType="submit"
                    block
                    size="large"
                    loading={changePassword.isPending}
                    icon={<CheckCircleOutlined />}
                  >
                    完成首次改密
                  </Button>
                </Form.Item>
              </Form>
            </section>
          )}

          {phase === "mfa" && (
            <section className={styles.stepSection}>
              <Title level={2} className={styles.stepTitle}>
                绑定 MFA
              </Title>
              <Paragraph type="secondary">
                首发管理员必须绑定 MFA 后才能执行高危配置、租户开通和应急操作。
              </Paragraph>
              <Form
                form={mfaForm}
                layout="vertical"
                requiredMark={false}
                initialValues={{ label: state.username || "首发管理员安全设备" }}
                onFinish={submitMfa}
              >
                <Form.Item
                  label="设备名称"
                  name="label"
                  rules={[{ required: true, message: "请输入 MFA 设备名称" }]}
                >
                  <Input prefix={<SafetyCertificateOutlined />} size="large" />
                </Form.Item>
                <Form.Item className={styles.lastItem}>
                  <Button
                    aria-label="生成一次性恢复码"
                    type="primary"
                    htmlType="submit"
                    block
                    size="large"
                    loading={bindMfa.isPending}
                    icon={<SafetyCertificateOutlined />}
                  >
                    生成一次性恢复码
                  </Button>
                </Form.Item>
              </Form>
            </section>
          )}

          {phase === "done" && (
            <Result
              status="success"
              title="首发身份接管完成"
              subTitle="后续可开通首个租户；国产化真实运行证据仍按待处理清单在最终适配阶段关闭。"
              extra={[
                recoveryCode ? (
                  <div className={styles.recoveryBox} key="recovery">
                    <Text type="secondary">一次性恢复码</Text>
                    <Text strong className={styles.recoveryCode}>
                      {recoveryCode}
                    </Text>
                    <Text type="secondary">只在此处展示一次，数据库仅保存摘要。</Text>
                  </div>
                ) : null,
                <Button
                  aria-label="进入工作台"
                  type="primary"
                  key="dashboard"
                  icon={<LoginOutlined />}
                  onClick={() => navigate("/dashboard")}
                >
                  进入工作台
                </Button>,
              ]}
            />
          )}
        </div>
      </Card>
    </main>
  );
}
