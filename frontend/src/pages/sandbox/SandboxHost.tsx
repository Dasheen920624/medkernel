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
  scenariosByServiceLine,
} from "@/features/sandbox/sandboxScenarios";
import type { SandboxScenario } from "@/features/sandbox/sandboxScenarios";
import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useRunSandboxScenario,
  useSandboxRuntimeStatus,
  useSandboxScenarios,
  useSecurityProfile,
} from "@/shared/api/hooks";
import type {
  SandboxResolutionSource,
  SandboxRuleDifferenceType,
  SandboxRunMode,
  SandboxRunRequest,
  SandboxRunResponse,
} from "@/shared/api/hooks";
import { findRouteByPath } from "@/shared/config/routes";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import type { RouteExperience } from "@/shared/ui/experienceTypes";

import styles from "./SandboxHost.module.css";

const SERVICE_LINE_LABELS = {
  "clinical-collaboration": "临床协同",
  "quality-improvement": "质量改进",
  "engine-orchestration": "引擎编排",
} as const;

const RESOLUTION_SOURCE_LABELS: Record<SandboxResolutionSource, string> = {
  CURRENT_RUNTIME_RELEASE: "当前机构生效版本",
  REPLAY_MANIFEST: "历史重放清单",
} as const;

const DIFFERENCE_LABELS: Record<SandboxRuleDifferenceType, string> = {
  NEW_HIT: "新增命中",
  NO_LONGER_HIT: "取消命中",
  SEVERITY_INCREASED: "严重度升高",
  SEVERITY_DECREASED: "严重度降低",
  ACTION_CHANGED: "动作变化",
  SOURCE_CHANGED: "来源变化",
  VERSION_CHANGED: "版本或摘要变化",
  ASSET_MISSING: "资产缺失",
};

const RUN_MODE_LABELS: Record<SandboxRunMode, string> = {
  CURRENT: "当前机构版本",
  HISTORICAL_EXACT: "历史原样重放",
  COMPARE: "版本差异评估",
};

const ACTION_LABELS: Record<string, string> = {
  STRONG_REMINDER: "强提醒",
  REMIND: "提醒",
  SUGGEST_ORDER: "建议处置",
  CATALOG_REQUIRED: "等待场景目录",
};

const SEVERITY_LABELS: Record<string, string> = {
  CRITICAL: "危急风险",
  HIGH: "高风险",
  MEDIUM: "中风险",
  LOW: "低风险",
};

const HISTORICAL_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "当时生效",
  ENABLED: "当时生效",
  RETIRED: "历史已退役",
  DISABLED: "当时停用",
  ARCHIVED: "历史已归档",
  DRAFT: "当时草稿",
};

const RULE_SOURCE_TIER_LABELS: Record<string, string> = {
  PLATFORM: "平台标准",
  ORG: "机构版本",
  TENANT: "机构版本",
  HOSPITAL: "医院版本",
  DEPARTMENT: "科室版本",
};

const route = findRouteByPath("/sandbox");

if (!route?.experience) {
  throw new Error("全真体验沙盘缺少体验声明");
}

const PAGE_META: { title: string; experience: RouteExperience } = {
  title: route.title,
  experience: route.experience,
};

function isRunModeReady(mode: SandboxRunMode, runtimeReady: boolean, replayReady: boolean) {
  if (mode === "CURRENT") return runtimeReady;
  if (mode === "COMPARE") return runtimeReady && replayReady;
  return replayReady;
}

function runtimeLabel(mode: SandboxRunMode, replayCaseId: string, currentRuntimeLabel: string) {
  if (mode === "CURRENT") return currentRuntimeLabel;
  if (mode === "COMPARE") {
    return `${replayCaseId.trim() || "待输入"} ↔ ${currentRuntimeLabel}`;
  }
  return replayCaseId.trim() || "待输入";
}

function sourceLabel(mode: SandboxRunMode, runtimeSourceLabel: string) {
  if (mode === "CURRENT") return runtimeSourceLabel;
  if (mode === "COMPARE") return `历史重放清单 ↔ ${runtimeSourceLabel}`;
  return "历史重放清单";
}

function actionLabel(value: string) {
  return ACTION_LABELS[value] ?? "引擎处置建议";
}

function severityLabel(value: string) {
  return SEVERITY_LABELS[value] ?? "风险待核查";
}

function evidenceLabel(
  value: string | null | undefined,
  labels: Record<string, string>,
  fallback: string,
  evidenceDetailsEnabled: boolean,
) {
  if (!value) return fallback;
  const label = labels[value] ?? fallback;
  return evidenceDetailsEnabled ? `${label}（${value}）` : label;
}

function severityDisplay(value: string | null | undefined, evidenceDetailsEnabled: boolean) {
  return evidenceLabel(value, SEVERITY_LABELS, "风险待核查", evidenceDetailsEnabled);
}

function historicalStatusDisplay(value: string | null | undefined, evidenceDetailsEnabled: boolean) {
  return evidenceLabel(value, HISTORICAL_STATUS_LABELS, "历史状态待核查", evidenceDetailsEnabled);
}

function ruleSourceTierDisplay(value: string | null | undefined, evidenceDetailsEnabled: boolean) {
  return evidenceLabel(value, RULE_SOURCE_TIER_LABELS, "来源待核查", evidenceDetailsEnabled);
}

type ComparableRuleSide = NonNullable<
  NonNullable<SandboxRunResponse["comparison"]>["differences"][number]["historical"]
>;

function comparisonSideDisplay(side: ComparableRuleSide, evidenceDetailsEnabled: boolean) {
  return `${ruleSourceTierDisplay(side.sourceTier, evidenceDetailsEnabled)} 第 ${
    side.assetVersion
  } 版 / ${side.hit ? "命中" : "未命中"}`;
}

function differenceColor(change: SandboxRuleDifferenceType) {
  if (change === "SEVERITY_INCREASED" || change === "NEW_HIT") return "error";
  if (change === "ASSET_MISSING") return "warning";
  return "processing";
}

export default function SandboxHost() {
  const { message } = App.useApp();
  const security = useSecurityProfile();
  const scenariosQuery = useSandboxScenarios();
  const runtimeQuery = useSandboxRuntimeStatus();
  const runMutation = useRunSandboxScenario();
  const evidenceDetailsEnabled = useEvidenceDetailsStore((state) => state.enabled);
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
  const scenarioGroups = scenariosByServiceLine(scenarios);
  const runtimeStatus = runtimeQuery.data;
  const runtimeReady = runtimeStatus?.ready === true;
  const replayReady = replayCaseId.trim().length > 0;
  const scenarioRunnable =
    selectedScenario.status === "runtime-check" &&
    isRunModeReady(runMode, runtimeReady, replayReady);
  const runtimeSourceLabel = runtimeStatus?.resolutionSource
    ? RESOLUTION_SOURCE_LABELS[runtimeStatus.resolutionSource]
    : "尚未解析";
  const currentRuntimeLabel = runtimeStatus?.ready
    ? `第 ${runtimeStatus.runtimeRevisionNo ?? "?"} 版 · ${
        runtimeStatus.runtimeReleaseId ?? "未知生效版本"
      }`
    : "未就绪";
  const selectedRuntimeLabel = runtimeLabel(runMode, replayCaseId, currentRuntimeLabel);

  const scenarioStatusLabel = (scenario: SandboxScenario) => {
    if (scenario.status === "catalog-unavailable") return "目录不可用";
    if (runMode === "HISTORICAL_EXACT") return replayReady ? "可重放" : "待选清单";
    if (runMode === "COMPARE") {
      return replayReady && runtimeReady ? "可对比" : "待清单或当前基线";
    }
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
      setRunError("请输入历史演练清单");
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
    <PageExperienceShell meta={PAGE_META} securityProfile={security.data}>
      <div className={styles.workspace}>
        <aside className={styles.scenarioRail} aria-label="沙盘场景">
          <div className={styles.sectionHeading}>
            <ExperimentOutlined />
            <Typography.Text strong>业务场景</Typography.Text>
          </div>
          {Object.entries(scenarioGroups).map(([serviceLine, scenarios]) => (
            <section key={serviceLine} className={styles.scenarioGroup}>
              <Typography.Text type="secondary" className={styles.groupLabel}>
                {SERVICE_LINE_LABELS[serviceLine as keyof typeof SERVICE_LINE_LABELS]}
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
                  { label: "版本差异评估", value: "COMPARE" },
                ]}
              />
            </Space>
            <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 5 }}>
              <Descriptions.Item label="运行模式">
                <Tag color="processing">{RUN_MODE_LABELS[runMode]}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="规则来源">
                {sourceLabel(runMode, runtimeSourceLabel)}
              </Descriptions.Item>
              <Descriptions.Item label={runMode === "CURRENT" ? "当前修订" : "重放清单"}>
                {selectedRuntimeLabel}
              </Descriptions.Item>
              <Descriptions.Item label="有效资产">
                {runMode === "HISTORICAL_EXACT" ? "运行时验真" : (runtimeStatus?.assetCount ?? 0)}
              </Descriptions.Item>
              <Descriptions.Item label="安全边界">
                <Tag color="success">外部副作用已关闭</Tag>
              </Descriptions.Item>
            </Descriptions>
          </section>

          <section className={styles.statusStrip} aria-label="场景验收目标">
            <div>
              <Typography.Text type="secondary">验收重点</Typography.Text>
              <Typography.Text>
                {selectedScenario.expectedRuleCode ? "规则命中" : "综合推荐"}
              </Typography.Text>
            </div>
            <div>
              <Typography.Text type="secondary">预期动作</Typography.Text>
              <Tag color="warning">{actionLabel(selectedScenario.expectedAction)}</Tag>
            </div>
            <div>
              <Typography.Text type="secondary">风险等级</Typography.Text>
              <Tag color="error">{severityLabel(selectedScenario.expectedSeverity)}</Tag>
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
                message="机构生效版本未就绪"
                description={runtimeStatus.reason || "演练机构尚未发布可用版本。"}
              />
            )}
          {scenariosQuery.isError && (
            <Alert
              type="warning"
              showIcon
              message="沙盘场景目录暂不可用"
              description="当前仅展示目录未就绪状态，不生成或暗示可运行临床场景。"
            />
          )}

          {result && (
            <section className={styles.runSummary} aria-label="运行证据摘要">
              <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }}>
                <Descriptions.Item label="运行结论">
                  {result.result === "PASS" ? "真实链路已完成" : "链路未完成"}
                </Descriptions.Item>
                <Descriptions.Item label="运行模式">{RUN_MODE_LABELS[result.mode]}</Descriptions.Item>
                <Descriptions.Item label="规则来源">
                  {RESOLUTION_SOURCE_LABELS[result.resolutionSource]}
                </Descriptions.Item>
                {result.replayCaseId && (
                  <Descriptions.Item label="重放清单">{result.replayCaseId}</Descriptions.Item>
                )}
                <Descriptions.Item label="机构生效版本">
                  {result.runtimeReleaseRef
                    ? `${result.runtimeReleaseRef}${
                        result.runtimeRevisionNo ? ` · 第 ${result.runtimeRevisionNo} 版` : ""
                      }`
                    : "未记录"}
                </Descriptions.Item>
                <Descriptions.Item label="安全边界">
                  {result.externalSideEffects ? "外部副作用未关闭" : "外部副作用已关闭"}
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
                {evidenceDetailsEnabled && (
                  <>
                    <Descriptions.Item label="演练编号">{result.runId}</Descriptions.Item>
                    <Descriptions.Item label="当前标准版本">{result.baselineId}</Descriptions.Item>
                    <Descriptions.Item label="追踪号">{result.traceId}</Descriptions.Item>
                    <Descriptions.Item label="上下文快照">
                      {result.snapshotId || "未生成"}
                    </Descriptions.Item>
                    <Descriptions.Item label="触发标识">
                      {result.triggerId || "未生成"}
                    </Descriptions.Item>
                  </>
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
                      <Typography.Text>{`历史版本 ${rule.assetVersion}`}</Typography.Text>
                      {evidenceDetailsEnabled && (
                        <Typography.Text code>
                          {rule.ruleCode}@{rule.assetVersion}
                        </Typography.Text>
                      )}
                      <Tag>{historicalStatusDisplay(rule.historicalStatus, evidenceDetailsEnabled)}</Tag>
                      <Tag color={rule.hit ? "error" : "default"}>
                        {rule.hit ? "命中" : "未命中"}
                      </Tag>
                      {rule.severity && (
                        <Tag color="warning">{severityDisplay(rule.severity, evidenceDetailsEnabled)}</Tag>
                      )}
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

          {result?.comparison && (
            <section className={styles.runSummary} aria-label="规则版本差异">
              <Typography.Title level={5}>规则版本差异</Typography.Title>
              <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 5 }}>
                <Descriptions.Item label="差异规则">
                  {result.comparison.summary.differenceCount}
                </Descriptions.Item>
                <Descriptions.Item label="新增命中">
                  {result.comparison.summary.newHitCount}
                </Descriptions.Item>
                <Descriptions.Item label="取消命中">
                  {result.comparison.summary.noLongerHitCount}
                </Descriptions.Item>
                <Descriptions.Item label="高风险变化">
                  {result.comparison.summary.highRiskChangeCount}
                </Descriptions.Item>
                <Descriptions.Item label="未变化（已折叠）">
                  {result.comparison.unchangedCount}
                </Descriptions.Item>
              </Descriptions>
              <Space direction="vertical" size="middle">
                {result.comparison.differences.map((difference) => (
                  <div key={difference.ruleCode}>
                    <Space wrap>
                      <Typography.Text strong>{difference.ruleName}</Typography.Text>
                      {evidenceDetailsEnabled && (
                        <Typography.Text code>{difference.ruleCode}</Typography.Text>
                      )}
                      {difference.changes.map((change) => (
                        <Tag key={change} color={differenceColor(change)}>
                          {DIFFERENCE_LABELS[change]}
                        </Tag>
                      ))}
                    </Space>
                    {!difference.comparable && (
                      <Typography.Paragraph type="warning">
                        {difference.nonComparableReason}
                      </Typography.Paragraph>
                    )}
                    {difference.comparable && (
                      <Typography.Paragraph type="secondary">
                        历史：
                        {difference.historical
                          ? comparisonSideDisplay(difference.historical, evidenceDetailsEnabled)
                          : "缺失"}
                        {"；"}当前：
                        {difference.current
                          ? comparisonSideDisplay(difference.current, evidenceDetailsEnabled)
                          : "缺失"}
                      </Typography.Paragraph>
                    )}
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
                {runMode === "COMPARE"
                  ? "以不可变清单中的 D4 脱敏上下文，同时执行历史精确版本与当前冻结标准版本；只生成差异证据，不产生业务写回。"
                  : "按不可变清单装载 D4 脱敏上下文与精确历史规则版本；不读取当前规则，不产生业务写回。"}
              </Typography.Paragraph>
              <Space direction="vertical">
                <Input
                  aria-label="历史演练清单"
                  placeholder="例如 高钾规则历史演练清单"
                  value={replayCaseId}
                  onChange={(event) => setReplayCaseId(event.target.value)}
                />
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  aria-label={runMode === "COMPARE" ? "运行版本差异评估" : "按清单原样重放"}
                  loading={runMutation.isPending}
                  disabled={!scenarioRunnable}
                  onClick={handleHistoricalRun}
                >
                  {runMode === "COMPARE" ? "运行版本差异评估" : "按清单原样重放"}
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
                  <span>追踪号：{latestDecision.traceId || result?.traceId || "未返回"}</span>
                </Space>
              }
            />
          )}

          <SandboxPathInspector
            steps={result?.steps ?? []}
            evidenceDetailsEnabled={evidenceDetailsEnabled}
          />
        </main>
      </div>
    </PageExperienceShell>
  );
}
