import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  Segmented,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
  message,
} from "antd";
import { ReloadOutlined, SaveOutlined } from "@ant-design/icons";

import {
  useSaveWorkflowSystemNotificationSettings,
  useSaveWorkflowNotificationSettings,
  useSecurityProfile,
  useWorkflowSystemNotificationSettings,
  useWorkflowNotificationSettings,
} from "@/shared/api/hooks";
import type {
  WorkflowNotificationLevel,
  WorkflowNotificationSettingsPayload,
  WorkflowNotificationType,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import { PageShell } from "@/shared/ui/PageShell";

const { Text } = Typography;

type SettingsMode = "PERSONAL" | "SYSTEM";
type NotificationSettingsForm = WorkflowNotificationSettingsPayload & {
  changeReason?: string;
};

const SAFETY_BYPASS_LEVELS: WorkflowNotificationLevel[] = ["CRITICAL", "HIGH"];
const MANDATORY_NOTIFICATION_TYPES: WorkflowNotificationType[] = ["SAFETY"];
const ALL_NOTIFICATION_TYPES: WorkflowNotificationType[] = [
  "SAFETY",
  "FOLLOWUP",
  "WORKFLOW",
  "SYNC",
];

const DEFAULT_FORM_VALUES: NotificationSettingsForm = {
  inAppEnabled: true,
  smsEnabled: false,
  emailEnabled: false,
  pushEnabled: false,
  webhookEnabled: false,
  inHospitalMessageEnabled: false,
  quietHoursEnabled: false,
  quietStart: "22:00",
  quietEnd: "07:00",
  quietBypassLevels: SAFETY_BYPASS_LEVELS,
  subscribedTypes: ALL_NOTIFICATION_TYPES,
};

const levelLabels: Record<WorkflowNotificationLevel, string> = {
  CRITICAL: "危急",
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低",
  INFO: "信息",
};

const bypassOptions = (
  ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"] as WorkflowNotificationLevel[]
).map((level) => ({
  value: level,
  label: levelLabels[level],
  disabled: SAFETY_BYPASS_LEVELS.includes(level),
}));

const notificationTypeLabels: Record<WorkflowNotificationType, string> = {
  SAFETY: "安全与危急",
  FOLLOWUP: "随访异常",
  WORKFLOW: "协作待办",
  SYNC: "同步与接入",
};

const subscriptionOptions = ALL_NOTIFICATION_TYPES.map((type) => ({
  value: type,
  label: notificationTypeLabels[type],
  disabled: MANDATORY_NOTIFICATION_TYPES.includes(type),
}));

function normalizeBypassLevels(levels: WorkflowNotificationLevel[] | undefined) {
  return Array.from(new Set([...SAFETY_BYPASS_LEVELS, ...(levels ?? [])]));
}

function normalizeSubscribedTypes(types: WorkflowNotificationType[] | undefined) {
  return Array.from(new Set([...MANDATORY_NOTIFICATION_TYPES, ...(types ?? [])]));
}

function hasPermission(
  profile: ReturnType<typeof useSecurityProfile>["data"],
  permissionCode: string,
) {
  return profile?.permissions.some((permission) => permission.code === permissionCode) ?? false;
}

export default function NotificationSettings() {
  const [form] = Form.useForm<NotificationSettingsForm>();
  const [mode, setMode] = useState<SettingsMode>("PERSONAL");
  const securityQuery = useSecurityProfile();
  const personalQuery = useWorkflowNotificationSettings();
  const canViewSystem = hasPermission(securityQuery.data, "system.read");
  const canManageSystem = hasPermission(securityQuery.data, "system.manage");
  const systemQuery = useWorkflowSystemNotificationSettings(canViewSystem);
  const savePersonalMutation = useSaveWorkflowNotificationSettings();
  const saveSystemMutation = useSaveWorkflowSystemNotificationSettings();
  const currentQuery = mode === "SYSTEM" ? systemQuery : personalQuery;
  const currentSettings = currentQuery.data;
  const isSaving = savePersonalMutation.isPending || saveSystemMutation.isPending;
  let sourceMessage = "";
  if (currentSettings && mode === "SYSTEM") {
    sourceMessage = `当前服务机构默认策略 · 版本 ${currentSettings.systemVersion}`;
  } else if (currentSettings?.source === "SYSTEM_DEFAULT") {
    sourceMessage = "当前使用系统默认策略，保存后形成个人覆盖";
  } else if (currentSettings) {
    sourceMessage = `当前使用个人偏好 · 版本 ${currentSettings.version}`;
  }

  useEffect(() => {
    if (!currentSettings) return;
    form.setFieldsValue({
      inAppEnabled: currentSettings.inAppEnabled,
      smsEnabled: currentSettings.smsEnabled,
      emailEnabled: currentSettings.emailEnabled,
      pushEnabled: currentSettings.pushEnabled,
      webhookEnabled: currentSettings.webhookEnabled,
      inHospitalMessageEnabled: currentSettings.inHospitalMessageEnabled,
      quietHoursEnabled: currentSettings.quietHoursEnabled,
      quietStart: currentSettings.quietStart,
      quietEnd: currentSettings.quietEnd,
      quietBypassLevels: normalizeBypassLevels(currentSettings.quietBypassLevels),
      subscribedTypes: normalizeSubscribedTypes(currentSettings.subscribedTypes),
      changeReason: undefined,
    });
  }, [currentSettings, form]);

  const saveSettings = async () => {
    try {
      const values = await form.validateFields();
      const { changeReason, ...settingValues } = values;
      const payload: WorkflowNotificationSettingsPayload = {
        ...settingValues,
        quietBypassLevels: normalizeBypassLevels(values.quietBypassLevels),
        subscribedTypes: normalizeSubscribedTypes(values.subscribedTypes),
      };
      if (mode === "SYSTEM") {
        if (!canManageSystem || !systemQuery.data) {
          message.error("当前账号无权修改系统通知默认策略");
          return;
        }
        await saveSystemMutation.mutateAsync({
          settings: payload,
          reason: changeReason?.trim() ?? "",
          expectedVersion: systemQuery.data.systemVersion,
        });
        message.success("系统通知默认策略已保存");
        await systemQuery.refetch();
      } else {
        await savePersonalMutation.mutateAsync(payload);
        message.success("个人通知偏好已保存");
        await personalQuery.refetch();
      }
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "通知偏好保存失败"));
    }
  };

  return (
    <PageShell
      title="通知偏好"
      description={
        mode === "SYSTEM"
          ? "配置当前服务机构的通知默认策略；已有个人偏好继续优先生效。"
          : "配置本人的通知渠道、订阅类型与免打扰窗口。"
      }
      primary={
        <Button
          type="primary"
          aria-label="保存通知偏好"
          icon={<SaveOutlined />}
          loading={isSaving}
          disabled={mode === "SYSTEM" && !canManageSystem}
          onClick={saveSettings}
        >
          保存
        </Button>
      }
      extras={
        <Space wrap>
          {canViewSystem && (
            <Segmented<SettingsMode>
              aria-label="通知偏好范围"
              value={mode}
              options={[
                { label: "个人偏好", value: "PERSONAL" },
                { label: "系统默认", value: "SYSTEM" },
              ]}
              onChange={setMode}
            />
          )}
          <Button
            icon={<ReloadOutlined />}
            loading={currentQuery.isLoading}
            onClick={() => currentQuery.refetch()}
          >
            刷新
          </Button>
        </Space>
      }
    >
      {currentQuery.isError && (
        <Alert
          type="error"
          showIcon
          className="mk-card-gap-bottom"
          message="通知偏好读取失败"
          description="请检查登录状态、服务空间或后端通知偏好接口。"
        />
      )}

      {!currentQuery.isError && currentSettings && (
        <Alert
          type={currentSettings.source === "SYSTEM_DEFAULT" ? "info" : "success"}
          showIcon
          className="mk-card-gap-bottom"
          message={sourceMessage}
        />
      )}

      <Form
        form={form}
        layout="vertical"
        initialValues={DEFAULT_FORM_VALUES}
        disabled={currentQuery.isLoading || (mode === "SYSTEM" && !canManageSystem)}
      >
        <Card loading={currentQuery.isLoading} title="渠道偏好" className="mk-card-gap-bottom">
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Alert
              type="info"
              showIcon
              message="启用外部通道后会登记外发补偿消息；当前未接真实发送连接器时会明确显示“未连接”，不声明短信、邮件、移动推送、Webhook 或院内消息已完成投递。"
            />
            <Space wrap size="large">
              <Form.Item name="inAppEnabled" label="站内信" valuePropName="checked">
                <Switch aria-label="站内信偏好" />
              </Form.Item>
              <Form.Item name="smsEnabled" label="短信" valuePropName="checked">
                <Switch aria-label="短信偏好" />
              </Form.Item>
              <Form.Item name="emailEnabled" label="邮件" valuePropName="checked">
                <Switch aria-label="邮件偏好" />
              </Form.Item>
              <Form.Item name="pushEnabled" label="移动推送" valuePropName="checked">
                <Switch aria-label="移动推送偏好" />
              </Form.Item>
              <Form.Item name="webhookEnabled" label="Webhook" valuePropName="checked">
                <Switch aria-label="Webhook 偏好" />
              </Form.Item>
              <Form.Item name="inHospitalMessageEnabled" label="院内消息" valuePropName="checked">
                <Switch aria-label="院内消息偏好" />
              </Form.Item>
            </Space>
          </Space>
        </Card>

        <Card loading={currentQuery.isLoading} title="订阅类型" className="mk-card-gap-bottom">
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Alert
              type="warning"
              showIcon
              message="安全与危急通知不可退订；类型退订仅停止普通外部触达，站内审计留痕继续保留。"
            />
            <Form.Item name="subscribedTypes" label="接收以下通知">
              <Checkbox.Group
                aria-label="通知订阅类型"
                options={subscriptionOptions}
                className="mk-full-width"
              />
            </Form.Item>
          </Space>
        </Card>

        <Card loading={currentQuery.isLoading} title="免打扰策略">
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Form.Item name="quietHoursEnabled" label="启用免打扰" valuePropName="checked">
              <Switch aria-label="免打扰偏好" />
            </Form.Item>
            <Space wrap>
              <Form.Item
                name="quietStart"
                label="开始时间"
                rules={[
                  { required: true, message: "请填写免打扰开始时间" },
                  {
                    pattern: /^([01]\d|2[0-3]):[0-5]\d$/,
                    message: "时间格式应为 HH:mm",
                  },
                ]}
              >
                <Input aria-label="免打扰开始时间" inputMode="numeric" maxLength={5} />
              </Form.Item>
              <Form.Item
                name="quietEnd"
                label="结束时间"
                rules={[
                  { required: true, message: "请填写免打扰结束时间" },
                  {
                    pattern: /^([01]\d|2[0-3]):[0-5]\d$/,
                    message: "时间格式应为 HH:mm",
                  },
                ]}
              >
                <Input aria-label="免打扰结束时间" inputMode="numeric" maxLength={5} />
              </Form.Item>
            </Space>
            <Form.Item name="quietBypassLevels" label="免打扰绕过级别">
              <Select
                mode="multiple"
                aria-label="免打扰绕过级别"
                options={bypassOptions}
                className="mk-full-width"
              />
            </Form.Item>
            <Space wrap>
              <Text type="secondary">以上级别始终绕过免打扰。</Text>
              {currentSettings?.quietActiveNow && <Tag color="blue">当前免打扰生效</Tag>}
              {mode === "PERSONAL" && currentSettings?.version ? (
                <Text type="secondary">个人版本 {currentSettings.version}</Text>
              ) : null}
            </Space>
            {mode === "SYSTEM" && (
              <Form.Item
                name="changeReason"
                label="变更原因"
                rules={[
                  { required: true, message: "请填写系统默认策略变更原因" },
                  { max: 500, message: "变更原因不能超过 500 个字符" },
                ]}
              >
                <Input.TextArea
                  aria-label="系统通知策略变更原因"
                  rows={3}
                  placeholder="说明适用范围、变更目的和验证方式"
                />
              </Form.Item>
            )}
          </Space>
        </Card>
      </Form>
    </PageShell>
  );
}
