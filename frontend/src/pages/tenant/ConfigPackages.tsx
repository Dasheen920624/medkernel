import { useState } from "react";
import {
  Table,
  Button,
  Drawer,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  Card,
  Descriptions,
  Badge,
  Alert,
  App as AntdApp,
  Upload,
  Row,
  Col,
  Timeline,
  Statistic,
  Progress,
  Radio,
  Popconfirm,
  Checkbox,
  theme as antdTheme,
  Space,
} from "antd";
import {
  PlusOutlined,
  CloudSyncOutlined,
  HistoryOutlined,
  SettingOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  InfoCircleOutlined,
  CompassOutlined,
  ArrowRightOutlined,
  DatabaseOutlined,
  AuditOutlined,
  FileProtectOutlined,
  SearchOutlined,
  DownloadOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useSyncTargets,
  useCreatePackage,
  usePackages,
  usePackageDetail,
  useAddPackageItem,
  useCalculateDiff,
  useSyncPackage,
  useRollbackPackage,
  useRuleDefinitions,
  usePathwayTemplates,
  useEvaluationIndicators,
  useTerminologyMappings,
  useImportOfflinePackage,
  downloadPackageDiffExport,
  downloadPackageOfflineExport,
} from "@/shared/api/hooks";
import type {
  EvaluationIndicator,
  KnowledgePackage,
  PackageItem,
  PathwayTemplate,
  RuleDefinition,
  SyncLogResponse,
  TermMapping,
} from "@/shared/api/hooks";

const { TextArea } = Input;
const { Option } = Select;

type ApiErrorResponse = {
  response?: {
    data?: {
      message?: string;
    };
  };
};

type BadgeStatus = "success" | "processing" | "default" | "error" | "warning";

function apiErrorMessage(error: unknown, fallback: string) {
  if (typeof error !== "object" || error === null || !("response" in error)) {
    return fallback;
  }
  const messageText = (error as ApiErrorResponse).response?.data?.message;
  return messageText && messageText.trim().length > 0 ? messageText : fallback;
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

export default function ConfigPackages() {
  const { token } = antdTheme.useToken();
  const { message } = AntdApp.useApp();

  // 1. API 数据拉取
  const [currentPage, setCurrentPage] = useState<number>(1);
  const { data: apiPackagesData, refetch: refetchPackages } = usePackages(currentPage - 1, 10);
  const { data: apiSyncTargets } = useSyncTargets();

  const [searchKeyword, setSearchKeyword] = useState<string>("");

  const apiPackages = apiPackagesData?.items ?? [];
  const normalizedKeyword = searchKeyword.trim().toLowerCase();
  const displayPackages = apiPackages.filter(
    (p) =>
      !normalizedKeyword ||
      p.name.toLowerCase().includes(normalizedKeyword) ||
      p.packageCode.toLowerCase().includes(normalizedKeyword),
  );

  const totalPackagesCount =
    normalizedKeyword.length > 0 ? displayPackages.length : (apiPackagesData?.total ?? 0);

  const displayTargets = apiSyncTargets ?? [];

  // 3. UI 模态窗控制状态
  const [createModalVisible, setCreateModalVisible] = useState<boolean>(false);
  const [detailDrawerVisible, setDetailDrawerVisible] = useState<boolean>(false);
  const [selectedPackageId, setSelectedPackageId] = useState<string | null>(null);

  const [diffModalVisible, setDiffModalVisible] = useState<boolean>(false);
  const [basePackageIdForDiff, setBasePackageIdForDiff] = useState<string | undefined>(undefined);

  const [syncModalVisible, setSyncModalVisible] = useState<boolean>(false);
  const [rollbackModalVisible, setRollbackModalVisible] = useState<boolean>(false);
  const [offlineImportModalVisible, setOfflineImportModalVisible] = useState<boolean>(false);
  const [offlineImportContent, setOfflineImportContent] = useState<string>("");
  const [rollbackReason, setRollbackReason] = useState<string>("");
  const [rollbackConfirmed, setRollbackConfirmed] = useState<boolean>(false);

  const closeRollbackModal = () => {
    setRollbackModalVisible(false);
    setRollbackReason("");
    setRollbackConfirmed(false);
  };

  // 物理添加细项的库选项（RULE/PATHWAY/EVALUATION/TERMINOLOGY）
  const { data: activeRules } = useRuleDefinitions({ size: 100 });
  const { data: activePathways } = usePathwayTemplates({ size: 100 });
  const { data: activeEvaluations } = useEvaluationIndicators({ size: 100 });
  const { data: activeTerminologies } = useTerminologyMappings({ size: 100 });

  // 4. API 突变 Hooks
  const createPackageMutation = useCreatePackage();
  const addPackageItemMutation = useAddPackageItem();
  const syncPackageMutation = useSyncPackage();
  const rollbackPackageMutation = useRollbackPackage();
  const importOfflinePackageMutation = useImportOfflinePackage();

  // 5. 表单定义
  const [createForm] = Form.useForm();
  const [itemForm] = Form.useForm();
  const [syncForm] = Form.useForm();

  // 选中包的详情与细项
  const selectedPackage = displayPackages.find((p) => p.packageId === selectedPackageId);
  const { data: apiDetail, refetch: refetchPackageDetail } = usePackageDetail(
    selectedPackageId || "",
  );
  const currentItems = apiDetail?.items || [];

  // 计算多版本差异 API
  const { data: apiDiffData } = useCalculateDiff(selectedPackageId || "", basePackageIdForDiff);
  const [diffExporting, setDiffExporting] = useState<boolean>(false);
  const [offlineExportingId, setOfflineExportingId] = useState<string | null>(null);

  // 6. 核心动作逻辑

  // A. 创建包草稿
  const handleCreatePackage = async () => {
    try {
      const values = await createForm.validateFields();
      const res = await createPackageMutation.mutateAsync({
        packageCode: values.packageCode,
        packageVersion: values.packageVersion,
        name: values.name,
        description: values.description,
      });

      message.success(`知识配置包草稿创建成功！编码: ${res?.packageCode}`);
      setCreateModalVisible(false);
      createForm.resetFields();
      refetchPackages();
    } catch (err: unknown) {
      message.error(apiErrorMessage(err, "配置包草稿创建失败，请检查接口状态后重试。"));
    }
  };

  // B. 细项管理：添加资产
  const [selectedAssetType, setSelectedAssetType] = useState<string>("RULE");
  const handleAddItem = async () => {
    if (!selectedPackageId) return;
    try {
      const values = await itemForm.validateFields();
      await addPackageItemMutation.mutateAsync({
        packageId: selectedPackageId,
        request: {
          assetType: values.assetType,
          assetId: values.assetId,
          assetVersion: values.assetVersion,
        },
      });

      message.success("资产细项添加成功！");
      itemForm.resetFields(["assetId", "assetVersion"]);
      refetchPackageDetail();
      refetchPackages();
    } catch (err: unknown) {
      message.error(apiErrorMessage(err, "资产细项添加失败，请检查接口状态后重试。"));
    }
  };

  // D. 多物理通道投影同步
  const [syncProgress, setSyncProgress] = useState<number>(0);
  const [syncLogs, setSyncLogs] = useState<SyncLogResponse[]>([]);
  const [syncExecuting, setSyncExecuting] = useState<boolean>(false);

  const handleSyncPackage = async () => {
    if (!selectedPackageId) return;
    try {
      const values = await syncForm.validateFields();
      setSyncExecuting(true);
      setSyncProgress(20);

      // 发起后端同步
      const res = await syncPackageMutation.mutateAsync({
        packageId: selectedPackageId,
        request: {
          targetOrgUnitId: values.targetOrgUnitId,
          strategy: values.strategy,
          scopeType: values.scopeType || "ALL",
          scopeValue: values.scopeValue || "",
          targetIds: values.targetIds,
        },
      });

      setSyncProgress(100);
      setSyncLogs(res?.logs || []);
      setSyncExecuting(false);
      message.success("物理通道投影同步完成！");
      refetchPackages();
    } catch (err: unknown) {
      setSyncExecuting(false);
      setSyncProgress(0);
      setSyncLogs([]);
      message.error(apiErrorMessage(err, "投影同步失败，未生成同步证据。"));
    }
  };

  const handleExportDiffEvidence = async () => {
    if (!selectedPackageId || !apiDiffData) return;
    setDiffExporting(true);
    try {
      const blob = await downloadPackageDiffExport(selectedPackageId, basePackageIdForDiff);
      const safeName = (selectedPackage?.packageCode || selectedPackageId).replace(/[^\w.-]/g, "_");
      triggerBlobDownload(blob, `package-diff-${safeName}.jsonl`);
      message.success("影响范围证据已开始下载。");
    } catch (err: unknown) {
      message.error(apiErrorMessage(err, "影响范围证据导出失败，请稍后重试。"));
    } finally {
      setDiffExporting(false);
    }
  };

  const handleExportOfflinePackage = async (record: KnowledgePackage) => {
    setOfflineExportingId(record.packageId);
    try {
      const blob = await downloadPackageOfflineExport(record.packageId);
      const safeName = (record.packageCode || record.packageId).replace(/[^\w.-]/g, "_");
      triggerBlobDownload(blob, `package-offline-${safeName}.json`);
      message.success("离线包已开始下载，文件内包含完整性摘要。");
    } catch (err: unknown) {
      message.error(apiErrorMessage(err, "离线包导出失败，请检查接口状态后重试。"));
    } finally {
      setOfflineExportingId(null);
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
      refetchPackages();
    } catch (err: unknown) {
      message.error(apiErrorMessage(err, "离线包导入失败，未通过完整性校验或发布门禁。"));
    }
  };

  // E. 版本一键回滚
  const handleRollback = async (targetPackage: KnowledgePackage) => {
    if (!selectedPackageId || !selectedPackage) return;
    const reason = rollbackReason.trim();
    if (!reason || !rollbackConfirmed) {
      message.error("请填写回滚原因，并完成高危影响确认。");
      return;
    }
    try {
      await rollbackPackageMutation.mutateAsync({
        packageId: selectedPackageId,
        request: {
          targetPackageId: targetPackage.packageId,
          confirmedCurrentVersion: selectedPackage.packageVersion,
          confirmedTargetVersion: targetPackage.packageVersion,
          reason,
          confirmedHighRisk: rollbackConfirmed,
        },
      });
      message.success("版本回滚成功，目标版本已启用，当前版本已下线。");
      closeRollbackModal();
      refetchPackages();
    } catch (err: unknown) {
      message.error(apiErrorMessage(err, "版本回滚失败，状态未在前端伪造切换。"));
    }
  };

  // 7. 看板指标统计 (Metric Card Calculations)
  const activeCount = displayPackages.filter((p) => p.status === "ACTIVE").length;
  const publishedCount = displayPackages.filter((p) => p.status === "PUBLISHED").length;
  const draftCount = displayPackages.filter((p) => p.status === "DRAFT").length;
  const offlineCount = displayPackages.filter((p) => p.status === "OFFLINE").length;
  const rollbackActionDisabled = !rollbackReason.trim() || !rollbackConfirmed;
  const availableRollbackPackages = selectedPackage
    ? displayPackages.filter(
        (p) =>
          p.packageId !== selectedPackageId &&
          p.status === "OFFLINE" &&
          p.packageCode === selectedPackage.packageCode,
      )
    : [];

  // 8. 表格列定义
  const columns = [
    {
      title: "配置包编码",
      dataIndex: "packageCode",
      key: "packageCode",
      render: (text: string) => <span className="font-normal text-xs font-semibold">{text}</span>,
    },
    {
      title: "包名称",
      dataIndex: "name",
      key: "name",
      className: "font-semibold text-slate-800",
    },
    {
      title: "发布版本",
      dataIndex: "packageVersion",
      key: "packageVersion",
      render: (text: string) => (
        <Tag color="purple" className="font-normal">
          {text}
        </Tag>
      ),
    },
    {
      title: "配置细项",
      key: "itemCount",
      render: (_: unknown, record: KnowledgePackage) => {
        const count =
          record.packageId === selectedPackageId && apiDetail?.items
            ? apiDetail.items.length
            : null;
        return (
          <span className="font-normal font-medium">
            {count === null ? "打开后查看" : `${count} 个资产`}
          </span>
        );
      },
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: string) => {
        const config: Record<string, { status: BadgeStatus; text: string }> = {
          DRAFT: { status: "default", text: "草案 (DRAFT)" },
          PUBLISHED: { status: "processing", text: "已发布 (PUBLISHED)" },
          ACTIVE: { status: "success", text: "执行中 (ACTIVE)" },
          OFFLINE: { status: "error", text: "已下线 (OFFLINE)" },
        };
        const current = config[status] || { status: "default", text: status };
        return <Badge status={current.status} text={current.text} className="font-medium" />;
      },
    },
    {
      title: "创建人 / 创建日期",
      key: "creator",
      render: (_: unknown, record: KnowledgePackage) => (
        <div className="text-xs text-slate-500">
          <div>{record.createdBy}</div>
          <div className="font-normal text-[10px] text-slate-400">
            {new Date(record.createdAt).toLocaleDateString()}
          </div>
        </div>
      ),
    },
    {
      title: "操作",
      key: "actions",
      render: (_: unknown, record: KnowledgePackage) => {
        const isActive = record.status === "ACTIVE";
        return (
          <Space size="middle">
            <Button
              type="link"
              onClick={() => {
                setSelectedPackageId(record.packageId);
                setDetailDrawerVisible(true);
              }}
              className="p-0 font-semibold text-sky-600 hover:text-sky-800"
            >
              办理细项
            </Button>
            <Button
              type="link"
              onClick={() => {
                setSelectedPackageId(record.packageId);
                setDiffModalVisible(true);
              }}
              className="p-0 font-semibold text-indigo-600 hover:text-indigo-800"
            >
              变动差异比对
            </Button>
            <Button
              type="link"
              icon={<DownloadOutlined aria-hidden="true" />}
              aria-label="导出离线包"
              loading={offlineExportingId === record.packageId}
              onClick={() => handleExportOfflinePackage(record)}
              className="p-0 font-semibold text-cyan-700 hover:text-cyan-900"
            >
              导出离线包
            </Button>
            <Button
              type="link"
              onClick={() => {
                setSelectedPackageId(record.packageId);
                setSyncModalVisible(true);
              }}
              className="p-0 font-semibold text-emerald-600 hover:text-emerald-800"
            >
              物理投影同步
            </Button>
            {isActive && (
              <Button
                type="link"
                danger
                onClick={() => {
                  setSelectedPackageId(record.packageId);
                  setRollbackModalVisible(true);
                }}
                className="p-0 font-semibold"
              >
                一键回滚
              </Button>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <PageShell
      title="配置包中心"
      description="提供全租户专病智能发布引擎工作台。支持医疗资产（规则、路径、指标、术语字典）统一打包封存、多版本变动科室影响度分析、多物理通道（HIS、Dify等）全量与灰度发布投影及一键回滚存证审计。"
    >
      {/* 1. 顶端宏观数据 Metric 看板 Grid */}
      <Row gutter={16} className="mb-6">
        <Col span={6}>
          <div className="bg-gradient-to-br from-slate-50 to-slate-100 border border-slate-200 p-6 rounded-2xl shadow-sm hover:shadow-md transition-shadow duration-300">
            <Statistic
              title={<span className="text-slate-500 font-medium">总配置包版本 (累计)</span>}
              value={totalPackagesCount}
              valueStyle={{ color: token.colorPrimary, fontWeight: "bold", fontSize: "28px" }}
              prefix={<DatabaseOutlined className="mr-2 text-slate-500" />}
            />
            <div className="text-xs text-slate-400 mt-2">基于多租户物理安全隔离的版本快照</div>
          </div>
        </Col>
        <Col span={6}>
          <div className="bg-gradient-to-br from-emerald-50 to-teal-50 border border-emerald-100 p-6 rounded-2xl shadow-sm hover:shadow-md transition-shadow duration-300">
            <Statistic
              title={<span className="text-emerald-600 font-medium">运行执行中 (ACTIVE)</span>}
              value={activeCount}
              valueStyle={{ color: token.colorSuccess, fontWeight: "bold", fontSize: "28px" }}
              prefix={<CheckCircleOutlined className="mr-2 text-emerald-500 animate-pulse" />}
            />
            <div className="text-xs text-emerald-600 font-semibold mt-2 flex items-center gap-1">
              <span>当前主干正承载临床决策流转运行</span>
            </div>
          </div>
        </Col>
        <Col span={6}>
          <div className="bg-gradient-to-br from-blue-50 to-sky-50 border border-blue-100 p-6 rounded-2xl shadow-sm hover:shadow-md transition-shadow duration-300">
            <Statistic
              title={<span className="text-blue-600 font-medium">已发布待激活 (PUBLISHED)</span>}
              value={publishedCount}
              valueStyle={{ color: token.colorInfo, fontWeight: "bold", fontSize: "28px" }}
              prefix={<CloudSyncOutlined className="mr-2 text-blue-500" />}
            />
            <div className="text-xs text-slate-400 mt-2">已投影成功，尚未成为历史回滚点</div>
          </div>
        </Col>
        <Col span={6}>
          <div className="bg-gradient-to-br from-amber-50 to-orange-50 border border-amber-100 p-6 rounded-2xl shadow-sm hover:shadow-md transition-shadow duration-300">
            <Statistic
              title={<span className="text-amber-600 font-medium">未发布草案 / 下线</span>}
              value={draftCount + offlineCount}
              valueStyle={{ color: token.colorWarning, fontWeight: "bold", fontSize: "28px" }}
              prefix={<WarningOutlined className="mr-2 text-amber-500" />}
            />
            <div className="text-xs text-orange-600 font-semibold mt-2">
              草案: {draftCount} 个 · 已下线: {offlineCount} 个
            </div>
          </div>
        </Col>
      </Row>

      {/* 2. 检索及操作 Form */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 mb-6">
        <Form layout="inline" className="flex flex-wrap gap-4 items-center w-full">
          <Form.Item label="专病包搜索" className="mb-0">
            <Input
              placeholder="请输入配置包名称或编码..."
              allowClear
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              prefix={<SearchOutlined className="text-slate-400" />}
              className="w-[280px] rounded-lg"
            />
          </Form.Item>
          <Form.Item className="ml-auto mb-0">
            <Button
              icon={<UploadOutlined aria-hidden="true" />}
              onClick={() => setOfflineImportModalVisible(true)}
              className="rounded-lg font-medium flex items-center gap-1"
            >
              导入离线包
            </Button>
          </Form.Item>
          <Form.Item className="mb-0">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalVisible(true)}
              className="rounded-lg font-medium bg-sky-600 border-sky-600 hover:bg-sky-700 hover:border-sky-700 flex items-center gap-1"
            >
              一键创建知识配置包草稿
            </Button>
          </Form.Item>
        </Form>
      </div>

      {/* 3. 配置包主台账列表 */}
      <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden mb-6">
        <Table
          columns={columns}
          dataSource={displayPackages}
          rowKey="packageId"
          pagination={{
            current: currentPage,
            pageSize: 10,
            total: totalPackagesCount,
            onChange: (page) => setCurrentPage(page),
            showTotal: (total) => `共 ${total} 个配置包版本`,
          }}
          className="medkernel-table"
        />
      </div>

      {/* ────────────────── Modal: 一键创建知识配置包草稿 ────────────────── */}
      <Modal
        title={
          <div className="flex items-center gap-2 text-sky-700 font-semibold text-lg border-b border-slate-100 pb-3">
            <PlusOutlined />
            <span>新建配置包草案</span>
          </div>
        }
        open={createModalVisible}
        onOk={handleCreatePackage}
        onCancel={() => setCreateModalVisible(false)}
        width={580}
        confirmLoading={createPackageMutation.isPending}
        destroyOnClose
        okText="提交创建草案"
        cancelText="取消"
        okButtonProps={{ className: "bg-sky-600 border-sky-600 hover:bg-sky-700" }}
      >
        <Form form={createForm} layout="vertical" className="mt-4">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="packageCode"
                label="配置包全局唯一编码 (Package Code)"
                rules={[{ required: true, message: "请输入配置包唯一识别编码" }]}
              >
                <Input placeholder="请输入全局唯一编码" className="rounded-lg" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="packageVersion"
                label="发布版本号 (Package Version)"
                rules={[{ required: true, message: "请输入版本号" }]}
              >
                <Input placeholder="请输入版本号" className="rounded-lg font-normal" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="name"
            label="配置包名称"
            rules={[{ required: true, message: "请输入配置包名称" }]}
          >
            <Input placeholder="请输入配置包名称" className="rounded-lg" />
          </Form.Item>

          <Form.Item name="description" label="包核心资产及发布范围描述">
            <TextArea
              rows={3}
              placeholder="请填写该配置包版本所包含的核心资产及本次发布计划摘要..."
              className="rounded-lg"
            />
          </Form.Item>

          <Alert
            message="安全审计约束规范"
            description="新创建的配置包版本默认置为 DRAFT (草案) 状态。只有加入并关联经过临床审核通过的医疗资产，方可触发物理通道投影发布。"
            type="info"
            showIcon
            className="rounded-lg border-sky-100 bg-sky-50 text-sky-900"
          />
        </Form>
      </Modal>

      {/* ────────────────── Modal: 导入离线配置包 ────────────────── */}
      <Modal
        title={
          <div className="flex items-center gap-2 text-cyan-700 font-semibold text-lg border-b border-slate-100 pb-3">
            <UploadOutlined />
            <span>导入离线包</span>
          </div>
        }
        open={offlineImportModalVisible}
        onOk={handleImportOfflinePackage}
        onCancel={closeOfflineImportModal}
        width={680}
        confirmLoading={importOfflinePackageMutation.isPending}
        destroyOnClose
        okText="导入并校验"
        cancelText="取消"
      >
        <div className="mt-4 flex flex-col gap-4">
          <Alert
            message="导入后保持草案状态"
            description="系统会先校验格式、租户和 payload 摘要，通过后生成本地草案；仍需按本院流程发布后才会生效。"
            type="info"
            showIcon
            className="rounded-lg border-cyan-100 bg-cyan-50 text-cyan-900"
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
                className="rounded-lg font-normal"
              />
            </Form.Item>
          </Form>
        </div>
      </Modal>

      {/* ────────────────── Drawer: 办理包内资产细项 ────────────────── */}
      <Drawer
        title={
          <div className="flex items-center gap-2 text-sky-700 font-semibold">
            <SettingOutlined />
            <span>医学核心资产细项配置管理台</span>
          </div>
        }
        width={920}
        onClose={() => {
          setDetailDrawerVisible(false);
          setSelectedPackageId(null);
        }}
        open={detailDrawerVisible}
        destroyOnClose
      >
        {selectedPackage && (
          <div className="flex flex-col gap-6">
            <Descriptions
              title={
                <div className="flex items-center gap-2 text-slate-800 text-sm font-semibold border-l-4 border-sky-500 pl-2">
                  <span>归属包元信息 facts</span>
                </div>
              }
              bordered
              column={3}
              size="small"
              className="bg-slate-50 p-4 rounded-xl border border-slate-200"
            >
              <Descriptions.Item label="包编码">
                <span className="font-normal text-xs font-semibold">
                  {selectedPackage.packageCode}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="版本号">
                <Tag color="purple">{selectedPackage.packageVersion}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="生命周期状态">
                <Badge
                  status={selectedPackage.status === "ACTIVE" ? "processing" : "default"}
                  text={selectedPackage.status}
                />
              </Descriptions.Item>
              <Descriptions.Item label="配置包名称" span={3}>
                <span className="font-medium text-slate-800">{selectedPackage.name}</span>
              </Descriptions.Item>
              <Descriptions.Item label="包描述说明" span={3}>
                <span className="text-xs text-slate-500">{selectedPackage.description}</span>
              </Descriptions.Item>
            </Descriptions>

            {/* 新增资产入包表单 (仅在 DRAFT 状态允许编辑修改) */}
            {selectedPackage.status === "DRAFT" ? (
              <Card
                title={
                  <div className="flex items-center gap-2 text-sky-600 font-semibold text-xs">
                    <PlusOutlined />
                    <span>物理追加临床资产细项入包</span>
                  </div>
                }
                size="small"
                className="rounded-xl border-sky-100 bg-sky-50/20"
              >
                <Form form={itemForm} layout="vertical" onFinish={handleAddItem}>
                  <Row gutter={12}>
                    <Col span={6}>
                      <Form.Item
                        name="assetType"
                        label="资产类型"
                        rules={[{ required: true }]}
                        initialValue="RULE"
                      >
                        <Select
                          onChange={(val) => setSelectedAssetType(val)}
                          className="rounded-lg"
                        >
                          <Option value="RULE">规则引擎 (RULE)</Option>
                          <Option value="PATHWAY">临床路径 (PATHWAY)</Option>
                          <Option value="EVALUATION">质控评估指标 (EVALUATION)</Option>
                          <Option value="TERMINOLOGY">术语字典映射 (TERMINOLOGY)</Option>
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item
                        name="assetId"
                        label="选择已发布的临床资产"
                        rules={[{ required: true, message: "请选择有效资产" }]}
                      >
                        <Select
                          placeholder="请选择已发布资产"
                          showSearch
                          allowClear
                          className="rounded-lg"
                        >
                          {selectedAssetType === "RULE" &&
                            (activeRules?.items || []).map((r: RuleDefinition) => (
                              <Option key={r.ruleId} value={r.ruleId}>
                                {r.name} ({r.ruleId})
                              </Option>
                            ))}

                          {selectedAssetType === "PATHWAY" &&
                            (activePathways?.items || []).map((p: PathwayTemplate) => (
                              <Option key={p.templateId} value={p.templateId}>
                                {p.name} ({p.templateId})
                              </Option>
                            ))}

                          {selectedAssetType === "EVALUATION" &&
                            (activeEvaluations?.items || []).map((e: EvaluationIndicator) => (
                              <Option key={e.indicatorId} value={e.indicatorId}>
                                {e.name} ({e.indicatorId})
                              </Option>
                            ))}

                          {selectedAssetType === "TERMINOLOGY" &&
                            (activeTerminologies?.items || []).map((t: TermMapping) => (
                              <Option
                                key={t.localTermId || t.id}
                                value={`term-map-${t.localTermId || t.id}`}
                              >
                                {t.category} (ID: {t.localTermId || t.id})
                              </Option>
                            ))}
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={6}>
                      <Form.Item
                        name="assetVersion"
                        label="资产快照版本"
                        rules={[{ required: true }]}
                      >
                        <Input
                          placeholder="请输入资产快照版本"
                          className="rounded-lg font-normal"
                        />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={addPackageItemMutation.isPending}
                    className="w-full bg-sky-600 border-sky-600 hover:bg-sky-700 rounded-lg font-semibold"
                  >
                    确认将此资产关联加入当前包草稿
                  </Button>
                </Form>
              </Card>
            ) : (
              <Alert
                message="编辑权限受限说明"
                description="当前配置包已处于发布/激活生命周期中，资产条目已物理锁存（Read-Only），无法进行追加或删除修改。如需修改资产，请创建新的配置包草稿版本。"
                type="warning"
                showIcon
                className="rounded-lg"
              />
            )}

            {/* 配置资产细项列表 Table */}
            <Card
              title={
                <div className="font-semibold text-slate-700">
                  配置包包含的核心资产条目 ({currentItems.length})
                </div>
              }
              className="rounded-xl"
            >
              <Table
                dataSource={currentItems}
                rowKey="itemId"
                size="small"
                pagination={false}
                columns={[
                  {
                    title: "资产细项 ID",
                    dataIndex: "itemId",
                    key: "itemId",
                    render: (text: string) => (
                      <span className="font-normal text-[10px] text-slate-400">{text}</span>
                    ),
                  },
                  {
                    title: "资产类型",
                    dataIndex: "assetType",
                    key: "assetType",
                    render: (type: string) => {
                      const colors: Record<string, string> = {
                        RULE: "blue",
                        PATHWAY: "purple",
                        EVALUATION: "cyan",
                        TERMINOLOGY: "orange",
                      };
                      return <Tag color={colors[type] || "default"}>{type}</Tag>;
                    },
                  },
                  {
                    title: "关联资产 ID",
                    dataIndex: "assetId",
                    key: "assetId",
                    className: "font-normal font-semibold text-slate-700",
                  },
                  {
                    title: "引入资产版本",
                    dataIndex: "assetVersion",
                    key: "assetVersion",
                    render: (v: string) => <Tag className="font-normal text-[10px]">{v}</Tag>,
                  },
                  {
                    title: "加入时间 / 操作人",
                    key: "time",
                    render: (_: unknown, record: PackageItem) => (
                      <span className="text-[11px] text-slate-400">
                        {record.createdBy} · {new Date(record.createdAt).toLocaleDateString()}
                      </span>
                    ),
                  },
                  {
                    title: "操作",
                    key: "action",
                    render: (_: unknown, record: PackageItem) => (
                      <span className="text-xs text-slate-400" title={record.itemId}>
                        已记录
                      </span>
                    ),
                  },
                ]}
              />
            </Card>
          </div>
        )}
      </Drawer>

      {/* ────────────────── Modal: 变动差异与临床影响比对 ────────────────── */}
      <Modal
        title={
          <div className="flex items-center gap-2 text-indigo-700 font-semibold text-lg border-b border-slate-100 pb-3">
            <AuditOutlined />
            <span>配置包多版本变动差异与临床影响分析</span>
          </div>
        }
        open={diffModalVisible}
        onCancel={() => {
          setDiffModalVisible(false);
          setBasePackageIdForDiff(undefined);
        }}
        width={720}
        footer={null}
        destroyOnClose
      >
        <div className="mt-4 flex flex-col gap-6">
          <div className="bg-indigo-50/50 p-4 rounded-xl border border-indigo-100 flex flex-wrap gap-4 items-center justify-between">
            <div className="font-medium text-indigo-900">
              当前比对目标版本:{" "}
              <Tag color="indigo" className="font-normal">
                {selectedPackage?.packageVersion || "未选择"}
              </Tag>
            </div>
            <div>
              <span className="text-slate-500 mr-2">基准比对版本:</span>
              <Select
                placeholder="请选择基准对比版本"
                value={basePackageIdForDiff}
                onChange={(val) => setBasePackageIdForDiff(val)}
                className="w-[260px] rounded-lg"
              >
                {displayPackages
                  .filter((p) => p.packageId !== selectedPackageId)
                  .map((p) => (
                    <Option key={p.packageId} value={p.packageId}>
                      {p.name} ({p.packageVersion})
                    </Option>
                  ))}
              </Select>
            </div>
            <Button
              icon={<DownloadOutlined />}
              disabled={!apiDiffData}
              loading={diffExporting}
              onClick={handleExportDiffEvidence}
            >
              导出影响证据
            </Button>
          </div>

          {apiDiffData ? (
            <div className="flex flex-col gap-6">
              {/* 比对数据 KPI Row */}
              <Row gutter={16}>
                <Col span={8}>
                  <div className="bg-slate-50 border border-slate-200 p-4 rounded-xl text-center">
                    <div className="text-xs text-slate-500 font-medium mb-1">新增引入资产</div>
                    <div className="text-2xl font-bold font-normal text-emerald-600">
                      +{apiDiffData.addedCount}
                    </div>
                  </div>
                </Col>
                <Col span={8}>
                  <div className="bg-slate-50 border border-slate-200 p-4 rounded-xl text-center">
                    <div className="text-xs text-slate-500 font-medium mb-1">升级改动资产</div>
                    <div className="text-2xl font-bold font-normal text-indigo-600">
                      {apiDiffData.updatedCount}
                    </div>
                  </div>
                </Col>
                <Col span={8}>
                  <div className="bg-slate-50 border border-slate-200 p-4 rounded-xl text-center">
                    <div className="text-xs text-slate-500 font-medium mb-1">废弃移除资产</div>
                    <div className="text-2xl font-bold font-normal text-rose-600">
                      -{apiDiffData.removedCount}
                    </div>
                  </div>
                </Col>
              </Row>

              {/* 临床受影响责任科室分析 */}
              <Card
                title={
                  <div className="flex items-center gap-2 text-indigo-700 text-xs font-semibold">
                    <CompassOutlined />
                    <span>临床责任受影响科室深度分析 (PDCA 追溯)</span>
                  </div>
                }
                className="rounded-xl border-indigo-100"
              >
                <div className="flex flex-col gap-3">
                  <div className="text-xs text-slate-500 leading-relaxed">
                    依据该配置包中“规则/路径/指标”的绑定属性，系统智能计算出本次发布物理投影后，将直接或间接影响以下临床专科诊疗路径动作及指标核查：
                  </div>
                  <div className="flex flex-wrap gap-2 mt-1">
                    {(apiDiffData.affectedDepartments ?? []).map((dept: string) => (
                      <Tag color="geekblue" key={dept} className="py-1 px-3 rounded-md font-medium">
                        {dept}
                      </Tag>
                    ))}
                  </div>
                </div>
              </Card>

              {/* 可信溯源安全审计凭证 */}
              <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 flex items-center justify-between text-xs text-slate-500">
                <span className="flex items-center gap-1 font-medium text-slate-600">
                  <AuditOutlined /> 审计诊断解释追踪凭证 traceId
                </span>
                <span className="font-normal bg-slate-200 px-2 py-0.5 rounded font-semibold">
                  {selectedPackage?.traceId || "后端未返回审计追踪号"}
                </span>
              </div>
            </div>
          ) : (
            <div className="text-center py-16 text-slate-400">
              <InfoCircleOutlined className="text-lg mb-2" />
              <div>请在上方下拉框中选择一个基准配置包，以自动触发多版本临床影响差异比对。</div>
            </div>
          )}
        </div>
      </Modal>

      {/* ────────────────── Modal: 物理投影发布同步中心 ────────────────── */}
      <Modal
        title={
          <div className="flex items-center gap-2 text-emerald-700 font-semibold text-lg border-b border-slate-100 pb-3">
            <CloudSyncOutlined />
            <span>物理投影与多通道同步发布中心</span>
          </div>
        }
        open={syncModalVisible}
        onCancel={() => {
          setSyncModalVisible(false);
          setSyncLogs([]);
          setSyncProgress(0);
          syncForm.resetFields();
        }}
        width={780}
        footer={null}
        destroyOnClose
      >
        <Form form={syncForm} layout="vertical" className="mt-4" onFinish={handleSyncPackage}>
          {/* 基本物理 facts 对照 */}
          <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 flex items-center justify-between mb-4 text-xs font-semibold text-slate-600">
            <div>包名称: {selectedPackage?.name}</div>
            <div>
              发布版本: <Tag color="purple">{selectedPackage?.packageVersion}</Tag>
            </div>
          </div>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="strategy"
                label="发布投放策略"
                rules={[{ required: true, message: "请选择同步发布策略" }]}
                initialValue="FULL"
              >
                <Radio.Group className="w-full">
                  <Radio.Button
                    value="FULL"
                    className="w-1/2 text-center py-1.5 h-auto font-semibold"
                  >
                    全量发布 (FULL)
                  </Radio.Button>
                  <Radio.Button
                    value="GRAYSCALE"
                    className="w-1/2 text-center py-1.5 h-auto font-semibold"
                  >
                    灰度发布 (GRAYSCALE)
                  </Radio.Button>
                </Radio.Group>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="targetIds"
                label="选择物理同步投影通道目标"
                rules={[{ required: true, message: "请至少选择一个同步目标" }]}
              >
                <Select mode="multiple" placeholder="请选择同步目标通道" className="rounded-lg">
                  {displayTargets.map((t) => (
                    <Option key={t.targetId} value={t.targetId}>
                      {t.targetName}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          {/* 灰度发布下的物理约束和门禁字段 */}
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.strategy !== curr.strategy}>
            {({ getFieldValue }) =>
              getFieldValue("strategy") === "GRAYSCALE" ? (
                <Card
                  title={
                    <div className="text-xs font-semibold text-amber-700 flex items-center gap-1">
                      <WarningOutlined />
                      <span>物理作用域灰度拦截门禁</span>
                    </div>
                  }
                  size="small"
                  className="bg-amber-50/20 border-amber-100 mb-4 rounded-xl"
                >
                  <Row gutter={12}>
                    <Col span={12}>
                      <Form.Item
                        name="scopeType"
                        label="灰度物理作用域级别"
                        rules={[{ required: true, message: "灰度发布必须选择物理作用域" }]}
                        initialValue="DEPARTMENT"
                      >
                        <Select className="rounded-lg">
                          <Option value="CAMPUS">院区级别 (CAMPUS)</Option>
                          <Option value="SITE">院区站点 (SITE)</Option>
                          <Option value="DEPARTMENT">临床科室级 (DEPARTMENT)</Option>
                          <Option value="SPECIALTY">专病维度 (SPECIALTY)</Option>
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item
                        name="scopeValue"
                        label="物理过滤匹配值 (指定科室/院区ID)"
                        rules={[{ required: true, message: "灰度匹配过滤值不能为空" }]}
                      >
                        <Input
                          placeholder="请输入真实组织单元 ID 或组织编码"
                          className="rounded-lg font-normal"
                        />
                      </Form.Item>
                    </Col>
                  </Row>
                </Card>
              ) : null
            }
          </Form.Item>

          <Form.Item
            name="targetOrgUnitId"
            label="接收组织单元"
            rules={[{ required: true, message: "请输入接收组织单元 ID" }]}
          >
            <Input placeholder="请输入真实组织单元 ID" className="rounded-lg font-normal" />
          </Form.Item>

          {/* 安全说明 Alert */}
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.strategy !== curr.strategy}>
            {({ getFieldValue }) => {
              const isFull = getFieldValue("strategy") === "FULL";
              return (
                <Alert
                  message={isFull ? "【强安全提醒】全量发布" : "灰度发布策略"}
                  description={
                    isFull
                      ? "全量物理同步投影成功后，当前配置包将被彻底激活为 ACTIVE 运行实体。同编码（packageCode）的旧版 ACTIVE 包将被原子降级 OFFLINE 隔离，从而平滑安全切换决策树。"
                      : "灰度同步投影仅会将配置包封存发布为已就绪(PUBLISHED)状态，不覆盖主运行流。只有当患者的临床事实命中上方指定的灰度物理过滤值时，才会小流量执行该配置包规则。"
                  }
                  type={isFull ? "error" : "info"}
                  showIcon
                  className="mb-4 rounded-lg"
                />
              );
            }}
          </Form.Item>

          {/* 实时同步执行进度 & 证据链渲染 */}
          {(syncExecuting || syncProgress > 0) && (
            <div className="mb-6 bg-slate-50 p-4 rounded-xl border border-slate-200">
              <div className="flex justify-between items-center mb-2">
                <span className="font-semibold text-slate-700 text-xs">同步发布执行进度:</span>
                <span className="font-normal text-xs font-bold text-sky-600">{syncProgress}%</span>
              </div>
              <Progress
                percent={syncProgress}
                status={syncExecuting ? "active" : "normal"}
                strokeColor={{ "0%": token.colorPrimary, "100%": token.colorSuccess }}
                className="mb-4"
              />

              {/* 实时同步证据 Timeline */}
              {syncLogs.length > 0 && (
                <div className="mt-4">
                  <div className="font-semibold text-slate-700 text-xs mb-3 flex items-center gap-1">
                    <FileProtectOutlined className="text-emerald-600" />
                    <span>多通道物理发布存证证据链 (Evidence Trace Chain)</span>
                  </div>
                  <Timeline className="ml-2">
                    {syncLogs.map((log) => (
                      <Timeline.Item
                        key={log.logId}
                        color={log.status === "SUCCESS" ? "green" : "red"}
                      >
                        <div className="bg-white p-3 rounded-lg border border-slate-100 shadow-sm flex flex-col gap-1.5">
                          <div className="flex justify-between items-center text-xs font-semibold">
                            <span className="text-slate-800">
                              通道:{" "}
                              {displayTargets.find((t) => t.targetId === log.targetId)
                                ?.targetName || log.targetId}
                            </span>
                            <Tag color="green" className="m-0 text-[10px]">
                              {log.status}
                            </Tag>
                          </div>
                          {log.syncEvidence && (
                            <div className="flex items-center justify-between gap-4 mt-1 bg-slate-50 p-2 rounded border border-slate-200">
                              <span className="text-[10px] text-slate-400 font-normal flex items-center gap-1">
                                <AuditOutlined /> 物理存证证据:
                              </span>
                              <span className="font-normal text-[10px] text-emerald-700 font-bold break-all bg-emerald-50 px-1 py-0.5 rounded">
                                {log.syncEvidence}
                              </span>
                            </div>
                          )}
                        </div>
                      </Timeline.Item>
                    ))}
                  </Timeline>
                </div>
              )}
            </div>
          )}

          <Button
            type="primary"
            htmlType="submit"
            loading={syncExecuting}
            icon={<CloudSyncOutlined />}
            className="w-full bg-emerald-600 border-emerald-600 hover:bg-emerald-700 rounded-lg py-5 font-semibold text-center flex items-center justify-center gap-1 mt-2"
          >
            {syncExecuting
              ? "正在物理长链接投影及写入证据存证..."
              : "开始发起物理投影多通道同步发布"}
          </Button>
        </Form>
      </Modal>

      {/* ────────────────── Modal: 版本一键安全回滚 ────────────────── */}
      <Modal
        title={
          <div className="flex items-center gap-2 text-rose-700 font-semibold text-lg border-b border-slate-100 pb-3">
            <HistoryOutlined />
            <span>高危决策：配置包版本安全回滚</span>
          </div>
        }
        open={rollbackModalVisible}
        onCancel={closeRollbackModal}
        width={680}
        footer={null}
        destroyOnClose
      >
        {selectedPackage && (
          <div className="mt-4 flex flex-col gap-6">
            <Alert
              message="高危操作警告"
              description="版本回滚会下线当前执行中 (ACTIVE) 版本，并启用指定历史版本。此动作可能直接影响临床在用诊断流程，请务必核实并由专家确认。"
              type="error"
              showIcon
              className="rounded-lg font-medium"
            />

            {/* 回滚对照面板 */}
            <div className="bg-slate-50 p-4 rounded-xl border border-slate-200">
              <Row gutter={16} align="middle">
                <Col span={10} className="text-center">
                  <div className="text-xs text-slate-400 font-medium mb-1">当前执行版本</div>
                  <Tag color="red" className="font-normal py-1 px-3 text-sm font-bold">
                    {selectedPackage.packageVersion}
                  </Tag>
                  <div className="text-xs text-slate-500 mt-2 font-medium">
                    {selectedPackage.name}
                  </div>
                </Col>
                <Col span={4} className="text-center text-slate-300">
                  <ArrowRightOutlined className="text-2xl text-slate-400 animate-pulse" />
                </Col>
                <Col span={10} className="text-center">
                  <div className="text-xs text-slate-400 font-medium mb-1">安全原子回退至</div>
                  <Tag color="green" className="font-normal py-1 px-3 text-sm font-bold">
                    历史版本点
                  </Tag>
                  <div className="text-xs text-slate-500 mt-2 font-medium">
                    请在下方选择同一配置包编码下曾经执行并已下线的历史版本
                  </div>
                </Col>
              </Row>
            </div>

            <div className="bg-white p-4 rounded-xl border border-rose-100">
              <Form layout="vertical">
                <Form.Item label="回滚原因（写入审计）" required className="mb-3">
                  <TextArea
                    rows={3}
                    maxLength={500}
                    showCount
                    value={rollbackReason}
                    onChange={(event) => setRollbackReason(event.target.value)}
                    placeholder="请填写临床专家确认、回滚窗口、影响范围或故障原因。"
                    className="rounded-lg"
                  />
                </Form.Item>
                <Checkbox
                  checked={rollbackConfirmed}
                  onChange={(event) => setRollbackConfirmed(event.target.checked)}
                >
                  我已核对当前版本与目标版本，并确认该回滚会影响临床在用流程。
                </Checkbox>
              </Form>
            </div>

            {/* 历史版本列表表格 (排除当前选中的 ACTIVE 版本) */}
            <Card
              title={
                <div className="font-semibold text-slate-700 text-xs">
                  可供回退激活的已下线历史版本库
                </div>
              }
              size="small"
              className="rounded-xl"
            >
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
                    render: (text: string) => (
                      <Tag color="purple" className="font-normal font-semibold">
                        {text}
                      </Tag>
                    ),
                  },
                  {
                    title: "包名称",
                    dataIndex: "name",
                    key: "name",
                  },
                  {
                    title: "历史状态",
                    dataIndex: "status",
                    key: "status",
                    render: () => <Tag color="default">已下线 (OFFLINE)</Tag>,
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
                          type="primary"
                          danger
                          size="small"
                          icon={<HistoryOutlined />}
                          disabled={rollbackActionDisabled}
                          loading={rollbackPackageMutation.isPending}
                          className="rounded-md text-xs font-semibold"
                        >
                          确认回滚
                        </Button>
                      </Popconfirm>
                    ),
                  },
                ]}
              />
            </Card>
          </div>
        )}
      </Modal>
    </PageShell>
  );
}
