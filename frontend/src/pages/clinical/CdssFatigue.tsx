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
  message,
  Tabs,
  Row,
  Col,
  Timeline,
  Progress,
  Empty,
} from "antd";
import type { BadgeProps, TableProps } from "antd";
import {
  BugOutlined,
  BookOutlined,
  CheckCircleOutlined,
  DashboardOutlined,
  FireOutlined,
  ReadOutlined,
  AuditOutlined,
  UserOutlined,
  CalendarOutlined,
  ExclamationCircleOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import {
  useCreateRecommendationTrigger,
  useRecommendationCards,
  useRecommendationCardDetail,
  useRecommendationCardSources,
  useSubmitRecommendationFeedback,
  useRecommendationFatigueSignals,
  useRecommendationTriggerDiagnose,
} from "@/shared/api/hooks";
import type {
  RecommendationCard,
  RecommendationSource,
  RecommendationFatigueSignal,
  RecommendationCardStatus,
  RecommendationRiskLevel,
  RecommendationFeedbackType,
} from "@/shared/api/hooks";

const { TextArea } = Input;
const { Option } = Select;

type RecommendationBadgeStatus = Exclude<BadgeProps["status"], undefined>;

/** 计算输入载荷的真实 SHA-256 摘要（不伪造哈希）。 */
async function sha256Hex(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export default function CdssFatigue() {
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [triggerModalVisible, setTriggerModalVisible] = useState<boolean>(false);
  const [diagnoseDrawerVisible, setDiagnoseDrawerVisible] = useState<boolean>(false);

  // 分页与过滤状态
  const [page, setPage] = useState<number>(1);
  const [size] = useState<number>(10);
  const [statusFilter, setStatusFilter] = useState<RecommendationCardStatus | undefined>(undefined);
  const [riskFilter, setRiskFilter] = useState<RecommendationRiskLevel | undefined>(undefined);
  const [patientIdFilter, setPatientIdFilter] = useState<string>("");

  // 医师反馈表单绑定
  const [feedbackForm] = Form.useForm();
  const [triggerForm] = Form.useForm();

  // 真实推荐卡列表：后端分页 + 服务端过滤，页面不伪造卡片。
  const {
    data: cardsPage,
    isLoading: cardsLoading,
    isError: cardsError,
    refetch: refetchCards,
  } = useRecommendationCards({
    status: statusFilter,
    riskLevel: riskFilter,
    patientId: patientIdFilter || undefined,
    page,
    size,
  });
  const cards: RecommendationCard[] = cardsPage?.items ?? [];

  const { data: detailData, refetch: refetchDetail } = useRecommendationCardDetail(
    selectedCardId || "",
  );

  const { data: sourcesData, refetch: refetchSources } = useRecommendationCardSources(
    selectedCardId || "",
  );

  // 获取该卡片相关的疲劳静音治理信号
  const { data: fatigueSignalsData, refetch: refetchFatigue } = useRecommendationFatigueSignals({
    fatigueKey: detailData?.card.scenarioCode || undefined,
    page: 1,
    size: 20,
  });

  // 获取该卡片的诊断可审计链
  const { data: diagnoseData, refetch: refetchDiagnose } = useRecommendationTriggerDiagnose(
    detailData?.card.triggerId || "",
  );

  // 突变动作
  const triggerCdssMutation = useCreateRecommendationTrigger();
  const feedbackMutation = useSubmitRecommendationFeedback(selectedCardId || "");

  // 触发一次推荐评估事件。后端为受控写入/治理层，不会凭患者+病种"生成"卡；
  // 候选卡由上游引擎/适配器提交，本沙箱仅登记触发并按后端真实返回刷新列表。
  const handleTriggerCdss = async () => {
    try {
      const values = await triggerForm.validateFields();
      let payloadJsonParsed = "{}";
      if (values.payloadJson) {
        try {
          JSON.parse(values.payloadJson);
          payloadJsonParsed = values.payloadJson;
        } catch {
          message.error("病种快照 payload 的 JSON 格式不合法！");
          return;
        }
      }

      const res = await triggerCdssMutation.mutateAsync({
        triggerCode: `CDSS-SANDBOX-${values.scenarioCode}`,
        triggerType: "MANUAL_SANDBOX",
        scenarioCode: values.scenarioCode,
        inputDigest: await sha256Hex(payloadJsonParsed),
        patientId: values.patientId,
        encounterId: values.encounterId || undefined,
      });

      message.success(
        `已登记推荐触发事件（trigger=${res.triggerId}），后端生成提醒卡 ${res.cardCount} 张；下方列表为后端真实数据。`,
      );
      setTriggerModalVisible(false);
      triggerForm.resetFields();
      refetchCards();
    } catch (error: unknown) {
      if (applyApiFieldErrors(triggerForm, error)) return;
      message.error(getApiErrorMessage(error, "触发 CDSS 计算失败，请稍后重试"));
    }
  };

  // 提交医师反馈 (ACCEPT / REJECT)。操作者身份由后端从登录态取真实用户，前端绝不伪造 physicianId。
  const handleFeedback = async (feedbackType: RecommendationFeedbackType) => {
    if (!selectedCardId) return;
    try {
      const values = await feedbackForm.validateFields();
      await feedbackMutation.mutateAsync({
        feedbackType,
        reasonCode: feedbackType === "REJECT" ? values.rejectReason : undefined,
        reasonText: values.comments,
      });

      message.success(
        feedbackType === "ACCEPT"
          ? "已登记采纳，已生成临床决策证据；是否下达医嘱请在 HIS 中确认。"
          : "已登记不采纳反馈，建议已封存归档。",
      );
      feedbackForm.resetFields();

      // 反馈后刷新真实数据，状态以后端为准
      refetchCards();
      refetchDetail();
      refetchSources();
      refetchFatigue();
      refetchDiagnose();
    } catch (error: unknown) {
      if (applyApiFieldErrors(feedbackForm, error)) return;
      message.error(getApiErrorMessage(error, "反馈提交失败，卡片可能已过期或已处于终止态"));
    }
  };

  // 表格列
  const columns: TableProps<RecommendationCard>["columns"] = [
    {
      title: "卡片编号",
      dataIndex: "cardId",
      key: "cardId",
      render: (text: string) => <span className="font-normal text-xs font-semibold">{text}</span>,
    },
    {
      title: "提醒摘要 Title",
      dataIndex: "title",
      key: "title",
      className: "font-semibold text-gray-800",
      width: 280,
    },
    {
      title: "患者 ID",
      dataIndex: "patientId",
      key: "patientId",
    },
    {
      title: "严重度",
      dataIndex: "riskLevel",
      key: "riskLevel",
      render: (level: RecommendationRiskLevel) => {
        const colors: Record<string, string> = { HIGH: "red", MEDIUM: "orange", LOW: "green" };
        return <Tag color={colors[level] || "blue"}>{level}</Tag>;
      },
    },
    {
      title: "拦截级别",
      dataIndex: "interruptLevel",
      key: "interruptLevel",
      render: (level: string) => {
        const colors = { HARD: "purple", SOFT: "volcano", NONE: "default" };
        return <Tag color={colors[level as keyof typeof colors] || "blue"}>{level}</Tag>;
      },
    },
    {
      title: "就诊场景",
      dataIndex: "scenarioCode",
      key: "scenarioCode",
      render: (c: string) => <Tag color="cyan">{c}</Tag>,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: RecommendationCardStatus) => {
        const config: Record<string, { status: RecommendationBadgeStatus; text: string }> = {
          PENDING: { status: "warning", text: "待处理 (PENDING)" },
          ACCEPTED: { status: "success", text: "已采纳 (ACCEPTED)" },
          REJECTED: { status: "error", text: "已驳回 (REJECTED)" },
          EXPIRED: { status: "default", text: "已失效 (EXPIRED)" },
        };
        const current = config[status] ?? { status: "default", text: status };
        return <Badge status={current.status} text={current.text} />;
      },
    },
    {
      title: "管理",
      key: "action",
      render: (record: RecommendationCard) => (
        <Button
          type="link"
          icon={<AuditOutlined />}
          onClick={() => {
            setSelectedCardId(record.cardId);
            setDiagnoseDrawerVisible(false);
          }}
          className="text-indigo-600 hover:text-indigo-900 font-semibold"
        >
          查看与人机反馈
        </Button>
      ),
    },
  ];

  return (
    <PageShell
      title="智能建议治理"
      description="汇总临床运行中的 CDSS 医嘱提醒卡，结合权威医学文献提供透明化解释，支持临床医生实时采纳反馈并监控超频提醒疲劳度治理信号。"
    >
      {/* 过滤搜索条 */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 mb-6">
        <Form layout="inline" className="flex flex-wrap gap-4 items-center w-full">
          <Form.Item label="状态">
            <Select
              placeholder="全部状态"
              allowClear
              value={statusFilter}
              onChange={(v) => {
                setStatusFilter(v);
                setPage(1);
              }}
              className="w-[140px]"
            >
              <Option value="PENDING">待处理</Option>
              <Option value="ACCEPTED">已采纳</Option>
              <Option value="REJECTED">已驳回</Option>
              <Option value="EXPIRED">已失效</Option>
            </Select>
          </Form.Item>
          <Form.Item label="严重度风险">
            <Select
              placeholder="全部严重度"
              allowClear
              value={riskFilter}
              onChange={(v) => {
                setRiskFilter(v);
                setPage(1);
              }}
              className="w-[140px]"
            >
              <Option value="HIGH">HIGH (红线强阻断)</Option>
              <Option value="MEDIUM">MEDIUM (黄线软提醒)</Option>
              <Option value="LOW">LOW (绿线低打扰)</Option>
            </Select>
          </Form.Item>
          <Form.Item label="患者 ID">
            <Input
              placeholder="输入患者 ID"
              allowClear
              value={patientIdFilter}
              onChange={(e) => {
                setPatientIdFilter(e.target.value);
                setPage(1);
              }}
              className="w-[160px]"
            />
          </Form.Item>
          <Form.Item className="ml-auto">
            <Button
              type="primary"
              icon={<FireOutlined />}
              onClick={() => setTriggerModalVisible(true)}
              className="rounded-lg font-medium bg-amber-600 border-amber-600 hover:bg-amber-700"
            >
              CDSS 触发沙箱
            </Button>
          </Form.Item>
        </Form>
      </div>

      {/* 主表格数据 */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
        {cardsError ? (
          <PageState
            state="error"
            description="推荐卡列表加载失败，请稍后重试或联系信息科。"
            onRetry={() => refetchCards()}
            traceId={cardsPage?.traceId}
          />
        ) : (
          <Table
            columns={columns}
            dataSource={cards}
            rowKey="cardId"
            loading={cardsLoading}
            pagination={{
              current: page,
              pageSize: size,
              total: cardsPage?.total ?? 0,
              onChange: (p) => setPage(p),
              showTotal: (t) => `共 ${t} 张临床运行提醒卡`,
            }}
            className="medkernel-table"
          />
        )}
      </div>

      {/* 触发 CDSS 沙箱 Modal */}
      <Modal
        title="CDSS 触发沙箱 (登记一次推荐触发评估)"
        open={triggerModalVisible}
        onOk={handleTriggerCdss}
        onCancel={() => setTriggerModalVisible(false)}
        width={560}
        confirmLoading={triggerCdssMutation.isPending}
        destroyOnClose
      >
        <Alert
          message="本沙箱仅向推荐引擎登记一次触发评估事件。候选提醒卡由上游临床引擎/适配器提交，触发本身不凭空生成卡片；提交后下方列表按后端真实数据刷新。"
          type="info"
          showIcon
          className="mb-4 rounded-lg text-xs"
        />
        <Form form={triggerForm} layout="vertical" className="mt-2">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="patientId" label="患者 ID (临床快照卡)" rules={[{ required: true }]}>
                <Input placeholder="输入真实患者 ID" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="encounterId" label="就诊 ID (Encounter ID，可选)">
                <Input placeholder="可留空" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="scenarioCode" label="触发决策就诊场景" rules={[{ required: true }]}>
                <Select placeholder="选择触发场景">
                  <Option value="PRESCRIPTION_SUBMIT">PRESCRIPTION_SUBMIT (开立医嘱提交)</Option>
                  <Option value="ORDER_CHECK">ORDER_CHECK (医保规范核查)</Option>
                  <Option value="CLINICAL_ADMIT">CLINICAL_ADMIT (办理入院评估)</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="diseaseCode" label="病种诊断 (ICD-10)" rules={[{ required: true }]}>
                <Input placeholder="输入真实病种编码" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="payloadJson"
            label="病种快照上下文 JSON (可选，将计算 SHA-256 作为 inputDigest)"
          >
            <TextArea
              rows={4}
              placeholder="请输入临床上下文载荷 JSON..."
              className="font-normal text-xs"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 采纳与疲劳治理抽屉 */}
      <Drawer
        title={
          <div className="flex items-center gap-2">
            <BookOutlined className="text-amber-600" />
            <span>智能建议人机闭环反馈与疲劳治理控制台</span>
          </div>
        }
        width={900}
        onClose={() => setSelectedCardId(null)}
        open={!!selectedCardId}
        destroyOnClose
      >
        {detailData && (
          <div>
            <Descriptions
              title="提醒卡主数据 Facts"
              bordered
              column={3}
              size="small"
              className="mb-6"
            >
              <Descriptions.Item label="卡片编号">
                <span className="font-normal text-xs font-semibold">{detailData.card.cardId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="患者 ID">
                <span className="font-semibold">{detailData.card.patientId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="就诊编码">
                <span className="font-normal text-xs">{detailData.card.encounterId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="决策场景">
                <Tag color="cyan">{detailData.card.scenarioCode}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="风险分级">
                <Tag color={detailData.card.riskLevel === "HIGH" ? "red" : "orange"}>
                  {detailData.card.riskLevel}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="拦截定位">
                <Tag color={detailData.card.interruptLevel === "HARD" ? "purple" : "volcano"}>
                  {detailData.card.interruptLevel}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="提醒摘要描述" span={3}>
                <span className="text-gray-800 font-medium">{detailData.card.summary}</span>
              </Descriptions.Item>
            </Descriptions>

            <Tabs
              defaultActiveKey="sources"
              items={[
                {
                  key: "sources",
                  label: (
                    <span>
                      <ReadOutlined /> 临床学术指南与文献证据 (Evidence)
                    </span>
                  ),
                  children: (
                    <div className="flex flex-col gap-4 mt-2 max-h-[460px] overflow-y-auto pr-2">
                      {sourcesData && sourcesData.length > 0 ? (
                        sourcesData.map((source: RecommendationSource) => (
                          <Card
                            key={source.sourceId}
                            size="small"
                            title={
                              <div className="flex items-center justify-between">
                                <span className="font-semibold text-gray-800 text-xs">
                                  {source.title}
                                </span>
                                <Tag
                                  color={
                                    typeof source.authorityScore === "number" ? "purple" : "default"
                                  }
                                >
                                  {typeof source.authorityScore === "number"
                                    ? `权威度评分: ${source.authorityScore}分`
                                    : "权威度评分未提供"}
                                </Tag>
                              </div>
                            }
                            className="border-gray-200 bg-gray-50 rounded-lg shadow-sm"
                          >
                            <div className="text-xs text-gray-700 leading-relaxed font-normal">
                              {source.content}
                            </div>
                            <Descriptions
                              size="small"
                              column={2}
                              className="mt-3 bg-white p-2 rounded border border-gray-100"
                            >
                              <Descriptions.Item label="指南/文献出处">
                                <span className="text-gray-500 font-semibold">
                                  {source.sourceRef}
                                </span>
                              </Descriptions.Item>
                              <Descriptions.Item label="证据级别">
                                {source.evidenceLevel ? (
                                  <Tag color="cyan">{source.evidenceLevel}</Tag>
                                ) : (
                                  <Tag>未提供</Tag>
                                )}
                              </Descriptions.Item>
                            </Descriptions>
                          </Card>
                        ))
                      ) : (
                        <Empty description="该提醒卡暂无来源解释证据（仅展示后端真实来源，不做任何兜底伪造）" />
                      )}
                    </div>
                  ),
                },

                {
                  key: "feedback",
                  disabled: detailData.card.status !== "PENDING",
                  label: (
                    <span>
                      <CheckCircleOutlined /> 医师人机交互反馈 (Feedback)
                    </span>
                  ),
                  children: (
                    <Card className="border-gray-200 shadow-sm rounded-xl mt-2">
                      <Form form={feedbackForm} layout="vertical">
                        <Alert
                          message="合理化医师反馈是临床合理处方闭环的核心留痕。选择不采纳时，请录入客观严谨的临床医学抗拒理由，以便医疗质控追溯与持续优化CDSS阈值。操作者身份由系统按登录态如实记录。"
                          type="info"
                          showIcon
                          className="mb-4 rounded-lg"
                        />

                        <Tabs
                          defaultActiveKey="accept"
                          type="card"
                          size="small"
                          className="mb-4"
                          items={[
                            {
                              key: "accept",
                              label: "采纳合理建议 (ACCEPT)",
                              children: (
                                <>
                                  <div className="p-4 bg-emerald-50 rounded-lg border border-emerald-100 text-emerald-800 text-xs mb-4">
                                    确认采纳此建议。系统将登记采纳反馈并生成临床决策证据；是否下达/撤销医嘱由医师在
                                    HIS 中确认。
                                  </div>
                                  <Form.Item name="comments" label="采纳说明 (非必填)">
                                    <Input placeholder="输入采纳说明，如：遵照指南撤销不合理克拉霉素..." />
                                  </Form.Item>
                                  <Button
                                    type="primary"
                                    onClick={() => handleFeedback("ACCEPT")}
                                    loading={feedbackMutation.isPending}
                                    className="w-full bg-emerald-600 border-emerald-600 hover:bg-emerald-700"
                                  >
                                    确认并予以采纳 (ACCEPT)
                                  </Button>
                                </>
                              ),
                            },

                            {
                              key: "reject",
                              label: "拒绝驳回建议 (REJECT)",
                              children: (
                                <>
                                  <Form.Item
                                    name="rejectReason"
                                    label="医生拒绝/不采纳的临床抗拒原因"
                                    rules={[
                                      { required: true, message: "请选择拒绝采纳的临床理由" },
                                    ]}
                                  >
                                    <Select placeholder="选择合理的抗拒指征原因">
                                      <Option value="方案不合个体指征">
                                        方案不合个体指征 (患者存在基因多态或联合耐药事实)
                                      </Option>
                                      <Option value="已有替代有效疗法">
                                        已有替代有效疗法 (临床已采取其它合理对症治疗手段)
                                      </Option>
                                      <Option value="数据存在延迟偏差">
                                        数据存在延迟偏差 (系统检测到的就诊或过敏事实与临床现状不符)
                                      </Option>
                                      <Option value="其他合理临床抉择">
                                        其他合理临床抉择 (需要医生在下方输入备注具体说明)
                                      </Option>
                                    </Select>
                                  </Form.Item>
                                  <Form.Item
                                    name="comments"
                                    label="备注/不采纳详细医学判定说明"
                                    rules={[{ required: true, message: "请输入详细拒绝说明" }]}
                                  >
                                    <TextArea
                                      rows={2}
                                      placeholder="请录入专业客观的临床诊断说明以便应对质控核查..."
                                    />
                                  </Form.Item>
                                  <Button
                                    type="primary"
                                    danger
                                    onClick={() => handleFeedback("REJECT")}
                                    loading={feedbackMutation.isPending}
                                    className="w-full"
                                  >
                                    确认拒绝采纳该建议 (REJECT)
                                  </Button>
                                </>
                              ),
                            },
                          ]}
                        />
                      </Form>
                    </Card>
                  ),
                },

                {
                  key: "fatigue",
                  label: (
                    <span>
                      <DashboardOutlined /> 提醒超频疲劳治理 (Fatigue Signal)
                    </span>
                  ),
                  children: (
                    <div className="mt-2 pr-2">
                      <Alert
                        message="为防范“提醒狼来了麻木”，MedKernel 引擎引入高阶提醒疲劳度限流控制事实。当特定场景超频触发且被医生频繁驳回时，系统会触发静音/限频甚至全面物理拦截阻断。"
                        type="warning"
                        showIcon
                        className="mb-4 rounded-lg"
                      />

                      {fatigueSignalsData?.items && fatigueSignalsData.items.length > 0 ? (
                        fatigueSignalsData.items.map((signal: RecommendationFatigueSignal) => (
                          <Card
                            key={signal.signalId}
                            size="small"
                            className="border-gray-200 bg-gray-50 rounded-lg shadow-sm mb-4"
                          >
                            <Descriptions size="small" column={2}>
                              <Descriptions.Item label="疲劳 Key">
                                <span className="font-normal text-xs font-semibold">
                                  {signal.fatigueKey}
                                </span>
                              </Descriptions.Item>
                              <Descriptions.Item label="信号定位">
                                <Tag color={signal.signalType === "MUTE" ? "orange" : "red"}>
                                  {signal.signalType}
                                </Tag>
                              </Descriptions.Item>
                            </Descriptions>
                            <div className="mt-3">
                              <div className="flex justify-between items-center text-xs text-gray-500 mb-1">
                                <span>疲劳触发进度 (当前触发 / 疲劳静音阈值)</span>
                                <span className="font-semibold text-gray-700">
                                  {signal.triggerCount} / {signal.governanceThreshold} 次
                                </span>
                              </div>
                              <Progress
                                percent={Math.min(
                                  100,
                                  Math.floor(
                                    (signal.triggerCount / signal.governanceThreshold) * 100,
                                  ),
                                )}
                                status={
                                  signal.triggerCount >= signal.governanceThreshold
                                    ? "exception"
                                    : "active"
                                }
                              />
                            </div>
                            {signal.summary && (
                              <div className="mt-3 text-xs text-gray-500 italic bg-white p-2 rounded border border-gray-100">
                                {signal.summary}
                              </div>
                            )}
                          </Card>
                        ))
                      ) : (
                        <Empty description="该场景暂无疲劳治理信号（仅展示后端真实采集信号）" />
                      )}
                    </div>
                  ),
                },
              ]}
            />

            {/* 可信归因诊断按钮 */}
            <div className="mt-6 flex justify-center w-full">
              <Button
                type="default"
                icon={<BugOutlined />}
                onClick={() => {
                  setDiagnoseDrawerVisible(true);
                  refetchDiagnose();
                }}
                className="rounded-lg border-indigo-500 text-indigo-600 hover:bg-indigo-50 w-full max-w-sm py-5 font-semibold text-center flex items-center justify-center gap-2"
              >
                可信推荐归因与决策审计追溯 (diagnose)
              </Button>
            </div>
          </div>
        )}
      </Drawer>

      {/* 可信诊断追溯 Drawer */}
      <Drawer
        title={
          <div className="flex items-center gap-2">
            <BugOutlined className="text-indigo-600" />
            <span>推荐决策链可信归因审计</span>
          </div>
        }
        width={640}
        onClose={() => setDiagnoseDrawerVisible(false)}
        open={diagnoseDrawerVisible}
        destroyOnClose
      >
        {diagnoseData ? (
          <div>
            <Alert
              message="决策解释追溯数据提取自底座 StateTransitionRecorder 物理事件留痕，保证透明、可复核、可审计。"
              type="info"
              showIcon
              className="mb-6 rounded-lg"
            />

            <Descriptions title="求值Trace元数据" bordered column={1} size="small" className="mb-6">
              <Descriptions.Item label="推荐 Trigger ID">
                <span className="font-normal text-xs">{diagnoseData.executionId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="链路 Trace ID">
                <span className="font-normal text-xs">{diagnoseData.traceId}</span>
              </Descriptions.Item>
              <Descriptions.Item label="输入 Payload 摘要 (SHA-256)">
                <span className="font-normal text-xs">
                  {diagnoseData.inputPayloadSummary || "—"}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="提醒卡风险定级">
                <Tag color={diagnoseData.riskLevel === "HIGH" ? "red" : "orange"}>
                  {diagnoseData.riskLevel || "LOW"}
                </Tag>
              </Descriptions.Item>
            </Descriptions>

            <Card
              title={
                <div className="flex items-center gap-2 text-indigo-600 font-semibold">
                  <UserOutlined />
                  <span>推荐决策求值证据与可信解释</span>
                </div>
              }
              className="mb-6 rounded-xl border-gray-200"
            >
              <div className="text-sm text-gray-800 bg-gray-50 p-4 rounded-lg font-normal border border-gray-100">
                {diagnoseData.explanationSnapshot || "暂无决策解释快照"}
              </div>
            </Card>

            <Card
              title={
                <div className="flex items-center gap-2 text-indigo-600 font-semibold">
                  <CalendarOutlined />
                  <span>审计流转历史 (State History)</span>
                </div>
              }
              className="rounded-xl border-gray-200"
            >
              <Timeline>
                {diagnoseData.statusHistory?.map((h, idx) => (
                  <Timeline.Item key={idx} color={h.status === "SIGNED" ? "green" : "blue"}>
                    <div className="flex justify-between items-center font-semibold text-gray-800 text-xs">
                      <span>状态: {h.status}</span>
                      <span className="text-gray-400 font-normal">
                        {new Date(h.changedAt).toLocaleString()}
                      </span>
                    </div>
                    <div className="text-gray-600 text-xs mt-1">
                      <span>操作人: </span>
                      <Tag color="cyan" icon={<UserOutlined />}>
                        {h.changedBy}
                      </Tag>
                    </div>
                    <div className="text-gray-500 text-xs mt-1 font-normal italic">{h.summary}</div>
                  </Timeline.Item>
                ))}
              </Timeline>
            </Card>
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center min-h-[200px] text-gray-400">
            <ExclamationCircleOutlined className="text-48px mb-4" />
            <span>无法获取该推荐触发实例的决策追溯链。</span>
          </div>
        )}
      </Drawer>
    </PageShell>
  );
}
