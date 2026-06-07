package com.medkernel.shared.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MedKernel v1.0 GA · GA-ENG-API-01b 失败留痕子事务发布器。
 *
 * <p>当业务方法 {@code @Transactional} 中途抛 ApiException 时，业务事务回滚 →
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 不触发 → 失败 audit 丢失。
 * 本组件用 {@code PROPAGATION_REQUIRES_NEW} 让 audit 走独立子事务，保证 outcome=FAILED
 * 的审计事件不被主事务回滚带走。
 *
 * <p>使用场景仅限于业务失败留痕；成功路径使用 {@link AuditRecorder#record}
 * 由 AFTER_COMMIT 同事务保证一致性。
 */
@Component
public class IsolatedAuditPublisher {

    private final AuditEventPublisher delegate;
    private final TransactionTemplate requiresNew;

    public IsolatedAuditPublisher(AuditEventPublisher delegate, PlatformTransactionManager txm) {
        this.delegate = delegate;
        this.requiresNew = new TransactionTemplate(txm);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void publishInNewTx(AuditEvent event) {
        requiresNew.executeWithoutResult(status -> delegate.publish(event));
    }
}
