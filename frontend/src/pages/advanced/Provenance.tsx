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
  SearchOutlined,
} from "@ant-design/icons";

import { useKnowledgeIdentities, useKnowledgeProvenance } from "@/shared/api/hooks";
import type {
  KnowledgeAssetVersion,
  KnowledgeDomain,
  KnowledgeIdentity,
  KnowledgeIdentityStatus,
  KnowledgeSourceEvidence,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import { PageShell } from "@/shared/ui/PageShell";

import styles from "./Advanced.module.css";

const { Paragraph, Text, Title } = Typography;

const domainOptions: Array<{ value: KnowledgeDomain; label: string }> = [
  { value: "GUIDELINE", label: "指南" },
  { value: "DRUG", label: "药品说明书" },
  { value: "PATHWAY_KNOWLEDGE", label: "路径知识" },
  { value: "NURSING", label: "护理" },
  { value: "REPORT", label: "检查检验报告" },
  { value: "TCM", label: "中医药知识" },
  { value: "PROTOCOL", label: "诊疗方案" },
  { value: "POLICY", label: "政策法规" },
  { value: "LITERATURE", label: "文献" },
  { value: "DIAGNOSIS", label: "诊断知识" },
  { value: "OTHER", label: "其他" },
];

const domainLabels = new Map(domainOptions.map((option) => [option.value, option.label]));
const identityStatusLabels = new Map<KnowledgeIdentityStatus, string>([
  ["ACTIVE", "有效身份"],
  ["WITHDRAWN", "已撤回"],
  ["ARCHIVED", "已归档"],
]);

function domainLabel(value: KnowledgeDomain) {
  return domainLabels.get(value) ?? value;
}

function identityStatusLabel(value: KnowledgeIdentityStatus) {
  return identityStatusLabels.get(value) ?? value;
}

function formatDateTime(value?: string | null) {
  if (!value) return "未记录";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function versionLabel(version: KnowledgeAssetVersion) {
  return version.versionLabel || version.versionNo;
}

function versionStatus(version: KnowledgeAssetVersion, currentVersionId?: number | null) {
  if (version.id === currentVersionId) return <Tag color="success">当前权威版本</Tag>;
  if (version.status === "WITHDRAWN") return <Tag color="error">已撤回</Tag>;
  return <Tag>历史版本</Tag>;
}

function EvidenceList({ items }: { items: KnowledgeSourceEvidence[] }) {
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
              <Text type="secondary">{item.sourceCode}</Text>
            </div>

            <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 2 }}>
              <Descriptions.Item label="来源版本">
                {item.sourceVersionNo || "未记录"}
              </Descriptions.Item>
              <Descriptions.Item label="发布日期">
                {formatDateTime(item.publishedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="锚点路径">
                <Text code>{item.anchorPath || "未记录"}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="锚点名称">{item.anchorLabel || "未记录"}</Descriptions.Item>
              <Descriptions.Item label="片段偏移">
                {item.startOffset ?? "?"} - {item.endOffset ?? "?"}
              </Descriptions.Item>
              <Descriptions.Item label="引用关系">
                {item.relation || "未记录"} / 权重 {item.weight ?? "未记录"}
              </Descriptions.Item>
            </Descriptions>

            {item.textExcerpt && (
              <Paragraph className={styles.evidenceExcerpt}>{item.textExcerpt}</Paragraph>
            )}

            {item.authorityBasis && <Text type="secondary">权威依据：{item.authorityBasis}</Text>}
            <Text type="secondary">{item.rankingReason}</Text>
            <div className={styles.hashGrid}>
              <Text copyable={Boolean(item.sourceVersionHash)} className={styles.hashText}>
                来源版本指纹：{item.sourceVersionHash || "未记录"}
              </Text>
              <Text copyable={Boolean(item.fragmentHash)} className={styles.hashText}>
                来源片段指纹：{item.fragmentHash || "未记录"}
              </Text>
            </div>
          </Space>
        </List.Item>
      )}
    />
  );
}

export default function Provenance() {
  const [searchParams, setSearchParams] = useSearchParams();
  const linkedIdentityId = useMemo(() => {
    const value = Number(searchParams.get("identityId"));
    return Number.isSafeInteger(value) && value > 0 ? value : undefined;
  }, [searchParams]);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [domain, setDomain] = useState<string>();
  const [page, setPage] = useState(1);
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
  const provenanceQuery = useKnowledgeProvenance(selectedIdentityId);

  useEffect(() => {
    if (linkedIdentityId) {
      setSelectedIdentityId(linkedIdentityId);
      return;
    }
    if (identities.length === 0) {
      setSelectedIdentityId(undefined);
      return;
    }
    if (!identities.some((identity) => identity.id === selectedIdentityId)) {
      setSelectedIdentityId(identities[0].id);
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
          <Text type="secondary" className={styles.smallText}>
            {record.identityCode}
          </Text>
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
          <Text strong>{versionLabel(record)}</Text>
          <Text type="secondary" className={styles.smallText}>
            {record.versionNo}
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
    const nextParams = new URLSearchParams(searchParams);
    nextParams.delete("identityId");
    setSearchParams(nextParams, { replace: true });
  };

  const selectIdentity = (identityId: number) => {
    setSelectedIdentityId(identityId);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("identityId", String(identityId));
    setSearchParams(nextParams, { replace: true });
  };

  const refresh = () => {
    void identitiesQuery.refetch();
    if (selectedIdentityId) {
      void provenanceQuery.refetch();
    }
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
    const activeVersion = provenance.versions.find(
      (version) => version.id === provenance.currentVersionId,
    );
    detailContent = (
      <Space direction="vertical" size="large" className={styles.fullWidth}>
        <div className={styles.detailHeader}>
          <div>
            <Title level={4} className={styles.compactTitle}>
              {provenance.identity.subject}
            </Title>
            <Text type="secondary">{provenance.identity.identityCode}</Text>
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

        <section>
          <Space className={styles.sectionTitle}>
            <HistoryOutlined />
            <Text strong>版本沿革</Text>
          </Space>
          <Table
            columns={versionColumns}
            dataSource={provenance.versions}
            rowKey="id"
            size="small"
            pagination={false}
            locale={{ emptyText: <Empty description="暂无版本记录" /> }}
            scroll={{ x: 520 }}
          />
        </section>

        <section>
          <Space className={styles.sectionTitle}>
            <FileSearchOutlined />
            <Text strong>精确来源锚点</Text>
          </Space>
          <EvidenceList items={provenance.sourceEvidence} />
        </section>
      </Space>
    );
  }

  return (
    <PageShell
      title="知识来源追溯"
      description="按知识身份查看当前权威版本、历史沿革和精确来源锚点。"
    >
      <Space direction="vertical" size="large" className={styles.fullWidth}>
        <Card>
          <div className={styles.toolbar}>
            <Space wrap>
              <Input.Search
                allowClear
                enterButton="检索"
                prefix={<SearchOutlined />}
                placeholder="输入知识主题或身份编码"
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
              "请检查登录权限、租户上下文或知识服务状态。",
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
    </PageShell>
  );
}
