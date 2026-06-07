package com.medkernel.engine.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;

/**
 * 应急权限授予服务。
 *
 * <p>所有 break-glass 授予都通过本服务落表、审计、设置过期时间；权限求值只读取未撤销且未过期的记录。
 */
@Service
public class EmergencyPermissionGrantService {

    private static final String RESOURCE_TYPE = "emergency_permission_grant";

    private final EmergencyPermissionGrantRepository repository;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    @Autowired
    public EmergencyPermissionGrantService(
            EmergencyPermissionGrantRepository repository,
            AuditRecorder auditRecorder) {
        this(repository, auditRecorder, Clock.systemUTC());
    }

    EmergencyPermissionGrantService(
            EmergencyPermissionGrantRepository repository,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public EmergencyPermissionGrant grant(
            String tenantId,
            String userId,
            Instant expiresAt,
            String reason,
            String grantedBy) {
        String safeTenantId = requireText(tenantId, "租户不能为空");
        String safeUserId = requireText(userId, "应急权限用户不能为空");
        String safeReason = requireText(reason, "应急权限原因不能为空");
        String safeGrantedBy = requireText(grantedBy, "应急权限授予人不能为空");
        Instant now = clock.instant();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "应急权限过期时间必须晚于当前时间");
        }

        EmergencyPermissionGrant saved = repository.save(new EmergencyPermissionGrant(
            null,
            safeTenantId,
            safeUserId,
            PermissionCode.ENV_EMERGENCY.code(),
            safeReason,
            safeGrantedBy,
            now,
            expiresAt,
            null,
            null,
            "Y",
            now,
            safeGrantedBy,
            now,
            safeGrantedBy
        ));
        auditRecorder.record(
            AuditAction.PERMISSION_CHANGE,
            RESOURCE_TYPE,
            resourceId(safeTenantId, safeUserId),
            "应急权限授予 user=" + safeUserId + " expiresAt=" + expiresAt);
        return saved;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String resourceId(String tenantId, String userId) {
        return tenantId + ":" + userId + ":" + PermissionCode.ENV_EMERGENCY.code();
    }
}
