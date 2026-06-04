import { useState } from "react";
import {
  Row,
  Col,
  Card,
  Input,
  Button,
  Table,
  Tag,
  Descriptions,
  Alert,
  message,
  Drawer,
} from "antd";
import type { TableProps } from "antd";
import {
  PlayCircleOutlined,
  BugOutlined,
  CompassOutlined,
  FileTextOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { useEvaluateRules, useRuleExecutionExplain } from "@/shared/api/hooks";
import type { RuleEvaluationItem, RuleEvaluateResponse } from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";

const { TextArea } = Input;

export default function RuleValidate() {
  const [contextJson, setContextJson] = useState<string>("");
  const [triggerPoint, setTriggerPoint] = useState<string>("order-sign");
  const [patientId, setPatientId] = useState<string>("");
  const [packageVersion, setPackageVersion] = useState<string>("");

  const [evaluateResponse, setEvaluateResponse] = useState<RuleEvaluateResponse | null>(null);
  const [selectedExecutionId, setSelectedExecutionId] = useState<string | null>(null);

  const evaluateMutation = useEvaluateRules();
  const { data: explainData, isLoading: explainLoading } = useRuleExecutionExplain(
    selectedExecutionId || "",
  );

  const handleEvaluate = async () => {
    try {
      const payloadJson = contextJson.trim();
      if (!payloadJson) {
        message.error("请先粘贴真实脱敏上下文 JSON");
        return;
      }
      try {
        JSON.parse(payloadJson);
      } catch {
        message.error("临床上下文的 JSON 格式不合法，请检查！");
        return;
      }

      const res = await evaluateMutation.mutateAsync({
        triggerPoint,
        patientId: patientId.trim() || undefined,
        packageVersion,
        payloadJson,
      });

      setEvaluateResponse(res);
      message.success("批量规则匹配评估成功！");
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "批量规则评估失败"));
    }
  };

  const renderJson = (value: unknown) => {
    if (typeof value === "string") return value;
    if (value === null || value === undefined) return "暂无解释。";
    return JSON.stringify(value, null, 2);
  };

  const columns: TableProps<RuleEvaluationItem>["columns"] = [
    {
      title: "规则 ID",
      dataIndex: "ruleId",
      key: "ruleId",
      render: (text: string) => <Tag color="cyan">{text}</Tag>,
    },
    {
      title: "版本 ID",
      dataIndex: "versionId",
      key: "versionId",
      className: "font-semibold text-gray-800",
      render: (text: string | undefined, record: RuleEvaluationItem) => (
        <span>{text || record.ruleCode || "未返回版本"}</span>
      ),
    },
    {
      title: "警示严重度",
      dataIndex: "severity",
      key: "severity",
      render: (level: string) => {
        const colors: Record<string, string> = {
          LOW: "green",
          MEDIUM: "orange",
          HIGH: "red",
        };
        return <Tag color={colors[level]}>{level}</Tag>;
      },
    },
    {
      title: "处置动作",
      key: "actions",
      render: (_value: unknown, record: RuleEvaluationItem) => {
        const actionCodes =
          record.actions?.map((action) => action.actionCode).filter(Boolean) ??
          (record.actionCode ? [record.actionCode] : []);
        return actionCodes.length > 0 ? (
          <div className="flex flex-wrap gap-1">
            {actionCodes.map((code) => (
              <Tag color="blue" key={code}>
                {code}
              </Tag>
            ))}
          </div>
        ) : (
          <Tag>未返回动作</Tag>
        );
      },
    },
    {
      title: "命中解释",
      key: "explanation",
      render: (_value: unknown, record: RuleEvaluationItem) => (
        <span className="text-xs text-gray-700 whitespace-pre-wrap">
          {renderJson(record.explanation)}
        </span>
      ),
    },
    {
      title: "解释追溯",
      key: "action",
      render: (_record: RuleEvaluationItem) => {
        if (evaluateResponse?.executionId) {
          return (
            <Button
              type="link"
              icon={<BugOutlined />}
              onClick={() => setSelectedExecutionId(evaluateResponse.executionId)}
              className="text-indigo-600 hover:text-indigo-900 font-medium"
            >
              查看执行解释
            </Button>
          );
        }
        return <span className="text-gray-400">无可追溯快照</span>;
      },
    },
  ];

  return (
    <PageShell
      title="规则试运行"
      description="向规则引擎输入真实脱敏上下文，实时观测匹配命中情况，进行可信解释与归因追溯。"
    >
      <Row gutter={24}>
        {/* 左栏：输入上下文 */}
        <Col span={10}>
          <Card
            title={
              <div className="flex items-center gap-2 text-indigo-600">
                <CompassOutlined />
                <span>临床输入上下文</span>
              </div>
            }
            className="shadow-sm rounded-2xl border-gray-100"
          >
            <div className="mb-4">
              <div className="text-xs font-semibold text-gray-700 mb-1">
                触发时点 (Trigger Point)
              </div>
              <Input
                placeholder="输入触发时点编码"
                value={triggerPoint}
                onChange={(e) => setTriggerPoint(e.target.value)}
                className="font-normal text-sm"
              />
            </div>

            <div className="mb-4">
              <div className="text-xs font-semibold text-gray-700 mb-1">患者 ID（可选）</div>
              <Input
                placeholder="输入真实患者 ID；无患者上下文时可留空"
                value={patientId}
                onChange={(e) => setPatientId(e.target.value)}
                className="font-normal text-sm"
              />
            </div>

            <div className="mb-4">
              <div className="text-xs font-semibold text-gray-700 mb-1">标准上下文包版本</div>
              <Input
                placeholder="输入本次规则求值绑定的配置包版本"
                value={packageVersion}
                onChange={(e) => setPackageVersion(e.target.value)}
                className="font-normal text-sm"
              />
            </div>

            <div>
              <div className="text-xs font-semibold text-gray-700 mb-1">
                真实脱敏 Payload JSON 快照
              </div>
              <TextArea
                rows={16}
                value={contextJson}
                onChange={(e) => setContextJson(e.target.value)}
                placeholder="粘贴由上下文快照接口返回的脱敏 JSON，不在页面内预置患者、病种或药品。"
                className="font-normal text-xs p-3 bg-gray-50 rounded-lg"
              />
            </div>

            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={handleEvaluate}
              loading={evaluateMutation.isPending}
              className="w-full mt-6 h-10 font-semibold"
            >
              一键执行匹配校验
            </Button>
          </Card>
        </Col>

        {/* 右栏：评估看板 */}
        <Col span={14}>
          <Card
            title={
              <div className="flex items-center gap-2 text-emerald-600">
                <PlayCircleOutlined />
                <span>规则评估看板</span>
              </div>
            }
            className="shadow-sm rounded-2xl border-gray-100 h-full min-h-[580px]"
          >
            {evaluateResponse ? (
              <div>
                <div className="bg-gray-50 p-4 rounded-xl border border-gray-100 mb-6 flex flex-wrap gap-6 items-center">
                  <Descriptions size="small" column={2} className="flex-1">
                    <Descriptions.Item label="链路 TraceId">
                      <span className="font-normal text-xs text-gray-500">
                        {evaluateResponse.traceId}
                      </span>
                    </Descriptions.Item>
                    <Descriptions.Item label="求值 ExecutionId">
                      <span className="font-normal text-xs text-indigo-500">
                        {evaluateResponse.executionId}
                      </span>
                    </Descriptions.Item>
                    <Descriptions.Item label="最高严重警示">
                      <Tag color={evaluateResponse.highestSeverity === "HIGH" ? "red" : "orange"}>
                        {evaluateResponse.highestSeverity || "NONE"}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="命中规则总数">
                      <span className="font-semibold text-lg text-indigo-600">
                        {evaluateResponse.items?.filter((i: RuleEvaluationItem) => i.hit).length ||
                          0}{" "}
                        条
                      </span>
                    </Descriptions.Item>
                  </Descriptions>
                </div>

                <div className="text-sm font-semibold text-gray-800 mb-3">
                  命中规则及合理性建议列表
                </div>
                <Table
                  dataSource={
                    evaluateResponse.items?.filter((i: RuleEvaluationItem) => i.hit) || []
                  }
                  columns={columns}
                  rowKey="ruleId"
                  pagination={false}
                  locale={{ emptyText: "该临床快照未触发任何高风险或规则拦截，通过。" }}
                  className="medkernel-table"
                />
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center min-h-[400px] text-gray-400">
                <PlayCircleOutlined className="text-[64px] mb-4" />
                <span className="text-gray-500 font-medium">
                  请在左侧输入临床快照后，点击校验开始沙箱匹配
                </span>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* 可解释归因追溯抽屉 */}
      <Drawer
        title={
          <div className="flex items-center gap-2">
            <BugOutlined className="text-indigo-600" />
            <span>临床可信解释与归因追溯</span>
          </div>
        }
        width={640}
        onClose={() => setSelectedExecutionId(null)}
        open={!!selectedExecutionId}
        loading={explainLoading}
        destroyOnClose
      >
        {explainData && (
          <div>
            <Alert
              message="本解释视图读取规则执行日志中的真实输入摘要、命中结果、动作与解释快照；页面不补写归因。"
              type="info"
              showIcon
              className="mb-6 rounded-lg"
            />

            <Descriptions title="求值快照元数据" bordered column={1} size="small" className="mb-6">
              <Descriptions.Item label="求值 Execution ID">
                <span className="font-normal text-xs">{explainData.executionId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="链路 Trace ID">
                <span className="font-normal text-xs">{explainData.traceId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="触发点">
                <span className="font-normal text-xs">{explainData.triggerPoint}</span>
              </Descriptions.Item>
              <Descriptions.Item label="输入 Payload 摘要 (SHA-256)">
                <span className="font-normal text-xs">{explainData.inputDigest}</span>
              </Descriptions.Item>
              <Descriptions.Item label="风险评级">
                <Tag color={explainData.severity === "HIGH" ? "red" : "orange"}>
                  {explainData.severity || "LOW"}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="执行状态">
                <Tag color={explainData.status === "SUCCESS" ? "green" : "red"}>
                  {explainData.status}
                </Tag>
              </Descriptions.Item>
            </Descriptions>

            <Card
              title={
                <div className="flex items-center gap-2 text-indigo-600 font-semibold">
                  <FileTextOutlined />
                  <span>规则求值可信解释文本</span>
                </div>
              }
              className="mb-6 rounded-xl border-gray-200"
            >
              <div className="text-sm text-gray-800 bg-gray-50 p-4 rounded-lg font-normal border border-gray-100 whitespace-pre-wrap">
                {renderJson(explainData.explanation)}
              </div>
            </Card>

            <Card
              title={
                <div className="flex items-center gap-2 text-indigo-600 font-semibold">
                  <FileTextOutlined />
                  <span>执行动作快照</span>
                </div>
              }
              className="rounded-xl border-gray-200"
            >
              <div className="text-sm text-gray-800 bg-gray-50 p-4 rounded-lg font-normal border border-gray-100 whitespace-pre-wrap">
                {renderJson(explainData.actions)}
              </div>
            </Card>
          </div>
        )}
      </Drawer>
    </PageShell>
  );
}
