import { CopyOutlined } from "@ant-design/icons";
import { Button, Result, Space, Spin, Typography } from "antd";
import type { ReactNode } from "react";
import { customerSafeDisplayText } from "@/shared/config/customerLabels";
import type { FailureDetail, NonReadyPageStateKind, PageStateKind } from "./PageState.contract";

const { Text } = Typography;
const PARTIAL_FAILURE_REASON_FALLBACK = "当前项目读取失败，请重试或转人工处理。";

export interface PageStateProps {
  state: PageStateKind;
  title?: string;
  description?: ReactNode;
  traceId?: string;
  successCount?: number;
  failureCount?: number;
  failureDetails?: FailureDetail[];
  action?: ReactNode;
  onRetry?: () => void;
  children?: ReactNode;
}

const DEFAULT_TITLE: Record<NonReadyPageStateKind, string> = {
  loading: "正在加载",
  empty: "暂无数据",
  error: "页面暂时不可用",
  forbidden: "当前权限不足",
  partial: "部分处理完成",
};

const RESULT_STATUS: Record<NonReadyPageStateKind, "info" | "error" | "403"> = {
  loading: "info",
  empty: "info",
  error: "error",
  forbidden: "403",
  partial: "info",
};

const DEFAULT_DESCRIPTION: Record<NonReadyPageStateKind, ReactNode> = {
  loading: "正在读取当前组织范围内的数据。",
  empty: "当前筛选条件下没有结果，可调整筛选或创建第一条记录。",
  error: "请稍后重试；如果持续失败，请凭追踪号联系信息科。",
  forbidden: "该页面包含受控数据，请联系信息科主任调整角色或数据范围。",
  partial: "部分项目已完成，其余项目需要查看原因后重试或转人工处理。",
};

function copyTraceId(traceId: string) {
  if (typeof navigator === "undefined") return;
  const writeResult = navigator.clipboard?.writeText(traceId);
  if (writeResult && typeof writeResult.catch === "function") {
    void writeResult.catch(() => undefined);
  }
}

export function PageState({
  state,
  title,
  description,
  traceId,
  successCount,
  failureCount,
  failureDetails = [],
  action,
  onRetry,
  children,
}: PageStateProps) {
  if (state === "ready") {
    return <>{children}</>;
  }

  if (state === "loading") {
    return (
      <Result
        icon={<Spin size="large" />}
        title={title ?? DEFAULT_TITLE.loading}
        subTitle={description ?? DEFAULT_DESCRIPTION.loading}
      />
    );
  }

  const extra =
    action ??
    (state === "error" && onRetry ? (
      <Button aria-label="重试" onClick={onRetry}>
        重试
      </Button>
    ) : undefined);
  const partialDescription =
    state === "partial" && typeof successCount === "number" && typeof failureCount === "number"
      ? `${successCount} 项成功，${failureCount} 项需处理。`
      : undefined;
  const partialDetails =
    state === "partial" && failureDetails.length > 0 ? (
      <Space direction="vertical" size={2}>
        {failureDetails.map((failure) => (
          <Text key={failure.key} type="secondary">
            {failure.key}：
            {customerSafeDisplayText(failure.reason, PARTIAL_FAILURE_REASON_FALLBACK)}
            {failure.retryable ? "，可重试" : ""}
          </Text>
        ))}
      </Space>
    ) : null;

  return (
    <Result
      status={RESULT_STATUS[state] ?? "info"}
      title={title ?? DEFAULT_TITLE[state]}
      subTitle={
        <>
          <div>{description ?? partialDescription ?? DEFAULT_DESCRIPTION[state]}</div>
          {partialDetails}
          {traceId && (
            <Space size={8} wrap>
              <Text type="secondary">追踪号：{traceId}</Text>
              <Button
                aria-label={`复制追踪号 ${traceId}`}
                icon={<CopyOutlined />}
                size="small"
                type="link"
                onClick={() => copyTraceId(traceId)}
              >
                复制追踪号
              </Button>
            </Space>
          )}
        </>
      }
      extra={extra}
    />
  );
}
