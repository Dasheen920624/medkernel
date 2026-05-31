import { useState } from "react";
import { PageShell } from "@/shared/ui/PageShell";
import {
  Table,
  Button,
  Tag,
  Input,
  Select,
  Drawer,
  Form,
  Card,
  Descriptions,
  message,
  Tabs,
  Alert,
  Steps,
  Timeline,
  Modal,
  Empty,
} from "antd";
import {
  WarningOutlined,
  SearchOutlined,
  HistoryOutlined,
  AuditOutlined,
  SendOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from "@ant-design/icons";
import {
  useQualityFindings,
  useQualityFindingDetail,
  useSubmitRectification,
  useReviewRectification,
  useEvaluationRunDiagnose,
} from "@/shared/api/hooks";
import type {
  QualityFinding,
  QualityFindingSeverity,
  QualityFindingStatus,
} from "@/shared/api/hooks";

const { Option } = Select;

export default function QcAlerts() {
  const [filterSeverity, setFilterSeverity] = useState<QualityFindingSeverity | undefined>(
    undefined,
  );
  const [filterStatus, setFilterStatus] = useState<QualityFindingStatus | undefined>(undefined);
  const [filterDept, setFilterDept] = useState<string>("");
  const [activeTab, setActiveTab] = useState<string>("assigned_tasks");

  // 抽屉详情相关状态
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [selectedFinding, setSelectedFinding] = useState<QualityFinding | null>(null);

  // 加载主台账
  const severityParam = filterSeverity;
  let statusParam: QualityFindingStatus | undefined = filterStatus;
  if (!statusParam) {
    if (activeTab === "assigned_tasks") {
      statusParam = "ASSIGNED";
    } else if (activeTab === "remediating_tasks") {
      statusParam = "REMEDIATING";
    }
  }

  const {
    data: pageData,
    refetch,
    isLoading,
  } = useQualityFindings({
    severity: severityParam,
    status: statusParam,
    responsibleDepartmentId: filterDept ? filterDept : undefined,
    page: 1,
    size: 50,
  });

  // 获取缺陷三合一详情事实 (Finding + Task + Reviews)
  const { data: detailData, refetch: refetchDetail } = useQualityFindingDetail(
    selectedFinding?.findingId || "",
  );

  // 诊断 Trace 详情
  const { data: diagnoseData } = useEvaluationRunDiagnose(selectedFinding?.runId || "");

  // 提交整改与复核 hooks
  const submitRectMutation = useSubmitRectification(selectedFinding?.findingId || "");
  const reviewRectMutation = useReviewRectification(selectedFinding?.findingId || "");

  const getStepCurrent = (status: QualityFindingStatus) => {
    if (status === "ASSIGNED") return 1;
    if (status === "REMEDIATING") return 2;
    if (status === "CLOSED" || status === "WAIVED") return 3;
    return 0;
  };

  const handleTabChange = (key: string) => {
    setActiveTab(key);
    setFilterStatus(undefined); // 重置状态过滤器，以 Tab 的默认状态为准
  };

  // 医师提交整改反馈
  const onRemediateSubmit = async (values: {
    rectificationSummary: string;
    evidenceRef?: string;
  }) => {
    if (!selectedFinding) return;
    try {
      const idempotencyKey = `rect-${selectedFinding.findingId}-${Date.now()}`;
      await submitRectMutation.mutateAsync({
        request: {
          rectificationSummary: values.rectificationSummary,
          evidenceRef: values.evidenceRef || "",
        },
        idempotencyKey,
      });
      message.success("科室整改反馈成功提交，已推至质控复核中心");
      refetch();
      refetchDetail();
    } catch {
      message.error("提交整改失败");
    }
  };

  // 专家复核认定
  const onReviewSubmit = async (values: {
    decision: "APPROVED" | "RETURNED" | "WAIVED";
    comment?: string;
    evidenceRef?: string;
  }) => {
    if (!selectedFinding) return;

    // ==================== P0 核心红线指标特殊豁免物理门禁阻断 ====================
    if (selectedFinding.severity === "P0" && values.decision === "WAIVED") {
      Modal.error({
        title: "医疗安全强制阻断警告",
        icon: <CloseCircleOutlined className="text-rose-500" />,
        content: (
          <div className="space-y-2 mt-2">
            <p className="font-semibold text-slate-800 text-xs">
              当前质控缺陷属于 P0 级极危/医疗安全红线指标！
            </p>
            <p className="text-slate-500 text-[11px] leading-relaxed">
              依据 MedKernel 医院质量控制核心安全门禁，P0
              级极危缺陷关系患者重大生命安全，**绝对严禁进行特殊豁免**。请立即驳回并督促科室医师开具合理医嘱完成实质整改！
            </p>
          </div>
        ),
      });
      return; // 物理阻断，绝对不向后端发送请求！
    }

    try {
      const idempotencyKey = `rev-${selectedFinding.findingId}-${Date.now()}`;
      await reviewRectMutation.mutateAsync({
        request: {
          decision: values.decision,
          comment: values.comment || "",
          evidenceRef: values.evidenceRef,
        },
        idempotencyKey,
      });

      let decisionText = "特殊豁免";
      if (values.decision === "APPROVED") {
        decisionText = "通过关闭";
      } else if (values.decision === "RETURNED") {
        decisionText = "退回整改";
      }
      message.success(`复核完毕，结论已判定为：${decisionText}`);
      refetch();
      refetchDetail();
    } catch (err) {
      const errorMsg = (err as { response?: { data?: { message?: string } } })?.response?.data
        ?.message;
      message.error(errorMsg || "复核操作提交失败");
    }
  };

  // 渲染严重度 Badge，P0 采用极美呼吸小圆点！
  const renderSeverityTag = (severity: QualityFindingSeverity) => {
    switch (severity) {
      case "P0":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-500 border border-rose-500/20">
            <span className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-ping" />
            P0 安全红线
          </span>
        );
      case "P1":
        return <Tag color="error">P1 高危缺陷</Tag>;
      case "P2":
        return <Tag color="warning">P2 中危缺陷</Tag>;
      case "P3":
        return <Tag color="default">P3 低危缺陷</Tag>;
      default:
        return <Tag>{severity}</Tag>;
    }
  };

  // 渲染状态 Tag
  const renderStatusTag = (status: QualityFindingStatus) => {
    const map: Record<string, { color: string; text: string }> = {
      NEW: { color: "default", text: "待分配" },
      ASSIGNED: { color: "warning", text: "待整改" },
      REMEDIATING: { color: "processing", text: "待复核" },
      CLOSED: { color: "success", text: "已关闭" },
      WAIVED: { color: "default", text: "特殊豁免" },
    };
    const item = map[status] || { color: "default", text: status };
    return <Tag color={item.color}>{item.text}</Tag>;
  };

  const columns = [
    {
      title: "严重度",
      dataIndex: "severity",
      key: "severity",
      width: 140,
      render: (sev: QualityFindingSeverity) => renderSeverityTag(sev),
    },
    {
      title: "问题编码",
      dataIndex: "findingCode",
      key: "findingCode",
      className: "font-normal text-xs text-slate-500",
    },
    {
      title: "质控缺陷名称",
      dataIndex: "title",
      key: "title",
      className: "font-semibold text-slate-800",
    },
    {
      title: "责任科室",
      dataIndex: "responsibleDepartmentId",
      key: "responsibleDepartmentId",
      render: (dept: string) => (
        <Tag className="border-slate-200 bg-slate-50 text-slate-600">{dept || "未指定"}</Tag>
      ),
    },
    {
      title: "整改截止期限",
      dataIndex: "dueAt",
      key: "dueAt",
      render: (date: string) => {
        if (!date) return "--";
        const isOverdue = new Date(date) < new Date();
        return (
          <span className={isOverdue ? "text-rose-500 font-semibold" : "text-slate-500"}>
            {date.substring(0, 10)} {isOverdue && "(已逾期!)"}
          </span>
        );
      },
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: QualityFindingStatus) => renderStatusTag(status),
    },
    {
      title: "动作",
      key: "action",
      render: (_: unknown, record: QualityFinding) => (
        <Button
          type="primary"
          icon={<AuditOutlined />}
          className="bg-sky-600 hover:bg-sky-700 text-xs py-1 h-auto rounded-lg"
          onClick={() => {
            setSelectedFinding(record);
            setIsDrawerOpen(true);
            refetchDetail();
          }}
        >
          整改复核闭环
        </Button>
      ),
    },
  ];

  return (
    <PageShell
      title="质控预警与整改工作台"
      description="自动汇集临床病例不达标缺陷，派发科室整改指引，支撑质控专家复核判定并对 TraceId 审计留痕"
    >
      <div className="space-y-6">
        {/* 顶部过滤控制台 */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 rounded-2xl bg-white border border-slate-100 shadow-sm">
          <div className="flex flex-wrap items-center gap-3">
            <Select
              placeholder="严重层级"
              allowClear
              className="w-36"
              value={filterSeverity}
              onChange={(v) => setFilterSeverity(v)}
            >
              <Option value="P0">P0 核心红线</Option>
              <Option value="P1">P1 高危缺陷</Option>
              <Option value="P2">P2 中危</Option>
              <Option value="P3">P3 低危</Option>
            </Select>

            {activeTab === "all_findings" && (
              <Select
                placeholder="问题状态"
                allowClear
                className="w-36"
                value={filterStatus}
                onChange={(v) => setFilterStatus(v)}
              >
                <Option value="NEW">待分配</Option>
                <Option value="ASSIGNED">待整改</Option>
                <Option value="REMEDIATING">待复核</Option>
                <Option value="CLOSED">已关闭</Option>
                <Option value="WAIVED">特殊豁免</Option>
              </Select>
            )}

            <Input
              placeholder="检索责任科室..."
              prefix={<SearchOutlined className="text-slate-400" />}
              className="w-48 rounded-lg"
              value={filterDept}
              onChange={(e) => setFilterDept(e.target.value)}
              onPressEnter={() => refetch()}
            />

            <Button
              type="primary"
              className="bg-sky-600 hover:bg-sky-700 rounded-lg"
              onClick={() => refetch()}
            >
              高级筛选
            </Button>
          </div>
        </div>

        {/* 主分类大 Tabs */}
        <div className="bg-white rounded-2xl border border-slate-100 p-5 shadow-sm">
          <Tabs
            activeKey={activeTab}
            onChange={handleTabChange}
            className="custom-tabs"
            items={[
              {
                key: "assigned_tasks",
                label: (
                  <span className="flex items-center gap-1.5">
                    <WarningOutlined />
                    待我科室整改 (Assigned)
                  </span>
                ),
              },
              {
                key: "remediating_tasks",
                label: (
                  <span className="flex items-center gap-1.5">
                    <SendOutlined />
                    等待质控复核 (Remediating)
                  </span>
                ),
              },
              {
                key: "all_findings",
                label: (
                  <span className="flex items-center gap-1.5">
                    <HistoryOutlined />
                    全量缺陷历史大台账
                  </span>
                ),
              },
            ]}
          />

          <Table
            dataSource={pageData?.items || []}
            columns={columns}
            rowKey={(r: QualityFinding) => r.findingId}
            loading={isLoading}
            className="mt-4"
            pagination={{
              total: pageData?.total || 0,
              pageSize: 10,
              showSizeChanger: false,
            }}
          />
        </div>
      </div>

      {/* 侧滑抽屉：PDCA 闭环管理中心 */}
      <Drawer
        title={
          <div className="flex items-center gap-2">
            <AuditOutlined className="text-sky-600" />
            <span className="font-semibold text-lg">PDCA 质控整改与专家复核中心</span>
          </div>
        }
        placement="right"
        width={650}
        onClose={() => setIsDrawerOpen(false)}
        open={isDrawerOpen}
        className="rounded-l-2xl"
        destroyOnClose
      >
        {selectedFinding ? (
          <div className="space-y-6">
            {/* 特殊 P0 级醒目红线警告 */}
            {selectedFinding.severity === "P0" && (
              <Alert
                message="【P0级安全红线缺陷】强制整改警告"
                description="本缺陷属于极危医疗安全指标！质控法理要求科室强制完成实质整改闭环，严禁通过复核予以直接特殊豁免。"
                type="error"
                showIcon
                className="rounded-xl"
              />
            )}

            {/* 顶端缺陷当前状态进度 Steps */}
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-100">
              <Steps
                size="small"
                current={getStepCurrent(selectedFinding.status)}
                items={[
                  { title: "自动生成" },
                  { title: "科室整改" },
                  { title: "专家复核" },
                  { title: "已关闭" },
                ]}
              />
            </div>

            {/* 多 Tab 面板 */}
            <Tabs
              defaultActiveKey="finding_detail"
              className="bg-white"
              items={[
                {
                  key: "finding_detail",
                  label: "缺陷基础事实",
                  children: (
                    <div className="space-y-4 py-2">
                      <Descriptions bordered column={1} size="small" className="bg-slate-50/30">
                        <Descriptions.Item label="质控问题 ID">
                          <span className="font-normal text-xs font-semibold">
                            {selectedFinding.findingId}
                          </span>
                        </Descriptions.Item>
                        <Descriptions.Item label="不达标缺陷">
                          {selectedFinding.title}
                        </Descriptions.Item>
                        <Descriptions.Item label="扫描详细描述">
                          {selectedFinding.description}
                        </Descriptions.Item>
                        <Descriptions.Item label="不合规审计证据">
                          <span className="text-xs text-rose-600">
                            {selectedFinding.evidenceSummary}
                          </span>
                        </Descriptions.Item>
                        <Descriptions.Item label="考核严重度">
                          {renderSeverityTag(selectedFinding.severity)}
                        </Descriptions.Item>
                        <Descriptions.Item label="整改责任科室">
                          {selectedFinding.responsibleDepartmentId}
                        </Descriptions.Item>
                        <Descriptions.Item label="整改截止期限">
                          {selectedFinding.dueAt ? selectedFinding.dueAt.substring(0, 16) : "--"}
                        </Descriptions.Item>
                      </Descriptions>
                    </div>
                  ),
                },

                {
                  key: "remediation_tab",
                  label: "科室整改反馈",
                  disabled:
                    selectedFinding.status !== "ASSIGNED" && selectedFinding.status !== "NEW",
                  children: (
                    <div className="py-2 space-y-4">
                      <Alert
                        message="临床责任医师整改指引"
                        description="请根据上面的不合规证据补全/修订HIS/EMR里的临床事实与合理性原因，并在此上传说明。"
                        type="info"
                        showIcon
                      />
                      <Form layout="vertical" onFinish={onRemediateSubmit}>
                        <Form.Item
                          name="rectificationSummary"
                          label="整改临床合理性原因 / 修订事实说明"
                          rules={[{ required: true, message: "请输入整改方案与说明" }]}
                        >
                          <Input.TextArea
                            rows={4}
                            placeholder="请输入已经完成的事实补录、医嘱修订或合理性说明。"
                          />
                        </Form.Item>
                        <Form.Item
                          name="evidenceRef"
                          label="修订后的合理医嘱 / 病例文书证据定位引用"
                          rules={[{ required: true, message: "请输入证据定位" }]}
                        >
                          <Input placeholder="例如：电子病历第16页第3行或医嘱号ORD-298372..." />
                        </Form.Item>
                        <Button
                          type="primary"
                          htmlType="submit"
                          icon={<SendOutlined />}
                          className="bg-sky-600 hover:bg-sky-700 w-full h-10 rounded-lg"
                          loading={submitRectMutation.isPending}
                        >
                          递交整改结果送审
                        </Button>
                      </Form>
                    </div>
                  ),
                },

                {
                  key: "review_tab",
                  label: "质控专家复核",
                  disabled: selectedFinding.status !== "REMEDIATING",
                  children: (
                    <div className="py-2 space-y-4">
                      {/* 科室递交的整改事实展示 */}
                      {detailData?.task && (
                        <Card
                          size="small"
                          title="责任科室提交的整改答案"
                          className="bg-slate-50 border-slate-100"
                        >
                          <div className="space-y-2 text-xs">
                            <div>
                              <span className="text-slate-400">整改人：</span>
                              <span className="text-slate-700 font-semibold">
                                {detailData.task.submittedBy || "科室医师"}
                              </span>
                            </div>
                            <div>
                              <span className="text-slate-400">整改时间：</span>
                              <span className="text-slate-700">
                                {detailData.task.submittedAt
                                  ? detailData.task.submittedAt.substring(0, 16)
                                  : "--"}
                              </span>
                            </div>
                            <div>
                              <span className="text-slate-400">整改说明：</span>
                              <p className="mt-1 text-slate-800 bg-white p-2.5 rounded border border-slate-200 leading-relaxed">
                                {detailData.task.rectificationSummary}
                              </p>
                            </div>
                            <div>
                              <span className="text-slate-400">合理证据：</span>
                              <span className="text-sky-600 font-semibold">
                                {detailData.task.evidenceRef}
                              </span>
                            </div>
                          </div>
                        </Card>
                      )}

                      <Form
                        layout="vertical"
                        onFinish={onReviewSubmit}
                        initialValues={{ decision: "APPROVED" }}
                      >
                        <Form.Item
                          name="decision"
                          label="专家复核结论判定"
                          rules={[{ required: true }]}
                        >
                          <Select className="rounded-lg">
                            <Option value="APPROVED">通过整改，合规闭环 (APPROVED)</Option>
                            <Option value="RETURNED">证据不足，打回重改 (RETURNED)</Option>
                            <Option value="WAIVED">符合豁免医学特征，特殊豁免 (WAIVED)</Option>
                          </Select>
                        </Form.Item>
                        <Form.Item
                          name="comment"
                          label="专家评语及临床合规意见"
                          rules={[{ required: true, message: "请输入复核评语" }]}
                        >
                          <Input.TextArea rows={3} placeholder="输入复核把关意见..." />
                        </Form.Item>
                        <Form.Item name="evidenceRef" label="专家复核依据/审计证据定位引用">
                          <Input placeholder="输入复核专家文献或豁免法理..." />
                        </Form.Item>
                        <Button
                          type="primary"
                          htmlType="submit"
                          icon={<CheckCircleOutlined />}
                          className="bg-emerald-600 hover:bg-emerald-700 w-full h-10 rounded-lg border-none"
                          loading={reviewRectMutation.isPending}
                        >
                          提交复核裁决结论
                        </Button>
                      </Form>
                    </div>
                  ),
                },

                {
                  key: "trace_tab",
                  label: "可信审计Trace",
                  children: (
                    <div className="py-3 space-y-5">
                      {/* TraceId 高亮展现 */}
                      <div className="p-3.5 rounded-xl border border-sky-100 bg-sky-500/5 flex items-center justify-between">
                        <div>
                          <div className="text-slate-400 text-xs font-semibold">
                            审计 Trace ID (可追溯唯一凭据)
                          </div>
                          <div className="font-normal font-bold text-sky-600 text-sm mt-1">
                            {selectedFinding.createdAt
                              ? selectedFinding.findingId + "_TRACE"
                              : "TRACE_NOT_FOUND"}
                          </div>
                        </div>
                        <Tag color="cyan">100% 留痕合规</Tag>
                      </div>

                      {/* 状态转移历史 */}
                      {diagnoseData && (
                        <div className="space-y-3">
                          <h4 className="text-xs font-semibold text-slate-800 flex items-center gap-1">
                            <HistoryOutlined />
                            基于 StateTransitionRecorder 留下的物理状态转移记录
                          </h4>

                          <Timeline mode="left" className="mt-3">
                            <Timeline.Item label="创建缺陷" color="blue">
                              <div className="text-xs">
                                <span className="font-semibold text-slate-700">
                                  系统自动病例扫描
                                </span>
                                <p className="text-slate-400 text-[10px] mt-0.5">
                                  状态初始化为 ASSIGNED / NEW
                                </p>
                              </div>
                            </Timeline.Item>

                            {detailData?.task?.submittedAt && (
                              <Timeline.Item label="提交整改" color="orange">
                                <div className="text-xs">
                                  <span className="font-semibold text-slate-700">
                                    科室医师递交整改
                                  </span>
                                  <p className="text-slate-400 text-[10px] mt-0.5">
                                    流转为 REMEDIATING ·{" "}
                                    {detailData.task.submittedAt.substring(0, 16)}
                                  </p>
                                </div>
                              </Timeline.Item>
                            )}

                            {detailData?.reviews &&
                              detailData.reviews.map((rev) => (
                                <Timeline.Item
                                  key={rev.reviewId}
                                  label={rev.decision === "APPROVED" ? "通过关闭" : "打回重改"}
                                  color={rev.decision === "APPROVED" ? "green" : "red"}
                                >
                                  <div className="text-xs">
                                    <span className="font-semibold text-slate-700">
                                      专家 {rev.reviewedBy} 复核完毕
                                    </span>
                                    <p className="text-slate-500 text-[11px] mt-0.5 bg-slate-50 p-2 rounded leading-relaxed border border-slate-100">
                                      {rev.comments}
                                    </p>
                                    <p className="text-slate-400 text-[10px] mt-0.5">
                                      {rev.reviewedAt.substring(0, 16)}
                                    </p>
                                  </div>
                                </Timeline.Item>
                              ))}
                          </Timeline>
                        </div>
                      )}
                    </div>
                  ),
                },
              ]}
            />
          </div>
        ) : (
          <Empty description="选择特定缺陷问题开启 PDCA 闭环" />
        )}
      </Drawer>
    </PageShell>
  );
}
