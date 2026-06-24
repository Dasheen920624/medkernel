package com.medkernel.engine.embed;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.cdshook.CdsHookContract;
import com.medkernel.engine.recommendation.RecommendationCardFilter;
import com.medkernel.engine.recommendation.RecommendationCardStatus;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationFeedbackRequest;
import com.medkernel.engine.recommendation.RecommendationFeedbackType;
import com.medkernel.engine.security.AuthenticatedRoleGuard;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 页面嵌入引擎服务实现类 (GA-ENG-API-11)。
 *
 * <p>提供嵌入启动凭证的生命周期管理（短时生成、原子锁定校验）、来源域名允许清单校验以及医生反馈决策闭环子事务留痕能力。
 */
@Service
public class EmbedEngineService {

    private static final int DEFAULT_EXPIRE_SECONDS = 60;
    private static final String LAUNCH_ENDPOINT = "/api/v1/engine/embed/launch";
    private static final String CDS_HOOK_VERSION = "1.0";
    private static final String HOST_CALLBACK_NOT_CONFIGURED = "HOST_CALLBACK_NOT_CONFIGURED";
    private static final int SESSION_MINUTES = 30;

    private final EmbedLaunchTokenRepository tokenRepo;
    private final EmbedOriginWhitelistRepository originRepo;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;
    private final RecommendationEngineService recommendations;

    private record LaunchContract(String triggerPoint, String hook, String hookInstance) {}

    public EmbedEngineService(EmbedLaunchTokenRepository tokenRepo,
                              EmbedOriginWhitelistRepository originRepo,
                              AuditRecorder auditRecorder,
                              IsolatedAuditPublisher isolatedAudit,
                              RecommendationEngineService recommendations) {
        this.tokenRepo = tokenRepo;
        this.originRepo = originRepo;
        this.auditRecorder = auditRecorder;
        this.isolatedAudit = isolatedAudit;
        this.recommendations = recommendations;
    }

    /**
     * 生成一次性嵌入启动凭证。
     *
     * @param req 启动凭证申请请求信息，含用户、就诊和触发位置点
     * @return 启动凭证及拼接好的嵌入URL
     */
    @Transactional
    public EmbedLaunchTokenResponse generateToken(EmbedLaunchTokenRequest req) {
        String tenantId = requireCurrentTenant();
        String createdBy = requireCurrentUser();
        String roleCode = requireAuthenticatedRole(req.roleCode());
        String traceId = RequestContext.currentTraceId();

        String triggerPoint = requireSupportedCdsHook(req.triggerPoint(), "签发嵌入凭证触发点");
        String hook = requireSupportedCdsHook(req.hook(), "签发嵌入凭证 CDS Hook");
        requireSameCdsHook(triggerPoint, hook, "签发嵌入凭证");
        String parentOrigin = requireTokenOrigin(tenantId, req.integrationMode(), req.parentOrigin());

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
            createdBy,
            roleCode,
            req.patientId(),
            req.encounterId(),
            triggerPoint,
            "UNUSED",
            expiredAt,
            now,
            createdBy,
            now,
            createdBy,
            traceId,
            req.integrationMode().name(),
            hook,
            hookInstance,
            null,
            parentOrigin
        );
        tokenRepo.save(entity);

        String embedUrl = "/embed/launch?token=" + tokenValue;

        auditRecorder.record(AuditAction.CREATE, "embed_launch_token", tokenValue,
            "生成嵌入启动凭证 triggerPoint=" + triggerPoint + " patientId=" + req.patientId());

        return new EmbedLaunchTokenResponse(tokenValue, expiredAt, embedUrl,
            req.integrationMode(), LAUNCH_ENDPOINT, hook, hookInstance);
    }

    /**
     * 校验并原子性使用启动凭证，获取当前嵌入会话上下文。
     *
     * @param request 启动凭证兑换请求
     * @return 会话及关联的临床上下文
     */
    @Transactional
    public EmbedLaunchContextResponse validateAndExchange(EmbedLaunchRequest request) {
        EmbedLaunchToken entity = tokenRepo.findByToken(request.token())
            .orElseThrow(() -> {
                publishFailureAudit(ErrorCode.ENG_EMBED_004, "启动凭证不存在 凭证编号=" + request.token());
                return new ApiException(ErrorCode.ENG_EMBED_004, "启动凭证不存在");
            });

        String tenantId = entity.tenantId();
        String parentOrigin = requirePersistedOrigin(entity);
        LaunchContract contract = validateLaunchContract(request, entity);

        // 2. 凭证状态与时效性校验
        if (EmbedLaunchTokenStatus.USED.name().equalsIgnoreCase(entity.status())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_003, "启动凭证已被使用 凭证编号=" + request.token());
            throw new ApiException(ErrorCode.ENG_EMBED_003, "启动凭证已被使用");
        }

        Instant now = Instant.now();
        if (EmbedLaunchTokenStatus.EXPIRED.name().equalsIgnoreCase(entity.status()) || !now.isBefore(entity.expiredAt())) {
            if (EmbedLaunchTokenStatus.UNUSED.name().equalsIgnoreCase(entity.status())) {
                EmbedLaunchToken expired = new EmbedLaunchToken(
                    entity.id(), entity.token(), entity.tenantId(), entity.userId(), entity.roleCode(),
                    entity.patientId(), entity.encounterId(), entity.triggerPoint(), EmbedLaunchTokenStatus.EXPIRED.name(),
                    entity.expiredAt(), entity.createdAt(), entity.createdBy(), now, actor(),
                    entity.traceId(), entity.integrationMode(), entity.hook(), entity.hookInstance(), entity.consumedAt(),
                    entity.parentOrigin()
                );
                tokenRepo.save(expired);
            }
            publishFailureAudit(ErrorCode.ENG_EMBED_001, "启动凭证已过期 凭证编号=" + request.token());
            throw new ApiException(ErrorCode.ENG_EMBED_001, "启动凭证已过期");
        }

        if (!EmbedLaunchTokenStatus.UNUSED.name().equalsIgnoreCase(entity.status())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005, "启动凭证状态不允许使用 凭证编号=" + request.token());
            throw new ApiException(ErrorCode.ENG_EMBED_005, "启动凭证状态不允许使用");
        }

        // 3. 原子标记为已使用，实现一次性物理消费
        int consumed = tokenRepo.consumeUnusedToken(
            request.token(), tenantId, now, now.plus(SESSION_MINUTES, ChronoUnit.MINUTES), now, actor());
        if (consumed != 1) {
            throw classifyFailedAtomicConsume(request.token(), tenantId);
        }

        auditRecorder.record(AuditAction.EXECUTE, "embed_launch_token", request.token(),
            "使用嵌入启动凭证成功 userId=" + entity.userId() + " triggerPoint=" + entity.triggerPoint());

        return new EmbedLaunchContextResponse(
            entity.userId(),
            entity.roleCode(),
            entity.tenantId(),
            entity.patientId(),
            entity.encounterId(),
            contract.triggerPoint(),
            true,
            entity.traceId(),
            parseIntegrationMode(entity.integrationMode()),
            contract.hook(),
            contract.hookInstance(),
            EmbedModelStatus.MODEL_DISABLED,
            EmbedConnectionStatus.CONNECTED,
            CDS_HOOK_VERSION,
            parentOrigin
        );
    }

    /**
     * 按启动凭证绑定的临床上下文读取可处置建议，不依赖浏览器登录 Cookie。
     */
    @Transactional(readOnly = true)
    public EmbedRecommendationCardsResponse listCards(EmbedRecommendationCardsRequest req) {
        EmbedLaunchToken entity = requireActiveSession(req.token());
        return callInTokenContext(entity, () -> {
            var page = recommendations.listClinicalCards(
                new RecommendationCardFilter(
                    null, null, null, entity.patientId(), entity.encounterId(), entity.triggerPoint()),
                new PageRequest(1, PageRequest.MAX_SIZE, "createdAt,desc"));
            List<EmbedRecommendationCardResponse> items = page.items().stream()
                .filter(card -> card.status() == RecommendationCardStatus.PENDING
                    || card.status() == RecommendationCardStatus.VIEWED
                    || card.status() == RecommendationCardStatus.DEFERRED)
                .map(EmbedRecommendationCardResponse::from)
                .toList();
            return new EmbedRecommendationCardsResponse(items, entity.traceId());
        });
    }

    /**
     * 回传记录医师在工作站嵌入页面的交互采纳与拒绝反馈，强制采用隔离独立子事务记录审计。
     *
     * @param req 反馈请求参数
     */
    @Transactional
    public EmbedFeedbackResponse feedback(EmbedFeedbackRequest req) {
        EmbedFeedbackActionType actionType = requireSupportedFeedbackAction(req.actionType());
        EmbedLaunchToken entity = requireActiveSession(req.token());
        return callInTokenContext(entity, () -> {
            var detail = recommendations.cardDetail(req.cardId());
            if (detail.trigger() == null
                    || !same(entity.patientId(), detail.trigger().patientId())
                    || !same(entity.encounterId(), detail.trigger().encounterId())
                    || !same(entity.triggerPoint(), detail.trigger().triggerType())) {
                publishFailureAudit(ErrorCode.ENG_EMBED_005,
                    "嵌入反馈卡片超出当前会话授权范围 凭证编号=" + req.token() + " cardId=" + req.cardId());
                throw new ApiException(ErrorCode.ENG_EMBED_005, "反馈卡片不属于当前嵌入会话");
            }
            var feedback = recommendations.feedback(
                req.cardId(),
                recommendationFeedback(req, entity, actionType));
            auditRecorder.record(AuditAction.FEEDBACK, "embed_launch_token", req.token(),
                String.format("医生提交嵌入反馈 cardId=%s actionType=%s patientId=%s",
                    req.cardId(), actionType.name(), entity.patientId()));
            return new EmbedFeedbackResponse(
                req.token(),
                req.cardId(),
                actionType.name(),
                feedback.cardStatus().name(),
                EmbedConnectionStatus.NOT_CONNECTED,
                false,
                HOST_CALLBACK_NOT_CONFIGURED,
                feedback.traceId());
        });
    }

    /**
     * 为当前服务机构添加允许嵌入的 Origin 来源域名。
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

        auditRecorder.record(AuditAction.CREATE, "embed_origin_whitelist", req.origin(),
            "添加 Origin 来源域名允许清单 origin=" + req.origin());
    }

    /**
     * 获取当前服务机构下配置的所有 Origin 来源域名允许清单。
     *
     * @return Origin 来源域名允许清单
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

    private String requireCurrentUser() {
        return RequestContext.currentUserId()
            .filter(this::hasText)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EMBED_005, "签发嵌入凭证缺少认证用户"));
    }

    private String requireAuthenticatedRole(String value) {
        RoleCode role = RoleCode.fromCode(value)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EMBED_005, "签发角色不是标准职责角色"));
        if (!AuthenticatedRoleGuard.has(role)) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005, "签发角色不属于当前认证用户 roleCode=" + value);
            throw new ApiException(ErrorCode.ENG_EMBED_005, "签发角色不属于当前认证用户");
        }
        return role.code();
    }

    private LaunchContract validateLaunchContract(EmbedLaunchRequest request, EmbedLaunchToken entity) {
        EmbedIntegrationMode tokenMode = parseIntegrationMode(entity.integrationMode());
        if (request.integrationMode() != tokenMode) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005,
                "嵌入方式不匹配 token=" + request.token() + " expected=" + tokenMode
                    + " actual=" + request.integrationMode());
            throw new ApiException(ErrorCode.ENG_EMBED_005, "嵌入方式与启动凭证不匹配");
        }
        String tokenTriggerPoint = requireSupportedCdsHook(entity.triggerPoint(), "启动凭证触发点");
        String tokenHook = hasText(entity.hook())
            ? requireSupportedCdsHook(entity.hook(), "启动凭证 CDS Hook")
            : tokenTriggerPoint;
        requireSameCdsHook(tokenTriggerPoint, tokenHook, "启动凭证");
        String requestHook = hasText(request.hook())
            ? requireSupportedCdsHook(request.hook(), "兑换请求 CDS Hook")
            : tokenHook;
        if (!requestHook.equals(tokenHook)) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005,
                "CDS Hook 不匹配 token=" + request.token() + " expected=" + tokenHook
                    + " actual=" + requestHook);
            throw new ApiException(ErrorCode.ENG_EMBED_005, "CDS Hook 与启动凭证不匹配");
        }
        if (hasText(request.hookInstance()) && hasText(entity.hookInstance())
                && !request.hookInstance().equals(entity.hookInstance())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005,
                "CDS Hook 实例不匹配 token=" + request.token());
            throw new ApiException(ErrorCode.ENG_EMBED_005, "CDS Hook 实例与启动凭证不匹配");
        }
        String hookInstance = hasText(entity.hookInstance()) ? entity.hookInstance() : request.hookInstance();
        return new LaunchContract(tokenTriggerPoint, tokenHook, hookInstance);
    }

    private String requireTokenOrigin(String tenantId, EmbedIntegrationMode mode, String requestedOrigin) {
        if (mode == EmbedIntegrationMode.API && !hasText(requestedOrigin)) {
            return null;
        }
        if (!hasText(requestedOrigin)) {
            publishFailureAudit(ErrorCode.ENG_EMBED_002, "签发嵌入凭证缺少父系统 Origin");
            throw new ApiException(ErrorCode.ENG_EMBED_002, "父系统 Origin 不能为空");
        }
        String origin = requestedOrigin.trim();
        boolean allowed = originRepo.findByTenantIdAndOrigin(tenantId, origin).isPresent();
        if (!allowed) {
            publishFailureAudit(ErrorCode.ENG_EMBED_002, "非法的 Origin 域名=" + origin);
            throw new ApiException(ErrorCode.ENG_EMBED_002, "非法的 Origin 域名: " + origin);
        }
        return origin;
    }

    private String requirePersistedOrigin(EmbedLaunchToken entity) {
        EmbedIntegrationMode mode = parseIntegrationMode(entity.integrationMode());
        if (mode == EmbedIntegrationMode.API) {
            return entity.parentOrigin();
        }
        if (!hasText(entity.parentOrigin())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_002, "启动凭证未绑定父系统 Origin 凭证编号=" + entity.token());
            throw new ApiException(ErrorCode.ENG_EMBED_002, "启动凭证未绑定父系统 Origin");
        }
        return entity.parentOrigin();
    }

    private EmbedLaunchToken requireActiveSession(String token) {
        EmbedLaunchToken entity = tokenRepo.findByToken(token)
            .orElseThrow(() -> {
                publishFailureAudit(ErrorCode.ENG_EMBED_004, "嵌入会话凭证不存在 凭证编号=" + token);
                return new ApiException(ErrorCode.ENG_EMBED_004, "嵌入会话凭证不存在");
            });
        if (!EmbedLaunchTokenStatus.USED.name().equalsIgnoreCase(entity.status())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005, "嵌入会话凭证未完成使用 凭证编号=" + token);
            throw new ApiException(ErrorCode.ENG_EMBED_005, "嵌入会话凭证未完成使用");
        }
        if (!Instant.now().isBefore(entity.expiredAt())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_001, "嵌入会话已过期 token=" + token);
            throw new ApiException(ErrorCode.ENG_EMBED_001, "嵌入会话已过期");
        }
        requirePersistedOrigin(entity);
        return entity;
    }

    private RecommendationFeedbackRequest recommendationFeedback(
            EmbedFeedbackRequest req,
            EmbedLaunchToken entity,
            EmbedFeedbackActionType actionType) {
        RecommendationFeedbackType type = switch (actionType) {
            case ADOPT -> RecommendationFeedbackType.ACCEPT;
            case REJECT -> RecommendationFeedbackType.REJECT;
            case LATER -> RecommendationFeedbackType.DEFER;
            case IGNORE, CLOSE -> RecommendationFeedbackType.DISMISS;
        };
        String reasonCode = switch (actionType) {
            case ADOPT -> "EMBED_ADOPT";
            case REJECT -> "EMBED_REJECT";
            case LATER -> null;
            case IGNORE -> "EMBED_IGNORE";
            case CLOSE -> "EMBED_CLOSE";
        };
        String reasonText = hasText(req.reason()) ? req.reason().trim() : switch (actionType) {
            case ADOPT -> "医师在嵌入工作站确认采纳";
            case REJECT -> "医师在嵌入工作站确认不采纳";
            case LATER -> null;
            case IGNORE -> "医师在嵌入工作站忽略本次建议";
            case CLOSE -> "医师在嵌入工作站关闭本次建议";
        };
        return new RecommendationFeedbackRequest(
            type,
            reasonCode,
            reasonText,
            entity.roleCode(),
            "embed:" + entity.token() + ":" + req.cardId() + ":" + actionType.name());
    }

    private <T> T callInTokenContext(EmbedLaunchToken entity, Callable<T> action) {
        try {
            return RequestContext.callWith(
                new RequestContext.Snapshot(
                    entity.traceId(), OrgScope.tenant(entity.tenantId()), entity.userId()),
                action);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "嵌入会话执行失败", exception);
        }
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private ApiException classifyFailedAtomicConsume(String token, String tenantId) {
        Optional<EmbedLaunchToken> current = tokenRepo.findByToken(token)
            .filter(t -> tenantId.equals(t.tenantId()));
        if (current.isPresent() && EmbedLaunchTokenStatus.USED.name().equalsIgnoreCase(current.get().status())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_003, "启动凭证已被并发使用 凭证编号=" + token);
            return new ApiException(ErrorCode.ENG_EMBED_003, "启动凭证已被使用");
        }
        if (current.isPresent() && Instant.now().isAfter(current.get().expiredAt())) {
            publishFailureAudit(ErrorCode.ENG_EMBED_001, "启动凭证并发使用时已过期 凭证编号=" + token);
            return new ApiException(ErrorCode.ENG_EMBED_001, "启动凭证已过期");
        }
        publishFailureAudit(ErrorCode.ENG_EMBED_005, "启动凭证原子使用失败 凭证编号=" + token);
        return new ApiException(ErrorCode.ENG_EMBED_005, "启动凭证无法完成原子使用");
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

    private String requireSupportedCdsHook(String value, String label) {
        if (!hasText(value)) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005, label + "缺失");
            throw new ApiException(ErrorCode.ENG_EMBED_005, label + "缺失");
        }
        try {
            return CdsHookContract.requireSupportedHook(value).wireValue();
        } catch (ApiException ex) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005, label + "不在 CDS Hooks 6 触发点内 value=" + value);
            throw new ApiException(ErrorCode.ENG_EMBED_005, label + "不在 CDS Hooks 6 触发点内");
        }
    }

    private void requireSameCdsHook(String triggerPoint, String hook, String label) {
        if (!triggerPoint.equals(hook)) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005,
                label + " triggerPoint 与 CDS Hook 不一致 triggerPoint=" + triggerPoint + " hook=" + hook);
            throw new ApiException(ErrorCode.ENG_EMBED_005, label + " triggerPoint 与 CDS Hook 不一致");
        }
    }

    private EmbedFeedbackActionType requireSupportedFeedbackAction(String value) {
        try {
            return EmbedFeedbackActionType.fromWireValue(value);
        } catch (IllegalArgumentException ex) {
            publishFailureAudit(ErrorCode.ENG_EMBED_005, "嵌入反馈动作不受支持 actionType=" + value);
            throw new ApiException(ErrorCode.ENG_EMBED_005, "嵌入反馈动作不受支持");
        }
    }

    private void publishFailureAudit(ErrorCode code, String summary) {
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.EXECUTE, "embed_launch_token", null, code.code(), summary));
    }
}
