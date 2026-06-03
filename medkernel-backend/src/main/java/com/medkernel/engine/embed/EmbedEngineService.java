package com.medkernel.engine.embed;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 页面嵌入引擎服务实现类 (GA-ENG-API-11)。
 *
 * <p>提供嵌入 Launch Token 的生命周期管理（短时生成、原子锁定校验）、Origin安全域名过滤以及医生反馈决策闭环子事务留痕能力。
 */
@Service
public class EmbedEngineService {

    private static final int DEFAULT_EXPIRE_SECONDS = 60;
    private static final String LAUNCH_ENDPOINT = "/api/v1/engine/embed/launch";
    private static final String CDS_HOOK_VERSION = "1.0";

    private final EmbedLaunchTokenRepository tokenRepo;
    private final EmbedOriginWhitelistRepository originRepo;
    private final AuditEventPublisher auditPublisher;
    private final IsolatedAuditPublisher isolatedAudit;

    public EmbedEngineService(EmbedLaunchTokenRepository tokenRepo,
                              EmbedOriginWhitelistRepository originRepo,
                              AuditEventPublisher auditPublisher,
                              IsolatedAuditPublisher isolatedAudit) {
        this.tokenRepo = tokenRepo;
        this.originRepo = originRepo;
        this.auditPublisher = auditPublisher;
        this.isolatedAudit = isolatedAudit;
    }

    /**
     * 生成一次性嵌入启动令牌。
     *
     * @param req 令牌申请请求信息，含用户、就诊和触发位置点
     * @return 启动令牌及拼接好的嵌入URL
     */
    @Transactional
    public EmbedLaunchTokenResponse generateToken(EmbedLaunchTokenRequest req) {
        String tenantId = requireCurrentTenant();
        String createdBy = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();

        String tokenValue = "tkn-" + UUID.randomUUID().toString().replace("-", "");
        int expireSec = req.expireSeconds() != null && req.expireSeconds() > 0 ? req.expireSeconds() : DEFAULT_EXPIRE_SECONDS;
        Instant now = Instant.now();
        Instant expiredAt = now.plusSeconds(expireSec);

        String hookInstance = req.hookInstance() == null || req.hookInstance().isBlank()
            ? traceId
            : req.hookInstance();
        EmbedLaunchToken entity = new EmbedLaunchToken(
            null,
            tokenValue,
            tenantId,
            req.userId(),
            req.roleCode(),
            req.patientId(),
            req.encounterId(),
            req.triggerPoint(),
            "UNUSED",
            expiredAt,
            now,
            createdBy,
            now,
            createdBy,
            traceId,
            req.integrationMode().name(),
            req.hook(),
            hookInstance,
            null
        );
        tokenRepo.save(entity);

        // 拼接默认页面嵌入 URL，外部 HIS 可直接使用此 URL 嵌入
        String embedUrl = String.format("/embed/launch?token=%s", tokenValue);

        auditPublisher.publish(AuditAction.CREATE, "embed_launch_token", tokenValue,
            "生成嵌入启动令牌 triggerPoint=" + req.triggerPoint() + " patientId=" + req.patientId());

        return new EmbedLaunchTokenResponse(tokenValue, expiredAt, embedUrl,
            req.integrationMode(), LAUNCH_ENDPOINT, req.hook());
    }

    /**
     * 校验并原子性消费启动令牌，获取当前嵌入会话上下文。
     *
     * @param token 启动令牌
     * @param originHeader 请求头中的 Origin 域名，用于安全过滤
     * @return 会话及关联的临床上下文
     */
    @Transactional
    public EmbedLaunchContextResponse validateAndExchange(EmbedLaunchRequest request, String originHeader) {
        EmbedLaunchToken entity = tokenRepo.findByToken(request.token())
            .orElseThrow(() -> {
                publishFailureAudit(ErrorCode.ENG_EMBED_004, "启动令牌不存在 token=" + request.token());
                return new ApiException(ErrorCode.ENG_EMBED_004, "启动令牌不存在");
            });

        String tenantId = entity.tenantId();
        requireAllowedOrigin(tenantId, originHeader, request.token());
        validateLaunchContract(request, entity);

        // 2. 令牌状态与时效性校验
        if (EmbedLaunchTokenStatus.USED.name().equalsIgnoreCase(entity.status())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_003, "启动令牌已被使用 token=" + request.token());
            throw new ApiException(ErrorCode.ENG_EMBED_003, "启动令牌已被使用");
        }

        Instant now = Instant.now();
        if (EmbedLaunchTokenStatus.EXPIRED.name().equalsIgnoreCase(entity.status()) || !now.isBefore(entity.expiredAt())) {
            if (EmbedLaunchTokenStatus.UNUSED.name().equalsIgnoreCase(entity.status())) {
                EmbedLaunchToken expired = new EmbedLaunchToken(
                    entity.id(), entity.token(), entity.tenantId(), entity.userId(), entity.roleCode(),
                    entity.patientId(), entity.encounterId(), entity.triggerPoint(), EmbedLaunchTokenStatus.EXPIRED.name(),
                    entity.expiredAt(), entity.createdAt(), entity.createdBy(), now, actor(),
                    entity.traceId(), entity.integrationMode(), entity.hook(), entity.hookInstance(), entity.consumedAt()
                );
                tokenRepo.save(expired);
            }
            publishFailureAudit(ErrorCode.ENG_EMBED_001, "启动令牌已过期 token=" + request.token());
            throw new ApiException(ErrorCode.ENG_EMBED_001, "启动令牌已过期");
        }

        if (!EmbedLaunchTokenStatus.UNUSED.name().equalsIgnoreCase(entity.status())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005, "启动令牌状态不允许消费 token=" + request.token());
            throw new ApiException(ErrorCode.ENG_EMBED_005, "启动令牌状态不允许消费");
        }

        // 3. 原子标记为已使用，实现一次性物理消费
        int consumed = tokenRepo.consumeUnusedToken(request.token(), tenantId, now, now, actor());
        if (consumed != 1) {
            throw classifyFailedAtomicConsume(request.token(), tenantId);
        }

        auditPublisher.publish(AuditAction.EXECUTE, "embed_launch_token", request.token(),
            "消费嵌入令牌成功 userId=" + entity.userId() + " triggerPoint=" + entity.triggerPoint());

        return new EmbedLaunchContextResponse(
            entity.userId(),
            entity.roleCode(),
            entity.tenantId(),
            entity.patientId(),
            entity.encounterId(),
            entity.triggerPoint(),
            true,
            entity.traceId(),
            parseIntegrationMode(entity.integrationMode()),
            entity.hook(),
            entity.hookInstance(),
            EmbedModelStatus.MODEL_DISABLED,
            EmbedConnectionStatus.CONNECTED,
            CDS_HOOK_VERSION
        );
    }

    /**
     * 回传记录医师在工作站嵌入页面的交互采纳与拒绝反馈，强制采用隔离独立子事务记录审计。
     *
     * @param req 反馈请求参数
     */
    @Transactional
    public EmbedFeedbackResponse feedback(EmbedFeedbackRequest req) {
        EmbedLaunchToken entity = tokenRepo.findByToken(req.token())
            .orElseThrow(() -> {
                publishFailureAudit(ErrorCode.ENG_EMBED_004, "提交反馈失败，启动令牌不存在 token=" + req.token());
                return new ApiException(ErrorCode.ENG_EMBED_004, "启动令牌不存在");
            });
        if (!EmbedLaunchTokenStatus.USED.name().equalsIgnoreCase(entity.status())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005, "提交反馈失败，令牌未完成一次性消费 token=" + req.token());
            throw new ApiException(ErrorCode.ENG_EMBED_005, "令牌未完成一次性消费，拒绝反馈回调");
        }

        // 记录闭环反馈审计事件
        auditPublisher.publish(AuditAction.FEEDBACK, "embed_launch_token", req.token(),
            String.format("医生提交交互反馈 actionType=%s reason=%s patientId=%s",
                req.actionType(), req.reason() != null ? req.reason() : "", entity.patientId()));
        return new EmbedFeedbackResponse(req.token(), req.actionType(), EmbedConnectionStatus.NOT_CONNECTED, entity.traceId());
    }

    /**
     * 为当前租户添加允许嵌入 Origin 白名单。
     *
     * @param req 域名Origin配置
     */
    @Transactional
    public void addOrigin(EmbedOriginRequest req) {
        String tenantId = requireCurrentTenant();
        String createdBy = RequestContext.currentUserId().orElse("system");

        Optional<EmbedOriginWhitelist> existing = originRepo.findByTenantIdAndOrigin(tenantId, req.origin());
        if (existing.isPresent()) {
            return;
        }

        Instant now = Instant.now();
        EmbedOriginWhitelist entity = new EmbedOriginWhitelist(
            null,
            tenantId,
            req.origin(),
            now,
            createdBy,
            now,
            createdBy
        );
        originRepo.save(entity);

        auditPublisher.publish(AuditAction.CREATE, "embed_origin_whitelist", req.origin(),
            "添加Origin安全域名白名单 origin=" + req.origin());
    }

    /**
     * 获取当前租户下配置的所有 Origin 白名单列表。
     *
     * @return Origin域名白名单列表
     */
    @Transactional(readOnly = true)
    public List<String> getOrigins() {
        String tenantId = requireCurrentTenant();
        return originRepo.findByTenantId(tenantId).stream()
            .map(EmbedOriginWhitelist::origin)
            .toList();
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private void validateLaunchContract(EmbedLaunchRequest request, EmbedLaunchToken entity) {
        EmbedIntegrationMode tokenMode = parseIntegrationMode(entity.integrationMode());
        if (request.integrationMode() != tokenMode) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005,
                "嵌入方式不匹配 token=" + request.token() + " expected=" + tokenMode
                    + " actual=" + request.integrationMode());
            throw new ApiException(ErrorCode.ENG_EMBED_005, "嵌入方式与启动令牌不匹配");
        }
        if (hasText(request.hook()) && hasText(entity.hook()) && !request.hook().equals(entity.hook())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005,
                "CDS Hook 不匹配 token=" + request.token() + " expected=" + entity.hook()
                    + " actual=" + request.hook());
            throw new ApiException(ErrorCode.ENG_EMBED_005, "CDS Hook 与启动令牌不匹配");
        }
        if (hasText(request.hookInstance()) && hasText(entity.hookInstance())
                && !request.hookInstance().equals(entity.hookInstance())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005,
                "CDS Hook 实例不匹配 token=" + request.token());
            throw new ApiException(ErrorCode.ENG_EMBED_005, "CDS Hook 实例与启动令牌不匹配");
        }
    }

    private void requireAllowedOrigin(String tenantId, String originHeader, String token) {
        if (!hasText(originHeader)) {
            publishFailureAudit(ErrorCode.ENG_EMBED_002, "缺少 Origin 白名单校验信息 token=" + token);
            throw new ApiException(ErrorCode.ENG_EMBED_002, "缺少 Origin 白名单校验信息");
        }
        String origin = originHeader.trim();
        boolean allowed = originRepo.findByTenantIdAndOrigin(tenantId, origin).isPresent();
        if (!allowed) {
            publishFailureAudit(ErrorCode.ENG_EMBED_002, "非法的 Origin 域名=" + origin);
            throw new ApiException(ErrorCode.ENG_EMBED_002, "非法的 Origin 域名: " + origin);
        }
    }

    private ApiException classifyFailedAtomicConsume(String token, String tenantId) {
        Optional<EmbedLaunchToken> current = tokenRepo.findByToken(token)
            .filter(t -> tenantId.equals(t.tenantId()));
        if (current.isPresent() && EmbedLaunchTokenStatus.USED.name().equalsIgnoreCase(current.get().status())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_003, "启动令牌已被并发使用 token=" + token);
            return new ApiException(ErrorCode.ENG_EMBED_003, "启动令牌已被使用");
        }
        if (current.isPresent() && Instant.now().isAfter(current.get().expiredAt())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_001, "启动令牌并发消费时已过期 token=" + token);
            return new ApiException(ErrorCode.ENG_EMBED_001, "启动令牌已过期");
        }
        publishFailureAudit(ErrorCode.ENG_EMBED_005, "启动令牌原子消费失败 token=" + token);
        return new ApiException(ErrorCode.ENG_EMBED_005, "启动令牌无法完成原子消费");
    }

    private EmbedIntegrationMode parseIntegrationMode(String value) {
        if (!hasText(value)) {
            return EmbedIntegrationMode.IFRAME;
        }
        try {
            return EmbedIntegrationMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.ENG_EMBED_005, "嵌入方式不受支持: " + value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void publishFailureAudit(ErrorCode code, String summary) {
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.EXECUTE, "embed_launch_token", null, code.code(), summary));
    }
}
