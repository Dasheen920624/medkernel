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
  DownloadOutlined,
  FileProtectOutlined,
  HistoryOutlined,
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
  useCalculateDiff,
  useCreatePackage,
  useEvaluationIndicators,
  useImportOfflinePackage,
  useInstantiatePilotTemplate,
  usePackageAssetReadiness,
  usePackageDetail,
  usePackages,
  usePackageSyncLogs,
  usePathwayTemplates,
  usePilotPackageTemplates,
  useReleasePackage,
  useRollbackPackage,
  useRuleDefinitions,
  useSecurityProfile,
  useSyncTargets,
  useTerminologyPackages,
} from "@/shared/api/hooks";
import type {
  EvaluationIndicator,
  KnowledgePackage,
  PackageItem,
  PilotPackageTemplate,
  RuleDefinition,
  SyncLogResponse,
  TermMappingPackage,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import { StepFlow } from "@/shared/ui/StepFlow";
import type { StepKey } from "@/shared/ui/StepFlow.contract";
import styles from "./ConfigPackages.module.css";

const { TextArea } = Input;
const { Option } = Select;
const { Text } = Typography;

type PackageStatusFilter = "DRAFT" | "PUBLISHED" | "ACTIVE" | "OFFLINE";
type BadgeStatus = "success" | "processing" | "default" | "error" | "warning";

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

const packageStatusOptions: Array<{ value: PackageStatusFilter; label: string }> = [
  { value: "DRAFT", label: "草案" },
  { value: "PUBLISHED", label: "已发布" },
  { value: "ACTIVE", label: "生效中" },
  { value: "OFFLINE", label: "已下线" },
];

function hasHospitalAdminRole(roles: Array<{ code?: string }> | undefined) {
  return (roles ?? []).some((role) => {
    const normalized = (role.code ?? "").trim().toUpperCase().replace(/[-.]/g, "_");
    return (
      normalized === "HOSPITAL_ADMIN" ||
      normalized === "ROLE_HOSPITAL_ADMIN" ||
      normalized === "TENANT_ADMIN" ||
      normalized === "ROLE_TENANT_ADMIN"
    );
  });
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
    FOLLOWUP: "magenta",
  };
  return colors[type] || "default";
}

function defaultPilotPackageCode(template: PilotPackageTemplate) {
  const suffix = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  return `${template.packageCodePrefix}.${suffix}`;
}

function safeFilename(value: string) {
  return value.replace(/[^\w.-]/g, "_");
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
  const { data: apiSyncTargets } = useSyncTargets();
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
  const displayTargets = apiSyncTargets ?? [];
  const canDirectFullRelease = hasHospitalAdminRole(securityProfile?.roles);

  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [selectedPackageId, setSelectedPackageId] = useState<string | null>(null);
  const [diffModalVisible, setDiffModalVisible] = useState(false);
  const [basePackageIdForDiff, setBasePackageIdForDiff] = useState<string | undefined>(undefined);
  const [syncModalVisible, setSyncModalVisible] = useState(false);
  const [rollbackModalVisible, setRollbackModalVisible] = useState(false);
  const [offlineImportModalVisible, setOfflineImportModalVisible] = useState(false);
  const [pilotTemplateModalVisible, setPilotTemplateModalVisible] = useState(false);
  const [selectedPilotTemplateCode, setSelectedPilotTemplateCode] = useState<string | undefined>();
  const [offlineImportContent, setOfflineImportContent] = useState("");
  const [rollbackReason, setRollbackReason] = useState("");
  const [rollbackConfirmed, setRollbackConfirmed] = useState(false);
  const [selectedAssetType, setSelectedAssetType] = useState<string>("RULE");
  const [syncProgress, setSyncProgress] = useState(0);
  const [syncLogs, setSyncLogs] = useState<SyncLogResponse[]>([]);
  const [syncExecuting, setSyncExecuting] = useState(false);
  const [diffExporting, setDiffExporting] = useState(false);
  const [offlineExportingId, setOfflineExportingId] = useState<string | null>(null);
  const [syncEvidenceExporting, setSyncEvidenceExporting] = useState(false);

  const [createForm] = Form.useForm();
  const [itemForm] = Form.useForm();
  const [syncForm] = Form.useForm();
  const [pilotTemplateForm] = Form.useForm();

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
  const { data: apiDiffData } = useCalculateDiff(effectivePackageId || "", basePackageIdForDiff);

  const { data: activeRules } = useRuleDefinitions({ size: 100 });
  const { data: activePathways } = usePathwayTemplates({ size: 100 });
  const { data: activeEvaluations } = useEvaluationIndicators({ size: 100 });
  const { data: activeTerminologyPackages } = useTerminologyPackages({ size: 100 });

  const createPackageMutation = useCreatePackage();
  const addPackageItemMutation = useAddPackageItem();
  const releasePackageMutation = useReleasePackage();
  const rollbackPackageMutation = useRollbackPackage();
  const importOfflinePackageMutation = useImportOfflinePackage();
  const instantiatePilotTemplateMutation = useInstantiatePilotTemplate();

  const activeCount = apiPackages.filter((p) => p.status === "ACTIVE").length;
  const publishedCount = apiPackages.filter((p) => p.status === "PUBLISHED").length;
  const draftCount = apiPackages.filter((p) => p.status === "DRAFT").length;
  const offlineCount = apiPackages.filter((p) => p.status === "OFFLINE").length;
  const readinessBlockers = assetReadiness?.blockers ?? [];
  const canInstantiatePilotTemplate = pilotTemplates.length > 0 && !pilotTemplatesLoading;
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

  const terminologyPackageOptions = (activeTerminologyPackages?.items ?? []).filter(
    (item) => item.status === "PUBLISHED" || item.status === "GRAY",
  );
  const terminologyAssetId = (item: TermMappingPackage) =>
    `${item.packageCode}|${item.scopeLevel}|${item.scopeCode}`;
  const syncTargetName = (targetId: string) =>
    displayTargets.find((target) => target.targetId === targetId)?.targetName || targetId;

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
    pilotTemplateForm.setFieldsValue({
      templateCode: template.templateCode,
      packageCode: defaultPilotPackageCode(template),
      packageVersion: template.defaultPackageVersion,
      name: `${template.name}配置包`,
      description: template.description ?? "",
    });
    setPilotTemplateModalVisible(true);
  };

  const handlePilotTemplateChange = (templateCode: string) => {
    const template = pilotTemplates.find((item) => item.templateCode === templateCode);
    setSelectedPilotTemplateCode(templateCode);
    if (!template) return;
    pilotTemplateForm.setFieldsValue({
      packageCode: defaultPilotPackageCode(template),
      packageVersion: template.defaultPackageVersion,
      name: `${template.name}配置包`,
      description: template.description ?? "",
    });
  };

  const closePilotTemplateModal = () => {
    setPilotTemplateModalVisible(false);
    setSelectedPilotTemplateCode(undefined);
    pilotTemplateForm.resetFields();
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

  const handleInstantiatePilotTemplate = async () => {
    try {
      const values = await pilotTemplateForm.validateFields();
      const instantiated = await instantiatePilotTemplateMutation.mutateAsync({
        templateCode: values.templateCode,
        request: {
          packageCode: values.packageCode,
          packageVersion: values.packageVersion,
          name: values.name,
          description: values.description,
        },
      });
      message.success(`首发配置包草案已生成：${instantiated.packageInfo.packageCode}`);
      closePilotTemplateModal();
      void packageQuery.refetch();
      void refetchAssetReadiness();
    } catch (err: unknown) {
      if (applyApiFieldErrors(pilotTemplateForm, err)) return;
      message.error(getApiErrorMessage(err, "首发配置包草案生成失败，请核对模板资产依赖"));
    }
  };

  const handleAddItem = async () => {
    if (!effectivePackageId) return;
    try {
      const values = await itemForm.validateFields();
      await addPackageItemMutation.mutateAsync({
        packageId: effectivePackageId,
        request: {
          assetType: values.assetType,
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
          strategy,
          scopeType: strategy === "GRAYSCALE" ? values.scopeType || "ALL" : "ALL",
          scopeValue: strategy === "GRAYSCALE" ? values.scopeValue || "" : "",
          targetIds: values.targetIds,
          packageVersion: selectedPackage?.packageVersion || "",
        },
      });

      setSyncProgress(100);
      setSyncLogs(res?.logs || []);
      const hasNotSynced =
        res?.status === "NOT_SYNCED" ||
        (res?.logs || []).some((log) => log.status === "NOT_SYNCED");
      message[hasNotSynced ? "warning" : "success"](
        hasNotSynced ? "发布计划已记录，存在未接入同步目标。" : "院内同步发布完成。",
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

  const handleExportOfflinePackage = async (record: KnowledgePackage) => {
    setOfflineExportingId(record.packageId);
    try {
      const blob = await downloadPackageOfflineExport(record.packageId);
      triggerBlobDownload(blob, `package-offline-${safeFilename(record.packageCode)}.json`);
      message.success("离线包已开始下载，文件内包含完整性摘要。");
    } catch (err: unknown) {
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
  };

  const handleOfflineImportFile = (file: File) => {
    file
      .text()
      .then((content) => setOfflineImportContent(content))
      .catch(() => message.error("离线包 JSON 文件读取失败，请重新选择文件。"));
    return false;
  };

  const handleImportOfflinePackage = async () => {
    const offlinePackageJson = offlineImportContent.trim();
    if (!offlinePackageJson) {
      message.error("请先选择或粘贴离线包 JSON。");
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
        <Badge status={statusBadge[status] ?? "default"} text={statusText[status] ?? status} />
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
            onClick={() => handleExportOfflinePackage(record)}
          >
            导出离线包
          </Button>
          <Button type="link" onClick={() => openSyncModal(record.packageId)}>
            院内同步发布
          </Button>
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
          <Button onClick={openPilotTemplateModal} disabled={!canInstantiatePilotTemplate}>
            从首发模板创建
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
            message={`失败 / 未接入站点 ${attentionSyncLogs.length} 个`}
          />
        )}
      </Space>
    ),
  };

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
      <PageShell
        title="配置包中心"
        description="等待首个配置包草案"
        state="empty"
        stateProps={{
          title: "暂无配置包",
          description: "当前租户尚未生成配置包；可从首发模板或离线包创建草案。",
          onRetry: () => {
            void packageQuery.refetch();
            void refetchAssetReadiness();
          },
        }}
        primary={
          <Button
            type="primary"
            icon={<PlusOutlined aria-hidden="true" />}
            disabled={!canInstantiatePilotTemplate}
            onClick={openPilotTemplateModal}
          >
            从首发模板创建
          </Button>
        }
      >
        <></>
      </PageShell>
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
          <Button
            icon={<UploadOutlined aria-hidden="true" />}
            onClick={() => setOfflineImportModalVisible(true)}
          >
            导入离线包
          </Button>
          <Button
            icon={<FileProtectOutlined aria-hidden="true" />}
            disabled={!canInstantiatePilotTemplate}
            loading={pilotTemplatesLoading || readinessLoading}
            onClick={openPilotTemplateModal}
          >
            从首发模板创建
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
                  {assetReadiness?.releasedPackageCount ?? publishedCount + activeCount} 个
                </span>
              )
            }
          />
        </Card>

        <StepFlow currentStep={currentStep} panelByStep={stepPanels} />

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
              scroll={{ x: 1100 }}
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
            message="失败 / 未接入站点"
            description={
              <Space direction="vertical" className="mk-full-width">
                {attentionSyncLogs.map((log) => (
                  <div key={log.logId} className={styles.syncIssue}>
                    <Space className="mk-flex-between">
                      <Text strong>{syncTargetName(log.targetId)}</Text>
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
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="packageCode"
            label="配置包编码"
            rules={[{ required: true, message: "请输入配置包编码" }]}
          >
            <Input placeholder="输入租户内唯一配置包编码" />
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
          <Alert
            type="info"
            showIcon
            message="新包默认保持草案状态"
            description="只有通过资产依赖校验、影响分析和发布门禁后，才可进入灰度或全量。"
          />
        </Form>
      </Modal>

      <Modal
        title="从首发模板创建配置包草案"
        open={pilotTemplateModalVisible}
        onOk={handleInstantiatePilotTemplate}
        onCancel={closePilotTemplateModal}
        confirmLoading={instantiatePilotTemplateMutation.isPending}
        destroyOnClose
        forceRender
        okText="生成配置包草案"
        cancelText="取消"
        width={760}
      >
        <Form form={pilotTemplateForm} layout="vertical">
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
            name="packageCode"
            label="配置包编码"
            rules={[{ required: true, message: "请输入配置包编码" }]}
          >
            <Input placeholder="输入配置包编码" />
          </Form.Item>
          <Form.Item
            name="packageVersion"
            label="配置包版本"
            rules={[{ required: true, message: "请输入配置包版本" }]}
          >
            <Input placeholder="输入配置包版本" />
          </Form.Item>
          <Form.Item
            name="name"
            label="配置包名称"
            rules={[{ required: true, message: "请输入配置包名称" }]}
          >
            <Input placeholder="输入配置包名称" />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <TextArea rows={3} placeholder="输入本次首发准备说明" />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="只生成草案，不绕过发布门禁"
            description="系统会校验模板中的必需资产是否真实存在且已发布；生成后仍需走灰度、全量与回滚审计链路。"
          />
        </Form>
      </Modal>

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
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Alert
            type="info"
            showIcon
            message="导入后保持草案状态"
            description="系统会先校验格式、租户和 payload 摘要，通过后生成本地草案；仍需按本院流程发布后才会生效。"
          />
          <Upload
            accept=".json,application/json"
            showUploadList={false}
            beforeUpload={handleOfflineImportFile}
          >
            <Button icon={<UploadOutlined aria-hidden="true" />}>选择 JSON 文件</Button>
          </Upload>
          <Form layout="vertical">
            <Form.Item label="离线包 JSON" htmlFor="offline-package-json" required>
              <TextArea
                id="offline-package-json"
                rows={10}
                value={offlineImportContent}
                onChange={(event) => setOfflineImportContent(event.target.value)}
                placeholder="粘贴离线包 JSON 内容"
              />
            </Form.Item>
          </Form>
        </Space>
      </Modal>

      <Drawer
        title="办理包内资产细项"
        width={920}
        onClose={() => {
          setDetailDrawerVisible(false);
          setSelectedPackageId(null);
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
                  text={statusText[selectedPackage.status] ?? selectedPackage.status}
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
              <Card title="追加临床资产条目" size="small">
                <Form form={itemForm} layout="vertical" onFinish={handleAddItem}>
                  <Form.Item
                    name="assetType"
                    label="资产类型"
                    rules={[{ required: true }]}
                    initialValue="RULE"
                  >
                    <Select
                      onChange={(value) => {
                        setSelectedAssetType(value);
                        itemForm.resetFields(["assetId", "assetVersion"]);
                      }}
                    >
                      <Option value="RULE">规则引擎 (RULE)</Option>
                      <Option value="PATHWAY">临床路径 (PATHWAY)</Option>
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
                      onChange={(value) => {
                        if (selectedAssetType !== "TERMINOLOGY") return;
                        const selected = terminologyPackageOptions.find(
                          (item) => terminologyAssetId(item) === value,
                        );
                        itemForm.setFieldsValue({
                          assetVersion: selected?.packageVersion,
                        });
                      }}
                    >
                      {selectedAssetType === "RULE" &&
                        (activeRules?.items ?? []).map((rule: RuleDefinition) => (
                          <Option key={rule.ruleId} value={rule.ruleId}>
                            {rule.name} ({rule.ruleId})
                          </Option>
                        ))}
                      {selectedAssetType === "PATHWAY" &&
                        (activePathways?.items ?? []).map((pathway) => (
                          <Option key={pathway.templateId} value={pathway.templateId}>
                            {pathway.name} ({pathway.templateId})
                          </Option>
                        ))}
                      {selectedAssetType === "EVALUATION" &&
                        (activeEvaluations?.items ?? []).map((evaluation: EvaluationIndicator) => (
                          <Option key={evaluation.indicatorId} value={evaluation.indicatorId}>
                            {evaluation.name} ({evaluation.indicatorId})
                          </Option>
                        ))}
                      {selectedAssetType === "TERMINOLOGY" &&
                        terminologyPackageOptions.map((termPackage: TermMappingPackage) => (
                          <Option
                            key={`${termPackage.packageCode}-${termPackage.packageVersion}-${termPackage.scopeLevel}-${termPackage.scopeCode}`}
                            value={terminologyAssetId(termPackage)}
                          >
                            {termPackage.displayName} ({termPackage.packageCode} /{" "}
                            {termPackage.scopeLevel}:{termPackage.scopeCode})
                          </Option>
                        ))}
                    </Select>
                  </Form.Item>
                  <Form.Item name="assetVersion" label="资产快照版本" rules={[{ required: true }]}>
                    <Input
                      placeholder={
                        selectedAssetType === "TERMINOLOGY"
                          ? "选择术语包后自动带出版本"
                          : "输入资产快照版本"
                      }
                      readOnly={selectedAssetType === "TERMINOLOGY"}
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
            name="targetIds"
            label="选择同步通道目标"
            rules={[{ required: true, message: "请至少选择一个同步目标" }]}
          >
            <Select mode="multiple" placeholder="请选择同步目标通道">
              {displayTargets.map((target) => (
                <Option key={target.targetId} value={target.targetId}>
                  {target.targetName}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="targetOrgUnitId"
            label="接收组织单元"
            rules={[{ required: true, message: "请输入接收组织单元 ID" }]}
          >
            <Input placeholder="请输入真实组织单元 ID" />
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
                    message="失败 / 未接入站点"
                    description={
                      <Space direction="vertical" className="mk-full-width">
                        {attentionSyncLogs.map((log) => (
                          <div key={log.logId} className={styles.syncIssue}>
                            <Space className="mk-flex-between">
                              <Text strong>{syncTargetName(log.targetId)}</Text>
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
                          <Text>通道: {syncTargetName(log.targetId)}</Text>
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
