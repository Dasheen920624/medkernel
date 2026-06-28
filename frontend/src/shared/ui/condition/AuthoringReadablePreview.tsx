import { Alert, Skeleton, Space, Tag, Typography } from "antd";

import { useAuthoringPreview, type AuthoringPreviewSubject } from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import { customerSafeDisplayText } from "@/shared/config/customerLabels";

import styles from "./AuthoringReadablePreview.module.css";

const { Paragraph, Text } = Typography;
const PREVIEW_EVIDENCE_TEXT = "预览证据已记录";
const PREVIEW_WARNING_FALLBACK = "预览生成提示，请核对结构化定义。";
const FALLBACK_FIELD_LABEL_WARNING = "部分预览内容使用通用字段标签，请核对字段含义。";
const JSON_PATH_PATTERN = /(?:^|\s)\$(?:\.|\[)/;

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

  const readableWarnings = Array.from(
    new Set((previewQuery.data?.warnings ?? []).map(readablePreviewWarning)),
  );

  return (
    <section className={styles.surface} aria-label={title}>
      <div className={styles.header}>
        <Text strong>{title}</Text>
        <Space size="small" wrap>
          {previewQuery.isFetching && <Tag color="processing">更新中</Tag>}
          {previewQuery.data?.traceId && <Tag>{PREVIEW_EVIDENCE_TEXT}</Tag>}
        </Space>
      </div>
      <Paragraph className={styles.text}>{previewQuery.data?.previewText ?? "暂无预览"}</Paragraph>
      {readableWarnings.length > 0 && (
        <div className={styles.warningList}>
          {readableWarnings.map((warning) => (
            <Alert key={warning} type="warning" showIcon message={warning} />
          ))}
        </div>
      )}
    </section>
  );
}

function readablePreviewWarning(warning: string) {
  const trimmed = warning.trim();
  if (JSON_PATH_PATTERN.test(trimmed) || trimmed.includes("兜底字段标签")) {
    return FALLBACK_FIELD_LABEL_WARNING;
  }

  return customerSafeDisplayText(trimmed, PREVIEW_WARNING_FALLBACK);
}

export default AuthoringReadablePreview;
