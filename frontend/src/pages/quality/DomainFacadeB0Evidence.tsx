import { Alert, Card, Descriptions, Button, Space, Statistic, Table, Tag, Typography } from "antd";
import { ReloadOutlined } from "@ant-design/icons";

import { parseApiError } from "@/shared/api/errors";
import {
  useDomainFacadeB0Evidence,
  type DomainFacadeB0Evidence as DomainFacadeB0EvidenceRow,
  type DomainFacadeEngineEvidence,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";

const { Text } = Typography;

const STATUS_COLOR: Record<string, string> = {
  PASS: "success",
  FAIL: "error",
};

const KIND_LABEL: Record<string, string> = {
  DOMAIN: "专业领域门面",
  SERVICE_COMBINATION: "服务组合门面",
};

export default function DomainFacadeB0Evidence() {
  const evidence = useDomainFacadeB0Evidence();
  const rows = evidence.data ?? [];
  const summary = summarize(rows);
  const reloadButton = (
    <Button
      type="primary"
      aria-label="刷新领域门面无模型证据"
      icon={<ReloadOutlined />}
      onClick={() => void evidence.refetch()}
    >
      刷新证据
    </Button>
  );

  if (evidence.isLoading) {
    return (
      <PageShell
        title="领域门面无模型证据"
        description="正在读取门面无模型规则链路"
        primary={reloadButton}
        state="loading"
      >
        <></>
      </PageShell>
    );
  }

  if (evidence.isError) {
    const parsed = parseApiError(evidence.error, "暂时无法读取领域门面无模型证据");
    return (
      <PageShell
        title="领域门面无模型证据"
        description="门面无模型证据读取失败"
        primary={reloadButton}
        state="error"
        stateProps={{
          title: "暂时无法读取领域门面无模型证据",
          description: parsed.message,
          traceId: parsed.traceId,
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (rows.length === 0) {
    return (
      <PageShell
        title="领域门面无模型证据"
        description="暂无门面无模型规则链路证据"
        primary={reloadButton}
        state="empty"
        stateProps={{
          title: "暂无领域门面证据",
          description: "当前暂无 17 张领域门面的无模型共享链路证据，请稍后重试。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  return (
    <PageShell
      title="领域门面无模型证据"
      description="只读核查门面无模型共享链路"
      primary={reloadButton}
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Alert
          showIcon
          type="info"
          message="无模型规则主链路"
          description="不预置真实医学内容/不新增专属业务引擎/不声明完整专业领域上线；本页只核查 17 张门面复用共享链路，不替代 S0-S40、真实消费者、业务闭环或上线验收。"
        />

        <Space wrap>
          <Card>
            <Statistic title="17 张领域门面" value={summary.total} suffix="/ 17" />
          </Card>
          <Card>
            <Statistic title="无模型规则主链路" value={summary.b0Executable} suffix="条" />
          </Card>
          <Card>
            <Statistic
              title="未预置真实医学内容"
              value={summary.noClinicalContentSeeded}
              suffix="条"
            />
          </Card>
          <Card>
            <Statistic
              title="无需新增专属业务引擎"
              value={summary.noNewBusinessEngine}
              suffix="条"
            />
          </Card>
        </Space>

        <Card title="门面证据">
          <Table<DomainFacadeB0EvidenceRow>
            rowKey="code"
            dataSource={rows}
            pagination={false}
            expandable={{
              expandedRowRender: renderExpandedEvidence,
            }}
            columns={[
              {
                title: "门面代码",
                dataIndex: "code",
                render: (code: string) => <Text code>{code}</Text>,
              },
              {
                title: "类型",
                dataIndex: "kind",
                render: (kind: string) => KIND_LABEL[kind] ?? kind,
              },
              {
                title: "状态",
                dataIndex: "status",
                render: (status: string) => (
                  <Tag color={STATUS_COLOR[status] ?? "default"}>{status}</Tag>
                ),
              },
              {
                title: "证据编号",
                dataIndex: "evidenceId",
                render: (evidenceId: string) => <Text code>{evidenceId}</Text>,
              },
              {
                title: "边界",
                render: (_, record) => (
                  <Space wrap size={[4, 4]}>
                    <Tag color={record.b0Executable ? "success" : "error"}>
                      可在无模型条件下执行
                    </Tag>
                    <Tag color={record.modelRequired ? "error" : "success"}>不依赖模型</Tag>
                    <Tag color={record.clinicalContentSeeded ? "error" : "success"}>
                      不预置真实医学内容
                    </Tag>
                    <Tag color={record.newBusinessEngineRequired ? "error" : "success"}>
                      不新增专属业务引擎
                    </Tag>
                    <Tag color={record.honestEmptyWhenAssetsMissing ? "success" : "warning"}>
                      缺资产诚实空态
                    </Tag>
                  </Space>
                ),
              },
              {
                title: "成员解析",
                render: (_, record) => memberResolutionText(record),
              },
            ]}
          />
        </Card>
      </Space>
    </PageShell>
  );
}

function renderExpandedEvidence(record: DomainFacadeB0EvidenceRow) {
  return (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Descriptions size="small" column={2}>
        <Descriptions.Item label="资产种子策略">{record.assetSeedPolicy}</Descriptions.Item>
        <Descriptions.Item label="无模型工作流">
          {record.b0Workflows.length > 0 ? record.b0Workflows.join(" / ") : "无"}
        </Descriptions.Item>
        <Descriptions.Item label="声明成员">
          {record.memberFacadeCodes.length > 0 ? record.memberFacadeCodes.join("、") : "无"}
        </Descriptions.Item>
        <Descriptions.Item label="已解析成员">
          {record.verifiedMemberFacadeCodes.length > 0
            ? record.verifiedMemberFacadeCodes.join("、")
            : "无"}
        </Descriptions.Item>
      </Descriptions>
      <Table<DomainFacadeEngineEvidence>
        rowKey={(engine) => `${record.code}-${engine.engine}-${engine.b0Route}`}
        size="small"
        pagination={false}
        dataSource={record.engineEvidence}
        columns={[
          { title: "共享引擎", dataIndex: "engine" },
          {
            title: "共享处理器",
            dataIndex: "sharedHandlerClass",
            render: (value: string) => <Text code>{value}</Text>,
          },
          {
            title: "确定性入口",
            dataIndex: "b0Route",
            render: (value: string) => <Text code>{value}</Text>,
          },
          { title: "无模型核查结果", dataIndex: "b0Assertion" },
          {
            title: "确定性",
            render: (_, engine) => (
              <Space wrap size={[4, 4]}>
                <Tag color={engine.deterministic ? "success" : "error"}>确定性</Tag>
                <Tag color={engine.handlerPresent ? "success" : "error"}>处理器存在</Tag>
                <Tag color={engine.clinicalContentSeeded ? "error" : "success"}>
                  不预置真实医学内容
                </Tag>
              </Space>
            ),
          },
        ]}
      />
    </Space>
  );
}

function summarize(rows: DomainFacadeB0EvidenceRow[]) {
  return {
    total: rows.length,
    b0Executable: rows.filter((row) => row.b0Executable && !row.modelRequired).length,
    noClinicalContentSeeded: rows.filter((row) => !row.clinicalContentSeeded).length,
    noNewBusinessEngine: rows.filter((row) => !row.newBusinessEngineRequired).length,
  };
}

function memberResolutionText(record: DomainFacadeB0EvidenceRow) {
  if (record.memberFacadeCodes.length === 0) {
    return "无组合成员";
  }
  return record.serviceCombinationMembersResolvable
    ? `${record.verifiedMemberFacadeCodes.length}/${record.memberFacadeCodes.length} 已解析`
    : `${record.verifiedMemberFacadeCodes.length}/${record.memberFacadeCodes.length} 未全部解析`;
}
