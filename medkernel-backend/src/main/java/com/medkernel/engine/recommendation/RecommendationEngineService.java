package com.medkernel.engine.recommendation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.BusinessMetrics;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GA-ENG-API-07 推荐/CDSS 服务（触发受控写入 + 推荐卡/来源/反馈/疲劳治理事实读写 + 诊断）。
 *
 * <p>负责推荐触发校验（高风险卡必须 {@code requiresPhysicianConfirmation=true}、强打断必须高风险、
 * 每张卡至少一条来源）、推荐卡状态机推进（{@link RecommendationCardStatus}）、
 * 反馈幂等记录、疲劳治理信号采集与阈值抑制、审计/状态历史/诊断聚合。
 * 不自动生成医嘱、诊断、病历或随访任务；
 * 错误码 {@code ENG_REC_001..ENG_REC_007} 覆盖参数/未找到/反馈终止态/来源缺失/高风险未确认等场景。
 */
@Service
public class RecommendationEngineService {

    private final RecommendationTriggerRepository triggers;
    private final RecommendationCardRepository cards;
    private final RecommendationSourceRepository sources;
    private final RecommendationFeedbackRepository feedback;
    private final RecommendationFatigueSignalRepository fatigueSignals;
    private final AuditEventPublisher auditPublisher;
    private final IsolatedAuditPublisher isolatedAudit;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final BusinessMetrics businessMetrics;

    public RecommendationEngineService(
            RecommendationTriggerRepository triggers,
            RecommendationCardRepository cards,
            RecommendationSourceRepository sources,
            RecommendationFeedbackRepository feedback,
            RecommendationFatigueSignalRepository fatigueSignals,
            AuditEventPublisher auditPublisher,
            StateTransitionRecorder transitions,
            DiagnoseResponseAssembler diagnoseAssembler,
            IsolatedAuditPublisher isolatedAudit,
            BusinessMetrics businessMetrics) {
        this.triggers = triggers;
        this.cards = cards;
        this.sources = sources;
        this.feedback = feedback;
        this.fatigueSignals = fatigueSignals;
        this.auditPublisher = auditPublisher;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.isolatedAudit = isolatedAudit;
        this.businessMetrics = businessMetrics;
    }

    /**
     * 接收推荐触发并把候选卡、来源、初始疲劳信号一并落库。
     *
     * <p>触发状态由候选卡数量决定（{@link RecommendationTriggerStatus#EVALUATED}/
     * {@link RecommendationTriggerStatus#NO_CARD}）；
     * 候选卡来源为空抛 {@code ENG_REC_005}，高风险未确认抛 {@code ENG_REC_006}，
     * 强打断非高风险抛 {@code ENG_REC_001}；
     * 同事务写状态历史 + EXECUTE 审计；返回 triggerId 与本次落库卡数。
     */
    @Transactional
    public RecommendationTriggerResponse trigger(RecommendationTriggerRequest request) {
        try {
            validateCards(request.candidateCards());
        } catch (ApiException e) {
            // CDSS-M-01：来源缺失/高风险未确认/强打断非高风险等医疗安全校验失败，
            // 经 IsolatedAuditPublisher 发 outcome=FAILED 审计，保证失败也留痕（不被主事务回滚带走）。
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE, "recommendation_trigger", request.triggerCode(),
                e.errorCode().code(), "推荐触发校验失败 errorCode=" + e.errorCode().code()));
            throw e;
        }
        String tenantId = tenantId();
        String actor = actor();
        String traceId = traceId();
        Instant now = Instant.now();
        String triggerId = "rt-" + UUID.randomUUID();
        RecommendationTriggerStatus status = request.candidateCards().isEmpty()
            ? RecommendationTriggerStatus.NO_CARD
            : RecommendationTriggerStatus.EVALUATED;

        RecommendationTrigger trigger = triggers.save(new RecommendationTrigger(
            null, triggerId, tenantId, request.triggerCode(), request.triggerType(),
            request.sourceEventId(), request.contextSnapshotId(), request.patientId(), request.encounterId(),
            request.patientPathwayId(), request.scenarioCode(), request.packageVersion(), request.inputDigest(),
            status, null, request.occurredAt() == null ? now : request.occurredAt(),
            now, actor, now, actor, traceId));

        for (RecommendationCardRequest cardRequest : request.candidateCards()) {
            RecommendationCard card = saveCard(trigger, cardRequest, now, actor, traceId);
            for (RecommendationSourceRequest sourceRequest : cardRequest.sources()) {
                saveSource(card.cardId(), sourceRequest, now, actor, traceId);
            }
            saveFatigueSignal(trigger, card, initialSignal(cardRequest), null, now, actor, traceId);
            // CDSS-M-03：每发出一张提醒卡计入 medkernel_cdss_alerts_total（临床运行业务指标）。
            businessMetrics.incCdssAlerts();
        }

        transitions.record("recommendation_trigger", triggerId, null, status.name(), "接收推荐触发", null);
        auditPublisher.publish(AuditAction.EXECUTE, "recommendation_trigger", triggerId,
            "接收推荐触发 " + request.triggerCode());
        return new RecommendationTriggerResponse(triggerId, status, request.candidateCards().size(), traceId);
    }

    /**
     * 客户面推荐评估接口：关模型时只持久化并返回确定性候选卡，同时返回 {@code MODEL_DISABLED}。
     *
     * <p>低/中风险卡在请求携带 fatigueSuppressionThreshold 与 fatigueWindowHours 时按历史低价值信号抑制；
     * 高风险/红线卡永不因疲劳阈值抑制。被抑制卡以 SUPPRESSED 状态留库并写疲劳信号，保证可解释、可审计。
     */
    @Transactional
    public RecommendationEvaluationResponse evaluate(RecommendationTriggerRequest request) {
        List<RecommendationCardRequest> deterministicCards = request.candidateCards().stream()
            .filter(card -> !card.aiGenerated())
            .toList();
        try {
            validateCards(deterministicCards);
        } catch (ApiException e) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE, "recommendation_trigger", request.triggerCode(),
                e.errorCode().code(), "推荐评估校验失败 errorCode=" + e.errorCode().code()));
            throw e;
        }

        String tenantId = tenantId();
        String actor = actor();
        String traceId = traceId();
        Instant now = Instant.now();
        String triggerId = "rt-" + UUID.randomUUID();
        RecommendationTriggerStatus status = deterministicCards.isEmpty()
            ? RecommendationTriggerStatus.NO_CARD
            : RecommendationTriggerStatus.EVALUATED;

        RecommendationTrigger trigger = triggers.save(new RecommendationTrigger(
            null, triggerId, tenantId, request.triggerCode(), request.triggerType(),
            request.sourceEventId(), request.contextSnapshotId(), request.patientId(), request.encounterId(),
            request.patientPathwayId(), request.scenarioCode(), request.packageVersion(), request.inputDigest(),
            status, null, request.occurredAt() == null ? now : request.occurredAt(),
            now, actor, now, actor, traceId));

        List<RecommendationCard> visibleCards = new ArrayList<>();
        int suppressedCount = 0;
        for (RecommendationCardRequest cardRequest : deterministicCards) {
            boolean suppressed = shouldSuppress(request, cardRequest, tenantId, now);
            RecommendationCard card = saveCard(trigger, cardRequest, now, actor, traceId,
                suppressed ? RecommendationCardStatus.SUPPRESSED : RecommendationCardStatus.PENDING);
            for (RecommendationSourceRequest sourceRequest : cardRequest.sources()) {
                saveSource(card.cardId(), sourceRequest, now, actor, traceId);
            }
            saveFatigueSignal(trigger, card, suppressed ? RecommendationFatigueSignalType.SUPPRESSED
                : initialSignal(cardRequest), null, now, actor, traceId);
            if (suppressed) {
                suppressedCount++;
            } else {
                visibleCards.add(card);
                businessMetrics.incCdssAlerts();
            }
        }

        transitions.record("recommendation_trigger", triggerId, null, status.name(), "评估推荐触发", null);
        auditPublisher.publish(AuditAction.EXECUTE, "recommendation_trigger", triggerId,
            "评估推荐触发 " + request.triggerCode());
        return new RecommendationEvaluationResponse(
            triggerId, status, request.candidateCards().size(), visibleCards.size(), suppressedCount,
            RecommendationModelStatus.MODEL_DISABLED, visibleCards, traceId);
    }

    /**
     * 分页查询当前租户的推荐卡，按 status / riskLevel / scenarioCode / patientId / encounterId / triggerPoint 过滤。
     */
    @Transactional(readOnly = true)
    public PageResponse<RecommendationCard> listCards(RecommendationCardFilter filter, PageRequest pageRequest) {
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        RecommendationCardFilter f = filter == null ? new RecommendationCardFilter(null, null, null, null, null, null)
            : filter;
        String status = f.status() == null ? null : f.status().name();
        String risk = f.riskLevel() == null ? null : f.riskLevel().name();
        long total = cards.countByFilter(
            tenantId(), status, risk, f.scenarioCode(), f.patientId(), f.encounterId(), f.triggerPoint());
        List<RecommendationCard> rows = cards.pageByFilter(
            tenantId(), status, risk, f.scenarioCode(), f.patientId(), f.encounterId(), f.triggerPoint(),
            req.offset(), req.safeSize());
        return PageResponse.of(rows, req, total);
    }

    /**
     * 查询推荐卡详情（含来源、反馈、疲劳信号），卡不存在抛 {@code ENG_REC_003}。
     */
    @Transactional(readOnly = true)
    public RecommendationCardDetailResponse cardDetail(String cardId) {
        RecommendationCard card = findCard(cardId);
        return new RecommendationCardDetailResponse(
            card,
            sources.findByCardIdAndTenantIdOrderByCreatedAtAsc(cardId, tenantId()),
            feedback.findByCardIdAndTenantIdOrderByCreatedAtAsc(cardId, tenantId()),
            fatigueSignals.findByCardIdAndTenantIdOrderByCreatedAtAsc(cardId, tenantId())
        );
    }

    /**
     * 查询推荐卡的来源解释列表，按 created_at 升序；卡不存在抛 {@code ENG_REC_003}。
     */
    @Transactional(readOnly = true)
    public List<RecommendationSource> sources(String cardId) {
        findCard(cardId);
        return sources.findByCardIdAndTenantIdOrderByCreatedAtAsc(cardId, tenantId());
    }

    /**
     * 接收医师反馈，推进推荐卡状态并采集疲劳治理信号。
     *
     * <p>终止态卡或已过期卡抛 {@code ENG_REC_004}；卡不存在抛 {@code ENG_REC_003}；
     * 同事务写状态历史 + FEEDBACK 审计；返回 feedbackId、cardId、推进后的卡状态和 traceId。
     */
    @Transactional
    public RecommendationFeedbackResponse feedback(String cardId, RecommendationFeedbackRequest request) {
        RecommendationCard card = findCard(cardId);
        if (request.idempotencyKey() != null) {
            var existing = feedback.findByCardIdAndTenantIdAndIdempotencyKey(cardId, tenantId(), request.idempotencyKey());
            if (existing.isPresent()) {
                RecommendationFeedback savedFeedback = existing.get();
                return new RecommendationFeedbackResponse(savedFeedback.feedbackId(), cardId,
                    nextStatus(savedFeedback.feedbackType()),
                    savedFeedback.traceId() == null ? traceId() : savedFeedback.traceId());
            }
        }
        validateFeedbackReason(request);
        if (isClosed(card) || isExpired(card)) {
            throw new ApiException(ErrorCode.ENG_REC_004);
        }

        String tenantId = tenantId();
        String actor = actor();
        String traceId = traceId();
        Instant now = Instant.now();
        RecommendationCardStatus nextStatus = nextStatus(request.feedbackType());
        RecommendationCard savedCard = cards.save(rewriteStatus(card, nextStatus, now, actor));
        String feedbackId = "rf-" + UUID.randomUUID();
        feedback.save(new RecommendationFeedback(
            null, feedbackId, tenantId, cardId, request.idempotencyKey(), request.feedbackType(),
            request.reasonCode(), request.reasonText(), actor, request.operatorRole(),
            now, actor, now, actor, traceId));

        RecommendationTrigger trigger = triggers.findByTriggerIdAndTenantId(card.triggerId(), tenantId).orElse(null);
        saveFatigueSignal(trigger, savedCard, feedbackSignal(request.feedbackType()), actor, now, actor, traceId);
        transitions.record("recommendation_card", cardId, card.status().name(), nextStatus.name(),
            "推荐反馈 " + request.feedbackType(), null);
        auditPublisher.publish(AuditAction.FEEDBACK, "recommendation_card", cardId,
            "推荐卡反馈 " + request.feedbackType());
        return new RecommendationFeedbackResponse(feedbackId, cardId, nextStatus, traceId);
    }

    /**
     * 分页查询当前租户的疲劳治理信号，按 fatigueKey / signalType 过滤；
     * 使用 limit+1 估算下一页可用性（{@code PageResponse.ofEstimated}）。
     */
    @Transactional(readOnly = true)
    public PageResponse<RecommendationFatigueSignal> fatigueSignals(
            RecommendationFatigueSignalFilter filter, PageRequest pageRequest) {
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        RecommendationFatigueSignalFilter f = filter == null
            ? new RecommendationFatigueSignalFilter(null, null)
            : filter;
        String signalType = f.signalType() == null ? null : f.signalType().name();
        List<RecommendationFatigueSignal> rows =
            fatigueSignals.pageByFilter(tenantId(), f.fatigueKey(), signalType, req.offset(), req.safeSize());
        boolean hasNext = rows.size() == req.safeSize();
        long estimated = (long) req.offset() + rows.size() + (hasNext ? 1 : 0);
        return PageResponse.ofEstimated(rows, req, estimated, hasNext);
    }

    /**
     * 输出推荐触发诊断响应，聚合推荐卡、反馈和疲劳治理信号；
     * 触发不存在抛 {@code ENG_REC_002}。
     */
    @Transactional(readOnly = true)
    public DiagnoseResponse diagnose(String triggerId) {
        RecommendationTrigger trigger = triggers.findByTriggerIdAndTenantId(triggerId, tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_REC_002));
        List<RecommendationCard> triggerCards =
            cards.findByTriggerIdAndTenantIdOrderByCreatedAtAsc(triggerId, tenantId());
        List<RecommendationFeedback> allFeedback = new ArrayList<>();
        for (RecommendationCard card : triggerCards) {
            allFeedback.addAll(feedback.findByCardIdAndTenantIdOrderByCreatedAtAsc(card.cardId(), tenantId()));
        }
        List<RecommendationFatigueSignal> signals =
            fatigueSignals.findByTriggerIdAndTenantIdOrderByCreatedAtAsc(triggerId, tenantId());
        Map<String, List<String>> related = Map.of(
            "cards", triggerCards.stream().map(RecommendationCard::cardId).toList(),
            "feedback", allFeedback.stream().map(RecommendationFeedback::feedbackId).toList(),
            "fatigueSignals", signals.stream().map(RecommendationFatigueSignal::signalId).toList()
        );
        return diagnoseAssembler.assemble(
            "recommendation_trigger", triggerId, tenantId(), trigger.status().name(),
            trigger, List.of(), related, null,
            trigger.traceId() == null ? traceId() : trigger.traceId());
    }

    private void validateCards(List<RecommendationCardRequest> cardRequests) {
        for (RecommendationCardRequest card : cardRequests) {
            if (card.sources().isEmpty()) {
                throw new ApiException(ErrorCode.ENG_REC_005);
            }
            if (isHighRisk(card.riskLevel()) && !card.requiresPhysicianConfirmation()) {
                throw new ApiException(ErrorCode.ENG_REC_006);
            }
            if (card.interruptLevel() == RecommendationInterruptLevel.STRONG_INTERRUPTIVE
                    && !isHighRisk(card.riskLevel())) {
                throw new ApiException(ErrorCode.ENG_REC_001, "强打断推荐必须是高风险或红线风险");
            }
        }
    }

    private RecommendationCard saveCard(RecommendationTrigger trigger, RecommendationCardRequest request,
                                        Instant now, String actor, String traceId) {
        return saveCard(trigger, request, now, actor, traceId, RecommendationCardStatus.PENDING);
    }

    private RecommendationCard saveCard(RecommendationTrigger trigger, RecommendationCardRequest request,
                                        Instant now, String actor, String traceId,
                                        RecommendationCardStatus status) {
        return cards.save(new RecommendationCard(
            null, "rc-" + UUID.randomUUID(), trigger.tenantId(), trigger.triggerId(), request.cardCode(),
            request.cardType(), request.title(), request.summary(), request.suggestedAction(),
            request.riskLevel(), request.interruptLevel(), status,
            request.requiresPhysicianConfirmation(), request.aiGenerated(), request.sourceSummary(),
            request.explanationJson(), request.fatigueKey(), request.expiresAt(),
            now, actor, now, actor, traceId));
    }

    private RecommendationSource saveSource(String cardId, RecommendationSourceRequest request,
                                            Instant now, String actor, String traceId) {
        return sources.save(new RecommendationSource(
            null, "rs-" + UUID.randomUUID(), tenantId(), cardId, request.sourceType(),
            request.sourceRefId(), request.sourceVersion(), request.sourceTitle(), request.citationLocator(),
            request.sourceHash(), request.summary(), now, actor, now, actor, traceId));
    }

    private void saveFatigueSignal(RecommendationTrigger trigger, RecommendationCard card,
                                   RecommendationFatigueSignalType signalType, String operatorId,
                                   Instant now, String actor, String traceId) {
        fatigueSignals.save(new RecommendationFatigueSignal(
            null, "rfs-" + UUID.randomUUID(), tenantId(),
            trigger == null ? card.triggerId() : trigger.triggerId(),
            card.cardId(), card.fatigueKey(),
            trigger == null ? null : trigger.patientId(),
            trigger == null ? null : trigger.encounterId(),
            operatorId, signalType, 1, now, now, actor, now, actor, traceId));
    }

    private RecommendationCard findCard(String cardId) {
        return cards.findByCardIdAndTenantId(cardId, tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_REC_003));
    }

    private RecommendationCard rewriteStatus(RecommendationCard card, RecommendationCardStatus status,
                                             Instant now, String actor) {
        return new RecommendationCard(
            card.id(), card.cardId(), card.tenantId(), card.triggerId(), card.cardCode(), card.cardType(),
            card.title(), card.summary(), card.suggestedAction(), card.riskLevel(), card.interruptLevel(),
            status, card.requiresPhysicianConfirmation(), card.aiGenerated(), card.sourceSummary(),
            card.explanationJson(), card.fatigueKey(), card.expiresAt(),
            card.createdAt(), card.createdBy(), now, actor, card.traceId());
    }

    private RecommendationCardStatus nextStatus(RecommendationFeedbackType feedbackType) {
        return switch (feedbackType) {
            case VIEW_SOURCE -> RecommendationCardStatus.VIEWED;
            case ACCEPT -> RecommendationCardStatus.ACCEPTED;
            case REJECT -> RecommendationCardStatus.REJECTED;
            case DEFER -> RecommendationCardStatus.DEFERRED;
            case DISMISS -> RecommendationCardStatus.DISMISSED;
        };
    }

    private RecommendationFatigueSignalType initialSignal(RecommendationCardRequest request) {
        return request.interruptLevel() == RecommendationInterruptLevel.SILENT
            ? RecommendationFatigueSignalType.SILENT_RECORDED
            : RecommendationFatigueSignalType.SHOWN;
    }

    private RecommendationFatigueSignalType feedbackSignal(RecommendationFeedbackType feedbackType) {
        return switch (feedbackType) {
            case VIEW_SOURCE -> RecommendationFatigueSignalType.VIEWED;
            case ACCEPT -> RecommendationFatigueSignalType.ACCEPTED;
            case REJECT -> RecommendationFatigueSignalType.REJECTED;
            case DEFER -> RecommendationFatigueSignalType.DEFERRED;
            case DISMISS -> RecommendationFatigueSignalType.DISMISSED;
        };
    }

    private boolean shouldSuppress(RecommendationTriggerRequest request, RecommendationCardRequest cardRequest,
                                   String tenantId, Instant now) {
        if (!suppressionPolicyEnabled(request) || isHighRisk(cardRequest.riskLevel())) {
            return false;
        }
        if (request.patientId() == null || request.patientId().isBlank()
                || cardRequest.fatigueKey() == null || cardRequest.fatigueKey().isBlank()) {
            return false;
        }
        Instant windowStartedAt = now.minusSeconds(request.fatigueWindowHours().longValue() * 3600L);
        return fatigueSignals.countLowValueSignals(
            tenantId, request.patientId(), cardRequest.fatigueKey(), windowStartedAt)
            >= request.fatigueSuppressionThreshold();
    }

    private boolean suppressionPolicyEnabled(RecommendationTriggerRequest request) {
        return request.fatigueSuppressionThreshold() != null
            && request.fatigueSuppressionThreshold() > 0
            && request.fatigueWindowHours() != null
            && request.fatigueWindowHours() > 0;
    }

    private void validateFeedbackReason(RecommendationFeedbackRequest request) {
        if ((request.feedbackType() == RecommendationFeedbackType.ACCEPT
                || request.feedbackType() == RecommendationFeedbackType.REJECT)
                && (request.reasonCode() == null || request.reasonText() == null)) {
            throw new ApiException(ErrorCode.ENG_REC_007);
        }
    }

    private boolean isHighRisk(RecommendationRiskLevel riskLevel) {
        return riskLevel == RecommendationRiskLevel.HIGH || riskLevel == RecommendationRiskLevel.CRITICAL;
    }

    private boolean isClosed(RecommendationCard card) {
        return card.status() == RecommendationCardStatus.ACCEPTED
            || card.status() == RecommendationCardStatus.REJECTED
            || card.status() == RecommendationCardStatus.DISMISSED
            || card.status() == RecommendationCardStatus.SUPPRESSED
            || card.status() == RecommendationCardStatus.EXPIRED;
    }

    private boolean isExpired(RecommendationCard card) {
        return card.expiresAt() != null && card.expiresAt().isBefore(Instant.now());
    }

    private String tenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }
}
