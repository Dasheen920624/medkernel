package com.medkernel.engine.cdss.risk;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPT-03 CDSS 风险分级矩阵 API。
 */
@RestController
@RequestMapping("/api/v1/engine/cdss/risk-matrix")
@DataScope(requireTenant = true)
public class CdssRiskMatrixController {

    private final CdssRiskMatrixService service;

    public CdssRiskMatrixController(CdssRiskMatrixService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('recommendation.read')")
    public ApiResult<CdssRiskMatrixResponse> activeMatrix() {
        return ApiResult.ok(service.activeMatrix());
    }

    @PutMapping
    @PreAuthorize("@perm.has('recommendation.write')")
    public ApiResult<CdssRiskMatrixResponse> updateMatrix(
            @RequestBody @Valid CdssRiskMatrixUpdateRequest request) {
        return ApiResult.ok(service.updateMatrix(request));
    }
}
