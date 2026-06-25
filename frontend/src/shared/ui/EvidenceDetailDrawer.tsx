import { Alert, Button, Descriptions, Drawer, Space, Spin, Typography } from "antd";
import type { ReactNode } from "react";

const { Text, Title } = Typography;

export interface EvidenceDetailSection {
  key: string;
  title: string;
  items: Array<{ label: string; value: ReactNode; advancedOnly?: boolean }>;
}

interface EvidenceDetailDrawerProps {
  open: boolean;
  title: string;
  loading?: boolean;
  error?: Error;
  evidenceDetailsEnabled: boolean;
  sections: EvidenceDetailSection[];
  traceId?: string;
  onClose: () => void;
  onRetry?: () => void;
}

export function EvidenceDetailDrawer({
  open,
  title,
  loading = false,
  error,
  evidenceDetailsEnabled,
  sections,
  traceId,
  onClose,
  onRetry,
}: EvidenceDetailDrawerProps) {
  let contents: ReactNode;

  if (loading) {
    contents = (
      <Space>
        <Spin />
        <Text>正在加载详情</Text>
      </Space>
    );
  } else if (error) {
    contents = (
      <Alert
        type="error"
        showIcon
        message="详情暂时不可用"
        description="请稍后重试；如果持续失败，请联系信息科。"
        action={
          onRetry ? (
            <Button size="small" aria-label="重试" onClick={onRetry}>
              重试
            </Button>
          ) : undefined
        }
      />
    );
  } else {
    contents = (
      <Space direction="vertical" size="large" className="mk-full-width">
        {sections.map((section) => {
          const visibleItems = section.items.filter(
            (item) => evidenceDetailsEnabled || !item.advancedOnly,
          );
          if (visibleItems.length === 0) return null;

          return (
            <section key={section.key}>
              <Title level={5}>{section.title}</Title>
              <Descriptions
                size="small"
                column={1}
                items={visibleItems.map((item) => ({
                  key: item.label,
                  label: item.label,
                  children: item.value,
                }))}
              />
            </section>
          );
        })}
        {evidenceDetailsEnabled && traceId && <Text type="secondary">追踪号：{traceId}</Text>}
      </Space>
    );
  }

  return (
    <Drawer title={title} open={open} width={480} onClose={onClose}>
      {contents}
    </Drawer>
  );
}
