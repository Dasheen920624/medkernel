import { useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Select,
  Space,
  Tag,
  Typography,
} from "antd";
import {
  AuditOutlined,
  FileSearchOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";

import { getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import {
  useContextSnapshotDetail,
  useContextSnapshots,
  useEvaluationIndicators,
  useInsuranceIssues,
  useOrgUnits,
  useRunDrgGrouping,
  useRunInsuranceAudit,
  useRunQualityCaseReview,
  useSecurityProfile,
  type DrgGroupingResponse,
  type InsuranceAuditResponse,
  type InsuranceIssuePageItem,
  type InsuranceIssueStatus,
  type InsuranceIssuesQueryParams,
  type QualityCaseReviewResponse,
  type QualityFindingSeverity,
  type SecurityProfile,
} from "@/shared/api/hooks";
import { ContextSnapshotSelector } from "@/shared/ui/ContextSnapshotSelector";
import { customerDisplayText, customerEnumLabel } from "@/shared/config/customerLabels";
import { PageShell } from "@/shared/ui/PageShell";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { clinicalDateTimeInputToIso } from "@/shared/lib/dateTimeText";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";
import { RectificationDueAtField } from "@/shared/ui/RectificationDueAtField";

const { Text } = Typography;
const DEPARTMENT_REFERENCE_PAGE_SIZE = 20;
const AUDIT_INDICATOR_REFERENCE_PAGE_SIZE = 20;
const MANUAL_INSURANCE_RULE_INDICATOR_ID = "INSURANCE_RULE_MANUAL";

type TimeScope = "THIS_MONTH" | "LAST_7_DAYS" | "ALL";

interface AuditFormValues {
  scenarioCode: string;
  responsibleDepartmentId: string;
  indicatorId: string;
  dueAt: string;
  grouperVersion: string;
  expectedGroupCode: string;
  actualGroupCode: string;
  drgExplanation: string;
  ruleCode: string;
  ruleVersion: string;
  issueType: string;
  severity: QualityFindingSeverity;
  maxAmount?: string;
  requiredClaimStatus?: string;
  requiredClaimType?: string;
  ruleDescription: string;
}

export default function InsuranceAudit() {
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const [status, setStatus] = useState<InsuranceIssueStatus>("OPEN");
  const [timeScope, setTimeScope] = useState<TimeScope>("THIS_MONTH");
  const [severity, setSeverity] = useState<QualityFindingSeverity>("P1");
  const [selectedIssue, setSelectedIssue] = useState<InsuranceIssuePageItem | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [caseReviewResult, setCaseReviewResult] = useState<QualityCaseReviewResponse | null>(null);
  const [drgResult, setDrgResult] = useState<DrgGroupingResponse | null>(null);
  const [auditResult, setAuditResult] = useState<InsuranceAuditResponse | null>(null);
  const [snapshotPatientId, setSnapshotPatientId] = useState("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState("");
  const [selectedSnapshotId, setSelectedSnapshotId] = useState("");
  const [auditFeedback, setAuditFeedback] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);
  const [departmentSearch, setDepartmentSearch] = useState("");
  const [indicatorSearch, setIndicatorSearch] = useState("");
  const [form] = Form.useForm<AuditFormValues>();

  const issueParams = useMemo<InsuranceIssuesQueryParams>(() => {
    const range = resolveTimeRange(timeScope);
    return {
      ...range,
      status,
      severity,
      page: 1,
      size: 20,
    };
  }, [severity, status, timeScope]);

  const issuesQuery = useInsuranceIssues(issueParams);
  const departmentKeyword = departmentSearch.trim();
  const departmentsQuery = useOrgUnits({
    page: 1,
    size: DEPARTMENT_REFERENCE_PAGE_SIZE,
    sort: "name,asc",
    level: "DEPARTMENT",
    status: "ACTIVE",
    ...(departmentKeyword ? { keyword: departmentKeyword } : {}),
  });
  const indicatorKeyword = indicatorSearch.trim();
  const indicatorsQuery = useEvaluationIndicators(
    {
      status: "ACTIVE",
      ...(indicatorKeyword ? { indicatorCode: indicatorKeyword } : {}),
      page: 1,
      size: AUDIT_INDICATOR_REFERENCE_PAGE_SIZE,
      sort: "name,asc",
    },
    { enabled: true },
  );
  const departmentOptions = (departmentsQuery.data?.items ?? [])
    .filter((unit) => unit.level === "DEPARTMENT" && unit.status === "ACTIVE" && Boolean(unit.id))
    .map((unit) => ({
      value: unit.id as string,
      label: `${unit.name} · ${unit.code}`,
    }));
  const indicatorOptions = (indicatorsQuery.data?.items ?? []).map((indicator) => ({
    value: indicator.indicatorId,
    label: `${indicator.name} · ${indicator.indicatorCode} · v${indicator.versionNo}`,
  }));
  const scopeDepartmentOption = resolveScopeDepartmentOption(security.data?.dataScope);
  const useScopeDepartmentOption =
    Boolean(scopeDepartmentOption) &&
    departmentOptions.length === 0 &&
    !departmentKeyword &&
    !departmentsQuery.isLoading &&
    !departmentsQuery.isError;
  const effectiveDepartmentOptions =
    useScopeDepartmentOption && scopeDepartmentOption ? [scopeDepartmentOption] : departmentOptions;
  const useManualIndicatorOption =
    indicatorOptions.length === 0 &&
    !indicatorKeyword &&
    !indicatorsQuery.isLoading &&
    !indicatorsQuery.isError;
  const effectiveIndicatorOptions = useManualIndicatorOption
    ? [{ value: MANUAL_INSURANCE_RULE_INDICATOR_ID, label: "按本次规则归档" }]
    : indicatorOptions;
  const patientFilter = snapshotPatientId.trim();
  const encounterFilter = snapshotEncounterId.trim();
  const hasSnapshotFilter = Boolean(patientFilter || encounterFilter);
  const snapshotsQuery = useContextSnapshots(
    {
      patientId: patientFilter || undefined,
      encounterId: encounterFilter || undefined,
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
  const caseReviewMutation = useRunQualityCaseReview();
  const drgMutation = useRunDrgGrouping();
  const auditMutation = useRunInsuranceAudit();

  const issues = issuesQuery.data?.items ?? [];
  const parsedError = issuesQuery.isError
    ? parseApiError(issuesQuery.error, "医保病案问题读取失败")
    : null;
  const errorStatus = getResponseStatus(issuesQuery.error);
  const isRunning =
    caseReviewMutation.isPending || drgMutation.isPending || auditMutation.isPending;

  async function runAudit(values: AuditFormValues) {
    setAuditFeedback(null);
    setCaseReviewResult(null);
    setDrgResult(null);
    setAuditResult(null);
    if (!selectedSnapshotId) {
      setAuditFeedback({ type: "error", text: "请先选择已生效病案快照。" });
      return;
    }
    try {
      const base = {
        contextSnapshotId: selectedSnapshotId,
        responsibleDepartmentId: values.responsibleDepartmentId.trim(),
      };
      const scenarioCode = values.scenarioCode.trim();
      const caseReview = await caseReviewMutation.mutateAsync({
        ...base,
        scenarioCode,
      });
      const drgGrouping = await drgMutation.mutateAsync({
        ...base,
        grouperVersion: values.grouperVersion.trim(),
        expectedGroupCode: values.expectedGroupCode.trim(),
        actualGroupCode: values.actualGroupCode.trim(),
        explanation: values.drgExplanation.trim(),
      });
      const audit = await auditMutation.mutateAsync({
        ...base,
        scenarioCode,
        indicatorId: values.indicatorId.trim(),
        dueAt: clinicalDateTimeInputToIso(values.dueAt),
        rules: [
          {
            ruleCode: values.ruleCode.trim(),
            ruleVersion: values.ruleVersion.trim(),
            issueType: values.issueType,
            severity: values.severity,
            maxAmount: numberOrUndefined(values.maxAmount),
            requiredClaimStatus: optionalText(values.requiredClaimStatus),
            requiredClaimType: optionalText(values.requiredClaimType),
            description: values.ruleDescription.trim(),
          },
        ],
      });
      setCaseReviewResult(caseReview);
      setDrgResult(drgGrouping);
      setAuditResult(audit);
      setAuditFeedback({
        type: "success",
        text:
          audit.auditStatus === "INSUFFICIENT_DATA"
            ? "当前快照未找到可核验的真实医保结算事实，未生成违规。"
            : "医保审核已基于真实结算事实执行，命中问题已由服务联动整改闭环。",
      });
      issuesQuery.refetch();
    } catch (error: unknown) {
      setAuditFeedback({ type: "error", text: getApiErrorMessage(error, "医保审核执行失败") });
    }
  }

  function openEvidence(issue: InsuranceIssuePageItem) {
    setSelectedIssue(issue);
    setDrawerOpen(true);
  }

  return (
    <PageShell
      title="医保智能审核"
      description="按真实结算事实核查病案"
      primary={
        <Button
          aria-label="执行审核并派整改"
          type="primary"
          icon={<SafetyCertificateOutlined />}
          loading={isRunning}
          disabled={!selectedSnapshotId || snapshotDetailQuery.isLoading}
          form="insurance-audit-form"
          htmlType="submit"
        >
          执行审核并派整改
        </Button>
      }
      extras={
        <Space wrap>
          <EvidenceDetailsToggle securityProfile={security.data} />
          <Button
            aria-label="刷新医保问题"
            icon={<ReloadOutlined />}
            onClick={() => issuesQuery.refetch()}
          >
            刷新
          </Button>
        </Space>
      }
      state={resolvePageState(issuesQuery.isLoading, issuesQuery.isError, errorStatus, issues)}
      stateProps={{
        title: issuesQuery.isError ? parsedError?.message : "当前筛选下暂无真实医保问题",
        description: issuesQuery.isError
          ? "请稍后重试；若持续失败，请联系信息科核查医保审核服务。失败已留痕，可在审计证据中追溯。"
          : "当前没有符合筛选条件的医保病案问题。",
        traceId: parsedError?.traceId,
        onRetry: () => issuesQuery.refetch(),
      }}
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Card>
          <Space wrap size="middle" align="center">
            <Select
              aria-label="问题状态"
              value={status}
              className="mk-select-narrow"
              onChange={setStatus}
              options={[
                { value: "OPEN", label: "未处理" },
                { value: "RECTIFICATION_CREATED", label: "已派整改" },
                { value: "RESOLVED", label: "已闭环" },
                { value: "WAIVED", label: "已豁免" },
              ]}
            />
            <Select
              aria-label="问题时间"
              value={timeScope}
              className="mk-select-narrow"
              onChange={setTimeScope}
              options={[
                { value: "THIS_MONTH", label: "本月" },
                { value: "LAST_7_DAYS", label: "近 7 日" },
                { value: "ALL", label: "全量" },
              ]}
            />
            <Select
              aria-label="问题级别"
              value={severity}
              className="mk-select-narrow"
              onChange={setSeverity}
              options={[
                { value: "P1", label: "高金额/高风险" },
                { value: "P0", label: "安全红线" },
                { value: "P2", label: "中危" },
                { value: "P3", label: "低危" },
              ]}
            />
          </Space>
        </Card>

        <Card title="医保病案审核输入">
          <Form
            id="insurance-audit-form"
            form={form}
            layout="vertical"
            onFinish={runAudit}
            initialValues={{
              scenarioCode: "A9",
              issueType: "FEE",
              severity: "P1",
            }}
          >
            <Space direction="vertical" size="small" className="mk-full-width">
              <Space wrap size="middle" className="mk-full-width">
                <Form.Item label="患者信息" htmlFor="insurance-snapshot-patient">
                  <Input
                    id="insurance-snapshot-patient"
                    value={snapshotPatientId}
                    placeholder="输入患者主索引或院内登记号检索病案快照"
                    onChange={(event) => {
                      setSnapshotPatientId(event.target.value);
                      setSelectedSnapshotId("");
                    }}
                  />
                </Form.Item>
                <Form.Item label="就诊信息" htmlFor="insurance-snapshot-encounter">
                  <Input
                    id="insurance-snapshot-encounter"
                    value={snapshotEncounterId}
                    placeholder="可按住院号、门诊号或就诊信息检索"
                    onChange={(event) => {
                      setSnapshotEncounterId(event.target.value);
                      setSelectedSnapshotId("");
                    }}
                  />
                </Form.Item>
              </Space>

              <ContextSnapshotSelector
                enabled={hasSnapshotFilter}
                loading={snapshotsQuery.isLoading}
                error={snapshotsQuery.isError}
                snapshots={snapshotsQuery.data?.items ?? []}
                selectedSnapshotId={selectedSnapshotId}
                onSelect={setSelectedSnapshotId}
                noun="病案快照"
              />

              {snapshotDetailQuery.data && (
                <Descriptions bordered size="small" column={3}>
                  <Descriptions.Item label="机构生效版本">
                    {evidenceText(
                      snapshotDetailQuery.data.runtimeReleaseId,
                      evidenceDetailsEnabled,
                      "机构生效版本已匹配",
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="质量状态">
                    {customerDisplayText(snapshotDetailQuery.data.qualityStatus)}
                  </Descriptions.Item>
                  <Descriptions.Item label="证据">
                    {evidenceText(
                      snapshotDetailQuery.data.traceId,
                      evidenceDetailsEnabled,
                      "快照证据已记录",
                    )}
                  </Descriptions.Item>
                </Descriptions>
              )}

              {useScopeDepartmentOption ? (
                <Alert
                  type="info"
                  showIcon
                  message="未读取到可选科室，已使用当前组织范围作为责任归属。"
                />
              ) : null}
              {useManualIndicatorOption ? (
                <Alert
                  type="info"
                  showIcon
                  message="未读取到已生效质控指标，先按本次医保规则依据归档。"
                />
              ) : null}

              <Space wrap size="middle" className="mk-full-width">
                <Form.Item
                  label="责任科室"
                  name="responsibleDepartmentId"
                  rules={[{ required: true, message: "请选择责任科室" }]}
                >
                  <Select
                    showSearch
                    filterOption={false}
                    onSearch={setDepartmentSearch}
                    onClear={() => setDepartmentSearch("")}
                    placeholder="选择责任科室"
                    options={effectiveDepartmentOptions}
                    loading={departmentsQuery.isLoading}
                    notFoundContent="暂无可选科室"
                  />
                </Form.Item>
                <Form.Item
                  label="质控指标"
                  name="indicatorId"
                  rules={[{ required: true, message: "请选择质控指标" }]}
                >
                  <Select
                    showSearch
                    allowClear
                    filterOption={false}
                    onSearch={setIndicatorSearch}
                    onClear={() => setIndicatorSearch("")}
                    placeholder="选择已生效指标"
                    options={effectiveIndicatorOptions}
                    loading={indicatorsQuery.isLoading}
                    notFoundContent="暂无已生效质控指标"
                  />
                </Form.Item>
              </Space>
              <Space wrap size="middle" className="mk-full-width">
                <Form.Item
                  label="审核场景"
                  name="scenarioCode"
                  rules={[{ required: true, message: "请输入审核场景" }]}
                >
                  <Input placeholder="输入本次医保审核场景" />
                </Form.Item>
                <RectificationDueAtField />
              </Space>
              <Space wrap size="middle" className="mk-full-width">
                <Form.Item
                  label="DRG 分组器版本"
                  name="grouperVersion"
                  rules={[{ required: true, message: "请输入分组器版本" }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  label="期望入组"
                  name="expectedGroupCode"
                  rules={[{ required: true, message: "请输入期望入组" }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  label="实际入组"
                  name="actualGroupCode"
                  rules={[{ required: true, message: "请输入实际入组" }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  label="入组说明"
                  name="drgExplanation"
                  rules={[{ required: true, message: "请输入入组说明" }]}
                >
                  <Input />
                </Form.Item>
              </Space>
              <Space wrap size="middle" className="mk-full-width">
                <Form.Item
                  label="医保规则依据"
                  name="ruleCode"
                  rules={[{ required: true, message: "请输入医保规则依据" }]}
                >
                  <Input placeholder="输入已生效医保规则依据" />
                </Form.Item>
                <Form.Item
                  label="依据版本"
                  name="ruleVersion"
                  rules={[{ required: true, message: "请输入依据版本" }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item label="问题类型" name="issueType">
                  <Select
                    options={[
                      { value: "FEE", label: "费用" },
                      { value: "CODING", label: "编码" },
                      { value: "DRG", label: "DRG/DIP" },
                      { value: "CLAIM_STATUS", label: "结算状态" },
                    ]}
                  />
                </Form.Item>
                <Form.Item label="规则级别" name="severity">
                  <Select
                    options={[
                      { value: "P1", label: "高风险" },
                      { value: "P2", label: "中风险" },
                      { value: "P3", label: "低风险" },
                    ]}
                  />
                </Form.Item>
              </Space>
              <Space wrap size="middle" className="mk-full-width">
                <Form.Item label="费用阈值" name="maxAmount">
                  <Input inputMode="decimal" placeholder="可选，按真实结算金额比较" />
                </Form.Item>
                <Form.Item label="期望结算状态" name="requiredClaimStatus">
                  <Input placeholder="可选" />
                </Form.Item>
                <Form.Item label="期望结算类型" name="requiredClaimType">
                  <Input placeholder="可选" />
                </Form.Item>
                <Form.Item
                  label="规则说明"
                  name="ruleDescription"
                  rules={[{ required: true, message: "请输入规则依据说明" }]}
                >
                  <Input />
                </Form.Item>
              </Space>
            </Space>
          </Form>
        </Card>

        {auditFeedback ? (
          <Alert type={auditFeedback.type} showIcon message={auditFeedback.text} />
        ) : null}

        <Space wrap size="middle" className="mk-full-width">
          <MetricCard title="真实医保问题总数" value={`${issuesQuery.data?.total ?? 0} 条`} />
          <MetricCard title="未处理问题" value={`${countOpenIssues(issues)} 个`} />
          <MetricCard title="最近审核整改" value={latestRectificationText(auditResult)} />
        </Space>

        {(caseReviewResult || drgResult || auditResult) && (
          <Card title="本次审核结果">
            <Space direction="vertical" size="middle" className="mk-full-width">
              {caseReviewResult && (
                <Descriptions bordered size="small" column={1}>
                  <Descriptions.Item label="病案内涵质控">
                    {caseReviewStatusText(caseReviewResult.reviewStatus)}
                  </Descriptions.Item>
                  <Descriptions.Item label="评估运行">
                    {evidenceText(
                      caseReviewResult.evaluationRunId,
                      evidenceDetailsEnabled,
                      "评估运行已记录",
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="模型状态">
                    {customerEnumLabel(caseReviewResult.modelStatus)}
                  </Descriptions.Item>
                  <Descriptions.Item label="问题 / 整改">
                    {caseReviewResult.findingCount} 个问题 / 整改任务 {caseReviewResult.taskCount}{" "}
                    个
                  </Descriptions.Item>
                </Descriptions>
              )}
              {drgResult && (
                <Descriptions bordered size="small" column={1}>
                  <Descriptions.Item label="DRG/DIP 入组">
                    {drgGroupingStatusText(drgResult.groupingStatus)}
                  </Descriptions.Item>
                  <Descriptions.Item label="期望 / 实际">
                    {drgResult.expectedGroupCode} / {drgResult.actualGroupCode}
                  </Descriptions.Item>
                  <Descriptions.Item label="解释">{drgResult.explanation}</Descriptions.Item>
                </Descriptions>
              )}
              {auditResult && (
                <Descriptions bordered size="small" column={1}>
                  <Descriptions.Item label="医保审核状态">
                    {insuranceAuditStatusText(auditResult.auditStatus)}
                  </Descriptions.Item>
                  <Descriptions.Item label="评估运行">
                    {evidenceText(
                      auditResult.evaluationRunId,
                      evidenceDetailsEnabled,
                      "评估运行已记录",
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="命中 / 整改">
                    {auditResult.findingCount} 个命中 / 整改任务 {auditResult.taskCount} 个
                  </Descriptions.Item>
                  <Descriptions.Item label="证据">
                    {evidenceText(auditResult.traceId, evidenceDetailsEnabled, "审核证据已记录")}
                  </Descriptions.Item>
                </Descriptions>
              )}
            </Space>
          </Card>
        )}

        <Card title="医保问题列表">
          <List
            dataSource={issues}
            locale={{ emptyText: <Empty description="当前筛选下暂无真实医保问题" /> }}
            renderItem={(issue) => (
              <List.Item
                actions={[
                  <Button
                    key="evidence"
                    aria-label="查看证据"
                    icon={<AuditOutlined />}
                    onClick={() => openEvidence(issue)}
                  >
                    查看证据
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  avatar={severityTag(issue.severity)}
                  title={
                    <Space wrap>
                      <Text strong>
                        {evidenceText(issue.claimId, evidenceDetailsEnabled, "医保结算已关联")}
                      </Text>
                      {issueTypeTag(issue.issueType)}
                      {issueStatusTag(issue.status)}
                    </Space>
                  }
                  description={
                    <Space direction="vertical" size={4}>
                      <Space wrap>
                        <Text type="secondary">规则</Text>
                        <Text>{ruleText(issue, evidenceDetailsEnabled)}</Text>
                        <Text type="secondary">科室</Text>
                        <Text>{issue.departmentId ?? "未指定"}</Text>
                      </Space>
                      <Text>{`证据摘要：${insuranceEvidenceSummary(issue, evidenceDetailsEnabled)}`}</Text>
                      <Space wrap>
                        <Text type="secondary">金额 / 阈值</Text>
                        <Text>
                          {formatAmount(issue.claimAmount)} / {formatAmount(issue.thresholdAmount)}
                        </Text>
                        <Text type="secondary">证据</Text>
                        <Text>
                          {evidenceText(issue.traceId, evidenceDetailsEnabled, "证据已记录")}
                        </Text>
                      </Space>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        </Card>
      </Space>

      <Drawer
        title={
          <Space>
            <FileSearchOutlined />
            <span>医保问题证据</span>
          </Space>
        }
        placement="right"
        width={640}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        {selectedIssue ? (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="医保问题">
              {evidenceText(selectedIssue.issueId, evidenceDetailsEnabled, "医保问题已登记")}
            </Descriptions.Item>
            <Descriptions.Item label="结算事实">
              {evidenceText(selectedIssue.claimId, evidenceDetailsEnabled, "医保结算已关联")}
            </Descriptions.Item>
            <Descriptions.Item label="规则依据">
              {ruleText(selectedIssue, evidenceDetailsEnabled)}
            </Descriptions.Item>
            <Descriptions.Item label="问题状态">
              {issueStatusTag(selectedIssue.status)}
            </Descriptions.Item>
            <Descriptions.Item label="证据摘要">
              {insuranceEvidenceSummary(selectedIssue, evidenceDetailsEnabled)}
            </Descriptions.Item>
            <Descriptions.Item label="评估运行">
              {evidenceText(
                selectedIssue.evaluationRunId,
                evidenceDetailsEnabled,
                "评估运行已记录",
              )}
            </Descriptions.Item>
            <Descriptions.Item label="证据">
              {evidenceText(selectedIssue.traceId, evidenceDetailsEnabled, "证据已记录")}
            </Descriptions.Item>
          </Descriptions>
        ) : null}
      </Drawer>
    </PageShell>
  );
}

function MetricCard({ title, value }: { title: string; value: string }) {
  return (
    <Card className="mk-card-compact">
      <Space direction="vertical" size={2}>
        <Text type="secondary">{title}</Text>
        <Text strong>{value}</Text>
      </Space>
    </Card>
  );
}

function resolveTimeRange(scope: TimeScope): Pick<InsuranceIssuesQueryParams, "from" | "to"> {
  if (scope === "ALL") {
    return {};
  }
  const now = new Date();
  const from =
    scope === "THIS_MONTH"
      ? new Date(now.getFullYear(), now.getMonth(), 1)
      : new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: now.toISOString() };
}

function resolvePageState(
  isLoading: boolean,
  isError: boolean,
  errorStatus: number | undefined,
  items: InsuranceIssuePageItem[],
) {
  if (isLoading) return "loading";
  if (isError) return errorStatus === 403 ? "forbidden" : "error";
  if (items.length === 0) return "ready";
  return "ready";
}

function issueStatusTag(status: string) {
  if (status === "OPEN") {
    return <Tag color="warning">未处理</Tag>;
  }
  if (status === "RECTIFICATION_CREATED") {
    return <Tag color="processing">已派整改</Tag>;
  }
  if (status === "RESOLVED") {
    return <Tag color="success">已闭环</Tag>;
  }
  if (status === "WAIVED") {
    return <Tag>已豁免</Tag>;
  }
  return <Tag>{customerEnumLabel(status)}</Tag>;
}

function issueTypeTag(type: string) {
  if (type === "FEE") {
    return <Tag color="gold">费用</Tag>;
  }
  if (type === "CODING") {
    return <Tag color="blue">编码</Tag>;
  }
  if (type === "DRG") {
    return <Tag color="purple">DRG/DIP</Tag>;
  }
  if (type === "CLAIM_STATUS") {
    return <Tag color="cyan">结算状态</Tag>;
  }
  return <Tag>{customerEnumLabel(type)}</Tag>;
}

function insuranceEvidenceSummary(
  issue: Pick<InsuranceIssuePageItem, "issueType" | "evidenceSummary">,
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) {
    return issue.evidenceSummary;
  }
  if (issue.issueType === "FEE") {
    return "费用超阈值证据已记录";
  }
  if (issue.issueType === "CODING") {
    return "编码问题证据已记录";
  }
  if (issue.issueType === "DRG") {
    return "DRG/DIP 问题证据已记录";
  }
  if (issue.issueType === "CLAIM_STATUS") {
    return "结算状态问题证据已记录";
  }
  return "医保问题证据已记录";
}

function ruleText(
  issue: Pick<InsuranceIssuePageItem, "ruleCode" | "ruleVersion">,
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) {
    return `${issue.ruleCode}@${issue.ruleVersion}`;
  }
  return "规则依据已关联";
}

function caseReviewStatusText(status: string) {
  if (status === "PASS") {
    return "病案内涵质控通过";
  }
  if (status === "NON_COMPLIANT") {
    return "病案内涵质控发现问题";
  }
  return customerEnumLabel(status);
}

function drgGroupingStatusText(status: string) {
  if (status === "MATCHED") {
    return "DRG/DIP 入组一致";
  }
  if (status === "MISMATCHED") {
    return "DRG/DIP 入组不一致";
  }
  return customerEnumLabel(status);
}

function insuranceAuditStatusText(status: string) {
  if (status === "ISSUE_FOUND") {
    return "发现医保问题";
  }
  if (status === "NO_ISSUE") {
    return "未发现医保问题";
  }
  if (status === "INSUFFICIENT_DATA") {
    return "缺少可核验结算事实";
  }
  return customerEnumLabel(status);
}

function evidenceText(
  value: string | null | undefined,
  evidenceDetailsEnabled: boolean,
  businessText: string,
) {
  if (evidenceDetailsEnabled) {
    return value || "--";
  }
  return businessText;
}

function severityTag(severity: string) {
  if (severity === "P0") {
    return <Tag color="error">安全红线</Tag>;
  }
  if (severity === "P1") {
    return <Tag color="volcano">高优先级</Tag>;
  }
  if (severity === "P2") {
    return <Tag color="gold">一般优先级</Tag>;
  }
  return <Tag>{severity ? customerEnumLabel(severity) : "未分级"}</Tag>;
}

function countOpenIssues(items: InsuranceIssuePageItem[]) {
  return items.filter((item) => item.status === "OPEN").length;
}

function latestRectificationText(audit: InsuranceAuditResponse | null) {
  if (!audit) {
    return "尚未执行";
  }
  return `整改任务 ${audit.taskCount} 个`;
}

function formatAmount(value: number | null) {
  if (value === null || Number.isNaN(value)) {
    return "未返回";
  }
  return value.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function optionalText(value?: string | null) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function resolveScopeDepartmentOption(dataScope: SecurityProfile["dataScope"] | undefined) {
  const candidates: Array<[string, string | null | undefined]> = [
    ["当前科室", dataScope?.departmentId],
    ["当前病区", dataScope?.wardId],
    ["当前站点", dataScope?.siteId],
    ["当前院区", dataScope?.campusId],
    ["当前机构", dataScope?.hospitalId],
    ["当前集团", dataScope?.groupId],
    ["当前租户", dataScope?.tenantId],
  ];
  const fallback = candidates.find(([, value]) => Boolean(value?.trim()));
  if (!fallback) {
    return null;
  }
  const [label, value] = fallback;
  return { value: value as string, label: `${label} · ${value}` };
}

function numberOrUndefined(value?: string) {
  const trimmed = value?.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function getResponseStatus(error: unknown) {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = (error as { response?: { status?: number } }).response;
    return response?.status;
  }
  return undefined;
}
