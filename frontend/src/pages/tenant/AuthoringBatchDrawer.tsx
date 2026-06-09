import { useMemo, useState } from "react";
import {
  Alert,
  App as AntdApp,
  Button,
  Checkbox,
  Drawer,
  Form,
  Input,
  Segmented,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
} from "antd";
import type { UploadFile } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  CloudDownloadOutlined,
  CloudUploadOutlined,
  DownloadOutlined,
  PlayCircleOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useAnalyzeAuthoringBatchRuleImpacts,
  useAuthoringBatchJobs,
  useDistributeAuthoringBatchPackages,
  useExportAuthoringBatchPackages,
  useGenerateAuthoringBatchRules,
  useImportAuthoringBatchPackages,
  usePublishAuthoringBatchRules,
} from "@/shared/api/hooks";
import type {
  AuthoringBatchItemResponse,
  AuthoringBatchJobResponse,
  AuthoringBatchRuleGenerateRow,
  AuthoringBatchRuleImpactItem,
  ReleaseScopeType,
  RuleGovernanceState,
  RuleRiskLevel,
} from "@/shared/api/hooks";
import styles from "./AuthoringBatchDrawer.module.css";

const { Text, Title } = Typography;
const { TextArea } = Input;

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
  NOT_CONNECTED: "目标未连接",
};

const JOB_TYPE_LABELS: Record<AuthoringBatchJobResponse["jobType"], string> = {
  RULE_GENERATE: "规则生成",
  RULE_PUBLISH: "规则发布",
  PACKAGE_IMPORT: "配置包导入",
  PACKAGE_EXPORT: "配置包导出",
  PACKAGE_DISTRIBUTE: "配置包分发",
};

const RISK_LABELS: Record<RuleRiskLevel, string> = {
  LOW: "低风险",
  MEDIUM: "中风险",
  HIGH: "高危",
  CRITICAL: "极高危",
};

function parseTable(text: string): TableRow[] {
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  if (lines.length < 2) throw new Error("参数表至少需要表头和一行数据");
  const delimiter = lines[0].includes("\t") ? "\t" : ",";
  const headers = lines[0].split(delimiter).map((header) => header.trim());
  if (headers.some((header) => !header)) throw new Error("参数表表头不能为空");
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

function jobStatusColor(status: string) {
  if (status === "SUCCEEDED") return "success";
  if (status === "PARTIAL_SUCCESS") return "warning";
  if (status === "NOT_CONNECTED") return "processing";
  if (status === "FAILED") return "error";
  return "default";
}

function jobAlertType(status: string): "success" | "warning" | "error" {
  if (status === "FAILED") return "error";
  if (status === "PARTIAL_SUCCESS" || status === "NOT_CONNECTED") return "warning";
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
  const [targetState, setTargetState] = useState<RuleGovernanceState>("PEER_REVIEW");
  const [impactItems, setImpactItems] = useState<AuthoringBatchRuleImpactItem[]>([]);
  const [confirmedHighRisk, setConfirmedHighRisk] = useState<Set<string>>(new Set());
  const [exchangeMode, setExchangeMode] = useState<"import" | "export">("import");
  const [importFiles, setImportFiles] = useState<Array<UploadFile & { payload: string }>>([]);
  const [exportTable, setExportTable] = useState("");
  const [distributionTable, setDistributionTable] = useState("");
  const [distributionReason, setDistributionReason] = useState("");
  const [distributionStrategy, setDistributionStrategy] = useState<"GRAYSCALE" | "FULL">(
    "GRAYSCALE",
  );
  const [distributionScope, setDistributionScope] = useState<ReleaseScopeType>("FACILITY");

  const jobsQuery = useAuthoringBatchJobs({ enabled: open });
  const generateMutation = useGenerateAuthoringBatchRules();
  const analyzeMutation = useAnalyzeAuthoringBatchRuleImpacts();
  const publishMutation = usePublishAuthoringBatchRules();
  const importMutation = useImportAuthoringBatchPackages();
  const exportMutation = useExportAuthoringBatchPackages();
  const distributeMutation = useDistributeAuthoringBatchPackages();

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
      message.success(`批量任务 ${job.jobId} 已记录`);
      return job;
    } catch (error) {
      message.error(getApiErrorMessage(error, fallback));
      return null;
    }
  };

  const generateRules = async () => {
    try {
      if (!templateRuleId.trim()) throw new Error("请输入模板规则 ID");
      const reserved = new Set([
        "ruleCode",
        "name",
        "packageVersion",
        "applicableOrgUnitId",
        "changeSummary",
      ]);
      const rows: AuthoringBatchRuleGenerateRow[] = parseTable(parameterTable).map((row, index) => {
        if (!row.ruleCode || !row.name) throw new Error(`第 ${index + 2} 行缺少 ruleCode 或 name`);
        return {
          rowId: `row-${index + 1}`,
          ruleCode: row.ruleCode,
          name: row.name,
          parameterBindings: Object.fromEntries(
            Object.entries(row)
              .filter(([key]) => !reserved.has(key))
              .map(([key, value]) => [key, parseValue(value)]),
          ),
          ...(row.packageVersion ? { packageVersion: row.packageVersion } : {}),
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
      message.error(error instanceof Error ? error.message : "参数表解析失败");
    }
  };

  const analyzeImpacts = async () => {
    const ruleIds = splitLines(publishRuleIds);
    if (!ruleIds.length) {
      message.error("请输入至少一个规则 ID");
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

  const importPackages = async () => {
    if (!importFiles.length) {
      message.error("请先选择离线包 JSON 文件");
      return;
    }
    await run(
      () =>
        importMutation.mutateAsync({
          items: importFiles.map((file, index) => ({
            itemId: file.uid || `file-${index + 1}`,
            offlinePackageJson: file.payload,
          })),
        }),
      "配置包批量导入失败",
    );
  };

  const exportPackages = async () => {
    try {
      const rows = parseTable(exportTable);
      await run(
        () =>
          exportMutation.mutateAsync({
            items: rows.map((row, index) => {
              if (!row.packageId || !row.targetOrgUnitId) {
                throw new Error(`第 ${index + 2} 行缺少 packageId 或 targetOrgUnitId`);
              }
              return {
                itemId: `row-${index + 1}`,
                packageId: row.packageId,
                targetOrgUnitId: row.targetOrgUnitId,
              };
            }),
          }),
        "配置包批量导出失败",
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : "导出目标表解析失败");
    }
  };

  const distributePackages = async () => {
    try {
      if (!distributionReason.trim()) throw new Error("请输入分发说明");
      const rows = parseTable(distributionTable);
      await run(
        () =>
          distributeMutation.mutateAsync({
            items: rows.map((row, index) => {
              if (!row.packageId || !row.targetOrgUnitId || !row.adapterIds) {
                throw new Error(`第 ${index + 2} 行缺少 packageId、targetOrgUnitId 或 adapterIds`);
              }
              return {
                itemId: `row-${index + 1}`,
                packageId: row.packageId,
                targetOrgUnitId: row.targetOrgUnitId,
                strategy: distributionStrategy,
                scopeType: distributionScope,
                scopeValue: distributionScope === "ALL" ? undefined : row.targetOrgUnitId,
                adapterIds: row.adapterIds
                  .split(/[;；|]+/)
                  .map((item) => item.trim())
                  .filter(Boolean),
                reason: distributionReason.trim(),
              };
            }),
          }),
        "配置包批量分发失败",
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : "分发目标表解析失败");
    }
  };

  const toggleHighRisk = (ruleId: string, checked: boolean) => {
    setConfirmedHighRisk((current) => {
      const next = new Set(current);
      if (checked) next.add(ruleId);
      else next.delete(ruleId);
      return next;
    });
  };

  const downloadExport = (item: AuthoringBatchItemResponse) => {
    if (!item.resultJson) return;
    try {
      const result = JSON.parse(item.resultJson) as { offlinePackageJson?: string };
      if (!result.offlinePackageJson) throw new Error("导出结果不含离线包");
      const blob = new Blob([result.offlinePackageJson], {
        type: "application/json;charset=utf-8",
      });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${item.targetId ?? item.itemId}.json`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "离线包下载失败");
    }
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
    {
      title: "",
      key: "download",
      width: 48,
      render: (_value, item) =>
        lastJob?.jobType === "PACKAGE_EXPORT" && item.status === "SUCCEEDED" ? (
          <Button
            type="text"
            icon={<DownloadOutlined />}
            aria-label={`下载 ${item.itemId}`}
            title="下载离线包"
            onClick={() => downloadExport(item)}
          />
        ) : null,
    },
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
      <Form.Item label="模板规则 ID" required>
        <Input
          aria-label="模板规则 ID"
          value={templateRuleId}
          onChange={(event) => setTemplateRuleId(event.target.value)}
          placeholder="例如 rule-template-ckd"
        />
      </Form.Item>
      <Form.Item
        label="参数表"
        required
        extra="首列至少包含 ruleCode、name；其余列自动作为模板参数。可直接粘贴 Excel 表格。"
      >
        <TextArea
          aria-label="参数表"
          value={parameterTable}
          onChange={(event) => setParameterTable(event.target.value)}
          rows={9}
          placeholder={"ruleCode,name,threshold\nRULE.CKD.1,CKD 阈值 1,45"}
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
        <Form.Item label="规则 ID" required extra="每行一个规则 ID，也可用逗号分隔。">
          <TextArea
            aria-label="规则 ID"
            value={publishRuleIds}
            onChange={(event) => setPublishRuleIds(event.target.value)}
            rows={4}
            placeholder={"rule-ckd-1\nrule-ckd-2"}
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
                    { value: "PEER_REVIEW", label: "同行评审" },
                    { value: "COMMITTEE", label: "临床委员会" },
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
                  placeholder="填写评审或发布依据"
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

  const exchangePanel = (
    <Space direction="vertical" size="middle" className={styles.fullWidth}>
      <Segmented
        value={exchangeMode}
        onChange={(value) => setExchangeMode(value as "import" | "export")}
        options={[
          { value: "import", label: "导入离线包", icon: <CloudUploadOutlined /> },
          { value: "export", label: "导出离线包", icon: <CloudDownloadOutlined /> },
        ]}
      />
      {exchangeMode === "import" ? (
        <>
          <Upload.Dragger
            accept=".json,application/json"
            multiple
            fileList={importFiles}
            beforeUpload={async (file) => {
              const payload = await file.text();
              setImportFiles((current) => [
                ...current.filter((item) => item.uid !== file.uid),
                { ...file, status: "done", payload },
              ]);
              return Upload.LIST_IGNORE;
            }}
            onRemove={(file) => {
              setImportFiles((current) => current.filter((item) => item.uid !== file.uid));
            }}
          >
            <p className="ant-upload-drag-icon">
              <CloudUploadOutlined />
            </p>
            <p>选择或拖入一个或多个离线包 JSON</p>
          </Upload.Dragger>
          <Button
            type="primary"
            disabled={!canWrite || importFiles.length === 0}
            loading={importMutation.isPending}
            onClick={importPackages}
          >
            批量导入
          </Button>
        </>
      ) : (
        <Form layout="vertical" className={styles.form}>
          <Form.Item
            label="导出目标表"
            required
            extra="表头为 packageId、targetOrgUnitId，可直接粘贴 Excel 表格。"
          >
            <TextArea
              aria-label="导出目标表"
              value={exportTable}
              onChange={(event) => setExportTable(event.target.value)}
              rows={8}
              placeholder={"packageId,targetOrgUnitId\npackage-1,hospital-1"}
            />
          </Form.Item>
          <Button
            type="primary"
            disabled={!canWrite}
            loading={exportMutation.isPending}
            onClick={exportPackages}
          >
            批量导出
          </Button>
        </Form>
      )}
    </Space>
  );

  const distributionPanel = (
    <Form layout="vertical" className={styles.form}>
      <div className={styles.twoColumns}>
        <Form.Item label="发布策略">
          <Select
            value={distributionStrategy}
            onChange={setDistributionStrategy}
            options={[
              { value: "FULL", label: "全量" },
              { value: "GRAYSCALE", label: "灰度" },
            ]}
          />
        </Form.Item>
        <Form.Item label="作用范围">
          <Select
            value={distributionScope}
            onChange={setDistributionScope}
            options={[
              { value: "ALL", label: "全部" },
              { value: "REGION", label: "区域" },
              { value: "FACILITY", label: "机构" },
              { value: "CAMPUS", label: "院区" },
              { value: "DEPARTMENT", label: "科室" },
              { value: "WARD", label: "病区" },
            ]}
          />
        </Form.Item>
      </div>
      <Form.Item
        label="分发目标表"
        required
        extra="表头为 packageId、targetOrgUnitId、adapterIds；多个适配器用分号分隔。"
      >
        <TextArea
          aria-label="分发目标表"
          value={distributionTable}
          onChange={(event) => setDistributionTable(event.target.value)}
          rows={8}
          placeholder={"packageId,targetOrgUnitId,adapterIds\npackage-1,hospital-1,fhir;webhook"}
        />
      </Form.Item>
      <Form.Item label="分发说明" required>
        <Input
          aria-label="分发说明"
          value={distributionReason}
          onChange={(event) => setDistributionReason(event.target.value)}
          placeholder="填写本次分发依据"
        />
      </Form.Item>
      <Button
        type="primary"
        disabled={!canWrite}
        loading={distributeMutation.isPending}
        onClick={distributePackages}
      >
        开始分发
      </Button>
    </Form>
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
      render: (_value, job) =>
        `${job.successCount + job.failureCount + job.retryableCount}/${job.totalCount}`,
    },
    { title: "更新时间", dataIndex: "updatedAt", key: "updatedAt" },
  ];

  const recentPanel = (
    <Table
      rowKey="jobId"
      dataSource={jobsQuery.data ?? []}
      columns={recentColumns}
      loading={jobsQuery.isLoading}
      pagination={false}
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
            { key: "exchange", label: "包交换", children: exchangePanel },
            { key: "distribute", label: "包分发", children: distributionPanel },
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
                  <Text>可重试 {lastJob.retryableCount}</Text>
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
