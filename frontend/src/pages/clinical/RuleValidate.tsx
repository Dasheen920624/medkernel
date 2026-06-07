import { useState } from "react";
import {
  Row,
  Col,
  AutoComplete,
  Card,
  Input,
  Modal,
  Pagination,
  Select,
  Button,
  Table,
  Tag,
  Descriptions,
  Alert,
  message,
  Drawer,
} from "antd";
import type { TableProps } from "antd";
import {
  PlayCircleOutlined,
  BugOutlined,
  CompassOutlined,
  FileTextOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { ContextSnapshotSelector } from "@/shared/ui/ContextSnapshotSelector";
import {
  useContextSnapshotDetail,
  useContextSnapshots,
  useCaptureRuleOverride,
  useEvaluateRules,
  useRuleExecutions,
  useRuleExecutionExplain,
} from "@/shared/api/hooks";
import type { RuleEvaluationItem, RuleEvaluateResponse } from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import {
  CLINICAL_TRIGGER_POINT_OPTIONS,
  type ClinicalTriggerPoint,
} from "@/shared/config/clinicalTriggerPoints";
import styles from "./Clinical.module.css";

function isCriticalSeverity(severity?: string | null) {
  return severity === "CRITICAL";
}

function isRedlineActionCode(actionCode?: string | null) {
  return actionCode === "BLOCK";
}

function isRedlineEvaluationItem(item: RuleEvaluationItem) {
  return (
    isCriticalSeverity(item.severity) ||
    item.ruleId.startsWith("RDL-") ||
    item.actions.some((action) => isRedlineActionCode(action.actionCode))
  );
}

function severityColor(severity?: string | null) {
  const colors: Record<string, string> = {
    CRITICAL: "red",
    HIGH: "red",
    MEDIUM: "orange",
    LOW: "green",
  };
  return colors[severity ?? ""] ?? "default";
}

function executionStatusColor(status: string) {
  if (status === "SUCCESS") return "green";
  if (status === "SUPPRESSED") return "orange";
  if (status === "DEDUPLICATED") return "blue";
  if (status === "FAILED") return "red";
  return "default";
}

function explanationSummary(value: unknown) {
  if (typeof value === "string") return value;
  if (!value || typeof value !== "object") return "暂无解释";
  const explanation = value as Record<string, unknown>;
  const title = typeof explanation.title === "string" ? explanation.title.trim() : "";
  const reason = typeof explanation.reason === "string" ? explanation.reason.trim() : "";
  return [title, reason].filter(Boolean).join("：") || "查看执行解释获取完整证据";
}

type OverrideTarget = {
  executionId: string;
  actionCode: "BLOCK" | "STRONG_REMINDER";
  suggestions: string[];
};

export default function RuleValidate() {
  const [triggerPoint, setTriggerPoint] = useState<ClinicalTriggerPoint>("order-sign");
  const [snapshotPatientId, setSnapshotPatientId] = useState<string>("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState<string>("");
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string>("");
  const [replayExecutionId, setReplayExecutionId] = useState<string>("");
  const [executionPage, setExecutionPage] = useState(1);

  const [evaluateResponse, setEvaluateResponse] = useState<RuleEvaluateResponse | null>(null);
  const [selectedExecutionId, setSelectedExecutionId] = useState<string | null>(null);
  const [overrideTarget, setOverrideTarget] = useState<OverrideTarget | null>(null);
  const [overrideReason, setOverrideReason] = useState("");

  const evaluateMutation = useEvaluateRules();
  const captureOverrideMutation = useCaptureRuleOverride();
  const hasSnapshotFilter = Boolean(snapshotPatientId.trim() || snapshotEncounterId.trim());
  const snapshotsQuery = useContextSnapshots(
    {
      patientId: snapshotPatientId.trim() || undefined,
      encounterId: snapshotEncounterId.trim() || undefined,
      status: "ACTIVE",
      page: 1,
      size: 20,
      sort: "createdAt,desc",
    },
    { enabled: hasSnapshotFilter },
  );
  const snapshotDetailQuery = useContextSnapshotDetail(selectedSnapshotId, {
    enabled: Boolean(selectedSnapshotId),
  });
  const executionsQuery = useRuleExecutions({ page: executionPage, size: 20 });
  const { data: explainData, isLoading: explainLoading } = useRuleExecutionExplain(
    selectedExecutionId || "",
  );
  const executionOptions = (executionsQuery.data?.items ?? []).map((execution) => ({
    value: execution.executionId,
    label: `${execution.ruleId} · ${execution.triggerPoint} · ${executionStatusLabel(execution.status)} · ${formatTime(execution.executedAt)}`,
  }));

  const handleEvaluate = async () => {
    try {
      if (!selectedSnapshotId) {
        message.error("请先选择 ACTIVE 临床快照");
        return;
      }
      const packageVersion = snapshotDetailQuery.data?.packageVersion?.trim();
      if (!packageVersion) {
        message.error("所选临床快照缺少配置包版本，不能执行规则");
        return;
      }

      const res = await evaluateMutation.mutateAsync({
        triggerPoint,
        packageVersion,
        contextSnapshotId: selectedSnapshotId,
      });

      setEvaluateResponse(res);
      message.success("批量规则匹配评估成功！");
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "批量规则评估失败"));
    }
  };

  const handleReplayExecution = () => {
    const executionId = replayExecutionId.trim();
    if (!executionId) {
      message.error("请选择历史执行记录");
      return;
    }
    setSelectedExecutionId(executionId);
  };

  const handleCaptureOverride = async () => {
    const reason = overrideReason.trim();
    if (!overrideTarget || !reason) {
      message.error("请填写临床人工继续理由");
      return;
    }
    try {
      await captureOverrideMutation.mutateAsync({
        executionId: overrideTarget.executionId,
        actionCode: overrideTarget.actionCode,
        reason,
      });
      message.success("人工继续理由已留痕");
      setOverrideTarget(null);
      setOverrideReason("");
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "人工继续理由留痕失败"));
    }
  };

  const renderJson = (value: unknown) => {
    if (typeof value === "string") return value;
    if (value === null || value === undefined) return "暂无解释。";
    return JSON.stringify(value, null, 2);
  };

  const columns: TableProps<RuleEvaluationItem>["columns"] = [
    {
      title: "规则 ID",
      dataIndex: "ruleId",
      key: "ruleId",
      width: 190,
      render: (text: string) => <span className={styles.tableCode}>{text}</span>,
    },
    {
      title: "版本 ID",
      dataIndex: "versionId",
      key: "versionId",
      className: styles.textStrong,
      width: 180,
      render: (text: string) => <span>{text || "未返回版本"}</span>,
    },
    {
      title: "执行状态",
      dataIndex: "status",
      key: "status",
      width: 170,
      render: (status: string, record: RuleEvaluationItem) => (
        <div>
          <Tag color={executionStatusColor(status)}>{executionStatusLabel(status)}</Tag>
          {status === "SUPPRESSED" && record.suppressedBy && (
            <div className={styles.textMuted}>由 {record.suppressedBy} 抑制</div>
          )}
          {status === "DEDUPLICATED" && record.deduplicatedFromExecutionId && (
            <div className={styles.textMuted}>首次执行 {record.deduplicatedFromExecutionId}</div>
          )}
        </div>
      ),
    },
    {
      title: "警示严重度",
      dataIndex: "severity",
      key: "severity",
      width: 110,
      render: (level: string) => {
        return <Tag color={severityColor(level)}>{level || "无"}</Tag>;
      },
    },
    {
      title: "处置动作",
      key: "actions",
      width: 125,
      render: (_value: unknown, record: RuleEvaluationItem) => {
        const actionCodes = record.actions.map((action) => action.actionCode).filter(Boolean);
        return actionCodes.length > 0 ? (
          <div className={styles.tagRow}>
            {actionCodes.map((code) => (
              <Tag color={isRedlineActionCode(code) ? "red" : "blue"} key={code}>
                {code}
              </Tag>
            ))}
          </div>
        ) : (
          <Tag>未返回动作</Tag>
        );
      },
    },
    {
      title: "确认要求",
      key: "confirmation",
      width: 130,
      render: (_value: unknown, record: RuleEvaluationItem) => {
        if (record.status !== "SUCCESS") {
          return <Tag>不适用</Tag>;
        }
        return record.actions.some((action) => action.requiresPhysicianConfirmation) ? (
          <Tag color="red">必须医师确认</Tag>
        ) : (
          <Tag>无需额外确认</Tag>
        );
      },
    },
    {
      title: "命中解释",
      key: "explanation",
      width: 250,
      render: (_value: unknown, record: RuleEvaluationItem) => (
        <span className={styles.explanationCell}>{explanationSummary(record.explanation)}</span>
      ),
    },
    {
      title: "追溯与处置",
      key: "action",
      width: 145,
      render: (_value: unknown, record: RuleEvaluationItem) => {
        const overrideAction = record.actions.find(
          (action) => action.actionCode === "BLOCK" || action.actionCode === "STRONG_REMINDER",
        );
        const overrideActionCode =
          overrideAction?.actionCode === "BLOCK" || overrideAction?.actionCode === "STRONG_REMINDER"
            ? overrideAction.actionCode
            : null;
        return record.executionId ? (
          <div>
            <Button
              type="link"
              icon={<BugOutlined />}
              onClick={() => setSelectedExecutionId(record.executionId ?? null)}
              className={styles.linkButton}
            >
              查看执行解释
            </Button>
            {record.status === "SUCCESS" && record.hit && overrideActionCode && (
              <Button
                type="link"
                danger={overrideActionCode === "BLOCK"}
                onClick={() => {
                  setOverrideTarget({
                    executionId: record.executionId,
                    actionCode: overrideActionCode,
                    suggestions: overrideAction?.overrideReasons ?? [],
                  });
                  setOverrideReason("");
                }}
              >
                记录人工继续
              </Button>
            )}
          </div>
        ) : (
          <span className={styles.textMuted}>无可追溯快照</span>
        );
      },
    },
  ];

  return (
    <PageShell
      title="规则试运行"
      description="向规则引擎输入真实脱敏上下文，实时观测匹配命中情况，进行可信解释与归因追溯。"
    >
      <Row gutter={[24, 24]} className={styles.ruleWorkspace}>
        <Col xs={24} xl={10}>
          <Card
            title={
              <div className={styles.sectionTitle}>
                <CompassOutlined className={styles.iconInfo} />
                <span>临床输入上下文</span>
              </div>
            }
            className={styles.panelCard}
          >
            <div className={styles.field}>
              <label className={styles.fieldLabel} htmlFor="rule-trigger-point">
                触发时点 (Trigger Point)
              </label>
              <Select
                id="rule-trigger-point"
                aria-label="触发时点"
                value={triggerPoint}
                options={[...CLINICAL_TRIGGER_POINT_OPTIONS]}
                onChange={setTriggerPoint}
                className={styles.fullWidth}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.fieldLabel} htmlFor="rule-patient-id">
                患者 ID
              </label>
              <Input
                id="rule-patient-id"
                placeholder="输入患者 ID 检索 ACTIVE 快照"
                value={snapshotPatientId}
                onChange={(e) => {
                  setSnapshotPatientId(e.target.value);
                  setSelectedSnapshotId("");
                }}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.fieldLabel} htmlFor="rule-encounter-id">
                就诊 ID
              </label>
              <Input
                id="rule-encounter-id"
                placeholder="可单独按就诊 ID 检索"
                value={snapshotEncounterId}
                onChange={(e) => {
                  setSnapshotEncounterId(e.target.value);
                  setSelectedSnapshotId("");
                }}
              />
            </div>

            <ContextSnapshotSelector
              enabled={hasSnapshotFilter}
              loading={snapshotsQuery.isLoading}
              error={snapshotsQuery.isError}
              snapshots={snapshotsQuery.data?.items ?? []}
              selectedSnapshotId={selectedSnapshotId}
              onSelect={setSelectedSnapshotId}
            />

            {snapshotDetailQuery.data && (
              <Descriptions bordered size="small" column={1} className={styles.sectionGap}>
                <Descriptions.Item label="配置包版本">
                  {snapshotDetailQuery.data.packageVersion || "缺失"}
                </Descriptions.Item>
                <Descriptions.Item label="质量状态">
                  {snapshotDetailQuery.data.qualityStatus}
                </Descriptions.Item>
                <Descriptions.Item label="链路 TraceId">
                  {snapshotDetailQuery.data.traceId || "未返回"}
                </Descriptions.Item>
              </Descriptions>
            )}

            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={handleEvaluate}
              loading={evaluateMutation.isPending}
              disabled={!selectedSnapshotId || snapshotDetailQuery.isLoading}
              className={styles.primaryFull}
            >
              执行匹配校验
            </Button>

            <div className={styles.replayPanel}>
              <label className={styles.fieldLabel} htmlFor="rule-replay-execution">
                历史执行解释回放
              </label>
              <div className={styles.replayRow}>
                <Select
                  id="rule-replay-execution"
                  aria-label="历史执行记录"
                  placeholder="选择真实历史执行"
                  showSearch
                  optionFilterProp="label"
                  options={executionOptions}
                  loading={executionsQuery.isLoading}
                  disabled={executionsQuery.isError}
                  value={replayExecutionId}
                  onChange={setReplayExecutionId}
                  notFoundContent={executionsQuery.isError ? "执行目录读取失败" : "暂无执行记录"}
                />
                <Button icon={<FileTextOutlined />} onClick={handleReplayExecution}>
                  回放执行解释
                </Button>
              </div>
              {(executionsQuery.data?.total ?? 0) > 20 && (
                <Pagination
                  current={executionPage}
                  pageSize={20}
                  total={executionsQuery.data?.total ?? 0}
                  showSizeChanger={false}
                  size="small"
                  onChange={(page) => {
                    setExecutionPage(page);
                    setReplayExecutionId("");
                  }}
                />
              )}
            </div>
          </Card>
        </Col>

        <Col xs={24} xl={14}>
          <Card
            title={
              <div className={styles.sectionTitle}>
                <PlayCircleOutlined className={styles.iconSuccess} />
                <span>规则评估看板</span>
              </div>
            }
            className={`${styles.panelCard} ${styles.panelCardTall}`}
          >
            {evaluateResponse ? (
              <div className={styles.resultPanel}>
                <div className={styles.resultSummary}>
                  <Descriptions size="small" column={2} className={styles.flexGrow}>
                    <Descriptions.Item label="链路 TraceId">
                      <span className={styles.codeText}>{evaluateResponse.traceId}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="求值 RequestId">
                      <span className={styles.codeText}>{evaluateResponse.requestId}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="最高严重警示">
                      <Tag color={severityColor(evaluateResponse.highestSeverity)}>
                        {evaluateResponse.highestSeverity || "NONE"}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="有效命中规则数">
                      <span className={styles.metricValue}>
                        {evaluateResponse.items?.filter(
                          (i: RuleEvaluationItem) => i.status === "SUCCESS" && i.hit,
                        ).length || 0}{" "}
                        条
                      </span>
                    </Descriptions.Item>
                  </Descriptions>
                </div>

                {evaluateResponse.items?.some(
                  (item) => item.status === "SUCCESS" && item.hit && isRedlineEvaluationItem(item),
                ) && (
                  <Alert
                    message="安全红线不可忽略"
                    description="该校验只提示和阻断，不自动改写医嘱；命中后必须按院内流程进行医师确认与复核。"
                    type="error"
                    showIcon
                    className={styles.sectionGap}
                  />
                )}

                <div className={`${styles.textStrong} ${styles.sectionGap}`}>
                  规则执行、抑制与去重证据
                </div>
                <Table
                  dataSource={evaluateResponse.items || []}
                  columns={columns}
                  rowKey="executionId"
                  pagination={false}
                  locale={{ emptyText: "该临床快照没有可执行规则。" }}
                  className="medkernel-table"
                  tableLayout="fixed"
                  scroll={{ x: 1300 }}
                />
              </div>
            ) : (
              <div className={styles.emptyState}>
                <PlayCircleOutlined className={styles.emptyIcon} />
                <span className={styles.textMuted}>请在左侧输入临床快照后执行规则匹配</span>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <Modal
        title="记录人工继续"
        open={Boolean(overrideTarget)}
        onOk={handleCaptureOverride}
        onCancel={() => {
          setOverrideTarget(null);
          setOverrideReason("");
        }}
        okText="确认留痕"
        cancelText="取消"
        confirmLoading={captureOverrideMutation.isPending}
        okButtonProps={{ disabled: !overrideReason.trim() }}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          message="该操作不会修改规则结论，只记录临床人员在完成复核后的继续理由。"
          className={styles.sectionGap}
        />
        <label className={styles.fieldLabel} htmlFor="rule-override-reason">
          越权理由
        </label>
        <AutoComplete
          id="rule-override-reason"
          value={overrideReason}
          options={(overrideTarget?.suggestions ?? []).map((reason) => ({
            value: reason,
          }))}
          onChange={setOverrideReason}
          placeholder="选择建议理由或填写真实临床复核结论"
          className="mk-full-width"
        />
      </Modal>

      <Drawer
        title={
          <div className={styles.drawerTitle}>
            <BugOutlined className={styles.iconInfo} />
            <span>临床可信解释与归因追溯</span>
          </div>
        }
        width={640}
        onClose={() => setSelectedExecutionId(null)}
        open={!!selectedExecutionId}
        loading={explainLoading}
        destroyOnClose
      >
        {explainData && (
          <div>
            <Alert
              message="本解释视图读取规则执行日志中的真实输入摘要、命中结果、动作与解释快照；页面不补写归因。"
              type="info"
              showIcon
              className={styles.sectionGapLg}
            />

            <Descriptions
              title="求值快照元数据"
              bordered
              column={1}
              size="small"
              className={styles.sectionGapLg}
            >
              <Descriptions.Item label="求值 Execution ID">
                <span className={styles.codeText}>{explainData.executionId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="链路 Trace ID">
                <span className={styles.codeText}>{explainData.traceId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="触发点">
                <span className={styles.codeText}>{explainData.triggerPoint}</span>
              </Descriptions.Item>
              <Descriptions.Item label="输入 Payload 摘要 (SHA-256)">
                <span className={styles.codeText}>{explainData.inputDigest}</span>
              </Descriptions.Item>
              <Descriptions.Item label="风险评级">
                <Tag color={explainData.severity === "HIGH" ? "red" : "orange"}>
                  {explainData.severity || "LOW"}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="执行状态">
                <Tag color={explainData.status === "SUCCESS" ? "green" : "red"}>
                  {explainData.status}
                </Tag>
              </Descriptions.Item>
            </Descriptions>

            <Card
              title={
                <div className={styles.sectionTitle}>
                  <FileTextOutlined className={styles.iconInfo} />
                  <span>规则求值可信解释文本</span>
                </div>
              }
              className={`${styles.detailCard} ${styles.sectionGapLg}`}
            >
              <div className={styles.detailBody}>{renderJson(explainData.explanation)}</div>
            </Card>

            <Card
              title={
                <div className={styles.sectionTitle}>
                  <FileTextOutlined className={styles.iconInfo} />
                  <span>执行动作快照</span>
                </div>
              }
              className={styles.detailCard}
            >
              <div className={styles.detailBody}>{renderJson(explainData.actions)}</div>
            </Card>
          </div>
        )}
      </Drawer>
    </PageShell>
  );
}

function executionStatusLabel(status: string) {
  if (status === "SUCCESS") return "成功";
  if (status === "MISS") return "未命中";
  if (status === "SUPPRESSED") return "已抑制";
  if (status === "DEDUPLICATED") return "已去重";
  if (status === "FAILED") return "失败";
  return status;
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}
