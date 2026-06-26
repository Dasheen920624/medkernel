import { Card, Space, Typography } from "antd";
import type { ReactNode } from "react";
import { PageState } from "./PageState";
import type { PageStateProps } from "./PageState";
import type { PageStateKind } from "./PageState.contract";

const { Title, Text } = Typography;

/**
 * 通用页骨架。
 *
 * 所有客户可见页面统一使用此 PageShell，保证：
 * - 1 个主标题 + 1 句业务描述（不超过 30 字）
 * - 右上角放 1 个主按钮（main）+ 次级动作（extras）
 * - 默认 1 个主目标内容区，低频参数折叠到子组件内
 *
 * 与 docs/CONSTITUTION.md §1 第 6 条对齐：默认 1 主按钮 / 1 主目标 / ≤ 3 默认筛选。
 */
interface PageShellProps {
  title: string;
  description?: string;
  primary?: ReactNode;
  extras?: ReactNode;
  state?: PageStateKind;
  stateProps?: Omit<PageStateProps, "state" | "children">;
  children: ReactNode;
}

export function PageShell({
  title,
  description,
  primary,
  extras,
  state = "ready",
  stateProps,
  children,
}: PageShellProps) {
  return (
    <Space direction="vertical" size="large" className="mk-full-width">
      <Card bordered={false} className="mk-card-transparent mk-card-body-flush">
        <Space className="mk-flex-between" align="start" wrap>
          <Space direction="vertical" size={0}>
            <Title level={4} className="mk-title-tight">
              {title}
            </Title>
            {description && <Text type="secondary">{description}</Text>}
          </Space>
          <Space wrap>
            {extras}
            {primary}
          </Space>
        </Space>
      </Card>
      {state === "ready" ? children : <PageState state={state} {...stateProps} />}
    </Space>
  );
}
