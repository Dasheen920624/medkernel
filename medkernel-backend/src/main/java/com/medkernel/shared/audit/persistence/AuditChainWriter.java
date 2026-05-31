package com.medkernel.shared.audit.persistence;

import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 审计哈希链写入器；与 {@link AuditPersistenceSink} 拆分是为了让 {@code @Transactional}
 * 通过 Spring AOP 代理生效（自调用不走代理）。
 *
 * <p>{@code Propagation.REQUIRES_NEW} 让链推进在独立事务里完成：
 * 既适用于 {@code AFTER_COMMIT} 已无主事务的场景，
 * 也避免后续被业务事务回滚连带丢失审计。
 */
@Component
public class AuditChainWriter {

    public static final String SYSTEM_TENANT = "__SYSTEM__";
    public static final String GENESIS = "GENESIS";
    public static final String CANONICAL_FIELD_SEPARATOR = "|";
    public static final String CANONICAL_PREV_SEPARATOR = "\n";
    public static final String STATUS_SIGNED = "SIGNED";

    private final AuditEventRepository repository;
    private final SmCryptoService crypto;

    public AuditChainWriter(AuditEventRepository repository, SmCryptoService crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEventRecord persist(AuditEvent event) {
        String tenantId = resolveTenant(event.orgScope());

        try {
            repository.initChainHead(tenantId);
        } catch (DuplicateKeyException ignored) {
            // tenant chain already initialised — expected on every event except the first
        }

        AuditEventRepository.ChainHead head = repository.lockChainHead(tenantId)
            .orElseThrow(() -> new IllegalStateException(
                "audit_chain_head missing immediately after initChainHead for tenant=" + tenantId));

        String prevSignature = head.lastSignature() == null ? GENESIS : head.lastSignature();
        String canonical = canonicalize(event, tenantId);
        String signature = crypto.sm3Hex(prevSignature + CANONICAL_PREV_SEPARATOR + canonical);

        AuditEventRecord record = new AuditEventRecord(
            null,
            event.id(),
            event.traceId(),
            event.occurredAt() == null ? Instant.now() : event.occurredAt(),
            event.actorUserId(),
            event.action().name(),
            event.resourceType(),
            event.resourceId(),
            event.summary(),
            event.payloadDigest(),
            tenantId,
            event.orgScope() == null ? null : event.orgScope().hospitalId(),
            event.orgScope() == null ? null : event.orgScope().departmentId(),
            head.lastEventId(),
            prevSignature,
            signature,
            STATUS_SIGNED,
            event.outcome() == null ? AuditEvent.OUTCOME_SUCCESS : event.outcome(),
            event.errorCode(),
            null
        );
        repository.insertEvent(record);
        repository.advanceChainHead(tenantId, event.id(), signature);
        return record;
    }

    /** 验签：重放规范载荷重新计算签名，与存储的签名比对。 */
    public boolean verify(AuditEventRecord record) {
        AuditEvent reconstructed = new AuditEvent(
            record.eventId(),
            record.traceId(),
            record.occurredAt(),
            record.actorUserId(),
            com.medkernel.shared.audit.AuditAction.valueOf(record.action()),
            record.resourceType(),
            record.resourceId(),
            record.summary(),
            record.payloadDigest(),
            new OrgScope(
                isSystemTenant(record.tenantId()) ? null : record.tenantId(),
                null,
                record.hospitalId(),
                null,
                null,
                record.departmentId(),
                null),
            record.outcome(),
            record.errorCode()
        );
        String canonical = canonicalize(reconstructed, record.tenantId());
        String prev = record.prevSignature() == null ? GENESIS : record.prevSignature();
        String expected = crypto.sm3Hex(prev + CANONICAL_PREV_SEPARATOR + canonical);
        return expected.equals(record.signature());
    }

    public static String canonicalize(AuditEvent event, String tenantId) {
        OrgScope scope = event.orgScope() == null ? OrgScope.empty() : event.orgScope();
        return String.join(
            CANONICAL_FIELD_SEPARATOR,
            nullSafe(event.action() == null ? null : event.action().name()),
            nullSafe(event.resourceType()),
            nullSafe(event.resourceId()),
            nullSafe(event.actorUserId()),
            nullSafe(tenantId),
            nullSafe(scope.hospitalId()),
            nullSafe(scope.departmentId()),
            event.occurredAt() == null ? "" : event.occurredAt().toString(),
            nullSafe(event.payloadDigest())
        );
    }

    private static boolean isSystemTenant(String tenantId) {
        return SYSTEM_TENANT.equals(tenantId);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String resolveTenant(OrgScope scope) {
        if (scope != null && scope.hasTenant()) {
            return scope.tenantId();
        }
        return SYSTEM_TENANT;
    }
}
