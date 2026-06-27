import { useMemo, useState, type ReactNode } from "react";
import { Alert, Badge, Button, Card, List, Select, Space, Tag, message } from "antd";
import type { BadgeProps } from "antd";
import { CheckOutlined, ReloadOutlined, SettingOutlined } from "@ant-design/icons";

import {
  useOrgUnits,
  useReadWorkflowNotification,
  useSecurityProfile,
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
import { findRouteByPath } from "@/shared/config/routes";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import { customerEnumLabel } from "@/shared/config/customerLabels";

import styles from "./Clinical.module.css";

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

const ORG_UNIT_REFERENCE_PAGE_SIZE = 20;
const route = findRouteByPath("/notifications");
const PAGE_META = {
  title: route?.title ?? "消息通知",
  experience: route?.experience ?? {
    primaryRole: "临床使用者",
    goal: "查看需要关注的通知",
    defaultView: "未读通知",
    defaultFilters: [],
    evidenceDetailContent: ["患者编号", "就诊编号", "来源编号", "追踪号"],
    interruptionLevel: "info" as const,
    evidence: "通知来源、外发补偿和已读动作均保留审计证据",
    dataScale: { expected: "large" as const, pagination: "page" as const, exportStrategy: "none" as const },
    riskLevel: "medium" as const,
  },
};

export default function Notifications() {
  const [status, setStatus] = useState<WorkflowNotificationStatus | undefined>("UNREAD");
  const [level, setLevel] = useState<WorkflowNotificationLevel | undefined>();
  const [orgUnitId, setOrgUnitId] = useState<string | undefined>();
  const [orgUnitSearch, setOrgUnitSearch] = useState("");
  const security = useSecurityProfile();
  const evidenceDetailsEnabled = useEvidenceDetailsStore((state) => state.enabled);

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
  const orgUnitKeyword = orgUnitSearch.trim();
  const { data: orgUnits, isLoading: orgUnitsLoading } = useOrgUnits({
    page: 1,
    size: ORG_UNIT_REFERENCE_PAGE_SIZE,
    status: "ACTIVE",
    ...(orgUnitKeyword ? { keyword: orgUnitKeyword } : {}),
  });
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
        description="通知偏好暂时不可用，请刷新或到通知设置页确认。"
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
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
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
      <Card className={styles.sectionGap}>
        <Space direction="vertical" size="middle" className={styles.fullWidth}>
          {quietSettingsAlert}
          <Space wrap>
            <Select
              aria-label="通知状态"
              value={status}
              onChange={setStatus}
              allowClear
              className={styles.controlSm}
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
              className={styles.controlSm}
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
              showSearch
              filterOption={false}
              onSearch={setOrgUnitSearch}
              onClear={() => setOrgUnitSearch("")}
              loading={orgUnitsLoading}
              placeholder="组织范围"
              className={styles.controlMd}
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
          className={styles.sectionGap}
          message="通知读取失败"
          description="请确认登录状态、组织范围；若持续失败，请联系信息科核查通知服务。"
        />
      )}

      <Card>
        <List
          loading={isLoading}
          dataSource={data?.items ?? []}
          locale={{ emptyText: "当前暂无通知" }}
          renderItem={(item) => {
            const sourceLink = resolveSourceDeepLink(item.deepLink);
            const externalDeliveries = item.externalDeliveries ?? [];
            const sourceAction = sourceLink ? (
              <Button
                key="source"
                type="link"
                aria-label="打开来源"
                href={sourceLink}
                className={styles.buttonLink}
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
                  className={styles.buttonLink}
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
                      <span className={styles.textStrong}>{item.title}</span>
                      <Tag color={levelColor[item.level]}>{levelText[item.level]}</Tag>
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
                      <Space wrap className={styles.textSmall}>
                        <span>{sourceText[item.sourceType]}</span>
                        {item.patientId || item.encounterId ? (
                          <span>已关联患者上下文</span>
                        ) : (
                          <span>未关联患者上下文</span>
                        )}
                        {evidenceDetailsEnabled && (
                          <>
                            <span>{item.patientId || "患者编号未提供"}</span>
                            <span>{item.encounterId || "就诊编号未提供"}</span>
                            <span>来源编号 {item.sourceId}</span>
                            <span>
                              {item.traceId
                                ? `追踪号 ${item.traceId}`
                                : SOURCE_TRACE_MISSING_TEXT}
                            </span>
                          </>
                        )}
                      </Space>
                      {externalDeliveries.length > 0 && (
                        <Space wrap className={styles.textSmall}>
                          <span>外发状态</span>
                          {externalDeliveries.map((delivery) => (
                            <Tag
                              key={`${delivery.channelCode}-${delivery.status}`}
                              color={delivery.compensationRequired ? "orange" : "green"}
                            >
                              {`${delivery.channelName} ${customerEnumLabel(delivery.status)}`}
                            </Tag>
                          ))}
                          {externalDeliveries.some((delivery) => delivery.compensationRequired) && (
                            <Tag color="orange">需补偿</Tag>
                          )}
                          {externalDeliveries.map((delivery) =>
                            delivery.errorMessage ? (
                              <span key={`${delivery.channelCode}-error`}>
                                {delivery.errorMessage}
                              </span>
                            ) : null,
                          )}
                        </Space>
                      )}
                    </Space>
                  }
                />
              </List.Item>
            );
          }}
        />
      </Card>
    </PageExperienceShell>
  );
}
