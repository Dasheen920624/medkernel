import {
  ReloadOutlined,
  RocketOutlined,
  RollbackOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
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
  useSimulateReleaseImpact,
  useSecurityProfile,
  type ClinicalRuntimeAssetSelection,
  type ClinicalRuntimeRelease,
  type ReleaseImpactSimulationResult,
  type PlatformBaselineItem,
  type ReleaseCandidateAsset,
  type ReleaseAssetRef,
  type RuntimeAssetType,
} from "@/shared/api/hooks";
import { ENGINE_ASSET_LABELS, RUNTIME_ASSET_OPTIONS } from "@/shared/config/assetCatalog";
import { formatClinicalDateTime } from "@/shared/lib/dateTimeText";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";
import { PageShell } from "@/shared/ui/PageShell";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import styles from "./ReleaseGovernance.module.css";

const { Text, Title } = Typography;

function assetKey(assetType: RuntimeAssetType, assetIdentity: string) {
  return `${assetType}|${assetIdentity}`;
}

function evidenceText(
  rawValue: string | number | null | undefined,
  evidenceDetailsEnabled: boolean,
  businessText: string,
) {
  if (!evidenceDetailsEnabled) return businessText;
  if (rawValue === undefined || rawValue === null || rawValue === "") return "未返回";
  return String(rawValue);
}

function assetLabel(assetType: RuntimeAssetType) {
  return ENGINE_ASSET_LABELS[assetType] ?? "运行";
}

function assetContentLabel(assetType: RuntimeAssetType) {
  return `${assetLabel(assetType)}内容`;
}

function sourceLayerLabel(sourceLayer?: string | null) {
  if (sourceLayer === "GROUP") return "集团";
  if (sourceLayer === "HOSPITAL") return "本院";
  return "平台";
}

function assetSummaryText(
  assetType: RuntimeAssetType,
  evidenceDetailsEnabled: boolean,
  assetIdentity: string,
  businessSuffix: string,
) {
  return evidenceText(
    assetIdentity,
    evidenceDetailsEnabled,
    `${assetContentLabel(assetType)}${businessSuffix}`,
  );
}

function assetActionLabel(
  action: string,
  assetType: RuntimeAssetType,
  versionNo: string | null | undefined,
  sourceLayer?: string | null,
) {
  const sourcePrefix =
    sourceLayer && sourceLayer !== "PLATFORM" ? sourceLayerLabel(sourceLayer) : "";
  const versionText = versionNo ? ` ${versionNo}` : "";
  return `${action}${sourcePrefix}${assetContentLabel(assetType)}${versionText}`;
}

function shortHash(value: string | null | undefined) {
  return value ? `${value.slice(0, 12)}…` : "—";
}

function hashEvidenceText(
  value: string | null | undefined,
  evidenceDetailsEnabled: boolean,
  businessText: string,
) {
  return evidenceDetailsEnabled ? shortHash(value) : businessText;
}

function revision(_prefix: "A" | "H", value: number | null | undefined) {
  return value ? `第 ${value} 版` : "尚未建立";
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

function replaySummary(result: ReleaseImpactSimulationResult) {
  if (result.replay.status === "NO_DATA") {
    return result.replay.reason || "暂无可回放病例";
  }
  if (result.replay.status === "UNSUPPORTED") {
    return result.replay.reason || "暂不能完成病例回放";
  }
  return `回放病例 ${result.replay.sampledCases} 例，变化 ${result.replay.changedCases} 例`;
}

function dependencyImpactSummary(result: ReleaseImpactSimulationResult) {
  const count = result.replay.impactedAssets.length;
  return count > 0 ? `影响 ${count} 项在用资产` : "未发现已启用依赖资产";
}

function impactIssueSummary(result: ReleaseImpactSimulationResult) {
  const issues = [
    ...result.safety.issues,
    ...result.dependencies.issues,
    ...(result.replay.status === "UNSUPPORTED" ? [result.replay.reason || "病例回放暂不可用"] : []),
    ...(result.conflicts.length > 0 ? [`${result.conflicts.length} 个机构覆盖冲突`] : []),
  ].filter((value): value is string => Boolean(value));
  if (issues.length > 0) return issues.join("；");
  return result.releasable ? "未发现阻断项" : "需复核评估结果";
}

export default function ReleaseGovernance() {
  const { message } = AntdApp.useApp();
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
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
  const [impactResults, setImpactResults] = useState<ReleaseImpactSimulationResult[]>([]);
  const [impactError, setImpactError] = useState<string>();
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
  const simulateReleaseImpact = useSimulateReleaseImpact();
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;

  const baseline = baselineQuery.data;
  const platformCandidates = platformCandidatesQuery.data?.items ?? [];
  const localCandidates = useMemo(
    () => localCandidatesQuery.data?.items ?? [],
    [localCandidatesQuery.data?.items],
  );
  const currentRuntime = runtimeQuery.data;
  const history = historyQuery.data?.items ?? [];

  const hospitals = useMemo(
    () =>
      (hospitalsQuery.data?.items ?? [])
        .filter((item) => item.id && item.facilityType === "HOSPITAL")
        .map((item) => ({
          value: item.id as string,
          label: `${item.name} · ${item.code}`,
          orgPath: item.orgPath ?? null,
        })),
    [hospitalsQuery.data?.items],
  );
  const selectedHospital = useMemo(
    () => hospitals.find((item) => item.value === hospitalId),
    [hospitalId, hospitals],
  );

  const activeBaselineItems = useMemo(
    () => (baseline?.items ?? []).filter((item) => item.entryState === "ACTIVE"),
    [baseline?.items],
  );
  const selectedLocalCandidates = useMemo(() => {
    const selectedVersionIds = new Set(
      Array.from(hospitalSelections.values())
        .map((selection) => selection.versionId)
        .filter((value): value is string => Boolean(value)),
    );
    return localCandidates.filter((candidate) => selectedVersionIds.has(candidate.versionId));
  }, [hospitalSelections, localCandidates]);
  const selectedLocalCandidateKey = selectedLocalCandidates
    .map((candidate) => candidate.versionId)
    .join("|");

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

  useEffect(() => {
    setImpactResults([]);
    setImpactError(undefined);
  }, [hospitalId, selectedLocalCandidateKey]);

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
      message.success(`平台标准版本 ${revision("A", result.revisionNo)} 已发布`);
    } catch (error) {
      message.error(getApiErrorMessage(error, "平台标准版本发布失败"));
    }
  }

  async function activateHospitalRuntime() {
    if (!hospitalId || !baseline?.release.baselineReleaseId) {
      message.warning("请先选择机构并确认平台标准版本");
      return;
    }
    if (selectedLocalCandidates.length > 0) {
      const passedImpactResults = new Map(
        impactResults
          .filter((result) => result.releasable)
          .map((result) => [result.candidateVersionId, result]),
      );
      const hasUnassessedLocalContent = selectedLocalCandidates.some(
        (candidate) => !passedImpactResults.has(candidate.versionId),
      );
      if (hasUnassessedLocalContent) {
        message.warning("请先完成发布影响评估，并处理所有阻断项");
        return;
      }
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
      message.success(`机构生效版本 ${revision("H", result.revisionNo)} 已生成`);
    } catch (error) {
      message.error(getApiErrorMessage(error, "机构生效版本生成失败"));
    }
  }

  async function simulateSelectedReleaseImpact() {
    if (!hospitalId || !selectedHospital?.orgPath) {
      message.warning("请先选择组织路径完整的目标医院");
      return;
    }
    if (selectedLocalCandidates.length === 0) {
      message.warning("请先选择需要进入机构生效版本的集团或本院内容");
      return;
    }
    try {
      setImpactError(undefined);
      const results = await Promise.all(
        selectedLocalCandidates.map((candidate) =>
          simulateReleaseImpact.mutateAsync({
            assetType: candidate.assetType,
            assetIdentity: candidate.assetIdentity,
            candidateVersionId: candidate.versionId,
            targetOrgUnitIds: [hospitalId],
            targetOrgPath: selectedHospital.orgPath as string,
            applicableScope: candidate.applicableScope,
            rolloutPolicy: {
              strategy: "ORG_LIST",
              orgUnitIds: [hospitalId],
            },
            replayDays: 30,
            replayLimit: 100,
          }),
        ),
      );
      setImpactResults(results);
      const blocked = results.filter((result) => !result.releasable).length;
      if (blocked > 0) {
        message.warning(`${blocked} 项内容需要处理后再生成机构生效版本`);
      } else {
        message.success("发布影响评估通过");
      }
    } catch (error) {
      const fallback = "发布影响评估失败";
      const reason = getApiErrorMessage(error, fallback);
      setImpactResults([]);
      setImpactError(reason);
      message.error(reason);
    }
  }

  async function rollback(target: ClinicalRuntimeRelease) {
    if (!hospitalId) return;
    try {
      const result = await rollbackHospital.mutateAsync({
        hospitalId,
        targetReleaseId: target.releaseId,
      });
      message.success(`已生成回滚版本 ${revision("H", result.revisionNo)}`);
    } catch (error) {
      message.error(getApiErrorMessage(error, "机构生效版本回滚失败"));
    }
  }

  let hospitalRuntimeSummary = null;
  if (hospitalId && currentRuntime) {
    hospitalRuntimeSummary = (
      <Card>
        <Title level={5}>当前机构生效版本 {revision("H", currentRuntime.release.revisionNo)}</Title>
        <Descriptions size="small" column={3}>
          <Descriptions.Item label="平台标准版本">
            {revision("A", baseline?.release.revisionNo)}
          </Descriptions.Item>
          <Descriptions.Item label="启用内容">
            {currentRuntime.items.filter((item) => item.entryState === "ACTIVE").length} 项
          </Descriptions.Item>
          <Descriptions.Item label="完整性状态">
            {hashEvidenceText(
              currentRuntime.release.manifestSha256,
              evidenceDetailsEnabled,
              "清单完整性已校验",
            )}
          </Descriptions.Item>
          <Descriptions.Item label="生效版本证据">
            {evidenceText(
              currentRuntime.release.releaseId,
              evidenceDetailsEnabled,
              "机构生效版本已记录",
            )}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    );
  } else if (hospitalId) {
    hospitalRuntimeSummary = (
      <Alert
        type="info"
        showIcon
        message="该机构尚未建立生效版本"
        description="默认沿用当前平台标准版本，也可加入集团或本院内容。"
      />
    );
  }

  const platformContent = (
    <Space direction="vertical" size="large" className={styles.fullWidth}>
      {baseline ? (
        <Card>
          <Title level={5}>当前平台标准版本 {revision("A", baseline.release.revisionNo)}</Title>
          <Descriptions size="small" column={3}>
            <Descriptions.Item label="启用内容">{baseline.items.length} 项</Descriptions.Item>
            <Descriptions.Item label="发布时间">
              {formatClinicalDateTime(baseline.release.publishedAt, baseline.release.publishedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="完整性状态">
              {hashEvidenceText(
                baseline.release.manifestSha256,
                evidenceDetailsEnabled,
                "清单完整性已校验",
              )}
            </Descriptions.Item>
            <Descriptions.Item label="发布证据">
              {evidenceText(
                baseline.release.baselineReleaseId,
                evidenceDetailsEnabled,
                "平台标准版本已记录",
              )}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      ) : (
        <Alert
          type="info"
          showIcon
          message="平台尚未建立标准版本"
          description="选择已完成校验的草稿内容后发布首个平台标准版本。"
        />
      )}

      <Card
        title="本次发布变更"
        extra={
          <Space>
            <Select
              allowClear
              placeholder="全部内容类型"
              value={assetType}
              options={RUNTIME_ASSET_OPTIONS}
              onChange={setAssetType}
              className={styles.assetTypeSelect}
            />
            <Input.Search
              allowClear
              placeholder="搜索内容名称、身份或来源"
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
                  aria-label={assetActionLabel(
                    "发布",
                    candidate.assetType,
                    candidate.versionNo,
                    candidate.sourceLayer,
                  )}
                  checked={platformPublishIds.includes(candidate.versionId)}
                  onChange={(event) => togglePlatformCandidate(candidate, event.target.checked)}
                />
              ),
            },
            {
              title: "内容",
              render: (_value, candidate) => (
                <Space direction="vertical" size={0}>
                  <Text strong>
                    {assetSummaryText(
                      candidate.assetType,
                      evidenceDetailsEnabled,
                      candidate.assetIdentity,
                      "已准备发布",
                    )}
                  </Text>
                  <Text type="secondary">
                    {assetContentLabel(candidate.assetType)} · {candidate.versionNo}
                  </Text>
                </Space>
              ),
            },
            { title: "内容版本", dataIndex: "versionNo", width: 110 },
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
          locale={{ emptyText: "当前标准版本没有启用内容" }}
          columns={[
            {
              title: "停用",
              width: 72,
              render: (_value, item) => (
                <Checkbox
                  aria-label={assetActionLabel("停用", item.assetType, item.versionNo)}
                  checked={platformDisabled.some(
                    (candidate) =>
                      assetKey(candidate.assetType, candidate.assetIdentity) ===
                      assetKey(item.assetType, item.assetIdentity),
                  )}
                  onChange={(event) => togglePlatformDisabled(item, event.target.checked)}
                />
              ),
            },
            {
              title: "内容",
              render: (_value, item) => (
                <Space direction="vertical" size={0}>
                  <Text strong>
                    {assetSummaryText(
                      item.assetType,
                      evidenceDetailsEnabled,
                      item.assetIdentity,
                      "已在平台标准版本中",
                    )}
                  </Text>
                  <Text type="secondary">{assetContentLabel(item.assetType)}</Text>
                </Space>
              ),
            },
            { title: "当前版本", dataIndex: "versionNo", width: 110 },
          ]}
        />
      </Card>

      <div className={styles.primaryAction}>
        <Button
          type="primary"
          aria-label="发布新平台标准版本"
          icon={<RocketOutlined />}
          loading={publishPlatform.isPending}
          onClick={() => void publishPlatformBaseline()}
        >
          发布新平台标准版本
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
          <Card title="平台标准内容">
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
                        aria-label={assetActionLabel("启用平台", item.assetType, item.versionNo)}
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
                {
                  title: "内容",
                  render: (_value, item) => (
                    <Space direction="vertical" size={0}>
                      <Text strong>
                        {assetSummaryText(
                          item.assetType,
                          evidenceDetailsEnabled,
                          item.assetIdentity,
                          "沿用平台标准版本",
                        )}
                      </Text>
                      <Text type="secondary">{assetContentLabel(item.assetType)}</Text>
                    </Space>
                  ),
                },
                { title: "基线版本", dataIndex: "versionNo", width: 110 },
              ]}
            />
          </Card>

          <Card title="集团与本院内容">
            <Table
              rowKey="versionId"
              size="small"
              loading={localCandidatesQuery.isLoading}
              pagination={false}
              dataSource={localCandidates}
              locale={{ emptyText: "没有适用于该机构的本地内容" }}
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
                        aria-label={assetActionLabel(
                          "启用",
                          candidate.assetType,
                          candidate.versionNo,
                          candidate.sourceLayer,
                        )}
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
                {
                  title: "内容",
                  render: (_value, candidate) => (
                    <Space direction="vertical" size={0}>
                      <Text strong>
                        {assetSummaryText(
                          candidate.assetType,
                          evidenceDetailsEnabled,
                          candidate.assetIdentity,
                          "可加入机构生效版本",
                        )}
                      </Text>
                      <Text type="secondary">
                        {sourceLayerLabel(candidate.sourceLayer)} ·{" "}
                        {assetContentLabel(candidate.assetType)}
                      </Text>
                    </Space>
                  ),
                },
                {
                  title: "来源",
                  dataIndex: "sourceLayer",
                  render: (value: string) => sourceLayerLabel(value),
                },
                { title: "内容版本", dataIndex: "versionNo", width: 110 },
                {
                  title: "状态",
                  dataIndex: "status",
                  width: 100,
                  render: stateTag,
                },
              ]}
            />
          </Card>

          {selectedLocalCandidates.length > 0 && (
            <Card
              title="发布影响评估"
              extra={
                <Button
                  aria-label="评估发布影响"
                  icon={<SafetyCertificateOutlined />}
                  loading={simulateReleaseImpact.isPending}
                  onClick={() => void simulateSelectedReleaseImpact()}
                >
                  评估发布影响
                </Button>
              }
            >
              <Space direction="vertical" className={styles.fullWidth}>
                {impactError && (
                  <Alert
                    type="warning"
                    showIcon
                    message="发布影响评估未完成"
                    description={impactError}
                  />
                )}
                <Table
                  rowKey="candidateVersionId"
                  size="small"
                  pagination={false}
                  dataSource={impactResults}
                  locale={{ emptyText: "尚未评估本次机构内容变更" }}
                  columns={[
                    {
                      title: "内容",
                      render: (_value, result) => {
                        const candidate = localCandidates.find(
                          (item) => item.versionId === result.candidateVersionId,
                        );
                        return (
                          <Space direction="vertical" size={0}>
                            <Text strong>
                              {candidate
                                ? assetSummaryText(
                                    candidate.assetType,
                                    evidenceDetailsEnabled,
                                    candidate.assetIdentity,
                                    "已完成影响评估",
                                  )
                                : evidenceText(
                                    result.candidateVersionId,
                                    evidenceDetailsEnabled,
                                    "候选内容已完成影响评估",
                                  )}
                            </Text>
                            <Text type="secondary">
                              {candidate ? assetContentLabel(candidate.assetType) : "运行内容"} ·{" "}
                              {result.diff.candidateVersionNo ?? candidate?.versionNo ?? "候选版本"}
                            </Text>
                          </Space>
                        );
                      },
                    },
                    {
                      title: "结论",
                      width: 100,
                      render: (_value, result) => (
                        <Tag color={result.releasable ? "success" : "error"}>
                          {result.releasable ? "可发布" : "需处理"}
                        </Tag>
                      ),
                    },
                    {
                      title: "病例回放",
                      render: (_value, result) => replaySummary(result),
                    },
                    {
                      title: "依赖影响",
                      render: (_value, result) => (
                        <Space direction="vertical" size={0}>
                          <Text>{dependencyImpactSummary(result)}</Text>
                          {result.replay.impactedAssets.map((asset) => (
                            <Text
                              key={`${asset.assetType}|${asset.assetIdentity}|${asset.versionId}`}
                              type="secondary"
                            >
                              {evidenceDetailsEnabled
                                ? `${assetLabel(asset.assetType)} · ${asset.assetIdentity} · ${asset.versionNo}`
                                : `${assetContentLabel(asset.assetType)} · ${asset.versionNo}`}
                            </Text>
                          ))}
                        </Space>
                      ),
                    },
                    {
                      title: "阻断原因",
                      render: (_value, result) => impactIssueSummary(result),
                    },
                  ]}
                />
              </Space>
            </Card>
          )}

          <div className={styles.primaryAction}>
            <Button
              type="primary"
              aria-label="生成新机构生效版本"
              icon={<RocketOutlined />}
              loading={activateHospital.isPending}
              onClick={() => void activateHospitalRuntime()}
            >
              生成新机构生效版本
            </Button>
          </div>

          <Card
            title="机构版本历史"
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
                  title: "平台标准版本",
                  render: (_value, item) =>
                    evidenceText(
                      item.platformBaselineReleaseId,
                      evidenceDetailsEnabled,
                      "已关联平台标准版本",
                    ),
                },
                {
                  title: "生效版本证据",
                  render: (_value, item) =>
                    evidenceText(item.releaseId, evidenceDetailsEnabled, "机构版本已记录"),
                },
                {
                  title: "启用时间",
                  dataIndex: "activatedAt",
                  render: (value: string) => formatClinicalDateTime(value, "—"),
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
                        description="系统会复制当前内容组合并生成新的生效版本。"
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
    <PageShell
      title="发布治理"
      description="发布平台标准版本，并为机构确认当前生效版本。"
      extras={<EvidenceDetailsToggle securityProfile={security.data} />}
    >
      {baselineQuery.isError && (
        <Alert
          type="warning"
          showIcon
          message="当前平台标准版本不可用"
          description="若这是全新环境，可直接从平台草稿发布首个标准版本。"
          className={styles.statusAlert}
        />
      )}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: "platform", label: "平台标准版本", children: platformContent },
          { key: "hospital", label: "机构生效版本", children: hospitalContent },
        ]}
      />
    </PageShell>
  );
}
