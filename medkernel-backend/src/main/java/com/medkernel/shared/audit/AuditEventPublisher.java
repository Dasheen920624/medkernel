package com.medkernel.shared.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 审计事件发布器（兼容门面 + 底层事件总线）。
 *
 * <p>新业务代码应直接使用 {@link AuditRecorder}；历史调用 {@link #publish(AuditAction, String, String, String)}
 * 会转交给 {@link AuditRecorder}，确保角色、组织路径、环境、快照摘要等字段由同一入口补齐。
 *
 * <p>{@link #publish(AuditEvent)} 仅作为低层事件总线，用于失败事件、测试或已构造完整审计事件的场景。
 * 事件通过 Spring {@link ApplicationEventPublisher} 在请求线程内同步分发到所有 {@code @EventListener}。
 *
 * <p>{@link LoggingAuditSink} 同步写入 INFO 日志便于本地开发观察；
 * {@code com.medkernel.shared.audit.persistence.AuditPersistenceSink} 在事务提交后异步落库
 * 并写入 SM3 哈希链（GA-ENG-BASE-04）。
 */
@Component
public class AuditEventPublisher {

    private final ApplicationEventPublisher publisher;
    private final AuditRecorder recorder;

    @Autowired
    public AuditEventPublisher(ApplicationEventPublisher publisher, AuditRecorder recorder) {
        this.publisher = publisher;
        this.recorder = recorder;
    }

    public AuditEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
        this.recorder = null;
    }

    public AuditEvent publish(AuditAction action, String resourceType, String resourceId, String summary) {
        if (recorder != null) {
            return recorder.record(new AuditRecordCommand(action, resourceType, resourceId, summary, null, null, null));
        }
        AuditEvent event = AuditEvent.of(action, resourceType, resourceId, summary);
        publisher.publishEvent(event);
        return event;
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
