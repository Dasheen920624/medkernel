import {
  Alert,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import { useState } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import { useSecurityProfile } from "@/shared/api/hooks";
import {
  type ModelProviderActivationPayload,
  type ModelProviderGovernanceView,
  useCheckModelProviderHealth,
  useModelProviders,
  useRemoveModelProviderCredential,
  useSaveModelProviderCredential,
  useSetModelProviderEnabled,
  useUpsertModelProvider,
} from "@/shared/api/modelProviders";
import {
  MODEL_CAPABILITY_OPTIONS,
  MODEL_PROVIDER_TYPE_OPTIONS,
} from "@/shared/config/modelProduction";
import { formatClinicalDateTime } from "@/shared/lib/dateTimeText";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";
import { PageState } from "@/shared/ui/PageState";

import styles from "./ProviderSetupPanel.module.css";

const { Text } = Typography;
const PROVIDER_PAGE_SIZE = 20;

const STATUS_META: Record<string, { label: string; color: string }> = {
  HEALTHY: { label: "健康", color: "success" },
  UNHEALTHY: { label: "连接异常", color: "error" },
  NOT_CONNECTED: { label: "待健康检查", color: "warning" },
};

type ProviderFormValues = {
  providerCode: string;
  providerType: ModelProviderGovernanceView["providerType"];
  endpointUri: string;
  modelVersion: string;
};

type CredentialFormValues = {
  credential: string;
  reason: string;
  confirmedHighRisk: boolean;
};

type ActivationFormValues = {
  capabilityCode?: string;
  reason: string;
  confirmedHighRisk: boolean;
};

type RemovalFormValues = {
  reason: string;
  confirmedHighRisk: boolean;
};

function credentialLabel(provider: ModelProviderGovernanceView) {
  if (!provider.credentialConfigured) return <Tag>未配置</Tag>;
  if (provider.credentialLast4) return <Tag color="blue">尾号 {provider.credentialLast4}</Tag>;
  return <Tag color="blue">密钥已配置</Tag>;
}

function formatDateTime(value?: string | null) {
  if (!value) return "—";
  return formatClinicalDateTime(value, value);
}

function providerTypeLabel(providerType: ModelProviderGovernanceView["providerType"]) {
  return (
    MODEL_PROVIDER_TYPE_OPTIONS.find((option) => option.value === providerType)?.label ??
    providerType
  );
}

function renderProviderIdentity(
  provider: ModelProviderGovernanceView,
  evidenceDetailsEnabled: boolean,
) {
  return (
    <Space direction="vertical" size={0} className={styles.providerCell}>
      <Text strong>
        {evidenceDetailsEnabled ? provider.providerCode : providerTypeLabel(provider.providerType)}
      </Text>
      <Text type="secondary">{provider.modelVersion}</Text>
      {evidenceDetailsEnabled ? <Text code>{provider.endpointUri}</Text> : null}
    </Space>
  );
}

function renderCredentialAudit(
  provider: ModelProviderGovernanceView,
  evidenceDetailsEnabled: boolean,
) {
  if (!provider.credentialUpdatedAt) return null;
  const updatedAt = formatDateTime(provider.credentialUpdatedAt);
  return (
    <Text type="secondary">
      {evidenceDetailsEnabled
        ? `${updatedAt} · ${provider.credentialUpdatedBy ?? "未知更新人"}`
        : `${updatedAt} · 已记录`}
    </Text>
  );
}

export default function ProviderSetupPanel() {
  const security = useSecurityProfile();
  const canManage =
    security.data?.permissions?.some((permission) => permission.code === "llm.provider.manage") ??
    false;
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const [providerPage, setProviderPage] = useState(1);
  const providers = useModelProviders(
    { page: providerPage, size: PROVIDER_PAGE_SIZE },
    Boolean(security.data),
  );
  const upsert = useUpsertModelProvider();
  const saveCredential = useSaveModelProviderCredential();
  const removeCredential = useRemoveModelProviderCredential();
  const checkHealth = useCheckModelProviderHealth();
  const setEnabled = useSetModelProviderEnabled();
  const [providerForm] = Form.useForm<ProviderFormValues>();
  const [credentialForm] = Form.useForm<CredentialFormValues>();
  const [activationForm] = Form.useForm<ActivationFormValues>();
  const [removalForm] = Form.useForm<RemovalFormValues>();
  const [providerModalOpen, setProviderModalOpen] = useState(false);
  const [editingProvider, setEditingProvider] = useState<ModelProviderGovernanceView | null>(null);
  const [credentialProvider, setCredentialProvider] = useState<ModelProviderGovernanceView | null>(
    null,
  );
  const [removalProvider, setRemovalProvider] = useState<ModelProviderGovernanceView | null>(null);
  const [activation, setActivation] = useState<{
    provider: ModelProviderGovernanceView;
    enabled: boolean;
  } | null>(null);

  const closeCredentialModal = () => {
    credentialForm.resetFields();
    setCredentialProvider(null);
  };

  const closeProviderModal = () => {
    providerForm.resetFields();
    setEditingProvider(null);
    setProviderModalOpen(false);
  };

  const openNewProviderModal = () => {
    providerForm.resetFields();
    setEditingProvider(null);
    setProviderModalOpen(true);
  };

  const openEditProviderModal = (provider: ModelProviderGovernanceView) => {
    setEditingProvider(provider);
    providerForm.setFieldsValue({
      providerCode: provider.providerCode,
      providerType: provider.providerType,
      endpointUri: provider.endpointUri,
      modelVersion: provider.modelVersion,
    });
    setProviderModalOpen(true);
  };

  const submitProvider = async (values: ProviderFormValues) => {
    try {
      await upsert.mutateAsync({
        ...values,
        expectedVersion: editingProvider?.version ?? null,
      });
      closeProviderModal();
      message.success(
        editingProvider ? "模型服务配置已更新并保持停用" : "模型服务已登记并保持停用",
      );
    } catch (error) {
      message.error(getApiErrorMessage(error, "模型服务保存失败"));
    }
  };

  const submitCredential = async (values: CredentialFormValues) => {
    if (!credentialProvider) return;
    try {
      await saveCredential.mutateAsync({
        providerCode: credentialProvider.providerCode,
        credential: values.credential,
        reason: values.reason.trim(),
        expectedVersion: credentialProvider.credentialVersion ?? null,
        confirmedHighRisk: values.confirmedHighRisk,
      });
      closeCredentialModal();
      message.success("模型密钥已加密保存；服务已停用，需重新健康检查");
    } catch (error) {
      message.error(getApiErrorMessage(error, "模型密钥保存失败"));
    }
  };

  const submitActivation = async (values: ActivationFormValues) => {
    if (!activation) return;
    const payload: ModelProviderActivationPayload = {
      providerCode: activation.provider.providerCode,
      enabled: activation.enabled,
      capabilityCode: activation.enabled ? values.capabilityCode?.trim() : undefined,
      reason: values.reason.trim(),
      expectedVersion: activation.provider.version,
      confirmedHighRisk: values.confirmedHighRisk,
    };
    try {
      await setEnabled.mutateAsync(payload);
      activationForm.resetFields();
      setActivation(null);
      message.success(activation.enabled ? "模型服务已受控启用" : "模型服务已停用");
    } catch (error) {
      message.error(getApiErrorMessage(error, "模型服务启停失败"));
    }
  };

  const closeRemovalModal = () => {
    removalForm.resetFields();
    setRemovalProvider(null);
  };

  const removeKey = async (values: RemovalFormValues) => {
    if (
      !removalProvider ||
      removalProvider.credentialVersion === null ||
      removalProvider.credentialVersion === undefined
    ) {
      return;
    }
    try {
      await removeCredential.mutateAsync({
        providerCode: removalProvider.providerCode,
        reason: values.reason.trim(),
        expectedVersion: removalProvider.credentialVersion,
        confirmedHighRisk: values.confirmedHighRisk,
      });
      closeRemovalModal();
      message.success("模型密钥已移除，服务保持停用");
    } catch (error) {
      message.error(getApiErrorMessage(error, "模型密钥移除失败"));
    }
  };

  const columns = [
    {
      title: "模型服务",
      key: "provider",
      width: 220,
      render: (_: unknown, provider: ModelProviderGovernanceView) =>
        renderProviderIdentity(provider, evidenceDetailsEnabled),
    },
    {
      title: "密钥",
      key: "credential",
      width: 240,
      render: (_: unknown, provider: ModelProviderGovernanceView) => (
        <Space direction="vertical" size={0}>
          {credentialLabel(provider)}
          {renderCredentialAudit(provider, evidenceDetailsEnabled)}
        </Space>
      ),
    },
    {
      title: "连接状态",
      key: "status",
      width: 180,
      render: (_: unknown, provider: ModelProviderGovernanceView) => {
        const meta = STATUS_META[provider.status] ?? {
          label: provider.status,
          color: "default",
        };
        return (
          <Space>
            <Tag color={meta.color}>{meta.label}</Tag>
            <Tag color={provider.enabled ? "success" : "default"}>
              {provider.enabled ? "已启用" : "已停用"}
            </Tag>
          </Space>
        );
      },
    },
    {
      title: "操作",
      key: "actions",
      width: 340,
      render: (_: unknown, provider: ModelProviderGovernanceView) =>
        canManage ? (
          <div className={styles.actionGroup}>
            <Button onClick={() => openEditProviderModal(provider)}>编辑配置</Button>
            <Button onClick={() => setCredentialProvider(provider)}>
              {provider.credentialConfigured ? "轮换密钥" : "配置密钥"}
            </Button>
            <Button
              loading={checkHealth.isPending}
              onClick={() => {
                void checkHealth
                  .mutateAsync(provider.providerCode)
                  .then(() => message.success("真实健康检查已完成"))
                  .catch((error) =>
                    message.error(getApiErrorMessage(error, "模型服务健康检查失败")),
                  );
              }}
            >
              健康检查
            </Button>
            <Button
              type={provider.enabled ? "default" : "primary"}
              disabled={!provider.enabled && provider.status !== "HEALTHY"}
              title={
                !provider.enabled && provider.status !== "HEALTHY"
                  ? "请先完成真实健康检查"
                  : undefined
              }
              onClick={() => setActivation({ provider, enabled: !provider.enabled })}
            >
              {provider.enabled ? "停用" : "启用"}
            </Button>
            {provider.credentialConfigured ? (
              <Button danger onClick={() => setRemovalProvider(provider)}>
                移除密钥
              </Button>
            ) : null}
          </div>
        ) : (
          <Text type="secondary">由医疗引擎运营员处理</Text>
        ),
    },
  ];

  let content;
  if (security.isLoading || providers.isLoading) {
    content = <PageState state="loading" title="正在读取模型服务" />;
  } else if (security.isError || providers.isError) {
    content = (
      <PageState
        state="error"
        title="模型服务读取失败"
        description={getApiErrorMessage(
          providers.error ?? security.error,
          "请重试；若持续失败，请联系信息科核查模型服务配置。失败已留痕，可在审计证据中追溯。",
        )}
        onRetry={() => void providers.refetch()}
      />
    );
  } else if (!providers.data?.items.length) {
    content = (
      <PageState
        state="empty"
        title="尚未登记模型服务"
        description={
          canManage
            ? "请先登记服务地址和模型版本，再安全配置密钥。"
            : "请联系医疗引擎运营员登记模型服务。"
        }
      />
    );
  } else {
    content = (
      <div className={styles.tablePanel} data-testid="model-provider-table-panel">
        <Table<ModelProviderGovernanceView>
          rowKey="providerCode"
          columns={columns}
          dataSource={providers.data.items}
          pagination={{
            current: providerPage,
            pageSize: providers.data.size,
            total: providers.data.total,
            showSizeChanger: false,
            onChange: setProviderPage,
          }}
          scroll={{ x: 980 }}
          tableLayout="fixed"
        />
      </div>
    );
  }

  return (
    <>
      <Card
        title="模型服务与密钥"
        extra={
          <Space wrap>
            <EvidenceDetailsToggle securityProfile={security.data} />
            {canManage ? (
              <Button type="primary" onClick={openNewProviderModal}>
                登记模型服务
              </Button>
            ) : null}
          </Space>
        }
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="info"
            showIcon
            message="密钥只加密保存，保存后不再回显"
            description="配置或轮换密钥会强制停用服务并清除最近一次健康结论；必须重新健康检查、评测并受控启用。"
          />
          {!canManage && security.data ? (
            <Alert
              type="warning"
              showIcon
              message="当前职责仅可查看"
              description="由医疗引擎运营员维护模型服务、密钥、健康检查和医学评测。"
            />
          ) : null}
          {content}
        </Space>
      </Card>

      <Modal
        title={editingProvider ? "编辑模型服务" : "登记模型服务"}
        open={providerModalOpen}
        okText="保存并保持停用"
        cancelText="取消"
        confirmLoading={upsert.isPending}
        onOk={() => providerForm.submit()}
        onCancel={closeProviderModal}
        destroyOnClose
      >
        <Form
          form={providerForm}
          layout="vertical"
          onFinish={(values) => void submitProvider(values)}
        >
          <Form.Item
            name="providerCode"
            label="稳定模型服务身份"
            rules={[{ required: true, message: "请填写稳定模型服务身份" }]}
            extra="用于发布、评测和审计追溯；默认列表仍按服务类型与模型版本展示。"
          >
            <Input
              autoComplete="off"
              disabled={Boolean(editingProvider)}
              placeholder="例如 local-qwen25 或 public-openai-compatible"
            />
          </Form.Item>
          <Form.Item
            name="providerType"
            label="服务类型"
            rules={[{ required: true, message: "请选择服务类型" }]}
          >
            <Select options={[...MODEL_PROVIDER_TYPE_OPTIONS]} />
          </Form.Item>
          <Form.Item
            name="endpointUri"
            label="服务地址"
            rules={[{ required: true, message: "请填写服务地址" }]}
          >
            <Input placeholder="https://model.example.com/v1" autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="modelVersion"
            label="模型版本"
            rules={[{ required: true, message: "请填写模型版本" }]}
          >
            <Input autoComplete="off" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="移除模型密钥"
        open={Boolean(removalProvider)}
        okText="确认移除"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={removeCredential.isPending}
        onOk={() => removalForm.submit()}
        onCancel={closeRemovalModal}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          message="本操作会清除当前凭据"
          description="模型服务将被强制停用并失去最近一次健康结论；如需恢复，必须重新配置密钥、健康检查、评测和受控启用。"
        />
        <Form
          form={removalForm}
          layout="vertical"
          initialValues={{ confirmedHighRisk: false }}
          onFinish={(values) => void removeKey(values)}
        >
          <Form.Item
            name="reason"
            label="移除原因"
            rules={[
              { required: true, message: "请填写移除原因" },
              { min: 8, message: "请填写至少 8 个字符的具体原因" },
            ]}
          >
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item
            name="confirmedHighRisk"
            valuePropName="checked"
            rules={[
              {
                validator: (_, checked) =>
                  checked ? Promise.resolve() : Promise.reject(new Error("请确认高风险影响")),
              },
            ]}
          >
            <Checkbox>我确认移除后将强制停用模型服务并要求重新验证</Checkbox>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`${credentialProvider?.credentialConfigured ? "轮换" : "配置"}模型密钥`}
        open={Boolean(credentialProvider)}
        okText="保存并停用"
        cancelText="取消"
        confirmLoading={saveCredential.isPending}
        onOk={() => credentialForm.submit()}
        onCancel={closeCredentialModal}
        destroyOnClose
      >
        <Form
          form={credentialForm}
          layout="vertical"
          initialValues={{ confirmedHighRisk: false }}
          onFinish={(values) => void submitCredential(values)}
        >
          <Form.Item
            name="credential"
            label="模型密钥"
            rules={[
              { required: true, message: "请输入模型密钥" },
              { min: 8, message: "模型密钥至少 8 个字符" },
            ]}
          >
            <Input
              type="password"
              aria-label="模型密钥"
              autoComplete="new-password"
              maxLength={2048}
            />
          </Form.Item>
          <Form.Item
            name="reason"
            label="变更原因"
            rules={[
              { required: true, message: "请填写变更原因" },
              { min: 8, message: "请填写至少 8 个字符的具体原因" },
            ]}
          >
            <Input.TextArea aria-label="变更原因" rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item
            name="confirmedHighRisk"
            valuePropName="checked"
            rules={[
              {
                validator: (_, checked) =>
                  checked ? Promise.resolve() : Promise.reject(new Error("请确认高风险影响")),
              },
            ]}
          >
            <Checkbox>我确认密钥变更将强制停用模型服务并要求重新验证</Checkbox>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={activation?.enabled ? "受控启用模型服务" : "停用模型服务"}
        open={Boolean(activation)}
        okText={activation?.enabled ? "确认启用" : "确认停用"}
        cancelText="取消"
        confirmLoading={setEnabled.isPending}
        onOk={() => activationForm.submit()}
        onCancel={() => {
          activationForm.resetFields();
          setActivation(null);
        }}
        destroyOnClose
      >
        <Form
          form={activationForm}
          layout="vertical"
          initialValues={{ confirmedHighRisk: false }}
          onFinish={(values) => void submitActivation(values)}
        >
          {activation?.enabled ? (
            <Form.Item
              name="capabilityCode"
              label="已通过评测的模型能力"
              rules={[{ required: true, message: "请选择已通过医学评测的模型能力" }]}
            >
              <Select
                aria-label="已通过评测的模型能力"
                options={[...MODEL_CAPABILITY_OPTIONS]}
                placeholder="选择与医学评测一致的能力"
              />
            </Form.Item>
          ) : null}
          <Form.Item
            name="reason"
            label="启停原因"
            rules={[
              { required: true, message: "请填写启停原因" },
              { min: 8, message: "请填写至少 8 个字符的具体原因" },
            ]}
          >
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item
            name="confirmedHighRisk"
            valuePropName="checked"
            rules={[
              {
                validator: (_, checked) =>
                  checked ? Promise.resolve() : Promise.reject(new Error("请确认高风险影响")),
              },
            ]}
          >
            <Checkbox>我确认本操作受医学评测、部署形态和审计检查约束</Checkbox>
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
