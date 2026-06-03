package com.medkernel.engine.recommendation;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-07 客户面推荐评估 suffix 入口：POST /api/v1/engine/recommendations:evaluate。
 */
@RestController
@RequestMapping("/api/v1/engine/recommendations:evaluate")
@DataScope(requireTenant = true)
public class RecommendationEvaluateSuffixController {

    private final RecommendationEngineService service;

    public RecommendationEvaluateSuffixController(RecommendationEngineService service) {
        this.service = service;
    }

    /**
     * 评估推荐触发，返回可展示卡、疲劳抑制数与模型降级状态。
     */
    @PostMapping
    @PreAuthorize("@perm.has('recommendation.write')")
    public ApiResult<RecommendationEvaluationResponse> evaluate(
            @RequestBody @Valid RecommendationTriggerRequest request) {
        return ApiResult.ok(service.evaluate(request));
    }
}
