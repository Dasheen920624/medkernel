import { useState } from "react";
import {
  Alert,
  App as AntdApp,
  Badge,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Radio,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
  Upload,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  CheckCircleOutlined,
  CloudSyncOutlined,
  DeleteOutlined,
  DownloadOutlined,
  ExperimentOutlined,
  FileProtectOutlined,
  HistoryOutlined,
  KeyOutlined,
  PlusOutlined,
  SearchOutlined,
  UploadOutlined,
  WarningOutlined,
} from "@ant-design/icons";

import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import {
  downloadPackageDiffExport,
  downloadPackageOfflineExport,
  downloadPackageSyncEvidenceExport,
  useAddPackageItem,
  useApplyPilotTemplateReferences,
  useAuthoringAssets,
  useCalculateDiff,
  useCreatePackage,
  useEvaluationIndicators,
  useImportOfflinePackage,
  usePackageAssetReadiness,
  usePackageDetail,
  usePackageInheritanceImpact,
  usePackageEntitlements,
  useOrgUnits,
  usePackages,
  usePackageSyncLogs,
  usePilotPackageTemplates,
  useReleasePackage,
  useGrantPackageEntitlement,
  useRevokePackageEntitlement,
  useRollbackPackage,
  useSecurityProfile,
  usePackageReleaseAdapters,
  useTenants,
} from "@/shared/api/hooks";
import { platformTenantId } from "@/shared/config/tenantDictionary";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import type {
  AuthoringAssetLibraryItem,
  EngineAssetType,
  EvaluationIndicator,
  KnowledgePackage,
  PackageEntitlement,
  PackageItem,
  PackageInheritanceImpactQuery,
  PackageInheritanceImpactTarget,
  PilotPackageInitialOverrideRequest,
  OrgUnit,
  SyncLogResponse,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import { StepFlow } from "@/shared/ui/StepFlow";
import type { StepKey } from "@/shared/ui/StepFlow.contract";
import styles from "./ConfigPackages.module.css";

const { TextArea } = Input;
const { Option } = Select;
const { Text } = Typography;

type PackageStatusFilter = "DRAFT" | "PUBLISHED" | "ACTIVE" | "OFFLINE";
type InheritancePerspective = "PLATFORM" | "TENANT" | "ORG";
type BadgeStatus = "success" | "processing" | "default" | "error" | "warning";
type OfflineImportSummary = {
  filename: string;
  packageCode: string;
  packageVersion: string;
  itemCount: number;
};

const statusText: Record<string, string> = {
  DRAFT: "草案",
  PUBLISHED: "已发布",
  ACTIVE: "生效中",
  OFFLINE: "已下线",
};

const statusBadge: Record<string, BadgeStatus> = {
  DRAFT: "default",
  PUBLISHED: "processing",
  ACTIVE: "success",
  OFFLINE: "error",
};

const entitlementStatusText: Record<string, string> = {
  ACTIVE: "有效",
  EXPIRED: "已到期",
  REVOKED: "已撤销",
};

const entitlementStatusColor: Record<string, string> = {
  ACTIVE: "green",
  EXPIRED: "orange",
  REVOKED: "default",
};

const packageStatusOptions: Array<{ value: PackageStatusFilter; label: string }> = [
  { value: "DRAFT", label: "草案" },
  { value: "PUBLISHED", label: "已发布" },
  { value: "ACTIVE", label: "生效中" },
  { value: "OFFLINE", label: "已下线" },
];

const orgLevelLabel: Record<OrgUnit["level"], string> = {
  PLATFORM: "平台",
  TENANT: "服务空间",
  REGION: "区域/联合体",
  FACILITY: "医疗机构",
  CAMPUS: "院区",
  DEPARTMENT: "科室",
  WARD: "病区/护理单元",
};

const facilityTypeLabel: Record<NonNullable<OrgUnit["facilityType"]>, string> = {
  HOSPITAL: "医院",
  SPECIALTY_HOSPITAL: "专科医院",
  BRANCH_HOSPITAL: "分院",
  COMMUNITY_HEALTH_CENTER: "社区卫生服务中心",
  TOWNSHIP_CLINIC: "乡镇卫生院",
  VILLAGE_CLINIC: "村卫生室",
  OUTPATIENT_CLINIC: "门诊部",
  STATION: "卫生服务站",
  OTHER: "其他医疗机构",
};

function orgUnitTypeLabel(unit: OrgUnit) {
  return unit.facilityType ? facilityTypeLabel[unit.facilityType] : orgLevelLabel[unit.level];
}

const inheritancePerspectiveOptions: Array<{ value: InheritancePerspective; label: string }> = [
  { value: "PLATFORM", label: "平台视角" },
  { value: "TENANT", label: "服务机构视角" },
  { value: "ORG", label: "当前机构" },
];

const inheritanceAssetTypeOptions: Array<{ value: EngineAssetType; label: string }> = [
  { value: "RULE", label: "规则引擎 (RULE)" },
  { value: "PATHWAY", label: "临床路径 (PATHWAY)" },
  { value: "CONDITION_FRAGMENT", label: "条件片段 (CONDITION_FRAGMENT)" },
  { value: "EVALUATION", label: "质控评估指标 (EVALUATION)" },
  { value: "TERMINOLOGY", label: "术语字典映射 (TERMINOLOGY)" },
  { value: "KNOWLEDGE", label: "知识资产 (KNOWLEDGE)" },
];

const authoringAssetTypes = ["RULE", "PATHWAY", "CONDITION_FRAGMENT"] as const;
type AuthoringPackageAssetType = (typeof authoringAssetTypes)[number];

function isAuthoringPackageAssetType(value: string): value is AuthoringPackageAssetType {
  return authoringAssetTypes.includes(value as AuthoringPackageAssetType);
}

function hasOrganizationAdminRole(roles: Array<{ code?: string }> | undefined) {
  return (roles ?? []).some((role) => {
    const normalized = (role.code ?? "").trim().toUpperCase().replace(/[-.]/g, "_");
    return (
      normalized === "ORGANIZATION_ADMIN" ||
      normalized === "ROLE_ORGANIZATION_ADMIN" ||
      normalized === "PLATFORM_GOVERNANCE_ADMIN" ||
      normalized === "ROLE_PLATFORM_GOVERNANCE_ADMIN"
    );
  });
}

function hasPermission(profile: ReturnType<typeof useSecurityProfile>["data"], code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

function assetTypeColor(type: string) {
  const colors: Record<string, string> = {
    KNOWLEDGE: "green",
    TERMINOLOGY: "orange",
    RULE: "blue",
    PATHWAY: "purple",
    EVALUATION: "cyan",
    CONDITION_FRAGMENT: "geekblue",
    VALUE_SET: "lime",
    ORDER_SET: "volcano",
    ACTION_CARD: "gold",
    SUBPATHWAY: "magenta",
    FOLLOWUP: "magenta",
  };
  return colors[type] || "default";
}

function safeFilename(value: string) {
  return value.replace(/[^\w.-]/g, "_");
}

function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () =>
      typeof reader.result === "string"
        ? resolve(reader.result)
        : reject(new Error("文件内容不是文本"));
    reader.onerror = () => reject(reader.error ?? new Error("文件读取失败"));
    reader.readAsText(file, "UTF-8");
  });
}

function parseOfflineImportSummary(filename: string, content: string): OfflineImportSummary {
  const root = JSON.parse(content) as {
    format?: unknown;
    manifest?: {
      packageCode?: unknown;
      packageVersion?: unknown;
      itemCount?: unknown;
    };
  };
  const packageCode = root.manifest?.packageCode;
  const packageVersion = root.manifest?.packageVersion;
  const itemCount = root.manifest?.itemCount;
  if (root.format !== "MEDKERNEL_PACKAGE_OFFLINE_V2") {
    throw new Error("离线包格式不受支持");
  }
  if (
    typeof packageCode !== "string" ||
    !packageCode.trim() ||
    typeof packageVersion !== "string" ||
    !packageVersion.trim() ||
    typeof itemCount !== "number" ||
    !Number.isInteger(itemCount) ||
    itemCount < 0
  ) {
    throw new Error("离线包摘要字段不完整");
  }
  return { filename, packageCode, packageVersion, itemCount };
}

function currentStepFor(
  selectedPackage: KnowledgePackage | undefined,
  currentItems: PackageItem[],
  visibleSyncLogs: SyncLogResponse[],
): StepKey {
  if (!selectedPackage) return "select_template";
  if (visibleSyncLogs.length > 0) return "evidence_rollback";
  if (selectedPackage.status === "DRAFT") {
    return currentItems.length > 0 ? "impact_preview" : "auto_validate";
  }
  if (selectedPackage.status === "PUBLISHED") return "canary_release";
  if (selectedPackage.status === "ACTIVE") return "full_rollout";
  if (selectedPackage.status === "OFFLINE") return "evidence_rollback";
  return "select_template";
}

function syncLogStatusColor(status: string) {
  if (status === "SUCCESS") return "green";
  if (status === "NOT_SYNCED") return "orange";
  if (status === "RUNNING") return "blue";
  return "red";
}

function syncLogStatusText(status: string) {
  if (status === "NOT_SYNCED") return "未接入";
  if (status === "SUCCESS") return "成功";
  if (status === "FAILED") return "失败";
  if (status === "RUNNING") return "执行中";
  return status;
}

function sourceTierText(sourceTier: string | null | undefined) {
  if (sourceTier === "PLATFORM") return "平台基线";
  if (sourceTier === "TENANT") return "服务机构定制";
  if (sourceTier === "ORG") return "本级定制";
  if (sourceTier === "PARENT_ORG") return "继承上级定制";
  if (sourceTier === "DISABLED") return "已停用";
  return "未解析";
}

function sourceTierColor(sourceTier: string | null | undefined) {
  if (sourceTier === "PLATFORM") return "green";
  if (sourceTier === "TENANT") return "cyan";
  if (sourceTier === "ORG") return "blue";
  if (sourceTier === "PARENT_ORG") return "geekblue";
  if (sourceTier === "DISABLED") return "red";
  return "default";
}

function impactTypeText(impactType: string) {
  if (impactType === "AUTO_INHERITS_UPSTREAM") return "自动继承上游";
  if (impactType === "REBASE_RECOMMENDED") return "建议 rebase";
  if (impactType === "DISABLE_REVIEW_RECOMMENDED") return "建议停用复核";
  if (impactType === "UNAFFECTED") return "不受影响";
  return impactType;
}

function impactTypeColor(impactType: string) {
  if (impactType === "AUTO_INHERITS_UPSTREAM") return "green";
  if (impactType === "REBASE_RECOMMENDED") return "orange";
  if (impactType === "DISABLE_REVIEW_RECOMMENDED") return "red";
  if (impactType === "UNAFFECTED") return "default";
  return "blue";
}

function overrideModeText(mode: string) {
  if (mode === "REPLACE") return "替换继承 (REPLACE)";
  if (mode === "DISABLE") return "本院不用 (DISABLE)";
  if (mode === "ADD") return "新增本院专有 (ADD)";
  return mode;
}

function propagationText(propagation: string) {
  if (propagation === "INHERITABLE") return "复用，下级继承 (INHERITABLE)";
  if (propagation === "EXCLUSIVE") return "独有，仅本组织 (EXCLUSIVE)";
  return propagation;
}

function targetMatchesPerspective(
  target: PackageInheritanceImpactTarget,
  perspective: InheritancePerspective,
) {
  if (perspective === "PLATFORM") {
    return target.sourceTier === "PLATFORM";
  }
  if (perspective === "TENANT") {
    return target.sourceTier === "TENANT" || target.sourceTier === "PARENT_ORG";
  }
  return target.sourceTier === "ORG" || target.impactType === "REBASE_RECOMMENDED";
}

function blankToUndefined(value: unknown) {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

export default function ConfigPackages() {
  const { message } = AntdApp.useApp();
  const [currentPage, setCurrentPage] = useState(1);
  const [keywordInput, setKeywordInput] = useState("");
  const [statusInput, setStatusInput] = useState<PackageStatusFilter | undefined>(undefined);
  const [filters, setFilters] = useState<{
    keyword?: string;
    status?: PackageStatusFilter;
  }>({});

  const { data: securityProfile } = useSecurityProfile();
  const packageQuery = usePackages({
    page: currentPage - 1,
    size: 10,
    keyword: filters.keyword,
    status: filters.status,
  });
  const { data: releaseAdapters } = usePackageReleaseAdapters();
  const { data: pilotTemplates = [], isLoading: pilotTemplatesLoading = false } =
    usePilotPackageTemplates();
  const {
    data: assetReadiness,
    refetch: refetchAssetReadiness,
    isLoading: readinessLoading = false,
    isError: readinessError = false,
  } = usePackageAssetReadiness();

  const apiPackagesData = packageQuery.data;
  const apiPackages = apiPackagesData?.items ?? [];
  const totalPackagesCount = apiPackagesData?.total ?? 0;
  const displayAdapters = releaseAdapters ?? [];
  const usableReleaseAdapters = displayAdapters.filter(
    (adapter) =>
      adapter.status === "ACTIVE" &&
      adapter.healthStatus === "HEALTHY" &&
      adapter.connectorAvailable,
  );
  const canDirectFullRelease = hasOrganizationAdminRole(securityProfile?.roles);
  const canManageEntitlements =
    securityProfile?.dataScope.tenantId === platformTenantId &&
    hasPermission(securityProfile, "platform.publish");
  const tenantDirectoryQuery = useTenants(canManageEntitlements);
  const activeCustomerTenants = (tenantDirectoryQuery.data ?? []).filter(
    (tenant) => tenant.tenantId !== platformTenantId && tenant.status === "ACTIVE",
  );
  const tenantNameById = new Map(
    (tenantDirectoryQuery.data ?? []).map((tenant) => [tenant.tenantId, tenant.name]),
  );
  const canReadAuthoringAssets =
    hasPermission(securityProfile, "rule.read") || hasPermission(securityProfile, "pathway.read");
  const canReadEvaluations = hasPermission(securityProfile, "evaluation.read");

  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [selectedPackageId, setSelectedPackageId] = useState<string | null>(null);
  const [diffModalVisible, setDiffModalVisible] = useState(false);
  const [basePackageIdForDiff, setBasePackageIdForDiff] = useState<string | undefined>(undefined);
  const [syncModalVisible, setSyncModalVisible] = useState(false);
  const [rollbackModalVisible, setRollbackModalVisible] = useState(false);
  const [offlineImportModalVisible, setOfflineImportModalVisible] = useState(false);
  const [offlineExportModalVisible, setOfflineExportModalVisible] = useState(false);
  const [offlineExportPackage, setOfflineExportPackage] = useState<KnowledgePackage | null>(null);
  const [pilotTemplateModalVisible, setPilotTemplateModalVisible] = useState(false);
  const [entitlementPackage, setEntitlementPackage] = useState<KnowledgePackage | null>(null);
  const [revokingEntitlement, setRevokingEntitlement] = useState<PackageEntitlement | null>(null);
  const [entitlementPage, setEntitlementPage] = useState(1);
  const [selectedPilotTemplateCode, setSelectedPilotTemplateCode] = useState<string | undefined>();
  const [offlineImportContent, setOfflineImportContent] = useState("");
  const [offlineImportSummary, setOfflineImportSummary] = useState<OfflineImportSummary | null>(
    null,
  );
  const [inheritancePerspective, setInheritancePerspective] =
    useState<InheritancePerspective>("ORG");
  const [inheritanceImpactQuery, setInheritanceImpactQuery] =
    useState<PackageInheritanceImpactQuery | null>(null);
  const [initialOverrides, setInitialOverrides] = useState<PilotPackageInitialOverrideRequest[]>(
    [],
  );
  const [overrideModeInput, setOverrideModeInput] = useState<string>("REPLACE");
  const [selectedAssetType, setSelectedAssetType] = useState<EngineAssetType>("RULE");
  const [rollbackReason, setRollbackReason] = useState("");
  const [rollbackConfirmed, setRollbackConfirmed] = useState(false);
  const [syncProgress, setSyncProgress] = useState(0);
  const [syncLogs, setSyncLogs] = useState<SyncLogResponse[]>([]);
  const [syncExecuting, setSyncExecuting] = useState(false);
  const [diffExporting, setDiffExporting] = useState(false);
  const [offlineExportingId, setOfflineExportingId] = useState<string | null>(null);
  const [syncEvidenceExporting, setSyncEvidenceExporting] = useState(false);

  const [createForm] = Form.useForm();
  const [itemForm] = Form.useForm();
  const [syncForm] = Form.useForm();
  const [offlineExportForm] = Form.useForm<{ targetOrgUnitId: string }>();
  const [pilotTemplateForm] = Form.useForm();
  const [entitlementForm] = Form.useForm<{
    targetTenantId: string;
    expiresAt: string;
    reason: string;
  }>();
  const [revokeEntitlementForm] = Form.useForm<{ reason: string }>();
  const [inheritanceImpactForm] = Form.useForm<PackageInheritanceImpactQuery>();
  const [initialOverrideForm] = Form.useForm<PilotPackageInitialOverrideRequest>();

  const effectivePackageId = selectedPackageId ?? apiPackages[0]?.packageId ?? null;
  const selectedPackage = apiPackages.find((p) => p.packageId === effectivePackageId);
  const selectedPilotTemplate =
    pilotTemplates.find((template) => template.templateCode === selectedPilotTemplateCode) ??
    pilotTemplates[0];

  const { data: apiDetail, refetch: refetchPackageDetail } = usePackageDetail(
    effectivePackageId || "",
  );
  const currentItems = apiDetail?.items ?? [];
  const { data: persistedSyncLogs } = usePackageSyncLogs(effectivePackageId || "");
  const entitlementQuery = usePackageEntitlements(
    entitlementPackage?.packageId ?? "",
    Boolean(entitlementPackage) && canManageEntitlements,
    entitlementPage,
  );
  const { data: apiDiffData } = useCalculateDiff(effectivePackageId || "", basePackageIdForDiff);
  const { data: inheritanceImpact, isFetching: inheritanceImpactFetching } =
    usePackageInheritanceImpact(inheritanceImpactQuery ?? {}, {
      enabled: Boolean(inheritanceImpactQuery),
    });

  const selectedAssetIsAuthoring = isAuthoringPackageAssetType(selectedAssetType);
  const { data: authoringAssets } = useAuthoringAssets(
    {
      assetType: selectedAssetIsAuthoring ? selectedAssetType : undefined,
      size: 100,
    },
    { enabled: canReadAuthoringAssets && selectedAssetIsAuthoring },
  );
  const { data: activeEvaluations } = useEvaluationIndicators(
    { size: 100 },
    { enabled: canReadEvaluations },
  );
  const { data: terminologyPackagesData } = usePackages({
    size: 100,
    assetType: "TERMINOLOGY",
  });
  const { data: orgUnitsData, isLoading: orgUnitsLoading } = useOrgUnits({
    page: 1,
    size: 100,
    sort: "level,asc",
  });
  const orgUnitOptions = (orgUnitsData?.items ?? [])
    .filter((unit) => unit.status === "ACTIVE" && Boolean(unit.id))
    .map((unit) => ({
      value: unit.id as string,
      label: `${unit.name} · ${orgUnitTypeLabel(unit)} · ${unit.code}`,
    }));

  const createPackageMutation = useCreatePackage();
  const addPackageItemMutation = useAddPackageItem();
  const releasePackageMutation = useReleasePackage();
  const rollbackPackageMutation = useRollbackPackage();
  const importOfflinePackageMutation = useImportOfflinePackage();
  const applyPilotTemplateReferencesMutation = useApplyPilotTemplateReferences();
  const grantPackageEntitlementMutation = useGrantPackageEntitlement();
  const revokePackageEntitlementMutation = useRevokePackageEntitlement();

  const activeCount = apiPackages.filter((p) => p.status === "ACTIVE").length;
  const publishedCount = apiPackages.filter((p) => p.status === "PUBLISHED").length;
  const draftCount = apiPackages.filter((p) => p.status === "DRAFT").length;
  const offlineCount = apiPackages.filter((p) => p.status === "OFFLINE").length;
  const readinessBlockers = assetReadiness?.blockers ?? [];
  const canApplyPilotTemplateReferences = pilotTemplates.length > 0 && !pilotTemplatesLoading;
  const visibleSyncLogs = syncLogs.length > 0 ? syncLogs : (persistedSyncLogs ?? []);
  const attentionSyncLogs = visibleSyncLogs.filter(
    (log) => log.status === "FAILED" || log.status === "NOT_SYNCED",
  );
  const rollbackActionDisabled = !rollbackReason.trim() || !rollbackConfirmed;
  const availableRollbackPackages = selectedPackage
    ? apiPackages.filter(
        (p) =>
          p.packageId !== selectedPackage.packageId &&
          p.status === "OFFLINE" &&
          p.packageCode === selectedPackage.packageCode,
      )
    : [];
  const currentStep = currentStepFor(selectedPackage, currentItems, visibleSyncLogs);
  let assetVersionPlaceholder = "输入资产快照版本";
  if (selectedAssetType === "TERMINOLOGY") {
    assetVersionPlaceholder = "选择术语包后自动带出版本";
  } else if (selectedAssetIsAuthoring || selectedAssetType === "EVALUATION") {
    assetVersionPlaceholder = "选择资产后自动带出版本";
  }

  const terminologyPackageOptions = (terminologyPackagesData?.items ?? []).filter(
    (item) =>
      Boolean(item.primaryAssetId) && (item.status === "PUBLISHED" || item.status === "ACTIVE"),
  );
  const terminologyAssetId = (item: KnowledgePackage) => item.primaryAssetId ?? "";
  const authoringAssetOptions = (authoringAssets?.items ?? []).filter(
    (item) => item.status === "PUBLISHED" || item.status === "ACTIVE",
  );
  const findSelectedAuthoringAsset = (assetId: string) =>
    authoringAssetOptions.find((item) => item.assetId === assetId);
  const releaseAdapterName = (adapterId: string) =>
    displayAdapters.find((adapter) => adapter.adapterId === adapterId)?.adapterName || adapterId;
  const defaultTargetOrgUnitId =
    securityProfile?.dataScope.hospitalId ??
    securityProfile?.dataScope.campusId ??
    securityProfile?.dataScope.siteId ??
    securityProfile?.dataScope.departmentId ??
    orgUnitOptions[0]?.value;

  const fillAssetVersion = (assetId: string | undefined) => {
    if (!assetId) {
      itemForm.resetFields(["assetVersion"]);
      return;
    }
    if (selectedAssetIsAuthoring) {
      const selected = findSelectedAuthoringAsset(assetId);
      itemForm.setFieldsValue({ assetVersion: selected?.version });
      return;
    }
    if (selectedAssetType === "EVALUATION") {
      const selected = (activeEvaluations?.items ?? []).find(
        (item: EvaluationIndicator) => item.indicatorId === assetId,
      );
      itemForm.setFieldsValue({ assetVersion: selected?.versionNo?.toString() });
      return;
    }
    if (selectedAssetType === "TERMINOLOGY") {
      const selected = terminologyPackageOptions.find(
        (item) => terminologyAssetId(item) === assetId,
      );
      itemForm.setFieldsValue({ assetVersion: selected?.packageVersion });
    }
  };

  const applyFilters = () => {
    setCurrentPage(1);
    setFilters({
      keyword: keywordInput.trim() || undefined,
      status: statusInput,
    });
  };

  const clearFilters = () => {
    setCurrentPage(1);
    setKeywordInput("");
    setStatusInput(undefined);
    setFilters({});
  };

  const openPilotTemplateModal = () => {
    const template = selectedPilotTemplate ?? pilotTemplates[0];
    if (!template) return;
    setSelectedPilotTemplateCode(template.templateCode);
    setInitialOverrides([]);
    setOverrideModeInput("REPLACE");
    pilotTemplateForm.setFieldsValue({
      templateCode: template.templateCode,
      targetOrgUnitId: defaultTargetOrgUnitId,
    });
    initialOverrideForm.setFieldsValue({
      asset_type: "RULE",
      target_org_unit_id: defaultTargetOrgUnitId,
      applicable_scope: "adult|inpatient",
      override_mode: "REPLACE",
      propagation: "INHERITABLE",
    });
    setPilotTemplateModalVisible(true);
  };

  const handlePilotTemplateChange = (templateCode: string) => {
    const template = pilotTemplates.find((item) => item.templateCode === templateCode);
    setSelectedPilotTemplateCode(templateCode);
    if (!template) return;
    pilotTemplateForm.setFieldsValue({
      targetOrgUnitId: pilotTemplateForm.getFieldValue("targetOrgUnitId") ?? defaultTargetOrgUnitId,
    });
  };

  const closePilotTemplateModal = () => {
    setPilotTemplateModalVisible(false);
    setSelectedPilotTemplateCode(undefined);
    setInitialOverrides([]);
    setOverrideModeInput("REPLACE");
    pilotTemplateForm.resetFields();
    initialOverrideForm.resetFields();
  };

  const handleQueryInheritanceImpact = async () => {
    const values = await inheritanceImpactForm.validateFields();
    setInheritanceImpactQuery({
      assetType: values.assetType,
      assetIdentity: values.assetIdentity,
      applicableScope: values.applicableScope,
      upstreamVersionId: values.upstreamVersionId,
    });
  };

  const handleAddInitialOverride = async () => {
    const values = await initialOverrideForm.validateFields();
    const mode = values.override_mode;
    const targetOrgUnitId =
      values.target_org_unit_id ?? pilotTemplateForm.getFieldValue("targetOrgUnitId");
    const nextOverride: PilotPackageInitialOverrideRequest = {
      asset_type: values.asset_type,
      asset_identity: String(blankToUndefined(values.asset_identity)),
      inherited_version_id:
        mode === "ADD" ? undefined : (blankToUndefined(values.inherited_version_id) as string),
      override_version_id:
        mode === "DISABLE" ? undefined : (blankToUndefined(values.override_version_id) as string),
      target_org_unit_id: targetOrgUnitId,
      applicable_scope: blankToUndefined(values.applicable_scope) as string | undefined,
      override_mode: mode,
      propagation: values.propagation,
      diff_summary: blankToUndefined(values.diff_summary) as string | undefined,
      override_reason: blankToUndefined(values.override_reason) as string | undefined,
      impact_scope: blankToUndefined(values.impact_scope) as string | undefined,
    };
    setInitialOverrides((current) => [...current, nextOverride]);
    initialOverrideForm.resetFields([
      "asset_identity",
      "inherited_version_id",
      "override_version_id",
      "diff_summary",
      "override_reason",
      "impact_scope",
    ]);
    initialOverrideForm.setFieldsValue({
      asset_type: "RULE",
      target_org_unit_id: targetOrgUnitId,
      applicable_scope: "adult|inpatient",
      override_mode: "REPLACE",
      propagation: "INHERITABLE",
    });
    setOverrideModeInput("REPLACE");
  };

  const removeInitialOverride = (index: number) => {
    setInitialOverrides((current) => current.filter((_, itemIndex) => itemIndex !== index));
  };

  const openSyncModal = (packageId?: string) => {
    const nextPackageId = packageId ?? effectivePackageId;
    if (!nextPackageId) {
      message.warning("请先创建或选择配置包。");
      return;
    }
    setSelectedPackageId(nextPackageId);
    setSyncLogs([]);
    setSyncProgress(0);
    syncForm.setFieldsValue({ strategy: "GRAYSCALE" });
    setSyncModalVisible(true);
  };

  const closeSyncModal = () => {
    setSyncModalVisible(false);
    setSyncLogs([]);
    setSyncProgress(0);
    syncForm.resetFields();
  };

  const closeRollbackModal = () => {
    setRollbackModalVisible(false);
    setRollbackReason("");
    setRollbackConfirmed(false);
  };

  const handleCreatePackage = async () => {
    try {
      const values = await createForm.validateFields();
      const res = await createPackageMutation.mutateAsync({
        packageCode: values.packageCode,
        packageVersion: values.packageVersion,
        name: values.name,
        description: values.description,
        accessPolicy: canManageEntitlements ? values.accessPolicy : "OPEN",
      });

      message.success(`配置包草案已创建：${res?.packageCode}`);
      setCreateModalVisible(false);
      createForm.resetFields();
      void packageQuery.refetch();
    } catch (err: unknown) {
      if (applyApiFieldErrors(createForm, err)) return;
      message.error(getApiErrorMessage(err, "配置包草案创建失败"));
    }
  };

  const openEntitlementModal = (record: KnowledgePackage) => {
    setEntitlementPage(1);
    setEntitlementPackage(record);
    entitlementForm.resetFields();
  };

  const closeEntitlementModal = () => {
    setEntitlementPackage(null);
    setRevokingEntitlement(null);
    setEntitlementPage(1);
    entitlementForm.resetFields();
    revokeEntitlementForm.resetFields();
  };

  const handleGrantPackageEntitlement = async () => {
    if (!entitlementPackage) return;
    try {
      const values = await entitlementForm.validateFields();
      await grantPackageEntitlementMutation.mutateAsync({
        packageId: entitlementPackage.packageId,
        packageVersion: entitlementPackage.packageVersion,
        request: {
          targetTenantId: values.targetTenantId.trim(),
          expiresAt: new Date(values.expiresAt).toISOString(),
          reason: values.reason.trim(),
        },
      });
      message.success("服务空间授权已开通或续期");
      entitlementForm.resetFields();
    } catch (err: unknown) {
      if (Array.isArray((err as { errorFields?: unknown[] }).errorFields)) return;
      if (applyApiFieldErrors(entitlementForm, err)) return;
      message.error(getApiErrorMessage(err, "服务空间授权操作失败"));
    }
  };

  const openRevokeEntitlementModal = (record: PackageEntitlement) => {
    setRevokingEntitlement(record);
    revokeEntitlementForm.resetFields();
  };

  const closeRevokeEntitlementModal = () => {
    setRevokingEntitlement(null);
    revokeEntitlementForm.resetFields();
  };

  const handleRevokePackageEntitlement = async (values: { reason: string }) => {
    if (!entitlementPackage || !revokingEntitlement) return;
    try {
      await revokePackageEntitlementMutation.mutateAsync({
        packageId: entitlementPackage.packageId,
        packageVersion: entitlementPackage.packageVersion,
        tenantId: revokingEntitlement.tenantId,
        reason: values.reason.trim(),
      });
      message.success(`已撤销服务空间 ${revokingEntitlement.tenantId} 的包授权`);
      closeRevokeEntitlementModal();
    } catch (err: unknown) {
      if (Array.isArray((err as { errorFields?: unknown[] }).errorFields)) return;
      if (applyApiFieldErrors(revokeEntitlementForm, err)) return;
      message.error(getApiErrorMessage(err, "撤销服务空间授权失败"));
    }
  };

  const handleApplyPilotTemplateReferences = async () => {
    try {
      const values = await pilotTemplateForm.validateFields();
      const template = pilotTemplates.find((item) => item.templateCode === values.templateCode);
      const applied = await applyPilotTemplateReferencesMutation.mutateAsync({
        templateCode: values.templateCode,
        packageVersion: template?.defaultPackageVersion ?? "ONBOARDING",
        request: {
          target_org_unit_id: values.targetOrgUnitId,
          initial_overrides: initialOverrides,
        },
      });
      message.success(`首发平台包引用已应用：${applied.references.length} 个`);
      closePilotTemplateModal();
      void refetchAssetReadiness();
    } catch (err: unknown) {
      if (applyApiFieldErrors(pilotTemplateForm, err)) return;
      message.error(getApiErrorMessage(err, "首发平台包引用失败，请核对模板和组织"));
    }
  };

  const handleAddItem = async () => {
    if (!effectivePackageId) return;
    try {
      const values = await itemForm.validateFields();
      const assetType = values.assetType as EngineAssetType;
      await addPackageItemMutation.mutateAsync({
        packageId: effectivePackageId,
        request: {
          assetType,
          assetId: values.assetId,
          assetVersion: values.assetVersion,
          packageVersion: selectedPackage?.packageVersion || values.assetVersion,
        },
      });

      message.success("资产条目已加入草案");
      itemForm.resetFields(["assetId", "assetVersion"]);
      void refetchPackageDetail();
      void packageQuery.refetch();
    } catch (err: unknown) {
      if (applyApiFieldErrors(itemForm, err)) return;
      message.error(getApiErrorMessage(err, "资产条目添加失败"));
    }
  };

  const handleSyncPackage = async () => {
    if (!effectivePackageId) return;
    try {
      const values = await syncForm.validateFields();
      const strategy = values.strategy || "GRAYSCALE";
      if (strategy === "FULL" && !canDirectFullRelease) {
        message.error("只有院级管理员可直接全量发布，请先走默认 10% 灰度。");
        return;
      }
      setSyncExecuting(true);
      setSyncProgress(20);

      const res = await releasePackageMutation.mutateAsync({
        packageId: effectivePackageId,
        request: {
          targetOrgUnitId: values.targetOrgUnitId,
          reason: values.reason,
          strategy,
          scopeType: strategy === "GRAYSCALE" ? values.scopeType || "ALL" : "ALL",
          scopeValue: strategy === "GRAYSCALE" ? values.scopeValue || "" : "",
          adapterIds: values.adapterIds,
          packageVersion: selectedPackage?.packageVersion || "",
        },
      });

      setSyncProgress(100);
      setSyncLogs(res?.logs || []);
      const hasNotSynced =
        res?.status === "NOT_SYNCED" ||
        (res?.logs || []).some((log) => log.status === "NOT_SYNCED");
      message[hasNotSynced ? "warning" : "success"](
        hasNotSynced ? "发布计划已记录，存在未连通适配器。" : "院内同步发布完成。",
      );
      void packageQuery.refetch();
    } catch (err: unknown) {
      setSyncProgress(0);
      setSyncLogs([]);
      if (applyApiFieldErrors(syncForm, err)) return;
      message.error(getApiErrorMessage(err, "同步发布失败，未生成同步证据"));
    } finally {
      setSyncExecuting(false);
    }
  };

  const handleExportDiffEvidence = async () => {
    if (!effectivePackageId || !apiDiffData) return;
    setDiffExporting(true);
    try {
      const blob = await downloadPackageDiffExport(effectivePackageId, basePackageIdForDiff);
      triggerBlobDownload(
        blob,
        `package-diff-${safeFilename(selectedPackage?.packageCode || effectivePackageId)}.jsonl`,
      );
      message.success("影响范围证据已开始下载。");
    } catch (err: unknown) {
      message.error(getApiErrorMessage(err, "影响范围证据导出失败"));
    } finally {
      setDiffExporting(false);
    }
  };

  const openOfflineExportModal = (record: KnowledgePackage) => {
    setOfflineExportPackage(record);
    offlineExportForm.resetFields();
    setOfflineExportModalVisible(true);
  };

  const closeOfflineExportModal = () => {
    setOfflineExportModalVisible(false);
    setOfflineExportPackage(null);
    offlineExportForm.resetFields();
  };

  const handleExportOfflinePackage = async () => {
    if (!offlineExportPackage) return;
    try {
      const values = await offlineExportForm.validateFields();
      const targetOrgUnitId = values.targetOrgUnitId.trim();
      setOfflineExportingId(offlineExportPackage.packageId);
      const blob = await downloadPackageOfflineExport(
        offlineExportPackage.packageId,
        targetOrgUnitId,
      );
      triggerBlobDownload(
        blob,
        `package-offline-${safeFilename(offlineExportPackage.packageCode)}-${safeFilename(targetOrgUnitId)}.json`,
      );
      message.success("离线包有效快照已开始下载。");
      closeOfflineExportModal();
    } catch (err: unknown) {
      if (Array.isArray((err as { errorFields?: unknown[] }).errorFields)) return;
      if (applyApiFieldErrors(offlineExportForm, err)) return;
      message.error(getApiErrorMessage(err, "离线包导出失败"));
    } finally {
      setOfflineExportingId(null);
    }
  };

  const handleExportSyncEvidence = async () => {
    if (!effectivePackageId) return;
    setSyncEvidenceExporting(true);
    try {
      const blob = await downloadPackageSyncEvidenceExport(effectivePackageId);
      triggerBlobDownload(
        blob,
        `package-sync-evidence-${safeFilename(selectedPackage?.packageCode || effectivePackageId)}.jsonl`,
      );
      message.success("同步证据已开始下载。");
    } catch (err: unknown) {
      message.error(getApiErrorMessage(err, "同步证据导出失败"));
    } finally {
      setSyncEvidenceExporting(false);
    }
  };

  const closeOfflineImportModal = () => {
    setOfflineImportModalVisible(false);
    setOfflineImportContent("");
    setOfflineImportSummary(null);
  };

  const handleOfflineImportFile = (file: File) => {
    if (file.size > 10 * 1024 * 1024) {
      message.error("离线包文件不能超过 10 MB。");
      return false;
    }
    readFileAsText(file)
      .then((content) => {
        setOfflineImportContent(content);
        setOfflineImportSummary(parseOfflineImportSummary(file.name, content));
      })
      .catch(() => {
        setOfflineImportContent("");
        setOfflineImportSummary(null);
        message.error("离线包 JSON 文件无法识别，请重新选择有效文件。");
      });
    return false;
  };

  const handleImportOfflinePackage = async () => {
    const offlinePackageJson = offlineImportContent.trim();
    if (!offlinePackageJson) {
      message.error("请先选择离线包 JSON 文件。");
      return;
    }
    try {
      const imported = await importOfflinePackageMutation.mutateAsync(offlinePackageJson);
      message.success(
        `离线包已导入为草案：${imported.packageCode} / ${imported.packageVersion}，共 ${imported.itemCount} 个资产条目。`,
      );
      closeOfflineImportModal();
      void packageQuery.refetch();
    } catch (err: unknown) {
      message.error(getApiErrorMessage(err, "离线包导入失败，未通过完整性校验或发布门禁"));
    }
  };

  const handleRollback = async (targetPackage: KnowledgePackage) => {
    if (!effectivePackageId || !selectedPackage) return;
    const reason = rollbackReason.trim();
    if (!reason || !rollbackConfirmed) {
      message.error("请填写回滚原因，并完成高危影响确认。");
      return;
    }
    try {
      await rollbackPackageMutation.mutateAsync({
        packageId: effectivePackageId,
        request: {
          targetPackageId: targetPackage.packageId,
          confirmedCurrentVersion: selectedPackage.packageVersion,
          confirmedTargetVersion: targetPackage.packageVersion,
          reason,
          confirmedHighRisk: rollbackConfirmed,
          packageVersion: selectedPackage.packageVersion,
        },
      });
      message.success("版本回滚成功，目标版本已启用，当前版本已下线。");
      closeRollbackModal();
      void packageQuery.refetch();
    } catch (err: unknown) {
      message.error(getApiErrorMessage(err, "版本回滚失败，状态未在前端伪造切换"));
    }
  };

  const inheritanceImpactTargets = inheritanceImpact?.targets ?? [];
  const visibleInheritanceImpactTargets = inheritanceImpactTargets.filter((target) =>
    targetMatchesPerspective(target, inheritancePerspective),
  );
  const visibleAutoInheritedCount = visibleInheritanceImpactTargets.filter(
    (target) => target.impactType === "AUTO_INHERITS_UPSTREAM",
  ).length;
  const visibleRebaseRequiredCount = visibleInheritanceImpactTargets.filter(
    (target) => target.impactType === "REBASE_RECOMMENDED",
  ).length;
  const inheritanceImpactColumns: ColumnsType<PackageInheritanceImpactTarget> = [
    {
      title: "组织",
      key: "org",
      width: 180,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.orgUnitId}</Text>
          <Text type="secondary" className={styles.nowrap}>
            {record.orgPath}
          </Text>
        </Space>
      ),
    },
    {
      title: "来源",
      dataIndex: "sourceTier",
      key: "sourceTier",
      width: 120,
      render: (sourceTier: string | null) => (
        <Tag color={sourceTierColor(sourceTier)}>{sourceTierText(sourceTier)}</Tag>
      ),
    },
    {
      title: "影响",
      dataIndex: "impactType",
      key: "impactType",
      width: 140,
      render: (impactType: string) => (
        <Tag color={impactTypeColor(impactType)}>{impactTypeText(impactType)}</Tag>
      ),
    },
    {
      title: "生效版本",
      key: "effectiveVersion",
      width: 160,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.effectiveVersionNo ?? "已停用"}</Text>
          {record.effectiveVersionId && (
            <Text type="secondary" className={styles.codeText}>
              {record.effectiveVersionId}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: "提示",
      key: "prompt",
      render: (_: unknown, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.rebasePrompt ?? "无额外动作"}</Text>
          {record.diffSummary && <Text type="secondary">{record.diffSummary}</Text>}
        </Space>
      ),
    },
  ];

  const initialOverrideColumns: ColumnsType<PilotPackageInitialOverrideRequest> = [
    {
      title: "资产",
      key: "asset",
      render: (_: unknown, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.asset_identity}</Text>
          <Tag color={assetTypeColor(record.asset_type)}>
            {customerEnumLabel(record.asset_type)}
          </Tag>
        </Space>
      ),
    },
    {
      title: "方式",
      dataIndex: "override_mode",
      key: "override_mode",
      width: 150,
      render: (mode: string) => (
        <Tag color={mode === "DISABLE" ? "red" : "blue"}>{customerEnumLabel(mode)}</Tag>
      ),
    },
    {
      title: "传播",
      dataIndex: "propagation",
      key: "propagation",
      width: 160,
      render: (propagation: string) => (
        <Tag color={propagation === "EXCLUSIVE" ? "orange" : "green"}>
          {customerEnumLabel(propagation)}
        </Tag>
      ),
    },
    {
      title: "版本",
      key: "versions",
      render: (_: unknown, record) => (
        <Space direction="vertical" size={0}>
          {record.inherited_version_id && <Text>继承 {record.inherited_version_id}</Text>}
          {record.override_version_id && <Text>本地 {record.override_version_id}</Text>}
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 80,
      render: (_: unknown, __: PilotPackageInitialOverrideRequest, index: number) => (
        <Button
          aria-label="移除覆盖"
          icon={<DeleteOutlined aria-hidden="true" />}
          onClick={() => removeInitialOverride(index)}
        />
      ),
    },
  ];

  const columns: ColumnsType<KnowledgePackage> = [
    {
      title: "配置包编码",
      dataIndex: "packageCode",
      key: "packageCode",
      width: 150,
      render: (text: string) => <span className={styles.codeText}>{text}</span>,
    },
    {
      title: "名称",
      dataIndex: "name",
      key: "name",
      width: 180,
      render: (text: string) => <Text strong>{text}</Text>,
    },
    {
      title: "版本",
      dataIndex: "packageVersion",
      key: "packageVersion",
      width: 110,
      render: (text: string) => <Tag color="purple">{text}</Tag>,
    },
    {
      title: "访问",
      dataIndex: "accessPolicy",
      key: "accessPolicy",
      width: 110,
      render: (policy: string) => (
        <Tag color={policy === "ENTITLED" ? "gold" : "green"}>
          {policy === "ENTITLED" ? "按服务空间授权" : "开放"}
        </Tag>
      ),
    },
    {
      title: "资产条目",
      key: "itemCount",
      width: 120,
      render: (_: unknown, record: KnowledgePackage) => {
        const count =
          record.packageId === effectivePackageId && apiDetail?.items
            ? apiDetail.items.length
            : null;
        return (
          <Text type="secondary" className={styles.nowrap}>
            {count === null ? "打开后查看" : `${count} 个资产`}
          </Text>
        );
      },
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 110,
      render: (status: string) => (
        <Badge
          status={statusBadge[status] ?? "default"}
          text={statusText[status] ?? customerEnumLabel(status)}
        />
      ),
    },
    {
      title: "创建信息",
      key: "created",
      width: 170,
      render: (_: unknown, record: KnowledgePackage) => (
        <Space direction="vertical" size={0}>
          <Text className={styles.nowrap}>{record.createdBy}</Text>
          <Text type="secondary" className={styles.nowrap}>
            {new Date(record.createdAt).toLocaleDateString()}
          </Text>
        </Space>
      ),
    },
    {
      title: "操作",
      key: "actions",
      width: 260,
      render: (_: unknown, record: KnowledgePackage) => (
        <Space wrap>
          <Button
            type="link"
            onClick={() => {
              setSelectedPackageId(record.packageId);
              setDetailDrawerVisible(true);
            }}
          >
            办理细项
          </Button>
          <Button
            type="link"
            onClick={() => {
              setSelectedPackageId(record.packageId);
              setDiffModalVisible(true);
            }}
          >
            看影响
          </Button>
          <Button
            type="link"
            loading={offlineExportingId === record.packageId}
            onClick={() => openOfflineExportModal(record)}
          >
            导出离线包
          </Button>
          <Button type="link" onClick={() => openSyncModal(record.packageId)}>
            院内同步发布
          </Button>
          {canManageEntitlements && record.accessPolicy === "ENTITLED" && (
            <Button
              type="link"
              icon={<KeyOutlined aria-hidden="true" />}
              onClick={() => openEntitlementModal(record)}
            >
              授权管理
            </Button>
          )}
          {record.status === "ACTIVE" && (
            <Button
              type="link"
              danger
              onClick={() => {
                setSelectedPackageId(record.packageId);
                setRollbackModalVisible(true);
              }}
            >
              回滚
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const stepPanels: Partial<Record<StepKey, React.ReactNode>> = {
    select_template: (
      <Space direction="vertical" className="mk-full-width">
        <Text>从首发模板、离线包或手工草案进入发布流。当前模板 {pilotTemplates.length} 个。</Text>
        <Space wrap>
          <Button onClick={openPilotTemplateModal} disabled={!canApplyPilotTemplateReferences}>
            应用首发引用
          </Button>
          <Button onClick={() => setOfflineImportModalVisible(true)}>导入离线包</Button>
        </Space>
      </Space>
    ),
    auto_validate: (
      <Space direction="vertical" className="mk-full-width">
        <Text>后端按真实资产依赖复算首发就绪门，不在前端伪造通过状态。</Text>
        {readinessBlockers.length > 0 ? (
          <Alert
            type="warning"
            showIcon
            message="依赖仍有阻塞"
            description={readinessBlockers.join("；")}
          />
        ) : (
          <Alert type="success" showIcon message="未返回阻塞项" />
        )}
      </Space>
    ),
    impact_preview: (
      <Space direction="vertical" className="mk-full-width">
        <Text>包内资产 {currentItems.length} 个。选择基准版本后展示后端差异与影响科室。</Text>
        {apiDiffData && (
          <Space wrap>
            <Tag color="green">新增 {apiDiffData.addedCount}</Tag>
            <Tag color="blue">更新 {apiDiffData.updatedCount}</Tag>
            <Tag color="red">移除 {apiDiffData.removedCount}</Tag>
          </Space>
        )}
      </Space>
    ),
    submit_review: (
      <Text>
        当前状态：
        {selectedPackage
          ? (statusText[selectedPackage.status] ?? selectedPackage.status)
          : "未选择"}
        。
      </Text>
    ),
    canary_release: (
      <Text>灰度默认 10% 床位或指定组织范围；无真实同步通道时后端保持 NOT_SYNCED。</Text>
    ),
    full_rollout: <Text>全量发布仅院级管理员可直接触发，其他角色先走灰度并提交审核。</Text>,
    evidence_rollback: (
      <Space direction="vertical" className="mk-full-width">
        <Text>同步日志 {visibleSyncLogs.length} 条，可导出证据；已下线同编码版本可回滚。</Text>
        {attentionSyncLogs.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message={`失败 / 未连通适配器 ${attentionSyncLogs.length} 个`}
          />
        )}
      </Space>
    ),
  };

  const createPackageModal = (
    <Modal
      title="新建配置包草案"
      open={createModalVisible}
      onOk={handleCreatePackage}
      onCancel={() => setCreateModalVisible(false)}
      confirmLoading={createPackageMutation.isPending}
      destroyOnClose
      okText="提交创建草案"
      cancelText="取消"
    >
      <Form
        form={createForm}
        name="package-create"
        layout="vertical"
        initialValues={{ accessPolicy: "OPEN" }}
      >
        <Form.Item
          name="packageCode"
          label="配置包编码"
          rules={[{ required: true, message: "请输入配置包编码" }]}
        >
          <Input placeholder="输入当前服务空间内唯一配置包编码" />
        </Form.Item>
        <Form.Item
          name="packageVersion"
          label="配置包版本"
          rules={[{ required: true, message: "请输入配置包版本" }]}
        >
          <Input placeholder="输入版本号" />
        </Form.Item>
        <Form.Item
          name="name"
          label="配置包名称"
          rules={[{ required: true, message: "请输入配置包名称" }]}
        >
          <Input placeholder="输入配置包名称" />
        </Form.Item>
        <Form.Item name="description" label="发布范围说明">
          <TextArea rows={3} placeholder="填写资产范围、适用组织和发布计划摘要" />
        </Form.Item>
        {canManageEntitlements && (
          <Form.Item
            name="accessPolicy"
            label="访问策略"
            rules={[{ required: true, message: "请选择访问策略" }]}
          >
            <Select>
              <Option value="OPEN">开放给所有服务空间</Option>
              <Option value="ENTITLED">按服务空间授权</Option>
            </Select>
          </Form.Item>
        )}
        <Alert
          type="info"
          showIcon
          message="新包默认保持草案状态"
          description="只有通过资产依赖校验、影响分析和发布门禁后，才可进入灰度或全量。"
        />
      </Form>
    </Modal>
  );

  const entitlementColumns: ColumnsType<PackageEntitlement> = [
    {
      title: "服务空间",
      dataIndex: "tenantId",
      key: "tenantId",
      width: 180,
      render: (tenantId: string) => {
        const tenantName = tenantNameById.get(tenantId);
        return (
          <Space direction="vertical" size={0}>
            <Text strong>{tenantName ?? tenantId}</Text>
            {tenantName && <Text type="secondary">{tenantId}</Text>}
          </Space>
        );
      },
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 100,
      render: (status: string) => (
        <Tag color={entitlementStatusColor[status] ?? "default"}>
          {entitlementStatusText[status] ?? customerEnumLabel(status)}
        </Tag>
      ),
    },
    {
      title: "到期时间",
      dataIndex: "expiresAt",
      key: "expiresAt",
      width: 180,
      render: (expiresAt: string) => new Date(expiresAt).toLocaleString(),
    },
    {
      title: "最近操作",
      key: "updated",
      width: 180,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.updatedBy}</Text>
          <Text type="secondary">{new Date(record.updatedAt).toLocaleString()}</Text>
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 90,
      render: (_: unknown, record) =>
        record.status === "REVOKED" ? null : (
          <Button
            type="link"
            danger
            disabled={revokePackageEntitlementMutation.isPending}
            onClick={() => openRevokeEntitlementModal(record)}
          >
            撤销
          </Button>
        ),
    },
  ];

  const entitlementModal = (
    <Modal
      title={`服务空间授权 · ${entitlementPackage?.packageCode ?? ""}`}
      open={Boolean(entitlementPackage)}
      onCancel={closeEntitlementModal}
      footer={<Button onClick={closeEntitlementModal}>关闭</Button>}
      width={860}
      className={styles.entitlementModal}
      destroyOnClose
    >
      <Space
        direction="vertical"
        size="large"
        className={`${styles.entitlementContent} mk-full-width`}
      >
        <Alert
          type="info"
          showIcon
          message="授权只控制受限平台包"
          description="未授权服务空间不可见也不可下发；授权到期后保留审计历史，但停止解析。"
        />
        <Form form={entitlementForm} name="package-entitlement-grant" layout="vertical">
          <div className={styles.entitlementFormGrid}>
            <Form.Item
              name="targetTenantId"
              label="目标服务空间"
              rules={[{ required: true, message: "请选择目标服务空间" }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                loading={tenantDirectoryQuery.isLoading}
                disabled={tenantDirectoryQuery.isError}
                placeholder={
                  tenantDirectoryQuery.isError
                    ? "服务空间目录读取失败"
                    : "选择已启用服务空间"
                }
                options={activeCustomerTenants.map((tenant) => ({
                  value: tenant.tenantId,
                  label: `${tenant.name} · ${tenant.tenantId}`,
                }))}
                notFoundContent="没有可授权的已启用服务空间"
              />
            </Form.Item>
            <Form.Item
              name="expiresAt"
              label="授权到期时间"
              rules={[{ required: true, message: "请选择授权到期时间" }]}
            >
              <Input type="datetime-local" />
            </Form.Item>
          </div>
          {tenantDirectoryQuery.isError && (
            <Alert
              className={styles.formAlert}
              type="error"
              showIcon
              message="服务空间目录读取失败"
              action={<Button onClick={() => void tenantDirectoryQuery.refetch()}>重试</Button>}
            />
          )}
          <Form.Item
            name="reason"
            label="授权原因"
            rules={[{ required: true, message: "请填写授权原因" }]}
          >
            <TextArea rows={2} maxLength={500} placeholder="填写合同、审批或续期依据" />
          </Form.Item>
          <Button
            type="primary"
            icon={<KeyOutlined aria-hidden="true" />}
            loading={grantPackageEntitlementMutation.isPending}
            onClick={handleGrantPackageEntitlement}
          >
            开通或续期授权
          </Button>
        </Form>

        {entitlementQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="授权台账读取失败"
            action={<Button onClick={() => void entitlementQuery.refetch()}>重试</Button>}
          />
        ) : (
          <div className={styles.entitlementTableWrap}>
            <Table
              columns={entitlementColumns}
              dataSource={entitlementQuery.data?.items ?? []}
              rowKey="entitlementId"
              loading={entitlementQuery.isLoading}
              pagination={{
                current: entitlementQuery.data?.page ?? entitlementPage,
                pageSize: entitlementQuery.data?.size ?? 20,
                total: entitlementQuery.data?.total ?? 0,
                showSizeChanger: false,
                hideOnSinglePage: true,
                onChange: setEntitlementPage,
              }}
              size="small"
              locale={{ emptyText: "尚未向任何服务空间开通授权" }}
              scroll={{ x: 760 }}
            />
          </div>
        )}
      </Space>
    </Modal>
  );

  const revokeEntitlementModal = (
    <Modal
      title="撤销服务空间授权"
      open={Boolean(revokingEntitlement)}
      okText="确认撤销授权"
      cancelText="取消"
      okButtonProps={{ danger: true }}
      confirmLoading={revokePackageEntitlementMutation.isPending}
      onOk={() => revokeEntitlementForm.submit()}
      onCancel={closeRevokeEntitlementModal}
      destroyOnClose
    >
      <Alert
        className={styles.formAlert}
        type="warning"
        showIcon
        message={`撤销服务空间 ${revokingEntitlement?.tenantId ?? ""} 的授权`}
        description="撤销后该服务空间将无法继续解析或下发此平台包，历史授权记录仍保留用于审计。"
      />
      <Form
        form={revokeEntitlementForm}
        name="package-entitlement-revoke"
        layout="vertical"
        onFinish={handleRevokePackageEntitlement}
      >
        <Form.Item
          name="reason"
          label="撤销原因"
          rules={[{ required: true, message: "请填写撤销原因" }]}
        >
          <TextArea rows={3} maxLength={500} placeholder="填写合同终止、审批决定或其他撤销依据" />
        </Form.Item>
      </Form>
    </Modal>
  );

  const pilotTemplateModal = (
    <Modal
      title="应用首发平台包引用"
      open={pilotTemplateModalVisible}
      onOk={handleApplyPilotTemplateReferences}
      onCancel={closePilotTemplateModal}
      confirmLoading={applyPilotTemplateReferencesMutation.isPending}
      destroyOnClose
      forceRender
      okText="应用平台引用"
      cancelText="取消"
      width={880}
    >
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Form form={pilotTemplateForm} name="pilot-template-reference" layout="vertical">
          <Form.Item
            name="templateCode"
            label="首发模板"
            rules={[{ required: true, message: "请选择首发模板" }]}
          >
            <Select placeholder="请选择首发模板" onChange={handlePilotTemplateChange}>
              {pilotTemplates.map((template) => (
                <Option key={template.templateCode} value={template.templateCode}>
                  {template.name}
                </Option>
              ))}
            </Select>
          </Form.Item>
          {selectedPilotTemplate && (
            <Card size="small" className={styles.templatePreview}>
              <Space direction="vertical" className="mk-full-width">
                <Space className="mk-flex-between" wrap>
                  <Space direction="vertical" size={0}>
                    <Text strong>当前模板：{selectedPilotTemplate.name}</Text>
                    <Text type="secondary">
                      {selectedPilotTemplate.description || "该模板未填写说明"}
                    </Text>
                  </Space>
                  <Space wrap>
                    <Tag color="green">资产 {selectedPilotTemplate.itemCount} 个</Tag>
                    <Tag color="cyan">{selectedPilotTemplate.templateCode}</Tag>
                  </Space>
                </Space>
                <Space wrap>
                  {selectedPilotTemplate.items.map((item) => (
                    <Tag
                      key={`${item.assetType}-${item.assetId}-${item.assetVersion}`}
                      color={assetTypeColor(item.assetType)}
                    >
                      {item.assetType} · {item.assetId} · {item.assetVersion}
                    </Tag>
                  ))}
                </Space>
              </Space>
            </Card>
          )}
          <Form.Item
            name="targetOrgUnitId"
            label="目标组织"
            rules={[{ required: true, message: "请选择目标组织" }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择引用生效组织"
              options={orgUnitOptions}
              loading={orgUnitsLoading}
              notFoundContent="暂无可用组织单元"
              onChange={(value) => {
                if (!initialOverrideForm.getFieldValue("target_org_unit_id")) {
                  initialOverrideForm.setFieldsValue({ target_org_unit_id: value });
                }
              }}
            />
          </Form.Item>
        </Form>

        <Card size="small" title="初始覆盖例外" className={styles.templatePreview}>
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Form form={initialOverrideForm} name="pilot-initial-override" layout="vertical">
              <div className={styles.overrideGrid}>
                <Form.Item
                  name="override_mode"
                  label="覆盖方式"
                  rules={[{ required: true, message: "请选择覆盖方式" }]}
                >
                  <Select onChange={(value) => setOverrideModeInput(value)}>
                    <Option value="REPLACE">{overrideModeText("REPLACE")}</Option>
                    <Option value="DISABLE">{overrideModeText("DISABLE")}</Option>
                    <Option value="ADD">{overrideModeText("ADD")}</Option>
                  </Select>
                </Form.Item>
                <Form.Item
                  name="asset_type"
                  label="覆盖资产类型"
                  rules={[{ required: true, message: "请选择资产类型" }]}
                >
                  <Select options={inheritanceAssetTypeOptions} />
                </Form.Item>
                <Form.Item
                  name="asset_identity"
                  label="覆盖资产身份"
                  rules={[{ required: true, message: "请输入资产身份" }]}
                >
                  <Input placeholder="如 RULE.COPD.LOCAL" />
                </Form.Item>
                <Form.Item
                  name="target_org_unit_id"
                  label="覆盖目标组织"
                  rules={[{ required: true, message: "请选择覆盖目标组织" }]}
                >
                  <Select
                    showSearch
                    optionFilterProp="label"
                    options={orgUnitOptions}
                    loading={orgUnitsLoading}
                    notFoundContent="暂无可用组织单元"
                  />
                </Form.Item>
                <Form.Item
                  name="inherited_version_id"
                  label="继承版本 ID"
                  rules={
                    overrideModeInput === "ADD"
                      ? []
                      : [{ required: true, message: "请输入被继承版本 ID" }]
                  }
                >
                  <Input disabled={overrideModeInput === "ADD"} placeholder="ADD 时留空" />
                </Form.Item>
                <Form.Item
                  name="override_version_id"
                  label="覆盖版本 ID"
                  rules={
                    overrideModeInput === "DISABLE"
                      ? []
                      : [{ required: true, message: "请输入覆盖版本 ID" }]
                  }
                >
                  <Input
                    disabled={overrideModeInput === "DISABLE"}
                    placeholder="本地 ACTIVE 版本 ID"
                  />
                </Form.Item>
                <Form.Item
                  name="applicable_scope"
                  label="覆盖适用范围"
                  rules={[{ required: true, message: "请输入适用范围" }]}
                >
                  <Input placeholder="如 adult|inpatient" />
                </Form.Item>
                <Form.Item
                  name="propagation"
                  label="传播范围"
                  rules={[{ required: true, message: "请选择传播范围" }]}
                >
                  <Select>
                    <Option value="INHERITABLE">{propagationText("INHERITABLE")}</Option>
                    <Option value="EXCLUSIVE">{propagationText("EXCLUSIVE")}</Option>
                  </Select>
                </Form.Item>
              </div>
              <Form.Item
                name="diff_summary"
                label="差异说明"
                rules={[{ required: true, message: "请填写差异说明" }]}
              >
                <TextArea rows={2} maxLength={200} />
              </Form.Item>
              <Form.Item
                name="override_reason"
                label="覆盖原因"
                rules={[{ required: true, message: "请填写覆盖原因" }]}
              >
                <TextArea rows={2} maxLength={200} />
              </Form.Item>
              <Form.Item
                name="impact_scope"
                label="影响范围"
                rules={[{ required: true, message: "请填写影响范围" }]}
              >
                <TextArea rows={2} maxLength={200} />
              </Form.Item>
              <Button onClick={handleAddInitialOverride}>加入覆盖清单</Button>
            </Form>
            <Table
              dataSource={initialOverrides}
              columns={initialOverrideColumns}
              rowKey={(record) =>
                `${record.asset_identity}-${record.override_mode}-${
                  record.override_version_id ??
                  record.inherited_version_id ??
                  record.target_org_unit_id
                }`
              }
              size="small"
              pagination={false}
              locale={{ emptyText: "暂无覆盖例外" }}
              scroll={{ x: 720 }}
            />
          </Space>
        </Card>

        <Alert
          type="info"
          showIcon
          message="引用平台包，不复制资产"
          description="系统会校验模板推荐的平台包是否真实存在且已发布，并把引用绑定到目标组织。"
        />
      </Space>
    </Modal>
  );

  const offlineImportModal = (
    <Modal
      title="导入离线包"
      open={offlineImportModalVisible}
      onOk={handleImportOfflinePackage}
      onCancel={closeOfflineImportModal}
      confirmLoading={importOfflinePackageMutation.isPending}
      destroyOnClose
      okText="导入并校验"
      cancelText="取消"
      width={680}
      okButtonProps={{ disabled: !offlineImportContent }}
    >
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Alert
          type="info"
          showIcon
          message="导入后保持草案状态"
          description="系统会先校验格式、服务空间和内容摘要，通过后生成本地草案；仍需按本机构流程发布后才会生效。"
        />
        <Upload
          accept=".json,application/json"
          showUploadList={false}
          beforeUpload={handleOfflineImportFile}
        >
          <Button icon={<UploadOutlined aria-hidden="true" />}>选择 JSON 文件</Button>
        </Upload>
        {offlineImportSummary && (
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="文件" span={2}>
              {offlineImportSummary.filename}
            </Descriptions.Item>
            <Descriptions.Item label="配置包">
              {offlineImportSummary.packageCode} / {offlineImportSummary.packageVersion}
            </Descriptions.Item>
            <Descriptions.Item label="资产条目">{offlineImportSummary.itemCount}</Descriptions.Item>
          </Descriptions>
        )}
      </Space>
    </Modal>
  );

  const pageStateLoading = packageQuery.isLoading || pilotTemplatesLoading || readinessLoading;
  const pageStateError = packageQuery.isError || readinessError;
  const hasActiveFilters = Boolean(filters.keyword || filters.status);

  if (pageStateLoading) {
    return (
      <PageShell
        title="配置包中心"
        description="读取配置包发布事实"
        state="loading"
        stateProps={{
          title: "正在加载配置包中心",
          description: "正在读取配置包列表、首发模板和资产就绪门。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (pageStateError) {
    return (
      <PageShell
        title="配置包中心"
        description="请重试或联系信息科"
        state="error"
        stateProps={{
          title: "配置包中心读取失败",
          description: "请重试；若持续失败，请带 traceId 排查配置包和资产准备接口。",
          onRetry: () => {
            void packageQuery.refetch();
            void refetchAssetReadiness();
          },
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (apiPackages.length === 0 && !hasActiveFilters) {
    return (
      <>
        <PageShell
          title="配置包中心"
          description="等待首个配置包草案"
          state="empty"
          stateProps={{
            title: "暂无配置包",
            description:
              readinessBlockers.length > 0 ? (
                <Space direction="vertical" size={4}>
                  <Text>首发模板暂不可用，可先创建空白草案或导入离线包。</Text>
                  {readinessBlockers.map((blocker) => (
                    <Text key={blocker} type="warning">
                      {blocker}
                    </Text>
                  ))}
                </Space>
              ) : (
                "当前服务机构尚未生成配置包；可从首发模板或离线包创建草案。"
              ),
            action: <Button onClick={() => setOfflineImportModalVisible(true)}>导入离线包</Button>,
            onRetry: () => {
              void packageQuery.refetch();
              void refetchAssetReadiness();
            },
          }}
          primary={
            canApplyPilotTemplateReferences ? (
              <Button
                type="primary"
                icon={<PlusOutlined aria-hidden="true" />}
                onClick={openPilotTemplateModal}
              >
                应用首发引用
              </Button>
            ) : (
              <Button
                type="primary"
                icon={<PlusOutlined aria-hidden="true" />}
                onClick={() => setCreateModalVisible(true)}
              >
                新建配置包草案
              </Button>
            )
          }
        >
          <></>
        </PageShell>
        {createPackageModal}
        {pilotTemplateModal}
        {offlineImportModal}
      </>
    );
  }

  return (
    <PageShell
      title="配置包中心"
      description="按 7 步流发布、同步、留证和回滚"
      primary={
        <Button
          type="primary"
          icon={<CloudSyncOutlined aria-hidden="true" />}
          disabled={!selectedPackage}
          onClick={() => openSyncModal()}
        >
          发布配置包
        </Button>
      }
      extras={
        <Space wrap>
          <Button icon={<ExperimentOutlined aria-hidden="true" />} href="/config/releases">
            发布治理
          </Button>
          <Button
            icon={<UploadOutlined aria-hidden="true" />}
            onClick={() => setOfflineImportModalVisible(true)}
          >
            导入离线包
          </Button>
          <Button
            icon={<FileProtectOutlined aria-hidden="true" />}
            disabled={!canApplyPilotTemplateReferences}
            loading={pilotTemplatesLoading || readinessLoading}
            onClick={openPilotTemplateModal}
          >
            应用首发引用
          </Button>
          <Button
            icon={<PlusOutlined aria-hidden="true" />}
            onClick={() => setCreateModalVisible(true)}
          >
            新建配置包草案
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <section className={styles.summaryGrid}>
          <Card className={styles.summaryCard}>
            <Statistic
              title="总配置包版本 (累计)"
              value={totalPackagesCount}
              prefix={<FileProtectOutlined />}
            />
            <Text type="secondary">服务端分页总数</Text>
          </Card>
          <Card className={styles.summaryCard}>
            <Statistic title="生效中" value={activeCount} prefix={<CheckCircleOutlined />} />
            <Text type="secondary">当前 ACTIVE 版本</Text>
          </Card>
          <Card className={styles.summaryCard}>
            <Statistic title="待全量" value={publishedCount} prefix={<CloudSyncOutlined />} />
            <Text type="secondary">已发布但未成为主版本</Text>
          </Card>
          <Card className={styles.summaryCard}>
            <Statistic
              title="草案 / 已下线"
              value={draftCount + offlineCount}
              prefix={<WarningOutlined />}
            />
            <Text type="secondary">
              草案 {draftCount} 个 · 已下线 {offlineCount} 个
            </Text>
          </Card>
        </section>

        <Card title="首发资产准备" className={styles.sectionCard}>
          <Alert
            type={assetReadiness?.ready ? "success" : "warning"}
            showIcon
            message={
              <Space wrap>
                <span>{assetReadiness?.ready ? "已具备首发条件" : "仍有待处理项"}</span>
                <Tag color={assetReadiness?.grayscaleReady ? "green" : "orange"}>
                  灰度证据 {assetReadiness?.grayscaleReady ? "已满足" : "待完成"}
                </Tag>
                {assetReadiness?.readyPackageId && (
                  <Tag color="cyan">就绪包 {assetReadiness.readyPackageId}</Tag>
                )}
              </Space>
            }
            description={
              readinessBlockers.length > 0 ? (
                <Space wrap>
                  {readinessBlockers.map((blocker) => (
                    <Tag key={blocker} color="orange">
                      {blocker}
                    </Tag>
                  ))}
                </Space>
              ) : (
                <span>
                  模板 {assetReadiness?.templateCount ?? pilotTemplates.length} 个 · 草案{" "}
                  {assetReadiness?.draftPackageCount ?? draftCount} 个 · 已发布{" "}
                  {assetReadiness?.releasedPackageCount ?? publishedCount + activeCount} 个 ·
                  平台引用 {assetReadiness?.activePackageReferenceCount ?? 0} 个
                </span>
              )
            }
          />
        </Card>

        <StepFlow currentStep={currentStep} panelByStep={stepPanels} />

        <Card
          title="继承治理"
          className={styles.sectionCard}
          extra={
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              value={inheritancePerspective}
              onChange={(event) => setInheritancePerspective(event.target.value)}
              options={inheritancePerspectiveOptions}
            />
          }
        >
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Form
              form={inheritanceImpactForm}
              layout="inline"
              className={styles.inheritanceQuery}
              initialValues={{ applicableScope: "adult|inpatient" }}
            >
              <Form.Item
                name="assetType"
                label="影响资产类型"
                rules={[{ required: true, message: "请选择资产类型" }]}
              >
                <Select className={styles.statusSelect} options={inheritanceAssetTypeOptions} />
              </Form.Item>
              <Form.Item
                name="assetIdentity"
                label="影响资产身份"
                rules={[{ required: true, message: "请输入资产身份" }]}
              >
                <Input placeholder="如 RULE.VTE.RISK" />
              </Form.Item>
              <Form.Item
                name="applicableScope"
                label="适用范围"
                rules={[{ required: true, message: "请输入适用范围" }]}
              >
                <Input placeholder="如 adult|inpatient" />
              </Form.Item>
              <Form.Item
                name="upstreamVersionId"
                label="上游版本 ID"
                rules={[{ required: true, message: "请输入上游版本 ID" }]}
              >
                <Input placeholder="平台新版本 ID" />
              </Form.Item>
              <Button loading={inheritanceImpactFetching} onClick={handleQueryInheritanceImpact}>
                查询继承影响
              </Button>
            </Form>

            {inheritanceImpact ? (
              <>
                <section className={styles.impactStats}>
                  <div className={styles.impactStat}>
                    <Text type="secondary">上游版本</Text>
                    <Text strong>
                      {inheritanceImpact.upstreamBaseVersion} →{" "}
                      {inheritanceImpact.upstreamTargetVersion}
                    </Text>
                  </div>
                  <div className={styles.impactStat}>
                    <Text type="secondary">自动继承</Text>
                    <Text strong>{visibleAutoInheritedCount}</Text>
                  </div>
                  <div className={styles.impactStat}>
                    <Text type="secondary">建议 rebase</Text>
                    <Text strong>{visibleRebaseRequiredCount}</Text>
                  </div>
                  <div className={styles.impactStat}>
                    <Text type="secondary">视角内条目</Text>
                    <Text strong>{visibleInheritanceImpactTargets.length}</Text>
                    <Text type="secondary">
                      {
                        inheritancePerspectiveOptions.find(
                          (option) => option.value === inheritancePerspective,
                        )?.label
                      }
                    </Text>
                  </div>
                </section>
                <Table
                  dataSource={visibleInheritanceImpactTargets}
                  columns={inheritanceImpactColumns}
                  rowKey={(record) => `${record.orgUnitId}-${record.orgPath}`}
                  size="small"
                  pagination={false}
                  locale={{ emptyText: "当前视角暂无继承影响" }}
                  scroll={{ x: 820 }}
                />
              </>
            ) : (
              <Alert
                type="info"
                showIcon
                message="输入资产身份和平台上游版本后查看继承差异"
                description="结果由后端按平台基线、服务机构覆盖和组织闭包计算，页面只展示真实返回。"
              />
            )}
          </Space>
        </Card>

        <Card title="配置包台账" className={styles.sectionCard}>
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Form layout="inline" className={styles.filterBar}>
              <Form.Item label="关键字">
                <Input
                  placeholder="配置包名称或编码"
                  allowClear
                  value={keywordInput}
                  onChange={(event) => setKeywordInput(event.target.value)}
                  prefix={<SearchOutlined />}
                />
              </Form.Item>
              <Form.Item label="状态">
                <Select
                  placeholder="全部状态"
                  allowClear
                  value={statusInput}
                  onChange={(value) => setStatusInput(value)}
                  className={styles.statusSelect}
                >
                  {packageStatusOptions.map((option) => (
                    <Option key={option.value} value={option.value}>
                      {option.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
              <Space>
                <Button onClick={applyFilters}>查询</Button>
                <Button onClick={clearFilters}>重置</Button>
              </Space>
            </Form>

            <Table
              columns={columns}
              dataSource={apiPackages}
              rowKey="packageId"
              scroll={{ x: 1220 }}
              rowSelection={{
                type: "radio",
                selectedRowKeys: effectivePackageId ? [effectivePackageId] : [],
                onChange: (keys) => setSelectedPackageId(String(keys[0])),
              }}
              pagination={{
                current: currentPage,
                pageSize: 10,
                total: totalPackagesCount,
                onChange: (page) => setCurrentPage(page),
                showTotal: (total) => `共 ${total} 个配置包版本`,
              }}
              locale={{ emptyText: hasActiveFilters ? "没有匹配的配置包" : "暂无配置包" }}
            />
          </Space>
        </Card>

        {!syncModalVisible && attentionSyncLogs.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message="失败 / 未连通适配器"
            description={
              <Space direction="vertical" className="mk-full-width">
                {attentionSyncLogs.map((log) => (
                  <div key={log.logId} className={styles.syncIssue}>
                    <Space className="mk-flex-between">
                      <Text strong>{releaseAdapterName(log.adapterId)}</Text>
                      <Tag color={syncLogStatusColor(log.status)}>
                        {syncLogStatusText(log.status)}
                      </Tag>
                    </Space>
                    {log.errorMessage && <Text type="secondary">{log.errorMessage}</Text>}
                    {!log.syncEvidence && (
                      <Text type="secondary">该站点没有成功同步水位，系统不会伪造成已同步。</Text>
                    )}
                  </div>
                ))}
              </Space>
            }
            action={
              <Button loading={syncEvidenceExporting} onClick={handleExportSyncEvidence}>
                导出同步证据
              </Button>
            }
          />
        )}
      </Space>

      {createPackageModal}
      {entitlementModal}
      {revokeEntitlementModal}
      {pilotTemplateModal}

      <Modal
        title="导出离线包"
        open={offlineExportModalVisible}
        onOk={handleExportOfflinePackage}
        onCancel={closeOfflineExportModal}
        confirmLoading={offlineExportingId === offlineExportPackage?.packageId}
        destroyOnClose
        okText="导出有效快照"
        cancelText="取消"
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="info"
            showIcon
            message="按接收组织生成有效包"
            description="离线包会先解析平台基线与本地覆盖，只导出该组织最终生效的资产版本和来源指针。"
          />
          <Form form={offlineExportForm} layout="vertical">
            <Form.Item
              name="targetOrgUnitId"
              label="接收组织单元"
              rules={[{ required: true, message: "请选择接收组织单元" }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择接收组织"
                options={orgUnitOptions}
                loading={orgUnitsLoading}
                notFoundContent="暂无可用组织单元"
              />
            </Form.Item>
          </Form>
        </Space>
      </Modal>

      {offlineImportModal}

      <Drawer
        title="办理包内资产细项"
        aria-label="办理包内资产细项"
        width={920}
        onClose={() => {
          setDetailDrawerVisible(false);
          setSelectedPackageId(null);
          setSelectedAssetType("RULE");
          itemForm.resetFields(["assetType", "assetId", "assetVersion"]);
        }}
        open={detailDrawerVisible}
        destroyOnClose
      >
        {selectedPackage && (
          <Space direction="vertical" size="large" className="mk-full-width">
            <Descriptions bordered column={3} size="small">
              <Descriptions.Item label="包编码">{selectedPackage.packageCode}</Descriptions.Item>
              <Descriptions.Item label="版本">
                <Tag color="purple">{selectedPackage.packageVersion}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Badge
                  status={statusBadge[selectedPackage.status] ?? "default"}
                  text={
                    statusText[selectedPackage.status] ??
                    customerEnumLabel(selectedPackage.status)
                  }
                />
              </Descriptions.Item>
              <Descriptions.Item label="名称" span={3}>
                {selectedPackage.name}
              </Descriptions.Item>
              <Descriptions.Item label="说明" span={3}>
                {selectedPackage.description}
              </Descriptions.Item>
            </Descriptions>

            {selectedPackage.status === "DRAFT" ? (
              <Card
                title="追加临床资产条目"
                size="small"
                extra={
                  <Button href="/authoring/assets" icon={<FileProtectOutlined />}>
                    整理资产库
                  </Button>
                }
              >
                <Form form={itemForm} layout="vertical" onFinish={handleAddItem}>
                  <Form.Item
                    name="assetType"
                    label="资产类型"
                    rules={[{ required: true }]}
                    initialValue="RULE"
                  >
                    <Select
                      onChange={(value) => {
                        setSelectedAssetType(value as EngineAssetType);
                        itemForm.setFieldsValue({ assetType: value });
                        itemForm.resetFields(["assetId", "assetVersion"]);
                      }}
                    >
                      <Option value="RULE">规则引擎 (RULE)</Option>
                      <Option value="PATHWAY">临床路径 (PATHWAY)</Option>
                      <Option value="CONDITION_FRAGMENT">条件片段 (CONDITION_FRAGMENT)</Option>
                      <Option value="EVALUATION">质控评估指标 (EVALUATION)</Option>
                      <Option value="TERMINOLOGY">术语字典映射 (TERMINOLOGY)</Option>
                    </Select>
                  </Form.Item>
                  <Form.Item
                    name="assetId"
                    label="选择已发布的临床资产"
                    rules={[{ required: true, message: "请选择有效资产" }]}
                  >
                    <Select
                      placeholder="请选择已发布资产"
                      showSearch
                      allowClear
                      optionFilterProp="label"
                      onChange={fillAssetVersion}
                    >
                      {selectedAssetIsAuthoring &&
                        authoringAssetOptions.map((asset: AuthoringAssetLibraryItem) => (
                          <Option
                            key={`${asset.assetType}-${asset.assetId}-${asset.version}`}
                            value={asset.assetId}
                            label={`${asset.name} ${asset.assetCode} ${asset.version}`}
                          >
                            <Space size="small" wrap>
                              <span>{asset.name}</span>
                              <Text type="secondary">
                                {asset.assetCode} · v{asset.version}
                              </Text>
                              {asset.favorite && <Tag color="blue">已收藏</Tag>}
                              {asset.tags.slice(0, 2).map((tag) => (
                                <Tag key={tag}>{tag}</Tag>
                              ))}
                            </Space>
                          </Option>
                        ))}
                      {selectedAssetType === "EVALUATION" &&
                        (activeEvaluations?.items ?? []).map((evaluation: EvaluationIndicator) => (
                          <Option
                            key={evaluation.indicatorId}
                            value={evaluation.indicatorId}
                            label={`${evaluation.name} ${evaluation.indicatorCode}`}
                          >
                            {evaluation.name} ({evaluation.indicatorCode} · v{evaluation.versionNo})
                          </Option>
                        ))}
                      {selectedAssetType === "TERMINOLOGY" &&
                        terminologyPackageOptions.map((termPackage: KnowledgePackage) => (
                          <Option
                            key={termPackage.packageId}
                            value={terminologyAssetId(termPackage)}
                            label={`${termPackage.name} ${termPackage.packageCode} ${termPackage.primaryAssetId ?? ""}`}
                          >
                            {termPackage.name} ({termPackage.packageCode} /{" "}
                            {termPackage.primaryAssetId?.split("|").slice(1).join(":")})
                          </Option>
                        ))}
                    </Select>
                  </Form.Item>
                  <Form.Item name="assetVersion" label="资产快照版本" rules={[{ required: true }]}>
                    <Input
                      placeholder={assetVersionPlaceholder}
                      readOnly={
                        selectedAssetIsAuthoring ||
                        selectedAssetType === "EVALUATION" ||
                        selectedAssetType === "TERMINOLOGY"
                      }
                    />
                  </Form.Item>
                  <Button htmlType="submit" loading={addPackageItemMutation.isPending}>
                    确认将此资产关联加入当前包草稿
                  </Button>
                </Form>
              </Card>
            ) : (
              <Alert
                type="warning"
                showIcon
                message="资产条目已锁定"
                description="当前配置包不处于草案状态，如需修改资产，请创建新的配置包草案版本。"
              />
            )}

            <Card title={`配置包包含的核心资产条目 (${currentItems.length})`}>
              <Table
                dataSource={currentItems}
                rowKey="itemId"
                size="small"
                pagination={false}
                columns={[
                  { title: "资产条目", dataIndex: "itemId", key: "itemId" },
                  {
                    title: "资产类型",
                    dataIndex: "assetType",
                    key: "assetType",
                    render: (type: string) => <Tag color={assetTypeColor(type)}>{type}</Tag>,
                  },
                  { title: "资产 ID", dataIndex: "assetId", key: "assetId" },
                  {
                    title: "资产版本",
                    dataIndex: "assetVersion",
                    key: "assetVersion",
                    render: (version: string) => <Tag>{version}</Tag>,
                  },
                ]}
              />
            </Card>
          </Space>
        )}
      </Drawer>

      <Modal
        title="配置包多版本变动差异与临床影响分析"
        open={diffModalVisible}
        onCancel={() => {
          setDiffModalVisible(false);
          setBasePackageIdForDiff(undefined);
        }}
        width={720}
        footer={null}
        destroyOnClose
      >
        <Space direction="vertical" size="large" className="mk-full-width">
          <Space wrap className="mk-flex-between">
            <Text>
              当前版本：<Tag color="purple">{selectedPackage?.packageVersion || "未选择"}</Tag>
            </Text>
            <Select
              placeholder="请选择基准对比版本"
              value={basePackageIdForDiff}
              onChange={(value) => setBasePackageIdForDiff(value)}
              className={styles.diffSelect}
            >
              {apiPackages
                .filter((pkg) => pkg.packageId !== effectivePackageId)
                .map((pkg) => (
                  <Option key={pkg.packageId} value={pkg.packageId}>
                    {pkg.name} ({pkg.packageVersion})
                  </Option>
                ))}
            </Select>
            <Button
              icon={<DownloadOutlined />}
              disabled={!apiDiffData}
              loading={diffExporting}
              onClick={handleExportDiffEvidence}
            >
              导出影响证据
            </Button>
          </Space>

          {apiDiffData ? (
            <>
              <section className={styles.diffGrid}>
                <Card size="small">
                  <Statistic title="新增引入资产" value={apiDiffData.addedCount} />
                </Card>
                <Card size="small">
                  <Statistic title="升级改动资产" value={apiDiffData.updatedCount} />
                </Card>
                <Card size="small">
                  <Statistic title="废弃移除资产" value={apiDiffData.removedCount} />
                </Card>
              </section>
              <Card title="临床责任受影响科室">
                <Space wrap>
                  {(apiDiffData.affectedDepartments ?? []).map((dept) => (
                    <Tag color="geekblue" key={dept}>
                      {dept}
                    </Tag>
                  ))}
                </Space>
              </Card>
              <Text type="secondary">
                审计追踪号：{selectedPackage?.traceId || "后端未返回审计追踪号"}
              </Text>
            </>
          ) : (
            <Alert
              type="info"
              showIcon
              message="请选择基准配置包"
              description="选择后由后端计算新增、更新、移除和影响科室，不在前端伪造差异。"
            />
          )}
        </Space>
      </Modal>

      <Modal
        title="院内同步发布中心"
        open={syncModalVisible}
        onCancel={closeSyncModal}
        width={780}
        footer={null}
        destroyOnClose
      >
        <Form form={syncForm} layout="vertical" onFinish={handleSyncPackage}>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="包名称">{selectedPackage?.name}</Descriptions.Item>
            <Descriptions.Item label="发布版本">
              <Tag color="purple">{selectedPackage?.packageVersion}</Tag>
            </Descriptions.Item>
          </Descriptions>

          <Form.Item
            name="strategy"
            label="发布投放策略"
            rules={[{ required: true, message: "请选择同步发布策略" }]}
            initialValue="GRAYSCALE"
          >
            <Radio.Group>
              <Radio.Button value="GRAYSCALE">灰度发布 (GRAYSCALE)</Radio.Button>
              <Radio.Button value="FULL" disabled={!canDirectFullRelease}>
                全量发布 (FULL)
              </Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.strategy !== curr.strategy}>
            {({ getFieldValue }) =>
              getFieldValue("strategy") === "GRAYSCALE" ? (
                <Alert
                  type="info"
                  showIcon
                  className={styles.formAlert}
                  message="默认灰度策略"
                  description="默认按接收组织内 10% 床位进入灰度，不覆盖当前 ACTIVE 版本；需要直接全量时必须由院级管理员确认。"
                />
              ) : (
                <Alert
                  type="error"
                  showIcon
                  className={styles.formAlert}
                  message="全量发布"
                  description="全量发布成功后，当前配置包将激活为 ACTIVE。同编码旧版 ACTIVE 包会降级为 OFFLINE，确保版本切换可追溯。"
                />
              )
            }
          </Form.Item>
          <Form.Item
            name="reason"
            label="发布说明"
            rules={[{ required: true, message: "请填写审核结论或投放依据" }]}
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
              placeholder="填写审核结论、灰度依据或全量批准说明"
            />
          </Form.Item>
          <Form.Item
            name="adapterIds"
            label="选择发布适配器"
            rules={[{ required: true, message: "请至少选择一个发布适配器" }]}
          >
            <Select mode="multiple" placeholder="请选择已健康的发布适配器">
              {displayAdapters.map((adapter) => (
                <Option
                  key={adapter.adapterId}
                  value={adapter.adapterId}
                  disabled={
                    adapter.status !== "ACTIVE" ||
                    adapter.healthStatus !== "HEALTHY" ||
                    !adapter.connectorAvailable
                  }
                >
                  {adapter.adapterName} · {adapter.protocolType} ·{" "}
                  {adapter.healthStatus === "HEALTHY" && adapter.connectorAvailable
                    ? "健康"
                    : "未就绪"}
                </Option>
              ))}
            </Select>
          </Form.Item>
          {usableReleaseAdapters.length === 0 && (
            <Alert
              type="warning"
              showIcon
              className={styles.formAlert}
              message="暂无可用同步适配器"
              description="请先完成适配器配置与健康检查。"
              action={<Button href="/adapter/hub">前往适配器中心</Button>}
            />
          )}
          <Form.Item
            name="targetOrgUnitId"
            label="接收组织单元"
            rules={[{ required: true, message: "请选择接收组织单元" }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择接收组织"
              options={orgUnitOptions}
              loading={orgUnitsLoading}
              notFoundContent="暂无可用组织单元"
            />
          </Form.Item>

          {(syncExecuting || syncProgress > 0 || visibleSyncLogs.length > 0) && (
            <Card size="small" className={styles.syncProgress}>
              <Space direction="vertical" className="mk-full-width">
                <Space className="mk-flex-between">
                  <Text strong>同步发布执行进度</Text>
                  <Text>{syncProgress}%</Text>
                </Space>
                <Progress percent={syncProgress} status={syncExecuting ? "active" : "normal"} />
                {attentionSyncLogs.length > 0 && (
                  <Alert
                    type="warning"
                    showIcon
                    message="失败 / 未连通适配器"
                    description={
                      <Space direction="vertical" className="mk-full-width">
                        {attentionSyncLogs.map((log) => (
                          <div key={log.logId} className={styles.syncIssue}>
                            <Space className="mk-flex-between">
                              <Text strong>{releaseAdapterName(log.adapterId)}</Text>
                              <Tag color={syncLogStatusColor(log.status)}>
                                {syncLogStatusText(log.status)}
                              </Tag>
                            </Space>
                            {log.errorMessage && <Text type="secondary">{log.errorMessage}</Text>}
                            {!log.syncEvidence && (
                              <Text type="secondary">
                                该站点没有成功同步水位，系统不会伪造成已同步。
                              </Text>
                            )}
                          </div>
                        ))}
                      </Space>
                    }
                    action={
                      <Button loading={syncEvidenceExporting} onClick={handleExportSyncEvidence}>
                        导出同步证据
                      </Button>
                    }
                  />
                )}
                {visibleSyncLogs.length > 0 && (
                  <Timeline
                    items={visibleSyncLogs.map((log) => ({
                      key: log.logId,
                      color: syncLogStatusColor(log.status),
                      children: (
                        <Space direction="vertical" size={0}>
                          <Text>通道: {releaseAdapterName(log.adapterId)}</Text>
                          <Tag color={syncLogStatusColor(log.status)}>
                            {syncLogStatusText(log.status)}
                          </Tag>
                          {log.errorMessage && <Text type="secondary">{log.errorMessage}</Text>}
                          {log.syncEvidence && <Text type="secondary">{log.syncEvidence}</Text>}
                        </Space>
                      ),
                    }))}
                  />
                )}
              </Space>
            </Card>
          )}

          <Button
            htmlType="submit"
            loading={syncExecuting}
            disabled={usableReleaseAdapters.length === 0}
            icon={<CloudSyncOutlined aria-hidden="true" />}
            block
          >
            {syncExecuting ? "正在执行同步并写入证据..." : "开始同步发布"}
          </Button>
        </Form>
      </Modal>

      <Modal
        title="配置包版本安全回滚"
        open={rollbackModalVisible}
        onCancel={closeRollbackModal}
        width={680}
        footer={null}
        destroyOnClose
      >
        {selectedPackage && (
          <Space direction="vertical" size="large" className="mk-full-width">
            <Alert
              type="error"
              showIcon
              message="高危操作警告"
              description="版本回滚会下线当前执行中版本，并启用指定历史版本。此动作可能影响临床在用流程，请核实并由专家确认。"
            />
            <Form layout="vertical">
              <Form.Item label="回滚原因（写入审计）" required>
                <TextArea
                  rows={3}
                  maxLength={500}
                  showCount
                  value={rollbackReason}
                  onChange={(event) => setRollbackReason(event.target.value)}
                  placeholder="填写临床专家确认、回滚窗口、影响范围或故障原因"
                />
              </Form.Item>
              <Checkbox
                checked={rollbackConfirmed}
                onChange={(event) => setRollbackConfirmed(event.target.checked)}
              >
                我已核对当前版本与目标版本，并确认该回滚会影响临床在用流程。
              </Checkbox>
            </Form>
            <Table
              dataSource={availableRollbackPackages}
              rowKey="packageId"
              size="small"
              pagination={false}
              locale={{ emptyText: "暂无可回滚的已下线历史版本" }}
              columns={[
                {
                  title: "配置包版本",
                  dataIndex: "packageVersion",
                  key: "packageVersion",
                  render: (text: string) => <Tag color="purple">{text}</Tag>,
                },
                { title: "包名称", dataIndex: "name", key: "name" },
                {
                  title: "状态",
                  dataIndex: "status",
                  key: "status",
                  render: () => <Tag>已下线</Tag>,
                },
                {
                  title: "操作",
                  key: "rollback",
                  render: (_: unknown, record: KnowledgePackage) => (
                    <Popconfirm
                      title={`确认将 ${selectedPackage.packageVersion} 回滚至 ${record.packageVersion}？`}
                      description="系统会再次核对版本、原因与高危确认，校验失败不会变更包状态。"
                      onConfirm={() => handleRollback(record)}
                      okText="确认回退"
                      cancelText="我再想想"
                      disabled={rollbackActionDisabled}
                      okButtonProps={{ danger: true, loading: rollbackPackageMutation.isPending }}
                    >
                      <Button
                        danger
                        size="small"
                        icon={<HistoryOutlined aria-hidden="true" />}
                        disabled={rollbackActionDisabled}
                        loading={rollbackPackageMutation.isPending}
                      >
                        确认回滚
                      </Button>
                    </Popconfirm>
                  ),
                },
              ]}
            />
          </Space>
        )}
      </Modal>
    </PageShell>
  );
}
