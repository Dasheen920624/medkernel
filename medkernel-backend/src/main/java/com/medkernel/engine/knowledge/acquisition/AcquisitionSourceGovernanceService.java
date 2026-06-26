package com.medkernel.engine.knowledge.acquisition;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.engine.knowledge.production.generation.GenerationItem;

/**
 * AIK-STD-14 公域来源治理：运营员登记停用配置，安全校验通过后显式启用。
 *
 * <p>配置更新一律停用来源，避免域名、许可或调度参数被静默替换；启用不要求第二操作人或 MFA，
 * 但仍强制校验 HTTPS、公开域名、许可、robots 策略和生成计划，并保留完整审计记录。
 */
@Service
public class AcquisitionSourceGovernanceService {

    private static final Pattern SOURCE_CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{1,127}");
    private static final Pattern HOST_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private final KnowledgeAcquisitionSourceRepository repository;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public AcquisitionSourceGovernanceService(KnowledgeAcquisitionSourceRepository repository,
                                              AuditRecorder auditRecorder,
                                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    /** 保存停用配置；任何配置变化都要求操作者重新显式启用。 */
    @Transactional
    public KnowledgeAcquisitionSource saveDraft(String sourceCode, AcquisitionSourceDraftRequest request) {
        String tenantId = requireCurrentTenant();
        String actor = requireCurrentActor();
        String code = normalizeSourceCode(sourceCode);
        ValidatedDraft draft = validateDraft(request);
        Instant now = Instant.now();
        Optional<KnowledgeAcquisitionSource> existing = repository.findByTenantIdAndSourceCode(tenantId, code);
        KnowledgeAcquisitionSource saved = repository.save(new KnowledgeAcquisitionSource(
            existing.map(KnowledgeAcquisitionSource::id).orElse(null),
            tenantId,
            code,
            draft.domain(),
            draft.baseUrl(),
            request.sourceType(),
            request.authorityLevel(),
            requireText(request.authorityBasis(), "权威依据", 512),
            requireText(request.title(), "来源标题", 512),
            requireText(request.publisher(), "发布机构", 256),
            requireText(request.license(), "许可依据", 512),
            requireNonNull(request.licensePolicy(), "许可裁决"),
            requireNonNull(request.robotsPolicy(), "robots 策略"),
            "N",
            request.scheduleRequested() ? "Y" : "N",
            request.scheduleRequested() ? request.scheduleIntervalMinutes() : null,
            null,
            null,
            request.scheduleRequested() ? request.defaultFormat() : null,
            serializeGenerationPlan(request.generationPlan()),
            existing.map(KnowledgeAcquisitionSource::createdAt).orElse(now),
            existing.map(KnowledgeAcquisitionSource::createdBy).orElse(actor),
            now,
            actor,
            existing.map(KnowledgeAcquisitionSource::version).orElse(null)));
        if (existing.isPresent()) {
            auditRecorder.record(AuditAction.UPDATE, "mk_knowledge_acquisition_source", code,
                "更新公域来源停用配置 " + code);
        } else {
            auditRecorder.record(AuditAction.CREATE, "mk_knowledge_acquisition_source", code,
                "登记公域来源停用配置 " + code);
        }
        return saved;
    }

    /** 安全校验通过后由当前运营员显式启用来源。 */
    @Transactional
    public KnowledgeAcquisitionSource enable(String sourceCode) {
        String tenantId = requireCurrentTenant();
        String actor = requireCurrentActor();
        String code = normalizeSourceCode(sourceCode);
        KnowledgeAcquisitionSource current = find(tenantId, code);
        if ("Y".equalsIgnoreCase(current.enabledFlag())) {
            return current;
        }
        validateEnableable(current);
        Instant now = Instant.now();
        KnowledgeAcquisitionSource saved = repository.save(copyWithStatus(
            current,
            "Y",
            current.scheduleEnabledFlag(),
            "Y".equals(current.scheduleEnabledFlag()) ? now : null,
            current.lastCheckAt(),
            actor,
            now));
        auditRecorder.record(AuditAction.UPDATE, "mk_knowledge_acquisition_source", code,
            "启用公域来源 " + code);
        return saved;
    }

    /** 停用来源和自动调度。 */
    @Transactional
    public KnowledgeAcquisitionSource disable(String sourceCode) {
        String tenantId = requireCurrentTenant();
        String actor = requireCurrentActor();
        String code = normalizeSourceCode(sourceCode);
        KnowledgeAcquisitionSource current = find(tenantId, code);
        Instant now = Instant.now();
        KnowledgeAcquisitionSource saved = repository.save(copyWithStatus(
            current,
            "N",
            "N",
            null,
            current.lastCheckAt(),
            actor,
            now));
        auditRecorder.record(AuditAction.UPDATE, "mk_knowledge_acquisition_source", code,
            "停用公域来源 " + code);
        return saved;
    }

    private ValidatedDraft validateDraft(AcquisitionSourceDraftRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "公域来源草稿不能为空");
        }
        String domain = normalizeDomain(request.domain());
        URI baseUri = parseBaseUri(request.baseUrl());
        String baseHost = normalizeDomain(baseUri.getHost());
        if (!baseHost.equals(domain) && !baseHost.endsWith("." + domain)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "基础 URL 域名不在声明的来源域名内");
        }
        requireNonNull(request.sourceType(), "来源类型");
        requireNonNull(request.authorityLevel(), "权威等级");
        requireNonNull(request.licensePolicy(), "许可裁决");
        requireNonNull(request.robotsPolicy(), "robots 策略");
        if (request.scheduleRequested()) {
            if (request.scheduleIntervalMinutes() == null || request.scheduleIntervalMinutes() <= 0) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "启用调度须设置正数分钟间隔");
            }
            if (request.defaultFormat() == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "启用调度须设置默认文档格式");
            }
            if (request.licensePolicy() != AcquisitionLicensePolicy.PERMITTED
                || request.robotsPolicy() != AcquisitionRobotsPolicy.ALLOW_FETCH) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                    "自动调度仅允许许可已确认且 robots 明确允许抓取的来源");
            }
        }
        validateGenerationPlan(request.generationPlan());
        return new ValidatedDraft(domain, canonicalBaseUri(baseUri));
    }

    private void validateEnableable(KnowledgeAcquisitionSource source) {
        validateDraft(new AcquisitionSourceDraftRequest(
            source.domain(), source.baseUrl(), source.sourceType(), source.authorityLevel(),
            source.authorityBasis(), source.title(), source.publisher(), source.license(),
            source.licensePolicy(), source.robotsPolicy(), "Y".equals(source.scheduleEnabledFlag()),
            source.scheduleIntervalMinutes(), source.defaultFormat(), null));
        if (source.licensePolicy() != AcquisitionLicensePolicy.PERMITTED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "来源许可未确认允许，禁止启用");
        }
        if (source.robotsPolicy() == null || !source.robotsPolicy().allowsFetch()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "来源 robots 策略不允许抓取，禁止启用");
        }
        if ("Y".equals(source.scheduleEnabledFlag()) && source.generationPlanJson() != null) {
            validateGenerationPlan(deserializeGenerationPlan(source.generationPlanJson()));
        }
    }

    private void validateGenerationPlan(AcquisitionCandidateGenerationRequest plan) {
        if (plan == null) {
            return;
        }
        if (plan.targetPipeline() == null || plan.domain() == null
            || plan.items() == null || plan.items().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                "候选生成计划须声明目标管道、领域和至少一个生成项");
        }
        for (GenerationItem item : plan.items()) {
            if (item == null || item.assetType() == null || item.target() == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                    "候选生成计划中的每个生成项须声明资产类型和物化目标");
            }
            try {
                item.target().validate();
            } catch (IllegalArgumentException exception) {
                throw new ApiException(ErrorCode.BAD_REQUEST, exception.getMessage());
            }
        }
    }

    private KnowledgeAcquisitionSource find(String tenantId, String code) {
        return repository.findByTenantIdAndSourceCode(tenantId, code)
            .orElseThrow(() -> ApiException.notFound("公域来源 " + code));
    }

    private KnowledgeAcquisitionSource copyWithStatus(KnowledgeAcquisitionSource source,
                                                       String enabledFlag,
                                                       String scheduleEnabledFlag,
                                                       Instant nextCheckAt,
                                                       Instant lastCheckAt,
                                                       String updatedBy,
                                                       Instant updatedAt) {
        return new KnowledgeAcquisitionSource(
            source.id(), source.tenantId(), source.sourceCode(), source.domain(), source.baseUrl(),
            source.sourceType(), source.authorityLevel(), source.authorityBasis(), source.title(),
            source.publisher(), source.license(), source.licensePolicy(), source.robotsPolicy(),
            enabledFlag, scheduleEnabledFlag, source.scheduleIntervalMinutes(),
            nextCheckAt, lastCheckAt, source.defaultFormat(), source.generationPlanJson(),
            source.createdAt(), source.createdBy(), updatedAt, updatedBy, source.version());
    }

    private URI parseBaseUri(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "基础 URL 不能为空");
        }
        if (raw.trim().length() > 512) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "基础 URL 长度不能超过 512 个字符");
        }
        try {
            URI uri = new URI(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "基础 URL 必须是包含域名的 HTTPS 地址");
            }
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "基础 URL 禁止携带用户信息、查询串或片段");
            }
            if (uri.getPort() != -1 && uri.getPort() != 443) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "公域来源仅允许 HTTPS 标准端口 443");
            }
            normalizeDomain(uri.getHost());
            return uri.normalize();
        } catch (URISyntaxException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "基础 URL 不是合法 URI");
        }
    }

    private String canonicalBaseUri(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        try {
            return new URI("https", null, normalizeDomain(uri.getHost()), uri.getPort(), path,
                null, null).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "基础 URL 无法规范化");
        }
    }

    private String normalizeDomain(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "来源域名不能为空");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        final String ascii;
        try {
            ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "来源域名不合法");
        }
        String[] labels = ascii.split("\\.", -1);
        if (labels.length < 2 || ascii.length() > 253 || ascii.matches("\\d+(?:\\.\\d+){3}")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "来源域名必须是公开完整域名");
        }
        for (String label : labels) {
            if (!HOST_LABEL.matcher(label).matches()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "来源域名不合法");
            }
        }
        if (ascii.endsWith(".local") || ascii.endsWith(".internal") || ascii.endsWith(".localhost")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "来源域名不得指向本地或内部网络");
        }
        return ascii;
    }

    private String normalizeSourceCode(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_CODE.matcher(code).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "来源编码须为 2-128 位大写字母、数字、点、下划线或连字符");
        }
        return code;
    }

    private String serializeGenerationPlan(AcquisitionCandidateGenerationRequest plan) {
        if (plan == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选生成计划无法序列化", exception);
        }
    }

    private AcquisitionCandidateGenerationRequest deserializeGenerationPlan(String json) {
        try {
            return objectMapper.readValue(json, AcquisitionCandidateGenerationRequest.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选生成计划不是合法结构", exception);
        }
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String requireCurrentActor() {
        return RequestContext.currentUserId()
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "缺少当前操作人"));
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static <T> T requireNonNull(T value, String label) {
        if (value == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        return value;
    }

    private record ValidatedDraft(String domain, String baseUrl) {
    }
}
