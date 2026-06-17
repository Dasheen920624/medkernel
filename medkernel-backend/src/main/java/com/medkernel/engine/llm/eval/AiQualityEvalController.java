package com.medkernel.engine.llm.eval;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;

/**
 * AI 质量评测中心控制器（OPT-06）。
 *
 * <p>面向知识生成到上线链路的模型输出质控：按能力码、模型版本、prompt 版本和 tool 版本运行质量评测，
 * 持久化幻觉拦截、中文术语质量与版本趋势，作为上线门禁依据。
 */
@RestController
@RequestMapping("/api/v1/ai-eval")
@DataScope(requireTenant = true)
public class AiQualityEvalController {

    private final ModelEvalService service;

    public AiQualityEvalController(ModelEvalService service) {
        this.service = service;
    }

    /**
     * 运行一次 AI 质量评测，支持离线 B0 输出或真实 provider 输出。
     */
    @PostMapping("/runs")
    @PreAuthorize("@perm.has('llm.eval.manage')")
    public ApiResult<ModelEvalRun> run(@Valid @RequestBody AiQualityEvalRunRequest request) {
        return ApiResult.ok(service.runQualityEvaluation(request));
    }

    /**
     * 查询指定能力码和模型版本的最近 AI 质量趋势。
     */
    @GetMapping("/trends")
    @PreAuthorize("@perm.has('llm.eval.manage')")
    public ApiResult<AiQualityTrendResponse> trend(
            @RequestParam String capabilityCode,
            @RequestParam String modelVersion) {
        return ApiResult.ok(service.qualityTrend(capabilityCode, modelVersion));
    }
}
