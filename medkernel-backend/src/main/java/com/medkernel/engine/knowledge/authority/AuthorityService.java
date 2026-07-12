package com.medkernel.engine.knowledge.authority;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

/**
 * 平台知识权威稳定身份服务。
 *
 * <p>权威身份只能由唯一平台主租户 {@code t-1} 初始化。调用方必须提供与部署环境解耦的
 * 稳定标识；服务不读取 IP、主机名或部署目录，重启、迁机和备份恢复均以数据库中的既有身份为准。
 */
@Service
public class AuthorityService {

    private static final String RESOURCE_TYPE = "mk_knowledge_authority";
    private static final String UNRESOLVED_AUTHORITY_ID = "UNRESOLVED_AUTHORITY_ID";
    private static final Pattern AUTHORITY_ID_PATTERN =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final AuthorityRepository repository;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;

    public AuthorityService(AuthorityRepository repository,
                            AuditRecorder auditRecorder,
                            IsolatedAuditPublisher isolatedAudit) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
        this.isolatedAudit = isolatedAudit;
    }

    /**
     * 初始化或读取唯一平台知识权威。
     *
     * @param requestedAuthorityId 与宿主环境解耦、后续不可变的权威标识
     * @return 首次持久化或数据库中已存在的同一权威
     */
    @Transactional
    public Authority initialize(String requestedAuthorityId) {
        assertPlatformTenant(requestedAuthorityId);
        String authorityId = validateAuthorityId(requestedAuthorityId);

        Authority existing = repository.findByTenantId(PlatformTenant.ID).orElse(null);
        if (existing != null) {
            if (Objects.equals(existing.authorityId(), authorityId)) {
                return existing;
            }
            rejectSecondAuthority(existing.authorityId(), authorityId);
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        Authority saved = repository.save(new Authority(
            null,
            PlatformTenant.ID,
            authorityId,
            null,
            null,
            0,
            0,
            null,
            now,
            actor,
            now,
            actor,
            traceId()));
        auditRecorder.record(
            AuditAction.CREATE,
            RESOURCE_TYPE,
            authorityId,
            "初始化平台知识权威 authorityId=" + authorityId);
        return saved;
    }

    private void assertPlatformTenant(String requestedAuthorityId) {
        OrgScope scope = RequestContext.currentOrgScope();
        String tenantId = scope == null ? null : scope.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            rejectOwnership(
                requestedAuthorityId,
                ErrorCode.TENANT_CONTEXT_MISSING,
                "初始化平台知识权威失败：缺少平台租户上下文");
        }
        if (!PlatformTenant.ID.equals(tenantId)) {
            rejectOwnership(
                requestedAuthorityId,
                ErrorCode.TENANT_FORBIDDEN,
                "初始化平台知识权威失败：权威只能归属唯一平台主租户 " + PlatformTenant.ID);
        }
    }

    private String validateAuthorityId(String requestedAuthorityId) {
        if (!isValidAuthorityId(requestedAuthorityId)) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.CREATE,
                RESOURCE_TYPE,
                UNRESOLVED_AUTHORITY_ID,
                ErrorCode.VALIDATION_FAILED.code(),
                "初始化平台知识权威失败：authorityId 必须为 1 至 128 位稳定安全标识"));
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "authorityId 必须为 1 至 128 位字母、数字、点、下划线、冒号或连字符");
        }
        return requestedAuthorityId;
    }

    private void rejectSecondAuthority(String existingAuthorityId, String requestedAuthorityId) {
        String summary = "初始化平台知识权威失败：现有 authorityId=" + existingAuthorityId
            + "，请求 authorityId=" + requestedAuthorityId;
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.CREATE,
            RESOURCE_TYPE,
            existingAuthorityId,
            ErrorCode.CONFLICT.code(),
            summary));
        throw new ApiException(ErrorCode.CONFLICT, summary);
    }

    private void rejectOwnership(String requestedAuthorityId, ErrorCode errorCode, String summary) {
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.CREATE,
            RESOURCE_TYPE,
            auditAuthorityId(requestedAuthorityId),
            errorCode.code(),
            summary));
        throw new ApiException(errorCode, summary);
    }

    private String auditAuthorityId(String requestedAuthorityId) {
        return isValidAuthorityId(requestedAuthorityId)
            ? requestedAuthorityId
            : UNRESOLVED_AUTHORITY_ID;
    }

    private boolean isValidAuthorityId(String requestedAuthorityId) {
        return requestedAuthorityId != null
            && requestedAuthorityId.equals(requestedAuthorityId.trim())
            && AUTHORITY_ID_PATTERN.matcher(requestedAuthorityId).matches();
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }
}
