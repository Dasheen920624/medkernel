import { Alert, Button, Card, Form, Select, Space, Typography, message } from "antd";
import { useMemo } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import { useRunModelEvaluation, useSecurityProfile } from "@/shared/api/hooks";
import { useModelProviders } from "@/shared/api/modelProviders";
import { MODEL_CAPABILITY_OPTIONS } from "@/shared/config/modelProduction";
import { PageState } from "@/shared/ui/PageState";

const { Text } = Typography;

type EvaluationFormValues = {
  providerCode: string;
  capabilityCode: string;
};

export default function MedicalEvaluationPanel() {
  const [form] = Form.useForm<EvaluationFormValues>();
  const security = useSecurityProfile();
  const canEvaluate =
    security.data?.permissions?.some((permission) => permission.code === "llm.eval.manage") ??
    false;
  const providers = useModelProviders({ page: 1, size: 50 }, Boolean(security.data));
  const runEvaluation = useRunModelEvaluation();
  const healthyProviders = useMemo(
    () => (providers.data?.items ?? []).filter((provider) => provider.status === "HEALTHY"),
    [providers.data?.items],
  );
  const providerOptions = healthyProviders.map((provider) => ({
    value: provider.providerCode,
    label: `${provider.providerCode} · ${provider.modelVersion}`,
  }));

  const submit = async (values: EvaluationFormValues) => {
    const provider = healthyProviders.find(
      (candidate) => candidate.providerCode === values.providerCode,
    );
    if (!provider) {
      message.error("所选模型服务未通过当前健康检查");
      return;
    }
    try {
      await runEvaluation.mutateAsync({
        providerCode: provider.providerCode,
        modelVersion: provider.modelVersion,
        capabilityCode: values.capabilityCode,
      });
      message.success("当前交付文件医学评测已完成，结果和逐例证据已留痕");
    } catch (error) {
      message.error(getApiErrorMessage(error, "医学评测运行失败"));
    }
  };

  let formContent;
  if (security.isLoading || providers.isLoading) {
    formContent = <PageState state="loading" title="正在读取评测条件" />;
  } else if (security.isError || providers.isError) {
    formContent = (
      <PageState
        state="error"
        title="医学评测条件读取失败"
        description={getApiErrorMessage(
          providers.error ?? security.error,
          "请重试，或凭追踪号联系系统管理员。",
        )}
      />
    );
  } else if (!canEvaluate) {
    formContent = (
      <Alert
        type="warning"
        showIcon
        message="当前职责仅可查看评测进度"
        description="由医疗引擎运营人员运行当前交付内容评测；通过结果直接作为模型放行证据。"
      />
    );
  } else if (healthyProviders.length === 0) {
    formContent = (
      <PageState
        state="empty"
        title="暂无可评测的健康模型服务"
        description="请先由医疗引擎运营员配置密钥并完成真实健康检查。"
      />
    );
  } else {
    formContent = (
      <Form form={form} layout="vertical" onFinish={(values) => void submit(values)}>
        <Space align="start" wrap>
          <Form.Item
            name="providerCode"
            label="模型服务"
            rules={[{ required: true, message: "请选择模型服务" }]}
          >
            <Select
              aria-label="模型服务"
              options={providerOptions}
              className="mk-select-medium"
              placeholder="选择已通过健康检查的服务"
            />
          </Form.Item>
          <Form.Item
            name="capabilityCode"
            label="医学能力"
            rules={[{ required: true, message: "请选择医学能力" }]}
          >
            <Select
              aria-label="医学能力"
              options={[...MODEL_CAPABILITY_OPTIONS]}
              className="mk-select-medium"
              placeholder="选择本次评测能力"
            />
          </Form.Item>
          <Form.Item label=" ">
            <Button type="primary" htmlType="submit" loading={runEvaluation.isPending}>
              运行当前交付文件评测
            </Button>
          </Form.Item>
        </Space>
      </Form>
    );
  }

  return (
    <Space direction="vertical" size="large" className="mk-full-width">
      <Card title="医学评测">
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="info"
            showIcon
            message="评测绑定当前交付文件、模型服务、模型版本和医学能力"
            description="历史交付文件的通过记录只保留审计，不能替代当前交付文件评测。"
          />
          {formContent}
          <Text type="secondary">
            评测通过后直接作为当前交付文件的模型放行证据；模型输出仍只进入候选治理链，不会自动发布或开立医嘱。
          </Text>
        </Space>
      </Card>
    </Space>
  );
}
