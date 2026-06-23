import { Alert, Skeleton, Space, Tag, Typography } from "antd";

import { useAuthoringPreview, type AuthoringPreviewSubject } from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";

import styles from "./AuthoringReadablePreview.module.css";

const { Paragraph, Text } = Typography;

interface AuthoringReadablePreviewProps {
  subject: AuthoringPreviewSubject;
  dsl: unknown;
  title?: string;
  enabled?: boolean;
}

export function AuthoringReadablePreview({
  subject,
  dsl,
  title = "可读预览",
  enabled = true,
}: AuthoringReadablePreviewProps) {
  const payload = enabled && dsl ? { subject, dsl } : null;
  const previewQuery = useAuthoringPreview(payload, { enabled: enabled && Boolean(payload) });

  if (!payload) {
    return <Alert type="warning" showIcon message="缺少结构化定义，预览暂不可生成。" />;
  }

  if (previewQuery.isLoading && !previewQuery.data) {
    return (
      <div className={styles.surface} aria-busy="true">
        <Skeleton active paragraph={{ rows: 2 }} title={false} />
      </div>
    );
  }

  if (previewQuery.isError) {
    return (
      <Alert
        type="warning"
        showIcon
        message={getApiErrorMessage(previewQuery.error, "自然语言预览生成失败。")}
      />
    );
  }

  return (
    <section className={styles.surface} aria-label={title}>
      <div className={styles.header}>
        <Text strong>{title}</Text>
        <Space size="small" wrap>
          {previewQuery.isFetching && <Tag color="processing">更新中</Tag>}
          {previewQuery.data?.traceId && <Tag>{previewQuery.data.traceId}</Tag>}
        </Space>
      </div>
      <Paragraph className={styles.text}>{previewQuery.data?.previewText ?? "暂无预览"}</Paragraph>
      {(previewQuery.data?.warnings.length ?? 0) > 0 && (
        <div className={styles.warningList}>
          {previewQuery.data?.warnings.map((warning) => (
            <Alert key={warning} type="warning" showIcon message={warning} />
          ))}
        </div>
      )}
    </section>
  );
}

export default AuthoringReadablePreview;
