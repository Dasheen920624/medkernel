import { useCallback, useEffect, useState, useMemo, useRef } from "react";
import {
  App as AntdApp,
  Avatar,
  Breadcrumb,
  Drawer,
  Dropdown,
  Form,
  Grid,
  Input,
  Layout,
  Menu,
  Modal,
  Typography,
  Space,
  Button,
  Tooltip,
} from "antd";
import {
  BellOutlined,
  LockOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SearchOutlined,
  ToolOutlined,
  UserOutlined,
} from "@ant-design/icons";
import type { MenuProps } from "antd";
import { useNavigate, useLocation, Outlet } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { getMenuItemsForProfile, getMenuSectionsForProfile } from "@/shared/config/menu";
import { canAccessRoute, findRouteByPath, getRouteBreadcrumb } from "@/shared/config/routes";
import {
  useChangePassword,
  useLogout,
  useRenewSession,
  useSessionStatus,
  useSecurityProfile,
  type SecurityProfile,
} from "@/shared/api/hooks";
import {
  broadcastAuthSessionEvent,
  subscribeAuthSessionEvent,
  type AuthSessionEventReason,
} from "@/shared/auth/sessionEvents";
import { getApiErrorMessage } from "@/shared/api/errors";
import { PageState } from "@/shared/ui/PageState";
import { PermissionChip } from "@/features/permission-chip/PermissionChip";
import { CommandPalette } from "@/features/command-palette/CommandPalette";
import { AuditSnapshotButton } from "@/features/audit-snapshot/AuditSnapshotButton";
import { ThemeSwitcher } from "@/features/theme-switcher/ThemeSwitcher";

const { Header, Sider, Content } = Layout;

type ChangePasswordValues = {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
};

function userDisplayName(profile: SecurityProfile | undefined) {
  return profile?.username || profile?.userId || "当前用户";
}

function primaryRoleName(profile: SecurityProfile | undefined) {
  return profile?.roles[0]?.displayName || "未绑定角色";
}

function organizationSummary(profile: SecurityProfile | undefined) {
  const scope = profile?.dataScope;
  if (!scope) {
    return "组织范围未配置";
  }
  const parts = [
    ["服务空间", scope.tenantId],
    ["集团", scope.groupId],
    ["医院", scope.hospitalId],
    ["院区", scope.campusId],
    ["服务点", scope.siteId],
    ["科室", scope.departmentId],
    ["病区", scope.wardId],
    ["专病", scope.specialtyId],
  ]
    .filter(([, value]) => Boolean(value))
    .map(([label, value]) => `${label} ${value}`);
  return parts.length > 0 ? parts.join(" / ") : "组织范围未配置";
}

/**
 * 主布局：左侧 SideMenu + 顶部 Header。
 *
 * 严格遵守 docs/CONSTITUTION.md §1 第 2 条：左侧 SideMenu 永远是主菜单，
 * 顶部 Header 只放品牌、面包屑、命令面板、审计快照、主题、权限指纹。
 */
export function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);
  const [openMenuSectionKeys, setOpenMenuSectionKeys] = useState<string[]>([]);
  const [sessionWarningOpen, setSessionWarningOpen] = useState(false);
  const warningTimerRef = useRef<number | undefined>(undefined);
  const logoutTimerRef = useRef<number | undefined>(undefined);
  const lastRenewedAtRef = useRef(0);
  const [changePasswordForm] = Form.useForm<ChangePasswordValues>();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const screens = Grid.useBreakpoint();
  const isDesktop = screens.lg ?? (typeof window === "undefined" ? true : window.innerWidth >= 992);
  const currentRoute = findRouteByPath(location.pathname);
  const breadcrumb = getRouteBreadcrumb(location.pathname);
  const securityProfile = useSecurityProfile();
  const changePassword = useChangePassword();
  const logout = useLogout();
  const sessionStatus = useSessionStatus();
  const renewSession = useRenewSession();
  const routeRequiresAuth = currentRoute?.requireAuth ?? true;
  const hasSecurityProfile = Boolean(securityProfile.data);
  const bootstrapSetupRequired = Boolean(
    routeRequiresAuth &&
      securityProfile.data &&
      (securityProfile.data.mustChangePwd ||
        (securityProfile.data.mfaRequired && !securityProfile.data.mfaBound)),
  );
  const canViewCurrentRoute =
    !routeRequiresAuth ||
    (hasSecurityProfile &&
      !bootstrapSetupRequired &&
      canAccessRoute(currentRoute, securityProfile.data));

  const visibleMenuSections = useMemo(
    () => getMenuSectionsForProfile(securityProfile.data).filter((s) => s.items.length > 0),
    [securityProfile.data],
  );
  const defaultOpenMenuSectionKeys = useMemo(
    () => visibleMenuSections.filter((s) => s.key !== "workbench").map((s) => s.key),
    [visibleMenuSections],
  );

  useEffect(() => {
    setOpenMenuSectionKeys(defaultOpenMenuSectionKeys);
  }, [defaultOpenMenuSectionKeys]);

  const items: MenuProps["items"] = useMemo(
    () =>
      visibleMenuSections.map((section) => {
        if (section.key === "workbench") {
          return section.items.map((it) => ({
            key: it.path,
            label: it.label,
            icon: <ToolOutlined />,
          }))[0];
        }
        return {
          key: section.key,
          label: section.label,
          children: section.items.map((it) => ({ key: it.path, label: it.label })),
        };
      }),
    [visibleMenuSections],
  );

  const headerItems = useMemo(
    () => getMenuItemsForProfile(securityProfile.data, "header"),
    [securityProfile.data],
  );
  const profileItems = useMemo(
    () => getMenuItemsForProfile(securityProfile.data, "profile"),
    [securityProfile.data],
  );
  const commandSections = visibleMenuSections;
  const displayName = userDisplayName(securityProfile.data);
  const roleName = primaryRoleName(securityProfile.data);
  const orgText = organizationSummary(securityProfile.data);
  const userMenuItems: MenuProps["items"] = useMemo(
    () =>
      securityProfile.data
        ? [
            {
              key: "profile",
              disabled: true,
              label: (
                <Space direction="vertical" size={0}>
                  <Typography.Text strong>{displayName}</Typography.Text>
                  <Typography.Text type="secondary">{roleName}</Typography.Text>
                  <Typography.Text type="secondary">{orgText}</Typography.Text>
                </Space>
              ),
            },
            { type: "divider" },
            ...profileItems.map((item) => ({
              key: item.path,
              label: item.label,
            })),
            {
              key: "change-password",
              icon: <LockOutlined />,
              label: "修改密码",
            },
            {
              key: "logout",
              danger: true,
              icon: <LogoutOutlined />,
              label: "退出登录",
            },
          ]
        : [],
    [displayName, orgText, profileItems, roleName, securityProfile.data],
  );
  let mainLayoutClassName = "mk-layout-main mk-layout-main-mobile";
  if (isDesktop) {
    mainLayoutClassName = collapsed
      ? "mk-layout-main mk-layout-main-collapsed"
      : "mk-layout-main mk-layout-main-expanded";
  }
  const headerClassName = isDesktop ? "mk-app-header" : "mk-app-header mk-app-header-mobile";
  const breadcrumbItems = (isDesktop ? breadcrumb : breadcrumb.slice(-1)).map((title) => ({
    title,
  }));

  const handleMenuClick: MenuProps["onClick"] = (info) => {
    if (info.key.startsWith("/")) {
      navigate(info.key);
      setMobileMenuOpen(false);
    }
  };

  const clearIdleTimers = useCallback(() => {
    if (warningTimerRef.current !== undefined) {
      window.clearTimeout(warningTimerRef.current);
      warningTimerRef.current = undefined;
    }
    if (logoutTimerRef.current !== undefined) {
      window.clearTimeout(logoutTimerRef.current);
      logoutTimerRef.current = undefined;
    }
  }, []);

  const clearSessionAndReturnToLogin = useCallback(
    (reason: AuthSessionEventReason, options: { broadcast?: boolean } = {}) => {
      clearIdleTimers();
      if (options.broadcast !== false) {
        broadcastAuthSessionEvent(reason);
      }
      queryClient.clear();
      setPaletteOpen(false);
      setMobileMenuOpen(false);
      setLogoutConfirmOpen(false);
      setChangePasswordOpen(false);
      setSessionWarningOpen(false);
      navigate("/login", { replace: true, state: { reason } });
    },
    [clearIdleTimers, navigate, queryClient],
  );

  const handleIdleLogout = useCallback(async () => {
    try {
      await logout.mutateAsync();
    } finally {
      clearSessionAndReturnToLogin("expired");
    }
  }, [clearSessionAndReturnToLogin, logout]);

  const handleSessionWarningLogout = useCallback(async () => {
    try {
      await logout.mutateAsync();
    } finally {
      clearSessionAndReturnToLogin("logout");
    }
  }, [clearSessionAndReturnToLogin, logout]);

  const maybeRenewOnActivity = useCallback(() => {
    if (!hasSecurityProfile || !sessionStatus.data) {
      return;
    }
    const idleWindowMs = sessionStatus.data.idleTimeoutSeconds * 1000;
    const renewIntervalMs = Math.min(60_000, Math.max(10_000, idleWindowMs / 2));
    const now = Date.now();
    if (now - lastRenewedAtRef.current < renewIntervalMs) {
      return;
    }
    lastRenewedAtRef.current = now;
    void renewSession.mutateAsync().catch(() => clearSessionAndReturnToLogin("expired"));
  }, [clearSessionAndReturnToLogin, hasSecurityProfile, renewSession, sessionStatus.data]);

  const resetIdleTimers = useCallback(
    (options: { renew?: boolean } = {}) => {
      clearIdleTimers();
      if (!hasSecurityProfile || !sessionStatus.data) {
        return;
      }
      setSessionWarningOpen(false);
      const idleWindowMs = sessionStatus.data.idleTimeoutSeconds * 1000;
      const warningMs = sessionStatus.data.warningSeconds * 1000;
      const warningDelay = Math.max(0, idleWindowMs - warningMs);
      warningTimerRef.current = window.setTimeout(() => setSessionWarningOpen(true), warningDelay);
      logoutTimerRef.current = window.setTimeout(() => {
        void handleIdleLogout();
      }, idleWindowMs);
      if (options.renew) {
        maybeRenewOnActivity();
      }
    },
    [
      clearIdleTimers,
      handleIdleLogout,
      hasSecurityProfile,
      maybeRenewOnActivity,
      sessionStatus.data,
    ],
  );

  const handleRenewSession = async () => {
    try {
      await renewSession.mutateAsync();
      message.success("会话已续期");
      resetIdleTimers();
    } catch (error) {
      message.error(getApiErrorMessage(error, "会话续期失败，请重新登录"));
      clearSessionAndReturnToLogin("expired");
    }
  };

  const handleUserMenuClick: MenuProps["onClick"] = ({ key }) => {
    if (key.startsWith("/")) {
      navigate(key);
      return;
    }
    if (key === "change-password") {
      setChangePasswordOpen(true);
    }
    if (key === "logout") {
      setLogoutConfirmOpen(true);
    }
  };

  const handleChangePassword = async () => {
    try {
      const values = await changePasswordForm.validateFields();
      await changePassword.mutateAsync({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      message.success("密码已更新");
      setChangePasswordOpen(false);
      changePasswordForm.resetFields();
    } catch (error) {
      if (error && typeof error === "object" && "errorFields" in error) {
        return;
      }
      message.error(getApiErrorMessage(error, "修改密码失败，请检查当前密码后重试"));
    }
  };

  const handleLogout = async () => {
    try {
      await logout.mutateAsync();
      message.success("已退出登录");
      clearSessionAndReturnToLogin("logout");
    } catch (error) {
      message.error(getApiErrorMessage(error, "退出登录失败，请稍后重试"));
    }
  };

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setPaletteOpen(true);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => {
    const onAuthRequired = () => clearSessionAndReturnToLogin("expired");
    window.addEventListener("medkernel:auth-required", onAuthRequired);
    return () => window.removeEventListener("medkernel:auth-required", onAuthRequired);
  }, [clearSessionAndReturnToLogin]);

  useEffect(
    () =>
      subscribeAuthSessionEvent((reason) =>
        clearSessionAndReturnToLogin(reason, { broadcast: false }),
      ),
    [clearSessionAndReturnToLogin],
  );

  useEffect(() => {
    resetIdleTimers();
    return clearIdleTimers;
  }, [clearIdleTimers, resetIdleTimers]);

  useEffect(() => {
    if (!hasSecurityProfile || !sessionStatus.data) {
      return undefined;
    }
    const onActivity = () => resetIdleTimers({ renew: true });
    const events = ["mousemove", "mousedown", "keydown", "touchstart", "scroll"] as const;
    events.forEach((eventName) =>
      window.addEventListener(eventName, onActivity, { passive: true }),
    );
    return () => events.forEach((eventName) => window.removeEventListener(eventName, onActivity));
  }, [hasSecurityProfile, resetIdleTimers, sessionStatus.data]);

  const renderContent = () => {
    if (routeRequiresAuth && !hasSecurityProfile) {
      return securityProfile.isError ? (
        <PageState
          state="error"
          title="暂时无法核验权限"
          description="当前无法获取授权信息，请稍后重试或联系信息科。"
        />
      ) : (
        <PageState
          state="loading"
          title="正在核验权限"
          description="正在确认当前角色与数据范围。"
        />
      );
    }
    if (bootstrapSetupRequired) {
      return (
        <PageState
          state="forbidden"
          title="需要完成首次安全设置"
          description="当前账号仍需完成首次改密或 MFA 绑定，完成前不能进入业务页面。"
          action={
            <Button
              type="primary"
              aria-label="继续设置"
              onClick={() =>
                navigate("/bootstrap", {
                  state: {
                    phase: securityProfile.data?.mustChangePwd ? "change-password" : "mfa",
                    login: securityProfile.data,
                  },
                })
              }
            >
              继续设置
            </Button>
          }
        />
      );
    }
    if (canViewCurrentRoute) {
      return <Outlet />;
    }
    return <PageState state="forbidden" />;
  };

  const renderNavigation = (isCollapsed: boolean) => (
    <>
      <div className={isCollapsed ? "mk-nav-brand mk-nav-brand-collapsed" : "mk-nav-brand"}>
        {isCollapsed ? "MK" : "集团医疗智能中枢"}
      </div>
      <Menu
        mode="inline"
        theme="light"
        selectedKeys={[location.pathname]}
        openKeys={isCollapsed ? [] : openMenuSectionKeys}
        onOpenChange={(keys) => setOpenMenuSectionKeys(keys)}
        items={items}
        onClick={handleMenuClick}
        className="mk-menu-borderless"
      />
    </>
  );

  return (
    <Layout className="mk-layout-shell" hasSider={isDesktop}>
      {isDesktop && (
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          trigger={null}
          width={240}
          className="mk-sider-root"
        >
          {renderNavigation(collapsed)}
        </Sider>
      )}
      <Drawer
        title="集团医疗智能中枢"
        placement="left"
        open={!isDesktop && mobileMenuOpen}
        onClose={() => setMobileMenuOpen(false)}
        width={300}
        rootClassName="mk-drawer-no-body-padding"
      >
        {renderNavigation(false)}
      </Drawer>
      <Layout className={mainLayoutClassName}>
        <Header className={headerClassName}>
          <Space className="mk-min-0">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() =>
                isDesktop ? setCollapsed(!collapsed) : setMobileMenuOpen((open) => !open)
              }
            />
            <Breadcrumb className="mk-route-breadcrumb" items={breadcrumbItems} />
          </Space>
          <Space size={isDesktop ? "small" : 4}>
            {headerItems.map((item) => (
              <Tooltip title={item.label} key={item.key}>
                <Button
                  type="text"
                  aria-label={item.label}
                  icon={<BellOutlined />}
                  onClick={() => navigate(item.path)}
                />
              </Tooltip>
            ))}
            <Tooltip title="命令面板 (Ctrl+K)">
              <Button type="text" icon={<SearchOutlined />} onClick={() => setPaletteOpen(true)}>
                {isDesktop ? "搜索" : null}
              </Button>
            </Tooltip>
            <AuditSnapshotButton compact={!isDesktop} />
            <ThemeSwitcher compact={!isDesktop} />
            {securityProfile.data && (
              <Dropdown
                menu={{ items: userMenuItems, onClick: handleUserMenuClick }}
                placement="bottomRight"
                trigger={["click"]}
              >
                <Button
                  type="text"
                  aria-label="当前用户菜单"
                  icon={<Avatar size="small" icon={<UserOutlined />} />}
                >
                  {isDesktop ? displayName : null}
                </Button>
              </Dropdown>
            )}
            {isDesktop && <PermissionChip />}
          </Space>
        </Header>
        <Content className="mk-app-content">{renderContent()}</Content>
      </Layout>
      <Modal
        title="修改密码"
        open={changePasswordOpen}
        okText="保存修改"
        cancelText="取消"
        confirmLoading={changePassword.isPending}
        onOk={handleChangePassword}
        onCancel={() => {
          setChangePasswordOpen(false);
          changePasswordForm.resetFields();
        }}
      >
        <Form form={changePasswordForm} layout="vertical">
          <Form.Item
            name="oldPassword"
            label="当前密码"
            rules={[{ required: true, message: "请输入当前密码" }]}
          >
            <Input.Password autoComplete="current-password" placeholder="请输入当前密码" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true, message: "请输入新密码" }]}
          >
            <Input.Password autoComplete="new-password" placeholder="请输入新密码" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={["newPassword"]}
            rules={[
              { required: true, message: "请再次输入新密码" },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue("newPassword") === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error("两次输入的新密码不一致"));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" placeholder="再次输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="确认退出登录"
        open={logoutConfirmOpen}
        okText="确认退出"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={logout.isPending}
        onOk={handleLogout}
        onCancel={() => setLogoutConfirmOpen(false)}
      >
        <Typography.Paragraph>
          退出后将清除当前前端会话状态，并由后端清理 httpOnly 登录
          Cookie；再次访问业务页面需要重新登录。
        </Typography.Paragraph>
      </Modal>
      <Modal
        title="会话即将超时"
        open={sessionWarningOpen}
        okText="继续使用"
        cancelText="退出登录"
        onOk={handleRenewSession}
        onCancel={() => void handleSessionWarningLogout()}
        confirmLoading={renewSession.isPending}
      >
        <Typography.Paragraph>
          当前会话长时间无操作。继续使用会向服务端续期；不处理将自动退出登录。
        </Typography.Paragraph>
      </Modal>
      <CommandPalette
        open={paletteOpen}
        onClose={() => setPaletteOpen(false)}
        sections={commandSections}
      />
    </Layout>
  );
}
