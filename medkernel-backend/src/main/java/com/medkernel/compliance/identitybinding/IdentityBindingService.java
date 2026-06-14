package com.medkernel.compliance.identitybinding;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import com.medkernel.shared.ids.Ulid;

/**
 * D5 身份绑定编排服务。
 *
 * <p>本服务只管理用户与外部身份的映射，不复制 D0 登录认证和权限求值能力。
 */
@Service
public class IdentityBindingService {

    private static final String ACTIVE = "ACTIVE";
    private static final String UNBOUND = "UNBOUND";
    private static final String DIGEST_PREFIX = "sm3:";

    private final IdentityBindingRepository repository;
    private final TenantUserRepository users;
    private final SmCryptoService crypto;
    private final AuditRecorder auditRecorder;

    public IdentityBindingService(IdentityBindingRepository repository,
                                  TenantUserRepository users,
                                  SmCryptoService crypto,
                                  AuditRecorder auditRecorder) {
        this.repository = repository;
        this.users = users;
        this.crypto = crypto;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 查询当前租户全部绑定状态，包含已解绑记录，便于管理员核查当前关系。
     */
    @Transactional(readOnly = true)
    public List<IdentityBindingResponse> list(String tenantId) {
        return repository.findByTenantIdOrderByUpdatedAtDesc(requireTenant(tenantId)).stream()
            .map(IdentityBindingResponse::from)
            .toList();
    }

    /**
     * 查询指定租户成员的身份来源。
     */
    @Transactional(readOnly = true)
    public List<IdentityBindingResponse> listForUser(String tenantId, String userId) {
        return repository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(
                requireTenant(tenantId), userId.trim())
            .stream()
            .map(IdentityBindingResponse::from)
            .toList();
    }

    /**
     * 创建租户内外部身份绑定。同一身份重复提交到同一用户时按幂等成功返回，
     * 已绑定其他用户或同一用户已有同类型身份时返回冲突。
     */
    @Transactional
    public IdentityBindingResponse create(String tenantId, IdentityBindingCreateRequest request) {
        String safeTenant = requireTenant(tenantId);
        String userId = request.userId().trim();
        requireKnownUser(safeTenant, userId);
        String providerType = request.providerType().name();
        String externalSubject = request.externalSubject().trim();
        String digest = DIGEST_PREFIX + crypto.sm3Hex(externalSubject);
        return createDigest(
            safeTenant,
            userId,
            providerType,
            digest,
            subjectHint(externalSubject),
            request.reason());
    }

    /**
     * 使用已计算的国密摘要建立身份来源，供不保存身份原文的批量导入任务调用。
     */
    @Transactional
    public IdentityBindingResponse createDigest(
            String tenantId,
            String userId,
            String providerType,
            String digest,
            String subjectHint,
            String reason) {
        String safeTenant = requireTenant(tenantId);
        String safeUserId = userId.trim();
        requireKnownUser(safeTenant, safeUserId);

        var existingSubject = repository.findByTenantIdAndProviderTypeAndExternalSubjectDigest(
            safeTenant, providerType, digest);
        if (existingSubject.isPresent()) {
            IdentityBinding existing = existingSubject.get();
            if (ACTIVE.equals(existing.status()) && safeUserId.equals(existing.userId())) {
                return IdentityBindingResponse.from(existing);
            }
            if (ACTIVE.equals(existing.status())) {
                throw ApiException.conflict("该外部身份已绑定其他用户");
            }
            repository.findByTenantIdAndUserIdAndProviderTypeAndStatus(
                    safeTenant, safeUserId, providerType, ACTIVE)
                .ifPresent(ignored -> {
                    throw ApiException.conflict("该用户已绑定同类型外部身份");
                });
            return rebind(existing, safeUserId, reason);
        }
        repository.findByTenantIdAndUserIdAndProviderTypeAndStatus(
                safeTenant, safeUserId, providerType, ACTIVE)
            .ifPresent(ignored -> {
                throw ApiException.conflict("该用户已绑定同类型外部身份");
            });

        String actor = RequestContext.currentUserId().orElse("system");
        Instant now = Instant.now();
        IdentityBinding saved = repository.save(new IdentityBinding(
            null,
            "idb-" + Ulid.newUlid(),
            safeTenant,
            safeUserId,
            providerType,
            digest,
            subjectHint,
            ACTIVE,
            1L,
            null,
            now,
            actor,
            now,
            actor,
            RequestContext.currentTraceId()));
        IdentityBindingResponse response = IdentityBindingResponse.from(saved);
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            "mk_compliance_identity_binding",
            saved.bindingId(),
            "绑定外部身份：" + providerType + "；原因：" + compact(reason),
            null,
            response,
            null));
        return response;
    }

    /**
     * 精确同步当前受信业务系统负责的外部身份。
     *
     * <p>只替换或撤销当前 {@code integration:<来源>} 创建的绑定，院内人工绑定和其他
     * 业务系统绑定保持不变。提供者与身份主体同时为空表示当前来源不再提供身份。
     */
    @Transactional
    public IdentityBindingResponse syncExternalIdentity(
            String tenantId,
            String userId,
            IdentityProviderType providerType,
            String externalSubject) {
        String safeTenant = requireTenant(tenantId);
        String safeUserId = userId == null ? "" : userId.trim();
        String actor = requireExternalSourceActor();
        var user = users.findByTenantIdAndUserId(safeTenant, safeUserId)
            .orElseThrow(() -> ApiException.notFound("租户成员 " + safeUserId));
        String safeSubject = externalSubject == null ? null : externalSubject.trim();
        boolean identityAbsent = providerType == null && (safeSubject == null || safeSubject.isBlank());
        if (!identityAbsent && (providerType == null || safeSubject == null || safeSubject.isBlank())) {
            throw new ApiException(
                com.medkernel.shared.api.error.ErrorCode.BAD_REQUEST,
                "身份来源类型与外部身份必须同时提供");
        }
        if (!identityAbsent && !user.active()) {
            throw new ApiException(
                com.medkernel.shared.api.error.ErrorCode.BAD_REQUEST,
                "租户成员 " + safeUserId + " 未启用");
        }

        List<IdentityBinding> sourceOwned = repository
            .findByTenantIdAndUserIdOrderByUpdatedAtDesc(safeTenant, safeUserId)
            .stream()
            .filter(item -> ACTIVE.equals(item.status()))
            .filter(item -> actor.equals(item.createdBy()))
            .toList();
        if (identityAbsent) {
            sourceOwned.forEach(this::unbindExternalSourceBinding);
            return null;
        }

        String provider = providerType.name();
        String digest = DIGEST_PREFIX + crypto.sm3Hex(safeSubject);
        IdentityBinding desired = repository
            .findByTenantIdAndProviderTypeAndExternalSubjectDigest(
                safeTenant, provider, digest)
            .orElse(null);
        if (desired != null && !actor.equals(desired.createdBy())) {
            throw ApiException.conflict("该外部身份已由人工或其他来源维护，不能被当前来源接管");
        }
        if (desired != null && ACTIVE.equals(desired.status())
                && !safeUserId.equals(desired.userId())) {
            throw ApiException.conflict("该外部身份已绑定其他用户");
        }

        sourceOwned.stream()
            .filter(item -> desired == null || !item.bindingId().equals(desired.bindingId()))
            .forEach(this::unbindExternalSourceBinding);
        if (desired != null && ACTIVE.equals(desired.status())) {
            return IdentityBindingResponse.from(desired);
        }
        return createDigest(
            safeTenant,
            safeUserId,
            provider,
            digest,
            subjectHint(safeSubject),
            "院内主数据同步身份来源");
    }

    private void unbindExternalSourceBinding(IdentityBinding current) {
        String actor = requireExternalSourceActor();
        Instant now = Instant.now();
        IdentityBinding saved = repository.save(new IdentityBinding(
            current.id(),
            current.bindingId(),
            current.tenantId(),
            current.userId(),
            current.providerType(),
            current.externalSubjectDigest(),
            current.subjectHint(),
            UNBOUND,
            current.version() + 1L,
            "院内主数据同步撤销已失效身份",
            current.createdAt(),
            current.createdBy(),
            now,
            actor,
            RequestContext.currentTraceId()));
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            "mk_compliance_identity_binding",
            saved.bindingId(),
            "院内主数据同步解绑外部身份：" + saved.providerType(),
            IdentityBindingResponse.from(current),
            IdentityBindingResponse.from(saved),
            null));
    }

    private IdentityBindingResponse rebind(IdentityBinding current, String userId, String reason) {
        String actor = RequestContext.currentUserId().orElse("system");
        Instant now = Instant.now();
        IdentityBinding saved = repository.save(new IdentityBinding(
            current.id(),
            current.bindingId(),
            current.tenantId(),
            userId,
            current.providerType(),
            current.externalSubjectDigest(),
            current.subjectHint(),
            ACTIVE,
            current.version() + 1L,
            null,
            current.createdAt(),
            current.createdBy(),
            now,
            actor,
            RequestContext.currentTraceId()));
        IdentityBindingResponse before = IdentityBindingResponse.from(current);
        IdentityBindingResponse after = IdentityBindingResponse.from(saved);
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            "mk_compliance_identity_binding",
            saved.bindingId(),
            "重新绑定外部身份：" + saved.providerType() + "；原因：" + compact(reason),
            before,
            after,
            null));
        return after;
    }

    /**
     * 解除外部身份绑定，保留绑定记录并递增版本。
     */
    @Transactional
    public IdentityBindingResponse unbind(
            String tenantId, String bindingId, IdentityBindingUnbindRequest request) {
        String safeTenant = requireTenant(tenantId);
        IdentityBinding current = repository.findByTenantIdAndBindingId(safeTenant, bindingId)
            .orElseThrow(() -> ApiException.notFound("身份绑定 " + bindingId));
        if (UNBOUND.equals(current.status())) {
            return IdentityBindingResponse.from(current);
        }
        if (!request.expectedVersion().equals(current.version())) {
            throw ApiException.conflict("身份绑定版本冲突，请刷新后重试");
        }

        String actor = RequestContext.currentUserId().orElse("system");
        Instant now = Instant.now();
        IdentityBinding saved = repository.save(new IdentityBinding(
            current.id(),
            current.bindingId(),
            current.tenantId(),
            current.userId(),
            current.providerType(),
            current.externalSubjectDigest(),
            current.subjectHint(),
            UNBOUND,
            current.version() + 1L,
            compact(request.reason()),
            current.createdAt(),
            current.createdBy(),
            now,
            actor,
            RequestContext.currentTraceId()));
        IdentityBindingResponse before = IdentityBindingResponse.from(current);
        IdentityBindingResponse after = IdentityBindingResponse.from(saved);
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            "mk_compliance_identity_binding",
            saved.bindingId(),
            "解绑外部身份：" + saved.providerType() + "；原因：" + compact(request.reason()),
            before,
            after,
            null));
        return after;
    }

    private void requireKnownUser(String tenantId, String userId) {
        var user = users.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> ApiException.notFound("租户成员 " + userId));
        if (!user.active()) {
            throw new ApiException(
                com.medkernel.shared.api.error.ErrorCode.BAD_REQUEST,
                "租户成员 " + userId + " 未启用");
        }
    }

    private String subjectHint(String externalSubject) {
        int visibleLength = Math.min(4, externalSubject.length());
        return "****" + externalSubject.substring(externalSubject.length() - visibleLength);
    }

    private String compact(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId.trim();
    }

    private String requireExternalSourceActor() {
        String actor = RequestContext.currentUserId().orElse("");
        if (!actor.startsWith("integration:")) {
            throw ApiException.forbidden("外部身份同步必须在受信集成来源上下文中执行");
        }
        return actor;
    }
}
