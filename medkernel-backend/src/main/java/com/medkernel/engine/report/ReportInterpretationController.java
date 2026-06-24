package com.medkernel.engine.report;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医技报告解读 API，复用推荐引擎客户面权限与治理。
 */
@RestController
@RequestMapping("/api/v1/engine/recommendations")
@DataScope(requireTenant = true)
public class ReportInterpretationController {

    private final ReportInterpretationService service;

    public ReportInterpretationController(ReportInterpretationService service) {
        this.service = service;
    }

    @PostMapping("/report-interpretation")
    @PreAuthorize("@perm.has('recommendation.write')")
    public ApiResult<ReportInterpretationResponse> interpret(
            @RequestBody @Valid ReportInterpretationRequest request) {
        return ApiResult.ok(service.interpret(request));
    }
}
