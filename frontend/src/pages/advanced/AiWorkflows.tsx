import { useMemo } from "react";
import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Result,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { TableProps } from "antd";
import { ReloadOutlined } from "@ant-design/icons";

import { useModelCapabilitiesStatus, useSecurityProfile } from "@/shared/api/hooks";
import type { ModelCapabilityStatusResponse } from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";

import styles from "./AiWorkflows.module.css";

const { Text } = Typography;

const routeStrategyView: Record<string, { color: string; label: string }> = {
  BASELINE: { color: "blue", label: "B0 基线" },
  DISABLED: { color: "default", label: "MODEL_DISABLED" },
  LOCAL_MODEL: { color: "cyan", label: "本地模型策略" },
  EXTERNAL_MODEL: { color: "geekblue", label: "外部模型策略" },
};

const desensitizeStrategyView: Record<string, string> = {
  DEFAULT: "默认脱敏",
  MASK_ALL: "全量掩码",
  NONE: "未启用脱敏",
};

function routeView(routeStrategy: string) {
  return (
    routeStrategyView[routeStrategy] ?? {
      color: "default",
      label: routeStrategy || "NOT_AVAILABLE",
    }
  );
}

function availabilityView(item: ModelCapabilityStatusResponse) {
  if (item.routeStrategy === "DISABLED") {
    return { color: "default", label: "已停用" };
  }
  if (item.fallbackAvailable) {
    return { color: "success", label: "基线可用" };
  }
  return { color: "warning", label: "NOT_AVAILABLE" };
}

function capabilityDetails(item: ModelCapabilityStatusResponse) {
  return (
    <Descriptions className={styles.details} column={{ xs: 1, sm: 2, lg: 3 }} size="small">
      <Descriptions.Item label="能力代码">
        <Text code>{item.capabilityCode}</Text>
      </Descriptions.Item>
      <Descriptions.Item label="路由策略">{item.routeStrategy}</Descriptions.Item>
      <Descriptions.Item label="脱敏策略">{item.desensitizeStrategy}</Descriptions.Item>
      <Descriptions.Item label="租户专属配置">
        {item.configured ? "已配置" : "使用系统默认"}
      </Descriptions.Item>
      <Descriptions.Item label="结构约束">
        {item.expectedSchema ? "已配置 JSON Schema" : "未配置"}
      </Descriptions.Item>
      <Descriptions.Item label="状态说明">{item.fallbackReason}</Descriptions.Item>
      {item.expectedSchema ? (
        <Descriptions.Item label="JSON Schema" span={3}>
          <Text code className={styles.schemaText}>
            {item.expectedSchema}
          </Text>
        </Descriptions.Item>
      ) : null}
    </Descriptions>
  );
}

export default function AiWorkflows() {
  const securityQuery = useSecurityProfile();
  const permissionCodes = useMemo(
    () => new Set(securityQuery.data?.permissions.map((permission) => permission.code) ?? []),
    [securityQuery.data],
  );
  const canRead = permissionCodes.has("llm.read");
  const statusQuery = useModelCapabilitiesStatus(canRead);
  const capabilities = useMemo(() => statusQuery.data ?? [], [statusQuery.data]);

  const summary = useMemo(
    () => ({
      total: capabilities.length,
      baseline: capabilities.filter((item) => item.routeStrategy === "BASELINE").length,
      configured: capabilities.filter((item) => item.configured).length,
      disabled: capabilities.filter((item) => item.routeStrategy === "DISABLED").length,
    }),
    [capabilities],
  );
  const unavailableCount = useMemo(
    () =>
      capabilities.filter((item) => item.routeStrategy !== "DISABLED" && !item.fallbackAvailable)
        .length,
    [capabilities],
  );

  const columns: TableProps<ModelCapabilityStatusResponse>["columns"] = [
    {
      title: "能力",
      key: "capability",
      width: 320,
      render: (_value, item) => (
        <div className={styles.capabilityCell}>
          <Text strong>{item.displayName}</Text>
          <Text type="secondary">{item.description}</Text>
          <Text code className={styles.capabilityCode}>
            {item.capabilityCode}
          </Text>
        </div>
      ),
    },
    {
      title: "业务分类",
      dataIndex: "category",
      key: "category",
      width: 140,
    },
    {
      title: "运行方式",
      dataIndex: "routeStrategy",
      key: "routeStrategy",
      width: 150,
      render: (value: string) => {
        const view = routeView(value);
        return <Tag color={view.color}>{view.label}</Tag>;
      },
    },
    {
      title: "数据保护",
      dataIndex: "desensitizeStrategy",
      key: "desensitizeStrategy",
      width: 130,
      render: (value: string) => desensitizeStrategyView[value] ?? value,
    },
    {
      title: "结构约束",
      dataIndex: "expectedSchema",
      key: "expectedSchema",
      width: 120,
      render: (value: string | null) => (value ? "已配置" : "未配置"),
    },
    {
      title: "当前状态",
      key: "status",
      width: 260,
      render: (_value, item) => {
        const view = availabilityView(item);
        return (
          <div className={styles.statusCell}>
            <Tag color={view.color}>{view.label}</Tag>
            <Text type="secondary">{item.fallbackReason}</Text>
          </div>
        );
      },
    },
  ];

  if (securityQuery.isLoading) {
    return (
      <PageShell title="AI 工作流" description="查看当前组织的 AI 能力与降级状态">
        <Spin aria-label="正在核验访问权限" />
      </PageShell>
    );
  }

  if (securityQuery.isError || !canRead) {
    return (
      <PageShell title="AI 工作流" description="查看当前组织的 AI 能力与降级状态">
        <Result status="403" title="无权查看 AI 工作流" subTitle="需要 AI 能力读取权限。" />
      </PageShell>
    );
  }

  if (statusQuery.isError) {
    return (
      <PageShell title="AI 工作流" description="查看当前组织的 AI 能力与降级状态">
        <Result
          status="error"
          title="AI 能力状态读取失败"
          subTitle="未使用本地默认项替代真实状态。"
          extra={
            <Button type="primary" onClick={() => statusQuery.refetch()}>
              重新读取
            </Button>
          }
        />
      </PageShell>
    );
  }

  if (statusQuery.isLoading) {
    return (
      <PageShell title="AI 工作流" description="查看当前组织的 AI 能力与降级状态">
        <div className={styles.loadingState}>
          <Spin aria-label="正在读取 AI 能力状态" size="large" />
          <Text type="secondary">正在读取真实能力目录与运行状态</Text>
        </div>
      </PageShell>
    );
  }

  return (
    <PageShell
      title="AI 工作流"
      description="查看当前组织已登记能力、路由策略与无模型降级状态"
      extras={
        <Tooltip title="刷新能力状态">
          <Button
            aria-label="刷新能力状态"
            icon={<ReloadOutlined />}
            loading={statusQuery.isFetching}
            onClick={() => statusQuery.refetch()}
          />
        </Tooltip>
      }
    >
      <div className={styles.pageStack}>
        <section className={styles.summaryStrip} aria-label="AI 能力状态摘要">
          <div className={styles.summaryItem}>
            <Text type="secondary">已登记能力</Text>
            <strong>{summary.total}</strong>
          </div>
          <div className={styles.summaryItem}>
            <Text type="secondary">B0 基线</Text>
            <strong>{summary.baseline}</strong>
          </div>
          <div className={styles.summaryItem}>
            <Text type="secondary">租户已配置</Text>
            <strong>{summary.configured}</strong>
          </div>
          <div className={styles.summaryItem}>
            <Text type="secondary">已停用</Text>
            <strong>{summary.disabled}</strong>
          </div>
        </section>

        {unavailableCount > 0 ? (
          <Alert
            type="warning"
            showIcon
            message="部分 AI 能力当前不可用"
            description={`${unavailableCount} 项能力没有可用路由或基线，其他能力仍可查看。`}
          />
        ) : null}

        {capabilities.length === 0 ? (
          <div className={styles.emptyState}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <div className={styles.emptyDescription}>
                  <Text>当前组织没有已启用的 AI 能力</Text>
                  <Text type="secondary">未使用本地默认项补齐真实结果。</Text>
                </div>
              }
            />
          </div>
        ) : (
          <div className={styles.tableWrap}>
            <Table
              rowKey="capabilityCode"
              columns={columns}
              dataSource={capabilities}
              loading={statusQuery.isLoading}
              pagination={false}
              scroll={{ x: 1184 }}
              tableLayout="fixed"
              expandable={{
                expandedRowRender: capabilityDetails,
                rowExpandable: () => true,
                columnTitle: "详情",
                columnWidth: 64,
              }}
            />
          </div>
        )}
      </div>
    </PageShell>
  );
}
