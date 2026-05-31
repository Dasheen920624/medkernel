package com.medkernel.shared.audit;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventPublisherTest {

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void publishCarriesContextSnapshotIntoEvent() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pub",
            new OrgScope("t-1", "g-1", "h-1", null, null, "d-1", null),
            "u-9"));

        List<AuditEvent> captured = new ArrayList<>();
        ApplicationEventPublisher capturingBus = event -> {
            if (event instanceof AuditEvent ae) {
                captured.add(ae);
            }
        };

        AuditEventPublisher publisher = new AuditEventPublisher(capturingBus);
        AuditEvent event = publisher.publish(AuditAction.PUBLISH, "rule", "r-7", "发布规则 r-7");

        assertThat(captured).hasSize(1);
        AuditEvent emitted = captured.get(0);
        assertThat(emitted).isEqualTo(event);
        assertThat(emitted.action()).isEqualTo(AuditAction.PUBLISH);
        assertThat(emitted.resourceType()).isEqualTo("rule");
        assertThat(emitted.resourceId()).isEqualTo("r-7");
        assertThat(emitted.actorUserId()).isEqualTo("u-9");
        assertThat(emitted.traceId()).isEqualTo("trace-pub");
        assertThat(emitted.orgScope().tenantId()).isEqualTo("t-1");
        assertThat(emitted.orgScope().departmentId()).isEqualTo("d-1");
    }

    @Test
    void payloadDigestCanBeAttachedAfterCreation() {
        AuditEvent base = AuditEvent.of(AuditAction.EXPORT, "audit", "snapshot-1", "导出审计快照");
        AuditEvent signed = base.withPayloadDigest("sm3:abcdef0123456789");

        assertThat(signed.id()).isEqualTo(base.id());
        assertThat(signed.payloadDigest()).isEqualTo("sm3:abcdef0123456789");
    }
}
