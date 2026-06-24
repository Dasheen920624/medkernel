import { Alert, Collapse, Empty, Steps, Tag, Typography } from "antd";
import {
  ApiOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DatabaseOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import type { ReactNode } from "react";

import type { SandboxStepTrace } from "@/shared/api/hooks";
import styles from "./Sandbox.module.css";

const stageLabels: Record<string, string> = {
  CONTEXT: "上下文快照",
  RECOMMENDATION: "推荐评估",
  TOKEN: "访问凭证",
};

const stageIcons: Record<string, ReactNode> = {
  CONTEXT: <DatabaseOutlined />,
  RECOMMENDATION: <SafetyCertificateOutlined />,
  TOKEN: <ApiOutlined />,
};

function formatted(value: unknown) {
  return JSON.stringify(value ?? null, null, 2);
}

export default function SandboxPathInspector({ steps }: { steps: SandboxStepTrace[] }) {
  if (steps.length === 0) {
    return (
      <section className={styles.panel} aria-labelledby="sandbox-path-title">
        <Typography.Title id="sandbox-path-title" level={5}>
          路径证据
        </Typography.Title>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无运行轨迹" />
      </section>
    );
  }

  const failedStep = steps.find((step) => step.status === "FAIL");
  const collapseItems = steps.map((step, index) => ({
    key: `${step.stage}-${index}`,
    label: (
      <span className={styles.stepLabel}>
        {stageLabels[step.stage] ?? step.stage}
        <Tag
          icon={step.status === "OK" ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
          color={step.status === "OK" ? "success" : "error"}
        >
          {step.status === "OK" ? "通过" : "失败"}
        </Tag>
      </span>
    ),
    children: (
      <div className={styles.traceDetails}>
        <div>
          <Typography.Text type="secondary">调用地址</Typography.Text>
          <Typography.Text code>{step.endpoint}</Typography.Text>
        </div>
        <div className={styles.traceGrid}>
          <div>
            <Typography.Text strong>输入内容</Typography.Text>
            <pre>{formatted(step.request)}</pre>
          </div>
          <div>
            <Typography.Text strong>返回结果</Typography.Text>
            <pre>{formatted(step.response)}</pre>
          </div>
          <div>
            <Typography.Text strong>服务端事实</Typography.Text>
            <pre>{formatted(step.serverFacts)}</pre>
          </div>
        </div>
      </div>
    ),
  }));

  return (
    <section className={styles.panel} aria-labelledby="sandbox-path-title">
      <div className={styles.panelHeader}>
        <Typography.Title id="sandbox-path-title" level={5}>
          路径证据
        </Typography.Title>
        <Tag color={failedStep ? "error" : "success"}>{failedStep ? "链路未完成" : "链路完成"}</Tag>
      </div>
      <Steps
        size="small"
        current={failedStep ? Math.max(steps.indexOf(failedStep), 0) : steps.length}
        status={failedStep ? "error" : "finish"}
        items={steps.map((step) => ({
          title: stageLabels[step.stage] ?? step.stage,
          icon: stageIcons[step.stage],
        }))}
      />
      {failedStep?.error && (
        <Alert className={styles.pathAlert} type="error" showIcon message={failedStep.error} />
      )}
      <Collapse items={collapseItems} className={styles.traceCollapse} />
    </section>
  );
}
