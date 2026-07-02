import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  List,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { TableProps } from "antd";
import {
  FileSearchOutlined,
  HistoryOutlined,
  LinkOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SwapOutlined,
} from "@ant-design/icons";

import {
  useKnowledgeIdentities,
  useKnowledgeProvenance,
  useKnowledgeReviewQueue,
  useSecurityProfile,
} from "@/shared/api/hooks";
import type {
  KnowledgeAssetVersion,
  KnowledgeIdentity,
  KnowledgeIdentityStatus,
  KnowledgeSourceEvidence,
} from "@/shared/api/hooks";
import { KNOWLEDGE_DOMAIN_OPTIONS, type KnowledgeDomain } from "@/shared/config/assetCatalog";
import { getApiErrorMessage } from "@/shared/api/errors";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { findRouteByPath } from "@/shared/config/routes";
import { formatClinicalDateTime } from "@/shared/lib/dateTimeText";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import type { RouteExperience } from "@/shared/ui/experienceTypes";

import styles from "./Advanced.module.css";

const { Paragraph, Text, Title } = Typography;

const domainOptions = KNOWLEDGE_DOMAIN_OPTIONS;

const domainLabels = new Map(domainOptions.map((option) => [option.value, option.label]));
const identityStatusLabels = new Map<KnowledgeIdentityStatus, string>([
  ["ACTIVE", "有效身份"],
  ["DEPRECATED", "迁移宽限期"],
  ["WITHDRAWN", "已撤回"],
  ["ARCHIVED", "已归档"],
]);
const evidenceQualityLabels = new Map([
  ["HIGH", "高质量"],
  ["MEDIUM", "中等质量"],
  ["LOW", "低质量"],
]);
const sourceRelationLabels = new Map([
  ["SUPPORTS", "支持"],
  ["CONFLICTS", "存在冲突"],
  ["REPLACES", "替代"],
  ["CITES", "引用"],
]);
const PROVENANCE_HISTORY_PAGE_SIZE = 20;
const route = findRouteByPath("/advanced/provenance");
const TECHNICAL_VERSION_LABEL_PATTERNS = [
  /^ai-draft-task-[a-z0-9-]+$/i,
  /^model-task-[a-z0-9-]+$/i,
  /^[0-9a-f]{32,}$/i,
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
];

if (!route?.experience) {
  throw new Error("来源与血缘页面缺少体验声明");
}

const PAGE_META: { title: string; experience: RouteExperience } = {
  title: "知识来源追溯",
  experience: route.experience,
};

function domainLabel(value: KnowledgeDomain) {
  return domainLabels.get(value) ?? customerEnumLabel(value);
}

function identityStatusLabel(value: KnowledgeIdentityStatus) {
  return identityStatusLabels.get(value) ?? customerEnumLabel(value);
}

function evidenceQualityLabel(value?: string | null) {
  return value ? (evidenceQualityLabels.get(value) ?? customerEnumLabel(value)) : "未记录";
}

function sourceRelationLabel(value?: string | null) {
  return value ? (sourceRelationLabels.get(value) ?? customerEnumLabel(value)) : "未记录";
}

function formatDateTime(value?: string | null) {
  if (!value) return "未记录";
  return formatClinicalDateTime(value, value);
}

function normalizedText(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}

function isTechnicalVersionLabel(value: unknown) {
  const label = normalizedText(value);
  return TECHNICAL_VERSION_LABEL_PATTERNS.some((pattern) => pattern.test(label));
}

function businessVersionNoLabel(value: unknown) {
  const versionNo = normalizedText(value);
  if (!versionNo) {
    return "版本待确认";
  }
  const semanticVersion = versionNo.match(/^v(\d+(?:\.\d+)*)$/i);
  if (semanticVersion) {
    return `第 ${semanticVersion[1]} 版`;
  }
  const numericYear = Number(versionNo);
  if (/^\d{4}$/.test(versionNo) && numericYear >= 1900 && numericYear <= 2100) {
    return `${versionNo} 版`;
  }
  if (/^\d+$/.test(versionNo)) {
    return `第 ${versionNo} 版`;
  }
  return versionNo;
}

function lineageVersionLabel(version: KnowledgeAssetVersion, currentVersionId?: number | null) {
  if (version.id === currentVersionId || version.status === "ACTIVE") {
    return "当前权威版本";
  }
  if (version.status === "DRAFT") {
    return "草稿版本";
  }
  if (version.status === "CANDIDATE" || version.status === "PENDING_REPLACEMENT_REVIEW") {
    return "候选版本";
  }
  if (version.status === "UNDER_REVIEW") {
    return "审核中版本";
  }
  if (version.status === "SUPERSEDED") {
    return "历史版本";
  }
  if (version.status === "WITHDRAWN") {
    return "已撤回版本";
  }
  if (version.status === "REJECTED") {
    return "已驳回版本";
  }
  return "知识版本";
}

function technicalVersionEvidence(...values: unknown[]) {
  const unique = new Set<string>();
  for (const value of values) {
    const label = normalizedText(value);
    if (label && isTechnicalVersionLabel(label)) {
      unique.add(label);
    }
  }
  return Array.from(unique);
}

function safeVersionLabel(
  version: KnowledgeAssetVersion,
  currentVersionId: number | null | undefined,
) {
  const rawVersionLabel = normalizedText(version.versionLabel);
  const rawVersionNo = normalizedText(version.versionNo);
  if (rawVersionLabel && !isTechnicalVersionLabel(rawVersionLabel)) {
    return rawVersionLabel;
  }
  if (rawVersionNo && !isTechnicalVersionLabel(rawVersionNo)) {
    return businessVersionNoLabel(rawVersionNo);
  }
  return lineageVersionLabel(version, currentVersionId);
}

function versionLabel(
  version: KnowledgeAssetVersion,
  currentVersionId: number | null | undefined,
  evidenceDetailsEnabled: boolean,
) {
  const safeLabel = safeVersionLabel(version, currentVersionId);
  const technicalEvidence = technicalVersionEvidence(version.versionLabel, version.versionNo);
  if (evidenceDetailsEnabled && technicalEvidence.length > 0) {
    return `${safeLabel} · ${technicalEvidence.join(" · ")}`;
  }
  return safeLabel;
}

function versionNoText(version: KnowledgeAssetVersion, evidenceDetailsEnabled: boolean) {
  const rawVersionNo = normalizedText(version.versionNo);
  if (evidenceDetailsEnabled) {
    return rawVersionNo || "版本号未记录";
  }
  if (!rawVersionNo) {
    return "版本号待确认";
  }
  if (isTechnicalVersionLabel(rawVersionNo)) {
    return "版本来源已记录";
  }
  return rawVersionNo;
}

function versionStatus(version: KnowledgeAssetVersion, currentVersionId?: number | null) {
  if (version.id === currentVersionId) return <Tag color="success">当前权威版本</Tag>;
  if (version.status === "WITHDRAWN") return <Tag color="error">已撤回</Tag>;
  return <Tag>历史版本</Tag>;
}

function EvidenceList({
  items,
  evidenceDetailsEnabled,
}: {
  items: KnowledgeSourceEvidence[];
  evidenceDetailsEnabled: boolean;
}) {
  if (items.length === 0) {
    return <Empty description="当前权威版本暂无来源引用" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  return (
    <List
      dataSource={items}
      split
      renderItem={(item) => (
        <List.Item>
          <Space direction="vertical" size="small" className={styles.fullWidth}>
            <div className={styles.detailHeader}>
              <Space wrap>
                <LinkOutlined />
                <Text strong>{item.sourceTitle}</Text>
                <Tag color={item.recommendedByDefault ? "blue" : "default"}>
                  {item.displayLabel}
                </Tag>
              </Space>
              {evidenceDetailsEnabled ? <Text type="secondary">{item.sourceCode}</Text> : null}
            </div>

            <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 2 }}>
              <Descriptions.Item label="来源版本">
                {item.sourceVersionNo || "未记录"}
              </Descriptions.Item>
              <Descriptions.Item label="发布日期">
                {formatDateTime(item.publishedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="引用位置">{item.anchorLabel || "未记录"}</Descriptions.Item>
              <Descriptions.Item label="引用关系">
                {sourceRelationLabel(item.relation)}
              </Descriptions.Item>
              {evidenceDetailsEnabled ? (
                <>
                  <Descriptions.Item label="锚点路径">
                    <Text code>{item.anchorPath || "未记录"}</Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="片段偏移">
                    {item.startOffset ?? "?"} - {item.endOffset ?? "?"}
                  </Descriptions.Item>
                  <Descriptions.Item label="排序权重">{item.weight ?? "未记录"}</Descriptions.Item>
                </>
              ) : null}
            </Descriptions>

            {item.textExcerpt && (
              <Paragraph className={styles.evidenceExcerpt}>{item.textExcerpt}</Paragraph>
            )}

            {item.authorityBasis && <Text type="secondary">权威依据：{item.authorityBasis}</Text>}
            <Text type="secondary">{item.rankingReason}</Text>
            {evidenceDetailsEnabled ? (
              <div className={styles.hashGrid}>
                <Text copyable={Boolean(item.sourceVersionHash)} className={styles.hashText}>
                  来源版本指纹：{item.sourceVersionHash || "未记录"}
                </Text>
                <Text copyable={Boolean(item.fragmentHash)} className={styles.hashText}>
                  来源片段指纹：{item.fragmentHash || "未记录"}
                </Text>
              </div>
            ) : null}
          </Space>
        </List.Item>
      )}
    />
  );
}

export default function Provenance() {
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const [searchParams, setSearchParams] = useSearchParams();
  const linkedIdentityId = useMemo(() => {
    const value = Number(searchParams.get("identityId"));
    return Number.isSafeInteger(value) && value > 0 ? value : undefined;
  }, [searchParams]);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [domain, setDomain] = useState<KnowledgeDomain>();
  const [page, setPage] = useState(1);
  const [historyPage, setHistoryPage] = useState(1);
  const [selectedIdentityId, setSelectedIdentityId] = useState<number | undefined>(
    linkedIdentityId,
  );

  const identitiesQuery = useKnowledgeIdentities({
    keyword: keyword || undefined,
    domain,
    page,
    size: 20,
  });
  const identities = useMemo(
    () => identitiesQuery.data?.items ?? [],
    [identitiesQuery.data?.items],
  );
  const provenanceQuery = useKnowledgeProvenance(selectedIdentityId, {
    page: historyPage,
    size: PROVENANCE_HISTORY_PAGE_SIZE,
  });
  const reviewQueueQuery = useKnowledgeReviewQueue({
    withinDays: 30,
    page: 1,
    size: 20,
  });

  useEffect(() => {
    if (linkedIdentityId) {
      setSelectedIdentityId(linkedIdentityId);
      setHistoryPage(1);
      return;
    }
    if (identities.length === 0) {
      setSelectedIdentityId(undefined);
      setHistoryPage(1);
      return;
    }
    if (!identities.some((identity) => identity.id === selectedIdentityId)) {
      setSelectedIdentityId(identities[0].id);
      setHistoryPage(1);
    }
  }, [identities, linkedIdentityId, selectedIdentityId]);

  const identityColumns: TableProps<KnowledgeIdentity>["columns"] = [
    {
      title: "知识主题",
      dataIndex: "subject",
      key: "subject",
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{value}</Text>
          {evidenceDetailsEnabled ? (
            <Text type="secondary" className={styles.smallText}>
              {record.identityCode}
            </Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: "领域",
      dataIndex: "domain",
      key: "domain",
      width: 112,
      render: (value: KnowledgeDomain) => <Tag>{domainLabel(value)}</Tag>,
    },
  ];

  const versionColumns: TableProps<KnowledgeAssetVersion>["columns"] = [
    {
      title: "版本",
      key: "version",
      render: (_value: unknown, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>
            {versionLabel(record, provenanceQuery.data?.currentVersionId, evidenceDetailsEnabled)}
          </Text>
          <Text type="secondary" className={styles.smallText}>
            {versionNoText(record, evidenceDetailsEnabled)}
          </Text>
        </Space>
      ),
    },
    {
      title: "状态",
      key: "status",
      width: 132,
      render: (_value: unknown, record) =>
        versionStatus(record, provenanceQuery.data?.currentVersionId),
    },
    {
      title: "生效时间",
      dataIndex: "effectiveFrom",
      key: "effectiveFrom",
      width: 176,
      render: (value?: string) => formatDateTime(value),
    },
  ];

  const submitSearch = (value: string) => {
    setKeyword(value.trim());
    setPage(1);
    setSelectedIdentityId(undefined);
    setHistoryPage(1);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.delete("identityId");
    setSearchParams(nextParams, { replace: true });
  };

  const selectIdentity = (identityId: number) => {
    setSelectedIdentityId(identityId);
    setHistoryPage(1);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("identityId", String(identityId));
    setSearchParams(nextParams, { replace: true });
  };

  const refresh = () => {
    void identitiesQuery.refetch();
    if (selectedIdentityId) {
      void provenanceQuery.refetch();
    }
    void reviewQueueQuery.refetch();
  };

  let detailContent = <Empty description="请选择知识身份" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  if (selectedIdentityId && provenanceQuery.isLoading) {
    detailContent = <Alert type="info" showIcon message="正在读取知识来源链" />;
  } else if (selectedIdentityId && provenanceQuery.isError) {
    detailContent = (
      <Alert
        type="error"
        showIcon
        message="知识来源链读取失败"
        description={getApiErrorMessage(provenanceQuery.error, "请检查权限或知识服务状态。")}
      />
    );
  } else if (provenanceQuery.data) {
    const provenance = provenanceQuery.data;
    const versionItems = provenance.versions.items ?? [];
    const supersessionItems = provenance.supersessions.items ?? [];
    const activeVersion = versionItems.find(
      (version) => version.id === provenance.currentVersionId,
    );
    const retirement = supersessionItems.find(
      (item) => item.transitionType === "DEPRECATE" || item.transitionType === "RETIRE",
    );
    detailContent = (
      <Space direction="vertical" size="large" className={styles.fullWidth}>
        <div className={styles.detailHeader}>
          <div>
            <Title level={4} className={styles.compactTitle}>
              {provenance.identity.subject}
            </Title>
            {evidenceDetailsEnabled ? (
              <Text type="secondary">{provenance.identity.identityCode}</Text>
            ) : null}
          </div>
          <Space wrap>
            <Tag>{domainLabel(provenance.identity.domain)}</Tag>
            <Tag color="success">{identityStatusLabel(provenance.identity.status)}</Tag>
          </Space>
        </div>

        {provenance.partial && (
          <Alert
            type="warning"
            showIcon
            message={`${provenance.unresolvedCitationCount} 条引用未能解析，当前结果为部分成功。`}
            description="已解析内容保持可读；未解析引用不会被静默忽略，请由知识治理人员补齐来源链。"
          />
        )}

        {activeVersion?.conflictArbitration && (
          <Alert
            type="info"
            showIcon
            message="当前版本存在冲突裁决记录"
            description={activeVersion.conflictArbitration}
          />
        )}

        {retirement?.migrationGuidance && (
          <Alert
            type={retirement.transitionType === "RETIRE" ? "error" : "warning"}
            showIcon
            message={
              retirement.transitionType === "RETIRE"
                ? "该知识身份已退役"
                : `该知识身份将在 ${formatDateTime(retirement.gracePeriodEnd)} 结束迁移宽限期`
            }
            description={
              evidenceDetailsEnabled
                ? `${retirement.migrationGuidance}；后继身份 ID：${
                    retirement.successorIdentityId ?? "未记录"
                  }`
                : retirement.migrationGuidance
            }
          />
        )}

        {activeVersion && (
          <section>
            <Space className={styles.sectionTitle}>
              <SafetyCertificateOutlined />
              <Text strong>循证与复审</Text>
            </Space>
            <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }}>
              <Descriptions.Item label="来源级别">
                {activeVersion.authorityLevel
                  ? customerEnumLabel(activeVersion.authorityLevel)
                  : "未记录"}
              </Descriptions.Item>
              <Descriptions.Item label="证据质量">
                {evidenceQualityLabel(activeVersion.gradeQuality)}
              </Descriptions.Item>
              <Descriptions.Item label="复审周期">
                {activeVersion.reviewCycleMonths
                  ? `${activeVersion.reviewCycleMonths} 个月`
                  : "未记录"}
              </Descriptions.Item>
              <Descriptions.Item label="最近复审">
                {formatDateTime(activeVersion.reviewedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="下次复审">
                {formatDateTime(activeVersion.nextReviewAt)}
              </Descriptions.Item>
              {evidenceDetailsEnabled ? (
                <Descriptions.Item label="复审人">
                  {activeVersion.reviewedBy || "未记录"}
                </Descriptions.Item>
              ) : null}
            </Descriptions>
          </section>
        )}

        <section>
          <Space className={styles.sectionTitle}>
            <HistoryOutlined />
            <Text strong>版本沿革</Text>
          </Space>
          <Table
            columns={versionColumns}
            dataSource={versionItems}
            rowKey="id"
            size="small"
            pagination={{
              current: provenance.versions.page ?? historyPage,
              pageSize: provenance.versions.size ?? PROVENANCE_HISTORY_PAGE_SIZE,
              total: provenance.versions.total ?? 0,
              showSizeChanger: false,
              onChange: setHistoryPage,
            }}
            locale={{ emptyText: <Empty description="暂无版本记录" /> }}
            scroll={{ x: 520 }}
          />
        </section>

        <section>
          <Space className={styles.sectionTitle}>
            <FileSearchOutlined />
            <Text strong>精确来源锚点</Text>
          </Space>
          <EvidenceList
            items={provenance.sourceEvidence}
            evidenceDetailsEnabled={evidenceDetailsEnabled}
          />
        </section>
      </Space>
    );
  }

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
      evidenceDetailsEnabled={evidenceDetailsEnabled}
    >
      <Space direction="vertical" size="large" className={styles.fullWidth}>
        {Boolean(reviewQueueQuery.data?.total) && (
          <Alert
            type={
              reviewQueueQuery.data?.items.some((item) => item.status === "OVERDUE")
                ? "warning"
                : "info"
            }
            showIcon
            icon={<SwapOutlined />}
            message={`${reviewQueueQuery.data?.total ?? 0} 项知识需要复审`}
            description={reviewQueueQuery.data?.items
              ?.slice(0, 3)
              .map(
                (item) =>
                  `${item.identity.subject}（${item.status === "OVERDUE" ? "已逾期" : "临近到期"}）`,
              )
              .join("、")}
          />
        )}
        <Card>
          <div className={styles.toolbar}>
            <Space wrap>
              <Input.Search
                allowClear
                enterButton="检索"
                prefix={<SearchOutlined />}
                placeholder="输入知识主题或知识身份"
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                onSearch={submitSearch}
                className={styles.searchWide}
              />
              <Select
                allowClear
                placeholder="知识领域"
                value={domain}
                onChange={(value) => {
                  setDomain(value);
                  setPage(1);
                  setSelectedIdentityId(undefined);
                  setHistoryPage(1);
                  const nextParams = new URLSearchParams(searchParams);
                  nextParams.delete("identityId");
                  setSearchParams(nextParams, { replace: true });
                }}
                options={domainOptions}
                className={styles.selectWide}
              />
            </Space>
            <Button icon={<ReloadOutlined />} onClick={refresh}>
              刷新
            </Button>
          </div>
        </Card>

        {identitiesQuery.isError && (
          <Alert
            type="error"
            showIcon
            message="知识身份读取失败"
            description={getApiErrorMessage(
              identitiesQuery.error,
              "请检查登录权限、服务机构范围或知识服务状态。",
            )}
          />
        )}

        <div className={styles.provenanceGrid}>
          <Card title="知识身份">
            <Table
              columns={identityColumns}
              dataSource={identities}
              rowKey="id"
              loading={identitiesQuery.isLoading}
              size="small"
              rowClassName={(record) =>
                record.id === selectedIdentityId
                  ? styles.selectedTableRow
                  : styles.interactiveTableRow
              }
              onRow={(record) => ({
                onClick: () => selectIdentity(record.id),
              })}
              locale={{ emptyText: <Empty description="暂无知识身份" /> }}
              pagination={{
                current: page,
                pageSize: 20,
                total: identitiesQuery.data?.total ?? 0,
                showSizeChanger: false,
                onChange: setPage,
              }}
              scroll={{ x: 420 }}
            />
          </Card>

          <Card title="来源详情">{detailContent}</Card>
        </div>
      </Space>
    </PageExperienceShell>
  );
}
