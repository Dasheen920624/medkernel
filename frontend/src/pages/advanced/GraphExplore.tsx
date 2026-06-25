import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Input,
  List,
  Popconfirm,
  Result,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from "antd";
import type { TableProps } from "antd";
import { ReloadOutlined, SearchOutlined, SyncOutlined } from "@ant-design/icons";

import {
  useProjectionConsistency,
  useProjectionFacts,
  useProjectionRuntimeStatus,
  useRebuildProjection,
  useSecurityProfile,
} from "@/shared/api/hooks";
import type {
  ProjectionDiffItem,
  ProjectionFactItem,
  ProjectionSyncStatus,
  ProjectionTargetType,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { PageShell } from "@/shared/ui/PageShell";
import { ProjectionGraphCanvas } from "./ProjectionGraphCanvas";
import { projectionObjectLabel, projectionPredicateLabel } from "./projectionGraph";

import styles from "./GraphExplore.module.css";

const { Text, Title } = Typography;

const targetOptions: Array<{ label: string; value: ProjectionTargetType }> = [
  { label: "临床关系投影", value: "CLINICAL_GRAPH" },
  { label: "知识关系投影", value: "KNOWLEDGE_GRAPH" },
  { label: "知识检索投影", value: "KNOWLEDGE_SEARCH" },
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

function statusTag(status?: ProjectionSyncStatus | string | null) {
  const value = status ?? "UNKNOWN";
  const color = value === "SUCCESS" || value === "UP" || value === "READY" ? "success" : "warning";
  return <Tag color={color}>{statusText[value] ?? customerEnumLabel(value)}</Tag>;
}

function diffPanel(title: string, items: ProjectionDiffItem[]) {
  return (
    <section className={styles.diffPanel}>
      <div className={styles.diffHeader}>
        <Text strong>{title}</Text>
        <Tag>{items.length}</Tag>
      </div>
      {items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无差异" />
      ) : (
        <List
          size="small"
          dataSource={items}
          renderItem={(item) => (
            <List.Item>
              <Text code className={styles.diffKey}>
                {item.factKey}
              </Text>
            </List.Item>
          )}
        />
      )}
    </section>
  );
}

export default function GraphExplore() {
  const securityQuery = useSecurityProfile();
  const permissionCodes = useMemo(
    () => new Set(securityQuery.data?.permissions.map((permission) => permission.code) ?? []),
    [securityQuery.data],
  );
  const canRead = permissionCodes.has("projection.read");
  const canRebuild = permissionCodes.has("projection.rebuild");

  const [targetType, setTargetType] = useState<ProjectionTargetType>("CLINICAL_GRAPH");
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1);
  const [selectedNodeKey, setSelectedNodeKey] = useState<string | null>(null);
  const [selectedFact, setSelectedFact] = useState<ProjectionFactItem | null>(null);

  const runtimeStatusEnabled = canRead && targetType === "CLINICAL_GRAPH";
  const runtimeQuery = useProjectionRuntimeStatus(targetType, runtimeStatusEnabled);
  const consistencyQuery = useProjectionConsistency(targetType, canRead);
  const factsQuery = useProjectionFacts(
    {
      targetType,
      keyword,
      page,
      size: 40,
    },
    canRead,
  );
  const rebuildMutation = useRebuildProjection();

  const facts = factsQuery.data?.items ?? [];
  const total = factsQuery.data?.total ?? 0;
  const report = consistencyQuery.data;
  const diffCount =
    (report?.missing.length ?? 0) + (report?.extra.length ?? 0) + (report?.changed.length ?? 0);
  const partial =
    Boolean(factsQuery.data) &&
    (consistencyQuery.isError || (runtimeStatusEnabled && runtimeQuery.isError));
  const loading =
    securityQuery.isLoading ||
    factsQuery.isLoading ||
    consistencyQuery.isLoading ||
    (runtimeStatusEnabled && runtimeQuery.isLoading);

  useEffect(() => {
    setSelectedNodeKey(null);
    setSelectedFact(null);
  }, [factsQuery.data]);

  const handleTargetChange = (next: ProjectionTargetType) => {
    setTargetType(next);
    setPage(1);
  };

  const handleSearch = () => {
    setKeyword(keywordInput.trim());
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
      message.success(result.message || "投影重建完成");
      await handleReload();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "投影重建失败"));
    }
  };

  const columns: TableProps<ProjectionFactItem>["columns"] = [
    {
      title: "对象",
      key: "object",
      render: (_value, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{projectionObjectLabel(record.objectType)}</Text>
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
            <Text>{record.subjectKey}</Text>
            <Text type="secondary">{projectionPredicateLabel(record.predicate)}</Text>
            <Text>{record.objectKey}</Text>
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
      title: "追踪号",
      dataIndex: "traceId",
      key: "traceId",
      render: (value?: string | null) => value || "未返回",
    },
  ];

  if (securityQuery.isLoading) {
    return (
      <PageShell title="图谱查询" description="关系库权威源的可重建投影">
        <Spin aria-label="正在核验访问权限" />
      </PageShell>
    );
  }

  if (securityQuery.isError || !canRead) {
    return (
      <PageShell title="图谱查询" description="关系库权威源的可重建投影">
        <Result status="403" title="无权查看图谱投影" subTitle="需要图谱投影读取权限。" />
      </PageShell>
    );
  }

  if (factsQuery.isError) {
    return (
      <PageShell title="图谱查询" description="关系库权威源的可重建投影">
        <Result
          status="error"
          title="投影事实读取失败"
          subTitle="未使用本地演示数据替代真实结果。"
          extra={
            <Button type="primary" onClick={() => factsQuery.refetch()}>
              重新读取
            </Button>
          }
        />
      </PageShell>
    );
  }

  const graphTab = (
    <div className={styles.workspaceGrid}>
      <section className={styles.graphPane}>
        {loading && facts.length === 0 ? (
          <Spin aria-label="正在读取投影关系" />
        ) : (
          <ProjectionGraphCanvas
            facts={facts}
            selectedKey={selectedNodeKey}
            onSelect={(nodeKey, fact) => {
              setSelectedNodeKey(nodeKey);
              setSelectedFact(fact ?? null);
            }}
          />
        )}
      </section>
      <aside className={styles.detailPane}>
        <Title level={5} className={styles.detailTitle}>
          选中对象
        </Title>
        {selectedFact ? (
          <Descriptions column={1} size="small">
            <Descriptions.Item label="类型">
              {projectionObjectLabel(selectedFact.objectType)}
            </Descriptions.Item>
            <Descriptions.Item label="对象标识">{selectedFact.objectId}</Descriptions.Item>
            <Descriptions.Item label="关系">
              {selectedFact.factKind === "EDGE"
                ? projectionPredicateLabel(selectedFact.predicate)
                : "节点"}
            </Descriptions.Item>
            <Descriptions.Item label="内容摘要">
              {shortHash(selectedFact.contentHash)}
            </Descriptions.Item>
            <Descriptions.Item label="同步时间">
              {formatDateTime(selectedFact.syncedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="追踪号">{selectedFact.traceId || "未返回"}</Descriptions.Item>
          </Descriptions>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择节点查看投影证据" />
        )}
      </aside>
    </div>
  );

  const factsTab = (
    <div className={styles.tableWrap}>
      <Table
        rowKey="factKey"
        loading={factsQuery.isLoading}
        columns={columns}
        dataSource={facts}
        locale={{
          emptyText: (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="当前查询范围没有真实投影事实"
            />
          ),
        }}
        pagination={{
          current: page,
          pageSize: factsQuery.data?.size ?? 40,
          total,
          showSizeChanger: false,
          showTotal: (count) => `共 ${count} 条投影事实`,
          onChange: setPage,
        }}
      />
    </div>
  );

  const consistencyTab = (
    <>
      <div className={styles.diffList}>
        {diffPanel("关系库有、投影缺失", report?.missing ?? [])}
        {diffPanel("投影多余", report?.extra ?? [])}
        {diffPanel("内容已变化", report?.changed ?? [])}
      </div>
    </>
  );

  return (
    <PageShell
      title="图谱查询"
      description="关系库权威源的可重建投影"
      extras={
        <Tooltip title="刷新当前结果">
          <Button
            aria-label="刷新当前结果"
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={handleReload}
          />
        </Tooltip>
      }
      primary={
        canRebuild ? (
          <Popconfirm
            title="确认重建当前投影"
            description="只重建派生快照，不改写关系库权威源。"
            okText="确认重建"
            cancelText="取消"
            onConfirm={handleRebuild}
          >
            <Button
              aria-label="重建投影"
              type="primary"
              danger
              icon={<SyncOutlined />}
              loading={rebuildMutation.isPending}
            >
              重建投影
            </Button>
          </Popconfirm>
        ) : null
      }
    >
      <div className={styles.pageStack}>
        <Alert
          type={partial || report?.consistent === false ? "warning" : "info"}
          showIcon
          message={
            partial
              ? "部分状态暂不可用"
              : (runtimeQuery.data?.message ?? report?.message ?? "正在读取投影状态")
          }
          description={
            partial ? (
              "真实投影事实仍可查询；一致性或运行状态暂未返回，未以本地数据补齐。"
            ) : (
              <Space direction="vertical" size={0}>
                <Text>图谱仅用于探索关系库权威数据生成的可重建投影，不直接驱动临床决策。</Text>
                {report?.message && <Text type="secondary">{report.message}</Text>}
              </Space>
            )
          }
        />

        <div className={styles.queryBar}>
          <Select
            aria-label="投影目标"
            value={targetType}
            options={targetOptions}
            onChange={handleTargetChange}
          />
          <Input
            aria-label="实体、关系或追踪号"
            allowClear
            value={keywordInput}
            placeholder="实体、关系、对象标识或追踪号"
            onChange={(event) => setKeywordInput(event.target.value)}
            onPressEnter={handleSearch}
          />
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            查询
          </Button>
        </div>

        <div className={styles.statusStrip}>
          <div className={styles.statusItem}>
            <span className={styles.statusLabel}>投影目标</span>
            <span className={styles.statusValue}>{targetLabel(targetType)}</span>
          </div>
          <div className={styles.statusItem}>
            <span className={styles.statusLabel}>源事实</span>
            <span className={styles.statusValue}>{report?.sourceCount ?? "未返回"}</span>
          </div>
          <div className={styles.statusItem}>
            <span className={styles.statusLabel}>投影快照</span>
            <span className={styles.statusValue}>
              {report?.projectionCount ?? runtimeQuery.data?.snapshotCount ?? "未返回"}
            </span>
          </div>
          <div className={styles.statusItem}>
            <span className={styles.statusLabel}>一致性</span>
            <span className={styles.statusValue}>
              {statusTag(report?.status)} {diffCount > 0 ? `${diffCount} 项差异` : ""}
            </span>
          </div>
        </div>

        <Tabs
          defaultActiveKey="graph"
          items={[
            { key: "graph", label: "关系图", children: graphTab },
            { key: "facts", label: `事实明细 (${total})`, children: factsTab },
            { key: "consistency", label: `一致性差异 (${diffCount})`, children: consistencyTab },
          ]}
        />
      </div>
    </PageShell>
  );
}
