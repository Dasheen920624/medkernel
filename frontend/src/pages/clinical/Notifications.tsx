import { useMemo, useState, type ReactNode } from "react";
import { Alert, Badge, Button, Card, List, Select, Space, Tag, message } from "antd";
import type { BadgeProps } from "antd";
import { CheckOutlined, ReloadOutlined, SettingOutlined } from "@ant-design/icons";

import {
  useOrgUnits,
  useReadWorkflowNotification,
  useWorkflowNotificationSettings,
  useWorkflowNotifications,
} from "@/shared/api/hooks";
import type {
  WorkflowNotification,
  WorkflowNotificationLevel,
  WorkflowNotificationSourceType,
  WorkflowNotificationStatus,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import {
  SOURCE_LINK_MISSING_TEXT,
  SOURCE_LINK_UNAVAILABLE_TEXT,
  SOURCE_TRACE_MISSING_TEXT,
  resolveSourceDeepLink,
} from "@/shared/lib/sourceLink";
import { PageShell } from "@/shared/ui/PageShell";

const statusText: Record<WorkflowNotificationStatus, string> = {
  UNREAD: "未读",
  READ: "已读",
};

const statusBadge: Record<WorkflowNotificationStatus, BadgeProps["status"]> = {
  UNREAD: "processing",
  READ: "success",
};

const levelColor: Record<WorkflowNotificationLevel, string> = {
  CRITICAL: "red",
  HIGH: "volcano",
  MEDIUM: "gold",
  LOW: "blue",
  INFO: "default",
};

const levelText: Record<WorkflowNotificationLevel, string> = {
  CRITICAL: "危急",
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低",
  INFO: "信息",
};

const sourceText: Record<WorkflowNotificationSourceType, string> = {
  FOLLOWUP_EVENT: "随访事件",
  SAFETY_REVIEW: "安全复核",
  WORKFLOW_TODO: "协同待办",
  SYNC_EVENT: "同步事件",
};

export default function Notifications() {
  const [status, setStatus] = useState<WorkflowNotificationStatus | undefined>("UNREAD");
  const [level, setLevel] = useState<WorkflowNotificationLevel | undefined>();
  const [orgUnitId, setOrgUnitId] = useState<string | undefined>();

  const queryParams = {
    status,
    level,
    orgUnitId,
    page: 1,
    size: 10,
  };
  const { data, isError, isLoading, refetch } = useWorkflowNotifications(queryParams);
  const { data: notificationSettings, isError: notificationSettingsError } =
    useWorkflowNotificationSettings();
  const { data: orgUnits, isLoading: orgUnitsLoading } = useOrgUnits({ page: 1, size: 100 });
  const readMutation = useReadWorkflowNotification();
  const unreadNotifications = data?.items.filter((item) => item.status === "UNREAD") ?? [];
  const quietActiveNow = Boolean(
    notificationSettings?.quietHoursEnabled && notificationSettings.quietActiveNow,
  );
  const quietWindow = notificationSettings
    ? `${notificationSettings.quietStart} - ${notificationSettings.quietEnd}`
    : undefined;
  const quietBypassText = (notificationSettings?.quietBypassLevels ?? [])
    .map((item) => levelText[item])
    .join("、");
  const orgUnitOptions = useMemo(
    () =>
      (orgUnits?.items ?? [])
        .filter((unit) => unit.id)
        .map((unit) => ({ value: unit.id ?? "", label: unit.name })),
    [orgUnits?.items],
  );
  const isQuietBypassLevel = (notificationLevel: WorkflowNotificationLevel) =>
    notificationSettings?.quietBypassLevels.includes(notificationLevel) ?? false;
  const isMutedByQuietHours = (notificationLevel: WorkflowNotificationLevel) =>
    quietActiveNow && !isQuietBypassLevel(notificationLevel);
  let quietSettingsAlert: ReactNode = null;
  if (notificationSettingsError) {
    quietSettingsAlert = (
      <Alert
        type="warning"
        showIcon
        message="免打扰状态暂不可确认"
        description="通知设置接口暂不可用，请刷新或到设置页确认。"
      />
    );
  } else if (notificationSettings?.quietHoursEnabled) {
    quietSettingsAlert = (
      <Alert
        type={quietActiveNow ? "info" : "success"}
        showIcon
        message={quietActiveNow ? "当前免打扰生效" : "免打扰已配置"}
        description={
          <Space wrap>
            {quietWindow && <span>{quietWindow}</span>}
            {quietBypassText && <span>{quietBypassText}绕过免打扰</span>}
          </Space>
        }
      />
    );
  }

  const markRead = async (notification: WorkflowNotification) => {
    try {
      await readMutation.mutateAsync(notification.notificationId);
      message.success("通知已标记为已读");
      await refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "通知已读失败"));
    }
  };

  const markAllRead = async () => {
    if (unreadNotifications.length === 0) return;
    try {
      await Promise.all(
        unreadNotifications.map((notification) =>
          readMutation.mutateAsync(notification.notificationId),
        ),
      );
      message.success("当前页未读通知已标记为已读");
      await refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "全部已读失败"));
    }
  };

  return (
    <PageShell
      title="通知中心"
      description="查看真实业务通知并同步已读状态。"
      extras={
        <Space wrap>
          {unreadNotifications.length > 0 && (
            <Button
              type="primary"
              aria-label="全部已读"
              icon={<CheckOutlined />}
              loading={readMutation.isPending}
              onClick={markAllRead}
            >
              全部已读
            </Button>
          )}
          <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
            刷新
          </Button>
          <Button icon={<SettingOutlined />} href="/notifications/settings">
            设置
          </Button>
        </Space>
      }
    >
      <Card className="mb-4">
        <Space direction="vertical" size="middle" className="mk-full-width">
          {quietSettingsAlert}
          <Space wrap>
            <Select
              aria-label="通知状态"
              value={status}
              onChange={setStatus}
              allowClear
              className="w-36"
              options={[
                { value: "UNREAD", label: "未读" },
                { value: "READ", label: "已读" },
              ]}
            />
            <Select
              aria-label="通知级别"
              value={level}
              onChange={setLevel}
              allowClear
              placeholder="级别"
              className="w-36"
              options={[
                { value: "CRITICAL", label: "危急" },
                { value: "HIGH", label: "高" },
                { value: "MEDIUM", label: "中" },
                { value: "LOW", label: "低" },
                { value: "INFO", label: "信息" },
              ]}
            />
            <Select
              id="notifications-org-unit"
              value={orgUnitId}
              onChange={setOrgUnitId}
              allowClear
              loading={orgUnitsLoading}
              placeholder="组织范围"
              className="w-44"
              options={orgUnitOptions}
            />
            <label className="mk-sr-only" htmlFor="notifications-org-unit">
              组织范围
            </label>
          </Space>
        </Space>
      </Card>

      {isError && (
        <Alert
          type="error"
          showIcon
          className="mb-4"
          message="通知读取失败"
          description="请检查登录状态、租户上下文或后端通知接口。"
        />
      )}

      <Card>
        <List
          loading={isLoading}
          dataSource={data?.items ?? []}
          locale={{ emptyText: "当前暂无通知" }}
          renderItem={(item) => {
            const sourceLink = resolveSourceDeepLink(item.deepLink);
            const sourceAction = sourceLink ? (
              <Button
                key="source"
                type="link"
                aria-label="打开来源"
                href={sourceLink}
                className="px-0 font-semibold"
              >
                打开来源
              </Button>
            ) : null;
            const unavailableSourceAction =
              !sourceLink && item.deepLink ? (
                <Tag key="source-unavailable" color="default">
                  {SOURCE_LINK_UNAVAILABLE_TEXT}
                </Tag>
              ) : null;
            const missingSourceAction =
              !sourceLink && !item.deepLink ? (
                <Tag key="source-missing" color="default">
                  {SOURCE_LINK_MISSING_TEXT}
                </Tag>
              ) : null;
            const readAction =
              item.status === "UNREAD" ? (
                <Button
                  key="read"
                  type="link"
                  aria-label="标为已读"
                  icon={<CheckOutlined />}
                  loading={readMutation.isPending}
                  onClick={() => markRead(item)}
                  className="px-0 font-semibold"
                >
                  标为已读
                </Button>
              ) : null;

            return (
              <List.Item
                actions={[
                  sourceAction,
                  unavailableSourceAction,
                  missingSourceAction,
                  readAction,
                ].filter(Boolean)}
              >
                <List.Item.Meta
                  title={
                    <Space wrap>
                      <span className="font-semibold text-slate-800">{item.title}</span>
                      <Tag color={levelColor[item.level]}>{item.level}</Tag>
                      {isMutedByQuietHours(item.level) && <Tag color="default">免打扰中</Tag>}
                      {quietActiveNow && isQuietBypassLevel(item.level) && (
                        <Tag color="green">安全绕过</Tag>
                      )}
                      <Badge status={statusBadge[item.status]} text={statusText[item.status]} />
                    </Space>
                  }
                  description={
                    <Space direction="vertical" size={2}>
                      <span>{item.message}</span>
                      <Space wrap className="text-xs text-slate-500">
                        <span>{sourceText[item.sourceType]}</span>
                        <span>{item.patientId || "-"}</span>
                        <span>{item.encounterId || "-"}</span>
                        <span>来源对象 {item.sourceId}</span>
                        <span>
                          {item.traceId ? `追踪链路 ${item.traceId}` : SOURCE_TRACE_MISSING_TEXT}
                        </span>
                      </Space>
                    </Space>
                  }
                />
              </List.Item>
            );
          }}
        />
      </Card>
    </PageShell>
  );
}
