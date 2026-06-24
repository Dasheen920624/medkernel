import { Alert, Button, Empty, List, Space, Tag } from "antd";

import type { ContextSnapshotSummary } from "@/shared/api/hooks";

interface ContextSnapshotSelectorProps {
  enabled: boolean;
  loading: boolean;
  error: boolean;
  snapshots: ContextSnapshotSummary[];
  selectedSnapshotId: string;
  onSelect: (snapshotId: string) => void;
  noun?: string;
}

export function ContextSnapshotSelector({
  enabled,
  loading,
  error,
  snapshots,
  selectedSnapshotId,
  onSelect,
  noun = "临床快照",
}: ContextSnapshotSelectorProps) {
  if (!enabled) {
    return <Empty description={`输入患者 ID 或就诊 ID 后读取已生效${noun}`} />;
  }
  if (loading) {
    return <Alert type="info" showIcon message={`正在读取${noun}`} />;
  }
  if (error) {
    return <Alert type="error" showIcon message={`${noun}读取失败`} />;
  }
  if (snapshots.length === 0) {
    return <Empty description={`当前患者或就诊下暂无已生效${noun}`} />;
  }
  return (
    <List
      bordered
      dataSource={snapshots}
      renderItem={(snapshot) => (
        <List.Item
          actions={[
            <Button
              key="select"
              aria-label={`选择 ${snapshot.snapshotId}`}
              type={selectedSnapshotId === snapshot.snapshotId ? "primary" : "default"}
              onClick={() => onSelect(snapshot.snapshotId)}
            >
              选择
            </Button>,
          ]}
        >
          <List.Item.Meta
            title={`患者 ${snapshot.patientId} · 就诊 ${snapshot.encounterId}`}
            description={
              <Space wrap size="small">
                <span>快照 {snapshot.snapshotId}</span>
                <Tag color={snapshot.qualityStatus === "VALID" ? "green" : "orange"}>
                  {snapshot.qualityStatus}
                </Tag>
              </Space>
            }
          />
        </List.Item>
      )}
    />
  );
}
