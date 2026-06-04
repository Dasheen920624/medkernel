package com.medkernel.engine.knowledge.diagnosis.runtime;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行时鉴别诊断 API（归推荐引擎客户面，复用推荐写权限与卡治理）。
 *
 * <p>从已建上下文快照产出可解释、可降级、守监管边界的鉴别诊断候选并落库为推荐卡（需医师确认，非自动诊断）。
 */
@RestController
@RequestMapping("/api/v1/engine/recommendations")
@DataScope(requireTenant = true)
public class DiagnosisAssistController {

    private final DiagnosisAssistService service;

    public DiagnosisAssistController(DiagnosisAssistService service) {
        this.service = service;
    }

    @PostMapping("/diagnosis-assist")
    @PreAuthorize("@perm.has('recommendation.write')")
    public ApiResult<DiagnosisAssistResponse> diagnosisAssist(@RequestBody @Valid DiagnosisAssistRequest request) {
        return ApiResult.ok(service.assist(request));
    }
}
