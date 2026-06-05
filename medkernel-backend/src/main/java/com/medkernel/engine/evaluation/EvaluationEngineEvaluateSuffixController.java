package com.medkernel.engine.evaluation;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-08 评估执行 suffix 入口：POST /api/v1/engine/evaluation:evaluate。
 */
@RestController
@RequestMapping("/api/v1/engine/evaluation:evaluate")
@DataScope(requireTenant = true)
public class EvaluationEngineEvaluateSuffixController {

    private final EvaluationEngineService service;

    public EvaluationEngineEvaluateSuffixController(EvaluationEngineService service) {
        this.service = service;
    }

    /**
     * 执行上下文快照确定性 B0 评估扫描，响应显式携带 {@code MODEL_DISABLED}。
     */
    @PostMapping
    @PreAuthorize("@perm.has('evaluation.execute')")
    public ApiResult<EvaluationRunResponse> evaluate(
            @RequestBody @Valid EvaluationEvaluateSnapshotRequest request) {
        return ApiResult.ok(service.evaluateSnapshot(request));
    }
}
