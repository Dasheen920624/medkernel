import { useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Empty,
  Input,
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
  ApartmentOutlined,
  DatabaseOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SyncOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useProjectionConsistency,
  useProjectionFacts,
  useProjectionRuntimeStatus,
  useRebuildProjection,
} from "@/shared/api/hooks";
import type {
  ProjectionFactItem,
  ProjectionSyncStatus,
  ProjectionTargetType,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";

import styles from "./Advanced.module.css";

const { Text } = Typography;

const targetOptions: Array<{ label: string; value: ProjectionTargetType }> = [
  { label: "临床图投影", value: "CLINICAL_GRAPH" },
  { label: "知识图投影", value: "KNOWLEDGE_GRAPH" },
  { label: "知识搜索投影", value: "KNOWLEDGE_SEARCH" },
];

const statusText: Record<string, string> = {
  SUCCESS: "一致",
  FAILED: "不一致",
  NOT_SYNCED: "未同步",
  UP: "可用",
  READY: "就绪",
  DEGRADED: "降级",
  MODEL_DISABLED: "模型未启用",
  NOT_CONNECTED: "未连接",
};

function statusTag(status?: ProjectionSyncStatus | string | null) {
  const value = status ?? "UNKNOWN";
  const color = value === "SUCCESS" || value === "UP" || value === "READY" ? "success" : "warning";
  return <Tag color={color}>{statusText[value] ?? value}</Tag>;
}

function targetLabel(targetType: ProjectionTargetType) {
  return targetOptions.find((option) => option.value === targetType)?.label ?? targetType;
}

function shortHash(value?: string | null) {
  if (!value) return "未返回";
  if (value.length <= 24) return value;
  return `${value.slice(0, 12)}...${value.slice(-8)}`;
}

function formatDateTime(value?: string | null) {
  if (!value) return "未返回";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function nullableText(value?: string | null) {
  return value && value.trim() ? value : "未返回";
}

export default function GraphExplore() {
  const [targetType, setTargetType] = useState<ProjectionTargetType>("CLINICAL_GRAPH");
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1);

  const runtimeQuery = useProjectionRuntimeStatus(targetType);
  const consistencyQuery = useProjectionConsistency(targetType);
  const factsQuery = useProjectionFacts({
    targetType,
    keyword,
    page,
    size: 20,
  });
  const rebuildMutation = useRebuildProjection();

  const facts = factsQuery.data?.items ?? [];
  const total = factsQuery.data?.total ?? 0;
  const runtimeStatusEnabled = targetType === "CLINICAL_GRAPH";
  const diffCount = useMemo(() => {
    const report = consistencyQuery.data;
    if (!report) return 0;
    return report.missing.length + report.extra.length + report.changed.length;
  }, [consistencyQuery.data]);

  const handleTargetChange = (next: ProjectionTargetType) => {
    setTargetType(next);
    setPage(1);
  };

  const handleSearch = (value?: string) => {
    setKeyword((value ?? keywordInput).trim());
    setPage(1);
  };

  const handleReload = async () => {
    await Promise.all([
      runtimeStatusEnabled ? runtimeQuery.refetch() : Promise.resolve(),
      consistencyQuery.refetch(),
      factsQuery.refetch(),
    ]);
  };

  const handleRebuild = async () => {
    try {
      const result = await rebuildMutation.mutateAsync(targetType);
      message.success(result.message || "投影重建请求已提交");
      await handleReload();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "投影重建失败"));
    }
  };

  const columns: TableProps<ProjectionFactItem>["columns"] = [
    {
      title: "事实键",
      dataIndex: "factKey",
      key: "factKey",
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: "类型",
      dataIndex: "factKind",
      key: "factKind",
      render: (value: string) => <Tag color={value === "EDGE" ? "purple" : "blue"}>{value}</Tag>,
    },
    {
      title: "对象",
      key: "object",
      render: (_value, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.objectType}</Text>
          <Text type="secondary">{record.objectId}</Text>
        </Space>
      ),
    },
    {
      title: "关系",
      key: "relation",
      render: (_value, record) =>
        record.factKind === "EDGE" ? (
          <Space direction="vertical" size={0}>
            <Text>{nullableText(record.subjectKey)}</Text>
            <Text type="secondary">{nullableText(record.predicate)}</Text>
            <Text>{nullableText(record.objectKey)}</Text>
          </Space>
        ) : (
          <Text type="secondary">节点</Text>
        ),
    },
    {
      title: "摘要",
      dataIndex: "contentHash",
      key: "contentHash",
      render: (value: string) => <Text code>{shortHash(value)}</Text>,
    },
    {
      title: "同步时间",
      dataIndex: "syncedAt",
      key: "syncedAt",
      render: (value: string) => <Text type="secondary">{formatDateTime(value)}</Text>,
    },
    {
      title: "traceId",
      dataIndex: "traceId",
      key: "traceId",
      render: (value: string) => nullableText(value),
    },
  ];

  const loading =
    consistencyQuery.isLoading ||
    factsQuery.isLoading ||
    (runtimeStatusEnabled && runtimeQuery.isLoading);
  const hasError =
    consistencyQuery.isError ||
    factsQuery.isError ||
    (runtimeStatusEnabled && runtimeQuery.isError);

  return (
    <PageShell
      title="图谱查询"
      description="查询关系库权威源生成的投影快照"
      extras={
        <Button icon={<ReloadOutlined />} onClick={handleReload} loading={loading}>
          刷新
        </Button>
      }
      primary={
        <Button
          type="primary"
          icon={<SyncOutlined />}
          onClick={handleRebuild}
          loading={rebuildMutation.isPending}
        >
          重建投影
        </Button>
      }
    >
      <Space direction="vertical" size="large" className={styles.fullWidth}>
        <Alert
          type={hasError ? "error" : "info"}
          showIcon
          message={
            hasError ? "投影状态读取失败" : (consistencyQuery.data?.message ?? "正在读取投影状态")
          }
          description={
            (runtimeStatusEnabled ? runtimeQuery.data?.message : null) ??
            "页面只展示关系库权威源派生的投影快照；外部图谱或模型未连接时不伪造查询结果。"
          }
        />

        <Card>
          <Space wrap className={styles.toolbar}>
            <Space wrap>
              <Select
                aria-label="投影目标"
                value={targetType}
                options={targetOptions}
                onChange={handleTargetChange}
                className={styles.selectWide}
              />
              <Input.Search
                allowClear
                enterButton="检索"
                prefix={<SearchOutlined />}
                placeholder="输入 factKey、对象 ID、traceId 或关系谓词"
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                onSearch={handleSearch}
                className={styles.searchWide}
              />
            </Space>
            <Text type="secondary">当前目标：{targetLabel(targetType)}</Text>
          </Space>
        </Card>

        <div className={styles.statsGrid}>
          <Card>
            <Statistic
              title="投影目标"
              value={targetLabel(targetType)}
              prefix={<ApartmentOutlined />}
            />
          </Card>
          <Card>
            <Statistic
              title="源事实"
              value={consistencyQuery.data?.sourceCount ?? 0}
              prefix={<DatabaseOutlined />}
            />
          </Card>
          <Card>
            <Statistic
              title="投影快照"
              value={
                consistencyQuery.data?.projectionCount ?? runtimeQuery.data?.snapshotCount ?? 0
              }
              prefix={<SafetyCertificateOutlined />}
            />
          </Card>
          <Card>
            <Statistic title="差异项" value={diffCount} prefix={<WarningOutlined />} />
          </Card>
        </div>

        <Card title="一致性状态" extra={statusTag(consistencyQuery.data?.status)}>
          <Space direction="vertical" size="small" className={styles.fullWidth}>
            <Text>{consistencyQuery.data?.message ?? "正在读取一致性报告"}</Text>
            <Space wrap>
              <Tag>源摘要：{shortHash(consistencyQuery.data?.sourceHash)}</Tag>
              <Tag>投影摘要：{shortHash(consistencyQuery.data?.projectionHash)}</Tag>
              {targetType === "CLINICAL_GRAPH" && (
                <Tag>运行状态：{runtimeQuery.data?.clinicalProjectionStatus ?? "未返回"}</Tag>
              )}
            </Space>
            {diffCount > 0 && (
              <Alert
                type="warning"
                showIcon
                message={`缺失 ${consistencyQuery.data?.missing.length ?? 0} / 多余 ${
                  consistencyQuery.data?.extra.length ?? 0
                } / 变更 ${consistencyQuery.data?.changed.length ?? 0}`}
              />
            )}
          </Space>
        </Card>

        <Card title="投影事实">
          <div data-testid="projection-facts-table">
            <Table
              rowKey="factKey"
              loading={factsQuery.isLoading}
              columns={columns}
              dataSource={facts}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="当前筛选范围暂无真实投影事实"
                  />
                ),
              }}
              pagination={{
                current: page,
                pageSize: factsQuery.data?.size ?? 20,
                total,
                showSizeChanger: false,
                showTotal: (count) => `共 ${count} 条投影事实`,
                onChange: (nextPage) => setPage(nextPage),
              }}
            />
          </div>
        </Card>
      </Space>
    </PageShell>
  );
}
