import { Alert, Button, Empty, List, Space, Tag } from "antd";

import type { ContextSnapshotSummary } from "@/shared/api/hooks";
import { formatClinicalDateTimeWithSeconds } from "@/shared/lib/dateTimeText";

interface ContextSnapshotSelectorProps {
  enabled: boolean;
  loading: boolean;
  error: boolean;
  snapshots: ContextSnapshotSummary[];
  selectedSnapshotId: string;
  onSelect: (snapshotId: string) => void;
  noun?: string;
  evidenceDetailsEnabled?: boolean;
}

export function ContextSnapshotSelector({
  enabled,
  loading,
  error,
  snapshots,
  selectedSnapshotId,
  onSelect,
  noun = "临床快照",
  evidenceDetailsEnabled = false,
}: ContextSnapshotSelectorProps) {
  if (!enabled) {
    return <Empty description={`输入患者信息或就诊信息后读取已生效${noun}`} />;
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
      renderItem={(snapshot, index) => {
        const createdAtText = formatClinicalDateTimeWithSeconds(snapshot.createdAt, "");
        const selectLabel = evidenceDetailsEnabled
          ? `选择 ${snapshot.snapshotId}`
          : createdAtText
            ? `选择 ${createdAtText} 建立的${noun}`
            : `选择第 ${index + 1} 个${noun}`;
        return (
          <List.Item
            actions={[
              <Button
                key="select"
                aria-label={selectLabel}
                type={selectedSnapshotId === snapshot.snapshotId ? "primary" : "default"}
                onClick={() => onSelect(snapshot.snapshotId)}
              >
                选择
              </Button>,
            ]}
          >
            <List.Item.Meta
              title={
                evidenceDetailsEnabled
                  ? `患者 ${snapshot.patientId} · 就诊 ${snapshot.encounterId}`
                  : "已关联患者与就诊"
              }
              description={
                <Space wrap size="small">
                  {evidenceDetailsEnabled ? (
                    <span>快照 {snapshot.snapshotId}</span>
                  ) : (
                    <span>{noun}已生效</span>
                  )}
                  {createdAtText && <span>建立时间：{createdAtText}</span>}
                  <Tag color={snapshot.qualityStatus === "VALID" ? "green" : "orange"}>
                    {snapshot.qualityStatus}
                  </Tag>
                </Space>
              }
            />
          </List.Item>
        );
      }}
    />
  );
}
