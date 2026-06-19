import { useEffect, useState } from "react";
import { Button, Descriptions, Form, InputNumber, Space, Tag, Typography } from "antd";
import { ExperimentOutlined, PlayCircleOutlined } from "@ant-design/icons";

import type { NumericSandboxScenario } from "./sandboxScenarios";
import styles from "./Sandbox.module.css";

export interface SandboxDataInput {
  numericValue: number;
  occurredAt: string;
}

interface SandboxDataEntryProps {
  scenario: NumericSandboxScenario;
  running: boolean;
  onRun: (input: SandboxDataInput) => void;
}

export default function SandboxDataEntry({ scenario, running, onRun }: SandboxDataEntryProps) {
  const [numericValue, setNumericValue] = useState(scenario.defaultNumericValue);

  useEffect(() => {
    setNumericValue(scenario.defaultNumericValue);
  }, [scenario]);

  const overReference =
    scenario.upperReferenceValue !== null &&
    scenario.upperReferenceValue !== undefined &&
    numericValue > scenario.upperReferenceValue;
  const precision =
    scenario.step !== null && scenario.step !== undefined && scenario.step < 1
      ? (String(scenario.step).split(".")[1]?.length ?? 1)
      : undefined;

  return (
    <section className={styles.panel} aria-labelledby="sandbox-data-title">
      <div className={styles.panelHeader}>
        <div>
          <Typography.Title id="sandbox-data-title" level={5}>
            业务数据
          </Typography.Title>
          <Typography.Text type="secondary">{scenario.hostSummary}</Typography.Text>
        </div>
        <Tag icon={<ExperimentOutlined />} color="processing">
          {scenario.triggerPoint}
        </Tag>
      </div>

      <Descriptions size="small" column={2} className={styles.descriptionGrid}>
        <Descriptions.Item label="患者标识">{scenario.patientId}</Descriptions.Item>
        <Descriptions.Item label="就诊标识">{scenario.encounterId}</Descriptions.Item>
        <Descriptions.Item label="场景">{scenario.encounterType}</Descriptions.Item>
        <Descriptions.Item label="参考范围">
          {scenario.referenceRange} {scenario.unit}
        </Descriptions.Item>
      </Descriptions>

      <Form layout="vertical" className={styles.dataForm}>
        <Form.Item label={scenario.observationName}>
          <InputNumber
            aria-label={scenario.observationName}
            value={numericValue}
            min={scenario.minValue ?? undefined}
            max={scenario.maxValue ?? undefined}
            step={scenario.step ?? undefined}
            precision={precision}
            addonAfter={scenario.unit}
            onChange={(value) => setNumericValue(value ?? scenario.defaultNumericValue)}
          />
        </Form.Item>
      </Form>

      <Space className={styles.actionRow}>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          aria-label={running ? "运行中" : "医生复核并触发 MedKernel"}
          loading={running}
          disabled={running}
          onClick={() =>
            onRun({
              numericValue,
              occurredAt: new Date().toISOString(),
            })
          }
        >
          {running ? "运行中" : "医生复核并触发 MedKernel"}
        </Button>
        <Typography.Text type={overReference ? "danger" : "secondary"}>
          当前结果 {overReference ? "超过参考上限" : "未超过参考上限"}
        </Typography.Text>
      </Space>
    </section>
  );
}
