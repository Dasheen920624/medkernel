package com.medkernel.engine.llm.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.llm.provider.ModelProviderRegistry;
import com.medkernel.engine.llm.provider.ProviderCompletion;
import com.medkernel.engine.llm.provider.ProviderRequest;
import com.medkernel.engine.security.AuthenticatedRoleGuard;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.config.HighRiskChangeGuard;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 医学回归评测服务（LLM-07 T16/T17/T18）。
 *
 * <p>运行评测（对候选 provider 跑基准集，无基准集不认证）、高风险换版专家复核签字、上线门禁查询。
 * provider 启用前须存在 {@code PASSED} 评测运行（{@link #isClearedForGoLive}），否则 {@code ENG-LLM-008} 阻断。
 */
@Service
public class ModelEvalService {

    private static final String SIGN_OFF_RESOURCE_TYPE = "model_eval_sign_off";

    private final MedicalRegressionCaseRepository caseRepo;
    private final ModelEvalRunRepository runRepo;
    private final MedicalRegressionEvaluator evaluator;
    private final ModelProviderRegistry registry;
    private final AuditRecorder auditRecorder;
    private final HighRiskChangeGuard highRiskChangeGuard;

    public ModelEvalService(MedicalRegressionCaseRepository caseRepo,
                            ModelEvalRunRepository runRepo,
                            MedicalRegressionEvaluator evaluator,
                            ModelProviderRegistry registry,
                            AuditRecorder auditRecorder,
                            HighRiskChangeGuard highRiskChangeGuard) {
        this.caseRepo = caseRepo;
        this.runRepo = runRepo;
        this.evaluator = evaluator;
        this.registry = registry;
        this.auditRecorder = auditRecorder;
        this.highRiskChangeGuard = highRiskChangeGuard;
    }

    @Transactional
    public ModelEvalRun runEvaluation(String providerCode, String modelVersion, String capabilityCode) {
        String tenantId = requireCurrentTenant();
        String normalizedProviderCode = requireText(providerCode, "provider_code");
        String normalizedModelVersion = requireText(modelVersion, "model_version");
        String normalizedCapabilityCode = requireText(capabilityCode, "capability_code");
        List<MedicalRegressionCase> cases =
            caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(tenantId, normalizedCapabilityCode, "Y");
        if (cases.isEmpty()) {
            // 无医学基准集不得自动认证（铁律 #1/#3），如实记 FAILED。
            return persist(tenantId, normalizedProviderCode, normalizedModelVersion, normalizedCapabilityCode, cases,
                new MedicalRegressionEvaluator.EvalVerdict(0, 0, 0, false, false, "FAILED"));
        }

        var resolved = registry.resolveByCode(tenantId, normalizedProviderCode);
        if (resolved.isEmpty()) {
            return persist(tenantId, normalizedProviderCode, normalizedModelVersion, normalizedCapabilityCode, cases,
                new MedicalRegressionEvaluator.EvalVerdict(cases.size(), 0, cases.size(), false, false, "FAILED"));
        }
        var provider = resolved.get();
        String configuredModelVersion = requireText(provider.config().modelVersion(), "provider.model_version");
        if (!normalizedModelVersion.equals(configuredModelVersion)) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "评测模型版本与 provider 当前配置不一致");
        }

        MedicalRegressionEvaluator.EvalVerdict verdict = evaluator.evaluate(cases,
            regCase -> requireEvaluatedModelVersion(
                provider.adapter().complete(provider.config(),
                    new ProviderRequest(regCase.capabilityCode(), regCase.caseInput(), 60_000)),
                normalizedModelVersion));
        return persist(
            tenantId,
            normalizedProviderCode,
            normalizedModelVersion,
            normalizedCapabilityCode,
            cases,
            verdict);
    }

    @Transactional
    public ModelEvalRun runQualityEvaluation(AiQualityEvalRunRequest request) {
        String tenantId = requireCurrentTenant();
        String capabilityCode = requireText(request.capabilityCode(), "capability_code");
        String providerCode = normalizeOptional(request.providerCode(), "offline-fixture");
        String modelVersion = requireText(request.modelVersion(), "model_version");
        String promptVersion = requireText(request.promptVersion(), "prompt_version");
        String toolVersion = requireText(request.toolVersion(), "tool_version");
        validateCaseOutputs(modelVersion, request.caseOutputs());
        List<MedicalRegressionCase> cases =
            caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(tenantId, capabilityCode, "Y");
        if (cases.isEmpty()) {
            return persistQuality(tenantId, providerCode, modelVersion, capabilityCode, promptVersion, toolVersion,
                new MedicalRegressionEvaluator.QualityEvalVerdict(
                    0, 0, 0, 0, 0.0, null, false, "FAILED", "[]"));
        }

        MedicalRegressionEvaluator.QualityEvalVerdict verdict =
            evaluator.evaluateQuality(cases, qualityRunner(tenantId, providerCode, request.caseOutputs()));
        return persistQuality(tenantId, providerCode, modelVersion, capabilityCode, promptVersion, toolVersion, verdict);
    }

    @Transactional
    public ModelEvalRun signOff(Long runId) {
        String tenantId = requireCurrentTenant();
        if (!AuthenticatedRoleGuard.has(RoleCode.QUALITY_GOVERNOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "仅质量治理员可执行专家复核签字");
        }
        ModelEvalRun run = runRepo.findById(runId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "评测运行不存在: " + runId));
        if (!tenantId.equals(run.tenantId())) {
            throw new ApiException(ErrorCode.TENANT_FORBIDDEN, "无权访问该评测运行");
        }
        if (!"PENDING_REVIEW".equals(run.status())) {
            throw new ApiException(ErrorCode.ENG_LLM_008,
                "仅待复核（PENDING_REVIEW）评测可签字放行，当前状态: " + run.status());
        }
        String actor = RequestContext.currentUserId().orElse("system");
        if (actor.equals(run.createdBy())) {
            throw ApiException.conflict("专家签字人与评测执行人必须分离");
        }
        highRiskChangeGuard.assertHighRiskAllowed(SIGN_OFF_RESOURCE_TYPE, String.valueOf(runId));
        Instant now = Instant.now();
        int updated = runRepo.signOffPending(runId, tenantId, actor, now);
        if (updated != 1) {
            throw ApiException.conflict("评测复核状态已被其他请求处理，请刷新后重试");
        }
        ModelEvalRun signed = new ModelEvalRun(
            run.id(), run.tenantId(), run.providerCode(), run.modelVersion(),
            run.capabilityCode(), run.promptVersion(), run.toolVersion(),
            run.totalCases(), run.passedCases(), run.failedCases(),
            run.qualityScore(), run.terminologyScore(),
            run.fakeCitationDetected(), run.redLineBreach(), run.hallucinationDetected(),
            "PASSED", run.caseSummaryJson(),
            actor, now, run.createdAt(), run.createdBy(), now, actor);
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_eval_run", String.valueOf(runId),
            "专家复核签字放行评测 " + run.providerCode() + "/" + run.modelVersion());
        return signed;
    }

    @Transactional(readOnly = true)
    public boolean isClearedForGoLive(String tenantId, String providerCode, String modelVersion) {
        Optional<ModelEvalRun> run = runRepo
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndStatusOrderByIdDesc(
                tenantId, providerCode, modelVersion, "PASSED");
        if (run.isEmpty() || run.get().capabilityCode() == null || run.get().capabilityCode().isBlank()) {
            return false;
        }
        List<MedicalRegressionCase> currentCases = caseRepo
            .findByTenantIdAndCapabilityCodeAndEnabledFlag(
                tenantId, run.get().capabilityCode(), "Y");
        if (currentCases.isEmpty()
            || run.get().totalCases() != currentCases.size()
            || run.get().passedCases() != currentCases.size()
            || run.get().failedCases() != 0
            || !RegressionBaselineEvidence.matches(run.get().caseSummaryJson(), currentCases)) {
            return false;
        }
        boolean requiresExpertSignOff = currentCases.stream().anyMatch(MedicalRegressionCase::redLine);
        return !requiresExpertSignOff
            || (run.get().reviewer() != null
                && !run.get().reviewer().isBlank()
                && run.get().signedAt() != null);
    }

    @Transactional(readOnly = true)
    public AiQualityTrendResponse qualityTrend(String capabilityCode, String modelVersion) {
        String tenantId = requireCurrentTenant();
        String normalizedCapability = requireText(capabilityCode, "capability_code");
        String normalizedModelVersion = requireText(modelVersion, "model_version");
        List<AiQualityTrendPoint> points = runRepo
            .findTop20ByTenantIdAndCapabilityCodeAndModelVersionOrderByCreatedAtDesc(
                tenantId, normalizedCapability, normalizedModelVersion)
            .stream()
            .map(this::toTrendPoint)
            .toList();
        return new AiQualityTrendResponse(normalizedCapability, normalizedModelVersion, points);
    }

    private ModelEvalRun persist(String tenantId, String providerCode, String modelVersion,
                                 String capabilityCode,
                                 List<MedicalRegressionCase> cases,
                                 MedicalRegressionEvaluator.EvalVerdict verdict) {
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelEvalRun saved = runRepo.save(new ModelEvalRun(
            null, tenantId, providerCode, modelVersion, capabilityCode, null, null,
            verdict.total(), verdict.passed(), verdict.failed(),
            null, null, verdict.fakeCitationDetected() ? "Y" : "N", verdict.redLineBreach() ? "Y" : "N",
            "N", verdict.status(), RegressionBaselineEvidence.toJson(cases),
            null, null, now, actor, now, actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_llm_eval_run", providerCode + "/" + modelVersion,
            "运行医学回归评测 " + providerCode + "/" + modelVersion + " -> " + verdict.status());
        return saved;
    }

    private ProviderCompletion requireEvaluatedModelVersion(ProviderCompletion completion,
                                                              String expectedModelVersion) {
        if (completion == null || !expectedModelVersion.equals(completion.modelVersion())) {
            return new ProviderCompletion("", null, null, "[]");
        }
        return completion;
    }

    private ModelEvalRun persistQuality(
            String tenantId,
            String providerCode,
            String modelVersion,
            String capabilityCode,
            String promptVersion,
            String toolVersion,
            MedicalRegressionEvaluator.QualityEvalVerdict verdict) {
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelEvalRun saved = runRepo.save(new ModelEvalRun(
            null, tenantId, providerCode, modelVersion, capabilityCode, promptVersion, toolVersion,
            verdict.total(), verdict.passed(), verdict.failed(),
            verdict.qualityScore(), verdict.terminologyScore(), "N", "N",
            verdict.hallucinationDetected() ? "Y" : "N", verdict.status(), verdict.caseSummaryJson(),
            null, null, now, actor, now, actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_llm_eval_run", capabilityCode + "/" + modelVersion,
            "运行 AI 质量评测 " + capabilityCode + "/" + modelVersion + " -> " + verdict.status());
        return saved;
    }

    private Function<MedicalRegressionCase, ProviderCompletion> qualityRunner(
            String tenantId,
            String providerCode,
            List<AiQualityEvalCaseOutput> outputs) {
        Map<Long, AiQualityEvalCaseOutput> outputByCase = outputs.stream()
            .filter(output -> output.caseId() != null)
            .collect(Collectors.toMap(AiQualityEvalCaseOutput::caseId, output -> output, (left, right) -> left));
        if (!outputByCase.isEmpty()) {
            return regCase -> toCompletion(outputByCase.get(regCase.id()));
        }

        var resolved = registry.resolveByCode(tenantId, providerCode);
        if (resolved.isEmpty()) {
            return ignored -> new ProviderCompletion("", null, null, "[]");
        }
        var provider = resolved.get();
        return regCase -> provider.adapter().complete(provider.config(),
            new ProviderRequest(regCase.capabilityCode(), regCase.caseInput(), 60_000));
    }

    private ProviderCompletion toCompletion(AiQualityEvalCaseOutput output) {
        if (output == null) {
            return new ProviderCompletion("", null, null, "[]");
        }
        return new ProviderCompletion(
            output.content(),
            output.modelVersion(),
            output.confidence(),
            output.sourceCitations());
    }

    private AiQualityTrendPoint toTrendPoint(ModelEvalRun run) {
        return new AiQualityTrendPoint(
            run.id(),
            run.createdAt(),
            run.providerCode(),
            run.modelVersion(),
            run.promptVersion(),
            run.toolVersion(),
            run.status(),
            run.qualityScore(),
            run.terminologyScore(),
            "Y".equalsIgnoreCase(run.hallucinationDetected()));
    }

    private void validateCaseOutputs(String modelVersion, List<AiQualityEvalCaseOutput> outputs) {
        for (AiQualityEvalCaseOutput output : outputs) {
            if (output.caseId() == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "caseOutputs.case_id 不能为空");
            }
            String outputModelVersion = normalizeOptional(output.modelVersion(), modelVersion);
            if (!modelVersion.equals(outputModelVersion)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "caseOutputs.model_version 必须与 model_version 一致");
            }
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, fieldName + " 不能为空");
        }
        return value.trim();
    }

    private String normalizeOptional(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
