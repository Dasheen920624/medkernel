import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  QRCode,
  Result,
  Space,
  Spin,
  Steps,
  Tag,
  Typography,
  theme,
} from "antd";
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  KeyOutlined,
  LockOutlined,
  LoginOutlined,
  QrcodeOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { useMemo, useState, type CSSProperties } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ThemeSwitcher } from "@/features/theme-switcher/ThemeSwitcher";
import {
  useBindBootstrapMfa,
  useBootstrapStatus,
  useChangePassword,
  useCheckBootstrapInitToken,
  useCreateBootstrapAdmin,
  useVerifyMfa,
  type BootstrapAdminResult,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { platformTenantId } from "@/shared/config/tenantDictionary";
import { formatClinicalDateTime } from "@/shared/lib/dateTimeText";
import styles from "./Bootstrap.module.css";

const { Title, Text, Paragraph } = Typography;

const handoverSignals = [
  {
    label: "准备接管码",
    value: "由部署包生成，现场离线也能校验",
  },
  {
    label: "创建初始管理员",
    value: "只创建第一个接管账号，其余账号进入工作台按职责开通",
  },
  {
    label: "按需启用多因素认证",
    value: "多因素认证默认关闭，需要时由部署配置显式开启",
  },
];

function buildAccountSecuritySignals(platformSetup: boolean, mfaRequired: boolean) {
  const signals = [
    {
      label: "修改临时密码",
      value: "首次登录先建立仅本人掌握的长期凭据",
    },
  ];
  if (mfaRequired) {
    signals.push({
      label: "完成多因素认证",
      value: platformSetup
        ? "当前部署已开启多因素认证，按平台安全策略完成认证器验证"
        : "当前部署已开启多因素认证，按服务机构安全策略完成认证器验证",
    });
  }
  signals.push({
    label: platformSetup ? "进入平台治理" : "进入机构工作台",
    value: platformSetup
      ? "安全设置完成后返回平台治理入口继续工作"
      : "安全设置完成后返回所在服务机构继续工作",
  });
  return signals;
}

function buildStepItems(accountSecuritySetup: boolean, mfaRequired: boolean) {
  if (!accountSecuritySetup) {
    return [{ title: "接管码" }, { title: "账号" }, { title: "完成" }];
  }
  if (mfaRequired) {
    return [{ title: "改密" }, { title: "多因素认证" }, { title: "完成" }];
  }
  return [{ title: "改密" }, { title: "完成" }];
}

function buildAccountSecurityLead(platformSetup: boolean, mfaRequired: boolean) {
  if (mfaRequired) {
    return platformSetup
      ? "首次登录需要修改临时密码，并完成当前部署已开启的多因素认证。完成后进入平台治理工作台。"
      : "首次登录需要修改临时密码，并完成当前部署已开启的多因素认证。完成后进入机构工作台。";
  }
  return platformSetup
    ? "首次登录只需修改临时密码，多因素认证当前关闭。完成后进入平台治理工作台。"
    : "首次登录只需修改临时密码，多因素认证当前关闭。完成后进入机构工作台。";
}

function buildGuardRailText(accountSecuritySetup: boolean, mfaRequired: boolean) {
  if (!accountSecuritySetup) {
    return "接管码只校验和消费一次；多因素认证默认关闭，需要时可在部署配置中统一开启。";
  }
  return mfaRequired
    ? "临时密码不得复用；当前会话完成多因素认证前不能进入业务页面。"
    : "临时密码不得复用；多因素认证当前关闭，不会阻塞账号进入业务页面。";
}

function buildMfaSubmitLabel(mfaBound: boolean, hasMfaSetup: boolean) {
  if (mfaBound) return "验证并进入系统";
  if (hasMfaSetup) return "验证并完成绑定";
  return "生成认证密钥";
}

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
  return formatClinicalDateTime(value, value);
}

export default function Bootstrap() {
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as BootstrapLocationState;
  const accountSecuritySetup = state.phase === "change-password" || state.phase === "mfa";
  const accountMfaRequired = Boolean(state.login?.mfaRequired);
  const platformSecuritySetup =
    accountSecuritySetup && (state.login?.tenantId ?? state.tenantId) === platformTenantId;
  const [phase, setPhase] = useState<BootstrapPhase>(() => normalizePhase(state.phase));
  const [initToken, setInitToken] = useState("");
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [admin, setAdmin] = useState<BootstrapAdminResult | null>(null);
  const [recoveryCode, setRecoveryCode] = useState<string | null>(null);
  const [mfaSetup, setMfaSetup] = useState<{
    label: string;
    secret: string;
    otpauthUri?: string;
  } | null>(null);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [tokenForm] = Form.useForm<{ token: string }>();
  const [adminForm] = Form.useForm<{
    username: string;
    password: string;
    confirmPassword: string;
  }>();
  const [passwordForm] = Form.useForm<{
    oldPassword: string;
    newPassword: string;
    confirmPassword: string;
  }>();
  const [mfaForm] = Form.useForm<{ label: string; code?: string }>();
  const checkToken = useCheckBootstrapInitToken();
  const createAdmin = useCreateBootstrapAdmin();
  const changePassword = useChangePassword();
  const bindMfa = useBindBootstrapMfa();
  const verifyMfa = useVerifyMfa();
  const bootstrapStatus = useBootstrapStatus(!accountSecuritySetup);
  const { token } = theme.useToken();
  const shouldGateByBootstrapStatus = !accountSecuritySetup && phase === "init-token";

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
    if (accountSecuritySetup) {
      if (phase === "change-password") return 0;
      if (accountMfaRequired && phase === "mfa") return 1;
      return accountMfaRequired ? 2 : 1;
    }
    if (phase === "init-token") return 0;
    if (phase === "password") return 1;
    return 2;
  }, [accountMfaRequired, accountSecuritySetup, phase]);

  const stepItems = buildStepItems(accountSecuritySetup, accountMfaRequired);
  const signals = accountSecuritySetup
    ? buildAccountSecuritySignals(platformSecuritySetup, accountMfaRequired)
    : handoverSignals;
  const accountSecurityLead = buildAccountSecurityLead(platformSecuritySetup, accountMfaRequired);
  const accountSecurityDoneDescription = platformSecuritySetup
    ? "当前账号已完成首次安全设置，可以进入平台治理工作台。"
    : "当前账号已完成首次安全设置，可以进入机构工作台。";
  const guardRailText = buildGuardRailText(accountSecuritySetup, accountMfaRequired);
  const mfaSubmitLabel = buildMfaSubmitLabel(Boolean(state.login?.mfaBound), Boolean(mfaSetup));

  const goLogin = () => navigate("/login");

  const returnLoginButton = (
    <Button aria-label="返回登录" block size="large" icon={<ArrowLeftOutlined />} onClick={goLogin}>
      返回登录
    </Button>
  );

  async function submitToken(values: { token: string }) {
    setGlobalError(null);
    try {
      const normalizedToken = values.token.trim();
      const result = await checkToken.mutateAsync(normalizedToken);
      setInitToken(normalizedToken);
      setExpiresAt(result.expiresAt);
      setPhase("password");
    } catch (err) {
      const errorMessage = getApiErrorMessage(err, "部署接管码校验失败");
      if (!applyApiFieldErrors(tokenForm, err, { fieldNameMap: mapBootstrapField })) {
        tokenForm.setFields([{ name: "token", errors: [errorMessage] }]);
      }
      setGlobalError(errorMessage);
    }
  }

  async function submitAdmin(values: {
    username: string;
    password: string;
    confirmPassword: string;
  }) {
    setGlobalError(null);
    try {
      const result = await createAdmin.mutateAsync({
        token: initToken,
        username: values.username.trim(),
        password: values.password,
      });
      setAdmin(result);
      setPhase("login-required");
    } catch (err) {
      const errorMessage = getApiErrorMessage(err, "初始管理员创建失败");
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
      if (state.login?.mfaRequired) {
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

  async function submitMfa(values: { label: string; code?: string }) {
    setGlobalError(null);
    try {
      const code = values.code?.trim();
      if (state.login?.mfaBound && !mfaSetup) {
        if (!code) {
          mfaForm.setFields([{ name: "code", errors: ["请输入认证器中的动态验证码"] }]);
          return;
        }
        const verified = await verifyMfa.mutateAsync({ code });
        if (!verified.verified) {
          throw new Error("多因素认证未完成，请重新输入验证码。");
        }
        setPhase("done");
        return;
      }

      const label = (mfaSetup?.label ?? values.label).trim();
      if (!mfaSetup) {
        const result = await bindMfa.mutateAsync({ label });
        if (result.mfaBound && result.recoveryCode) {
          setRecoveryCode(result.recoveryCode);
          setPhase("done");
          return;
        }
        if (!result.secret) {
          throw new Error("多因素认证密钥生成失败，请重试。");
        }
        setMfaSetup({
          label,
          secret: result.secret,
          otpauthUri: result.otpauthUri,
        });
        mfaForm.setFieldsValue({ label, code: "" });
        return;
      }

      if (!code) {
        mfaForm.setFields([{ name: "code", errors: ["请输入认证器中的动态验证码"] }]);
        return;
      }
      const result = await bindMfa.mutateAsync({
        label,
        secret: mfaSetup.secret,
        code,
      });
      if (!result.mfaBound || !result.recoveryCode) {
        throw new Error("多因素认证未完成，请重新输入验证码。");
      }
      const verified = await verifyMfa.mutateAsync({ code });
      if (!verified.verified) {
        throw new Error("多因素认证未完成，请重新输入验证码。");
      }
      setRecoveryCode(result.recoveryCode);
      setPhase("done");
    } catch (err) {
      const errorMessage = getApiErrorMessage(err, "多因素认证绑定失败");
      if (!applyApiFieldErrors(mfaForm, err)) {
        mfaForm.setFields([{ name: mfaSetup ? "code" : "label", errors: [errorMessage] }]);
      }
      setGlobalError(errorMessage);
    }
  }

  if (shouldGateByBootstrapStatus && bootstrapStatus.isLoading) {
    return (
      <main className={styles.page} style={pageStyle}>
        <div className={styles.themeSwitcher}>
          <ThemeSwitcher syncRemote={false} />
        </div>
        <Card className={styles.statusPanel} bordered={false}>
          <Spin size="large" />
          <Title level={2} className={styles.stepTitle}>
            正在确认首次部署状态
          </Title>
          <Text type="secondary">状态确认完成前不会开放接管操作。</Text>
        </Card>
      </main>
    );
  }

  if (shouldGateByBootstrapStatus && (bootstrapStatus.isError || !bootstrapStatus.data)) {
    return (
      <main className={styles.page} style={pageStyle}>
        <div className={styles.themeSwitcher}>
          <ThemeSwitcher syncRemote={false} />
        </div>
        <Card className={styles.statusPanel} bordered={false}>
          <Result
            status="error"
            title="首次部署状态读取失败"
            subTitle="当前无法确认系统是否已完成初始化，接管入口已暂时关闭。"
            extra={[
              <Button
                type="primary"
                key="retry"
                icon={<ReloadOutlined />}
                onClick={() => void bootstrapStatus.refetch()}
              >
                重新读取
              </Button>,
              <Button key="login" icon={<ArrowLeftOutlined />} onClick={goLogin}>
                返回登录
              </Button>,
            ]}
          />
        </Card>
      </main>
    );
  }

  if (shouldGateByBootstrapStatus && bootstrapStatus.data?.initialized) {
    return (
      <main className={styles.page} style={pageStyle}>
        <div className={styles.themeSwitcher}>
          <ThemeSwitcher syncRemote={false} />
        </div>
        <Card className={styles.statusPanel} bordered={false}>
          <Result
            status="success"
            title="系统已完成首次部署"
            subTitle="初始管理员已经建立，请返回登录。账号与服务机构统一在工作台内维护。"
            extra={[
              <Button
                aria-label="返回登录"
                type="primary"
                key="login"
                icon={<ArrowLeftOutlined />}
                onClick={goLogin}
              >
                返回登录
              </Button>,
            ]}
          />
        </Card>
      </main>
    );
  }

  return (
    <main className={styles.page} style={pageStyle}>
      <div className={styles.themeSwitcher}>
        <ThemeSwitcher syncRemote={false} />
      </div>

      <section
        className={styles.bootstrapShell}
        aria-label={accountSecuritySetup ? "账号安全设置工作区" : "首次部署接管工作区"}
      >
        <section
          className={`${styles.hero} ${styles.heroCard}`}
          aria-label={accountSecuritySetup ? "账号安全设置说明" : "首次部署接管说明"}
        >
          <Space size={8} wrap>
            {accountSecuritySetup ? (
              <>
                <Tag color="processing">账号安全</Tag>
                <Tag>首次改密</Tag>
                {accountMfaRequired && <Tag>多因素认证</Tag>}
              </>
            ) : (
              <>
                <Tag color="processing">平台接管</Tag>
                <Tag>离线可用</Tag>
                <Tag>只初始化初始身份</Tag>
              </>
            )}
          </Space>
          <Title level={1} className={styles.title}>
            {accountSecuritySetup ? "完成账号安全设置" : "首次部署接管"}
          </Title>
          <Text className={styles.lead}>
            {accountSecuritySetup
              ? accountSecurityLead
              : "使用部署接管码创建平台初始管理员。首登只要求改密；多因素认证默认关闭，需要时再由部署配置开启。"}
          </Text>
          <ul
            className={styles.signalList}
            aria-label={accountSecuritySetup ? "账号安全设置流程说明" : "接管流程说明"}
          >
            {signals.map((item) => (
              <li className={styles.signalItem} key={item.label}>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
              </li>
            ))}
          </ul>
          <div className={styles.guardRail}>
            <SafetyCertificateOutlined aria-hidden="true" />
            <Text>{guardRailText}</Text>
          </div>
        </section>

        <Card className={styles.panel} bordered={false}>
          <div className={styles.panelStack}>
            <Steps size="small" current={currentStep} items={stepItems} />

            {globalError && <Alert type="error" showIcon message={globalError} />}

            {phase === "init-token" && (
              <section className={styles.stepSection}>
                <Title level={2} className={styles.stepTitle}>
                  校验部署接管码
                </Title>
                <Paragraph type="secondary">
                  输入由部署包生成的短期接管码。这里不会创建登录态，也不会保存明文。
                </Paragraph>
                <Form
                  form={tokenForm}
                  layout="vertical"
                  requiredMark={false}
                  onFinish={submitToken}
                >
                  <Form.Item
                    label="部署接管码"
                    name="token"
                    rules={[{ required: true, message: "请输入部署接管码" }]}
                  >
                    <Input.Password
                      prefix={<KeyOutlined />}
                      autoComplete="one-time-code"
                      size="large"
                      placeholder="输入部署接管码"
                    />
                  </Form.Item>
                  <Form.Item className={styles.lastItem}>
                    <div className={styles.formActions}>
                      <Button
                        aria-label="继续接管"
                        type="primary"
                        htmlType="submit"
                        block
                        size="large"
                        loading={checkToken.isPending}
                        icon={<CheckCircleOutlined />}
                      >
                        继续接管
                      </Button>
                      {returnLoginButton}
                    </div>
                  </Form.Item>
                </Form>
              </section>
            )}

            {phase === "password" && (
              <section className={styles.stepSection}>
                <Title level={2} className={styles.stepTitle}>
                  设置初始管理员
                </Title>
                <Paragraph type="secondary">
                  接管码有效期：{formatTime(expiresAt) ?? "以部署配置为准"}
                  。初始管理员属于平台治理入口（唯一内置），集团和医院服务机构进入平台治理后开通。
                </Paragraph>
                <div className={styles.bootstrapTenantContext}>
                  <SafetyCertificateOutlined aria-hidden="true" />
                  <div>
                    <Text strong>平台治理入口自动绑定</Text>
                    <Text type="secondary">集团和医院服务机构进入平台治理后开通。</Text>
                  </div>
                </div>
                <Form
                  form={adminForm}
                  layout="vertical"
                  requiredMark={false}
                  onFinish={submitAdmin}
                >
                  <Form.Item
                    label="账号"
                    name="username"
                    rules={[{ required: true, message: "请输入初始管理员账号" }]}
                  >
                    <Input prefix={<UserOutlined />} size="large" autoComplete="username" />
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
                    <div className={styles.formActions}>
                      <Button
                        aria-label="创建初始管理员"
                        type="primary"
                        htmlType="submit"
                        block
                        size="large"
                        loading={createAdmin.isPending}
                        icon={<UserOutlined />}
                      >
                        创建初始管理员
                      </Button>
                      {returnLoginButton}
                    </div>
                  </Form.Item>
                </Form>
              </section>
            )}

            {phase === "login-required" && (
              <Result
                status="success"
                title="初始管理员已创建"
                subTitle={`请使用初始账号登录并完成首次改密：${admin?.username ?? "初始管理员"}`}
                extra={[
                  <Button
                    aria-label="返回登录"
                    type="primary"
                    key="login"
                    icon={<LoginOutlined />}
                    onClick={goLogin}
                  >
                    返回登录
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
                    <div className={styles.formActions}>
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
                      {returnLoginButton}
                    </div>
                  </Form.Item>
                </Form>
              </section>
            )}

            {phase === "mfa" && (
              <section className={styles.stepSection}>
                <Title level={2} className={styles.stepTitle}>
                  {state.login?.mfaBound ? "验证多因素认证" : "绑定多因素认证"}
                </Title>
                <Paragraph type="secondary">
                  {state.login?.mfaBound
                    ? "请输入认证器中的动态验证码，验证成功后进入系统。"
                    : "当前部署已启用多因素认证，请先绑定认证器并完成本次验证。"}
                </Paragraph>
                {mfaSetup && (
                  <div className={styles.mfaSetupGrid}>
                    {mfaSetup.otpauthUri && (
                      <div className={styles.qrPanel} aria-label="离线多因素认证二维码">
                        <Text strong>
                          <QrcodeOutlined aria-hidden="true" /> 扫码绑定
                        </Text>
                        <QRCode
                          value={mfaSetup.otpauthUri}
                          size={156}
                          bordered={false}
                          type="svg"
                        />
                        <Text type="secondary">二维码由本页面生成，不访问外网。</Text>
                      </div>
                    )}
                    <div className={styles.manualPanel}>
                      <Text strong>不能扫码时手动录入</Text>
                      <ol className={styles.mfaSteps}>
                        <li>在手机或内网安全终端打开认证器 App，选择“扫码添加”。</li>
                        <li>内网不可扫码时选择“手动输入密钥”。</li>
                        <li>每 30 秒生成 6 位动态验证码，把当前验证码填到下方。</li>
                      </ol>
                      <dl className={styles.mfaManualList}>
                        <div>
                          <dt>密钥</dt>
                          <dd>
                            <Text strong copyable className={styles.recoveryCode}>
                              {mfaSetup.secret}
                            </Text>
                          </dd>
                        </div>
                        <div>
                          <dt>发行方</dt>
                          <dd>MedKernel</dd>
                        </div>
                        <div>
                          <dt>账号/设备</dt>
                          <dd>{mfaSetup.label}</dd>
                        </div>
                        <div>
                          <dt>验证码位数</dt>
                          <dd>6 位</dd>
                        </div>
                        <div>
                          <dt>刷新周期</dt>
                          <dd>30 秒</dd>
                        </div>
                      </dl>
                    </div>
                  </div>
                )}
                <Form
                  form={mfaForm}
                  layout="vertical"
                  requiredMark={false}
                  initialValues={{ label: state.username || "账号安全设备" }}
                  onFinish={submitMfa}
                >
                  {!state.login?.mfaBound && (
                    <Form.Item
                      label="设备名称"
                      name="label"
                      rules={[{ required: true, message: "请输入设备名称" }]}
                    >
                      <Input
                        prefix={<SafetyCertificateOutlined />}
                        size="large"
                        disabled={Boolean(mfaSetup)}
                      />
                    </Form.Item>
                  )}
                  {(mfaSetup || state.login?.mfaBound) && (
                    <Form.Item
                      label="动态验证码"
                      name="code"
                      rules={[
                        { required: true, message: "请输入认证器中的动态验证码" },
                        { pattern: /^\d{6}$/, message: "动态验证码为 6 位数字" },
                      ]}
                    >
                      <Input
                        prefix={<SafetyCertificateOutlined />}
                        size="large"
                        inputMode="numeric"
                        autoComplete="one-time-code"
                      />
                    </Form.Item>
                  )}
                  <Form.Item className={styles.lastItem}>
                    <div className={styles.formActions}>
                      <Button
                        aria-label={mfaSubmitLabel}
                        type="primary"
                        htmlType="submit"
                        block
                        size="large"
                        loading={bindMfa.isPending || verifyMfa.isPending}
                        icon={<SafetyCertificateOutlined />}
                      >
                        {mfaSubmitLabel}
                      </Button>
                      {returnLoginButton}
                    </div>
                  </Form.Item>
                </Form>
              </section>
            )}

            {phase === "done" && (
              <Result
                status="success"
                title={accountSecuritySetup ? "账号安全设置完成" : "初始身份接管完成"}
                subTitle={
                  accountSecuritySetup
                    ? accountSecurityDoneDescription
                    : "现在可以返回登录进入平台治理；集团、医院和其他服务机构统一在服务机构管理中维护。"
                }
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
                  <Button key="login" icon={<ArrowLeftOutlined />} onClick={goLogin}>
                    返回登录
                  </Button>,
                ]}
              />
            )}
          </div>
        </Card>
      </section>
    </main>
  );
}
