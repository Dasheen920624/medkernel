package com.medkernel.engine.context;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.observability.StateTransitionRecorder;
import com.medkernel.shared.observability.TransitionError;

/**
 * 单个临床事件处理器。只处理业务状态推进，不负责领取和重试。
 */
@Service
public class ClinicalEventProcessor {

    private static final String ENTITY_TYPE = "clinical_event";

    private final ClinicalEventRepository events;
    private final ClinicalEventPayloadRepository payloads;
    private final AuditRecorder auditRecorder;
    private final AuditEventPublisher auditPublisher;
    private final StateTransitionRecorder transitions;
    private final ApplicationEventPublisher applicationEvents;
    private final ClinicalEventContextFactory contextFactory;
    private final ClinicalEventEngineDispatcher engineDispatcher;

    public ClinicalEventProcessor(ClinicalEventRepository events,
                                  ClinicalEventPayloadRepository payloads,
                                  AuditRecorder auditRecorder,
                                  AuditEventPublisher auditPublisher,
                                  StateTransitionRecorder transitions,
                                  ApplicationEventPublisher applicationEvents,
                                  ClinicalEventContextFactory contextFactory,
                                  ClinicalEventEngineDispatcher engineDispatcher) {
        this.events = events;
        this.payloads = payloads;
        this.auditRecorder = auditRecorder;
        this.auditPublisher = auditPublisher;
        this.transitions = transitions;
        this.applicationEvents = applicationEvents;
        this.contextFactory = contextFactory;
        this.engineDispatcher = engineDispatcher;
    }

    @Transactional
    public ClinicalEventStatus process(String eventId, String tenantId) {
        ClinicalEvent event = events.findByEventIdAndTenantId(eventId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVENT_003,
                "临床事件不存在: " + eventId));
        ClinicalEventPayload payload = payloads.findByEventIdAndTenantId(eventId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_OBS_001,
                "事件 payload 不存在: " + eventId));

        if (event.processingStatus() == ClinicalEventStatus.PROCESSED) {
            return ClinicalEventStatus.PROCESSED;
        }

        ClinicalEvent mapped = withStatus(event, ClinicalEventStatus.MAPPED);
        events.save(mapped);
        transitions.record(ENTITY_TYPE, eventId,
            event.processingStatus().name(), ClinicalEventStatus.MAPPED.name(),
            "TERMINOLOGY_OK", null);

        ClinicalEventContext context = contextFactory.from(mapped, payload);
        java.util.List<ClinicalEventEngineDispatchResult> dispatchResults = engineDispatcher.dispatch(context);
        java.util.Optional<ClinicalEventEngineDispatchResult> unavailable = dispatchResults.stream()
            .filter(result -> result.status() == ClinicalEventEngineDispatchStatus.UNAVAILABLE)
            .findFirst();
        if (unavailable.isPresent()) {
            return markEnginesUnavailable(mapped, unavailable.get());
        }

        ClinicalEvent processed = withStatus(mapped, ClinicalEventStatus.PROCESSED);
        events.save(processed);
        transitions.record(ENTITY_TYPE, eventId,
            ClinicalEventStatus.MAPPED.name(), ClinicalEventStatus.PROCESSED.name(),
            "ENGINES_OK", null);

        auditRecorder.record(AuditAction.EXECUTE, ENTITY_TYPE, eventId,
            "处理临床事件成功 type=" + event.eventType());
        applicationEvents.publishEvent(new ClinicalEventProcessedEvent(
            eventId, tenantId, event.traceId(), context));
        return ClinicalEventStatus.PROCESSED;
    }

    @Transactional
    public void markFailed(String eventId, String tenantId, ErrorCode errorCode,
                           int retryCount, boolean dead, Instant nextRetryAt) {
        events.findByEventIdAndTenantId(eventId, tenantId).ifPresent(event -> {
            ClinicalEvent failed = new ClinicalEvent(
                event.id(), event.eventId(), event.tenantId(), event.eventType(),
                event.triggerPoint(), event.idempotencyKey(), event.callbackWebhookId(),
                event.orgScopeJson(),
                event.patientId(), event.encounterId(), event.clinicalSetting(),
                event.sourceSystem(), event.packageVersion(),
                event.payloadDigest(), event.occurredAt(), event.receivedAt(), event.snapshotId(),
                ClinicalEventStatus.FAILED, errorCode.code(), errorCode.errorClass().name(),
                retryCount, event.rootEventId(), event.traceId());
            events.save(failed);
            transitions.record(ENTITY_TYPE, eventId,
                event.processingStatus().name(), ClinicalEventStatus.FAILED.name(),
                dead ? "PROCESS_DEAD" : "PROCESS_FAILED",
                TransitionError.of(errorCode.code(), errorCode.errorClass(),
                    errorCode.defaultMessage(), retryCount, nextRetryAt));
            auditPublisher.publish(AuditEvent.failure(AuditAction.EXECUTE, ENTITY_TYPE, eventId,
                errorCode.code(), "处理临床事件失败 retryCount=" + retryCount));
        });
    }

    private ClinicalEvent withStatus(ClinicalEvent source, ClinicalEventStatus status) {
        return new ClinicalEvent(
            source.id(), source.eventId(), source.tenantId(), source.eventType(),
            source.triggerPoint(), source.idempotencyKey(), source.callbackWebhookId(),
            source.orgScopeJson(),
            source.patientId(), source.encounterId(), source.clinicalSetting(),
            source.sourceSystem(), source.packageVersion(),
            source.payloadDigest(), source.occurredAt(), source.receivedAt(), source.snapshotId(),
            status, null, null, source.retryCount(), source.rootEventId(), source.traceId());
    }

    private ClinicalEventStatus markEnginesUnavailable(
            ClinicalEvent source,
            ClinicalEventEngineDispatchResult result) {
        ErrorCode errorCode = ErrorCode.DOWNSTREAM_UNAVAILABLE;
        ClinicalEvent failed = new ClinicalEvent(
            source.id(), source.eventId(), source.tenantId(), source.eventType(),
            source.triggerPoint(), source.idempotencyKey(), source.callbackWebhookId(),
            source.orgScopeJson(),
            source.patientId(), source.encounterId(), source.clinicalSetting(),
            source.sourceSystem(), source.packageVersion(),
            source.payloadDigest(), source.occurredAt(), source.receivedAt(), source.snapshotId(),
            ClinicalEventStatus.FAILED, errorCode.code(), errorCode.errorClass().name(),
            source.retryCount(), source.rootEventId(), source.traceId());
        events.save(failed);
        transitions.record(ENTITY_TYPE, source.eventId(),
            ClinicalEventStatus.MAPPED.name(), ClinicalEventStatus.FAILED.name(),
            "ENGINES_UNAVAILABLE",
            TransitionError.of(errorCode.code(), errorCode.errorClass(),
                result.engine() + ": " + result.message(), source.retryCount(), null));
        auditPublisher.publish(AuditEvent.failure(AuditAction.EXECUTE, ENTITY_TYPE, source.eventId(),
            errorCode.code(), "临床事件下游引擎不可用 engine=" + result.engine()));
        return ClinicalEventStatus.FAILED;
    }
}
