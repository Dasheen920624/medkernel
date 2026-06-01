package com.medkernel.engine.context;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;

/**
 * 从持久化事件与 payload 构造统一临床事件上下文。
 */
@Component
public class ClinicalEventContextFactory {

    private final ObjectMapper json;

    public ClinicalEventContextFactory(ObjectMapper json) {
        this.json = json;
    }

    public ClinicalEventContext from(ClinicalEvent event, ClinicalEventPayload payload) {
        JsonNode payloadNode = readPayload(payload);
        return new ClinicalEventContext(
            event.eventId(),
            event.tenantId(),
            readOrgScope(event),
            event.eventType(),
            event.patientId(),
            event.encounterId(),
            event.snapshotId(),
            event.sourceSystem(),
            event.packageVersion(),
            event.payloadDigest(),
            event.occurredAt(),
            triggerSource(event),
            event.traceId(),
            payloadNode,
            List.of()
        );
    }

    private JsonNode readPayload(ClinicalEventPayload payload) {
        try {
            return json.readTree(payload.payload());
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_EVENT_001, "临床事件 payload JSON 解析失败", exception);
        }
    }

    private OrgScope readOrgScope(ClinicalEvent event) {
        if (event.orgScopeJson() == null || event.orgScopeJson().isBlank()) {
            return OrgScope.tenant(event.tenantId());
        }
        try {
            OrgScope scope = json.readValue(event.orgScopeJson(), OrgScope.class);
            return scope.hasTenant() ? scope : OrgScope.tenant(event.tenantId());
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_EVENT_001, "临床事件组织上下文 JSON 解析失败", exception);
        }
    }

    private String triggerSource(ClinicalEvent event) {
        String source = event.sourceSystem() == null || event.sourceSystem().isBlank()
            ? "UNKNOWN"
            : event.sourceSystem();
        return source + ":" + event.eventType().name();
    }
}
