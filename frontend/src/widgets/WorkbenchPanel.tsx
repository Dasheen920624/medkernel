import { Card, Space, Typography } from "antd";
import { TenantLifecyclePanel } from "@/features/tenant-lifecycle/TenantLifecyclePanel";
import { PageState } from "@/shared/ui/PageState";

/**
 * 工作台：只展示已接入真实数据的院级状态，未接入聚合 API 的区域诚实空态。
 *
 * 严格遵守 docs/CONSTITUTION.md §5 第 1 条：院长首屏 ≤ 10 秒看懂今天系统状态。
 */
export function WorkbenchPanel() {
  return (
    <PageState state="ready">
      <Space direction="vertical" size="large" className="mk-full-width">
        {/* 1. 院级生命周期面板 */}
        <TenantLifecyclePanel />

        <Card
          title="真实工作台聚合数据待接入"
          extra={<Typography.Text type="secondary">等待真实聚合 API</Typography.Text>}
        >
          <PageState
            state="empty"
            title="暂无真实工作台聚合数据"
            description="当前版本仅展示上方真实生命周期状态；试点进度、今日提醒、质控闭环和合规待审需要接入真实工作台聚合 API 后再呈现。"
          />
        </Card>
      </Space>
    </PageState>
  );
}
