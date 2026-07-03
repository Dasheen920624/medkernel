package com.medkernel.engine.authoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.rule.RuleCreateResponse;
import com.medkernel.engine.rule.RuleGovernanceResponse;
import com.medkernel.engine.rule.RuleImpactResponse;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;

/**
 * 创作批量任务应用服务。
 *
 * <p>任务在当前请求内真实执行并逐项落库，避免提交到尚无执行器的伪异步队列。
 * 后续引入工作进程时可直接复用本服务的逐项执行语义。
 */
@Service
public class AuthoringBatchJobService {

    private static final String ENTITY = "mk_engine_authoring_batch_job";

    private final ObjectMapper json;
    private final AuthoringBatchJobRepository jobs;
    private final AuthoringBatchItemRepository items;
    private final AuthoringBatchRulePort rules;
    private final AuthoringFeatureGate featureGate;
    private final AuditRecorder auditRecorder;

    public AuthoringBatchJobService(
            ObjectMapper json,
            AuthoringBatchJobRepository jobs,
            AuthoringBatchItemRepository items,
            AuthoringBatchRulePort rules,
            AuthoringFeatureGate featureGate,
            AuditRecorder auditRecorder) {
        this.json = json;
        this.jobs = jobs;
        this.items = items;
        this.rules = rules;
        this.featureGate = featureGate;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 使用一条基准规则与参数表生成独立草稿。
     */
    public AuthoringBatchJobResponse generateRules(AuthoringBatchRuleGenerateRequest request) {
        requireFeature();
        requireDistinctIds(request.rows().stream().map(AuthoringBatchRuleGenerateRow::rowId).toList());
        AuthoringBatchRuleTemplate template = rules.loadTemplate(request.templateRuleId());
        AuthoringBatchJob job = createJob(
            AuthoringBatchJobType.RULE_GENERATE, request.rows().size(), request);
        List<AuthoringBatchItem> results = new ArrayList<>();
        for (AuthoringBatchRuleGenerateRow row : request.rows()) {
            try {
                RuleCreateResponse created = rules.createDraft(new AuthoringBatchRuleDraftCommand(
                    row.ruleCode(),
                    row.name(),
                    template,
                    row.triggers(),
                    row.applicableOrgUnitId(),
                    row.changeSummary(),
                    row.parameterBindings()));
                results.add(saveSuccess(
                    job,
                    row.rowId(),
                    "RULE",
                    created.ruleId(),
                    Map.of(
                        "ruleId", created.ruleId(),
                        "versionId", created.versionId(),
                        "status", created.status()),
                    null,
                    "规则草稿已生成"));
            } catch (RuntimeException exception) {
                results.add(saveFailure(job, row.rowId(), "RULE", row.ruleCode(), exception));
            }
        }
        return finish(job, results);
    }

    /**
     * 聚合分析多条规则的发布影响，不产生发布副作用。
     */
    public AuthoringBatchRuleImpactResponse analyzeRuleImpacts(AuthoringBatchRuleImpactRequest request) {
        requireFeature();
        requireDistinctIds(request.ruleIds());
        List<AuthoringBatchRuleImpactItem> results = request.ruleIds().stream()
            .map(rules::impact)
            .map(this::impactItem)
            .toList();
        int high = (int) results.stream().filter(item -> item.riskLevel() == RuleRiskLevel.HIGH).count();
        int critical = (int) results.stream().filter(item -> item.riskLevel() == RuleRiskLevel.CRITICAL).count();
        return new AuthoringBatchRuleImpactResponse(
            results.size(), high, critical, results, RequestContext.currentTraceId());
    }

    /**
     * 批量推进规则治理状态；高危与极高危规则必须逐条显式确认。
     */
    public AuthoringBatchJobResponse publishRules(AuthoringBatchRulePublishRequest request) {
        requireFeature();
        requireDistinctIds(request.items().stream().map(AuthoringBatchRulePublishItem::itemId).toList());
        Map<String, RuleImpactResponse> impacts = new HashMap<>();
        for (AuthoringBatchRulePublishItem item : request.items()) {
            RuleImpactResponse impact = rules.impact(item.ruleId());
            impacts.put(item.ruleId(), impact);
            if (isHighRisk(impact.riskLevel()) && !item.highRiskConfirmed()) {
                throw new ApiException(
                    ErrorCode.ENG_RULE_004,
                    "高危规则必须逐条确认后才能批量推进: " + item.ruleId());
            }
        }

        AuthoringBatchJob job = createJob(
            AuthoringBatchJobType.RULE_PUBLISH, request.items().size(), request);
        List<AuthoringBatchItem> results = new ArrayList<>();
        for (AuthoringBatchRulePublishItem item : request.items()) {
            try {
                RuleImpactResponse impact = impacts.get(item.ruleId());
                if (!impact.impactDigest().equals(item.impactDigest())) {
                    throw new ApiException(ErrorCode.ENG_RULE_004, "规则影响摘要已变化，请重新分析");
                }
                RuleGovernanceResponse transitioned = rules.transition(
                    item.ruleId(),
                    new AuthoringBatchRuleTransitionCommand(
                        request.targetState(), item.impactDigest(), request.reason()));
                results.add(saveSuccess(
                    job,
                    item.itemId(),
                    "RULE",
                    item.ruleId(),
                    Map.of(
                        "state", transitioned.state(),
                        "versionId", transitioned.versionId(),
                        "impactDigest", item.impactDigest()),
                    transitioned.versionId(),
                    "规则治理状态已推进"));
            } catch (RuntimeException exception) {
                results.add(saveFailure(job, item.itemId(), "RULE", item.ruleId(), exception));
            }
        }
        return finish(job, results);
    }

    /**
     * 查询单个任务及逐项结果。
     */
    public AuthoringBatchJobResponse get(String jobId) {
        requireFeature();
        String tenantId = requireTenant();
        AuthoringBatchJob job = jobs.findByTenantIdAndJobId(tenantId, jobId)
            .orElseThrow(() -> ApiException.notFound("批量任务"));
        return response(job, items.findByTenantIdAndJobIdOrderByIdAsc(tenantId, jobId));
    }

    /**
     * 分页查询当前租户批量任务台账。
     */
    public PageResponse<AuthoringBatchJobResponse> listRecent(PageRequest request) {
        requireFeature();
        String tenantId = requireTenant();
        PageRequest page = request == null ? PageRequest.defaults() : request;
        long total = jobs.countByTenantId(tenantId);
        if (total == 0) {
            return PageResponse.empty(page);
        }
        List<AuthoringBatchJobResponse> rows = jobs.pageByTenantId(tenantId, page.offset(), page.safeSize()).stream()
            .map(job -> response(job, List.of()))
            .toList();
        return PageResponse.of(rows, page, total);
    }

    private AuthoringBatchJob createJob(AuthoringBatchJobType type, int totalCount, Object request) {
        String tenantId = requireTenant();
        Instant now = Instant.now();
        String actor = actor();
        AuthoringBatchJob saved = jobs.save(new AuthoringBatchJob(
            null,
            "abj-" + UUID.randomUUID(),
            tenantId,
            type,
            AuthoringBatchJobStatus.RUNNING,
            totalCount,
            0,
            0,
            writeJson(request, "批量任务请求摘要无法序列化"),
            null,
            now,
            actor,
            now,
            actor,
            RequestContext.currentTraceId()));
        auditRecorder.record(AuditAction.CREATE, ENTITY, saved.jobId(), "创建创作批量任务 " + type);
        return saved;
    }

    private AuthoringBatchItem saveSuccess(
            AuthoringBatchJob job,
            String itemId,
            String targetType,
            String targetId,
            Object result,
            String rollbackRef,
            String message) {
        return saveItem(
            job, itemId, AuthoringBatchItemStatus.SUCCEEDED, targetType, targetId,
            writeJson(result, "批量任务结果无法序列化"), rollbackRef, null, message);
    }

    private AuthoringBatchItem saveFailure(
            AuthoringBatchJob job,
            String itemId,
            String targetType,
            String targetId,
            RuntimeException exception) {
        String code = exception instanceof ApiException api
            ? api.errorCode().code()
            : ErrorCode.INTERNAL_ERROR.code();
        String message = exception.getMessage() == null ? "批量任务逐项执行失败" : exception.getMessage();
        return saveItem(
            job, itemId, AuthoringBatchItemStatus.FAILED, targetType, targetId,
            null, null, code, message);
    }

    private AuthoringBatchItem saveItem(
            AuthoringBatchJob job,
            String itemId,
            AuthoringBatchItemStatus status,
            String targetType,
            String targetId,
            String resultJson,
            String rollbackRef,
            String errorCode,
            String message) {
        return items.save(new AuthoringBatchItem(
            null,
            job.jobId(),
            job.tenantId(),
            itemId,
            status,
            targetType,
            targetId,
            resultJson,
            rollbackRef,
            errorCode,
            message,
            Instant.now(),
            actor(),
            job.traceId()));
    }

    private AuthoringBatchJobResponse finish(
            AuthoringBatchJob job,
            List<AuthoringBatchItem> results) {
        int successes = count(results, AuthoringBatchItemStatus.SUCCEEDED);
        int failures = count(results, AuthoringBatchItemStatus.FAILED);
        AuthoringBatchJobStatus status = finalStatus(results.size(), successes);
        Map<String, Object> summary = Map.of(
            "status", status,
            "totalCount", results.size(),
            "successCount", successes,
            "failureCount", failures);
        AuthoringBatchJob completed = jobs.save(job.completed(
            status,
            successes,
            failures,
            writeJson(summary, "批量任务汇总无法序列化"),
            Instant.now(),
            actor()));
        auditRecorder.record(
            completionAuditAction(completed.jobType()),
            ENTITY,
            completed.jobId(),
            "完成创作批量任务 " + completed.jobType() + "，状态 " + completed.status());
        return response(completed, results);
    }

    private AuditAction completionAuditAction(AuthoringBatchJobType type) {
        return switch (type) {
            case RULE_GENERATE -> AuditAction.CREATE;
            case RULE_PUBLISH -> AuditAction.PUBLISH;
        };
    }

    private AuthoringBatchJobStatus finalStatus(int total, int successes) {
        if (successes == total) {
            return AuthoringBatchJobStatus.SUCCEEDED;
        }
        if (successes > 0) {
            return AuthoringBatchJobStatus.PARTIAL_SUCCESS;
        }
        return AuthoringBatchJobStatus.FAILED;
    }

    private int count(List<AuthoringBatchItem> values, AuthoringBatchItemStatus status) {
        return (int) values.stream().filter(item -> item.status() == status).count();
    }

    private AuthoringBatchRuleImpactItem impactItem(RuleImpactResponse impact) {
        int affected = impact.affectedRules().size()
            + impact.affectedPathways().size()
            + impact.inPathPatients().size()
            + impact.integrationAdapters().size();
        return new AuthoringBatchRuleImpactItem(
            impact.ruleId(),
            impact.versionId(),
            impact.riskLevel(),
            impact.analysisStatus(),
            impact.impactDigest(),
            affected,
            impact.unavailableScopes());
    }

    private AuthoringBatchJobResponse response(
            AuthoringBatchJob job,
            List<AuthoringBatchItem> jobItems) {
        return new AuthoringBatchJobResponse(
            job.jobId(),
            job.jobType(),
            job.status(),
            job.totalCount(),
            job.successCount(),
            job.failureCount(),
            job.resultSummaryJson(),
            jobItems.stream().map(this::responseItem).toList(),
            job.traceId(),
            job.createdAt(),
            job.updatedAt());
    }

    private AuthoringBatchItemResponse responseItem(AuthoringBatchItem item) {
        return new AuthoringBatchItemResponse(
            item.itemId(),
            item.status(),
            item.targetType(),
            item.targetId(),
            item.resultJson(),
            item.rollbackRef(),
            item.errorCode(),
            item.message(),
            item.createdAt());
    }

    private void requireFeature() {
        if (!featureGate.enabled(AuthoringFeatureFlag.BATCH_AUTHORING)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "批量创作能力未启用");
        }
    }

    private void requireDistinctIds(List<String> ids) {
        Set<String> distinct = new HashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "批量任务逐项标识不能为空");
            }
            if (!distinct.add(id)) {
                throw new ApiException(ErrorCode.CONFLICT, "批量任务逐项标识重复: " + id);
            }
        }
    }

    private boolean isHighRisk(RuleRiskLevel riskLevel) {
        return riskLevel == RuleRiskLevel.HIGH || riskLevel == RuleRiskLevel.CRITICAL;
    }

    private String requireTenant() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String writeJson(Object value, String message) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, message, exception);
        }
    }
}
