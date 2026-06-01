package com.medkernel.shared.observability;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 基于数据库的 payload 存储实现。
 *
 * <p>OBS-01 之后不再允许 JVM 内存作为默认 payload 存储，避免重启丢证据和多实例不一致。
 */
@Component
public class DbPayloadStorage implements PayloadStoragePort {

    private static final String DB_URI_PREFIX = "db://mk_obs_payload_store/";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String SYSTEM_ACTOR = "system";

    private final PayloadStoreRepository repository;

    public DbPayloadStorage(PayloadStoreRepository repository) {
        this.repository = repository;
    }

    @Override
    public PayloadRef put(PayloadDescriptor descriptor, byte[] payload) {
        if (payload == null) {
            payload = new byte[0];
        }
        String tenantId = resolveTenantId(descriptor);
        String payloadId = "pl-" + UUID.randomUUID();
        String contentType = normalizeContentType(descriptor == null ? null : descriptor.contentType());
        Instant now = Instant.now();
        PayloadStoreRecord saved = repository.save(new PayloadStoreRecord(
            null,
            payloadId,
            tenantId,
            orgPath(RequestContext.currentOrgScope()),
            required(descriptor == null ? null : descriptor.entityType(), "entityType"),
            required(descriptor == null ? null : descriptor.entityId(), "entityId"),
            RequestContext.currentTraceId(),
            PayloadRef.STORAGE_INLINE,
            contentType,
            sha256(payload),
            payload.length,
            Base64.getEncoder().encodeToString(payload),
            null,
            now,
            RequestContext.currentUserId().orElse(SYSTEM_ACTOR),
            null,
            null
        ));
        return toRef(saved);
    }

    @Override
    public byte[] get(PayloadRef ref) {
        PayloadStoreRecord record = repository.findByPayloadIdAndDeletedAtIsNull(payloadId(ref))
            .orElseThrow(() -> missingPayload(ref));
        if (!PayloadRef.STORAGE_INLINE.equals(record.storageType()) || record.payloadBase64() == null) {
            throw missingPayload(ref);
        }
        return Base64.getDecoder().decode(record.payloadBase64());
    }

    @Override
    public void delete(PayloadRef ref) {
        PayloadStoreRecord record = repository.findByPayloadIdAndDeletedAtIsNull(payloadId(ref))
            .orElseThrow(() -> missingPayload(ref));
        repository.save(record.markDeleted(
            Instant.now(),
            RequestContext.currentUserId().orElse(SYSTEM_ACTOR)
        ));
    }

    @Override
    public List<PayloadRef> findByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return repository.findByTraceIdAndDeletedAtIsNullOrderByCreatedAtAsc(traceId)
            .stream()
            .map(this::toRef)
            .toList();
    }

    private PayloadRef toRef(PayloadStoreRecord record) {
        return new PayloadRef(
            record.storageType(),
            record.digest(),
            DB_URI_PREFIX + record.payloadId(),
            record.sizeBytes(),
            record.contentType()
        );
    }

    private String resolveTenantId(PayloadDescriptor descriptor) {
        if (descriptor != null && descriptor.tenantId() != null && !descriptor.tenantId().isBlank()) {
            return descriptor.tenantId();
        }
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope.hasTenant()) {
            return scope.tenantId();
        }
        throw ApiException.tenantMissing();
    }

    private String payloadId(PayloadRef ref) {
        if (ref == null || ref.uri() == null || !ref.uri().startsWith(DB_URI_PREFIX)) {
            throw missingPayload(ref);
        }
        String payloadId = ref.uri().substring(DB_URI_PREFIX.length());
        if (payloadId.isBlank()) {
            throw missingPayload(ref);
        }
        return payloadId;
    }

    private static String orgPath(OrgScope scope) {
        if (scope == null) {
            return null;
        }
        String path = Stream.of(
                scope.tenantId(), scope.groupId(), scope.hospitalId(), scope.campusId(),
                scope.siteId(), scope.departmentId(), scope.specialtyId())
            .filter(value -> value != null && !value.isBlank())
            .reduce((left, right) -> left + "/" + right)
            .orElse(null);
        return path == null || path.isBlank() ? null : path;
    }

    private static String normalizeContentType(String contentType) {
        return contentType == null || contentType.isBlank()
            ? DEFAULT_CONTENT_TYPE
            : contentType;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, field + " 不能为空");
        }
        return value;
    }

    private static ApiException missingPayload(PayloadRef ref) {
        String uri = ref == null ? "<null>" : ref.uri();
        return new ApiException(ErrorCode.ENG_OBS_001, "payload 不存在或已归档: " + uri);
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
