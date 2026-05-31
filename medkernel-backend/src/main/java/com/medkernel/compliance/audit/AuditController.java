package com.medkernel.compliance.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.CursorRequest;
import com.medkernel.shared.api.CursorResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditConfigChangeCommand;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.AuditSafetyGuard;
import com.medkernel.shared.audit.persistence.AuditEventRecord;
import com.medkernel.shared.audit.persistence.AuditQueryService;
import com.medkernel.shared.crypto.SmCryptoService;
import com.medkernel.shared.datascope.DataScope;

/**
 * 合规审计 API（GA-ENG-BASE-04）。
 *
 * <p>本控制器只暴露读端和导出端：
 * <ul>
 *   <li>{@code GET /events} — 当前租户的审计事件流，游标分页 + 过滤</li>
 *   <li>{@code POST /snapshot} — 发布一个 {@code EXPORT} 审计事件并返回落库后的视图，
 *       既是导出快照接口，也是端到端的链路校验</li>
 * </ul>
 *
 * <p>所有访问受 {@code @DataScope(requireTenant=true)} 保护，没有租户上下文的请求返回
 * {@code ENG-BASE-001 TENANT_CONTEXT_MISSING}。
 */
@RestController
@RequestMapping("/api/v1/compliance/audit")
@DataScope(requireTenant = true)
public class AuditController {

    private final AuditQueryService queryService;
    private final AuditEventPublisher publisher;
    private final SmCryptoService crypto;
    private final AuditSafetyGuard safetyGuard;

    public AuditController(AuditQueryService queryService,
                           AuditEventPublisher publisher,
                           SmCryptoService crypto,
                           AuditSafetyGuard safetyGuard) {
        this.queryService = queryService;
        this.publisher = publisher;
        this.crypto = crypto;
        this.safetyGuard = safetyGuard;
    }

    @GetMapping("/events")
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<CursorResponse<AuditEvent>> events(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String orgPath,
            @RequestParam(required = false) String environmentKey,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Boolean superAdminOnly,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        CursorRequest req = new CursorRequest(cursor, size);
        CursorResponse<AuditEventRecord> records = queryService.list(
            req, action, resourceType, actorUserId, orgPath, environmentKey, outcome, superAdminOnly, from, to);
        List<AuditEvent> items = records.items().stream().map(AuditEvent::from).toList();
        return ApiResult.ok(CursorResponse.of(items, records.nextCursor()));
    }

    @PostMapping("/settings/validate")
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<Map<String, Boolean>> validateSettingChange(
            @Valid @RequestBody AuditSettingChangeRequest request) {
        safetyGuard.assertChangeAllowed(request.toCommand());
        return ApiResult.ok(Map.of("accepted", true));
    }

    @PostMapping("/snapshot")
    @PreAuthorize("@perm.has('audit.export')")
    public ApiResult<AuditEvent> snapshot(@RequestParam(defaultValue = "manual") String reason) {
        String resourceId = "snapshot-" + Instant.now().toEpochMilli();
        String digest = "sm3:" + crypto.sm3Hex(reason + "|" + resourceId);

        com.medkernel.shared.audit.AuditEvent event = com.medkernel.shared.audit.AuditEvent
            .of(AuditAction.EXPORT, "audit", resourceId,
                "导出审计快照（reason=" + reason + "）")
            .withPayloadDigest(digest);
        publisher.publish(event);

        AuditEventRecord persisted = queryService.findByEventId(event.id())
            .orElseThrow(() -> ApiException.notFound("审计事件 " + event.id()));
        return ApiResult.ok(AuditEvent.from(persisted));
    }

    public record AuditSettingChangeRequest(
        @NotBlank String key,
        String beforeValue,
        @NotBlank String value,
        String reason
    ) {

        AuditConfigChangeCommand toCommand() {
            return new AuditConfigChangeCommand(key, beforeValue, value, reason);
        }
    }
}
