import { useState } from "react";
import {
  App as AntdApp,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  AppstoreAddOutlined,
  EditOutlined,
  StarFilled,
  StarOutlined,
} from "@ant-design/icons";

import {
  useAuthoringAssets,
  useFavoriteAuthoringAsset,
  useSecurityProfile,
  useUnfavoriteAuthoringAsset,
  useUpdateAuthoringAssetProfile,
} from "@/shared/api/hooks";
import type { AuthoringAssetLibraryItem, EngineAssetType } from "@/shared/api/hooks";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { PageShell } from "@/shared/ui/PageShell";
import FieldCatalogManager from "@/shared/ui/condition/FieldCatalogManager";
import AuthoringBatchDrawer from "./AuthoringBatchDrawer";
import DeclarativeAssetWorkbench from "./DeclarativeAssetWorkbench";
import styles from "./AuthoringAssets.module.css";

const { Option } = Select;
const { Text } = Typography;

const assetTypeOptions: Array<{ value: EngineAssetType | "ALL"; label: string }> = [
  { value: "ALL", label: "全部资产" },
  { value: "RULE", label: "规则" },
  { value: "PATHWAY", label: "路径" },
  { value: "FOLLOWUP", label: "随访模板" },
];

const assetTypeLabels: Record<string, string> = {
  RULE: "规则",
  PATHWAY: "路径",
  FOLLOWUP: "随访模板",
};

function assetTypeColor(type: string) {
  const colors: Record<string, string> = {
    RULE: "blue",
    PATHWAY: "purple",
    FOLLOWUP: "cyan",
  };
  return colors[type] || "default";
}

function statusColor(status: string) {
  if (status === "PUBLISHED" || status === "ACTIVE") return "success";
  if (status === "DRAFT") return "default";
  return "warning";
}

function splitTags(value: string | undefined) {
  return Array.from(
    new Set(
      (value ?? "")
        .split(/[,，\s]+/)
        .map((tag) => tag.trim())
        .filter(Boolean),
    ),
  );
}

function canWriteAssets(profile: ReturnType<typeof useSecurityProfile>["data"]) {
  const permissions = new Set(profile?.permissions.map((permission) => permission.code) ?? []);
  return (
    permissions.has("rule.write") ||
    permissions.has("pathway.write") ||
    permissions.has("followup.write")
  );
}

function hasPermission(
  profile: ReturnType<typeof useSecurityProfile>["data"],
  permission: string,
) {
  return profile?.permissions.some((item) => item.code === permission) ?? false;
}

export default function AuthoringAssets() {
  const { message } = AntdApp.useApp();
  const security = useSecurityProfile();
  const canWrite = canWriteAssets(security.data);
  const canWriteDeclarative = hasPermission(security.data, "asset.write");
  const canWriteFields = hasPermission(security.data, "context.write");
  const [assetType, setAssetType] = useState<EngineAssetType | "ALL">("ALL");
  const [keyword, setKeyword] = useState("");
  const [tag, setTag] = useState("");
  const [favoriteOnly, setFavoriteOnly] = useState(false);
  const [profileAsset, setProfileAsset] = useState<AuthoringAssetLibraryItem | null>(null);
  const [batchOpen, setBatchOpen] = useState(false);
  const [fieldCatalogOpen, setFieldCatalogOpen] = useState(false);
  const [profileForm] = Form.useForm<{ category: string; tags: string }>();

  const assetsQuery = useAuthoringAssets(
    {
      ...(assetType === "ALL" ? {} : { assetType }),
      ...(keyword.trim() ? { keyword: keyword.trim() } : {}),
      ...(tag.trim() ? { tag: tag.trim() } : {}),
      ...(favoriteOnly ? { favoriteOnly: true } : {}),
      size: 50,
    },
    { enabled: true },
  );
  const updateProfileMutation = useUpdateAuthoringAssetProfile();
  const favoriteMutation = useFavoriteAuthoringAsset();
  const unfavoriteMutation = useUnfavoriteAuthoringAsset();
  const assets = assetsQuery.data?.items ?? [];

  const openProfileModal = (asset: AuthoringAssetLibraryItem) => {
    setProfileAsset(asset);
    profileForm.setFieldsValue({
      category: asset.category ?? "",
      tags: asset.tags.join(", "),
    });
  };

  const saveProfile = async () => {
    if (!profileAsset) return;
    const values = await profileForm.validateFields();
    await updateProfileMutation.mutateAsync({
      assetType: profileAsset.assetType,
      assetId: profileAsset.assetId,
      request: {
        category: values.category?.trim() || null,
        tags: splitTags(values.tags),
      },
    });
    message.success("资产标签已更新");
    setProfileAsset(null);
  };

  const toggleFavorite = async (asset: AuthoringAssetLibraryItem) => {
    if (asset.favorite) {
      await unfavoriteMutation.mutateAsync({ assetType: asset.assetType, assetId: asset.assetId });
      message.success("已取消收藏");
      return;
    }
    await favoriteMutation.mutateAsync({ assetType: asset.assetType, assetId: asset.assetId });
    message.success("已收藏");
  };

  const columns: ColumnsType<AuthoringAssetLibraryItem> = [
    {
      title: "资产",
      dataIndex: "name",
      key: "name",
      render: (_value, asset) => (
        <div className={styles.assetName}>
          <Text strong>{asset.name}</Text>
          <Text type="secondary" className={styles.codeText}>
            {asset.assetCode}
          </Text>
        </div>
      ),
    },
    {
      title: "类型",
      dataIndex: "assetType",
      key: "assetType",
      render: (type: string) => (
        <Tag color={assetTypeColor(type)}>{assetTypeLabels[type] ?? customerEnumLabel(type)}</Tag>
      ),
    },
    {
      title: "分类与标签",
      key: "tags",
      render: (_value, asset) => (
        <Space size="small" wrap>
          {asset.category && <Tag>{asset.category}</Tag>}
          {asset.tags.map((item) => (
            <Tag key={item}>{item}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: "版本",
      dataIndex: "version",
      key: "version",
      render: (version: string) => <Tag>{version}</Tag>,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: string) => (
        <Tag color={statusColor(status)}>{customerEnumLabel(status)}</Tag>
      ),
    },
    {
      title: "操作",
      key: "actions",
      render: (_value, asset) => (
        <Space size="small" wrap>
          <Button
            icon={<EditOutlined />}
            aria-label="编辑标签"
            disabled={!canWrite}
            onClick={() => openProfileModal(asset)}
          >
            编辑标签
          </Button>
          <Button
            icon={asset.favorite ? <StarFilled /> : <StarOutlined />}
            aria-label={asset.favorite ? "取消收藏" : "收藏"}
            disabled={!canWrite}
            onClick={() => toggleFavorite(asset)}
          >
            {asset.favorite ? "取消收藏" : "收藏"}
          </Button>
        </Space>
      ),
    },
  ];

  if (assetsQuery.isLoading) {
    return (
      <PageShell
        title="统一资产库"
        description="检索、收藏和复用创作资产"
        state="loading"
        stateProps={{ title: "正在加载统一资产库", description: "正在读取规则、路径和随访模板。" }}
      >
        <></>
      </PageShell>
    );
  }

  if (assetsQuery.isError) {
    return (
      <PageShell
        title="统一资产库"
        description="检索、收藏和复用创作资产"
        state="error"
        stateProps={{
          title: "统一资产库读取失败",
          description: "请重试；若持续失败，请凭追踪号联系信息科排查创作资产服务。",
          onRetry: () => assetsQuery.refetch(),
        }}
      >
        <></>
      </PageShell>
    );
  }

  return (
    <PageShell title="统一资产库" description="检索、收藏、维护和复用全部引擎资产">
      <Tabs
        defaultActiveKey="library"
        items={[
          {
            key: "library",
            label: "专业资产库",
            children: (
              <Space direction="vertical" size="middle" className="mk-full-width">
                <Space wrap className={styles.filterBar}>
          <Select
            value={assetType}
            onChange={setAssetType}
            className={styles.assetTypeSelect}
            optionFilterProp="label"
          >
            {assetTypeOptions.map((option) => (
              <Option key={option.value} value={option.value} label={option.label}>
                {option.label}
              </Option>
            ))}
          </Select>
          <Input
            allowClear
            placeholder="搜索资产编码或名称"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            className={styles.keywordInput}
          />
          <Input
            allowClear
            placeholder="标签"
            value={tag}
            onChange={(event) => setTag(event.target.value)}
            className={styles.tagInput}
          />
          <Checkbox
            checked={favoriteOnly}
            onChange={(event) => setFavoriteOnly(event.target.checked)}
          >
            仅收藏
          </Checkbox>
          <Button
            icon={<AppstoreAddOutlined />}
            disabled={!canWrite}
            onClick={() => setBatchOpen(true)}
          >
            批量处理
          </Button>
                </Space>
                <Table
                  rowKey={(asset) => `${asset.assetType}-${asset.assetId}-${asset.version}`}
                  dataSource={assets}
                  columns={columns}
                  pagination={{ pageSize: 10, showSizeChanger: false }}
                  size="middle"
                />
              </Space>
            ),
          },
          {
            key: "configuration",
            label: "配置资产维护",
            children: (
              <Space direction="vertical" size="middle" className="mk-full-width">
                <Button disabled={!canWriteFields} onClick={() => setFieldCatalogOpen(true)}>
                  维护字段目录
                </Button>
                <DeclarativeAssetWorkbench canWrite={canWriteDeclarative} />
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title="编辑资产标签"
        open={Boolean(profileAsset)}
        onCancel={() => setProfileAsset(null)}
        onOk={saveProfile}
        okText="保存"
        cancelText="取消"
        okButtonProps={{ "aria-label": "保存" }}
        confirmLoading={updateProfileMutation.isPending}
        destroyOnClose
      >
        <Form form={profileForm} layout="vertical">
          <Form.Item name="category" label="分类">
            <Input />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Input placeholder="多个标签用逗号分隔" />
          </Form.Item>
        </Form>
      </Modal>

      {batchOpen && (
        <AuthoringBatchDrawer open canWrite={canWrite} onClose={() => setBatchOpen(false)} />
      )}
      <FieldCatalogManager
        open={fieldCatalogOpen}
        onClose={() => setFieldCatalogOpen(false)}
      />
    </PageShell>
  );
}
