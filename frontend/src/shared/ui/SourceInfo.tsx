import { Alert, Descriptions, Space, Tag, Typography } from "antd";

import { formatClinicalDateTime } from "@/shared/lib/dateTimeText";

const { Text } = Typography;

interface SourceInfoProps {
  sourceDocumentId?: number | null;
  sourceVersionId?: number | null;
  authorityLevel?: string | null;
  anchors?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
}

function formatDateTime(value?: string | null) {
  if (!value) return "未审核";
  return formatClinicalDateTime(value, value);
}

export function SourceInfo({
  sourceDocumentId,
  sourceVersionId,
  authorityLevel,
  anchors,
  reviewedBy,
  reviewedAt,
}: SourceInfoProps) {
  const hasSource =
    (sourceDocumentId !== null && sourceDocumentId !== undefined) ||
    (sourceVersionId !== null && sourceVersionId !== undefined) ||
    Boolean(authorityLevel) ||
    Boolean(anchors);

  if (!hasSource) {
    return (
      <Alert
        type="warning"
        showIcon
        message="来源未登记"
        description="该版本缺少可核验来源，不应进入发布流程。"
      />
    );
  }

  return (
    <Descriptions title="来源与审核" size="small" bordered column={1}>
      <Descriptions.Item label="来源编号">
        <Space size={4} split="·" wrap>
          <Text>
            {sourceDocumentId !== null && sourceDocumentId !== undefined
              ? `来源文档 #${sourceDocumentId}`
              : "来源文档未登记"}
          </Text>
          <Text>
            {sourceVersionId !== null && sourceVersionId !== undefined
              ? `来源版本 #${sourceVersionId}`
              : "来源版本未登记"}
          </Text>
        </Space>
      </Descriptions.Item>
      <Descriptions.Item label="权威分级">
        {authorityLevel ? <Tag>{authorityLevel}</Tag> : "未分级"}
      </Descriptions.Item>
      <Descriptions.Item label="证据锚点">{anchors || "未登记"}</Descriptions.Item>
      <Descriptions.Item label="审核记录">
        <Text>
          {reviewedBy || "未审核"} · {formatDateTime(reviewedAt)}
        </Text>
      </Descriptions.Item>
    </Descriptions>
  );
}
