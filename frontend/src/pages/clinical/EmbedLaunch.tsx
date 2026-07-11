import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Alert,
  App as AntdApp,
  Badge,
  Button,
  Card,
  Dropdown,
  Empty,
  Input,
  Modal,
  Radio,
  Spin,
  Switch,
  Tag,
  Tooltip,
} from "antd";
import {
  AuditOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  HeartOutlined,
  InfoCircleOutlined,
  MoreOutlined,
  SendOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import {
  useEmbedLaunch,
  useEmbedRecommendationCards,
  useSubmitEmbedFeedback,
} from "@/shared/api/hooks";
import type { EmbedFeedbackAction, EmbedFeedbackResponse } from "@/shared/api/hooks";
import { riskLabel } from "@/shared/config/customerLabels";
import styles from "./EmbedLaunch.module.css";

const { TextArea } = Input;

const actionLabels: Record<EmbedFeedbackAction, string> = {
  ADOPT: "采纳建议",
  REJECT: "不采纳",
  LATER: "稍后处理",
  IGNORE: "忽略本次",
  CLOSE: "关闭建议",
};

const alternateActionReasons: Record<"LATER" | "IGNORE" | "CLOSE", string> = {
  LATER: "医师选择稍后处理",
  IGNORE: "医师忽略本次建议",
  CLOSE: "医师关闭本次建议",
};

const triggerPointLabels: Record<string, string> = {
  ORDER_ENTRY: "医嘱录入",
  CHART_REVIEW: "病历浏览",
  DISCHARGE_REVIEW: "出院审核",
  NURSING_REVIEW: "护理评估",
};

function triggerPointText(triggerPoint?: string | null, evidenceDetailsEnabled = false) {
  if (evidenceDetailsEnabled) {
    return triggerPoint || "未返回";
  }
  return triggerPointLabels[triggerPoint ?? ""] ?? "业务触发";
}

export default function EmbedLaunch() {
  const { message } = AntdApp.useApp();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") || "";
  const {
    data: launchContext,
    isLoading: loadingLaunch,
    isError: launchError,
  } = useEmbedLaunch(token);

  const [sessionClosed, setSessionClosed] = useState(false);
  const [feedbackVisible, setFeedbackVisible] = useState(false);
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [selectedAction, setSelectedAction] = useState<EmbedFeedbackAction>("ADOPT");
  const [rejectReason, setRejectReason] = useState("");
  const [customReason, setCustomReason] = useState("");
  const [submittedFeedback, setSubmittedFeedback] = useState(false);
  const [feedbackDelivery, setFeedbackDelivery] = useState<EmbedFeedbackResponse | null>(null);
  const [evidenceDetailsEnabled, setEvidenceDetailsEnabled] = useState(false);

  const submitFeedbackMutation = useSubmitEmbedFeedback();
  const recommendationsEnabled = Boolean(launchContext?.active);
  const {
    data: apiCards,
    isLoading: loadingCards,
    isError: cardsError,
  } = useEmbedRecommendationCards(token, recommendationsEnabled);
  const displayCards = apiCards?.items ?? [];

  const sendPostMessage = (
    actionType: EmbedFeedbackAction,
    cardId: string,
    reasonText: string,
    recommendationStatus: string,
  ) => {
    const parentOrigin = launchContext?.parentOrigin;
    if (!parentOrigin) return false;

    window.parent.postMessage(
      {
        source: "MEDKERNEL_CDSS_EMBED",
        action: actionType,
        cardId,
        reason: reasonText,
        recommendationStatus,
        patientId: launchContext.patientId,
        encounterId: launchContext.encounterId,
        triggerPoint: launchContext.triggerPoint,
        timestamp: new Date().toISOString(),
        traceId: launchContext.traceId,
      },
      parentOrigin,
    );
    return true;
  };

  const submitDecision = async (
    cardId: string,
    actionType: EmbedFeedbackAction,
    reason: string,
  ) => {
    setSelectedCardId(cardId);
    setSelectedAction(actionType);
    try {
      const result = await submitFeedbackMutation.mutateAsync({
        token,
        cardId,
        actionType,
        reason,
      });
      setFeedbackDelivery(result);
      setSubmittedFeedback(true);
      sendPostMessage(actionType, cardId, reason, result.recommendationStatus);
      message.success(`已记录“${actionLabels[actionType]}”决策。`);
      return true;
    } catch {
      message.error(`${actionLabels[actionType]}提交失败，未向集成方发送交互事件。`);
      return false;
    }
  };

  const handleAdopt = (cardId: string) =>
    submitDecision(cardId, "ADOPT", "医师确认符合临床指征并采纳建议");

  const handleReject = (cardId: string) => {
    setSelectedCardId(cardId);
    setSelectedAction("REJECT");
    setRejectReason("");
    setCustomReason("");
    setFeedbackVisible(true);
  };

  const handleAlternateAction = (cardId: string, actionType: "LATER" | "IGNORE" | "CLOSE") =>
    submitDecision(cardId, actionType, alternateActionReasons[actionType]);

  const handleSubmitReject = async () => {
    const finalReason = rejectReason === "OTHER" ? customReason.trim() : rejectReason;
    if (!selectedCardId || !finalReason) {
      message.warning("请选择或输入具体的不采纳理由。");
      return;
    }
    if (await submitDecision(selectedCardId, "REJECT", finalReason)) {
      setFeedbackVisible(false);
    }
  };

  const isSessionInvalid = !token || launchError || (!loadingLaunch && !launchContext?.active);

  if (loadingLaunch) {
    return (
      <div className={styles.centerState}>
        <Spin size="large" />
        <div className={styles.loadingText}>正在校验一次性启动凭证并核查当前就诊上下文</div>
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
              <li>一次性启动凭证仅允许使用一次。</li>
              <li>当前嵌入会话已结案或失效。</li>
              <li>来源系统未通过当前服务机构的允许清单校验。</li>
            </ul>
          </div>
          <Alert
            message="请在 HIS / EMR 系统中重新发起当前患者的临床建议会话。"
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
            {evidenceDetailsEnabled
              ? `患者: ${launchContext?.patientId || "未返回"}`
              : "患者已关联"}
          </Tag>
          <Tag color="blue" className={styles.contextTag}>
            {evidenceDetailsEnabled
              ? `就诊: ${launchContext?.encounterId || "未返回"}`
              : "就诊已关联"}
          </Tag>
          <Tag color="purple" className={styles.contextTag}>
            {`触发点: ${triggerPointText(launchContext?.triggerPoint, evidenceDetailsEnabled)}`}
          </Tag>
        </div>
        <div className={styles.brandCluster}>
          <div className={styles.brandStatus}>
            <HeartOutlined className={styles.heartIcon} />
            <span>MedKernel 临床建议已连接</span>
          </div>
          <div className={styles.evidenceToggle}>
            <Tooltip title="展开审计追溯、原始标识和受控诊断字段">
              <span>追溯证据</span>
            </Tooltip>
            <Switch
              aria-label="证据详情"
              size="small"
              checked={evidenceDetailsEnabled}
              onChange={setEvidenceDetailsEnabled}
            />
          </div>
        </div>
      </div>

      <div className={styles.content}>
        {submittedFeedback && (
          <div className={styles.feedbackPanel}>
            <CheckCircleOutlined className={styles.successIcon} />
            <div className={styles.successTitle}>医师反馈已记录并留痕</div>
            <div className={styles.feedbackMeta}>
              反馈结果：
              <Tag color={selectedAction === "REJECT" ? "red" : "green"}>
                {actionLabels[selectedAction]}
              </Tag>
            </div>
            <div className={styles.feedbackMeta}>
              {`建议卡片：${evidenceDetailsEnabled ? selectedCardId || "未返回" : "建议已记录"}`}
            </div>
            {selectedAction === "REJECT" && (
              <div className={styles.rejectReason}>
                不采纳理由：{rejectReason === "OTHER" ? customReason : rejectReason}
              </div>
            )}
            <Alert
              message="已向通过允许清单校验的来源工作站发送浏览器交互事件"
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
            message="临床建议读取失败"
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
              请确认提醒与推荐已为当前就诊生成有效建议，或返回工作站重新触发。
            </div>
          </div>
        )}
        {!submittedFeedback &&
          !loadingCards &&
          !cardsError &&
          displayCards.map((card) => {
            const isCritical = card.riskLevel === "CRITICAL" || card.riskLevel === "HIGH";
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
                      {riskLabel(card.riskLevel)}
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
                    <div className={styles.recommendationItem}>
                      <Tag color="cyan" className={styles.actionTag}>
                        需医师确认
                      </Tag>
                      <span className={styles.actionText}>{card.suggestedAction}</span>
                    </div>
                  </div>
                  <Alert
                    message="临床依据"
                    description={card.sourceSummary || "当前建议未返回可展示的来源摘要"}
                    type="warning"
                    showIcon
                    icon={<InfoCircleOutlined />}
                    className={styles.evidenceAlert}
                  />
                  <div className={styles.actions}>
                    <Button
                      type="primary"
                      onClick={() => void handleAdopt(card.cardId)}
                      icon={<CheckCircleOutlined />}
                      loading={submitFeedbackMutation.isPending}
                      className={styles.adoptButton}
                    >
                      采纳建议（仍需人工处置）
                    </Button>
                    <Button
                      danger
                      onClick={() => handleReject(card.cardId)}
                      icon={<CloseCircleOutlined />}
                      disabled={submitFeedbackMutation.isPending}
                      className={styles.rejectButton}
                    >
                      不采纳
                    </Button>
                    <Dropdown
                      trigger={["click"]}
                      menu={{
                        items: [
                          { key: "LATER", label: "稍后处理" },
                          { key: "IGNORE", label: "忽略本次" },
                          { key: "CLOSE", label: "关闭建议" },
                        ],
                        onClick: ({ key }) =>
                          void handleAlternateAction(
                            card.cardId,
                            key as "LATER" | "IGNORE" | "CLOSE",
                          ),
                      }}
                    >
                      <Button icon={<MoreOutlined />} disabled={submitFeedbackMutation.isPending}>
                        其他处理
                      </Button>
                    </Dropdown>
                  </div>
                </div>
              </Card>
            );
          })}
      </div>

      <div className={styles.auditBar}>
        <span className={styles.auditLabel}>
          <AuditOutlined />{" "}
          {evidenceDetailsEnabled ? "嵌入式交互合规审计追踪号" : "嵌入式交互合规审计"}
        </span>
        <span className={styles.auditTrace}>
          {evidenceDetailsEnabled ? launchContext?.traceId || "暂无追踪号" : "合规审计已留痕"}
        </span>
      </div>

      <Modal
        title={
          <div className={styles.modalTitle}>
            <CloseCircleOutlined />
            <span>不采纳临床建议理由备案</span>
          </div>
        }
        open={feedbackVisible}
        onOk={() => void handleSubmitReject()}
        onCancel={() => setFeedbackVisible(false)}
        confirmLoading={submitFeedbackMutation.isPending}
        width={480}
        okText="提交备案"
        cancelText="取消"
        okButtonProps={{ danger: true }}
      >
        <div className={styles.modalBody}>
          <div className={styles.modalHint}>
            请选择并提供当前临床判断理由；系统只记录决策，不自动生成医嘱。
          </div>
          <Radio.Group
            value={rejectReason}
            onChange={(event) => setRejectReason(event.target.value)}
            className={styles.reasonGroup}
          >
            <Radio value="CLINICAL_MISMATCH">
              <span className={styles.reasonText}>患者临床表现及风险指征不符</span>
            </Radio>
            <Radio value="CONTRAINDICATION_EXISTS">
              <span className={styles.reasonText}>存在其他未录入的用药或处置禁忌</span>
            </Radio>
            <Radio value="PATIENT_DECLINED">
              <span className={styles.reasonText}>患者及家属明确拒绝此项处置建议</span>
            </Radio>
            <Radio value="OTHER">
              <span className={styles.reasonText}>其他理由（手动录入说明）</span>
            </Radio>
          </Radio.Group>
          {rejectReason === "OTHER" && (
            <TextArea
              rows={3}
              placeholder="请输入真实的临床判断理由。"
              value={customReason}
              onChange={(event) => setCustomReason(event.target.value)}
              className={styles.customReason}
            />
          )}
        </div>
      </Modal>
    </div>
  );
}
