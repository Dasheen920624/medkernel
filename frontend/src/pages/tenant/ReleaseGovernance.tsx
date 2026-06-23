import { ReloadOutlined, RocketOutlined, RollbackOutlined } from "@ant-design/icons";
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import { useEffect, useMemo, useRef, useState } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useActivateHospitalRuntime,
  useCurrentHospitalRuntime,
  useCurrentPlatformBaseline,
  useHospitalRuntimeCandidates,
  useHospitalRuntimeHistory,
  useOrgUnits,
  usePlatformReleaseCandidates,
  usePublishPlatformBaseline,
  useRollbackHospitalRuntime,
  type ClinicalRuntimeAssetSelection,
  type ClinicalRuntimeRelease,
  type PlatformBaselineItem,
  type ReleaseCandidateAsset,
  type ReleaseAssetRef,
  type RuntimeAssetType,
} from "@/shared/api/hooks";
import { ENGINE_ASSET_LABELS, RUNTIME_ASSET_OPTIONS } from "@/shared/config/assetCatalog";
import { PageShell } from "@/shared/ui/PageShell";
import styles from "./ReleaseGovernance.module.css";

const { Text, Title } = Typography;

function assetKey(assetType: RuntimeAssetType, assetIdentity: string) {
  return `${assetType}|${assetIdentity}`;
}

function shortHash(value: string | null | undefined) {
  return value ? `${value.slice(0, 12)}…` : "—";
}

function revision(prefix: "A" | "H", value: number | null | undefined) {
  return value ? `${prefix}${value}` : "尚未建立";
}

function stateTag(state: string) {
  let label = "已发布";
  if (state === "ACTIVE") label = "启用";
  if (state === "DISABLED") label = "停用";
  if (state === "DRAFT") label = "草稿";
  return (
    <Tag color={state === "ACTIVE" || state === "PUBLISHED" ? "success" : "default"}>{label}</Tag>
  );
}

export default function ReleaseGovernance() {
  const { message } = AntdApp.useApp();
  const [activeTab, setActiveTab] = useState("platform");
  const [assetType, setAssetType] = useState<RuntimeAssetType>();
  const [keyword, setKeyword] = useState("");
  const [hospitalKeyword, setHospitalKeyword] = useState("");
  const [hospitalId, setHospitalId] = useState<string>();
  const [platformPublishIds, setPlatformPublishIds] = useState<string[]>([]);
  const [platformDisabled, setPlatformDisabled] = useState<ReleaseAssetRef[]>([]);
  const [hospitalSelections, setHospitalSelections] = useState<
    Map<string, ClinicalRuntimeAssetSelection>
  >(new Map());
  const initializedHospitalRevision = useRef<string>();

  const hospitalsQuery = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    keyword: hospitalKeyword || undefined,
    level: "FACILITY",
    status: "ACTIVE",
  });
  const baselineQuery = useCurrentPlatformBaseline();
  const platformCandidatesQuery = usePlatformReleaseCandidates({
    assetType,
    keyword: keyword || undefined,
    page: 1,
    size: 50,
  });
  const runtimeQuery = useCurrentHospitalRuntime(hospitalId);
  const localCandidatesQuery = useHospitalRuntimeCandidates(hospitalId, {
    assetType,
    keyword: keyword || undefined,
    page: 1,
    size: 50,
  });
  const historyQuery = useHospitalRuntimeHistory(hospitalId, {
    page: 1,
    size: 50,
    sort: "revisionNo,desc",
  });
  const publishPlatform = usePublishPlatformBaseline();
  const activateHospital = useActivateHospitalRuntime();
  const rollbackHospital = useRollbackHospitalRuntime();

  const baseline = baselineQuery.data;
  const platformCandidates = platformCandidatesQuery.data?.items ?? [];
  const localCandidates = localCandidatesQuery.data?.items ?? [];
  const currentRuntime = runtimeQuery.data;
  const history = historyQuery.data?.items ?? [];

  const hospitals = useMemo(
    () =>
      (hospitalsQuery.data?.items ?? [])
        .filter((item) => item.id && item.facilityType === "HOSPITAL")
        .map((item) => ({
          value: item.id as string,
          label: `${item.name} · ${item.code}`,
        })),
    [hospitalsQuery.data?.items],
  );

  const activeBaselineItems = useMemo(
    () => (baseline?.items ?? []).filter((item) => item.entryState === "ACTIVE"),
    [baseline?.items],
  );

  useEffect(() => {
    if (!hospitalId) {
      initializedHospitalRevision.current = undefined;
      return;
    }
    const initializationKey = `${hospitalId}|${currentRuntime?.release.releaseId ?? "new"}`;
    if (initializedHospitalRevision.current === initializationKey) return;
    const next = new Map<string, ClinicalRuntimeAssetSelection>();
    if (currentRuntime) {
      for (const item of currentRuntime.items) {
        if (item.entryState !== "ACTIVE") continue;
        next.set(assetKey(item.assetType, item.assetIdentity), {
          assetType: item.assetType,
          assetIdentity: item.assetIdentity,
          versionId: item.sourceLayer === "PLATFORM" ? null : item.versionId,
        });
      }
    } else {
      for (const item of activeBaselineItems) {
        next.set(assetKey(item.assetType, item.assetIdentity), {
          assetType: item.assetType,
          assetIdentity: item.assetIdentity,
          versionId: null,
        });
      }
    }
    initializedHospitalRevision.current = initializationKey;
    setHospitalSelections(next);
  }, [activeBaselineItems, currentRuntime, hospitalId]);

  function togglePlatformCandidate(candidate: ReleaseCandidateAsset, checked: boolean) {
    setPlatformPublishIds((current) =>
      checked
        ? [...current.filter((value) => value !== candidate.versionId), candidate.versionId]
        : current.filter((value) => value !== candidate.versionId),
    );
    if (checked) {
      setPlatformDisabled((current) =>
        current.filter(
          (item) =>
            assetKey(item.assetType, item.assetIdentity) !==
            assetKey(candidate.assetType, candidate.assetIdentity),
        ),
      );
    }
  }

  function togglePlatformDisabled(item: PlatformBaselineItem, checked: boolean) {
    const ref = { assetType: item.assetType, assetIdentity: item.assetIdentity };
    setPlatformDisabled((current) =>
      checked
        ? [
            ...current.filter(
              (value) =>
                assetKey(value.assetType, value.assetIdentity) !==
                assetKey(item.assetType, item.assetIdentity),
            ),
            ref,
          ]
        : current.filter(
            (value) =>
              assetKey(value.assetType, value.assetIdentity) !==
              assetKey(item.assetType, item.assetIdentity),
          ),
    );
    if (checked) {
      const replacement = platformCandidates.find(
        (candidate) =>
          candidate.assetType === item.assetType && candidate.assetIdentity === item.assetIdentity,
      );
      if (replacement) {
        setPlatformPublishIds((current) =>
          current.filter((value) => value !== replacement.versionId),
        );
      }
    }
  }

  function toggleHospitalSelection(selection: ClinicalRuntimeAssetSelection, checked: boolean) {
    const key = assetKey(selection.assetType, selection.assetIdentity);
    setHospitalSelections((current) => {
      const next = new Map(current);
      if (checked) next.set(key, selection);
      else next.delete(key);
      return next;
    });
  }

  async function publishPlatformBaseline() {
    if (platformPublishIds.length === 0 && platformDisabled.length === 0) {
      message.warning("请至少选择一个发布或停用变更");
      return;
    }
    try {
      const result = await publishPlatform.mutateAsync({
        publishVersionIds: platformPublishIds,
        disabledAssets: platformDisabled,
      });
      setPlatformPublishIds([]);
      setPlatformDisabled([]);
      message.success(`平台基线 ${revision("A", result.revisionNo)} 已发布`);
    } catch (error) {
      message.error(getApiErrorMessage(error, "平台基线发布失败"));
    }
  }

  async function activateHospitalRuntime() {
    if (!hospitalId || !baseline?.release.baselineReleaseId) {
      message.warning("请先选择医院并确认平台基线");
      return;
    }
    try {
      const result = await activateHospital.mutateAsync({
        hospitalId,
        request: {
          platformBaselineReleaseId: baseline.release.baselineReleaseId,
          expectedCurrentReleaseId: currentRuntime?.release.releaseId ?? null,
          activeAssets: Array.from(hospitalSelections.values()),
        },
      });
      message.success(`医院运行修订 ${revision("H", result.revisionNo)} 已生成`);
    } catch (error) {
      message.error(getApiErrorMessage(error, "医院运行修订生成失败"));
    }
  }

  async function rollback(target: ClinicalRuntimeRelease) {
    if (!hospitalId) return;
    try {
      const result = await rollbackHospital.mutateAsync({
        hospitalId,
        targetReleaseId: target.releaseId,
      });
      message.success(`已生成回滚修订 ${revision("H", result.revisionNo)}`);
    } catch (error) {
      message.error(getApiErrorMessage(error, "医院运行修订回滚失败"));
    }
  }

  let hospitalRuntimeSummary = null;
  if (hospitalId && currentRuntime) {
    hospitalRuntimeSummary = (
      <Card>
        <Title level={5}>当前医院运行修订 {revision("H", currentRuntime.release.revisionNo)}</Title>
        <Descriptions size="small" column={3}>
          <Descriptions.Item label="平台基线">
            {revision("A", baseline?.release.revisionNo)}
          </Descriptions.Item>
          <Descriptions.Item label="启用资产">
            {currentRuntime.items.filter((item) => item.entryState === "ACTIVE").length} 项
          </Descriptions.Item>
          <Descriptions.Item label="清单摘要">
            <Text code>{shortHash(currentRuntime.release.manifestSha256)}</Text>
          </Descriptions.Item>
        </Descriptions>
      </Card>
    );
  } else if (hospitalId) {
    hospitalRuntimeSummary = (
      <Alert
        type="info"
        showIcon
        message="该医院尚未建立运行修订"
        description="默认从当前平台基线开始选择，也可加入集团或本院资产。"
      />
    );
  }

  const platformContent = (
    <Space direction="vertical" size="large" className={styles.fullWidth}>
      {baseline ? (
        <Card>
          <Title level={5}>当前平台基线 {revision("A", baseline.release.revisionNo)}</Title>
          <Descriptions size="small" column={3}>
            <Descriptions.Item label="完整清单">{baseline.items.length} 项</Descriptions.Item>
            <Descriptions.Item label="发布时间">
              {new Date(baseline.release.publishedAt).toLocaleString()}
            </Descriptions.Item>
            <Descriptions.Item label="清单摘要">
              <Text code>{shortHash(baseline.release.manifestSha256)}</Text>
            </Descriptions.Item>
          </Descriptions>
        </Card>
      ) : (
        <Alert
          type="info"
          showIcon
          message="平台尚未建立权威基线"
          description="选择已完成技术校验的草稿资产后发布首个 A 修订。"
        />
      )}

      <Card
        title="本次发布变更"
        extra={
          <Space>
            <Select
              allowClear
              placeholder="全部资产类型"
              value={assetType}
              options={RUNTIME_ASSET_OPTIONS}
              onChange={setAssetType}
              className={styles.assetTypeSelect}
            />
            <Input.Search
              allowClear
              placeholder="搜索资产编码或来源"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              className={styles.keywordInput}
            />
          </Space>
        }
      >
        <Table
          rowKey="versionId"
          size="small"
          loading={platformCandidatesQuery.isLoading}
          pagination={false}
          dataSource={platformCandidates}
          locale={{ emptyText: "没有待发布草稿" }}
          columns={[
            {
              title: "选择",
              width: 72,
              render: (_value, candidate) => (
                <Checkbox
                  aria-label={`发布 ${candidate.assetIdentity} ${candidate.versionNo}`}
                  checked={platformPublishIds.includes(candidate.versionId)}
                  onChange={(event) => togglePlatformCandidate(candidate, event.target.checked)}
                />
              ),
            },
            {
              title: "资产",
              render: (_value, candidate) => (
                <Space direction="vertical" size={0}>
                  <Text strong>{candidate.assetIdentity}</Text>
                  <Text type="secondary">{ENGINE_ASSET_LABELS[candidate.assetType]}</Text>
                </Space>
              ),
            },
            { title: "资产版本", dataIndex: "versionNo", width: 110 },
            {
              title: "状态",
              dataIndex: "status",
              width: 100,
              render: stateTag,
            },
            { title: "来源依据", dataIndex: "sourceRef" },
          ]}
        />
      </Card>

      <Card title="当前清单停用">
        <Table
          rowKey={(item) => assetKey(item.assetType, item.assetIdentity)}
          size="small"
          pagination={false}
          dataSource={activeBaselineItems}
          locale={{ emptyText: "当前基线没有启用资产" }}
          columns={[
            {
              title: "停用",
              width: 72,
              render: (_value, item) => (
                <Checkbox
                  aria-label={`停用 ${item.assetIdentity}`}
                  checked={platformDisabled.some(
                    (candidate) =>
                      assetKey(candidate.assetType, candidate.assetIdentity) ===
                      assetKey(item.assetType, item.assetIdentity),
                  )}
                  onChange={(event) => togglePlatformDisabled(item, event.target.checked)}
                />
              ),
            },
            { title: "资产编码", dataIndex: "assetIdentity" },
            {
              title: "类型",
              dataIndex: "assetType",
              render: (value: RuntimeAssetType) => ENGINE_ASSET_LABELS[value],
            },
            { title: "当前版本", dataIndex: "versionNo", width: 110 },
          ]}
        />
      </Card>

      <div className={styles.primaryAction}>
        <Button
          type="primary"
          aria-label="发布新平台基线"
          icon={<RocketOutlined />}
          loading={publishPlatform.isPending}
          onClick={() => void publishPlatformBaseline()}
        >
          发布新平台基线
        </Button>
      </div>
    </Space>
  );

  const hospitalContent = (
    <Space direction="vertical" size="large" className={styles.fullWidth}>
      <Card>
        <Space direction="vertical" className={styles.fullWidth}>
          <Text strong>目标医院</Text>
          <Select
            showSearch
            allowClear
            aria-label="目标医院"
            placeholder="选择医院"
            value={hospitalId}
            options={hospitals}
            filterOption={false}
            onSearch={setHospitalKeyword}
            onChange={(value) => {
              initializedHospitalRevision.current = undefined;
              setHospitalSelections(new Map());
              setHospitalId(value);
            }}
            loading={hospitalsQuery.isLoading}
            className={styles.hospitalSelect}
          />
        </Space>
      </Card>

      {hospitalRuntimeSummary}

      {hospitalId && (
        <>
          <Card title="平台基线资产">
            <Table
              rowKey={(item) => assetKey(item.assetType, item.assetIdentity)}
              size="small"
              pagination={false}
              dataSource={activeBaselineItems}
              columns={[
                {
                  title: "启用",
                  width: 72,
                  render: (_value, item) => {
                    const selected = hospitalSelections.get(
                      assetKey(item.assetType, item.assetIdentity),
                    );
                    return (
                      <Checkbox
                        aria-label={`启用平台资产 ${item.assetIdentity}`}
                        checked={Boolean(selected) && !selected?.versionId}
                        onChange={(event) =>
                          toggleHospitalSelection(
                            {
                              assetType: item.assetType,
                              assetIdentity: item.assetIdentity,
                              versionId: null,
                            },
                            event.target.checked,
                          )
                        }
                      />
                    );
                  },
                },
                { title: "资产编码", dataIndex: "assetIdentity" },
                {
                  title: "类型",
                  dataIndex: "assetType",
                  render: (value: RuntimeAssetType) => ENGINE_ASSET_LABELS[value],
                },
                { title: "基线版本", dataIndex: "versionNo", width: 110 },
              ]}
            />
          </Card>

          <Card title="集团与本院资产">
            <Table
              rowKey="versionId"
              size="small"
              loading={localCandidatesQuery.isLoading}
              pagination={false}
              dataSource={localCandidates}
              locale={{ emptyText: "没有适用于该医院的本地资产" }}
              columns={[
                {
                  title: "启用",
                  width: 72,
                  render: (_value, candidate) => {
                    const selected = hospitalSelections.get(
                      assetKey(candidate.assetType, candidate.assetIdentity),
                    );
                    return (
                      <Checkbox
                        aria-label={`启用本地资产 ${candidate.assetIdentity} ${candidate.versionNo}`}
                        checked={selected?.versionId === candidate.versionId}
                        onChange={(event) =>
                          toggleHospitalSelection(
                            {
                              assetType: candidate.assetType,
                              assetIdentity: candidate.assetIdentity,
                              versionId: candidate.versionId,
                            },
                            event.target.checked,
                          )
                        }
                      />
                    );
                  },
                },
                { title: "资产编码", dataIndex: "assetIdentity" },
                {
                  title: "来源",
                  dataIndex: "sourceLayer",
                  render: (value: string) => (value === "GROUP" ? "集团" : "本院"),
                },
                { title: "资产版本", dataIndex: "versionNo", width: 110 },
                {
                  title: "状态",
                  dataIndex: "status",
                  width: 100,
                  render: stateTag,
                },
              ]}
            />
          </Card>

          <div className={styles.primaryAction}>
            <Button
              type="primary"
              aria-label="生成新医院运行修订"
              icon={<RocketOutlined />}
              loading={activateHospital.isPending}
              onClick={() => void activateHospitalRuntime()}
            >
              生成新医院运行修订
            </Button>
          </div>

          <Card
            title="医院运行历史"
            extra={
              <Button icon={<ReloadOutlined />} onClick={() => void historyQuery.refetch?.()}>
                刷新
              </Button>
            }
          >
            <Table
              rowKey="releaseId"
              size="small"
              loading={historyQuery.isLoading}
              pagination={false}
              dataSource={history}
              columns={[
                {
                  title: "修订",
                  dataIndex: "revisionNo",
                  render: (value: number) => revision("H", value),
                },
                {
                  title: "平台基线",
                  dataIndex: "platformBaselineReleaseId",
                },
                {
                  title: "启用时间",
                  dataIndex: "activatedAt",
                  render: (value: string) => (value ? new Date(value).toLocaleString() : "—"),
                },
                {
                  title: "操作",
                  width: 140,
                  render: (_value, item) =>
                    item.releaseId === currentRuntime?.release.releaseId ? (
                      <Tag color="processing">当前</Tag>
                    ) : (
                      <Popconfirm
                        title={`回滚到 ${revision("H", item.revisionNo)}`}
                        description="系统会复制该完整清单并生成更高编号的新修订。"
                        okText="确认回滚"
                        cancelText="取消"
                        onConfirm={() => rollback(item)}
                      >
                        <Button
                          size="small"
                          aria-label={`回滚到 ${revision("H", item.revisionNo)}`}
                          icon={<RollbackOutlined />}
                          loading={rollbackHospital.isPending}
                        >
                          回滚到 {revision("H", item.revisionNo)}
                        </Button>
                      </Popconfirm>
                    ),
                },
              ]}
            />
          </Card>
        </>
      )}
    </Space>
  );

  return (
    <PageShell title="运行发布" description="平台维护权威基线，医院维护自己的完整运行修订。">
      {baselineQuery.isError && (
        <Alert
          type="warning"
          showIcon
          message="当前平台基线不可用"
          description="若这是全新环境，可直接从平台草稿发布首个基线。"
          className={styles.statusAlert}
        />
      )}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: "platform", label: "平台权威基线", children: platformContent },
          { key: "hospital", label: "医院运行修订", children: hospitalContent },
        ]}
      />
    </PageShell>
  );
}
