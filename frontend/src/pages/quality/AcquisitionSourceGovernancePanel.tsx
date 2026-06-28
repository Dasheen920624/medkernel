import { CheckCircleOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useDisableKnowledgeAcquisitionSource,
  useEnableKnowledgeAcquisitionSource,
  useKnowledgeAcquisitionSources,
  useSaveKnowledgeAcquisitionSourceDraft,
  type KnowledgeAcquisitionSource,
  type KnowledgeAcquisitionSourceDraftRequest,
} from "@/shared/api/hooks";
import {
  KNOWLEDGE_ACQUISITION_AUTHORITY_OPTIONS,
  KNOWLEDGE_ACQUISITION_LICENSE_OPTIONS,
  KNOWLEDGE_ACQUISITION_ROBOTS_OPTIONS,
  KNOWLEDGE_ACQUISITION_SOURCE_TYPE_OPTIONS,
} from "@/shared/config/knowledgeAcquisition";
import { PageState } from "@/shared/ui/PageState";

const { Text } = Typography;
const PAGE_SIZE = 20;

interface AcquisitionSourceGovernancePanelProps {
  canWrite: boolean;
  evidenceDetailsEnabled?: boolean;
}

type SourceDraftForm = Omit<
  KnowledgeAcquisitionSourceDraftRequest,
  "generationPlan" | "scheduleIntervalMinutes" | "defaultFormat"
> & { sourceCode: string };
type PendingAction = { type: "enable" | "disable"; source: KnowledgeAcquisitionSource };

function sourceStatus(source: KnowledgeAcquisitionSource) {
  if (source.enabledFlag === "Y") {
    return <Tag color="success">已启用</Tag>;
  }
  return <Tag>已停用</Tag>;
}

function optionLabel(options: Array<{ value: string; label: string }>, value: string) {
  return options.find((option) => option.value === value)?.label ?? "未知裁决";
}

function formatDateTime(value?: string | null) {
  if (!value) return "未记录";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "时间格式异常"
    : date.toLocaleString("zh-CN", { hour12: false });
}

function sourceIdentityText(source: KnowledgeAcquisitionSource, evidenceDetailsEnabled: boolean) {
  return evidenceDetailsEnabled ? source.sourceCode : "来源身份已登记";
}

function sourceEndpointText(source: KnowledgeAcquisitionSource, evidenceDetailsEnabled: boolean) {
  return evidenceDetailsEnabled ? source.baseUrl : "入口地址已登记";
}

function maintenanceOperatorText(
  source: KnowledgeAcquisitionSource,
  evidenceDetailsEnabled: boolean,
) {
  if (!source.updatedBy) return "未记录";
  return evidenceDetailsEnabled ? source.updatedBy : "维护人已记录";
}

function toDraftForm(source: KnowledgeAcquisitionSource): SourceDraftForm {
  return {
    sourceCode: source.sourceCode,
    domain: source.domain,
    baseUrl: source.baseUrl,
    sourceType: source.sourceType,
    authorityLevel: source.authorityLevel,
    authorityBasis: source.authorityBasis,
    title: source.title,
    publisher: source.publisher,
    license: source.license,
    licensePolicy: source.licensePolicy as SourceDraftForm["licensePolicy"],
    robotsPolicy: source.robotsPolicy as SourceDraftForm["robotsPolicy"],
    scheduleEnabled: false,
  };
}

export default function AcquisitionSourceGovernancePanel({
  canWrite,
  evidenceDetailsEnabled = false,
}: AcquisitionSourceGovernancePanelProps) {
  const { message } = AntdApp.useApp();
  const [page, setPage] = useState(1);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingSourceCode, setEditingSourceCode] = useState<string>();
  const [pendingAction, setPendingAction] = useState<PendingAction>();
  const [form] = Form.useForm<SourceDraftForm>();
  const sourcesQuery = useKnowledgeAcquisitionSources({ page, size: PAGE_SIZE });
  const saveDraft = useSaveKnowledgeAcquisitionSourceDraft();
  const enableSource = useEnableKnowledgeAcquisitionSource();
  const disableSource = useDisableKnowledgeAcquisitionSource();

  const openDraft = (source?: KnowledgeAcquisitionSource) => {
    setEditingSourceCode(source?.sourceCode);
    form.resetFields();
    if (source) form.setFieldsValue(toDraftForm(source));
    setDrawerOpen(true);
  };

  const submitDraft = async (values: SourceDraftForm) => {
    const { sourceCode, ...request } = values;
    try {
      await saveDraft.mutateAsync({
        sourceCode: sourceCode.trim().toUpperCase(),
        request: { ...request, scheduleEnabled: false },
      });
      message.success("来源已保存为停用配置，核对无误后可直接启用");
      setDrawerOpen(false);
    } catch (error) {
      message.error(getApiErrorMessage(error, "来源草稿保存失败"));
    }
  };

  const confirmAction = async () => {
    if (!pendingAction) return;
    try {
      if (pendingAction.type === "enable") {
        await enableSource.mutateAsync(pendingAction.source.sourceCode);
        message.success("来源已启用");
      } else {
        await disableSource.mutateAsync(pendingAction.source.sourceCode);
        message.success("来源及自动调度已停用");
      }
      setPendingAction(undefined);
    } catch (error) {
      message.error(
        getApiErrorMessage(
          error,
          pendingAction.type === "enable" ? "来源启用失败" : "来源停用失败",
        ),
      );
    }
  };

  const columns: ColumnsType<KnowledgeAcquisitionSource> = [
    {
      title: "来源",
      key: "source",
      render: (_, source) => (
        <Space direction="vertical" size={0}>
          <Text strong>{source.title}</Text>
          <Text type="secondary">{source.publisher}</Text>
          <Text type="secondary">{sourceIdentityText(source, evidenceDetailsEnabled)}</Text>
        </Space>
      ),
    },
    {
      title: "域名与入口",
      key: "endpoint",
      width: 320,
      render: (_, source) => (
        <Space direction="vertical" size={0}>
          <Text>{source.domain}</Text>
          <Text type="secondary" ellipsis={{ tooltip: source.baseUrl }}>
            {sourceEndpointText(source, evidenceDetailsEnabled)}
          </Text>
        </Space>
      ),
    },
    {
      title: "治理裁决",
      key: "governance",
      render: (_, source) => (
        <Space direction="vertical" size={0}>
          <Text>{optionLabel(KNOWLEDGE_ACQUISITION_LICENSE_OPTIONS, source.licensePolicy)}</Text>
          <Text type="secondary">
            {optionLabel(KNOWLEDGE_ACQUISITION_ROBOTS_OPTIONS, source.robotsPolicy)}
          </Text>
        </Space>
      ),
    },
    { title: "状态", key: "status", render: (_, source) => sourceStatus(source) },
    {
      title: "维护记录",
      key: "maintenance",
      render: (_, source) =>
        source.updatedBy ? (
          <Text>
            {maintenanceOperatorText(source, evidenceDetailsEnabled)}
            <br />
            <Text type="secondary">{formatDateTime(source.updatedAt)}</Text>
          </Text>
        ) : (
          <Text type="secondary">未记录</Text>
        ),
    },
    {
      title: "操作",
      key: "actions",
      render: (_, source) => (
        <Space size="small" wrap>
          {canWrite ? (
            <Button
              type="link"
              icon={<EditOutlined />}
              aria-label={`编辑来源草稿 ${source.title}`}
              onClick={() => openDraft(source)}
            >
              编辑
            </Button>
          ) : null}
          {canWrite && source.enabledFlag !== "Y" ? (
            <Button
              type="link"
              icon={<CheckCircleOutlined />}
              aria-label={`启用来源 ${source.title}`}
              onClick={() => setPendingAction({ type: "enable", source })}
            >
              启用
            </Button>
          ) : null}
          {canWrite && source.enabledFlag === "Y" ? (
            <Button
              type="link"
              danger
              aria-label={`停用来源 ${source.title}`}
              onClick={() => setPendingAction({ type: "disable", source })}
            >
              停用
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  let content;
  if (sourcesQuery.isLoading) {
    content = <PageState state="loading" title="正在读取来源允许清单" />;
  } else if (sourcesQuery.isError) {
    content = (
      <PageState
        state="error"
        title="来源允许清单读取失败"
        description={getApiErrorMessage(sourcesQuery.error, "无法读取来源允许清单")}
        onRetry={() => void sourcesQuery.refetch()}
      />
    );
  } else if ((sourcesQuery.data?.items.length ?? 0) === 0) {
    content = (
      <PageState
        state="empty"
        title="暂无受治理的公域来源"
        description="先登记来源配置，核对许可与 robots 策略后直接启用。"
      />
    );
  } else {
    content = (
      <Table
        rowKey="id"
        columns={columns}
        dataSource={sourcesQuery.data?.items ?? []}
        pagination={{
          current: sourcesQuery.data?.page ?? page,
          pageSize: PAGE_SIZE,
          total: sourcesQuery.data?.total ?? 0,
          showSizeChanger: false,
          onChange: setPage,
        }}
        scroll={{ x: 1080 }}
        size="small"
      />
    );
  }

  return (
    <Card
      title="公域来源治理"
      extra={
        <Space>
          <Button
            aria-label="刷新来源允许清单"
            icon={<ReloadOutlined />}
            onClick={() => void sourcesQuery.refetch()}
          >
            刷新
          </Button>
          {canWrite ? (
            <Button aria-label="登记来源草稿" icon={<PlusOutlined />} onClick={() => openDraft()}>
              登记来源草稿
            </Button>
          ) : null}
        </Space>
      }
    >
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Alert
          type="info"
          showIcon
          message="来源配置、安全校验、运行获取"
          description="保存后默认停用；启用前系统校验域名、HTTPS、许可与 robots 边界，所有变更保留维护记录。"
        />
        {content}
      </Space>

      <Drawer
        title={editingSourceCode ? "编辑公域来源草稿" : "登记公域来源草稿"}
        open={drawerOpen}
        width={560}
        destroyOnClose
        onClose={() => setDrawerOpen(false)}
        extra={
          <Button loading={saveDraft.isPending} onClick={() => void form.submit()}>
            保存停用草稿
          </Button>
        }
      >
        <Alert
          type="warning"
          showIcon
          message="任何编辑都会停用来源"
          description="本表单不直接启用自动调度；请先核对来源、许可与 robots 信息。"
          className="mk-card-gap-bottom"
        />
        <Form form={form} layout="vertical" onFinish={(values) => void submitDraft(values)}>
          <Form.Item
            name="sourceCode"
            label="稳定来源身份"
            rules={[
              { required: true, message: "请输入稳定来源身份" },
              {
                pattern: /^[A-Z0-9][A-Z0-9._-]{1,127}$/,
                message: "仅允许大写字母、数字、点、下划线和连字符",
              },
            ]}
          >
            <Input disabled={Boolean(editingSourceCode)} placeholder="例如 NHC-GUIDELINE" />
          </Form.Item>
          <Form.Item name="title" label="来源标题" rules={[{ required: true }]}>
            <Input maxLength={512} />
          </Form.Item>
          <Form.Item name="publisher" label="发布机构" rules={[{ required: true }]}>
            <Input maxLength={256} />
          </Form.Item>
          <Form.Item name="domain" label="声明域名" rules={[{ required: true }]}>
            <Input placeholder="www.example.gov.cn" maxLength={255} />
          </Form.Item>
          <Form.Item
            name="baseUrl"
            label="HTTPS 基础地址"
            rules={[{ required: true }, { type: "url", message: "请输入完整 HTTPS 地址" }]}
          >
            <Input placeholder="https://www.example.gov.cn/path/document" maxLength={512} />
          </Form.Item>
          <Form.Item name="sourceType" label="来源类型" rules={[{ required: true }]}>
            <Select options={KNOWLEDGE_ACQUISITION_SOURCE_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="authorityLevel" label="权威等级" rules={[{ required: true }]}>
            <Select options={KNOWLEDGE_ACQUISITION_AUTHORITY_OPTIONS} />
          </Form.Item>
          <Form.Item name="authorityBasis" label="权威依据" rules={[{ required: true }]}>
            <Input.TextArea rows={2} maxLength={512} showCount />
          </Form.Item>
          <Form.Item name="license" label="许可依据" rules={[{ required: true }]}>
            <Input.TextArea rows={2} maxLength={512} showCount />
          </Form.Item>
          <Form.Item name="licensePolicy" label="许可裁决" rules={[{ required: true }]}>
            <Select options={KNOWLEDGE_ACQUISITION_LICENSE_OPTIONS} placeholder="必须人工选择" />
          </Form.Item>
          <Form.Item name="robotsPolicy" label="robots 策略" rules={[{ required: true }]}>
            <Select options={KNOWLEDGE_ACQUISITION_ROBOTS_OPTIONS} placeholder="必须人工选择" />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title={pendingAction?.type === "enable" ? "确认启用来源？" : "确认停用来源？"}
        open={Boolean(pendingAction)}
        okText={pendingAction?.type === "enable" ? "确认启用" : "确认停用"}
        okButtonProps={{
          danger: pendingAction?.type === "disable",
          loading: enableSource.isPending || disableSource.isPending,
        }}
        cancelText="取消"
        onOk={() => void confirmAction()}
        onCancel={() => setPendingAction(undefined)}
      >
        <Space direction="vertical" size={0}>
          <Text>
            {pendingAction?.type === "enable"
              ? `将启用「${pendingAction.source.title}」。系统会校验来源配置、许可与 robots 边界。`
              : `将停用「${pendingAction?.source.title ?? "该来源"}」及其自动调度，历史维护记录仍保留。`}
          </Text>
          {pendingAction && evidenceDetailsEnabled ? (
            <Text type="secondary">稳定来源身份：{pendingAction.source.sourceCode}</Text>
          ) : null}
        </Space>
      </Modal>
    </Card>
  );
}
