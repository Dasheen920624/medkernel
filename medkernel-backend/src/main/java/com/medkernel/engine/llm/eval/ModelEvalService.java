package com.medkernel.engine.llm.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.llm.provider.ModelProviderRegistry;
import com.medkernel.engine.llm.provider.ProviderCompletion;
import com.medkernel.engine.llm.provider.ProviderRequest;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
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

    private final MedicalRegressionCaseRepository caseRepo;
    private final ModelEvalRunRepository runRepo;
    private final MedicalRegressionEvaluator evaluator;
    private final ModelProviderRegistry registry;
    private final AuditRecorder auditRecorder;

    public ModelEvalService(MedicalRegressionCaseRepository caseRepo,
                            ModelEvalRunRepository runRepo,
                            MedicalRegressionEvaluator evaluator,
                            ModelProviderRegistry registry,
                            AuditRecorder auditRecorder) {
        this.caseRepo = caseRepo;
        this.runRepo = runRepo;
        this.evaluator = evaluator;
        this.registry = registry;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public ModelEvalRun runEvaluation(String providerCode, String modelVersion, String capabilityCode) {
        String tenantId = requireCurrentTenant();
        List<MedicalRegressionCase> cases =
            caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(tenantId, capabilityCode, "Y");
        if (cases.isEmpty()) {
            // 无医学基准集不得自动认证（铁律 #1/#3），如实记 FAILED。
            return persist(tenantId, providerCode, modelVersion,
                new MedicalRegressionEvaluator.EvalVerdict(0, 0, 0, false, false, "FAILED"));
        }

        var resolved = registry.resolveByCode(tenantId, providerCode);
        if (resolved.isEmpty()) {
            return persist(tenantId, providerCode, modelVersion,
                new MedicalRegressionEvaluator.EvalVerdict(cases.size(), 0, cases.size(), false, false, "FAILED"));
        }
        var provider = resolved.get();

        MedicalRegressionEvaluator.EvalVerdict verdict = evaluator.evaluate(cases,
            regCase -> provider.adapter().complete(provider.config(),
                new ProviderRequest(regCase.capabilityCode(), regCase.caseInput(), 60_000)));
        return persist(tenantId, providerCode, modelVersion, verdict);
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
        ModelEvalRun run = runRepo.findById(runId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "评测运行不存在: " + runId));
        if (!tenantId.equals(run.tenantId())) {
            throw new ApiException(ErrorCode.TENANT_FORBIDDEN, "无权访问该评测运行");
        }
        if (!"PENDING_REVIEW".equals(run.status())) {
            throw new ApiException(ErrorCode.ENG_LLM_008,
                "仅待复核（PENDING_REVIEW）评测可签字放行，当前状态: " + run.status());
        }
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelEvalRun signed = runRepo.save(new ModelEvalRun(
            run.id(), run.tenantId(), run.providerCode(), run.modelVersion(),
            run.capabilityCode(), run.promptVersion(), run.toolVersion(),
            run.totalCases(), run.passedCases(), run.failedCases(),
            run.qualityScore(), run.terminologyScore(),
            run.fakeCitationDetected(), run.redLineBreach(), run.hallucinationDetected(),
            "PASSED", run.caseSummaryJson(),
            actor, now, run.createdAt(), run.createdBy(), now, actor));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_eval_run", String.valueOf(runId),
            "专家复核签字放行评测 " + run.providerCode() + "/" + run.modelVersion());
        return signed;
    }

    @Transactional(readOnly = true)
    public boolean isClearedForGoLive(String tenantId, String providerCode, String modelVersion) {
        return runRepo.findFirstByTenantIdAndProviderCodeAndModelVersionAndStatusOrderByIdDesc(
            tenantId, providerCode, modelVersion, "PASSED").isPresent();
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
                                 MedicalRegressionEvaluator.EvalVerdict verdict) {
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelEvalRun saved = runRepo.save(new ModelEvalRun(
            null, tenantId, providerCode, modelVersion, null, null, null,
            verdict.total(), verdict.passed(), verdict.failed(),
            null, null, verdict.fakeCitationDetected() ? "Y" : "N", verdict.redLineBreach() ? "Y" : "N",
            "N", verdict.status(), "[]", null, null, now, actor, now, actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_llm_eval_run", providerCode + "/" + modelVersion,
            "运行医学回归评测 " + providerCode + "/" + modelVersion + " -> " + verdict.status());
        return saved;
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
