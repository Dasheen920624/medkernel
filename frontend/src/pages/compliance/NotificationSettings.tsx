import { useEffect } from "react";
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
  message,
} from "antd";
import { ReloadOutlined, SaveOutlined } from "@ant-design/icons";

import {
  useSaveWorkflowNotificationSettings,
  useWorkflowNotificationSettings,
} from "@/shared/api/hooks";
import type {
  WorkflowNotificationLevel,
  WorkflowNotificationSettingsPayload,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import { PageShell } from "@/shared/ui/PageShell";

const { Text } = Typography;

type NotificationSettingsForm = WorkflowNotificationSettingsPayload;

const SAFETY_BYPASS_LEVELS: WorkflowNotificationLevel[] = ["CRITICAL", "HIGH"];

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

function normalizeBypassLevels(levels: WorkflowNotificationLevel[] | undefined) {
  return Array.from(new Set([...SAFETY_BYPASS_LEVELS, ...(levels ?? [])]));
}

export default function NotificationSettings() {
  const [form] = Form.useForm<NotificationSettingsForm>();
  const settingsQuery = useWorkflowNotificationSettings();
  const saveMutation = useSaveWorkflowNotificationSettings();

  useEffect(() => {
    if (!settingsQuery.data) return;
    form.setFieldsValue({
      inAppEnabled: settingsQuery.data.inAppEnabled,
      smsEnabled: settingsQuery.data.smsEnabled,
      emailEnabled: settingsQuery.data.emailEnabled,
      pushEnabled: settingsQuery.data.pushEnabled,
      webhookEnabled: settingsQuery.data.webhookEnabled,
      inHospitalMessageEnabled: settingsQuery.data.inHospitalMessageEnabled,
      quietHoursEnabled: settingsQuery.data.quietHoursEnabled,
      quietStart: settingsQuery.data.quietStart,
      quietEnd: settingsQuery.data.quietEnd,
      quietBypassLevels: normalizeBypassLevels(settingsQuery.data.quietBypassLevels),
    });
  }, [form, settingsQuery.data]);

  const saveSettings = async () => {
    try {
      const values = await form.validateFields();
      await saveMutation.mutateAsync({
        ...values,
        quietBypassLevels: normalizeBypassLevels(values.quietBypassLevels),
      });
      message.success("通知设置已保存");
      await settingsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "通知设置保存失败"));
    }
  };

  return (
    <PageShell
      title="通知设置"
      description="保存个人通知偏好与免打扰窗口。"
      primary={
        <Button
          type="primary"
          aria-label="保存通知设置"
          icon={<SaveOutlined />}
          loading={saveMutation.isPending}
          onClick={saveSettings}
        >
          保存
        </Button>
      }
      extras={
        <Button
          icon={<ReloadOutlined />}
          loading={settingsQuery.isLoading}
          onClick={() => settingsQuery.refetch()}
        >
          刷新
        </Button>
      }
    >
      {settingsQuery.isError && (
        <Alert
          type="error"
          showIcon
          className="mb-4"
          message="通知设置读取失败"
          description="请检查登录状态、租户上下文或后端通知设置接口。"
        />
      )}

      <Form
        form={form}
        layout="vertical"
        initialValues={DEFAULT_FORM_VALUES}
        disabled={settingsQuery.isLoading}
      >
        <Card loading={settingsQuery.isLoading} title="渠道偏好" className="mb-4">
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Alert
              type="info"
              showIcon
              message="启用外部通道后会登记外发补偿消息；当前未接真实发送连接器，状态为 NOT_CONNECTED，不声明短信、邮件、移动推送、Webhook 或院内消息已完成投递。"
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

        <Card loading={settingsQuery.isLoading} title="免打扰策略">
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
              {settingsQuery.data?.quietActiveNow && <Tag color="blue">当前免打扰生效</Tag>}
              {settingsQuery.data?.version ? (
                <Text type="secondary">版本 {settingsQuery.data.version}</Text>
              ) : null}
            </Space>
          </Space>
        </Card>
      </Form>
    </PageShell>
  );
}
