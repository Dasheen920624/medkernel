package com.medkernel.shared.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 审计事件底层总线。
 *
 * <p>业务成功留痕必须使用 {@link AuditRecorder}。本类仅发布已构造完整的失败事件或系统事件，
 * 不负责补齐审计上下文和摘要。
 * 事件通过 Spring {@link ApplicationEventPublisher} 在请求线程内同步分发到所有 {@code @EventListener}。
 *
 * <p>{@link LoggingAuditSink} 同步写入 INFO 日志便于本地开发观察；
 * {@code com.medkernel.shared.audit.persistence.AuditPersistenceSink} 在事务提交后异步落库
 * 并写入 SM3 哈希链（GA-ENG-BASE-04）。
 */
@Component
public class AuditEventPublisher {

    private final ApplicationEventPublisher publisher;

    public AuditEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(AuditEvent event) {
        publisher.publishEvent(event);
    }

    @Component
    static class LoggingAuditSink {
        private static final Logger log = LoggerFactory.getLogger("audit");

        @org.springframework.context.event.EventListener
        public void onEvent(AuditEvent event) {
            log.info("AUDIT action={} resource={}/{} actor={} tenant={} traceId={} summary={}",
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.actorUserId(),
                event.orgScope() == null ? null : event.orgScope().tenantId(),
                event.traceId(),
                event.summary());
        }
    }
}
