package com.medkernel.engine.llm.eval;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.llm.provider.ModelProviderRegistry;
import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.llm.provider.DeploymentFormService;
import com.medkernel.engine.llm.provider.ProviderCompletion;
import com.medkernel.engine.llm.provider.ProviderRequest;
import com.medkernel.engine.security.AuthenticatedRoleGuard;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.config.HighRiskChangeGuard;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.runtime.RuntimeProperties;

/**
 * 医学回归评测服务（LLM-07 T16/T17/T18）。
 *
 * <p>运行评测（对候选 provider 跑基准集，无基准集不认证）、高风险换版专家复核签字、上线门禁查询。
 * provider 启用前须存在 {@code PASSED} 评测运行（{@link #isClearedForGoLive}），否则 {@code ENG-LLM-008} 阻断。
 */
@Service
public class ModelEvalService {

    private static final String SIGN_OFF_RESOURCE_TYPE = "model_eval_sign_off";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final Set<String> RUN_STATUSES = Set.of("PENDING_REVIEW", "PASSED", "FAILED");

    private final MedicalRegressionCaseRepository caseRepo;
    private final ModelEvalRunRepository runRepo;
    private final ModelEvalCaseEvidenceRepository evidenceRepo;
    private final MedicalRegressionEvaluator evaluator;
    private final ModelProviderRegistry registry;
    private final AuditRecorder auditRecorder;
    private final HighRiskChangeGuard highRiskChangeGuard;
    private final RuntimeProperties runtimeProperties;
    private final DeploymentFormService deploymentFormService;

    public ModelEvalService(MedicalRegressionCaseRepository caseRepo,
                            ModelEvalRunRepository runRepo,
                            ModelEvalCaseEvidenceRepository evidenceRepo,
                            MedicalRegressionEvaluator evaluator,
                            ModelProviderRegistry registry,
                            AuditRecorder auditRecorder,
                            HighRiskChangeGuard highRiskChangeGuard,
                            RuntimeProperties runtimeProperties,
                            DeploymentFormService deploymentFormService) {
        this.caseRepo = caseRepo;
        this.runRepo = runRepo;
        this.evidenceRepo = evidenceRepo;
        this.evaluator = evaluator;
        this.registry = registry;
        this.auditRecorder = auditRecorder;
        this.highRiskChangeGuard = highRiskChangeGuard;
        this.runtimeProperties = runtimeProperties;
        this.deploymentFormService = deploymentFormService;
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
    public ModelEvalRun signOff(Long runId, ModelEvalSignOffRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.evidenceAcknowledged())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "必须确认已逐例核查评测证据");
        }
        String reviewComment = requireText(request.reviewComment(), "review_comment");
        if (reviewComment.length() < 10 || reviewComment.length() > 1000) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "专家复核意见须为 10 至 1000 字");
        }
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
        if (!requireCurrentReleaseFingerprint().equals(run.releaseFingerprint())) {
            throw new ApiException(
                ErrorCode.ENG_LLM_008,
                "该评测属于历史运行制品，必须在当前制品重新运行");
        }
        String actor = RequestContext.currentUserId().orElse("system");
        if (actor.equals(run.createdBy())) {
            throw ApiException.conflict("专家签字人与评测执行人必须分离");
        }
        List<ModelEvalCaseEvidence> evidence = evidenceRepo
            .findByTenantIdAndRunIdOrderByIdAsc(tenantId, runId);
        assertReviewEvidenceComplete(run, evidence);
        List<MedicalRegressionCase> currentCases = caseRepo
            .findByTenantIdAndCapabilityCodeAndEnabledFlag(
                tenantId, requireText(run.capabilityCode(), "capability_code"), "Y");
        if (currentCases.isEmpty()
            || !RegressionBaselineEvidence.matches(run.caseSummaryJson(), currentCases)
            || !evidenceMatchesCurrentCases(evidence, currentCases)) {
            throw new ApiException(ErrorCode.ENG_LLM_008, "当前医学回归基准已变化，必须重新运行评测");
        }
        highRiskChangeGuard.assertHighRiskAllowed(SIGN_OFF_RESOURCE_TYPE, String.valueOf(runId));
        Instant now = Instant.now();
        int updated = runRepo.signOffPending(runId, tenantId, actor, reviewComment, now);
        if (updated != 1) {
            throw ApiException.conflict("评测复核状态已被其他请求处理，请刷新后重试");
        }
        ModelEvalRun signed = new ModelEvalRun(
            run.id(), run.tenantId(), run.providerCode(), run.modelVersion(),
            run.capabilityCode(), run.promptVersion(), run.toolVersion(), run.releaseFingerprint(),
            run.totalCases(), run.passedCases(), run.failedCases(),
            run.qualityScore(), run.terminologyScore(),
            run.fakeCitationDetected(), run.redLineBreach(), run.hallucinationDetected(),
            "PASSED", run.caseSummaryJson(),
            reviewComment, actor, now, run.createdAt(), run.createdBy(), now, actor);
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_eval_run", String.valueOf(runId),
            "专家复核签字放行评测 " + run.providerCode() + "/" + run.modelVersion());
        return signed;
    }

    private void assertReviewEvidenceComplete(
            ModelEvalRun run,
            List<ModelEvalCaseEvidence> evidence) {
        if (!isReviewEvidenceComplete(run, evidence)) {
            throw new ApiException(ErrorCode.ENG_LLM_008, "评测逐用例证据不完整或存在未通过裁决");
        }
    }

    private boolean isReviewEvidenceComplete(
            ModelEvalRun run,
            List<ModelEvalCaseEvidence> evidence) {
        boolean summaryPassed = run.totalCases() > 0
            && run.totalCases() == run.passedCases()
            && run.failedCases() == 0
            && "N".equalsIgnoreCase(run.fakeCitationDetected())
            && "N".equalsIgnoreCase(run.redLineBreach())
            && "N".equalsIgnoreCase(run.hallucinationDetected());
        boolean everyCasePassed = evidence != null
            && evidence.size() == run.totalCases()
            && evidence.stream().allMatch(item -> item.passed()
                && "Y".equalsIgnoreCase(item.expectedPhraseHit())
                && "Y".equalsIgnoreCase(item.citationVerified())
                && "N".equalsIgnoreCase(item.redLineBreach()));
        return summaryPassed && everyCasePassed;
    }

    private boolean evidenceMatchesCurrentCases(
            List<ModelEvalCaseEvidence> evidence,
            List<MedicalRegressionCase> currentCases) {
        Map<Long, MedicalRegressionCase> currentById = currentCases.stream()
            .filter(item -> item.id() != null)
            .collect(Collectors.toMap(MedicalRegressionCase::id, Function.identity()));
        if (currentById.size() != currentCases.size()) {
            return false;
        }
        return evidence.stream().allMatch(item -> {
            MedicalRegressionCase current = currentById.get(item.regressionCaseId());
            return current != null
                && java.util.Objects.equals(item.caseVersion(), current.caseVersion())
                && java.util.Objects.equals(item.caseInput(), current.caseInput())
                && java.util.Objects.equals(item.expectedPhrase(), current.expectedPhrase())
                && java.util.Objects.equals(item.redLineType(), current.redLineType())
                && java.util.Objects.equals(item.sourceReference(), current.sourceReference());
        });
    }

    @Transactional(readOnly = true)
    public boolean isClearedForGoLive(
            String tenantId,
            String providerCode,
            String modelVersion,
            String capabilityCode) {
        String normalizedCapability = requireText(capabilityCode, "capability_code");
        Optional<ModelEvalRun> run = runRepo
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                tenantId, providerCode, modelVersion, normalizedCapability, "PASSED");
        if (run.isEmpty()) {
            return false;
        }
        if (!releaseMatchesCurrent(run.get())) {
            return false;
        }
        List<MedicalRegressionCase> currentCases = caseRepo
            .findByTenantIdAndCapabilityCodeAndEnabledFlag(
                tenantId, normalizedCapability, "Y");
        if (currentCases.isEmpty()
            || run.get().totalCases() != currentCases.size()
            || run.get().passedCases() != currentCases.size()
            || run.get().failedCases() != 0
            || !RegressionBaselineEvidence.matches(run.get().caseSummaryJson(), currentCases)) {
            return false;
        }
        List<ModelEvalCaseEvidence> evidence = evidenceRepo
            .findByTenantIdAndRunIdOrderByIdAsc(tenantId, run.get().id());
        if (!isReviewEvidenceComplete(run.get(), evidence)
            || !evidenceMatchesCurrentCases(evidence, currentCases)) {
            return false;
        }
        boolean requiresExpertSignOff = currentCases.stream().anyMatch(MedicalRegressionCase::redLine);
        return !requiresExpertSignOff
            || (run.get().reviewer() != null
                && !run.get().reviewer().isBlank()
                && !run.get().reviewer().equals(run.get().createdBy())
                && run.get().reviewComment() != null
                && !run.get().reviewComment().isBlank()
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

    /** 按当前租户和状态查询医学回归运行，始终使用服务端分页。 */
    @Transactional(readOnly = true)
    public PageResponse<ModelEvalRunSummaryResponse> listRuns(String status, PageRequest pageRequest) {
        String tenantId = requireCurrentTenant();
        String normalizedStatus = requireText(status, "status").toUpperCase(Locale.ROOT);
        if (!RUN_STATUSES.contains(normalizedStatus)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "医学回归评测状态无效");
        }
        PageRequest page = pageRequest == null ? PageRequest.defaults() : pageRequest;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
            page.safePage() - 1, page.safeSize());
        List<ModelEvalRunSummaryResponse> items = runRepo
            .findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, normalizedStatus, pageable)
            .stream()
            .map(this::toSummary)
            .toList();
        long total = runRepo.countByTenantIdAndStatus(tenantId, normalizedStatus);
        return PageResponse.of(items, page, total);
    }

    /** 读取单次运行及逐例证据，并计算当前是否仍满足复核条件。 */
    @Transactional(readOnly = true)
    public ModelEvalRunDetailResponse getRunDetail(Long runId) {
        String tenantId = requireCurrentTenant();
        ModelEvalRun run = requireTenantRun(runId, tenantId);
        List<ModelEvalCaseEvidence> evidence = evidenceRepo
            .findByTenantIdAndRunIdOrderByIdAsc(tenantId, runId);
        List<MedicalRegressionCase> currentCases = run.capabilityCode() == null
            || run.capabilityCode().isBlank()
            ? List.of()
            : caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(
                tenantId, run.capabilityCode(), "Y");
        boolean evidenceComplete = isReviewEvidenceComplete(run, evidence);
        boolean baselineCurrent = !currentCases.isEmpty()
            && RegressionBaselineEvidence.matches(run.caseSummaryJson(), currentCases)
            && (evidence.isEmpty() || evidenceMatchesCurrentCases(evidence, currentCases));
        boolean releaseCurrent = releaseMatchesCurrent(run);
        boolean reviewable = "PENDING_REVIEW".equals(run.status())
            && evidenceComplete
            && baselineCurrent
            && releaseCurrent;
        return new ModelEvalRunDetailResponse(
            toSummary(run),
            evidence.stream().map(this::toEvidenceResponse).toList(),
            evidenceComplete,
            baselineCurrent,
            releaseCurrent,
            reviewable,
            reviewBlockReason(run, evidenceComplete, baselineCurrent, releaseCurrent));
    }

    private ModelEvalRun requireTenantRun(Long runId, String tenantId) {
        ModelEvalRun run = runRepo.findById(runId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "评测运行不存在: " + runId));
        if (!tenantId.equals(run.tenantId())) {
            throw new ApiException(ErrorCode.TENANT_FORBIDDEN, "无权访问该评测运行");
        }
        return run;
    }

    private ModelEvalRunSummaryResponse toSummary(ModelEvalRun run) {
        return new ModelEvalRunSummaryResponse(
            run.id(), run.providerCode(), run.modelVersion(), run.capabilityCode(),
            run.promptVersion(), run.toolVersion(), run.releaseFingerprint(),
            run.totalCases(), run.passedCases(), run.failedCases(),
            "Y".equalsIgnoreCase(run.fakeCitationDetected()),
            "Y".equalsIgnoreCase(run.redLineBreach()),
            "Y".equalsIgnoreCase(run.hallucinationDetected()),
            run.status(), run.reviewer(), run.signedAt(), run.reviewComment(), run.createdAt(), run.createdBy());
    }

    private ModelEvalCaseEvidenceResponse toEvidenceResponse(ModelEvalCaseEvidence evidence) {
        return new ModelEvalCaseEvidenceResponse(
            evidence.id(), evidence.regressionCaseId(), evidence.caseVersion(), evidence.caseInput(),
            evidence.expectedPhrase(), evidence.redLineType(), evidence.sourceReference(),
            evidence.outputContent(), evidence.sourceCitations(),
            "Y".equalsIgnoreCase(evidence.expectedPhraseHit()),
            "Y".equalsIgnoreCase(evidence.citationRequired()),
            "Y".equalsIgnoreCase(evidence.citationVerified()),
            "Y".equalsIgnoreCase(evidence.redLineCase()),
            "Y".equalsIgnoreCase(evidence.redLineBreach()),
            evidence.passed(), readFailureReasons(evidence.failureReasonsJson()));
    }

    private List<String> readFailureReasons(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST);
        } catch (JsonProcessingException invalidEvidence) {
            return List.of("EVIDENCE_FORMAT_INVALID");
        }
    }

    private String reviewBlockReason(
            ModelEvalRun run,
            boolean evidenceComplete,
            boolean baselineCurrent,
            boolean releaseCurrent) {
        if (!releaseCurrent) {
            return "该评测属于历史运行制品，必须在当前制品重新运行";
        }
        if (!"PENDING_REVIEW".equals(run.status())) {
            return "该运行当前不是待复核状态";
        }
        if (!evidenceComplete) {
            return "逐用例证据不完整，必须重新运行评测";
        }
        if (!baselineCurrent) {
            return "当前医学回归基准已变化，必须重新运行评测";
        }
        return null;
    }

    private ModelEvalRun persist(String tenantId, String providerCode, String modelVersion,
                                 String capabilityCode,
                                 List<MedicalRegressionCase> cases,
                                 MedicalRegressionEvaluator.EvalVerdict verdict) {
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelEvalRun saved = runRepo.save(new ModelEvalRun(
            null, tenantId, providerCode, modelVersion, capabilityCode, null, null,
            requireCurrentReleaseFingerprint(),
            verdict.total(), verdict.passed(), verdict.failed(),
            null, null, verdict.fakeCitationDetected() ? "Y" : "N", verdict.redLineBreach() ? "Y" : "N",
            "N", verdict.status(), RegressionBaselineEvidence.toJson(cases),
            null, null, null, now, actor, now, actor));
        persistCaseEvidence(saved, verdict.caseEvidence(), now, actor);
        auditRecorder.record(AuditAction.EXECUTE, "mk_llm_eval_run", providerCode + "/" + modelVersion,
            "运行医学回归评测 " + providerCode + "/" + modelVersion + " -> " + verdict.status());
        return saved;
    }

    private void persistCaseEvidence(
            ModelEvalRun run,
            List<MedicalRegressionEvaluator.EvalCaseEvidence> caseEvidence,
            Instant createdAt,
            String actor) {
        if (caseEvidence == null || caseEvidence.isEmpty()) {
            return;
        }
        if (run == null || run.id() == null) {
            throw new IllegalStateException("评测运行未生成主键，不能保存逐用例证据");
        }
        List<ModelEvalCaseEvidence> rows = caseEvidence.stream()
            .map(item -> new ModelEvalCaseEvidence(
                null,
                run.tenantId(),
                run.id(),
                item.caseId(),
                item.caseVersion(),
                item.caseInput(),
                item.expectedPhrase(),
                item.redLineType(),
                item.sourceReference(),
                item.outputContent() == null ? "" : item.outputContent(),
                item.sourceCitations(),
                flag(item.expectedPhraseHit()),
                flag(item.citationRequired()),
                flag(item.citationVerified()),
                flag(item.redLineCase()),
                flag(item.redLineBreach()),
                flag(item.passed()),
                toJson(item.failureReasons()),
                createdAt,
                actor))
            .toList();
        evidenceRepo.saveAll(rows);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("评测证据序列化失败", error);
        }
    }

    private String flag(boolean value) {
        return value ? "Y" : "N";
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
            requireCurrentReleaseFingerprint(),
            verdict.total(), verdict.passed(), verdict.failed(),
            verdict.qualityScore(), verdict.terminologyScore(), "N", "N",
            verdict.hallucinationDetected() ? "Y" : "N", verdict.status(), verdict.caseSummaryJson(),
            null, null, null, now, actor, now, actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_llm_eval_run", capabilityCode + "/" + modelVersion,
            "运行 AI 质量评测 " + capabilityCode + "/" + modelVersion + " -> " + verdict.status());
        return saved;
    }

    private boolean releaseMatchesCurrent(ModelEvalRun run) {
        if (run == null || run.releaseFingerprint() == null || run.releaseFingerprint().isBlank()) {
            return false;
        }
        try {
            return run.releaseFingerprint().equals(requireCurrentReleaseFingerprint());
        } catch (ApiException invalidRuntimeFingerprint) {
            return false;
        }
    }

    private String requireCurrentReleaseFingerprint() {
        String raw = runtimeProperties.getReleaseFingerprint();
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new ApiException(ErrorCode.ENG_LLM_008, "当前运行制品指纹未正确配置");
        }
        if (deploymentFormService.currentForm() == DeploymentForm.PRODUCTION_CENTER
            && Set.of("development", "dev", "unset", "unknown").contains(
                normalized.toLowerCase(Locale.ROOT))) {
            throw new ApiException(ErrorCode.ENG_LLM_008, "生产中心禁止使用占位运行制品指纹");
        }
        return normalized;
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
