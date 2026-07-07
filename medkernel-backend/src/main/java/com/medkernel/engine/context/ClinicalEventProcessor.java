package com.medkernel.engine.context;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
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
    private final ContextSnapshotService contextSnapshots;

    public ClinicalEventProcessor(ClinicalEventRepository events,
                                  ClinicalEventPayloadRepository payloads,
                                  AuditRecorder auditRecorder,
                                  AuditEventPublisher auditPublisher,
                                  StateTransitionRecorder transitions,
                                  ApplicationEventPublisher applicationEvents,
                                  ClinicalEventContextFactory contextFactory,
                                  ClinicalEventEngineDispatcher engineDispatcher,
                                  ContextSnapshotService contextSnapshots) {
        this.events = events;
        this.payloads = payloads;
        this.auditRecorder = auditRecorder;
        this.auditPublisher = auditPublisher;
        this.transitions = transitions;
        this.applicationEvents = applicationEvents;
        this.contextFactory = contextFactory;
        this.engineDispatcher = engineDispatcher;
        this.contextSnapshots = contextSnapshots;
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

        ClinicalEventContext context = contextFactory.from(event, payload);
        return processWithEventScope(event, context);
    }

    @Transactional
    public void markFailed(String eventId, String tenantId, ErrorCode errorCode,
                           int retryCount, boolean dead, Instant nextRetryAt) {
        events.findByEventIdAndTenantId(eventId, tenantId).ifPresent(event -> {
            RequestContext.Snapshot snapshot = eventScopeSnapshot(event, contextFactory.readOrgScope(event));
            RequestContext.runWith(snapshot, () -> markFailedWithEventScope(
                event, eventId, errorCode, retryCount, dead, nextRetryAt));
        });
    }

    private void markFailedWithEventScope(ClinicalEvent event, String eventId, ErrorCode errorCode,
                                          int retryCount, boolean dead, Instant nextRetryAt) {
            ClinicalEvent failed = new ClinicalEvent(
                event.id(), event.eventId(), event.tenantId(), event.eventType(),
                event.triggerPoint(), event.idempotencyKey(), event.callbackWebhookId(),
                event.orgScopeJson(),
                event.patientId(), event.encounterId(), event.clinicalSetting(),
                event.sourceSystem(), event.runtimeReleaseId(),
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
    }

    private ClinicalEvent withStatus(ClinicalEvent source, ClinicalEventStatus status) {
        return withStatusAndSnapshot(source, status, source.snapshotId());
    }

    private ClinicalEvent withStatusAndSnapshot(ClinicalEvent source, ClinicalEventStatus status, String snapshotId) {
        return new ClinicalEvent(
            source.id(), source.eventId(), source.tenantId(), source.eventType(),
            source.triggerPoint(), source.idempotencyKey(), source.callbackWebhookId(),
            source.orgScopeJson(),
            source.patientId(), source.encounterId(), source.clinicalSetting(),
            source.sourceSystem(), source.runtimeReleaseId(),
            source.payloadDigest(), source.occurredAt(), source.receivedAt(), snapshotId,
            status, null, null, source.retryCount(), source.rootEventId(), source.traceId());
    }

    private ClinicalEventStatus processWithEventScope(ClinicalEvent event, ClinicalEventContext context) {
        RequestContext.Snapshot snapshot = eventScopeSnapshot(event, context.orgScope());
        try {
            return RequestContext.callWith(snapshot, () -> processMappedAndDispatch(event, context));
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.ENG_EVENT_005, "临床事件处理失败", exception);
        }
    }

    private RequestContext.Snapshot eventScopeSnapshot(ClinicalEvent event, OrgScope orgScope) {
        return new RequestContext.Snapshot(
            event.traceId(),
            orgScope,
            RequestContext.currentUserId().orElse(null)
        );
    }

    private ClinicalEventStatus processMappedAndDispatch(ClinicalEvent event, ClinicalEventContext context) {
        String snapshotId = ensureContextSnapshot(context);
        context = context.withContextSnapshotId(snapshotId);
        ClinicalEvent mapped = withStatusAndSnapshot(event, ClinicalEventStatus.MAPPED, snapshotId);
        events.save(mapped);
        transitions.record(ENTITY_TYPE, event.eventId(),
            event.processingStatus().name(), ClinicalEventStatus.MAPPED.name(),
            "TERMINOLOGY_OK", null);

        List<ClinicalEventEngineDispatchResult> dispatchResults = engineDispatcher.dispatch(context);
        java.util.Optional<ClinicalEventEngineDispatchResult> unavailable = dispatchResults.stream()
            .filter(result -> result.status() == ClinicalEventEngineDispatchStatus.UNAVAILABLE)
            .findFirst();
        if (unavailable.isPresent()) {
            return markEnginesUnavailable(mapped, unavailable.get());
        }

        ClinicalEvent processed = withStatus(mapped, ClinicalEventStatus.PROCESSED);
        events.save(processed);
        transitions.record(ENTITY_TYPE, event.eventId(),
            ClinicalEventStatus.MAPPED.name(), ClinicalEventStatus.PROCESSED.name(),
            "ENGINES_OK", null);

        auditRecorder.record(AuditAction.EXECUTE, ENTITY_TYPE, event.eventId(),
            successAuditMessage(event, dispatchResults));
        applicationEvents.publishEvent(new ClinicalEventProcessedEvent(
            event.eventId(), event.tenantId(), event.traceId(), context));
        return ClinicalEventStatus.PROCESSED;
    }

    private String ensureContextSnapshot(ClinicalEventContext context) {
        if (hasText(context.contextSnapshotId())) {
            return context.contextSnapshotId();
        }
        ContextSnapshotResponse snapshot = contextSnapshots.createBound(
            snapshotRequest(context),
            "clinical-event:" + context.eventId(),
            context.runtimeReleaseId());
        return snapshot.snapshotId();
    }

    private ContextSnapshotRequest snapshotRequest(ClinicalEventContext context) {
        OrgScope scope = context.orgScope();
        return new ContextSnapshotRequest(
            "clinical-event:" + context.eventId(),
            context.traceId(),
            context.tenantId(),
            null,
            null,
            null,
            null,
            null,
            null,
            RequestContext.currentUserId().orElse(null),
            List.of(),
            context.patientId(),
            context.encounterId(),
            orgUnitId(scope, context.tenantId()),
            context.resources()
        );
    }

    private String orgUnitId(OrgScope scope, String tenantId) {
        if (scope == null) {
            return tenantId;
        }
        return scope.nearestOrgUnitIdOrTenant(tenantId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
            source.sourceSystem(), source.runtimeReleaseId(),
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
            errorCode.code(), "临床事件下游能力不可用 code=" + result.engine()));
        return ClinicalEventStatus.FAILED;
    }

    private String successAuditMessage(
            ClinicalEvent event,
            List<ClinicalEventEngineDispatchResult> dispatchResults) {
        String engines = String.join("|", dispatchResults.stream()
            .map(result -> result.engine() + ":" + result.status() + ":" + trimForAudit(result.message()))
            .toList());
        if (!hasText(engines)) {
            return "处理临床事件成功 type=" + event.eventType();
        }
        return "处理临床事件成功 type=" + event.eventType() + " capabilities=" + engines;
    }

    private String trimForAudit(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }
}
