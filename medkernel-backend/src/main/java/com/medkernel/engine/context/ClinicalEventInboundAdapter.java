package com.medkernel.engine.context;

import org.springframework.stereotype.Component;

import com.medkernel.engine.integration.inbound.InboundClinicalEventAccepted;
import com.medkernel.engine.integration.inbound.InboundClinicalEventCommand;
import com.medkernel.engine.integration.inbound.InboundClinicalEventPort;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 把已验签、已标准化的第三方入站数据交给统一临床事件主链。
 */
@Component
public class ClinicalEventInboundAdapter implements InboundClinicalEventPort {

    private final ClinicalEventService clinicalEvents;

    public ClinicalEventInboundAdapter(ClinicalEventService clinicalEvents) {
        this.clinicalEvents = clinicalEvents;
    }

    @Override
    public InboundClinicalEventAccepted accept(String tenantId, InboundClinicalEventCommand command) {
        requireTenantContext(tenantId);
        ClinicalEventAcceptedResponse accepted = clinicalEvents.receiveAsyncBound(new ClinicalEventRequest(
            command.eventId(),
            command.eventType(),
            command.patientId(),
            command.encounterId(),
            command.clinicalSetting(),
            command.sourceSystem(),
            command.triggerPoint(),
            command.idempotencyKey(),
            null,
            command.payload(),
            command.occurredAt()
        ), command.runtimeReleaseId());
        return new InboundClinicalEventAccepted(
            accepted.eventId(),
            accepted.status().name()
        );
    }

    private void requireTenantContext(String tenantId) {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        if (!scope.tenantId().equals(tenantId)) {
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "入站消息租户与当前组织上下文不一致");
        }
    }
}
