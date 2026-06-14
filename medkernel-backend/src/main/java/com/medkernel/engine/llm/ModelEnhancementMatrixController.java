package com.medkernel.engine.llm;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;

/**
 * 全业务模型增强接入矩阵治理控制器（LLM-05）。
 *
 * <p>「模型网关全局目录」的增强接入图谱：读侧（{@code llm.read}）查矩阵台账与覆盖核查，
 * 写侧（{@code llm.enhancement.manage}，§9 平台治理管理员职责）登记/更新业务点接入——上线过
 * B0 前置门禁与统一网关接入校验。全线 {@link DataScope} 强多租户隔离。
 */
@RestController
@RequestMapping("/api/v1/model-enhancement-matrix")
@DataScope(requireTenant = true)
public class ModelEnhancementMatrixController {

    private final ModelEnhancementMatrixService service;

    public ModelEnhancementMatrixController(ModelEnhancementMatrixService service) {
        this.service = service;
    }

    /** FR-1：列举全部可增强业务点矩阵台账。 */
    @GetMapping
    @PreAuthorize("@perm.has('llm.read')")
    public ApiResult<List<ModelEnhancementMatrixResponse>> list() {
        return ApiResult.ok(service.listMatrix());
    }

    /** FR-4：覆盖核查——缺口诚实标注，不虚报覆盖。 */
    @GetMapping("/coverage")
    @PreAuthorize("@perm.has('llm.read')")
    public ApiResult<EnhancementCoverageReport> coverage() {
        return ApiResult.ok(service.coverageReport());
    }

    /** FR-2/FR-3：登记/更新业务点接入（上线过 B0 前置门禁 + 统一网关接入校验）。 */
    @PutMapping("/{businessPoint}")
    @PreAuthorize("@perm.has('llm.enhancement.manage')")
    public ApiResult<ModelEnhancementMatrixResponse> upsert(
            @PathVariable String businessPoint,
            @Valid @RequestBody ModelEnhancementMatrixUpsertRequest request) {
        return ApiResult.ok(service.upsertEntry(businessPoint, request));
    }
}
