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

    public KnowledgeProductionController(KnowledgeProductionOrchestrationService service,
                                         CandidateProvenanceService provenanceService,
                                         ProfessionalAssetTemplateRegistry templateRegistry) {
        this.service = service;
        this.provenanceService = provenanceService;
        this.templateRegistry = templateRegistry;
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
}
