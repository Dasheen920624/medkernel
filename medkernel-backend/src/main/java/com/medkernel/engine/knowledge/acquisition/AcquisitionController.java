package com.medkernel.engine.knowledge.acquisition;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;

/**
 * 公域知识资料获取 API（AIK-STD-14）。
 *
 * <p>触发获取仅登记候选来源资料并进入解析链路，不直接发布权威知识；来源白名单和运行账本用于追溯许可、
 * robots 策略、真实 URL、原文指纹和资料 URI。类级 {@link DataScope}：所有方法需租户上下文。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge/acquisition")
@DataScope(requireTenant = true)
public class AcquisitionController {

    private final AcquisitionOrchestrationService service;
    private final AcquisitionSourceGovernanceService sourceGovernanceService;

    public AcquisitionController(AcquisitionOrchestrationService service,
                                 AcquisitionSourceGovernanceService sourceGovernanceService) {
        this.service = service;
        this.sourceGovernanceService = sourceGovernanceService;
    }

    /** 触发一次公域资料获取：白名单门禁 → 真实抓取 → 解析入受控来源。 */
    @PostMapping("/runs")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<KnowledgeAcquisitionRunResponse> run(@Valid @RequestBody KnowledgeAcquisitionRunRequest request) {
        return ApiResult.ok(service.run(request));
    }

    /** 分页查询公域资料来源白名单。 */
    @GetMapping("/sources")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<PageResponse<KnowledgeAcquisitionSource>> listSources(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(service.listSources(page, size));
    }

    /** 登记或更新来源待审批草稿；服务层强制停用并撤销旧审批。 */
    @PutMapping("/sources/{sourceCode}")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<KnowledgeAcquisitionSource> saveSourceDraft(
            @PathVariable String sourceCode,
            @Valid @RequestBody AcquisitionSourceDraftRequest request) {
        return ApiResult.ok(sourceGovernanceService.saveDraft(sourceCode, request));
    }

    /** 经 MFA 和职责分离校验后审批并启用来源。 */
    @PostMapping("/sources/{sourceCode}/approval")
    @PreAuthorize("@perm.has('knowledge.acquisition.approve')")
    public ApiResult<KnowledgeAcquisitionSource> approveSource(@PathVariable String sourceCode) {
        return ApiResult.ok(sourceGovernanceService.approve(sourceCode));
    }

    /** 高权限停用来源及其自动调度，保留历史审批证据。 */
    @PostMapping("/sources/{sourceCode}/disable")
    @PreAuthorize("@perm.has('knowledge.acquisition.approve')")
    public ApiResult<KnowledgeAcquisitionSource> disableSource(@PathVariable String sourceCode) {
        return ApiResult.ok(sourceGovernanceService.disable(sourceCode));
    }

    /** 分页查询公域资料获取运行账本。 */
    @GetMapping("/runs")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<PageResponse<KnowledgeAcquisitionRun>> listRuns(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(service.listRuns(page, size));
    }
}
