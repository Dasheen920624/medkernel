import { useMemo, useState } from "react";
import { Alert, App, Button, Descriptions, Input, Radio, Space, Tag, Typography } from "antd";
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
import {
  useRunSandboxScenario,
  useSandboxRuntimeStatus,
  useSandboxScenarios,
} from "@/shared/api/hooks";
import type {
  SandboxResolutionSource,
  SandboxRunMode,
  SandboxRunRequest,
  SandboxRunResponse,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";

import styles from "./SandboxHost.module.css";

const SERVICE_PACKAGE_LABELS = {
  "clinical-collaboration": "临床协同",
  "quality-improvement": "质量改进",
  "engine-orchestration": "引擎编排",
} as const;

const RESOLUTION_SOURCE_LABELS: Record<SandboxResolutionSource, string> = {
  TENANT_PACKAGE: "演练机构规则",
  PLATFORM_PACKAGE: "平台主源规则",
  REPLAY_MANIFEST: "历史重放清单",
} as const;

export default function SandboxHost() {
  const { message } = App.useApp();
  const scenariosQuery = useSandboxScenarios();
  const runtimeQuery = useSandboxRuntimeStatus();
  const runMutation = useRunSandboxScenario();
  const [selectedScenarioId, setSelectedScenarioId] = useState(SANDBOX_SCENARIOS[0].id);
  const [result, setResult] = useState<SandboxRunResponse | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [latestDecision, setLatestDecision] = useState<SandboxEmbedDecision | null>(null);
  const [embedMode, setEmbedMode] = useState<SandboxEmbedMode>("IFRAME");
  const [runMode, setRunMode] = useState<SandboxRunMode>("CURRENT");
  const [replayCaseId, setReplayCaseId] = useState("");
  const scenarios = useMemo(() => mergeSandboxCatalog(scenariosQuery.data), [scenariosQuery.data]);
  const selectedScenario: SandboxScenario =
    scenarios.find((scenario) => scenario.id === selectedScenarioId) ?? scenarios[0];
  const scenarioGroups = scenariosByServicePackage(scenarios);
  const runtimeStatus = runtimeQuery.data;
  const runtimeReady = runtimeStatus?.ready === true;
  const replayReady = replayCaseId.trim().length > 0;
  const scenarioRunnable =
    selectedScenario.status === "runtime-check" &&
    (runMode === "CURRENT" ? runtimeReady : replayReady);
  const runtimeSourceLabel = runtimeStatus?.resolutionSource
    ? RESOLUTION_SOURCE_LABELS[runtimeStatus.resolutionSource]
    : "尚未解析";
  const currentBindingLabel = runtimeStatus?.ready
    ? `${runtimeStatus.packageCode}@${runtimeStatus.packageVersion}`
    : "未就绪";
  const selectedBindingLabel =
    runMode === "CURRENT" ? currentBindingLabel : replayCaseId.trim() || "待输入";

  const scenarioStatusLabel = (scenario: SandboxScenario) => {
    if (scenario.status === "catalog-unavailable") return "目录不可用";
    if (runMode !== "CURRENT") return replayReady ? "可重放" : "待选清单";
    if (runtimeQuery.isLoading) return "校验中";
    return runtimeReady ? "可运行" : "基线未就绪";
  };

  const executeRun = async (body: SandboxRunRequest) => {
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

  const handleHistoricalRun = async () => {
    const normalizedReplayCaseId = replayCaseId.trim();
    if (!normalizedReplayCaseId) {
      setRunError("请输入历史重放清单标识");
      return;
    }
    await executeRun({
      entryMode: "SNAPSHOT",
      mode: runMode,
      replayCaseId: normalizedReplayCaseId,
    });
  };

  const handleRunModeChange = (mode: SandboxRunMode) => {
    setRunMode(mode);
    setResult(null);
    setRunError(null);
    setLatestDecision(null);
  };

  const handleRun = async ({ numericValue, occurredAt }: SandboxDataInput) => {
    if (!isNumericScenario(selectedScenario) || !scenarioRunnable) {
      setRunError(runtimeStatus?.reason || selectedScenario.statusReason);
      return;
    }
    await executeRun({
      entryMode: "SNAPSHOT",
      mode: "CURRENT",
      occurredAt,
      parentOrigin: window.location.origin,
      integrationMode: embedMode,
      contextOverride: buildSandboxContextOverride(selectedScenario, numericValue, occurredAt),
    });
  };

  const handleOrchestrationRun = async () => {
    if (!scenarioRunnable) {
      setRunError(runtimeStatus?.reason || selectedScenario.statusReason);
      return;
    }
    await executeRun({
      entryMode: "SNAPSHOT",
      mode: "CURRENT",
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
                      setReplayCaseId("");
                    }}
                  >
                    <span>{scenario.title}</span>
                    <Tag
                      color={
                        scenario.status === "runtime-check" && runtimeReady ? "success" : "default"
                      }
                    >
                      {scenarioStatusLabel(scenario)}
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
          <section className={styles.runtimePanel} aria-label="沙盘运行基线">
            <Space direction="vertical" size="small">
              <Typography.Text strong>运行口径</Typography.Text>
              <Radio.Group
                aria-label="沙盘运行模式"
                optionType="button"
                buttonStyle="solid"
                value={runMode}
                onChange={(event) => handleRunModeChange(event.target.value as SandboxRunMode)}
                options={[
                  { label: "当前规则", value: "CURRENT" },
                  { label: "历史原样重放", value: "HISTORICAL_EXACT" },
                  { label: "新旧对比", value: "COMPARE", disabled: true },
                ]}
              />
            </Space>
            <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 5 }}>
              <Descriptions.Item label="运行模式">
                <Tag color="processing">{runMode}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="规则来源">
                {runMode === "CURRENT" ? runtimeSourceLabel : "历史重放清单"}
              </Descriptions.Item>
              <Descriptions.Item label={runMode === "CURRENT" ? "当前绑定" : "重放清单"}>
                {selectedBindingLabel}
              </Descriptions.Item>
              <Descriptions.Item label="有效资产">
                {runMode === "CURRENT" ? (runtimeStatus?.assetCount ?? 0) : "运行时验真"}
              </Descriptions.Item>
              <Descriptions.Item label="安全边界">
                <Tag color="success">外部副作用已关闭</Tag>
              </Descriptions.Item>
            </Descriptions>
          </section>

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
          {runMode === "CURRENT" && runtimeQuery.isError && (
            <Alert
              type="warning"
              showIcon
              message="运行基线状态暂不可用"
              description="当前不开放运行，避免在未冻结规则来源时产生误导结果。"
            />
          )}
          {runMode === "CURRENT" &&
            !runtimeQuery.isLoading &&
            runtimeStatus &&
            !runtimeStatus.ready && (
              <Alert
                type="warning"
                showIcon
                message="运行基线未就绪"
                description={runtimeStatus.reason || "演练机构尚未建立可运行绑定。"}
              />
            )}
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
                <Descriptions.Item label="运行标识">{result.runId}</Descriptions.Item>
                <Descriptions.Item label="冻结基线">{result.baselineId}</Descriptions.Item>
                <Descriptions.Item label="运行模式">{result.mode}</Descriptions.Item>
                <Descriptions.Item label="规则来源">
                  {RESOLUTION_SOURCE_LABELS[result.resolutionSource]}
                </Descriptions.Item>
                {result.replayCaseId && (
                  <Descriptions.Item label="重放清单">{result.replayCaseId}</Descriptions.Item>
                )}
                <Descriptions.Item label="解析版本">
                  {result.resolvedPackageVersion}
                </Descriptions.Item>
                <Descriptions.Item label="安全边界">
                  {result.externalSideEffects ? "外部副作用未关闭" : "外部副作用已关闭"}
                </Descriptions.Item>
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

          {result?.replayRuleResults && result.replayRuleResults.length > 0 && (
            <section className={styles.runSummary} aria-label="历史规则结果">
              <Typography.Title level={5}>历史规则结果</Typography.Title>
              <Space direction="vertical" size="middle">
                {result.replayRuleResults.map((rule) => (
                  <div key={`${rule.ruleCode}:${rule.versionId}`}>
                    <Space wrap>
                      <Typography.Text strong>{rule.ruleName}</Typography.Text>
                      <Typography.Text code>
                        {rule.ruleCode}@{rule.assetVersion}
                      </Typography.Text>
                      <Tag>{rule.historicalStatus}</Tag>
                      <Tag color={rule.hit ? "error" : "default"}>
                        {rule.hit ? "命中" : "未命中"}
                      </Tag>
                      {rule.severity && <Tag color="warning">{rule.severity}</Tag>}
                    </Space>
                    {rule.actions.map((action, index) => (
                      <Typography.Paragraph key={`${rule.versionId}:action:${index}`}>
                        {action.summary}
                      </Typography.Paragraph>
                    ))}
                  </div>
                ))}
              </Space>
            </section>
          )}

          {selectedScenario.status === "runtime-check" && runMode !== "CURRENT" && (
            <section className={styles.orchestrationPanel} aria-labelledby="sandbox-replay-title">
              <Typography.Title id="sandbox-replay-title" level={5}>
                历史重放清单
              </Typography.Title>
              <Typography.Paragraph type="secondary">
                按不可变清单装载 D4 脱敏上下文与精确历史规则版本；不读取当前规则，不产生业务写回。
              </Typography.Paragraph>
              <Space direction="vertical">
                <Input
                  aria-label="历史重放清单标识"
                  placeholder="例如 replay-2025-001"
                  value={replayCaseId}
                  onChange={(event) => setReplayCaseId(event.target.value)}
                />
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  aria-label="按清单原样重放"
                  loading={runMutation.isPending}
                  disabled={!scenarioRunnable || runMode === "COMPARE"}
                  onClick={handleHistoricalRun}
                >
                  按清单原样重放
                </Button>
              </Space>
            </section>
          )}

          {scenarioRunnable && runMode === "CURRENT" && (
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
          )}
          {!scenarioRunnable && selectedScenario.status === "catalog-unavailable" && (
            <Alert
              type="warning"
              showIcon
              message="目录不可用"
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
