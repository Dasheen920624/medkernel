package com.medkernel.shared.audit;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventPublisherTest {

    @Test
    void publishSendsTheCompleteEventUnchanged() {
        List<AuditEvent> captured = new ArrayList<>();
        ApplicationEventPublisher capturingBus = event -> {
            if (event instanceof AuditEvent ae) {
                captured.add(ae);
            }
        };

        AuditEventPublisher publisher = new AuditEventPublisher(capturingBus);
        AuditEvent event = AuditEvent.failure(
            AuditAction.EXECUTE,
            "clinical_event",
            "evt-1",
            "ENG-EVENT-004",
            "处理临床事件失败");
        publisher.publish(event);

        assertThat(captured).containsExactly(event);
    }

    @Test
    void payloadDigestCanBeAttachedAfterCreation() {
        AuditEvent base = AuditEvent.of(AuditAction.EXPORT, "audit", "snapshot-1", "导出审计快照");
        AuditEvent signed = base.withPayloadDigest("sm3:abcdef0123456789");

        assertThat(signed.id()).isEqualTo(base.id());
        assertThat(signed.payloadDigest()).isEqualTo("sm3:abcdef0123456789");
    }
}
