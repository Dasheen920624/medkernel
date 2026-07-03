import { Steps, Card, Space, Typography } from "antd";
import type { ReactNode } from "react";
import { StatusBadge } from "./StatusBadge";
import { SEVEN_STEPS, STEP_CHANGE_STATUS } from "./StepFlow.contract";
import type { StepKey } from "./StepFlow.contract";

const { Text } = Typography;

/**
 * 7 步极简配置流模板（与 docs/CONSTITUTION.md §4 对齐）。
 *
 * 机构生效版本、规则、路径、图谱、字典、适配器、评估指标等流程全部复用此组件。
 * 任何配置类页面缺这 7 步即视为 PR 不通过。
 */

interface StepFlowProps {
  currentStep: StepKey;
  /** 每步右侧可渲染自定义内容（如校验结果、影响表）。 */
  panelByStep?: Partial<Record<StepKey, ReactNode>>;
  /** 步骤进度（0~6）。current 之外可手动指定 finished/error。 */
  status?: "wait" | "process" | "finish" | "error";
}

/**
 * 7 步流页面骨架。业务层只需传 currentStep 和每步 panel。
 *
 * @example
 *   <StepFlow currentStep="impact_preview" panelByStep={{
 *     auto_validate: <ValidationResult ... />,
 *     impact_preview: <ImpactTable ... />,
 *   }} />
 */
export function StepFlow({ currentStep, panelByStep = {}, status = "process" }: StepFlowProps) {
  const currentIdx = SEVEN_STEPS.findIndex((s) => s.key === currentStep);
  const currentMeta = SEVEN_STEPS[currentIdx];
  const currentPanel = panelByStep[currentStep];
  const currentChangeStatus = STEP_CHANGE_STATUS[currentStep];

  if (!currentMeta) {
    throw new Error(`未注册 7 步流步骤：${String(currentStep)}`);
  }

  return (
    <Space direction="vertical" size="large" className="mk-full-width">
      <Steps
        current={currentIdx}
        status={status}
        items={SEVEN_STEPS.map((s) => ({ title: s.title, description: s.description }))}
      />
      <Card
        title={currentMeta.title}
        extra={
          <Space size="small">
            <StatusBadge machine="change" status={currentChangeStatus} />
            <Text type="secondary">{currentMeta.description}</Text>
          </Space>
        }
      >
        {currentPanel ?? (
          <Text type="secondary">请在当前步骤展示真实校验、影响、审核、发布或回滚证据。</Text>
        )}
      </Card>
    </Space>
  );
}
