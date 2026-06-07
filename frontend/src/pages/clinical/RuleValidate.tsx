import { useState } from "react";
import {
  Row,
  Col,
  Card,
  Input,
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
  useEvaluateRules,
  useRuleExecutions,
  useRuleExecutionExplain,
} from "@/shared/api/hooks";
import type { RuleEvaluationItem, RuleEvaluateResponse } from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import styles from "./Clinical.module.css";

function isCriticalSeverity(severity?: string | null) {
  return severity === "CRITICAL";
}

function isRedlineActionCode(actionCode?: string | null) {
  return actionCode === "CLINICAL_REDLINE" || actionCode?.startsWith("REDLINE") === true;
}

function isRedlineEvaluationItem(item: RuleEvaluationItem) {
  return (
    isCriticalSeverity(item.severity) ||
    item.ruleId.startsWith("RDL-") ||
    item.actions?.some((action) => isRedlineActionCode(action.actionCode)) === true ||
    isRedlineActionCode(item.actionCode)
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

export default function RuleValidate() {
  const [triggerPoint, setTriggerPoint] = useState<string>("order-sign");
  const [snapshotPatientId, setSnapshotPatientId] = useState<string>("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState<string>("");
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string>("");
  const [replayExecutionId, setReplayExecutionId] = useState<string>("");
  const [executionPage, setExecutionPage] = useState(1);

  const [evaluateResponse, setEvaluateResponse] = useState<RuleEvaluateResponse | null>(null);
  const [selectedExecutionId, setSelectedExecutionId] = useState<string | null>(null);

  const evaluateMutation = useEvaluateRules();
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
      render: (text: string) => <Tag color="cyan">{text}</Tag>,
    },
    {
      title: "版本 ID",
      dataIndex: "versionId",
      key: "versionId",
      className: styles.textStrong,
      render: (text: string | undefined, record: RuleEvaluationItem) => (
        <span>{text || record.ruleCode || "未返回版本"}</span>
      ),
    },
    {
      title: "警示严重度",
      dataIndex: "severity",
      key: "severity",
      render: (level: string) => {
        return <Tag color={severityColor(level)}>{level}</Tag>;
      },
    },
    {
      title: "处置动作",
      key: "actions",
      render: (_value: unknown, record: RuleEvaluationItem) => {
        const actionCodes =
          record.actions?.map((action) => action.actionCode).filter(Boolean) ??
          (record.actionCode ? [record.actionCode] : []);
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
      title: "命中解释",
      key: "explanation",
      render: (_value: unknown, record: RuleEvaluationItem) => (
        <span className={styles.preWrap}>{renderJson(record.explanation)}</span>
      ),
    },
    {
      title: "解释追溯",
      key: "action",
      render: (_record: RuleEvaluationItem) => {
        if (evaluateResponse?.executionId) {
          return (
            <Button
              type="link"
              icon={<BugOutlined />}
              onClick={() => setSelectedExecutionId(evaluateResponse.executionId)}
              className={styles.linkButton}
            >
              查看执行解释
            </Button>
          );
        }
        return <span className={styles.textMuted}>无可追溯快照</span>;
      },
    },
  ];

  return (
    <PageShell
      title="规则试运行"
      description="向规则引擎输入真实脱敏上下文，实时观测匹配命中情况，进行可信解释与归因追溯。"
    >
      <Row gutter={[24, 24]}>
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
              <Input
                id="rule-trigger-point"
                placeholder="输入触发时点编码"
                value={triggerPoint}
                onChange={(e) => setTriggerPoint(e.target.value)}
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
              <div>
                <div className={styles.resultSummary}>
                  <Descriptions size="small" column={2} className={styles.flexGrow}>
                    <Descriptions.Item label="链路 TraceId">
                      <span className={styles.codeText}>{evaluateResponse.traceId}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="求值 ExecutionId">
                      <span className={styles.codeText}>{evaluateResponse.executionId}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="最高严重警示">
                      <Tag color={severityColor(evaluateResponse.highestSeverity)}>
                        {evaluateResponse.highestSeverity || "NONE"}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="命中规则总数">
                      <span className={styles.metricValue}>
                        {evaluateResponse.items?.filter((i: RuleEvaluationItem) => i.hit).length ||
                          0}{" "}
                        条
                      </span>
                    </Descriptions.Item>
                  </Descriptions>
                </div>

                {evaluateResponse.items?.some(
                  (item) => item.hit && isRedlineEvaluationItem(item),
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
                  命中规则及合理性建议列表
                </div>
                <Table
                  dataSource={
                    evaluateResponse.items?.filter((i: RuleEvaluationItem) => i.hit) || []
                  }
                  columns={columns}
                  rowKey="ruleId"
                  pagination={false}
                  locale={{ emptyText: "该临床快照未触发任何高风险或规则拦截，通过。" }}
                  className="medkernel-table"
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
