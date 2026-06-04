package com.medkernel.engine.safety;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OPT-04 临床安全红线目录服务。
 *
 * <p>红线内容只来自数据库 / 知识配置。空库返回 NOT_CONFIGURED，不内置任何医学常量。
 */
@Service
public class ClinicalRedlineService {

    private final ClinicalRedlineRepository repository;
    private final ClinicalRedlineTrialRepository trialRepository;
    private final AuditEventPublisher auditPublisher;

    public ClinicalRedlineService(
            ClinicalRedlineRepository repository,
            ClinicalRedlineTrialRepository trialRepository,
            AuditEventPublisher auditPublisher) {
        this.repository = repository;
        this.trialRepository = trialRepository;
        this.auditPublisher = auditPublisher;
    }

    @Transactional(readOnly = true)
    public ClinicalRedlineCatalogResponse activeCatalog(ClinicalRedlineCategory category) {
        String tenantId = tenantId();
        List<ClinicalRedlineRule> rows = category == null
            ? repository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
                tenantId, ClinicalRedlineStatus.ACTIVE)
            : repository.findByTenantIdAndCategoryAndStatusOrderByRedlineKeyAscUpdatedAtDesc(
                tenantId, category, ClinicalRedlineStatus.ACTIVE);
        List<ClinicalRedlineResponse> redlines = rows.stream()
            .sorted(Comparator
                .comparing((ClinicalRedlineRule row) -> row.category().name())
                .thenComparing(ClinicalRedlineRule::redlineKey)
                .thenComparing(ClinicalRedlineRule::updatedAt, Comparator.reverseOrder()))
            .map(ClinicalRedlineRule::toResponse)
            .toList();
        ClinicalRedlineContentStatus contentStatus = redlines.isEmpty()
            ? ClinicalRedlineContentStatus.NOT_CONFIGURED
            : ClinicalRedlineContentStatus.CONFIGURED;
        return new ClinicalRedlineCatalogResponse(
            contentStatus,
            ClinicalRedlineCategory.requiredSafetyCategories(),
            redlines,
            RequestContext.currentTraceId());
    }

    @Transactional
    public ClinicalRedlineTrialResponse dryRun(ClinicalRedlineDryRunRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "静默试运行请求不能为空");
        }
        String tenantId = tenantId();
        ClinicalRedlineRule rule = repository.findByTenantIdAndRedlineId(
                tenantId, requireText(request.redlineId(), "红线 ID 不能为空"))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "红线不存在"));
        if (rule.status() != ClinicalRedlineStatus.DRAFT
                && rule.status() != ClinicalRedlineStatus.SILENT_RUNNING) {
            throw new ApiException(ErrorCode.CONFLICT, "只有草稿或静默试运行中的红线可以记录试运行证据");
        }
        validateHazardBundle(rule);
        validateTrialWindowAndCounts(request);
        long actualHours = Duration.between(request.observedFrom(), request.observedTo()).toHours();
        boolean gatePassed = actualHours >= rule.silentRunHours() && request.safetyIncidentCount() == 0;
        ClinicalRedlineTrialStatus status = gatePassed
            ? ClinicalRedlineTrialStatus.PASSED
            : ClinicalRedlineTrialStatus.FAILED;
        Instant now = Instant.now();
        String actor = actor();
        String traceId = traceId();
        ClinicalRedlineTrial saved = trialRepository.save(new ClinicalRedlineTrial(
            null,
            "crt-" + UUID.randomUUID(),
            tenantId,
            rule.redlineId(),
            rule.redlineKey(),
            rule.redlineVersion(),
            status,
            request.observedFrom(),
            request.observedTo(),
            rule.silentRunHours(),
            actualHours,
            request.evaluatedCaseCount(),
            request.matchedCaseCount(),
            request.falsePositiveCaseCount(),
            request.safetyIncidentCount(),
            gatePassed,
            requireText(request.evidenceReference(), "试运行证据引用不能为空"),
            trimToNull(request.operatorNote()),
            now,
            actor,
            traceId));
        repository.save(rule.withStatus(ClinicalRedlineStatus.SILENT_RUNNING, now, actor, traceId));
        auditPublisher.publish(AuditAction.EXECUTE, "mk_engine_clinical_redline_trial",
            saved.trialId(), "记录临床安全红线静默试运行证据");
        return saved.toResponse();
    }

    @Transactional
    public ClinicalRedlineResponse promote(ClinicalRedlinePromoteRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "红线上线请求不能为空");
        }
        String tenantId = tenantId();
        ClinicalRedlineRule rule = repository.findByTenantIdAndRedlineId(
                tenantId, requireText(request.redlineId(), "红线 ID 不能为空"))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "红线不存在"));
        if (rule.status() != ClinicalRedlineStatus.SILENT_RUNNING) {
            throw new ApiException(ErrorCode.CONFLICT, "只有静默试运行中的红线可以上线");
        }
        if (!rule.redlineVersion().equals(requireText(request.expectedRedlineVersion(), "预期红线版本不能为空"))) {
            throw new ApiException(ErrorCode.CONFLICT, "红线版本与上线请求不一致");
        }
        ClinicalRedlineTrial trial = trialRepository.findByTenantIdAndRedlineIdAndTrialId(
                tenantId,
                rule.redlineId(),
                requireText(request.trialId(), "试运行证据 ID 不能为空"))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "静默试运行证据不存在"));
        if (!trial.redlineVersion().equals(rule.redlineVersion())) {
            throw new ApiException(ErrorCode.CONFLICT, "静默试运行证据与当前红线版本不一致");
        }
        if (trial.status() != ClinicalRedlineTrialStatus.PASSED
                || !trial.gatePassed()
                || trial.actualSilentHours() < rule.silentRunHours()
                || trial.safetyIncidentCount() > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "静默试运行未达标，禁止上线");
        }
        if (rule.lowerTenantOverrideAllowed()) {
            throw new ApiException(ErrorCode.CONFLICT, "安全红线禁止下级关闭，lowerTenantOverrideAllowed 必须为 false");
        }
        requireText(request.promotionReason(), "红线上线原因不能为空");
        repository.findByTenantIdAndActiveScopeKeyAndStatus(
                tenantId, rule.activeScopeKey(), ClinicalRedlineStatus.ACTIVE)
            .filter(active -> !active.redlineId().equals(rule.redlineId()))
            .ifPresent(active -> {
                throw new ApiException(ErrorCode.CONFLICT, "同适用域已有生效红线，需先走安全撤回或版本切换流程");
            });
        Instant now = Instant.now();
        String actor = actor();
        String traceId = traceId();
        ClinicalRedlineRule activated = repository.save(rule.withStatus(
            ClinicalRedlineStatus.ACTIVE, now, actor, traceId));
        auditPublisher.publish(AuditAction.PUBLISH, "mk_engine_clinical_redline",
            activated.redlineId(), "临床安全红线静默试运行达标后上线");
        return activated.toResponse();
    }

    private void validateHazardBundle(ClinicalRedlineRule rule) {
        if (rule.hazardSeverity() == null
                || rule.reviewRequirement() == null
                || !hasText(rule.riskMatrixId())
                || !hasText(rule.riskMatrixVersion())
                || !hasText(rule.clinicalHazard())
                || !hasText(rule.conditionDsl())
                || !hasText(rule.evidenceSource())
                || !hasText(rule.evidenceReference())
                || !hasText(rule.releaseGate())) {
            throw new ApiException(ErrorCode.CONFLICT, "红线危害分析、证据来源和风险矩阵绑定不能为空");
        }
    }

    private void validateTrialWindowAndCounts(ClinicalRedlineDryRunRequest request) {
        if (request.observedFrom() == null || request.observedTo() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "静默试运行观察窗口不能为空");
        }
        if (!request.observedTo().isAfter(request.observedFrom())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "静默试运行观察结束时间必须晚于开始时间");
        }
        if (request.evaluatedCaseCount() < 0
                || request.matchedCaseCount() < 0
                || request.falsePositiveCaseCount() < 0
                || request.safetyIncidentCount() < 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "静默试运行统计计数不能为负数");
        }
        if (request.matchedCaseCount() > request.evaluatedCaseCount()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "红线命中数不能超过评估病例数");
        }
        if (request.falsePositiveCaseCount() > request.matchedCaseCount()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "误报数不能超过命中数");
        }
    }

    private String tenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
