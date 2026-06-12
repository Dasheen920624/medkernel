import type { Key } from "react";
import { useEffect, useMemo, useRef, useState } from "react";

import {
  Alert,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from "antd";
import {
  CheckCircleOutlined,
  CloudUploadOutlined,
  RollbackOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";

import {
  parseSavedExperienceView,
  useBatchConfirmTerminologyCandidates,
  useBuildTerminologyKnowledgePackage,
  useConfirmTerminologyCandidate,
  useLargeListExportJob,
  useLocalTerms,
  usePackages,
  usePackageReleaseAdapters,
  useReleasePackage,
  useRollbackPackage,
  useSaveView,
  useSavedViews,
  useSecurityProfile,
  useStandardTerms,
  useSubmitLargeListExport,
  useTerminologyCandidates,
  useTerminologyConflicts,
  useTerminologyMappings,
  type MappingConflict,
  type ReleaseScopeType,
  type SecurityProfile,
  type TermMapping,
  type TermMappingCandidate,
} from "@/shared/api/hooks";
import { canAccessRoute, findRouteByPath } from "@/shared/config/routes";
import { AsyncExportAction } from "@/shared/ui/AsyncExportAction";
import { EvidenceDetailDrawer, type EvidenceDetailSection } from "@/shared/ui/EvidenceDetailDrawer";
import { ExperienceFilterBar } from "@/shared/ui/ExperienceFilterBar";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import { PageState } from "@/shared/ui/PageState";
import type { PageStateKind } from "@/shared/ui/PageState.contract";
import { ServerDataTable } from "@/shared/ui/ServerDataTable";
import { StepFlow } from "@/shared/ui/StepFlow";
import type {
  ExperienceColumn,
  ExperienceFilterValue,
  ExperiencePageRequest,
  ExperienceViewSnapshot,
  RouteExperience,
} from "@/shared/ui/experienceTypes";
import { buildAsyncExportRequest, normalizePageResponse } from "@/shared/ui/experienceView";

import styles from "./TerminologyMapping.module.css";

const { Text } = Typography;

const VIEW_KEY = "terminology.mapping";
const PAGE_SIZE = 20;
const AUTHORING_CONTEXT_VERSION = "AUTHORING";
const route = findRouteByPath("/terminology/mapping");

if (!route?.experience) {
  throw new Error("术语与字典页面缺少体验声明");
}

const PAGE_META: { title: string; experience: RouteExperience } = {
  title: route.title,
  experience: route.experience,
};

const DEFAULT_REQUEST: ExperiencePageRequest = {
  pageNumber: 1,
  pageSize: PAGE_SIZE,
  sortBy: "updatedAt",
  sortOrder: "desc",
  filters: {},
};

const STATUS_COLOR: Record<TermMapping["status"], string> = {
  CONFIRMED: "green",
  DRAFT: "orange",
  SUPERSEDED: "blue",
  ROLLED_BACK: "red",
};

const STATUS_LABEL: Record<TermMapping["status"], string> = {
  CONFIRMED: "已确认",
  DRAFT: "草稿",
  SUPERSEDED: "已替换",
  ROLLED_BACK: "已回滚",
};

const RISK_COLOR: Record<TermMapping["riskLevel"], string> = {
  HIGH: "red",
  MEDIUM: "orange",
  LOW: "blue",
};

const RISK_LABEL: Record<TermMapping["riskLevel"], string> = {
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低",
};

const PACKAGE_STATUS_LABEL: Record<string, string> = {
  DRAFT: "草稿",
  PUBLISHED: "已发布",
  ACTIVE: "生效中",
  OFFLINE: "已下线",
  ARCHIVED: "已归档",
};

function parseReleaseScopeType(value: string | undefined): ReleaseScopeType | undefined {
  switch (value?.trim().toUpperCase()) {
    case "REGION":
      return "REGION";
    case "FACILITY":
      return "FACILITY";
    case "CAMPUS":
      return "CAMPUS";
    case "DEPARTMENT":
      return "DEPARTMENT";
    case "WARD":
      return "WARD";
    default:
      return undefined;
  }
}

const tableColumns: Array<ExperienceColumn<TermMapping>> = [
  { key: "sourceSystem", title: "来源系统", dataIndex: "sourceSystem", always: true },
  { key: "category", title: "类别", dataIndex: "category" },
  {
    key: "riskLevel",
    title: "风险等级",
    dataIndex: "riskLevel",
    render: (value) => {
      const risk = value as TermMapping["riskLevel"];
      return <Tag color={RISK_COLOR[risk]}>{RISK_LABEL[risk]}</Tag>;
    },
  },
  {
    key: "confidence",
    title: "置信度",
    dataIndex: "confidence",
    render: (value) => `${((value as number) * 100).toFixed(1)}%`,
  },
  {
    key: "status",
    title: "状态",
    dataIndex: "status",
    render: (value) => {
      const status = value as TermMapping["status"];
      return <Tag color={STATUS_COLOR[status]}>{STATUS_LABEL[status]}</Tag>;
    },
  },
  { key: "updatedAt", title: "更新时间", dataIndex: "updatedAt" },
];
const DEFAULT_VISIBLE_COLUMNS = tableColumns.map((column) => column.key);

function getFilterValue(
  filters: readonly ExperienceFilterValue[],
  key: string,
): string | undefined {
  const value = filters.find((filter) => filter.key === key)?.value;
  return typeof value === "string" ? value : undefined;
}

function buildFilterRecord(filters: readonly ExperienceFilterValue[]): Record<string, unknown> {
  return Object.fromEntries(
    filters
      .filter((filter) => filter.value !== undefined)
      .map((filter) => [filter.key, filter.value]),
  );
}

function detailSections(mapping?: TermMapping): EvidenceDetailSection[] {
  if (!mapping) return [];

  return [
    {
      key: "summary",
      title: "映射摘要",
      items: [
        { label: "状态", value: STATUS_LABEL[mapping.status] },
        { label: "风险等级", value: RISK_LABEL[mapping.riskLevel] },
        { label: "置信度", value: `${(mapping.confidence * 100).toFixed(1)}%` },
      ],
    },
    {
      key: "source",
      title: "来源与证据",
      items: [
        { label: "来源系统", value: mapping.sourceSystem },
        { label: "类别", value: mapping.category },
        { label: "证据", value: mapping.evidenceText ?? "暂无补充证据" },
        { label: "确认人", value: mapping.confirmedBy ?? "尚未确认" },
        { label: "确认时间", value: mapping.confirmedAt ?? "尚未确认" },
      ],
    },
    {
      key: "expert",
      title: "技术字段",
      items: [
        { label: "映射 ID", value: mapping.id, expertOnly: true },
        { label: "院内编码 ID", value: mapping.localTermId, expertOnly: true },
        { label: "标准编码 ID", value: mapping.standardTermId, expertOnly: true },
      ],
    },
  ];
}

function percent(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

function hasPermission(profile: ReturnType<typeof useSecurityProfile>["data"], code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function hasOrganizationAdminRole(roles: SecurityProfile["roles"] | undefined) {
  return (roles ?? []).some((role) => {
    const normalized = role.code.trim().toUpperCase().replace(/[-.]/g, "_");
    return (
      normalized === "ORGANIZATION_ADMIN" ||
      normalized === "ROLE_ORGANIZATION_ADMIN" ||
      normalized === "PLATFORM_GOVERNANCE_ADMIN" ||
      normalized === "ROLE_PLATFORM_GOVERNANCE_ADMIN"
    );
  });
}

function packageScopeOptions(profile: SecurityProfile | undefined) {
  const scope = profile?.dataScope;
  if (!scope) return [];
  const facilityId = scope.siteId ?? scope.hospitalId;
  return [
    { level: "DEPARTMENT", code: scope.departmentId, label: "当前科室" },
    { level: "CAMPUS", code: scope.campusId, label: "当前院区" },
    { level: "FACILITY", code: facilityId, label: "当前机构" },
    { level: "REGION", code: scope.groupId, label: "当前区域" },
    { level: "TENANT", code: scope.tenantId, label: "当前服务空间" },
  ]
    .filter((item): item is { level: string; code: string; label: string } => Boolean(item.code))
    .map((item) => ({
      value: `${item.level}|${item.code}`,
      label: `${item.label} · ${item.code}`,
      level: item.level,
      code: item.code,
    }));
}

function candidateRiskTag(candidate: TermMappingCandidate) {
  if (candidate.highRiskFlag) {
    return <Tag color="red">高危</Tag>;
  }
  return <Tag color={RISK_COLOR[candidate.riskLevel]}>{RISK_LABEL[candidate.riskLevel]}</Tag>;
}

export default function TerminologyMapping() {
  const savedViews = useSavedViews(VIEW_KEY);
  const saveView = useSaveView();
  const submitExport = useSubmitLargeListExport();
  const pollExport = useLargeListExportJob();
  const savedViewApplied = useRef(false);
  const [filters, setFilters] = useState<ExperienceFilterValue[]>([]);
  const [request, setRequest] = useState<ExperiencePageRequest>(DEFAULT_REQUEST);
  const [visibleColumnKeys, setVisibleColumnKeys] =
    useState<readonly string[]>(DEFAULT_VISIBLE_COLUMNS);
  const [expertMode, setExpertMode] = useState(false);
  const [selectionSnapshot, setSelectionSnapshot] = useState<{
    selectedRowKeys: Key[];
    rowCount: number;
  }>();
  const [selectedMapping, setSelectedMapping] = useState<TermMapping>();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [buildOpen, setBuildOpen] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [rollbackOpen, setRollbackOpen] = useState(false);
  const [confirmForm] = Form.useForm();
  const [buildForm] = Form.useForm();
  const [publishForm] = Form.useForm();
  const [rollbackForm] = Form.useForm();

  const security = useSecurityProfile();
  const canPublish = hasPermission(security.data, "term.publish");
  const query = useTerminologyMappings({
    page: request.pageNumber,
    size: request.pageSize,
    sort:
      request.sortBy && request.sortOrder ? `${request.sortBy},${request.sortOrder}` : undefined,
    status: getFilterValue(filters, "status") as TermMapping["status"] | undefined,
    sourceSystem: getFilterValue(filters, "sourceSystem"),
    keyword: getFilterValue(filters, "keyword"),
  });
  const standardTerms = useStandardTerms({ page: 0, size: PAGE_SIZE, status: "ACTIVE" });
  const localTerms = useLocalTerms({
    page: 0,
    size: PAGE_SIZE,
    sourceSystem: getFilterValue(filters, "sourceSystem"),
    status: "UNMAPPED",
  });
  const candidates = useTerminologyCandidates({
    page: 0,
    size: PAGE_SIZE,
    status: "PENDING",
    riskLevel: "HIGH",
  });
  const conflicts = useTerminologyConflicts({ page: 0, size: 10, status: "OPEN" });
  const packages = usePackages({ page: 0, size: 10, assetType: "TERMINOLOGY" });
  const releaseAdapters = usePackageReleaseAdapters(canPublish);
  const confirmCandidate = useConfirmTerminologyCandidate();
  const batchConfirmCandidates = useBatchConfirmTerminologyCandidates();
  const buildPackage = useBuildTerminologyKnowledgePackage();
  const publishPackage = useReleasePackage();
  const rollbackPackage = useRollbackPackage();

  useEffect(() => {
    if (savedViewApplied.current || !savedViews.data || savedViews.data.length === 0) {
      return;
    }
    const savedSnapshot = parseSavedExperienceView(
      savedViews.data.find((view) => view.defaultView) ?? savedViews.data[0],
    );
    if (!savedSnapshot) {
      savedViewApplied.current = true;
      return;
    }
    setFilters([...savedSnapshot.filters]);
    setRequest(savedSnapshot.pageRequest);
    setVisibleColumnKeys(savedSnapshot.visibleColumnKeys);
    setExpertMode(savedSnapshot.expertMode);
    savedViewApplied.current = true;
  }, [savedViews.data]);

  function snapshot(
    nextFilters = filters,
    nextRequest = request,
    nextColumns = visibleColumnKeys,
    nextExpertMode = expertMode,
  ): ExperienceViewSnapshot {
    return {
      viewKey: VIEW_KEY,
      filters: nextFilters,
      pageRequest: nextRequest,
      visibleColumnKeys: nextColumns,
      expertMode: nextExpertMode,
      capturedAt: new Date().toISOString(),
    };
  }

  function updateFilters(nextFilters: ExperienceFilterValue[]) {
    const nextRequest = {
      ...request,
      pageNumber: 1,
      filters: buildFilterRecord(nextFilters),
    };
    setFilters(nextFilters);
    setRequest(nextRequest);
  }

  function saveCurrentView() {
    void saveView.mutateAsync({
      pageKey: VIEW_KEY,
      viewName: "默认视图",
      snapshot: snapshot(),
      defaultView: true,
    });
  }

  const routeAllowed = !security.data || canAccessRoute(route, security.data);
  const canExport = hasPermission(security.data, "list.export");
  const canWrite = hasPermission(security.data, "term.write");
  const canRollback = hasPermission(security.data, "package.rollback");
  const mappingItems = query.data?.items ?? [];
  const standardItems = standardTerms.data?.items ?? [];
  const localItems = localTerms.data?.items ?? [];
  const candidateItems = candidates.data?.items ?? [];
  const conflictItems = conflicts.data?.items ?? [];
  const packageItems = packages.data?.items ?? [];
  const highRiskCandidates = candidateItems.filter((candidate) => candidate.highRiskFlag);
  const ordinaryCandidates = candidateItems.filter((candidate) => !candidate.highRiskFlag);
  const selectedCandidate = highRiskCandidates[0] ?? candidateItems[0];
  const selectedPackage = packageItems[0];
  const currentPackageVersion = selectedPackage?.packageVersion;
  const requestPackageVersion = currentPackageVersion ?? AUTHORING_CONTEXT_VERSION;
  const scopeOptions = packageScopeOptions(security.data);
  const rollbackCandidates = selectedPackage
    ? packageItems.filter(
        (item) =>
          item.packageId !== selectedPackage.packageId &&
          item.packageCode === selectedPackage.packageCode &&
          item.status === "OFFLINE",
      )
    : [];
  const canReleaseSelected =
    selectedPackage?.status === "DRAFT" || selectedPackage?.status === "PUBLISHED";
  const canRollbackSelected = selectedPackage?.status === "ACTIVE" && rollbackCandidates.length > 0;
  const usableReleaseAdapters = (releaseAdapters.data ?? []).filter(
    (adapter) =>
      adapter.status === "ACTIVE" &&
      adapter.healthStatus === "HEALTHY" &&
      adapter.connectorAvailable,
  );

  let pageState: PageStateKind = "ready";
  if (!routeAllowed) pageState = "forbidden";
  else if (query.isLoading) pageState = "loading";
  else if (query.isError) pageState = "error";
  else if (mappingItems.length === 0) pageState = "empty";

  const exportRequest = buildAsyncExportRequest({
    resourceType: "TERMINOLOGY_MAPPING",
    requestSnapshot: snapshot(),
    selectedScope: "currentPage",
    selectionSnapshot,
    reason: "导出字典映射核查结果",
  });

  const stepPanels = useMemo(
    () => ({
      evidence_rollback: (
        <Space direction="vertical" size="small" className="mk-full-width">
          <Text>
            <Text strong>选字典</Text>：标准 {standardItems.length} 条，院内待映射{" "}
            {localItems.length} 条
          </Text>
          <Text>
            <Text strong>生成候选</Text>：待确认 {candidateItems.length} 条，高危{" "}
            {highRiskCandidates.length} 条
          </Text>
          <Text>
            <Text strong>逐条确认</Text>：高危候选必须二次确认，普通候选才允许批量确认
          </Text>
          <Text>
            <Text strong>证据/回滚</Text>：映射包发布、灰度和回滚均由 API-04 留审计证据
          </Text>
        </Space>
      ),
    }),
    [candidateItems.length, highRiskCandidates.length, localItems.length, standardItems.length],
  );

  const candidateColumns: ColumnsType<TermMappingCandidate> = [
    {
      title: "候选",
      dataIndex: "id",
      width: 120,
      render: (id) => <Text strong>#{id}</Text>,
    },
    {
      title: "语义分",
      dataIndex: "semanticMatchScore",
      width: 120,
      render: (value: number) => percent(value),
    },
    {
      title: "风险",
      dataIndex: "riskLevel",
      width: 140,
      render: (_, candidate) => candidateRiskTag(candidate),
    },
    {
      title: "证据",
      dataIndex: "evidenceText",
      render: (value?: string) => value ?? "暂无候选证据",
    },
  ];

  const conflictColumns: ColumnsType<MappingConflict> = [
    {
      title: "冲突类型",
      dataIndex: "conflictType",
      width: 160,
      render: (value: MappingConflict["conflictType"]) =>
        value === "ONE_TO_MANY" ? "一对多冲突" : value,
    },
    {
      title: "风险",
      dataIndex: "riskLevel",
      width: 100,
      render: (value: MappingConflict["riskLevel"]) => (
        <Tag color={RISK_COLOR[value]}>{RISK_LABEL[value]}</Tag>
      ),
    },
    {
      title: "待裁说明",
      dataIndex: "description",
    },
  ];

  async function submitHighRiskConfirmation() {
    if (!selectedCandidate) return;
    const values = await confirmForm.validateFields();
    await confirmCandidate.mutateAsync({
      candidateId: selectedCandidate.id,
      request: {
        packageVersion: requestPackageVersion,
        reviewNote: selectedCandidate.highRiskFlag ? "逐条确认高危候选" : "确认普通候选",
        highRiskAcknowledged: Boolean(values.highRiskAcknowledged),
        highRiskReason: values.highRiskReason,
      },
    });
    setConfirmOpen(false);
    confirmForm.resetFields();
  }

  async function submitOrdinaryBatchConfirmation() {
    if (ordinaryCandidates.length === 0 || highRiskCandidates.length > 0) {
      return;
    }
    await batchConfirmCandidates.mutateAsync({
      candidateIds: ordinaryCandidates.map((candidate) => candidate.id),
      request: { packageVersion: requestPackageVersion, reviewNote: "批量确认普通候选" },
    });
  }

  function openBuildPackage() {
    const defaultScope = scopeOptions[0];
    if (!defaultScope) return;
    buildForm.setFieldsValue({
      packageCode: selectedPackage?.packageCode ?? "TERM.MAPPING",
      packageVersion: undefined,
      name: selectedPackage?.name ?? "术语映射包",
      scopeKey: defaultScope.value,
    });
    setBuildOpen(true);
  }

  async function submitBuildPackage() {
    const values = await buildForm.validateFields();
    const scope = scopeOptions.find((item) => item.value === values.scopeKey);
    if (!scope) return;
    await buildPackage.mutateAsync({
      packageCode: values.packageCode.trim(),
      packageVersion: values.packageVersion.trim(),
      scopeLevel: scope.level,
      scopeCode: scope.code,
      name: values.name.trim(),
    });
    setBuildOpen(false);
    buildForm.resetFields();
  }

  async function submitPublish() {
    if (!selectedPackage || !currentPackageVersion) return;
    const values = await publishForm.validateFields();
    const [, rawScopeLevel, scopeCode] = (selectedPackage.primaryAssetId ?? "").split("|");
    const scopeType = values.releaseMode === "FULL" ? "ALL" : parseReleaseScopeType(rawScopeLevel);
    if (!scopeType) {
      publishForm.setFields([
        {
          name: "targetOrgUnitId",
          errors: ["知识包缺少有效组织作用域，无法灰度发布"],
        },
      ]);
      return;
    }
    publishForm.setFields([{ name: "targetOrgUnitId", errors: [] }]);
    await publishPackage.mutateAsync({
      packageId: selectedPackage.packageId,
      request: {
        packageVersion: currentPackageVersion,
        strategy: values.releaseMode === "FULL" ? "FULL" : "GRAYSCALE",
        targetOrgUnitId: values.targetOrgUnitId,
        scopeType,
        scopeValue: values.releaseMode === "FULL" ? "" : (scopeCode ?? values.targetOrgUnitId),
        adapterIds: values.adapterIds,
        reason: values.reason,
      },
    });
    setPublishOpen(false);
    publishForm.resetFields();
  }

  async function submitRollback() {
    if (!selectedPackage || !currentPackageVersion) return;
    const values = await rollbackForm.validateFields();
    await rollbackPackage.mutateAsync({
      packageId: selectedPackage.packageId,
      request: {
        packageVersion: currentPackageVersion,
        targetPackageId: values.targetPackageId,
        confirmedCurrentVersion: currentPackageVersion,
        confirmedTargetVersion: values.confirmedTargetVersion,
        reason: values.reason,
        confirmedHighRisk: values.confirmedHighRisk,
      },
    });
    setRollbackOpen(false);
    rollbackForm.resetFields();
  }

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
      expertMode={expertMode}
      onExpertModeChange={setExpertMode}
      primary={
        <Button
          type="primary"
          aria-label="确认候选"
          icon={<SafetyCertificateOutlined aria-hidden="true" />}
          disabled={!canWrite || !selectedCandidate}
          onClick={() => setConfirmOpen(true)}
        >
          确认候选
        </Button>
      }
      extras={
        <Space wrap>
          <AsyncExportAction
            enabled
            permissionGranted={canExport}
            request={exportRequest}
            onSubmit={submitExport.mutateAsync}
            onPoll={pollExport.mutateAsync}
          />
          <Button
            aria-label="构建映射包"
            icon={<CheckCircleOutlined aria-hidden="true" />}
            disabled={!canWrite || mappingItems.length === 0 || scopeOptions.length === 0}
            onClick={openBuildPackage}
          >
            构建映射包
          </Button>
          <Button
            aria-label="发布映射包"
            icon={<CloudUploadOutlined aria-hidden="true" />}
            disabled={!canPublish || !selectedPackage || !canReleaseSelected}
            onClick={() => {
              const [, , scopeCode] = (selectedPackage?.primaryAssetId ?? "").split("|");
              publishForm.setFieldsValue({
                targetOrgUnitId:
                  scopeCode ??
                  security.data?.dataScope.departmentId ??
                  security.data?.dataScope.hospitalId,
                adapterIds: usableReleaseAdapters.map((adapter) => adapter.adapterId),
              });
              setPublishOpen(true);
            }}
          >
            发布映射包
          </Button>
          <Button
            aria-label="回滚映射包"
            icon={<RollbackOutlined aria-hidden="true" />}
            disabled={!canRollback || !selectedPackage || !canRollbackSelected}
            onClick={() => setRollbackOpen(true)}
          >
            回滚映射包
          </Button>
        </Space>
      }
    >
      <ExperienceFilterBar
        filters={PAGE_META.experience.defaultFilters}
        value={filters}
        onChange={updateFilters}
        onSaveView={saveCurrentView}
      />
      <PageState
        state={pageState}
        title={pageState === "empty" ? "暂无字典映射条目" : undefined}
        description={pageState === "empty" ? "当前筛选范围内没有可核查的映射条目。" : undefined}
        traceId={query.data?.traceId}
        onRetry={query.refetch}
      >
        {query.data && (
          <Space direction="vertical" size="large" className="mk-full-width">
            <div className={styles.summaryGrid}>
              <Card className={styles.summaryCard}>
                <Statistic
                  title="标准字典"
                  value={standardTerms.data?.total ?? standardItems.length}
                />
                <Text type="secondary">ICD / LOINC / 药品本位码等分页读取</Text>
              </Card>
              <Card className={styles.summaryCard}>
                <Statistic title="院内待映射" value={localTerms.data?.total ?? localItems.length} />
                <Text type="secondary">按来源系统和关键词服务端筛选</Text>
              </Card>
              <Card className={styles.summaryCard}>
                <Statistic title="高危候选" value={highRiskCandidates.length} />
                <Text type="secondary">高危逐条确认，禁批量通过</Text>
              </Card>
              <Card className={styles.summaryCard}>
                <Statistic title="待裁冲突数" value={conflictItems.length} />
                <Text type="secondary">一对多 / 多对一保持人工裁决</Text>
              </Card>
            </div>

            <StepFlow currentStep="evidence_rollback" panelByStep={stepPanels} />

            <ServerDataTable<TermMapping>
              viewKey={VIEW_KEY}
              rowKey="id"
              columns={tableColumns}
              query={normalizePageResponse(query.data)}
              request={request}
              loading={false}
              partial={
                query.data.partial
                  ? { ...query.data.partial, onRetryFailures: () => void query.refetch() }
                  : undefined
              }
              expertMode={expertMode}
              initialVisibleColumnKeys={visibleColumnKeys}
              onRequestChange={setRequest}
              onOpenDetail={setSelectedMapping}
              onViewSnapshotChange={(nextSnapshot) =>
                setVisibleColumnKeys(nextSnapshot.visibleColumnKeys)
              }
              onSelectionSnapshotChange={setSelectionSnapshot}
            />

            <Card title="候选映射" className={styles.sectionCard}>
              {highRiskCandidates.length > 0 && (
                <Alert
                  type="warning"
                  showIcon
                  className={styles.sectionAlert}
                  message="高危近似"
                  description="当前队列包含高危近似候选，系统禁用批量确认；必须逐条二次确认。"
                />
              )}
              <Space direction="vertical" size="middle" className="mk-full-width">
                <Table<TermMappingCandidate>
                  rowKey="id"
                  columns={candidateColumns}
                  dataSource={candidateItems}
                  pagination={false}
                  scroll={{ x: 720 }}
                  size="small"
                />
                <Button
                  disabled={
                    !canWrite || highRiskCandidates.length > 0 || ordinaryCandidates.length === 0
                  }
                  onClick={() => void submitOrdinaryBatchConfirmation()}
                >
                  批量确认候选
                </Button>
              </Space>
            </Card>

            <Card title="冲突待裁" className={styles.sectionCard}>
              <Table<MappingConflict>
                rowKey="id"
                columns={conflictColumns}
                dataSource={conflictItems}
                pagination={false}
                scroll={{ x: 640 }}
                size="small"
              />
            </Card>

            <Card title="映射包发布" className={styles.sectionCard}>
              {selectedPackage ? (
                <div className={styles.descriptionScroll}>
                  <Descriptions column={3} size="small" bordered>
                    <Descriptions.Item label="名称">{selectedPackage.name}</Descriptions.Item>
                    <Descriptions.Item label="版本">
                      {selectedPackage.packageVersion}
                    </Descriptions.Item>
                    <Descriptions.Item label="状态">
                      <Tag>{PACKAGE_STATUS_LABEL[selectedPackage.status]}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="范围">
                      {selectedPackage.primaryAssetId?.split("|").slice(1).join(":") ?? "未声明"}
                    </Descriptions.Item>
                    <Descriptions.Item label="资产数">
                      {selectedPackage.itemCount}
                    </Descriptions.Item>
                    <Descriptions.Item label="包编码">
                      <Text code>{selectedPackage.packageCode}</Text>
                    </Descriptions.Item>
                  </Descriptions>
                </div>
              ) : (
                <Text type="secondary">暂无可发布映射包。</Text>
              )}
            </Card>
          </Space>
        )}
      </PageState>
      <EvidenceDetailDrawer
        open={!!selectedMapping}
        title="字典映射详情"
        expertMode={expertMode}
        sections={detailSections(selectedMapping)}
        traceId={query.data?.traceId}
        onClose={() => setSelectedMapping(undefined)}
      />
      <Modal
        title={selectedCandidate?.highRiskFlag ? "确认高危候选" : "确认普通候选"}
        open={confirmOpen}
        onCancel={() => setConfirmOpen(false)}
        onOk={() => void submitHighRiskConfirmation()}
        okText="提交确认"
        destroyOnClose
      >
        <Form form={confirmForm} layout="vertical" preserve={false}>
          <Alert
            type={selectedCandidate?.highRiskFlag ? "warning" : "info"}
            showIcon
            className={styles.sectionAlert}
            message={selectedCandidate?.evidenceText ?? "确认候选前请核对来源证据。"}
          />
          <Form.Item
            name="highRiskAcknowledged"
            valuePropName="checked"
            rules={[{ required: true, message: "请先逐条核对高危近似风险" }]}
          >
            <Checkbox>已逐条核对高危近似风险</Checkbox>
          </Form.Item>
          <Form.Item
            name="highRiskReason"
            label="高危确认理由"
            rules={[{ required: true, message: "请填写高危确认理由" }]}
          >
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="构建术语映射包"
        open={buildOpen}
        onCancel={() => setBuildOpen(false)}
        onOk={() => void submitBuildPackage()}
        okText="创建草稿"
        destroyOnClose
      >
        <Form form={buildForm} layout="vertical" preserve={false}>
          <Form.Item
            name="packageCode"
            label="包编码"
            rules={[{ required: true, whitespace: true, message: "请输入包编码" }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="packageVersion"
            label="新版本"
            rules={[{ required: true, whitespace: true, message: "请输入新版本" }]}
          >
            <Input maxLength={64} placeholder="例如 2026.06.1" />
          </Form.Item>
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, whitespace: true, message: "请输入名称" }]}
          >
            <Input maxLength={256} />
          </Form.Item>
          <Form.Item name="scopeKey" label="生效范围" rules={[{ required: true }]}>
            <Select options={scopeOptions} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="发布映射包流程"
        open={publishOpen}
        onCancel={() => setPublishOpen(false)}
        onOk={() => void submitPublish()}
        okText="提交发布"
        destroyOnClose
      >
        <Form
          form={publishForm}
          layout="vertical"
          preserve={false}
          initialValues={{ releaseMode: "GRAY" }}
        >
          <Form.Item name="releaseMode" label="发布模式" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value="GRAY">10% 灰度</Radio>
              <Radio value="FULL" disabled={!hasOrganizationAdminRole(security.data?.roles)}>
                全量
              </Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            name="targetOrgUnitId"
            label="目标组织"
            rules={[{ required: true, whitespace: true, message: "请输入目标组织" }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="adapterIds"
            label="同步通道"
            rules={[{ required: true, type: "array", min: 1, message: "请选择至少一个可用通道" }]}
          >
            <Select
              mode="multiple"
              options={usableReleaseAdapters.map((adapter) => ({
                value: adapter.adapterId,
                label: adapter.adapterName,
              }))}
            />
          </Form.Item>
          <Form.Item name="reason" label="发布原因" rules={[{ required: true }]}>
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="回滚映射包流程"
        open={rollbackOpen}
        onCancel={() => setRollbackOpen(false)}
        onOk={() => void submitRollback()}
        okText="提交回滚"
        destroyOnClose
      >
        <Form form={rollbackForm} layout="vertical" preserve={false}>
          <Form.Item
            name="targetPackageId"
            label="回滚目标"
            rules={[{ required: true, message: "请选择历史发布版本" }]}
          >
            <Select
              options={rollbackCandidates.map((item) => ({
                value: item.packageId,
                label: `${item.packageVersion} · ${item.name}`,
              }))}
              onChange={(packageId) => {
                const target = rollbackCandidates.find((item) => item.packageId === packageId);
                rollbackForm.setFieldValue("confirmedTargetVersion", target?.packageVersion);
              }}
            />
          </Form.Item>
          <Form.Item name="confirmedTargetVersion" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="reason" label="回滚原因" rules={[{ required: true }]}>
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
          <Form.Item
            name="confirmedHighRisk"
            valuePropName="checked"
            rules={[
              {
                validator: (_, value) =>
                  value ? Promise.resolve() : Promise.reject(new Error("请确认高危回滚影响")),
              },
            ]}
          >
            <Checkbox>已确认回滚会切换当前生效术语版本</Checkbox>
          </Form.Item>
        </Form>
      </Modal>
    </PageExperienceShell>
  );
}
