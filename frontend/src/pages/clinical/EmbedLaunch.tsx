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
import type { EmbedFeedbackResponse } from "@/shared/api/hooks";
import { riskLabel } from "@/shared/config/customerLabels";
import styles from "./EmbedLaunch.module.css";

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
  const [feedbackDelivery, setFeedbackDelivery] = useState<EmbedFeedbackResponse | null>(null);

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
        const result = await submitFeedbackMutation.mutateAsync({
          token,
          actionType: "ADOPT",
          reason: "医师确认符合临床指征并予以执行",
        });
        message.success("已记录采纳决策。");
        setFeedbackDelivery(result);
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
      const result = await submitFeedbackMutation.mutateAsync({
        token,
        actionType: "REJECT",
        reason: finalReason,
      });
      message.info("已记录不采纳决策。");
      setFeedbackDelivery(result);
      setFeedbackVisible(false);
      setSubmittedFeedback(true);
      sendPostMessage("REJECT", finalReason);
    } catch {
      message.error("拒绝反馈提交失败，未向集成方发送拒绝事件。");
    }
  };

  // 通过 window.postMessage 向父级工作站（集成方）派发物理双向通知事件
  const sendPostMessage = (actionType: "ADOPT" | "REJECT", reasonText: string) => {
    const parentOrigin = launchContext?.parentOrigin;
    if (!parentOrigin) return false;

    const eventData = {
      source: "MEDKERNEL_CDSS_EMBED",
      action: actionType,
      reason: reasonText,
      patientId: launchContext.patientId,
      encounterId: launchContext.encounterId,
      triggerPoint: launchContext.triggerPoint,
      timestamp: new Date().toISOString(),
      traceId: launchContext.traceId,
    };
    window.parent.postMessage(eventData, parentOrigin);
    return true;
  };

  // 针对单次原子消费令牌，在兑换失败、或者刷新导致 USED 时优雅降级至安全隔离状态
  const isSessionInvalid = !token || launchError || (!loadingLaunch && !launchContext?.active);

  if (loadingLaunch) {
    return (
      <div className={styles.centerState}>
        <Spin size="large" />
        <div className={styles.loadingText}>正在兑换一次性启动令牌并核查当前就诊上下文</div>
      </div>
    );
  }

  if (isSessionInvalid || sessionClosed) {
    return (
      <div className={styles.centerState}>
        <div className={styles.statePanel}>
          <WarningOutlined className={styles.stateIcon} />
          <div className={styles.stateTitle}>临床建议会话已安全隔离</div>
          <div className={styles.stateBody}>
            当前会话无法继续：
            <ul className={styles.bulletList}>
              <li>就诊 Launch Token 仅允许消费一次。</li>
              <li>当前嵌入令牌已被单次兑换结案或失效。</li>
              <li>嵌入来源未通过当前服务空间的来源域名白名单校验。</li>
            </ul>
          </div>
          <Alert
            message="请在 HIS / EMR 系统中重新发起点击，以瞬间原子化拉起当前患者的智能决策终端。"
            type="error"
            className={styles.stateAlert}
          />
        </div>
      </div>
    );
  }

  return (
    <div className={styles.shell}>
      <div className={styles.topBar}>
        <div className={styles.contextRow}>
          <Badge status="processing" />
          <span className={styles.contextLabel}>当前就诊上下文</span>
          <Tag color="cyan" className={styles.contextTag}>
            患者: {launchContext?.patientId}
          </Tag>
          <Tag color="blue" className={styles.contextTag}>
            就诊: {launchContext?.encounterId}
          </Tag>
          <Tag color="purple" className={styles.contextTag}>
            触发点: {launchContext?.triggerPoint}
          </Tag>
        </div>
        <div className={styles.brandStatus}>
          <HeartOutlined className={styles.heartIcon} />
          <span>MedKernel 临床建议已连接</span>
        </div>
      </div>

      <div className={styles.content}>
        {submittedFeedback && (
          <div className={styles.feedbackPanel}>
            <CheckCircleOutlined className={styles.successIcon} />
            <div className={styles.successTitle}>医师反馈已记录并留痕</div>
            <div className={styles.feedbackMeta}>
              反馈结果：
              <Tag color={selectedAction === "ADOPT" ? "green" : "red"}>
                {selectedAction === "ADOPT" ? "采纳建议" : "拒绝建议"}
              </Tag>
            </div>
            {selectedAction === "REJECT" && (
              <div className={styles.rejectReason}>
                拒绝理由：{rejectReason === "OTHER" ? customReason : rejectReason}
              </div>
            )}
            <Alert
              message="已向通过白名单校验的父工作站发送交互事件"
              description={
                feedbackDelivery?.callbackDelivered
                  ? "服务端回调已送达，当前会话可以安全退出。"
                  : `服务端回调未送达：${feedbackDelivery?.degradationReason || "未返回原因"}。反馈审计记录已保留。`
              }
              type={feedbackDelivery?.callbackDelivered ? "success" : "warning"}
              showIcon
              className={styles.feedbackAlert}
            />
            <Button
              type="default"
              size="small"
              onClick={() => setSessionClosed(true)}
              className={styles.sessionButton}
            >
              安全退出会话
            </Button>
          </div>
        )}
        {!submittedFeedback && loadingCards && (
          <div className={styles.loadingPanel}>
            <Spin />
            <div className={styles.loadingMessage}>正在读取当前就诊的真实临床建议</div>
          </div>
        )}
        {!submittedFeedback && !loadingCards && cardsError && (
          <Alert
            type="error"
            showIcon
            message="临床建议接口读取失败"
            description="请返回工作站重试，或联系信息部门核查推荐服务状态。"
            className={styles.errorAlert}
          />
        )}
        {!submittedFeedback && !loadingCards && !cardsError && displayCards.length === 0 && (
          <div className={styles.emptyPanel}>
            <Empty
              description={
                <span className={styles.emptyDescription}>当前就诊暂无可显示的临床建议</span>
              }
            />
            <div className={styles.helperText}>
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
                  <div className={styles.cardTitle}>
                    <span className={styles.cardTitleLeft}>
                      <Badge color={isCritical ? "red" : "gold"} />
                      <span>{card.title}</span>
                    </span>
                    <Tag color={isCritical ? "red" : "orange"} className={styles.severityTag}>
                      {riskLabel(card.severity)}干预
                    </Tag>
                  </div>
                }
                className={styles.recommendationCard}
              >
                <div className={styles.stack}>
                  <div className={styles.summary}>{card.summary}</div>

                  <div className={styles.recommendationList}>
                    <div className={styles.recommendationTitle}>
                      <SendOutlined className={styles.recommendationIcon} />
                      <span>建议处置动作</span>
                    </div>
                    {card.recommendations?.map((rec, i) => (
                      <div key={i} className={styles.recommendationItem}>
                        <Tag color="cyan" className={styles.actionTag}>
                          {rec.actionType}
                        </Tag>
                        <span className={styles.actionText}>{rec.description}</span>
                      </div>
                    ))}
                  </div>

                  <Alert
                    message="质控合规依据及客观证据"
                    description={card.evidenceSummary}
                    type="warning"
                    showIcon
                    icon={<InfoCircleOutlined />}
                    className={styles.evidenceAlert}
                  />

                  <div className={styles.actions}>
                    <Button
                      type="primary"
                      onClick={() => handleDecision("ADOPT")}
                      icon={<CheckCircleOutlined />}
                      className={styles.adoptButton}
                    >
                      符合指征，确认开具采纳
                    </Button>
                    <Button
                      danger
                      onClick={() => handleDecision("REJECT")}
                      icon={<CloseCircleOutlined />}
                      className={styles.rejectButton}
                    >
                      拒绝采纳
                    </Button>
                  </div>
                </div>
              </Card>
            );
          })}
      </div>

      <div className={styles.auditBar}>
        <span className={styles.auditLabel}>
          <AuditOutlined /> 嵌入式交互合规审计凭证 traceId
        </span>
        <span className={styles.auditTrace}>{launchContext?.traceId || "暂无追踪链路"}</span>
      </div>

      <Modal
        title={
          <div className={styles.modalTitle}>
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
        okButtonProps={{ danger: true }}
      >
        <div className={styles.modalBody}>
          <div className={styles.modalHint}>
            作为医疗安全质控的必经环节，请选择并提供拒绝开具此建议的医学判断理由，以便提交至质控处备案：
          </div>

          <Radio.Group
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            className={styles.reasonGroup}
          >
            <Radio value="CLINICAL_MISMATCH">
              <span className={styles.reasonText}>患者临床表现及风险指征不符</span>
            </Radio>
            <Radio value="CONTRAINDICATION_EXISTS">
              <span className={styles.reasonText}>存在其他未被录入的绝对溶栓/用药禁忌症</span>
            </Radio>
            <Radio value="PATIENT_DECLINED">
              <span className={styles.reasonText}>患者及家属明确拒绝此项治疗方案</span>
            </Radio>
            <Radio value="OTHER">
              <span className={styles.reasonText}>其他理由（手动录入说明）</span>
            </Radio>
          </Radio.Group>

          {rejectReason === "OTHER" && (
            <TextArea
              rows={3}
              placeholder="请输入真实的临床判断拒绝理由，例如：患者已安排于明日进行急诊起搏器植入手术，故暂停用药预防。"
              value={customReason}
              onChange={(e) => setCustomReason(e.target.value)}
              className={styles.customReason}
            />
          )}
        </div>
      </Modal>
    </div>
  );
}
