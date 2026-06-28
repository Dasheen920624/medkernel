import { Alert, Collapse, Descriptions, Empty, Steps, Tag, Typography } from "antd";
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

const SENSITIVE_KEY_PARTS = [
  "patient",
  "mpi",
  "encounter",
  "token",
  "credential",
  "phone",
  "idno",
  "identity",
  "patientname",
  "personname",
  "username",
  "embedurl",
  "sourcerecord",
];

function isSensitiveKey(key: string) {
  const normalized = key.toLowerCase();
  return SENSITIVE_KEY_PARTS.some((part) => normalized.includes(part));
}

function sanitizeEvidence(value: unknown, parentKey = ""): unknown {
  if (value === null || value === undefined) return value ?? null;
  if (isSensitiveKey(parentKey)) return "已脱敏";
  if (Array.isArray(value)) return value.map((item) => sanitizeEvidence(item, parentKey));
  if (typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, child]) => [key, sanitizeEvidence(child, key)]),
    );
  }
  if (typeof value === "string" && value.includes("token=")) return "已隐藏";
  return value;
}

function formatted(value: unknown) {
  return JSON.stringify(sanitizeEvidence(value) ?? null, null, 2);
}

function stageSummary(step: SandboxStepTrace) {
  if (step.status === "FAIL") return step.error ?? "该步骤未完成，请查看失败原因。";
  if (step.stage === "CONTEXT") return "已生成本次运行所需的患者上下文快照。";
  if (step.stage === "RECOMMENDATION") return "已按当前机构生效版本完成推荐评估。";
  if (step.stage === "TOKEN") return "已生成受控嵌入访问凭证，默认不展示明文。";
  return "该运行步骤已完成。";
}

interface SandboxPathInspectorProps {
  steps: SandboxStepTrace[];
  evidenceDetailsEnabled?: boolean;
}

export default function SandboxPathInspector({
  steps,
  evidenceDetailsEnabled = false,
}: SandboxPathInspectorProps) {
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
      <Descriptions
        size="small"
        column={1}
        className={styles.traceSummary}
        items={steps.map((step, index) => ({
          key: `${step.stage}-${index}`,
          label: stageLabels[step.stage] ?? step.stage,
          children: stageSummary(step),
        }))}
      />
      {evidenceDetailsEnabled ? (
        <Collapse items={collapseItems} className={styles.traceCollapse} />
      ) : null}
    </section>
  );
}
