package com.medkernel.engine.knowledge.production;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.factory.ProfessionalAssetTemplate;
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.knowledge.production.gate.AikGateResult;
import com.medkernel.engine.knowledge.production.gate.CandidateSafetyGateService;
import com.medkernel.engine.knowledge.production.generation.CandidateGenerationOrchestrationService;
import com.medkernel.engine.knowledge.production.generation.CandidateGenerationRequest;
import com.medkernel.engine.knowledge.production.generation.GenerationSummary;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowEvaluationService;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowRun;
import com.medkernel.engine.knowledge.production.triage.GenerationTriage;
import com.medkernel.engine.knowledge.production.triage.KnowledgeGenerationTriageService;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;

/**
 * 知识生产编排 API（AIK-STD-13）。
 *
 * <p>统一编排层：建生产 job（FR-1）+ 提交候选（FR-3，经校验闸 + §9 双形态隔离守卫）+ job 台账/进度。
 * 建 job / 提交候选走 {@code knowledge.write}，台账走 {@code knowledge.read}；隔离靠 t-1 守卫非权限（会签角色路由 PR2）。
 * 类级 {@link DataScope}：所有方法需租户上下文。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge-production")
@DataScope(requireTenant = true)
public class KnowledgeProductionController {

    private final KnowledgeProductionOrchestrationService service;
    private final CandidateProvenanceService provenanceService;
    private final ProfessionalAssetTemplateRegistry templateRegistry;
    private final CandidateGenerationOrchestrationService generationService;
    private final CandidateSafetyGateService gateService;
    private final KnowledgeGenerationTriageService triageService;
    private final KnowledgeShadowEvaluationService shadowService;
    private final CandidateCoexistenceService coexistenceService;
    private final KnowledgeProductionReadinessService readinessService;

    public KnowledgeProductionController(KnowledgeProductionOrchestrationService service,
                                         CandidateProvenanceService provenanceService,
                                         ProfessionalAssetTemplateRegistry templateRegistry,
                                         CandidateGenerationOrchestrationService generationService,
                                         CandidateSafetyGateService gateService,
                                         KnowledgeGenerationTriageService triageService,
                                         KnowledgeShadowEvaluationService shadowService,
                                         CandidateCoexistenceService coexistenceService,
                                         KnowledgeProductionReadinessService readinessService) {
        this.service = service;
        this.provenanceService = provenanceService;
        this.templateRegistry = templateRegistry;
        this.generationService = generationService;
        this.gateService = gateService;
        this.triageService = triageService;
        this.shadowService = shadowService;
        this.coexistenceService = coexistenceService;
        this.readinessService = readinessService;
    }

    @PostMapping("/jobs")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<ProductionJobResponse> createJob(@Valid @RequestBody ProductionJobRequest request) {
        return ApiResult.ok(service.createJob(request));
    }

    @GetMapping("/jobs")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<PageResponse<KnowledgeProductionJob>> listJobs(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(service.listJobs(page, size));
    }

    @GetMapping("/jobs/{jobCode}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<ProductionJobResponse> getJob(@PathVariable String jobCode) {
        return ApiResult.ok(service.getJob(jobCode));
    }

    @PostMapping("/jobs/{jobCode}/candidates")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<CandidateSubmissionResponse> submitCandidate(@PathVariable String jobCode,
                                             @Valid @RequestBody CandidateSubmissionRequest request) {
        return ApiResult.ok(service.submitCandidate(jobCode, request.candidate(), request.target()));
    }

    /** 候选生产血缘列表（FR-5 可回溯）。 */
    @GetMapping("/jobs/{jobCode}/candidates")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<ProductionCandidateView>> listCandidates(@PathVariable String jobCode) {
        return ApiResult.ok(service.listCandidates(jobCode));
    }

    /** 候选来源溯源（AIK-STD-12 PR1）：审核台批量反查候选 AI 工厂来源，旁挂只读不改既有候选响应。 */
    @PostMapping("/candidates/provenance")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<CandidateProvenanceView>> candidateProvenance(
            @Valid @RequestBody CandidateProvenanceRequest request) {
        return ApiResult.ok(provenanceService.resolve(request.candidateRefs()));
    }

    /** 候选共存视图（AIK-STD-09/11）：待审候选不执行，现行 ACTIVE 仍是唯一执行版本。 */
    @GetMapping("/candidates/coexistence")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<CandidateCoexistenceView> candidateCoexistence(@RequestParam String candidateRef) {
        return ApiResult.ok(coexistenceService.resolve(candidateRef));
    }

    /** 正式模型生成知识 readiness 闸（AIK-STD-13/LLM-01/02/04）：只读返回阻断项，不调用模型。 */
    @GetMapping("/readiness")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<KnowledgeProductionReadinessResponse> readiness(
            @RequestParam(required = false, defaultValue = "API_MODEL") KnowledgeProducer producer,
            @RequestParam(required = false) String capabilityCode,
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String modelStrategy) {
        return ApiResult.ok(readinessService.evaluate(producer, capabilityCode, providerCode, modelStrategy));
    }

    /** 完成 job（FR-1）。 */
    @PostMapping("/jobs/{jobCode}/complete")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<ProductionJobResponse> completeJob(@PathVariable String jobCode) {
        return ApiResult.ok(service.completeJob(jobCode));
    }

    /** 中止 job（FR-1）。 */
    @PostMapping("/jobs/{jobCode}/cancel")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<ProductionJobResponse> cancelJob(@PathVariable String jobCode) {
        return ApiResult.ok(service.cancelJob(jobCode));
    }

    /** 重放 job（FR-5）。 */
    @PostMapping("/jobs/{jobCode}/replay")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<ProductionJobResponse> replayJob(@PathVariable String jobCode) {
        return ApiResult.ok(service.replayJob(jobCode));
    }

    /** 全专业标准资产模板目录（AIK-STD-12 FR-1）：审核台/工作台按资产类型+领域对照核查完整性。 */
    @GetMapping("/asset-templates")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<ProfessionalAssetTemplate>> assetTemplates() {
        return ApiResult.ok(templateRegistry.listAll());
    }

    /** 从受控来源生成知识候选（AIK-STD-04）：逐资产类型建 job → 模板桩候选 → 既有审核链。 */
    @PostMapping("/generate")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<GenerationSummary> generate(@Valid @RequestBody CandidateGenerationRequest request) {
        return ApiResult.ok(generationService.generate(request));
    }

    /** 候选安全门禁结果列表（AIK-STD-05 FR-5）：按 job 回溯逐项门禁判定与不过原因，可审计。 */
    @GetMapping("/jobs/{jobCode}/gate-results")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<AikGateResult>> gateResults(@PathVariable String jobCode) {
        return ApiResult.ok(gateService.listResults(jobCode));
    }

    /** 生成期 8 态分流结果列表（AIK-STD-10 FR-5）：按 job 回溯身份识别、去重与处理去向。 */
    @GetMapping("/jobs/{jobCode}/triage-results")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<GenerationTriage>> triageResults(@PathVariable String jobCode) {
        return ApiResult.ok(triageService.listResults(jobCode));
    }

    /** 生成期影子评测结果列表（AIK-STD-06 FR-2/3/4）：只读回溯指标、退化和达标裁决。 */
    @GetMapping("/jobs/{jobCode}/shadow-runs")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<KnowledgeShadowRun>> shadowRuns(@PathVariable String jobCode) {
        return ApiResult.ok(shadowService.listResults(jobCode));
    }
}
