import { useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import type { TableProps } from "antd";
import {
  AuditOutlined,
  CheckCircleOutlined,
  ExportOutlined,
  FileProtectOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useAuditEvents,
  useEvidences,
  useExportEvidences,
  useVerifyEvidence,
} from "@/shared/api/hooks";
import type {
  AuditEventRow,
  EvidenceExportResult,
  EvidenceSnapshot,
  EvidenceVerifyResult,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";

import styles from "./Advanced.module.css";

const { Text } = Typography;

const evidenceTypeOptions = [
  { value: "KNOWLEDGE_SOURCE", label: "指南文献" },
  { value: "RULE_DEFINITION", label: "规则与路径" },
  { value: "RELEASE", label: "配置包发布" },
  { value: "CLINICAL_CLOCK", label: "就诊事实" },
  { value: "FEEDBACK", label: "医师交互" },
  { value: "RECTIFICATION", label: "质控整改" },
];

function formatDateTime(value?: string) {
  if (!value) return "未返回";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function shortHash(value?: string) {
  if (!value) return "未返回";
  if (value.length <= 24) return value;
  return `${value.slice(0, 16)}...${value.slice(-8)}`;
}

function AuditEventList({ events }: { events: AuditEventRow[] }) {
  if (events.length === 0) {
    return <Empty description="暂无真实审计事件" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  return (
    <Space direction="vertical" size="small" className={styles.fullWidth}>
      {events.slice(0, 8).map((event) => (
        <Card key={event.eventId} size="small">
          <Space direction="vertical" size={4} className={styles.fullWidth}>
            <Space className={styles.auditHeader}>
              <Text strong>{event.summary}</Text>
              <Tag color={event.status === "FAILED" ? "red" : "blue"}>{event.status}</Tag>
            </Space>
            <Text type="secondary" className={styles.smallText}>
              {event.resourceType} / {event.resourceId}
            </Text>
            <Text type="secondary" className={styles.smallText}>
              {formatDateTime(event.occurredAt)}
            </Text>
          </Space>
        </Card>
      ))}
    </Space>
  );
}

export default function Provenance() {
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [evidenceType, setEvidenceType] = useState<string | undefined>();
  const [selectedEvidence, setSelectedEvidence] = useState<EvidenceSnapshot | null>(null);
  const [verifyResult, setVerifyResult] = useState<EvidenceVerifyResult | null>(null);
  const [exportResult, setExportResult] = useState<EvidenceExportResult | null>(null);

  const evidencesQuery = useEvidences({
    keyword: keyword || undefined,
    evidenceType,
    page: 1,
    size: 20,
  });
  const auditQuery = useAuditEvents();
  const verifyMutation = useVerifyEvidence();
  const exportMutation = useExportEvidences();

  const evidences = useMemo(() => evidencesQuery.data?.items ?? [], [evidencesQuery.data?.items]);
  const metrics = useMemo(() => {
    const total = evidencesQuery.data?.total ?? evidences.length;
    const valid = evidences.filter((item) => item.isValid).length;
    const invalid = evidences.filter((item) => !item.isValid).length;
    return {
      total,
      valid,
      invalid,
      auditEvents: auditQuery.data?.length ?? 0,
    };
  }, [auditQuery.data?.length, evidences, evidencesQuery.data?.total]);

  const handleSearch = (value?: string) => {
    const nextKeyword = (value ?? keywordInput).trim();
    setKeyword(nextKeyword);
  };

  const handleVerify = async (record: EvidenceSnapshot) => {
    try {
      const result = await verifyMutation.mutateAsync(record.evidenceId);
      setSelectedEvidence(record);
      setVerifyResult(result);
      if (result.isValid) {
        message.success("证据快照验签通过");
      } else {
        message.error("证据快照验签失败，请立即核查原始数据");
      }
      await auditQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "证据快照验签失败"));
    }
  };

  const handleExport = async () => {
    if (metrics.total === 0) {
      message.warning("当前筛选范围无真实证据快照，不生成导出指纹");
      return;
    }

    try {
      const result = await exportMutation.mutateAsync(evidenceType);
      setExportResult(result);
      message.success("证据归档指纹已由后端生成");
      await auditQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "证据导出失败"));
    }
  };

  const columns: TableProps<EvidenceSnapshot>["columns"] = [
    {
      title: "证据 ID",
      dataIndex: "evidenceId",
      key: "evidenceId",
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: "traceId",
      dataIndex: "traceId",
      key: "traceId",
      render: (value: string) => value || "未指定",
    },
    {
      title: "类型",
      dataIndex: "evidenceType",
      key: "evidenceType",
      render: (value: string) => <Tag color="blue">{value}</Tag>,
    },
    {
      title: "摘要",
      dataIndex: "evidenceSummary",
      key: "evidenceSummary",
      ellipsis: true,
    },
    {
      title: "指纹",
      dataIndex: "payloadHash",
      key: "payloadHash",
      render: (value: string) => <Text code>{shortHash(value)}</Text>,
    },
    {
      title: "状态",
      dataIndex: "isValid",
      key: "isValid",
      render: (value: boolean) =>
        value ? <Tag color="success">验签有效</Tag> : <Tag color="error">验签异常</Tag>,
    },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      key: "createdAt",
      render: (value: string) => <Text type="secondary">{formatDateTime(value)}</Text>,
    },
    {
      title: "操作",
      key: "action",
      render: (_value: unknown, record) => (
        <Button
          size="small"
          icon={<SafetyCertificateOutlined />}
          loading={verifyMutation.isPending && selectedEvidence?.evidenceId === record.evidenceId}
          onClick={() => handleVerify(record)}
        >
          后端验签
        </Button>
      ),
    },
  ];

  return (
    <PageShell
      title="来源与临床证据追溯"
      description="按 traceId、证据类型或摘要检索真实证据快照；无后端证据时只显示空态，不构造本地证据链。"
    >
      <Space direction="vertical" size="large" className={styles.fullWidth}>
        <div className={styles.statsGrid}>
          <Card>
            <Statistic
              title="真实证据快照"
              value={metrics.total}
              prefix={<FileProtectOutlined />}
            />
          </Card>
          <Card>
            <Statistic title="当前页有效" value={metrics.valid} prefix={<CheckCircleOutlined />} />
          </Card>
          <Card>
            <Statistic title="当前页异常" value={metrics.invalid} prefix={<WarningOutlined />} />
          </Card>
          <Card>
            <Statistic title="审计事件" value={metrics.auditEvents} prefix={<AuditOutlined />} />
          </Card>
        </div>

        <Card>
          <Space wrap className={styles.toolbar}>
            <Space wrap>
              <Input.Search
                allowClear
                enterButton="检索"
                prefix={<SearchOutlined />}
                placeholder="输入 traceId、证据 ID 或摘要关键词"
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                onSearch={handleSearch}
                className={styles.searchWide}
              />
              <Select
                allowClear
                placeholder="证据类型"
                value={evidenceType}
                onChange={setEvidenceType}
                options={evidenceTypeOptions}
                className={styles.selectWide}
              />
            </Space>
            <Space>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => {
                  evidencesQuery.refetch();
                  auditQuery.refetch();
                }}
              >
                刷新
              </Button>
              <Button
                type="primary"
                icon={<ExportOutlined />}
                loading={exportMutation.isPending}
                disabled={metrics.total === 0}
                onClick={handleExport}
              >
                生成归档指纹
              </Button>
            </Space>
          </Space>
        </Card>

        {evidencesQuery.isError && (
          <Alert
            type="error"
            showIcon
            message="证据快照接口读取失败"
            description="请检查登录权限、租户上下文或证据服务状态。"
          />
        )}

        <div className={styles.contentGrid}>
          <Card title="真实证据快照">
            <Table
              columns={columns}
              dataSource={evidences}
              rowKey="evidenceId"
              loading={evidencesQuery.isLoading}
              locale={{ emptyText: <Empty description="暂无真实证据快照" /> }}
              pagination={{
                total: metrics.total,
                pageSize: 20,
                showSizeChanger: false,
              }}
            />
          </Card>

          <Card title="真实审计事件">
            {auditQuery.isLoading ? (
              <Alert type="info" showIcon message="正在读取审计事件" />
            ) : (
              <AuditEventList events={auditQuery.data ?? []} />
            )}
          </Card>
        </div>
      </Space>

      <Modal
        title="后端证据验签结果"
        open={!!verifyResult}
        onCancel={() => setVerifyResult(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setVerifyResult(null)}>
            关闭
          </Button>,
        ]}
      >
        {verifyResult && (
          <Space direction="vertical" size="middle" className={styles.fullWidth}>
            <Alert
              type={verifyResult.isValid ? "success" : "error"}
              showIcon
              message={verifyResult.isValid ? "证据快照验签通过" : "证据快照验签失败"}
            />
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="证据 ID">{verifyResult.evidenceId}</Descriptions.Item>
              <Descriptions.Item label="存储指纹">
                <Text code copyable>
                  {verifyResult.storedHash}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="计算指纹">
                <Text code copyable>
                  {verifyResult.calculatedHash}
                </Text>
              </Descriptions.Item>
            </Descriptions>
          </Space>
        )}
      </Modal>

      <Modal
        title="证据归档指纹"
        open={!!exportResult}
        onCancel={() => setExportResult(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setExportResult(null)}>
            关闭
          </Button>,
        ]}
      >
        {exportResult && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="导出状态">{exportResult.status}</Descriptions.Item>
            <Descriptions.Item label="归档指纹">
              <Text code copyable>
                {exportResult.archiveHash}
              </Text>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </PageShell>
  );
}
