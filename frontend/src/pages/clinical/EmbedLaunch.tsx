import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Card, Button, Tag, Alert, Spin, Radio, Input, Modal, Badge, Empty, message } from "antd";
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  WarningOutlined,
  AuditOutlined,
  InfoCircleOutlined,
  HeartOutlined,
  SendOutlined,
} from "@ant-design/icons";
import { useEmbedLaunch, useSubmitEmbedFeedback, useRecommendationCards } from "@/shared/api/hooks";

const { TextArea } = Input;

export default function EmbedLaunch() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") || "";

  // 1. 调用后端接口：使用 Launch Token 兑换就诊临床上下文
  const {
    data: launchContext,
    isLoading: loadingLaunch,
    isError: launchError,
  } = useEmbedLaunch(token);

  // 2. 状态原子定义
  const [sessionClosed, setSessionClosed] = useState<boolean>(false);
  const [feedbackVisible, setFeedbackVisible] = useState<boolean>(false);
  const [selectedAction, setSelectedAction] = useState<"ADOPT" | "REJECT">("ADOPT");
  const [rejectReason, setRejectReason] = useState<string>("");
  const [customReason, setCustomReason] = useState<string>("");
  const [submittedFeedback, setSubmittedFeedback] = useState<boolean>(false);

  // 3. 突变反馈 API
  const submitFeedbackMutation = useSubmitEmbedFeedback();

  // 4. 根据兑换出来的患者上下文，拉取对应的 CDSS 推荐卡片
  const recommendationsEnabled = Boolean(launchContext?.active && launchContext.patientId);
  const {
    data: apiCards,
    isLoading: loadingCards,
    isError: cardsError,
  } = useRecommendationCards(
    {
      patientId: launchContext?.patientId,
      status: "ACTIVE",
    },
    { enabled: recommendationsEnabled },
  );

  const displayCards = apiCards?.items ?? [];

  // 医师做决策并派发双向跨域通知
  const handleDecision = async (action: "ADOPT" | "REJECT") => {
    setSelectedAction(action);
    if (action === "REJECT") {
      setFeedbackVisible(true);
    } else {
      // 采纳临床建议
      try {
        await submitFeedbackMutation.mutateAsync({
          token,
          actionType: "ADOPT",
          reason: "医师确认符合临床指征并予以执行",
        });
        message.success("已成功采纳建议并提交反馈！");
        setSubmittedFeedback(true);
        sendPostMessage("ADOPT", "医师确认符合临床指征并予以执行");
      } catch {
        message.error("建议采纳提交失败，未向集成方发送采纳事件。");
      }
    }
  };

  // 提交拒绝采纳反馈
  const handleSubmitReject = async () => {
    const finalReason = rejectReason === "OTHER" ? customReason : rejectReason;
    if (!finalReason) {
      message.warning("请选择或输入具体的拒绝建议理由！");
      return;
    }

    try {
      await submitFeedbackMutation.mutateAsync({
        token,
        actionType: "REJECT",
        reason: finalReason,
      });
      message.info("已拒绝该建议，感谢您的专业反馈！");
      setFeedbackVisible(false);
      setSubmittedFeedback(true);
      sendPostMessage("REJECT", finalReason);
    } catch {
      message.error("拒绝反馈提交失败，未向集成方发送拒绝事件。");
    }
  };

  // 通过 window.postMessage 向父级工作站（集成方）派发物理双向通知事件
  const sendPostMessage = (actionType: "ADOPT" | "REJECT", reasonText: string) => {
    if (window.parent) {
      const eventData = {
        source: "MEDKERNEL_CDSS_EMBED",
        action: actionType,
        reason: reasonText,
        patientId: launchContext?.patientId || "未提供患者标识",
        encounterId: launchContext?.encounterId || "未提供就诊标识",
        triggerPoint: launchContext?.triggerPoint || "未提供触发点",
        timestamp: new Date().toISOString(),
        traceId: launchContext?.traceId || "未提供追踪链路",
      };
      window.parent.postMessage(eventData, "*");
    }
  };

  // 针对单次原子消费令牌，在兑换失败、或者刷新导致 USED 时优雅降级至安全隔离状态
  const isSessionInvalid = !token || launchError || (!loadingLaunch && !launchContext?.active);

  if (loadingLaunch) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-slate-900 text-slate-100 p-6 text-center">
        <Spin size="large" />
        <div className="mt-4 font-medium text-slate-400 text-sm animate-pulse">
          集团医疗智能中枢正在进行一次性 Launch Token 安全兑换与就诊事实核查...
        </div>
      </div>
    );
  }

  if (isSessionInvalid || sessionClosed) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-slate-950 text-slate-200 p-8 text-center">
        <div className="bg-rose-950/20 border border-rose-900/30 p-8 rounded-2xl max-w-md shadow-2xl flex flex-col items-center gap-4 animate-fadeIn">
          <WarningOutlined className="text-4xl text-rose-500 animate-pulse" />
          <div className="text-lg font-bold text-rose-400">页面嵌入式临床建议会话已安全隔离</div>
          <div className="text-xs text-slate-400 leading-relaxed text-left bg-slate-900 p-4 rounded-xl border border-slate-800">
            为了符合国家卫健委对电子病历互联互通和医院信息安全的物理红线规范：
            <ul className="list-disc pl-5 mt-2 flex flex-col gap-1">
              <li>就诊 Launch Token 仅允许消费一次。</li>
              <li>当前嵌入令牌已被单次兑换结案或失效。</li>
              <li>检测到非法的跨域嵌入来源（CSRF 防护启用）。</li>
            </ul>
          </div>
          <Alert
            message="请在 HIS / EMR 系统中重新发起点击，以瞬间原子化拉起当前患者的智能决策终端。"
            type="error"
            className="text-[11px] rounded-lg text-left mt-2"
          />
        </div>
      </div>
    );
  }

  return (
    <div className="bg-slate-900 min-h-screen text-slate-100 p-4 flex flex-col gap-4">
      {/* 顶栏事实 Badge 面板 (紧凑精简设计，最大化 iframe 空间) */}
      <div className="bg-slate-800/80 p-3.5 rounded-xl border border-slate-700/60 flex items-center justify-between shadow-sm">
        <div className="flex items-center gap-3">
          <Badge status="processing" className="animate-pulse" />
          <span className="text-xs text-slate-400">当前嵌入就诊上下文 facts：</span>
          <Tag color="cyan" className="font-semibold m-0 text-xs">
            患者: {launchContext?.patientId}
          </Tag>
          <Tag color="blue" className="font-semibold m-0 text-xs">
            就诊: {launchContext?.encounterId}
          </Tag>
          <Tag color="purple" className="m-0 text-[10px] font-normal">
            触发点: {launchContext?.triggerPoint}
          </Tag>
        </div>
        <div className="text-[11px] text-slate-500 flex items-center gap-1 font-medium">
          <HeartOutlined className="text-rose-500" />
          <span>MedKernel 智能决策内核已激活</span>
        </div>
      </div>

      {/* 临床决策卡片 Panel 区域 */}
      <div className="flex flex-col gap-4 flex-1 overflow-y-auto pr-1">
        {submittedFeedback && (
          <div className="bg-slate-800 border border-slate-700 p-8 rounded-2xl text-center shadow-lg animate-fadeIn">
            <CheckCircleOutlined className="text-4xl text-emerald-500 mb-2 animate-bounce" />
            <div className="text-sm font-bold text-emerald-400">医师反馈决策已安全同步并留痕！</div>
            <div className="text-xs text-slate-400 mt-2">
              反馈结果：
              <Tag color={selectedAction === "ADOPT" ? "green" : "red"}>
                {selectedAction === "ADOPT" ? "采纳建议" : "拒绝建议"}
              </Tag>
            </div>
            {selectedAction === "REJECT" && (
              <div className="text-xs text-rose-300 mt-2 bg-rose-950/20 py-2 px-3 rounded-lg max-w-sm mx-auto">
                拒绝理由：{rejectReason === "OTHER" ? customReason : rejectReason}
              </div>
            )}
            <Alert
              message="事件已通过 postMessage 双向同步回核心工作站。为了审计安全性，请完成后主动退出当前嵌入式令牌会话。"
              type="info"
              showIcon
              className="text-left max-w-md mx-auto mt-4 text-xs bg-slate-900 border-slate-800 text-slate-300"
            />
            <Button
              type="default"
              size="small"
              onClick={() => setSessionClosed(true)}
              className="mt-4 rounded-lg bg-slate-700 border-slate-600 text-slate-200 hover:bg-slate-600"
            >
              立即安全隔离退出
            </Button>
          </div>
        )}
        {!submittedFeedback && loadingCards && (
          <div className="bg-slate-800 border border-slate-700 p-8 rounded-2xl text-center">
            <Spin />
            <div className="mt-3 text-xs text-slate-400">正在读取当前就诊的真实临床建议...</div>
          </div>
        )}
        {!submittedFeedback && !loadingCards && cardsError && (
          <Alert
            type="error"
            showIcon
            message="临床建议接口读取失败"
            description="请返回工作站重试，或联系信息部门核查推荐服务状态。"
            className="bg-slate-800 border-slate-700 text-slate-200"
          />
        )}
        {!submittedFeedback && !loadingCards && !cardsError && displayCards.length === 0 && (
          <div className="bg-slate-800 border border-slate-700 p-10 rounded-2xl">
            <Empty
              description={<span className="text-slate-300">当前就诊暂无可显示的临床建议</span>}
            />
            <div className="text-center text-xs text-slate-500 mt-3">
              请确认推荐引擎已为当前就诊生成有效建议，或返回工作站重新触发。
            </div>
          </div>
        )}
        {!submittedFeedback &&
          !loadingCards &&
          !cardsError &&
          displayCards.length > 0 &&
          displayCards.map((card) => {
            const isCritical = card.severity === "CRITICAL";
            return (
              <Card
                key={card.cardId}
                title={
                  <div className="flex justify-between items-center w-full py-1 text-slate-100 text-xs font-semibold">
                    <span className="flex items-center gap-1.5">
                      <Badge color={isCritical ? "red" : "gold"} className="animate-ping" />
                      <span>{card.title}</span>
                    </span>
                    <Tag color={isCritical ? "red" : "orange"} className="text-[10px] m-0">
                      {card.severity} 级干预
                    </Tag>
                  </div>
                }
                className="bg-slate-800 border-slate-700/80 shadow-md rounded-2xl hover:border-slate-600 transition-all duration-300"
                bodyStyle={{ padding: "16px" }}
              >
                <div className="flex flex-col gap-3">
                  {/* 摘要与背景依据 */}
                  <div className="text-xs text-slate-300 bg-slate-900/60 p-3.5 rounded-xl border border-slate-700/30 leading-relaxed font-medium">
                    {card.summary}
                  </div>

                  {/* 核心建议列表 */}
                  <div className="flex flex-col gap-2">
                    <div className="text-[11px] text-slate-400 font-semibold flex items-center gap-1">
                      <SendOutlined className="text-sky-500" />
                      <span>决策引擎推荐开具处方/处置动作：</span>
                    </div>
                    {card.recommendations?.map((rec, i) => (
                      <div
                        key={i}
                        className="bg-slate-900/30 border border-slate-700/50 p-2.5 rounded-lg text-xs flex items-start gap-2"
                      >
                        <Tag color="cyan" className="text-[9px] font-semibold mt-0.5 m-0 px-1">
                          {rec.actionType}
                        </Tag>
                        <span className="text-slate-200 font-medium leading-relaxed">
                          {rec.description}
                        </span>
                      </div>
                    ))}
                  </div>

                  {/* 医学证据链 */}
                  <Alert
                    message="质控合规依据及客观证据"
                    description={card.evidenceSummary}
                    type="warning"
                    showIcon
                    icon={<InfoCircleOutlined />}
                    className="text-[11px] bg-slate-900/40 border-slate-800 rounded-lg text-slate-300"
                  />

                  {/* 医师采纳与双向反馈 Action Area */}
                  <div className="flex gap-4 mt-2">
                    <Button
                      type="primary"
                      onClick={() => handleDecision("ADOPT")}
                      icon={<CheckCircleOutlined />}
                      className="flex-1 py-4 font-semibold rounded-lg bg-emerald-600 border-emerald-600 hover:bg-emerald-700 hover:border-emerald-700 flex items-center justify-center gap-1"
                    >
                      符合指征，确认开具采纳
                    </Button>
                    <Button
                      danger
                      onClick={() => handleDecision("REJECT")}
                      icon={<CloseCircleOutlined />}
                      className="py-4 font-semibold rounded-lg bg-rose-950/20 border-rose-900/30 hover:bg-rose-950/40 flex items-center justify-center gap-1"
                    >
                      拒绝采纳
                    </Button>
                  </div>
                </div>
              </Card>
            );
          })}
      </div>

      {/* 底部审计存证信息 */}
      <div className="bg-slate-950/50 p-3 rounded-lg border border-slate-800/40 flex items-center justify-between text-[10px] text-slate-500">
        <span className="flex items-center gap-1">
          <AuditOutlined /> 嵌入式交互合规审计凭证 traceId
        </span>
        <span className="font-normal bg-slate-900 px-2 py-0.5 rounded text-slate-400">
          {launchContext?.traceId || "暂无追踪链路"}
        </span>
      </div>

      {/* Modal: 拒绝采纳理由收集弹窗 */}
      <Modal
        title={
          <div className="flex items-center gap-2 text-rose-700 font-bold border-b border-slate-100 pb-3">
            <CloseCircleOutlined />
            <span>临床拒绝采纳建议审计理由备案</span>
          </div>
        }
        open={feedbackVisible}
        onOk={handleSubmitReject}
        onCancel={() => setFeedbackVisible(false)}
        width={480}
        okText="提交备案"
        cancelText="取消"
        okButtonProps={{ className: "bg-rose-600 border-rose-600 hover:bg-rose-700" }}
      >
        <div className="mt-4 flex flex-col gap-4">
          <div className="text-xs text-slate-500">
            作为医疗安全质控的必经环节，请选择并提供拒绝开具此建议的医学判断理由，以便提交至质控处备案：
          </div>

          <Radio.Group
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            className="flex flex-col gap-2.5 w-full bg-slate-50 p-4 rounded-xl border border-slate-200"
          >
            <Radio value="CLINICAL_MISMATCH">
              <span className="text-xs text-slate-700 font-medium">患者临床表现及风险指征不符</span>
            </Radio>
            <Radio value="CONTRAINDICATION_EXISTS">
              <span className="text-xs text-slate-700 font-medium">
                存在其他未被录入的绝对溶栓/用药禁忌症
              </span>
            </Radio>
            <Radio value="PATIENT_DECLINED">
              <span className="text-xs text-slate-700 font-medium">
                患者及家属明确拒绝此项治疗方案
              </span>
            </Radio>
            <Radio value="OTHER">
              <span className="text-xs text-slate-700 font-medium">其他理由（手动录入说明）</span>
            </Radio>
          </Radio.Group>

          {rejectReason === "OTHER" && (
            <TextArea
              rows={3}
              placeholder="请输入真实的临床判断拒绝理由，例如：患者已安排于明日进行急诊起搏器植入手术，故暂停用药预防。"
              value={customReason}
              onChange={(e) => setCustomReason(e.target.value)}
              className="rounded-lg text-xs"
            />
          )}
        </div>
      </Modal>
    </div>
  );
}
