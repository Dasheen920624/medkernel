package com.medkernel.engine.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponse.AuditEventSummary;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.PayloadRef;
import com.medkernel.shared.observability.StateTransitionRecorder;

/**
 * 临床事件接收、查询、诊断与重放编排。
 */
@Service
public class ClinicalEventService {

    private static final String ENTITY_TYPE = "clinical_event";

    private final ClinicalEventRepository events;
    private final ClinicalEventPayloadRepository payloads;
    private final ClinicalEventOutboxRepository outbox;
    private final ClinicalEventProcessor processor;
    private final AuditEventPublisher auditPublisher;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final ObjectMapper json;
    private final ClinicalEventProperties properties;

    public ClinicalEventService(ClinicalEventRepository events,
                                ClinicalEventPayloadRepository payloads,
                                ClinicalEventOutboxRepository outbox,
                                ClinicalEventProcessor processor,
                                AuditEventPublisher auditPublisher,
                                StateTransitionRecorder transitions,
                                DiagnoseResponseAssembler diagnoseAssembler,
                                ObjectMapper json,
                                ClinicalEventProperties properties) {
        this.events = events;
        this.payloads = payloads;
        this.outbox = outbox;
        this.processor = processor;
        this.auditPublisher = auditPublisher;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.json = json;
        this.properties = properties;
    }

    @Transactional
    public ClinicalEventAcceptedResponse receive(ClinicalEventRequest req) {
        String tenantId = requireCurrentTenant();
        boolean existingBeforeReceive = events.findByEventIdAndTenantId(req.eventId(), tenantId).isPresent();
        ClinicalEventAcceptedResponse accepted = receiveAsync(req);
        if (existingBeforeReceive || accepted.status() == ClinicalEventStatus.PROCESSED) {
            return accepted;
        }
        processor.process(accepted.eventId(), tenantId);
        outbox.findByEventIdAndTenantId(accepted.eventId(), tenantId)
            .filter(row -> row.id() != null)
            .ifPresent(row -> outbox.markProcessed(row.id(), Instant.now()));
        return new ClinicalEventAcceptedResponse(
            accepted.eventId(), ClinicalEventStatus.PROCESSED,
            accepted.payloadDigest(), accepted.traceId(), accepted.acceptedAt());
    }

    @Transactional
    public ClinicalEventAcceptedResponse receiveAsync(ClinicalEventRequest req) {
        String tenantId = requireCurrentTenant();
        String traceId = RequestContext.currentTraceId();
        String payload = writePayload(req.payload());
        long size = payload.getBytes(StandardCharsets.UTF_8).length;
        if (size > properties.maxPayloadSizeBytes()) {
            throw new ApiException(ErrorCode.ENG_EVENT_001,
                "事件 payload 超过限制 size=" + size);
        }
        String digest = sha256(payload);
        Instant now = Instant.now();

        var existing = events.findByEventIdAndTenantId(req.eventId(), tenantId);
        if (existing.isPresent()) {
            if (!digest.equals(existing.get().payloadDigest())) {
                throw new ApiException(ErrorCode.ENG_EVENT_002,
                    "eventId 已存在且 payload 不一致: " + req.eventId());
            }
            return accepted(existing.get(), digest, traceId, now);
        }

        ClinicalEvent saved = events.save(new ClinicalEvent(
            null, req.eventId(), tenantId, req.eventType(),
            writeOrgScope(),
            req.patientId(), req.encounterId(), req.sourceSystem(), req.packageVersion(),
            digest, req.occurredAt(), now, null, ClinicalEventStatus.RECEIVED,
            null, null, 0, null, traceId
        ));
        payloads.save(new ClinicalEventPayload(
            null, req.eventId(), tenantId, payload, null,
            PayloadRef.STORAGE_INLINE, "application/json", digest, size, now, null
        ));
        outbox.save(new ClinicalEventOutbox(
            null, req.eventId(), tenantId, traceId, RequestContext.currentUserId().orElse(null), "PENDING",
            null, null, now, 0, null, now, null
        ));

        transitions.record(ENTITY_TYPE, req.eventId(), null,
            ClinicalEventStatus.RECEIVED.name(), "INITIAL_RECEIVE", null);
        auditPublisher.publish(AuditAction.CREATE, ENTITY_TYPE, req.eventId(),
            "接收临床事件 type=" + req.eventType() + " patient=" + req.patientId());

        return accepted(saved, digest, traceId, now);
    }

    @Transactional
    public ClinicalEventBatchResponse receiveBatch(ClinicalEventBatchRequest req) {
        String batchId = "batch-" + UUID.randomUUID();
        List<ClinicalEventAcceptedResponse> items = req.events().stream()
            .map(this::receiveAsync)
            .toList();
        return new ClinicalEventBatchResponse(batchId, items, RequestContext.currentTraceId());
    }

    @Transactional(readOnly = true)
    public ClinicalEventDetailResponse findById(String eventId) {
        String tenantId = requireCurrentTenant();
        return toDetail(findEvent(eventId, tenantId));
    }

    @Transactional(readOnly = true)
    public ClinicalEventPayloadResponse payload(String eventId) {
        String tenantId = requireCurrentTenant();
        ClinicalEventPayload payload = payloads.findByEventIdAndTenantId(eventId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_OBS_001,
                "事件 payload 不存在: " + eventId));
        return new ClinicalEventPayloadResponse(
            payload.eventId(), payload.contentType(), payload.storageType(),
            payload.digest(), payload.sizeBytes(), payload.payload(), payload.createdAt());
    }

    @Transactional(readOnly = true)
    public DiagnoseResponse diagnose(String eventId) {
        String tenantId = requireCurrentTenant();
        ClinicalEvent event = findEvent(eventId, tenantId);
        PayloadRef ref = payloads.findByEventIdAndTenantId(eventId, tenantId)
            .map(payload -> new PayloadRef(
                payload.storageType(), payload.digest(),
                "db://clinical_event_payload/" + payload.eventId(),
                payload.sizeBytes(), payload.contentType()))
            .orElse(null);
        return diagnoseAssembler.assemble(
            ENTITY_TYPE, event.eventId(), event.tenantId(),
            event.processingStatus() == null ? null : event.processingStatus().name(),
            event, List.<AuditEventSummary>of(), Map.of(), ref, event.traceId());
    }

    @Transactional(readOnly = true)
    public PageResponse<ClinicalEventDetailResponse> list(ClinicalEventFilter filter, PageRequest page) {
        String tenantId = requireCurrentTenant();
        String status = filter.status() == null ? null : filter.status().name();
        String eventType = filter.eventType() == null ? null : filter.eventType().name();
        long total = events.countByFilter(tenantId, blankToNull(filter.patientId()),
            blankToNull(filter.encounterId()), status, eventType);
        List<ClinicalEventDetailResponse> rows = total == 0 ? List.of()
            : events.pageByFilter(tenantId, blankToNull(filter.patientId()),
                blankToNull(filter.encounterId()), status, eventType,
                page.offset(), page.safeSize()).stream()
                .map(this::toDetail)
                .toList();
        return PageResponse.of(rows, page, total);
    }

    @Transactional
    public ClinicalEventReplayResponse replay(String eventId) {
        String tenantId = requireCurrentTenant();
        ClinicalEvent source = findEvent(eventId, tenantId);
        if (source.processingStatus() != ClinicalEventStatus.PROCESSED
            && source.processingStatus() != ClinicalEventStatus.FAILED) {
            throw new ApiException(ErrorCode.ENG_EVENT_006,
                "仅允许重放 PROCESSED/FAILED 事件: " + eventId);
        }
        ClinicalEventPayload payload = payloads.findByEventIdAndTenantId(eventId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_OBS_001,
                "事件 payload 不存在: " + eventId));
        String newEventId = "evt-replay-" + UUID.randomUUID();
        Instant now = Instant.now();

        events.save(copyWithStatus(source, ClinicalEventStatus.SUPERSEDED));
        events.save(new ClinicalEvent(
            null, newEventId, tenantId, source.eventType(),
            source.orgScopeJson(),
            source.patientId(), source.encounterId(), source.sourceSystem(), source.packageVersion(),
            payload.digest(), source.occurredAt(), now, null, ClinicalEventStatus.RECEIVED,
            null, null, 0, source.eventId(), RequestContext.currentTraceId()
        ));
        payloads.save(new ClinicalEventPayload(
            null, newEventId, tenantId, payload.payload(), payload.payloadUri(),
            payload.storageType(), payload.contentType(), payload.digest(),
            payload.sizeBytes(), now, null
        ));
        outbox.save(new ClinicalEventOutbox(
            null, newEventId, tenantId, RequestContext.currentTraceId(),
            RequestContext.currentUserId().orElse(null), "PENDING",
            null, null, now, 0, null, now, null
        ));
        transitions.record(ENTITY_TYPE, source.eventId(),
            source.processingStatus().name(), ClinicalEventStatus.SUPERSEDED.name(),
            "REPLAY_SUPERSEDE", null);
        transitions.record(ENTITY_TYPE, newEventId, null,
            ClinicalEventStatus.RECEIVED.name(), "REPLAY_RECEIVE", null);
        auditPublisher.publish(AuditAction.ROLLBACK, ENTITY_TYPE, source.eventId(),
            "重放临床事件 newEventId=" + newEventId);
        return new ClinicalEventReplayResponse(
            source.eventId(), newEventId, ClinicalEventStatus.RECEIVED, RequestContext.currentTraceId());
    }

    static String digest(ObjectMapper json, JsonNode payload) {
        return sha256(writePayload(json, payload));
    }

    private ClinicalEvent findEvent(String eventId, String tenantId) {
        return events.findByEventIdAndTenantId(eventId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVENT_003,
                "临床事件不存在: " + eventId));
    }

    private ClinicalEventAcceptedResponse accepted(ClinicalEvent event, String digest, String traceId, Instant now) {
        return new ClinicalEventAcceptedResponse(
            event.eventId(), event.processingStatus(), digest, traceId, now);
    }

    private ClinicalEventDetailResponse toDetail(ClinicalEvent event) {
        return new ClinicalEventDetailResponse(
            event.eventId(), event.eventType(), event.patientId(), event.encounterId(),
            event.sourceSystem(), event.packageVersion(), event.processingStatus(),
            event.payloadDigest(), event.errorCode(), event.errorClass(),
            event.retryCount(), event.rootEventId(), event.occurredAt(),
            event.receivedAt(), event.traceId());
    }

    private ClinicalEvent copyWithStatus(ClinicalEvent source, ClinicalEventStatus status) {
        return new ClinicalEvent(
            source.id(), source.eventId(), source.tenantId(), source.eventType(),
            source.orgScopeJson(),
            source.patientId(), source.encounterId(), source.sourceSystem(), source.packageVersion(),
            source.payloadDigest(), source.occurredAt(), source.receivedAt(), source.snapshotId(),
            status, source.errorCode(), source.errorClass(), source.retryCount(),
            source.rootEventId(), source.traceId());
    }

    private String writePayload(JsonNode payload) {
        return writePayload(json, payload);
    }

    private static String writePayload(ObjectMapper json, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new ApiException(ErrorCode.ENG_EVENT_001, "事件 payload 不能为空");
        }
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_EVENT_001, "事件 payload 无法序列化", exception);
        }
    }

    private String writeOrgScope() {
        try {
            return json.writeValueAsString(RequestContext.currentOrgScope());
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_EVENT_001, "组织上下文无法序列化", exception);
        }
    }

    private static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", exception);
        }
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
