package com.medkernel.engine.llm.eval;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;

/**
 * 医学回归评测治理控制器（LLM-07 T18）。
 *
 * <p>由质量与医保治理员（{@code llm.eval.manage}）对候选 provider/版本运行医学回归评测、对高风险换版做专家复核签字放行。
 * 评测结果是 provider 上线门禁（{@code ENG-LLM-008}）的依据。全线 {@link DataScope} 强多租户隔离。
 */
@RestController
@RequestMapping("/api/v1/model-evaluations")
@DataScope(requireTenant = true)
public class ModelEvalController {

    private final ModelEvalService service;
    private final MedicalRegressionCaseManagementService caseManagementService;

    public ModelEvalController(
            ModelEvalService service,
            MedicalRegressionCaseManagementService caseManagementService) {
        this.service = service;
        this.caseManagementService = caseManagementService;
    }

    /**
     * 对候选 provider 的指定模型版本在某能力码基准集上运行一次医学回归评测。
     */
    @PostMapping
    @PreAuthorize("@perm.has('llm.eval.manage')")
    public ApiResult<ModelEvalRun> runEvaluation(@Valid @RequestBody ModelEvalRunRequest request) {
        return ApiResult.ok(service.runEvaluation(
            request.providerCode().trim(), request.modelVersion().trim(), request.capabilityCode().trim()));
    }

    /**
     * 专家复核签字放行一条待复核（{@code PENDING_REVIEW}）评测运行（高风险换版）。
     */
    @PostMapping("/{runId}/sign-off")
    @PreAuthorize("@perm.has('llm.eval.manage')")
    public ApiResult<ModelEvalRun> signOff(@PathVariable Long runId) {
        return ApiResult.ok(service.signOff(runId));
    }

    /**
     * 查询当前租户医学回归基准用例，供评测治理台维护真实基线。
     */
    @GetMapping("/regression-cases")
    @PreAuthorize("@perm.has('llm.eval.manage')")
    public ApiResult<List<MedicalRegressionCase>> listRegressionCases(
            @RequestParam(required = false) String capabilityCode,
            @RequestParam(required = false) String enabledFlag) {
        return ApiResult.ok(caseManagementService.list(capabilityCode, enabledFlag));
    }

    /**
     * 新增一条带真实来源引用的医学回归基准用例。
     */
    @PostMapping("/regression-cases")
    @PreAuthorize("@perm.has('llm.eval.manage')")
    public ApiResult<MedicalRegressionCase> createRegressionCase(
            @Valid @RequestBody MedicalRegressionCaseRequest request) {
        return ApiResult.ok(caseManagementService.create(request));
    }

    /**
     * 批量导入真实医学回归基准用例，逐条保留能力码和版本。
     */
    @PostMapping("/regression-cases:bulk-import")
    @PreAuthorize("@perm.has('llm.eval.manage')")
    public ApiResult<List<MedicalRegressionCase>> bulkImportRegressionCases(
            @Valid @RequestBody MedicalRegressionCaseBulkImportRequest request) {
        return ApiResult.ok(caseManagementService.bulkImport(request));
    }

    /**
     * 启用当前租户的一条基准用例。
     */
    @PostMapping("/regression-cases/{caseId}:enable")
    @PreAuthorize("@perm.has('llm.eval.manage')")
    public ApiResult<MedicalRegressionCase> enableRegressionCase(@PathVariable Long caseId) {
        return ApiResult.ok(caseManagementService.setEnabled(caseId, true));
    }

    /**
     * 停用当前租户的一条基准用例。
     */
    @PostMapping("/regression-cases/{caseId}:disable")
    @PreAuthorize("@perm.has('llm.eval.manage')")
    public ApiResult<MedicalRegressionCase> disableRegressionCase(@PathVariable Long caseId) {
        return ApiResult.ok(caseManagementService.setEnabled(caseId, false));
    }
}
