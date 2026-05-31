package com.medkernel.shared.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * MedKernel 共享审计事件（{@code com.medkernel.shared.audit.AuditEvent}）。
 *
 * <p>与 {@code com.medkernel.compliance.audit.AuditEvent}（合规模块的对外展示 DTO）区分：
 * <ul>
 *   <li>本类是引擎内部统一发出的事件契约，承载完整组织/追踪上下文</li>
 *   <li>{@code compliance.audit.AuditEvent} 是面向客户端的审计列表 DTO，由本事件投影而来</li>
 * </ul>
 *
 * <p>持久化 + SM3 哈希链由 {@code com.medkernel.shared.audit.persistence.AuditPersistenceSink}
 * 在 {@code AFTER_COMMIT} 阶段完成（GA-ENG-BASE-04）。
 *
 * <p>{@code outcome} 区分业务成功/失败（CONSTITUTION §8 审计链）：{@link #of} 默认 SUCCESS；
 * 业务失败用 {@link #failure} 工厂发出 outcome=FAILED + errorCode。事件的身份、角色、组织路径、
 * 环境与变更快照会参与持久化 SM3 规范串，保证证据链可重放校验。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEvent(
    String id,
    String traceId,
    Instant occurredAt,
    String actorUserId,
    AuditAction action,
    String resourceType,
    String resourceId,
    String summary,
    String payloadDigest,
    OrgScope orgScope,
    String outcome,
    String errorCode,
    String actorRoles,
    String orgPath,
    String environmentKey,
    String beforeSnapshot,
    String afterSnapshot
) {

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILED  = "FAILED";

    public AuditEvent(String id,
                      String traceId,
                      Instant occurredAt,
                      String actorUserId,
                      AuditAction action,
                      String resourceType,
                      String resourceId,
                      String summary,
                      String payloadDigest,
                      OrgScope orgScope,
                      String outcome,
                      String errorCode) {
        this(id, traceId, occurredAt, actorUserId, action, resourceType, resourceId, summary,
            payloadDigest, orgScope, outcome, errorCode, null, orgPath(orgScope), null, null, null);
    }

    public static AuditEvent of(AuditAction action, String resourceType, String resourceId, String summary) {
        return of(action, resourceType, resourceId, summary, null, null, null, null, Instant.now());
    }

    public static AuditEvent of(AuditAction action,
                                String resourceType,
                                String resourceId,
                                String summary,
                                String payloadDigest,
                                String environmentKey,
                                String beforeSnapshot,
                                String afterSnapshot,
                                Instant occurredAt) {
        OrgScope orgScope = RequestContext.currentOrgScope();
        return new AuditEvent(
            UUID.randomUUID().toString(),
            RequestContext.currentTraceId(),
            occurredAt == null ? Instant.now() : occurredAt,
            RequestContext.currentUserId().orElse(null),
            action, resourceType, resourceId, summary,
            payloadDigest,
            orgScope,
            OUTCOME_SUCCESS, null,
            currentActorRoles(),
            orgPath(orgScope),
            environmentKey,
            beforeSnapshot,
            afterSnapshot
        );
    }

    /** 业务失败留痕：发出 outcome=FAILED + errorCode 的 audit。 */
    public static AuditEvent failure(AuditAction action, String resourceType, String resourceId,
                                     String errorCode, String summary) {
        OrgScope orgScope = RequestContext.currentOrgScope();
        return new AuditEvent(
            UUID.randomUUID().toString(),
            RequestContext.currentTraceId(),
            Instant.now(),
            RequestContext.currentUserId().orElse(null),
            action, resourceType, resourceId, summary,
            null,
            orgScope,
            OUTCOME_FAILED, errorCode,
            currentActorRoles(),
            orgPath(orgScope),
            null,
            null,
            null
        );
    }

    public AuditEvent withPayloadDigest(String digest) {
        return new AuditEvent(id, traceId, occurredAt, actorUserId, action,
            resourceType, resourceId, summary, digest, orgScope, outcome, errorCode,
            actorRoles, orgPath, environmentKey, beforeSnapshot, afterSnapshot);
    }

    public static String currentActorRoles() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        String roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority != null && authority.startsWith("ROLE_"))
            .sorted(Comparator.naturalOrder())
            .distinct()
            .collect(Collectors.joining(","));
        return roles.isBlank() ? null : roles;
    }

    public static String orgPath(OrgScope scope) {
        if (scope == null) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        addSegment(segments, "tenant", scope.tenantId());
        addSegment(segments, "group", scope.groupId());
        addSegment(segments, "hospital", scope.hospitalId());
        addSegment(segments, "campus", scope.campusId());
        addSegment(segments, "site", scope.siteId());
        addSegment(segments, "department", scope.departmentId());
        addSegment(segments, "specialty", scope.specialtyId());
        return segments.isEmpty() ? null : String.join("/", segments);
    }

    private static void addSegment(List<String> segments, String name, String value) {
        if (value != null && !value.isBlank()) {
            segments.add(name + ":" + value.trim());
        }
    }
}
