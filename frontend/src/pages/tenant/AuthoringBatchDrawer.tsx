import { useMemo, useState } from "react";
import {
  Alert,
  App as AntdApp,
  Button,
  Checkbox,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlayCircleOutlined, SafetyCertificateOutlined } from "@ant-design/icons";

import { getApiErrorMessage } from "@/shared/api/errors";
import { customerSafeDisplayText } from "@/shared/config/customerLabels";
import {
  useAnalyzeAuthoringBatchRuleImpacts,
  useAuthoringBatchJobs,
  useGenerateAuthoringBatchRules,
  usePublishAuthoringBatchRules,
} from "@/shared/api/hooks";
import type {
  AuthoringBatchItemResponse,
  AuthoringBatchJobResponse,
  AuthoringBatchRuleGenerateRow,
  AuthoringBatchRuleImpactItem,
  RuleGovernanceState,
  RuleRiskLevel,
} from "@/shared/api/hooks";
import styles from "./AuthoringBatchDrawer.module.css";

const { Text, Title } = Typography;
const { TextArea } = Input;

const AUTHORING_BATCH_JOB_PAGE_SIZE = 20;

interface AuthoringBatchDrawerProps {
  open: boolean;
  canWrite: boolean;
  onClose: () => void;
}

type TableRow = Record<string, string>;

const JOB_STATUS_LABELS: Record<AuthoringBatchJobResponse["status"], string> = {
  RUNNING: "执行中",
  SUCCEEDED: "成功",
  PARTIAL_SUCCESS: "部分成功",
  FAILED: "失败",
};

const JOB_TYPE_LABELS: Record<AuthoringBatchJobResponse["jobType"], string> = {
  RULE_GENERATE: "规则生成",
  RULE_PUBLISH: "规则发布",
};

const RISK_LABELS: Record<RuleRiskLevel, string> = {
  LOW: "低风险",
  MEDIUM: "中风险",
  HIGH: "高危",
  CRITICAL: "极高危",
};

const TABLE_HEADER_ALIASES: Record<string, string> = {
  ruleCode: "ruleCode",
  规则身份: "ruleCode",
  稳定规则身份: "ruleCode",
  规则资产身份: "ruleCode",
  规则编码: "ruleCode",
  name: "name",
  名称: "name",
  规则名称: "name",
  applicableOrgUnitId: "applicableOrgUnitId",
  适用组织身份: "applicableOrgUnitId",
  适用科室身份: "applicableOrgUnitId",
  适用范围身份: "applicableOrgUnitId",
  changeSummary: "changeSummary",
  变更说明: "changeSummary",
  调整说明: "changeSummary",
  上线说明: "changeSummary",
  threshold: "threshold",
  阈值: "threshold",
  阈值数值: "threshold",
  enabled: "enabled",
  启用: "enabled",
  是否启用: "enabled",
};

function normalizeTableHeader(header: string) {
  return TABLE_HEADER_ALIASES[header.replace(/\s+/g, "")] ?? header;
}

function parseTable(text: string): TableRow[] {
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  if (lines.length < 2) throw new Error("批量规则表至少需要表头和一行数据");
  const delimiter = lines[0].includes("\t") ? "\t" : ",";
  const rawHeaders = lines[0].split(delimiter).map((header) => header.trim());
  if (rawHeaders.some((header) => !header)) throw new Error("批量规则表表头不能为空");
  const headers = rawHeaders.map(normalizeTableHeader);
  const seenHeaders = new Set<string>();
  const duplicateHeaderIndex = headers.findIndex((header) => {
    if (seenHeaders.has(header)) return true;
    seenHeaders.add(header);
    return false;
  });
  if (duplicateHeaderIndex >= 0) {
    throw new Error(`批量规则表存在重复列：${rawHeaders[duplicateHeaderIndex]}`);
  }
  return lines.slice(1).map((line, index) => {
    const values = line.split(delimiter).map((value) => value.trim());
    if (values.length !== headers.length) {
      throw new Error(`第 ${index + 2} 行列数与表头不一致`);
    }
    return Object.fromEntries(headers.map((header, column) => [header, values[column]]));
  });
}

function parseValue(value: string): unknown {
  if (value === "true") return true;
  if (value === "false") return false;
  if (value === "null") return null;
  if (value !== "" && Number.isFinite(Number(value))) return Number(value);
  if (
    (value.startsWith("{") && value.endsWith("}")) ||
    (value.startsWith("[") && value.endsWith("]"))
  ) {
    try {
      return JSON.parse(value);
    } catch {
      return value;
    }
  }
  return value;
}

function splitLines(value: string) {
  return Array.from(
    new Set(
      value
        .split(/[\n,，\s]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  );
}

function localFailureMessage(error: unknown, fallback: string): string {
  if (!(error instanceof Error) || !/[\u3400-\u9fff]/.test(error.message)) {
    return fallback;
  }
  return customerSafeDisplayText(error.message, fallback);
}

function jobStatusColor(status: string) {
  if (status === "SUCCEEDED") return "success";
  if (status === "PARTIAL_SUCCESS") return "warning";
  if (status === "FAILED") return "error";
  return "default";
}

function jobAlertType(status: string): "success" | "warning" | "error" {
  if (status === "FAILED") return "error";
  if (status === "PARTIAL_SUCCESS") return "warning";
  return "success";
}

function isHighRisk(item: AuthoringBatchRuleImpactItem) {
  return item.riskLevel === "HIGH" || item.riskLevel === "CRITICAL";
}

export default function AuthoringBatchDrawer({
  open,
  canWrite,
  onClose,
}: AuthoringBatchDrawerProps) {
  const { message } = AntdApp.useApp();
  const [activeTab, setActiveTab] = useState("generate");
  const [lastJob, setLastJob] = useState<AuthoringBatchJobResponse | null>(null);
  const [templateRuleId, setTemplateRuleId] = useState("");
  const [parameterTable, setParameterTable] = useState("");
  const [publishRuleIds, setPublishRuleIds] = useState("");
  const [publishReason, setPublishReason] = useState("");
  const [targetState, setTargetState] = useState<RuleGovernanceState>("REVIEWED");
  const [impactItems, setImpactItems] = useState<AuthoringBatchRuleImpactItem[]>([]);
  const [confirmedHighRisk, setConfirmedHighRisk] = useState<Set<string>>(new Set());
  const [jobPage, setJobPage] = useState(1);

  const jobsQuery = useAuthoringBatchJobs({
    page: jobPage,
    size: AUTHORING_BATCH_JOB_PAGE_SIZE,
    enabled: open,
  });
  const generateMutation = useGenerateAuthoringBatchRules();
  const analyzeMutation = useAnalyzeAuthoringBatchRuleImpacts();
  const publishMutation = usePublishAuthoringBatchRules();

  const allHighRiskConfirmed = useMemo(
    () =>
      impactItems.length > 0 &&
      impactItems.filter(isHighRisk).every((item) => confirmedHighRisk.has(item.ruleId)),
    [confirmedHighRisk, impactItems],
  );
  const run = async (operation: () => Promise<AuthoringBatchJobResponse>, fallback: string) => {
    try {
      const job = await operation();
      setLastJob(job);
      setJobPage(1);
      message.success(`批量任务 ${job.jobId} 已记录`);
      return job;
    } catch (error) {
      message.error(getApiErrorMessage(error, fallback));
      return null;
    }
  };

  const generateRules = async () => {
    try {
      if (!templateRuleId.trim()) throw new Error("请输入基准规则资产");
      const reserved = new Set(["ruleCode", "name", "applicableOrgUnitId", "changeSummary"]);
      const rows: AuthoringBatchRuleGenerateRow[] = parseTable(parameterTable).map((row, index) => {
        if (!row.ruleCode || !row.name) {
          throw new Error(`第 ${index + 2} 行缺少规则身份或规则名称`);
        }
        return {
          rowId: `row-${index + 1}`,
          ruleCode: row.ruleCode,
          name: row.name,
          parameterBindings: Object.fromEntries(
            Object.entries(row)
              .filter(([key]) => !reserved.has(key))
              .map(([key, value]) => [key, parseValue(value)]),
          ),
          ...(row.applicableOrgUnitId ? { applicableOrgUnitId: row.applicableOrgUnitId } : {}),
          ...(row.changeSummary ? { changeSummary: row.changeSummary } : {}),
        };
      });
      await run(
        () =>
          generateMutation.mutateAsync({
            templateRuleId: templateRuleId.trim(),
            rows,
          }),
        "规则批量生成失败",
      );
    } catch (error) {
      message.error(localFailureMessage(error, "批量规则表解析失败"));
    }
  };

  const analyzeImpacts = async () => {
    const ruleIds = splitLines(publishRuleIds);
    if (!ruleIds.length) {
      message.error("请输入至少一个待发布规则资产");
      return;
    }
    try {
      const impact = await analyzeMutation.mutateAsync(ruleIds);
      setImpactItems(impact.items);
      setConfirmedHighRisk(new Set());
      message.success(`已完成 ${impact.totalCount} 条规则的影响分析`);
    } catch (error) {
      message.error(getApiErrorMessage(error, "批量影响分析失败"));
    }
  };

  const publishRules = async () => {
    if (!impactItems.length || !publishReason.trim()) return;
    await run(
      () =>
        publishMutation.mutateAsync({
          targetState,
          reason: publishReason.trim(),
          items: impactItems.map((item) => ({
            itemId: item.ruleId,
            ruleId: item.ruleId,
            impactDigest: item.impactDigest,
            highRiskConfirmed: !isHighRisk(item) || confirmedHighRisk.has(item.ruleId),
          })),
        }),
      "规则批量推进失败",
    );
  };

  const toggleHighRisk = (ruleId: string, checked: boolean) => {
    setConfirmedHighRisk((current) => {
      const next = new Set(current);
      if (checked) next.add(ruleId);
      else next.delete(ruleId);
      return next;
    });
  };

  const resultColumns: ColumnsType<AuthoringBatchItemResponse> = [
    { title: "项目", dataIndex: "itemId", key: "itemId" },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: AuthoringBatchItemResponse["status"]) => (
        <Tag color={jobStatusColor(status)}>{JOB_STATUS_LABELS[status]}</Tag>
      ),
    },
    { title: "目标", dataIndex: "targetId", key: "targetId", render: (value) => value || "-" },
    { title: "结果", dataIndex: "message", key: "message" },
  ];

  const impactColumns: ColumnsType<AuthoringBatchRuleImpactItem> = [
    { title: "规则", dataIndex: "ruleId", key: "ruleId" },
    {
      title: "风险",
      dataIndex: "riskLevel",
      key: "riskLevel",
      render: (risk: RuleRiskLevel) => (
        <Tag color={risk === "HIGH" || risk === "CRITICAL" ? "error" : "default"}>
          {RISK_LABELS[risk]}
        </Tag>
      ),
    },
    { title: "影响对象", dataIndex: "affectedCount", key: "affectedCount" },
    {
      title: "逐条确认",
      key: "confirm",
      render: (_value, item) =>
        isHighRisk(item) ? (
          <Checkbox
            aria-label={`确认 ${item.ruleId}`}
            checked={confirmedHighRisk.has(item.ruleId)}
            onChange={(event) => toggleHighRisk(item.ruleId, event.target.checked)}
          >
            已核对
          </Checkbox>
        ) : (
          <Text type="secondary">无需额外确认</Text>
        ),
    },
  ];

  const generatePanel = (
    <Form layout="vertical" className={styles.form}>
      <Alert
        type="info"
        showIcon
        message="批量生成独立规则草稿"
        description="每行创建一个独立规则版本，沿用基准规则的触发绑定；真正上线的版本由机构生效版本统一选择。"
      />
      <Form.Item label="基准规则资产" required>
        <Input
          aria-label="基准规则资产"
          value={templateRuleId}
          onChange={(event) => setTemplateRuleId(event.target.value)}
          placeholder="输入已审核基准规则的稳定身份"
        />
      </Form.Item>
      <Form.Item
        label="批量规则草稿表"
        required
        extra="至少包含规则身份和规则名称；阈值、启用等列会作为批量参数。可直接粘贴 Excel 表格。"
      >
        <TextArea
          aria-label="批量规则草稿表"
          value={parameterTable}
          onChange={(event) => setParameterTable(event.target.value)}
          rows={9}
          placeholder={"规则身份,规则名称,阈值,启用\nCKD-阈值-45,CKD 阈值 1,45,true"}
        />
      </Form.Item>
      <Button
        type="primary"
        icon={<PlayCircleOutlined />}
        aria-label="生成草稿"
        disabled={!canWrite}
        loading={generateMutation.isPending}
        onClick={generateRules}
      >
        生成草稿
      </Button>
    </Form>
  );

  const publishPanel = (
    <Space direction="vertical" size="middle" className={styles.fullWidth}>
      <Form layout="vertical" className={styles.form}>
        <Form.Item label="待发布规则资产" required extra="每行一个稳定规则资产，也可用逗号分隔。">
          <TextArea
            aria-label="待发布规则资产"
            value={publishRuleIds}
            onChange={(event) => setPublishRuleIds(event.target.value)}
            rows={4}
            placeholder={"RULE.CKD.1\nRULE.CKD.2"}
          />
        </Form.Item>
        <Button
          icon={<SafetyCertificateOutlined />}
          aria-label="分析影响"
          loading={analyzeMutation.isPending}
          onClick={analyzeImpacts}
        >
          分析影响
        </Button>
      </Form>
      {impactItems.length > 0 && (
        <>
          <Alert
            type={impactItems.some(isHighRisk) ? "warning" : "info"}
            showIcon
            message={
              impactItems.some(isHighRisk)
                ? "包含高危规则，必须逐条核对并确认"
                : "影响分析完成，可批量推进"
            }
          />
          <Table
            rowKey="ruleId"
            dataSource={impactItems}
            columns={impactColumns}
            pagination={false}
            size="small"
          />
          <Form layout="vertical" className={styles.form}>
            <div className={styles.twoColumns}>
              <Form.Item label="目标状态">
                <Select
                  value={targetState}
                  onChange={setTargetState}
                  options={[
                    { value: "REVIEWED", label: "安全复核" },
                    { value: "SHADOW", label: "影子运行" },
                    { value: "CANARY", label: "灰度" },
                    { value: "FULL", label: "全量" },
                    { value: "MONITOR", label: "监测" },
                    { value: "RETIRED", label: "退役" },
                  ]}
                />
              </Form.Item>
              <Form.Item label="推进说明" required>
                <Input
                  aria-label="推进说明"
                  value={publishReason}
                  onChange={(event) => setPublishReason(event.target.value)}
                  placeholder="填写确认或发布依据"
                />
              </Form.Item>
            </div>
            <Button
              type="primary"
              disabled={!canWrite || !publishReason.trim() || !allHighRiskConfirmed}
              loading={publishMutation.isPending}
              onClick={publishRules}
            >
              批量推进
            </Button>
          </Form>
        </>
      )}
    </Space>
  );

  const recentColumns: ColumnsType<AuthoringBatchJobResponse> = [
    { title: "任务号", dataIndex: "jobId", key: "jobId" },
    {
      title: "类型",
      dataIndex: "jobType",
      key: "jobType",
      render: (type: AuthoringBatchJobResponse["jobType"]) => JOB_TYPE_LABELS[type],
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: AuthoringBatchJobResponse["status"]) => (
        <Tag color={jobStatusColor(status)}>{JOB_STATUS_LABELS[status]}</Tag>
      ),
    },
    {
      title: "进度",
      key: "progress",
      render: (_value, job) => `${job.successCount + job.failureCount}/${job.totalCount}`,
    },
    { title: "更新时间", dataIndex: "updatedAt", key: "updatedAt" },
  ];

  const recentPanel = (
    <Table
      rowKey="jobId"
      dataSource={jobsQuery.data?.items ?? []}
      columns={recentColumns}
      loading={jobsQuery.isLoading}
      pagination={{
        current: jobsQuery.data?.page ?? jobPage,
        pageSize: jobsQuery.data?.size ?? AUTHORING_BATCH_JOB_PAGE_SIZE,
        total: jobsQuery.data?.total ?? 0,
        showSizeChanger: false,
        onChange: (page) => setJobPage(page),
      }}
      size="small"
    />
  );

  return (
    <Drawer title="批量处理" open={open} onClose={onClose} width={820} destroyOnClose={false}>
      <Space direction="vertical" size="large" className={styles.fullWidth}>
        {!canWrite && (
          <Alert type="warning" showIcon message="当前账号仅可查看批量任务，不能执行写操作" />
        )}
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            { key: "generate", label: "规则生成", children: generatePanel },
            { key: "publish", label: "规则发布", children: publishPanel },
            { key: "jobs", label: "任务记录", children: recentPanel },
          ]}
        />
        {lastJob && (
          <section className={styles.resultSection} aria-label="批量任务结果">
            <Title level={5}>执行结果</Title>
            <Alert
              type={jobAlertType(lastJob.status)}
              showIcon
              message={`批量任务 ${lastJob.jobId} 执行结束`}
              description={
                <Space wrap>
                  <Text>成功 {lastJob.successCount}</Text>
                  <Text>失败 {lastJob.failureCount}</Text>
                  <Tag color={jobStatusColor(lastJob.status)}>
                    {JOB_STATUS_LABELS[lastJob.status]}
                  </Tag>
                </Space>
              }
            />
            <Table
              rowKey="itemId"
              dataSource={lastJob.items}
              columns={resultColumns}
              pagination={false}
              size="small"
            />
          </section>
        )}
      </Space>
    </Drawer>
  );
}
