import { useMemo, useState } from "react";
import { Alert, App, Button, Descriptions, Space, Tag, Typography } from "antd";
import {
  CheckCircleOutlined,
  ExperimentOutlined,
  PlayCircleOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";

import SandboxDataEntry from "@/features/sandbox/SandboxDataEntry";
import type { SandboxDataInput } from "@/features/sandbox/SandboxDataEntry";
import SandboxEmbedFrame from "@/features/sandbox/SandboxEmbedFrame";
import type { SandboxEmbedDecision, SandboxEmbedMode } from "@/features/sandbox/SandboxEmbedFrame";
import SandboxPathInspector from "@/features/sandbox/SandboxPathInspector";
import {
  SANDBOX_SCENARIOS,
  buildSandboxContextOverride,
  isNumericScenario,
  mergeSandboxCatalog,
  scenariosByServicePackage,
} from "@/features/sandbox/sandboxScenarios";
import type { SandboxScenario } from "@/features/sandbox/sandboxScenarios";
import { getApiErrorMessage } from "@/shared/api/errors";
import { useRunSandboxScenario, useSandboxScenarios } from "@/shared/api/hooks";
import type { SandboxRunResponse } from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";

import styles from "./SandboxHost.module.css";

const SERVICE_PACKAGE_LABELS = {
  "clinical-collaboration": "临床协同",
  "quality-improvement": "质量改进",
  "engine-orchestration": "引擎编排",
} as const;

const STATUS_LABELS = {
  ready: "可运行",
  "clinical-review-required": "待临床评审",
} as const;

export default function SandboxHost() {
  const { message } = App.useApp();
  const scenariosQuery = useSandboxScenarios();
  const runMutation = useRunSandboxScenario();
  const [selectedScenarioId, setSelectedScenarioId] = useState(SANDBOX_SCENARIOS[0].id);
  const [result, setResult] = useState<SandboxRunResponse | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [latestDecision, setLatestDecision] = useState<SandboxEmbedDecision | null>(null);
  const [embedMode, setEmbedMode] = useState<SandboxEmbedMode>("IFRAME");
  const scenarios = useMemo(() => mergeSandboxCatalog(scenariosQuery.data), [scenariosQuery.data]);
  const selectedScenario: SandboxScenario =
    scenarios.find((scenario) => scenario.id === selectedScenarioId) ?? scenarios[0];
  const scenarioGroups = scenariosByServicePackage(scenarios);

  const executeRun = async (body: {
    entryMode: "SNAPSHOT";
    occurredAt: string;
    parentOrigin: string;
    integrationMode: SandboxEmbedMode;
    contextOverride?: unknown;
  }) => {
    setRunError(null);
    setLatestDecision(null);
    setResult(null);
    try {
      const response = await runMutation.mutateAsync({
        scenarioId: selectedScenario.id,
        body,
      });
      setResult(response);
      if (response.result === "PASS") {
        message.success("真实引擎链路已完成");
      } else {
        setRunError("沙盘链路未完整通过，请根据路径证据定位失败步骤");
      }
    } catch (error) {
      setRunError(getApiErrorMessage(error, "沙盘编排失败，请稍后重试"));
    }
  };

  const handleRun = async ({ numericValue, occurredAt }: SandboxDataInput) => {
    if (!isNumericScenario(selectedScenario) || selectedScenario.status !== "ready") {
      setRunError(selectedScenario.statusReason);
      return;
    }
    await executeRun({
      entryMode: "SNAPSHOT",
      occurredAt,
      parentOrigin: window.location.origin,
      integrationMode: embedMode,
      contextOverride: buildSandboxContextOverride(selectedScenario, numericValue, occurredAt),
    });
  };

  const handleOrchestrationRun = async () => {
    if (selectedScenario.status !== "ready") {
      setRunError(selectedScenario.statusReason);
      return;
    }
    await executeRun({
      entryMode: "SNAPSHOT",
      occurredAt: new Date().toISOString(),
      parentOrigin: window.location.origin,
      integrationMode: embedMode,
    });
  };

  const handleModeChange = (mode: SandboxEmbedMode) => {
    setEmbedMode(mode);
    setResult(null);
    setRunError(null);
    setLatestDecision(null);
  };

  const handleDecision = (decision: SandboxEmbedDecision) => {
    setLatestDecision(decision);
    message.success("嵌入终端决策已回传宿主面板");
  };

  return (
    <PageShell title="全真体验沙盘" description="以院内业务系统视角验证真实引擎与嵌入终端">
      <div className={styles.workspace}>
        <aside className={styles.scenarioRail} aria-label="沙盘场景">
          <div className={styles.sectionHeading}>
            <ExperimentOutlined />
            <Typography.Text strong>业务场景</Typography.Text>
          </div>
          {Object.entries(scenarioGroups).map(([servicePackage, scenarios]) => (
            <section key={servicePackage} className={styles.scenarioGroup}>
              <Typography.Text type="secondary" className={styles.groupLabel}>
                {SERVICE_PACKAGE_LABELS[servicePackage as keyof typeof SERVICE_PACKAGE_LABELS]}
              </Typography.Text>
              <div className={styles.scenarioList}>
                {scenarios.map((scenario) => (
                  <Button
                    key={scenario.id}
                    type={scenario.id === selectedScenario.id ? "primary" : "default"}
                    className={styles.scenarioButton}
                    onClick={() => {
                      setSelectedScenarioId(scenario.id);
                      setResult(null);
                      setRunError(null);
                      setLatestDecision(null);
                      setEmbedMode("IFRAME");
                    }}
                  >
                    <span>{scenario.title}</span>
                    <Tag color={scenario.status === "ready" ? "success" : "default"}>
                      {STATUS_LABELS[scenario.status]}
                    </Tag>
                  </Button>
                ))}
              </div>
            </section>
          ))}
          <Typography.Paragraph type="secondary" className={styles.scenarioNarrative}>
            {selectedScenario.narrative}
          </Typography.Paragraph>
        </aside>

        <main className={styles.mainArea}>
          <section className={styles.statusStrip} aria-label="场景验收目标">
            <div>
              <Typography.Text type="secondary">预期规则</Typography.Text>
              <Typography.Text code>
                {selectedScenario.expectedRuleCode || selectedScenario.playbook}
              </Typography.Text>
            </div>
            <div>
              <Typography.Text type="secondary">预期动作</Typography.Text>
              <Tag color="warning">{selectedScenario.expectedAction}</Tag>
            </div>
            <div>
              <Typography.Text type="secondary">风险等级</Typography.Text>
              <Tag color="error">{selectedScenario.expectedSeverity}</Tag>
            </div>
            <Tag
              icon={
                result?.result === "PASS" ? <CheckCircleOutlined /> : <SafetyCertificateOutlined />
              }
              color={result?.result === "PASS" ? "success" : "processing"}
            >
              {result?.result === "PASS" ? "真实链路通过" : "等待运行"}
            </Tag>
          </section>

          {runError && <Alert type="error" showIcon message={runError} />}
          {scenariosQuery.isError && (
            <Alert
              type="warning"
              showIcon
              message="后端场景目录暂不可用"
              description="当前使用前端内置受控目录兜底，不伪装远端目录已同步。"
            />
          )}

          {result && (
            <section className={styles.runSummary} aria-label="运行证据摘要">
              <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }}>
                <Descriptions.Item label="追踪链路">{result.traceId}</Descriptions.Item>
                <Descriptions.Item label="上下文快照">
                  {result.snapshotId || "未生成"}
                </Descriptions.Item>
                <Descriptions.Item label="触发标识">
                  {result.triggerId || "未生成"}
                </Descriptions.Item>
                <Descriptions.Item label="推荐卡">{result.cardCount}</Descriptions.Item>
                {result.patientPathwayId && (
                  <Descriptions.Item label="患者路径">{result.patientPathwayId}</Descriptions.Item>
                )}
                {result.followupPlanId && (
                  <Descriptions.Item label="随访计划">{result.followupPlanId}</Descriptions.Item>
                )}
                {result.evaluationRunId && (
                  <Descriptions.Item label="评估运行">{result.evaluationRunId}</Descriptions.Item>
                )}
              </Descriptions>
            </section>
          )}

          {selectedScenario.status === "ready" ? (
            <div className={styles.hostGrid}>
              {isNumericScenario(selectedScenario) ? (
                <SandboxDataEntry
                  scenario={selectedScenario}
                  running={runMutation.isPending}
                  onRun={handleRun}
                />
              ) : (
                <section
                  className={styles.orchestrationPanel}
                  aria-labelledby="sandbox-orchestration-title"
                >
                  <Typography.Title id="sandbox-orchestration-title" level={5}>
                    引擎编排入口
                  </Typography.Title>
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label="剧本">{selectedScenario.playbook}</Descriptions.Item>
                    <Descriptions.Item label="引擎">{selectedScenario.engine}</Descriptions.Item>
                    {selectedScenario.expectedAssetCode && (
                      <Descriptions.Item label="依赖资产">
                        {selectedScenario.expectedAssetCode}
                      </Descriptions.Item>
                    )}
                  </Descriptions>
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    aria-label="运行真实引擎链路"
                    loading={runMutation.isPending}
                    onClick={handleOrchestrationRun}
                  >
                    运行真实引擎链路
                  </Button>
                </section>
              )}
              <SandboxEmbedFrame
                embedUrl={result?.embedUrl}
                embedToken={result?.embedToken}
                mode={embedMode}
                onModeChange={handleModeChange}
                onDecision={handleDecision}
              />
            </div>
          ) : (
            <Alert
              type="warning"
              showIcon
              message={STATUS_LABELS[selectedScenario.status]}
              description={selectedScenario.statusReason}
            />
          )}

          {latestDecision && (
            <Alert
              type="success"
              showIcon
              message={`宿主已收到 ${latestDecision.action} 决策`}
              description={
                <Space size="large" wrap>
                  <span>卡片：{latestDecision.cardId || "未返回"}</span>
                  <span>状态：{latestDecision.recommendationStatus || "已记录"}</span>
                  <span>追踪链路：{latestDecision.traceId || result?.traceId || "未返回"}</span>
                </Space>
              }
            />
          )}

          <SandboxPathInspector steps={result?.steps ?? []} />
        </main>
      </div>
    </PageShell>
  );
}
