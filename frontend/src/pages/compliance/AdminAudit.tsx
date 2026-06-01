import { Table, Tag, Button, Typography, Alert, message } from "antd";
import { SafetyCertificateOutlined, ExportOutlined } from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { useAuditEvents, useAuditSnapshot } from "@/shared/api/hooks";
import { PageState } from "@/shared/ui/PageState";

function getAuditPageState(isLoading: boolean, isError: boolean, itemCount: number) {
  if (isLoading) return "loading";
  if (isError) return "error";
  if (itemCount === 0) return "empty";
  return "ready";
}

export default function AdminAudit() {
  const events = useAuditEvents();
  const snapshot = useAuditSnapshot();
  const auditPageState = getAuditPageState(
    events.isLoading,
    events.isError,
    events.data?.length ?? 0,
  );

  return (
    <PageShell
      title="审计日志"
      description="所有用户操作 + 系统事件，TSA 时间戳验签，6 个月内随时可查"
      primary={
        <Button
          icon={<ExportOutlined />}
          loading={snapshot.isPending}
          onClick={() =>
            snapshot.mutate("manual-export", {
              onSuccess: (data) => message.success(`快照已生成 · ${data.signature}`),
            })
          }
        >
          导出审计快照
        </Button>
      }
    >
      <Alert
        type="info"
        showIcon
        icon={<SafetyCertificateOutlined />}
        message="审计链已启用"
        description="等保 2.0 三级 + 个保法审计留痕 + 国密 SM2/SM3 验签 + TSA 国家可信时间戳"
      />
      <PageState state={auditPageState} title="暂无审计事件" onRetry={() => void events.refetch()}>
        <Table
          rowKey="id"
          dataSource={events.data ?? []}
          scroll={{ x: "max-content" }}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          columns={[
            {
              title: "时间",
              dataIndex: "occurredAt",
              render: (v: string) => (v ? new Date(v).toLocaleString() : "-"),
            },
            { title: "用户", dataIndex: "user", render: (v) => v ?? "system" },
            { title: "操作", dataIndex: "action" },
            { title: "Trace ID", dataIndex: "traceId", render: (v) => <Tag>{v}</Tag> },
            {
              title: "签名",
              dataIndex: "signature",
              render: (v: string | null) =>
                v ? <Typography.Text type="success">{v.slice(0, 16)}…</Typography.Text> : "—",
            },
          ]}
        />
      </PageState>
    </PageShell>
  );
}
