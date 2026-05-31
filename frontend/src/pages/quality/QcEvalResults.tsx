import { useMemo, useState } from "react";
import { PageShell } from "@/shared/ui/PageShell";
import {
  Alert,
  Button,
  Card,
  Empty,
  Input,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from "antd";
import type { TableProps } from "antd";
import {
  CheckCircleOutlined,
  DatabaseOutlined,
  MinusCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { useEvaluationResults } from "@/shared/api/hooks";
import type { EvaluationResult, EvaluationResultLevel } from "@/shared/api/hooks";

export default function QcEvalResults() {
  const [filterCode, setFilterCode] = useState("");
  const [filterLevel, setFilterLevel] = useState<EvaluationResultLevel | undefined>(undefined);
  const [filterDept, setFilterDept] = useState("");

  const {
    data: pageData,
    refetch,
    isLoading,
    isError,
  } = useEvaluationResults({
    indicatorCode: filterCode.trim() || undefined,
    resultLevel: filterLevel,
    responsibleDepartmentId: filterDept.trim() || undefined,
    page: 1,
    size: 50,
  });

  const results = useMemo(() => pageData?.items ?? [], [pageData?.items]);

  const metrics = useMemo(() => {
    const currentPageTotal = results.length;
    const passCount = results.filter((item) => item.resultLevel === "PASS").length;
    const defectCount = results.filter((item) => item.resultLevel !== "PASS").length;
    const complianceRate =
      currentPageTotal > 0 ? Math.round((passCount / currentPageTotal) * 1000) / 10 : 0;
    return {
      totalResults: pageData?.total ?? currentPageTotal,
      currentPageTotal,
      complianceRate,
      defectCount,
    };
  }, [pageData?.total, results]);

  const renderScoreTag = (score: number | undefined) => {
    if (score === undefined || score === null) {
      return <Tag color="default">不计分</Tag>;
    }
    const colorClass = score >= 90 ? "text-emerald-500" : "text-rose-500";
    return <span className={`font-bold text-sm ${colorClass}`}>{score.toFixed(1)}分</span>;
  };

  const renderLevelTag = (level: EvaluationResultLevel) => {
    switch (level) {
      case "PASS":
        return <Tag color="success">达标</Tag>;
      case "ATTENTION":
        return <Tag color="warning">需关注</Tag>;
      case "NON_COMPLIANT":
        return <Tag color="error">缺陷</Tag>;
      case "CRITICAL":
        return (
          <Tag className="border-rose-500 bg-rose-50 text-rose-600 font-semibold">严重红线</Tag>
        );
      default:
        return <Tag>{level}</Tag>;
    }
  };

  const columns: TableProps<EvaluationResult>["columns"] = [
    {
      title: "指标编码",
      dataIndex: "indicatorCode",
      key: "indicatorCode",
      className: "font-semibold text-slate-700",
    },
    {
      title: "考核得分",
      dataIndex: "scoreValue",
      key: "scoreValue",
      render: (score: number | undefined) => renderScoreTag(score),
    },
    {
      title: "评估级别",
      dataIndex: "resultLevel",
      key: "resultLevel",
      render: (level: EvaluationResultLevel) => renderLevelTag(level),
    },
    {
      title: "命中标志",
      dataIndex: "hitFlag",
      key: "hitFlag",
      render: (hit: boolean) =>
        hit ? (
          <Tooltip title="评估项达标">
            <CheckCircleOutlined className="text-emerald-500 text-base" />
          </Tooltip>
        ) : (
          <Tooltip title="评估项未达标">
            <WarningOutlined className="text-rose-500 text-base" />
          </Tooltip>
        ),
    },
    {
      title: "质量事实审计摘要",
      dataIndex: "evidenceSummary",
      key: "evidenceSummary",
      className: "text-slate-600 text-xs",
    },
    {
      title: "评估科室",
      dataIndex: "responsibleDepartmentId",
      key: "responsibleDepartmentId",
      render: (dept: string | undefined) => (
        <Tag className="border-slate-100 bg-slate-50 text-slate-500">{dept || "全院"}</Tag>
      ),
    },
    {
      title: "扫描计算时间",
      dataIndex: "createdAt",
      key: "createdAt",
      render: (date: string | undefined) => (
        <span className="text-slate-400 text-xs">{date ? date.substring(0, 16) : "--"}</span>
      ),
    },
  ];

  return (
    <PageShell
      title="评估结果"
      description="汇总真实质控扫描结果，顶部指标只按当前查询返回的数据计算。"
    >
      <Space direction="vertical" size="large" className="w-full">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Card>
            <StatisticLine
              icon={<DatabaseOutlined className="text-xl" />}
              label="真实评估结果总数"
              value={`${metrics.totalResults} 例`}
            />
          </Card>
          <Card>
            <StatisticLine
              icon={<MinusCircleOutlined className="text-xl" />}
              label="当前页结果数"
              value={`${metrics.currentPageTotal} 例`}
            />
          </Card>
          <Card>
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <div className="text-slate-500 text-xs font-semibold">当前页达标率</div>
                <span className="text-emerald-500 font-bold text-xs">
                  {metrics.complianceRate}%
                </span>
              </div>
              <Progress percent={metrics.complianceRate} size="small" showInfo={false} />
            </div>
          </Card>
          <Card>
            <StatisticLine
              icon={<WarningOutlined className="text-xl" />}
              label="当前页缺陷/红线"
              value={`${metrics.defectCount} 项`}
              danger
            />
          </Card>
        </div>

        <Card>
          <Space wrap className="w-full justify-between">
            <Space wrap>
              <Input
                placeholder="检索指标编码"
                prefix={<SearchOutlined className="text-slate-400" />}
                className="w-48"
                value={filterCode}
                onChange={(event) => setFilterCode(event.target.value)}
                onPressEnter={() => refetch()}
              />
              <Select
                placeholder="评估等级"
                allowClear
                value={filterLevel}
                className="w-40"
                onChange={setFilterLevel}
                options={[
                  { value: "PASS", label: "通过达标" },
                  { value: "ATTENTION", label: "需关注" },
                  { value: "NON_COMPLIANT", label: "质控缺陷" },
                  { value: "CRITICAL", label: "严重红线" },
                ]}
              />
              <Input
                placeholder="考核科室"
                className="w-48"
                value={filterDept}
                onChange={(event) => setFilterDept(event.target.value)}
                onPressEnter={() => refetch()}
              />
              <Button type="primary" onClick={() => refetch()}>
                过滤查询
              </Button>
            </Space>
            <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
              刷新
            </Button>
          </Space>
        </Card>

        {isError && (
          <Alert
            type="error"
            showIcon
            message="评估结果接口读取失败"
            description="请检查登录权限、租户上下文或评估服务状态。"
          />
        )}

        <Card title="扫描明细结果台账">
          <Table
            dataSource={results}
            columns={columns}
            rowKey={(record) => record.resultId}
            loading={isLoading}
            locale={{ emptyText: <Empty description="暂无真实评估结果" /> }}
            pagination={{
              total: pageData?.total ?? 0,
              pageSize: 10,
              showSizeChanger: false,
            }}
          />
        </Card>
      </Space>
    </PageShell>
  );
}

function StatisticLine({
  icon,
  label,
  value,
  danger = false,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  danger?: boolean;
}) {
  return (
    <div className="flex items-center gap-3">
      <span
        className={`flex items-center justify-center text-slate-600 ${danger ? "text-rose-600" : ""}`}
      >
        {icon}
      </span>
      <div>
        <div className="text-slate-500 text-xs font-semibold">{label}</div>
        <div className={`text-2xl font-bold mt-1 ${danger ? "text-rose-500" : "text-slate-800"}`}>
          {value}
        </div>
      </div>
    </div>
  );
}
