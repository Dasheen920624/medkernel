package com.medkernel.engine.authoring;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创作批量任务 REST 入口。
 */
@RestController
@RequestMapping("/api/v1/engine/authoring/batch")
@DataScope(requireTenant = true)
public class AuthoringBatchJobController {

    private final AuthoringBatchJobService service;

    public AuthoringBatchJobController(AuthoringBatchJobService service) {
        this.service = service;
    }

    /**
     * 分页查询批量任务台账。
     */
    @GetMapping
    @PreAuthorize("@perm.hasAny('rule.read','pathway.read','package.read')")
    public ApiResult<PageResponse<AuthoringBatchJobResponse>> listRecent(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(service.listRecent(new PageRequest(page, size, null)));
    }

    /**
     * 查询批量任务详情。
     */
    @GetMapping("/{jobId}")
    @PreAuthorize("@perm.hasAny('rule.read','pathway.read','package.read')")
    public ApiResult<AuthoringBatchJobResponse> get(@PathVariable String jobId) {
        return ApiResult.ok(service.get(jobId));
    }

    /**
     * 通过规则模板和参数表批量生成规则草稿。
     */
    @PostMapping("/rules/generate")
    @PreAuthorize("@perm.has('rule.write')")
    public ApiResult<AuthoringBatchJobResponse> generateRules(
            @RequestBody @Valid AuthoringBatchRuleGenerateRequest request) {
        return ApiResult.ok(service.generateRules(request));
    }

    /**
     * 聚合分析规则批量发布影响。
     */
    @PostMapping("/rules/impact")
    @PreAuthorize("@perm.has('rule.read')")
    public ApiResult<AuthoringBatchRuleImpactResponse> analyzeRuleImpacts(
            @RequestBody @Valid AuthoringBatchRuleImpactRequest request) {
        return ApiResult.ok(service.analyzeRuleImpacts(request));
    }

    /**
     * 批量推进规则治理状态。
     */
    @PostMapping("/rules/publish")
    @PreAuthorize("@perm.hasAny('rule.publish','rule.write')")
    public ApiResult<AuthoringBatchJobResponse> publishRules(
            @RequestBody @Valid AuthoringBatchRulePublishRequest request) {
        return ApiResult.ok(service.publishRules(request));
    }

    /**
     * 批量导入离线配置包。
     */
    @PostMapping("/packages/import")
    @PreAuthorize("@perm.has('package.publish')")
    public ApiResult<AuthoringBatchJobResponse> importPackages(
            @RequestBody @Valid AuthoringBatchPackageImportRequest request) {
        return ApiResult.ok(service.importPackages(request));
    }

    /**
     * 批量导出离线配置包。
     */
    @PostMapping("/packages/export")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<AuthoringBatchJobResponse> exportPackages(
            @RequestBody @Valid AuthoringBatchPackageExportRequest request) {
        return ApiResult.ok(service.exportPackages(request));
    }

    /**
     * 批量向多个同步目标分发配置包。
     */
    @PostMapping("/packages/distribute")
    @PreAuthorize("@perm.has('package.publish')")
    public ApiResult<AuthoringBatchJobResponse> distributePackages(
            @RequestBody @Valid AuthoringBatchPackageDistributeRequest request) {
        return ApiResult.ok(service.distributePackages(request));
    }
}
